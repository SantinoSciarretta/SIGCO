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
  },
});
