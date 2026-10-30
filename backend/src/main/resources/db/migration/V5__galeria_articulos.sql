CREATE TABLE articulo_imagenes (
  comercio_id BIGINT NOT NULL,
  articulo_id BIGINT NOT NULL,
  posicion TINYINT NOT NULL,
  clave VARCHAR(80) NOT NULL,
  ilustrativa BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (comercio_id,articulo_id,posicion),
  UNIQUE KEY uk_articulo_foto (comercio_id,articulo_id,clave),
  KEY ix_foto_clave (clave),
  CONSTRAINT fk_imagen_articulo FOREIGN KEY (comercio_id,articulo_id) REFERENCES articulos(comercio_id,id),
  CONSTRAINT ck_imagen_posicion CHECK (posicion BETWEEN 1 AND 4)
) ENGINE=InnoDB;

INSERT INTO articulo_imagenes(comercio_id,articulo_id,posicion,clave,ilustrativa)
  SELECT comercio_id,id,1,imagen_clave,imagen_ilustrativa FROM articulos WHERE imagen_clave IS NOT NULL;
