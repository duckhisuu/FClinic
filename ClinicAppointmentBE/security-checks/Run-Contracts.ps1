param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$entries = [Collections.Generic.HashSet[string]]::new()
foreach ($module in @('auth-service', 'patient-service', 'appointment-service')) {
    $modulePath = Join-Path $backend "services/$module"
    $classes = Join-Path $modulePath 'target/classes'
    if (-not (Test-Path -LiteralPath $classes)) { throw "Build and test $module first." }
    $null = $entries.Add($classes)
    $report = Get-ChildItem -LiteralPath (Join-Path $modulePath 'target/surefire-reports') -Filter 'TEST-*.xml' | Select-Object -First 1
    if (-not $report) { throw "Run Maven tests for $module first." }
    [xml]$xml = Get-Content -LiteralPath $report.FullName
    $classpath = ($xml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    foreach ($entry in $classpath.Split([IO.Path]::PathSeparator)) { $null = $entries.Add($entry) }
}
$argsFile = Join-Path ([IO.Path]::GetTempPath()) ('fclinic-contracts-' + [guid]::NewGuid().ToString('N') + '.args')
try {
    $cp = ([string[]]$entries) -join [IO.Path]::PathSeparator
    $source = Join-Path $PSScriptRoot 'VerifyServiceContracts.java'
    [IO.File]::WriteAllLines($argsFile, @('-cp', ('"' + $cp.Replace('\', '/') + '"'), ('"' + $source.Replace('\', '/') + '"')))
    & (Join-Path $JavaHome 'bin/java.exe') "@$argsFile"
    if ($LASTEXITCODE -ne 0) { throw 'Cross-service contract verification failed.' }
} finally { Remove-Item -LiteralPath $argsFile -ErrorAction SilentlyContinue }
