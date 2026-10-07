# Diagrama relacional de SIGCO, dividido por areas, para el informe.
# Uso: python relacional.py [numero ...]   (sin argumentos genera todos)
#
# Lee el esquema REAL de la base (PostgreSQL local, todas las migraciones
# aplicadas): las tablas, columnas, tipos, claves primarias, claves foraneas e
# indices unicos salen de ahi y no de una lista escrita a mano.
#
# Cada linea sale de la fila de la clave foranea y llega a la fila de la clave
# primaria que referencia. Las lineas van por carriles verticales entre las
# columnas de tablas, uno por linea, y el orden de los carriles se elige probando
# todas las combinaciones hasta encontrar la que no cruza lineas.
import itertools
import os
import subprocess
import sys
from html import escape
from lucid import text_w, render

PSQL = r'C:\Program Files\PostgreSQL\17\bin\psql.exe'
HEAD, ROW = 38, 27
MARG, STEP = 42, 17            # margen de un carril a la tabla y separacion entre carriles
LINEA = '#4a3d78'


# ----------------------------------------------------------------------
#  Esquema
# ----------------------------------------------------------------------

def consulta(sql):
    r = subprocess.run([PSQL, '-U', 'postgres', '-h', 'localhost', '-d', 'sigco_dev', '-At', '-F', '|', '-c', sql],
                       capture_output=True, text=True, encoding='utf-8',
                       env={**os.environ, 'PGPASSWORD': 'sigco2026'})
    if r.returncode:
        raise SystemExit(r.stderr)
    return [ln.split('|') for ln in r.stdout.splitlines() if ln]


def tipo(data_type, largo, precision, escala):
    if data_type == 'character varying':
        return f'varchar({largo})'
    if data_type == 'numeric':
        return f'numeric({precision},{escala})'
    if data_type.startswith('timestamp'):
        return 'timestamp'
    return data_type


def esquema():
    cols = {}
    for t, c, dt, largo, prec, esc, nulo, _ in consulta(
            "select c.table_name, c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, "
            "c.numeric_scale, c.is_nullable, c.ordinal_position from information_schema.columns c "
            "join information_schema.tables t on t.table_name = c.table_name and t.table_schema = c.table_schema "
            "where c.table_schema = 'public' and t.table_type = 'BASE TABLE' and c.table_name <> 'flyway_schema_history' "
            "order by c.table_name, c.ordinal_position"):
        cols.setdefault(t, []).append(dict(nombre=c, tipo=tipo(dt, largo, prec, esc), nulo=nulo == 'YES'))
    pks, fks = {}, []
    for t, kind, c, pt, pc in consulta(
            "select tc.table_name, tc.constraint_type, kcu.column_name, ccu.table_name, ccu.column_name "
            "from information_schema.table_constraints tc join information_schema.key_column_usage kcu "
            "on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema "
            "left join information_schema.constraint_column_usage ccu on tc.constraint_type = 'FOREIGN KEY' "
            "and ccu.constraint_name = tc.constraint_name where tc.table_schema = 'public' "
            "and tc.constraint_type in ('PRIMARY KEY', 'FOREIGN KEY')"):
        if kind == 'PRIMARY KEY':
            pks.setdefault(t, set()).add(c)
        else:
            fks.append((t, c, pt, pc))
    unicos = set()
    for t, definicion in consulta("select tablename, indexdef from pg_indexes where schemaname = 'public' "
                                  "and indexdef like 'CREATE UNIQUE%' and indexname not like '%pkey'"):
        dentro = definicion.split('(', 1)[1].split(')')[0]
        if ',' not in dentro and 'lower' not in definicion and not definicion.split(' ')[3].startswith('pk_'):
            unicos.add((t, dentro.strip()))
    return cols, pks, fks, unicos


# ----------------------------------------------------------------------
#  Tablas
# ----------------------------------------------------------------------

class Tabla:
    def __init__(self, nombre, cols, pks, fkcols, unicos, resumen=False, nota=None):
        self.nombre, self.resumen, self.nota = nombre, resumen, nota
        filas = [c for c in cols if c['nombre'] in pks] if resumen else cols
        self.filas = []
        for c in filas:
            marcas = []
            if c['nombre'] in pks:
                marcas.append('PK')
            if c['nombre'] in fkcols:
                marcas.append('FK')
            if (nombre, c['nombre']) in unicos:
                marcas.append('UQ')
            self.filas.append(dict(c, marcas=marcas))
        self.badge_w = 34 + 30 * (max((len(f['marcas']) for f in self.filas), default=1) - 1)
        ancho = max(self.badge_w + 14 + text_w(f['nombre'], 15, bold='PK' in f['marcas']) + 26 + text_w(f['tipo'], 13) + 14
                    for f in self.filas)
        titulo = text_w(nombre, 17, bold=True) + 40 + (text_w(nota, 12.5) + 10 if nota else 0)
        self.w = max(ancho, titulo, 200)
        self.h = HEAD + len(self.filas) * ROW
        self.x = self.y = 0

    def fila_y(self, col):
        for i, f in enumerate(self.filas):
            if f['nombre'] == col:
                return self.y + HEAD + i * ROW + ROW / 2
        raise KeyError(f'{self.nombre}.{col}')

    def svg(self):
        x, y, w = self.x, self.y, self.w
        cab = '#9a9aa2' if self.resumen else '#4b2a83'
        o = [f'<rect x="{x}" y="{y}" width="{w}" height="{self.h}" rx="7" fill="#ffffff" stroke="{"#9a9aa2" if self.resumen else "#3d1a78"}" stroke-width="1.6"/>',
             f'<path d="M {x} {y+HEAD} L {x} {y+7} Q {x} {y} {x+7} {y} L {x+w-7} {y} Q {x+w} {y} {x+w} {y+7} L {x+w} {y+HEAD} Z" fill="{cab}"/>',
             f'<text x="{x+14}" y="{y+25}" font-size="17" font-weight="bold" fill="#ffffff">{escape(self.nombre)}</text>']
        if self.nota:
            o.append(f'<text x="{x+w-12}" y="{y+24}" font-size="12.5" fill="#f0f0f2" text-anchor="end">{escape(self.nota)}</text>')
        for i, f in enumerate(self.filas):
            fy = y + HEAD + i * ROW
            if i % 2:
                o.append(f'<rect x="{x+1}" y="{fy}" width="{w-2}" height="{ROW}" fill="#f7f4fc"/>')
            if i:
                o.append(f'<line x1="{x}" y1="{fy}" x2="{x+w}" y2="{fy}" stroke="#ebe6f4" stroke-width="1"/>')
            bx = x + 8
            for m in f['marcas']:
                estilo = {'PK': ('#4b2a83', '#ffffff', '#4b2a83'), 'FK': ('#e8e0f6', '#4b2a83', '#b7a3e3'),
                          'UQ': ('#ffffff', '#6a6a72', '#a9a9b0')}[m]
                o.append(f'<rect x="{bx}" y="{fy+6}" width="26" height="15" rx="3" fill="{estilo[0]}" stroke="{estilo[2]}" stroke-width="1"/>'
                         f'<text x="{bx+13}" y="{fy+17.5}" font-size="10.5" font-weight="bold" fill="{estilo[1]}" text-anchor="middle">{m}</text>')
                bx += 30
            negrita = ' font-weight="bold"' if 'PK' in f['marcas'] else ''
            cursiva = ' font-style="italic"' if f['nulo'] else ''
            color = '#6a6a72' if f['nulo'] else '#1f1f24'
            o.append(f'<text x="{x+self.badge_w+14}" y="{fy+18.5}" font-size="15" fill="{color}"{negrita}{cursiva}>{escape(f["nombre"])}</text>')
            o.append(f'<text x="{x+w-12}" y="{fy+18}" font-size="13" fill="#8a8a92" text-anchor="end">{escape(f["tipo"])}</text>')
        return '\n'.join(o)


# ----------------------------------------------------------------------
#  Diagrama de un area
# ----------------------------------------------------------------------

class Area:
    def __init__(self, titulo, columnas, lados=None, separacion=None, resumenes=None):
        """columnas: lista de columnas; cada una, lista de nombres de tabla. Un nombre que
        termina en '*' es una tabla de otra area: se dibuja solo con su clave.
        lados: {(tabla, columna_fk): 'l' o 'r'} para las referencias dentro de una misma columna.
        separacion: {tabla: espacio extra arriba}."""
        self.titulo, self.columnas = titulo, columnas
        self.lados = lados or {}
        self.separacion = separacion or {}
        self.resumenes = resumenes or {}

    def armar(self, cols, pks, fks, unicos):
        self.t = {}
        self.col_de = {}
        fk_por_tabla = {}
        for t, c, pt, pc in fks:
            fk_por_tabla.setdefault(t, set()).add(c)
        for i, columna in enumerate(self.columnas):
            for nombre in columna:
                resumen = nombre.endswith('*')
                n = nombre.rstrip('*')
                self.t[n] = Tabla(n, cols[n], pks.get(n, set()), fk_por_tabla.get(n, set()) if not resumen else set(),
                                  unicos, resumen=resumen, nota=self.resumenes.get(n) if resumen else None)
                self.col_de[n] = i
        # conexiones: solo las de tablas completas del area
        self.con = []
        for t, c, pt, pc in fks:
            if t in self.t and pt in self.t and not self.t[t].resumen:
                a, b = self.col_de[t], self.col_de[pt]
                if abs(a - b) > 1:
                    raise SystemExit(f'{t}.{c} -> {pt}: columnas no vecinas')
                if a < b:
                    lado_h, lado_p, corr = 'r', 'l', ('m', a)
                elif a > b:
                    lado_h, lado_p, corr = 'l', 'r', ('m', b)
                else:
                    lado = self.lados.get((t, c), 'l' if a == 0 else 'r')
                    lado_h = lado_p = lado
                    corr = ('m', a - 1) if lado == 'l' and a > 0 else ('m', a) if lado == 'r' and a < len(self.columnas) - 1 \
                        else ('izq',) if lado == 'l' else ('der',)
                unico = (t, c) in unicos
                nulo = next(f['nulo'] for f in cols[t] if f['nombre'] == c)
                self.con.append(dict(h=t, c=c, p=pt, pc=pc, lh=lado_h, lp=lado_p, corr=corr, unico=unico, nulo=nulo))
        # anchos de columna y de corredores
        anchos = [max(self.t[n.rstrip('*')].w for n in col) for col in self.columnas]
        cuantos = {}
        for k in self.con:
            cuantos[k['corr']] = cuantos.get(k['corr'], 0) + 1
        ancho_corr = lambda key: 2 * MARG + max(0, cuantos.get(key, 0) - 1) * STEP if cuantos.get(key) else 70
        x = 40 + (ancho_corr(('izq',)) if cuantos.get(('izq',)) else 0)
        self.xcol = []
        self.corr_x = {}
        if cuantos.get(('izq',)):
            self.corr_x[('izq',)] = (40, x)
        for i, ancho in enumerate(anchos):
            self.xcol.append(x)
            x += ancho
            if i < len(anchos) - 1:
                cw = max(ancho_corr(('m', i)), 110)
                self.corr_x[('m', i)] = (x, x + cw)
                x += cw
        if cuantos.get(('der',)):
            self.corr_x[('der',)] = (x, x + ancho_corr(('der',)))
            x += ancho_corr(('der',))
        self.W = int(max(x + 40, text_w(self.titulo, 30, bold=True) + 40 + 460))
        # posiciones verticales
        self.top = 250
        for i, col in enumerate(self.columnas):
            y = self.top
            for nombre in col:
                n = nombre.rstrip('*')
                y += self.separacion.get(n, 0)
                self.t[n].x, self.t[n].y = self.xcol[i], y
                y += self.t[n].h + 46
        self.H = int(max(t.y + t.h for t in self.t.values()) + 50)
        self.rutear()

    # ---- ruteo ----
    def _llegadas(self, grupo):
        """Puntos de llegada (y) de las lineas que entran a la misma tabla por el mismo lado."""
        k0 = grupo[0]
        tp = self.t[k0['p']]
        y_pk = tp.fila_y(k0['pc'])
        m = len(grupo)
        if m == 1:
            return [y_pk]
        # Varias llegadas al mismo lado: se reparten entre la cabecera y la fila de la clave,
        # con lugar suficiente para que sus marcas no se encimen.
        arriba, abajo = tp.y + 12, y_pk + (ROW / 2 - 5 if tp.resumen else 8)
        abajo = min(max(abajo, arriba + (m - 1) * 22), tp.y + tp.h - 12)
        return [arriba + (abajo - arriba) * j / (m - 1) for j in range(m)]

    def _camino(self, k, x_carril, y_llegada):
        th, tp = self.t[k['h']], self.t[k['p']]
        y0 = th.fila_y(k['c'])
        x0 = th.x + th.w if k['lh'] == 'r' else th.x
        x1 = tp.x + tp.w if k['lp'] == 'r' else tp.x
        return [(x0, y0), (x_carril, y0), (x_carril, y_llegada), (x1, y_llegada)]

    @staticmethod
    def _cruces(caminos):
        segs = [(i, p, q) for i, c in enumerate(caminos) for p, q in zip(c, c[1:])]
        n = 0
        for a in range(len(segs)):
            for b in range(a + 1, len(segs)):
                i, p, q = segs[a]
                j, r, s = segs[b]
                if i == j:
                    continue
                if _cruza(p, q, r, s):
                    n += 1
        return n

    def rutear(self):
        self.caminos = {}
        for key, (xa, xb) in self.corr_x.items():
            grupo = [k for k in self.con if k['corr'] == key]
            if not grupo:
                continue
            carriles = [xa + MARG + j * STEP for j in range(len(grupo))]
            # en los corredores laterales, el carril mas cercano a la tabla es el primero
            if key == ('izq',):
                carriles = [xb - MARG - j * STEP for j in range(len(grupo))]
            llegadas = {}
            for k in grupo:
                llegadas.setdefault((k['p'], k['lp']), []).append(k)
            if len(grupo) > 6:
                self._busqueda_local(grupo, carriles, llegadas)
                continue
            mejor = None
            for orden in itertools.permutations(range(len(grupo))):
                x_de = {id(grupo[i]): carriles[o] for i, o in enumerate(orden)}
                # dentro de cada grupo de llegada, se prueban todos los ordenes
                opciones = []
                for g in llegadas.values():
                    ys = self._llegadas(g)
                    opciones.append([dict(zip(map(id, perm), ys)) for perm in itertools.permutations(g)])
                for combinacion in itertools.product(*opciones):
                    y_de = {}
                    for d_ in combinacion:
                        y_de.update(d_)
                    caminos = [self._camino(k, x_de[id(k)], y_de[id(k)]) for k in grupo]
                    n = self._cruces(caminos)
                    largo = sum(abs(c[1][0] - c[0][0]) + abs(c[3][0] - c[2][0]) for c in caminos)
                    if mejor is None or (n, largo) < mejor[0]:
                        mejor = ((n, largo), caminos)
                if mejor[0][0] == 0 and len(grupo) > 6:
                    break
            for k, c in zip(grupo, mejor[1]):
                self.caminos[id(k)] = c

    def _busqueda_local(self, grupo, carriles, llegadas, intentos=12):
        """Orden de carriles y de llegadas por mejoras sucesivas (para corredores con muchas lineas)."""
        import random
        rnd = random.Random(7)
        grupos = list(llegadas.values())
        ys = [self._llegadas(g) for g in grupos]

        def costo(orden, llegada):
            x_de = {id(grupo[i]): carriles[o] for i, o in enumerate(orden)}
            y_de = {}
            for g, yy, perm in zip(grupos, ys, llegada):
                for k, j in zip(g, perm):
                    y_de[id(k)] = yy[j]
            caminos = [self._camino(k, x_de[id(k)], y_de[id(k)]) for k in grupo]
            largo = sum(abs(c[1][0] - c[0][0]) + abs(c[3][0] - c[2][0]) for c in caminos)
            return (self._cruces(caminos), largo), caminos

        mejor = None
        for intento in range(intentos):
            # arranque: carriles ordenados por la altura de la tabla de llegada (con algo de azar)
            orden = list(range(len(grupo)))
            if intento:
                rnd.shuffle(orden)
            else:
                por_y = sorted(range(len(grupo)), key=lambda i: self.t[grupo[i]['p']].y)
                orden = [0] * len(grupo)
                for pos, i in enumerate(por_y):
                    orden[i] = pos
            llegada = [list(range(len(g))) for g in grupos]
            c, cam = costo(orden, llegada)
            mejoro = True
            while mejoro:
                mejoro = False
                for i in range(len(orden)):
                    for j in range(i + 1, len(orden)):
                        orden[i], orden[j] = orden[j], orden[i]
                        c2, cam2 = costo(orden, llegada)
                        if c2 < c:
                            c, cam, mejoro = c2, cam2, True
                        else:
                            orden[i], orden[j] = orden[j], orden[i]
                for gi, perm in enumerate(llegada):
                    for i in range(len(perm)):
                        for j in range(i + 1, len(perm)):
                            perm[i], perm[j] = perm[j], perm[i]
                            c2, cam2 = costo(orden, llegada)
                            if c2 < c:
                                c, cam, mejoro = c2, cam2, True
                            else:
                                perm[i], perm[j] = perm[j], perm[i]
            if mejor is None or c < mejor[0]:
                mejor = (c, cam)
        for k, cam in zip(grupo, mejor[1]):
            self.caminos[id(k)] = cam

    # ---- dibujo ----
    def _marca(self, o, x, y, lado, tipo_):
        d = 1 if lado == 'r' else -1
        if tipo_ == 'muchos':          # cero o muchos: circulo y pata de gallo
            o.append(f'<path d="M {x} {y-8} L {x+d*14} {y} L {x} {y+8} M {x} {y} L {x+d*14} {y}" fill="none" stroke="{LINEA}" stroke-width="1.7"/>')
            o.append(f'<circle cx="{x+d*21}" cy="{y}" r="5" fill="#ffffff" stroke="{LINEA}" stroke-width="1.6"/>')
        elif tipo_ == 'uno':           # uno y solo uno
            for dx in (8, 14):
                o.append(f'<line x1="{x+d*dx}" y1="{y-7}" x2="{x+d*dx}" y2="{y+7}" stroke="{LINEA}" stroke-width="1.8"/>')
        else:                          # cero o uno
            o.append(f'<line x1="{x+d*8}" y1="{y-7}" x2="{x+d*8}" y2="{y+7}" stroke="{LINEA}" stroke-width="1.8"/>')
            o.append(f'<circle cx="{x+d*17}" cy="{y}" r="5" fill="#ffffff" stroke="{LINEA}" stroke-width="1.6"/>')

    def _leyenda(self, o):
        x, y = self.W - 420, 18
        o.append(f'<rect x="{x}" y="{y}" width="400" height="196" rx="7" fill="#fbfbfb" stroke="#dadada"/>')
        filas = [('PK', 'Clave primaria'), ('FK', 'Clave foránea'), ('UQ', 'Valor único')]
        for i, (m, t) in enumerate(filas):
            estilo = {'PK': ('#4b2a83', '#ffffff', '#4b2a83'), 'FK': ('#e8e0f6', '#4b2a83', '#b7a3e3'), 'UQ': ('#ffffff', '#6a6a72', '#a9a9b0')}[m]
            yy = y + 14 + i * 26
            o.append(f'<rect x="{x+14}" y="{yy}" width="26" height="15" rx="3" fill="{estilo[0]}" stroke="{estilo[2]}"/>'
                     f'<text x="{x+27}" y="{yy+11.5}" font-size="10.5" font-weight="bold" fill="{estilo[1]}" text-anchor="middle">{m}</text>'
                     f'<text x="{x+52}" y="{yy+12}" font-size="14" fill="#444">{t}</text>')
        o.append(f'<text x="{x+14}" y="{y+104}" font-size="14" font-style="italic" fill="#6a6a72">en cursiva</text>'
                 f'<text x="{x+92}" y="{y+104}" font-size="14" fill="#444">Admite valor nulo</text>')
        o.append(f'<rect x="{x+14}" y="{y+120}" width="26" height="14" rx="3" fill="#9a9aa2"/>'
                 f'<text x="{x+52}" y="{y+132}" font-size="14" fill="#444">Tabla de otra área (solo su clave)</text>')
        mx = x + 205
        for i, (tipo_, t) in enumerate([('uno', 'Uno y solo uno'), ('cero_uno', 'Cero o uno'), ('muchos', 'Cero o muchos')]):
            yy = y + 22 + i * 26
            o.append(f'<line x1="{mx}" y1="{yy}" x2="{mx+40}" y2="{yy}" stroke="{LINEA}" stroke-width="1.7"/>')
            self._marca(o, mx + 40, yy, 'l', tipo_)
            o.append(f'<text x="{mx+52}" y="{yy+5}" font-size="14" fill="#444">{t}</text>')
        o.append(f'<text x="{x+14}" y="{y+180}" font-size="13" fill="#6a6a72">La línea va de la clave foránea a la clave primaria.</text>')

    def svg(self):
        o = [f'<rect width="{self.W}" height="{self.H}" fill="#ffffff"/>',
             ('' if __import__('os').environ.get('SIN_TITULO') else f'<text x="40" y="54" font-size="30" font-weight="bold" fill="#2f2f2f">{escape(self.titulo)}</text>')]
        self._leyenda(o)
        # Donde dos lineas se cruzan, la horizontal salta por encima de la vertical (como en Lucidchart)
        verticales = []
        for k in self.con:
            c = self.caminos[id(k)]
            for p, q in zip(c, c[1:]):
                if abs(p[0] - q[0]) < 0.01:
                    verticales.append((id(k), p[0], min(p[1], q[1]), max(p[1], q[1])))
        for k in self.con:
            c = self.caminos[id(k)]
            d = [f'M {c[0][0]:.1f} {c[0][1]:.1f}']
            for p, q in zip(c, c[1:]):
                if abs(p[1] - q[1]) < 0.01 and abs(p[0] - q[0]) > 0.01:
                    y = p[1]
                    sentido = 1 if q[0] > p[0] else -1
                    saltos = sorted((x for (kid, x, y0, y1) in verticales
                                     if kid != id(k) and y0 + 1 < y < y1 - 1
                                     and min(p[0], q[0]) + 8 < x < max(p[0], q[0]) - 8), reverse=sentido < 0)
                    for x in saltos:
                        d.append(f'L {x - sentido*7:.1f} {y:.1f} A 7 7 0 0 {1 if sentido > 0 else 0} {x + sentido*7:.1f} {y:.1f}')
                d.append(f'L {q[0]:.1f} {q[1]:.1f}')
            o.append(f'<path d="{" ".join(d)}" fill="none" stroke="{LINEA}" stroke-width="1.7" stroke-linejoin="round"/>')
        for t in self.t.values():
            o.append(t.svg())
        for k in self.con:
            c = self.caminos[id(k)]
            self._marca(o, c[0][0], c[0][1], k['lh'], 'cero_uno' if k['unico'] else 'muchos')
            self._marca(o, c[-1][0], c[-1][1], k['lp'], 'cero_uno' if k['nulo'] else 'uno')
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.W}" height="{self.H}" viewBox="0 0 {self.W} {self.H}" '
                f'font-family="Arial, Helvetica, sans-serif">\n' + '\n'.join(o) + '\n</svg>')

    def problemas(self):
        caminos = [self.caminos[id(k)] for k in self.con]
        out = []
        n = self._cruces(caminos)
        if n:
            out.append(f'{n} cruce(s) de lineas')
        for k, c in zip(self.con, caminos):
            for p, q in zip(c, c[1:]):
                for t in self.t.values():
                    for s in range(1, 40):
                        px, py = p[0] + (q[0] - p[0]) * s / 40, p[1] + (q[1] - p[1]) * s / 40
                        if t.x + 1 < px < t.x + t.w - 1 and t.y + 1 < py < t.y + t.h - 1:
                            out.append(f'{k["h"]}.{k["c"]} atraviesa {t.nombre}')
                            break
        return sorted(set(out))


def _cruza(p, q, r, s):
    def orient(a, b, c):
        v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        return 0 if abs(v) < 1e-6 else (1 if v > 0 else -1)
    o1, o2, o3, o4 = orient(p, q, r), orient(p, q, s), orient(r, s, p), orient(r, s, q)
    if o1 * o2 < 0 and o3 * o4 < 0:
        return True
    # tramos superpuestos sobre la misma recta
    if o1 == o2 == 0 and o3 == o4 == 0:
        if p[0] == q[0] == r[0] == s[0]:
            return min(max(p[1], q[1]), max(r[1], s[1])) - max(min(p[1], q[1]), min(r[1], s[1])) > 1
        if p[1] == q[1] == r[1] == s[1]:
            return min(max(p[0], q[0]), max(r[0], s[0])) - max(min(p[0], q[0]), min(r[0], s[0])) > 1
    return False


AREAS = [
    Area('Modelo relacional 1: Clientes, obras y presupuestos',
         [['cliente', 'obra', 'registro_cac*'], ['presupuesto', 'item_presupuesto'], ['subrubro', 'rubro', 'material']],
         lados={('subrubro', 'id_rubro'): 'r', ('material', 'id_rubro'): 'r'},
         resumenes={'registro_cac': 'ver Cobros'}),
    Area('Modelo relacional 2: Compras y proveedores',
         [['obra*', 'usuario*'], ['pedido', 'pedido_material', 'material*'], ['observacion_proveedor', 'proveedor', 'cotizacion']],
         lados={('pedido_material', 'id_material'): 'r', ('pedido_material', 'id_pedido'): 'r',
                ('cotizacion', 'id_proveedor'): 'r', ('observacion_proveedor', 'id_proveedor'): 'r'},
         resumenes={'obra': 'ver Obras', 'usuario': 'ver Usuarios', 'material': 'ver Obras'}),
    Area('Modelo relacional 3: Gastos y personal',
         [['operario_obra', 'inasistencia'], ['obra*', 'rubro*', 'subrubro*', 'pedido*', 'operario', 'usuario*'], ['gasto']],
         resumenes={'obra': 'ver Obras', 'usuario': 'ver Usuarios', 'pedido': 'ver Compras',
                    'rubro': 'ver Obras', 'subrubro': 'ver Obras'}),
    Area('Modelo relacional 4: Seguimiento de obras',
         [['hito', 'plantilla_hito', 'plantilla_hito_detalle'], ['obra*', 'usuario*', 'rubro*']],
         resumenes={'obra': 'ver Obras', 'rubro': 'ver Obras', 'usuario': 'ver Usuarios'}),
    Area('Modelo relacional 5: Cobros y portfolio',
         [['pago', 'imagen_portfolio'], ['cuota', 'usuario*', 'publicacion_portfolio'], ['obra*', 'registro_cac']],
         resumenes={'obra': 'ver Obras', 'usuario': 'ver Usuarios'}),
    Area('Modelo relacional 6: Usuarios y accesos',
         [['registro_auditoria', 'operario*'], ['usuario'], ['rol', 'rol_permiso', 'permiso']],
         lados={('rol_permiso', 'id_rol'): 'r', ('rol_permiso', 'id_permiso'): 'r'},
         resumenes={'operario': 'ver Personal'}),
]
COMPLETO = Area('Modelo relacional completo de SIGCO',
    [['imagen_portfolio', 'plantilla_hito', 'plantilla_hito_detalle', 'permiso', 'rol_permiso'],
     ['publicacion_portfolio', 'presupuesto', 'cuota', 'cliente', 'registro_cac', 'pago', 'registro_auditoria', 'rol', 'item_presupuesto'],
     ['obra', 'usuario', 'rubro', 'subrubro', 'material'],
     ['operario_obra', 'inasistencia', 'pedido', 'hito', 'operario', 'gasto', 'pedido_material', 'cotizacion'],
     ['observacion_proveedor', 'proveedor']],
    lados={('pago', 'id_cuota'): 'l', ('presupuesto', 'id_presupuesto_base'): 'l',
           ('item_presupuesto', 'id_presupuesto'): 'l', ('plantilla_hito_detalle', 'id_plantilla'): 'l',
           ('rol_permiso', 'id_permiso'): 'l', ('observacion_proveedor', 'id_proveedor'): 'r'})

NOMBRES = ['obras-presupuestos', 'compras', 'gastos-personal', 'seguimiento', 'cobros-portfolio', 'usuarios-accesos']

if __name__ == '__main__':
    cols, pks, fks, unicos = esquema()
    pedidos = [int(a) if a.isdigit() else a for a in sys.argv[1:]] or list(range(1, len(AREAS) + 1)) + ['completo']
    for i in pedidos:
        a = COMPLETO if i == 'completo' else AREAS[i - 1]
        a.armar(cols, pks, fks, unicos)
        nombre = 'relacional-completo' if i == 'completo' else f'relacional-{i}-{NOMBRES[i - 1]}'
        with open(nombre + '.svg', 'w', encoding='utf-8') as f:
            f.write(a.svg())
        render(nombre + '.svg', nombre + '.png', a.W, a.H, 2)
        p = a.problemas()
        print(nombre, f'{a.W}x{a.H}', 'OK' if not p else '')
        for x in p:
            print('   !', x)
