import { useEffect, useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'
import { ArticlePhotosField } from './ArticlePhotosField'
import {
  savedPhotos,
  photoPayload,
  type ArticlePhoto,
  type SavedArticlePhoto,
} from '../lib/articlePhotos'

export function ArticleImageEditor({
  id,
  name,
  onClose,
}: {
  id: number
  name: string
  onClose: () => void
}) {
  const [photos, setPhotos] = useState<ArticlePhoto[]>([])
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [reload, setReload] = useState(0)
  const [reading, setReading] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void apiFetch('/api/v1/operations/articles/' + id + '/images', { signal: controller.signal })
      .then(async (response) => {
        const body = await response.json()
        if (!response.ok) throw new Error(body.error ?? 'No se pudieron cargar las fotos.')
        setPhotos(savedPhotos(body as SavedArticlePhoto[]))
        setLoaded(true)
        setLoading(false)
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(e.message)
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [id, reload])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (loading || !loaded || reading || busy) return
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch('/api/v1/operations/articles/' + id + '/images', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ photos: photoPayload(photos) }),
      })
      if (!response.ok) {
        const body = await response.json()
        throw new Error(body.error ?? 'No se pudieron guardar las fotos.')
      }
      window.dispatchEvent(new Event('alon:refresh'))
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron guardar las fotos.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="panel article-image-editor">
      <h2>Fotos · {name}</h2>
      {loading ? (
        <p>Cargando fotos…</p>
      ) : (
        <form className="entry-form" onSubmit={(e) => void submit(e)}>
          <ArticlePhotosField
            value={photos}
            onChange={setPhotos}
            disabled={busy}
            onReading={setReading}
          />
          <div className="row-actions">
            <button className="primary-button" disabled={busy || reading || !loaded}>
              {busy ? 'Guardando…' : 'Guardar fotos'}
            </button>
            <button className="secondary-button" type="button" onClick={onClose} disabled={busy}>
              Cancelar
            </button>
          </div>
        </form>
      )}
      {error && (
        <p role="alert" className="entry-message">
          {error}
        </p>
      )}
      {!loading && !loaded && (
        <button
          type="button"
          className="secondary-button"
          onClick={() => {
            setError('')
            setLoading(true)
            setReload((n) => n + 1)
          }}
        >
          Reintentar carga de fotos
        </button>
      )}
    </section>
  )
}
