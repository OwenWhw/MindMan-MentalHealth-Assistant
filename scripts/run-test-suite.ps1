param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$JMeterPath = '',
    [ValidateRange(1, 100)][int]$Threads = 5,
    [ValidateRange(1, 1000)][int]$Loops = 3,
    [ValidateRange(1, 600)][int]$RampUpSeconds = 10,
    [switch]$AllowRemote,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tests = Join-Path $repo 'tests'
$frontend = Join-Path $repo 'code\ai-vue'
$collection = Join-Path $tests 'mindman-smoke.postman_collection.json'
$jmx = Join-Path $tests 'garden-read.jmx'
$newman = Join-Path $tests 'node_modules\.bin\newman.cmd'
$playwright = Join-Path $frontend 'node_modules\.bin\playwright.cmd'

function Find-JMeter {
    if ($JMeterPath) {
        if (Test-Path -LiteralPath $JMeterPath -PathType Container) {
            $candidate = Join-Path $JMeterPath 'bin\jmeter.bat'
        } else {
            $candidate = $JMeterPath
        }
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return (Resolve-Path -LiteralPath $candidate).Path }
        throw "找不到 JMeter：$candidate"
    }
    if ($env:JMETER_HOME) {
        $candidate = Join-Path $env:JMETER_HOME 'bin\jmeter.bat'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command = Get-Command jmeter -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $downloads = Join-Path $HOME 'Downloads'
    if (Test-Path -LiteralPath $downloads -PathType Container) {
        $found = Get-ChildItem -LiteralPath $downloads -Directory -Filter 'apache-jmeter-*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\jmeter.bat' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
        if ($found) { return $found }
    }
    throw '未找到 JMeter。安装后设置 JMETER_HOME，或传入 -JMeterPath "C:\apache-jmeter\bin\jmeter.bat"。'
}

$jmeter = Find-JMeter
if (-not (Test-Path -LiteralPath $newman -PathType Leaf)) {
    throw "未找到 Newman。请先在 $tests 执行 npm install。"
}
if (-not (Test-Path -LiteralPath $playwright -PathType Leaf)) {
    throw "未找到 Playwright。请先在 $frontend 执行 npm install。"
}
Write-Host "JMeter: $jmeter"
Write-Host "Newman: $newman"
Write-Host "Playwright: $playwright"
if ($CheckOnly) { Write-Host '工具检查通过；尚未发送请求。'; return }

if (-not $env:MINDMAN_TEST_USERNAME -or -not $env:MINDMAN_TEST_PASSWORD) {
    throw '请先设置 MINDMAN_TEST_USERNAME 和 MINDMAN_TEST_PASSWORD（专用测试账号）。'
}
$uri = [uri]$BaseUrl
if ($uri.Scheme -notin @('http', 'https') -or $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment) {
    throw 'BaseUrl 应为站点根地址，例如 http://127.0.0.1:8080。'
}
if (-not $AllowRemote -and $uri.Host -notin @('127.0.0.1', 'localhost', '::1')) {
    throw '默认仅对本机运行压测。若是自己的测试服务器，请显式添加 -AllowRemote。'
}
if (-not (Test-Path -LiteralPath $collection) -or -not (Test-Path -LiteralPath $jmx)) {
    throw '找不到测试集合或 JMeter 测试计划。'
}

# 先做一次认证预检，以免把无法登录的请求当作压测结果。
$base = $BaseUrl.TrimEnd('/')
$loginBody = @{ username = $env:MINDMAN_TEST_USERNAME; password = $env:MINDMAN_TEST_PASSWORD } | ConvertTo-Json -Compress
try {
    $login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody)) -TimeoutSec 10
} catch {
    throw "后端登录预检失败：$($_.Exception.Message)。请确认后端运行、地址和测试账号。"
}
if ($login.code -ne 200 -or -not $login.data.token) {
    throw '后端登录预检没有返回有效 token，测试未开始。'
}

$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 6)
$reportDir = Join-Path $repo "test-results\integration\$runId"
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$envFile = Join-Path ([IO.Path]::GetTempPath()) ("mindman-newman-" + [guid]::NewGuid().ToString('N') + '.json')
$oldToken = $env:MINDMAN_TEST_TOKEN
$oldJunit = $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE
try {
    $env:MINDMAN_TEST_TOKEN = $login.data.token
    $jmeterArgs = @(
        '-n', '-t', $jmx,
        '-l', (Join-Path $reportDir 'jmeter.jtl'),
        '-j', (Join-Path $reportDir 'jmeter.log'),
        '-e', '-o', (Join-Path $reportDir 'jmeter-html'),
        "-Jhost=$($uri.Host)",
        "-Jport=$($uri.Port)",
        "-Jprotocol=$($uri.Scheme)",
        "-Jthreads=$Threads", "-Jloops=$Loops", "-JrampUp=$RampUpSeconds"
    )
    Write-Host '[1/3] JMeter 花园查询轻量压测'
    & $jmeter @jmeterArgs
    if ($LASTEXITCODE -ne 0) { throw "JMeter 退出码：$LASTEXITCODE" }
    $samples = @(Import-Csv -LiteralPath (Join-Path $reportDir 'jmeter.jtl'))
    if ($samples.Count -eq 0 -or @($samples | Where-Object { $_.success -ne 'true' }).Count -gt 0) {
        throw "JMeter 有失败请求或没有样本；查看 $reportDir\jmeter.jtl。"
    }

    # Newman 的环境文件只存在于系统临时目录；结束时删除，避免把测试密码写入仓库或报告。
    $environment = @{ name = 'MindMan local test'; values = @(
        @{ key = 'baseUrl'; value = $base; enabled = $true },
        @{ key = 'username'; value = $env:MINDMAN_TEST_USERNAME; enabled = $true },
        @{ key = 'password'; value = $env:MINDMAN_TEST_PASSWORD; enabled = $true }
    ) } | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText($envFile, $environment, [Text.UTF8Encoding]::new($false))
    Write-Host '[2/3] Postman/Newman 真实接口冒烟测试'
    & $newman run $collection -e $envFile --reporters cli,junit --reporter-junit-export (Join-Path $reportDir 'postman-junit.xml') --timeout-request 15000
    if ($LASTEXITCODE -ne 0) { throw "Postman/Newman 退出码：$LASTEXITCODE" }

    Write-Host '[3/3] Playwright 页面测试'
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = Join-Path $reportDir 'playwright-junit.xml'
    $playwrightLog = Join-Path $reportDir 'playwright.log'
    Push-Location $frontend
    try {
        & $playwright test --reporter=junit *> $playwrightLog
        if ($LASTEXITCODE -ne 0) {
            Get-Content -LiteralPath $playwrightLog -Tail 25 | Write-Host
            throw "Playwright 退出码：$LASTEXITCODE；完整日志：$playwrightLog"
        }
    } finally {
        Pop-Location
    }
    $playwrightXml = [xml](Get-Content -LiteralPath $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE -Raw)
    $testCount = @($playwrightXml.testsuites.testsuite | ForEach-Object { [int]$_.tests } | Measure-Object -Sum).Sum
    Write-Host "Playwright 通过：$testCount 项"
    Write-Host "三阶段测试通过。报告目录：$reportDir"
} finally {
    $env:MINDMAN_TEST_TOKEN = $oldToken
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $oldJunit
    if (Test-Path -LiteralPath $envFile) { Remove-Item -LiteralPath $envFile -Force }
}
