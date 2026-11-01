import type { EntryField } from '../components/Forms'
import type { ApiRow } from '../hooks/useApiRows'
import { textValue } from './format'

export function apparelFields(row?: ApiRow): EntryField[] {
  const field = (name: string, label: string, column: string, fallback = ''): EntryField => ({
    name,
    label,
    defaultValue: row ? textValue(row[column]) : fallback,
  })
  return [
    {
      ...field('apparelKind', 'Clase de producto', 'tipo_producto', 'INDUMENTARIA'),
      options: [
        { value: 'INDUMENTARIA', label: 'Indumentaria' },
        { value: 'CALZADO', label: 'Calzado' },
      ],
    },
    field('apparelModel', 'Modelo / referencia', 'modelo'),
    field('apparelBrand', 'Marca (o Sin marca)', 'marca'),
    field('apparelSize', 'Talle de esta variante (S, M, L, 38, 40…)', 'talle'),
    {
      ...field('apparelSizeSystem', 'Sistema de talles', 'sistema_talle', 'AR'),
      options: ['AR', 'EU', 'US', 'UK', 'LETRAS', 'UNICO', 'OTRO'].map((value) => ({
        value,
        label: value,
      })),
    },
    field('apparelColor', 'Color de esta variante', 'color'),
    field('apparelMaterial', 'Material / composición', 'material'),
    {
      ...field('apparelAudience', 'Público', 'publico', 'UNISEX'),
      options: ['UNISEX', 'MUJER', 'HOMBRE', 'INFANTIL', 'BEBE'].map((value) => ({
        value,
        label: value,
      })),
    },
    {
      ...field('apparelSeason', 'Temporada', 'temporada', 'TODO EL AÑO'),
      options: ['TODO EL AÑO', 'PRIMAVERA / VERANO', 'OTOÑO / INVIERNO'].map((value) => ({
        value,
        label: value,
      })),
    },
    {
      ...field(
        'apparelDescription',
        'Descripción para la página (medidas, cuidados y detalles)',
        'descripcion',
      ),
      type: 'textarea',
    },
  ]
}
