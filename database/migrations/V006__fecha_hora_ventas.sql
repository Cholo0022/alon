-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
-- Compatibilidad con esquemas anteriores a Flyway; ejecutar una vez.
SET @alon_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ventas' AND column_name='fecha_hora')=0, 'ALTER TABLE ventas ADD COLUMN fecha_hora DATETIME NULL AFTER fecha', 'SELECT 1');
PREPARE alon_stmt FROM @alon_ddl; EXECUTE alon_stmt; DEALLOCATE PREPARE alon_stmt;
UPDATE ventas SET fecha_hora=TIMESTAMP(fecha,'12:00:00') WHERE fecha_hora IS NULL;
ALTER TABLE ventas MODIFY fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
