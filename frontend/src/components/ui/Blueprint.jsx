/**
 * Bloque del sistema: rectángulo de línea fina con las marcas de registro en
 * las cuatro esquinas.
 *
 * Es el contenedor que usan todas las pantallas, el equivalente a la "tarjeta"
 * de otros sistemas. Las marcas de esquina vienen del plano de obra, donde
 * sirven para alinear hojas superpuestas; acá son la firma visual de SIGCO.
 *
 * Se escribe una vez y lo reutilizan los 14 módulos, así nadie tiene que
 * acordarse de poner los cuatro <i className="corner">.
 *
 * @param {string|Function} [as]   elemento a renderizar: 'div' por defecto,
 *                                 'button' o 'a' cuando el bloque es accionable
 *                                 (las marcas necesitan la clase .blueprint en
 *                                 el propio elemento para dibujarse)
 * @param {boolean} [claro]        true sobre fondo oscuro: aclara las marcas
 *                                 para que se vean sobre el azul profundo
 * @param {boolean} [sinBorde]     oculta el borde y deja solo las marcas
 */
export default function Blueprint({
  as: Elemento = 'div',
  children,
  className = '',
  style,
  claro = false,
  sinBorde = false,
  ...resto
}) {
  const colorMarcas = claro ? { color: 'rgba(242,242,243,.6)' } : undefined;
  const estilo = sinBorde ? { border: 'none', ...style } : style;

  return (
    <Elemento className={`blueprint ${className}`.trim()} style={estilo} {...resto}>
      <i className="corner tl" style={colorMarcas} />
      <i className="corner tr" style={colorMarcas} />
      <i className="corner bl" style={colorMarcas} />
      <i className="corner br" style={colorMarcas} />
      {children}
    </Elemento>
  );
}
