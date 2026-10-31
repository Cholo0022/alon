// Permite elegir y organizar las fotos que se guardarán con el artículo.
import { useRef, useState } from 'react'

import type { ArticlePhoto } from '../lib/articlePhotos'
export type { ArticlePhoto, SavedArticlePhoto } from '../lib/articlePhotos'

export function ArticlePhotosField({
  value,
  onChange,
  disabled = false,
  onReading,
}: {
  value: ArticlePhoto[]
  onChange: (photos: ArticlePhoto[]) => void
  disabled?: boolean
  onReading?: (reading: boolean) => void
}) {
  const [error, setError] = useState('')
  const [reading, setReading] = useState(false)
  const chooser = useRef<HTMLInputElement>(null)
  const add = async (files: File[]) => {
    setError('')
    if (files.length + value.length > 4) {
      setError('Podés agregar hasta cuatro fotos por artículo.')
      return
    }
    if (
      files.some(
        (file) =>
          file.size > 5 * 1024 * 1024 ||
          !file.size ||
          !['image/jpeg', 'image/png'].includes(file.type),
      )
    ) {
      setError('Usá fotos JPG o PNG de hasta 5 MB cada una.')
      return
    }
    setReading(true)
    onReading?.(true)
    try {
      const next: ArticlePhoto[] = []
      for (const file of files) {
        const preview = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result))
          reader.onerror = () => reject(new Error('No se pudo leer una foto.'))
          reader.readAsDataURL(file)
        })
        next.push({ data: preview.split(',')[1], illustrative: false, preview })
      }
      onChange([...value, ...next])
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron leer las fotos.')
    } finally {
      setReading(false)
      onReading?.(false)
      if (chooser.current) chooser.current.value = ''
    }
  }
  return (
    <fieldset className="article-photos-field" disabled={disabled || reading}>
      <legend>Fotos del artículo · {value.length}/4</legend>
      <p>La primera será la portada de la tienda. Podés agregar hasta cuatro fotos JPG o PNG.</p>
      <div className="article-photos-grid">
        {value.map((p, i) => (
          <div className="article-photo-tile" key={p.key ?? p.preview}>
            <img src={p.preview} alt={'Foto ' + (i + 1) + ' del artículo'} />
            <strong>{i === 0 ? 'Principal' : 'Foto ' + (i + 1)}</strong>
            <label>
              <input
                type="checkbox"
                checked={p.illustrative}
                onChange={(e) =>
                  onChange(
                    value.map((photo, index) =>
                      index === i ? { ...photo, illustrative: e.target.checked } : photo,
                    ),
                  )
                }
              />{' '}
              Ilustrativa
            </label>
            <div className="row-actions">
              {i > 0 && (
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => onChange([p, ...value.filter((_, index) => index !== i)])}
                >
                  Hacer principal
                </button>
              )}
              <button
                className="secondary-button"
                type="button"
                aria-label={'Quitar foto ' + (i + 1)}
                onClick={() => onChange(value.filter((_, index) => index !== i))}
              >
                Quitar
              </button>
            </div>
          </div>
        ))}
      </div>
      <label className="article-photos-chooser">
        Agregar fotos
        <input
          ref={chooser}
          type="file"
          multiple
          accept="image/jpeg,image/png"
          disabled={disabled || reading || value.length >= 4}
          onChange={(e) => {
            if (e.target.files) void add(Array.from(e.target.files))
          }}
        />
      </label>
      {reading && <p role="status">Preparando fotos…</p>}
      {error && (
        <p className="entry-message" role="alert">
          {error}
        </p>
      )}
    </fieldset>
  )
}
