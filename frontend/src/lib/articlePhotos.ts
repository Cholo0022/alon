// Prepara las fotos existentes y nuevas para enviarlas al servidor.
export type ArticlePhoto = { key?: string; data?: string; illustrative: boolean; preview: string }
export type SavedArticlePhoto = { key: string; illustrative: boolean }
/** Convierte las fotos guardadas por la API en entradas del editor con sus direcciones de vista previa. */
export const savedPhotos = (photos: SavedArticlePhoto[] = []): ArticlePhoto[] =>
  photos.map((p) => ({ ...p, preview: '/api/v1/shop/images/' + p.key }))
/** Prepara únicamente los datos de fotos que acepta el servidor, excluyendo las vistas previas locales. */
export const photoPayload = (photos: ArticlePhoto[]) =>
  photos.map(({ key, data, illustrative }) => ({ key, data, illustrative }))
