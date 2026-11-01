-- Conserva el precio de venta existente; convierte los fletes anteriores a porcentaje del costo.
-- Los valores originales siguen disponibles en el backup previo a la actualización.
ALTER TABLE articulos MODIFY COLUMN flete_porcentaje DECIMAL(24,6) NOT NULL DEFAULT 0;
UPDATE articulos SET flete_porcentaje = flete_porcentaje + CASE WHEN costo>0 THEN ROUND(flete_unitario/costo*100,6) ELSE 0 END, flete_unitario=0 WHERE flete_unitario<>0;
