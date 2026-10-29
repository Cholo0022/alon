import { useState, type FormEvent } from 'react'
import { apiFetch } from '../lib/http'

export function ArticleImageEditor({
  id,
  name,
  onClose,
}: {
  id: number
  name: string
  onClose: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const file = form.get('photo')
    if (!(file instanceof File) || !file.size) {
      setError('Seleccioná una imagen.')
      return
    }
    if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Usá un archivo JPG o PNG de hasta 5 MB.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result).split(',')[1])
        reader.onerror = () => reject(new Error('No se pudo leer la imagen.'))
        reader.readAsDataURL(file)
      })
      const r = await apiFetch('/api/v1/operations/articles/' + id + '/image', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ data, illustrative: form.get('illustrative') === 'on' }),
      })
      if (!r.ok) {
        const b = await r.json()
        throw new Error(b.error ?? 'No se pudo guardar la foto.')
      }
      window.dispatchEvent(new Event('alon:refresh'))
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo guardar la imagen.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="panel article-image-editor">
      <h2>Foto · {name}</h2>
      <form className="entry-form" onSubmit={(e) => void submit(e)}>
        <label>
          Imagen del artículo
          <input type="file" name="photo" accept="image/jpeg,image/png" required />
        </label>
        <label className="article-illustrative">
          <input type="checkbox" name="illustrative" /> Es una imagen ilustrativa
        </label>
        <div className="row-actions">
          <button className="primary-button" disabled={busy}>
            {busy ? 'Guardando…' : 'Guardar imagen'}
          </button>
          <button className="secondary-button" type="button" onClick={onClose} disabled={busy}>
            Cancelar
          </button>
        </div>
      </form>
      <p>
        JPG o PNG, hasta 5 MB y 16 megapíxeles. Usá una foto real del producto o marcala como
        ilustrativa.
      </p>
      {error && (
        <p role="alert" className="entry-message">
          {error}
        </p>
      )}
    </section>
  )
}
