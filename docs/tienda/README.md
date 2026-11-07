# Tienda online de Alon

La tienda funciona localmente en http://127.0.0.1:5173/tienda y utiliza los artículos y rubros de la base actual. No está limitada a ferreterías. La imagen de portada es opcional y configurable mediante ALON_SHOP_HERO_IMAGE; sin esa configuración queda una cabecera neutra.

## Imágenes

Los archivos se guardan en backend/data/article-images, fuera del código y del directorio público. La ruta se puede cambiar con ALON_IMAGES_DIRECTORY. La base guarda una galería ordenada de hasta cuatro claves de archivo, con la indicación de imagen ilustrativa de cada foto; no guarda archivos binarios ni rutas proporcionadas por el comprador. No hace falta una carpeta por artículo: los nombres UUID evitan colisiones y permiten reutilizar una imagen ilustrativa entre artículos relacionados.

El alta y la modificación de artículos permiten seleccionar hasta cuatro fotos y guardarlas en la misma operación que los datos del artículo. El botón Fotos permite editar solamente la galería. Cada foto admite JPG o PNG de hasta 5 MB y 16 megapíxeles. El servidor comprueba el contenido, vuelve a codificarlo a PNG y sirve solamente archivos vinculados a artículos activos. Si falla una foto, la operación completa se revierte y los archivos nuevos se retiran. Las fotos anteriores se conservan para no romper respaldos anteriores. El directorio debe respaldarse junto con la base; copiar solamente la base no incluye las fotos. En un despliegue futuro con varias instancias convendrá usar almacenamiento de objetos compartido.

La primera imagen es la portada. En el editor se puede hacer principal cualquier foto y quitar las que ya no se necesiten. En la tienda se muestra una sola foto por tarjeta, con flechas anterior/siguiente y un contador cuando hay varias. V5 migra las fotos existentes a la primera posición de la galería sin cambiar los archivos.

## Flete

El formulario de artículos, indumentaria y calzado tiene un único campo de flete: porcentaje del costo. El precio de venta se calcula sumando ese porcentaje al costo y aplicando después el margen y el redondeo indicados.

Se generaron tres imágenes ilustrativas con la herramienta integrada de generación de imágenes, sin marcas comerciales. Son ejemplos, no fotografías verificadas de cada artículo:

- frontend/public/shop-assets/portada-herramientas.png: portada opcional para el comercio actual.
- docs/tienda/tornillos-ilustrativos.png: imagen fuente para artículos de tornillos para madera, marcada como ilustrativa al vincularla.
- docs/tienda/adhesivo-ilustrativo.png: imagen fuente para artículos de adhesivo de contacto, marcada como ilustrativa al vincularla.

Prompts finales utilizados:

> Use case: product-mockup. Asset type: illustrative category photo for Alon hardware store ecommerce. Generate a square professional studio product photograph of a small group of zinc plated countersunk pozidriv wood screws, arranged neatly, isolated on a pale neutral grey tabletop, crisp realistic metal threads and subtle soft shadows. No packaging, no ruler, no text, no logos or watermark. This is an illustrative image for wood screws category, not evidence of an exact SKU. Save generated image normally and return local file path metadata.

> Use case: product-mockup. Asset type: wide website banner for Alon hardware store. Professional editorial product photograph, a neatly arranged small collection of real everyday hardware tools: claw hammer, adjustable wrench, combination pliers, tape measure and screwdrivers on a neutral pale grey workbench, quiet teal accents. Landscape composition, tools clustered toward right half, left half clean empty for website text overlay. Soft studio daylight, realistic metal and rubber texture, uncluttered high-end ecommerce aesthetic. No text, no logos, no watermark, no brands. This is an illustrative hardware store banner.

> Use case: product-mockup. Asset type: illustrative ecommerce category image for contact adhesive sold in a hardware store. Square studio product photograph of a single small unbranded cylindrical metal contact adhesive can with a closed lid on a very pale neutral grey background. Simple matte teal paper label with no text and no logos, no stated volume. Realistic clean metal rim, soft daylight shadows, centered product with generous whitespace, consistent high-end catalog photography. No extra objects, no watermark. This is a generic illustrative category image, not an exact SKU photo.

## Compradores y pedidos

Alon muestra un cartel con la cantidad total de ventas web pendientes de confirmar en todas las secciones de los usuarios que tienen acceso a Tienda online. El botón Ver pedidos abre ese módulo. El contador se actualiza cada 10 segundos mientras la pestaña está visible, al volver a la aplicación y después de confirmar o cancelar un pedido. Solo cuenta pedidos pendientes del comercio de la sesión, sin depender de las páginas del listado. El cartel desaparece cuando no queda ninguno.

La tienda no utiliza usuario ni contraseña. Al pulsar Enviar pedido aparece un formulario de nombre, apellido, celular de exactamente diez dígitos, DNI numérico, provincia y ciudad. Provincia propone Entre Ríos y ciudad propone Larroque; ambas se pueden cambiar. No se solicita una forma de pago: el vendedor contacta al cliente para acordarla. Se mantiene la protección CSRF, la limitación de envíos y la clave de operación para no duplicar pedidos al reintentar.

Los contactos se identifican por DNI dentro de cada comercio y se conservan en `compradores_tienda`, sin contraseña para los nuevos registros. Cada pedido guarda sus datos de contacto y ubicación de ese momento. Un nuevo envío con el mismo DNI no reemplaza automáticamente los datos del contacto o del cliente de gestión. No se publica un buscador de DNI ni el historial de compras: esos datos solo son visibles al personal autorizado.

El vendedor ve “Es cliente web nuevo” cuando no hay otra compra web confirmada del mismo contacto, y “Este cliente ya ha comprado en la web” cuando existe otra venta web confirmada. Un pedido pendiente, rechazado o con venta anulada no acredita una compra. Confirmar el primer pedido crea el cliente de gestión; los siguientes pedidos reutilizan ese vínculo. Rechazar un pedido no genera cliente de gestión, venta, cobro ni salida de stock.

El catálogo muestra artículos activos con stock mayor a cero. El carrito comprueba precios y stock al enviarse, y guarda el detalle del pedido con sus precios. Los pedidos pendientes no reservan ni descuentan stock. En Tienda online el dueño o vendedor con permiso tienda puede revisar el comprador, quitar artículos, cancelar o confirmar. Quitar un artículo recalcula el total y conserva la línea excluida para auditoría.

El vendedor elige efectivo, transferencia o Mercado Pago después de contactar al cliente y comprobar el pago. La confirmación exige una caja abierta en la fecha actual y registra en una única transacción la venta, el cobro en esa caja y la salida de stock. Si falta stock o la caja está cerrada, el pedido queda pendiente y no se registra ningún cobro ni salida. Los precios son los que quedaron guardados al enviar el pedido, aunque cambie después el catálogo.

Las ventas de la tienda tienen origen WEB, número con prefijo VW- y referencia al pedido; aparecen identificadas en Ventas y en la descripción del movimiento de caja. Los cierres y reportes incluyen estos cobros como ventas. Mercado Pago y transferencia son registros internos del cobro: no verifican ni capturan pagos en un proveedor externo. El vendedor debe comprobar el pago al confirmar.

Cancelar la venta vinculada desde Ventas devuelve el stock, revierte el cobro en una caja abierta y deja el pedido cancelado. La venta se vincula una sola vez al pedido para evitar duplicaciones. Los pedidos confirmados antes de incorporar esta integración se conservan sin crear ventas ni cobros retroactivos.

Las cuentas existentes de vendedor reciben el permiso tienda. Los usuarios personalizados deben tener ese permiso asignado por el dueño.

Los pedidos anteriores de texto libre siguen visibles. Como no contienen IDs ni cantidades verificables, no se confirman automáticamente como salidas de stock.

## Base de datos y puesta en marcha

La migración V3 agrega referencias a imágenes, compradores y líneas de pedidos. V4 agrega medio de pago, cliente vinculado y la relación entre pedido y venta de origen web. Ambas conservan los registros existentes. Antes de aplicarlas en una instalación con datos se debe respaldar la base y probar las migraciones en una base desechable.

V18 incorpora pedidos sin cuenta: conserva compradores y pedidos previos, admite contactos nuevos sin contraseña, amplía DNI a doce dígitos y guarda provincia y ciudad por pedido. La identificación pasa a ser por DNI; distintos DNI pueden compartir un teléfono. Las rutas antiguas de registro, ingreso, cambio/recuperación de contraseña y consulta pública de pedidos devuelven 410, sin exponer datos. Los accesos del personal de gestión no cambian. Antes de actualizar, crear un respaldo completo; después, reiniciar servidor e interfaz juntos y recargar la tienda.

La validación de migración, envío, reintentos, compras anteriores, confirmación y rechazo puede ejecutarse con `scripts/Probar-Pedidos-Web.ps1`. Utiliza y recrea exclusivamente la base de pruebas `alon_validation`; no la base del negocio ni las bases de rendimiento.

El servidor se inicia con la configuración habitual de MySQL. La interfaz de gestión sigue en la raíz; la tienda se abre en /tienda. Ambos usan la misma API por el proxy local, sin exponer MySQL al navegador.
# Vinculación con clientes por DNI

Al confirmar un pedido, se busca el DNI en los clientes del mismo comercio y se reutiliza ese cliente, conservando sus datos, saldo e historial. Clientes permite cargar y modificar un DNI opcional, solo numérico, y buscarlo desde el listado. Si el cliente fue cargado manualmente sin DNI, primero hay que completarlo para poder reconocerlo.

La migración V19 recupera el DNI de los contactos web ya vinculados cuando no es ambiguo. Los duplicados anteriores se conservan para no alterar ventas ni saldos. Si hay varios clientes con el mismo DNI, la confirmación pide revisarlos; no elige uno arbitrariamente.
