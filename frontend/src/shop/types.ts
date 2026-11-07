export type Product = {
  id: number
  variants?: Product[]
  nombre: string
  tipo_producto?: string
  marca?: string
  modelo?: string
  talle?: string
  sistema_talle?: string
  color?: string
  material?: string
  publico?: string
  temporada?: string
  descripcion?: string
  codigo?: string
  rubro?: string
  unidad: string
  precio_venta: number
  stock: number
  imagen_clave: string | null
  imagen_ilustrativa: boolean
  photos?: { key: string; illustrative: boolean }[]
}
export type CartLine = Product & { quantity: number }
