import { compactListText } from '../lib/format'

export function ListHeading({ label }: { label: string }) {
  const text = compactListText(label)
  return /^\S{8,}$/.test(text) ? <span className="compact-heading">{text}</span> : text
}
