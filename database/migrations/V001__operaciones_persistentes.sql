-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
-- Ejecutar una sola vez sobre alon_demo después de database/demo-data.sql.
-- Habilita claves generadas por MySQL para las altas creadas desde la aplicación.
-- MySQL exige retirar temporalmente las claves foráneas que apuntan a IDs
-- antes de agregar AUTO_INCREMENT a esas columnas.
ALTER TABLE ventas DROP FOREIGN KEY fk_venta_cliente;
ALTER TABLE articulos DROP FOREIGN KEY fk_articulo_proveedor;
ALTER TABLE compras DROP FOREIGN KEY fk_compra_proveedor;
ALTER TABLE pagos_proveedores DROP FOREIGN KEY fk_pago_proveedor;
ALTER TABLE compra_detalle DROP FOREIGN KEY fk_compra_detalle_compra;
ALTER TABLE compra_detalle DROP FOREIGN KEY fk_compra_detalle_articulo;
ALTER TABLE venta_detalle DROP FOREIGN KEY fk_venta_detalle_venta;
ALTER TABLE venta_detalle DROP FOREIGN KEY fk_venta_detalle_articulo;
ALTER TABLE gastos DROP FOREIGN KEY fk_gasto_caja;
ALTER TABLE pagos_proveedores DROP FOREIGN KEY fk_pago_compra;
ALTER TABLE pagos_proveedores DROP FOREIGN KEY fk_pago_caja;
ALTER TABLE movimientos_caja DROP FOREIGN KEY fk_mov_caja;

ALTER TABLE clientes MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE proveedores MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE articulos MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE ventas MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE compras MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE cajas MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE gastos MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE movimientos_caja MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE pagos_proveedores MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE cajas MODIFY cerrado_en DATETIME NULL;
ALTER TABLE proveedores ADD COLUMN cuit VARCHAR(13) NULL AFTER nombre;

ALTER TABLE ventas ADD CONSTRAINT fk_venta_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);
ALTER TABLE articulos ADD CONSTRAINT fk_articulo_proveedor FOREIGN KEY (proveedor_principal_id) REFERENCES proveedores(id);
ALTER TABLE compras ADD CONSTRAINT fk_compra_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id);
ALTER TABLE pagos_proveedores ADD CONSTRAINT fk_pago_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id);
ALTER TABLE compra_detalle ADD CONSTRAINT fk_compra_detalle_compra FOREIGN KEY (compra_id) REFERENCES compras(id);
ALTER TABLE compra_detalle ADD CONSTRAINT fk_compra_detalle_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id);
ALTER TABLE venta_detalle ADD CONSTRAINT fk_venta_detalle_venta FOREIGN KEY (venta_id) REFERENCES ventas(id);
ALTER TABLE venta_detalle ADD CONSTRAINT fk_venta_detalle_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id);
ALTER TABLE gastos ADD CONSTRAINT fk_gasto_caja FOREIGN KEY (caja_id) REFERENCES cajas(id);
ALTER TABLE pagos_proveedores ADD CONSTRAINT fk_pago_compra FOREIGN KEY (compra_id) REFERENCES compras(id);
ALTER TABLE pagos_proveedores ADD CONSTRAINT fk_pago_caja FOREIGN KEY (caja_id) REFERENCES cajas(id);
ALTER TABLE movimientos_caja ADD CONSTRAINT fk_mov_caja FOREIGN KEY (caja_id) REFERENCES cajas(id);

CREATE TABLE pedidos_tienda (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  nombre_cliente VARCHAR(150) NOT NULL,
  contacto VARCHAR(100) NOT NULL,
  detalle VARCHAR(500) NOT NULL,
  total_estimado DECIMAL(15,2) NOT NULL,
  estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_CONFIRMACION',
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pedido_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;

CREATE TABLE movimientos_stock (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  articulo_id BIGINT NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cantidad DECIMAL(15,3) NOT NULL,
  referencia VARCHAR(60) NOT NULL,
  descripcion VARCHAR(180) NOT NULL,
  KEY ix_mov_stock_articulo_fecha (comercio_id, articulo_id, fecha_hora),
  CONSTRAINT fk_mov_stock_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_mov_stock_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
) ENGINE=InnoDB;
