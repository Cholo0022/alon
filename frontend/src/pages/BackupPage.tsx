import { RestoreBackupPanel } from '../components/RestoreBackupPanel'
import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/http'

type Backup = { name: string; size: number; created: string }
export function BackupPage() {
  const [backups, setBackups] = useState<Backup[]>([])
  const [busy, setBusy] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  async function load() {
    try {
      const response = await apiFetch('/api/v1/backups')
      if (!response.ok) throw new Error('No se pudo consultar el historial de backups.')
      setBackups((await response.json()) as Backup[])
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo conectar.')
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    let active = true
    void apiFetch('/api/v1/backups')
      .then(async (response) => {
        if (!response.ok) throw new Error('No se pudo consultar el historial de backups.')
        const result = (await response.json()) as Backup[]
        if (active) setBackups(result)
      })
      .catch(() => {
        if (active) setError('No se pudo consultar el historial de backups.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])
  async function create() {
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch('/api/v1/backups', { method: 'POST' })
      if (!response.ok) {
        const body = (await response.json()) as { error?: string }
        throw new Error(body.error ?? 'No se pudo crear el backup.')
      }
      const backup = (await response.json()) as Backup
      setBackups((current) => [backup, ...current.filter((item) => item.name !== backup.name)])
      setMessage(
        'Backup creado y guardado en esta PC. Descargalo para conservar otra copia en un disco externo.',
      )
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : 'No se pudo conectar. Consultá el historial antes de reintentar.',
      )
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="panel" style={{ padding: 24 }}>
      <h2>Copia de seguridad del sistema</h2>
      <p>
        Incluye toda la información de gestión: ventas, compras, caja, clientes, proveedores,
        artículos, stock, usuarios, configuración, pedidos e imágenes.
      </p>
      <p>
        También incluye la clave del autenticador. El archivo contiene información privada: guardalo
        en un lugar protegido. Los backups quedan guardados en la PC del servidor y podés
        descargarlos.
      </p>
      <p>
        Creá la copia cuando no se estén cargando ni cambiando imágenes. La configuración de
        conexión y el programa se conservan por separado.
      </p>
      <button
        className="primary-button"
        type="button"
        disabled={busy || restoring}
        onClick={() => void create()}
      >
        {busy ? 'Preparando backup…' : 'Crear backup completo'}
      </button>
      {busy && <p role="status">Puede demorar varios minutos. Esperá hasta que termine.</p>}
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      <h3 style={{ marginTop: 28 }}>Backups disponibles</h3>
      <button
        className="select-button"
        type="button"
        disabled={busy || restoring}
        onClick={() => void load()}
      >
        Actualizar historial
      </button>
      {loading ? (
        <p>Cargando backups…</p>
      ) : backups.length === 0 ? (
        <p>Todavía no hay copias de seguridad guardadas.</p>
      ) : (
        <ul>
          {backups.map((backup) => (
            <li key={backup.name} style={{ marginTop: 16 }}>
              <strong>{new Date(backup.created).toLocaleString('es-AR')}</strong> ·{' '}
              {(backup.size / 1024 / 1024).toFixed(2)} MB{' '}
              <a
                className="select-button"
                href={`/api/v1/backups/${encodeURIComponent(backup.name)}`}
                download
              >
                Descargar ZIP
              </a>
            </li>
          ))}
        </ul>
      )}
      <RestoreBackupPanel disabled={busy} onBusyChange={setRestoring} />
    </section>
  )
}
