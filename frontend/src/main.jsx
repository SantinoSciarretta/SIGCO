import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import Router from './app/router';
import DemoProvider from './datos/DemoProvider';
import { ProveedorSesion } from './modules/sesion/ContextoSesion';

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
 * ProveedorSesion envuelve todo: quién entró y qué permisos tiene lo necesitan
 * el menú, el router y las pantallas, así que vive arriba de todo en lugar de
 * pasarse por props a través de media aplicación.
 *
 * Va POR FUERA del router porque el router ya depende de la sesión: es la
 * sesión la que decide si una ruta se puede abrir.
 *
 * DemoProvider sostiene los datos de muestra que todavía alimentan las
 * pantallas del capataz y la ficha de obra del diseño. Se retira cuando esas
 * pantallas pasen a consumir el backend.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ProveedorSesion>
      <DemoProvider>
        <BrowserRouter>
          <Router />
        </BrowserRouter>
      </DemoProvider>
    </ProveedorSesion>
  </StrictMode>,
);
