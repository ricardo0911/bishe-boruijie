#!/usr/bin/env python3
"""Run an API smoke test against the backend.

This script verifies the main order lifecycle:
1) list products
2) create order (LOCKED)
3) pay order (PAID)
4) cancel paid order to trigger refund rollback (REFUNDED)
5) confirm order appears in user order list
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass


class ApiError(RuntimeError):
    """Raised when API response is not successful."""


@dataclass
class ApiClient:
    base_url: str
    timeout: float

    def request(self, method: str, path: str, payload: dict | None = None):
        url = f"{self.base_url.rstrip('/')}/{path.lstrip('/')}"
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode("utf-8")

        req = urllib.request.Request(url=url, data=data, headers=headers, method=method)

        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise ApiError(f"HTTP {exc.code} {method} {path}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise ApiError(f"Network error {method} {path}: {exc.reason}") from exc

        try:
            body = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ApiError(f"Invalid JSON {method} {path}: {raw}") from exc

        if not isinstance(body, dict):
            raise ApiError(
                f"Unexpected response type {method} {path}: {type(body).__name__}"
            )

        if not body.get("success"):
            code = body.get("code", "UNKNOWN")
            msg = body.get("message", "request failed")
            raise ApiError(f"Business error {method} {path}: {code} {msg}")

        return body.get("data")


def pick_product(products: list[dict], product_id: int | None) -> dict:
    if not products:
        raise ApiError("No products found, cannot create smoke order")

    if product_id is None:
        return products[0]

    for product in products:
        if int(product.get("id", -1)) == product_id:
            return product

    raise ApiError(f"Product {product_id} not found in /products")


def require(condition: bool, message: str):
    if not condition:
        raise ApiError(message)


def main() -> int:
    parser = argparse.ArgumentParser(description="Flower backend API smoke test")
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:8080/api/v1",
        help="Backend API base URL",
    )
    parser.add_argument(
        "--timeout", type=float, default=10.0, help="HTTP timeout seconds"
    )
    parser.add_argument("--user-id", type=int, default=1, help="User ID for test order")
    parser.add_argument(
        "--product-id", type=int, default=None, help="Optional product ID override"
    )
    parser.add_argument("--quantity", type=int, default=1, help="Order quantity")
    parser.add_argument(
        "--payment-channel", default="WECHAT_PAY", help="Payment channel value"
    )
    parser.add_argument(
        "--skip-refund",
        action="store_true",
        help="Skip cancel/refund step after pay",
    )
    args = parser.parse_args()

    client = ApiClient(base_url=args.base_url, timeout=args.timeout)

    print("[1/6] GET /products")
    products = client.request("GET", "/products")
    require(isinstance(products, list), "Expected list from /products")
    selected = pick_product(products, args.product_id)
    product_id = int(selected["id"])
    product_title = selected.get("title", "")
    print(f"  Selected product: {product_id} {product_title}")

    print("[2/6] POST /orders")
    create_payload = {
        "userId": args.user_id,
        "items": [{"productId": product_id, "quantity": max(1, args.quantity)}],
        "receiverName": "Smoke Test",
        "receiverPhone": "13800009999",
        "receiverAddress": "Smoke Test Address",
        "packagingFee": 0,
        "deliveryFee": 0,
        "remark": "api smoke",
    }
    created = client.request("POST", "/orders", payload=create_payload)
    require(isinstance(created, dict), "Expected object from POST /orders")
    order_no = created.get("orderNo")
    require(
        isinstance(order_no, str) and order_no, "orderNo missing in create response"
    )
    print(f"  Created order: {order_no}")

    print("[3/6] GET /orders/{orderNo}")
    order_detail = client.request("GET", f"/orders/{order_no}")
    require(
        isinstance(order_detail, dict), "Expected object from GET /orders/{orderNo}"
    )
    status = order_detail.get("status")
    require(status == "LOCKED", f"Expected LOCKED after create, got {status}")

    print("[4/6] POST /orders/{orderNo}/pay")
    pay_payload = {
        "paymentChannel": args.payment_channel,
        "paymentNo": f"smoke_{int(time.time())}",
    }
    paid = client.request("POST", f"/orders/{order_no}/pay", payload=pay_payload)
    require(isinstance(paid, dict), "Expected object from POST /orders/{orderNo}/pay")
    paid_status = paid.get("status")
    require(paid_status == "PAID", f"Expected PAID after pay, got {paid_status}")

    print("[5/6] Verify paid order detail")
    paid_detail = client.request("GET", f"/orders/{order_no}")
    require(
        isinstance(paid_detail, dict),
        "Expected object from GET /orders/{orderNo} after pay",
    )
    require(
        paid_detail.get("status") == "PAID",
        f"Expected PAID detail status, got {paid_detail.get('status')}",
    )

    if not args.skip_refund:
        print("[6/6] POST /orders/{orderNo}/cancel (refund rollback)")
        cancel_payload = {"reason": "api smoke rollback"}
        cancelled = client.request(
            "POST", f"/orders/{order_no}/cancel", payload=cancel_payload
        )
        require(
            isinstance(cancelled, dict),
            "Expected object from POST /orders/{orderNo}/cancel",
        )
        cancel_status = cancelled.get("status")
        require(
            cancel_status == "REFUNDED",
            f"Expected REFUNDED after cancel paid order, got {cancel_status}",
        )
        final_detail = client.request("GET", f"/orders/{order_no}")
        require(
            isinstance(final_detail, dict),
            "Expected object from GET /orders/{orderNo} after cancel",
        )
        require(
            final_detail.get("status") == "REFUNDED",
            f"Expected REFUNDED detail status, got {final_detail.get('status')}",
        )
    else:
        print("[6/6] Skip refund step (--skip-refund)")

    print("[extra] GET /orders/user/{userId}/details?limit=10")
    order_list = client.request("GET", f"/orders/user/{args.user_id}/details?limit=10")
    require(isinstance(order_list, list), "Expected list from user order details")
    order_nos = {item.get("orderNo") for item in order_list if isinstance(item, dict)}
    require(order_no in order_nos, "Created order not found in user detail list")

    print()
    print("Smoke test passed.")
    print(f"orderNo={order_no}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
