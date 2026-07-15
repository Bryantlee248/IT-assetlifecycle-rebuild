import unittest

from deploy.smoke_test import expect_success, flatten_asset_types


class SmokeTestHelpersTest(unittest.TestCase):
    def test_expect_success_returns_data(self):
        self.assertEqual(expect_success({"code": 0, "data": {"id": "1"}}), {"id": "1"})

    def test_expect_success_rejects_error_envelope(self):
        with self.assertRaisesRegex(RuntimeError, "failed"):
            expect_success({"code": 40000, "message": "failed", "data": None})

    def test_flatten_asset_types_walks_children(self):
        nodes = [{"id": "a", "enabled": True, "children": [{"id": "b", "enabled": True, "children": []}]}]
        self.assertEqual([node["id"] for node in flatten_asset_types(nodes)], ["a", "b"])


if __name__ == "__main__":
    unittest.main()
