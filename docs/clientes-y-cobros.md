# Clientes Alon y cobros del servicio

La administración general muestra los informes y el listado de clientes. Ver abre una ficha modal con datos, estado de acceso, instalación, cuenta, recibos e historial. El alta y la edición se realizan dentro de esa ficha.

## Acceso

- Activo: puede operar mientras Alon no lo inhabilite o dé de baja. El abono no determina el acceso.
- En prueba: se elige entre 1 y 120 meses. Al alta, el plazo se calcula desde la fecha de alta; al cambiar un cliente a prueba, desde la fecha actual de Argentina.
- La fecha de fin de prueba es inclusiva. Al llegar a la medianoche siguiente se bloquean las API de gestión y tienda, incluso para sesiones ya abiertas. La comprobación de disponibilidad muestra el vencimiento y sigue accesible para consultar una eventual activación.
- Inhabilitado conserva la información. La baja definitiva mantiene el comportamiento ya existente y su confirmación explícita.
- Activar un cliente elimina el límite de acceso por plazo. No requiere claves de licencia.
- Los campos internos históricos de licencia se conservan por compatibilidad y para no reescribir migraciones anteriores. Los endpoints de emisión y activación de claves responden 410.

Las instalaciones independientes deben ejecutar el backend actualizado para usar el control de prueba. La migración V27 recupera los vencimientos de prueba ya reservados, cuando faltaban en la ficha central. Al iniciar cada instalación, el administrador escribe el estado y el fin de prueba junto a los archivos de control; el negocio vuelve a comprobarlos en cada solicitud.

## Recibos

Un recibo empieza con un mes y bonificación 0%. Permite elegir fecha, primer mes, cantidad de meses y porcentaje de bonificación, de 0 a 100 con dos decimales. El importe definitivo se calcula en el servidor desde el abono vigente y queda guardado en el recibo; cambios posteriores del abono no alteran el historial.

Cada recibo tiene número propio, conserva los períodos cobrados y puede descargarse como texto. No es un comprobante fiscal. Un mes con recibo vigente no puede cobrarse nuevamente al mismo cliente. Una referencia de envío impide duplicar el recibo si se pierde la respuesta; el navegador guarda el envío pendiente hasta confirmar su resultado.

El recibo registra un cargo neto del abono y el cobro de ese mismo importe. Los cargos manuales anteriores no se imputan automáticamente: se conservan y deben conciliarse por separado. Si la bonificación es del 100% se guarda el recibo y sus períodos, sin movimientos monetarios de importe cero.

La anulación requiere motivo y conserva el recibo y sus movimientos, marcados como anulados. Los movimientos anulados no se suman al saldo ni al informe cobrado. Los períodos quedan disponibles para emitir un nuevo recibo. Pagar o anular nunca cambia el estado del servicio.

## Actualización y validación

Aplicar la migración con el procedimiento habitual de actualización, primero sobre una copia. No ejecutar scripts de ejemplo en la base real. Estos cambios se preparan en la rama codex/clientes-pruebas-recibos: no reemplazan cambios locales todavía no publicados.

Pruebas añadidas: límites de prueba, estado activo sin vencimiento, cálculos y reintentos de recibos, períodos duplicados, anulación, aislamiento por cliente y formularios. La prueba PlatformReceiptsMySqlTest solo acepta la base alon_validation y revierte los datos de cada caso.
