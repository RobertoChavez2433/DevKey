# Pre-commit hook - quality gate orchestrator for DevKey
# Called by .githooks/pre-commit shell shim
#
# Sequence: assembleDebug -> detekt -> grep checks
# ANY failure in steps 1-3 = hard block (exit 1)

param()

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$checksDir = Join-Path $scriptDir "checks"

# Get staged .kt/.kts files (skip generated)
$stagedKotlin = @(git diff --cached --name-only --diff-filter=ACM | Where-Object {
    $_ -match '\.(kt|kts)$' -and
    $_ -notmatch 'build[/\\]generated'
})

if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to get staged files from git." -ForegroundColor Red
    exit 1
}

# Get staged Python files (E2E harness)
$stagedPython = @(git diff --cached --name-only --diff-filter=ACM | Where-Object { $_ -match '\.py$' })

# Get staged Gradle/TOML files
$stagedBuild = @(git diff --cached --name-only --diff-filter=ACM | Where-Object { $_ -match '\.(gradle|gradle\.kts|toml)$' })

# Check for sensitive files BEFORE the early exit — must never be bypassed
$stagedSecrets = @(git diff --cached --name-only --diff-filter=ACM | Where-Object {
    $_ -match '\.(env|pem|key)$' -or $_ -match 'credentials\.json|keystore\.jks|service-account'
})
if ($stagedSecrets.Count -gt 0) {
    foreach ($secret in $stagedSecrets) {
        Write-Host "BLOCKED: Sensitive file staged for commit: $secret - remove with 'git reset HEAD $secret'" -ForegroundColor Red
    }
    exit 1
}

$totalStaged = $stagedKotlin.Count + $stagedPython.Count + $stagedBuild.Count

if ($totalStaged -eq 0) {
    Write-Host "No staged Kotlin, Python, or build files - skipping pre-commit checks." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Pre-commit: $($stagedKotlin.Count) Kotlin, $($stagedPython.Count) Python, $($stagedBuild.Count) build file(s)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Step 1: assembleDebug (only if production Kotlin or build files changed)
$prodKotlin = @($stagedKotlin | Where-Object { $_ -match 'app/src/main/' })
if ($prodKotlin.Count -gt 0 -or $stagedBuild.Count -gt 0) {
    & pwsh -File (Join-Path $checksDir "run-build.ps1")
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# Step 2: detekt (only if any Kotlin files changed)
if ($stagedKotlin.Count -gt 0) {
    & pwsh -File (Join-Path $checksDir "run-detekt.ps1")
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# Step 3: grep checks (always runs if any staged files)
& pwsh -File (Join-Path $checksDir "grep-checks.ps1")
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "All pre-commit checks passed." -ForegroundColor Green
exit 0
