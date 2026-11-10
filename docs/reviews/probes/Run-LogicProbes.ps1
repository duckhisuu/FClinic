param([Parameter(Mandatory)][string]$JavaHome, [string]$RepositoryRoot)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
if ($RepositoryRoot) { $repo = (Resolve-Path -LiteralPath $RepositoryRoot).Path }
$argsFile = Join-Path ([IO.Path]::GetTempPath()) ('fclinic-review-' + [guid]::NewGuid().ToString('N') + '.args')
Push-Location $repo
try {
    foreach ($probe in @(
        @{ Module='auth-service'; Source='ReviewAuthLogic.java' },
        @{ Module='doctor-service'; Source='ReviewDoctorLogic.java' }
    )) {
        $modulePath = Join-Path $repo ('ClinicAppointmentBE/services/' + $probe.Module)
        $report = Get-ChildItem -LiteralPath (Join-Path $modulePath 'target/surefire-reports') -Filter 'TEST-*.xml' | Select-Object -First 1
        if (-not $report) { throw "Run Maven tests for $($probe.Module) first." }
        [xml]$xml = Get-Content -LiteralPath $report.FullName
        $cp = ($xml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
        $source = Join-Path $PSScriptRoot $probe.Source
        [IO.File]::WriteAllLines($argsFile, @('-cp', ('"' + $cp.Replace('\', '/') + '"'), ('"' + $source.Replace('\', '/') + '"')))
        & (Join-Path $JavaHome 'bin/java.exe') "@$argsFile"
        if ($LASTEXITCODE -ne 0) { throw "Probe failed: $($probe.Source)" }
    }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $argsFile -ErrorAction SilentlyContinue
}
