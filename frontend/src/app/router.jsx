import { Route, Routes } from 'react-router-dom';

import Layout from './Layout';
import Login from './paginas/Login';
import ModuloPendiente from './paginas/ModuloPendiente';
import NoEncontrado from './paginas/NoEncontrado';

import Tablero from './paginas/dueno/Tablero';
import DetalleObra from './paginas/dueno/DetalleObra';
import ClientesPage from '../modules/clientes/ClientesPage';
import ObrasPage from '../modules/obras/ObrasPage';
import CatalogoPage from '../modules/presupuestacion/CatalogoPage';
import PresupuestosPage from '../modules/presupuestacion/PresupuestosPage';
import PresupuestoDetalle from '../modules/presupuestacion/PresupuestoDetalle';
import PedidosPage from '../modules/compras/PedidosPage';
import GastosPage from '../modules/gastos/GastosPage';
import MaterialesPage from '../modules/materiales/MaterialesPage';
import ProveedoresPage from '../modules/proveedores/ProveedoresPage';

import LayoutCapataz from './paginas/capataz/LayoutCapataz';
import Home from './paginas/capataz/Home';
import Hitos from './paginas/capataz/Hitos';
import Materiales from './paginas/capataz/Materiales';

/**
 * Mapa de rutas del sistema.
 *
 * Hay tres zonas, y cada una tiene su propio marco:
 *
 *   /            ingreso, sin marco (pantalla de fondo oscuro)
 *   /tablero…    rol Dueño, con la barra de navegación de escritorio
 *   /obra…       rol Capataz, marco angosto pensado para el celular
 *
 * Las rutas de adentro se dibujan en el <Outlet /> de su marco, así la
 * navegación se arma una sola vez y al moverse solo cambia el contenido.
 *
 * A medida que cada módulo se desarrolla, su línea deja de apuntar a
 * ModuloPendiente y pasa a apuntar a su pantalla real.
 */
export default function Router() {
  return (
    <Routes>

      <Route path="/" element={<Login />} />

      {/* ---------- Rol Dueño ---------- */}
      <Route element={<Layout />}>
        <Route path="/tablero" element={<Tablero />} />
        <Route path="/obras" element={<ObrasPage />} />
        <Route path="/clientes" element={<ClientesPage />} />

        {/* Pantalla del diseño original, con datos de muestra. Se conserva
            como referencia de cómo va a quedar la ficha de obra cuando existan
            Gastos y Seguimiento, que son los módulos que producen esa
            información. No forma parte de la navegación. */}
        <Route path="/vista-diseno/obra" element={<DetalleObra />} />
        <Route path="/pedidos" element={<PedidosPage />} />
        <Route path="/gastos" element={<GastosPage />} />
        <Route path="/presupuestos" element={<PresupuestosPage />} />
        <Route path="/presupuestos/catalogo" element={<CatalogoPage />} />
        <Route path="/presupuestos/:id" element={<PresupuestoDetalle />} />
        <Route path="/materiales" element={<MaterialesPage />} />
        <Route path="/proveedores" element={<ProveedoresPage />} />
        <Route path="/cobranzas" element={<ModuloPendiente nombre="Cobranzas" />} />
      </Route>

      {/* ---------- Rol Capataz ---------- */}
      <Route element={<LayoutCapataz />}>
        <Route path="/obra" element={<Home />} />
        <Route path="/obra/hitos" element={<Hitos />} />
        <Route path="/obra/materiales" element={<Materiales />} />
      </Route>

      {/* Cualquier dirección que no coincida con las anteriores. */}
      <Route path="*" element={<NoEncontrado />} />

    </Routes>
  );
}
