ALTER TABLE plataforma_negocios
 ADD COLUMN estado_motivo VARCHAR(300) NOT NULL DEFAULT '',
 ADD COLUMN estado_actualizado_en TIMESTAMP NULL;
ALTER TABLE plataforma_auditoria ADD COLUMN detalle VARCHAR(500) NOT NULL DEFAULT '';
