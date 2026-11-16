ALTER TABLE plataforma_negocios
 ADD COLUMN alta_token CHAR(36) NULL UNIQUE,
 ADD COLUMN alta_gerente VARCHAR(80) NOT NULL DEFAULT '';
