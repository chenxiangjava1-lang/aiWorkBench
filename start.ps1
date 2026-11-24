# ============================================================
#  AI Workbench - Background Startup
#  MySQL (Windows Service) + Spring Boot + Vue 3
#  All processes fully detached via WMI
# ============================================================

$ErrorActionPreference = "Continue"

$MYSQL   = "F:\aiSoft\mysql-commercial-26.7.0-winx64"
$JDK     = "F:\aiSoft\jdk-17.0.20.1+1"
$PROJECT = Split-Path -Parent $MyInvocation.MyCommand.Path
$BACKEND = Join-Path $PROJECT "ai-workbench-java"
$FRONT   = Join-Path $PROJECT "ai-workbench\client"

Write-Host ""
Write-Host "=========================================="
Write-Host "  AI Workbench - Background Startup"
Write-Host "=========================================="
Write-Host ""

# ---- 0. Kill old ----
Write-Host "[0/4] kill old processes..."
taskkill /F /IM java.exe 2>$null | Out-Null
taskkill /F /IM node.exe 2>$null | Out-Null
Start-Sleep -Seconds 2
Write-Host "  OK"

# ---- 1. MySQL ----
Write-Host "[1/4] MySQL (Windows Service)..."
$svc = Get-Service -Name "MySQLWorkbench" -ErrorAction SilentlyContinue
if (-not $svc) {
    & "$MYSQL\bin\mysqld.exe" --install MySQLWorkbench 2>$null
}
$svc = Get-Service -Name "MySQLWorkbench"
if ($svc.Status -ne "Running") {
    Start-Service -Name "MySQLWorkbench"
    Start-Sleep -Seconds 5
}
$pidM = (Get-Process mysqld -ErrorAction SilentlyContinue).Id
Write-Host "  OK - MySQL ready (PID $pidM)"

# ---- 2. Spring Boot (WMI detached) ----
Write-Host "[2/4] Spring Boot (port 8080)..."
$jarPath = Join-Path $BACKEND "target\ai-workbench-1.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "  ERROR: jar not found: $jarPath" -ForegroundColor Red
    exit 1
}
# 把 JAVA_HOME / PATH 注入当前进程环境，WMI 子进程会继承
$env:JAVA_HOME = $JDK
$env:PATH = "$($JDK)\bin;" + $env:PATH

$bootCmd = "`"$JDK\bin\java.exe`" -jar `"$jarPath`""
try {
    $bootWmi = ([WMICLASS]"\\.\ROOT\CIMV2:Win32_Process").Create($bootCmd, $BACKEND, $null)
    $pidB = $bootWmi.ProcessId
    if ($pidB) { Write-Host "  OK - launched PID $pidB (WMI detached)" }
    else { throw "WMI ProcessId empty" }
} catch {
    Write-Host "  WARN: WMI Create failed ($_), falling back to Start-Process" -ForegroundColor Yellow
    $proc = Start-Process -FilePath "$JDK\bin\java.exe" -ArgumentList "-jar", $jarPath `
        -WorkingDirectory $BACKEND -WindowStyle Hidden -PassThru
    $pidB = $proc.Id
    Write-Host "  OK - launched PID $pidB (Start-Process detached)"
}

# wait for health
$bootReady = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $bootReady = $true; break }
    } catch {}
}
if ($bootReady) { Write-Host "  OK - health ready after ${i}s" }
else { Write-Host "  WARN: health timeout (60s)" -ForegroundColor Yellow }

# ---- 3. Vue (WMI detached) ----
Write-Host "[3/4] Vue frontend (port 5173)..."
if (-not (Test-Path (Join-Path $FRONT "node_modules"))) {
    Write-Host "  WARN: node_modules missing, skipping" -ForegroundColor Yellow
} else {
    try {
        $frontWmi = ([WMICLASS]"\\.\ROOT\CIMV2:Win32_Process").Create("npm.cmd run dev", $FRONT, $null)
        $pidF = $frontWmi.ProcessId
        if ($pidF) { Write-Host "  OK - launched PID $pidF (WMI detached)" }
        else { throw "WMI ProcessId empty" }
    } catch {
        Write-Host "  WARN: WMI Create failed ($_), falling back to Start-Process" -ForegroundColor Yellow
        $proc = Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev" `
            -WorkingDirectory $FRONT -WindowStyle Hidden -PassThru
        Write-Host "  OK - launched PID $($proc.Id) (Start-Process detached)"
    }
}

# ---- 4. Verify ----
Write-Host "[4/4] verify..."
Start-Sleep -Seconds 3
$h = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -UseBasicParsing -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "=========================================="
Write-Host "  ALL RUNNING (background)"
Write-Host "  MySQL:       localhost:3306"
Write-Host "  Spring Boot: http://localhost:8080"
Write-Host "  Health ok:   $($h.ok)"
Write-Host "  Dashboard:   http://localhost:5173"
Write-Host "=========================================="
Write-Host ""
