ALTER TABLE clientes ADD COLUMN dni VARCHAR(12) NULL;

-- Recupera el DNI de clientes previamente vinculados a un comprador web.
UPDATE clientes c
JOIN (SELECT comercio_id,cliente_id,MIN(dni) AS dni FROM compradores_tienda
      WHERE cliente_id IS NOT NULL AND dni REGEXP '^[0-9]{1,12}$'
      GROUP BY comercio_id,cliente_id HAVING COUNT(DISTINCT dni)=1) b
  ON b.comercio_id=c.comercio_id AND b.cliente_id=c.id
SET c.dni=b.dni
;

-- Los duplicados históricos se conservan: no se fusionan ventas ni saldos automáticamente.
ALTER TABLE clientes ADD INDEX ix_cliente_dni (comercio_id,dni);
