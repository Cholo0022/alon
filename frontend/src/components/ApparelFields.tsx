import { apparelFields } from '../lib/apparelFields'
import { LocalEntryPanel } from './Forms'

/** Se registra una combinación por código: evita mezclar existencias de distintos talles. */
export function ApparelEntry({ initialStock = true }: { initialStock?: boolean }) {
  return (
    <LocalEntryPanel
      title="Indumentaria y calzado · un talle y color por código"
      action="Cargar indumentaria"
      endpoint="/api/v1/operations/articles"
      pricePreview
      articlePhotos
      fields={[
        { name: 'name', label: 'Nombre de publicación (sin talle ni color)' },
        { name: 'code', label: 'Código único / código de barras de la variante' },
        ...apparelFields(),
        {
          name: 'unit',
          label: 'Unidad de venta (en calzado: un par)',
          options: [{ value: 'UNIDAD', label: 'Unidad / par' }],
          defaultValue: 'UNIDAD',
        },
        ...(initialStock
          ? [
              {
                name: 'initialStock',
                label: 'Stock de este talle y color',
                type: 'number',
                defaultValue: '0',
              },
            ]
          : []),
        {
          name: 'minimumStock',
          label: 'Stock mínimo de la variante',
          type: 'number',
          defaultValue: '0',
        },
        { name: 'cost', label: 'Costo unitario / por par', type: 'number', defaultValue: '0' },
        {
          name: 'freightPercent',
          label: 'Flete (% del costo)',
          type: 'number',
          defaultValue: '0',
        },
        {
          name: 'marginPercent',
          label: 'Porcentaje de ganancia',
          type: 'number',
          defaultValue: '0',
        },
        {
          name: 'roundPrice',
          label: 'Redondear al próximo $10',
          type: 'checkbox',
          defaultValue: 'true',
        },
        { name: 'publishOnWeb', label: 'Publicar en la página', type: 'checkbox' },
      ]}
    />
  )
}
