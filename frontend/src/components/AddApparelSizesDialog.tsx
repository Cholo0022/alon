import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { ApiRow } from '../hooks/useApiRows'
import { apiFetch } from '../lib/http'
import { textValue } from '../lib/format'
import { clearFieldValidation, showFieldErrors, validateForm } from '../lib/formValidation'
import { preventImplicitArticleSubmit } from '../lib/articleKeyboard'
import { PurchaseDialog } from './PurchaseDialog'

type Variant = { key: string; size: string; color: string; quantity: string; code: string }
const newVariant = (color: string): Variant => ({
  key: 'VAR-' + crypto.randomUUID().slice(0, 12).toUpperCase(),
  size: '',
  color,
  quantity: '0',
  code: '',
})

/** Agrega combinaciones nuevas al modelo guardado sin reemplazar las cantidades anteriores. */
export function AddApparelSizesDialog({
  article,
  onClose,
  onSaved,
}: {
  article: ApiRow
  onClose: () => void
  onSaved: () => void
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const [rows, setRows] = useState<Variant[]>(() => [newVariant(textValue(article.color))])
  const [existing, setExisting] = useState<ApiRow[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void (async () => {
      try {
        const all: ApiRow[] = []
        for (let page = 0; ; page++) {
          const response = await apiFetch(
            `/api/v1/alon/articles/${Number(article.id)}/variants?page=${page}&size=200`,
            { signal: controller.signal },
          )
          if (!response.ok) throw new Error('No se pudieron consultar los talles actuales.')
          const chunk: unknown = await response.json()
          if (!Array.isArray(chunk)) throw new Error('No se pudo leer la lista de talles.')
          all.push(...(chunk as ApiRow[]))
          if (chunk.length < 200) break
          if (all.length >= 2000)
            throw new Error('Este modelo tiene demasiadas variantes para esta pantalla.')
        }
        if (!controller.signal.aborted) {
          setExisting(all)
          setLoading(false)
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setLoadError(
            error instanceof Error ? error.message : 'No se pudieron consultar los talles.',
          )
          setLoading(false)
        }
      }
    })()
    return () => controller.abort()
  }, [article.id])
  useEffect(() => {
    if (!loading && !loadError) formRef.current?.querySelector<HTMLInputElement>('input')?.focus()
  }, [loading, loadError])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy || loading || loadError) return
    const form = event.currentTarget
    const invalid = validateForm(form)
    if (invalid) {
      setError(invalid)
      return
    }
    const seen = new Set(
      existing.map(
        (row) =>
          textValue(row.talle).trim().toLocaleUpperCase('es-AR') +
          '\n' +
          textValue(row.color).trim().toLocaleUpperCase('es-AR'),
      ),
    )
    const errors: Record<string, string> = {}
    const variants = rows.map((row, index) => {
      const key =
        row.size.trim().toLocaleUpperCase('es-AR') +
        '\n' +
        row.color.trim().toLocaleUpperCase('es-AR')
      if (seen.has(key))
        errors[`variants[${index}].size`] = 'Este talle y color ya existen en la prenda.'
      seen.add(key)
      return {
        code: row.code.trim() || row.key,
        size: row.size.trim(),
        color: row.color.trim(),
        quantity: Number(row.quantity),
      }
    })
    if (Object.keys(errors).length) {
      showFieldErrors(form, errors)
      setError('Elegí talles y colores que aún no estén cargados.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch(
        `/api/v1/operations/articles/${Number(article.id)}/variants`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ variants }),
        },
      )
      const result = await response.json()
      if (!response.ok) {
        showFieldErrors(form, result.fields ?? {})
        throw new Error(result.error ?? 'No se pudieron agregar los talles.')
      }
      onSaved()
      window.dispatchEvent(new Event('alon:refresh'))
    } catch (error) {
      setError(error instanceof Error ? error.message : 'No se pudieron guardar los talles.')
      setBusy(false)
    }
  }
  return (
    <PurchaseDialog title="Agregar talles a esta prenda" busy={busy} onClose={onClose} wide>
      <form
        ref={formRef}
        noValidate
        onSubmit={submit}
        onKeyDown={preventImplicitArticleSubmit}
        onInputCapture={(event) => {
          clearFieldValidation(event)
          setError('')
        }}
        onChangeCapture={clearFieldValidation}
      >
        <p>
          <strong>
            {textValue(article.modelo)} · {textValue(article.marca)}
          </strong>
        </p>
        <p>
          Los nuevos talles usarán los datos, el precio y las fotos guardados de esta prenda. Indicá
          únicamente el stock inicial de los talles nuevos.
        </p>
        {loading ? (
          <p role="status">Consultando talles actuales…</p>
        ) : (
          <details>
            <summary>Ver talles ya cargados ({existing.length})</summary>
            <ul>
              {existing.map((row) => (
                <li key={textValue(row.id)}>
                  {textValue(row.talle)} · {textValue(row.color)}: {textValue(row.stock)} unidades{' '}
                  {row.estado === 'INACTIVO' ? '(inactivo)' : ''}
                </li>
              ))}
            </ul>
          </details>
        )}
        <fieldset disabled={busy || loading || !!loadError}>
          <div className="table-scroll">
            <table className="purchase-variant-table">
              <thead>
                <tr>
                  <th>Talle nuevo</th>
                  <th>Color</th>
                  <th>Stock inicial</th>
                  <th>Código / barras opcional</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={row.key}>
                    {(['size', 'color', 'quantity', 'code'] as const).map((field) => (
                      <td key={field}>
                        <input
                          name={`variants[${index}].${field}`}
                          aria-label={`${{ size: 'Talle nuevo', color: 'Color nuevo', quantity: 'Stock nuevo', code: 'Código nuevo' }[field]} ${index + 1}`}
                          required={field !== 'code'}
                          type={field === 'quantity' ? 'number' : 'text'}
                          min={field === 'quantity' ? '0' : undefined}
                          step={field === 'quantity' ? '1' : undefined}
                          max={field === 'quantity' ? '1000000000' : undefined}
                          maxLength={field === 'color' ? 60 : 30}
                          value={row[field]}
                          placeholder={field === 'code' ? 'Automático' : ''}
                          onChange={(event) =>
                            setRows((current) =>
                              current.map((item) =>
                                item.key === row.key
                                  ? { ...item, [field]: event.target.value }
                                  : item,
                              ),
                            )
                          }
                        />
                      </td>
                    ))}
                    <td>
                      <button
                        type="button"
                        disabled={rows.length === 1}
                        aria-label={`Quitar talle nuevo ${index + 1}`}
                        onClick={() =>
                          setRows((current) => current.filter((item) => item.key !== row.key))
                        }
                      >
                        Quitar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button
            type="button"
            className="secondary-button"
            data-add-variant
            disabled={rows.length >= 100}
            onClick={() =>
              setRows((current) => [...current, newVariant(current.at(-1)?.color ?? '')])
            }
          >
            Agregar otro talle
          </button>
        </fieldset>
        {loadError && <p role="alert">{loadError}</p>}
        {error && <p role="alert">{error}</p>}
        <div className="entry-actions">
          <button type="button" className="secondary-button" disabled={busy} onClick={onClose}>
            Volver a modificar
          </button>
          <button
            type="submit"
            className="primary-button"
            disabled={busy || loading || !!loadError}
          >
            {busy ? 'Guardando…' : 'Guardar talles nuevos'}
          </button>
        </div>
      </form>
    </PurchaseDialog>
  )
}
