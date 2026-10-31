// Permite guardar el nombre, el slogan y los colores que usan Alon y la tienda web.
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { announceBrand, brandStyle, normalizeBrand, type BusinessBrand } from '../lib/businessBrand'

/** Permite al dueño editar nombre, slogan y colores del negocio con vista previa y sincronización al guardar. */
export function BusinessPage({ brand }: { brand: BusinessBrand }) {
  const [draft, setDraft] = useState(brand)
  const dirty = useRef(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (!dirty.current) setDraft(brand)
  }, [brand])
  /** Actualiza un campo del borrador y marca la edición pendiente para no sobrescribirla con datos externos. */
  const change = (key: keyof BusinessBrand, value: string) => {
    dirty.current = true
    setDraft((current) => ({ ...current, [key]: value }))
    setMessage('')
  }
  /** Guarda la identidad del negocio y, si la API confirma, publica el resultado a las demás pantallas y pestañas. */
  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/operations/business', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: draft.nombre,
          slogan: draft.slogan,
          primaryColor: draft.color_principal,
          secondaryColor: draft.color_secundario,
          backgroundColor: draft.color_fondo,
        }),
      })
      const result = await response.json()
      if (!response.ok) throw new Error(result.error ?? 'No se pudo guardar el negocio.')
      dirty.current = false
      const saved = normalizeBrand(result)
      setDraft(saved)
      announceBrand(saved)
      setMessage(
        'Negocio actualizado. Los cambios ya están disponibles en Alon y en la tienda web.',
      )
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo guardar el negocio.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="entry-panel business-settings">
      <div className="entry-heading">
        <strong>Identidad de tu comercio</strong>
      </div>
      <form className="entry-form" onSubmit={(event) => void save(event)}>
        <label>
          Nombre del comercio
          <input
            value={draft.nombre}
            onChange={(e) => change('nombre', e.target.value)}
            maxLength={150}
            required
            disabled={busy}
          />
        </label>
        <label>
          Slogan
          <input
            value={draft.slogan}
            onChange={(e) => change('slogan', e.target.value)}
            maxLength={180}
            placeholder="Una frase que identifique tu negocio"
            disabled={busy}
          />
        </label>
        {(
          [
            { key: 'color_principal', label: 'Color principal' },
            { key: 'color_secundario', label: 'Color secundario' },
            { key: 'color_fondo', label: 'Color de fondo' },
          ] as const
        ).map(({ key, label }) => (
          <label key={key}>
            {label}
            <div className="business-color">
              <input
                type="color"
                aria-label={label}
                value={draft[key]}
                onChange={(e) => change(key, e.target.value)}
                disabled={busy}
              />
              <span>{draft[key].toUpperCase()}</span>
            </div>
          </label>
        ))}
        <div
          className="business-preview"
          style={brandStyle(draft)}
          aria-label="Vista previa del negocio"
        >
          <div className="business-preview-header">
            <strong>{draft.nombre || 'Tu comercio'}</strong>
            <span>{draft.slogan}</span>
          </div>
          <div className="business-preview-content">
            <p>Así se verá la identidad de tu negocio.</p>
            <span className="business-preview-button">Botón de ejemplo</span>
          </div>
        </div>
        <div className="entry-actions">
          <span>Los colores se comparten entre Alon y tu tienda online.</span>
          <button className="primary-button" disabled={busy}>
            {busy ? 'Guardando…' : 'Guardar negocio'}
          </button>
        </div>
      </form>
      {message && (
        <p className="entry-message" role="status">
          {message}
        </p>
      )}
    </section>
  )
}
