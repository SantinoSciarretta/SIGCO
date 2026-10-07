# Generador de diagramas de secuencia con la misma paleta que los diagramas de flujo.
#
# Los participantes van arriba y se repiten abajo, cada mensaje lleva un numero en
# su origen, las llamadas son flechas llenas y las respuestas, punteadas. Los
# bloques "alt" y "opt" marcan los caminos alternativos, como en UML.
from html import escape
from lucid import text_w, wrap, render

ESTILO = {
    'ui':   dict(fill='#e8e0f6', stroke='#b7a3e3', text='#2a1650', sub='#5b4a85'),
    'srv':  dict(fill='#4b2a83', stroke='#3c1f6e', text='#ffffff', sub='#d9cff0'),
    'data': dict(fill='#f1f1f3', stroke='#9a9aa0', text='#2f2f2f', sub='#666670'),
}
LINEA, RETORNO, VIDA = '#3a3a3a', '#55555a', '#b3b3b8'
MARCO, MARCO_TAG = '#8a7fb0', '#ede8f7'
BW, BH = 176, 58          # caja de cada participante
FS = 15                   # tamano del texto de los mensajes


class Secuencia:
    def __init__(self, titulo, participantes, separacion=220, x0=150):
        """participantes: lista de (clave, nombre, subtitulo, tipo); tipo: actor, ui, srv, data.
        separacion: un numero o una lista con la distancia entre cada par vecino."""
        self.titulo = titulo
        self.p = {}
        self.orden = []
        x = x0
        seps = separacion if isinstance(separacion, list) else [separacion] * (len(participantes) - 1)
        for i, (k, nombre, sub, tipo) in enumerate(participantes):
            ancho = max(BW, text_w(nombre, 15, bold=True) + 28)
            self.p[k] = dict(x=x, nombre=nombre, sub=sub, tipo=tipo, w=ancho)
            self.orden.append(k)
            if i < len(seps):
                x += seps[i]
        self.W = int(x + 210)
        self.eventos = []

    # ---- eventos ----
    def msg(self, a, b, texto, ret=False):
        self.eventos.append(('msg', a, b, texto, ret))

    def self_(self, a, texto):
        self.eventos.append(('self', a, texto))

    def frame(self, tipo, cond, desde, hasta):
        self.eventos.append(('frame', tipo, cond, desde, hasta))

    def otro(self, cond):
        self.eventos.append(('else', cond))

    def fin(self):
        self.eventos.append(('end',))

    # ---- dibujo ----
    def _participante(self, k, y, o):
        p = self.p[k]
        x = p['x']
        if p['tipo'] == 'actor':
            c = '#3d1466'
            o.append(f'<g stroke="{c}" stroke-width="2.4" fill="none" stroke-linecap="round">'
                     f'<circle cx="{x}" cy="{y+9}" r="9" fill="#fff"/><line x1="{x}" y1="{y+18}" x2="{x}" y2="{y+38}"/>'
                     f'<line x1="{x-14}" y1="{y+25}" x2="{x+14}" y2="{y+25}"/><line x1="{x}" y1="{y+38}" x2="{x-11}" y2="{y+54}"/>'
                     f'<line x1="{x}" y1="{y+38}" x2="{x+11}" y2="{y+54}"/></g>')
            o.append(f'<text x="{x}" y="{y+74}" font-size="15" font-weight="bold" fill="{c}" text-anchor="middle">{escape(p["nombre"])}</text>')
            return
        e = ESTILO[p['tipo']]
        bw = p['w']
        o.append(f'<rect x="{x-bw/2}" y="{y}" width="{bw}" height="{BH}" rx="8" fill="{e["fill"]}" stroke="{e["stroke"]}" stroke-width="1.4"/>')
        if p['sub']:
            o.append(f'<text x="{x}" y="{y+25}" font-size="15" font-weight="bold" fill="{e["text"]}" text-anchor="middle">{escape(p["nombre"])}</text>')
            o.append(f'<text x="{x}" y="{y+44}" font-size="12.5" fill="{e["sub"]}" text-anchor="middle">{escape(p["sub"])}</text>')
        else:
            o.append(f'<text x="{x}" y="{y+34}" font-size="15" font-weight="bold" fill="{e["text"]}" text-anchor="middle">{escape(p["nombre"])}</text>')

    def _etiqueta(self, o, x, y, texto, anchor='middle', size=FS, color='#2b2b2b'):
        w = text_w(texto, size)
        x0 = x - w / 2 if anchor == 'middle' else x
        o.append(f'<rect x="{x0-4:.1f}" y="{y-size+1:.1f}" width="{w+8:.1f}" height="{size+5}" fill="#ffffff"/>')
        o.append(f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{color}" text-anchor="{anchor}">{escape(texto)}</text>')

    def _acomodar(self):
        """Separa los participantes lo necesario para que cada etiqueta entre en su flecha."""
        idx = {k: i for i, k in enumerate(self.orden)}
        xs = [self.p[k]['x'] for k in self.orden]
        gaps = [xs[i + 1] - xs[i] for i in range(len(xs) - 1)]
        for i in range(len(gaps)):
            a, b = self.p[self.orden[i]], self.p[self.orden[i + 1]]
            gaps[i] = max(gaps[i], (a['w'] + b['w']) / 2 + 34)
        for _ in range(3):
            for ev in self.eventos:
                if ev[0] != 'msg':
                    continue
                i, j = sorted((idx[ev[1]], idx[ev[2]]))
                falta = min(max(text_w(ev[3].partition(chr(10))[0], FS), text_w(ev[3].partition(chr(10))[2], 12.5)), 260) + 56 - sum(gaps[i:j])
                if falta > 0:
                    for g in range(i, j):
                        gaps[g] += falta / (j - i)
        x = xs[0]
        for i, k in enumerate(self.orden):
            self.p[k]['x'] = x
            if i < len(gaps):
                x += gaps[i]
        self.gaps = gaps
        self.W = int(x + 210)

    def _ancho_propio(self, k):
        """Ancho de texto para una llamada a si mismo, sin pisar la vida de al lado."""
        i = self.orden.index(k)
        if i == len(self.orden) - 1:
            return 300
        return max(190, min(300, self.gaps[i] - 80))

    def svg(self):
        self._acomodar()
        top_cajas = 118
        o_fondo, o_vidas, o_marcos, o_msgs = [], [], [], []
        y = top_cajas + BH + 46
        n = 0
        pila = []
        for ev in self.eventos:
            if ev[0] == 'msg':
                _, a, b, texto, ret = ev
                xa, xb = self.p[a]['x'], self.p[b]['x']
                principal, _, tecnico = texto.partition(chr(10))
                lineas = wrap(principal, FS, max(150, abs(xb - xa) - 44))
                if tecnico:
                    lineas.append('§' + tecnico)
                y += 44 + (len(lineas) - 1) * 19
                dirx = 1 if xb > xa else -1
                n += 1
                col = RETORNO if ret else LINEA
                dash = ' stroke-dasharray="7 5"' if ret else ''
                mk = 'url(#ret)' if ret else 'url(#call)'
                o_msgs.append(f'<line x1="{xa + dirx*10}" y1="{y}" x2="{xb - dirx*2}" y2="{y}" stroke="{col}" stroke-width="1.8"{dash} marker-end="{mk}"/>')
                for i, ln in enumerate(lineas):
                    yy = y - 9 - (len(lineas) - 1 - i) * 19
                    if ln.startswith('§'):
                        self._etiqueta(o_msgs, (xa + xb) / 2, yy, ln[1:], size=12.5, color='#6a6a72')
                    else:
                        self._etiqueta(o_msgs, (xa + xb) / 2, yy, ln)
                medio = (xa + xb) / 2
                ancho = max(text_w(ln[1:], 12.5) if ln.startswith('§') else text_w(ln, FS) for ln in lineas) / 2
                for f in pila:
                    f['x0'] = min(f['x0'], min(xa, xb, medio - ancho) - 30)
                    f['x1'] = max(f['x1'], max(xa, xb, medio + ancho) + 30)
                o_msgs.append(f'<circle cx="{xa}" cy="{y}" r="10" fill="#2f2f2f"/>'
                              f'<text x="{xa}" y="{y+4.2}" font-size="11.5" font-weight="bold" fill="#fff" text-anchor="middle">{n}</text>')
            elif ev[0] == 'self':
                _, a, texto = ev
                y += 40
                x = self.p[a]['x']
                n += 1
                lineas = wrap(texto, FS, self._ancho_propio(a))
                alto = max(30, len(lineas) * 19 + 8)
                o_msgs.append(f'<path d="M {x+10} {y} L {x+42} {y} L {x+42} {y+alto} L {x+3} {y+alto}" fill="none" stroke="{LINEA}" stroke-width="1.8" marker-end="url(#call)"/>')
                for i, ln in enumerate(lineas):
                    self._etiqueta(o_msgs, x + 52, y + 13 + i * 19, ln, anchor='start')
                derecha = x + 52 + max(text_w(ln, FS) for ln in lineas)
                for f in pila:
                    f['x1'] = max(f['x1'], derecha + 24)
                    f['x0'] = min(f['x0'], x - 40)
                o_msgs.append(f'<circle cx="{x}" cy="{y}" r="10" fill="#2f2f2f"/>'
                              f'<text x="{x}" y="{y+4.2}" font-size="11.5" font-weight="bold" fill="#fff" text-anchor="middle">{n}</text>')
                y += alto - 6
            elif ev[0] == 'frame':
                _, tipo, cond, desde, hasta = ev
                y += 30
                prof = len(pila)
                pad = 60 - 16 * prof
                x0 = self.p[desde]['x'] - pad
                x1 = self.p[hasta]['x'] + pad
                pila.append(dict(tipo=tipo, y=y, x0=x0, x1=x1, sep=[(y, cond)]))
                y += 22
            elif ev[0] == 'else':
                y += 22
                pila[-1]['sep'].append((y, ev[1]))
                y += 18
            elif ev[0] == 'end':
                y += 26
                f = pila.pop()
                for g in pila:
                    g['x0'] = min(g['x0'], f['x0'] - 16)
                    g['x1'] = max(g['x1'], f['x1'] + 16)
                o_marcos.append(f'<rect x="{f["x0"]}" y="{f["y"]}" width="{f["x1"]-f["x0"]}" height="{y-f["y"]}" fill="none" stroke="{MARCO}" stroke-width="1.5" stroke-dasharray="6 4"/>')
                tw = text_w(f['tipo'], 13, True) + 18
                o_marcos.append(f'<path d="M {f["x0"]} {f["y"]} h {tw} v 14 l -8 8 h {-tw+8} z" fill="{MARCO_TAG}" stroke="{MARCO}" stroke-width="1.3"/>'
                                f'<text x="{f["x0"]+8}" y="{f["y"]+16}" font-size="13" font-weight="bold" fill="#3d1a78">{f["tipo"]}</text>')
                for i, (sy, cond) in enumerate(f['sep']):
                    if i:
                        o_marcos.append(f'<line x1="{f["x0"]}" y1="{sy}" x2="{f["x1"]}" y2="{sy}" stroke="{MARCO}" stroke-width="1.3" stroke-dasharray="6 4"/>')
                    cx = f['x0'] + tw + 12
                    cw = text_w('[' + cond + ']', 14, True)
                    o_marcos.append(f'<rect x="{cx-4}" y="{sy+3}" width="{cw+8}" height="19" fill="#ffffff"/>'
                                    f'<text x="{cx}" y="{sy+17}" font-size="14" font-style="italic" font-weight="bold" fill="#4b3b7d">[{escape(cond)}]</text>')
        y += 50
        abajo = y
        derecha = self.p[self.orden[-1]]['x'] + self.p[self.orden[-1]]['w'] / 2 + 30
        for linea in o_marcos + o_msgs:
            for trozo in linea.split('x="')[1:] + linea.split('x2="')[1:]:
                try:
                    derecha = max(derecha, float(trozo.split('"')[0]))
                except ValueError:
                    pass
        for linea in o_msgs:
            if 'text-anchor="start"' in linea:
                xs = float(linea.split('<text x="')[1].split('"')[0])
                txt = linea.split('>')[-2].split('<')[0]
                derecha = max(derecha, xs + text_w(txt, FS) + 24)
        for linea in o_marcos:
            if linea.startswith('<rect') and 'stroke-dasharray' in linea:
                x0 = float(linea.split('x="')[1].split('"')[0])
                w = float(linea.split('width="')[1].split('"')[0])
                derecha = max(derecha, x0 + w + 20)
        self.W = int(max(derecha, 900))
        H = int(abajo + BH + 40)
        for k in self.orden:
            x = self.p[k]['x']
            y0 = top_cajas + (80 if self.p[k]['tipo'] == 'actor' else BH)
            o_vidas.append(f'<line x1="{x}" y1="{y0}" x2="{x}" y2="{abajo}" stroke="{VIDA}" stroke-width="1.5"/>')
        o_cajas = []
        for k in self.orden:
            self._participante(k, top_cajas, o_cajas)
            self._participante(k, abajo, o_cajas)
        # leyenda
        ley = [('ui', 'Pantalla (frontend)'), ('srv', 'Servidor (backend)'), ('data', 'Datos y archivos')]
        lx = self.W - 230
        o_fondo.append(f'<rect x="{lx}" y="14" width="214" height="{len(ley)*24+12}" rx="7" fill="#fbfbfb" stroke="#dadada"/>')
        for i, (t, txt) in enumerate(ley):
            e = ESTILO[t]
            o_fondo.append(f'<rect x="{lx+12}" y="{22+i*24}" width="16" height="16" rx="3" fill="{e["fill"]}" stroke="{e["stroke"]}"/>'
                           f'<text x="{lx+36}" y="{35+i*24}" font-size="13" fill="#444">{txt}</text>')
        defs = (f'<defs><marker id="call" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L12,6 L0,12 z" fill="{LINEA}"/></marker>'
                f'<marker id="ret" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" markerUnits="userSpaceOnUse" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="{RETORNO}" stroke-width="1.6"/></marker></defs>')
        titulo = '' if __import__('os').environ.get('SIN_TITULO') else f'<text x="40" y="52" font-size="30" font-weight="bold" fill="#2f2f2f">{escape(self.titulo)}</text>'
        cuerpo = '\n'.join([f'<rect width="{self.W}" height="{H}" fill="#ffffff"/>', titulo] + o_fondo + o_vidas + o_marcos + o_cajas + o_msgs)
        self.H = H
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.W}" height="{H}" viewBox="0 0 {self.W} {H}" '
                f'font-family="Arial, Helvetica, sans-serif">{defs}\n{cuerpo}</svg>')

    def save(self, nombre):
        s = self.svg()
        with open(nombre + '.svg', 'w', encoding='utf-8') as f:
            f.write(s)
        render(nombre + '.svg', nombre + '.png', self.W, self.H, 2)
        print(nombre, f'{self.W}x{self.H}')
