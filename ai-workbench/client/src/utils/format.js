// === 格式化工具 ===

/** task_key 保持原样：B003|Kimi|2026-11-23 */
export function fmtTaskKey(key) {
  return key || ''
}

/** 时间戳 → yyyy-MM-dd HH:mm:ss
 * 支持: 2026-11-23T05:20:55.305327900Z / 2026-11-23T05:20:55Z / 2026-11-23 05:20:55
 */
export function fmtTime(t) {
  if (!t) return ''
  // 已经是 yyyy-MM-dd HH:mm:ss → 原样
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(t)) return t.substring(0, 19)
  // ISO → 截 T 前日期 + T 后时间，去掉纳秒
  const s = String(t).replace('T', ' ')
  return s.replace(/\.\d+Z?$/, '').replace('Z', '').substring(0, 19)
}
