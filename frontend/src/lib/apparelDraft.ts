import type { ArticlePhoto } from './articlePhotos'
export type ApparelCommon = { name: string; cost: string; price: string; model: string; brand: string; sizeSystem: string; material: string; audience: string; season: string; description: string; minimumStock: string; freightPercent: string; marginPercent: string; roundPrice: boolean; publishOnWeb: boolean; singleColor: string }
export type ApparelDraftRow = { key: string; code: string; size: string; color: string; quantity: string }
export type ApparelDraft = { version: 1; kind: string; stockMode: boolean; common: ApparelCommon; rows: ApparelDraftRow[]; photos: ArticlePhoto[]; multipleColors: boolean; priceEdited: boolean; savedAt: number; photosOmitted?: boolean }
export const apparelDraftKey = (scope: string, context: string) => `alon:apparel-draft:v1:${scope}:${context}`
export function readApparelDraft(key?: string): ApparelDraft | null {
  if (!key) return null
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? 'null') as ApparelDraft | null
    if (!value || value.version !== 1 || !['INDUMENTARIA','CALZADO'].includes(value.kind) || !value.common || typeof value.common.name !== 'string' || typeof value.common.cost !== 'string' || typeof value.common.price !== 'string' || !Array.isArray(value.rows) || value.rows.length > 100 || value.rows.some((row) => !row || ['key','code','size','color','quantity'].some((key) => typeof row[key as keyof ApparelDraftRow] !== 'string')) || !Array.isArray(value.photos) || value.photos.length > 4) return null
    return value
  } catch { return null }
}
export function saveApparelDraft(key: string, draft: ApparelDraft): string {
  try { localStorage.setItem(key, JSON.stringify(draft)); return '' } catch {
    try { localStorage.setItem(key, JSON.stringify({ ...draft, photos: [], photosOmitted: draft.photos.length > 0 })); return 'El borrador conserva los datos, pero las fotos deben seleccionarse nuevamente al recuperarlo.' } catch { return 'No se pudo guardar el borrador en este navegador. Mantené esta pantalla abierta hasta guardar la prenda.' }
  }
}
