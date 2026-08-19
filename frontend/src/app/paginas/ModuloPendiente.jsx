import Blueprint from '../../components/ui/Blueprint';

/**
 * Pantalla que se muestra en los módulos todavía no desarrollados.
 *
 * Permite que la navegación esté completa desde el comienzo: se puede recorrer
 * el sistema y ver cómo va a estar organizado, aunque las pantallas no existan.
 * Cada módulo reemplaza este marcador cuando le toca.
 *
 * `children` es para los módulos que ya tienen alguna parte construida: sirve
 * para enlazarla desde acá, en lugar de dejarla sin acceso.
 */
export default function ModuloPendiente({ nombre, children }) {
  return (
    <Blueprint style={{ padding: '32px 28px', maxWidth: '56ch' }}>
      <span className="kicker kicker-acento">Módulo</span>
      <h2 style={{ margin: '6px 0 10px' }}>{nombre}</h2>
      <p className="text-muted" style={{ margin: 0 }}>
        Todavía no está desarrollado. La pantalla se construye cuando le toque
        el turno en el orden de módulos.
      </p>
      {children}
    </Blueprint>
  );
}
