# Busca el orden de las tablas dentro de cada columna del relacional completo que
# minimiza los cruces de lineas (medida rapida: dos lineas entre las mismas dos
# columnas se cruzan si sus alturas se invierten). Imprime el mejor orden.
import random, sys
import relacional as R

cols, pks, fks, unicos = R.esquema()
A = R.COMPLETO
cols_orden = [list(c) for c in A.columnas]
tablas = {}
for c in cols_orden:
    for n in c:
        tablas[n] = R.Tabla(n, cols[n], pks.get(n, set()), set(), unicos)
col_de = {n: i for i, c in enumerate(cols_orden) for n in c}
aristas = [(t, c, p) for t, c, p, _ in fks if t in col_de and p in col_de]

def alturas(orden):
    pos = {}
    for c in orden:
        y = 0
        for n in c:
            pos[n] = y
            y += tablas[n].h + 46
    return pos

def fila(t, col):
    for i, f in enumerate(tablas[t].filas):
        if f['nombre'] == col:
            return i
    return 0

def costo(orden):
    pos = alturas(orden)
    por_par = {}
    largo = 0
    for t, c, p in aristas:
        a, b = col_de[t], col_de[p]
        y_t = pos[t] + R.HEAD + fila(t, c) * R.ROW
        y_p = pos[p] + R.HEAD
        largo += abs(y_t - y_p)
        if a == b:
            continue
        if a < b:
            por_par.setdefault((a, b), []).append((y_t, y_p))
        else:
            por_par.setdefault((b, a), []).append((y_p, y_t))
    cruces = 0
    for lst in por_par.values():
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                if (lst[i][0] - lst[j][0]) * (lst[i][1] - lst[j][1]) < 0:
                    cruces += 1
    return cruces * 10000 + largo

rnd = random.Random(1)
mejor = [list(c) for c in cols_orden]
mejor_c = costo(mejor)
for reinicio in range(30):
    orden = [list(c) for c in mejor] if reinicio % 3 == 0 else [rnd.sample(c, len(c)) for c in cols_orden]
    c = costo(orden)
    mejoro = True
    while mejoro:
        mejoro = False
        for ci, col in enumerate(orden):
            for i in range(len(col)):
                for j in range(len(col)):
                    if i == j:
                        continue
                    nuevo = col[:]
                    nuevo.insert(j, nuevo.pop(i))
                    prueba = orden[:ci] + [nuevo] + orden[ci + 1:]
                    c2 = costo(prueba)
                    if c2 < c:
                        orden, c, mejoro = prueba, c2, True
                        col = nuevo
    if c < mejor_c:
        mejor, mejor_c = orden, c
print(mejor_c // 10000, 'cruces estimados')
print(mejor)
