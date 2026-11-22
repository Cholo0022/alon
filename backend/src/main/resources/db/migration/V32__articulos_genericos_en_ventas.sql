ALTER TABLE venta_detalle
    DROP FOREIGN KEY fk_venta_detalle_articulo,
    MODIFY COLUMN articulo_id BIGINT NULL,
    ADD COLUMN descripcion_generica VARCHAR(180) NULL;

ALTER TABLE venta_detalle
    ADD CONSTRAINT fk_venta_detalle_articulo_catalogo FOREIGN KEY (articulo_id) REFERENCES articulos(id);
