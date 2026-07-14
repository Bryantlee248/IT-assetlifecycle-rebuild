import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class DeploymentContractTest(unittest.TestCase):
    def test_frontend_container_files_exist(self):
        for relative in (
            "frontend/Dockerfile",
            "frontend/nginx.conf",
            "frontend/.dockerignore",
        ):
            self.assertTrue((ROOT / relative).is_file(), relative)

    def test_frontend_nginx_serves_spa_and_proxies_api(self):
        nginx_config = (ROOT / "frontend" / "nginx.conf").read_text(encoding="utf-8")

        self.assertIn("try_files $uri $uri/ /index.html", nginx_config)
        self.assertIn("location /api/", nginx_config)
        self.assertIn("proxy_pass http://backend:8080", nginx_config)


if __name__ == "__main__":
    unittest.main()
