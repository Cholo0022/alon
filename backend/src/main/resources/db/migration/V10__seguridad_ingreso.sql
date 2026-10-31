ALTER TABLE usuarios
 ADD COLUMN mfa_secret TEXT NULL,
 ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN mfa_last_step BIGINT NOT NULL DEFAULT -1,
 ADD COLUMN mfa_failures INT NOT NULL DEFAULT 0,
 ADD COLUMN mfa_blocked_until DATETIME NULL,
 ADD COLUMN security_email VARCHAR(254) NOT NULL DEFAULT '',
 ADD UNIQUE KEY uq_usuario_tenant(comercio_id,id);
UPDATE usuarios SET auth_version=auth_version+1;
CREATE TABLE security_recovery_codes (
 comercio_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL, code_hash CHAR(64) NOT NULL,
 used_at DATETIME NULL, PRIMARY KEY(comercio_id,usuario_id,code_hash),
 FOREIGN KEY(comercio_id,usuario_id) REFERENCES usuarios(comercio_id,id) ON DELETE CASCADE
);
CREATE TABLE security_browsers (
 id CHAR(36) PRIMARY KEY, comercio_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL,
 token_hash CHAR(64) NOT NULL UNIQUE, auth_version BIGINT NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', referencia VARCHAR(12) NOT NULL,
 ip VARCHAR(64) NOT NULL, navegador VARCHAR(255) NOT NULL,
 creado_en DATETIME NOT NULL, vence_en DATETIME NOT NULL,
 aprobado_por BIGINT NULL, aprobado_en DATETIME NULL, ultimo_uso DATETIME NULL,
 KEY ix_security_browser_owner(comercio_id,estado,creado_en),
 FOREIGN KEY(comercio_id,usuario_id) REFERENCES usuarios(comercio_id,id) ON DELETE CASCADE,
 FOREIGN KEY(comercio_id,aprobado_por) REFERENCES usuarios(comercio_id,id)
);
CREATE TABLE security_events (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, comercio_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL,
 tipo VARCHAR(40) NOT NULL, ip VARCHAR(64) NOT NULL, navegador VARCHAR(255) NOT NULL,
 creado_en DATETIME NOT NULL,
 FOREIGN KEY(comercio_id,usuario_id) REFERENCES usuarios(comercio_id,id) ON DELETE CASCADE
);
CREATE TABLE security_mail_queue (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, comercio_id BIGINT NOT NULL,
 destino VARCHAR(254) NOT NULL, asunto VARCHAR(180) NOT NULL, contenido TEXT NOT NULL,
 creado_en DATETIME NOT NULL, enviado_en DATETIME NULL, intentos INT NOT NULL DEFAULT 0,
 KEY ix_security_mail_pending(enviado_en,intentos), FOREIGN KEY(comercio_id) REFERENCES comercios(id)
);
