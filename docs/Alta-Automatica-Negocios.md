# Alta automática de negocios (instalaciones locales)

## Uso

1. Compilar el frontend y el backend actualizados. El servidor preparado queda en `backend/target-ready`.
2. Ejecutar desde Windows:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\Proyecto\Alon\Iniciar-Alon.ps1" -Reiniciar
   ```

3. Entrar como superusuario con el autenticador. En **Gerentes y licencias → Mis negocios Alon**, registrar el negocio o localizar uno existente.
4. Elegir **Habilitar sistema de …**. Indicar usuario, nombre, correo, contraseña de al menos 12 caracteres, meses iniciales de licencia y permisos del gerente.
5. Elegir **Crear base, gerente y habilitar negocio** y esperar el resultado. Solo se muestra **Listo para operar** si la nueva API responde con el identificador de esa instalación.
6. Abrir los enlaces desde ese panel. Gestión y tienda se sirven desde la misma instancia; no necesitan Vite ni iniciar otra terminal.

## Qué crea

- Un esquema MySQL `alon_n_<identificador aleatorio>`, con las migraciones completas y sin datos de otros comercios.
- Un usuario MySQL independiente `alon_u_<identificador>` con acceso a ese esquema. La contraseña aleatoria queda cifrada en la base central y se transmite al proceso mediante variables de entorno; no se incluye en el comando ni se devuelve al navegador.
- El comercio inicial con su nombre y licencia; un gerente DUENO y sus permisos. No copia el autenticador del administrador.
- Una carpeta `backend/data/businesses/<esquema>` con su ejecutable, frontend, imágenes, respaldos, claves y registros propios.
- Una instancia Java independiente con un máximo de heap de 384 MB y un puerto local reservado `20000 + id del registro`. Las cookies de sesión y aprobación tienen nombres distintos por negocio.
- Los enlaces de gestión, tienda y la referencia del esquema se actualizan en la ficha cuando el arranque se confirma.

El formulario de alta pide los datos del negocio y del gerente y los confirma juntos. Solo informa Negocio listo después de preparar la base, crear el gerente e iniciar la instancia. Las fichas anteriores sin instalación pueden completarse desde Ver > Habilitar sistema. Los gerentes del panel anterior siguen perteneciendo al comercio administrador: para una instalación nueva hay que usar el gerente del flujo **Habilitar sistema**.

## Requisitos de MySQL y configuración

El servidor MySQL debe estar disponible. La cuenta de aprovisionamiento necesita crear esquemas, crear usuarios y conceder privilegios sobre los esquemas nuevos. No se aumentan automáticamente los permisos de la cuenta actual.

Por defecto se usa la cuenta de conexión de Alon para esas tareas. Se puede configurar una cuenta separada con `ALON_PROVISIONING_USERNAME` y `ALON_PROVISIONING_PASSWORD` antes de iniciar el administrador. Las instancias de clientes reciben solamente su cuenta propia.

El iniciador establece `ALON_PROVISIONING_ENABLED=true` para la instalación central. Cada cliente se inicia con esa opción deshabilitada. Otros entornos pueden definir `alon.provisioning.jar`, `alon.provisioning.web`, `alon.provisioning.directory` y `alon.provisioning.client-host`; el último es `localhost` por defecto.

Guardar y respaldar la clave central `backend/data/security/mfa.key` por separado, además de la base central y los respaldos de cada negocio. Sin esa clave no se pueden recuperar las contraseñas de conexión cifradas.

## Reintentos y disponibilidad

El alta reserva la base, usuario, puerto y vencimiento una sola vez. Un bloqueo MySQL impide preparar dos veces simultáneamente el mismo negocio. Un reintento utiliza las mismas reservas, reanuda las migraciones y conserva la contraseña del gerente si ya fue creado. No extiende la licencia ni borra bases.

Si el sistema está preparado pero detenido, se puede elegir **Iniciar instalación existente**, sin volver a ingresar la contraseña del gerente. El administrador intenta recuperar las instalaciones preparadas al arrancar. **Consultar estado** comprueba si responde la instancia correcta; un registro LISTO guardado no se interpreta por sí solo como disponibilidad actual.

Los errores de permisos de MySQL y arranque se informan en el panel. Los detalles del proceso están en `servidor.log` y `servidor-error.log` de su carpeta. No cerrar el navegador durante un alta; si se pierde la respuesta, consultar el estado antes de reintentar.

Al recuperar una instalación ya encendida, el administrador comprueba que su servidor tenga el control de acceso actual. Si está usando una versión anterior, verifica el proceso y la reinicia con el ejecutable y frontend actualizados, sin recrear su base ni gerente. En Windows usa una consulta CIM por la ruta exacta del JAR cuando Java no entrega los argumentos del proceso. Si no puede verificarlo, informa el error y no confirma el bloqueo.

Al aplicar Inhabilitado o En preparación, se consulta el acceso real del comercio: debe confirmar el estado solicitado con HTTP 403 y los encabezados del control, o estar detenido. Un servidor que todavía responde normalmente no cuenta como bloqueo confirmado. En el listado se distingue el estado confirmado de un cambio pendiente de aplicar. Con el frontend actualizado, las pantallas abiertas de gestión y tienda verifican su disponibilidad cada cinco segundos y muestran el aviso de inhabilitación; el administrador central sigue disponible.

## Alcance

Esta versión prepara instalaciones en la misma computadora. Los enlaces `127.0.0.1` funcionan desde esa computadora. No crea DNS, dominios, certificados TLS, reglas de firewall ni un servicio de inicio automático de Windows. El administrador de Alon debe iniciarse para recuperar procesos después de reiniciar Windows. Cada negocio consume memoria y conexiones propias.

El abono no genera cargos automáticamente. Desde el listado se pueden aplicar los estados En preparación, Prueba, Activo, Inhabilitado (internamente SUSPENDIDO) y Baja, con un motivo opcional y auditoría del administrador. En preparación e Inhabilitado bloquean la API de gestión y de la tienda, incluso con sesiones ya iniciadas. Se conservan la base, archivos, usuarios y movimientos. Activo y Prueba permiten volver a operar si la instalación y la licencia están disponibles; no crean otra base ni renuevan la licencia. La baja es definitiva: detiene el proceso verificado, elimina el esquema MySQL independiente y su usuario, y borra la carpeta de instalación con imágenes, copias locales, ejecutable y registros. Solo conserva la ficha administrativa central, los abonos y la auditoría. No elimina copias descargadas fuera de la instalación. Requiere permisos DROP DATABASE y DROP USER. Las bajas anteriores no se eliminan automáticamente: hay que confirmar la baja definitiva desde el listado.

El control de acceso usa el archivo local `service-state` de cada instalación y se verifica en cada solicitud; no necesita compartir la base central con el cliente. El proceso puede permanecer encendido para monitoreo mientras está inhabilitado, pero no puede procesar operaciones ni pedidos. Si una instalación anterior no tiene este control, al inhabilitarla o darla de baja se detiene su proceso Java únicamente después de verificar la ruta exacta de su ejecutable; se actualizará al volver a iniciarla. Si Windows no permite verificar o detener ese proceso, el panel informa el error y no confirma el cambio. El formulario de edición general conserva el estado: los cambios se realizan desde las opciones del listado para sincronizar el control de acceso.

La licencia inicial se aplica en el alta; este flujo no renueva licencias de instalaciones existentes. La publicación y operación permanente en hosting necesitan configuración adicional de supervisión, accesos públicos, TLS y respaldos.

La compilación y las pruebas unitarias no sustituyen la prueba de creación y arranque contra el MySQL de destino. Antes del primer cliente, comprobar con un negocio de prueba: acceso del gerente, catálogo vacío, carga de un artículo, tienda propia y ausencia de datos del comercio administrador.


## Confirmación de baja definitiva

La opción predeterminada es **No dar de baja**. Enter cancela la confirmación, incluso si el nombre ya está escrito o el foco está en Eliminar definitivamente. Para borrar hay que escribir el nombre exacto del negocio y pulsar explícitamente el botón de eliminación. Inhabilitar sigue siendo reversible.

La baja usa el mismo bloqueo por negocio que el alta y arranque. Antes de borrar se marca la baja iniciada y se deshabilita el reinicio automático. Se comprueban el esquema y usuario generados, la base central protegida, la carpeta y el proceso exactos. Una baja incompleta no permite reactivación: debe reintentarse hasta terminar. Las operaciones DROP IF EXISTS y la limpieza de archivos permiten reanudar sin recrear datos. Solo se confirma eliminado_en al terminar todos los pasos; si faltan permisos o no se puede detener la instancia, se muestra un error.


## Enlaces por nombre comercial

Al registrar un negocio se generan y muestran automáticamente dos enlaces locales: gestión `http://127.0.0.1:20001/las-tres-l` y tienda `http://127.0.0.1:20001/tienda/las-tres-l` (el puerto identifica la instalación). El nombre se convierte a minúsculas y guiones, sin acentos; no hace falta escribir las URL. Se reserva la dirección al registrar y queda disponible al habilitar la instalación. No crea dominios públicos ni DNS. La dirección guardada es estable aunque después se edite el nombre comercial. Las rutas anteriores `/` y `/tienda` siguen funcionando. Al reanudar se comprueba que la instancia tenga las rutas actualizadas antes de publicar sus enlaces.

## Rubros de indumentaria

La indumentaria y el calzado usan los mismos rubros que los artículos. Primero se crea el rubro en Rubros y se selecciona al cargar la prenda. La carga por talles se habilita después de elegirlo y conserva el rubro en el borrador. Todos los talles y los nuevos talles agregados heredan el rubro. Los filtros del catálogo y la tienda usan ese rubro y no mezclan modelos de rubros distintos. Las prendas anteriores conservan su clasificación.

## Confirmar negocio y gerente

La confirmación requiere nombre comercial, datos del negocio, usuario, nombre, correo y contraseña del gerente y meses de licencia. El backend valida al gerente antes de reservar la ficha y asigna el rol DUENO con todos los permisos del comercio. La cuenta no recibe rol SUPER ni acceso a la administración general de Alon, a otros negocios o a sus licencias.

POST /api/v1/super/businesses/confirm recibe requestId, business y manager. El token de alta se guarda con una restricción única y la reserva se confirma en una transacción antes del aprovisionamiento. Los reintentos del mismo token usan la misma ficha y el mismo gerente reservado. La contraseña no se almacena en la ficha central; en el comercio se guarda su hash. Si falla MySQL o el arranque, la respuesta informa alta incompleta, conserva el identificador y el formulario no informa éxito. Los enlaces se presentan como listos solo con running=true. No se guarda la contraseña del formulario en el almacenamiento del navegador.

## Acceso automático al crear un usuario del comercio

El alta de un usuario por su gerente autoriza el navegador que hizo esa creación, con su IP y User-Agent actuales, por hasta 90 días. El usuario sigue ingresando con su usuario y contraseña. La autorización usa una cookie HttpOnly individual por usuario y por instancia, no solamente la coincidencia de IP o del nombre del navegador. Crear un segundo usuario no reemplaza la autorización del primero.

Un navegador nuevo, una ventana privada sin la cookie de autorización, otra IP o un User-Agent distinto generan la solicitud habitual de Seguridad > Permitir. Se siguen verificando estado del usuario, versión de autenticación, revocación y vencimiento en cada operación. Si no se puede identificar el contexto de alta, no se aprueba automáticamente. Las autorizaciones anteriores mantienen la compatibilidad con la cookie anterior y sus propios controles. No se aprueban retroactivamente solicitudes pendientes de origen desconocido.

Estos cambios llegan a los negocios ya instalados al reiniciar el administrador, que comprueba y actualiza sus copias del servidor y de la interfaz. La IP utilizada es la dirección remota que entrega el servidor; al publicar detrás de un proxy debe configurarse la cadena de proxies de confianza, sin aceptar encabezados arbitrarios enviados por el cliente como prueba de origen.

### Volver al ingreso y entrar como gerente

Volver al ingreso limpia la verificación de la sesión y conserva la solicitud en la base hasta su vencimiento. Antes ese botón la marcaba CANCELADO y el gerente no podía verla; ese comportamiento se corrigió. Cuando un gerente ingresa con su contraseña, solo se autorizan automáticamente las solicitudes pendientes de su comercio que prueban el mismo navegador mediante su cookie privada y coinciden en IP y User-Agent. También se admiten solicitudes vigentes marcadas CANCELADO por la versión anterior de ese botón, con las mismas pruebas. No basta coincidir en IP o marca de navegador, y no se rehabilitan filas rechazadas o revocadas. Las solicitudes de otro navegador permanecen visibles para Permitir. El acceso Permitir accesos es visible para el gerente tanto en la cabecera como en Usuarios y accesos.
