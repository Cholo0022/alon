import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { loadSaleReceipt } from '../lib/salesReceipt'
import './sales-receipt-preview.css'

export function SalesReceiptPreview({
  saleId,
  number,
  onClose,
}: {
  saleId: number
  number: string
  onClose: () => void
}) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [ready, setReady] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const frame = useRef<HTMLIFrameElement>(null)
  const panel = useRef<HTMLElement>(null)
  const close = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const origin = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    close.current?.focus()
    return () => {
      document.body.style.overflow = overflow
      if (origin?.isConnected) origin.focus()
    }
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl = ''
    void loadSaleReceipt(saleId, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setError(error instanceof Error ? error.message : 'No se pudo abrir el comprobante.')
      })
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [saleId, attempt])
  return createPortal(
    <div className="receipt-preview-backdrop">
      <section
        ref={panel}
        className="receipt-preview"
        role="dialog"
        aria-modal="true"
        aria-label={'Comprobante de venta ' + number}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            onClose()
          }
          if (event.key === 'Tab') {
            const controls = panel.current?.querySelectorAll<HTMLElement>(
              'button:not(:disabled),a,iframe',
            )
            if (!controls?.length) return
            const first = controls[0],
              last = controls[controls.length - 1]
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault()
              last.focus()
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault()
              first.focus()
            }
          }
        }}
      >
        <header>
          <div>
            <h2>Comprobante de venta {number}</h2>
            <p>Revisá el comprobante y elegí Imprimir si querés una copia en papel.</p>
          </div>
          <div className="receipt-preview-actions">
            <button
              type="button"
              className="primary-button"
              disabled={!ready}
              onClick={() => {
                try {
                  frame.current?.contentWindow?.focus()
                  frame.current?.contentWindow?.print()
                } catch {
                  setError(
                    'Usá el botón de impresión del visor o abrí el comprobante en otra pestaña.',
                  )
                }
              }}
            >
              Imprimir
            </button>
            <button ref={close} type="button" className="secondary-button" onClick={onClose}>
              Cerrar comprobante
            </button>
          </div>
        </header>
        {error && (
          <div role="alert" className="receipt-preview-error">
            {error}{' '}
            {!url && (
              <button
                type="button"
                onClick={() => {
                  setUrl('')
                  setError('')
                  setReady(false)
                  setAttempt((value) => value + 1)
                }}
              >
                Reintentar
              </button>
            )}
          </div>
        )}
        {!url && !error && <p role="status">Preparando comprobante…</p>}
        {url && (
          <>
            <iframe
              ref={frame}
              title={'Vista del comprobante de venta ' + number}
              src={url}
              onLoad={() => setReady(true)}
            />
            <a href={url} target="_blank" rel="noopener noreferrer">
              Abrir comprobante en otra pestaña
            </a>
          </>
        )}
      </section>
    </div>,
    document.body,
  )
}
