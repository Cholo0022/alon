# Contabilidad de Alon: guía de uso y alcance

## Dos opciones independientes

El menú **Contabilidad** agrega un informe mensual de gestión y un registro auxiliar de comprobantes para el contador. Tanto **Activar control de períodos mensuales** como **Activar carga de comprobantes con impuestos** comienzan desactivadas. No se modifican los comprobantes anteriores ni se asigna IVA automáticamente a sus importes.

El superusuario puede otorgar el permiso Contabilidad al gerente desde Gerentes y licencias. Un gerente con ese permiso puede delegarlo a sus usuarios. Quienes solo tienen Compras, Ventas o Caja pueden completar el desglose de sus operaciones, pero el permiso Contabilidad se exige para marcar sus datos fiscales como controlados. Este acceso incluye costos e información impositiva. El superusuario y el gerente con Contabilidad administran las opciones y abren, cierran o reabren los períodos; un usuario con Contabilidad puede consultar el informe y cargar o corregir sus registros fiscales.

## Abrir y cerrar un mes

1. Activar el control mensual si se desea exigir una apertura antes de cargar.
2. Elegir el mes y pulsar Abrir mes. Informar un motivo.
3. Cargar normalmente ventas, compras, gastos y movimientos de caja.
4. Cerrar las sesiones de caja de ese mes y revisar el informe.
5. Pulsar Cerrar mes, indicar el motivo y confirmar la revisión.

Con el control mensual activo se rechazan cargas en meses sin abrir. Un mes cerrado bloquea altas, correcciones y anulaciones fechadas en él, además de las correcciones vinculadas a registros fiscales de un mes cerrado. Las compras que cambian de fecha deben tener abiertos tanto el mes original como el nuevo. Las anulaciones de ventas también verifican el mes del reverso. La confirmación empieza con Cancelar seleccionado.

Desactivar el control evita exigir aperturas para meses nuevos, pero no desbloquea meses previamente cerrados. Para corregir uno cerrado debe reabrirse, con motivo y usuario registrados. Cada cierre conserva una copia del informe; reabrir no borra las versiones anteriores. El informe actual de un mes cerrado muestra esa copia y el historial permite consultar cada revisión.

Los pagos posteriores de facturas viejas se registran en su propia fecha de pago: no obligan a reabrir el mes de emisión de la factura. Los saldos de clientes y proveedores que aparecen en el informe son los existentes al generar la consulta; al cerrar, esos valores quedan congelados con el informe. No se presentan como saldos históricos recalculados de cualquier fecha.

## Carga opcional con impuestos

Para activarla se debe completar la CUIT del negocio y guardar la opción. La condición inicial es Responsable Inscripto, según el alcance solicitado para esta instalación, y puede configurarse por comercio.

Con la opción activa, después de guardar una factura de compra, una venta o un gasto se ofrece completar los datos de su comprobante fiscal. La operación comercial ya está guardada: esta segunda etapa no vuelve a ingresar stock ni vuelve a cobrar o pagar. Puede dejarse pendiente y completarse desde el informe. Los remitos no se tratan automáticamente como facturas.

También puede ingresarse un comprobante externo desde Contabilidad sin asociarlo a una operación del stock o la caja; queda solo en el registro auxiliar fiscal, no crea una venta, compra ni pago de gestión. Una factura vinculada debe coincidir con el importe de su operación. Se impiden duplicados por emisor, dirección compra/venta, tipo, letra, punto de venta y número. Compra y gasto comparten el control de duplicados para no computar dos veces una factura del mismo proveedor.

Campos: factura, nota de crédito o débito; letra A/B/C; punto de venta y número reales; emisor o receptor; CUIT/DNI; fecha de emisión y mes de registración; bases e IVA por alícuota (0; 2,5; 5; 10,5; 21; 27 %); exentos, no gravados, otros tributos, percepciones de IVA e IIBB; autorización informada y observaciones. Las notas requieren un comprobante asociado. Los importes se cargan en ARS.

El total debe cuadrar exactamente con el desglose. El IVA se copia del documento: no se presume una alícuota a partir del total ni se asigna por defecto todo el IVA como crédito fiscal. El crédito computable se carga expresamente, no puede superar el IVA registrado y se exige cero en ventas y comprobantes B/C o no fiscales. Su procedencia requiere revisión contable. Los gastos sin comprobante fiscal se identifican por separado y no generan crédito de IVA.

Las diferencias relevantes entre base por alícuota e IVA informado requieren observación antes de marcar el documento controlado. No se consulta el padrón, la validez del CAE/CAI/CAEA ni el estado del comprobante en ARCA. No se emiten facturas electrónicas.

Si el modo con impuestos está activo, el cierre exige completar los registros fiscales pendientes y controlar sus datos. Las ventas de gestión anuladas que tienen factura requieren su correspondiente nota de crédito completa para cerrar. La corrección de una compra deja su registro fiscal pendiente de revisión. Desactivar la opción no elimina los datos fiscales existentes.

## Informe para el contador

Incluye ventas confirmadas y anuladas, compras y remitos, gastos, movimientos de caja por medio, costo histórico de lo vendido, resultado de gestión, saldos al generar y el detalle fiscal por comprobante y alícuota. Las notas de crédito se restan; las percepciones se muestran aparte.

El resultado de gestión es ventas registradas menos costo histórico y gastos registrados. Las compras de mercadería no se descuentan nuevamente como gasto, y esos importes pueden contener impuestos. No es una ganancia neta contable ni un resultado impositivo.

El débito y crédito del resumen toman los comprobantes marcados como controlados, agrupados por el mes de registración elegido. La diferencia parcial de IVA no incorpora saldos anteriores, todas las retenciones, prorrateos ni los demás ajustes de una declaración jurada; no se presenta como IVA a pagar. La imputación del período, la procedencia del crédito y el tratamiento de regímenes especiales deben revisarse con el contador.

Puede imprimirse o guardarse como PDF desde el navegador, y descargarse como ZIP con archivos CSV en UTF-8 para revisión. Los CSV usan separador punto y coma, incluyen signos para las notas de crédito y neutralizan textos que podrían interpretarse como fórmulas de planilla. Este paquete no es un archivo de importación certificado por ARCA.

## Referencias oficiales consultadas (01/11/2026)

- [ARCA: IVA Simple, cronograma](https://www.arca.gob.ar/iva/iva-simple/cronograma-implementacion.asp). Para responsables inscriptos el procedimiento es obligatorio desde noviembre de 2025. El informe de Alon es auxiliar y no sustituye esa presentación.
- [ARCA: comprobantes del régimen general](https://www.arca.gob.ar/facturacion/regimen-general/comprobantes.asp).
- [ARCA: notas de crédito y débito](https://www.arca.gob.ar/facturacion/comprobantes/). Se relacionan con los comprobantes de las operaciones originarias.
- [Código Civil y Comercial, contabilidad y estados contables](https://www.argentina.gob.ar/normativa/nacional/ley-26994-235975/actualizacion). Los controles internos y las copias de cierre no sustituyen las formalidades de los libros legales ni las obligaciones de conservación de documentación.

Las reglas particulares del contribuyente, actividad, provincia, convenios, retenciones, percepciones y deducibilidad no se determinan automáticamente en esta versión. El uso del módulo no constituye certificación profesional ni presentación fiscal.

