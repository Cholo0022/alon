export type ArticlePhoto = { key?: string; data?: string; illustrative: boolean; preview: string }
export type SavedArticlePhoto = { key: string; illustrative: boolean }
export const savedPhotos = (photos: SavedArticlePhoto[] = []): ArticlePhoto[] =>
  photos.map((p) => ({ ...p, preview: '/api/v1/shop/images/' + p.key }))
export const photoPayload = (photos: ArticlePhoto[]) =>
  photos.map(({ key, data, illustrative }) => ({ key, data, illustrative }))
