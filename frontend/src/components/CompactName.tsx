import { textValue } from '../lib/format'

export function CompactName({ value }: { value: unknown }) {
  const name = textValue(value)
  const characters = Array.from(name)
  if (characters.length <= 10) return name || '—'

  return (
    <span className="compact-name" title={name} tabIndex={0}>
      {characters.slice(0, 10).join('')}
    </span>
  )
}
