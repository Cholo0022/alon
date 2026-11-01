import { expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiFetch } from '../lib/http'
import { BackupPage } from './BackupPage'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
it('creates a backup and offers a download only after success', async () => {
  vi.mocked(apiFetch)
    .mockResolvedValueOnce(Response.json([]))
    .mockResolvedValueOnce(
      Response.json({
        name: 'alon-backup-example.zip',
        size: 1048576,
        created: '2026-11-01T12:00:00Z',
      }),
    )
  render(<BackupPage />)
  await screen.findByText('Todavía no hay copias de seguridad guardadas.')
  await userEvent.click(screen.getByRole('button', { name: 'Crear backup completo' }))
  expect((await screen.findByRole('link', { name: 'Descargar ZIP' })).getAttribute('href')).toBe(
    '/api/v1/backups/alon-backup-example.zip',
  )
  expect(apiFetch).toHaveBeenCalledWith('/api/v1/backups', { method: 'POST' })
})
it('shows creation failures without offering a download', async () => {
  vi.mocked(apiFetch)
    .mockResolvedValueOnce(Response.json([]))
    .mockResolvedValueOnce(Response.json({ error: 'No hay espacio disponible.' }, { status: 503 }))
  render(<BackupPage />)
  await screen.findByText('Todavía no hay copias de seguridad guardadas.')
  await userEvent.click(screen.getByRole('button', { name: 'Crear backup completo' }))
  expect((await screen.findByRole('alert')).textContent).toBe('No hay espacio disponible.')
  expect(screen.queryByRole('link', { name: 'Descargar ZIP' })).toBeNull()
})
