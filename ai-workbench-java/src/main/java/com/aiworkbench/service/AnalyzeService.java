package com.aiworkbench.service;

import com.aiworkbench.entity.*;
import com.aiworkbench.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiworkbench.config.TimeUtil;
import java.time.Instant;
import java.util.*;

@Service
public class AnalyzeService {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeService.class);

    @Autowired ExpectedTaskRepo taskRepo;
    @Autowired CollectionRecordRepo recordRepo;
    @Autowired AnalysisResultRepo resultRepo;
    @Autowired AssetRepo assetRepo;
    @Autowired TaskAttemptRepo attemptRepo;

    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate rest = new RestTemplate();

    // ========== Spring AI ChatClient（自动注入） ==========
    private final ChatModel chatModel;

    public AnalyzeService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Value("${llm.endpoint:}") String llmEndpoint;
    @Value("${llm.api-key:}") String llmApiKey;
    @Value("${llm.model:gpt-4o-mini}") String llmModel;
    @Value("${llm.timeout-ms:15000}") int llmTimeout;

    /** Spring AI 可用条件：配了 endpoint 且不是占位地址 */
    public boolean canUseSpringAI() {
        return chatModel != null
            && llmEndpoint != null
            && !llmEndpoint.isBlank()
            && !llmEndpoint.contains("localhost:9999");
    }

    public boolean canUseLive() {
        return canUseSpringAI();
    }

    @Transactional
    public Map<String, Object> analyze(String taskKey) {
        ExpectedTask t = taskRepo.findById(taskKey).orElse(null);
        if (t == null) return Map.of("ok", false, "error", "任务不存在");
        CollectionRecord rec = recordRepo.findTopByTaskKey(taskKey);
        if (rec == null || rec.getRawAnswer() == null) {
            return Map.of("ok", false, "error", "该任务没有原始回答可供分析");
        }
        Asset asset = assetRepo.findById(t.getAssetId()).orElse(null);
        String raw = rec.getRawAnswer();
        String brandName = asset != null ? asset.getBrandName() : "";
        String assetName = asset != null ? asset.getAssetName() : "";

        // 路由：RestTemplate 直连 Ark（主路） → Mock 降级
        // 注：Spring AI ChatClient 因 Ark URL 路径拼接问题（/v3/v1 双重前缀）会白重试 4 次，已跳过
        String mode = "mock";
        String liveError = null;
        Map<String, Object> structured = null;

        if (canUseSpringAI()) {
            try {
                structured = callArkDirect(raw, brandName, assetName, t.getQuestion());
                mode = "ark-direct";
            } catch (Exception e) {
                liveError = "ArkDirect: " + e.getMessage();
                mode = "mock-fallback";
            }
        }

        if (structured == null) {
            structured = mockAnalyzeOnly(raw, brandName, assetName);
        }

        // evidence 可追溯校验
        Map<String, Object> validated = validateEvidence(structured, raw);
        String evidenceText = (String) validated.get("evidence_text");
        boolean isValid = (boolean) validated.get("is_valid");
        String validationNote = (String) validated.get("validation_note");
        List<String> riskFlags = (List<String>) validated.get("risk_flags");

        List<String> citationUrls = (List<String>) structured.get("citation_urls");
        if (citationUrls == null || citationUrls.isEmpty()) {
            if (rec.getSourceUrl() != null) citationUrls = List.of(rec.getSourceUrl());
            else citationUrls = List.of();
        }
        if (!citationUrls.isEmpty() && rec.getSourceUrl() != null) {
            for (String u : citationUrls) {
                if (!u.equals(rec.getSourceUrl()) && !raw.contains(u)) {
                    riskFlags.add("citation_not_in_source");
                    break;
                }
            }
        }

        String now = TimeUtil.now();
        String resultId = "RES-" + mode.toUpperCase().replace("-", "_") + "-" + System.currentTimeMillis();

        AnalysisResult ar = new AnalysisResult();
        ar.setResultId(resultId);
        ar.setTaskKey(taskKey);
        ar.setRecordId(rec.getRecordId());
        ar.setSummary(truncate((String) structured.getOrDefault("summary", ""), 200));
        ar.setBrandMentioned(normalizeMention((String) structured.get("brand_mentioned")));
        ar.setRecommended(normalizeRec((String) structured.get("recommended")));
        ar.setEvidenceText(evidenceText);
        try { ar.setCitationUrls(om.writeValueAsString(citationUrls)); } catch (Exception e) {}
        try { ar.setRiskFlags(om.writeValueAsString(riskFlags)); } catch (Exception e) {}
        ar.setAnalysisSource(mode);
        ar.setAnalyzedAt(now);
        ar.setIsValid(isValid ? 1 : 0);
        ar.setValidationNote(validationNote);

        resultRepo.deleteByTaskKey(taskKey);
        resultRepo.save(ar);

        // analyze 每次新建 attempt（repair 成功后会统一清场，只留最新一条）
        long maxAttempt = attemptRepo.findByTaskKey(taskKey).stream()
            .mapToLong(a -> a.getAttemptNo() != null ? a.getAttemptNo() : 0).max().orElse(0);
        TaskAttempt att = new TaskAttempt();
        att.setAttemptId("ATT-" + System.currentTimeMillis());
        att.setTaskKey(taskKey);
        att.setAttemptNo((int) maxAttempt + 1);
        att.setStatus("completed");
        att.setStartedAt(now);
        att.setFinishedAt(now);
        if ("mock-fallback".equals(mode)) att.setErrorReason("Live 调用失败，降级 Mock: " + liveError);
        attemptRepo.save(att);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("analysis_mode", mode);
        resp.put("live_error", liveError);
        resp.put("result", ar);
        return resp;
    }

    // ========== 真实采集：调 Kimi 直接回答问题 ==========
    @Transactional
    public Map<String, Object> collectRealAnswer(String taskKey) {
        ExpectedTask t = taskRepo.findById(taskKey).orElse(null);
        if (t == null) return Map.of("ok", false, "error", "任务不存在");
        Asset asset = assetRepo.findById(t.getAssetId()).orElse(null);
        String platform = t.getPlatform();
        String question = t.getQuestion();
        String brandName = asset != null ? asset.getBrandName() : "";
        String assetName = asset != null ? asset.getAssetName() : "";

        String prompt = """
            请回答下面这个用户问题，用自然语言直接给建议，不要输出 JSON、不要 markdown 标题、不要自我介绍或免责声明。
            可以自然地提到品牌 %s 吗？也可以不提及，视问题相关性而定。
            回答长度 200-400 字。

            用户问题：%s
            """.formatted(brandName, question);

        // 复用 callArkDirect 的 URL/headers/body 构造逻辑
        String url = llmEndpoint + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
            Map.of("role", "system", "content", "你是一个专业的产品选购顾问，请用自然语言、中文、200-400字回答用户问题。直接给答案，不要自我介绍、免责声明或思维过程。"),
            Map.of("role", "user", "content", prompt)
        ));
        body.put("max_tokens", 1200);
        body.put("temperature", 1.0);

        log.info("🤖 [Collect] 调 Kimi 直接回答 task={} question={}", taskKey, question);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> respMap = om.readValue(resp.getBody(), Map.class);
            List<Map<String, Object>> choices = (List) respMap.get("choices");
            Map<String, Object> message = (Map) choices.get(0).get("message");
            String rawAnswer = (String) message.get("content");
            String reasoning = (String) message.get("reasoning_content");
            log.info("🤖 [Collect] Ark 返回 content_len={} reasoning_len={}",
                     rawAnswer == null ? 0 : rawAnswer.length(),
                     reasoning == null ? 0 : reasoning.length());
            if (rawAnswer == null || rawAnswer.isBlank()) {
                // kimi-k3 可能只把正文放在 reasoning_content 里（某些版本）
                if (reasoning != null && !reasoning.isBlank()) {
                    log.warn("🤖 [Collect] content 为空，退回用 reasoning_content");
                    rawAnswer = reasoning;
                } else {
                    log.error("🤖 [Collect] content 与 reasoning_content 都为空，原始 body 前 500 字: {}",
                              resp.getBody() == null ? "<null>" : resp.getBody().substring(0, Math.min(500, resp.getBody().length())));
                    return Map.of("ok", false, "error", "Kimi 返回空回答");
                }
            }
            rawAnswer = rawAnswer.trim();

            // 存 DB
            String recId = "REC-KIMI-REAL-" + Instant.now().toEpochMilli();
            CollectionRecord rec = new CollectionRecord();
            rec.setRecordId(recId);
            rec.setTaskKey(taskKey);
            rec.setConnectorId("CON-LLM-SIM");
            rec.setRawAnswer(rawAnswer);
            rec.setSourceUrl("https://ark.cn-beijing.volces.com/chat/" + taskKey);
            rec.setCollectedAt(TimeUtil.now());
            recordRepo.save(rec);

            log.info("🤖 [Collect] 存 DB 成功 record_id={} raw_answer 前 100 字: {}", recId, rawAnswer.substring(0, Math.min(100, rawAnswer.length())));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("source", "kimi");
            result.put("record_id", recId);
            result.put("task_key", taskKey);
            result.put("raw_answer", rawAnswer);
            return result;
        } catch (Exception e) {
            log.error("🤖 [Collect] 失败: {}", e.getMessage());
            return Map.of("ok", false, "error", "调 Kimi 失败: " + e.getMessage());
        }
    }

    // ========== 统一 Prompt 构造（Spring AI 和 Ark 直连共用） ==========
    private String buildAnalyzePrompt(String raw, String brandName, String assetName, String question) {
        return """
            你是品牌监测分析器。从下面AI平台回答中提取结构化信息。

            ## 输入
            - 品牌：%s
            - 产品：%s
            - 问题：%s

            === raw_answer 开始 ===
            %s
            === raw_answer 结束 ===

            ## 输出规则（必须严格遵守）

            1. evidence_text 必须是 raw_answer 中的原文子串，不能改写或编造。
            2. brand_mentioned 只能是 yes（明确提到品牌）/ no（没提到）/ partial（间接提到或模糊）。
            3. recommended 只能是 yes（推荐）/ no（不推荐）/ neutral（中性、无明确态度）。
            4. risk_flags 是数组，**至少输出 1 条**！检查以下维度：
               - 如果 raw_answer 开头包含 "模拟平台" 字样 → 必须加 "simulated_answer"（模拟平台回答，非真实平台采集）
               - 如果回答是有条件推荐（如 "预算合适时"、"值得考虑"） → 加 "conditional_recommendation"
               - 如果回答没有具体型号、参数、实测数据 → 加 "lacks_specifics"
               - 如果回答没有引用来源或链接 → 加 "no_citation"
               - 如果有负面或保留意见 → 加 "negative_sentiment"
               - 如果品牌提及但没推荐 → 加 "brand_mentioned_not_recommended"
               - 如果以上都不满足 → 加 "no_major_risks"（表示无明显风险，但也不能为空数组！）
            5. citation_urls 如果 raw_answer 里没有 URL，返回空数组 []。
            6. 严格输出合法 JSON 对象，不要 markdown、不要解释、不要额外文字。

            ## 输出 schema
            {"summary":"简短概括，200字内","brand_mentioned":"yes|no|partial","recommended":"yes|no|neutral","evidence_text":"原文子串","citation_urls":[],"risk_flags":["根据上述规则输出至少1条"]}
            """.formatted(brandName, assetName, question, raw);
    }

    // ========== Spring AI ChatClient 调用 ==========
    private Map<String, Object> callSpringAI(String raw, String brandName, String assetName, String question) throws Exception {
        String prompt = buildAnalyzePrompt(raw, brandName, assetName, question);

        ChatClient client = ChatClient.builder(chatModel).build();
        String content = client.prompt()
            .system("Output valid JSON only, no extra text.")
            .user(prompt)
            .call()
            .content();

        content = content.replace("```json", "").replace("```", "").trim();
        content = fixJsonTail(content);
        return om.readValue(content, Map.class);
    }

    // ========== RestTemplate 直连火山引擎 Ark（绕过 Spring AI 反序列化限制） ==========
    private Map<String, Object> callArkDirect(String raw, String brandName, String assetName, String question) throws Exception {
        String prompt = buildAnalyzePrompt(raw, brandName, assetName, question);

        String url = llmEndpoint + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
            Map.of("role", "system", "content", "Output valid JSON only, no extra text."),
            Map.of("role", "user", "content", prompt)
        ));
        body.put("max_tokens", 8000);  // kimi-k3 推理模型：思维链可能花 2000+ tokens，必须给足
        body.put("temperature", 1.0);  // kimi-k3-1-pro 只允许 temperature=1

        log.info("🤖 [Ark] POST {} model={} prompt_len={}", url, llmModel, prompt.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, entity, String.class);

        // ===== 打印 Ark 原始响应 =====
        log.info("🤖 [Ark] HTTP {} Content-Type={}", resp.getStatusCode(), resp.getHeaders().getContentType());
        String rawJson = resp.getBody();
        log.info("🤖 [Ark] 原始 JSON: {}", rawJson);

        Map<String, Object> respMap = om.readValue(rawJson, Map.class);

        // Ark 响应：choices[0].message.content
        List<Map<String, Object>> choices = (List) respMap.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("Ark 返回空 choices");
        Map<String, Object> message = (Map) choices.get(0).get("message");
        String content = (String) message.get("content");
        // 也打印 reasoning_content（思维链）
        String reasoning = (String) message.get("reasoning_content");
        if (reasoning != null && !reasoning.isBlank()) log.info("🤖 [Ark] reasoning_content(思维链) len={}", reasoning.length());

        // kimi-k3 推理模型可能把正文塞 reasoning_content 里，content 为空
        if (content == null || content.isBlank()) {
            log.warn("🤖 [Ark] content 为空，尝试从 reasoning_content 里提取 JSON");
            if (reasoning != null && !reasoning.isBlank()) {
                // 从思维链里找 { ... } JSON 块
                int lb = reasoning.lastIndexOf('{');
                int rb = reasoning.lastIndexOf('}');
                if (lb >= 0 && rb > lb) {
                    content = reasoning.substring(lb, rb + 1);
                    log.info("🤖 [Ark] 从 reasoning_content 提取 JSON 前 300 字: {}", content.substring(0, Math.min(300, content.length())));
                }
            }
            if (content == null || content.isBlank()) {
                throw new RuntimeException("Ark 返回空 content，reasoning_content 也没提取到 JSON");
            }
        } else {
            log.info("🤖 [Ark] Kimi 输出 content len={}", content.length());
        }

        content = content.replace("```json", "").replace("```", "").trim();
        // LLM JSON 容错修复：末尾缺少 ] 或 }
        content = fixJsonTail(content);
        log.info("🤖 [Ark] fixJsonTail 后: {}", content);
        Map<String, Object> parsed = om.readValue(content, Map.class);
        log.info("🤖 [Ark] 解析成功: brand_mentioned={} recommended={}", parsed.get("brand_mentioned"), parsed.get("recommended"));
        return parsed;
    }

    /** 修复 LLM 输出 JSON 常见尾巴问题（截断、缺括号） */
    private String fixJsonTail(String json) {
        if (json == null || json.isEmpty()) return json;
        // 去掉最后一个完整的字符串后残留的 " 或 ,
        String s = json.trim();
        // 如果末尾是 , 或 , } 或 , ]，清理
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        // 如果 [ 比 ] 多，补 ]
        int openB = countChar(s, '[');
        int closeB = countChar(s, ']');
        for (int i = 0; i < openB - closeB; i++) s += "]";
        // 如果 { 比 } 多，补 }
        int openC = countChar(s, '{');
        int closeC = countChar(s, '}');
        for (int i = 0; i < openC - closeC; i++) s += "}";
        return s;
    }
    private int countChar(String s, char c) {
        int n = 0; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++; return n;
    }

    // ========== Mock 分析（降级） ==========
    private Map<String, Object> mockAnalyzeOnly(String raw, String brandName, String assetName) {
        List<String> keywords = Arrays.asList(brandName, assetName).stream()
            .filter(k -> k != null && !k.isBlank()).toList();

        String brandMentioned = "no";
        if (keywords.stream().anyMatch(raw::contains)) brandMentioned = "yes";
        else if (raw.length() > 50) brandMentioned = "partial";

        String recommended = "neutral";
        List<String> pos = List.of("推荐", "不错", "值得", "选择", "适合", "优秀");
        List<String> neg = List.of("不推荐", "避免", "谨慎", "问题", "缺点");
        if ("yes".equals(brandMentioned)) {
            if (pos.stream().anyMatch(raw::contains)) recommended = "yes";
            else if (neg.stream().anyMatch(raw::contains)) recommended = "no";
        }

        String summary = raw.length() > 120 ? raw.substring(0, 120) + "..." : raw;

        String evidence = raw;
        for (String sep : new String[]{"。", "！", "？", "\n"}) {
            String[] sentences = raw.split(sep);
            List<String> candidates = Arrays.stream(sentences)
                .filter(s -> s.trim().isEmpty() == false && keywords.stream().anyMatch(s::contains))
                .toList();
            if (!candidates.isEmpty()) { evidence = candidates.get(0).trim(); break; }
            if (sentences.length > 0) { evidence = sentences[0].trim(); }
        }
        evidence = evidence.length() > 120 ? evidence.substring(0, 120) : evidence;

        List<String> riskFlags = new ArrayList<>();
        if (evidence.length() < 5) riskFlags.add("evidence_too_short");
        if ("partial".equals(brandMentioned)) riskFlags.add("brand_not_explicitly_mentioned");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", summary);
        m.put("brand_mentioned", brandMentioned);
        m.put("recommended", recommended);
        m.put("evidence_text", evidence);
        m.put("citation_urls", new ArrayList<>());
        m.put("risk_flags", riskFlags);
        return m;
    }

    private Map<String, Object> validateEvidence(Map<String, Object> s, String raw) {
        String evidence = (String) s.get("evidence_text");
        List<String> flags = new ArrayList<>((List<String>) s.getOrDefault("risk_flags", new ArrayList<>()));
        boolean valid; String note;

        if (evidence == null || evidence.isBlank()) {
            evidence = raw.length() > 120 ? raw.substring(0, 120) : raw;
            valid = raw.contains(evidence);
            note = valid ? "evidence 可追溯（自动修复）" : "evidence 校验失败";
        } else if (raw.contains(evidence)) {
            valid = true; note = "evidence 可追溯";
        } else {
            valid = false; note = "evidence_text 在 raw_answer 中找不到，LLM 可能产生幻觉";
        }

        Map<String, Object> m = new LinkedHashMap<>(s);
        m.put("evidence_text", evidence);
        m.put("is_valid", valid);
        m.put("validation_note", note);
        m.put("risk_flags", flags);
        return m;
    }

    private String normalizeMention(String v) {
        if (v == null) return "no";
        return Set.of("yes", "no", "partial").contains(v) ? v : "no";
    }
    private String normalizeRec(String v) {
        if (v == null) return "neutral";
        return Set.of("yes", "no", "neutral").contains(v) ? v : "neutral";
    }
    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
