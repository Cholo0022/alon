# Listados y prueba de capacidad de Alon

Revisión del código de esta sesión del 6 de noviembre de 2026. Esta página separa lo corregido y verificado con pruebas automatizadas de lo que requiere una medición real con MySQL.

## Correcciones

- Proveedores con deuda: consulta independiente en el servidor, con `saldo_actual > 0` aplicado antes de paginar; mantiene su propia navegación. Ya no se filtran solamente los primeros 100 proveedores del directorio general.
- Cantidades: las consultas paginadas pueden devolver `X-Total-Count` cuando se solicita `includeTotal=true`. El total se calcula con el mismo comercio, estado y búsqueda, antes de `LIMIT/OFFSET`. La respuesta sigue siendo un array para conservar compatibilidad.
- El catálogo cuenta productos agrupados (una prenda), no sus talles como productos separados. Los clientes, proveedores y tablas generales muestran totales reales cuando el endpoint los suministra. Cuando no hay total disponible, se indica expresamente “en esta página”.
- La última página completa de 100 registros ya no ofrece “Siguiente” si el total confirma que terminó el listado.
- Se retiraron filtros locales redundantes que podían ocultar coincidencias del servidor por teléfono, rubro, especialidad o diferencias de mayúsculas/acentos.
- Los selectores de rubros del catálogo y del aumento de precios ahora consultan por búsqueda en el servidor. Las sugerencias de nombres de artículos también buscan remotamente, en lugar de usar los primeros 100 como catálogo completo. El servidor sigue validando nombres duplicados al guardar.
- El listado de rubros ya no anuncia una página parcial como cantidad total ni consulta los mismos datos dos veces solo para esa aclaración.

Los conteos agregan una consulta SQL solamente cuando se solicitan. Su costo debe incluirse en la medición; no se afirma una mejora de velocidad por corregir la exactitud.

## Resultado real de la ejecución completada

La ejecución local `20261106_a0ec37cd` terminó correctamente el 6 de noviembre a las 21:11 de Argentina, después de corregir el generador. `results.json` declara `status: completed`; Maven terminó con un caso ejecutado sin errores ni omisiones. El tiempo total aproximado de 21 minutos incluye generación, calentamientos y todas las repeticiones: no es la duración de una venta.

Se cargaron 10.000 artículos/variantes, 100.000 ventas históricas y 1.000.000 de renglones. Pasaron las comprobaciones de listados completos, aislamiento por comercio, stock, movimientos de caja e idempotencia. Se confirmaron 774 ventas adicionales entre escenarios y calentamientos, sin duplicarlas al reintentar.

| Operación | p95 medido |
|---|---:|
| Código exacto | 12 ms |
| Nombre selectivo / amplio | 505 / 367 ms |
| Catálogo agrupado, primera / última página | 1.140 / 1.142 ms |
| Ventas diarias del mes | 36 ms |
| Resumen semanal | 105 ms |
| Ventas mensuales del año | 2.114 ms |
| Artículos más vendidos del año | 25.367 ms |

| Puestos simultáneos | p95: artículos diferentes | p95: mismos artículos |
|---|---:|---:|
| 1 | 119 ms | 109 ms |
| 3 | 283 ms | 245 ms |
| 5 | 413 ms | 408 ms |

El p95 se calcula por rango más próximo. Cada consulta tiene treinta muestras; las ventas medidas son cuarenta por puesto y escenario, con calentamientos excluidos. Dos informes exceden el objetivo propuesto de dos segundos: ranking anual de artículos, de forma marcada, y resumen mensual anual, levemente. El siguiente trabajo de rendimiento debe concentrarse primero en el ranking anual; todavía no se atribuye su demora a una causa específica sin revisar su plan de ejecución real.

Equipo registrado: Windows 11, cuatro procesadores lógicos, aproximadamente 5,88 GiB de RAM física, Java 21.0.12.1 y MySQL 8.0.42 con buffer InnoDB de 128 MiB. Las condiciones y los límites de la simulación se mantienen: una caja contable, servicios Java/JDBC, sin HTTP, TLS, fotos, navegador ni autenticación. No es una certificación del despliegue remoto ni de diez usuarios simultáneos.

Fuente local: `output/performance/20261106_a0ec37cd/results.json`. El registro de consola pegado por el usuario coincide con la ejecución completada. La huella de fuentes está registrada en ese archivo y en `source-fingerprint.json` de la misma carpeta.

## Intentos anteriores de medición

La conexión desde el entorno de ejecución a `localhost:3306` y `127.0.0.1:3306` fue rechazada con el error MySQL `2003 / Windows 10013`, incluso después de abrir MySQL. Ese intento se detuvo en su comprobación previa sin crear datos de carga ni modificar la base del negocio. El usuario completó posteriormente la ejecución desde su PowerShell, cuyos resultados figuran arriba.

La compilación y las pruebas sin conexión a MySQL verifican los cambios de interfaz y la construcción de consultas; no sustituyen la prueba real de SQL, bloqueos y persistencia.

Validación ejecutada en esta revisión:

- Servidor: la batería general terminó correctamente; los informes suman 129 casos, 77 ejecutados sin fallos y 52 omitidos. Los casos omitidos incluyen la integración MySQL y la prueba de capacidad optativa.
- Interfaz: 26 pruebas de las ocho baterías seleccionadas pasaron, incluyendo los tres casos nuevos de listados completos. Se comprobaron alta de prendas, recibos, clientes, rubros/precios, búsquedas y formularios.
- La batería general de interfaz fue interrumpida al no ofrecer un resultado final; no se declara toda la interfaz validada. Después se ejecutaron por separado las baterías afectadas con resultado correcto.
- `npm run build` terminó correctamente. El servidor quedó empaquetado en `backend/target-ready/alon-api-0.1.0-SNAPSHOT.jar`; el iniciador lo aplica cuando se reinicia Alon.
- La comprobación real de conexión del ejecutor terminó bloqueada antes de crear la base. El registro correspondiente es `output/performance/20261106_71a6edac/status.json` y declara `timings_measured: false`.

## Ejecutar la prueba real

Actualización de la ejecución local del 6 de noviembre: MySQL 8.0.42 aceptó la conexión desde PowerShell del usuario y aplicó las 17 migraciones en `alon_perf_validation_20261106_4568ec39`. La generación se detuvo antes de cargar artículos porque V1 ya inserta el comercio 1 y el generador intentaba duplicarlo. El generador se corrigió para reutilizar exclusivamente esa fila inicial en la base nueva; dos pruebas de regresión verifican la reutilización y el rechazo de un estado inicial inesperado. Esta ejecución fallida no contiene mediciones de capacidad. Al repetir el comando se crea otra base nueva; no hace falta borrar la anterior. El script ahora usa UTF-8 con BOM para sus mensajes en Windows PowerShell y el generador informa el avance de cada etapa.

Desde PowerShell, dentro de `D:\Proyecto\Alon`:

```powershell
.\scripts\Probar-Capacidad-Alon.ps1
```

Usa la conexión guardada de Alon sin mostrar la contraseña y crea una base nueva con nombre `alon_perf_validation_FECHA_ID`. El usuario de MySQL necesita permiso para crear esa base. No ejecuta `DROP`, `clean` ni borrados sobre bases existentes. Si el nombre ya existe con tablas, la prueba se rechaza. La base se conserva al finalizar para poder inspeccionarla. Conviene ejecutarla en una instancia de pruebas: una base separada en el mismo servidor igualmente comparte CPU, memoria y disco con el negocio.

Se necesita Java 21, Maven y `mysql.exe`, que ya utiliza esta instalación. Puede ajustarse la cantidad de ventas medidas por puesto:

```powershell
.\scripts\Probar-Capacidad-Alon.ps1 -VentasPorPuesto 60
```

## Datos y escenarios

| Prueba | Datos / alcance |
|---|---|
| Artículos | 10.000 variantes: 9.000 artículos comunes y 500 prendas con dos talles; 9.500 productos agrupados |
| Historial | 100.000 ventas confirmadas con diez renglones cada una: 1.000.000 de renglones |
| Opciones y páginas | 120 rubros, 250 clientes y 250 proveedores; 125 proveedores con deuda y otro comercio para verificar aislamiento |
| Búsqueda | Código exacto, nombre selectivo, nombre amplio, primera y última página del catálogo agrupado |
| Informes | Ventas diarias del mes, ventas mensuales del año, artículos más vendidos del año y resumen semanal |
| Concurrencia | 1, 3 y 5 puestos, primero con artículos diferentes y después disputando los mismos artículos |

El historial se inserta por lotes con claves foráneas y totales coherentes para medir consultas. No reproduce un millón de movimientos históricos de stock, auditorías o fotos. Las ventas concurrentes sí usan `SalesService`, `CommandExecutor`, las transacciones JDBC, los bloqueos, el control de stock, los renglones, los movimientos de caja y las claves de idempotencia reales.

“Puestos” significa usuarios simultáneos del mismo comercio sobre una caja contable abierta, que es el modelo actual de Alon. No significa cinco sesiones de caja abiertas en paralelo. La prueba mide servicios Java, JDBC y serialización de respuestas de consultas; no mide HTTP, inicio de sesión, navegador, conexión del cliente ni HTTPS.

## Resultados que produce

Los archivos quedan en `output/performance/FECHA_ID/`:

- `results.json`: estado, volúmenes, equipo, versión/configuración de MySQL, p50/p95/p99, máximo, promedio y muestras de cada escenario; reintentos y comprobaciones de integridad.
- `source-fingerprint.json`: hashes de fuentes y migraciones para identificar la versión exacta, aunque existan cambios sin commit.
- `execution.log`: resultado del ejecutor de Maven.
- `status.json`: aparece si falla la comprobación previa; indica que no se midieron tiempos.

Cada búsqueda/informe tiene tres calentamientos excluidos y treinta muestras. Cada puesto realiza tres ventas de calentamiento y cuarenta ventas medidas por defecto. Cada diez ventas se reintenta la misma solicitud para comprobar que no se duplique. Se contrastan ventas, comandos, renglones, movimientos y disminución de stock. Una inconsistencia hace fallar la prueba.

El indicador p95 menor o igual a 2.000 ms es un objetivo propuesto para comparar, no una garantía. Primero debe completarse la ejecución sin errores; después hay que repetir la evaluación HTTP en el servidor de destino y probar el circuito con el cliente. No se deben trasladar resultados de esta notebook a un servidor distinto como si fueran equivalentes.
