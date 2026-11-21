ALTER TABLE plataforma_negocios
 ADD COLUMN cargo_configuracion DECIMAL(15,2) NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_negocio_cargo_configuracion CHECK(cargo_configuracion >= 0);

ALTER TABLE plataforma_recibos
 ADD COLUMN cargo_configuracion DECIMAL(15,2) NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_recibo_cargo_configuracion CHECK(cargo_configuracion >= 0);
