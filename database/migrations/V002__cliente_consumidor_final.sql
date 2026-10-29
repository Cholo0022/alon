-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
-- Agrega el cliente genérico usado por defecto en ventas de mostrador.
INSERT INTO clientes (comercio_id, codigo, nombre, telefono, email, limite_credito, estado)
VALUES (1, 'CLI-CONSUMIDOR-FINAL', 'Consumidor final', '', '', 0, 'ACTIVO')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), estado = 'ACTIVO';
