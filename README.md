# Alon

Sistema web de gestión comercial para pequeños comercios argentinos, ofrecido como servicio a distintos negocios.

## Tecnologías

- Backend: Java 21 y Spring Boot 4.1.2.
- Base de datos: MySQL 8.4.
- Frontend: React, TypeScript y Vite.
- Moneda y zona horaria: ARS y `America/Argentina/Buenos_Aires`.

## Estado actual

La aplicación permite registrar clientes, proveedores, artículos, ventas, compras, gastos, aperturas y cierres de caja, y pedidos web en el comercio de demostración. Las ventas descuentan stock en una transacción y pueden actualizar cuenta corriente; las compras reciben stock, actualizan costos y saldos de proveedor, y sus pagos se reflejan en caja. La base `alon_demo` conserva además los datos ficticios iniciales (100 artículos, 20 clientes, 20 proveedores, 10 compras y 1.000 ventas). Las ventas nuevas también son comprobantes internos no fiscales; no se emiten comprobantes ante ARCA.

El backend publica endpoints locales para estas operaciones y para consultar listados e informes. La aplicación sigue limitada al comercio de demostración con ID 1; incluye inicio de sesión y permisos por módulo, y no debe exponerse a internet ni usarse con comercios reales. Las cuentas iniciales son `demo` / `demo` (dueño) y `vendedor1` / `demo` (vendedor); el dueño puede modificarlas desde Usuarios y accesos. Las vistas SQL están en `database/demo-data.sql` y las consultas preparadas para MySQL Workbench en `database/reportes.sql`.

## Estructura

- `backend/`: API REST y lógica del negocio.
- `frontend/`: aplicación web de gestión y tienda adaptable a móviles.
- `docs/`: alcance, reglas iniciales y decisiones de arquitectura.
- `compose.yaml`: entorno local de MySQL.

## Inicio rápido

1. Instalar Java 21, Maven, Node.js 22.12 o superior y MySQL o Docker.
2. En MySQL Workbench, abrir y ejecutar `database/demo-data.sql`; crea una base separada llamada `alon_demo` y no borra otras bases.
3. Ejecutar una sola vez, en orden, `database/migrations/V001__operaciones_persistentes.sql` a `database/migrations/V008__usuarios_y_permisos.sql` sobre `alon_demo`. Habilitan las operaciones, las reglas de precio, caja y nombres únicos, y las cuentas de usuario con permisos. Si la tabla de usuarios está vacía, la API crea las dos cuentas de acceso inicial al arrancar.
4. Iniciar la API desde `backend/` configurando `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/alon_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Argentina/Buenos_Aires`, además de `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` en el entorno local.
5. Iniciar la web desde `frontend/`: `npm install && npm run dev`.
6. Abrir `http://127.0.0.1:5173`; las operaciones y los reportes consultan la API local en `http://127.0.0.1:8080`.

La configuración local usa credenciales de desarrollo y no debe reutilizarse en producción. La emisión fiscal ARCA y Mercado Pago se integrarán en etapas posteriores, con credenciales propias de cada comercio.

## Alcance del MVP

La primera entrega funcional prioriza comercios y usuarios, catálogo y stock, caja, ventas de contado y cuenta corriente, gastos e informes diarios y mensuales. El detalle está en [`docs/alcance-mvp.md`](docs/alcance-mvp.md).

## Derechos de autor

Copyright © 2026 Cholo0022. Todos los derechos reservados.
