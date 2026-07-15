#!/usr/bin/env python3
import argparse
import json
import time
import urllib.error
import urllib.request


def expect_success(envelope):
    if envelope.get("code") != 0:
        raise RuntimeError(envelope.get("message") or f"API code {envelope.get('code')}")
    return envelope.get("data")


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
    _, response = api.request("POST", "/api/v1/auth/login", {"username": username, "password": password})
    return expect_success(response)


def login_with_current_password(api, username, old_password, new_password):
    try:
        return login(api, username, new_password), False
    except RuntimeError:
        return login(api, username, old_password), True


def rotate_password(api, username, old_password, new_password):
    session, using_old = login_with_current_password(api, username, old_password, new_password)
    if using_old:
        _, changed = api.request(
            "POST",
            "/api/v1/auth/change-password",
            {"oldPassword": old_password, "newPassword": new_password},
            session["accessToken"],
        )
        expect_success(changed)
        session = login(api, username, new_password)
    return session


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1")
    parser.add_argument("--platform-new-password", required=True)
    parser.add_argument("--tenant-new-password", required=True)
    args = parser.parse_args()
    api = Api(args.base_url)

    _, health_response = api.request("GET", "/api/v1/health")
    health = expect_success(health_response)
    if health.get("pg") != "UP" or health.get("redis") != "UP":
        raise RuntimeError(f"unhealthy dependencies: {health}")

    platform = rotate_password(api, "platform_admin", "Platform@123", args.platform_new_password)
    _, me_response = api.request("GET", "/api/v1/auth/me", token=platform["accessToken"])
    expect_success(me_response)

    tenant = rotate_password(api, "tenant_admin", "Tenant@123", args.tenant_new_password)
    token = tenant["accessToken"]
    _, tree_response = api.request("GET", "/api/v1/metadata/asset-types/tree", token=token)
    nodes = [node for node in flatten_asset_types(expect_success(tree_response)) if node.get("enabled")]
    if not nodes:
        raise RuntimeError("no enabled asset type available")

    asset_no = f"SMOKE-{int(time.time())}"
    _, create_response = api.request(
        "POST",
        "/api/v1/assets",
        {"assetTypeId": nodes[0]["id"], "assetName": "Smoke Test Asset", "assetNo": asset_no, "attributes": {}},
        token,
    )
    asset = expect_success(create_response)
    asset_id = asset["id"]

    _, get_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
    expect_success(get_response)
    _, list_response = api.request("GET", f"/api/v1/assets?keyword={asset_no}", token=token)
    expect_success(list_response)
    _, update_response = api.request(
        "PUT",
        f"/api/v1/assets/{asset_id}",
        {"assetName": "Smoke Test Asset Updated", "attributes": {}},
        token,
    )
    updated = expect_success(update_response)
    if updated["assetName"] != "Smoke Test Asset Updated":
        raise RuntimeError("asset update was not persisted")

    _, delete_response = api.request("DELETE", f"/api/v1/assets/{asset_id}", token=token)
    expect_success(delete_response)
    status, missing_response = api.request("GET", f"/api/v1/assets/{asset_id}", token=token)
    if status != 404 and missing_response.get("code") == 0:
        raise RuntimeError("deleted asset remains accessible")

    print("Smoke test passed: health, password rotation, login, and asset CRUD")


if __name__ == "__main__":
    main()
