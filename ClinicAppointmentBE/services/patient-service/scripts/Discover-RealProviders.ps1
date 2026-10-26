# Read-only local discovery. Does not connect, launch providers or inspect credential values.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$moduleRoot=Split-Path $PSScriptRoot -Parent
$repoRoot=(Resolve-Path (Join-Path $moduleRoot '../../..')).Path
$expected='1924f0e22fd5de78a9b288d07cd26df262b75bbf8b8ad24def84f534cf96eb17'
$baseline=Get-Content (Join-Path $moduleRoot 'evidence/m5-c-verification.json') -Raw|ConvertFrom-Json
$drift=@()
$lines=@(foreach($file in $baseline.sourceManifest) {
    $path=Join-Path $moduleRoot $file.path
    $hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower()
    if($hash -ne $file.sha256) {$drift+=$file.path}
    $file.path+' '+$hash
})
$actual=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).ToLower()
$required=@('BOUNDARY','COLLECTION_URI','CLIENT_ID','CLIENT_SECRET','INVALID_CLIENT_ID','INVALID_CLIENT_SECRET','BINARY_PATH','DEPLOYMENT_EVIDENCE_PATH','VECTOR_PATH')
$environment=@(foreach($key in $required) {
    $name='PATIENT_REAL_PROVIDER_'+$key
    [ordered]@{name=$name;present=(-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name)))}
})
$providers=@(foreach($name in @('doctor-service','appointment-service')) {
    $target=Join-Path $repoRoot "ClinicAppointmentBE/services/$name/target"
    $jars=@(if(Test-Path -LiteralPath $target) {
        Get-ChildItem -LiteralPath $target -Filter '*.jar' -File | ForEach-Object {
            [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()}
        }
    })
    [ordered]@{service=$name;localExecutableArtifacts=$jars}
})
[ordered]@{
    timestamp=[DateTimeOffset]::UtcNow.ToString('o')
    baselineExpected=$expected
    currentManifestOfBaselineEntries=$actual
    baselineEntryChanges=$drift
    environmentPresenceOnly=$environment
    localProviderArtifacts=$providers
    outboundRequests=0
    classification='NOT EXECUTED'
    note='Artifact/source/env discovery is not provider runtime identity or compatibility evidence. No provider launched.'
}|ConvertTo-Json -Depth 7

