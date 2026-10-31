// Muestra informes calculados con los datos guardados en el comercio.
import { useId, useLayoutEffect, useRef, useState } from 'react'
import { ApiTable, type ApiColumn } from '../components/ApiTable'

const reports: { title: string; endpoint: string; columns: ApiColumn[] }[] = [
  {
    title: 'Ventas y ganancia bruta por día',
    endpoint: '/api/v1/alon/reports/daily-sales',
    columns: [
      { key: 'fecha', label: 'FECHA', date: true },
      { key: 'ventas', label: 'VENTAS' },
      { key: 'total_ventas', label: 'TOTAL DE VENTAS', money: true },
      { key: 'costo', label: 'COSTO', money: true },
      { key: 'descuentos', label: 'DESCUENTOS', money: true },
      { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true },
      { key: 'ticket_promedio', label: 'TICKET PROMEDIO', money: true },
    ],
  },
  {
    title: 'Resumen mensual',
    endpoint: '/api/v1/alon/reports/monthly-sales',
    columns: [
      { key: 'mes', label: 'MES', month: true },
      { key: 'ventas', label: 'VENTAS' },
      { key: 'total_ventas', label: 'TOTAL DE VENTAS', money: true },
      { key: 'costo', label: 'COSTO', money: true },
      { key: 'ganancia_bruta', label: 'GANANCIA BRUTA', money: true },
    ],
  },
  {
    title: 'Medios de pago',
    endpoint: '/api/v1/alon/reports/payment-methods',
    columns: [
      { key: 'medio_pago', label: 'MEDIO DE PAGO' },
      { key: 'operaciones', label: 'OPERACIONES' },
      { key: 'importe', label: 'IMPORTE', money: true },
    ],
  },
  {
    title: 'Ranking de clientes',
    endpoint: '/api/v1/alon/reports/top-customers',
    columns: [
      { key: 'codigo', label: 'CÓDIGO' },
      { key: 'nombre', label: 'CLIENTE' },
      { key: 'compras', label: 'COMPRAS' },
      { key: 'total_consumido', label: 'TOTAL CONSUMIDO', money: true },
      { key: 'saldo_pendiente', label: 'CUENTA CORRIENTE', money: true },
      { key: 'limite_credito', label: 'LÍMITE', money: true },
    ],
  },
  {
    title: 'Artículos más vendidos',
    endpoint: '/api/v1/alon/reports/top-articles',
    columns: [
      { key: 'codigo', label: 'CÓDIGO' },
      { key: 'nombre', label: 'ARTÍCULO' },
      { key: 'rubro', label: 'RUBRO' },
      { key: 'unidades_vendidas', label: 'UNIDADES' },
      { key: 'importe_vendido', label: 'VENTAS', money: true },
      { key: 'margen_bruto', label: 'MARGEN', money: true },
    ],
  },
  {
    title: 'Compras por artículo y proveedor',
    endpoint: '/api/v1/alon/reports/purchases-by-article',
    columns: [
      { key: 'codigo', label: 'CÓDIGO' },
      { key: 'articulo', label: 'ARTÍCULO' },
      { key: 'proveedor', label: 'PROVEEDOR' },
      { key: 'compras', label: 'COMPRAS' },
      { key: 'unidades_compradas', label: 'UNIDADES' },
      { key: 'importe', label: 'IMPORTE', money: true },
    ],
  },
  {
    title: 'Saldos de proveedores',
    endpoint: '/api/v1/alon/reports/supplier-balances',
    columns: [
      { key: 'codigo', label: 'CÓDIGO' },
      { key: 'nombre', label: 'PROVEEDOR' },
      { key: 'especialidad', label: 'ESPECIALIDAD' },
      { key: 'saldo_pendiente', label: 'SALDO PENDIENTE', money: true },
      { key: 'estado', label: 'ESTADO' },
    ],
  },
  {
    title: 'Gastos por día y categoría',
    endpoint: '/api/v1/alon/reports/expenses',
    columns: [
      { key: 'fecha', label: 'FECHA', date: true },
      { key: 'categoria', label: 'CATEGORÍA' },
      { key: 'movimientos', label: 'MOVIMIENTOS' },
      { key: 'total', label: 'TOTAL', money: true },
    ],
  },
  {
    title: 'Artículos bajo stock mínimo',
    endpoint: '/api/v1/alon/reports/low-stock',
    columns: [
      { key: 'codigo', label: 'CÓDIGO' },
      { key: 'nombre', label: 'ARTÍCULO' },
      { key: 'rubro', label: 'RUBRO' },
      { key: 'stock', label: 'STOCK' },
      { key: 'stock_minimo', label: 'MÍNIMO' },
    ],
  },
]

/** Presenta los informes cerrados por nombre, abre solo el seleccionado y lo ubica arriba para leerlo con el título visible. */
export function ReportsPage() {
  const [selected, setSelected] = useState<string | null>(null)
  const id = useId()
  const selectedReport = useRef<HTMLElement>(null)
  useLayoutEffect(() => {
    if (selected) selectedReport.current?.scrollIntoView({ block: 'start', behavior: 'instant' })
  }, [selected])
  return (
    <div className="report-stack">
      {reports.map((report, index) => {
        const open = selected === report.endpoint
        const buttonId = `${id}-title-${index}`
        const panelId = `${id}-panel-${index}`
        return (
          <section
            className={open ? 'report-accordion report-expanded' : 'report-accordion'}
            ref={open ? selectedReport : undefined}
            key={report.endpoint}
          >
            <h2>
              <button
                type="button"
                id={buttonId}
                aria-expanded={open}
                aria-controls={panelId}
                onClick={() => setSelected(open ? null : report.endpoint)}
              >
                <span>{report.title}</span>
                <span aria-hidden="true">{open ? '−' : '+'}</span>
              </button>
            </h2>
            <div id={panelId} role="region" aria-labelledby={buttonId} hidden={!open}>
              {open && <ApiTable {...report} showTitle={false} />}
            </div>
          </section>
        )
      })}
    </div>
  )
}
