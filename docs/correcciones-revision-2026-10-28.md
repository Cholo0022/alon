# Correcciones de la revisión del 28/10/2026

Los hallazgos de `revision-codigo-2026-10-28.md` describen el estado anterior. Esta entrega modifica el código, añade pruebas y establece un procedimiento de actualización. No aplica cambios a la base habitual del usuario.

## Hallazgos funcionales

| Hallazgo | Corrección |
| --- | --- |
| Instalación y migraciones incompatibles | Cadena Flyway canónica, datos demo separados, compatibilidad de V005/V006 y adopción explícita de una instalación anterior. |
| Costos, fletes y stock al editar compras | Valoración anterior y nueva por artículo, acumulación de renglones repetidos, reparto de centavos y bloqueo de correcciones que afecten salidas posteriores. |
| Pérdida del último dueño | Regla transaccional protegida mediante bloqueo del comercio, también ante cambios concurrentes. |
| Autenticación débil | Contraseñas nuevas de 12–128 caracteres, PBKDF2 con 600.000 iteraciones y formato versionado; compatibilidad y actualización de hashes anteriores; cuentas demo únicamente por perfil explícito. |
| Datos ficticios en listas vacías | La API es la fuente de los listados; estados de carga, error y vacío separados. |
| Precisión y ajustes de stock | Hasta tres decimales para cantidades, rechazo de precisión excesiva y movimientos con delta firmado y autor. |
| Anulaciones en informes de caja | La vista SQL y el cierre restan anulaciones de ventas y las excluyen de gastos. |
| Falta de auditoría | Historial anterior y nuevo de correcciones de compra y artículo, con identidad del usuario; autor en movimientos nuevos de stock. |
| Documentos de proveedores distintos | Restricción única por comercio, proveedor, tipo y número. |
| IDs de rubros con MAX+1 | AUTO_INCREMENT y unicidad del nombre normalizado por comercio. |

## Robustez y mantenimiento

- Servicios separados para catálogo, ventas, compras, caja y pedidos; páginas React por módulo y componentes compartidos.
- Entradas Java con validación anidada y límites; identidad pública con contrato propio y comprobación en el frontend.
- Respuestas de error uniformes para validación, referencias inexistentes, conflictos y errores inesperados.
- CSRF para escrituras, cookies HttpOnly y SameSite, expiración, perfil HTTPS y revocación de sesiones por contraseña o desactivación.
- Roles y permisos actualizados desde la base en cada solicitud; permisos independientes para descuentos y costos. El margen del resumen requiere informes.
- Comercio obtenido de la identidad autenticada; claves foráneas principales reforzadas para impedir referencias entre comercios.
- Identificador por operación y respuesta persistida dentro de la misma transacción; reintentos con la misma clave no repiten escrituras.
- Escrituras serializadas por comercio y lecturas limitadas con búsqueda y paginación; el objetivo de capacidad sigue sujeto a medición.
- Reloj inyectable para fechas operativas; importes con centavos visibles; campos de selección nativos accesibles.
- TypeScript estricto, análisis que rechaza advertencias, Prettier y Spotless, e integración continua con MySQL.
- Configuración local coherente y MySQL publicado únicamente en loopback; documentación de ejecución, arquitectura y actualización revisada.

## Comprobación y límites

La validación utiliza una instancia MySQL 8.4 temporal separada de la base habitual. Incluye instalación nueva, actualización de esquema anterior, carga demo, transacciones comerciales, aislamiento, reintentos, sesiones y permisos. Resultados comprobados:

| Comprobación | Resultado |
| --- | --- |
| Backend: pruebas unitarias | 9 casos aprobados, incluyendo protección CSRF, hashes antiguos, precisión, reparto de gastos y estados HTTP. |
| Backend: pruebas MySQL | 12 casos aprobados en la ejecución completa; consulta y filtro de rubros revalidados después del último ajuste. |
| Frontend: pruebas | 7 casos aprobados: listas vacías, paginación, búsqueda, reintentos, revocación, centavos y fechas de Buenos Aires. |
| Compilación | API empaquetada y aplicación web compilada correctamente. |
| Análisis y formato | Oxlint sin advertencias, Prettier y Spotless correctos. |
| Dependencias del frontend | npm audit sin vulnerabilidades informadas, incluyendo desarrollo. |
| Dependencias del backend | 68 dependencias de ejecución consultadas en OSV, sin vulnerabilidades informadas al 28/10/2026. |

La ejecución completa inicial incluyó 20 casos backend; luego se añadió el caso de estados HTTP y se ejecutaron los 9 unitarios y la regresión de consultas sobre los ajustes finales. La integración continua queda configurada para ejecutar todos los casos con MySQL. No se ejecutó un flujo remoto de GitHub ni se publicó el proyecto.

No se certifica un despliegue productivo ni capacidad de carga a partir de estas pruebas. Para actualizar una instalación existente se debe seguir `actualizacion-y-validacion.md`, empezando por una copia. Las contraseñas antiguas se conservan para evitar bloquear cuentas; cualquier contraseña de demostración existente debe cambiarse antes de utilizar datos reales. La operación de varias instancias, el alta pública de comercios y múltiples sucursales no forman parte del producto implementado.
