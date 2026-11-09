$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime = Join-Path $root '.runtime'
Get-ChildItem $runtime -Filter '*.pid' -ErrorAction SilentlyContinue | ForEach-Object {
    $servicePid = [int](Get-Content $_.FullName)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$servicePid" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -like "*$root*" -and $process.Name -in @('java.exe', 'node.exe')) {
        Stop-Process -Id $servicePid
        Write-Host "Stopped $($_.BaseName) (PID $servicePid)"
    }
}
# Infrastructure and all database volumes intentionally remain intact.
