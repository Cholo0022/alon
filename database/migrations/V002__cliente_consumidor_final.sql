-- Agrega el cliente genérico usado por defecto en ventas de mostrador.
USE alon_demo;

INSERT INTO clientes (comercio_id, codigo, nombre, telefono, email, limite_credito, estado)
VALUES (1, 'CLI-CONSUMIDOR-FINAL', 'Consumidor final', '', '', 0, 'ACTIVO')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), estado = 'ACTIVO';
