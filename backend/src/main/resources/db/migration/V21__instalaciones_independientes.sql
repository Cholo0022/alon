CREATE TABLE plataforma_instalaciones (
 negocio_id BIGINT PRIMARY KEY,
 esquema VARCHAR(48) NOT NULL UNIQUE,
 db_usuario VARCHAR(32) NOT NULL UNIQUE,
 db_clave_cifrada TEXT NOT NULL,
 puerto INT NOT NULL UNIQUE,
 identificador CHAR(36) NOT NULL UNIQUE,
 gerente_usuario VARCHAR(80) NOT NULL,
 vencimiento_licencia DATETIME NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'PREPARANDO',
 error VARCHAR(500) NOT NULL DEFAULT '',
 iniciar BOOLEAN NOT NULL DEFAULT FALSE,
 actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(negocio_id) REFERENCES plataforma_negocios(id),
 CONSTRAINT ck_instalacion_estado CHECK(estado IN ('PREPARANDO','ERROR','LISTO')),
 CONSTRAINT ck_instalacion_puerto CHECK(puerto BETWEEN 20001 AND 60000)
);
