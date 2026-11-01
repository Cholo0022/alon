-- El proveedor pertenece a cada compra, no al alta del catálogo. Conserva vínculos históricos.
ALTER TABLE articulos MODIFY COLUMN proveedor_principal_id BIGINT NULL DEFAULT NULL;
