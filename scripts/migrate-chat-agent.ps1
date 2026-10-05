$ErrorActionPreference = 'Stop'

$backendRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'code/backend'
$devConfig = Join-Path $backendRoot 'src/main/resources/application-dev.yml'
$migration = Join-Path $backendRoot 'src/main/resources/db/migration/V20260930_01__chat_agent_persistence.sql'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

$hostName = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { 'localhost' }
$port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3306' }
$database = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'mindman' }
$username = if ($env:MYSQL_USERNAME) { $env:MYSQL_USERNAME } else { 'root' }

if ($hostName -notin @('localhost', '127.0.0.1', '::1')) {
    throw '为避免误改远程数据库，本脚本只允许连接本机 MySQL。'
}
if (-not (Test-Path -LiteralPath $mysql)) {
    throw '未找到 MySQL 8.0 客户端。'
}
if (-not (Test-Path -LiteralPath $migration)) {
    throw '未找到 MindMan 会话迁移文件。'
}

$password = $env:MYSQL_PASSWORD
if (-not $password) {
    $passwordLine = Select-String -Path $devConfig -Pattern '^\s*password\s*:\s*\$\{MYSQL_PASSWORD:([^}]*)\}\s*$' |
        Select-Object -First 1
    if (-not $passwordLine) {
        throw '没有可用的本地 MySQL 密码配置。'
    }
    $password = $passwordLine.Matches[0].Groups[1].Value
}

$credentialFile = Join-Path $env:TEMP ('mindman-migration-' + [guid]::NewGuid().ToString('N') + '.cnf')
try {
    $escapedPassword = $password.Replace('\', '\\').Replace('"', '\"')
    $optionText = "[client]`nuser=$username`npassword=`"$escapedPassword`"`nhost=$hostName`nport=$port`ndefault-character-set=utf8mb4`n"
    [System.IO.File]::WriteAllText($credentialFile, $optionText, [System.Text.Encoding]::ASCII)

    $account = "$($env:USERDOMAIN)\$($env:USERNAME):(F)"
    & icacls.exe $credentialFile /inheritance:r /grant:r $account 'NT AUTHORITY\SYSTEM:(F)' *> $null
    if ($LASTEXITCODE -ne 0) {
        throw '无法限制临时数据库凭据文件的访问权限。'
    }

    $preflightArgs = @(
        "--defaults-extra-file=$credentialFile", '--batch', '--skip-column-names', $database,
        '--execute', "SELECT CONCAT(DATABASE(), '|', COUNT(*)) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('chat_session','chat_message')"
    )
    $preflight = & $mysql @preflightArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw '连接本地 MindMan 数据库失败，未执行迁移。'
    }
    if (($preflight -join '').Trim() -ne "$database|2") {
        throw '本地数据库未同时包含 MindMan 会话表和消息表，未执行迁移。'
    }

    $columnsArgs = @(
        "--defaults-extra-file=$credentialFile", '--batch', '--skip-column-names', $database,
        '--execute', "SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='chat_session' AND column_name IN ('summary','summary_updated_at')) OR (table_name='chat_message' AND column_name='delivery_status'))"
    )
    $existingColumns = & $mysql @columnsArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取本地会话表结构，未执行迁移。'
    }

    $present = @($existingColumns | ForEach-Object { $_.Trim() })
    $statements = [System.Collections.Generic.List[string]]::new()
    if ('chat_session.summary' -notin $present) {
        $statements.Add('ALTER TABLE chat_session ADD COLUMN summary MEDIUMTEXT NULL')
    }
    if ('chat_session.summary_updated_at' -notin $present) {
        $statements.Add('ALTER TABLE chat_session ADD COLUMN summary_updated_at DATETIME NULL')
    }
    if ('chat_message.delivery_status' -notin $present) {
        $statements.Add("ALTER TABLE chat_message ADD COLUMN delivery_status VARCHAR(16) NOT NULL DEFAULT 'complete'")
    }
    if ($statements.Count -gt 0) {
        $migrationArgs = @(
            "--defaults-extra-file=$credentialFile", '--default-character-set=utf8mb4', $database,
            '--execute', ($statements -join '; ')
        )
        & $mysql @migrationArgs | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'MindMan 会话数据库迁移执行失败。'
        }
    }

    $verifyArgs = @(
        "--defaults-extra-file=$credentialFile", '--batch', '--skip-column-names', $database,
        '--execute', "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='chat_session' AND column_name IN ('summary','summary_updated_at')) OR (table_name='chat_message' AND column_name='delivery_status'))"
    )
    $verified = & $mysql @verifyArgs 2>&1
    if ($LASTEXITCODE -ne 0 -or ($verified -join '').Trim() -ne '3') {
        throw '迁移后的字段校验失败。'
    }

    Write-Output 'MindMan 本地会话迁移完成：已验证 3 个新增字段，原有记录未删除。'
}
finally {
    if (Test-Path -LiteralPath $credentialFile) {
        Remove-Item -LiteralPath $credentialFile -Force
    }
}
