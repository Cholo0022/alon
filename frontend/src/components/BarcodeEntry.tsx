import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { apiFetch } from '../lib/http'
import { textValue } from '../lib/format'
import type { ApiRow } from '../hooks/useApiRows'
import { LocalEntryPanel } from './Forms'
import { apparelFields } from '../lib/apparelFields'

export function BarcodeEntry({
  purchase = false,
  onSelect,
}: {
  purchase?: boolean
  onSelect: (article: ApiRow) => void
}) {
  const [code, setCode] = useState('')
  const [missing, setMissing] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const request = useRef(0)
  const find = async (value: string) => {
    const response = await apiFetch('/api/v1/alon/articles?code=' + encodeURIComponent(value))
    if (!response.ok) throw new Error('No se pudo consultar el código. Volvé a intentar.')
    const rows = (await response.json()) as ApiRow[]
    return rows.find((row) => textValue(row.codigo) === value) ?? null
  }
  const search = async () => {
    const value = code.trim()
    if (!value || busy) return
    const version = ++request.current
    setBusy(true)
    setMissing('')
    setMessage('')
    try {
      const article = await find(value)
      if (version !== request.current) return
      if (article) {
        onSelect(article)
        setCode('')
      } else {
        setMessage(
          purchase
            ? 'Código nuevo. Completá los datos del artículo.'
            : 'No existe un artículo activo con ese código.',
        )
        if (purchase) setMissing(value)
      }
    } catch (error) {
      if (version === request.current)
        setMessage(error instanceof Error ? error.message : 'No se pudo buscar.')
    } finally {
      if (version === request.current) setBusy(false)
    }
  }
  return (
    <div className="barcode-entry">
      <label>
        Código de barras
        <input
          aria-label="Código de barras"
          autoComplete="off"
          value={code}
          placeholder="Escaneá o escribí el código y pulsá Enter"
          onChange={(event) => {
            request.current++
            setBusy(false)
            setCode(event.target.value)
            setMessage('')
            setMissing('')
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
              event.preventDefault()
              event.stopPropagation()
              void search()
            }
          }}
        />
      </label>
      <button
        type="button"
        className="secondary-button"
        disabled={busy || !code.trim()}
        onClick={() => void search()}
      >
        {busy ? 'Buscando…' : 'Buscar código'}
      </button>
      {message && <p role="status">{message}</p>}
      {missing && (
        <NewPurchaseArticleDialog
          initialCode={missing}
          onClose={() => setMissing('')}
          onSaved={(saved) => {
            setMissing('')
            setCode('')
            void find(textValue(saved.codigo))
              .then((article) => {
                if (article) onSelect(article)
                else setMessage('Artículo creado. Volvé a buscar su código.')
              })
              .catch(() => setMessage('Artículo creado. Volvé a buscar su código.'))
          }}
        />
      )}
    </div>
  )
}

export function NewPurchaseArticleDialog({
  initialCode = '',
  onClose,
  onSaved,
}: {
  initialCode?: string
  onClose: () => void
  onSaved: (saved: Record<string, unknown>) => void
}) {
  useEffect(() => {
    const origin = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = overflow
      origin?.focus()
    }
  }, [])
  const [apparel, setApparel] = useState(false)
  return createPortal(
    <div className="article-picker-backdrop">
      <section
        className="article-picker barcode-create-dialog"
        role="dialog"
        aria-modal="true"
        onSubmit={(event) => event.stopPropagation()}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            onClose()
          }
          if (event.key === 'Tab') {
            const controls = Array.from(
              event.currentTarget.querySelectorAll<HTMLElement>(
                'input:not([type="hidden"]):not(:disabled),select:not(:disabled),textarea:not(:disabled),button:not(:disabled)',
              ),
            )
            const first = controls[0],
              last = controls[controls.length - 1]
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault()
              last?.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault()
              first?.focus()
            }
          }
        }}
        aria-label="Nuevo artículo de la compra"
      >
        <button type="button" className="secondary-button" onClick={() => onClose()}>
          Cancelar alta
        </button>
        <label>
          <input
            type="checkbox"
            checked={apparel}
            onChange={(event) => setApparel(event.target.checked)}
          />{' '}
          Indumentaria o calzado
        </label>
        <LocalEntryPanel
          key={initialCode + apparel}
          initialOpen
          title="Nuevo artículo de la compra"
          action="Cargar artículo"
          endpoint="/api/v1/operations/articles"
          articlePhotos
          pricePreview
          fields={[
            { name: 'code', label: 'Código o código de barras', defaultValue: initialCode },
            { name: 'name', label: 'Nombre del artículo' },
            ...(apparel
              ? apparelFields()
              : [
                  {
                    name: 'categoryId',
                    label: 'Rubro',
                    valueType: 'number' as const,
                    lookupEndpoint: '/api/v1/alon/categories',
                  },
                ]),
            {
              name: 'unit',
              label: 'Unidad',
              defaultValue: 'UNIDAD',
              options: (apparel ? ['UNIDAD'] : ['UNIDAD', 'KILO', 'METRO', 'LITRO', 'CAJA']).map(
                (value) => ({ value, label: value }),
              ),
            },
            ...['minimumStock', 'cost', 'freightPercent', 'marginPercent'].map((name, index) => ({
              name,
              label: [
                'Stock mínimo',
                'Costo unitario',
                'Flete (% del costo)',
                'Porcentaje de ganancia',
              ][index],
              type: 'number',
              defaultValue: '0',
            })),
            {
              name: 'roundPrice',
              label: 'Redondear al próximo $10',
              type: 'checkbox',
              defaultValue: 'true',
            },
            { name: 'publishOnWeb', label: 'Publicar en la página', type: 'checkbox' },
          ]}
          onSaved={onSaved}
        />
      </section>
    </div>,
    document.body,
  )
}
