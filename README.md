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

Para abrir la tienda desde un celular conectado al mismo Wi-Fi, ejecutar `Iniciar-Tienda-Wifi.ps1` después de iniciar Alon. El script muestra la dirección de la tienda (puerto 5174); mantener la PC y la ventana de ese iniciador encendidas. La gestión sigue siendo local. Consultar la sección Tienda desde el celular por Wi-Fi en la guía de actualización.
