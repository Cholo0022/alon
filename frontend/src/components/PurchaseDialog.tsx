import { useEffect, useId, useRef, type ReactNode } from 'react'
import './article-picker.css'

/** Mantiene el foco dentro de las ventanas de carga y lo devuelve al control de origen. */
export function PurchaseDialog({
  title,
  busy = false,
  onClose,
  children,
  wide = false,
}: {
  title: string
  busy?: boolean
  onClose: () => void
  children: ReactNode
  wide?: boolean
}) {
  const id = useId()
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.querySelector<HTMLElement>('input,select,button')?.focus()
    return () => {
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  return (
    <div className="article-picker-backdrop">
      <section
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        className={'article-picker purchase-dialog ' + (wide ? 'purchase-dialog-wide' : '')}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            if (!busy) onClose()
          }
          if (event.key === 'Tab') {
            const controls = panel.current?.querySelectorAll<HTMLElement>(
              'input:not(:disabled),select:not(:disabled),button:not(:disabled),textarea:not(:disabled)',
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
          <h2 id={id}>{title}</h2>
        </header>
        {children}
      </section>
    </div>
  )
}
