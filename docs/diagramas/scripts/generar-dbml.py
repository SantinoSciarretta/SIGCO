# -*- coding: utf-8 -*-
"""
Genera el modelo de SIGCO en DBML, el lenguaje que entiende dbdiagram.io.

Igual que los otros generadores, lee el esquema REAL de PostgreSQL: el codigo
que sale de aca describe la base tal cual esta, no una version escrita a mano
que puede haber quedado vieja.

Uso:
    python generar-dbml.py

Deja el archivo en docs/diagramas/sigco.dbml. Se copia entero y se pega en
https://dbdiagram.io/d — el diagrama se arma solo.
"""
import io
import os
import re
import subprocess
import sys
from collections import OrderedDict

from descripciones import GENERICAS, D, TABLA, MODULOS

PSQL = r'C:\Program Files\PostgreSQL\17\bin\psql.exe'
ENTORNO = {'PGPASSWORD': 'sigco2026', 'SYSTEMROOT': r'C:\Windows'}

AQUI = os.path.dirname(os.path.abspath(__file__))
DIAGRAMAS = os.path.dirname(AQUI)


def consultar(sql):
    r = subprocess.run([PSQL, '-U', 'postgres', '-h', 'localhost', '-d', 'sigco_dev',
                        '-t', '-A', '-F', '|', '-c', sql],
                       capture_output=True, env=ENTORNO)
    salida = r.stdout.decode('utf-8', 'replace').replace('\r', '')
    return [[c.strip() for c in l.split('|')]
            for l in salida.split('\n') if l.strip()]


# ============================================================== esquema real ==

COLUMNAS = consultar("""
    SELECT c.table_name, c.column_name,
      CASE c.data_type
        WHEN 'character varying' THEN 'varchar('||c.character_maximum_length||')'
        WHEN 'numeric' THEN 'numeric('||c.numeric_precision||','||c.numeric_scale||')'
        WHEN 'timestamp without time zone' THEN 'timestamp'
        WHEN 'bigint' THEN CASE WHEN c.column_default LIKE 'nextval%'
                                THEN 'bigserial' ELSE 'bigint' END
        ELSE c.data_type END,
      c.is_nullable
    FROM information_schema.columns c
    WHERE c.table_schema='public' AND c.table_name <> 'flyway_schema_history'
    ORDER BY c.table_name, c.ordinal_position""")

# Clave primaria: se guarda la lista de columnas por tabla, en orden, porque
# las tablas intermedias tienen clave compuesta y en DBML se declara distinto.
PRIMARIA = OrderedDict()
for t, col in consultar("""
    SELECT tc.table_name, kcu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name=tc.constraint_name AND kcu.constraint_schema=tc.constraint_schema
    WHERE tc.table_schema='public' AND tc.constraint_type='PRIMARY KEY'
    ORDER BY tc.table_name, kcu.ordinal_position"""):
    PRIMARIA.setdefault(t, []).append(col)

RELACIONES = consultar("""
    SELECT tc.table_name, kcu.column_name, ccu.table_name, ccu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name=tc.constraint_name AND kcu.constraint_schema=tc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name=tc.constraint_name AND ccu.constraint_schema=tc.constraint_schema
    WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
    ORDER BY 1,2""")

# De los CHECK solo interesan los conjuntos cerrados de valores: son los que
# aportan algo al leer el diagrama (que estados admite cada tabla).
VALORES = {}
for t, definicion in consultar("""
    SELECT c.relname, pg_get_constraintdef(con.oid)
    FROM pg_constraint con
    JOIN pg_class c ON c.oid=con.conrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND con.contype='c'"""):
    d = definicion.replace('::character varying', '')
    d = re.sub(r'::[a-z]+(\[\])?', '', d)
    m = re.search(r'\((\w+)\) = ANY', d) or re.search(r'(\w+) = ANY', d)
    if m and 'ARRAY[' in d:
        valores = re.findall(r"'([^']*)'", d)
        if valores:
            VALORES[(t, m.group(1))] = valores

UNICOS = {}
for t, definicion in consultar("""
    SELECT tablename, indexdef FROM pg_indexes
    WHERE schemaname='public' AND indexdef LIKE 'CREATE UNIQUE%%'
      AND indexdef NOT LIKE '%%_pkey%%' AND indexdef NOT LIKE '%%pk_%%'
    ORDER BY tablename"""):
    cuerpo = definicion.split(' USING btree ', 1)[1].split(' WHERE ')[0].strip()
    cols = cuerpo.strip('()').replace('::text', '')
    for _ in range(4):
        cols = re.sub(r'lower\(\(?(\w+)\)?\)', r'\1', cols)
    UNICOS.setdefault(t, []).append([c.strip() for c in cols.split(',')])

tablas = OrderedDict()
for t, col, tipo, nulo in COLUMNAS:
    tablas.setdefault(t, []).append((col, tipo, nulo))


# ================================================================ armado DBML

def texto(s):
    """Escapa una descripcion para meterla como Note de DBML."""
    return s.replace('\\', '').replace("'", "\\'").replace('\n', ' ')


def limpiar(s):
    """Saca el marcado Markdown, que en dbdiagram.io se ve como caracteres sueltos."""
    s = re.sub(r'\*\*(.+?)\*\*', r'\1', s)
    s = re.sub(r'\*(.+?)\*', r'\1', s)
    return s.replace('`', '')


def describir(tabla, col):
    if (tabla, col) in D:
        return limpiar(D[(tabla, col)])
    if col in GENERICAS:
        return limpiar(GENERICAS[col])
    return ''


sal = []
w = sal.append

w('// ===========================================================================')
w('// SIGCO - Sistema Integral de Gestion de Obras | Granica SRL')
w('// Modelo entidad-relacion: %d tablas, %d columnas, %d claves foraneas.'
  % (len(tablas), len(COLUMNAS), len(RELACIONES)))
w('//')
w('// Generado desde el esquema real de PostgreSQL con')
w('// docs/diagramas/scripts/generar-dbml.py')
w('//')
w('// Para verlo: entrar a https://dbdiagram.io/d , borrar el ejemplo que trae')
w('// y pegar todo este archivo.')
w('// ===========================================================================')
w('')

for modulo, sus in MODULOS.items():
    presentes = [t for t in sus if t in tablas]
    if not presentes:
        continue
    w('')
    w('// ---------------------------------------------------------------------------')
    w('// %s' % modulo.upper())
    w('// ---------------------------------------------------------------------------')
    w('')

    for t in presentes:
        pk = PRIMARIA.get(t, [])
        compuesta = len(pk) > 1

        w('Table %s {' % t)
        for col, tipo, nulo in tablas[t]:
            atributos = []
            if col in pk and not compuesta:
                atributos.append('pk')
            elif nulo == 'NO':
                atributos.append('not null')

            nota = describir(t, col)
            valores = VALORES.get((t, col))
            # Los valores admitidos se agregan a la nota: en el diagrama se ven
            # al pasar el mouse, y es lo que mas se consulta. Se omiten si la
            # descripcion ya los enumera, para no repetirlos.
            if valores and not all(v in nota for v in valores):
                nota = ('%s Valores: %s.' % (nota, ' / '.join(valores))).strip()
            if nota:
                atributos.append("note: '%s'" % texto(nota))

            w('  %s %s%s' % (col, tipo,
                             ' [%s]' % ', '.join(atributos) if atributos else ''))

        # Clave compuesta e indices unicos: en DBML van en un bloque aparte.
        indices = []
        if compuesta:
            indices.append('    (%s) [pk]' % ', '.join(pk))
        for cols in UNICOS.get(t, []):
            if cols == pk:
                continue
            expr = cols[0] if len(cols) == 1 else '(%s)' % ', '.join(cols)
            indices.append('    %s [unique]' % expr)
        if indices:
            w('')
            w('  indexes {')
            for i in indices:
                w(i)
            w('  }')

        if t in TABLA:
            w('')
            w("  Note: '%s'" % texto(limpiar(TABLA[t])))
        w('}')
        w('')

w('')
w('// ---------------------------------------------------------------------------')
w('// RELACIONES  (de la clave foranea hacia la clave primaria que referencia)')
w('//')
w('// Todas son de muchos a uno: el simbolo > se lee "muchos de la izquierda')
w('// corresponden a uno de la derecha".')
w('// ---------------------------------------------------------------------------')
w('')
for origen, colOrigen, destino, colDestino in RELACIONES:
    w('Ref: %s.%s > %s.%s' % (origen, colOrigen, destino, colDestino))

w('')
w('')
w('// ---------------------------------------------------------------------------')
w('// AGRUPAMIENTO POR MODULO')
w('//')
w('// dbdiagram.io usa los grupos para acomodar juntas las tablas de cada modulo.')
w('// ---------------------------------------------------------------------------')
w('')
for modulo, sus in MODULOS.items():
    presentes = [t for t in sus if t in tablas]
    if not presentes:
        continue
    # El nombre del grupo no admite espacios ni acentos.
    nombre = (modulo.replace(' ', '_').replace('ó', 'o').replace('í', 'i'))
    w('TableGroup %s {' % nombre)
    for t in presentes:
        w('  %s' % t)
    w('}')
    w('')

destino = os.path.join(DIAGRAMAS, 'sigco.dbml')
io.open(destino, 'w', encoding='utf-8', newline='\n').write('\n'.join(sal) + '\n')
print('sigco.dbml: %d tablas, %d columnas, %d relaciones'
      % (len(tablas), len(COLUMNAS), len(RELACIONES)), file=sys.stderr)
