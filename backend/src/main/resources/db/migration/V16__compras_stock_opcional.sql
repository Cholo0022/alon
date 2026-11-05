-- Las compras anteriores mantienen su ingreso de stock.
ALTER TABLE compras ADD COLUMN modifica_stock BOOLEAN NOT NULL DEFAULT TRUE;
