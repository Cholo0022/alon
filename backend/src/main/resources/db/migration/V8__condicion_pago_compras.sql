ALTER TABLE compras
  ADD COLUMN condicion_pago VARCHAR(30) NOT NULL DEFAULT 'CONTADO',
  ADD COLUMN medio_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO';

-- Un pago realizado fuera de la caja conserva su comprobante, sin vincularlo al arqueo.
ALTER TABLE pagos_proveedores MODIFY COLUMN caja_id BIGINT NULL;

-- Las compras anteriores conservan el origen y el medio de su pago registrado.
UPDATE compras c
LEFT JOIN pagos_proveedores p ON p.comercio_id=c.comercio_id AND p.compra_id=c.id
  AND p.numero_recibo=CONCAT('PAG-COMPRA-',c.id)
SET c.condicion_pago=CASE WHEN c.saldo>0 THEN 'CUENTA_CORRIENTE' ELSE 'EFECTIVO_CAJA' END,
    c.medio_pago=COALESCE(p.medio_pago,'EFECTIVO');
