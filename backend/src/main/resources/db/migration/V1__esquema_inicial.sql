CREATE TABLE IF NOT EXISTS comercios (
  id BIGINT PRIMARY KEY,
  codigo VARCHAR(40) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL,
  moneda CHAR(3) NOT NULL DEFAULT 'ARS',
  zona_horaria VARCHAR(60) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS rubros (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  codigo VARCHAR(30) NOT NULL,
  nombre VARCHAR(90) NOT NULL,
  UNIQUE KEY uq_rubro_codigo (comercio_id, codigo),
  CONSTRAINT fk_rubro_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS proveedores (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  codigo VARCHAR(30) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  especialidad VARCHAR(90) NOT NULL,
  telefono VARCHAR(40) NOT NULL,
  email VARCHAR(160) NOT NULL,
  saldo_actual DECIMAL(15,2) NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  UNIQUE KEY uq_proveedor_codigo (comercio_id, codigo),
  CONSTRAINT fk_proveedor_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS clientes (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  codigo VARCHAR(30) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  telefono VARCHAR(40) NOT NULL,
  email VARCHAR(160) NOT NULL,
  saldo_cuenta_corriente DECIMAL(15,2) NOT NULL DEFAULT 0,
  limite_credito DECIMAL(15,2) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  UNIQUE KEY uq_cliente_codigo (comercio_id, codigo),
  CONSTRAINT fk_cliente_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS articulos (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  codigo VARCHAR(30) NOT NULL,
  nombre VARCHAR(180) NOT NULL,
  rubro_id BIGINT NOT NULL,
  proveedor_principal_id BIGINT NOT NULL,
  unidad VARCHAR(30) NOT NULL,
  costo DECIMAL(15,2) NOT NULL,
  precio_venta DECIMAL(15,2) NOT NULL,
  stock DECIMAL(15,3) NOT NULL DEFAULT 0,
  stock_minimo DECIMAL(15,3) NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  UNIQUE KEY uq_articulo_codigo (comercio_id, codigo),
  KEY ix_articulos_rubro (comercio_id, rubro_id),
  KEY ix_articulos_stock (comercio_id, stock),
  CONSTRAINT fk_articulo_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_articulo_rubro FOREIGN KEY (rubro_id) REFERENCES rubros(id),
  CONSTRAINT fk_articulo_proveedor FOREIGN KEY (proveedor_principal_id) REFERENCES proveedores(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS compras (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  proveedor_id BIGINT NOT NULL,
  tipo_documento VARCHAR(20) NOT NULL,
  numero_documento VARCHAR(50) NOT NULL,
  fecha DATE NOT NULL,
  subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
  gastos_adicionales DECIMAL(15,2) NOT NULL DEFAULT 0,
  total DECIMAL(15,2) NOT NULL DEFAULT 0,
  pagado DECIMAL(15,2) NOT NULL DEFAULT 0,
  saldo DECIMAL(15,2) NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  UNIQUE KEY uq_compra_documento (comercio_id, numero_documento),
  KEY ix_compras_fecha (comercio_id, fecha),
  CONSTRAINT fk_compra_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_compra_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS compra_detalle (
  compra_id BIGINT NOT NULL,
  renglon INT NOT NULL,
  articulo_id BIGINT NOT NULL,
  cantidad DECIMAL(15,3) NOT NULL,
  costo_unitario DECIMAL(15,2) NOT NULL,
  total_linea DECIMAL(15,2) NOT NULL,
  PRIMARY KEY (compra_id, renglon),
  CONSTRAINT fk_compra_detalle_compra FOREIGN KEY (compra_id) REFERENCES compras(id),
  CONSTRAINT fk_compra_detalle_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ventas (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  numero VARCHAR(50) NOT NULL,
  fecha DATE NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cliente_id BIGINT NOT NULL,
  tipo_venta VARCHAR(30) NOT NULL,
  medio_pago VARCHAR(30) NOT NULL,
  tipo_comprobante VARCHAR(40) NOT NULL DEFAULT 'INTERNO_NO_FISCAL',
  subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
  descuento DECIMAL(15,2) NOT NULL DEFAULT 0,
  total DECIMAL(15,2) NOT NULL DEFAULT 0,
  costo_total DECIMAL(15,2) NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
  UNIQUE KEY uq_venta_numero (comercio_id, numero),
  KEY ix_ventas_fecha (comercio_id, fecha),
  KEY ix_ventas_cliente (comercio_id, cliente_id),
  CONSTRAINT fk_venta_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_venta_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS venta_detalle (
  venta_id BIGINT NOT NULL,
  renglon INT NOT NULL,
  articulo_id BIGINT NOT NULL,
  cantidad DECIMAL(15,3) NOT NULL,
  precio_unitario DECIMAL(15,2) NOT NULL,
  costo_unitario DECIMAL(15,2) NOT NULL,
  descuento_pct DECIMAL(5,2) NOT NULL DEFAULT 0,
  total_linea DECIMAL(15,2) NOT NULL,
  PRIMARY KEY (venta_id, renglon),
  CONSTRAINT fk_venta_detalle_venta FOREIGN KEY (venta_id) REFERENCES ventas(id),
  CONSTRAINT fk_venta_detalle_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS cajas (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  fecha DATE NOT NULL,
  numero_sesion INT NOT NULL DEFAULT 1,
  abierto_en DATETIME NOT NULL,
  cerrado_en DATETIME NOT NULL,
  saldo_inicial DECIMAL(15,2) NOT NULL,
  efectivo_esperado DECIMAL(15,2) NOT NULL DEFAULT 0,
  efectivo_contado DECIMAL(15,2) NOT NULL DEFAULT 0,
  diferencia DECIMAL(15,2) NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL DEFAULT 'CERRADA',
  sesion_abierta TINYINT GENERATED ALWAYS AS (CASE WHEN estado='ABIERTA' THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uq_caja_sesion (comercio_id, fecha, numero_sesion),
  UNIQUE KEY uq_caja_abierta (comercio_id, fecha, sesion_abierta),
  CONSTRAINT fk_caja_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS gastos (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  caja_id BIGINT NOT NULL,
  fecha DATE NOT NULL,
  categoria VARCHAR(50) NOT NULL,
  descripcion VARCHAR(180) NOT NULL,
  medio_pago VARCHAR(30) NOT NULL,
  importe DECIMAL(15,2) NOT NULL,
  CONSTRAINT fk_gasto_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_gasto_caja FOREIGN KEY (caja_id) REFERENCES cajas(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS pagos_proveedores (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  compra_id BIGINT NOT NULL,
  proveedor_id BIGINT NOT NULL,
  caja_id BIGINT NOT NULL,
  fecha DATE NOT NULL,
  medio_pago VARCHAR(30) NOT NULL,
  importe DECIMAL(15,2) NOT NULL,
  numero_recibo VARCHAR(50) NOT NULL,
  CONSTRAINT fk_pago_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_pago_compra FOREIGN KEY (compra_id) REFERENCES compras(id),
  CONSTRAINT fk_pago_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores(id),
  CONSTRAINT fk_pago_caja FOREIGN KEY (caja_id) REFERENCES cajas(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS movimientos_caja (
  id BIGINT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  caja_id BIGINT NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  categoria VARCHAR(50) NOT NULL,
  medio_pago VARCHAR(30) NOT NULL,
  fecha_hora DATETIME NOT NULL,
  importe DECIMAL(15,2) NOT NULL,
  referencia VARCHAR(60) NOT NULL,
  descripcion VARCHAR(180) NOT NULL,
  KEY ix_mov_caja_fecha (comercio_id, caja_id, fecha_hora),
  CONSTRAINT fk_mov_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT fk_mov_caja FOREIGN KEY (caja_id) REFERENCES cajas(id)
) ENGINE=InnoDB;


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

-- Configuración de precio de venta por artículo.
ALTER TABLE articulos
  ADD COLUMN flete_unitario DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER costo,
  ADD COLUMN margen_porcentaje DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER flete_unitario,
  ADD COLUMN redondeo_precio TINYINT(1) NOT NULL DEFAULT 0 AFTER margen_porcentaje;

-- Mantiene los precios existentes sin cambiarlos; la regla se aplica a artículos nuevos
-- y cuando una compra actualiza el costo.

ALTER TABLE articulos
  ADD COLUMN flete_porcentaje DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER flete_unitario;

CREATE TABLE usuarios (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comercio_id BIGINT NOT NULL,
  usuario VARCHAR(80) NOT NULL,
  nombre VARCHAR(160) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  rol VARCHAR(20) NOT NULL,
  permisos TEXT NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_usuarios_comercio_usuario (comercio_id, usuario),
  CONSTRAINT fk_usuarios_comercio FOREIGN KEY (comercio_id) REFERENCES comercios(id),
  CONSTRAINT chk_usuarios_rol CHECK (rol IN ('DUENO', 'VENDEDOR', 'PERSONALIZADO'))
);

CREATE OR REPLACE VIEW vw_ventas_diarias AS
SELECT comercio_id, fecha, COUNT(*) ventas, SUM(subtotal) subtotal, SUM(descuento) descuentos,
       SUM(total) total_ventas, SUM(costo_total) costo, SUM(total-costo_total) ganancia_bruta,
       ROUND(AVG(total),2) ticket_promedio
FROM ventas WHERE estado='CONFIRMADA' GROUP BY comercio_id, fecha;
CREATE OR REPLACE VIEW vw_ventas_mensuales AS
SELECT comercio_id, DATE_FORMAT(fecha,'%Y-%m') mes, COUNT(*) ventas, SUM(total) total_ventas,
       SUM(costo_total) costo, SUM(total-costo_total) ganancia_bruta
FROM ventas WHERE estado='CONFIRMADA' GROUP BY comercio_id, DATE_FORMAT(fecha,'%Y-%m');
CREATE OR REPLACE VIEW vw_ventas_por_medio_pago AS
SELECT comercio_id, fecha, medio_pago, COUNT(*) operaciones, SUM(total) importe
FROM ventas WHERE estado='CONFIRMADA' GROUP BY comercio_id, fecha, medio_pago;
CREATE OR REPLACE VIEW vw_ranking_clientes AS
SELECT c.comercio_id, c.codigo, c.nombre, COUNT(v.id) compras, COALESCE(SUM(v.total),0) total_consumido,
       c.saldo_cuenta_corriente saldo_pendiente, c.limite_credito
FROM clientes c LEFT JOIN ventas v ON v.cliente_id=c.id AND v.estado='CONFIRMADA'
GROUP BY c.id, c.comercio_id, c.codigo, c.nombre, c.saldo_cuenta_corriente, c.limite_credito;
CREATE OR REPLACE VIEW vw_articulos_mas_vendidos AS
SELECT a.comercio_id, a.codigo, a.nombre, r.nombre rubro, SUM(d.cantidad) unidades_vendidas,
       SUM(d.total_linea) importe_vendido, SUM(d.cantidad*d.costo_unitario) costo_vendido,
       SUM(d.total_linea-d.cantidad*d.costo_unitario) margen_bruto
FROM venta_detalle d JOIN ventas v ON v.id=d.venta_id JOIN articulos a ON a.id=d.articulo_id
JOIN rubros r ON r.id=a.rubro_id WHERE v.estado='CONFIRMADA'
GROUP BY a.id, a.comercio_id, a.codigo, a.nombre, r.nombre;
CREATE OR REPLACE VIEW vw_compras_por_articulo AS
SELECT a.comercio_id, a.codigo, a.nombre articulo, p.codigo proveedor_codigo, p.nombre proveedor,
       COUNT(DISTINCT c.id) compras, SUM(d.cantidad) unidades_compradas, SUM(d.total_linea) importe
FROM compra_detalle d JOIN compras c ON c.id=d.compra_id JOIN articulos a ON a.id=d.articulo_id
JOIN proveedores p ON p.id=c.proveedor_id GROUP BY a.comercio_id, a.codigo, a.nombre, p.codigo, p.nombre;
CREATE OR REPLACE VIEW vw_saldos_proveedores AS
SELECT comercio_id, codigo, nombre, especialidad, saldo_actual saldo_pendiente, estado FROM proveedores;
CREATE OR REPLACE VIEW vw_gastos_diarios AS
SELECT comercio_id, fecha, categoria, COUNT(*) movimientos, SUM(importe) total
FROM gastos GROUP BY comercio_id, fecha, categoria;
CREATE OR REPLACE VIEW vw_cierre_caja AS
SELECT c.comercio_id, c.fecha, c.numero_sesion AS sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='EFECTIVO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='EFECTIVO' THEN -m.importe ELSE 0 END),0) ventas_efectivo,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='MERCADO_PAGO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='MERCADO_PAGO' THEN -m.importe ELSE 0 END),0) ventas_mercado_pago,
       COALESCE(SUM(CASE WHEN m.categoria='PAGO_PROVEEDOR' THEN m.importe ELSE 0 END),0) pagos_proveedores,
       COALESCE(SUM(CASE WHEN m.tipo='EGRESO' AND m.categoria NOT IN ('PAGO_PROVEEDOR','VENTA_ANULADA') THEN m.importe ELSE 0 END),0) gastos
FROM cajas c LEFT JOIN movimientos_caja m ON m.caja_id=c.id
GROUP BY c.id, c.comercio_id, c.fecha, c.numero_sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado;
CREATE OR REPLACE VIEW vw_stock_bajo AS
SELECT a.comercio_id, a.codigo, a.nombre, r.nombre rubro, a.stock, a.stock_minimo
FROM articulos a JOIN rubros r ON r.id=a.rubro_id WHERE a.stock<=a.stock_minimo AND a.estado='ACTIVO';


INSERT INTO comercios(id,codigo,nombre) VALUES(1,'ALON','Mi comercio');
