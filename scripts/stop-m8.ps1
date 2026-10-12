$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskPidFile = Join-Path $taskRoot '.local/processes.json'
if (-not (Test-Path $taskPidFile)) { Write-Output 'No recorded M8 processes.'; return }
$taskRecords = Get-Content $taskPidFile -Raw | ConvertFrom-Json
foreach ($taskRecord in $taskRecords) {
    $taskProcess = Get-Process -Id $taskRecord.id -ErrorAction SilentlyContinue
    if ($taskProcess -and $taskProcess.StartTime.ToUniversalTime().ToString('o') -eq $taskRecord.startTime) {
        Stop-Process -Id $taskRecord.id
        Write-Output "Stopped M8 $($taskRecord.kind)."
    }
}
Write-Output 'PostgreSQL and its data were preserved.'
