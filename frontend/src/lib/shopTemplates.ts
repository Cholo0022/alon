// Catálogo cerrado de diseños: todos comparten los mismos componentes y operaciones de tienda.
export const shopTemplates = [
  {
    id: 'clasica',
    name: 'Clásica',
    description: 'Catálogo equilibrado, tarjetas y carrito lateral.',
  },
  {
    id: 'minimal',
    name: 'Minimalista',
    description: 'Espacios limpios, líneas finas y protagonismo del producto.',
  },
  {
    id: 'boutique',
    name: 'Boutique',
    description: 'Cabecera centrada y tipografía editorial elegante.',
  },
  {
    id: 'urbana',
    name: 'Urbana',
    description: 'Títulos fuertes, bordes rectos y composición en bloques.',
  },
  { id: 'editorial', name: 'Editorial', description: 'Portada amplia y catálogo de dos columnas.' },
  {
    id: 'mercado',
    name: 'Mercado',
    description: 'Productos en filas para comparar y comprar con rapidez.',
  },
  {
    id: 'natural',
    name: 'Natural',
    description: 'Formas suaves y superficies con el color de tu marca.',
  },
  {
    id: 'tecnologica',
    name: 'Tecnológica',
    description: 'Tipografía técnica, cuadrícula y detalles de precisión.',
  },
  {
    id: 'artesanal',
    name: 'Artesanal',
    description: 'Estética cálida, líneas orgánicas y títulos con carácter.',
  },
  {
    id: 'vitrina',
    name: 'Vitrina',
    description: 'Fotos grandes y tarjetas amplias para destacar cada artículo.',
  },
] as const
export type ShopTemplateId = (typeof shopTemplates)[number]['id']

/** Comprueba que el diseño recibido pertenezca a las diez plantillas disponibles. */
export function isShopTemplate(value: unknown): value is ShopTemplateId {
  return shopTemplates.some((template) => template.id === value)
}
