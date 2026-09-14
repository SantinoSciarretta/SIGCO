import { Route, Routes } from 'react-router-dom';

import Layout from './Layout';
import RutaProtegida from './RutaProtegida';
import Login from './paginas/Login';
import NoEncontrado from './paginas/NoEncontrado';

import Tablero from './paginas/dueno/Tablero';
import ClientesPage from '../modules/clientes/ClientesPage';
import ObrasPage from '../modules/obras/ObrasPage';
import DetalleObraPage from '../modules/obras/DetalleObraPage';
import CatalogoPage from '../modules/presupuestacion/CatalogoPage';
import PresupuestosPage from '../modules/presupuestacion/PresupuestosPage';
import PresupuestoDetalle from '../modules/presupuestacion/PresupuestoDetalle';
import PedidosPage from '../modules/compras/PedidosPage';
import CobrosPage from '../modules/cobros/CobrosPage';
import PersonalPage from '../modules/personal/PersonalPage';
import PortfolioPage from '../modules/portfolio/PortfolioPage';
import SeguimientoPage from '../modules/seguimiento/SeguimientoPage';
import GastosPage from '../modules/gastos/GastosPage';
import MaterialesPage from '../modules/materiales/MaterialesPage';
import ProveedoresPage from '../modules/proveedores/ProveedoresPage';
import UsuariosPage from '../modules/usuarios/UsuariosPage';
import AccesosPage from '../modules/usuarios/AccesosPage';
import MiCuentaPage from '../modules/usuarios/MiCuentaPage';
import VidrieraPage from '../modules/portfolio/VidrieraPage';

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
 *   /tablero…    escritorio, con la barra de navegación
 *   /obra…       capataz, marco angosto pensado para el celular
 *
 * Las rutas de adentro se dibujan en el <Outlet /> de su marco, así la
 * navegación se arma una sola vez y al moverse solo cambia el contenido.
 *
 * ------------------------------------------------------------------
 *  Protección de rutas (módulo 14)
 * ------------------------------------------------------------------
 *
 * Cada ruta declara el permiso que hace falta para entrar, con el MISMO texto
 * que usa el backend en @PreAuthorize y que está cargado en la tabla permiso.
 * Que las tres capas usen literalmente la misma cadena es lo que permite
 * verificar la matriz del informe de punta a punta.
 *
 * Y hay que tener claro qué es esto: RutaProtegida es PRESENTACIÓN. Evita que
 * alguien llegue a una pantalla que no le sirve, pero no protege los datos —
 * quien quiera saltearla pide los datos directo a la API. La seguridad real
 * está en el backend, que revalida el permiso en cada petición.
 */
export default function Router() {
  return (
    <Routes>

      <Route path="/" element={<Login />} />

      {/* ---------- Vidriera pública ---------- */}
      {/* La única pantalla que ve alguien de afuera. Va FUERA de RutaProtegida
          y fuera del marco de la aplicación: no tiene login, ni menú, ni barra
          de navegación. Es la contraparte de /api/vidriera, que es una de las
          tres rutas abiertas del backend.

          Sin formulario de contacto, por decisión explícita del dueño: el
          portfolio es respaldo visual para quien ya llegó por recomendación,
          no una herramienta para captar desconocidos. */}
      <Route path="/obras-realizadas" element={<VidrieraPage />} />

      {/* ---------- Escritorio ---------- */}
      <Route element={<RutaProtegida><Layout /></RutaProtegida>}>
        <Route path="/tablero" element={
          <RutaProtegida permiso="tablero.ver"><Tablero /></RutaProtegida>} />

        <Route path="/obras" element={
          <RutaProtegida permiso="obras.ver"><ObrasPage /></RutaProtegida>} />

        {/* La ficha de obra: donde convergen todos los módulos para UNA obra.
            Reemplaza a la pantalla de muestra de /vista-diseno/obra. */}
        <Route path="/obras/:id" element={
          <RutaProtegida permiso="obras.ver"><DetalleObraPage /></RutaProtegida>} />

        <Route path="/clientes" element={
          <RutaProtegida permiso="clientes.ver"><ClientesPage /></RutaProtegida>} />

        <Route path="/pedidos" element={
          <RutaProtegida permiso="compras.ver"><PedidosPage /></RutaProtegida>} />

        <Route path="/gastos" element={
          <RutaProtegida permiso="gastos.ver"><GastosPage /></RutaProtegida>} />

        <Route path="/personal" element={
          <RutaProtegida permiso="personal.ver"><PersonalPage /></RutaProtegida>} />

        <Route path="/seguimiento" element={
          <RutaProtegida permiso="seguimiento.ver"><SeguimientoPage /></RutaProtegida>} />

        <Route path="/presupuestos" element={
          <RutaProtegida permiso="presupuestos.ver"><PresupuestosPage /></RutaProtegida>} />
        <Route path="/presupuestos/catalogo" element={
          <RutaProtegida permiso="presupuestos.ver"><CatalogoPage /></RutaProtegida>} />
        <Route path="/presupuestos/:id" element={
          <RutaProtegida permiso="presupuestos.ver"><PresupuestoDetalle /></RutaProtegida>} />

        <Route path="/materiales" element={
          <RutaProtegida permiso="materiales.ver"><MaterialesPage /></RutaProtegida>} />

        <Route path="/proveedores" element={
          <RutaProtegida permiso="proveedores.ver"><ProveedoresPage /></RutaProtegida>} />

        <Route path="/cobranzas" element={
          <RutaProtegida permiso="cobros.ver"><CobrosPage /></RutaProtegida>} />

        <Route path="/portfolio" element={
          <RutaProtegida permiso="portfolio.ver"><PortfolioPage /></RutaProtegida>} />

        {/* ---------- Administración ---------- */}
        <Route path="/usuarios" element={
          <RutaProtegida permiso="usuarios.ver"><UsuariosPage /></RutaProtegida>} />

        <Route path="/accesos" element={
          <RutaProtegida permiso="accesos.ver"><AccesosPage /></RutaProtegida>} />

        {/* Sin permiso: cualquiera tiene que poder cambiar su contraseña. */}
        <Route path="/mi-cuenta" element={<MiCuentaPage />} />
      </Route>

      {/* ---------- Capataz (celular en obra) ---------- */}
      <Route element={<RutaProtegida><LayoutCapataz /></RutaProtegida>}>
        <Route path="/obra" element={<Home />} />
        <Route path="/obra/hitos" element={
          <RutaProtegida permiso="seguimiento.ver"><Hitos /></RutaProtegida>} />
        <Route path="/obra/materiales" element={
          <RutaProtegida permiso="compras.ver"><Materiales /></RutaProtegida>} />
      </Route>

      {/* Cualquier dirección que no coincida con las anteriores. */}
      <Route path="*" element={<NoEncontrado />} />

    </Routes>
  );
}
