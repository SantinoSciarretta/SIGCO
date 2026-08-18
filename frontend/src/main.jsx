import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import Router from './app/router';
import DemoProvider from './datos/DemoProvider';

// El orden importa: primero los tokens (las variables), después los estilos
// base que los usan.
import './styles/tokens.css';
import './styles/base.css';

/**
 * Punto de entrada de la aplicación.
 *
 * Monta React sobre el <div id="root"> de index.html. Todo lo que se ve en el
 * navegador cuelga de acá.
 *
 * BrowserRouter habilita la navegación interna: al pasar de una pantalla a
 * otra, la dirección del navegador cambia y el contenido se reemplaza, pero la
 * página no se recarga. Por eso el sistema es una SPA.
 *
 * DemoProvider sostiene los datos de muestra que hoy alimentan las pantallas.
 * Se retira cuando cada módulo pase a consumir el backend.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <DemoProvider>
      <BrowserRouter>
        <Router />
      </BrowserRouter>
    </DemoProvider>
  </StrictMode>,
);
