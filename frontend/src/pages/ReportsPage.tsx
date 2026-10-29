import { ApiTable } from '../components/ApiTable'

export function ReportsPage() {
  return (
    <div className="report-stack">
      <ApiTable
        title="Ventas y ganancia bruta por día"
        endpoint="/api/v1/demo/reports/daily-sales"
        columns={[
          { key: 'fecha', label: 'FECHA', date: true },
          { key: 'ventas', label: 'VENTAS' },
          { key: 'total_ventas', label: 'FACTURACIÓN DEMO', money: true },
          { key: 'costo', label: 'COSTO', money: true },
          { key: 'descuentos', label: 'DESCUENTOS', money: true },
          { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true },
          { key: 'ticket_promedio', label: 'TICKET PROMEDIO', money: true },
        ]}
      />
      <ApiTable
        title="Resumen mensual"
        endpoint="/api/v1/demo/reports/monthly-sales"
        columns={[
          { key: 'mes', label: 'MES', month: true },
          { key: 'ventas', label: 'VENTAS' },
          { key: 'total_ventas', label: 'FACTURACIÓN DEMO', money: true },
          { key: 'costo', label: 'COSTO', money: true },
          { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true },
        ]}
      />
      <ApiTable
        title="Medios de pago"
        endpoint="/api/v1/demo/reports/payment-methods"
        columns={[
          { key: 'medio_pago', label: 'MEDIO DE PAGO' },
          { key: 'operaciones', label: 'OPERACIONES' },
          { key: 'importe', label: 'IMPORTE', money: true },
        ]}
      />
      <ApiTable
        title="Ranking de clientes"
        endpoint="/api/v1/demo/reports/top-customers"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'nombre', label: 'CLIENTE' },
          { key: 'compras', label: 'COMPRAS' },
          { key: 'total_consumido', label: 'TOTAL CONSUMIDO', money: true },
          { key: 'saldo_pendiente', label: 'CUENTA CORRIENTE', money: true },
          { key: 'limite_credito', label: 'LÍMITE', money: true },
        ]}
      />
      <ApiTable
        title="Artículos más vendidos"
        endpoint="/api/v1/demo/reports/top-articles"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'nombre', label: 'ARTÍCULO' },
          { key: 'rubro', label: 'RUBRO' },
          { key: 'unidades_vendidas', label: 'UNIDADES' },
          { key: 'importe_vendido', label: 'VENTAS', money: true },
          { key: 'margen_bruto', label: 'MARGEN', money: true },
        ]}
      />
      <ApiTable
        title="Compras por artículo y proveedor"
        endpoint="/api/v1/demo/reports/purchases-by-article"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'articulo', label: 'ARTÍCULO' },
          { key: 'proveedor', label: 'PROVEEDOR' },
          { key: 'compras', label: 'COMPRAS' },
          { key: 'unidades_compradas', label: 'UNIDADES' },
          { key: 'importe', label: 'IMPORTE', money: true },
        ]}
      />
      <ApiTable
        title="Saldos de proveedores"
        endpoint="/api/v1/demo/reports/supplier-balances"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'nombre', label: 'PROVEEDOR' },
          { key: 'especialidad', label: 'ESPECIALIDAD' },
          { key: 'saldo_pendiente', label: 'SALDO PENDIENTE', money: true },
          { key: 'estado', label: 'ESTADO' },
        ]}
      />
      <ApiTable
        title="Gastos por día y categoría"
        endpoint="/api/v1/demo/reports/expenses"
        columns={[
          { key: 'fecha', label: 'FECHA', date: true },
          { key: 'categoria', label: 'CATEGORÍA' },
          { key: 'movimientos', label: 'MOVIMIENTOS' },
          { key: 'total', label: 'TOTAL', money: true },
        ]}
      />
      <ApiTable
        title="Artículos bajo stock mínimo"
        endpoint="/api/v1/demo/reports/low-stock"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'nombre', label: 'ARTÍCULO' },
          { key: 'rubro', label: 'RUBRO' },
          { key: 'stock', label: 'STOCK' },
          { key: 'stock_minimo', label: 'MÍNIMO' },
        ]}
      />
    </div>
  )
}
