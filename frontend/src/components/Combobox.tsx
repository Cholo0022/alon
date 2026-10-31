// Muestra un buscador con sugerencias que también se pueden elegir con el teclado.
import { useEffect, useId, useRef, useState } from 'react'

export type ComboOption = { value: string; label: string }

export function Combobox({
  label,
  value,
  options,
  onChange,
  onChoose,
  required = false,
  autoFocus = false,
  placeholder = 'Escribí nombre o código…',
  valid = true,
  name,
  selectOnFocus = false,
  onEmptyEnter,
}: {
  label: string
  value: string
  options: ComboOption[]
  onChange: (value: string) => void
  onChoose: (option: ComboOption) => void
  required?: boolean
  autoFocus?: boolean
  placeholder?: string
  valid?: boolean
  onEmptyEnter?: () => void
  selectOnFocus?: boolean
  name?: string
}) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [activeKey, setActiveKey] = useState('')
  useEffect(() => {
    input.current?.setCustomValidity(
      required && value && !valid ? 'Elegí un registro de las sugerencias.' : '',
    )
  }, [required, value, valid])
  const active = options.findIndex((option) => option.value === activeKey)
  const choose = (option: ComboOption) => {
    onChoose(option)
    setOpen(false)
    setActiveKey('')
    input.current?.setCustomValidity('')
  }
  return (
    <div className="searchable-field">
      <input
        ref={input}
        name={name}
        type="text"
        role="combobox"
        aria-label={label}
        aria-autocomplete="list"
        aria-expanded={open && options.length > 0}
        aria-controls={id}
        aria-activedescendant={open && active >= 0 ? `${id}-${active}` : undefined}
        autoComplete="off"
        value={value}
        placeholder={placeholder}
        required={required}
        autoFocus={autoFocus}
        onFocus={(event) => {
          setOpen(true)
          if (selectOnFocus) event.currentTarget.select()
        }}
        onClick={() => setOpen(true)}
        onBlur={() => {
          setOpen(false)
          input.current?.setCustomValidity(
            required && !valid ? 'Elegí un registro de las sugerencias.' : '',
          )
        }}
        onChange={(event) => {
          onChange(event.target.value)
          setActiveKey('')
          setOpen(true)
          event.target.setCustomValidity('')
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            setOpen(false)
            return
          }
          if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && options.length) {
            event.preventDefault()
            setOpen(true)
            const next =
              event.key === 'ArrowDown'
                ? (active + 1) % options.length
                : active < 0
                  ? options.length - 1
                  : (active - 1 + options.length) % options.length
            setActiveKey(options[next].value)
            document.getElementById(`${id}-${next}`)?.scrollIntoView?.({ block: 'nearest' })
          } else if (event.key === 'Enter' && open && active >= 0) {
            event.preventDefault()
            choose(options[active])
          } else if (event.key === 'Enter' && onEmptyEnter) {
            event.preventDefault()
            if (!value.trim()) onEmptyEnter()
          }
        }}
      />
      {open && options.length > 0 && (
        <ul id={id} className="combo-options" role="listbox" aria-label={`Sugerencias de ${label}`}>
          {options.map((option, index) => (
            <li
              id={`${id}-${index}`}
              key={option.value}
              role="option"
              aria-selected={active === index}
              onMouseEnter={() => setActiveKey(option.value)}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(option)}
            >
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
