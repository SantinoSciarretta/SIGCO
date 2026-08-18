import { useMemo, useState } from 'react';
import { ContextoDemo } from './contextoDemo';
import { OBRA_DEL_CAPATAZ } from './demo';

/**
 * Estado compartido entre pantallas.
 *
 * Existe por una razón concreta del diseño: el sistema tiene que demostrar que
 * lo que hace el capataz en la obra lo ve la oficina al instante. Si el capataz
 * tilda un hito desde el celular, el porcentaje de avance cambia en su pantalla,
 * en el detalle de obra del dueño y en el tablero. Para que eso ocurra, ese dato
 * no puede vivir dentro de una pantalla: tiene que estar por encima de todas.
 *
 * También guarda qué obra está seleccionada, que es lo que vincula el tablero
 * con el detalle: se toca una barra del gráfico y el detalle pasa a ser el de
 * esa obra.
 *
 * Cuando se desarrollen los módulos, este estado se reemplaza por datos del
 * backend: el hito tildado será un PATCH a /api/hitos/{id} y el avance vendrá
 * calculado por el servidor, no por el navegador.
 */

/** Estado inicial: los primeros cinco hitos ya están completados. */
const HITOS_INICIALES = { 0: true, 1: true, 2: true, 3: true, 4: true };

/** El pedido arranca con algo cargado, para que la pantalla no se vea vacía. */
const PEDIDO_INICIAL = { 0: 40, 1: 6 };

export default function DemoProvider({ children }) {
  // --- Compartido entre el dueño y el capataz ---
  const [hitosCompletados, setHitosCompletados] = useState(HITOS_INICIALES);
  const [obraSeleccionada, setObraSeleccionada] = useState(OBRA_DEL_CAPATAZ);

  // --- Rol elegido en el ingreso (hasta que exista el módulo Accesos) ---
  const [rol, setRol] = useState('dueno');

  // --- Pedido de materiales del capataz ---
  const [cantidades, setCantidades] = useState(PEDIDO_INICIAL);
  const [pedidoEnviado, setPedidoEnviado] = useState(false);

  // --- Recepción del remito ---
  const [fotoTomada, setFotoTomada] = useState(false);
  const [huboDiferencias, setHuboDiferencias] = useState(null); // null | 'no' | 'si'
  const [notaDiferencia, setNotaDiferencia] = useState('');
  const [recepcionConfirmada, setRecepcionConfirmada] = useState(false);

  const valor = useMemo(() => ({
    hitosCompletados,
    alternarHito: (indice) =>
      setHitosCompletados((previo) => ({ ...previo, [indice]: !previo[indice] })),

    obraSeleccionada,
    seleccionarObra: setObraSeleccionada,

    rol,
    setRol,

    cantidades,
    sumarCantidad: (indice, paso) => {
      setCantidades((previo) => ({ ...previo, [indice]: (previo[indice] || 0) + paso }));
      setPedidoEnviado(false);
    },
    restarCantidad: (indice, paso) => {
      setCantidades((previo) => ({
        ...previo,
        [indice]: Math.max(0, (previo[indice] || 0) - paso),
      }));
      setPedidoEnviado(false);
    },
    pedidoEnviado,
    enviarPedido: () => setPedidoEnviado(true),

    fotoTomada,
    alternarFoto: () => setFotoTomada((previo) => !previo),
    huboDiferencias,
    setHuboDiferencias,
    notaDiferencia,
    setNotaDiferencia,
    recepcionConfirmada,
    confirmarRecepcion: () => setRecepcionConfirmada(true),
  }), [
    hitosCompletados, obraSeleccionada, rol, cantidades, pedidoEnviado,
    fotoTomada, huboDiferencias, notaDiferencia, recepcionConfirmada,
  ]);

  return <ContextoDemo.Provider value={valor}>{children}</ContextoDemo.Provider>;
}
