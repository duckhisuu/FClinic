#requires -Version 7.0
param()
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$envFile = Join-Path $root 'ClinicAppointmentBE/.env'
if (!(Test-Path -LiteralPath $envFile)) { throw "Missing local configuration: $envFile" }
$tlsFile = Join-Path $runtime 'demo-scope-tls.private.json'
if (!(Test-Path -LiteralPath $tlsFile)) {
    $java = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'AuthServiceApplication$' -and $_.CommandLine.Contains($root) })
    $keytool = if ($java.Count -eq 1) { Join-Path (Split-Path $java[0].ExecutablePath) 'keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
    $tls = @{ keyStore = (Join-Path $runtime 'demo-scope.p12'); password = [guid]::NewGuid().ToString('N') }
    & $keytool -genkeypair -alias demo-scope -keyalg RSA -keysize 2048 -validity 365 -dname 'CN=localhost' -ext 'SAN=DNS:localhost,IP:127.0.0.1' -storetype PKCS12 -keystore $tls.keyStore -storepass $tls.password -keypass $tls.password *> (Join-Path $runtime 'demo-scope-keytool.log')
    if ($LASTEXITCODE -ne 0) { throw 'Could not create local scope TLS certificate; see .runtime/demo-scope-keytool.log' }
    $tls | ConvertTo-Json | Set-Content -LiteralPath $tlsFile -Encoding utf8
}
$tls = Get-Content -LiteralPath $tlsFile -Raw | ConvertFrom-Json
$proxyScript = Join-Path $PSScriptRoot 'Demo-ScopeTlsProxy.cjs'
$proxyPidFile = Join-Path $runtime 'demo-scope-proxy.pid'
$proxyProcess = if (Test-Path -LiteralPath $proxyPidFile) { Get-CimInstance Win32_Process -Filter "ProcessId=$([int](Get-Content -LiteralPath $proxyPidFile))" }
if (!$proxyProcess -or !$proxyProcess.CommandLine.Contains($proxyScript)) {
    $proxy = Start-Process -FilePath (Get-Command node -ErrorAction Stop).Source -ArgumentList "`"$proxyScript`"" -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'demo-scope-proxy.log') -RedirectStandardError (Join-Path $runtime 'demo-scope-proxy.err.log')
    $proxy.Id | Set-Content -LiteralPath $proxyPidFile
    Start-Sleep -Milliseconds 500
    if ($proxy.HasExited) { throw 'Scope TLS endpoint could not start; see .runtime/demo-scope-proxy.err.log' }
}
$config = Join-Path $runtime 'patient-scopes.properties'
@(
    'patient.scope.enabled=true'
    'patient.scope.administrative.endpoint=https://127.0.0.1:8445/api/v1/auth/scopes'
    'patient.scope.administrative.client-id=${PATIENT_ADMIN_SCOPE_CLIENT_ID:patient-admin-scope}'
    'patient.scope.administrative.client-secret=${PATIENT_ADMIN_SCOPE_CLIENT_SECRET}'
    'patient.scope.clinical.endpoint=https://127.0.0.1:8445/api/v1/auth/scopes'
    'patient.scope.clinical.client-id=${PATIENT_CLINICAL_SCOPE_CLIENT_ID:patient-clinical-scope}'
    'patient.scope.clinical.client-secret=${PATIENT_CLINICAL_SCOPE_CLIENT_SECRET}'
    "patient.scope.administrative.trust-store=$($tls.keyStore.Replace('\', '/'))"
    "patient.scope.administrative.trust-store-password=$($tls.password)"
    "patient.scope.clinical.trust-store=$($tls.keyStore.Replace('\', '/'))"
    "patient.scope.clinical.trust-store-password=$($tls.password)"
) | Set-Content -LiteralPath $config -Encoding utf8
$lines = @(Get-Content -LiteralPath $envFile)
if (!($lines | Where-Object { $_ -match '^AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET=.+$' })) {
    $lines = @($lines | Where-Object { $_ -notmatch '^AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET=' })
    $lines += 'AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET=' + [guid]::NewGuid().ToString('N')
}
# Import from the .env resource itself so it also works when Auth starts in the IDE.
$demoImport = 'optional:file:' + $config.Replace('\', '/')
$existingImport = @($lines | Where-Object { $_ -match '^spring\.config\.import=' })
if ($existingImport.Count -gt 1) { throw 'Multiple spring.config.import entries in .env; resolve them before enabling demo access.' }
if ($existingImport.Count -eq 1) {
    $legacyImport = 'optional:file:' + (Join-Path $runtime 'demo-patient-access.properties').Replace('\', '/')
    $imports = @($existingImport[0].Substring('spring.config.import='.Length).Split(',') | Where-Object { $_ -ne $legacyImport })
    if ($demoImport -notin $imports) { $imports += $demoImport }
    $lines = @($lines | Where-Object { $_ -notmatch '^spring\.config\.import=' })
    $lines += 'spring.config.import=' + ($imports -join ',')
} else { $lines += 'spring.config.import=' + $demoImport }
$lines | Set-Content -LiteralPath $envFile -Encoding utf8
Write-Host 'Local Patient scope connection prepared. No patient assignments or business UUIDs were configured.'
Write-Host 'Restart Auth, Appointment and Patient Services, then refresh the browser.'
