param([Parameter(Mandatory)][string]$JavaHome, [string]$RepositoryRoot, [string]$EnvironmentFile)
$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$repo = if ($RepositoryRoot) { (Resolve-Path -LiteralPath $RepositoryRoot).Path } else { $sourceRoot }
$envFile = if ($EnvironmentFile) { $EnvironmentFile } else { Join-Path $sourceRoot 'ClinicAppointmentBE/.env' }
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
}
# Reuse the configured identity provider; never reset/import its realm or access deployment business DBs.
$realm = Get-Content -LiteralPath (Join-Path $repo 'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json') -Raw | ConvertFrom-Json
if (-not $env:APPOINTMENT_PATIENT_TOKEN_CLIENT_SECRET) {
    $env:APPOINTMENT_PATIENT_TOKEN_CLIENT_SECRET = ($realm.clients | Where-Object clientId -eq 'appointment-service').secret
}
$module = Join-Path $repo 'ClinicAppointmentBE/services/doctor-service'
$report = Get-ChildItem -LiteralPath (Join-Path $module 'target/surefire-reports') -Filter 'TEST-*.xml' | Select-Object -First 1
if (-not $report) { throw 'Build/package and run tests first.' }
[xml]$xml = Get-Content -LiteralPath $report.FullName
$cp = ($xml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
$argsFile = Join-Path ([IO.Path]::GetTempPath()) ('fclinic-onboarding-acceptance-' + [guid]::NewGuid().ToString('N') + '.args')
try {
    $source = Join-Path $PSScriptRoot 'TrustedOnboardingAcceptance.java'
    [IO.File]::WriteAllLines($argsFile, @('-cp', ('"' + $cp.Replace('\', '/') + '"'), ('"' + $source.Replace('\', '/') + '"'), ('"' + $repo.Replace('\', '/') + '"')))
    & (Join-Path $JavaHome 'bin/java.exe') "@$argsFile"
    if ($LASTEXITCODE -ne 0) { throw 'Trusted onboarding acceptance failed; inspect the test-owned logs.' }
} finally { Remove-Item -LiteralPath $argsFile -ErrorAction SilentlyContinue }
