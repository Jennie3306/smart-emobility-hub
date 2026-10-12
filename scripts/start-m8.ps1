param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskLogDir = Join-Path $taskRoot '.local'
New-Item -ItemType Directory -Path $taskLogDir -Force | Out-Null
foreach ($taskPort in @(8080,5173)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $taskPort -ErrorAction SilentlyContinue) {
        throw "Port $taskPort is occupied. Stop the existing app before starting M8."
    }
}
Push-Location $taskRoot
try {
    docker compose up -d db
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop and retry.' }
    $taskMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $taskMavenPath = if ($taskMaven) { $taskMaven.Source } else { 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' }
    if (-not $SkipBuild) {
        if (-not (Test-Path -LiteralPath $taskMavenPath)) { throw 'Maven is required. Add mvn.cmd to PATH.' }
        & $taskMavenPath -B -f backend/pom.xml package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        Push-Location (Join-Path $taskRoot 'frontend')
        try {
            if (-not (Test-Path node_modules)) { npm.cmd install; if ($LASTEXITCODE -ne 0) { throw 'Dependency install failed.' } }
            npm.cmd run build
            if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        } finally { Pop-Location }
    }
    $taskJar = Join-Path $taskRoot 'backend/target/emobility-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $taskJar)) { throw 'Backend JAR missing; run without -SkipBuild.' }
    $taskBackend = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',('"' + $taskJar + '"'),'--server.address=127.0.0.1') -WorkingDirectory (Join-Path $taskRoot 'backend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogDir 'backend.log') -RedirectStandardError (Join-Path $taskLogDir 'backend-error.log') -PassThru
    $taskFrontend = Start-Process -FilePath (Get-Command node).Source -ArgumentList @(('"' + (Join-Path $taskRoot 'frontend/node_modules/vite/bin/vite.js') + '"'),'--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $taskRoot 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogDir 'frontend.log') -RedirectStandardError (Join-Path $taskLogDir 'frontend-error.log') -PassThru
    @(@{id=$taskBackend.Id;startTime=$taskBackend.StartTime.ToUniversalTime().ToString('o');kind='backend'},@{id=$taskFrontend.Id;startTime=$taskFrontend.StartTime.ToUniversalTime().ToString('o');kind='frontend'}) | ConvertTo-Json | Set-Content (Join-Path $taskLogDir 'processes.json')
    Write-Output 'M8 starting: http://localhost:5173/m8/simulation — operator1 / 123456'
    Write-Output 'Startup logs: .local/backend.log and .local/frontend.log'
} finally { Pop-Location }
