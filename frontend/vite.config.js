import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Se fija el puerto de forma explicita porque el backend autoriza este
    // origen concreto en su configuracion de CORS
    // (application-dev.properties -> sigco.cors.origenes-permitidos).
    // strictPort evita que Vite cambie a otro puerto si el 5173 esta ocupado,
    // lo que dejaria de coincidir con lo autorizado y las llamadas fallarian.
    port: 5173,
    strictPort: true,

    // El navegador pide /api al MISMO origen que sirve la página y Vite lo
    // reenvía al backend. Resuelve dos problemas de una:
    //
    //   1. Desde el celular u otra computadora ya no hay que poner la IP de
    //      esta máquina en ningún lado. La IP la asigna el router por DHCP y
    //      cambia de una red a otra; con el proxy eso deja de importar.
    //   2. No hay CORS en desarrollo: al ser el mismo origen, el navegador ni
    //      lo evalúa. La configuración de CORS del backend sigue existiendo
    //      para producción, donde el frontend está en otro dominio.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
