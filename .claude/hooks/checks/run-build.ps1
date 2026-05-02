# Step 1: assembleDebug — verify production code compiles
# Hard block on failure (exit 1)

param()

Write-Host "=== Running assembleDebug ===" -ForegroundColor Cyan

$result = & ./gradlew assembleDebug --quiet 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAILED: assembleDebug" -ForegroundColor Red
    Write-Host $result -ForegroundColor Red
    exit 1
}

Write-Host "PASSED: assembleDebug" -ForegroundColor Green
exit 0
