# Arquitectura de Alon

Monolito modular organizado por funcionalidad. Cada paquete reúne sus controladores HTTP, servicios transaccionales, consultas y contratos de entrada. Se mantienen las rutas existentes para conservar compatibilidad con la interfaz.

## Módulos

```text
ar.com.alon
├── articles       Artículos, precios y formularios de entrada
├── categories     Rubros
├── customers      Clientes y búsqueda para ventas
├── suppliers      Proveedores, saldos y recibos de pago
├── sales          Ventas y anulaciones
├── purchases      Compras, correcciones y valoración
├── stock          Movimientos y bloqueo de existencias
├── cash           Caja, sesiones y movimientos de dinero
├── storefront     Pedidos de tienda
├── users          Autenticación, usuarios y permisos
├── reporting      Informes que combinan varios módulos
├── dashboard      Resumen del comercio
├── health         Estado del servicio
├── security       Sesiones, identidad, CSRF y contraseñas
├── config         Configuración y migraciones
└── shared
    ├── audit         Registro de auditoría
    ├── commands      Idempotencia y límite transaccional
    ├── persistence   Primitivas JDBC y lectura paginada
    ├── validation    Reglas decimales compartidas
    └── web           Errores HTTP comunes
```

Los módulos comerciales usan `Controller`, `Service`, `Queries` y `dto/` según corresponda. Stock expone `StockRepository`; caja expone `CashLedger` para los movimientos coordinados desde ventas, compras y proveedores. La regla de precio pertenece a artículos y la valoración de compras pertenece a compras.

No existen los agregadores `OperationsModel`, `CatalogService`, `DemoDataModel`, `OperationsController` ni `DemoDataController`. Las consultas y operaciones están en el módulo que las posee. `StoreJdbcSupport` conserva únicamente contexto de ejecución y primitivas comunes; no administra ventas, compras, stock o caja.

## Transacciones

```text
React → seguridad e identidad → controlador del módulo
      → CommandExecutor → servicio del módulo → repositorios y MySQL
```

CommandExecutor conserva el resultado dentro de la misma transacción que los cambios comerciales. Mantiene el bloqueo por comercio y la clave de idempotencia. Los servicios de ventas y compras coordinan stock y caja dentro de esa transacción; mover los paquetes no cambia su atomicidad ni modifica el esquema.

## Frontend

`pages/` contiene las pantallas; `components/` los controles compartidos; `hooks/` las lecturas cancelables y paginadas; `lib/` el cliente HTTP, contratos y formatos. `LookupField` busca y selecciona registros desde un solo campo, sin controles de paginación dentro del formulario.

## Verificación

Pruebas Java junto a su módulo y pruebas transversales en `integration/`. Spotless comprueba formato Java; TypeScript estricto, Oxlint, Prettier y Vitest verifican la interfaz. Las pruebas MySQL se ejecutan únicamente en la base desechable `alon_validation`.
