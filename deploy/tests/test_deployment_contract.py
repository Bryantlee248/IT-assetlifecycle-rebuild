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

    def test_compose_keeps_data_services_private(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("127.0.0.1:${ITAM_PUBLIC_PORT:?ITAM_PUBLIC_PORT is required}:80", compose)
        self.assertNotIn('"5432:5432"', compose)
        self.assertNotIn('"6379:6379"', compose)
        self.assertNotIn('"8080:8080"', compose)
        self.assertIn("SPRING_DATA_REDIS_HOST: redis", compose)

    def test_compose_requires_real_secrets(self):
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}", compose)
        self.assertIn("${ITAM_JWT_SECRET:?ITAM_JWT_SECRET is required}", compose)
        self.assertNotIn("changeme-please", compose)

    def test_environment_template_has_no_real_values(self):
        template = (ROOT / ".env.example").read_text(encoding="utf-8")
        self.assertIn("POSTGRES_PASSWORD=replace-with-random-password", template)
        self.assertIn("ITAM_JWT_SECRET=replace-with-random-secret", template)

    def test_backend_uses_spring_boot_redis_property_namespace(self):
        application = (ROOT / "backend" / "src" / "main" / "resources" / "application.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "  data:\n"
            "    redis:\n"
            "      host: ${ITAM_REDIS_HOST:localhost}\n"
            "      port: ${ITAM_REDIS_PORT:6379}\n"
            "      password: ${ITAM_REDIS_PASSWORD:}\n"
            "      database: ${ITAM_REDIS_DB:0}\n"
            "      timeout: 3000ms",
            application,
        )
        self.assertNotIn("\n  redis:\n", application)


if __name__ == "__main__":
    unittest.main()
