# Revisión de Alon — 4 de noviembre de 2026

Revisión del código actual de D:\Proyecto\Alon, configuración local, consultas de solo lectura y pruebas automatizadas. No se modificó la lógica de la aplicación ni los datos del negocio. No se realizaron pruebas de carga ni se provocaron fallos en la instalación en uso.

## Diagnóstico

Existe una base razonable para un comercio pequeño: transacciones, bloqueos de stock, importes decimales, claves contra operaciones duplicadas, permisos por comercio, autenticación administrativa con segundo factor, migraciones y pruebas. Sin embargo, no corresponde afirmar que está completamente optimizado o certificado para producción. Hay riesgos funcionales y de seguridad que no dependen del tamaño de la base.

## Estado medido

- MySQL 8.0.42.
- Base actual: aproximadamente 2,67 MiB de datos e índices. No incluye fotos, archivos temporales, registros de MySQL ni copias de seguridad.
- 114 artículos, 1.030 ventas, 3.046 renglones de venta y 18 compras al momento de la consulta. Estos conteos no acreditan carga simultánea ni antigüedad de uso productivo.
- 23 archivos de imágenes: 9.715.663 bytes en el directorio predeterminado revisado.
- 8 ZIP de respaldo: 33.909.697 bytes en el directorio predeterminado revisado. La copia más reciente allí es del 1 de noviembre de 2026, 18:47. No se verificaron posibles respaldos externos.
- Equipo: Intel Core i3-2377M, 2 núcleos / 4 hilos, 5,88 GiB de RAM utilizable.
- La base reside en C:, que corresponde al SSD. El proyecto reside en D:, un disco HDD. Espacio libre observado: C: 137,2 GiB; D: 452,3 GiB.
- Caché InnoDB configurada en 128 MiB. Es suficiente para contener el pequeño volumen observado; ampliarla exige medir la memoria compartida con Windows y Java.
- Registro de consultas lentas activo, pero su umbral es de 10 segundos: puede omitir consultas ya molestas para un cajero.

## Hallazgos y mejoras, por prioridad

### 1. Alta: recuperación de cuentas compradoras sin acreditar identidad

ShopAccountService.resetPassword cambia la contraseña a partir del teléfono y una clave nueva. ShopController expone esa recuperación sin exigir una sesión compradora autenticada. El token de protección CSRF y el límite de intentos no demuestran que quien solicita el cambio sea dueño del teléfono.

Consecuencia: una persona con acceso a la tienda que conozca el teléfono de un comprador puede intentar tomar su cuenta. Afecta a compradores; no equivale a obtener la cuenta administrativa. Es un riesgo actual, incluso con pocos registros. La modalidad simple está expresamente documentada en el código; aun así, conserva este riesgo.

Mejora: recuperación validada por el gerente o mediante una comprobación de identidad de un solo uso. Si no se quiere SMS o correo, limitar el restablecimiento al gerente es una alternativa.

Evidencia: backend/src/main/java/ar/com/alon/storefront/ShopAccountService.java:73 y ShopController.java:70.

### 2. Alta: editar una compra puede cambiar costos y precio de venta sin renovar la decisión

El alta respeta updateCost, pero la decisión no queda persistida como política de costo de esa compra. updatePurchase vuelve a calcular el costo ponderado y escribe costo y precio_venta para los artículos de compras que modifican stock. El editor tampoco vuelve a preguntar esa decisión.

Consecuencia: corregir una compra, incluso datos de cabecera, puede recalcular precios que el usuario decidió conservar o modificó manualmente. Las compras sin stock se saltan este recorrido; editar sus importes tampoco actualiza el costo del catálogo. Ambos casos necesitan una política explícita.

Mejora: separar corrección documental, corrección de stock y actualización de costos; preservar el precio de venta salvo decisión expresa. Agregar pruebas que editen solo la cabecera y que contemplen las combinaciones de costo y stock.

Evidencia: backend/src/main/java/ar/com/alon/purchases/PurchaseService.java:399, :423 y :437; frontend/src/pages/PurchasesPage.tsx, operación PUT de edición.

### 3. Alta si se vende a crédito: circuito de cobro de clientes incompleto

Las ventas en cuenta corriente aumentan saldo_cuenta_corriente. No se encontró una operación de recibo/cobro a clientes que aplique pagos a esa deuda: Clientes ofrece altas, modificaciones y estados; Caja ofrece apertura, gastos y cierre. La anulación de ventas resta deuda, pero no sustituye un cobro.

Consecuencia: se puede registrar la deuda sin contar con un flujo normal para cancelarla cuando el cliente paga.

Mejora: recibos de clientes, aplicación de pagos parciales, trazabilidad por venta, ingreso a caja y reversión de recibos. Si solo se vende de contado, este faltante no afecta ese circuito.

Evidencia: backend/src/main/java/ar/com/alon/sales/SalesService.java:215; customers/CustomerController.java; cash/CashController.java.

### 4. Media: el comprobante reimpreso utiliza nombres actuales

El PDF conserva cantidades y precios de venta registrados, pero consulta los nombres y códigos actuales de los artículos, el cliente y el comercio.

Consecuencia: renombrar un artículo o cambiar la identidad del negocio puede hacer que una reimpresión histórica difiera del comprobante original.

Mejora: guardar una fotografía de los datos comerciales al confirmar la venta, además de los identificadores y precios. Para documentos existentes, documentar la limitación; no inventar información histórica que nunca se guardó.

Evidencia: backend/src/main/java/ar/com/alon/sales/SalesQueries.java:34-47.

### 5. Media: algunos controles confunden una página con el conjunto completo

useApiRows carga 100 registros por página. El listado de proveedores con deuda se arma filtrando esa página, y su encabezado no indica esa limitación. La categoría y algunas sugerencias de artículos también se construyen con páginas parciales.

Consecuencia: al crecer el catálogo o la cantidad de proveedores, pueden omitirse opciones y mostrarse conteos parciales. No significa que la base admita solo 100 registros: los listados principales sí tienen paginación.

Mejora: totales calculados en el servidor, consultas de proveedores deudores paginadas con su propio filtro y búsquedas remotas para opciones de rubros.

Evidencia: frontend/src/hooks/useApiRows.ts:21; pages/SupplierPage.tsx:33, :52 y :186; pages/CatalogPage.tsx.

### 6. Media-alta operativa: los límites de restauración no acompañan el tamaño de los backups

La interfaz acepta ZIP de hasta 512 MB. La extracción admite hasta 2 GiB expandidos y 100.000 entradas. La creación de respaldos no impone límites equivalentes y guarda todas las fotos.

Consecuencia: es posible crear una copia que después no entre por la restauración de la interfaz. No implica que sea imposible recuperarla manualmente, pero sí requiere otro procedimiento. No se encontró automatización propia de Alon para copias periódicas fuera de la notebook; no se inspeccionaron sistemas externos de respaldo.

Mejora: avisar antes de superar los topes, preparar restauración para volúmenes mayores, definir retención y copia externa, y ensayar recuperaciones. No borrar indiscriminadamente auditorías ni claves de operaciones procesadas para ganar espacio.

Ejemplo de escala: 1.000 artículos con 4 fotos de 500 KB suman aproximadamente 2 GB de imágenes, además de la base. La compresión real depende de las fotos. Este límite puede importar mucho antes que el volumen de ventas.

Evidencia: backend/src/main/resources/application.properties:51; backup/BackupArchive.java:9; backup/BackupService.java.

## Rendimiento

- La búsqueda exacta por código utiliza uq_articulo_codigo y el plan estima una fila: es un punto bien resuelto.
- La búsqueda general transforma y concatena campos, y usa un patrón con coincidencia en cualquier posición. En el plan observado recorre el catálogo y ordena resultados. Debe revisarse con catálogos grandes; aumentar RAM no convierte esa expresión en una búsqueda indexada por nombre.
- CommandExecutor bloquea la fila del comercio durante cada operación de gestión. Favorece la consistencia, pero pone esas escrituras en fila si varias cajas trabajan simultáneamente. Rediseñar los bloqueos debe conservar la atomicidad y la protección contra duplicados.
- Resumen e informes recalculan agregados de todo el historial. Conviene incorporar períodos, índices acordes y resúmenes cuando se justifique con mediciones.
- Varias pantallas repiten consultas y refrescan todos sus listados con un evento global. Es razonable al inicio, mejorable con invalidación por entidad y caché acotada.
- La gestión local se inicia con el servidor de desarrollo Vite. Para una entrega estable conviene servir la compilación final y supervisar servicios, reinicios y registros. La tienda Wi-Fi ya sirve archivos compilados.
- Las fotos se normalizan y limitan; se cargan metadatos en lote en lugar de una consulta por foto. Son decisiones positivas.

## Capacidad y plazos

No se puede garantizar una cantidad de datos sin problemas con esta revisión. MySQL admite volúmenes muy superiores a los actuales; el límite útil de Alon depende del equipo, índices, consultas, fotos y concurrencia. No se realizó una prueba de carga con 10.000 o 100.000 artículos.

Propongo estos puntos de validación, no límites garantizados:

| Área | Escala inicial para probar | Qué medir |
|---|---:|---|
| Catálogo | 10.000 artículos | código, nombre, filtros y carga de fotos |
| Historial | 100.000 ventas | resumen, informes y búsquedas por fecha |
| Detalles | 1.000.000 de renglones | informes, índices y respaldos |
| Usuarios | 1, 3 y 5 cajas simultáneas | esperas, duplicados y consistencia de stock |

Se deberían acordar tiempos aceptables y medir percentiles, no solo promedios: por ejemplo, búsqueda y confirmación en menos de 2 segundos en el 95 % de las solicitudes, bajo la carga definida. Esto es un objetivo propuesto, no un resultado medido.

Ejemplos de crecimiento, suponiendo 5 artículos por venta y actividad todos los días:

| Ventas por día | Ventas por año | Renglones por año | Tiempo para sumar 1 millón de renglones |
|---:|---:|---:|---:|
| 100 | 36.500 | 182.500 | 5,5 años |
| 500 | 182.500 | 912.500 | 1,1 años |

También se acumulan movimientos de stock, caja y auditoría; los renglones no son la totalidad de los datos. Las cifras no predicen cuándo aparecerá lentitud. Los errores de seguridad y edición pueden aparecer desde el primer día; los de páginas parciales al superar los registros incluidos; los de rendimiento dependen de volumen y simultaneidad.

## Validaciones ejecutadas

- Servidor: mvn test completó sin errores. Los informes registran 110 casos: 64 ejecutados sin fallos y 46 omitidos. No se habilitó la batería de integración MySQL en esta revisión.
- Interfaz: 104 pruebas funcionales pasaron. npm test terminó con error porque Vitest intenta ejecutar storefront-lan.test.mjs, escrito para node:test.
- Pruebas Wi-Fi ejecutadas correctamente con node --test: 2 pasaron. Se debe separar su ejecución en la configuración general y CI.
- npm audit: no informó vulnerabilidades conocidas en las dependencias frontend al momento de la consulta. No es una auditoría de todas las dependencias Java ni de todas las superficies de seguridad.
- SQL de solo lectura y EXPLAIN para consultas representativas. EXPLAIN no es una prueba de carga ni una medición del tiempo con millones de registros.

## Orden recomendado

1. Resolver recuperación de cuentas y política de edición de compras.
2. Completar cobros de clientes si se utilizará cuenta corriente.
3. Conservar los datos históricos del comprobante y corregir listados parciales.
4. Automatizar copia externa y comprobar restauración con fotos.
5. Corregir la configuración de pruebas y servir la gestión compilada.
6. Medir las escalas propuestas; optimizar búsquedas, informes y bloqueos según resultados.

Referencias del motor: https://dev.mysql.com/doc/refman/8.0/en/innodb-limits.html y https://dev.mysql.com/doc/refman/8.0/en/explain.html .
