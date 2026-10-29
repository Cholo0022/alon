import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import AppView from './views/AppView.tsx'
import ShopApp from './shop/ShopApp.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {window.location.pathname === '/tienda' || window.location.pathname.startsWith('/tienda/') ? (
      <ShopApp />
    ) : (
      <AppView />
    )}
  </StrictMode>,
)
