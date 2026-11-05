# Alon

Sistema web de gestión comercial para comercios argentinos. Java 21, Spring Boot, MySQL 8.4, React y TypeScript. Importes en ARS; fechas operativas en America/Argentina/Buenos_Aires.

## Inicio con una base nueva

1. Instalar Java 21, Maven, Node.js 24 y MySQL 8.4 o Docker.
2. Iniciar MySQL con `docker compose up -d` o crear una base vacía `alon` y un usuario con acceso a ella. Compose publica MySQL solamente en 127.0.0.1.
3. Configurar las variables de `backend/.env.example` en el entorno. Spring no carga automáticamente archivos .env.
4. Desde backend/, ejecutar `mvn spring-boot:run`. Flyway crea y versiona el esquema. Las migraciones canónicas están en backend/src/main/resources/db/migration/.
5. Desde frontend/, ejecutar `npm ci` y `npm run dev`. Abrir http://127.0.0.1:5173 y configurar la cuenta dueña con una contraseña de 12 a 128 caracteres.

La base inicial contiene el comercio ALON. El alta de otros comercios es administrativa; no hay registro público de comercios. El código de comercio en el inicio de sesión selecciona la organización; las operaciones posteriores toman el comercio de la sesión verificada por el servidor. Si se deja vacío se usa ALON_BOOTSTRAP_STORE_ID, por defecto 1.

## Actualizar una base existente

Conservá `SPRING_DATASOURCE_URL` apuntando a tu base actual. El nombre predeterminado `alon` es para instalaciones nuevas; no se renombra una base existente. Al actualizar las rutas de la API, reiniciar el servidor y recargar la interfaz juntos.

No volver a cargar datos de prueba ni ejecutar el esquema inicial sobre una base con operaciones. Seguir [la guía de actualización](docs/actualizacion-y-validacion.md), conservar una copia de seguridad y probar primero sobre una copia. La adopción del esquema anterior requiere una opción explícita y comprueba que las migraciones antiguas estén completas. Flyway rechaza por defecto bases no vacías sin historial.

Los scripts de database/migrations/ se conservan únicamente como compatibilidad para instalaciones antiguas; no son la cadena Flyway de una instalación nueva.

## Datos de ejemplo opcionales

Usar exclusivamente una base nueva y desechable. Después de crear el esquema con Flyway, ejecutar una sola vez database/alon-sample-data.sql sobre alon. Contiene registros ficticios con IDs fijos y puede sobrescribir operaciones si se reutiliza incorrectamente; no es una herramienta de actualización.

Para crear cuentas de prueba en una base sin usuarios, iniciar con SPRING_PROFILES_ACTIVE=local. Usuarios: alon_pruebas y vendedor1; contraseña inicial de ambos: CambiarAlon-2026!. El perfil normal no crea cuentas conocidas. Las cuentas antiguas se conservan al migrar y sus hashes se actualizan al iniciar sesión; cambiar cualquier contraseña antigua de prueba desde Usuarios antes de cargar información real.

## Funcionamiento y reglas

La tienda local está en http://127.0.0.1:5173/tienda. Comparte catálogo y stock con Alon, permite registrar compradores y envía pedidos pendientes para revisar en Tienda online. Consultar [imágenes, pedidos y configuración](docs/tienda/README.md). Las fotos deben respaldarse junto con la base.

- Ventas, compras, pagos, stock y caja se guardan en transacciones. Cada escritura comercial requiere Idempotency-Key; la web lo genera y lo conserva cuando se pierde una respuesta. Reutilizar una clave con datos diferentes devuelve conflicto.
- Los importes usan DECIMAL/BigDecimal y las cantidades hasta tres decimales. Se rechaza precisión excesiva en vez de redondear silenciosamente la entrada.
- La corrección de una compra recalcula su valoración, conserva el registro anterior y registra al autor. Si hubo salidas posteriores del artículo, se impide reescribir esa compra; deben registrarse los ajustes correspondientes. Las ventas conservan sus costos históricos.
- Siempre debe quedar al menos un dueño activo. Los cambios de contraseña y desactivaciones revocan las sesiones anteriores; roles y permisos se consultan en cada solicitud.
- Los descuentos y el acceso a costos requieren permisos explícitos. El permiso de compras incluye consulta de costos de compra. Los márgenes del resumen requieren informes.
- Las consultas usan page, size (máximo 200) y q; la interfaz navega de a 100 registros. Los rankings están acotados a 10 resultados. No se ha certificado la capacidad objetivo de 99.999 artículos y usuarios.

## Verificación

Desde la raíz:

```sh
mvn -f backend/pom.xml verify
npm --prefix frontend run build
npm --prefix frontend run lint
npm --prefix frontend test
```

Las pruebas MySQL necesitan una base desechable llamada alon_validation y ALON_TEST_MYSQL_URL, ALON_TEST_MYSQL_USER, ALON_TEST_MYSQL_PASSWORD. Borran y reconstruyen exclusivamente esa base y rechazan otro nombre. Sin esas variables se ejecutan las pruebas unitarias y se omiten las de MySQL. La integración continua configura MySQL y ejecuta ambas.

## Despliegue

Usar el perfil production, credenciales propias, conexión MySQL protegida y HTTPS mediante un servidor web que mantenga /api en el mismo origen. Ese perfil exige las variables de conexión y cookies seguras. El perfil local es para desarrollo y no habilita cuentas conocidas en producción.

Las sesiones y el límite de intentos actuales están pensados para una instancia de API. Varias instancias requieren almacenamiento compartido de sesiones y limitación de intentos, además de pruebas de carga. Restringir acceso a la base y probar restauración de respaldos.

Las ventas son comprobantes internos no fiscales. No se emiten comprobantes ante ARCA ni se capturan pagos externos de Mercado Pago; indicar ese medio registra un movimiento interno. Esas integraciones siguen fuera de este alcance.

## Estructura

- backend/: controladores, seguridad, servicios por módulo, consultas, auditoría y migraciones.
- frontend/: páginas, componentes compartidos, cliente HTTP y pruebas.
- database/: datos de prueba opcionales, consultas para Workbench y compatibilidad anterior.
- docs/: alcance, decisiones, revisión y guía de actualización.

Copyright © 2026 Cholo0022. Todos los derechos reservados.

Para recorrer el proyecto y entender sus comentarios, consultar [la guía del código de Alon](docs/guia-codigo-alon.md).


## Inicio rápido en esta instalación de Windows

La conexión local se guarda en `.validation/connection/`, excluida de Git, y la contraseña queda cifrada para el usuario de Windows. Si no existe esa configuración, el iniciador usa las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` del entorno; si faltan, solicita los datos. El parámetro `-Configurar` permite volver a ingresarlos. Este comando inicia el servidor y la interfaz; `-Reiniciar` permite volver a abrir ambos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "D:\Proyecto\Alon\Iniciar-Alon.ps1" -Reiniciar
```

Alon queda disponible en `http://127.0.0.1:5173` y su tienda en `/tienda`. Los registros del inicio se guardan en `.validation/runtime/`.

## Doble factor y permisos de ingreso

El primer ingreso del dueño exige vincular una aplicación autenticadora y guardar diez códigos de recuperación. En **Usuarios → Seguridad de ingreso** se configura el correo del dueño y se autorizan o revocan navegadores de empleados. No se permite entrar a gestión con una verificación pendiente. La configuración de avisos por correo, los respaldos de la clave de cifrado y los detalles de actualización están en [la guía de actualización](docs/actualizacion-y-validacion.md#seguridad-de-ingreso-v10).

Desde V11, **alon** es el superusuario: ingresa con Google Authenticator y administra **Gerentes y licencias**. El Gerente recibe los accesos que alon seleccione. El menú **Seguridad** contiene el autenticador y los controles de ingreso. Solo el superusuario activa las licencias del negocio; el Gerente y los empleados no pueden renovarlas desde sus cuentas. Ver el apartado V11 de la guía de actualización.

Al iniciar Alon se prepara también la tienda para celulares del mismo Wi-Fi, en segundo plano y sin mantener una ventana abierta. La notebook debe estar encendida y conectada. Se puede iniciar por separado con Iniciar-Tienda-Wifi.ps1 -SegundoPlano; sin ese parámetro conserva el modo de ventana manual. Si cambia la dirección de Wi-Fi, el iniciador reemplaza únicamente el puente identificado de este proyecto.

En esta instalación, el acceso Alon.lnk de Inicio de Windows ejecuta Inicio-Automatico-Alon.ps1 al iniciar sesión con el usuario actual. Inicia Alon, espera la conexión Wi-Fi durante el arranque y registra el resultado en .validation/runtime/inicio-automatico.log. MySQL debe estar configurado como servicio automático. La dirección vigente aparece en ese registro; puede cambiar si el router asigna otra IP. Detener-Alon.ps1 detiene también el acceso Wi-Fi. No se publican la gestión ni la base de datos en la red.

## Backup del sistema de gestión

Ingresar como superusuario **alon** y abrir **Backup → Crear backup completo**. Al finalizar aparece en el historial y puede descargarse como ZIP. Las copias se guardan, por defecto, en `backend/data/backups/`; no se borran automáticamente. Conservar otra copia fuera de la PC. Este respaldo incluye toda la instalación y todos sus comercios. El superusuario puede habilitar Backup al gerente desde Gerentes y licencias; el gerente con ese permiso puede delegarlo a usuarios de rol Personalizado desde Usuarios y accesos. Sin el permiso no se puede crear, listar ni descargar copias.

El ZIP contiene `database.sql` (base completa, usuarios e historial de migraciones), `article-images/`, la clave `security/mfa.key` si existe y las instrucciones de restauración. Contiene datos privados y secretos sin cifrado: guardar en un medio protegido. Crear las copias sin altas ni cambios de fotos en curso; MySQL se vuelca con una transacción consistente y las imágenes se copian después. No realizar cambios de estructura mientras se crea un backup.

Se necesita `mysqldump` compatible con el servidor y un usuario MySQL con permisos de lectura del esquema. En Windows se detecta MySQL 8.4/8.0 en Program Files; también puede configurarse `ALON_MYSQLDUMP` con la ruta al ejecutable. `ALON_BACKUP_DIRECTORY` cambia la carpeta de destino. No se incluye la contraseña de conexión, la configuración del entorno ni el código del programa; conservarlos por separado.

Para restaurar: detener Alon, comprobar el respaldo en una base de prueba, importar `database.sql` con MySQL y reponer imágenes y `mfa.key` en sus rutas configuradas. El volcado usa el nombre original de la base: revisar el destino antes de importar. También puede recuperarse un ZIP completo desde Backup → Restaurar backup, como se detalla abajo.

## Eliminación y campos obligatorios

Los listados de Artículos, Clientes y Proveedores permiten eliminar definitivamente un registro. La confirmación abre con Cancelar seleccionado: Enter cancela por defecto; la eliminación requiere elegir Sí, eliminar definitivamente. Si existen operaciones o registros vinculados, se informa el motivo y se conserva el historial. En artículos nuevos se eliminan también el movimiento de stock inicial y las referencias de su galería; cualquier otra operación de stock impide la eliminación.

Al intentar guardar un formulario incompleto, los campos inválidos muestran borde y mensaje, y la pantalla se desplaza al primero para completarlo. La marca se retira cuando el dato queda válido.
## Restaurar backup desde Alon

Con el permiso **Backup**, abrir **Backup → Restaurar backup**, seleccionar el ZIP (máximo 512 MB), escribir `RESTAURAR` y elegir **Confirmar restauración**. Cancelar recibe el foco inicial: Enter cancela por defecto. No se inicia la recuperación al seleccionar el archivo ni se reenvía automáticamente si se pierde la conexión.

El archivo debe ser un ZIP generado por Alon, con todas las tablas y la misma estructura de la versión instalada, hasta 2 GB descomprimidos. La recuperación conserva la estructura y el historial de migraciones actuales; reemplaza los registros de toda la instalación, imágenes y clave del autenticador. Las columnas generadas se recalculan por MySQL y las fechas TIMESTAMP conservan su instante UTC. No se importan instrucciones SQL arbitrarias ni se ejecutan cambios de estructura incluidos en el archivo. Se rechazan tablas incompatibles, referencias incompletas, fotos faltantes y secretos de autenticador sin su clave correspondiente.

Antes de reemplazar los datos se crea un backup completo del estado actual, visible en el historial después de volver a ingresar. Si no puede crearse esa copia, no se continúa. Durante la restauración, la única instancia de API debe atender toda la gestión y tienda: se drenan las solicitudes activas y se bloquean nuevas operaciones. No usar otras instancias de Alon ni modificar la base con herramientas externas durante el proceso. Al finalizar se invalidan las sesiones de gestión y de tienda; se ingresa con los usuarios, contraseñas y autenticador del backup restaurado.

La base se recupera mediante una transacción InnoDB y se comprueban las referencias antes de confirmarla. Los errores ordinarios revierten datos y archivos. Si se pierde la conexión durante la confirmación, falla una reversión o se interrumpe el servidor durante el cambio de archivos, el sistema bloquea operaciones para revisión, incluso después de reiniciar cuando existe el marcador `backend/data/backups/restore-recovery-required.txt` (o en `ALON_BACKUP_DIRECTORY`). Ese marcador identifica la copia previa y, cuando corresponde, las imágenes anteriores; no borrarlo para eludir el bloqueo. Un administrador debe recuperar y comprobar base, imágenes y clave con la copia previa antes de retirar el marcador y reiniciar. Los ZIP previos nunca se borran automáticamente.

## Contabilidad y períodos mensuales (V12)

El menú Contabilidad reúne el informe mensual para el contador, exportación ZIP/CSV, impresión y registro auxiliar de impuestos. El control mensual y la carga con impuestos son opciones independientes, desactivadas por defecto. Los cierres conservan versiones del informe, bloquean cargas y correcciones del mes, y la reapertura requiere motivo. El acceso incluye costos y se asigna con el permiso Contabilidad. Consultar [la guía de contabilidad](docs/contabilidad.md).

Para Responsable Inscripto, el desglose se toma de comprobantes reales; no se calcula crédito fiscal desde totales históricos. El resumen de IVA es parcial y no sustituye IVA Simple, emisión de facturas, libros legales ni revisión profesional. Antes de actualizar la base, conservar un backup completo. Los respaldos de versiones anteriores requieren una versión compatible para su restauración, como se indica en la sección Backup.

La carga de indumentaria y calzado (V13) se describe en [docs/indumentaria.md](docs/indumentaria.md). Cada talle y color conserva stock propio.

## PDF de ventas

Al confirmar una venta se abre en pantalla su comprobante interno en PDF, usando los importes registrados, sin descargar archivos automáticamente. Desde el visor se puede imprimir en una impresora. Las ventas cortas ocupan la mitad superior de una hoja A4; los artículos largos y las ventas extensas continúan en páginas adicionales sin reducir la letra. El botón Ver / imprimir del listado permite abrirlo otra vez sin registrar otra venta. Si falla la vista previa, la venta queda guardada y se puede reintentar desde el visor. No emite factura fiscal ante ARCA.
