import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiFetch } from '../lib/http'
import { RestoreBackupPanel } from './RestoreBackupPanel'
vi.mock('../lib/http', () => ({ apiFetch: vi.fn() }))
beforeEach(() => {
  vi.mocked(apiFetch).mockReset()
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    },
  })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    },
  })
})
async function chooseAndOpen() {
  const user = userEvent.setup()
  await user.upload(
    screen.getByLabelText(/Archivo de backup/),
    new File(['zip'], 'alon-backup.zip', { type: 'application/zip' }),
  )
  await user.click(screen.getByRole('button', { name: 'Restaurar backup…' }))
  return user
}
it('opens with Cancel focused and Enter never starts a restore', async () => {
  render(<RestoreBackupPanel />)
  const user = await chooseAndOpen()
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cancelar' }))
  await user.keyboard('{Enter}')
  expect(apiFetch).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog')).toBeNull()
})
it('requires the exact typed confirmation and uploads only once without retries', async () => {
  vi.mocked(apiFetch).mockResolvedValue(
    Response.json({ safetyBackup: 'safety.zip', message: 'Backup restaurado.' }),
  )
  const restored = vi.fn()
  render(<RestoreBackupPanel onRestored={restored} />)
  const user = await chooseAndOpen()
  expect(
    (screen.getByRole('button', { name: 'Confirmar restauración' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  await user.type(
    screen.getByRole('textbox', { name: 'Escribí RESTAURAR para confirmar' }),
    'RESTAURAR',
  )
  expect(apiFetch).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Confirmar restauración' }))
  expect(apiFetch).toHaveBeenCalledOnce()
  const [url, options] = vi.mocked(apiFetch).mock.calls[0]
  expect(url).toBe('/api/v1/backups/restore')
  expect(options?.method).toBe('POST')
  expect(options?.retry).toBe(false)
  const body = options?.body as FormData
  expect(body.get('confirmation')).toBe('RESTAURAR')
  expect((body.get('file') as File).name).toBe('alon-backup.zip')
  expect(restored).toHaveBeenCalledWith({
    safetyBackup: 'safety.zip',
    message: 'Backup restaurado.',
  })
})
it('keeps errors visible without reporting success', async () => {
  vi.mocked(apiFetch).mockResolvedValue(
    Response.json({ error: 'El backup está incompleto.' }, { status: 400 }),
  )
  const restored = vi.fn()
  render(<RestoreBackupPanel onRestored={restored} />)
  const user = await chooseAndOpen()
  await user.type(
    screen.getByRole('textbox', { name: 'Escribí RESTAURAR para confirmar' }),
    'RESTAURAR',
  )
  await user.click(screen.getByRole('button', { name: 'Confirmar restauración' }))
  expect((await screen.findByRole('alert')).textContent).toBe('El backup está incompleto.')
  expect(restored).not.toHaveBeenCalled()
  expect(screen.getByRole('dialog')).toBeTruthy()
})
