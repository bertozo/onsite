import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { log } from './lib/log'

// The backend URL is baked in at build time (see web/Dockerfile), so "which backend is
// this tab actually talking to" is otherwise unanswerable from a user's browser.
log.info(`web starting backend=${import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080'} dev=${import.meta.env.DEV}`)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
