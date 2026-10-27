# Alcance funcional inicial de Alon

## Producto

Alon será una plataforma multi comercio para almacenes, ferreterías, librerías, corralones y bazares. Cada comercio administra su identidad, sucursales, usuarios, clientes, proveedores, catálogo, ventas e informes. Los datos de un comercio deben permanecer aislados de los demás.

Capacidad objetivo declarada: hasta 99.999 artículos y 99.999 usuarios. Esto es un requisito de diseño y deberá validarse mediante dimensionamiento y pruebas de carga antes de producción.

## MVP

1. **Comercios y acceso:** alta de comercio, sucursales, usuarios, roles de dueño/encargado/empleado, baja lógica y permisos. Los empleados pueden vender y preparar pedidos; no acceden a costos, márgenes ni ganancias salvo permiso explícito.
2. **Artículos y stock:** rubros, código de barras, precio, costo, margen, proveedores asociados, unidades y variantes; lotes y vencimientos opcionales; historial inmutable de movimientos y ajustes con motivo y usuario; alerta de stock bajo.
3. **Clientes y ventas:** alta, edición y baja lógica de clientes; venta de contado o cuenta corriente; límite de crédito, vencimientos y deuda; descuentos autorizados por permiso; el cierre de una venta descuenta stock en una transacción.
4. **Caja:** apertura por usuario/caja, cobros en efectivo y Mercado Pago, egresos, gastos, arqueo y cierre con diferencia documentada.
5. **Compras:** carga manual o importación de factura/remito, proveedor, renglones, costos adicionales atribuibles a mercadería y recepción de stock. Historial de precios y compras por artículo y proveedor.
6. **Informes:** ventas e ingresos por día/mes, costos, gastos y resultado estimado por período; historial y rankings de venta/compra. Los informes de ganancia se limitan a roles autorizados.
7. **Tienda:** catálogo, precios y disponibilidad propios por comercio, pedido web y coordinación por WhatsApp. El pedido no se considera venta facturada hasta la confirmación del negocio.

## Fuera de la primera entrega

- Autorización de comprobantes electrónicos ante ARCA.
- Cobro/captura automática de Mercado Pago.
- Portal de proveedores, contabilidad impositiva completa y sincronización multi sucursal en tiempo real.

Se incorporarán luego de validar el flujo operativo básico y sus requisitos legales/técnicos.

## Reglas críticas

- No se llamará “factura” fiscal a un comprobante interno sin autorización de ARCA. La interfaz identificará inequívocamente comprobantes autorizados y no fiscales.
- Stock, venta, cobro y cuenta corriente deben quedar registrados de manera transaccional y auditable.
- No borrar operaciones confirmadas: corregir mediante reversos, notas de crédito u otros movimientos apropiados.
- Cada consulta y escritura debe tener un comercio y sucursal autorizados; el identificador del comercio nunca se confiará solo al cliente web.
- El costo de compra puede distribuirse entre artículos según una regla configurable; registrar la regla para poder auditar el costo resultante.
- Los importes se guardarán con precisión decimal y moneda explícita. No usar punto flotante para dinero.

## Decisiones pendientes para antes de facturación

- Tipos de comprobante y condición fiscal que utilizará cada comercio.
- Si el stock de tienda y mostrador será físicamente compartido o reservará cantidades separadas.
- Regla contable de distribución de flete y otros gastos de compra.
- Política de autorización, caducidad y devolución de reservas de stock de pedidos web.
- Alcance de sucursales, impresoras fiscales/térmicas y operación sin conexión.
