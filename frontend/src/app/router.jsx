import { Route, Routes } from 'react-router-dom';
import Layout from './Layout';
import Inicio from './paginas/Inicio';
import ModuloPendiente from './paginas/ModuloPendiente';
import NoEncontrado from './paginas/NoEncontrado';
import { todosLosModulos } from './modulos';

/**
 * Mapa de rutas del sistema.
 *
 * La ruta exterior no tiene direccion propia: solo dibuja el Layout (barra
 * lateral y encabezado). Las rutas de adentro se dibujan en el <Outlet /> de
 * ese Layout, de modo que el marco de la aplicacion se arma una sola vez y al
 * navegar cambia unicamente el contenido central.
 *
 * A medida que cada modulo se desarrolla, su linea deja de apuntar a
 * ModuloPendiente y pasa a apuntar a su pantalla real.
 */
export default function Router() {
  // Modulos aun no desarrollados: se generan sus rutas automaticamente a partir
  // de la lista de modulos, para no repetir una linea igual por cada uno.
  const modulosPendientes = todosLosModulos.filter((modulo) => !modulo.listo);

  return (
    <Routes>
      <Route element={<Layout />}>

        <Route index element={<Inicio />} />

        {modulosPendientes.map((modulo) => (
          <Route
            key={modulo.ruta}
            path={modulo.ruta}
            element={<ModuloPendiente nombre={modulo.nombre} />}
          />
        ))}

        {/* Cualquier direccion que no coincida con las anteriores. */}
        <Route path="*" element={<NoEncontrado />} />

      </Route>
    </Routes>
  );
}
