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

V2 añade columnas de autor con valor nulo para movimientos anteriores; no inventa responsables históricos. Las correcciones y movimientos nuevos sí guardan al usuario. No cambia las contraseñas existentes ni carga cuentas de prueba automáticamente.

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


## Publicación de artículos y ubicación de compradores (V6)

La migración V6 agrega `cargar_web`, desactivado para artículos nuevos y existentes. Después de actualizar, marcar **Cargar en la web** y guardar los artículos que deban aparecer en la tienda. La opción se respeta en el catálogo, rubros, carrito y envío de pedidos; los pedidos ya recibidos conservan su contenido.

El redondeo queda seleccionado para artículos nuevos; las preferencias de los existentes se conservan. El registro de compradores pide código postal, ciudad, provincia y país; Argentina aparece seleccionada y se puede cambiar. Estos datos se guardan y se incluyen en el domicilio del pedido. Las cuentas existentes pueden seguir ingresando con sus credenciales.


## Identidad del negocio y aumentos por rubro (V7)

V7 agrega slogan y colores al comercio. El dueño puede modificarlos en **Modificar negocio**. La identidad se comparte con la tienda online; las pestañas abiertas se actualizan al recibir el cambio, recuperar el foco o dentro de 30 segundos mientras están visibles. Los valores predeterminados conservan la paleta base de Alon.

En **Artículos y stock → Modificar precios**, seleccionar un rubro y un porcentaje positivo. La vista previa muestra la cantidad total de artículos activos y hasta diez ejemplos. Al aplicar se actualizan todos los precios de venta del rubro, incluidos los artículos habilitados en la web, respetando el redondeo individual. Los costos, stock y márgenes configurados no cambian; una compra posterior conserva la regla existente de recalcular el precio desde esos costos y márgenes.

La aplicación es atómica, auditada y protegida frente a reintentos. Si cambian los precios o el conjunto de artículos después de la vista previa, se requiere revisarla nuevamente. Los usuarios necesitan acceso a Artículos y a Costos o Compras para realizar el aumento.


## Confirmación de pagos y origen del dinero (V8)

Guardar una venta o una factura abre una ventana para elegir la condición y el medio de pago. La cuenta corriente no muestra medio de pago. En compras, la escritura se realiza después de responder la confirmación final de actualización de costo. Cancelar conserva los artículos y los datos cargados.

En compras, **CONTADO DE CAJA** selecciona automáticamente Efectivo como medio fijo, requiere una caja abierta en la fecha del comprobante, utiliza el medio Efectivo y registra el egreso en el arqueo. **CONTADO** permite elegir Efectivo, Transferencia o Mercado Pago y registra el pago al proveedor fuera de caja, sin alterar el arqueo. **CUENTA CORRIENTE** deja el total pendiente; no solicita medio de pago ni registra un pago inicial.

V8 guarda la condición y el medio en la compra y permite que un pago externo tenga `caja_id` nulo. Las compras anteriores se clasifican conservando el medio de su comprobante original. Las solicitudes antiguas con `CONTADO` mantienen el comportamiento de pago desde caja.


## Carga de compras y confirmación de costo

La carga inicia con foco en Proveedor, con **Proveedor sin nombre** y **Remito** seleccionados. El proveedor predeterminado se crea una sola vez por comercio cuando se necesita y queda disponible también en los selectores de artículos. Enter en Proveedor abre el listado flotante con búsqueda, páginas, flechas y Enter para elegir.

Después de confirmar el pago, **¿Este comprobante modifica el precio de costo?** permite finalizar con **S / Sí** o **N / No**. Sí reemplaza únicamente el costo del producto por el costo unitario ingresado, sin incorporar gastos adicionales al costo del catálogo ni recalcular precio de venta, flete, margen o redondeo. Si el artículo se repite con costos distintos, queda el último costo ingresado. No conserva todos esos valores. Ambas opciones registran el comprobante, sus importes y el ingreso de stock. Volver permite revisar las condiciones antes de guardar.

Las compras se ordenan por registro más reciente, incluso cuando el comprobante tiene una fecha anterior. El historial de caja muestra primero las sesiones cerradas o abiertas más recientemente, con desempate por fecha y número de sesión.


## Diez plantillas de tienda online (V9)

En **Modificar negocio**, el dueño elige entre Clásica, Minimalista, Boutique, Urbana, Editorial, Mercado, Natural, Tecnológica, Artesanal y Vitrina. El selector muestra miniaturas y una vista previa más grande antes de guardar. Cada diseño cambia la composición, tipografía, bordes o proporciones de las tarjetas; todos comparten los mismos componentes de catálogo, cuenta, carrito y pedidos.

Se pueden personalizar colores, nombre y slogan, además del título y la descripción de bienvenida de la tienda. Los diseños de tienda no alteran la distribución de la aplicación de gestión. Cambiar de plantilla no vacía carritos ni modifica productos, precios, stock, clientes o pedidos. La identidad guardada se sincroniza al recuperar el foco y mediante consultas periódicas en pestañas abiertas.

V9 agrega plantilla y textos a `comercios`. El valor inicial Clásica y los textos anteriores conservan el aspecto existente. Las solicitudes antiguas que envían solamente identidad y colores conservan la plantilla y los textos ya guardados. La API rechaza identificadores de plantilla fuera del catálogo permitido y mantiene los controles de dueño, comercio y auditoría.

## Seguridad de ingreso (V10)

El dueño inicia con su contraseña y luego un código de una aplicación autenticadora (Google Authenticator, Microsoft Authenticator u otra compatible). En el primer ingreso debe escanear el QR, confirmar un código y guardar los diez códigos de recuperación antes de entrar. Estos códigos reemplazan al teléfono una sola vez cada uno; no se envían por correo. El QR se genera dentro de Alon. La base guarda los códigos de recuperación solamente como hashes y el secreto del autenticador cifrado.

En **Usuarios → Seguridad de ingreso**, cada dueño configura su correo, aprueba o rechaza solicitudes de empleados, revoca navegadores, consulta eventos y genera nuevos códigos. Cambiar el autenticador requiere contraseña y segundo factor vigente. Generar nuevos códigos anula todos los anteriores. El correo pertenece a la cuenta del dueño, no a la configuración general del negocio.

Un empleado que usa otro navegador, un perfil privado o una IP distinta necesita autorización del dueño, después de validar su contraseña. Las solicitudes vencen a los 30 minutos y la pantalla de espera a los diez minutos; al volver a iniciar sesión puede retomar una solicitud vigente. Una aprobación dura hasta 90 días y puede revocarse inmediatamente. Los cambios de contraseña o permisos invalidan las autorizaciones anteriores. Las sesiones duran como máximo ocho horas. Ventanas privadas del mismo navegador pueden compartir cookies hasta que se cierren todas. No se identifica físicamente la computadora: se usa una cookie opaca, HttpOnly y SameSite Strict, vinculada al usuario y a su aprobación. `ALON_REQUIRE_IP_APPROVAL=false` permite desactivar únicamente la comprobación de cambios de IP si la red es variable; por defecto está activa.

V10 invalida sesiones existentes para exigir el nuevo ingreso. Antes de actualizar, guardar una copia de MySQL. Después de la primera activación, respaldar también **backend/data/security/mfa.key** en un lugar restringido, separado de la base. `ALON_SECURITY_KEY_FILE` permite elegir otra ubicación persistente. Perder esta clave impide descifrar los autenticadores guardados; no se debe borrarla ni reemplazarla para intentar recuperar una cuenta. Los códigos de recuperación deben guardarse fuera de Alon. Cinco verificaciones incorrectas bloquean nuevos intentos durante cinco minutos. No hay acceso alternativo sin verificar identidad.

Los avisos se muestran dentro de Alon. Para enviar también correos, configurar en el entorno del proceso del servidor: `ALON_SMTP_HOST`, `ALON_SMTP_PORT` (587 por defecto), `ALON_SMTP_USERNAME`, `ALON_SMTP_PASSWORD` y `ALON_SECURITY_MAIL_FROM`. El servidor exige STARTTLS. `ALON_SMTP_AUTH` vale true por defecto. Reiniciar el servidor después de configurar estas variables. No guardar contraseñas de correo en Git. Sin configuración SMTP la pantalla lo informa y los avisos quedan pendientes, sin simular entregas. Los mensajes no incluyen contraseñas, secretos, QR ni códigos de recuperación. La bandeja intenta cada envío hasta tres veces y muestra fallos en Seguridad de ingreso.

Para acceso por Internet, publicar con HTTPS y `SESSION_COOKIE_SECURE=true`; el HTTP local de 127.0.0.1 sirve para esta instalación de desarrollo. Si se agrega un proxy, configurar explícitamente sus cabeceras confiables y no aceptar cabeceras de IP proporcionadas directamente por clientes.

## Superusuario, Gerente y licencias (V11)

La cuenta personal de administración de la plataforma se llama **alon** y tiene el rol exclusivo **Superusuario**. En esta instalación se adapta la cuenta principal existente, conservando su correo y el autenticador ya vinculado, si lo tiene. La vinculación inicial requiere la contraseña anterior; el ingreso posterior tiene un botón específico para **alon con Google Authenticator**, que acepta un código actual o uno de recuperación de un solo uso. Nunca se puede vincular un teléfono desde esa ruta sin verificar previamente el acceso inicial. El bloqueo por intentos y el control de reutilización de códigos también se aplican al superusuario.

El menú **Seguridad** contiene la configuración del autenticador, correo y aprobaciones de navegadores. El aviso superior aparece cuando hay solicitudes pendientes. El perfil anterior Dueño se muestra como **Gerente**; el identificador interno DUENO se conserva por compatibilidad con la base y las integraciones existentes. El Gerente recibe exactamente los permisos que alon seleccione, sin acceso automático a módulos, costos, descuentos o administración del negocio. Solo alon crea o modifica Gerentes. Un Gerente con permiso de Usuarios puede administrar empleados, pero no modificar a otro Gerente ni al superusuario, y no puede delegar permisos que no posea. Cambiar permisos o contraseña revoca las sesiones anteriores.

En **Gerentes y licencias**, exclusivo del superusuario, se crean Gerentes con usuario, nombre, correo, contraseña y secciones habilitadas. Un generador produce contraseñas aleatorias; las existentes nunca se recuperan en texto. El correo queda en la cuenta del Gerente. El mismo panel permite editar accesos y generar claves para otorgar de 1 a 120 meses de licencia.

**Solo alon puede activar o renovar la licencia.** Crear un Gerente o emitir una clave no suma meses por sí solo: se debe pulsar **Activar esta licencia** en el panel del superusuario. La clave es aleatoria, pertenece al negocio, se muestra una sola vez, vence a los 30 días si no se activa y solo se guarda como hash. Emitir una nueva anula las anteriores pendientes. Una activación no puede repetirse para sumar meses dos veces. El servidor bloquea el negocio durante el cálculo y consumo de la clave. Los meses se cuentan desde el vencimiento si la licencia sigue vigente, o desde la activación si ya venció; se usan meses calendario.

La licencia habilita a todo el negocio, incluidos Gerente y empleados. Al vencer, las operaciones quedan bloqueadas en el servidor y la interfaz informa que se debe contactar a alon. El catálogo público se conserva visible, pero no se admiten nuevos pedidos online. El superusuario conserva acceso para renovar. Los Gerentes no tienen formulario de activación ni autorización en la API para hacerlo.

V11 agrega columnas de vigencia a comercios, amplía la restricción de roles y crea el historial de claves con emisor, fecha y activación. Los negocios existentes conservan su funcionamiento hasta que alon emite su primera licencia; no se asignan meses arbitrarios durante la actualización. Emitir la primera clave activa el control de licencia y requiere activarla para que los demás usuarios operen. Respaldar MySQL antes de actualizar y conservar también la clave de cifrado del autenticador indicada en V10. Los datos comerciales no se eliminan cuando vence una licencia.

### Ingreso del Gerente con credenciales del formulario

El Gerente ingresa directamente con el usuario y la contraseña que alon cargó en **Gerentes y licencias**. No necesita vincular Google Authenticator ni introducir códigos de recuperación. Esta regla también se aplica a Gerentes que ya hubieran configurado un autenticador antes del cambio. Alon conserva su ingreso con Google Authenticator y los empleados conservan las aprobaciones de navegador. La vigencia de licencia, los permisos y la revocación de sesiones siguen verificándose en el servidor. En Seguridad, el Gerente puede administrar solicitudes si tiene ese permiso; su correo y contraseña se administran desde alon. Los controles del autenticador se muestran solamente al superusuario.

El superusuario puede asignar contraseñas de **8 a 128 caracteres al Gerente**, tanto al crearlo como al modificarlo. En la edición, dejar el campo vacío conserva la contraseña existente. Se admiten contraseñas de ocho dígitos; no se exige que sean exclusivamente numéricas.

## Tienda desde el celular por Wi-Fi

Con Alon iniciado, la tienda compilada se publica para la misma subred Wi-Fi mediante `Iniciar-Tienda-Wifi.ps1`. La dirección actual es **http://192.168.1.82:5174/tienda**. El celular debe estar conectado al mismo Wi-Fi y la PC debe permanecer encendida. Si la PC recibe otra dirección IP, el iniciador muestra la nueva URL. Después de reiniciar la PC, iniciar Alon y ejecutar este script; mantener su ventana abierta mientras se utiliza la tienda.

```powershell
.\Iniciar-Alon.ps1
.\Iniciar-Tienda-Wifi.ps1
```

El acceso de Wi-Fi sirve `frontend/dist`, el catálogo, las fotos y la API de compradores. Las rutas de gestión y los archivos fuente se rechazan. La administración continúa en 127.0.0.1:5173 y la API local en 127.0.0.1:8080. Las conexiones del servidor de tienda se limitan a la subred real de la interfaz Wi-Fi. Se aprovechan los permisos existentes de Node.js de esta instalación; no se cambiaron las reglas del firewall ni el router. No es publicación por Internet.

Los cambios de productos, precios y personalización se consultan desde la API. Para cambios en el código de la tienda se debe ejecutar `npm run build` desde frontend, porque la dirección del celular usa la versión compilada. El carrito admite el HTTP de la Wi-Fi con claves aleatorias de pedido generadas mediante getRandomValues cuando randomUUID no está disponible.

Las cuentas de compradores de la tienda online también admiten contraseñas de **8 a 128 caracteres**, incluidos ocho números. El registro por Wi-Fi usa el mismo límite que el servidor; las cuentas existentes conservan sus credenciales. Los compradores ingresan con teléfono y contraseña para enviar el carrito como pedido.

### Cambiar la contraseña del comprador en la tienda

Con la cuenta compradora ya iniciada, el encabezado de la tienda ofrece **Cambiar contraseña**. El formulario solicita únicamente la nueva contraseña, de 8 a 128 caracteres; no pide la anterior ni verificación por correo. No es un restablecimiento anónimo por teléfono: la cuenta se identifica desde la sesión existente. El cambio actualiza solo a ese comprador, conserva su sesión actual y revoca sus otras sesiones. El servidor exige la sesión y el token CSRF y verifica la huella de la credencial antes de reemplazarla. El cambio también está disponible en el acceso por Wi-Fi del celular después de recargar la tienda.
