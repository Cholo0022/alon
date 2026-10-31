// Comparte la identidad del comercio y actualiza sus colores cuando se guardan cambios.
import { useEffect, useState, type CSSProperties } from 'react'
import { apiFetch } from './http'
import { shopRequest } from '../shop/api'

export type BusinessBrand = {
  nombre: string
  slogan: string
  color_principal: string
  color_secundario: string
  color_fondo: string
}
export const defaultBrand: BusinessBrand = {
  nombre: 'Mi comercio',
  slogan: '',
  color_principal: '#006b73',
  color_secundario: '#102a43',
  color_fondo: '#f4f7f9',
}
const color = (value: unknown, fallback: string) =>
  typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value : fallback
export function normalizeBrand(value: Partial<BusinessBrand> | null | undefined): BusinessBrand {
  return {
    nombre: value?.nombre || defaultBrand.nombre,
    slogan: value?.slogan || '',
    color_principal: color(value?.color_principal, defaultBrand.color_principal),
    color_secundario: color(value?.color_secundario, defaultBrand.color_secundario),
    color_fondo: color(value?.color_fondo, defaultBrand.color_fondo),
  }
}
export function onColor(hex: string): string {
  const rgb = [1, 3, 5]
    .map((index) => parseInt(hex.slice(index, index + 2), 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722 > 0.179 ? '#000000' : '#ffffff'
}
export function brandStyle(brand: BusinessBrand): CSSProperties {
  return {
    '--business-primary': brand.color_principal,
    '--business-secondary': brand.color_secundario,
    '--business-background': brand.color_fondo,
    '--business-on-primary': onColor(brand.color_principal),
    '--business-on-secondary': onColor(brand.color_secundario),
    '--business-on-background': onColor(brand.color_fondo),
  } as CSSProperties
}
export function announceBrand(brand: BusinessBrand) {
  window.dispatchEvent(new CustomEvent('alon:business-updated', { detail: brand }))
  try {
    localStorage.setItem('alon-business-updated', String(Date.now()))
  } catch {
    /* Current tab still updates. */
  }
}
export function useBusinessBrand(shop = false) {
  const [brand, setBrand] = useState(defaultBrand)
  useEffect(() => {
    let stopped = false
    let version = 0
    const load = async () => {
      const current = ++version
      try {
        const result = shop
          ? await shopRequest<BusinessBrand>('/business')
          : await apiFetch('/api/v1/business').then(async (response) => {
              if (!response.ok) throw new Error('No se pudo cargar el negocio.')
              return response.json() as Promise<BusinessBrand>
            })
        if (!stopped && current === version) setBrand(normalizeBrand(result))
      } catch {
        /* Keep the last saved appearance when disconnected. */
      }
    }
    const refresh = () => {
      void load()
    }
    const updated = (event: Event) => {
      version++
      setBrand(normalizeBrand((event as CustomEvent<BusinessBrand>).detail))
    }
    const storage = (event: StorageEvent) => {
      if (event.key === 'alon-business-updated') refresh()
    }
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refresh()
    }, 30000)
    refresh()
    window.addEventListener('focus', refresh)
    window.addEventListener('storage', storage)
    window.addEventListener('alon:business-updated', updated)
    return () => {
      stopped = true
      clearInterval(timer)
      window.removeEventListener('focus', refresh)
      window.removeEventListener('storage', storage)
      window.removeEventListener('alon:business-updated', updated)
    }
  }, [shop])
  return brand
}
