param([switch]$Build)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $root
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
# Use the existing environment mechanism. Never overwrite an existing configuration.
$envFile = Join-Path $root 'ClinicAppointmentBE/.env'
if (-not (Test-Path $envFile)) { Copy-Item 'ClinicAppointmentBE/.env.example' $envFile }
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
$settingsFile = Join-Path $runtime 'settings.json'
if (-not (Test-Path $settingsFile)) {
    $settings = @{}
    @('TLS_PASSWORD','PATIENT_AUTH_CLIENT_SECRET','PATIENT_ADMIN_SCOPE_CLIENT_SECRET','PATIENT_CLINICAL_SCOPE_CLIENT_SECRET',
      'PATIENT_DOCTOR_REFERENCE_CLIENT_SECRET','PATIENT_APPOINTMENT_REFERENCE_CLIENT_SECRET','APPOINTMENT_AUTH_CLIENT_SECRET',
      'APPOINTMENT_SCOPE_CLIENT_SECRET','APPOINTMENT_DOCTOR_CLIENT_SECRET','APPOINTMENT_RECIPIENT_CLIENT_SECRET',
      'APPOINTMENT_NOTIFICATION_REFERENCE_CLIENT_SECRET') | ForEach-Object { $settings[$_] = [guid]::NewGuid().ToString('N') }
    $settings | ConvertTo-Json | Set-Content $settingsFile
}
$settings = Get-Content $settingsFile -Raw | ConvertFrom-Json
$settings.PSObject.Properties | ForEach-Object { [Environment]::SetEnvironmentVariable($_.Name, [string]$_.Value, 'Process') }
$env:AUTH_INTROSPECTION_CLIENT_SECRET = $env:PATIENT_AUTH_CLIENT_SECRET
$env:AUTH_INTROSPECTION_CLIENT_ID = 'patient-service'
$env:PATIENT_AUTH_CLIENT_ID = 'patient-service'
$env:PATIENT_ADMIN_SCOPE_CLIENT_ID = 'patient-admin-scope'
$env:PATIENT_CLINICAL_SCOPE_CLIENT_ID = 'patient-clinical-scope'
$env:PATIENT_DOCTOR_REFERENCE_CLIENT_ID = 'patient-doctor-reference'
$env:PATIENT_APPOINTMENT_REFERENCE_CLIENT_ID = 'patient-appointment-reference'
$env:AUTH_OTP_EXPOSE_IN_RESPONSE = 'false'
$env:AUTH_OTP_MAIL_ENABLED = 'true'
$env:PATIENT_AUTH_ISSUER = 'http://localhost:8180/realms/fclinic'
$env:KEYCLOAK_ISSUER_URI = $env:PATIENT_AUTH_ISSUER
$env:PATIENT_AUTH_INTROSPECTION_URI = 'https://localhost:8085/api/v1/auth/introspect'
$env:PATIENT_ADMIN_SCOPE_URI = 'https://localhost:8085/api/v1/auth/scopes'
$env:PATIENT_CLINICAL_SCOPE_URI = $env:PATIENT_ADMIN_SCOPE_URI
$env:PATIENT_DOCTOR_REFERENCE_URI = 'http://localhost:8082/internal/v1/doctors'
$env:PATIENT_APPOINTMENT_REFERENCE_URI = 'http://localhost:8083/api/v2/appointments'
$env:APPOINTMENT_AUTH_INTROSPECTION_URI = $env:PATIENT_AUTH_INTROSPECTION_URI
$env:APPOINTMENT_SCOPE_URI = $env:PATIENT_ADMIN_SCOPE_URI
$env:APPOINTMENT_RECIPIENT_URL = 'https://localhost:8085/api/v1/auth/internal/appointment-recipient-accounts'
$env:APPOINTMENT_PATIENT_TOKEN_URI = 'http://localhost:8180/realms/fclinic/protocol/openid-connect/token'
$env:APPOINTMENT_PATIENT_TOKEN_CLIENT_ID = 'appointment-service'
# Read the existing imported realm's client secret; do not invent a parallel identity provider.
$realm = Get-Content 'ClinicAppointmentBE/infra/keycloak/fclinic-realm.json' -Raw | ConvertFrom-Json
$env:APPOINTMENT_PATIENT_TOKEN_CLIENT_SECRET = ($realm.clients | Where-Object clientId -eq 'appointment-service').secret
$env:PATIENT_UUID_DB_URL = "jdbc:postgresql://localhost:$($env:POSTGRES_PATIENT_PORT)/$($env:PATIENT_DB_NAME)"
$env:PATIENT_UUID_DB_USERNAME = $env:DB_USERNAME
$env:PATIENT_UUID_DB_PASSWORD = $env:DB_PASSWORD
$keyStore = Join-Path $runtime 'localhost.p12'
$trustStore = Join-Path $runtime 'trust.p12'
$certificate = Join-Path $runtime 'localhost.pem'
if (-not (Test-Path $keyStore)) {
    & keytool -genkeypair -alias localhost -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -storetype PKCS12 -keystore $keyStore -storepass $env:TLS_PASSWORD *> (Join-Path $runtime 'certificate.log')
    if ($LASTEXITCODE) { throw 'Certificate generation failed' }
    & keytool -exportcert -rfc -alias localhost -keystore $keyStore -storepass $env:TLS_PASSWORD -file $certificate >> (Join-Path $runtime 'certificate.log') 2>&1
    & keytool -importcert -noprompt -alias localhost -file $certificate -storetype PKCS12 -keystore $trustStore -storepass $env:TLS_PASSWORD >> (Join-Path $runtime 'certificate.log') 2>&1
    if ($LASTEXITCODE) { throw 'Trust store generation failed' }
}
$env:PATIENT_IDENTITY_TRUST_STORE = $trustStore
$env:PATIENT_IDENTITY_TRUST_STORE_PASSWORD = $env:TLS_PASSWORD
$env:PATIENT_SCOPE_ADMINISTRATIVE_TRUST_STORE = $trustStore
$env:PATIENT_SCOPE_ADMINISTRATIVE_TRUST_STORE_PASSWORD = $env:TLS_PASSWORD
$env:PATIENT_SCOPE_CLINICAL_TRUST_STORE = $trustStore
$env:PATIENT_SCOPE_CLINICAL_TRUST_STORE_PASSWORD = $env:TLS_PASSWORD
$env:APPOINTMENT_TRUST_STORE = $trustStore
$env:APPOINTMENT_TRUST_STORE_PASSWORD = $env:TLS_PASSWORD
$env:EUREKA_ENABLED = 'true'
$env:EUREKA_INSTANCE_HOSTNAME = 'localhost'
$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS = 'false'
& docker compose -f ClinicAppointmentBE/docker-compose.yml up -d --wait
if ($LASTEXITCODE) { throw 'Infrastructure health checks failed' }
# Schema initialization is allowed only for a verified empty current Patient database.
$tables = & docker exec fclinic-postgres-patient psql -U $env:DB_USERNAME -d $env:PATIENT_DB_NAME -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
if ($LASTEXITCODE) { throw 'Cannot inspect Patient schema' }
if ($tables.Trim() -eq '0') {
    Get-Content 'ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/uuid/V001__patient_uuid_empty.sql' -Raw | & docker exec -i fclinic-postgres-patient psql -v ON_ERROR_STOP=1 -U $env:DB_USERNAME -d $env:PATIENT_DB_NAME | Out-Null
    if ($LASTEXITCODE) { throw 'Patient schema initialization failed' }
} else {
    $type = & docker exec fclinic-postgres-patient psql -U $env:DB_USERNAME -d $env:PATIENT_DB_NAME -Atc "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='patients' AND column_name='id'"
    if ($type.Trim() -ne 'uuid') { throw 'Existing Patient schema requires an owner-approved legacy ID mapping; preserved without changes.' }
}
if ($Build) {
    & (Join-Path $PSScriptRoot 'Stop-FClinic.ps1')
    & mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp
    if ($LASTEXITCODE) { throw 'Backend verify failed' }
}
$processes = @()
function Start-ServiceJar($name, $module, $extra = @()) {
    $pidFile = Join-Path $runtime "$name.pid"
    if (Test-Path $pidFile) {
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$([int](Get-Content $pidFile))" -ErrorAction SilentlyContinue
        if ($existing -and $existing.Name -eq 'java.exe' -and $existing.CommandLine -like "*$root*" -and $existing.CommandLine -like "*$name-1.0.0-SNAPSHOT.jar*") {
            Write-Host "$name already running (PID $($existing.ProcessId))"; return
        }
    }
    $jar = Join-Path $root "ClinicAppointmentBE/$module/target/$name-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "Build required: $jar" }
    $args = @('-Xmx384m', "-Djavax.net.ssl.trustStore=$trustStore", "-Djavax.net.ssl.trustStorePassword=$env:TLS_PASSWORD", '-jar', $jar) + $extra
    $process = Start-Process java -ArgumentList $args -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$name.log") -RedirectStandardError (Join-Path $runtime "$name.err.log")
    $process.Id | Set-Content $pidFile
    Write-Host "Started $name (PID $($process.Id))"
}
function Wait-Health($url, [switch]$Tls) {
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $args = @('--silent', '--fail', '--max-time', '2')
        if ($Tls) { $args += @('--cacert', $certificate) }
        $body = & curl.exe @args $url
        if ($LASTEXITCODE -eq 0 -and $body -match '"status"\s*:\s*"UP"') { return }
        Start-Sleep -Milliseconds 1000
    }
    throw "Service health timeout: $url. Inspect .runtime logs."
}
Start-ServiceJar 'discovery-server' 'infra/discovery-server'
Wait-Health 'http://localhost:8761/actuator/health'
# Realm imports do not update existing realms. Add only the reviewed audience
# mapper, preserving users, credentials, grants and all other realm settings.
$adminLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/master/protocol/openid-connect/token' -Body @{
    grant_type='password';client_id='admin-cli';username=$env:KEYCLOAK_ADMIN_USER;password=$env:KEYCLOAK_ADMIN_PASSWORD
}
$adminHeaders=@{Authorization="Bearer $($adminLogin.access_token)"}
$scopes=Invoke-RestMethod -Headers $adminHeaders -Uri 'http://localhost:8180/admin/realms/fclinic/client-scopes'
$claimsScope=@($scopes | Where-Object name -eq 'fclinic-claims')
if($claimsScope.Count -ne 1){throw 'Exactly one existing fclinic-claims scope is required'}
$mapperUri="http://localhost:8180/admin/realms/fclinic/client-scopes/$($claimsScope[0].id)/protocol-mappers/models"
$mappers=Invoke-RestMethod -Headers $adminHeaders -Uri $mapperUri
if(-not @($mappers | Where-Object name -eq 'fclinic-audience-notification-service').Count){
    $mapper=@{name='fclinic-audience-notification-service';protocol='openid-connect';protocolMapper='oidc-audience-mapper';consentRequired=$false;config=@{
        'included.custom.audience'='notification-service';'id.token.claim'='false';'access.token.claim'='true';'introspection.token.claim'='true'
    }} | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Method Post -Headers $adminHeaders -Uri $mapperUri -ContentType 'application/json' -Body $mapper | Out-Null
}
$adminLogin=$null; $adminHeaders=$null
# Optional finite, time-limited assignments are external configuration, never source credentials.
$assignments = Join-Path $runtime 'assignments.json'
if (Test-Path $assignments) { $env:SPRING_APPLICATION_JSON = Get-Content $assignments -Raw }
Start-ServiceJar 'auth-service' 'services/auth-service' @('--server.ssl.enabled=true', "--server.ssl.key-store=$keyStore", "--server.ssl.key-store-password=$env:TLS_PASSWORD", '--server.ssl.key-store-type=PKCS12', '--eureka.instance.non-secure-port-enabled=false', '--eureka.instance.secure-port-enabled=true', '--eureka.instance.secure-port=8085', '--eureka.instance.hostname=localhost', '--eureka.instance.prefer-ip-address=false')
Remove-Item Env:SPRING_APPLICATION_JSON -ErrorAction SilentlyContinue
Wait-Health 'https://localhost:8085/actuator/health' -Tls
Start-ServiceJar 'doctor-service' 'services/doctor-service'
Wait-Health 'http://localhost:8082/actuator/health'
Start-ServiceJar 'patient-service' 'services/patient-service' @('--spring.profiles.active=service-integration,doctor-reference')
Wait-Health 'http://localhost:8081/actuator/health'
Start-ServiceJar 'appointment-service' 'services/appointment-service'
Wait-Health 'http://localhost:8083/actuator/health'
Start-ServiceJar 'notification-service' 'services/notification-service'
Wait-Health 'http://localhost:8084/actuator/health'
Start-ServiceJar 'api-gateway' 'infra/api-gateway'
Wait-Health 'http://localhost:8080/actuator/health'
$env:NODE_EXTRA_CA_CERTS = $certificate
$env:VITE_AUTH_PROXY_TARGET = 'https://localhost:8085'
Write-Host 'Backend started. Frontend: cd ClinicAppointmentFE; npm run dev -- --host 127.0.0.1'
Write-Host 'Open http://127.0.0.1:5173 (this shell retains TLS trust for Vite).'
