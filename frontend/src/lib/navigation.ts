// Define las secciones del menú y las descripciones que aparecen en cada pantalla.
export type Section =
  | 'inicio'
  | 'ventas'
  | 'articulos'
  | 'rubros'
  | 'clientes'
  | 'compras'
  | 'proveedores'
  | 'caja'
  | 'informes'
  | 'tienda'
  | 'usuarios'
  | 'negocio'

export const navigation: { id: Section; label: string; icon: string }[] = [
  { id: 'inicio', label: 'Resumen', icon: '⌂' },
  { id: 'ventas', label: 'Ventas', icon: '↗' },
  { id: 'articulos', label: 'Artículos y stock', icon: '▦' },
  { id: 'rubros', label: 'Rubros', icon: '▤' },
  { id: 'clientes', label: 'Clientes', icon: '♙' },
  { id: 'compras', label: 'Compras', icon: '↙' },
  { id: 'proveedores', label: 'Proveedores', icon: '♧' },
  { id: 'caja', label: 'Caja', icon: '▣' },
  { id: 'informes', label: 'Informes', icon: '▤' },
  { id: 'tienda', label: 'Tienda online', icon: '⌑' },
  { id: 'usuarios', label: 'Usuarios y accesos', icon: '♙' },
  { id: 'negocio', label: 'Modificar negocio', icon: '⚙' },
]

export const descriptions: Record<Section, string> = {
  inicio: 'La actividad del negocio, en un solo lugar.',
  ventas: 'Registrá ventas de contado o en cuenta corriente.',
  articulos: 'Explorá los artículos del comercio y sus niveles de stock.',
  rubros: 'Creá y consultá los rubros disponibles para organizar tus artículos.',
  clientes: 'Consultá los clientes y sus saldos.',
  compras: 'Cargá facturas y remitos para actualizar costos y stock.',
  proveedores: 'Consultá proveedores y saldos pendientes.',
  caja: 'Controlá aperturas, cobros, gastos y cierres de caja.',
  informes: 'Revisá ventas, costos y gastos por período.',
  tienda: 'Prepará el catálogo online de tu comercio.',
  usuarios: 'Creá usuarios y elegí a qué secciones pueden acceder.',
  negocio: 'Personalizá el nombre, slogan y colores de Alon y de tu tienda web.',
}
