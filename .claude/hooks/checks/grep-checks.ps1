# Text pattern checks for DevKey domain rules
# Catches patterns that detekt/lint cannot detect via AST

param()

Write-Host "=== Running grep checks ===" -ForegroundColor Cyan

function Get-StagedContent {
    param([string]$FilePath)
    $raw = git show ":0:$FilePath" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return $raw
}

# Get staged .kt files (skip generated)
$stagedKotlin = @(git diff --cached --name-only --diff-filter=ACM | Where-Object {
    $_ -match '\.kt$' -and $_ -notmatch 'build[/\\]generated'
})
$failed = $false

# Check 1: Direct SharedPreferences reads outside SettingsRepository
# RULE: SettingsRepository is the single source of truth for settings
foreach ($file in $stagedKotlin) {
    if ($file -match "SettingsRepository\.kt$") { continue }
    $stagedLines = Get-StagedContent $file
    if ($null -eq $stagedLines) { continue }
    $lineNum = 0
    foreach ($l in $stagedLines) {
        $lineNum++
        if ($l -match "SharedPreferences|getSharedPreferences|PreferenceManager\.getDefault") {
            Write-Host "BLOCKED: Direct SharedPreferences access outside SettingsRepository: ${file}:${lineNum}" -ForegroundColor Red
            $failed = $true
        }
    }
}

# Check 2: Typed text in debug logs
# RULE: Debug logs may record structural state but never typed content
foreach ($file in $stagedKotlin) {
    $stagedLines = Get-StagedContent $file
    if ($null -eq $stagedLines) { continue }
    $lineNum = 0
    foreach ($l in $stagedLines) {
        $lineNum++
        # Flag DevKeyLogger calls that interpolate word/text/content variables
        if ($l -match 'DevKeyLogger\.' -and $l -match '\$\{?\s*(word|text|content|typed|composing|input)\b') {
            # Allow known safe patterns: word_length, word_count, text_length
            if ($l -match '(word_length|word_count|text_length|content_length|composing_length)') { continue }
            Write-Host "WARNING: Possible typed-text logging: ${file}:${lineNum}: $($l.Trim())" -ForegroundColor Yellow
        }
    }
}

# Check 3: JNI bridge class rename/move
# RULE: JNI-bound dictionary path locked to org.pocketworkstation.pckeyboard.BinaryDictionary
foreach ($file in $stagedKotlin) {
    if ($file -notmatch "BinaryDictionary|pckeyboard") { continue }
    $stagedLines = Get-StagedContent $file
    if ($null -eq $stagedLines) { continue }
    $content = $stagedLines -join "`n"
    if ($content -match "package\s+" -and $content -notmatch "org\.pocketworkstation\.pckeyboard") {
        if ($file -match "pckeyboard[/\\]BinaryDictionary") {
            Write-Host "BLOCKED: BinaryDictionary package must remain org.pocketworkstation.pckeyboard: ${file}" -ForegroundColor Red
            $failed = $true
        }
    }
}

# Check 4: Test-only code in production source sets
# RULE: MockInputConnection, TestImeState, TestSessionDependencies must stay in src/test/
foreach ($file in $stagedKotlin) {
    if ($file -notmatch 'app/src/main/') { continue }
    $stagedLines = Get-StagedContent $file
    if ($null -eq $stagedLines) { continue }
    $lineNum = 0
    foreach ($l in $stagedLines) {
        $lineNum++
        if ($l -match 'import\s+dev\.devkey\.keyboard\.testutil\.') {
            Write-Host "BLOCKED: Test utility imported in production code: ${file}:${lineNum}" -ForegroundColor Red
            $failed = $true
        }
    }
}

# Check 5: Block .env and credential files
$stagedSecrets = @(git diff --cached --name-only --diff-filter=ACM | Where-Object {
    $_ -match '\.(env|pem|key)$' -or $_ -match 'credentials\.json|keystore\.jks|service-account'
})
foreach ($file in $stagedSecrets) {
    Write-Host "BLOCKED: Sensitive file staged for commit: $file - remove with 'git reset HEAD $file'" -ForegroundColor Red
    $failed = $true
}

# Check 6: connectedAndroidTest in scripts or CI
$stagedAll = @(git diff --cached --name-only --diff-filter=ACM)
foreach ($file in $stagedAll) {
    if ($file -match '\.(yml|yaml|sh|ps1|py)$' -or $file -match 'Makefile|gradle') {
        $stagedLines = Get-StagedContent $file
        if ($null -eq $stagedLines) { continue }
        $lineNum = 0
        foreach ($l in $stagedLines) {
            $lineNum++
            if ($l -match 'connectedAndroidTest' -and $l -notmatch '#.*connectedAndroidTest') {
                Write-Host "WARNING: connectedAndroidTest disrupts installed IME state: ${file}:${lineNum}" -ForegroundColor Yellow
            }
        }
    }
}

if ($failed) {
    Write-Host "FAILED: grep checks found blocking issues." -ForegroundColor Red
    exit 1
}

Write-Host "PASSED: grep checks" -ForegroundColor Green
exit 0
