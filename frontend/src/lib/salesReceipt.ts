import { apiFetch } from './http'

export async function loadSaleReceipt(id: number, signal?: AbortSignal) {
  return loadReceiptPdf(
    '/api/v1/alon/sales/' + id + '/pdf',
    signal,
    'No se pudo abrir el comprobante. Podés volver a intentarlo sin registrar otra venta.',
  )
}

export async function loadReceiptPdf(
  path: string,
  signal?: AbortSignal,
  message = 'No se pudo abrir el comprobante. Podés volver a intentarlo desde la vista previa.',
) {
  const response = await apiFetch(path, { signal })
  if (!response.ok || !response.headers.get('Content-Type')?.includes('application/pdf')) {
    throw new Error(message)
  }
  return response.blob()
}
