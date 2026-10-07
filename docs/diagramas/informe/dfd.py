# Diagramas de flujo de datos (DFD) de SIGCO, para el informe.
# Uso: python dfd.py [numero ...]   (sin argumentos genera todos)
#
# Un DFD no muestra el orden de los pasos: muestra que datos entran, que proceso
# los transforma y en que almacen quedan guardados. Notacion de Gane y Sarson:
#   rectangulo blanco   entidad externa (quien entrega o recibe datos)
#   caja violeta        proceso, con su numero arriba
#   cilindro            almacen de datos (una o mas tablas de la base)
# La flecha punteada es un dato que entra o sale por fuera del sistema: lo carga
# o lo entrega el dueno. Una entidad o un almacen que aparece mas de una vez en el
# mismo diagrama lleva un asterisco, la convencion del DFD para no cruzar lineas.
import sys
from collections import Counter
from html import escape
from lucid import Diagram, C, text_w

XE, XP, XS = 155, 690, 1180          # columnas: entidades, procesos, almacenes
WE, WP, WS = 210, 280, 240
PITCH_S, PITCH_E = 94, 100           # separacion vertical entre almacenes / entidades
OFF = 13                             # separacion de las dos flechas de un ida y vuelta
GAP_FILAS = 96

# Almacenes de datos: siempre el mismo numero en todos los diagramas.
D = {
    'obras': 'D1 Obras',
    'presupuestos': 'D2 Presupuestos',
    'catalogo': 'D3 Rubros y materiales',
    'pedidos': 'D4 Pedidos',
    'proveedores': 'D5 Proveedores y cotizaciones',
    'gastos': 'D6 Gastos',
    'etapas': 'D7 Etapas y plantillas',
    'cuotas': 'D8 Cuotas y pagos',
    'cac': 'D9 Coeficientes CAC',
    'auditoria': 'D10 Auditoría',
    'archivos': 'D11 Archivos (fotos)',
}


def leyenda(d, x, y):
    o = [f'<rect x="{x}" y="{y}" width="330" height="150" rx="7" fill="#fbfbfb" stroke="#dadada"/>']
    o.append(f'<rect x="{x+14}" y="{y+14}" width="30" height="20" fill="#fff" stroke="{C["actor"]}" stroke-width="1.8"/>'
             f'<text x="{x+56}" y="{y+29}" font-size="14" fill="#444">Entidad externa</text>')
    o.append(f'<rect x="{x+14}" y="{y+42}" width="30" height="20" rx="5" fill="{C["sys"]}"/>'
             f'<text x="{x+56}" y="{y+57}" font-size="14" fill="#444">Proceso</text>')
    o.append(f'<path d="M {x+14} {y+74} L {x+14} {y+88} A 15 5 0 0 0 {x+44} {y+88} L {x+44} {y+74}" fill="{C["note"]}" stroke="#8a8a90" stroke-width="1.5"/>'
             f'<ellipse cx="{x+29}" cy="{y+74}" rx="15" ry="5" fill="#fafafb" stroke="#8a8a90" stroke-width="1.5"/>'
             f'<text x="{x+56}" y="{y+86}" font-size="14" fill="#444">Almacén de datos</text>')
    o.append(f'<line x1="{x+12}" y1="{y+108}" x2="{x+44}" y2="{y+108}" stroke="#7a7a80" stroke-width="1.8" stroke-dasharray="6 4"/>'
             f'<text x="{x+56}" y="{y+113}" font-size="14" fill="#444">Por fuera del sistema, vía el dueño</text>')
    o.append(f'<text x="{x+22}" y="{y+139}" font-size="16" font-weight="bold" fill="#444">*</text>'
             f'<text x="{x+56}" y="{y+137}" font-size="14" fill="#444">Aparece más de una vez</text>')
    d.extra.append('\n'.join(o))


def flecha(d, a, pa, b, pb, texto, arriba=True, dashed=False):
    """Flecha horizontal con la etiqueta arriba o abajo de la linea."""
    xa, ya = d.port(a, pa)
    xb, _ = d.port(b, pb)
    y = ya + (-9 if arriba else 23)
    d.edge(a, pa, b, pb, kind='df', label=texto, dashed=dashed, lpos=((xa + xb) / 2, y))


def modulo(titulo, filas):
    """filas: lista de dict(num, texto, ent=[(nombre, [(sentido, etiqueta, punteada)])],
    alm=[(clave, [(sentido, etiqueta)])], sig=etiqueta hacia el proceso siguiente o None).
    sentido de una entidad: 'in' (le da datos al proceso) o 'out' (los recibe).
    sentido de un almacen: 'lee' o 'guarda'."""
    d = Diagram(1360, 100, titulo, title_size=30)
    d.title_x = 'left'
    leyenda(d, 1010, 14)
    for x, t in [(XE, 'Entidades externas'), (XP, 'Procesos'), (XS, 'Almacenes de datos')]:
        d.extra.append(f'<text x="{x}" y="196" font-size="14" font-weight="bold" fill="#8a8a90" text-anchor="middle" letter-spacing="1">{t.upper()}</text>')

    veces_e = Counter(n for f in filas for n, _ in f['ent'])
    veces_s = Counter(k for f in filas for k, _ in f['alm'])
    y = 230
    previo = None
    for i, f in enumerate(filas):
        alto_s = len(f['alm']) * PITCH_S - (PITCH_S - 62)
        alto_e = len(f['ent']) * PITCH_E - (PITCH_E - 64)
        h = max(118, alto_s + 26, alto_e + 26)
        pid = f'p{i}'
        d.process(pid, XP, 0, f['num'], f['texto'], w=WP, h=h)
        h = d.nodes[pid]['h']
        cy = y + h / 2
        d.nodes[pid]['y'] = cy
        # almacenes, apilados y centrados en la fila
        for j, (clave, flujos) in enumerate(f['alm']):
            sid = f's{i}_{j}'
            nombre = D[clave] + (' *' if veces_s[clave] > 1 else '')
            d.store(sid, XS, cy - alto_s / 2 + 31 + j * PITCH_S, nombre, w=WS)
            dys = [0] if len(flujos) == 1 else [-OFF, OFF]
            for (sentido, etiqueta), dy, arriba in zip(flujos, dys, [True, False]):
                rel = d.nodes[sid]['y'] - cy + dy
                if sentido == 'lee':
                    flecha(d, sid, ('l', dy), pid, ('r', rel), etiqueta, arriba)
                else:
                    flecha(d, pid, ('r', rel), sid, ('l', dy), etiqueta, arriba)
        # entidades
        for j, (nombre, flujos) in enumerate(f['ent']):
            eid = f'e{i}_{j}'
            d.entity(eid, XE, cy - alto_e / 2 + 32 + j * PITCH_E, nombre + (' *' if veces_e[nombre] > 1 else ''), w=WE)
            dys = [0] if len(flujos) == 1 else [-OFF, OFF]
            for (sentido, etiqueta, punteada), dy, arriba in zip(flujos, dys, [True, False]):
                rel = d.nodes[eid]['y'] - cy + dy
                if sentido == 'in':
                    flecha(d, eid, ('r', dy), pid, ('l', rel), etiqueta, arriba, punteada)
                else:
                    flecha(d, pid, ('l', rel), eid, ('r', dy), etiqueta, arriba, punteada)
        if previo is not None and filas[i - 1].get('sig'):
            etiqueta = filas[i - 1]['sig']
            ya = d.port(previo, 'b')[1]
            d.edge(previo, 'b', pid, 't', kind='df', label=etiqueta,
                   lpos=(XP + 16 + text_w(etiqueta, 15) / 2, (ya + y) / 2 + 5))
        previo = pid
        y += h + GAP_FILAS
    d.fit_height(36)
    return d


def contexto():
    d = Diagram(1600, 100, 'DFD de contexto (nivel 0): SIGCO y su entorno', title_size=30)
    d.title_x = 'left'
    leyenda(d, 1250, 14)
    XI, XC, XD = 160, 805, 1440
    izq = [('Dueño', [('in', 'Presupuestos, gastos, pagos y aprobaciones', False),
                      ('out', 'Tablero, alertas, balances y documentos PDF', False)]),
           ('Capataz General', [('in', 'Pedidos, recepciones y avance de etapas', False),
                                ('out', 'Obras, pedidos y avance, sin montos', False)]),
           ('Capataz de Obra', [('in', 'Pedidos y recepción con foto del remito', False),
                                ('out', 'Pedidos y avance de su obra', False)])]
    der = [('Cliente', [('out', 'Presupuesto y planilla de pagos en PDF', True),
                        ('in', 'Aprobación y pagos', True)]),
           ('Corralón', [('out', 'Pedido de cotización por WhatsApp', True),
                         ('in', 'Precios de los materiales', True)]),
           ('Cámara Argentina de la Construcción', [('in', 'Coeficiente CAC del mes', True)]),
           ('Visitante de la vidriera', [('out', 'Obras realizadas y sus fotos', False)])]
    top = 250
    d.process('sigco', XC, 0, '0', 'SIGCO: Sistema Integral de Gestión de Obras', w=300, h=4 * 150 - 30, size=19)
    cy = top + d.nodes['sigco']['h'] / 2
    d.nodes['sigco']['y'] = cy
    for lado, lista, x in [('i', izq, XI), ('d', der, XD)]:
        n = len(lista)
        paso = 190 if n == 3 else 150
        for j, (nombre, flujos) in enumerate(lista):
            eid = f'{lado}{j}'
            d.entity(eid, x, cy + (j - (n - 1) / 2) * paso, nombre, w=240, h=70)
            dys = [0] if len(flujos) == 1 else [-OFF - 2, OFF + 2]
            for (sentido, etiqueta, punteada), dy, arriba in zip(flujos, dys, [True, False]):
                rel = d.nodes[eid]['y'] - cy + dy
                lado_e, lado_s = ('r', 'l') if lado == 'i' else ('l', 'r')
                if sentido == 'in':
                    flecha(d, eid, (lado_e, dy), 'sigco', (lado_s, rel), etiqueta, arriba, punteada)
                else:
                    flecha(d, 'sigco', (lado_s, rel), eid, (lado_e, dy), etiqueta, arriba, punteada)
    d.fit_height(40)
    return d


def presupuestacion():
    return modulo('DFD 1: Presupuestación', [
        dict(num='1.1', texto='Armar el presupuesto',
             ent=[('Dueño', [('in', 'Rubros, cantidades y precios', False), ('out', 'Subtotales y total en el momento', False)])],
             alm=[('obras', [('lee', 'Tipo de obra y plazo')]),
                  ('catalogo', [('lee', 'Materiales de cada rubro')]),
                  ('presupuestos', [('guarda', 'Ítems, honorarios y total')])],
             sig='Presupuesto armado'),
        dict(num='1.2', texto='Emitir el PDF',
             ent=[('Cliente', [('out', 'PDF sin precios unitarios', True)])],
             alm=[('presupuestos', [('lee', 'Subtotales por rubro')])],
             sig='Presupuesto enviado'),
        dict(num='1.3', texto='Aprobar el presupuesto',
             ent=[('Dueño', [('in', 'Respuesta del cliente', False)])],
             alm=[('presupuestos', [('guarda', 'Presupuesto aprobado')]),
                  ('obras', [('guarda', 'Obra en ejecución')]),
                  ('auditoria', [('guarda', 'Quién aprobó y por cuánto')])]),
    ])


def compras():
    return modulo('DFD 2: Compras', [
        dict(num='2.1', texto='Registrar el pedido',
             ent=[('Capataz o dueño', [('in', 'Materiales y cantidades', False), ('out', 'Pedido pendiente', False)])],
             alm=[('obras', [('lee', 'Obra en ejecución')]),
                  ('catalogo', [('lee', 'Materiales activos')]),
                  ('pedidos', [('guarda', 'Pedido y quién lo pidió')])],
             sig='Pedido pendiente'),
        dict(num='2.2', texto='Pedir cotización',
             ent=[('Dueño', [('in', 'Pedido a cotizar', False)]),
                  ('Corralón', [('out', 'Mensaje de WhatsApp, sin precios', True)])],
             alm=[('pedidos', [('lee', 'Materiales y cantidades')]),
                  ('proveedores', [('lee', 'Teléfono del corralón')])],
             sig='Pedido en cotización'),
        dict(num='2.3', texto='Aprobar el pedido',
             ent=[('Dueño', [('in', 'Precios sin IVA', False), ('out', 'Precios sugeridos y total', False)])],
             alm=[('proveedores', [('lee', 'Última cotización'), ('guarda', 'Cotización nueva')]),
                  ('pedidos', [('guarda', 'Pedido enviado')]),
                  ('gastos', [('guarda', 'Gasto por rubro, sin IVA')]),
                  ('auditoria', [('guarda', 'Quién aprobó')])],
             sig='Pedido enviado al proveedor'),
        dict(num='2.4', texto='Confirmar la recepción',
             ent=[('Capataz', [('in', 'Foto del remito y diferencias', False)])],
             alm=[('archivos', [('guarda', 'Foto del remito')]),
                  ('pedidos', [('guarda', 'Estado de la recepción')])]),
    ])


def gastos():
    return modulo('DFD 3: Gastos', [
        dict(num='3.1', texto='Registrar el gasto',
             ent=[('Dueño', [('in', 'Obra, rubro, tipo, monto y fecha', False)])],
             alm=[('presupuestos', [('lee', 'Definitivo aprobado')]),
                  ('archivos', [('guarda', 'Foto del comprobante')]),
                  ('gastos', [('guarda', 'Gasto y quién lo cargó')])],
             sig='Gasto registrado'),
        dict(num='3.2', texto='Calcular el estado financiero',
             ent=[('Dueño', [('out', 'Semáforo por rubro y ganancia', False)])],
             alm=[('presupuestos', [('lee', 'Presupuestado por rubro')]),
                  ('gastos', [('lee', 'Gastado por rubro')])]),
        dict(num='3.3', texto='Anular un gasto',
             ent=[('Dueño', [('in', 'Gasto y motivo', False)])],
             alm=[('gastos', [('guarda', 'Gasto anulado')]),
                  ('auditoria', [('guarda', 'Quién lo anuló y por qué')])]),
    ])


def seguimiento():
    return modulo('DFD 4: Seguimiento de obras', [
        dict(num='4.1', texto='Definir las etapas',
             ent=[('Dueño', [('in', 'Etapas, días y fechas de inicio', False)])],
             alm=[('etapas', [('lee', 'Plantilla elegida'), ('guarda', 'Etapas con su porcentaje')]),
                  ('obras', [('lee', 'Fecha de inicio')])],
             sig='Etapas pendientes'),
        dict(num='4.2', texto='Registrar el avance',
             ent=[('Dueño o capataz general', [('in', 'Etapa completada y fecha', False)])],
             alm=[('etapas', [('guarda', 'Etapa completada')]),
                  ('obras', [('guarda', 'Obra finalizada')])],
             sig='Etapa completada'),
        dict(num='4.3', texto='Calcular el avance',
             ent=[('Dueño', [('out', 'Avance físico, financiero y alertas', False)])],
             alm=[('etapas', [('lee', 'Porcentajes cumplidos')]),
                  ('gastos', [('lee', 'Total gastado')]),
                  ('presupuestos', [('lee', 'Subtotal aprobado')]),
                  ('obras', [('lee', 'Plazo de la obra')])]),
    ])


def cobros():
    return modulo('DFD 5: Cobros', [
        dict(num='5.1', texto='Generar el plan de cobro',
             ent=[('Dueño', [('in', 'Primer vencimiento', False)])],
             alm=[('presupuestos', [('lee', 'Total, anticipo y cuotas')]),
                  ('cuotas', [('guarda', 'Anticipo y cuotas')])],
             sig='Plan de cobro'),
        dict(num='5.2', texto='Registrar un pago',
             ent=[('Dueño', [('in', 'Pago del cliente', False)])],
             alm=[('cuotas', [('lee', 'Saldo de la cuota'), ('guarda', 'Pago y estado de la cuota')]),
                  ('auditoria', [('guarda', 'Cobro registrado')])]),
        dict(num='5.3', texto='Actualizar el saldo por CAC',
             ent=[('Dueño', [('in', 'Coeficiente del mes', False), ('out', 'Cuánto pasa a deberse', False)])],
             alm=[('cac', [('guarda', 'Coeficiente')]),
                  ('cuotas', [('lee', 'Saldos pendientes'), ('guarda', 'Saldos actualizados')]),
                  ('auditoria', [('guarda', 'Actualización aplicada')])]),
        dict(num='5.4', texto='Emitir la planilla y avisar vencimientos',
             ent=[('Dueño', [('out', 'Cuotas por vencer y vencidas', False)]),
                  ('Cliente', [('out', 'Planilla de pagos en PDF', True)])],
             alm=[('cuotas', [('lee', 'Cuotas y pagos')])]),
    ])


TODOS = [contexto, presupuestacion, compras, gastos, seguimiento, cobros]
NOMBRES = ['contexto', 'presupuestacion', 'compras', 'gastos', 'seguimiento', 'cobros']

if __name__ == '__main__':
    pedidos = [int(a) for a in sys.argv[1:]] or list(range(len(TODOS)))
    for i in pedidos:
        TODOS[i]().save(f'dfd-{i}-{NOMBRES[i]}')
