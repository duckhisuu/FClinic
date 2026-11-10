param([string]$RepositoryRoot)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
if ($RepositoryRoot) { $repo = (Resolve-Path -LiteralPath $RepositoryRoot).Path }
$frontend = Join-Path $repo 'ClinicAppointmentFE'
$target = Join-Path $frontend 'src/appointment/__tests__/codex-review-logic.test.jsx'
if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite $target" }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'FrontendLogic.probe.jsx') -Destination $target
Push-Location $frontend
try {
    & npm.cmd test -- src/appointment/__tests__/codex-review-logic.test.jsx
    if ($LASTEXITCODE -ne 0) { throw 'Frontend audit probes failed; inspect whether the expected failures changed.' }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $target
}
