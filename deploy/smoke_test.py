#!/usr/bin/env python3
import argparse
import json
import urllib.error
import urllib.parse
import urllib.request
import uuid


def load_credentials(path):
    credentials = {}
    with open(path, encoding="utf-8") as credentials_file:
        for line_number, raw_line in enumerate(credentials_file, 1):
            line = raw_line.rstrip("\r\n")
            if not line.strip():
                continue
            if "=" not in line:
                raise RuntimeError(f"invalid credentials file line {line_number}")
            key, value = line.split("=", 1)
            credentials[key] = value

    for key in ("PLATFORM_ADMIN_PASSWORD", "TENANT_ADMIN_PASSWORD"):
        if not credentials.get(key):
            raise RuntimeError(f"missing required credential: {key}")
    return credentials


def expect_success(envelope):
    if envelope.get("code") != 0:
        raise RuntimeError(envelope.get("message") or f"API code {envelope.get('code')}")
    return envelope.get("data")


def expect_http_success(status, envelope):
    if status < 200 or status >= 300:
        raise RuntimeError(f"HTTP status {status}")
    return expect_success(envelope)


def require_deleted(status, envelope):
    if status != 404 or envelope.get("code") != 40401:
        raise RuntimeError(
            f"expected HTTP 404 and API code 40401, got HTTP {status} and API code {envelope.get('code')}"
        )


def flatten_asset_types(nodes):
    result = []
    for node in nodes:
        result.append(node)
        result.extend(flatten_asset_types(node.get("children") or []))
    return result


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    def request(self, method, path, body=None, token=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            payload = json.loads(error.read().decode("utf-8"))
            return error.code, payload


def login(api, username, password):
    status, response = api.request(
        "POST", "/api/v1/auth/login", {"username": username, "password": password}
    )
    return expect_http_success(status, response)


def login_with_current_password(api, username, old_password, new_password):
    try:
        return login(api, username, new_password), False
    except RuntimeError:
        return login(api, username, old_password), True


def rotate_password(api, username, old_password, new_password):
    if new_password == old_password:
        raise RuntimeError("configured admin password must differ from the public seed")
    session, using_old = login_with_current_password(api, username, old_password, new_password)
    if using_old:
        status, changed = api.request(
            "POST",
            "/api/v1/auth/change-password",
            {"oldPassword": old_password, "newPassword": new_password},
            session["accessToken"],
        )
        expect_http_success(status, changed)
        session = login(api, username, new_password)
    status, rejected = api.request(
        "POST",
        "/api/v1/auth/login",
        {"username": username, "password": old_password},
    )
    if status != 401 or rejected.get("code") != 40100:
        raise RuntimeError("public seed login was not rejected with HTTP 401 and API code 40100")
    return session


def run_asset_crud(api, token, asset_type_id):
    asset_no = f"SMOKE-{uuid.uuid4()}"
    asset_id = None
    deleted = False
    try:
        status, create_response = api.request(
            "POST",
            "/api/v1/assets",
            {
                "assetTypeId": asset_type_id,
                "assetName": "Smoke Test Asset",
                "assetNo": asset_no,
                "attributes": {},
            },
            token,
        )
        asset = expect_http_success(status, create_response)
        asset_id = asset.get("id")
        if not asset_id or asset.get("assetNo") != asset_no or asset.get("assetName") != "Smoke Test Asset":
            raise RuntimeError("created asset does not match the request")

        status, get_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
        fetched = expect_http_success(status, get_response)
        if fetched.get("id") != asset_id or fetched.get("assetNo") != asset_no:
            raise RuntimeError("fetched asset does not match the created asset")

        query = urllib.parse.urlencode({"keyword": asset_no})
        status, list_response = api.request("GET", f"/api/v1/assets?{query}", token=token)
        page = expect_http_success(status, list_response)
        if not any(item.get("id") == asset_id for item in page.get("list") or []):
            raise RuntimeError("created asset is missing from the asset list")

        status, update_response = api.request(
            "PUT",
            f"/api/v1/assets/{asset_id}",
            {"assetName": "Smoke Test Asset Updated", "attributes": {}},
            token,
        )
        updated = expect_http_success(status, update_response)
        if (
            updated.get("id") != asset_id
            or updated.get("assetNo") != asset_no
            or updated.get("assetName") != "Smoke Test Asset Updated"
        ):
            raise RuntimeError("updated asset does not match the requested update")

        status, delete_response = api.request("DELETE", f"/api/v1/assets/{asset_id}", token=token)
        expect_http_success(status, delete_response)

        status, missing_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
        require_deleted(status, missing_response)
        deleted = True
    finally:
        if asset_id and not deleted:
            try:
                status, cleanup_response = api.request(
                    "DELETE", f"/api/v1/assets/{asset_id}", token=token
                )
                expect_http_success(status, cleanup_response)
            except Exception:
                pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1")
    parser.add_argument(
        "--credentials-file",
        default="/root/.config/itam/admin-credentials",
    )
    args = parser.parse_args()
    api = Api(args.base_url)
    credentials = load_credentials(args.credentials_file)

    status, health_response = api.request("GET", "/api/v1/health")
    health = expect_http_success(status, health_response)
    if health.get("pg") != "UP" or health.get("redis") != "UP":
        raise RuntimeError(f"unhealthy dependencies: {health}")

    platform = rotate_password(
        api,
        "platform_admin",
        "Platform@123",
        credentials["PLATFORM_ADMIN_PASSWORD"],
    )
    status, me_response = api.request("GET", "/api/v1/auth/me", token=platform["accessToken"])
    expect_http_success(status, me_response)

    tenant = rotate_password(
        api,
        "tenant_admin",
        "Tenant@123",
        credentials["TENANT_ADMIN_PASSWORD"],
    )
    token = tenant["accessToken"]
    status, tree_response = api.request("GET", "/api/v1/metadata/asset-types/tree", token=token)
    tree = expect_http_success(status, tree_response)
    nodes = [node for node in flatten_asset_types(tree) if node.get("enabled")]
    if not nodes:
        raise RuntimeError("no enabled asset type available")

    run_asset_crud(api, token, nodes[0]["id"])

    print("Smoke test passed: health, password rotation, login, and asset CRUD")


if __name__ == "__main__":
    main()
