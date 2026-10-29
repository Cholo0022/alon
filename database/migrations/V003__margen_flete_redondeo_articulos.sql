-- Seleccionar explícitamente la base de destino antes de ejecutar este script de compatibilidad.
-- Configuración de precio de venta por artículo.
ALTER TABLE articulos
  ADD COLUMN flete_unitario DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER costo,
  ADD COLUMN margen_porcentaje DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER flete_unitario,
  ADD COLUMN redondeo_precio TINYINT(1) NOT NULL DEFAULT 0 AFTER margen_porcentaje;

-- Mantiene los precios existentes sin cambiarlos; la regla se aplica a artículos nuevos
-- y cuando una compra actualiza el costo.
