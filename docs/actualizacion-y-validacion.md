# Actualización y validación

## Base nueva

Flyway ejecuta V1 (esquema inicial sin datos ficticios) y V2 (integridad, permisos versionados, auditoría e idempotencia). El historial se valida al iniciar la API. No ejecutar los scripts antiguos V001–V008 además de Flyway.

## Base anterior sin historial Flyway

1. Respaldar la base y probar sobre una copia con credenciales independientes.
2. Seleccionar explícitamente la copia de la base al ejecutar scripts de compatibilidad; estos ya no cambian de base mediante USE. Verificar que la instalación anterior haya completado V001–V008. Si V005 o V006 fallaron porque el esquema ya incluía sus columnas, usar las versiones de compatibilidad corregidas; estas comprueban columnas e índices antes de agregarlos. No repetir indiscriminadamente V001–V004 o V007.
3. Resolver rubros con nombres equivalentes duplicados, referencias que mezclen comercios y cualquier inconsistencia de datos antes de actualizar. La nueva unicidad de documentos de compra distingue proveedor y tipo.
4. Apuntar explícitamente las variables de conexión a la copia y ejecutar una vez la API con `ALON_ADOPT_LEGACY_SCHEMA=true`. Spring traduce esa variable a `alon.adopt-legacy-schema`. La comprobación previa exige las columnas de V001–V008 y rechaza rubros duplicados; después registra la versión 1 como base y aplica V2. La validación de claves foráneas de MySQL impide aceptar referencias incompatibles.
5. Retirar esa variable después de adoptar el esquema. Verificar ventas, stock, caja, usuarios e informes en la copia antes de repetir el procedimiento controlado sobre la base real.

MySQL confirma cambios de esquema por sentencia: un fallo de migración puede dejar DDL aplicado parcialmente. Si falla, conservar el diagnóstico, restaurar la copia de respaldo o corregir explícitamente el estado antes de reintentar. No usar `repair` para ocultar una actualización incompleta.

V2 añade columnas de autor con valor nulo para movimientos anteriores; no inventa responsables históricos. Las correcciones y movimientos nuevos sí guardan al usuario. No cambia las contraseñas existentes ni carga cuentas demo automáticamente.

## Comprobaciones reproducibles

La integración continua utiliza Java 21, Node 24 y MySQL 8.4. Las pruebas MySQL validan instalación, aislamiento entre comercios, valoración y corrección de compras, renglones repetidos, reversos de venta, falta de stock, conservación concurrente del último dueño, claves de operación, precisión y autoría del stock, paginación, permisos financieros y solicitudes HTTP con sesión y CSRF.

Para ejecutar localmente, crear una instancia/base desechable `alon_validation` y exportar:

```powershell
$env:ALON_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/alon_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Argentina/Buenos_Aires'
$env:ALON_TEST_MYSQL_USER='usuario_de_pruebas'
$env:ALON_TEST_MYSQL_PASSWORD='contraseña_de_pruebas'
mvn -f backend/pom.xml verify
```

Las pruebas se niegan a limpiar una base con otro nombre. Usar un usuario exclusivo para esa base; no reutilizar credenciales de producción.

## Límites de la validación

Las pruebas de regresión no sustituyen pruebas de carga, revisión de un despliegue HTTPS, restauración de respaldos ni validación fiscal. El aislamiento implementado toma el comercio de la sesión; la provisión y administración de nuevos comercios continúa siendo administrativa. El sistema mantiene una única sucursal operativa por comercio.
