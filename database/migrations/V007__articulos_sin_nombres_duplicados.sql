-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
ALTER TABLE articulos
  ADD UNIQUE KEY uq_articulo_nombre (comercio_id, nombre);
