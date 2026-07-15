#Requires -Version 5.1
<#
.SYNOPSIS
    IT 资产生命周期管理系统 第一版部署冒烟测试（PowerShell）

.DESCRIPTION
    对前端入口（默认 http://127.0.0.1:3000）做端到端冒烟测试，覆盖：
      1) 首页 SPA 外壳
      2) 子路由刷新 SPA fallback
      3) 健康检查 /api/v1/health
      4) 登录 /api/v1/auth/login 并获取 accessToken
      5) 带 token 访问 /api/v1/assets
      6) 带 token 访问 /api/v1/assets/{id}/lifecycle

    通过环境变量配置：
      ITAM_BASE_URL        (默认 http://127.0.0.1:3000)
      ITAM_SMOKE_USER      (登录用户名；缺失则跳过登录态相关断言并告警)
      ITAM_SMOKE_PASSWORD  (登录密码)

    任一项断言失败则 exit 1；全部通过则 exit 0。
#>

$ErrorActionPreference = 'Stop'

# ---------- 配置 ----------
$baseUrl = $env:ITAM_BASE_URL
if ([string]::IsNullOrEmpty($baseUrl)) { $baseUrl = 'http://127.0.0.1:3000' }

$smokeUser = $env:ITAM_SMOKE_USER
$smokePassword = $env:ITAM_SMOKE_PASSWORD
$hasCreds = -not ([string]::IsNullOrEmpty($smokeUser) -or [string]::IsNullOrEmpty($smokePassword))

$failed = 0
$skipped = 0

# ---------- 输出辅助 ----------
function Write-Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; $script:failed++ }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow; $script:skipped++ }
function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }

# ---------- 安全请求（不因病 4xx/5xx 抛异常，统一返回 StatusCode/Content）----------
function Invoke-SafeRequest {
    param(
        [string]$Uri,
        [string]$Method = 'GET',
        [string]$ContentType,
        [string]$Body,
        [hashtable]$Headers
    )
    try {
        $resp = Invoke-WebRequest -Uri $Uri -UseBasicParsing -Method $Method `
            -ContentType $ContentType -Body $Body -Headers $Headers -ErrorAction Stop
        return [pscustomobject]@{
            StatusCode  = [int]$resp.StatusCode
            Content     = $resp.Content
            ContentType = $resp.Headers['Content-Type']
        }
    } catch [System.Net.WebException] {
        $ex = $_.Exception
        $status = $null
        $content = $null
        if ($ex.Response -ne $null) {
            $status = [int]$ex.Response.StatusCode
            try {
                $stream = $ex.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $content = $reader.ReadToEnd()
            } catch { }
        }
        return [pscustomobject]@{
            StatusCode  = $status   # $null 表示连接级失败（无 HTTP 响应）
            Content     = $content
            ContentType = $null
        }
    }
}

# ================= 开始 =================
Write-Info "冒烟测试目标: $baseUrl"
Write-Info "凭据状态: $(if ($hasCreds) { '已提供' } else { '缺失（跳过登录态断言）' })"
Write-Host ''

# 1) 首页 SPA 外壳
$r = Invoke-SafeRequest -Uri "$baseUrl/"
if ($r.StatusCode -eq 200 -and ($r.Content -match '<div id="app">' -or $r.Content -match '<html')) {
    Write-Pass "首页 SPA 外壳 (GET / -> 200, 含 <div id=""app""> 或 <html)"
} else {
    Write-Fail "首页检查失败: status=$($r.StatusCode) (期望 200 且含 SPA 外壳)"
}

# 2) 子路由刷新 SPA fallback
$r = Invoke-SafeRequest -Uri "$baseUrl/assets"
$isHtml = ($null -ne $r.ContentType -and $r.ContentType -match 'text/html') `
    -or ($null -ne $r.Content -and ($r.Content -match '<html' -or $r.Content -match '<div id="app">'))
if ($r.StatusCode -eq 200 -and $isHtml) {
    Write-Pass "子路由刷新 SPA fallback (GET /assets -> 200, HTML)"
} else {
    Write-Fail "子路由刷新失败: status=$($r.StatusCode) (期望 200 且为 HTML)"
}

# 3) 健康检查
$r = Invoke-SafeRequest -Uri "$baseUrl/api/v1/health"
if ($r.StatusCode -eq 200) {
    Write-Pass "健康检查 (GET /api/v1/health -> 200)"
} else {
    Write-Fail "健康检查失败: status=$($r.StatusCode) (期望 200)"
}

# 4) 登录并获取 accessToken
$accessToken = $null
if ($hasCreds) {
    $body = @{ username = $smokeUser; password = $smokePassword } | ConvertTo-Json -Compress
    $r = Invoke-SafeRequest -Uri "$baseUrl/api/v1/auth/login" -Method Post `
        -ContentType 'application/json' -Body $body
    if ($r.StatusCode -eq 200) {
        try {
            $json = $r.Content | ConvertFrom-Json
            $accessToken = $json.data.accessToken
            if (-not [string]::IsNullOrEmpty($accessToken)) {
                Write-Pass "登录成功并获取 accessToken (POST /api/v1/auth/login -> 200)"
            } else {
                Write-Fail "登录返回 200 但未包含 data.accessToken"
            }
        } catch {
            Write-Fail "登录响应解析失败: $_"
        }
    } else {
        Write-Fail "登录失败: status=$($r.StatusCode) (期望 200)"
    }
} else {
    Write-Warn "未提供 ITAM_SMOKE_USER/ITAM_SMOKE_PASSWORD，跳过登录态断言（第4/5/6项）"
}

# 5) 资产列表（带 token）
if ($hasCreds -and -not [string]::IsNullOrEmpty($accessToken)) {
    $r = Invoke-SafeRequest -Uri "$baseUrl/api/v1/assets" `
        -Headers @{ Authorization = "Bearer $accessToken" }
    if ($r.StatusCode -eq 200) {
        Write-Pass "资产列表鉴权链路 (GET /api/v1/assets -> 200)"
    } elseif ($r.StatusCode -in @(401, 403)) {
        Write-Fail "资产列表返回 $($r.StatusCode)（鉴权链路可达，但应为 200，请检查账号权限 asset:view）"
    } elseif ($null -eq $r.StatusCode) {
        Write-Fail "资产列表连接失败（后端不可达，请检查 backend 健康检查）"
    } else {
        Write-Fail "资产列表返回 $($r.StatusCode)（期望 200）"
    }
}

# 6) 生命周期（带 token）
if ($hasCreds -and -not [string]::IsNullOrEmpty($accessToken)) {
    $assetId = '00000000-0000-0000-0000-000000000000'
    $r = Invoke-SafeRequest -Uri "$baseUrl/api/v1/assets/$assetId/lifecycle" `
        -Headers @{ Authorization = "Bearer $accessToken" }
    if ($r.StatusCode -in @(200, 404)) {
        Write-Pass "生命周期链路可达 (GET /api/v1/assets/$assetId/lifecycle -> $($r.StatusCode))"
    } elseif ($null -eq $r.StatusCode) {
        Write-Fail "生命周期连接失败（后端不可达，请检查 backend 健康检查）"
    } else {
        Write-Fail "生命周期返回 $($r.StatusCode)（非预期，应为 200/404）"
    }
}

# ================= 汇总 =================
Write-Host ''
if ($failed -eq 0) {
    if ($skipped -gt 0) {
        Write-Host "[RESULT] 已执行断言全部通过 ✅（有 $skipped 项因缺少凭据被跳过，不影响退出码）" -ForegroundColor Green
    } else {
        Write-Host "[RESULT] 全部断言通过 ✅" -ForegroundColor Green
    }
    exit 0
} else {
    Write-Host "[RESULT] 存在 $failed 项失败 ❌" -ForegroundColor Red
    exit 1
}
