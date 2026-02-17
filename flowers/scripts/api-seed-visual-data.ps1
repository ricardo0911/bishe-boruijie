param(
  [string]$BaseUrl = "http://localhost:8080/api/v1",
  [string]$HealthUrl = "",
  [int]$OrderCount = 36
)

$ErrorActionPreference = "Stop"

function Resolve-HealthUrl {
  param(
    [string]$ApiBase,
    [string]$CustomHealthUrl
  )

  if (-not [string]::IsNullOrWhiteSpace($CustomHealthUrl)) {
    return $CustomHealthUrl.Trim()
  }

  $normalized = $ApiBase.Trim().TrimEnd("/")
  $origin = $normalized -replace "/api(/v[0-9]+)?$", ""
  return "$origin/actuator/health"
}

$resolvedHealthUrl = Resolve-HealthUrl -ApiBase $BaseUrl -CustomHealthUrl $HealthUrl

try {
  $health = Invoke-RestMethod -Method Get -Uri $resolvedHealthUrl -TimeoutSec 8
  if ($health.status -ne "UP") {
    throw "backend not ready"
  }
} catch {
  Write-Host "[ERR] backend not available at $resolvedHealthUrl" -ForegroundColor Red
  Write-Host "Please run backend first or pass -HealthUrl explicitly"
  exit 1
}

try {
  $url = "$BaseUrl/debug/seed-visual-data?orderCount=$OrderCount"
  $res = Invoke-RestMethod -Method Post -Uri $url -TimeoutSec 60
  if (-not $res.success) {
    Write-Host "[ERR] seed failed: $($res.message)" -ForegroundColor Red
    exit 1
  }

  Write-Host "Seed visual data completed." -ForegroundColor Green
  $data = $res.data
  $data.PSObject.Properties | ForEach-Object {
    Write-Host ("{0} = {1}" -f $_.Name, $_.Value)
  }
} catch {
  Write-Host "[ERR] request failed: $_" -ForegroundColor Red
  exit 1
}
