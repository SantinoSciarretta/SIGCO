# Diagrama de clases del modelo de dominio de SIGCO, en notacion UML.
# Uso: python clases.py [numero ...]   (sin argumentos genera todos)
#
# Las clases se leen de las entidades JPA del backend (backend/src/main/java),
# no se escriben a mano: si una entidad gana un atributo o un metodo, aparece
# solo al regenerar. Se divide en las mismas seis areas que el entidad-relacion.
#
#   caja de tres partes   nombre / atributos (-) / metodos de negocio (+)
#   caja gris             clase de otra area: solo el nombre
#   linea                 asociacion, con la multiplicidad en cada extremo
#   rombo negro           composicion: la clase del rombo es duena de sus partes
#                         (las crea, las guarda y las borra con ella)
#   linea punteada        referencia por identificador (un id guardado como
#                         numero, sin relacion JPA)
#
# Se omiten los getters, los setters y las consultas de estado (estaActivo,
# esBorrador...), que no agregan comportamiento.
import math
import os
import re
import sys
from html import escape
from pathlib import Path
from lucid import render, text_w

SRC = Path(__file__).resolve().parents[3] / 'backend/src/main/java/com/sigco'

NOMBRE, MIEMBRO = 17, 14.5          # tamanos de letra
LH = 19                             # alto de linea de los miembros
PADX, PADY = 12, 8
VIOLETA, VIOLETA_CLARO, GRIS = '#3d1a78', '#ece7fb', '#9a9aa0'
CONSULTA = re.compile(r'^(esta|es[A-Z]|estuvo|fue|tiene|lleva|debe)[A-Za-z]*\(')


def partir_parametros(args):
    """Separa los parametros por las comas de primer nivel (no las de un tipo generico)."""
    partes, nivel, actual = [], 0, ''
    for ch in args:
        nivel += ch == '<'
        nivel -= ch == '>'
        if ch == ',' and nivel == 0:
            partes.append(actual); actual = ''
        else:
            actual += ch
    partes.append(actual)
    limpio = (re.sub(r'\b[a-z]+(\.[a-z]+)+\.', '', p.replace('final ', '')) for p in partes)
    return [re.sub(r'\s+', ' ', p).strip() for p in limpio if p.strip()]


def leer_entidades():
    """Atributos, relaciones y metodos de negocio de cada @Entity."""
    clases = {}
    for f in sorted(SRC.rglob('*.java')):
        t = f.read_text(encoding='utf-8')
        if '@Entity' not in t:
            continue
        cls = re.search(r'public class (\w+)', t).group(1)
        body = t[t.index('public class'):]
        attrs, rels = [], []
        for m in re.finditer(r'private\s+(static\s+final\s+)?([\w<>, ]+?)\s+(\w+)\s*(=[^;]*)?;', body):
            if m.group(1):
                continue  # constantes
            tipo, nombre = m.group(2).strip(), m.group(3)
            prev = body[:m.start()]
            k = max(prev.rfind(';'), prev.rfind('}'), prev.rfind('{'))
            ann = re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', prev[k + 1:], flags=re.S))
            rel = re.search(r'@(ManyToOne|OneToMany|OneToOne|ManyToMany)', ann)
            if rel:
                obligatorio = 'nullable = false' in ann or 'optional = false' in ann
                rels.append(dict(tipo=rel.group(1), campo=nombre,
                                 destino=re.sub(r'.*<|>', '', tipo), obligatorio=obligatorio,
                                 compuesta='CascadeType.ALL' in ann or 'orphanRemoval' in ann))
            elif tipo.endswith('Id') and tipo != 'Long':
                attrs.append(f'- {nombre}: {tipo} «clave compuesta»')
            else:
                attrs.append(f'- {nombre}: {tipo}')
        metodos = []
        for m in re.finditer(r'\n    public\s+(static\s+)?([\w<>, \[\]]+)\s+(\w+)\(([^)]*)\)', body):
            ret, n, args = m.group(2), m.group(3), m.group(4)
            if re.match(r'(get|set|is[A-Z]|equals|hashCode|toString)', n) or n == cls:
                continue
            tipos = [p.rsplit(' ', 1)[0].strip() for p in partir_parametros(args)]
            firma = f"{n}({', '.join(tipos) if len(tipos) <= 3 else '…'}): {ret}"
            if CONSULTA.match(firma):
                continue
            metodos.append(('+ ' if not m.group(1) else '+ «static» ') + firma)
        clases[cls] = dict(attrs=attrs, rels=rels, metodos=metodos)
    return clases


class Dibujo:
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.cajas, self.svg = {}, []

    def clase(self, nombre, x, y, datos):
        """Caja UML de tres partes con la esquina superior izquierda en (x, y)."""
        filas = datos['attrs'] or [' ']
        mets = datos['metodos'] or [' ']
        w = max([text_w(nombre, NOMBRE, bold=True)] + [text_w(s, MIEMBRO) for s in filas + mets]) + 2 * PADX
        h1 = NOMBRE + 2 * PADY + 6
        h2 = len(filas) * LH + 2 * PADY
        h3 = len(mets) * LH + 2 * PADY
        h = h1 + h2 + h3
        s = [f'<rect x="{x}" y="{y}" width="{w:.1f}" height="{h}" fill="#ffffff" stroke="{VIOLETA}" stroke-width="2"/>',
             f'<rect x="{x}" y="{y}" width="{w:.1f}" height="{h1}" fill="{VIOLETA_CLARO}" stroke="{VIOLETA}" stroke-width="2"/>',
             f'<text x="{x + w / 2:.1f}" y="{y + h1 / 2 + 6}" font-size="{NOMBRE}" font-weight="bold" fill="{VIOLETA}" text-anchor="middle">{escape(nombre)}</text>',
             f'<line x1="{x}" y1="{y + h1 + h2}" x2="{x + w:.1f}" y2="{y + h1 + h2}" stroke="{VIOLETA}" stroke-width="1.5"/>']
        for i, t in enumerate(filas):
            s.append(f'<text x="{x + PADX}" y="{y + h1 + PADY + 14 + i * LH}" font-size="{MIEMBRO}" fill="#222">{escape(t)}</text>')
        for i, t in enumerate(mets):
            s.append(f'<text x="{x + PADX}" y="{y + h1 + h2 + PADY + 14 + i * LH}" font-size="{MIEMBRO}" fill="#222">{escape(t)}</text>')
        self.svg += s
        self.cajas[nombre] = (x, y, w, h)

    def externa(self, nombre, x, y):
        w, h = text_w(nombre, NOMBRE, bold=True) + 2 * PADX + 10, NOMBRE + 2 * PADY + 10
        self.svg += [f'<rect x="{x}" y="{y}" width="{w:.1f}" height="{h}" fill="#f4f4f6" stroke="{GRIS}" stroke-width="2" stroke-dasharray="7 4"/>',
                     f'<text x="{x + w / 2:.1f}" y="{y + h / 2 + 6}" font-size="{NOMBRE}" font-weight="bold" fill="#77777c" text-anchor="middle">{escape(nombre)}</text>']
        self.cajas[nombre] = (x, y, w, h)

    def _borde(self, nombre, hacia, desde=None):
        x, y, w, h = self.cajas[nombre]
        cx, cy = desde or (x + w / 2, y + h / 2)
        dx, dy = hacia[0] - cx, hacia[1] - cy
        t = min((w / 2) / abs(dx) if dx else 1e9, (h / 2) / abs(dy) if dy else 1e9)
        if desde:  # punto de salida forzado: se proyecta sobre el lado mas cercano
            return desde
        return cx + dx * t, cy + dy * t

    def asociacion(self, a, b, ma, mb, rol=None, compuesta=False, punteada=False, pa=None, pb=None, via=(), rol_pos=None, ma_pos=None):
        """Linea de a hacia b, con quiebres opcionales (via). ma/mb: multiplicidad junto a
        cada extremo. pa/pb fuerzan el punto de salida y de llegada."""
        xa, ya, wa, ha = self.cajas[a]
        xb, yb, wb, hb = self.cajas[b]
        ca, cb = (xa + wa / 2, ya + ha / 2), (xb + wb / 2, yb + hb / 2)
        p1 = pa or self._borde(a, via[0] if via else cb)
        p2 = pb or self._borde(b, via[-1] if via else ca)
        pts = [p1, *via, p2]
        dash = ' stroke-dasharray="8 5"' if punteada else ''
        self.svg.append('<polyline points="' + ' '.join(f'{x:.1f},{y:.1f}' for x, y in pts) +
                        f'" fill="none" stroke="#3a3a3a" stroke-width="1.8"{dash}/>')

        def unit(p, q):
            L = math.hypot(q[0] - p[0], q[1] - p[1]) or 1
            return (q[0] - p[0]) / L, (q[1] - p[1]) / L
        u1 = unit(pts[0], pts[1])          # sale de a
        u2 = unit(pts[-1], pts[-2])        # sale de b, hacia atras
        if compuesta:  # rombo negro en el extremo b (el dueno)
            ux, uy = u2; nx, ny = -uy, ux; d = 13
            pts_r = [p2, (p2[0] + ux * d + nx * 7, p2[1] + uy * d + ny * 7), (p2[0] + ux * 2 * d, p2[1] + uy * 2 * d),
                     (p2[0] + ux * d - nx * 7, p2[1] + uy * d - ny * 7)]
            self.svg.append('<polygon points="' + ' '.join(f'{px:.1f},{py:.1f}' for px, py in pts_r) + '" fill="#3a3a3a"/>')
        for (px, py), (ux, uy), s, off in ((p1, u1, ma, 18), (p2, u2, mb, 34 if compuesta else 18)):
            if not s:
                continue
            if ma_pos and s is ma and (px, py) == p1:
                self.svg.append(f'<text x="{ma_pos[0]:.1f}" y="{ma_pos[1]:.1f}" font-size="15" font-weight="bold" fill="#3a3a3a" text-anchor="middle">{s}</text>')
                continue
            nx, ny = -uy, ux
            tx, ty = px + ux * off + nx * 14, py + uy * off + ny * 14
            self.svg.append(f'<text x="{tx:.1f}" y="{ty + 5:.1f}" font-size="15" font-weight="bold" fill="#3a3a3a" text-anchor="middle">{s}</text>')
        if rol and rol_pos:
            mx, my, anchor = rol_pos
            self.svg.append(f'<text x="{mx:.1f}" y="{my:.1f}" font-size="14" font-style="italic" fill="#555" text-anchor="{anchor}">{escape(rol)}</text>')
        elif rol:
            i = len(pts) // 2
            p, q = pts[i - 1], pts[i]
            ux, uy = unit(p, q); nx, ny = -uy, ux
            mx, my = (p[0] + q[0]) / 2 - nx * 14, (p[1] + q[1]) / 2 - ny * 14
            self.svg.append(f'<text x="{mx:.1f}" y="{my + 5:.1f}" font-size="14" font-style="italic" fill="#555" text-anchor="middle">{escape(rol)}</text>')

    def bucle(self, nombre, rol, m1, m2):
        """Autoasociacion: sale por la derecha y vuelve por arriba."""
        x, y, w, h = self.cajas[nombre]
        a, b = (x + w, y + 40), (x + w - 50, y)
        self.svg.append(f'<path d="M{a[0]:.1f},{a[1]} L{x + w + 60:.1f},{a[1]} L{x + w + 60:.1f},{y - 50} L{b[0]:.1f},{y - 50} L{b[0]:.1f},{b[1]}" fill="none" stroke="#3a3a3a" stroke-width="1.8"/>')
        self.svg += [f'<text x="{x + w + 20:.1f}" y="{a[1] - 8}" font-size="15" font-weight="bold" fill="#3a3a3a">{m1}</text>',
                     f'<text x="{b[0] - 10:.1f}" y="{y - 12}" font-size="15" font-weight="bold" fill="#3a3a3a" text-anchor="end">{m2}</text>',
                     f'<text x="{(b[0] + x + w + 60) / 2:.1f}" y="{y - 58:.1f}" font-size="14" font-style="italic" fill="#555" text-anchor="middle">{escape(rol)}</text>']

    def guardar(self, nombre, titulo):
        cab = '' if os.environ.get('SIN_TITULO') else \
            f'<text x="40" y="52" font-size="30" font-weight="bold" fill="#2f2f2f">{escape(titulo)}</text>'
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.W}" height="{self.H}" viewBox="0 0 {self.W} {self.H}" '
               f'font-family="Arial, Helvetica, sans-serif"><rect width="{self.W}" height="{self.H}" fill="#ffffff"/>{cab}'
               + '\n'.join(self.svg) + '</svg>')
        Path(nombre + '.svg').write_text(svg, encoding='utf-8')
        render(nombre + '.svg', nombre + '.png', self.W, self.H, 2)
        print(nombre, self.W, self.H)


def tamanos(clases, nombres):
    d = Dibujo(10, 10)
    for n in nombres:
        d.clase(n, 0, 0, clases[n])
        x, y, w, h = d.cajas[n]
        print(f'  {n}: {w:.0f} x {h}')


def d1(C):
    d = Dibujo(1070, 1580)
    d.clase('Cliente', 40, 100, C['Cliente'])
    d.clase('Obra', 600, 100, C['Obra'])
    d.externa('RegistroCac', 40, 470)
    d.clase('ItemPresupuesto', 40, 690, C['ItemPresupuesto'])
    d.clase('Presupuesto', 560, 690, C['Presupuesto'])
    d.clase('Subrubro', 40, 1290, C['Subrubro'])
    d.clase('Rubro', 290, 1290, C['Rubro'])
    d.clase('Material', 660, 1290, C['Material'])
    d.asociacion('Obra', 'Cliente', '*', '1')
    d.asociacion('Obra', 'RegistroCac', '*', '0..1', rol='último CAC aplicado', punteada=True,
                 rol_pos=(390, 458, 'middle'))
    ox, oy, ow, oh = d.cajas['Obra']
    d.asociacion('Presupuesto', 'Obra', '*', '1', pa=(ox + ow / 2, 690), pb=(ox + ow / 2, oy + oh))
    d.bucle('Presupuesto', 'presupuestoBase', '*', '0..1')
    iy = 690 + d.cajas['ItemPresupuesto'][3]
    d.asociacion('ItemPresupuesto', 'Presupuesto', '*', '1', compuesta=True)
    d.asociacion('ItemPresupuesto', 'Rubro', '*', '1', rol='rubro', pa=(310, iy), pb=(310, 1290),
                 rol_pos=(302, 1130, 'end'))
    d.asociacion('ItemPresupuesto', 'Rubro', '*', '0..1', rol='rubroReferido', pa=(365, iy), pb=(365, 1290),
                 rol_pos=(373, 1130, 'start'))
    d.asociacion('ItemPresupuesto', 'Subrubro', '*', '0..1', pa=(134, iy), pb=(134, 1290))
    ix, _, iw, _ = d.cajas['ItemPresupuesto']
    d.asociacion('ItemPresupuesto', 'Material', '*', '0..1', pa=(ix + iw, iy - 30),
                 via=((480, iy - 30), (480, 1245), (820, 1245)), pb=(820, 1290))
    d.asociacion('Subrubro', 'Rubro', '*', '1')
    d.asociacion('Material', 'Rubro', '*', '1')
    return d, 'clases-1-obras-presupuestos', 'Diagrama de clases 1: Clientes, obras y presupuestos'


def caja(d, n):
    x, y, w, h = d.cajas[n]
    return x, y, w, h, x + w / 2, y + h / 2


def d2(C):
    d = Dibujo(960, 1080)
    d.externa('Obra', 40, 90)
    d.clase('Pedido', 40, 220, C['Pedido'])
    d.clase('ObservacionProveedor', 600, 90, C['ObservacionProveedor'])
    d.clase('Proveedor', 600, 340, C['Proveedor'])
    d.clase('Cotizacion', 600, 720, C['Cotizacion'])
    d.clase('PedidoMaterial', 40, 820, C['PedidoMaterial'])
    px, py, pw, ph, pcx, pcy = caja(d, 'Pedido')
    d.externa('Material', 600, 980)
    d.asociacion('Pedido', 'Obra', '*', '1')
    vx, vy, vw, vh, vcx, vcy = caja(d, 'Proveedor')
    d.asociacion('Pedido', 'Proveedor', '*', '0..1', pa=(px + pw, vy + 60), pb=(vx, vy + 60))
    d.asociacion('PedidoMaterial', 'Pedido', '*', '1', compuesta=True,
                 pa=(pcx, 820), pb=(pcx, py + ph))
    mx, my, mw, mh, mcx, mcy = caja(d, 'PedidoMaterial')
    d.asociacion('PedidoMaterial', 'Material', '*', '1', pa=(mx + mw, 1000), pb=(600, 1000))
    d.asociacion('Cotizacion', 'Proveedor', '*', '1')
    d.asociacion('Cotizacion', 'Material', '*', '1')
    d.asociacion('ObservacionProveedor', 'Proveedor', '*', '1')
    ox, oy, ow, oh, ocx, ocy = caja(d, 'ObservacionProveedor')
    d.asociacion('ObservacionProveedor', 'Pedido', '*', '0..1', punteada=True, rol='idPedido',
                 pa=(ox, oy + 120), pb=(px + pw, py + 40), rol_pos=(490, 214, 'middle'))
    return d, 'clases-2-compras', 'Diagrama de clases 2: Compras y proveedores'


def d3(C):
    d = Dibujo(1060, 960)
    d.clase('Operario', 40, 110, C['Operario'])
    d.clase('Gasto', 600, 110, C['Gasto'])
    d.clase('OperarioObra', 40, 470, C['OperarioObra'])
    d.clase('Inasistencia', 40, 720, C['Inasistencia'])
    d.externa('Obra', 420, 640)
    gx, gy, gw, gh, gcx, gcy = caja(d, 'Gasto')
    d.externa('Rubro', 640, 640)
    d.externa('Subrubro', 770, 640)
    d.externa('Pedido', 930, 640)
    ox, oy, ow, oh, ocx, ocy = caja(d, 'Operario')
    d.asociacion('Gasto', 'Operario', '*', '0..1', punteada=True, rol='idOperario',
                 pa=(gx, oy + 120), pb=(ox + ow, oy + 120), rol_pos=((ox + ow + gx) / 2, oy + 108, 'middle'))
    d.asociacion('OperarioObra', 'Operario', '*', '1', compuesta=True)
    ix, iy, iw, ih, icx, icy = caja(d, 'Inasistencia')
    d.asociacion('Inasistencia', 'Operario', '*', '1', pa=(ix, iy + 60),
                 via=((18, iy + 60), (18, oy + 200)), pb=(ox, oy + 200))
    bx, by, bw, bh, bcx, bcy = caja(d, 'Obra')
    d.asociacion('OperarioObra', 'Obra', '*', '1', pb=(bx, by + 12))
    d.asociacion('Inasistencia', 'Obra', '*', '1', pb=(bx, by + bh - 12))
    d.asociacion('Gasto', 'Obra', '*', '1', pa=(gx + 40, gy + gh), pb=(bcx + 20, by),
                 ma_pos=(gx + 20, gy + gh + 30))
    salidas = {'Rubro': gx + 95, 'Subrubro': gx + gw - 45}
    for ref, mult, punt in (('Rubro', '1', False), ('Subrubro', '0..1', False), ('Pedido', '0..1', True)):
        rx, ry, rw, rh, rcx, rcy = caja(d, ref)
        pa = (salidas[ref], gy + gh) if ref in salidas else (gx + gw - 12, gy + gh)
        d.asociacion('Gasto', ref, '*', mult, punteada=punt, pa=pa, pb=(rcx, ry),
                     rol='idPedido' if punt else None, rol_pos=(rcx + 14, ry - 60, 'start') if punt else None)
    return d, 'clases-3-gastos-personal', 'Diagrama de clases 3: Gastos y personal'


def d4(C):
    d = Dibujo(860, 860)
    d.externa('Obra', 40, 90)
    d.clase('Hito', 40, 220, C['Hito'])
    hx, hy, hw, hh, hcx, hcy = caja(d, 'Hito')
    d.externa('Rubro', 520, 330)
    d.clase('PlantillaHito', 40, 640, C['PlantillaHito'])
    d.clase('PlantillaHitoDetalle', 520, 640, C['PlantillaHitoDetalle'])
    d.asociacion('Hito', 'Obra', '*', '1')
    rx, ry, rw, rh, rcx, rcy = caja(d, 'Rubro')
    d.asociacion('Hito', 'Rubro', '*', '0..1', pa=(hx + hw, rcy), pb=(rx, rcy))
    d.asociacion('PlantillaHitoDetalle', 'PlantillaHito', '*', '1', compuesta=True)
    return d, 'clases-4-seguimiento', 'Diagrama de clases 4: Seguimiento de obras'


def d5(C):
    d = Dibujo(960, 1000)
    d.externa('Obra', 420, 90)
    d.clase('Cuota', 40, 230, C['Cuota'])
    d.clase('Pago', 40, 690, C['Pago'])
    d.clase('RegistroCac', 360, 330, C['RegistroCac'])
    d.clase('PublicacionPortfolio', 640, 230, C['PublicacionPortfolio'])
    d.clase('ImagenPortfolio', 640, 620, C['ImagenPortfolio'])
    ox, oy, ow, oh, ocx, ocy = caja(d, 'Obra')
    d.asociacion('Cuota', 'Obra', '*', '1', pb=(ox, ocy))
    d.asociacion('Pago', 'Cuota', '*', '1')
    qx, qy, qw, qh, qcx, qcy = caja(d, 'PublicacionPortfolio')
    d.asociacion('PublicacionPortfolio', 'Obra', '0..1', '1', pa=(qx + 70, qy), pb=(ox + ow, ocy))
    d.asociacion('ImagenPortfolio', 'PublicacionPortfolio', '*', '1', compuesta=True)
    ox, oy, ow, oh, ocx, ocy = caja(d, 'Obra')
    cx, cy, cw, ch, ccx, ccy = caja(d, 'RegistroCac')
    d.asociacion('Obra', 'RegistroCac', '*', '0..1', punteada=True, pa=(ccx, oy + oh), pb=(ccx, cy),
                 rol='último CAC aplicado', rol_pos=(ccx + 12, cy - 110, 'start'))
    return d, 'clases-5-cobros-portfolio', 'Diagrama de clases 5: Cobros y portfolio'


def d6(C):
    d = Dibujo(900, 980)
    d.externa('Operario', 40, 90)
    d.clase('Usuario', 40, 220, C['Usuario'])
    d.clase('Rol', 560, 220, C['Rol'])
    d.clase('Permiso', 560, 480, C['Permiso'])
    d.clase('RegistroAuditoria', 560, 740, C['RegistroAuditoria'])
    d.asociacion('Usuario', 'Operario', '*', '0..1')
    ux, uy, uw, uh, ucx, ucy = caja(d, 'Usuario')
    rx, ry, rw, rh, rcx, rcy = caja(d, 'Rol')
    d.asociacion('Usuario', 'Rol', '*', '1', pa=(ux + uw, rcy), pb=(rx, rcy))
    d.asociacion('Rol', 'Permiso', '*', '*')
    ax, ay, aw, ah, acx, acy = caja(d, 'RegistroAuditoria')
    d.asociacion('RegistroAuditoria', 'Usuario', '*', '1', pa=(ax, acy), pb=(ux + uw, uy + uh - 40))
    return d, 'clases-6-usuarios-accesos', 'Diagrama de clases 6: Usuarios y accesos'


DIAGRAMAS = [d1, d2, d3, d4, d5, d6]

if __name__ == '__main__':
    C = leer_entidades()
    elegidos = [int(a) for a in sys.argv[1:]] or range(1, len(DIAGRAMAS) + 1)
    for i in elegidos:
        d, nombre, titulo = DIAGRAMAS[i - 1](C)
        d.guardar(nombre, titulo)
