# Decisiones técnicas iniciales

## Arquitectura

Monolito modular con API REST. Para el MVP evita costos y complejidad operativa de microservicios y mantiene transacciones de venta/caja/stock en una sola aplicación. Separar por dominios (`identity`, `tenancy`, `catalog`, `inventory`, `sales`, `cash`, `purchases`, `reporting`, `storefront`, `integrations`).

## Aislamiento multi comercio

El modelo inicial usa una base compartida con columna `tenant_id` obligatoria en cada agregado de negocio. Todas las claves únicas de negocio incluyen el comercio; consultas y restricciones filtran por tenant desde el servidor. Añadir pruebas de aislamiento antes de exponer datos reales. Mantener la opción de migrar comercios de alto volumen a esquemas o bases dedicadas.

## Dinero e impuestos

Usar `DECIMAL` en MySQL y `BigDecimal` en Java. Guardar moneda, importes netos, impuestos, descuentos, costo y total según corresponda. La lógica de redondeo debe ser explícita y consistente. El precio histórico de cada renglón se conserva en la venta aunque luego cambie el artículo.

## Integraciones

ARCA y Mercado Pago serán adaptadores aislados por comercio, con secretos cifrados y auditoría. Nunca guardar claves privadas o tokens en el repositorio. Los comprobantes fiscales requieren revisar documentación, certificados, puntos de venta y reglas vigentes antes de habilitar producción.

## Entorno

Configuración mediante variables de entorno. MySQL local se ofrece con Docker Compose. Migraciones de esquema versionadas; evitar cambios automáticos destructivos del esquema en producción.
