import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class DeploymentContractTests(unittest.TestCase):
    def test_frontend_container_files_exist(self):
        for path in (
            ROOT / "frontend" / "Dockerfile",
            ROOT / "frontend" / "nginx.conf",
            ROOT / "frontend" / ".dockerignore",
        ):
            with self.subTest(path=path):
                self.assertTrue(path.is_file())

    def test_frontend_nginx_serves_spa_and_proxies_api(self):
        nginx_config = (ROOT / "frontend" / "nginx.conf").read_text()

        self.assertIn("try_files $uri $uri/ /index.html", nginx_config)
        self.assertIn("location /api/", nginx_config)
        self.assertIn("proxy_pass http://backend:8080", nginx_config)


if __name__ == "__main__":
    unittest.main()
