# Mini libreria para dibujar los diagramas del informe con el aspecto de Lucidchart:
# cajas con esquinas redondeadas, rombos de decision, conectores ortogonales y notas.
# Las posiciones se fijan a mano (como en Lucidchart) y check() revisa que ninguna
# flecha cruce a otra ni atraviese una caja.
import os, subprocess, math
from html import escape
from PIL import ImageFont

FONTS = 'C:/Windows/Fonts/'
_cache = {}
def _font(size, bold=False, italic=False):
    name = 'arialbi.ttf' if bold and italic else 'arialbd.ttf' if bold else 'ariali.ttf' if italic else 'arial.ttf'
    k = (name, size)
    if k not in _cache:
        _cache[k] = ImageFont.truetype(FONTS + name, size)
    return _cache[k]

def text_w(t, size, bold=False, italic=False):
    return _font(size, bold, italic).getlength(t)

def wrap(text, size, maxw, bold=False, italic=False):
    lines = []
    for par in text.split('\n'):
        cur = ''
        for w in par.split(' '):
            cand = (cur + ' ' + w).strip()
            if text_w(cand, size, bold, italic) <= maxw or not cur:
                cur = cand
            else:
                lines.append(cur); cur = w
        lines.append(cur)
    return lines

# Paleta (tomada de los diagramas de referencia)
C = dict(
    sys='#4b2a83', sys_stroke='#3c1f6e', sys_text='#ffffff',
    user='#e8e0f6', user_stroke='#d6c9ef', user_text='#1d1d1f',
    line='#3a3a3a', loop='#4b3b7d', diamond='#8a8a8a', term='#3a3a3a',
    note='#f1f1f3', note_stroke='#b5b5ba', note_text='#555559',
    title='#2f2f2f', label='#333333',
    uc_fill='#ece7fb', uc_stroke='#3d1a78', uc_line='#7b4fc0', actor='#3d1466', ext='#6b6b70',
    boundary='#3d1a78', boundary_fill='#faf8fd',
)

class Diagram:
    def __init__(self, W, H, title=None, title_size=34, title_color=None):
        self.W, self.H = W, H
        self.nodes = {}
        self.edges = []
        self.extra = []      # svg suelto (leyendas, actores)
        self.title, self.title_size = title, title_size
        self.title_color = title_color or C['title']

    # ---------- nodos ----------
    def _add(self, name, kind, x, y, w, h, lines, size, **kw):
        self.nodes[name] = dict(kind=kind, x=x, y=y, w=w, h=h, lines=lines, size=size, **kw)
        return name

    def box(self, name, x, y, text, kind='user', w=400, size=20, minh=86):
        lines = wrap(text, size, w - 44)
        h = max(minh, len(lines) * size * 1.25 + 34)
        return self._add(name, kind, x, y, w, h, lines, size)

    def term(self, name, x, y, text, w=220, h=82, size=21):
        return self._add(name, 'term', x, y, w, h, [text], size)

    def diamond(self, name, x, y, text, w=320, h=160, size=18):
        lines = wrap(text, size, w * 0.56, bold=True)
        return self._add(name, 'diamond', x, y, w, h, lines, size)

    def note(self, name, x, y, text, w=330, size=16):
        lines = wrap(text, size, w - 36, italic=True)
        h = len(lines) * size * 1.3 + 30
        return self._add(name, 'note', x, y, w, h, lines, size)

    def status(self, name, x, y, text, color, w=220, h=60, size=19):
        """Caja chica de color funcional (por ejemplo, el semaforo de Gastos)."""
        return self._add(name, 'status', x, y, w, h, [text], size, color=color)

    def entity(self, name, x, y, text, w=210, h=64, size=17):
        """Entidad externa del DFD: quien entrega o recibe datos, fuera del sistema."""
        lines = wrap(text, size, w - 24, bold=True)
        return self._add(name, 'entity', x, y, w, max(h, len(lines) * size * 1.25 + 26), lines, size)

    def process(self, name, x, y, num, text, w=270, h=110, size=17):
        """Proceso del DFD, con su numero en la franja de arriba (Gane y Sarson)."""
        lines = wrap(text, size, w - 30)
        h = max(h, len(lines) * size * 1.25 + 52)
        return self._add(name, 'process', x, y, w, h, lines, size, num=num)

    def store(self, name, x, y, text, w=230, h=62, size=16):
        """Almacen de datos del DFD (una o mas tablas de la base)."""
        lines = wrap(text, size, w - 24, bold=True)
        return self._add(name, 'store', x, y, w, h, lines, size)

    def conn(self, name, x, y, letter, r=30):
        """Conector entre partes de un mismo diagrama (circulo con una letra)."""
        return self._add(name, 'conn', x, y, 2 * r, 2 * r, [letter], 24)

    def usecase(self, name, x, y, text, w=300, h=74, size=18):
        lines = wrap(text, size, w * 0.84)
        return self._add(name, 'usecase', x, y, w, h, lines, size)

    def stack(self, names, y0, gap=56):
        """Apila nodos en columna dejando siempre el mismo espacio entre uno y otro.
        y0 es el borde superior del primero. Devuelve el borde inferior del ultimo."""
        y = y0
        for nm in names:
            n = self.nodes[nm]
            n['y'] = y + n['h'] / 2
            y += n['h'] + gap
        return y - gap

    def align(self, name, ref, dy=0):
        """Pone un nodo a la altura de otro (por ejemplo, una rama lateral)."""
        self.nodes[name]['y'] = self.nodes[ref]['y'] + dy

    def fit_height(self, margin=40):
        self.H = int(max(n['y'] + n['h'] / 2 for n in self.nodes.values()) + margin)

    def port(self, name, side):
        n = self.nodes[name]
        x, y, w, h = n['x'], n['y'], n['w'], n['h']
        if isinstance(side, tuple):          # ('b', dx) -> desplazado sobre el borde
            side, d = side
        else:
            d = 0
        return {'t': (x + d, y - h / 2), 'b': (x + d, y + h / 2), 'l': (x - w / 2, y + d), 'r': (x + w / 2, y + d)}[side]

    # ---------- conectores ----------
    def edge(self, a, sa, b, sb, via=(), label=None, dashed=False, arrow=True, color=None,
             lpos=None, kind='flow', width=None, start_point=None, end_point=None):
        p0 = start_point or self.port(a, sa)
        p1 = end_point or self.port(b, sb)
        first_vertical = (sa if isinstance(sa, str) else sa[0]) in 'tb'
        pts = [p0]
        vertical = first_vertical
        for q in list(via) + [p1]:
            p = pts[-1]
            if abs(p[0] - q[0]) < 0.01 or abs(p[1] - q[1]) < 0.01:
                pts.append(q)
                vertical = abs(p[0] - q[0]) < 0.01
            else:
                corner = (p[0], q[1]) if vertical else (q[0], p[1])
                pts += [corner, q]
                vertical = abs(corner[0] - q[0]) < 0.01
        # quitar puntos repetidos
        clean = [pts[0]]
        for p in pts[1:]:
            if abs(p[0] - clean[-1][0]) > 0.01 or abs(p[1] - clean[-1][1]) > 0.01:
                clean.append(p)
        self.edges.append(dict(a=a, b=b, pts=clean, label=label, dashed=dashed, arrow=arrow,
                               color=color, lpos=lpos, kind=kind, width=width))

    def line(self, a, b, pa=None, pb=None, kind='assoc', dashed=False, arrow=False, label=None, color=None, width=None, head='open', lpos=None):
        """Linea recta libre (casos de uso: asociaciones, include/extend, generalizacion)."""
        self.edges.append(dict(a=a, b=b, pts=[pa, pb], label=label, dashed=dashed, arrow=arrow,
                               color=color, lpos=lpos, kind=kind, width=width, head=head))

    # ---------- render ----------
    def _node_svg(self, n):
        x, y, w, h, k = n['x'], n['y'], n['w'], n['h'], n['kind']
        s = n['size']
        out = []
        def text(lines, color, bold=False, italic=False, size=s, cx=x, cy=y, lh=1.25, anchor='middle'):
            total = (len(lines) - 1) * size * lh
            for i, ln in enumerate(lines):
                ty = cy - total / 2 + i * size * lh + size * 0.35
                out.append(f'<text x="{cx:.1f}" y="{ty:.1f}" font-size="{size}" fill="{color}" text-anchor="{anchor}"'
                           f'{" font-weight=\"bold\"" if bold else ""}{" font-style=\"italic\"" if italic else ""}>{escape(ln)}</text>')
        if k in ('user', 'sys'):
            fill, stroke, tc = (C['sys'], C['sys_stroke'], C['sys_text']) if k == 'sys' else (C['user'], C['user_stroke'], C['user_text'])
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h:.1f}" rx="10" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>')
            text(n['lines'], tc)
        elif k == 'term':
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h}" rx="{h/2}" fill="#ffffff" stroke="{C["term"]}" stroke-width="1.8"/>')
            text(n['lines'], '#2a2a2a', bold=True)
        elif k == 'diamond':
            pts = f'{x},{y-h/2} {x+w/2},{y} {x},{y+h/2} {x-w/2},{y}'
            out.append(f'<polygon points="{pts}" fill="#ffffff" stroke="{C["diamond"]}" stroke-width="2.2"/>')
            text(n['lines'], '#2a2a2a', bold=True, lh=1.22)
        elif k == 'note':
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h:.1f}" rx="6" fill="{C["note"]}" stroke="{C["note_stroke"]}" stroke-width="1.3" stroke-dasharray="5 4"/>')
            text(n['lines'], C['note_text'], italic=True, lh=1.3)
        elif k == 'status':
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h}" rx="10" fill="{n["color"]}" stroke="none"/>')
            text(n['lines'], '#ffffff', bold=True)
        elif k == 'entity':
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h:.1f}" fill="#ffffff" stroke="{C["actor"]}" stroke-width="2.2"/>')
            text(n['lines'], '#2a1650', bold=True)
        elif k == 'process':
            band = 30
            out.append(f'<rect x="{x-w/2:.1f}" y="{y-h/2:.1f}" width="{w}" height="{h:.1f}" rx="12" fill="{C["sys"]}" stroke="{C["sys_stroke"]}" stroke-width="1.4"/>')
            out.append(f'<path d="M {x-w/2:.1f} {y-h/2+band:.1f} L {x+w/2:.1f} {y-h/2+band:.1f}" stroke="#7d62b3" stroke-width="1.4"/>')
            out.append(f'<text x="{x:.1f}" y="{y-h/2+21:.1f}" font-size="15" font-weight="bold" fill="#d9cff0" text-anchor="middle">{escape(n["num"])}</text>')
            text(n['lines'], '#ffffff', cy=y + band / 2)
        elif k == 'store':
            ry = 9
            x0, x1, t, b = x - w / 2, x + w / 2, y - h / 2 + ry, y + h / 2 - ry
            out.append(f'<path d="M {x0:.1f} {t:.1f} L {x0:.1f} {b:.1f} A {w/2:.1f} {ry} 0 0 0 {x1:.1f} {b:.1f} L {x1:.1f} {t:.1f}" fill="{C["note"]}" stroke="#8a8a90" stroke-width="1.8"/>')
            out.append(f'<ellipse cx="{x:.1f}" cy="{t:.1f}" rx="{w/2:.1f}" ry="{ry}" fill="#fafafb" stroke="#8a8a90" stroke-width="1.8"/>')
            text(n['lines'], '#2f2f2f', bold=True, cy=y + 4)
        elif k == 'conn':
            out.append(f'<circle cx="{x}" cy="{y}" r="{w/2}" fill="#ffffff" stroke="{C["term"]}" stroke-width="2.2"/>')
            text(n['lines'], '#2a2a2a', bold=True)
        elif k == 'usecase':
            out.append(f'<ellipse cx="{x}" cy="{y}" rx="{w/2}" ry="{h/2}" fill="{C["uc_fill"]}" stroke="{C["uc_stroke"]}" stroke-width="1.8"/>')
            text(n['lines'], '#2a1650', lh=1.2)
        return '\n'.join(out)

    def _edge_svg(self, e):
        pts = e['pts']
        k = e['kind']
        if k == 'df':
            col = e['color'] or ('#7a7a80' if e['dashed'] else C['line'])
            wd = e['width'] or 1.9
            dash = ' stroke-dasharray="8 6"' if e['dashed'] else ''
            mk = ' marker-end="url(#arrowDf)"' if not e['dashed'] else ' marker-end="url(#arrowDfOut)"'
        elif k in ('flow',):
            col = e['color'] or (C['loop'] if e['dashed'] else C['line'])
            wd = e['width'] or (2.4 if e['dashed'] else 2.2)
            dash = ' stroke-dasharray="9 7"' if e['dashed'] else ''
            mk = ' marker-end="url(#arrowLoop)"' if (e['arrow'] and e['dashed']) else (' marker-end="url(#arrow)"' if e['arrow'] else '')
        else:
            col = e['color'] or C['uc_line']
            wd = e['width'] or 1.8
            dash = ' stroke-dasharray="8 6"' if e['dashed'] else ''
            mk = {'open': ' marker-end="url(#open)"', 'gen': ' marker-end="url(#gen)"'}.get(e.get('head'), '') if e['arrow'] else ''
        if k == 'note':
            col, wd, dash, mk = '#a9a9ae', 1.4, ' stroke-dasharray="3 4"', ''
        d = 'M ' + ' L '.join(f'{p[0]:.1f} {p[1]:.1f}' for p in pts)
        out = [f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{wd}"{dash}{mk} stroke-linejoin="round"/>']
        if e['label']:
            if e['lpos']:
                lx, ly = e['lpos']
            else:
                (x0, y0), (x1, y1) = pts[0], pts[1]
                L = math.hypot(x1 - x0, y1 - y0) or 1
                ux, uy = (x1 - x0) / L, (y1 - y0) / L
                off = min(34, L / 2)
                lx, ly = x0 + ux * off, y0 + uy * off
                if abs(uy) > abs(ux):
                    lx -= 24
                else:
                    ly -= 16
            size = 17 if k == 'flow' else 15
            bold = k == 'flow'
            if k == 'df':
                col = '#2b2b2b'
            tw_ = text_w(e['label'], size, bold=bold)
            out.append(f'<rect x="{lx-tw_/2-5:.1f}" y="{ly-size*0.8:.1f}" width="{tw_+10:.1f}" height="{size*1.25:.1f}" fill="#ffffff"/>')
            out.append(f'<text x="{lx:.1f}" y="{ly+size*0.3:.1f}" font-size="{size}" text-anchor="middle" fill="{col if k != "flow" else C["label"]}"'
                       f'{" font-weight=\"bold\"" if bold else ""}>{escape(e["label"])}</text>')
        return '\n'.join(out)

    def legend(self, x, y, items, w=None, size=15):
        """items: lista de (tipo, texto). tipo: 'user', 'sys', 'note' o un color."""
        w = w or max(text_w(t, size) for _, t in items) + 70
        h = len(items) * 30 + 16
        out = [f'<rect x="{x}" y="{y}" width="{w:.0f}" height="{h}" rx="7" fill="#fbfbfb" stroke="#dadada" stroke-width="1.2"/>']
        for i, (k, t) in enumerate(items):
            cy = y + 23 + i * 30
            if k == 'user':
                sw = f'fill="{C["user"]}" stroke="#9b86c9"'
            elif k == 'sys':
                sw = f'fill="{C["sys"]}" stroke="{C["sys_stroke"]}"'
            elif k == 'note':
                sw = f'fill="{C["note"]}" stroke="{C["note_stroke"]}" stroke-dasharray="3 2"'
            elif k == 'conn':
                out.append(f'<circle cx="{x+24}" cy="{cy}" r="10" fill="#ffffff" stroke="{C["term"]}" stroke-width="1.6"/>')
                out.append(f'<text x="{x+24}" y="{cy+4.5}" font-size="12" font-weight="bold" fill="#2a2a2a" text-anchor="middle">A</text>')
                out.append(f'<text x="{x+44}" y="{cy+5}" font-size="{size}" fill="#444">{escape(t)}</text>')
                continue
            else:
                sw = f'fill="{k}" stroke="none"'
            out.append(f'<rect x="{x+14}" y="{cy-10}" width="20" height="20" rx="4" {sw} stroke-width="1.2"/>')
            out.append(f'<text x="{x+44}" y="{cy+5}" font-size="{size}" fill="#444">{escape(t)}</text>')
        self.extra.append('\n'.join(out))
        return (x, y, w, h)

    def svg(self):
        defs = f'''<defs>
<marker id="arrow" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="{C['line']}"/></marker>
<marker id="arrowLoop" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="13" markerHeight="13" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="{C['loop']}"/></marker>
<marker id="arrowDf" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="{C['line']}"/></marker>
<marker id="arrowDfOut" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="#7a7a80"/></marker>
<marker id="open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="13" markerHeight="13" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12" fill="none" stroke="{C['uc_line']}" stroke-width="1.6"/></marker>
<marker id="gen" viewBox="0 0 16 16" refX="15" refY="8" markerWidth="20" markerHeight="20" markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L15,8 L1,15 z" fill="#ffffff" stroke="{C['actor']}" stroke-width="1.6"/></marker>
</defs>'''
        body = [f'<rect width="{self.W}" height="{self.H}" fill="#ffffff"/>']
        if self.title and not __import__('os').environ.get('SIN_TITULO'):
            if getattr(self, 'title_x', 'center') == 'left':
                body.append(f'<text x="40" y="{self.title_size + 22}" font-size="{self.title_size}" font-weight="bold" fill="{self.title_color}">{escape(self.title)}</text>')
            else:
                body.append(f'<text x="{self.W/2}" y="{self.title_size + 18}" font-size="{self.title_size}" font-weight="bold" fill="{self.title_color}" text-anchor="middle">{escape(self.title)}</text>')
        body += self.extra
        body += [self._edge_svg(e) for e in self.edges if e['kind'] == 'note']
        body += [self._edge_svg(e) for e in self.edges if e['kind'] != 'note']
        body += [self._node_svg(n) for n in self.nodes.values()]
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.W}" height="{self.H}" viewBox="0 0 {self.W} {self.H}" '
                f'font-family="Arial, Helvetica, sans-serif">\n{defs}\n' + '\n'.join(body) + '\n</svg>')

    # ---------- verificacion ----------
    def _inside(self, n, px, py, margin=1.5):
        x, y, w, h = n['x'], n['y'], n['w'], n['h']
        k = n['kind']
        if k in ('diamond', 'rel'):
            return abs(px - x) / (w / 2) + abs(py - y) / (h / 2) < 1 - margin / min(w, h) * 2
        if k == 'conn':
            return (px - x) ** 2 + (py - y) ** 2 < (w / 2 - margin) ** 2
        if k in ('usecase', 'attr'):
            return ((px - x) / (w / 2 - margin)) ** 2 + ((py - y) / (h / 2 - margin)) ** 2 < 1
        return abs(px - x) < w / 2 - margin and abs(py - y) < h / 2 - margin

    def check(self, extra_boxes=()):
        problems = []
        segs = []
        for i, e in enumerate(self.edges):
            pts = e['pts']
            for j in range(len(pts) - 1):
                segs.append((i, pts[j], pts[j + 1]))
            # atraviesa nodos
            for name, n in self.nodes.items():
                for j in range(len(pts) - 1):
                    (x0, y0), (x1, y1) = pts[j], pts[j + 1]
                    L = math.hypot(x1 - x0, y1 - y0)
                    steps = max(2, int(L / 3))
                    for s in range(1, steps):
                        t = s / steps
                        px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                        if self._inside(n, px, py):
                            problems.append(f'flecha {e["a"]}->{e["b"]} atraviesa {name}')
                            break
                    else:
                        continue
                    break
            for (bx0, by0, bx1, by1, bname) in extra_boxes:
                if bname in (e['a'], e['b']):
                    continue
                for j in range(len(pts) - 1):
                    (x0, y0), (x1, y1) = pts[j], pts[j + 1]
                    for s in range(1, 60):
                        t = s / 60
                        px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                        if bx0 < px < bx1 and by0 < py < by1:
                            problems.append(f'flecha {e["a"]}->{e["b"]} atraviesa {bname}'); break
        # cruces entre flechas
        def cross(p, q, r, s):
            def orient(a, b, c):
                v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
                return 0 if abs(v) < 1e-6 else (1 if v > 0 else -1)
            o1, o2, o3, o4 = orient(p, q, r), orient(p, q, s), orient(r, s, p), orient(r, s, q)
            return o1 * o2 < 0 and o3 * o4 < 0
        for a in range(len(segs)):
            for b in range(a + 1, len(segs)):
                i, p, q = segs[a]
                j, r, s = segs[b]
                if i != j and cross(p, q, r, s):
                    problems.append(f'cruce: {self.edges[i]["a"]}->{self.edges[i]["b"]} con {self.edges[j]["a"]}->{self.edges[j]["b"]}')
        # nodos que se superponen
        names = list(self.nodes)
        for a in range(len(names)):
            for b in range(a + 1, len(names)):
                n, m = self.nodes[names[a]], self.nodes[names[b]]
                if abs(n['x'] - m['x']) < (n['w'] + m['w']) / 2 + 8 and abs(n['y'] - m['y']) < (n['h'] + m['h']) / 2 + 8:
                    problems.append(f'superposicion: {names[a]} / {names[b]}')
        return sorted(set(problems))

    def save(self, path_noext, scale=2, extra_boxes=()):
        svg_path = path_noext + '.svg'
        with open(svg_path, 'w', encoding='utf-8') as f:
            f.write(self.svg())
        png = render(svg_path, path_noext + '.png', self.W, self.H, scale)
        probs = self.check(extra_boxes)
        print(os.path.basename(path_noext), f'{self.W}x{self.H}', 'OK' if not probs else '')
        for p in probs:
            print('   !', p)
        return png

CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'
def render(svg_path, png_path, W, H, scale=2):
    svg_abs = os.path.abspath(svg_path).replace('\\', '/')
    html = os.path.abspath(svg_path)[:-4] + '.render.html'
    with open(html, 'w', encoding='utf-8') as f:
        f.write(f'<html><body style="margin:0;background:#fff"><img src="file:///{svg_abs}" width="{W}" height="{H}" style="display:block"></body></html>')
    subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files',
                    f'--force-device-scale-factor={scale}', f'--window-size={W},{H}',
                    f'--screenshot={os.path.abspath(png_path)}', 'file:///' + html.replace('\\', '/')],
                   check=True, capture_output=True, timeout=120)
    os.remove(html)
    return png_path
