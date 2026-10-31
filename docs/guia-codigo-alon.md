# Cómo leer el código de Alon

La interfaz está en `frontend/src` y el servidor en `backend/src/main/java/ar/com/alon`.

- `views/AppView.tsx` organiza el ingreso y el menú de la aplicación.
- `pages/` contiene las pantallas de ventas, compras, artículos, caja y administración.
- `components/` reúne controles compartidos, como cantidades, buscadores y tablas.
- `lib/http.ts` se comunica con el servidor y evita duplicar una operación al reintentar.
- `hooks/useApiRows.ts` carga los listados por páginas y actualiza las búsquedas.
- `shop/ShopApp.tsx` organiza la tienda web y su carrito.

En el servidor, cada módulo sigue esta organización:

- Los archivos `Controller` reciben solicitudes y devuelven respuestas.
- Los archivos `Service` aplican las reglas y guardan los cambios.
- Los archivos `Queries` consultan los datos para mostrarlos.
- La carpeta `dto` define los campos aceptados y sus validaciones.

Los comentarios al comienzo de cada archivo explican su propósito. Los comentarios dentro de las funciones señalan pasos importantes, como guardar una venta completa, registrar un ajuste o evitar un cobro duplicado. `@Transactional` indica que los cambios se confirman juntos; si falla una parte, se deshace toda la operación.

Las consultas de gestión usan `/api/v1/alon/`; las escrituras usan `/api/v1/operations/` y la tienda pública usa `/api/v1/shop/`.

## Configuración de instalaciones existentes

La base predeterminada para instalaciones nuevas se llama `alon`. Si ya tenés una base con operaciones, conservá `SPRING_DATASOURCE_URL` apuntando a su nombre y dirección actuales. Este cambio de nombres del código no renombra ni modifica bases existentes.

El perfil opcional de pruebas locales es `local`, con la propiedad `alon.local-accounts`. Solo crea cuentas de ejemplo si no hay usuarios. No cambia usuarios ni contraseñas existentes.

Las migraciones Flyway ya publicadas conservan su contenido y sus nombres para que las bases existentes puedan seguir validando su historial.
