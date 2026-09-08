# -*- coding: utf-8 -*-
"""
Genera el diagrama entidad-relacion de SIGCO en formato Graphviz.

Lee el esquema REAL de PostgreSQL, no una definicion escrita a mano: el
diagrama no puede quedar desactualizado respecto de la base.

Usa etiquetas HTML de Graphviz para dibujar cada tabla como una tablita con
cabecera —igual que pgAdmin— pero con el nombre de la tabla destacado arriba,
que es lo que en pgAdmin queda tapado por el nombre del esquema.
"""
import io
import subprocess
from collections import OrderedDict

# Las rutas se resuelven contra la ubicacion del script, no contra la carpeta
# desde la que se lo ejecuta: asi el archivo siempre sale en docs/diagramas/.
import os
AQUI = os.path.dirname(os.path.abspath(__file__))
DIAGRAMAS = os.path.dirname(AQUI)
RAIZ = os.path.dirname(os.path.dirname(DIAGRAMAS))


def enDiagramas(nombre):
    return os.path.join(DIAGRAMAS, nombre)


PSQL = r'C:\Program Files\PostgreSQL\17\bin\psql.exe'
ENTORNO = {'PGPASSWORD': 'sigco2026', 'SYSTEMROOT': r'C:\Windows'}

# Paleta del sistema (frontend/src/styles/tokens.css)
ACERO_900 = '#1d2d3d'
ACERO = '#5980a6'
ACERO_100 = '#eef6ff'
BLANCO = '#ffffff'
GRIS_LINEA = '#c3ccd6'
TINTA = '#1d1f20'
TENUE = '#5d6b7a'


def consultar(sql):
    r = subprocess.run([PSQL, '-U', 'postgres', '-h', 'localhost', '-d', 'sigco_dev',
                        '-t', '-A', '-F', '|', '-c', sql],
                       capture_output=True, env=ENTORNO)
    salida = r.stdout.decode('utf-8', 'replace').replace('\r', '')
    return [[c.strip() for c in l.split('|')]
            for l in salida.split('\n') if l.strip()]


# --------------------------------------------------------------- esquema real
COLUMNAS = consultar("""
    SELECT c.table_name, c.column_name,
      CASE c.data_type
        WHEN 'character varying' THEN 'varchar('||c.character_maximum_length||')'
        WHEN 'numeric' THEN 'numeric('||c.numeric_precision||','||c.numeric_scale||')'
        WHEN 'timestamp without time zone' THEN 'timestamp'
        WHEN 'bigint' THEN CASE WHEN c.column_default LIKE 'nextval%'
                                THEN 'bigserial' ELSE 'bigint' END
        ELSE c.data_type END,
      CASE WHEN c.is_nullable='NO' THEN 'NN' ELSE '' END
    FROM information_schema.columns c
    WHERE c.table_schema='public' AND c.table_name <> 'flyway_schema_history'
    ORDER BY c.table_name, c.ordinal_position""")

CLAVES = {}
for t, col, tipo in consultar("""
    SELECT tc.table_name, kcu.column_name, tc.constraint_type
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name = tc.constraint_name
     AND kcu.constraint_schema = tc.constraint_schema
    WHERE tc.table_schema='public'
      AND tc.constraint_type IN ('PRIMARY KEY','FOREIGN KEY')"""):
    CLAVES.setdefault((t, col), set()).add('PK' if tipo == 'PRIMARY KEY' else 'FK')

RELACIONES = consultar("""
    SELECT tc.table_name, kcu.column_name, ccu.table_name, ccu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name=tc.constraint_name AND kcu.constraint_schema=tc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name=tc.constraint_name AND ccu.constraint_schema=tc.constraint_schema
    WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
    ORDER BY 1,2""")

# Modulo de cada tabla, para agrupar y colorear.
MODULOS = OrderedDict([
    ('CLIENTES', ['cliente']),
    ('OBRAS', ['obra']),
    ('PRESUPUESTACIÓN', ['rubro', 'subrubro', 'presupuesto', 'item_presupuesto']),
    ('MATERIALES', ['material']),
    ('PROVEEDORES', ['proveedor', 'cotizacion', 'observacion_proveedor']),
    ('COMPRAS', ['pedido', 'pedido_material']),
    ('GASTOS', ['gasto']),
    ('PERSONAL', ['operario', 'operario_obra', 'inasistencia']),
    ('SEGUIMIENTO', ['hito', 'plantilla_hito', 'plantilla_hito_detalle']),
    ('COBROS', ['cuota', 'registro_cac']),
    ('PORTFOLIO', ['publicacion_portfolio', 'imagen_portfolio']),
    ('USUARIOS Y ACCESOS', ['usuario', 'rol', 'permiso', 'rol_permiso', 'registro_auditoria']),
])

tablas = OrderedDict()
for t, col, tipo, nn in COLUMNAS:
    tablas.setdefault(t, []).append((col, tipo, nn))


def escapar(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def nodo(tabla):
    """Una tabla dibujada como tablita con cabecera, estilo pgAdmin."""
    filas = []
    # CABECERA: el nombre de la tabla, grande y sobre fondo oscuro. En pgAdmin
    # este lugar lo ocupa el nombre del esquema y el de la tabla queda perdido
    # como si fuera una columna mas.
    filas.append(
        '<TR><TD PORT="__t" BGCOLOR="%s" COLSPAN="4" ALIGN="CENTER" CELLPADDING="6">'
        '<FONT COLOR="%s" POINT-SIZE="17"><B>%s</B></FONT></TD></TR>'
        % (ACERO_900, BLANCO, escapar(tabla)))

    for col, tipo, nn in tablas[tabla]:
        claves = CLAVES.get((tabla, col), set())
        marca = 'PK' if 'PK' in claves else ('FK' if 'FK' in claves else '')
        # Las columnas clave se destacan: son las que arman las relaciones.
        fondo = ACERO_100 if marca else BLANCO
        negrita = ('<B>%s</B>' % escapar(col)) if 'PK' in claves else escapar(col)
        celdaMarca = ('<FONT COLOR="%s" POINT-SIZE="10"><B>%s</B></FONT>'
                      % (ACERO, marca)) if marca else ''
        celdaNn = ('<FONT COLOR="%s" POINT-SIZE="10">%s</FONT>'
                   % (TENUE, nn)) if nn else ''
        filas.append(
            '<TR>'
            '<TD BGCOLOR="%s" WIDTH="26" ALIGN="CENTER">%s</TD>'
            '<TD BGCOLOR="%s" PORT="%s" ALIGN="LEFT"><FONT POINT-SIZE="13">%s</FONT></TD>'
            '<TD BGCOLOR="%s" ALIGN="LEFT">'
            '<FONT COLOR="%s" POINT-SIZE="11">%s</FONT></TD>'
            '<TD BGCOLOR="%s" WIDTH="24" ALIGN="CENTER">%s</TD>'
            '</TR>'
            % (fondo, celdaMarca,
               fondo, col, negrita,
               fondo, TENUE, escapar(tipo),
               fondo, celdaNn))

    return ('  %s [label=<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" '
            'CELLPADDING="3" COLOR="%s">%s</TABLE>>];\n'
            % (tabla, GRIS_LINEA, ''.join(filas)))


lineas = ['digraph SIGCO {',
          # De arriba hacia abajo: con 28 tablas, LR daba una imagen de 2000x6500
          # imposible de leer. TB la deja apaisada, que es como se imprime.
          '  rankdir=TB;',
          # Ruteo ortogonal: las relaciones salen en angulo recto, como en los
          # diagramas de base de datos, en vez de cruzar en diagonal.
          '  splines=ortho;',
          '  nodesep=0.7;',
          '  ranksep=1.6;',
          # Fusiona los tramos de aristas que van al mismo lugar: con 41 claves
          # foraneas, muchas apuntando a obra, sin esto la imagen es una maraña.
          '  concentrate=true;',
          '  bgcolor="white";',
          '  fontname="Segoe UI,Arial";',
          '  node [shape=plain, fontname="Segoe UI,Arial"];',
          '  edge [color="%s", penwidth=1.2, arrowsize=0.8,' % ACERO,
          '        arrowhead=normal, arrowtail=none];',
          '']

for modulo, sus in MODULOS.items():
    lineas.append('  subgraph cluster_%s {' % modulo.replace(' ', '_').replace('Ó', 'O'))
    lineas.append('    label="%s";' % modulo)
    lineas.append('    style="rounded"; color="%s"; fontsize=15; fontcolor="%s";'
                  % (GRIS_LINEA, TENUE))
    lineas.append('    margin=14;')
    for t in sus:
        if t in tablas:
            lineas.append(nodo(t).rstrip())
    lineas.append('  }')
    lineas.append('')

lineas.append('  /* Claves foraneas: de la columna FK a la PK que referencia. */')
for origen, colOrigen, destino, colDestino in RELACIONES:
    lineas.append('  %s:%s -> %s:%s;' % (origen, colOrigen, destino, colDestino))

lineas.append('}')

io.open(enDiagramas('erd.dot'), 'w', encoding='utf-8', newline='\n').write('\n'.join(lineas) + '\n')
print('erd.dot generado: %d tablas, %d columnas, %d relaciones'
      % (len(tablas), len(COLUMNAS), len(RELACIONES)))
