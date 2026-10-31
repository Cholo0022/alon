ALTER TABLE usuarios DROP CHECK chk_usuarios_rol,
 ADD CONSTRAINT chk_usuarios_rol CHECK (rol IN ('SUPER','DUENO','VENDEDOR','PERSONALIZADO'));
UPDATE usuarios SET permisos=CONCAT_WS(',',NULLIF(permisos,''),'seguridad','negocio'),auth_version=auth_version+1 WHERE rol='DUENO';
ALTER TABLE comercios
 ADD COLUMN licencia_controlada BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN licencia_hasta DATETIME NULL;
CREATE TABLE licencias (
 id CHAR(36) PRIMARY KEY, comercio_id BIGINT NOT NULL, creado_por BIGINT NOT NULL,
 clave_hash CHAR(64) NOT NULL UNIQUE, meses INT NOT NULL,
 creado_en DATETIME NOT NULL, activar_hasta DATETIME NOT NULL,
 usada_en DATETIME NULL, usada_por BIGINT NULL, revocada BOOLEAN NOT NULL DEFAULT FALSE,
 KEY ix_licencias_comercio(comercio_id,creado_en),
 FOREIGN KEY(comercio_id,creado_por) REFERENCES usuarios(comercio_id,id),
 FOREIGN KEY(comercio_id,usada_por) REFERENCES usuarios(comercio_id,id),
 CONSTRAINT ck_licencias_meses CHECK (meses BETWEEN 1 AND 120)
);
