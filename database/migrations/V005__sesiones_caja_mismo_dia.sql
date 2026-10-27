USE alon_demo;

-- Una fecha puede contener varios cierres, pero solo una sesión abierta por comercio.
ALTER TABLE cajas
  ADD COLUMN numero_sesion INT NOT NULL DEFAULT 1 AFTER fecha,
  DROP INDEX uq_caja_fecha,
  ADD COLUMN sesion_abierta TINYINT GENERATED ALWAYS AS (CASE WHEN estado='ABIERTA' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_caja_sesion (comercio_id, fecha, numero_sesion),
  ADD UNIQUE KEY uq_caja_abierta (comercio_id, fecha, sesion_abierta);

CREATE OR REPLACE VIEW vw_cierre_caja AS
SELECT c.comercio_id, c.fecha, c.numero_sesion AS sesion, c.abierto_en, c.cerrado_en,
       c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='EFECTIVO' THEN m.importe ELSE 0 END),0) ventas_efectivo,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='MERCADO_PAGO' THEN m.importe ELSE 0 END),0) ventas_mercado_pago,
       COALESCE(SUM(CASE WHEN m.categoria='PAGO_PROVEEDOR' THEN m.importe ELSE 0 END),0) pagos_proveedores,
       COALESCE(SUM(CASE WHEN m.tipo='EGRESO' AND m.categoria<>'PAGO_PROVEEDOR' THEN m.importe ELSE 0 END),0) gastos
FROM cajas c LEFT JOIN movimientos_caja m ON m.caja_id=c.id
GROUP BY c.id, c.comercio_id, c.fecha, c.numero_sesion, c.abierto_en, c.cerrado_en,
         c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado;
