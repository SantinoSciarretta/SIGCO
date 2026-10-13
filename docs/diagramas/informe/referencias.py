# Hoja de referencias de todos los diagramas del informe.
# Uso: python referencias.py   (genera referencias.svg y referencias.png)
#
# Los diagramas del informe van sin el recuadro de referencias (SIN_LEYENDA=1):
# esta hoja junta en un solo lugar lo que significa cada simbolo, por tipo de
# diagrama. Cada recuadro sale de la misma funcion que lo dibujaba dentro del
# diagrama, asi que los simbolos son exactamente los mismos.
import os
from html import escape
from types import SimpleNamespace, MethodType

os.environ.pop('SIN_LEYENDA', None)

from lucid import Diagram, render, text_w
import casos_de_uso
import dfd
import er
import flujos
import gantt
import relacional
import secuencia_lib
from clases import VIOLETA, GRIS

TITULO = 24   # tamano del nombre de cada tipo de diagrama


def caja_flujo():
    d = Diagram(10, 10, '')
    x, y, w, h = d.legend(0, 0, flujos.LEYENDA)
    return '\n'.join(d.extra), w, h


def caja_dfd():
    d = SimpleNamespace(extra=[])
    dfd.leyenda(d, 0, 0)
    return '\n'.join(d.extra), 330, 150


def caja_er():
    d = SimpleNamespace(W=470, extra=[])
    er.ER.leyenda(d)
    return f'<g transform="translate(0,-16)">{d.extra[0]}</g>', 452, 180


def caja_relacional():
    d = SimpleNamespace(W=420)
    d._marca = MethodType(relacional.Area._marca, d)
    o = []
    relacional.Area._leyenda(d, o)
    return f'<g transform="translate(0,-18)">{"".join(o)}</g>', 400, 196


def caja_gantt():
    # En el diagrama es una fila; aca va en columna para que entre en la hoja
    w = max(text_w('Etapa ' + n, 15) for n, _, _, _ in gantt.ETAPAS) + 70
    h = len(gantt.ETAPAS) * 30 + 16
    o = [f'<rect x="0" y="0" width="{w:.0f}" height="{h}" rx="7" fill="#fbfbfb" stroke="#dadada" stroke-width="1.2"/>']
    for i, (nombre, col, _, _) in enumerate(gantt.ETAPAS):
        o.append(f'<rect x="14" y="{15 + i * 30}" width="20" height="20" rx="4" fill="{col}"/>'
                 f'<text x="44" y="{30 + i * 30}" font-size="15" fill="#444">Etapa {escape(nombre)}</text>')
    return '\n'.join(o), w, h


def caja_clases():
    # Los diagramas de clases no tenian recuadro (la notacion se explica en el texto
    # del informe). Se agrega aca para que la hoja cubra todos los diagramas.
    filas = [
        ('clase', 'Clase: nombre, atributos (-) y métodos de negocio (+)'),
        ('gris', 'Clase de otra área: solo su nombre'),
        ('asoc', 'Asociación, con la multiplicidad en cada extremo'),
        ('comp', 'Composición: la clase del rombo es dueña de sus partes'),
        ('ref', 'Referencia por identificador, sin relación entre clases'),
    ]
    w, h = 470, 8 + len(filas) * 46
    o = [f'<rect x="0" y="0" width="{w}" height="{h}" rx="7" fill="#fbfbfb" stroke="#dadada" stroke-width="1.2"/>']
    for i, (k, t) in enumerate(filas):
        y = 24 + i * 46
        if k == 'clase':
            o.append(f'<rect x="14" y="{y-14}" width="44" height="36" fill="#fff" stroke="{VIOLETA}" stroke-width="1.8"/>'
                     f'<line x1="14" y1="{y-2}" x2="58" y2="{y-2}" stroke="{VIOLETA}" stroke-width="1.4"/>'
                     f'<line x1="14" y1="{y+10}" x2="58" y2="{y+10}" stroke="{VIOLETA}" stroke-width="1.4"/>')
        elif k == 'gris':
            o.append(f'<rect x="14" y="{y-12}" width="44" height="28" fill="#f4f4f6" stroke="{GRIS}" stroke-width="1.8" stroke-dasharray="6 3"/>')
        elif k == 'asoc':
            o.append(f'<line x1="14" y1="{y+2}" x2="58" y2="{y+2}" stroke="#3a3a3a" stroke-width="1.8"/>'
                     f'<text x="14" y="{y-4}" font-size="11" font-weight="bold" fill="#3a3a3a">1</text>'
                     f'<text x="58" y="{y-4}" font-size="11" font-weight="bold" fill="#3a3a3a" text-anchor="end">0..*</text>')
        elif k == 'comp':
            o.append(f'<polygon points="14,{y+2} 24,{y-4} 34,{y+2} 24,{y+8}" fill="#3a3a3a"/>'
                     f'<line x1="34" y1="{y+2}" x2="58" y2="{y+2}" stroke="#3a3a3a" stroke-width="1.8"/>')
        else:
            o.append(f'<line x1="14" y1="{y+2}" x2="58" y2="{y+2}" stroke="#3a3a3a" stroke-width="1.8" stroke-dasharray="8 5"/>')
        o.append(f'<text x="72" y="{y+7}" font-size="15" fill="#444">{escape(t)}</text>')
    return '\n'.join(o), w, h


def caja_simple(svg, w, h):
    return svg, w, h


SECCIONES = [
    ('Diagrama de casos de uso', caja_simple(casos_de_uso.leyenda(0, 0), 300, 70)),
    ('Diagrama de flujo', caja_flujo()),
    ('Diagrama de flujo de datos (DFD)', caja_dfd()),
    ('Diagrama de secuencia', caja_simple(secuencia_lib.leyenda(0, 0), 214, 84)),
    ('Diagrama de clases', caja_clases()),
    ('Diagrama entidad-relación', caja_er()),
    ('Modelo relacional', caja_relacional()),
    ('Diagrama de Gantt', caja_gantt()),
]


def armar():
    margen, col_w, sep = 50, 560, 46
    cols = [[], []]
    alto = [0, 0]
    # reparte las secciones en dos columnas, en orden, equilibrando la altura
    for nombre, (svg, w, h) in SECCIONES:
        c = 0 if alto[0] <= alto[1] else 1
        cols[c].append((nombre, svg, w, h, alto[c]))
        alto[c] += TITULO + 18 + h + sep
    top = 120
    W = margen * 2 + col_w * 2 + 40
    H = int(top + max(alto) + margen - sep + 10)
    o = [f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
         f'<text x="{margen}" y="64" font-size="34" font-weight="bold" fill="#2f2f2f">Referencias de los diagramas de SIGCO</text>',
         f'<text x="{margen}" y="96" font-size="17" fill="#666">Qué significa cada símbolo, según el tipo de diagrama</text>']
    for c, secciones in enumerate(cols):
        x = margen + c * (col_w + 40)
        for nombre, svg, w, h, y in secciones:
            y += top
            o.append(f'<text x="{x}" y="{y + TITULO}" font-size="{TITULO}" font-weight="bold" fill="#3d1a78">{escape(nombre)}</text>')
            o.append(f'<g transform="translate({x},{y + TITULO + 14})">{svg}</g>')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
           f'font-family="Arial, Helvetica, sans-serif">\n' + '\n'.join(o) + '\n</svg>')
    return svg, W, H


if __name__ == '__main__':
    svg, W, H = armar()
    with open('referencias.svg', 'w', encoding='utf-8') as f:
        f.write(svg)
    render('referencias.svg', 'referencias.png', W, H, 2)
    print('referencias', W, H)
