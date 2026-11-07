-- Los compradores pasan a ser contactos: no necesitan credenciales para enviar un pedido.
-- Se conservan las filas y los enlaces históricos a clientes y pedidos existentes.
ALTER TABLE compradores_tienda
  MODIFY COLUMN dni VARCHAR(12) NOT NULL,
  MODIFY COLUMN password_hash VARCHAR(255) NULL,
  DROP INDEX uk_comprador_telefono;

ALTER TABLE pedidos_tienda
  MODIFY COLUMN dni VARCHAR(12) NULL,
  ADD COLUMN provincia VARCHAR(100) NULL,
  ADD COLUMN ciudad VARCHAR(100) NULL,
  ADD INDEX ix_pedido_historial_web (comercio_id,comprador_id,estado,venta_id);
