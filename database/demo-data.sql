-- SOLO para una base demo nueva y desechable ya migrada por Flyway.
-- No ejecutar sobre una base con operaciones de usuarios: los IDs son fijos.
USE alon_demo;
SET NAMES utf8mb4;
SET time_zone='-03:00';
INSERT INTO comercios (id, codigo, nombre) VALUES (1, 'DEMO-ALON', 'Ferretería Alon de Prueba')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
INSERT INTO rubros (id, comercio_id, codigo, nombre) VALUES
  (1,1,'FIJ','Fijaciones'), (2,1,'HMA','Herramientas manuales'),
  (3,1,'HEL','Herramientas eléctricas'), (4,1,'ELE','Electricidad'),
  (5,1,'PLO','Plomería'), (6,1,'PIN','Pinturería'),
  (7,1,'ADH','Adhesivos y selladores'), (8,1,'SEG','Seguridad')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

DROP TEMPORARY TABLE IF EXISTS demo_numeros;
CREATE TEMPORARY TABLE demo_numeros (n INT NOT NULL PRIMARY KEY);
INSERT INTO demo_numeros (n)
SELECT 1 + unidades.n + decenas.n * 10 + centenas.n * 100
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) unidades
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) decenas
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) centenas;

INSERT INTO proveedores (id, comercio_id, codigo, nombre, especialidad, telefono, email, estado)
SELECT n, 1, CONCAT('PROV-', LPAD(n,2,'0')), CONCAT('Proveedor de Prueba ', LPAD(n,2,'0')),
  ELT(1 + MOD(n - 1, 10), 'Tornillería', 'Herramientas', 'Electricidad', 'Plomería', 'Pinturería', 'Seguridad', 'Adhesivos', 'Materiales', 'Ferretería general', 'Fijaciones'),
  CONCAT('+54 11 5551-', LPAD(1000+n,4,'0')), CONCAT('proveedor', LPAD(n,2,'0'), '@example.test'), IF(n=20,'INACTIVO','ACTIVO')
FROM demo_numeros WHERE n <= 20
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), especialidad=VALUES(especialidad), estado=VALUES(estado);
INSERT INTO clientes (id, comercio_id, codigo, nombre, telefono, email, limite_credito, estado)
SELECT n, 1, CONCAT('CLI-', LPAD(n,3,'0')), CONCAT('Cliente de Prueba ', LPAD(n,2,'0')),
  CONCAT('+54 11 5550-', LPAD(1000+n,4,'0')), CONCAT('cliente', LPAD(n,2,'0'), '@example.test'),
  50000 + MOD(n,5)*25000, IF(n=20,'INACTIVO','ACTIVO')
FROM demo_numeros WHERE n <= 20
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), limite_credito=VALUES(limite_credito), estado=VALUES(estado);

INSERT INTO articulos (id, comercio_id, codigo, nombre, rubro_id, unidad, costo, precio_venta, stock, stock_minimo, proveedor_principal_id) VALUES
  (1, 1, 'FER-0001', 'Tornillo para madera zincado 3 × 20 mm', 1, 'UNIDAD', 90, 130, 0, 19, 1),
  (2, 1, 'FER-0002', 'Tornillo para madera zincado 3 × 30 mm', 1, 'UNIDAD', 115, 160, 0, 26, 2),
  (3, 1, 'FER-0003', 'Tornillo para madera zincado 4 × 40 mm', 1, 'UNIDAD', 140, 200, 0, 14, 3),
  (4, 1, 'FER-0004', 'Tornillo para madera zincado 5 × 50 mm', 1, 'UNIDAD', 165, 230, 0, 21, 4),
  (5, 1, 'FER-0005', 'Tornillo autoperforante 8 × 1/2 pulg.', 1, 'UNIDAD', 120, 170, 0, 28, 5),
  (6, 1, 'FER-0006', 'Tornillo autoperforante 8 × 3/4 pulg.', 1, 'UNIDAD', 145, 210, 0, 16, 6),
  (7, 1, 'FER-0007', 'Tornillo autoperforante 10 × 1 pulg.', 1, 'UNIDAD', 170, 240, 0, 23, 7),
  (8, 1, 'FER-0008', 'Tornillo autoperforante 12 × 1 1/2 pulg.', 1, 'UNIDAD', 195, 280, 0, 30, 8),
  (9, 1, 'FER-0009', 'Taco de nylon N.º 5', 1, 'UNIDAD', 75, 110, 0, 18, 9),
  (10, 1, 'FER-0010', 'Taco de nylon N.º 6', 1, 'UNIDAD', 100, 140, 0, 25, 10),
  (11, 1, 'FER-0011', 'Taco de nylon N.º 8', 1, 'UNIDAD', 125, 180, 0, 13, 1),
  (12, 1, 'FER-0012', 'Taco de nylon N.º 10', 1, 'UNIDAD', 150, 210, 0, 20, 2),
  (13, 1, 'FER-0013', 'Clavo punta París 1 pulg.', 1, 'UNIDAD', 55, 80, 0, 27, 3),
  (14, 1, 'FER-0014', 'Clavo punta París 1 1/2 pulg.', 1, 'UNIDAD', 80, 110, 0, 15, 4),
  (15, 1, 'FER-0015', 'Clavo punta París 2 pulg.', 1, 'UNIDAD', 105, 150, 0, 22, 5),
  (16, 1, 'FER-0016', 'Clavo punta París 3 pulg.', 1, 'UNIDAD', 130, 180, 0, 29, 6),
  (17, 1, 'FER-0017', 'Destornillador plano 3 × 75 mm', 2, 'UNIDAD', 2400, 3410, 0, 17, 7),
  (18, 1, 'FER-0018', 'Destornillador plano 5 × 100 mm', 2, 'UNIDAD', 2592, 3680, 0, 24, 8),
  (19, 1, 'FER-0019', 'Destornillador plano 6 × 150 mm', 2, 'UNIDAD', 2784, 3950, 0, 12, 9),
  (20, 1, 'FER-0020', 'Destornillador plano 8 × 200 mm', 2, 'UNIDAD', 2976, 4230, 0, 19, 10),
  (21, 1, 'FER-0021', 'Destornillador Phillips PH0 × 75 mm', 2, 'UNIDAD', 2600, 3690, 0, 26, 1),
  (22, 1, 'FER-0022', 'Destornillador Phillips PH1 × 100 mm', 2, 'UNIDAD', 2808, 3990, 0, 14, 2),
  (23, 1, 'FER-0023', 'Destornillador Phillips PH2 × 150 mm', 2, 'UNIDAD', 3016, 4280, 0, 21, 3),
  (24, 1, 'FER-0024', 'Destornillador Phillips PH3 × 200 mm', 2, 'UNIDAD', 3224, 4580, 0, 28, 4),
  (25, 1, 'FER-0025', 'Llave combinada 8 mm', 2, 'UNIDAD', 3200, 4540, 0, 16, 5),
  (26, 1, 'FER-0026', 'Llave combinada 10 mm', 2, 'UNIDAD', 3456, 4910, 0, 23, 6),
  (27, 1, 'FER-0027', 'Llave combinada 13 mm', 2, 'UNIDAD', 3712, 5270, 0, 30, 7),
  (28, 1, 'FER-0028', 'Llave combinada 17 mm', 2, 'UNIDAD', 3968, 5630, 0, 18, 8),
  (29, 1, 'FER-0029', 'Pinza universal 6 pulg.', 2, 'UNIDAD', 6800, 9660, 0, 25, 9),
  (30, 1, 'FER-0030', 'Pinza universal 7 pulg.', 2, 'UNIDAD', 7344, 10430, 0, 13, 10),
  (31, 1, 'FER-0031', 'Pinza universal 8 pulg.', 2, 'UNIDAD', 7888, 11200, 0, 20, 1),
  (32, 1, 'FER-0032', 'Pinza universal 10 pulg.', 2, 'UNIDAD', 8432, 11970, 0, 27, 2),
  (33, 1, 'FER-0033', 'Martillo de carpintero 300 g', 2, 'UNIDAD', 7500, 10650, 0, 15, 3),
  (34, 1, 'FER-0034', 'Martillo de carpintero 400 g', 2, 'UNIDAD', 8100, 11500, 0, 22, 4),
  (35, 1, 'FER-0035', 'Martillo de carpintero 500 g', 2, 'UNIDAD', 8700, 12350, 0, 29, 5),
  (36, 1, 'FER-0036', 'Martillo de carpintero 600 g', 2, 'UNIDAD', 9300, 13210, 0, 17, 6),
  (37, 1, 'FER-0037', 'Cinta métrica 3 m', 2, 'UNIDAD', 2800, 3980, 0, 24, 7),
  (38, 1, 'FER-0038', 'Cinta métrica 5 m', 2, 'UNIDAD', 3024, 4290, 0, 12, 8),
  (39, 1, 'FER-0039', 'Cinta métrica 8 m', 2, 'UNIDAD', 3248, 4610, 0, 19, 9),
  (40, 1, 'FER-0040', 'Cinta métrica 10 m', 2, 'UNIDAD', 3472, 4930, 0, 26, 10),
  (41, 1, 'FER-0041', 'Mecha para pared 5 mm', 3, 'UNIDAD', 950, 1350, 0, 14, 1),
  (42, 1, 'FER-0042', 'Mecha para pared 6 mm', 3, 'UNIDAD', 1026, 1460, 0, 21, 2),
  (43, 1, 'FER-0043', 'Mecha para pared 8 mm', 3, 'UNIDAD', 1102, 1560, 0, 28, 3),
  (44, 1, 'FER-0044', 'Mecha para pared 10 mm', 3, 'UNIDAD', 1178, 1670, 0, 16, 4),
  (45, 1, 'FER-0045', 'Disco de corte 115 mm fino', 3, 'UNIDAD', 1100, 1560, 0, 23, 5),
  (46, 1, 'FER-0046', 'Disco de corte 115 mm grueso', 3, 'UNIDAD', 1188, 1690, 0, 30, 6),
  (47, 1, 'FER-0047', 'Disco de corte 180 mm fino', 3, 'UNIDAD', 1276, 1810, 0, 18, 7),
  (48, 1, 'FER-0048', 'Disco de corte 230 mm fino', 3, 'UNIDAD', 1364, 1940, 0, 25, 8),
  (49, 1, 'FER-0049', 'Cable unipolar 1,5 mm² × 10 m', 4, 'UNIDAD', 8400, 11930, 0, 13, 9),
  (50, 1, 'FER-0050', 'Cable unipolar 2,5 mm² × 10 m', 4, 'UNIDAD', 9072, 12880, 0, 20, 10),
  (51, 1, 'FER-0051', 'Cable unipolar 4 mm² × 10 m', 4, 'UNIDAD', 9744, 13840, 0, 27, 1),
  (52, 1, 'FER-0052', 'Cable unipolar 6 mm² × 10 m', 4, 'UNIDAD', 10416, 14790, 0, 15, 2),
  (53, 1, 'FER-0053', 'Tomacorriente doble Blanco 10 A', 4, 'UNIDAD', 4100, 5820, 0, 22, 3),
  (54, 1, 'FER-0054', 'Tomacorriente doble Blanco 20 A', 4, 'UNIDAD', 4428, 6290, 0, 29, 4),
  (55, 1, 'FER-0055', 'Tomacorriente doble Marfil 10 A', 4, 'UNIDAD', 4756, 6750, 0, 17, 5),
  (56, 1, 'FER-0056', 'Tomacorriente doble Negro 10 A', 4, 'UNIDAD', 5084, 7220, 0, 24, 6),
  (57, 1, 'FER-0057', 'Cinta aisladora Negra 10 m', 4, 'UNIDAD', 900, 1280, 0, 12, 7),
  (58, 1, 'FER-0058', 'Cinta aisladora Negra 20 m', 4, 'UNIDAD', 972, 1380, 0, 19, 8),
  (59, 1, 'FER-0059', 'Cinta aisladora Azul 10 m', 4, 'UNIDAD', 1044, 1480, 0, 26, 9),
  (60, 1, 'FER-0060', 'Cinta aisladora Roja 10 m', 4, 'UNIDAD', 1116, 1580, 0, 14, 10),
  (61, 1, 'FER-0061', 'Codo de PVC 90° 20 mm', 5, 'UNIDAD', 420, 600, 0, 21, 1),
  (62, 1, 'FER-0062', 'Codo de PVC 90° 25 mm', 5, 'UNIDAD', 454, 640, 0, 28, 2),
  (63, 1, 'FER-0063', 'Codo de PVC 90° 32 mm', 5, 'UNIDAD', 488, 690, 0, 16, 3),
  (64, 1, 'FER-0064', 'Codo de PVC 90° 40 mm', 5, 'UNIDAD', 522, 740, 0, 23, 4),
  (65, 1, 'FER-0065', 'Niple galvanizado 1/2 × 50 mm', 5, 'UNIDAD', 1350, 1920, 0, 30, 5),
  (66, 1, 'FER-0066', 'Niple galvanizado 1/2 × 100 mm', 5, 'UNIDAD', 1458, 2070, 0, 18, 6),
  (67, 1, 'FER-0067', 'Niple galvanizado 3/4 × 50 mm', 5, 'UNIDAD', 1566, 2220, 0, 25, 7),
  (68, 1, 'FER-0068', 'Niple galvanizado 3/4 × 100 mm', 5, 'UNIDAD', 1674, 2380, 0, 13, 8),
  (69, 1, 'FER-0069', 'Cinta de teflón 12 mm × 10 m', 5, 'UNIDAD', 650, 920, 0, 20, 9),
  (70, 1, 'FER-0070', 'Cinta de teflón 12 mm × 20 m', 5, 'UNIDAD', 702, 1000, 0, 27, 10),
  (71, 1, 'FER-0071', 'Cinta de teflón 19 mm × 10 m', 5, 'UNIDAD', 754, 1070, 0, 15, 1),
  (72, 1, 'FER-0072', 'Cinta de teflón 19 mm × 20 m', 5, 'UNIDAD', 806, 1140, 0, 22, 2),
  (73, 1, 'FER-0073', 'Pincel 1 pulg.', 6, 'UNIDAD', 1600, 2270, 0, 29, 3),
  (74, 1, 'FER-0074', 'Pincel 1 1/2 pulg.', 6, 'UNIDAD', 1728, 2450, 0, 17, 4),
  (75, 1, 'FER-0075', 'Pincel 2 pulg.', 6, 'UNIDAD', 1856, 2640, 0, 24, 5),
  (76, 1, 'FER-0076', 'Pincel 3 pulg.', 6, 'UNIDAD', 1984, 2820, 0, 12, 6),
  (77, 1, 'FER-0077', 'Rodillo de lana 10 cm', 6, 'UNIDAD', 2400, 3410, 0, 19, 7),
  (78, 1, 'FER-0078', 'Rodillo de lana 17 cm', 6, 'UNIDAD', 2592, 3680, 0, 26, 8),
  (79, 1, 'FER-0079', 'Rodillo de lana 22 cm', 6, 'UNIDAD', 2784, 3950, 0, 14, 9),
  (80, 1, 'FER-0080', 'Rodillo de lana 25 cm', 6, 'UNIDAD', 2976, 4230, 0, 21, 10),
  (81, 1, 'FER-0081', 'Lija al agua N.º 100', 6, 'UNIDAD', 300, 430, 0, 28, 1),
  (82, 1, 'FER-0082', 'Lija al agua N.º 180', 6, 'UNIDAD', 325, 460, 0, 16, 2),
  (83, 1, 'FER-0083', 'Lija al agua N.º 240', 6, 'UNIDAD', 350, 500, 0, 23, 3),
  (84, 1, 'FER-0084', 'Lija al agua N.º 400', 6, 'UNIDAD', 375, 530, 0, 30, 4),
  (85, 1, 'FER-0085', 'Silicona neutra Transparente 280 ml', 7, 'UNIDAD', 3900, 5540, 0, 18, 5),
  (86, 1, 'FER-0086', 'Silicona neutra Blanca 280 ml', 7, 'UNIDAD', 4212, 5980, 0, 25, 6),
  (87, 1, 'FER-0087', 'Silicona neutra Negra 280 ml', 7, 'UNIDAD', 4524, 6420, 0, 13, 7),
  (88, 1, 'FER-0088', 'Silicona neutra Gris 280 ml', 7, 'UNIDAD', 4836, 6870, 0, 20, 8),
  (89, 1, 'FER-0089', 'Adhesivo de contacto 125 ml', 7, 'UNIDAD', 2500, 3550, 0, 27, 9),
  (90, 1, 'FER-0090', 'Adhesivo de contacto 250 ml', 7, 'UNIDAD', 2700, 3830, 0, 15, 10),
  (91, 1, 'FER-0091', 'Adhesivo de contacto 500 ml', 7, 'UNIDAD', 2900, 4120, 0, 22, 1),
  (92, 1, 'FER-0092', 'Adhesivo de contacto 1 litro', 7, 'UNIDAD', 3100, 4400, 0, 29, 2),
  (93, 1, 'FER-0093', 'Guante moteado Talle 8', 8, 'UNIDAD', 950, 1350, 0, 17, 3),
  (94, 1, 'FER-0094', 'Guante moteado Talle 9', 8, 'UNIDAD', 1026, 1460, 0, 24, 4),
  (95, 1, 'FER-0095', 'Guante moteado Talle 10', 8, 'UNIDAD', 1102, 1560, 0, 12, 5),
  (96, 1, 'FER-0096', 'Guante moteado Talle 11', 8, 'UNIDAD', 1178, 1670, 0, 19, 6),
  (97, 1, 'FER-0097', 'Antiparra transparente Clásica', 8, 'UNIDAD', 3200, 4540, 0, 26, 7),
  (98, 1, 'FER-0098', 'Antiparra transparente Lente antiempaño', 8, 'UNIDAD', 3456, 4910, 0, 14, 8),
  (99, 1, 'FER-0099', 'Antiparra transparente Con ventilación', 8, 'UNIDAD', 3712, 5270, 0, 21, 9),
  (100, 1, 'FER-0100', 'Antiparra transparente Panorámica', 8, 'UNIDAD', 3968, 5630, 0, 28, 10)
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre), rubro_id=VALUES(rubro_id), costo=VALUES(costo), precio_venta=VALUES(precio_venta), stock_minimo=VALUES(stock_minimo), proveedor_principal_id=VALUES(proveedor_principal_id);

INSERT INTO compras (id, comercio_id, proveedor_id, tipo_documento, numero_documento, fecha)
SELECT n, 1, n, IF(MOD(n,2)=0,'FACTURA','REMITO'), CONCAT('DOC-PRUEBA-',LPAD(n,3,'0')), DATE_SUB(CURDATE(), INTERVAL 11 DAY)
FROM demo_numeros WHERE n <= 10
ON DUPLICATE KEY UPDATE fecha=VALUES(fecha);
INSERT INTO compra_detalle (compra_id, renglon, articulo_id, cantidad, costo_unitario, total_linea)
SELECT a.proveedor_principal_id,
       ROW_NUMBER() OVER (PARTITION BY a.proveedor_principal_id ORDER BY a.id),
       a.id, 200 + MOD(a.id * 7, 101), a.costo, (200 + MOD(a.id * 7, 101)) * a.costo
FROM articulos a WHERE a.comercio_id=1
ON DUPLICATE KEY UPDATE cantidad=VALUES(cantidad), costo_unitario=VALUES(costo_unitario), total_linea=VALUES(total_linea);
UPDATE compras c JOIN (
  SELECT compra_id, SUM(total_linea) subtotal FROM compra_detalle GROUP BY compra_id
) d ON d.compra_id=c.id
SET c.subtotal=d.subtotal, c.gastos_adicionales=ROUND(d.subtotal*0.04,2),
    c.total=d.subtotal+ROUND(d.subtotal*0.04,2),
    c.pagado=ROUND((d.subtotal+ROUND(d.subtotal*0.04,2))*0.25,2),
    c.saldo=(d.subtotal+ROUND(d.subtotal*0.04,2))-ROUND((d.subtotal+ROUND(d.subtotal*0.04,2))*0.25,2),
    c.estado=IF(c.saldo=0,'PAGADA','PENDIENTE');

INSERT INTO cajas (id, comercio_id, fecha, abierto_en, cerrado_en, saldo_inicial)
SELECT n, 1, DATE_SUB(CURDATE(), INTERVAL (11-n) DAY),
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (11-n) DAY),'08:00:00'),
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (11-n) DAY),'20:00:00'), 80000
FROM demo_numeros WHERE n <= 10
ON DUPLICATE KEY UPDATE abierto_en=VALUES(abierto_en), cerrado_en=VALUES(cerrado_en), saldo_inicial=VALUES(saldo_inicial);
INSERT INTO pagos_proveedores (id, comercio_id, compra_id, proveedor_id, caja_id, fecha, medio_pago, importe, numero_recibo)
SELECT 3000+n, 1, n, n, n, DATE_SUB(CURDATE(), INTERVAL (11-n) DAY), 'EFECTIVO', c.pagado,
       CONCAT('REC-PRUEBA-',LPAD(n,3,'0'))
FROM demo_numeros n JOIN compras c ON c.id=n.n WHERE n.n <= 10
ON DUPLICATE KEY UPDATE fecha=VALUES(fecha), importe=VALUES(importe);

DROP TEMPORARY TABLE IF EXISTS demo_lineas;
CREATE TEMPORARY TABLE demo_lineas (n INT NOT NULL PRIMARY KEY);
INSERT INTO demo_lineas VALUES (1),(2),(3);
INSERT INTO ventas (id, comercio_id, numero, fecha, fecha_hora, cliente_id, tipo_venta, medio_pago, tipo_comprobante)
SELECT n.n, 1,
       CONCAT('V-DEMO-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL (10-FLOOR((n.n-1)/100)) DAY),'%y%m%d'), '-', LPAD(MOD(n.n-1,100)+1,3,'0')),
       DATE_SUB(CURDATE(), INTERVAL (10-FLOOR((n.n-1)/100)) DAY),
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (10-FLOOR((n.n-1)/100)) DAY),'12:00:00'),
       MOD(n.n*7,20)+1,
       IF(MOD(n.n,10) IN (0,1,2,3),'CUENTA_CORRIENTE','CONTADO'),
       CASE WHEN MOD(n.n,10) IN (0,1,2,3) THEN 'CUENTA_CORRIENTE'
            WHEN MOD(n.n,10) IN (4,5,6,7) THEN 'EFECTIVO' ELSE 'MERCADO_PAGO' END,
       'INTERNO_NO_FISCAL'
FROM demo_numeros n WHERE n.n <= 1000
ON DUPLICATE KEY UPDATE fecha=VALUES(fecha), fecha_hora=VALUES(fecha_hora), cliente_id=VALUES(cliente_id), tipo_venta=VALUES(tipo_venta), medio_pago=VALUES(medio_pago);
INSERT INTO venta_detalle (venta_id, renglon, articulo_id, cantidad, precio_unitario, costo_unitario, descuento_pct, total_linea)
SELECT v.id, l.n, MOD(v.id*17+l.n*29,100)+1,
       1+MOD(v.id+l.n,3), a.precio_venta, a.costo, MOD(v.id+l.n,6),
       ROUND((1+MOD(v.id+l.n,3))*a.precio_venta*(1-MOD(v.id+l.n,6)/100),2)
FROM ventas v CROSS JOIN demo_lineas l
JOIN articulos a ON a.id=MOD(v.id*17+l.n*29,100)+1
WHERE v.comercio_id=1
ON DUPLICATE KEY UPDATE cantidad=VALUES(cantidad), precio_unitario=VALUES(precio_unitario), costo_unitario=VALUES(costo_unitario), descuento_pct=VALUES(descuento_pct), total_linea=VALUES(total_linea);
UPDATE ventas v JOIN (
  SELECT venta_id, SUM(cantidad*precio_unitario) subtotal,
         SUM(cantidad*precio_unitario*descuento_pct/100) descuento,
         SUM(total_linea) total, SUM(cantidad*costo_unitario) costo
  FROM venta_detalle GROUP BY venta_id
) d ON d.venta_id=v.id
SET v.subtotal=d.subtotal, v.descuento=d.descuento, v.total=d.total, v.costo_total=d.costo;

INSERT INTO gastos (id, comercio_id, caja_id, fecha, categoria, descripcion, medio_pago, importe)
SELECT 2000+n.n, 1, CEIL(n.n/3), DATE_SUB(CURDATE(), INTERVAL (11-CEIL(n.n/3)) DAY),
       CASE MOD(n.n-1,3) WHEN 0 THEN 'SUELDOS' WHEN 1 THEN 'SERVICIOS' ELSE 'LOGISTICA' END,
       CASE MOD(n.n-1,3) WHEN 0 THEN 'Jornal diario ficticio' WHEN 1 THEN 'Prorrateo ficticio de luz y gas' ELSE 'Flete y gastos de entrega ficticios' END,
       'EFECTIVO', CASE MOD(n.n-1,3) WHEN 0 THEN 45000 WHEN 1 THEN 12500 ELSE 6500 END
FROM demo_numeros n WHERE n.n <= 30
ON DUPLICATE KEY UPDATE fecha=VALUES(fecha), importe=VALUES(importe);
INSERT INTO movimientos_caja (id, comercio_id, caja_id, tipo, categoria, medio_pago, fecha_hora, importe, referencia, descripcion)
SELECT v.id, 1, DATEDIFF(v.fecha, DATE_SUB(CURDATE(), INTERVAL 10 DAY))+1, 'INGRESO', 'VENTA', v.medio_pago,
       TIMESTAMP(v.fecha, '12:00:00'), v.total, v.numero, 'Cobro ficticio de venta'
FROM ventas v WHERE v.comercio_id=1 AND v.medio_pago IN ('EFECTIVO','MERCADO_PAGO')
ON DUPLICATE KEY UPDATE caja_id=VALUES(caja_id), importe=VALUES(importe), medio_pago=VALUES(medio_pago);
INSERT INTO movimientos_caja (id, comercio_id, caja_id, tipo, categoria, medio_pago, fecha_hora, importe, referencia, descripcion)
SELECT 2000+g.id, 1, g.caja_id, 'EGRESO', g.categoria, g.medio_pago, TIMESTAMP(g.fecha,'16:00:00'), g.importe,
       CONCAT('GASTO-',g.id), g.descripcion FROM gastos g
ON DUPLICATE KEY UPDATE importe=VALUES(importe), caja_id=VALUES(caja_id);
INSERT INTO movimientos_caja (id, comercio_id, caja_id, tipo, categoria, medio_pago, fecha_hora, importe, referencia, descripcion)
SELECT 6000+p.id, 1, p.caja_id, 'EGRESO', 'PAGO_PROVEEDOR', p.medio_pago, TIMESTAMP(p.fecha,'17:00:00'), p.importe,
       p.numero_recibo, CONCAT('Pago ficticio a ', pr.nombre)
FROM pagos_proveedores p JOIN proveedores pr ON pr.id=p.proveedor_id
ON DUPLICATE KEY UPDATE importe=VALUES(importe), caja_id=VALUES(caja_id);

UPDATE articulos a JOIN (
  SELECT a.id, COALESCE(c.comprado,0)-COALESCE(v.vendido,0) stock_final
  FROM articulos a
  LEFT JOIN (SELECT articulo_id, SUM(cantidad) comprado FROM compra_detalle GROUP BY articulo_id) c ON c.articulo_id=a.id
  LEFT JOIN (SELECT articulo_id, SUM(cantidad) vendido FROM venta_detalle GROUP BY articulo_id) v ON v.articulo_id=a.id
) s ON s.id=a.id SET a.stock=s.stock_final;
UPDATE clientes c JOIN (
  SELECT cliente_id, SUM(total) saldo FROM ventas WHERE medio_pago='CUENTA_CORRIENTE' GROUP BY cliente_id
) v ON v.cliente_id=c.id SET c.saldo_cuenta_corriente=v.saldo;
UPDATE clientes c LEFT JOIN (
  SELECT cliente_id, SUM(total) saldo FROM ventas WHERE medio_pago='CUENTA_CORRIENTE' GROUP BY cliente_id
) v ON v.cliente_id=c.id SET c.saldo_cuenta_corriente=COALESCE(v.saldo,0) WHERE c.comercio_id=1;
UPDATE proveedores p JOIN (
  SELECT c.proveedor_id, SUM(c.saldo) saldo FROM compras c GROUP BY c.proveedor_id
) b ON b.proveedor_id=p.id SET p.saldo_actual=b.saldo;
UPDATE cajas c JOIN (
  SELECT caja_id,
    SUM(CASE WHEN tipo='INGRESO' AND medio_pago='EFECTIVO' THEN importe
             WHEN tipo='EGRESO' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END) flujo
  FROM movimientos_caja GROUP BY caja_id
) m ON m.caja_id=c.id
SET c.efectivo_esperado=c.saldo_inicial+m.flujo,
    c.diferencia=CASE MOD(c.id,4) WHEN 0 THEN -3500 WHEN 1 THEN 1500 ELSE 0 END,
    c.efectivo_contado=c.saldo_inicial+m.flujo+CASE MOD(c.id,4) WHEN 0 THEN -3500 WHEN 1 THEN 1500 ELSE 0 END,
    c.estado='CERRADA';

-- Agrega el cliente genérico usado por defecto en ventas de mostrador.


INSERT INTO clientes (comercio_id, codigo, nombre, telefono, email, limite_credito, estado)
VALUES (1, 'CLI-CONSUMIDOR-FINAL', 'Consumidor final', '', '', 0, 'ACTIVO')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), estado = 'ACTIVO';
