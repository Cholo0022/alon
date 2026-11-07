import { LookupField } from '../components/LookupField'
import { GarmentEditor } from '../components/GarmentEditor'
import { AddApparelSizesDialog } from '../components/AddApparelSizesDialog'
import { CatalogArticleEntry } from '../components/CatalogArticleEntry'
import { apparelFields } from '../lib/apparelFields'
import { DeleteRecordButton } from '../components/DeleteRecordButton'
// Permite buscar artículos, editar sus datos y organizar el catálogo por rubro.
import { useState } from 'react'
import { CategoryPricePanel } from '../components/CategoryPricePanel'

import { currency, textValue, numberValue } from '../lib/format'
import { useApiRows, rowCount, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from '../components/Pagination'
import { type EntryField, LocalEntryPanel } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'
import { ArticleImageEditor } from '../components/ArticleImageEditor'
import type { SavedArticlePhoto } from '../components/ArticlePhotosField'

/** Presenta el catálogo con búsquedas, altas, edición, fotos, cambios de estado y aumentos por rubro según los permisos. */
export function ArticleList({
  canEditCosts = false,
  draftScope,
}: {
  canEditCosts?: boolean
  draftScope?: string
}) {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos los rubros')
  const [showInactive, setShowInactive] = useState(false)
  const [addingSizes, setAddingSizes] = useState(false)
  const [sizesMessage, setSizesMessage] = useState('')
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const [imageArticle, setImageArticle] = useState<{ id: number; name: string } | null>(null)
  const apiRows = useApiRows(
    '/api/v1/alon/articles/grouped?inactive=' +
      showInactive +
      '&q=' +
      encodeURIComponent(query) +
      '&category=' +
      encodeURIComponent(category === 'Todos los rubros' ? '' : category),
  )
  const articles = apiRows
    .map((row) => ({
      id: numberValue(row.id),
      code: textValue(row.codigo),
      name: textValue(row.nombre_publico ?? row.nombre),
      grouped: Array.isArray(row.variants),
      sizes: Array.isArray(row.variants)
        ? new Set((row.variants as ApiRow[]).map((item) => textValue(item.talle))).size
        : 0,
      category: textValue(row.rubro),
      stock: numberValue(row.stock_total ?? row.stock),
      unit: textValue(row.unidad),
      cost: numberValue(row.costo),
      freightPercent: numberValue(row.flete_porcentaje),
      margin: numberValue(row.margen_porcentaje),
      round: Boolean(row.redondeo_precio),
      price: Array.isArray(row.variants)
        ? Math.min(...(row.variants as ApiRow[]).map((item) => numberValue(item.precio_venta)))
        : numberValue(row.precio_venta),
      priceVaries:
        Array.isArray(row.variants) &&
        new Set((row.variants as ApiRow[]).map((item) => numberValue(item.precio_venta))).size > 1,
    }))
    .sort((a, b) => a.name.localeCompare(b.name, 'es-AR'))
  const filtered = articles

  /** Prepara la definición del formulario de artículo, incluyendo sus valores actuales y las opciones de rubros y proveedores. */
  const articleFields = (row?: ApiRow): EntryField[] => {
    /** Lee un dato del artículo que se edita o devuelve el valor inicial para un alta. */
    const value = (key: string, fallback = '') => (row ? textValue(row[key] ?? fallback) : fallback)
    const selectedCategory = value('rubro_id')
    return [
      ...(row && row.tipo_producto !== 'ARTICULO' && row.tipo_producto ? apparelFields(row) : []),
      {
        name: 'name',
        label: 'Nombre del artículo',
        defaultValue:
          row &&
          row.tipo_producto &&
          row.tipo_producto !== 'ARTICULO' &&
          value('nombre').endsWith(' · ' + value('talle') + ' · ' + value('color'))
            ? value('nombre').slice(0, -(' · ' + value('talle') + ' · ' + value('color')).length)
            : value('nombre'),
        ...(!row
          ? {
              suggestions: [],
              suggestionEndpoint: '/api/v1/alon/articles',
            }
          : {}),
      },
      { name: 'code', label: 'Código o código de barras', defaultValue: value('codigo') },
      ...(!(row && row.tipo_producto && row.tipo_producto !== 'ARTICULO')
        ? [
            {
              name: 'categoryId',
              label: 'Rubro',
              valueType: 'number' as const,
              lookupEndpoint: '/api/v1/alon/categories',
              defaultLabel: value('rubro'),
              searchable: true,
              defaultValue: selectedCategory,
            },
          ]
        : []),
      {
        name: 'unit',
        label: 'Unidad',
        options: ['UNIDAD', 'KILO', 'METRO', 'LITRO', 'CAJA'].map((item) => ({
          value: item,
          label: item,
        })),
        defaultValue: value('unidad', 'UNIDAD'),
      },
      {
        name: 'initialStock',
        label: row ? 'Cantidad / stock actual' : 'Stock inicial',
        type: 'number',
        defaultValue: value('stock', '0'),
      },
      {
        name: 'minimumStock',
        label: 'Stock mínimo',
        type: 'number',
        defaultValue: value('stock_minimo', '0'),
      },
      {
        name: 'cost',
        label: 'Costo del artículo',
        type: 'number',
        defaultValue: value('costo', '0'),
      },
      {
        name: 'freightPercent',
        label: 'Flete (% del costo)',
        type: 'number',
        defaultValue: value('flete_porcentaje', '0'),
      },
      {
        name: 'marginPercent',
        label: 'Porcentaje de ganancia',
        type: 'number',
        defaultValue: value('margen_porcentaje', '0'),
      },
      {
        name: 'roundPrice',
        label: 'Redondear hacia arriba al próximo $10',
        type: 'checkbox',
        defaultValue: !row || Boolean(row.redondeo_precio) ? 'true' : undefined,
      },
      {
        name: 'publishOnWeb',
        label: 'Cargar en la web',
        type: 'checkbox',
        defaultValue: row && Boolean(row.cargar_web) ? 'true' : undefined,
      },
      ...(row
        ? [
            {
              name: 'salePrice',
              label: 'Precio de venta',
              type: 'number',
              defaultValue: value('precio_venta', '0'),
            },
          ]
        : []),
    ]
  }
  /** Emite el evento de actualización para recargar los listados después de una modificación. */

  return (
    <>
      {canEditCosts && <CategoryPricePanel />}
      {sizesMessage && (
        <p role="status" className="entry-message">
          {sizesMessage}
        </p>
      )}
      {addingSizes && editing && (
        <AddApparelSizesDialog
          article={editing}
          onClose={() => setAddingSizes(false)}
          onSaved={() => {
            setAddingSizes(false)
            setSizesMessage('Talles agregados. Se conservaron los stocks que ya estaban cargados.')
          }}
        />
      )}
      {imageArticle && (
        <ArticleImageEditor
          key={imageArticle.id}
          {...imageArticle}
          onClose={() => setImageArticle(null)}
        />
      )}
      {(canEditCosts || (editing && Array.isArray(editing.variants))) &&
        (editing && Array.isArray(editing.variants) ? (
          <GarmentEditor
            articleId={Number(editing.id)}
            canEdit={canEditCosts && !showInactive}
            onBack={() => setEditing(null)}
            onSaved={() => {
              setEditing(null)
              window.dispatchEvent(new Event('alon:refresh'))
            }}
          />
        ) : editing ? (
          <LocalEntryPanel
            key={'edit-' + textValue(editing.id)}
            title={'Modificar artículo · ' + textValue(editing.nombre)}
            headerContent={
              editing.tipo_producto && editing.tipo_producto !== 'ARTICULO' ? (
                <div className="entry-actions">
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => {
                      setSizesMessage('')
                      setAddingSizes(true)
                    }}
                  >
                    Agregar talles a esta prenda
                  </button>
                </div>
              ) : undefined
            }
            action="Guardar cambios"
            endpoint={'/api/v1/operations/articles/' + textValue(editing.id)}
            method="PUT"
            pricePreview
            articlePhotos
            initialPhotos={(editing.photos ?? []) as SavedArticlePhoto[]}
            onSaved={() => setEditing(null)}
            initialOpen
            panelId="article-editor"
            fields={articleFields(editing)}
          />
        ) : (
          <CatalogArticleEntry
            draftScope={draftScope}
            fields={articleFields()}
            duplicateNames={[]}
          />
        ))}
      {editing && !Array.isArray(editing.variants) && (
        <button
          className="secondary-button article-cancel-edit"
          type="button"
          onClick={() => setEditing(null)}
        >
          Cancelar modificación
        </button>
      )}
      <section className="directory-panel">
        <div className="directory-toolbar">
          <div>
            <span className="alon-tag">{showInactive ? 'ARTÍCULOS DESACTIVADOS' : 'CATÁLOGO'}</span>
            <strong>{rowCount(apiRows, 'productos')}</strong>
          </div>
          <div className="directory-filters">
            <input
              type="search"
              aria-label="Buscar artículos"
              placeholder="Buscar por nombre o código"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <LookupField
              key={String(showInactive) + category}
              name="catalogCategory"
              label="Filtrar por rubro"
              endpoint="/api/v1/alon/categories"
              required={false}
              initialRecord={
                category === 'Todos los rubros' ? undefined : { id: category, nombre: category }
              }
              onSelect={(row) => setCategory(row ? textValue(row.nombre) : 'Todos los rubros')}
            />
            <button
              type="button"
              className="secondary-button"
              onClick={() => setCategory('Todos los rubros')}
            >
              Todos los rubros
            </button>
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setShowInactive((value) => !value)
                setQuery('')
                setCategory('Todos los rubros')
              }}
            >
              {showInactive ? 'Ver artículos activos' : 'Ver desactivados'}
            </button>
          </div>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>CÓDIGO</th>
                <th>ARTÍCULO</th>
                <th>SECTOR</th>
                <th>STOCK</th>
                <th>COSTO</th>
                <th>FLETE</th>
                <th>GANANCIA</th>
                <th>PRECIO</th>
                <th>ACCIONES</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((article) => {
                const row = apiRows.find((item) => numberValue(item.id) === article.id)
                return (
                  <tr key={article.code}>
                    <td data-label="Código" className="mono-cell">
                      {article.grouped ? textValue(row?.modelo) || 'Prenda' : article.code}
                    </td>
                    <td data-label="Artículo" className="strong-cell">
                      {article.name}
                    </td>
                    <td data-label="Sector">
                      <span className="category-chip">{article.category}</span>
                    </td>
                    <td data-label="Stock">
                      {article.grouped && <small>{article.sizes} talles · </small>}
                      <span className={article.stock < 20 ? 'stock-low' : 'stock-ok'}>
                        {article.stock}
                      </span>{' '}
                      <span className="muted-cell">{article.unit}</span>
                    </td>
                    <td data-label="Costo">{canEditCosts ? currency.format(article.cost) : '—'}</td>
                    <td data-label="Flete">{article.freightPercent}% del costo</td>
                    <td data-label="Ganancia">
                      {canEditCosts ? article.margin + '%' : '—'}
                      {article.round ? ' · redondeo $10' : ''}
                    </td>
                    <td data-label="Precio" className="strong-cell">
                      {article.priceVaries ? 'Desde ' : ''}
                      {currency.format(article.price)}
                    </td>
                    <td data-label="Acciones" className="article-actions">
                      <div className="row-actions">
                        {!showInactive && !article.grouped && (
                          <button
                            className="secondary-button"
                            onClick={() => setImageArticle({ id: article.id, name: article.name })}
                          >
                            Fotos
                          </button>
                        )}
                        {article.id > 0 && (
                          <>
                            {(article.grouped || (!showInactive && canEditCosts)) && (
                              <button
                                type="button"
                                className="secondary-button"
                                onClick={() => row && setEditing(row)}
                              >
                                {canEditCosts && !showInactive ? 'Modificar' : 'Ver talles'}
                              </button>
                            )}
                            {!article.grouped && (
                              <DeleteRecordButton
                                endpoint={`/api/v1/operations/articles/${article.id}`}
                                name={article.name}
                              />
                            )}
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {!apiRows.loading && !apiRows.error && filtered.length === 0 && (
            <div className="no-results">
              {showInactive
                ? 'No hay artículos desactivados.'
                : 'No hay artículos que coincidan con la búsqueda.'}
            </div>
          )}
        </div>
        <Pagination rows={apiRows} label="artículos" />
        <div className="table-footer">Precios y existencias del comercio.</div>
      </section>
      <ApiTable
        title="Movimientos de stock"
        endpoint="/api/v1/alon/stock-movements"
        columns={[
          { key: 'fecha_hora', label: 'FECHA', date: true },
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'articulo', label: 'ARTÍCULO' },
          { key: 'tipo', label: 'MOVIMIENTO' },
          { key: 'cantidad', label: 'CANTIDAD' },
          { key: 'referencia', label: 'REFERENCIA' },
          { key: 'descripcion', label: 'DETALLE' },
        ]}
        note="Los movimientos se registran junto con las altas, ventas y compras confirmadas."
      />
    </>
  )
}

/** Presenta el alta y el listado de rubros usados para organizar los artículos. */
export function CategoriesPage() {
  return (
    <>
      <LocalEntryPanel
        title="Nuevo rubro"
        action="Crear rubro"
        endpoint="/api/v1/operations/categories"
        fields={[{ name: 'name', label: 'Nombre del rubro' }]}
      />
      <ApiTable
        title="Rubros disponibles para artículos"
        endpoint="/api/v1/alon/categories"
        columns={[
          { key: 'codigo', label: 'CÓDIGO' },
          { key: 'nombre', label: 'RUBRO' },
        ]}
        note="Los rubros activos aparecen al crear o modificar un artículo."
      />
    </>
  )
}
