# ============================================================
# Lianzhi Attendance Mini-Program - One-Click Rebuild
# Auto-detect USB network IP -> patch vite.config.ts -> build -> patch-vendor -> copy
# Usage: powershell -ExecutionPolicy Bypass -File .\rebuild.ps1
# ============================================================

$ErrorActionPreference = "Continue"
$MiniDir  = $PSScriptRoot
$DistDir  = Join-Path $MiniDir "dist\build\mp-weixin"
$OutDir   = "D:\lianzhi-mp-v2"

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Lianzhi Mini-Program Rebuild" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# ---------- Step 1: Detect USB-shared network IP ----------
Write-Host "[1/5] Detecting USB network IP ..." -ForegroundColor Yellow

$ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {
    $_.IPAddress -match '^10\.\d+\.\d+\.\d+$' -and
    $_.InterfaceAlias -notmatch 'VMware|WSL|Hyper-V|Loopback' -and
    $_.IPAddress -ne '10.110.215.51'
}

if (-not $ips -or $ips.Count -eq 0) {
    Write-Host "[FAIL] No 10.x.x.x interface found! USB cable connected?" -ForegroundColor Red
    exit 1
}

$usbIp = $ips[0].IPAddress
$iface = $ips[0].InterfaceAlias
Write-Host "       OK: $iface -> $usbIp" -ForegroundColor Green

# ---------- Step 2: Test backend ----------
$backendUrl = "http://${usbIp}:8080"
Write-Host "[2/5] Test backend ${backendUrl} ..." -ForegroundColor Yellow

try {
    $null = Invoke-WebRequest -Uri "$backendUrl/h5/auth/login" -Method POST `
        -TimeoutSec 3 -ContentType "application/json" -Body '{}' -ErrorAction Stop
    Write-Host "       OK: backend reachable" -ForegroundColor Green
} catch {
    $msg = $_.Exception.Message
    if ($msg -and ($msg -match '401|405|200|Unauthorized|Method|error code')) {
        Write-Host "       OK: backend reachable (HTTP error = backend responding)" -ForegroundColor Green
    } elseif ($msg -and $msg -match 'timed out|Timeout|refused|connect') {
        Write-Host "       WARN: backend may be down: $msg" -ForegroundColor DarkYellow
    } else {
        Write-Host "       OK: backend reachable" -ForegroundColor Green
    }
}

# ---------- Step 3: Update vite.config.ts ----------
Write-Host "[3/5] Update vite.config.ts BACKEND_BASE -> ${backendUrl}" -ForegroundColor Yellow

$viteFile = Join-Path $MiniDir "vite.config.ts"
$viteContent = [System.IO.File]::ReadAllText($viteFile)
# Match with optional spaces around IP:port (handles broken "10.x.x.x :8080" too)
$oldPattern = [regex]'const\s+BACKEND_BASE\s*=\s*''http://[\d\.\s]+:8080'''
$newLine    = "const BACKEND_BASE = '${backendUrl}'"

if ($viteContent -match $oldPattern) {
    $viteContent = $oldPattern.Replace($viteContent, $newLine)
    [System.IO.File]::WriteAllText($viteFile, $viteContent, [System.Text.Encoding]::UTF8)
    Write-Host "       OK: updated" -ForegroundColor Green
} else {
    Write-Host "       WARN: BACKEND_BASE pattern not found in vite.config.ts" -ForegroundColor DarkYellow
}

# ---------- Step 4: Build ----------
Write-Host "[4/5] Build mini-program ..." -ForegroundColor Yellow

Push-Location $MiniDir
Remove-Item -Recurse -Force dist -ErrorAction SilentlyContinue

& npm.cmd run build:mp-weixin 2>&1 | ForEach-Object { Write-Host $_ }
$buildOk = ($LASTEXITCODE -eq 0)

if (-not $buildOk) {
    Write-Host "[FAIL] Build failed! Check errors above." -ForegroundColor Red
    Pop-Location
    exit 1
}
Write-Host "       OK: build done" -ForegroundColor Green

& node scripts/patch-vendor.js 2>&1 | ForEach-Object { Write-Host $_ }
Write-Host "       OK: patch done" -ForegroundColor Green
Pop-Location

# ---------- Step 5: Copy to output ----------
Write-Host "[5/5] Copy artifacts to $OutDir ..." -ForegroundColor Yellow

Remove-Item -Recurse -Force $OutDir -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force $DistDir $OutDir

$target = Join-Path $OutDir "mp-weixin\utils\request.js"
if (Test-Path $target) {
    $content = [System.IO.File]::ReadAllText($target)
    if ($content -match [regex]::Escape($usbIp)) {
        Write-Host "       OK: compiled IP matches: $usbIp" -ForegroundColor Green
    } else {
        Write-Host "       WARN: IP not found in compiled output" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "       WARN: request.js not found at $target" -ForegroundColor DarkYellow
}

# ---------- Done ----------
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  DONE!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Backend : ${backendUrl}"
Write-Host "  Import  : $OutDir\mp-weixin"
Write-Host "  AppID   : wx01e24c0c40273a70"
Write-Host ""
Write-Host "  -> DevTools: Ctrl+R to recompile" -ForegroundColor Gray
Write-Host ""
