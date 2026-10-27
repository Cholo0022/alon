USE alon_demo;

ALTER TABLE ventas
  ADD COLUMN fecha_hora DATETIME NULL AFTER fecha;

UPDATE ventas v
LEFT JOIN (
  SELECT referencia, MIN(fecha_hora) AS fecha_hora
  FROM movimientos_caja
  WHERE categoria='VENTA'
  GROUP BY referencia
) m ON m.referencia=v.numero
SET v.fecha_hora=COALESCE(m.fecha_hora,TIMESTAMP(v.fecha,'12:00:00'))
WHERE v.fecha_hora IS NULL;

ALTER TABLE ventas
  MODIFY COLUMN fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
