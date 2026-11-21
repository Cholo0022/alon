-- Recupera el cliente de mostrador de las instalaciones que ya lo tenían por código.
UPDATE clientes
SET nombre = 'Consumidor final', estado = 'ACTIVO'
WHERE codigo = 'CLI-CONSUMIDOR-FINAL';

-- Usa el cliente activo existente si el comercio ya lo creó manualmente.
INSERT INTO clientes (comercio_id, codigo, nombre, telefono, email, limite_credito, estado)
SELECT comercio.id, 'CLI-CONSUMIDOR-FINAL', 'Consumidor final', '', '', 0, 'ACTIVO'
FROM comercios comercio
WHERE NOT EXISTS (
    SELECT 1
    FROM clientes cliente
    WHERE cliente.comercio_id = comercio.id
      AND cliente.estado = 'ACTIVO'
      AND LOWER(TRIM(cliente.nombre)) = 'consumidor final'
);
