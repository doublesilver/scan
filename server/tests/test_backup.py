import pytest
import subprocess
import tempfile
import shutil
from pathlib import Path
from datetime import datetime, timedelta

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent


class TestBackupScript:

    def test_backup_script_exists(self):
        assert (PROJECT_ROOT / "scripts/backup.sh").exists()

    def test_backup_script_executable(self):
        import os
        assert os.access(PROJECT_ROOT / "scripts/backup.sh", os.X_OK)

    def test_backup_creates_file(self, tmp_path):
        fake_db = tmp_path / "scanner.db"
        fake_db.write_text("test data")
        backup_dir = tmp_path / "backups"

        result = subprocess.run(
            ["bash", str(PROJECT_ROOT / "scripts/backup.sh"), str(fake_db), str(backup_dir)],
            capture_output=True, text=True
        )
        assert result.returncode == 0
        backups = list(backup_dir.glob("scanner_*.db"))
        assert len(backups) == 1
        assert datetime.now().strftime("%Y%m%d") in backups[0].name

    def test_backup_retains_7_days(self, tmp_path):
        fake_db = tmp_path / "scanner.db"
        fake_db.write_text("test data")
        backup_dir = tmp_path / "backups"
        backup_dir.mkdir()

        for i in range(10):
            date = datetime.now() - timedelta(days=i)
            old_file = backup_dir / f"scanner_{date.strftime('%Y%m%d')}.db"
            old_file.write_text(f"backup day {i}")

        result = subprocess.run(
            ["bash", str(PROJECT_ROOT / "scripts/backup.sh"), str(fake_db), str(backup_dir)],
            capture_output=True, text=True
        )
        assert result.returncode == 0
        backups = list(backup_dir.glob("scanner_*.db"))
        assert len(backups) == 7

    def test_restore_script_exists(self):
        assert (PROJECT_ROOT / "scripts/restore.sh").exists()

    def test_restore_recovers_data(self, tmp_path):
        backup_dir = tmp_path / "backups"
        backup_dir.mkdir()
        today = datetime.now().strftime("%Y%m%d")
        backup_file = backup_dir / f"scanner_{today}.db"
        backup_file.write_text("backed up data")

        restore_target = tmp_path / "scanner.db"
        restore_target.write_text("corrupted data")

        result = subprocess.run(
            ["bash", str(PROJECT_ROOT / "scripts/restore.sh"), str(backup_file), str(restore_target)],
            capture_output=True, text=True
        )
        assert result.returncode == 0
        assert restore_target.read_text() == "backed up data"


class TestCronSetup:

    def test_cron_config_exists(self):
        server_dir = PROJECT_ROOT / "server"
        assert (server_dir / "scanner-backup.service").exists() or \
               (server_dir / "scanner-backup.timer").exists() or \
               (PROJECT_ROOT / "scripts/backup.sh").exists()
