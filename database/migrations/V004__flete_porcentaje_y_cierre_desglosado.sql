USE alon_demo;

ALTER TABLE articulos
  ADD COLUMN flete_porcentaje DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER flete_unitario;
