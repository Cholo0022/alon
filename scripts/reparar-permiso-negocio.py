"""Habilita el permiso de delegación para una instalación ya reservada en Hostinger."""

import argparse
import os
import re
import subprocess


def mysql(sql):
    command = [
        "docker", "compose", "-f", "compose.hostinger.yaml", "-f", "compose.https.yaml",
        "exec", "-T", "mysql", "sh", "-ec",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -BN alon',
    ]
    return subprocess.check_output(command, input=sql.encode("utf-8")).decode("utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("negocio")
    args = parser.parse_args()
    os.chdir("/opt/alon-prueba")
    name = args.negocio.encode("utf-8").hex()
    rows = mysql(f"""SELECT i.esquema, i.db_usuario
        FROM plataforma_instalaciones i JOIN plataforma_negocios n ON n.id=i.negocio_id
        WHERE n.nombre=CONVERT(X'{name}' USING utf8mb4)
          AND n.estado IN ('ACTIVO','PRUEBA') AND n.eliminado_en IS NULL
          AND n.baja_iniciada_en IS NULL AND i.iniciar=FALSE AND i.estado='ERROR';""").splitlines()
    if len(rows) != 1:
        raise SystemExit("No se encontró una única instalación fallida con ese nombre. Sin cambios.")
    schema, tenant_user = rows[0].split("\t")
    if not re.fullmatch(r"alon_n_[a-f0-9]{20}", schema) or tenant_user != "alon_u_" + schema[7:]:
        raise SystemExit("La instalación tiene identificadores inesperados. Sin cambios.")
    mysql(f"GRANT ALL PRIVILEGES ON `{schema}`.* TO 'alon_provisioner'@'%' WITH GRANT OPTION;")
    print("Permiso aplicado a la base reservada. Reintentá el alta desde el mismo formulario.")


if __name__ == "__main__":
    main()
