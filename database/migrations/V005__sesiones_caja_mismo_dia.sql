-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
-- Compatibilidad con esquemas anteriores a Flyway; ejecutar una vez.
SET @alon_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cajas' AND column_name='numero_sesion')=0, 'ALTER TABLE cajas ADD COLUMN numero_sesion INT NOT NULL DEFAULT 1 AFTER fecha', 'SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
SET @alon_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cajas' AND column_name='sesion_abierta')=0, 'ALTER TABLE cajas ADD COLUMN sesion_abierta TINYINT GENERATED ALWAYS AS (CASE WHEN estado=''ABIERTA'' THEN 1 ELSE NULL END) STORED', 'SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
SET @alon_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='cajas' AND index_name='uq_caja_fecha')>0,'ALTER TABLE cajas DROP INDEX uq_caja_fecha','SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
SET @alon_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='cajas' AND index_name='uq_caja_sesion')=0,'ALTER TABLE cajas ADD UNIQUE KEY uq_caja_sesion(comercio_id,fecha,numero_sesion)','SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
SET @alon_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='cajas' AND index_name='uq_caja_abierta')=0,'ALTER TABLE cajas ADD UNIQUE KEY uq_caja_abierta(comercio_id,fecha,sesion_abierta)','SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
CREATE OR REPLACE VIEW vw_cierre_caja AS
SELECT c.comercio_id, c.fecha, c.numero_sesion AS sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='EFECTIVO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='EFECTIVO' THEN -m.importe ELSE 0 END),0) ventas_efectivo,
       COALESCE(SUM(CASE WHEN m.categoria='VENTA' AND m.medio_pago='MERCADO_PAGO' THEN m.importe WHEN m.categoria='VENTA_ANULADA' AND m.medio_pago='MERCADO_PAGO' THEN -m.importe ELSE 0 END),0) ventas_mercado_pago,
       COALESCE(SUM(CASE WHEN m.categoria='PAGO_PROVEEDOR' THEN m.importe ELSE 0 END),0) pagos_proveedores,
       COALESCE(SUM(CASE WHEN m.tipo='EGRESO' AND m.categoria NOT IN ('PAGO_PROVEEDOR','VENTA_ANULADA') THEN m.importe ELSE 0 END),0) gastos
FROM cajas c LEFT JOIN movimientos_caja m ON m.caja_id=c.id
GROUP BY c.id, c.comercio_id, c.fecha, c.numero_sesion, c.abierto_en, c.cerrado_en, c.saldo_inicial, c.efectivo_esperado, c.efectivo_contado, c.diferencia, c.estado;
