// Organiza la apertura, los movimientos y el cierre de la caja.
import { LocalEntryPanel } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'

/** Organiza apertura, gastos y cierre de caja junto con el historial de sesiones y sus importes. */
export function CashPage() {
  return (
    <>
      <LocalEntryPanel
        title="Abrir caja"
        action="Abrir caja"
        endpoint="/api/v1/operations/cash/open"
        fields={[
          { name: 'date', label: 'Fecha', type: 'date' },
          { name: 'openingBalance', label: 'Saldo inicial en efectivo', type: 'number' },
        ]}
      />
      <LocalEntryPanel
        title="Registrar gasto"
        action="Cargar gasto"
        endpoint="/api/v1/operations/cash/expenses"
        fields={[
          { name: 'date', label: 'Fecha', type: 'date' },
          { name: 'category', label: 'Categoría' },
          { name: 'description', label: 'Detalle' },
          {
            name: 'paymentMethod',
            label: 'Medio de pago',
            options: [
              { value: 'EFECTIVO', label: 'Efectivo' },
              { value: 'TRANSFERENCIA', label: 'Transferencia' },
              { value: 'MERCADO_PAGO', label: 'Mercado Pago' },
            ],
          },
          { name: 'amount', label: 'Importe', type: 'number' },
        ]}
      />
      <LocalEntryPanel
        title="Cerrar caja"
        action="Arqueo y cierre"
        endpoint="/api/v1/operations/cash/close"
        fields={[
          { name: 'date', label: 'Fecha', type: 'date' },
          { name: 'countedCash', label: 'Efectivo contado', type: 'number' },
        ]}
      />
      <ApiTable
        title="Historial de cierres de caja"
        endpoint="/api/v1/alon/reports/cash"
        columns={[
          { key: 'fecha', label: 'FECHA', date: true },
          { key: 'sesion', label: 'SESIÓN' },
          { key: 'saldo_inicial', label: 'EFECTIVO INICIAL', money: true },
          { key: 'ventas_efectivo', label: 'VENTAS EN EFECTIVO', money: true },
          { key: 'ventas_mercado_pago', label: 'VENTAS MERCADO PAGO', money: true },
          { key: 'pagos_proveedores', label: 'PAGOS A PROVEEDORES', money: true },
          { key: 'gastos', label: 'GASTOS', money: true },
          { key: 'efectivo_esperado', label: 'EFECTIVO QUE DEBE HABER', money: true },
          { key: 'efectivo_contado', label: 'EFECTIVO CONTADO', money: true },
          { key: 'diferencia', label: 'DIFERENCIA', money: true },
        ]}
        note="Los cierres y diferencias corresponden a las operaciones registradas. Mercado Pago se informa por separado del efectivo físico."
      />
    </>
  )
}
