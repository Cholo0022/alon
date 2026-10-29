ALTER TABLE articulos
  ADD COLUMN imagen_clave VARCHAR(80) NULL,
  ADD COLUMN imagen_ilustrativa BOOLEAN NOT NULL DEFAULT FALSE,
  ADD INDEX ix_articulo_imagen (imagen_clave),
  ADD UNIQUE KEY uk_articulo_comercio (comercio_id,id);

CREATE TABLE compradores_tienda (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  nombre VARCHAR(60) NOT NULL,
  apellido VARCHAR(60) NOT NULL,
  dni VARCHAR(8) NOT NULL,
  domicilio VARCHAR(180) NOT NULL,
  telefono VARCHAR(20) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  telefono_verificado BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comprador_telefono (comercio_id,telefono),
  UNIQUE KEY uk_comprador_dni (comercio_id,dni),
  UNIQUE KEY uk_comprador_comercio (comercio_id,id),
  FOREIGN KEY (comercio_id) REFERENCES comercios(id)
) ENGINE=InnoDB;

ALTER TABLE pedidos_tienda
  ADD COLUMN comprador_id BIGINT NULL,
  ADD COLUMN dni VARCHAR(8) NULL,
  ADD COLUMN domicilio VARCHAR(180) NULL,
  ADD COLUMN clave_solicitud VARCHAR(80) NULL,
  ADD COLUMN solicitud_hash CHAR(64) NULL,
  ADD COLUMN confirmado_por BIGINT NULL,
  ADD COLUMN confirmado_en DATETIME NULL,
  ADD UNIQUE KEY uk_pedido_solicitud (comercio_id,comprador_id,clave_solicitud),
  ADD UNIQUE KEY uk_pedido_comercio (comercio_id,id),
  ADD CONSTRAINT fk_pedido_comprador FOREIGN KEY (comercio_id,comprador_id) REFERENCES compradores_tienda(comercio_id,id),
  ADD CONSTRAINT fk_pedido_confirmador FOREIGN KEY (confirmado_por) REFERENCES usuarios(id);

CREATE TABLE pedido_tienda_detalle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  comercio_id BIGINT NOT NULL,
  pedido_id BIGINT NOT NULL,
  articulo_id BIGINT NOT NULL,
  nombre_articulo VARCHAR(150) NOT NULL,
  unidad VARCHAR(20) NOT NULL,
  cantidad DECIMAL(15,3) NOT NULL,
  precio_unitario DECIMAL(15,2) NOT NULL,
  incluido BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_pedido_articulo (pedido_id,articulo_id),
  FOREIGN KEY (comercio_id,pedido_id) REFERENCES pedidos_tienda(comercio_id,id),
  FOREIGN KEY (comercio_id,articulo_id) REFERENCES articulos(comercio_id,id),
  CONSTRAINT ck_pedido_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_pedido_precio CHECK (precio_unitario >= 0)
) ENGINE=InnoDB;

UPDATE usuarios SET permisos=CONCAT_WS(',',NULLIF(permisos,''),'tienda')
 WHERE rol='VENDEDOR' AND FIND_IN_SET('tienda',COALESCE(permisos,''))=0;
