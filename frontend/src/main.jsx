import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import Router from './app/router';
import './index.css';

/**
 * Punto de entrada de la aplicacion.
 *
 * Monta React sobre el <div id="root"> de index.html. Todo lo que se ve en el
 * navegador cuelga de aca.
 *
 * BrowserRouter habilita la navegacion interna: al pasar de un modulo a otro,
 * la direccion del navegador cambia y la pantalla se reemplaza, pero la pagina
 * no se recarga. Por eso el sistema es una SPA (aplicacion de una sola pagina).
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <Router />
    </BrowserRouter>
  </StrictMode>,
);
