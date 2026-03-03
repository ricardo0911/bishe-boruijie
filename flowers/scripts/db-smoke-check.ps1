param(
  [string]$BaseUrl = "http://localhost:18080/api/v1",
  [string]$HealthUrl = ""
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

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Url,
    [object]$Body = $null
  )

  if ($null -eq $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Url -TimeoutSec 20
  }

  return Invoke-RestMethod -Method $Method -Uri $Url -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 6) -TimeoutSec 20
}

try {
  $health = Invoke-RestMethod -Method Get -Uri $resolvedHealthUrl -TimeoutSec 8
  if ($health.status -ne "UP") {
    throw "backend not ready"
  }
} catch {
  Write-Host "[ERR] backend not available at $resolvedHealthUrl : $_" -ForegroundColor Red
  Write-Host "Please run backend first or pass -HealthUrl explicitly"
  exit 1
}

$openid = "debug_" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$login = Invoke-Api -Method Post -Url "$BaseUrl/users/login" -Body @{ openid = $openid; name = "Debug User" }
$uid = [int]$login.data.id
Write-Host "[OK] user_customer -> id=$uid"

$products = Invoke-Api -Method Get -Url "$BaseUrl/products"
if (-not $products.data -or $products.data.Count -eq 0) {
  Write-Host "[ERR] product table is empty, please run seed.sql" -ForegroundColor Red
  exit 1
}

$productId = [int]$products.data[0].id
Write-Host "[OK] product -> pick id=$productId"

$null = Invoke-Api -Method Post -Url "$BaseUrl/cart" -Body @{ userId = $uid; productId = $productId; quantity = 2 }
$cart = Invoke-Api -Method Get -Url "$BaseUrl/cart/$uid"
Write-Host "[OK] cart_item -> items=$($cart.data.Count)"

$mobile = "138" + (Get-Random -Minimum 10000000 -Maximum 99999999)
$order = Invoke-Api -Method Post -Url "$BaseUrl/orders" -Body @{
  userId = $uid
  items = @(@{ productId = $productId; quantity = 1 })
  receiverName = "Debug Receiver"
  receiverPhone = $mobile
  receiverAddress = "Debug Street 88"
  remark = "smoke test"
}

$orderNo = $order.data.orderNo
Write-Host "[OK] customer_order + order_item + stock_lock -> orderNo=$orderNo"

$detail1 = Invoke-Api -Method Get -Url "$BaseUrl/orders/$orderNo"
Write-Host "[OK] order status(before pay)=$($detail1.data.status)"

$null = Invoke-Api -Method Post -Url "$BaseUrl/orders/$orderNo/pay" -Body @{ paymentChannel = "MOCK_WECHAT"; paymentNo = "MOCK_" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
$detail2 = Invoke-Api -Method Get -Url "$BaseUrl/orders/$orderNo"
Write-Host "[OK] order status(after pay)=$($detail2.data.status)"

$orders = Invoke-Api -Method Get -Url "$BaseUrl/orders/user/$uid/details?limit=20"
Write-Host "[OK] user order detail count=$($orders.data.Count)"

$null = Invoke-Api -Method Put -Url "$BaseUrl/users/$uid" -Body @{ name = "Debug User Updated"; phone = "139" + (Get-Random -Minimum 10000000 -Maximum 99999999) }
$u2 = Invoke-Api -Method Get -Url "$BaseUrl/users/$uid"
Write-Host "[OK] user update -> name=$($u2.data.name)"

Write-Host ""
Write-Host "Done: end-to-end data has been written to database tables." -ForegroundColor Green
Write-Host "Check tables in DB: user_customer, cart_item, customer_order, order_item, stock_lock"
