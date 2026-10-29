ALTER TABLE pedidos_tienda
  ADD COLUMN medio_pago VARCHAR(30) NULL,
  ADD COLUMN venta_id BIGINT NULL,
  ADD UNIQUE KEY uk_pedido_venta (venta_id),
  ADD CONSTRAINT fk_pedido_venta FOREIGN KEY (venta_id) REFERENCES ventas(id);

ALTER TABLE compradores_tienda
  ADD COLUMN cliente_id BIGINT NULL,
  ADD CONSTRAINT fk_comprador_cliente FOREIGN KEY (comercio_id,cliente_id) REFERENCES clientes(comercio_id,id);

ALTER TABLE ventas
  ADD COLUMN origen VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN pedido_tienda_id BIGINT NULL,
  ADD UNIQUE KEY uk_venta_pedido_tienda (comercio_id,pedido_tienda_id),
  ADD CONSTRAINT fk_venta_pedido_tienda FOREIGN KEY (comercio_id,pedido_tienda_id) REFERENCES pedidos_tienda(comercio_id,id),
  ADD CONSTRAINT ck_venta_origen CHECK (origen IN ('LOCAL','WEB'));
