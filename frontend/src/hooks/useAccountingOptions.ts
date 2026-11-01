import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/http'
export type FiscalTarget = {
  clase: 'VENTA' | 'COMPRA' | 'GASTO'
  id: number
  fecha: string
  total: number
  tercero: string
  documento?: string
}
export function announceFiscalTarget(target: FiscalTarget) {
  window.dispatchEvent(new CustomEvent('alon:fiscal-target', { detail: target }))
}
export function useAccountingOptions() {
  const [options, setOptions] = useState({ controlPeriodos: false, cargaImpuestos: false })
  useEffect(() => {
    let active = true
    const load = () => {
      void apiFetch('/api/v1/accounting/options')
        .then(async (response) => {
          if (response.ok && active) {
            const data = (await response.json()) as Record<string, unknown>
            setOptions({
              controlPeriodos: !!data.controlPeriodos,
              cargaImpuestos: !!data.cargaImpuestos,
            })
          }
        })
        .catch(() => undefined)
    }
    load()
    window.addEventListener('alon:refresh', load)
    return () => {
      active = false
      window.removeEventListener('alon:refresh', load)
    }
  }, [])
  return options
}
