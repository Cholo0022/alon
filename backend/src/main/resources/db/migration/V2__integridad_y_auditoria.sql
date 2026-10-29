-- Baseline V1 is the former schema after legacy migrations V001..V008.
ALTER TABLE usuarios ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE compras DROP INDEX uq_compra_documento, ADD UNIQUE KEY uq_compra_documento(comercio_id,proveedor_id,tipo_documento,numero_documento);
ALTER TABLE articulos DROP FOREIGN KEY fk_articulo_rubro;
ALTER TABLE rubros MODIFY id BIGINT NOT NULL AUTO_INCREMENT,
 ADD COLUMN nombre_normalizado VARCHAR(90) GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED,
 ADD UNIQUE KEY uq_rubro_nombre(comercio_id,nombre_normalizado),
 ADD UNIQUE KEY uq_rubro_tenant(comercio_id,id);
ALTER TABLE articulos ADD CONSTRAINT fk_articulo_rubro FOREIGN KEY(comercio_id,rubro_id) REFERENCES rubros(comercio_id,id);
ALTER TABLE proveedores ADD UNIQUE KEY uq_proveedor_tenant(comercio_id,id);
ALTER TABLE clientes ADD UNIQUE KEY uq_cliente_tenant(comercio_id,id);
ALTER TABLE articulos DROP FOREIGN KEY fk_articulo_proveedor, ADD CONSTRAINT fk_articulo_proveedor_tenant FOREIGN KEY(comercio_id,proveedor_principal_id) REFERENCES proveedores(comercio_id,id);
ALTER TABLE ventas DROP FOREIGN KEY fk_venta_cliente, ADD CONSTRAINT fk_venta_cliente_tenant FOREIGN KEY(comercio_id,cliente_id) REFERENCES clientes(comercio_id,id);
ALTER TABLE compras DROP FOREIGN KEY fk_compra_proveedor, ADD CONSTRAINT fk_compra_proveedor_tenant FOREIGN KEY(comercio_id,proveedor_id) REFERENCES proveedores(comercio_id,id);
ALTER TABLE movimientos_stock ADD COLUMN usuario_id BIGINT NULL, ADD CONSTRAINT fk_mov_stock_usuario FOREIGN KEY(usuario_id) REFERENCES usuarios(id);
CREATE TABLE auditoria_operaciones (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, comercio_id BIGINT NOT NULL, usuario_id BIGINT NOT NULL,
 entidad VARCHAR(40) NOT NULL, entidad_id BIGINT NOT NULL, accion VARCHAR(40) NOT NULL,
 datos_anteriores JSON NOT NULL, datos_nuevos JSON NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_auditoria_entidad(comercio_id,entidad,entidad_id,creado_en),
 FOREIGN KEY(comercio_id) REFERENCES comercios(id), FOREIGN KEY(usuario_id) REFERENCES usuarios(id)
);
CREATE TABLE comandos_procesados (
 comercio_id BIGINT NOT NULL, clave VARCHAR(80) NOT NULL, solicitud_hash CHAR(64) NOT NULL,
 respuesta JSON NOT NULL, creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(comercio_id,clave), FOREIGN KEY(comercio_id) REFERENCES comercios(id)
);
CREATE OR REPLACE VIEW vw_cierre_caja AS
SELECT c.comercio_id, c.fecha, c.numero_sesion AS sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='EFECTIVO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='EFECTIVO' THEN -m.importe ELSE 0 END),0) ventas_efectivo,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='MERCADO_PAGO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='MERCADO_PAGO' THEN -m.importe ELSE 0 END),0) ventas_mercado_pago,
       COALESCE(SUM(CASE WHEN m.categoria='PAGO_PROVEEDOR' THEN m.importe ELSE 0 END),0) pagos_proveedores,
       COALESCE(SUM(CASE WHEN m.tipo='EGRESO' AND m.categoria NOT IN ('PAGO_PROVEEDOR','VENTA_ANULADA') THEN m.importe ELSE 0 END),0) gastos
FROM cajas c LEFT JOIN movimientos_caja m ON m.caja_id=c.id
GROUP BY c.id, c.comercio_id, c.fecha, c.numero_sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado;
