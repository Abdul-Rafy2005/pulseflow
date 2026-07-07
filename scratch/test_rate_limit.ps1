# Rate limit E2E test: fires 120 events in a fast loop
# Expects: ~100 x 202 (accepted), ~20 x 429 (rate limited)
$apiKey = "dev-api-key-change-me-in-production"
$url = "http://localhost:8082/events"
$eventTypes = @("PAGE_VIEW","BUTTON_CLICK","LOGIN","LOGOUT","PURCHASE","SEARCH")

$count202 = 0
$count429 = 0
$countOther = 0

for ($i = 1; $i -le 120; $i++) {
    $type = $eventTypes[$i % $eventTypes.Length]
    $body = "{`"eventType`":`"$type`",`"userId`":$i,`"source`":`"rate-limit-test`"}"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method POST `
            -Headers @{ "X-API-Key" = $apiKey; "Content-Type" = "application/json" } `
            -Body $body -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 202) { $count202++ }
        else { $countOther++ }
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        if ($status -eq 429) { $count429++ }
        else { $countOther++; Write-Host "Event $i unexpected status: $status" }
    }
}

Write-Host ""
Write-Host "=== Rate Limit E2E Results ==="
Write-Host "  202 Accepted    : $count202"
Write-Host "  429 Rate Limited: $count429"
Write-Host "  Other           : $countOther"
Write-Host "  Total           : $($count202 + $count429 + $countOther)"
