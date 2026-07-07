$apiKey = "dev-api-key-change-me-in-production"
$url = "http://localhost:3000"

# 1. Register/Login to get Token
try {
    $resp = Invoke-WebRequest -Uri "$url/auth/register" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"username":"filtertest","email":"filter@test.com","password":"password123"}' -ErrorAction Stop
} catch {
    # If exists, ignore
}

$loginResp = Invoke-RestMethod -Uri "$url/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"identifier":"filtertest","password":"password123"}'
$token = $loginResp.token

# 2. Insert test events
$eventTypes = @("LOGIN","PURCHASE","PURCHASE","PAGE_VIEW","PURCHASE")
for ($i = 0; $i -lt $eventTypes.Length; $i++) {
    $type = $eventTypes[$i]
    Invoke-WebRequest -Uri "$url/events" -Method POST -Headers @{"X-API-Key"=$apiKey; "Content-Type"="application/json"} -Body "{`"eventType`":`"$type`",`"userId`":99,`"source`":`"filter-test`"}" -UseBasicParsing | Out-Null
}

# Wait a sec for processing
Start-Sleep -Seconds 2

# 3. Test filtering API via the frontend port (3000)
$filterResp = Invoke-RestMethod -Uri "$url/events?type=PURCHASE&page=0&size=10" -Method GET -Headers @{"Authorization"="Bearer $token"}
Write-Host "Filter by PURCHASE returned $($filterResp.content.Length) items. Total Elements: $($filterResp.totalElements)"
foreach ($ev in $filterResp.content) {
    Write-Host " - Event ID: $($ev.id), Type: $($ev.eventType)"
}
