# Start / stop the whole app for local development.
#   powershell -ExecutionPolicy Bypass -File dev.ps1 start    # backend :8080 + frontend :5173, opens the browser
#   powershell -ExecutionPolicy Bypass -File dev.ps1 stop
#   powershell -ExecutionPolicy Bypass -File dev.ps1 status
# Logs: logs/backend.log, logs/frontend.log (+ *.err.log)
param([ValidateSet('start', 'stop', 'status')][string]$Command = 'start')

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logs = Join-Path $root 'logs'
$apps = @(
    @{ Name = 'backend';  Port = 8080; Dir = 'backend';  Exe = 'mvn.cmd'; Args = @('-q', 'spring-boot:run') },
    @{ Name = 'frontend'; Port = 5173; Dir = 'frontend'; Exe = 'npm.cmd'; Args = @('run', 'dev') }
)

function Get-PortPid([int]$port) {
    (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
}

function Start-App($app) {
    if (Get-PortPid $app.Port) { Write-Output "$($app.Name) already running on :$($app.Port)"; return }
    $dir = Join-Path $root $app.Dir
    if ($app.Name -eq 'frontend' -and -not (Test-Path (Join-Path $dir 'node_modules'))) {
        Write-Output 'frontend: npm install (first run)'
        Push-Location $dir; try { npm.cmd install --no-fund --no-audit | Out-Null } finally { Pop-Location }
    }
    Start-Process -FilePath $app.Exe -ArgumentList $app.Args -WorkingDirectory $dir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs "$($app.Name).log") `
        -RedirectStandardError (Join-Path $logs "$($app.Name).err.log") | Out-Null
}

function Wait-App($app, [int]$seconds = 120) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Get-PortPid $app.Port) { Write-Output "$($app.Name) ready on :$($app.Port)"; return }
        Start-Sleep -Seconds 1
    }
    throw "$($app.Name) did not start within $seconds s, see logs/$($app.Name).log"
}

switch ($Command) {
    'start' {
        New-Item -ItemType Directory -Force $logs | Out-Null
        foreach ($app in $apps) { Start-App $app }
        foreach ($app in $apps) { Wait-App $app }
        Start-Process 'http://localhost:5173'
    }
    'stop' {
        foreach ($app in $apps) {
            $procId = Get-PortPid $app.Port
            if ($procId) {
                # /T kills the whole tree: mvn/npm leave child java/node processes behind otherwise.
                taskkill /PID $procId /T /F | Out-Null
                Write-Output "$($app.Name) stopped (pid $procId)"
            } else {
                Write-Output "$($app.Name) not running"
            }
        }
    }
    'status' {
        foreach ($app in $apps) {
            $procId = Get-PortPid $app.Port
            Write-Output ("{0,-9} :{1}  {2}" -f $app.Name, $app.Port, $(if ($procId) { "running (pid $procId)" } else { 'stopped' }))
        }
    }
}
