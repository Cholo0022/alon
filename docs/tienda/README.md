# Tienda online de Alon

La tienda funciona localmente en http://127.0.0.1:5173/tienda y utiliza los artículos y rubros de la base actual. No está limitada a ferreterías. La imagen de portada es opcional y configurable mediante ALON_SHOP_HERO_IMAGE; sin esa configuración queda una cabecera neutra.

## Imágenes

Los archivos se guardan en backend/data/article-images, fuera del código y del directorio público. La ruta se puede cambiar con ALON_IMAGES_DIRECTORY. La base guarda una galería ordenada de hasta cuatro claves de archivo, con la indicación de imagen ilustrativa de cada foto; no guarda archivos binarios ni rutas proporcionadas por el comprador. No hace falta una carpeta por artículo: los nombres UUID evitan colisiones y permiten reutilizar una imagen ilustrativa entre artículos relacionados.

El alta y la modificación de artículos permiten seleccionar hasta cuatro fotos y guardarlas en la misma operación que los datos del artículo. El botón Fotos permite editar solamente la galería. Cada foto admite JPG o PNG de hasta 5 MB y 16 megapíxeles. El servidor comprueba el contenido, vuelve a codificarlo a PNG y sirve solamente archivos vinculados a artículos activos. Si falla una foto, la operación completa se revierte y los archivos nuevos se retiran. Las fotos anteriores se conservan para no romper respaldos anteriores. El directorio debe respaldarse junto con la base; copiar solamente la base no incluye las fotos. En un despliegue futuro con varias instancias convendrá usar almacenamiento de objetos compartido.

La primera imagen es la portada. En el editor se puede hacer principal cualquier foto y quitar las que ya no se necesiten. En la tienda se muestra una sola foto por tarjeta, con flechas anterior/siguiente y un contador cuando hay varias. V5 migra las fotos existentes a la primera posición de la galería sin cambiar los archivos.

## Flete

El formulario del artículo tiene un solo importe de flete. Aplicar flete porcentual está marcado por defecto en artículos nuevos: el valor se calcula como porcentaje sobre el costo. Desmarcado, se agrega como importe fijo por unidad. El precio de venta se calcula sumando el flete al costo y aplicando después el margen y el redondeo indicados. Los artículos existentes conservan su modalidad al editarlos.

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

El registro pide nombre, apellido, DNI, domicilio, teléfono internacional y contraseña de 12 a 128 caracteres. Las cuentas de compradores son independientes de las de administración. Se utilizan sesiones, protección CSRF, contraseñas derivadas mediante PBKDF2 y limitación de intentos. La validación del teléfono por WhatsApp no está habilitada: requiere contratar/configurar un proveedor y una plantilla. El campo telefono_verificado queda en falso y no se simula una validación real.

El catálogo muestra artículos activos con stock mayor a cero. El carrito comprueba precios y stock al enviarse, y guarda el detalle del pedido con sus precios. Los pedidos pendientes no reservan ni descuentan stock. En Tienda online el dueño o vendedor con permiso tienda puede revisar el comprador, quitar artículos, cancelar o confirmar. Quitar un artículo recalcula el total y conserva la línea excluida para auditoría.

El comprador debe elegir efectivo, transferencia o Mercado Pago antes de enviar el pedido. El vendedor revisa ese medio de pago antes de confirmar. La confirmación exige una caja abierta en la fecha actual y registra en una única transacción la venta, el cobro en esa caja y la salida de stock. Si falta stock o la caja está cerrada, el pedido queda pendiente y no se registra ningún cobro ni salida. Los precios son los que quedaron guardados al enviar el pedido, aunque cambie después el catálogo.

Las ventas de la tienda tienen origen WEB, número con prefijo VW- y referencia al pedido; aparecen identificadas en Ventas y en la descripción del movimiento de caja. Los cierres y reportes incluyen estos cobros como ventas. Mercado Pago y transferencia son registros internos del cobro: no verifican ni capturan pagos en un proveedor externo. El vendedor debe comprobar el pago al confirmar.

Cancelar la venta vinculada desde Ventas devuelve el stock, revierte el cobro en una caja abierta y deja el pedido cancelado. La venta se vincula una sola vez al pedido para evitar duplicaciones. Los pedidos confirmados antes de incorporar esta integración se conservan sin crear ventas ni cobros retroactivos.

Las cuentas existentes de vendedor reciben el permiso tienda. Los usuarios personalizados deben tener ese permiso asignado por el dueño.

Los pedidos anteriores de texto libre siguen visibles. Como no contienen IDs ni cantidades verificables, no se confirman automáticamente como salidas de stock.

## Base de datos y puesta en marcha

La migración V3 agrega referencias a imágenes, compradores y líneas de pedidos. V4 agrega medio de pago, cliente vinculado y la relación entre pedido y venta de origen web. Ambas conservan los registros existentes. Antes de aplicarlas en una instalación con datos se debe respaldar la base y probar las migraciones en una base desechable.

El servidor se inicia con la configuración habitual de MySQL. La interfaz de gestión sigue en la raíz; la tienda se abre en /tienda. Ambos usan la misma API por el proxy local, sin exponer MySQL al navegador.
