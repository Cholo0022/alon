CREATE TABLE plataforma_recibos (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 negocio_id BIGINT NOT NULL,
 solicitud CHAR(36) NOT NULL UNIQUE,
 huella CHAR(64) NOT NULL,
 fecha DATE NOT NULL,
 periodo_desde DATE NOT NULL,
 meses INT NOT NULL,
 abono_unitario DECIMAL(15,2) NOT NULL,
 bonificacion DECIMAL(5,2) NOT NULL DEFAULT 0,
 subtotal DECIMAL(15,2) NOT NULL,
 total DECIMAL(15,2) NOT NULL,
 estado VARCHAR(10) NOT NULL DEFAULT 'EMITIDO',
 creado_por BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 anulado_por BIGINT NULL,
 anulado_en TIMESTAMP NULL,
 motivo_anulacion VARCHAR(300) NOT NULL DEFAULT '',
 FOREIGN KEY(negocio_id) REFERENCES plataforma_negocios(id),
 CONSTRAINT ck_recibo_meses CHECK(meses BETWEEN 1 AND 120),
 CONSTRAINT ck_recibo_bonificacion CHECK(bonificacion BETWEEN 0 AND 100),
 CONSTRAINT ck_recibo_estado CHECK(estado IN ('EMITIDO','ANULADO')),
 CONSTRAINT ck_recibo_importes CHECK(abono_unitario >= 0 AND subtotal >= 0 AND total >= 0),
 KEY ix_recibo_negocio(negocio_id,fecha)
);
CREATE TABLE plataforma_periodos_cobrados (
 negocio_id BIGINT NOT NULL,
 periodo DATE NOT NULL,
 recibo_id BIGINT NOT NULL,
 PRIMARY KEY(negocio_id,periodo),
 FOREIGN KEY(negocio_id) REFERENCES plataforma_negocios(id),
 FOREIGN KEY(recibo_id) REFERENCES plataforma_recibos(id)
);
ALTER TABLE plataforma_movimientos
 ADD COLUMN recibo_id BIGINT NULL,
 ADD COLUMN anulado BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT fk_movimiento_recibo FOREIGN KEY(recibo_id) REFERENCES plataforma_recibos(id);
UPDATE plataforma_negocios n JOIN plataforma_instalaciones i ON i.negocio_id=n.id
SET n.vencimiento=DATE(i.vencimiento_licencia)
WHERE n.estado='PRUEBA' AND n.vencimiento IS NULL;
