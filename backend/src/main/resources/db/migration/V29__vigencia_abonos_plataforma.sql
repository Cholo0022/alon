CREATE TABLE plataforma_abonos (
 negocio_id BIGINT NOT NULL,
 desde DATE NOT NULL,
 importe DECIMAL(15,2) NOT NULL,
 PRIMARY KEY (negocio_id, desde),
 FOREIGN KEY (negocio_id) REFERENCES plataforma_negocios(id),
 CONSTRAINT ck_plataforma_abono_importe CHECK(importe >= 0)
);

INSERT INTO plataforma_abonos(negocio_id, desde, importe)
SELECT id, CAST(DATE_FORMAT(alta, '%Y-%m-01') AS DATE), abono
FROM plataforma_negocios;

ALTER TABLE plataforma_recibos ADD COLUMN abonos_detalle TEXT NULL;
