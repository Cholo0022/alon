CREATE TABLE plataforma_negocios (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(160) NOT NULL, razon_social VARCHAR(160) NOT NULL,
 cuit VARCHAR(11) NOT NULL, rubro VARCHAR(100) NOT NULL,
 localidad VARCHAR(160) NOT NULL, responsable VARCHAR(160) NOT NULL,
 telefono VARCHAR(40) NOT NULL, email VARCHAR(254) NOT NULL,
 plan VARCHAR(80) NOT NULL, abono DECIMAL(15,2) NOT NULL,
 estado VARCHAR(20) NOT NULL, alta DATE NOT NULL, vencimiento DATE NULL,
 gestion_url VARCHAR(500) NOT NULL, tienda_url VARCHAR(500) NOT NULL,
 base_referencia VARCHAR(160) NOT NULL, respaldo DATE NULL,
 notas VARCHAR(2000) NOT NULL,
 CONSTRAINT ck_plataforma_abono CHECK(abono >= 0),
 CONSTRAINT ck_plataforma_estado CHECK(estado IN ('PREPARACION','PRUEBA','ACTIVO','SUSPENDIDO','BAJA'))
);
CREATE TABLE plataforma_movimientos (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 negocio_id BIGINT NOT NULL, tipo VARCHAR(10) NOT NULL,
 fecha DATE NOT NULL, importe DECIMAL(15,2) NOT NULL,
 concepto VARCHAR(300) NOT NULL,
 FOREIGN KEY(negocio_id) REFERENCES plataforma_negocios(id),
 CONSTRAINT ck_plataforma_tipo CHECK(tipo IN ('CARGO','COBRO')),
 CONSTRAINT ck_plataforma_importe CHECK(importe > 0),
 KEY ix_plataforma_movimientos(negocio_id,fecha)
);
CREATE TABLE plataforma_auditoria (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, usuario_id BIGINT NOT NULL,
 negocio_id BIGINT NOT NULL, accion VARCHAR(40) NOT NULL, fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
