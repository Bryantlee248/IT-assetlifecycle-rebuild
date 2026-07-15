import tempfile
import unittest
import urllib.parse
import uuid

from deploy.smoke_test import (
    expect_http_success,
    expect_success,
    flatten_asset_types,
    load_credentials,
    require_deleted,
    rotate_password,
    run_asset_crud,
)


class ScriptedApi:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def request(self, method, path, body=None, token=None):
        self.calls.append((method, path, body, token))
        response = self.responses.pop(0)
        if callable(response):
            return response(method, path, body, token)
        return response


class SmokeTestHelpersTest(unittest.TestCase):
    def asset_data(self, asset_no, name="Smoke Test Asset", asset_id="asset-1"):
        return {
            "id": asset_id,
            "assetNo": asset_no,
            "assetName": name,
            "assetKind": None,
            "assetTypeId": "type-1",
            "assetTypeName": "Server",
            "lifecycleStatus": "planned",
            "status": "active",
            "sourceType": "manual",
            "ownerUserId": None,
            "ownerOrgId": None,
            "locationId": None,
            "costCenterId": None,
            "responsibleUserId": None,
            "createdAt": "2026-07-15T00:00:00Z",
            "updatedAt": "2026-07-15T00:00:00Z",
            "fields": {},
        }

    def test_expect_success_returns_data(self):
        self.assertEqual(expect_success({"code": 0, "data": {"id": "1"}}), {"id": "1"})

    def test_expect_success_rejects_error_envelope(self):
        with self.assertRaisesRegex(RuntimeError, "failed"):
            expect_success({"code": 40000, "message": "failed", "data": None})

    def test_flatten_asset_types_walks_children(self):
        nodes = [{"id": "a", "enabled": True, "children": [{"id": "b", "enabled": True, "children": []}]}]
        self.assertEqual([node["id"] for node in flatten_asset_types(nodes)], ["a", "b"])

    def test_load_credentials_splits_values_once(self):
        with tempfile.TemporaryDirectory() as directory:
            path = f"{directory}/admin-credentials"
            with open(path, "w", encoding="utf-8") as credentials_file:
                credentials_file.write(
                    "PLATFORM_ADMIN_PASSWORD=platform=secret\n"
                    "\n"
                    "TENANT_ADMIN_PASSWORD=tenant-secret\n"
                )

            credentials = load_credentials(path)

        self.assertEqual(credentials["PLATFORM_ADMIN_PASSWORD"], "platform=secret")
        self.assertEqual(credentials["TENANT_ADMIN_PASSWORD"], "tenant-secret")

    def test_load_credentials_reports_missing_key_without_exposing_values(self):
        with tempfile.TemporaryDirectory() as directory:
            path = f"{directory}/admin-credentials"
            with open(path, "w", encoding="utf-8") as credentials_file:
                credentials_file.write("PLATFORM_ADMIN_PASSWORD=do-not-expose\n")

            with self.assertRaises(RuntimeError) as context:
                load_credentials(path)

        self.assertIn("TENANT_ADMIN_PASSWORD", str(context.exception))
        self.assertNotIn("do-not-expose", str(context.exception))

    def test_expect_http_success_rejects_non_2xx_with_success_envelope(self):
        with self.assertRaisesRegex(RuntimeError, "500"):
            expect_http_success(500, {"code": 0, "message": "ok", "data": {"id": "1"}})

    def test_rotate_password_rejects_configured_seed_before_login(self):
        api = ScriptedApi([])

        with self.assertRaisesRegex(RuntimeError, "seed"):
            rotate_password(api, "admin", "Seed@123", "Seed@123")

        self.assertEqual(api.calls, [])

    def test_rotate_password_verifies_seed_login_is_rejected(self):
        session = {"accessToken": "new-token", "refreshToken": "refresh-token"}
        api = ScriptedApi(
            [
                (200, {"code": 0, "message": "ok", "data": session}),
                (401, {"code": 40100, "message": "invalid credentials", "data": None}),
            ]
        )

        self.assertEqual(rotate_password(api, "admin", "Seed@123", "new-secret"), session)
        self.assertEqual([call[2]["password"] for call in api.calls], ["new-secret", "Seed@123"])

    def test_rotate_password_changes_seed_on_first_run(self):
        old_session = {"accessToken": "old-token", "refreshToken": "old-refresh"}
        new_session = {"accessToken": "new-token", "refreshToken": "new-refresh"}
        api = ScriptedApi(
            [
                (401, {"code": 40100, "message": "invalid credentials", "data": None}),
                (200, {"code": 0, "message": "ok", "data": old_session}),
                (200, {"code": 0, "message": "ok", "data": None}),
                (200, {"code": 0, "message": "ok", "data": new_session}),
                (401, {"code": 40100, "message": "invalid credentials", "data": None}),
            ]
        )

        self.assertEqual(rotate_password(api, "admin", "Seed@123", "new-secret"), new_session)
        self.assertEqual(
            [(call[0], call[1]) for call in api.calls],
            [
                ("POST", "/api/v1/auth/login"),
                ("POST", "/api/v1/auth/login"),
                ("POST", "/api/v1/auth/change-password"),
                ("POST", "/api/v1/auth/login"),
                ("POST", "/api/v1/auth/login"),
            ],
        )

    def test_rotate_password_rejects_unexpected_seed_login_response(self):
        session = {"accessToken": "new-token", "refreshToken": "refresh-token"}
        api = ScriptedApi(
            [
                (200, {"code": 0, "message": "ok", "data": session}),
                (200, {"code": 0, "message": "ok", "data": session}),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "seed"):
            rotate_password(api, "admin", "Seed@123", "new-secret")

    def test_require_deleted_accepts_only_asset_not_found(self):
        self.assertIsNone(
            require_deleted(404, {"code": 40401, "message": "asset not found", "data": None})
        )
        for status, code in ((200, 0), (404, 0), (404, 40400), (500, 40401)):
            with self.subTest(status=status, code=code):
                with self.assertRaises(RuntimeError):
                    require_deleted(status, {"code": code, "message": "unexpected", "data": None})

    def test_run_asset_crud_verifies_successful_workflow(self):
        state = {}

        def create(method, path, body, token):
            self.assertEqual((method, path, token), ("POST", "/api/v1/assets", "tenant-token"))
            self.assertEqual(body["assetTypeId"], "type-1")
            self.assertEqual(body["assetName"], "Smoke Test Asset")
            self.assertEqual(body["attributes"], {})
            self.assertTrue(body["assetNo"].startswith("SMOKE-"))
            uuid.UUID(body["assetNo"].removeprefix("SMOKE-"))
            state["assetNo"] = body["assetNo"]
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(body["assetNo"])}

        def get_created(method, path, body, token):
            self.assertEqual((method, path, body, token), ("GET", "/api/v1/assets/asset-1", None, "tenant-token"))
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(state["assetNo"])}

        def list_created(method, path, body, token):
            query = urllib.parse.urlencode({"keyword": state["assetNo"]})
            self.assertEqual((method, path, body, token), ("GET", f"/api/v1/assets?{query}", None, "tenant-token"))
            return 200, {
                "code": 0,
                "message": "ok",
                "data": {"page": 1, "size": 20, "total": 1, "list": [self.asset_data(state["assetNo"])]},
            }

        def update(method, path, body, token):
            self.assertEqual((method, path, token), ("PUT", "/api/v1/assets/asset-1", "tenant-token"))
            self.assertEqual(body, {"assetName": "Smoke Test Asset Updated", "attributes": {}})
            return 200, {
                "code": 0,
                "message": "ok",
                "data": self.asset_data(state["assetNo"], "Smoke Test Asset Updated"),
            }

        api = ScriptedApi(
            [
                create,
                get_created,
                list_created,
                update,
                (200, {"code": 0, "message": "ok", "data": None}),
                (404, {"code": 40401, "message": "asset not found", "data": None}),
            ]
        )

        run_asset_crud(api, "tenant-token", "type-1")

        self.assertEqual([call[0] for call in api.calls], ["POST", "GET", "GET", "PUT", "DELETE", "GET"])
        self.assertEqual(api.responses, [])

    def test_run_asset_crud_cleans_up_after_post_create_failure(self):
        state = {}

        def create(method, path, body, token):
            state["assetNo"] = body["assetNo"]
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(body["assetNo"])}

        api = ScriptedApi(
            [
                create,
                (500, {"code": 0, "message": "unexpected", "data": self.asset_data("unused")}),
                (200, {"code": 0, "message": "ok", "data": None}),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "500"):
            run_asset_crud(api, "tenant-token", "type-1")

        self.assertEqual([call[0] for call in api.calls], ["POST", "GET", "DELETE"])
        self.assertEqual(api.calls[-1][1], "/api/v1/assets/asset-1")
        self.assertEqual(api.responses, [])

    def test_run_asset_crud_rejects_mismatched_created_asset_and_cleans_up(self):
        api = ScriptedApi(
            [
                (200, {"code": 0, "message": "ok", "data": self.asset_data("wrong-asset-no")}),
                (200, {"code": 0, "message": "ok", "data": None}),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "created asset"):
            run_asset_crud(api, "tenant-token", "type-1")

        self.assertEqual([call[0] for call in api.calls], ["POST", "DELETE"])

    def test_run_asset_crud_rejects_mismatched_fetched_asset_and_cleans_up(self):
        state = {}

        def create(method, path, body, token):
            state["assetNo"] = body["assetNo"]
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(body["assetNo"])}

        api = ScriptedApi(
            [
                create,
                (200, {"code": 0, "message": "ok", "data": self.asset_data("wrong-asset-no")}),
                (200, {"code": 0, "message": "ok", "data": None}),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "fetched asset"):
            run_asset_crud(api, "tenant-token", "type-1")

        self.assertEqual([call[0] for call in api.calls], ["POST", "GET", "DELETE"])

    def test_run_asset_crud_rejects_mismatched_updated_asset_and_cleans_up(self):
        state = {}

        def create(method, path, body, token):
            state["assetNo"] = body["assetNo"]
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(body["assetNo"])}

        def valid_get(method, path, body, token):
            return 200, {"code": 0, "message": "ok", "data": self.asset_data(state["assetNo"])}

        def valid_list(method, path, body, token):
            return 200, {
                "code": 0,
                "message": "ok",
                "data": {"page": 1, "size": 20, "total": 1, "list": [self.asset_data(state["assetNo"])]},
            }

        api = ScriptedApi(
            [
                create,
                valid_get,
                valid_list,
                (200, {"code": 0, "message": "ok", "data": self.asset_data("wrong-asset-no")}),
                (200, {"code": 0, "message": "ok", "data": None}),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "updated asset"):
            run_asset_crud(api, "tenant-token", "type-1")

        self.assertEqual([call[0] for call in api.calls], ["POST", "GET", "GET", "PUT", "DELETE"])


if __name__ == "__main__":
    unittest.main()
