// Permite buscar artículos, editar sus datos y organizar el catálogo por rubro.
import { useState } from 'react'
import { CategoryPricePanel } from '../components/CategoryPricePanel'
import { apiFetch } from '../lib/http'
import { currency, textValue, numberValue } from '../lib/format'
import { useApiRows, type ApiRow } from '../hooks/useApiRows'
import { Pagination } from '../components/Pagination'
import { type EntryField, LocalEntryPanel } from '../components/Forms'
import { ApiTable } from '../components/ApiTable'
import { ArticleImageEditor } from '../components/ArticleImageEditor'
import type { SavedArticlePhoto } from '../components/ArticlePhotosField'

/** Presenta el catálogo con búsquedas, altas, edición, fotos, cambios de estado y aumentos por rubro según los permisos. */
export function ArticleList({ canEditCosts = false }: { canEditCosts?: boolean }) {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos los rubros')
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<ApiRow | null>(null)
  const [imageArticle, setImageArticle] = useState<{ id: number; name: string } | null>(null)
  const categoryRows = useApiRows('/api/v1/alon/categories')
  const apiRows = useApiRows(
    '/api/v1/alon/articles?inactive=' +
      showInactive +
      '&q=' +
      encodeURIComponent(query) +
      '&category=' +
      encodeURIComponent(category === 'Todos los rubros' ? '' : category),
  )
  const allRows = useApiRows('/api/v1/alon/articles')
  const articles = apiRows
    .map((row) => ({
      id: numberValue(row.id),
      code: textValue(row.codigo),
      name: textValue(row.nombre),
      category: textValue(row.rubro),
      stock: numberValue(row.stock),
      unit: textValue(row.unidad),
      cost: numberValue(row.costo),
      freight: numberValue(row.flete_unitario),
      freightPercent: numberValue(row.flete_porcentaje),
      margin: numberValue(row.margen_porcentaje),
      round: Boolean(row.redondeo_precio),
      price: numberValue(row.precio_venta),
      supplier: textValue(row.proveedor),
    }))
    .sort((a, b) => a.name.localeCompare(b.name, 'es-AR'))
  const categories = [
    ...new Set([
      ...categoryRows.map((row) => textValue(row.nombre)),
      ...articles.map((article) => article.category),
    ]),
  ].sort((a, b) => a.localeCompare(b, 'es-AR'))
  const filtered = articles.filter((article) => {
    const matchesQuery = `${article.code} ${article.name} ${article.category}`
      .toLocaleLowerCase('es-AR')
      .includes(query.toLocaleLowerCase('es-AR'))
    return matchesQuery && (category === 'Todos los rubros' || article.category === category)
  })

  /** Prepara la definición del formulario de artículo, incluyendo sus valores actuales y las opciones de rubros y proveedores. */
  const articleFields = (row?: ApiRow): EntryField[] => {
    /** Lee un dato del artículo que se edita o devuelve el valor inicial para un alta. */
    const value = (key: string, fallback = '') => (row ? textValue(row[key] ?? fallback) : fallback)
    const selectedCategory = value('rubro_id')
    const selectedSupplier = value('proveedor_principal_id')
    const categoryOptions = categoryRows.map((item) => ({
      value: String(item.id),
      label: textValue(item.nombre),
    }))
    return [
      {
        name: 'name',
        label: 'Nombre del artículo',
        defaultValue: value('nombre'),
        ...(!row
          ? {
              suggestions: allRows
                .filter((item) => textValue(item.estado) !== 'INACTIVO')
                .map((item) => textValue(item.nombre)),
            }
          : {}),
      },
      { name: 'code', label: 'Código o código de barras', defaultValue: value('codigo') },
      {
        name: 'categoryId',
        label: 'Rubro',
        valueType: 'number',
        options: categoryOptions,
        lookupEndpoint: '/api/v1/alon/categories',
        defaultLabel: value('rubro'),
        searchable: true,
        defaultValue: selectedCategory,
      },
      {
        name: 'supplierId',
        label: 'Proveedor principal',
        valueType: 'number',
        lookupEndpoint: '/api/v1/alon/lookups/suppliers',
        defaultLabel:
          value('proveedor_nombre', value('proveedor')) +
          (value('proveedor_estado') === 'INACTIVO' ? ' · inactivo' : ''),
        searchable: true,
        defaultValue: selectedSupplier,
      },
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
        name: 'freightAmount',
        label: 'Flete',
        type: 'number',
        defaultValue:
          row && numberValue(row.flete_unitario) > 0
            ? value('flete_unitario', '0')
            : value('flete_porcentaje', '0'),
        checkboxName: 'freightByPercent',
        checkboxLabel: 'Aplicar flete porcentual',
        checkedByDefault: !row || numberValue(row.flete_unitario) === 0,
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
  const refresh = () => window.dispatchEvent(new Event('alon:refresh'))
  /** Activa o desactiva un artículo en el servidor y recarga los listados si la operación se completa. */
  const setActive = async (id: number, active: boolean) => {
    const response = await apiFetch(
      `/api/v1/operations/articles/${id}/${active ? 'activate' : 'deactivate'}`,
      { method: 'POST' },
    )
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      window.alert(body.error ?? 'No se pudo cambiar el estado del artículo.')
      return
    }
    refresh()
  }

  return (
    <>
      {canEditCosts && <CategoryPricePanel />}
      {imageArticle && (
        <ArticleImageEditor
          key={imageArticle.id}
          {...imageArticle}
          onClose={() => setImageArticle(null)}
        />
      )}
      {canEditCosts &&
        (editing ? (
          <LocalEntryPanel
            key={'edit-' + textValue(editing.id)}
            title={'Modificar artículo · ' + textValue(editing.nombre)}
            action="Guardar cambios"
            endpoint={'/api/v1/operations/articles/' + textValue(editing.id)}
            method="PUT"
            pricePreview
            articlePhotos
            initialPhotos={(editing.photos ?? []) as SavedArticlePhoto[]}
            onSaved={() => setEditing(null)}
            initialOpen
            panelId="article-editor"
            duplicateNames={allRows
              .filter((item) => numberValue(item.id) !== numberValue(editing.id))
              .map((item) => textValue(item.nombre))}
            fields={articleFields(editing)}
          />
        ) : (
          <LocalEntryPanel
            title="Nuevo artículo"
            action="Cargar artículo"
            endpoint="/api/v1/operations/articles"
            pricePreview
            articlePhotos
            duplicateNames={allRows.map((row) => textValue(row.nombre))}
            fields={articleFields()}
          />
        ))}
      {editing && (
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
            <strong>{filtered.length} artículos</strong>
          </div>
          <div className="directory-filters">
            <input
              type="search"
              aria-label="Buscar artículos"
              placeholder="Buscar por nombre o código"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <select
              aria-label="Filtrar por rubro"
              value={category}
              onChange={(event) => setCategory(event.target.value)}
            >
              <option>Todos los rubros</option>
              {categories.map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
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
                <th>PROVEEDOR</th>
                <th>ACCIONES</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((article) => {
                const row = apiRows.find((item) => numberValue(item.id) === article.id)
                return (
                  <tr key={article.code}>
                    <td data-label="Código" className="mono-cell">
                      {article.code}
                    </td>
                    <td data-label="Artículo" className="strong-cell">
                      {article.name}
                    </td>
                    <td data-label="Sector">
                      <span className="category-chip">{article.category}</span>
                    </td>
                    <td data-label="Stock">
                      <span className={article.stock < 20 ? 'stock-low' : 'stock-ok'}>
                        {article.stock}
                      </span>{' '}
                      <span className="muted-cell">{article.unit}</span>
                    </td>
                    <td data-label="Costo">{canEditCosts ? currency.format(article.cost) : '—'}</td>
                    <td data-label="Flete">
                      {article.freightPercent
                        ? `${article.freightPercent}% del costo`
                        : article.freight
                          ? currency.format(article.freight)
                          : '—'}
                    </td>
                    <td data-label="Ganancia">
                      {canEditCosts ? article.margin + '%' : '—'}
                      {article.round ? ' · redondeo $10' : ''}
                    </td>
                    <td data-label="Precio" className="strong-cell">
                      {currency.format(article.price)}
                    </td>
                    <td data-label="Proveedor" className="mono-cell">
                      {article.supplier}
                    </td>
                    <td data-label="Acciones" className="article-actions">
                      <div className="row-actions">
                        {!showInactive && (
                          <button
                            className="secondary-button"
                            onClick={() => setImageArticle({ id: article.id, name: article.name })}
                          >
                            Fotos
                          </button>
                        )}
                        {article.id > 0 && (
                          <>
                            {!showInactive && canEditCosts && (
                              <button
                                type="button"
                                className="secondary-button"
                                onClick={() => row && setEditing(row)}
                              >
                                Modificar
                              </button>
                            )}
                            <button
                              type="button"
                              className="secondary-button"
                              onClick={() =>
                                void setActive(article.id, showInactive).catch(() => undefined)
                              }
                            >
                              {showInactive ? 'Activar' : 'Desactivar'}
                            </button>
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
  const rows = useApiRows('/api/v1/alon/categories')
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
        note={`${rows.length} rubros cargados. Los rubros activos aparecen al crear o modificar un artículo.`}
      />
    </>
  )
}
