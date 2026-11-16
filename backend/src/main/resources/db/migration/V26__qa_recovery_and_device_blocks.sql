ALTER TABLE plataforma_negocios
 ADD COLUMN alta_huella VARCHAR(255) NULL,
 ADD COLUMN estado_objetivo VARCHAR(20) NULL,
 ADD COLUMN estado_aplicacion VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO',
 ADD COLUMN estado_error VARCHAR(500) NOT NULL DEFAULT '',
 ADD COLUMN estado_actor BIGINT NULL,
 ADD COLUMN estado_objetivo_motivo VARCHAR(300) NOT NULL DEFAULT '',
 ADD COLUMN baja_paso VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE plataforma_instalaciones ADD COLUMN alta_huella VARCHAR(255) NULL;

CREATE TABLE security_browser_blocks (
 comercio_id BIGINT NOT NULL,
 usuario_id BIGINT NOT NULL,
 ip VARCHAR(64) NOT NULL,
 navegador VARCHAR(255) NOT NULL,
 PRIMARY KEY (comercio_id,usuario_id,ip,navegador)
);
INSERT IGNORE INTO security_browser_blocks(comercio_id,usuario_id,ip,navegador)
 SELECT comercio_id,usuario_id,ip,navegador FROM security_browsers
 WHERE estado IN ('RECHAZADO','REVOCADO');
