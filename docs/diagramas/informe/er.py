# Diagrama entidad-relacion (modelo conceptual) de SIGCO, en notacion de Chen.
# Uso: python er.py [numero ...]   (sin argumentos genera todos)
#
#   rectangulo   entidad            (gris: entidad de otra area)
#   rombo        relacion, con su verbo y el tipo: 1:N, M:N o 1:1
#   ovalo        atributo propio de una relacion muchos a muchos
#   (min,max)    junto a cada entidad: con cuantas de ESA entidad se relaciona
#                cada elemento del otro extremo. Ejemplo: Cliente (1,1) — encarga —
#                (0,N) Obra: cada obra tiene un cliente; cada cliente, de 0 a N obras.
#
# Es el mismo modelo que el diagrama relacional, visto a nivel conceptual: las
# tablas intermedias (operario_obra, pedido_material, rol_permiso) no son
# entidades sino relaciones muchos a muchos, y sus columnas propias pasan a ser
# atributos de la relacion. Las cardinalidades salen de las claves foraneas: una
# FK que admite nulo da (0,1); una que no, (1,1); una FK unica, relacion 1:1.
import math
import sys
from html import escape
from lucid import Diagram, C, text_w, wrap

EW, EH = 200, 62          # entidad
RW, RH = 158, 84          # rombo de relacion
VIOLETA = '#3d1a78'


class ER(Diagram):
    def __init__(self, titulo, W, H):
        super().__init__(W, H, titulo, title_size=30)
        self.title_x = 'left'
        self.cards = []

    def ent(self, k, x, y, texto, otra_area=False, w=EW):
        lines = wrap(texto, 17, w - 24, bold=True)
        self._add(k, 'ent_er', x, y, w, EH, lines, 17, gris=otra_area)

    def rel(self, k, x, y, verbo, tipo):
        lines = wrap(verbo, 15, RW * 0.6, bold=True)
        self._add(k, 'rel', x, y, RW, RH + (16 if len(lines) > 1 else 0), lines, 15, tipo=tipo)

    def attr(self, k, x, y, texto):
        w = text_w(texto, 14.5) + 34
        self._add(k, 'attr', x, y, w, 40, [texto], 14.5)

    def _borde(self, k, ancla, hacia):
        """Punto donde la recta que sale de 'ancla' hacia 'hacia' toca el borde del nodo k."""
        n = self.nodes[k]
        cx, cy = ancla
        dx, dy = hacia[0] - cx, hacia[1] - cy
        L = math.hypot(dx, dy) or 1
        ux, uy = dx / L, dy / L
        hw, hh = n['w'] / 2, n['h'] / 2
        ox, oy = cx - n['x'], cy - n['y']
        if n['kind'] == 'rel':
            # rombo: |x|/hw + |y|/hh = 1, partiendo del ancla (puede no ser el centro)
            lo, hi = 0.0, L
            for _ in range(40):
                m = (lo + hi) / 2
                px, py = ox + ux * m, oy + uy * m
                if abs(px) / hw + abs(py) / hh < 1:
                    lo = m
                else:
                    hi = m
            return (cx + ux * lo, cy + uy * lo)
        if n['kind'] == 'attr':
            t = 1 / math.sqrt((ux / hw) ** 2 + (uy / hh) ** 2)
            return (cx + ux * t, cy + uy * t)
        ts = []
        if abs(ux) > 1e-9:
            ts += [(hw - ox) / ux, (-hw - ox) / ux]
        if abs(uy) > 1e-9:
            ts += [(hh - oy) / uy, (-hh - oy) / uy]
        t = max(0.0, min(t for t in ts if t > -1e-6))
        return (cx + ux * t, cy + uy * t)

    def unir(self, e, r, card=None, de=(0, 0), a=None, lado_card=1):
        """Linea recta de la entidad e al rombo r. de: desplazamiento del ancla en la entidad.
        a: punto del rombo al que llega ('l', 'r', 't', 'b' o None para el centro)."""
        ne, nr = self.nodes[e], self.nodes[r]
        p_e = (ne['x'] + de[0], ne['y'] + de[1])
        if a:
            p_r = {'l': (nr['x'] - nr['w'] / 2, nr['y']), 'r': (nr['x'] + nr['w'] / 2, nr['y']),
                   't': (nr['x'], nr['y'] - nr['h'] / 2), 'b': (nr['x'], nr['y'] + nr['h'] / 2)}[a]
        else:
            p_r = (nr['x'], nr['y'])
        a0 = self._borde(e, p_e, p_r)
        a1 = p_r if a else self._borde(r, p_r, p_e)
        self.edges.append(dict(a=e, b=r, pts=[a0, a1], label=None, dashed=False, arrow=False,
                               color=VIOLETA, lpos=None, kind='er', width=1.8))
        if card:
            L = math.hypot(a1[0] - a0[0], a1[1] - a0[1]) or 1
            ux, uy = (a1[0] - a0[0]) / L, (a1[1] - a0[1]) / L
            px, py = -uy * lado_card, ux * lado_card
            self.cards.append((a0[0] + ux * 30 + px * 16, a0[1] + uy * 30 + py * 16, card))

    def unir_attr(self, at, r):
        na, nr = self.nodes[at], self.nodes[r]
        a0 = self._borde(at, (na['x'], na['y']), (nr['x'], nr['y']))
        a1 = self._borde(r, (nr['x'], nr['y']), (na['x'], na['y']))
        self.edges.append(dict(a=at, b=r, pts=[a0, a1], label=None, dashed=False, arrow=False,
                               color='#8a7fb0', lpos=None, kind='er', width=1.4))

    def leyenda(self):
        x, y = self.W - 470, 16
        o = [f'<rect x="{x}" y="{y}" width="452" height="168" rx="7" fill="#fbfbfb" stroke="#dadada"/>',
             f'<rect x="{x+14}" y="{y+14}" width="38" height="22" fill="#fff" stroke="{VIOLETA}" stroke-width="1.8"/>'
             f'<text x="{x+64}" y="{y+30}" font-size="14" fill="#444">Entidad</text>',
             f'<rect x="{x+150}" y="{y+14}" width="38" height="22" fill="#fff" stroke="#9a9aa2" stroke-width="1.8"/>'
             f'<text x="{x+200}" y="{y+30}" font-size="14" fill="#444">Entidad de otra área</text>',
             f'<polygon points="{x+33},{y+46} {x+52},{y+57} {x+33},{y+68} {x+14},{y+57}" fill="#ece6fb" stroke="{VIOLETA}" stroke-width="1.6"/>'
             f'<text x="{x+64}" y="{y+62}" font-size="14" fill="#444">Relación (1:N, M:N o 1:1)</text>',
             f'<ellipse cx="{x+33}" cy="{y+89}" rx="19" ry="10" fill="#fff" stroke="#8a7fb0" stroke-width="1.5"/>'
             f'<text x="{x+64}" y="{y+94}" font-size="14" fill="#444">Atributo de una relación</text>',
             f'<text x="{x+14}" y="{y+124}" font-size="14" font-weight="bold" fill="{VIOLETA}">(1,1) (0,N)</text>'
             f'<text x="{x+104}" y="{y+124}" font-size="14" fill="#444">Mínimo y máximo, junto a cada entidad.</text>',
             f'<text x="{x+14}" y="{y+150}" font-size="13" fill="#6a6a72">Ej.: Cliente (1,1) — encarga — (0,N) Obra: cada obra tiene un</text>',
             f'<text x="{x+14}" y="{y+166}" font-size="13" fill="#6a6a72">cliente, y cada cliente tiene de cero a muchas obras.</text>']
        self.extra.append('\n'.join(o))

    def _node_svg(self, n):
        k = n['kind']
        x, y, w, h = n['x'], n['y'], n['w'], n['h']
        if k == 'ent_er':
            col = '#9a9aa2' if n['gris'] else VIOLETA
            txt = '#6a6a72' if n['gris'] else '#2a1650'
            out = [f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h}" fill="#ffffff" stroke="{col}" stroke-width="2.2"/>']
            total = (len(n['lines']) - 1) * 21
            for i, ln in enumerate(n['lines']):
                out.append(f'<text x="{x}" y="{y - total/2 + i*21 + 6:.1f}" font-size="17" font-weight="bold" fill="{txt}" text-anchor="middle">{escape(ln)}</text>')
            return '\n'.join(out)
        if k == 'rel':
            pts = f'{x},{y-h/2} {x+w/2},{y} {x},{y+h/2} {x-w/2},{y}'
            out = [f'<polygon points="{pts}" fill="#ece6fb" stroke="{VIOLETA}" stroke-width="1.8"/>']
            lines = n['lines'] + [n['tipo']]
            total = (len(lines) - 1) * 18
            for i, ln in enumerate(lines):
                ultimo = i == len(lines) - 1
                out.append(f'<text x="{x}" y="{y - total/2 + i*18 + 5:.1f}" font-size="{13 if ultimo else 15}" '
                           f'{"" if ultimo else "font-weight=\"bold\" "}fill="{"#6a4aa3" if ultimo else "#2a1650"}" text-anchor="middle">{escape(ln)}</text>')
            return '\n'.join(out)
        if k == 'attr':
            return (f'<ellipse cx="{x}" cy="{y}" rx="{w/2}" ry="{h/2}" fill="#ffffff" stroke="#8a7fb0" stroke-width="1.5"/>'
                    f'<text x="{x}" y="{y+5}" font-size="14.5" fill="#3a3a44" text-anchor="middle">{escape(n["lines"][0])}</text>')
        return super()._node_svg(n)

    def svg(self):
        self.leyenda()
        base = super().svg()
        etiquetas = []
        for x, y, t in self.cards:
            w = text_w(t, 14, bold=True)
            etiquetas.append(f'<rect x="{x-w/2-3:.1f}" y="{y-11:.1f}" width="{w+6:.1f}" height="18" fill="#ffffff"/>'
                             f'<text x="{x:.1f}" y="{y+4:.1f}" font-size="14" font-weight="bold" fill="{VIOLETA}" text-anchor="middle">{t}</text>')
        return base.replace('</svg>', '\n'.join(etiquetas) + '\n</svg>')


def obras():
    d = ER('Entidad-relación 1: Clientes, obras y presupuestos', 1660, 1390)
    d.ent('cliente', 170, 480, 'Cliente')
    d.ent('obra', 620, 480, 'Obra')
    d.ent('pres', 1070, 480, 'Presupuesto')
    d.ent('cac', 620, 200, 'Registro CAC', otra_area=True)
    d.ent('item', 1070, 790, 'Ítem de presupuesto', w=230)
    d.ent('rubro', 1070, 1260, 'Rubro')
    d.ent('sub', 640, 1260, 'Subrubro')
    d.ent('mat', 1500, 1260, 'Material')
    d.rel('r1', 395, 480, 'encarga', '1:N')
    d.rel('r2', 845, 480, 'tiene', '1:N')
    d.rel('r3', 620, 340, 'último aplicado', '1:N')
    d.rel('r4', 1380, 300, 'deriva de', '1:N')
    d.rel('r5', 1070, 640, 'contiene', '1:N')
    d.rel('r6', 1015, 950, 'clasifica', '1:N')
    d.rel('r7', 1125, 1110, 'es referido por', '1:N')
    d.rel('r8', 797, 1025, 'detalla', '1:N')
    d.rel('r9', 855, 1260, 'se divide en', '1:N')
    d.rel('r10', 1285, 1260, 'agrupa', '1:N')
    d.rel('r11', 1342, 1025, 'se usa en', '1:N')
    d.unir('cliente', 'r1', '(1,1)'); d.unir('obra', 'r1', '(0,N)', lado_card=-1)
    d.unir('obra', 'r2', '(1,1)'); d.unir('pres', 'r2', '(0,N)', lado_card=-1)
    d.unir('cac', 'r3', '(0,1)', lado_card=-1); d.unir('obra', 'r3', '(0,N)')
    d.unir('pres', 'r4', '(0,1)', de=(60, -31), a='l', lado_card=-1)
    d.unir('pres', 'r4', '(0,N)', de=(100, 0), a='b', lado_card=1)
    d.unir('pres', 'r5', '(1,1)', lado_card=-1); d.unir('item', 'r5', '(0,N)')
    d.unir('rubro', 'r6', '(1,1)', de=(-55, 0), a='b', lado_card=1); d.unir('item', 'r6', '(0,N)', de=(-55, 0), a='t', lado_card=1)
    d.unir('rubro', 'r7', '(0,1)', de=(55, 0), a='b', lado_card=-1); d.unir('item', 'r7', '(0,N)', de=(55, 0), a='t', lado_card=-1)
    d.unir('sub', 'r8', '(0,1)', lado_card=-1); d.unir('item', 'r8', '(0,N)', de=(-115, 0), lado_card=1)
    d.unir('rubro', 'r9', '(1,1)'); d.unir('sub', 'r9', '(0,N)', lado_card=-1)
    d.unir('rubro', 'r10', '(1,1)', lado_card=-1); d.unir('mat', 'r10', '(0,N)')
    d.unir('mat', 'r11', '(0,1)'); d.unir('item', 'r11', '(0,N)', de=(115, 0), lado_card=-1)
    return d


def compras():
    d = ER('Entidad-relación 2: Compras y proveedores', 1500, 1010)
    d.ent('obra', 700, 300, 'Obra', otra_area=True)
    d.ent('usuario', 150, 560, 'Usuario', otra_area=True)
    d.ent('pedido', 700, 560, 'Pedido')
    d.ent('prov', 1150, 560, 'Proveedor')
    d.ent('obs', 1150, 300, 'Observación de proveedor', w=240)
    d.ent('mat', 700, 880, 'Material', otra_area=True)
    d.ent('cot', 1150, 880, 'Cotización')
    d.rel('r1', 700, 430, 'genera', '1:N')
    d.rel('r2', 375, 530, 'solicita', '1:N')
    d.rel('r3', 495, 590, 'recibe', '1:N')
    d.rel('r4', 925, 560, 'se le pide a', '1:N')
    d.rel('r5', 1150, 430, 'recibe', '1:N')
    d.rel('r6', 925, 430, 'origina', '1:N')
    d.rel('r7', 700, 720, 'incluye', 'M:N')
    d.rel('r8', 1150, 720, 'cotiza', '1:N')
    d.rel('r9', 925, 880, 'precio de', '1:N')
    d.attr('a1', 450, 690, 'cantidad')
    d.attr('a2', 450, 760, 'precio_unitario')
    d.unir('obra', 'r1', '(1,1)'); d.unir('pedido', 'r1', '(0,N)', lado_card=-1)
    d.unir('usuario', 'r2', '(0,1)', de=(0, -30), a='l'); d.unir('pedido', 'r2', '(0,N)', de=(-100, -30), a='r', lado_card=-1)
    d.unir('usuario', 'r3', '(0,1)', de=(0, 30), a='l', lado_card=-1); d.unir('pedido', 'r3', '(0,N)', de=(-100, 30), a='r')
    d.unir('pedido', 'r4', '(0,N)'); d.unir('prov', 'r4', '(0,1)', lado_card=-1)
    d.unir('prov', 'r5', '(1,1)', lado_card=-1); d.unir('obs', 'r5', '(0,N)')
    d.unir('pedido', 'r6', '(0,1)', lado_card=-1); d.unir('obs', 'r6', '(0,N)', lado_card=-1)
    d.unir('pedido', 'r7', '(0,N)', lado_card=-1); d.unir('mat', 'r7', '(0,N)')
    d.unir('prov', 'r8', '(1,1)', lado_card=-1); d.unir('cot', 'r8', '(0,N)')
    d.unir('mat', 'r9', '(1,1)'); d.unir('cot', 'r9', '(0,N)', lado_card=-1)
    d.unir_attr('a1', 'r7'); d.unir_attr('a2', 'r7')
    return d


def gastos():
    d = ER('Entidad-relación 3: Gastos y personal', 1500, 1080)
    d.ent('gasto', 830, 640, 'Gasto')
    d.ent('obra', 830, 300, 'Obra', otra_area=True)
    d.ent('rubro', 1300, 470, 'Rubro', otra_area=True)
    d.ent('sub', 1300, 810, 'Subrubro', otra_area=True)
    d.ent('pedido', 830, 980, 'Pedido', otra_area=True)
    d.ent('operario', 380, 640, 'Operario')
    d.ent('usuario', 160, 980, 'Usuario', otra_area=True)
    d.ent('inas', 380, 300, 'Inasistencia')
    d.rel('r1', 830, 470, 'tiene', '1:N')
    d.rel('r2', 1065, 555, 'clasifica', '1:N')
    d.rel('r3', 1065, 725, 'detalla', '1:N')
    d.rel('r4', 830, 810, 'genera', '1:N')
    d.rel('r5', 605, 640, 'cobra', '1:N')
    d.rel('r6', 495, 810, 'registra', '1:N')
    d.rel('r7', 605, 300, 'ocurre en', '1:N')
    d.rel('r8', 380, 470, 'falta', '1:N')
    d.rel('r9', 160, 640, 'registra', '1:N')
    d.rel('r10', 600, 470, 'trabaja en', 'M:N')
    d.attr('a1', 560, 380, 'fecha_asignacion')
    d.attr('a2', 640, 545, 'fecha_desasignacion')
    d.unir('obra', 'r1', '(1,1)'); d.unir('gasto', 'r1', '(0,N)', lado_card=-1)
    d.unir('rubro', 'r2', '(1,1)'); d.unir('gasto', 'r2', '(0,N)', lado_card=-1)
    d.unir('sub', 'r3', '(0,1)', lado_card=-1); d.unir('gasto', 'r3', '(0,N)')
    d.unir('pedido', 'r4', '(0,1)', lado_card=-1); d.unir('gasto', 'r4', '(0,N)')
    d.unir('operario', 'r5', '(0,1)', lado_card=-1); d.unir('gasto', 'r5', '(0,N)')
    d.unir('usuario', 'r6', '(0,1)'); d.unir('gasto', 'r6', '(0,N)', lado_card=-1)
    d.unir('obra', 'r7', '(1,1)'); d.unir('inas', 'r7', '(0,N)', lado_card=-1)
    d.unir('operario', 'r8', '(1,1)'); d.unir('inas', 'r8', '(0,N)', lado_card=-1)
    d.unir('usuario', 'r9', '(0,1)', lado_card=-1); d.unir('inas', 'r9', '(0,N)', de=(-70, 0))
    d.unir('operario', 'r10', '(0,N)', de=(60, 0), lado_card=-1); d.unir('obra', 'r10', '(0,N)', de=(-60, 0), lado_card=1)
    d.unir_attr('a1', 'r10'); d.unir_attr('a2', 'r10')
    return d


def seguimiento():
    d = ER('Entidad-relación 4: Seguimiento de obras', 1240, 900)
    d.ent('hito', 620, 520, 'Hito (etapa)')
    d.ent('obra', 620, 250, 'Obra', otra_area=True)
    d.ent('rubro', 1060, 520, 'Rubro', otra_area=True)
    d.ent('usuario', 180, 520, 'Usuario', otra_area=True)
    d.ent('plant', 330, 790, 'Plantilla de hitos')
    d.ent('det', 900, 790, 'Detalle de plantilla')
    d.rel('r1', 620, 385, 'se divide en', '1:N')
    d.rel('r2', 840, 520, 'clasifica', '1:N')
    d.rel('r3', 400, 520, 'completa', '1:N')
    d.rel('r4', 615, 790, 'contiene', '1:N')
    d.unir('obra', 'r1', '(1,1)'); d.unir('hito', 'r1', '(0,N)', lado_card=-1)
    d.unir('rubro', 'r2', '(0,1)', lado_card=-1); d.unir('hito', 'r2', '(0,N)')
    d.unir('usuario', 'r3', '(0,1)'); d.unir('hito', 'r3', '(0,N)', lado_card=-1)
    d.unir('plant', 'r4', '(1,1)'); d.unir('det', 'r4', '(0,N)', lado_card=-1)
    return d


def cobros():
    d = ER('Entidad-relación 5: Cobros y portfolio', 1260, 900)
    d.ent('obra', 620, 450, 'Obra', otra_area=True)
    d.ent('cuota', 200, 450, 'Cuota')
    d.ent('pago', 200, 770, 'Pago')
    d.ent('usuario', 620, 770, 'Usuario', otra_area=True)
    d.ent('pub', 1040, 450, 'Publicación de portfolio', w=230)
    d.ent('img', 1040, 770, 'Imagen de portfolio', w=230)
    d.ent('cac', 620, 200, 'Registro CAC')
    d.rel('r1', 410, 450, 'se cobra en', '1:N')
    d.rel('r2', 200, 610, 'recibe', '1:N')
    d.rel('r3', 410, 770, 'registra', '1:N')
    d.rel('r4', 830, 450, 'se publica en', '1:1')
    d.rel('r5', 1040, 610, 'muestra', '1:N')
    d.rel('r6', 620, 325, 'último aplicado', '1:N')
    d.unir('obra', 'r1', '(1,1)'); d.unir('cuota', 'r1', '(0,N)', lado_card=-1)
    d.unir('cuota', 'r2', '(1,1)', lado_card=-1); d.unir('pago', 'r2', '(0,N)')
    d.unir('usuario', 'r3', '(0,1)', lado_card=-1); d.unir('pago', 'r3', '(0,N)')
    d.unir('obra', 'r4', '(1,1)', lado_card=-1); d.unir('pub', 'r4', '(0,1)')
    d.unir('pub', 'r5', '(1,1)', lado_card=-1); d.unir('img', 'r5', '(0,N)')
    d.unir('cac', 'r6', '(0,1)', lado_card=-1); d.unir('obra', 'r6', '(0,N)')
    return d


def usuarios():
    d = ER('Entidad-relación 6: Usuarios y accesos', 1240, 900)
    d.ent('usuario', 620, 470, 'Usuario')
    d.ent('rol', 1040, 470, 'Rol')
    d.ent('permiso', 1040, 780, 'Permiso')
    d.ent('aud', 200, 470, 'Registro de auditoría', w=230)
    d.ent('operario', 620, 220, 'Operario', otra_area=True)
    d.rel('r1', 830, 470, 'asigna', '1:N')
    d.rel('r2', 1040, 625, 'incluye', 'M:N')
    d.rel('r3', 410, 470, 'realiza', '1:N')
    d.rel('r4', 620, 345, 'se vincula con', '1:1')
    d.unir('rol', 'r1', '(1,1)', lado_card=-1); d.unir('usuario', 'r1', '(0,N)')
    d.unir('rol', 'r2', '(0,N)', lado_card=-1); d.unir('permiso', 'r2', '(0,N)')
    d.unir('usuario', 'r3', '(1,1)', lado_card=-1); d.unir('aud', 'r3', '(0,N)')
    d.unir('operario', 'r4', '(0,1)', lado_card=-1); d.unir('usuario', 'r4', '(0,1)')
    return d


TODOS = [obras, compras, gastos, seguimiento, cobros, usuarios]
NOMBRES = ['obras-presupuestos', 'compras', 'gastos-personal', 'seguimiento', 'cobros-portfolio', 'usuarios-accesos']

if __name__ == '__main__':
    pedidos = [int(a) for a in sys.argv[1:]] or list(range(1, len(TODOS) + 1))
    for i in pedidos:
        TODOS[i - 1]().save(f'er-{i}-{NOMBRES[i - 1]}')
