ALTER TABLE comercios
 ADD COLUMN control_periodos BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN carga_impuestos BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN cuit_contable VARCHAR(11) NOT NULL DEFAULT '',
 ADD COLUMN condicion_iva VARCHAR(30) NOT NULL DEFAULT 'RESPONSABLE_INSCRIPTO';

CREATE TABLE periodos_contables (
 comercio_id BIGINT NOT NULL, mes DATE NOT NULL, estado VARCHAR(10) NOT NULL,
 actualizado_por BIGINT NOT NULL, actualizado_en DATETIME NOT NULL, motivo VARCHAR(500) NOT NULL,
 PRIMARY KEY(comercio_id,mes),
 FOREIGN KEY(comercio_id) REFERENCES comercios(id), FOREIGN KEY(actualizado_por) REFERENCES usuarios(id),
 CONSTRAINT ck_periodo_estado CHECK(estado IN ('ABIERTO','CERRADO')),
 CONSTRAINT ck_periodo_mes CHECK(DAY(mes)=1)
) ENGINE=InnoDB;
CREATE TABLE cierres_contables (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, comercio_id BIGINT NOT NULL, mes DATE NOT NULL,
 revision INT NOT NULL, informe JSON NOT NULL, cerrado_por BIGINT NOT NULL, cerrado_en DATETIME NOT NULL,
 motivo VARCHAR(500) NOT NULL,
 UNIQUE KEY uk_cierre_revision(comercio_id,mes,revision),
 FOREIGN KEY(comercio_id) REFERENCES comercios(id), FOREIGN KEY(cerrado_por) REFERENCES usuarios(id)
) ENGINE=InnoDB;
CREATE TABLE comprobantes_contables (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, comercio_id BIGINT NOT NULL,
 clase VARCHAR(10) NOT NULL, tipo VARCHAR(20) NOT NULL, letra CHAR(1) NOT NULL,
 direccion VARCHAR(6) GENERATED ALWAYS AS (IF(clase='VENTA','VENTA','COMPRA')) STORED,
 punto_venta INT NOT NULL, numero BIGINT NOT NULL, emisor_documento VARCHAR(20) NOT NULL,
 tercero VARCHAR(150) NOT NULL, documento_tipo VARCHAR(20) NOT NULL, documento VARCHAR(20) NOT NULL,
 fecha DATE NOT NULL, periodo DATE NOT NULL, operacion_id BIGINT NULL,
 comprobante_asociado VARCHAR(100) NOT NULL DEFAULT '',
 exento DECIMAL(15,2) NOT NULL, no_gravado DECIMAL(15,2) NOT NULL,
 otros_tributos DECIMAL(15,2) NOT NULL, percepcion_iva DECIMAL(15,2) NOT NULL,
 percepcion_iibb DECIMAL(15,2) NOT NULL, credito_computable DECIMAL(15,2) NOT NULL,
 total DECIMAL(15,2) NOT NULL, controlado BOOLEAN NOT NULL DEFAULT FALSE,
 autorizacion_tipo VARCHAR(10) NOT NULL DEFAULT '', autorizacion_codigo VARCHAR(14) NOT NULL DEFAULT '',
 observaciones VARCHAR(500) NOT NULL DEFAULT '', usuario_id BIGINT NOT NULL, actualizado_en DATETIME NOT NULL,
 UNIQUE KEY uk_contable_tenant(comercio_id,id),
 UNIQUE KEY uk_contable_documento(comercio_id,direccion,emisor_documento,tipo,letra,punto_venta,numero),
 KEY ix_contable_periodo(comercio_id,periodo),
 FOREIGN KEY(comercio_id) REFERENCES comercios(id), FOREIGN KEY(usuario_id) REFERENCES usuarios(id),
 CONSTRAINT ck_contable_clase CHECK(clase IN ('VENTA','COMPRA','GASTO')),
 CONSTRAINT ck_contable_tipo CHECK(tipo IN ('FACTURA','NOTA_CREDITO','NOTA_DEBITO','INTERNO_NO_FISCAL')),
 CONSTRAINT ck_contable_periodo CHECK(DAY(periodo)=1)
) ENGINE=InnoDB;
CREATE TABLE comprobantes_contables_iva (
 comercio_id BIGINT NOT NULL, comprobante_id BIGINT NOT NULL, alicuota DECIMAL(5,2) NOT NULL,
 neto DECIMAL(15,2) NOT NULL, iva DECIMAL(15,2) NOT NULL,
 PRIMARY KEY(comprobante_id,alicuota),
 FOREIGN KEY(comercio_id,comprobante_id) REFERENCES comprobantes_contables(comercio_id,id)
) ENGINE=InnoDB;

