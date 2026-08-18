/**
 * Pantalla que se muestra en los modulos todavia no desarrollados.
 *
 * Permite que el menu de navegacion este completo desde el comienzo: se puede
 * recorrer el sistema entero y ver como va a estar organizado, aunque las
 * pantallas no existan. Cada modulo reemplaza este marcador cuando le toca.
 */
export default function ModuloPendiente({ nombre }) {
  return (
    <div>
      <h1>{nombre}</h1>
      <p>Este modulo todavia no esta desarrollado.</p>
    </div>
  );
}
