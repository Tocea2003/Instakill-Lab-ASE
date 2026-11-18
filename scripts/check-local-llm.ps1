# Lab 7, steps 5 and 6: check that LM Studio serves the model and answers a chat completion.
# Usage: .\scripts\check-local-llm.ps1 [-BaseUrl http://localhost:12345] [-Model google/gemma-3-270m]
param(
    [string]$BaseUrl = "http://localhost:12345",
    [string]$Model = "google/gemma-3-270m"
)
$ErrorActionPreference = "Stop"

Write-Host "1) GET $BaseUrl/v1/models"
try {
    $models = Invoke-RestMethod -Method Get -Uri "$BaseUrl/v1/models" -TimeoutSec 5
} catch {
    Write-Host "LM Studio is not reachable at $BaseUrl. Start its server on port 12345 and retry." -ForegroundColor Red
    exit 1
}
$ids = @($models.data | ForEach-Object { $_.id })
$ids | ForEach-Object { Write-Host "   - $_" }
if ($ids -notcontains $Model) {
    Write-Host "Model '$Model' is not listed. Download it with: lms get $Model" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "2) POST $BaseUrl/v1/chat/completions"
$body = @{
    model       = $Model
    messages    = @(
        @{ role = "system"; content = "Always reply with the user's message, but rewritten in a more funny style." },
        @{ role = "user"; content = "Today I'm at my 7th Software Engineering lab." }
    )
    temperature = 0.7
    max_tokens  = 256
    stream      = $false
} | ConvertTo-Json -Depth 5
$response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/chat/completions" -ContentType "application/json" -Body $body -TimeoutSec 120
Write-Host $response.choices[0].message.content -ForegroundColor Green
Write-Host "OK: the model answered."
