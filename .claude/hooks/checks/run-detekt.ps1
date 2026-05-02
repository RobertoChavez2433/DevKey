# Step 2: detekt — static analysis for Kotlin
# Hard block on failure (exit 1)

param()

Write-Host "=== Running detekt ===" -ForegroundColor Cyan

$result = & ./gradlew detekt --quiet 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAILED: detekt" -ForegroundColor Red
    Write-Host $result -ForegroundColor Red
    exit 1
}

Write-Host "PASSED: detekt" -ForegroundColor Green
exit 0
