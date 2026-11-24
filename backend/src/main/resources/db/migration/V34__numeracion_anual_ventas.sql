CREATE TABLE venta_correlativos (
  comercio_id BIGINT NOT NULL,
  anio SMALLINT UNSIGNED NOT NULL,
  ultimo BIGINT NOT NULL,
  PRIMARY KEY (comercio_id, anio),
  CONSTRAINT fk_venta_correlativo_comercio
    FOREIGN KEY (comercio_id) REFERENCES comercios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO venta_correlativos (comercio_id, anio, ultimo)
SELECT comercio_id, CAST(RIGHT(numero, 4) AS UNSIGNED),
       MAX(CAST(SUBSTRING_INDEX(numero, '-', 1) AS UNSIGNED))
FROM ventas
WHERE numero REGEXP '^[1-9][0-9]{0,17}-[0-9]{4}$'
GROUP BY comercio_id, CAST(RIGHT(numero, 4) AS UNSIGNED);
