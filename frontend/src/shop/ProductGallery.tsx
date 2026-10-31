// Muestra las fotos del artículo y permite recorrer su galería.
import { useState } from 'react'

type Photo = { key: string; illustrative: boolean }
export function ProductGallery({
  name,
  photos = [],
  cover,
  illustrative = false,
}: {
  name: string
  photos?: Photo[]
  cover: string | null
  illustrative?: boolean
}) {
  const images = photos.length ? photos : cover ? [{ key: cover, illustrative }] : []
  const [activeKey, setActiveKey] = useState<string | null>(null)
  const active = Math.max(
    0,
    images.findIndex((p) => p.key === activeKey),
  )
  const photo = images[active]
  const move = (direction: number) =>
    setActiveKey(images[(active + direction + images.length) % images.length].key)
  return (
    <div className="shop-image" role="group" aria-label={'Fotos de ' + name}>
      {photo ? (
        <img
          src={'/api/v1/shop/images/' + photo.key}
          alt={name + (images.length > 1 ? ' · foto ' + (active + 1) + ' de ' + images.length : '')}
          loading="lazy"
        />
      ) : (
        <div className="shop-no-photo">
          <span>▧</span>Foto no disponible
        </div>
      )}
      {photo?.illustrative && <small>Imagen ilustrativa</small>}
      {images.length > 1 && (
        <>
          <button
            className="shop-photo-arrow shop-photo-previous"
            type="button"
            aria-label={'Foto anterior de ' + name}
            onClick={() => move(-1)}
          >
            ‹
          </button>
          <button
            className="shop-photo-arrow shop-photo-next"
            type="button"
            aria-label={'Foto siguiente de ' + name}
            onClick={() => move(1)}
          >
            ›
          </button>
          <span className="shop-photo-count" aria-live="polite">
            {active + 1}/{images.length}
          </span>
        </>
      )}
    </div>
  )
}
