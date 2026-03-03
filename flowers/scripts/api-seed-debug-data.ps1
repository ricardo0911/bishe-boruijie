param(
  [string]$BaseUrl = "http://localhost:8080/api/v1",
  [string]$HealthUrl = "",
  [int]$UserCount = 10,
  [int]$OrdersPerUser = 4
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

  return Invoke-RestMethod -Method $Method -Uri $Url -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8) -TimeoutSec 20
}

try {
  $health = Invoke-RestMethod -Method Get -Uri $resolvedHealthUrl -TimeoutSec 8
  if ($health.status -ne "UP") {
    throw "backend_not_ready"
  }
} catch {
  Write-Host "[ERR] backend is not reachable at $resolvedHealthUrl" -ForegroundColor Red
  Write-Host "Run backend first or pass -HealthUrl explicitly"
  exit 1
}

$productsResp = Invoke-Api -Method Get -Url "$BaseUrl/products"
if (-not $productsResp.success -or -not $productsResp.data -or $productsResp.data.Count -eq 0) {
  Write-Host "[ERR] no products found, please seed db first" -ForegroundColor Red
  exit 1
}

$products = @($productsResp.data)

$stats = [ordered]@{
  usersCreated = 0
  cartWrites = 0
  ordersCreated = 0
  paidOrders = 0
  cancelledOrders = 0
  refundedOrders = 0
  lockedOrders = 0
  orderFailures = 0
}

for ($u = 1; $u -le $UserCount; $u++) {
  $openid = "batch_debug_{0}_{1}" -f ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()), $u
  $name = "DebugUser{0}" -f $u

  try {
    $login = Invoke-Api -Method Post -Url "$BaseUrl/users/login" -Body @{ openid = $openid; name = $name }
    if (-not $login.success -or -not $login.data) {
      Write-Host "[WARN] login failed for user $u" -ForegroundColor Yellow
      continue
    }

    $uid = [int]$login.data.id
    $stats.usersCreated++

    $phone = "139{0}" -f (Get-Random -Minimum 10000000 -Maximum 99999999)
    $null = Invoke-Api -Method Put -Url "$BaseUrl/users/$uid" -Body @{
      name = "Debug User $u"
      phone = $phone
    }

    $cartProducts = $products | Get-Random -Count ([Math]::Min(2, $products.Count))
    foreach ($cp in $cartProducts) {
      $qty = Get-Random -Minimum 1 -Maximum 4
      $null = Invoke-Api -Method Post -Url "$BaseUrl/cart" -Body @{ userId = $uid; productId = [int]$cp.id; quantity = $qty }
      $stats.cartWrites++
    }

    for ($o = 1; $o -le $OrdersPerUser; $o++) {
      $product = $products | Get-Random
      $qty = Get-Random -Minimum 1 -Maximum 3

      try {
        $receiverPhone = "138{0}" -f (Get-Random -Minimum 10000000 -Maximum 99999999)
        $order = Invoke-Api -Method Post -Url "$BaseUrl/orders" -Body @{
          userId = $uid
          items = @(@{ productId = [int]$product.id; quantity = $qty })
          receiverName = "Receiver $uid"
          receiverPhone = $receiverPhone
          receiverAddress = "Debug Road No.$uid"
          remark = "seed script user=$uid order=$o"
        }

        if (-not $order.success -or -not $order.data) {
          $stats.orderFailures++
          continue
        }

        $stats.ordersCreated++
        $orderNo = [string]$order.data.orderNo

        $flow = ($u + $o) % 4
        switch ($flow) {
          0 {
            $stats.lockedOrders++
          }
          1 {
            $payResp = Invoke-Api -Method Post -Url "$BaseUrl/orders/$orderNo/pay" -Body @{ paymentChannel = "MOCK_WECHAT"; paymentNo = "MOCK_" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
            if ($payResp.success) {
              $stats.paidOrders++
            } else {
              $stats.orderFailures++
            }
          }
          2 {
            $cancelResp = Invoke-Api -Method Post -Url "$BaseUrl/orders/$orderNo/cancel" -Body @{ reason = "seed cancel" }
            if ($cancelResp.success) {
              $stats.cancelledOrders++
            } else {
              $stats.orderFailures++
            }
          }
          Default {
            $payResp = Invoke-Api -Method Post -Url "$BaseUrl/orders/$orderNo/pay" -Body @{ paymentChannel = "MOCK_WECHAT"; paymentNo = "MOCK_" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
            if ($payResp.success) {
              $cancelResp = Invoke-Api -Method Post -Url "$BaseUrl/orders/$orderNo/cancel" -Body @{ reason = "seed refund" }
              if ($cancelResp.success) {
                $stats.refundedOrders++
              } else {
                $stats.orderFailures++
              }
            } else {
              $stats.orderFailures++
            }
          }
        }
      } catch {
        $stats.orderFailures++
      }
    }
  } catch {
    Write-Host "[WARN] failed generating user $u" -ForegroundColor Yellow
  }
}

Write-Host ""
Write-Host "Seed complete." -ForegroundColor Green
Write-Host ("usersCreated   = {0}" -f $stats.usersCreated)
Write-Host ("cartWrites     = {0}" -f $stats.cartWrites)
Write-Host ("ordersCreated  = {0}" -f $stats.ordersCreated)
Write-Host ("paidOrders     = {0}" -f $stats.paidOrders)
Write-Host ("cancelledOrders= {0}" -f $stats.cancelledOrders)
Write-Host ("refundedOrders = {0}" -f $stats.refundedOrders)
Write-Host ("lockedOrders   = {0}" -f $stats.lockedOrders)
Write-Host ("orderFailures  = {0}" -f $stats.orderFailures)
Write-Host ""
Write-Host "Check DB tables: user_customer, cart_item, customer_order, order_item, stock_lock"
