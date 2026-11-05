import { apiFetch } from './http'

export async function loadSaleReceipt(id: number, signal?: AbortSignal) {
  const response = await apiFetch('/api/v1/alon/sales/' + id + '/pdf', { signal })
  if (!response.ok || !response.headers.get('Content-Type')?.includes('application/pdf')) {
    throw new Error(
      'No se pudo abrir el comprobante. Podés volver a intentarlo sin registrar otra venta.',
    )
  }
  return response.blob()
}
