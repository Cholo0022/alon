import { apiFetch } from './http'

export async function downloadSaleReceipt(id: number, number = String(id)) {
  const response = await apiFetch('/api/v1/alon/sales/' + id + '/pdf')
  if (!response.ok || !response.headers.get('Content-Type')?.includes('application/pdf')) {
    throw new Error('No se pudo generar el PDF. Podés volver a descargarlo desde la venta.')
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'Venta-' + number.replace(/[^a-zA-Z0-9_-]/g, '-') + '.pdf'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 60000)
}
