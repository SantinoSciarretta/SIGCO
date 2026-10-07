# Diagrama de casos de uso de los procesos centrales de SIGCO (sin los ABM).
#
# Para que ninguna linea se cruce se usa generalizacion entre actores: el Capataz
# General hace todo lo del Capataz de Obra (en todas las obras y no solo en la
# suya) y el Dueno hace todo lo del Capataz General. Asi cada caso de uso se une
# a un solo actor, el que lo agrega, y las asociaciones salen en abanico sin cruzarse.
from html import escape
from lucid import Diagram, C

W, H = 1360, 100
AX = 118                       # actores con cuenta
C1, C1W = 478, 330             # primera columna de casos de uso
C2, C2W = 930, 290             # segunda columna (include / extend)
VX = 1240                      # visitante
UH = 66
PITCH = 88
BAND_GAP = 46


def actor(d, x, y, nombre, sub=None, color=None):
    col = color or C['actor']
    s = [f'<g stroke="{col}" stroke-width="2.6" fill="none" stroke-linecap="round">',
         f'<circle cx="{x}" cy="{y-40}" r="15" fill="#ffffff"/>',
         f'<line x1="{x}" y1="{y-25}" x2="{x}" y2="{y+16}"/>',
         f'<line x1="{x-24}" y1="{y-10}" x2="{x+24}" y2="{y-10}"/>',
         f'<line x1="{x}" y1="{y+16}" x2="{x-19}" y2="{y+48}"/>',
         f'<line x1="{x}" y1="{y+16}" x2="{x+19}" y2="{y+48}"/>', '</g>',
         f'<text x="{x}" y="{y+76}" font-size="19" font-weight="bold" fill="{col}" text-anchor="middle">{escape(nombre)}</text>']
    if sub:
        s.append(f'<text x="{x}" y="{y+98}" font-size="15" fill="{"#6a3fb5" if not color else col}" text-anchor="middle">{escape(sub)}</text>')
    d.extra.append('\n'.join(s))
    return (x - 30, y - 58, x + 30, y + (104 if sub else 82))


def build():
    d = Diagram(W, H, 'Casos de uso de SIGCO', title_color='#3b0f6e')
    bandas = [
        ('Capataz de Obra', '(solo su obra)', [
            ('ped', 'Generar un pedido de materiales'),
            ('rec', 'Confirmar la recepción con foto del remito'),
            ('ava', 'Consultar el avance de la obra'),
        ]),
        ('Capataz General', '(todas las obras)', [
            ('eta', 'Marcar una etapa como completada'),
            ('tab', 'Consultar el Tablero reducido'),
        ]),
        ('Dueño', None, [
            ('obr', 'Crear y administrar la obra'),
            ('pre', 'Armar un presupuesto'),
            ('apr', 'Aprobar el presupuesto definitivo'),
            ('def', 'Definir las etapas de la obra'),
            ('cot', 'Pedir cotización por WhatsApp'),
            ('apd', 'Aprobar el pedido con los precios'),
            ('gas', 'Registrar un gasto'),
            ('pla', 'Generar el plan de cobro'),
            ('pag', 'Registrar un pago'),
            ('cac', 'Actualizar el saldo por CAC'),
            ('pub', 'Publicar la obra en el portfolio'),
            ('bal', 'Consultar el balance y los desvíos'),
        ]),
    ]
    y = 236
    actores, cajas = [], []
    for nombre, sub, casos in bandas:
        y0 = y
        for key, texto in casos:
            d.usecase(key, C1, y, texto, w=C1W, h=UH, size=18)
            y += PITCH
        centro = (y0 + y - PITCH) / 2
        actores.append((nombre, sub, centro, [k for k, _ in casos]))
        y += BAND_GAP
    fondo = y - BAND_GAP - PITCH + UH / 2

    # casos de uso que se agregan por include / extend, a la altura de su caso base
    d.usecase('fin', C2, d.nodes['eta']['y'], 'Dar la obra por finalizada', w=C2W, h=UH)
    d.usecase('pdf', C2, d.nodes['pre']['y'], 'Emitir el PDF del presupuesto', w=C2W, h=UH)
    d.usecase('eje', C2, d.nodes['apr']['y'], 'Pasar la obra a ejecución', w=C2W, h=UH)
    d.usecase('gap', C2, d.nodes['apd']['y'], 'Cargar el gasto de la compra por rubro', w=C2W, h=UH)
    d.usecase('vid', C2, d.nodes['pub']['y'], 'Ver la vidriera de obras realizadas', w=C2W, h=UH)

    # limite del sistema
    bx0, by0, bx1, by1 = C1 - C1W / 2 - 34, 150, C2 + C2W / 2 + 34, fondo + 40
    d.extra.append(f'<rect x="{bx0}" y="{by0}" width="{bx1-bx0}" height="{by1-by0}" rx="6" fill="{C["boundary_fill"]}" stroke="{C["boundary"]}" stroke-width="2.4"/>'
                   f'<text x="{bx0+18}" y="{by0+32}" font-size="20" font-weight="bold" fill="{C["boundary"]}">SIGCO</text>')

    # actores con cuenta y sus asociaciones
    pos = {}
    for nombre, sub, cy, keys in actores:
        cajas.append(actor(d, AX, cy, nombre, sub) + (nombre,))
        pos[nombre] = cy
        for k in keys:
            n = d.nodes[k]
            d.line(nombre, k, (AX + 28, cy - 6), (n['x'] - n['w'] / 2, n['y']))
    # generalizacion: Dueno -> Capataz General -> Capataz de Obra
    d.line('Dueño', 'Capataz General', (AX, pos['Dueño'] - 58), (AX, pos['Capataz General'] + 106), kind='gen', arrow=True, head='gen', color=C['actor'], width=2)
    d.line('Capataz General', 'Capataz de Obra', (AX, pos['Capataz General'] - 58), (AX, pos['Capataz de Obra'] + 106), kind='gen', arrow=True, head='gen', color=C['actor'], width=2)

    # include / extend (punteadas, con el estereotipo arriba)
    def rel(a, b, texto, sentido):
        na, nb = d.nodes[a], d.nodes[b]
        if sentido == 'der':
            pa, pb = (na['x'] + na['w'] / 2, na['y']), (nb['x'] - nb['w'] / 2, nb['y'])
        else:
            pa, pb = (na['x'] - na['w'] / 2, na['y']), (nb['x'] + nb['w'] / 2, nb['y'])
        d.line(a, b, pa, pb, kind='rel', dashed=True, arrow=True, label=texto, head='open',
               lpos=((pa[0] + pb[0]) / 2, pa[1] - 13))
    rel('apr', 'eje', '«include»', 'der')
    rel('apd', 'gap', '«include»', 'der')
    rel('pdf', 'pre', '«extend»', 'izq')
    rel('fin', 'eta', '«extend»', 'izq')

    # visitante sin cuenta
    vy = d.nodes['vid']['y']
    cajas.append(actor(d, VX, vy - 10, 'Visitante', '(sin cuenta)', color=C['ext']) + ('Visitante',))
    d.line('Visitante', 'vid', (VX - 28, vy - 16), (C2 + C2W / 2, vy), color='#8b8b90')

    # leyenda y nota
    lx, ly = 24, 70
    d.extra.append(f'<rect x="{lx}" y="{ly}" width="300" height="70" rx="7" fill="#fbfbfb" stroke="#dadada" stroke-width="1.2"/>'
                   f'<circle cx="{lx+24}" cy="{ly+22}" r="7" fill="none" stroke="{C["actor"]}" stroke-width="2.2"/>'
                   f'<text x="{lx+44}" y="{ly+27}" font-size="15" fill="#444">Actor con cuenta en el sistema</text>'
                   f'<circle cx="{lx+24}" cy="{ly+50}" r="7" fill="none" stroke="{C["ext"]}" stroke-width="2.2"/>'
                   f'<text x="{lx+44}" y="{ly+55}" font-size="15" fill="#444">Actor sin cuenta</text>')
    d.note('nota', VX, d.nodes['gap']['y'] + 40, 'El cliente y el proveedor no usan el sistema: reciben el PDF del presupuesto y el pedido de cotización que el dueño les manda por fuera', w=210, size=15)
    d.H = int(by1 + 30)
    extra = [(x0, y0, x1, y1, n) for x0, y0, x1, y1, n in cajas]
    return d, extra


if __name__ == '__main__':
    d, extra = build()
    d.save('casos-de-uso', extra_boxes=extra)
