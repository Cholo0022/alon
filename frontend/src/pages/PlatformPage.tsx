import { PlatformBusinesses } from './PlatformBusinesses'

/** La administración general muestra clientes, informes y la ficha de cada negocio. */
export function PlatformPage() {
  return <PlatformBusinesses />
}

/** Mensaje de compatibilidad para una instalación anterior pendiente de actualización. */
export function LicenseActivation() {
  return (
    <section className="entry-panel platform-panel">
      <h2>Estado del servicio</h2>
      <p>Contactá al administrador de Alon para consultar la habilitación del negocio.</p>
    </section>
  )
}
