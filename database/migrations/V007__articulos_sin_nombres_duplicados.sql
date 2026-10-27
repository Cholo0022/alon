USE alon_demo;

ALTER TABLE articulos
  ADD UNIQUE KEY uq_articulo_nombre (comercio_id, nombre);
