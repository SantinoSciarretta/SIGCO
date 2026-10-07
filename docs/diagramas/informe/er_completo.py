# Diagrama entidad-relacion COMPLETO de SIGCO (notacion de Chen), en una sola figura.
# Uso: python er_completo.py
#
# Son 26 entidades y 43 relaciones. Ubicarlas a mano sin cruces es muy dificil
# (Obra se relaciona con 10 entidades y Usuario con 9), asi que la ubicacion la
# busca un optimizador: pone las entidades en una grilla y prueba miles de
# movimientos (recocido simulado) quedandose con los que bajan los cruces de
# lineas, las lineas que pasan por encima de una caja y las superposiciones de
# rombos. Cada relacion es una recta de entidad a entidad con su rombo al medio.
import math
import random
import sys
from er import ER, RW, RH, EW, EH

ENT = {
    'cliente': 'Cliente', 'obra': 'Obra', 'pres': 'Presupuesto', 'item': 'Ítem de presupuesto',
    'rubro': 'Rubro', 'sub': 'Subrubro', 'mat': 'Material', 'cac': 'Registro CAC',
    'pedido': 'Pedido', 'prov': 'Proveedor', 'cot': 'Cotización', 'obs': 'Observación de proveedor',
    'gasto': 'Gasto', 'operario': 'Operario', 'inas': 'Inasistencia', 'hito': 'Hito (etapa)',
    'plant': 'Plantilla de hitos', 'det': 'Detalle de plantilla', 'cuota': 'Cuota', 'pago': 'Pago',
    'pub': 'Publicación de portfolio', 'img': 'Imagen de portfolio', 'usuario': 'Usuario',
    'rol': 'Rol', 'permiso': 'Permiso', 'aud': 'Registro de auditoría',
}
# (entidad A, entidad B, verbo, tipo, cardinalidad junto a A, cardinalidad junto a B)
REL = [
    ('cliente', 'obra', 'encarga', '1:N', '(1,1)', '(0,N)'),
    ('cac', 'obra', 'último aplicado', '1:N', '(0,1)', '(0,N)'),
    ('obra', 'pres', 'tiene', '1:N', '(1,1)', '(0,N)'),
    ('pres', 'item', 'contiene', '1:N', '(1,1)', '(0,N)'),
    ('rubro', 'item', 'clasifica', '1:N', '(1,1)', '(0,N)'),
    ('rubro', 'item', 'es referido por', '1:N', '(0,1)', '(0,N)'),
    ('sub', 'item', 'detalla', '1:N', '(0,1)', '(0,N)'),
    ('mat', 'item', 'se usa en', '1:N', '(0,1)', '(0,N)'),
    ('rubro', 'sub', 'se divide en', '1:N', '(1,1)', '(0,N)'),
    ('rubro', 'mat', 'agrupa', '1:N', '(1,1)', '(0,N)'),
    ('obra', 'pedido', 'genera', '1:N', '(1,1)', '(0,N)'),
    ('prov', 'pedido', 'se le pide a', '1:N', '(0,1)', '(0,N)'),
    ('usuario', 'pedido', 'solicita', '1:N', '(0,1)', '(0,N)'),
    ('usuario', 'pedido', 'recibe', '1:N', '(0,1)', '(0,N)'),
    ('pedido', 'mat', 'incluye', 'M:N', '(0,N)', '(0,N)'),
    ('prov', 'cot', 'cotiza', '1:N', '(1,1)', '(0,N)'),
    ('mat', 'cot', 'precio de', '1:N', '(1,1)', '(0,N)'),
    ('prov', 'obs', 'recibe', '1:N', '(1,1)', '(0,N)'),
    ('pedido', 'obs', 'origina', '1:N', '(0,1)', '(0,N)'),
    ('obra', 'gasto', 'tiene', '1:N', '(1,1)', '(0,N)'),
    ('rubro', 'gasto', 'clasifica', '1:N', '(1,1)', '(0,N)'),
    ('sub', 'gasto', 'detalla', '1:N', '(0,1)', '(0,N)'),
    ('pedido', 'gasto', 'genera', '1:N', '(0,1)', '(0,N)'),
    ('operario', 'gasto', 'cobra', '1:N', '(0,1)', '(0,N)'),
    ('usuario', 'gasto', 'registra', '1:N', '(0,1)', '(0,N)'),
    ('operario', 'obra', 'trabaja en', 'M:N', '(0,N)', '(0,N)'),
    ('operario', 'inas', 'falta', '1:N', '(1,1)', '(0,N)'),
    ('obra', 'inas', 'ocurre en', '1:N', '(1,1)', '(0,N)'),
    ('usuario', 'inas', 'registra', '1:N', '(0,1)', '(0,N)'),
    ('obra', 'hito', 'se divide en', '1:N', '(1,1)', '(0,N)'),
    ('rubro', 'hito', 'clasifica', '1:N', '(0,1)', '(0,N)'),
    ('usuario', 'hito', 'completa', '1:N', '(0,1)', '(0,N)'),
    ('plant', 'det', 'contiene', '1:N', '(1,1)', '(0,N)'),
    ('obra', 'cuota', 'se cobra en', '1:N', '(1,1)', '(0,N)'),
    ('cuota', 'pago', 'recibe', '1:N', '(1,1)', '(0,N)'),
    ('usuario', 'pago', 'registra', '1:N', '(0,1)', '(0,N)'),
    ('obra', 'pub', 'se publica en', '1:1', '(1,1)', '(0,1)'),
    ('pub', 'img', 'muestra', '1:N', '(1,1)', '(0,N)'),
    ('rol', 'usuario', 'asigna', '1:N', '(1,1)', '(0,N)'),
    ('rol', 'permiso', 'incluye', 'M:N', '(0,N)', '(0,N)'),
    ('usuario', 'aud', 'realiza', '1:N', '(1,1)', '(0,N)'),
    ('operario', 'usuario', 'se vincula con', '1:1', '(0,1)', '(0,1)'),
]
COLS, FILAS = 11, 8
CW, CH = 300, 220
X0, Y0 = 210, 330
ANCHO = {'item': 230, 'obs': 250, 'pub': 240, 'img': 220, 'aud': 230, 'plant': 210, 'det': 220}
# pares con dos relaciones: las dos rectas van separadas
DOBLES = {('rubro', 'item'): 40, ('usuario', 'pedido'): 40}


def centro(celda):
    c, f = celda
    return (X0 + c * CW, Y0 + f * CH)


def segmentos(pos):
    """Recta de cada relacion (con desplazamiento si el par tiene dos)."""
    vistos = {}
    out = []
    for a, b, *_ in REL:
        pa, pb = centro(pos[a]), centro(pos[b])
        par = (a, b)
        d = 0
        if par in DOBLES:
            n = vistos.get(par, 0)
            vistos[par] = n + 1
            d = DOBLES[par] * (-1 if n == 0 else 1)
        if d:
            L = math.hypot(pb[0] - pa[0], pb[1] - pa[1]) or 1
            nx, ny = -(pb[1] - pa[1]) / L * d, (pb[0] - pa[0]) / L * d
            pa, pb = (pa[0] + nx, pa[1] + ny), (pb[0] + nx, pb[1] + ny)
        out.append((pa, pb))
    return out


def rombo_en(i, p, q):
    """Donde va el rombo de la relacion i: al medio, o corrido si el par tiene dos relaciones."""
    a, b = REL[i][0], REL[i][1]
    t = 0.5
    if (a, b) in DOBLES:
        primero = next(j for j, r in enumerate(REL) if (r[0], r[1]) == (a, b))
        t = 0.36 if i == primero else 0.64
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


def _cruza(p, q, r, s):
    def o(a, b, c):
        v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        return 0 if abs(v) < 1e-9 else (1 if v > 0 else -1)
    return o(p, q, r) * o(p, q, s) < 0 and o(r, s, p) * o(r, s, q) < 0


def _corta_caja(p, q, cx, cy, hw, hh):
    """Si el segmento pq atraviesa el rectangulo (Liang-Barsky)."""
    x0, y0 = p
    dx, dy = q[0] - x0, q[1] - y0
    t0, t1 = 0.0, 1.0
    for pp, qq in ((-dx, x0 - (cx - hw)), (dx, (cx + hw) - x0), (-dy, y0 - (cy - hh)), (dy, (cy + hh) - y0)):
        if abs(pp) < 1e-12:
            if qq < 0:
                return False
        else:
            r = qq / pp
            if pp < 0:
                t0 = max(t0, r)
            else:
                t1 = min(t1, r)
            if t0 > t1:
                return False
    return t1 - t0 > 0.02


def costo(pos):
    segs = segmentos(pos)
    c = 0.0
    medios = [rombo_en(i, p, q) for i, (p, q) in enumerate(segs)]
    for i, (p, q) in enumerate(segs):
        a, b = REL[i][0], REL[i][1]
        L = math.hypot(q[0] - p[0], q[1] - p[1])
        c += L * 0.22
        # espacio libre entre las dos entidades, medido sobre la recta: tiene que entrar el rombo
        ux, uy = (abs(q[0] - p[0]) / L, abs(q[1] - p[1]) / L) if L else (1, 0)
        def media(k):
            hw, hh = ANCHO.get(k, EW) / 2, EH / 2
            return min(hw / ux if ux > 1e-9 else 1e9, hh / uy if uy > 1e-9 else 1e9)
        rombo = 1 / ((ux / (RW / 2)) + (uy / (RH / 2)))      # medio rombo en esa direccion
        libre = L - media(a) - media(b)
        if libre < 2 * rombo + 70:
            c += 400
        # pasa por encima de otra entidad
        for k, cel in pos.items():
            if k in (a, b):
                continue
            cx, cy = centro(cel)
            if _corta_caja(p, q, cx, cy, ANCHO.get(k, EW) / 2 + 18, EH / 2 + 18):
                c += 800
        # pasa por encima del rombo de otra relacion
        for j, m in enumerate(medios):
            if j == i:
                continue
            if _corta_caja(p, q, m[0], m[1], RW / 2 - 15, RH / 2 - 10):
                c += 400
        for j in range(i + 1, len(segs)):
            r, s = segs[j]
            if len({a, b} & {REL[j][0], REL[j][1]}):
                continue
            if _cruza(p, q, r, s):
                c += 120
    # entidades demasiado cerca entre si
    ks = list(pos)
    for i in range(len(ks)):
        xa, ya = centro(pos[ks[i]])
        for j in range(i + 1, len(ks)):
            xb, yb = centro(pos[ks[j]])
            if abs(xa - xb) < (ANCHO.get(ks[i], EW) + ANCHO.get(ks[j], EW)) / 2 + 50 and abs(ya - yb) < EH + 70:
                c += 600
    # rombos encimados entre si o con entidades
    for i in range(len(medios)):
        for j in range(i + 1, len(medios)):
            if abs(medios[i][0] - medios[j][0]) < RW + 6 and abs(medios[i][1] - medios[j][1]) < RH + 6:
                c += 300
        for k, cel in pos.items():
            cx, cy = centro(cel)
            if abs(medios[i][0] - cx) < (ANCHO.get(k, EW) + RW) / 2 + 6 and abs(medios[i][1] - cy) < (EH + RH) / 2 + 6:
                c += 300
    return c


def optimizar(semilla, iteraciones=26000, inicio=None, T0=400.0):
    rnd = random.Random(semilla)
    celdas = [(c, f) for c in range(COLS) for f in range(FILAS)]
    rnd.shuffle(celdas)
    pos = dict(inicio) if inicio else {k: celdas[i] for i, k in enumerate(ENT)}
    actual = costo(pos)
    mejor, mejor_pos = actual, dict(pos)
    T = T0
    for it in range(iteraciones):
        k = rnd.choice(list(ENT))
        destino = rnd.choice(celdas)
        ocupante = next((o for o, cel in pos.items() if cel == destino), None)
        viejo = pos[k]
        pos[k] = destino
        if ocupante:
            pos[ocupante] = viejo
        nuevo = costo(pos)
        if nuevo < actual or rnd.random() < math.exp((actual - nuevo) / T):
            actual = nuevo
            if nuevo < mejor:
                mejor, mejor_pos = nuevo, dict(pos)
        else:
            pos[k] = viejo
            if ocupante:
                pos[ocupante] = destino
        T = max(2.0, T * 0.99975)
    return mejor, mejor_pos


def dibujar(pos):
    usadas_c = sorted({c for c, _ in pos.values()})
    usadas_f = sorted({f for _, f in pos.values()})
    W = X0 + (max(usadas_c)) * CW + 260
    H = Y0 + (max(usadas_f)) * CH + 120
    d = ER('Entidad-relación completo de SIGCO', int(W), int(H))
    for k, nombre in ENT.items():
        x, y = centro(pos[k])
        d.ent(k, x, y, nombre, w=ANCHO.get(k, EW))
    segs = segmentos(pos)
    for i, ((a, b, verbo, tipo, ca, cb), (p, q)) in enumerate(zip(REL, segs)):
        rid = f'r{i}'
        d.rel(rid, *rombo_en(i, p, q), verbo, tipo)
        na, nb = d.nodes[a], d.nodes[b]
        # el punto de salida queda siempre dentro de la caja, aunque la linea este corrida
        dentro = lambda n, dx, dy: (max(-n['w'] / 2 + 14, min(n['w'] / 2 - 14, dx)), max(-n['h'] / 2 + 19, min(n['h'] / 2 - 19, dy)))
        d.unir(a, rid, ca, de=dentro(na, p[0] - na['x'], p[1] - na['y']), lado_card=1)
        d.unir(b, rid, cb, de=dentro(nb, q[0] - nb['x'], q[1] - nb['y']), lado_card=-1)
    return d


if __name__ == '__main__':
    semillas = [int(a) for a in sys.argv[1:]] or list(range(6))
    resultados = []
    for s in semillas:
        c, pos = optimizar(s)
        print('semilla', s, 'costo', round(c))
        resultados.append((c, s, pos))
    c, s, pos = min(resultados)
    print('mejor semilla', s, round(c))
    print(pos)
    d = dibujar(pos)
    d.save('er-completo')
