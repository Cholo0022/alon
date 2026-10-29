import { LocalEntryPanel } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'

export function StorePage() {
  return (
    <div className="store-module">
      <LocalEntryPanel
        title="Registrar pedido web"
        action="Cargar pedido"
        endpoint="/api/v1/operations/store-orders"
        fields={[
          { name: 'customerName', label: 'Nombre del cliente' },
          { name: 'contact', label: 'Teléfono o WhatsApp' },
          { name: 'details', label: 'Artículos solicitados' },
          { name: 'estimatedTotal', label: 'Total estimado', type: 'number' },
        ]}
      />
      <ApiTable
        title="Pedidos web pendientes de confirmar"
        endpoint="/api/v1/demo/store-orders"
        columns={[
          { key: 'id', label: 'NÚMERO' },
          { key: 'nombre_cliente', label: 'CLIENTE' },
          { key: 'contacto', label: 'CONTACTO' },
          { key: 'detalle', label: 'PEDIDO' },
          { key: 'total_estimado', label: 'TOTAL ESTIMADO', money: true },
          { key: 'estado', label: 'ESTADO' },
          { key: 'creado_en', label: 'FECHA', date: true },
        ]}
        note="Los pedidos son solicitudes; no descuentan stock ni se convierten en ventas hasta confirmarlos."
      />
      <section className="module-placeholder">
        <div className="placeholder-icon">⌑</div>
        <p className="eyebrow">CATÁLOGO ONLINE</p>
        <h2>Pedidos coordinados con tu comercio</h2>
        <p>
          Los pedidos web se mantienen como solicitudes y no descuentan stock ni generan una venta
          hasta que el negocio los confirme.
        </p>
      </section>
    </div>
  )
}
