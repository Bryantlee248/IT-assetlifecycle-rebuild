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

    def test_role_permission_seed_casts_role_ids_to_uuid(self):
        migration = (
            ROOT
            / "backend"
            / "src"
            / "main"
            / "resources"
            / "db"
            / "migration"
            / "V4__metadata_asset_seed.sql"
        ).read_text(encoding="utf-8")
        role_permissions = migration.split("INSERT INTO role_permission", 1)[1].split(
            "ON CONFLICT", 1
        )[0]

        self.assertIn("v.role_id::uuid", role_permissions)

    def test_metadata_tree_repositories_sort_by_entity_sort_order(self):
        for relative in (
            "backend/src/main/java/com/itam/metadata/repository/AssetTypeRepository.java",
            "backend/src/main/java/com/itam/metadata/repository/LocationRepository.java",
        ):
            repository = (ROOT / relative).read_text(encoding="utf-8")
            self.assertIn("OrderBySortOrderAsc", repository, relative)
            self.assertNotIn("OrderBySortAsc", repository, relative)

    def test_operations_files_include_safety_controls(self):
        backup = (ROOT / "deploy/backup-postgres.sh").read_text(encoding="utf-8")
        restore = (ROOT / "deploy/restore-postgres.sh").read_text(encoding="utf-8")
        host_nginx = (ROOT / "deploy/nginx-host.conf").read_text(encoding="utf-8")
        cron = (ROOT / "deploy/itam-backup.cron").read_text(encoding="utf-8")
        self.assertIn("-mtime +14 -delete", backup)
        self.assertIn('CONFIRM_RESTORE must be "yes"', restore)
        self.assertIn("proxy_pass http://127.0.0.1:3000", host_nginx)
        self.assertIn("30 2 * * * root", cron)

    def test_backup_script_serializes_private_backups(self):
        backup = (ROOT / "deploy/backup-postgres.sh").read_text(encoding="utf-8")

        self.assertIn("umask 077", backup)
        self.assertIn(".backup.lock", backup)
        self.assertIn("exec 9>", backup)
        self.assertIn("flock -n 9", backup)
        self.assertNotIn('mkdir "$lock_dir"', backup)
        self.assertNotIn("rmdir", backup)

    def test_restore_preflights_and_fails_atomically(self):
        restore = (ROOT / "deploy/restore-postgres.sh").read_text(encoding="utf-8")

        self.assertIn("pg_restore --list", restore)
        self.assertLess(restore.index("pg_restore --list"), restore.index("pg_restore --clean"))
        self.assertIn("--single-transaction", restore)
        self.assertIn("--exit-on-error", restore)

    def test_host_nginx_overwrites_forwarded_for(self):
        host_nginx = (ROOT / "deploy/nginx-host.conf").read_text(encoding="utf-8")

        self.assertIn("proxy_set_header X-Forwarded-For $remote_addr;", host_nginx)
        self.assertNotIn(
            "proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;", host_nginx
        )

    def test_restore_runbook_stops_and_restarts_app(self):
        readme = (ROOT / "deploy/README.md").read_text(encoding="utf-8")

        self.assertIn(
            "```sh\nset -eu\nbackup=$(ls -1t /opt/itam/backups/itam-*.dump | head -1)",
            readme,
        )
        self.assertIn(
            "docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml "
            "stop frontend backend",
            readme,
        )
        self.assertIn(
            "pre_restore_backup=$(/opt/itam/app/deploy/backup-postgres.sh)", readme
        )
        self.assertIn('test -s "$pre_restore_backup"', readme)
        self.assertIn(
            "docker compose --env-file /opt/itam/.env -f /opt/itam/app/docker-compose.yml "
            "up -d backend frontend",
            readme,
        )

    def test_live_smoke_commands_read_the_protected_credentials_file(self):
        plan = (
            ROOT / "docs" / "superpowers" / "plans" / "2026-07-14-initial-http-deployment.md"
        ).read_text(encoding="utf-8")
        live_steps = plan.split("### Task 10:", 1)[1]

        self.assertNotIn("--platform-new-password", live_steps)
        self.assertNotIn("--tenant-new-password", live_steps)
        self.assertGreaterEqual(
            live_steps.count("--credentials-file /root/.config/itam/admin-credentials"),
            2,
        )


if __name__ == "__main__":
    unittest.main()
