"""Prepara la cuenta MySQL de aprovisionamiento sin imprimir su contraseña."""

import os
import re
import secrets
import subprocess
from pathlib import Path


def main():
    os.chdir("/opt/alon-prueba")
    env = Path(".env")
    existing = re.findall(r"^ALON_PROVISIONING_PASSWORD=(.*)$", env.read_text(), re.M)
    password = existing[0] if existing else secrets.token_hex(32)
    if len(existing) > 1 or not re.fullmatch(r"[0-9a-f]{64}", password):
        raise SystemExit("Revisá la contraseña de aprovisionamiento de .env.")

    mysql = [
        "docker", "compose", "-f", "compose.hostinger.yaml", "-f", "compose.https.yaml",
        "exec", "-T", "mysql", "sh", "-ec",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -BN',
    ]
    settings = subprocess.check_output(mysql, input=b"SELECT @@partial_revokes;")
    if settings.strip() != b"0":
        raise SystemExit("MySQL tiene partial_revokes habilitado. No se aplicaron permisos.")
    if not existing:
        with env.open("a") as file:
            file.write(f"\nALON_PROVISIONING_PASSWORD={password}\n")
    schema_pattern = 'alon' + chr(92) + '_n' + chr(92) + '_%'
    sql = (
        f"CREATE USER IF NOT EXISTS 'alon_provisioner'@'%' IDENTIFIED BY '{password}';\n"
        "GRANT CREATE USER ON *.* TO 'alon_provisioner'@'%';\n"
        "GRANT SELECT ON alon.flyway_schema_history TO 'alon_provisioner'@'%';\n"
        f"GRANT ALL PRIVILEGES ON `{schema_pattern}`.* TO 'alon_provisioner'@'%' WITH GRANT OPTION;\n"
    )
    subprocess.run(mysql, input=sql.encode(), check=True)
    print("Cuenta de aprovisionamiento configurada.")


if __name__ == "__main__":
    main()
