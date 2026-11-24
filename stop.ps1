# ============================================================
#  AI Workbench - Stop All Services
# ============================================================

Write-Host ""
Write-Host "=========================================="
Write-Host "  AI Workbench - Stop All"
Write-Host "=========================================="
Write-Host ""

# Spring Boot + Vue
Write-Host "[1/2] kill java.exe ..."
taskkill /F /IM java.exe 2>$null | Out-Null
Write-Host "[2/2] kill node.exe ..."
taskkill /F /IM node.exe 2>$null | Out-Null

# MySQL stays as service (may have other DBs using it)
Write-Host ""
Write-Host "MySQL service kept running (MySQLWorkbench)."
Write-Host "To stop MySQL:  net stop MySQLWorkbench"
Write-Host "To uninstall:   mysqld --remove MySQLWorkbench"
Write-Host ""
Write-Host "Done."
Write-Host ""
