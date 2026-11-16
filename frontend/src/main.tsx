import { installSentenceCase } from './lib/sentenceCase'
import { installFormValidation } from './lib/formValidation'
// Inicia la interfaz y elige entre el sistema de gestión y la tienda web según la dirección.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import AppView from './views/AppView.tsx'
import ShopApp from './shop/ShopApp.tsx'
import { InstanceAvailability } from './components/InstanceAvailability'

installFormValidation()
installSentenceCase()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <InstanceAvailability>
      {window.location.pathname === '/tienda' || window.location.pathname.startsWith('/tienda/') ? (
        <ShopApp />
      ) : (
        <AppView />
      )}
    </InstanceAvailability>
  </StrictMode>,
)
