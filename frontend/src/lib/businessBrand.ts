// Comparte la identidad del comercio y actualiza sus colores cuando se guardan cambios.
import { useEffect, useState, type CSSProperties } from 'react'
import { isShopTemplate, type ShopTemplateId } from './shopTemplates'
import { apiFetch } from './http'
import { shopRequest } from '../shop/api'

export type BusinessBrand = {
  nombre: string
  slogan: string
  color_principal: string
  color_secundario: string
  color_fondo: string
  plantilla_tienda: ShopTemplateId
  titulo_tienda: string
  descripcion_tienda: string
}
export const defaultBrand: BusinessBrand = {
  nombre: 'Mi comercio',
  slogan: '',
  color_principal: '#006b73',
  color_secundario: '#102a43',
  color_fondo: '#f4f7f9',
  plantilla_tienda: 'clasica',
  titulo_tienda: 'Todo lo que necesitás, a un carrito de distancia.',
  descripcion_tienda: 'Elegí tus productos. Nosotros preparamos tu pedido.',
}
/** Acepta un color hexadecimal válido o devuelve el color de respaldo para evitar estilos inválidos. */
const color = (value: unknown, fallback: string) =>
  typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value : fallback
/** Completa la identidad del negocio con valores seguros cuando faltan datos o los colores recibidos no son válidos. */
export function normalizeBrand(value: Partial<BusinessBrand> | null | undefined): BusinessBrand {
  return {
    nombre: value?.nombre || defaultBrand.nombre,
    slogan: value?.slogan || '',
    color_principal: color(value?.color_principal, defaultBrand.color_principal),
    color_secundario: color(value?.color_secundario, defaultBrand.color_secundario),
    color_fondo: color(value?.color_fondo, defaultBrand.color_fondo),
    plantilla_tienda: isShopTemplate(value?.plantilla_tienda)
      ? value.plantilla_tienda
      : defaultBrand.plantilla_tienda,
    titulo_tienda: value?.titulo_tienda || defaultBrand.titulo_tienda,
    descripcion_tienda: value?.descripcion_tienda || defaultBrand.descripcion_tienda,
  }
}
/** Calcula si conviene texto negro o blanco sobre un color según su luminancia para mantener el contraste. */
export function onColor(hex: string): string {
  const rgb = [1, 3, 5]
    .map((index) => parseInt(hex.slice(index, index + 2), 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722 > 0.179 ? '#000000' : '#ffffff'
}
/** Transforma los colores del comercio en variables CSS y calcula el color de texto de cada fondo. */
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
/** Notifica la identidad guardada a esta pestaña y avisa a las demás mediante almacenamiento local. */
export function announceBrand(brand: BusinessBrand) {
  window.dispatchEvent(new CustomEvent('alon:business-updated', { detail: brand }))
  try {
    localStorage.setItem('alon-business-updated', String(Date.now()))
  } catch {
    /* Current tab still updates. */
  }
}
/** Obtiene la identidad del negocio y la sincroniza por eventos, foco y consultas periódicas, tanto en gestión como en la tienda. */
export function useBusinessBrand(shop = false) {
  const [brand, setBrand] = useState(defaultBrand)
  useEffect(() => {
    let stopped = false
    let version = 0
    /** Consulta la identidad del negocio e ignora respuestas antiguas o recibidas después de desmontar el componente. */
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
    /** Dispara una nueva consulta de la identidad del negocio. */
    const refresh = () => {
      void load()
    }
    /** Aplica inmediatamente la identidad recibida en el evento local e invalida consultas anteriores. */
    const updated = (event: Event) => {
      version++
      setBrand(normalizeBrand((event as CustomEvent<BusinessBrand>).detail))
    }
    /** Recarga la identidad si otra pestaña anuncia cambios del negocio. */
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
