-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
ALTER TABLE articulos
  ADD COLUMN flete_porcentaje DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER flete_unitario;
