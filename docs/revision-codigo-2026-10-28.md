# Revisión de código de Alon

> Registro de la revisión inicial. Las correcciones posteriores se detallan en [el seguimiento de correcciones](correcciones-revision-2026-10-28.md).

Fecha: 28/10/2026. Alcance: fuentes Java, React/TypeScript, CSS, SQL, configuración, dependencias declaradas, documentación y pruebas del repositorio.

## Dictamen

El proyecto tiene una base razonable para una demostración local, pero no cumple todavía las condiciones de calidad, seguridad y mantenibilidad necesarias para operar con comercios reales. Compilar correctamente no demuestra integridad de los procesos comerciales.

Se realizó revisión estática y se ejecutaron las comprobaciones indicadas abajo. No se inició la aplicación contra MySQL, no se ejecutaron migraciones ni se alteraron datos. Los defectos funcionales siguientes están sustentados en el flujo del código; requieren pruebas de regresión e integración al corregirse. No se modificó código de aplicación durante esta revisión.

## Hallazgos prioritarios

### 1. Alta — La instalación documentada no es reproducible

Referencias: `database/demo-data.sql:104`, `database/demo-data.sql:137`, `database/migrations/V005__sesiones_caja_mismo_dia.sql:4`, `database/migrations/V006__fecha_hora_ventas.sql:3`, `README.md`.

El esquema inicial ya incluye `numero_sesion`, `sesion_abierta`, los índices de sesiones y `ventas.fecha_hora`. V005 intenta volver a crear esas columnas y eliminar `uq_caja_fecha`, que no existe en ese esquema; V006 vuelve a crear `fecha_hora`. Seguir el inicio rápido sobre una base nueva produce errores DDL. Además, Compose y la configuración predeterminada apuntan a `alon`, mientras los scripts crean `alon_demo`; V008 no selecciona base explícitamente.

Acción: establecer un esquema inicial y una cadena coherente de migraciones, con seguimiento de versiones; separar datos demo del esquema y comprobar una instalación vacía en una base desechable. Preservar un camino de actualización para instalaciones existentes.

### 2. Alta — Editar compras deja costos y precios inconsistentes

Referencia: `backend/src/main/java/ar/com/alon/model/OperationsModel.java:338-369`.

`updatePurchase` recalcula costo solo cuando aumenta la cantidad. Cambiar una compra de 10 unidades a $100 por 10 unidades a $200 cambia la factura, pero deja el costo del artículo sin actualizar. Reducir cantidades tampoco revierte su valoración. Los gastos adicionales se distribuyen en una variable `allocated` que no se usa al actualizar artículos. Si la compra original repite un artículo, `oldQuantities.put` conserva solo la última cantidad: guardar la misma compra puede aumentar stock indebidamente.

Acción: agregar casos de regresión para costo, flete, cantidades y renglones repetidos; definir una política de corrección de compras recibidas y valoración histórica. Preferir correcciones auditables a sobrescribir operaciones confirmadas.

### 3. Alta — Es posible perder el último dueño activo

Referencia: `backend/src/main/java/ar/com/alon/model/AuthModel.java:68-88`.

`setActive` impide desactivar la propia cuenta, pero `updateUser` permite al único dueño cambiar su rol a vendedor. Después ya no existe una cuenta autorizada para administrar usuarios. Tampoco hay una comprobación transaccional que garantice conservar al menos un dueño activo ante cambios concurrentes.

Acción: proteger esa invariante con transacción y bloqueo; probar degradación propia, desactivación y modificaciones simultáneas de dueños.

### 4. Alta antes de producción — Acceso demo y protección de autenticación insuficientes

Referencias: `backend/src/main/java/ar/com/alon/model/AuthModel.java:29-34,96-115`, `backend/src/main/java/ar/com/alon/api/auth/PermissionFilter.java`.

El arranque crea cuentas con contraseña pública `demo` cuando no hay usuarios. Las contraseñas nuevas admiten cuatro caracteres; no se observa limitación de intentos. PBKDF2-HMAC-SHA256 usa 210.000 iteraciones sin guardar ese parámetro en el hash. La aplicación sí usa sal aleatoria y comparación resistente a diferencias de tiempo, lo cual es positivo.

Acción: limitar la carga demo a un perfil explícito, endurecer la política de acceso y usar un codificador versionado con actualización de hashes al iniciar sesión. OWASP indica 600.000 iteraciones para PBKDF2-HMAC-SHA256: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html . Revisar además protección CSRF, vencimiento y revocación de sesiones, cookies y HTTPS antes de desplegar; no se verificó una explotación de CSRF y las políticas del navegador también influyen.

### 5. Media — Listados vacíos o fallidos se sustituyen por datos inventados

Referencia: `frontend/src/views/AppView.tsx:39-53,207-213,280,315`.

El hook ignora errores y devuelve una lista vacía. Los componentes usan datos locales si `apiRows.length` es cero. Una respuesta válida sin artículos, clientes o proveedores muestra registros ficticios; ocurre también al consultar inactivos y ante fallos iniciales de red o permisos. Si había datos anteriores, un error puede dejarlos visibles sin aviso.

Acción: representar carga, éxito vacío, error y sesión vencida por separado; permitir datos ficticios solo mediante un modo demo explícito.

### 6. Media — Las cantidades pierden precisión o pueden quedar en cero

Referencia: `backend/src/main/java/ar/com/alon/model/OperationsModel.java:85,110,127,529-539`.

`createArticle` pasa stock inicial y mínimo por `nonNegative`, que redondea a dos decimales aunque la base guarda tres; `updateArticle` trata el stock inicial con tres. `positiveQuantity` comprueba positividad antes de redondear: 0,0001 se acepta y termina en 0,000, generando operaciones sin cantidad o divisiones por cero en compras con reparto de gastos.

Acción: separar validadores de dinero y cantidad, validar después de normalizar y definir si se rechaza precisión excesiva. Para ajustes de stock, guardar delta con signo: actualmente se usa valor absoluto y una baja queda registrada como positiva.

### 7. Media — El informe SQL de caja no trata anulaciones como el cierre de la API

Referencias: `database/migrations/V005__sesiones_caja_mismo_dia.sql:12`, `database/demo-data.sql:470`, `backend/src/main/java/ar/com/alon/model/OperationsModel.java:465-471`.

La vista suma ventas brutas y clasifica anulaciones entre egresos/gastos; la API resta `VENTA_ANULADA` de ventas y la excluye de otros egresos. La misma operación produce categorías y ventas netas distintas entre reportes y cierre.

Acción: acordar la semántica y centralizar el cálculo; comprobar venta y anulación en la misma sesión y en sesiones diferentes.

### 8. Media — No hay trazabilidad suficiente de cambios comerciales

Referencias: `backend/src/main/java/ar/com/alon/model/OperationsModel.java:362,509-511`, `database/migrations/V001__operaciones_persistentes.sql`.

Los movimientos de stock no guardan el usuario responsable. La modificación de compras borra y recrea detalles sin conservar la versión anterior ni quién la cambió. Esto contradice las reglas de auditoría descritas en `docs/alcance-mvp.md`.

Acción: obtener la identidad desde la sesión del servidor, registrar autor y motivo, y conservar revisiones o movimientos de corrección.

### 9. Media — Las restricciones de compras rechazan documentos válidos de distintos proveedores

Referencia: `database/demo-data.sql:83`.

La clave única es `(comercio_id, numero_documento)`. Dos proveedores del mismo comercio con el documento `0001-00000001` no pueden registrarse, aunque sus documentos sean distintos.

Acción: definir la identidad del comprobante incluyendo proveedor y, según la regla acordada, tipo de documento; migrar con comprobación de duplicados.

### 10. Media — Generación de identificadores de rubro expuesta a concurrencia

Referencia: `backend/src/main/java/ar/com/alon/model/OperationsModel.java:144-146`.

`MAX(id)+1` permite que dos altas concurrentes calculen el mismo identificador; una falla por clave duplicada aunque ambos rubros sean válidos.

Acción: usar generación de claves por la base, como en otras altas. Para nombres de rubro únicos, respaldar la comprobación previa con una restricción normalizada en base de datos.

## Mejoras de arquitectura y robustez

- **Responsabilidades:** `AppView.tsx` concentra 841 líneas y aproximadamente 100 KB; `OperationsModel` reúne todos los procesos comerciales y SQL. Dividir por funcionalidades, extraer cliente HTTP, componentes de formulario, servicios y repositorios. MVC no exige concentrar todo en una clase.
- **Contratos:** predominan `Map<String,Object>` y `Record<string,unknown>`. Introducir DTO de entrada/salida, validaciones de longitud, escala y elementos anidados; los campos nulos y las listas con elementos nulos no se manejan de manera uniforme. La dependencia de validación está presente, pero los controladores no usan `@Valid`.
- **Errores:** el consejo de excepciones aplica solo a operaciones. Usuarios duplicados o inexistentes pueden terminar como errores 500. Unificar respuestas y distinguir validación, ausencia, conflicto y fallo inesperado; evitar transformar cualquier `IllegalStateException` en conflicto de negocio.
- **Permisos:** el servidor actualiza permisos desde la base en cada petición, pero `/auth/status` devuelve la copia guardada en sesión. Tras editar un rol, la navegación puede seguir mostrando accesos obsoletos. El permiso `inicio` también entrega margen bruto desde `summary`; acordar si debe requerir acceso financiero explícito. Las ventas permiten descuentos de hasta 100% a cualquier usuario con permiso de ventas, sin permiso de descuento independiente.
- **Concurrencia y reintentos:** no se observan claves de idempotencia para ventas, compras y recibos; una respuesta perdida seguida de reintento puede duplicar operaciones. Los bloqueos de artículos siguen el orden recibido y pueden producir interbloqueos con órdenes inversos; adoptar orden estable y tratamiento de conflictos transitorios.
- **Escalabilidad:** artículos, clientes, proveedores y compras se cargan completos; ventas se limita a 200 en la vista sin controles de paginación. Introducir filtros y paginación del servidor antes de validar el objetivo de 99.999 registros.
- **Multi comercio:** las tres clases de modelo fijan comercio 1. Es una limitación demo documentada, no una implementación del aislamiento prometido. Obtener comercio autorizado desde identidad y reforzar relaciones y consultas con pruebas de aislamiento antes de ampliar el producto.
- **Fechas:** se mezclan `LocalDate.now()` y `LocalTime.now()` del servidor con fechas de Buenos Aires explícitas y fechas del navegador. Usar un reloj inyectable y una política uniforme por comercio.
- **Presentación monetaria:** `maximumFractionDigits: 0` oculta centavos en saldos, importes y recibos. Mostrar dos decimales donde el usuario revisa o confirma dinero; mantener cálculos definitivos en el servidor.
- **TypeScript:** no está configurado `strict`. La comprobación adicional con `--strict` pasó, por lo que activarlo tiene un costo inicial bajo. Evaluar comprobaciones adicionales de accesos por índice. Referencia: https://www.typescriptlang.org/tsconfig/strict.html .
- **Accesibilidad:** los desplegables personalizados con `role="combobox"` carecen de asociación completa con su lista y de navegación por flechas/selección por teclado. Verificar teclado y lector de pantalla. No se hizo inspección visual en navegador.
- **Entorno:** MySQL se publica con `3306:3306` en todas las interfaces del equipo, aunque la API se limita por defecto a loopback. Para desarrollo local, enlazarlo a `127.0.0.1`. Separar credenciales y ajustes de producción de los valores locales.
- **Documentación:** `frontend/README.md` afirma que los datos no se guardan en MySQL, contradiciendo el funcionamiento actual. Añadir instrucciones verificadas y requisitos de ejecución consistentes.
- **Automatización:** no hay configuración versionada de integración continua ni pruebas funcionales del frontend. Añadir una comprobación reproducible de compilación, análisis y pruebas críticas; evitar agregar herramientas que no resuelvan una necesidad concreta.

## Comprobaciones realizadas

| Comprobación | Resultado |
| --- | --- |
| `mvn test` | Correcto: 1 prueba, sin fallos. Solo prueba el controlador de salud mediante llamada directa; no verifica arranque, seguridad, SQL ni transacciones. |
| `npm run build` | Correcto: TypeScript y compilación Vite. |
| `npm run lint` | Finaliza correctamente con 6 advertencias: pureza en líneas 787, 807 y 829; estado dentro de efecto en 89; dependencias de efectos en 602 y 767. |
| `npx tsc -p tsconfig.app.json --strict --noEmit` | Correcto. |
| `npm audit --omit=dev --json` | 0 vulnerabilidades informadas. |
| `npm audit --json` | 0 vulnerabilidades informadas incluyendo desarrollo. |

La auditoría npm refleja el registro consultado en esta fecha y no garantiza ausencia de vulnerabilidades. No se ejecutó una auditoría de vulnerabilidades de dependencias Maven, prueba de carga, prueba de penetración, migración real ni recorrido integral con navegador y base de datos.

## Orden de trabajo sugerido

1. Corregir instalación, compras y preservación del último dueño; cubrirlos con pruebas de regresión.
2. Corregir precisión, movimientos de stock, informe de anulaciones y datos ficticios en listados.
3. Reforzar autenticación, validación, errores, permisos y auditoría comercial.
4. Modularizar código progresivamente, incorporar paginación y automatizar verificaciones.
5. Validar aislamiento, operación concurrente y despliegue antes de usar datos reales.
