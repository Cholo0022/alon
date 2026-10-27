USE alon_demo;

-- Resumen de ventas y margen bruto por día
SELECT * FROM vw_ventas_diarias ORDER BY fecha;

-- Resumen por mes
SELECT * FROM vw_ventas_mensuales ORDER BY mes;

-- Ventas y montos por medio de pago
SELECT * FROM vw_ventas_por_medio_pago ORDER BY fecha, medio_pago;

-- Ranking de clientes que más consumen
SELECT * FROM vw_ranking_clientes ORDER BY total_consumido DESC LIMIT 20;

-- Artículos más vendidos por cantidad y margen
SELECT * FROM vw_articulos_mas_vendidos ORDER BY unidades_vendidas DESC LIMIT 20;

-- Historial de compras por artículo y proveedor
SELECT * FROM vw_compras_por_articulo ORDER BY importe DESC;

-- Saldos pendientes de proveedores
SELECT * FROM vw_saldos_proveedores ORDER BY saldo_pendiente DESC;

-- Arqueo diario y diferencias de caja
SELECT * FROM vw_cierre_caja ORDER BY fecha;

-- Gastos por día y categoría
SELECT * FROM vw_gastos_diarios ORDER BY fecha, categoria;

-- Artículos para reponer
SELECT * FROM vw_stock_bajo ORDER BY stock;
