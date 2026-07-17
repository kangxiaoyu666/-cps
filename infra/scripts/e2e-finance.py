#!/usr/bin/env python3
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = os.environ.get("API_BASE_URL", "http://127.0.0.1:8080/api/v1")
MOCK = os.environ.get("AFFILIATE_MOCK_URL", "http://127.0.0.1:8090")
ADMIN_PASSWORD = os.environ.get("DEV_ADMIN_PASSWORD", "DemoAdmin123!")


def request(url, method="GET", body=None, headers=None, opener=None, expected=(200,)):
    payload = None if body is None else json.dumps(body).encode("utf-8")
    final_headers = {"Accept": "application/json"}
    if payload is not None:
        final_headers["Content-Type"] = "application/json"
    final_headers.update(headers or {})
    req = urllib.request.Request(url, data=payload, headers=final_headers, method=method)
    client = opener or urllib.request.build_opener()
    try:
        with client.open(req, timeout=30) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8")
    if status not in expected:
        raise AssertionError(f"{method} {url} returned HTTP {status}: {raw}")
    return status, json.loads(raw) if raw else {}


def data(response):
    if response.get("code") != "SUCCESS":
        raise AssertionError(f"API returned failure: {response}")
    return response["data"]


def assert_wallet(token, available, frozen, debt, stage):
    _, response = request(
        f"{API}/mini/wallet",
        headers={"Authorization": f"Bearer {token}"},
    )
    wallet = data(response)
    actual = (wallet["availableCent"], wallet["frozenCent"], wallet["debtCent"])
    expected = (available, frozen, debt)
    if actual != expected:
        raise AssertionError(f"{stage} wallet expected {expected}, got {actual}: {wallet}")
    print(f"PASS {stage}: available={available}, frozen={frozen}, debt={debt}")
    return wallet


def main():
    _, health = request(f"{MOCK}/health")
    if health.get("status") != "UP":
        raise AssertionError(f"affiliate mock unhealthy: {health}")

    cookies = http.cookiejar.CookieJar()
    admin = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
    _, login = request(
        f"{API}/admin/auth/login",
        method="POST",
        body={"tenantCode": "demo", "username": "admin", "password": ADMIN_PASSWORD},
        opener=admin,
    )
    if data(login)["role"] != "TENANT_ADMIN":
        raise AssertionError(f"unexpected admin role: {login}")

    _, csrf_response = request(f"{API}/admin/auth/csrf", opener=admin)
    csrf_header = data(csrf_response)["headerName"]
    csrf_token = next((cookie.value for cookie in cookies if cookie.name == "XSRF-TOKEN"), None)
    if not csrf_token:
        raise AssertionError("XSRF-TOKEN cookie was not issued")
    admin_headers = {csrf_header: urllib.parse.unquote(csrf_token)}

    def admin_request(path, method="GET", body=None, expected=(200,)):
        return request(
            f"{API}{path}", method=method, body=body, headers=admin_headers,
            opener=admin, expected=expected,
        )

    run_id = str(int(time.time() * 1000))
    _, mini_login = request(
        f"{API}/mini/auth/login",
        method="POST",
        body={"code": f"dev-e2e-{run_id}", "scene": None},
        headers={"X-Tenant-Code": "demo"},
    )
    token = data(mini_login)["token"]
    mini_headers = {"Authorization": f"Bearer {token}"}

    def mini_request(path, method="GET", body=None, headers=None, expected=(200,)):
        combined = dict(mini_headers)
        combined.update(headers or {})
        return request(f"{API}{path}", method=method, body=body, headers=combined, expected=expected)

    _, promotion = mini_request(
        "/mini/promotion-links",
        method="POST",
        body={"platform": "MEITUAN", "activityCode": None},
    )
    promotion_url = data(promotion)["url"]
    sid = urllib.parse.parse_qs(urllib.parse.urlparse(promotion_url).query).get("sid", [None])[0]
    if not sid:
        raise AssertionError(f"promotion link has no sid: {promotion_url}")
    print(f"PASS promotion attribution: sid={sid}")

    order_one = f"E2E-{run_id}-ONE"
    request(
        f"{MOCK}/mock/orders",
        method="POST",
        body={
            "platform": "MEITUAN", "orderId": order_one, "status": "SETTLED",
            "sid": sid, "amount": "100.00", "commission": "20.00",
            "refundCommission": "0.00",
        },
    )
    _, sync_one = admin_request("/admin/orders/sync/MEITUAN", method="POST")
    if data(sync_one)["successCount"] < 1:
        raise AssertionError(f"settled order was not synchronized: {sync_one}")
    assert_wallet(token, 1000, 0, 0, "settled commission")

    _, orders = admin_request("/admin/orders?pageSize=100")
    matching = [item for item in data(orders)["items"] if item["external_order_id"] == order_one]
    if len(matching) != 1:
        raise AssertionError(f"expected one persisted order, got {matching}")
    order_id = matching[0]["id"]

    idempotency_key = f"wd-{run_id}"
    _, withdrawal_one = mini_request(
        "/mini/withdrawals",
        method="POST",
        body={"amountCent": 1000},
        headers={"Idempotency-Key": idempotency_key},
    )
    first = data(withdrawal_one)
    _, withdrawal_two = mini_request(
        "/mini/withdrawals",
        method="POST",
        body={"amountCent": 1000},
        headers={"Idempotency-Key": idempotency_key},
    )
    duplicate = data(withdrawal_two)
    if first != duplicate:
        raise AssertionError(f"withdrawal idempotency failed: {first} != {duplicate}")
    print(f"PASS withdrawal idempotency: id={first['id']}")
    assert_wallet(token, 0, 1000, 0, "withdrawal frozen once")

    withdrawal_id = first["id"]
    admin_request(f"/admin/withdrawals/{withdrawal_id}/approve", method="POST")
    paid_body = {
        "channel": "BANK", "reference": f"LOCAL-{run_id}",
        "proofUrl": "http://localhost/e2e-proof",
    }
    admin_request(f"/admin/withdrawals/{withdrawal_id}/paid", method="POST", body=paid_body)
    assert_wallet(token, 0, 0, 0, "withdrawal paid once")

    status, paid_again = admin_request(
        f"/admin/withdrawals/{withdrawal_id}/paid",
        method="POST", body=paid_body, expected=(400,),
    )
    if status != 400 or paid_again.get("code") != "INVALID_WITHDRAWAL_TRANSITION":
        raise AssertionError(f"duplicate payment was not rejected correctly: {paid_again}")
    print("PASS duplicate payment rejected")
    assert_wallet(token, 0, 0, 0, "duplicate payment no balance change")

    admin_request("/admin/orders/sync/MEITUAN", method="POST")
    admin_request(f"/admin/orders/{order_id}/retry", method="POST")
    assert_wallet(token, 0, 0, 0, "duplicate settlement no balance change")

    request(
        f"{MOCK}/mock/orders",
        method="POST",
        body={
            "platform": "MEITUAN", "orderId": order_one, "status": "REFUNDED",
            "sid": sid, "amount": "100.00", "commission": "20.00",
            "refundCommission": "20.00",
        },
    )
    admin_request("/admin/orders/sync/MEITUAN", method="POST")
    assert_wallet(token, 0, 0, 1000, "refund creates debt")
    admin_request("/admin/orders/sync/MEITUAN", method="POST")
    assert_wallet(token, 0, 0, 1000, "duplicate refund no extra debt")

    _, blocked = mini_request(
        "/mini/withdrawals",
        method="POST",
        body={"amountCent": 1000},
        headers={"Idempotency-Key": f"wd-debt-{run_id}"},
        expected=(400,),
    )
    if blocked.get("code") != "WITHDRAWAL_BLOCKED_BY_DEBT":
        raise AssertionError(f"debt did not block withdrawal: {blocked}")
    print("PASS debt blocks withdrawal")

    order_two = f"E2E-{run_id}-TWO"
    request(
        f"{MOCK}/mock/orders",
        method="POST",
        body={
            "platform": "MEITUAN", "orderId": order_two, "status": "SETTLED",
            "sid": sid, "amount": "150.00", "commission": "30.00",
            "refundCommission": "0.00",
        },
    )
    admin_request("/admin/orders/sync/MEITUAN", method="POST")
    assert_wallet(token, 500, 0, 0, "later commission repays debt first")

    _, entries = mini_request("/mini/wallet/entries?pageSize=100")
    business_types = [entry["business_type"] for entry in data(entries)]
    required = {"COMMISSION", "WITHDRAWAL_FREEZE", "WITHDRAWAL_PAID", "COMMISSION_REVERSAL"}
    missing = required.difference(business_types)
    if missing:
        raise AssertionError(f"wallet ledger is missing entries: {sorted(missing)}")
    print(f"PASS immutable wallet ledger: {sorted(required)}")
    print(json.dumps({
        "result": "PASS", "runId": run_id, "userSid": sid,
        "firstOrder": order_one, "secondOrder": order_two,
        "withdrawalId": withdrawal_id,
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise
