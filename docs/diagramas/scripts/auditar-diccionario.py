# -*- coding: utf-8 -*-
"""
Compara el Diccionario de Datos del informe contra el esquema real de
PostgreSQL, tabla por tabla y campo por campo.

Audita todas las tablas que existen en la base contra el Diccionario
pertenecen a modulos que todavia no se desarrollaron y no hay contra que
compararlas.
"""
import io
import re
import subprocess

INFORME = r'C:\Users\Santi\Documents\Proyecto SIGCO Seminario\docs\informe-sigco.md'
PSQL = r'C:\Program Files\PostgreSQL\17\bin\psql.exe'


# --------------------------------------------------------------- el informe
def leer_diccionario():
    """Devuelve {tabla: [(campo, tipo, clave), ...]} tal como figura en el doc."""
    texto = io.open(INFORME, encoding='utf-8').read()
    texto = texto[texto.index('# **Diccionario de Datos**'):]
    # Cortar al empezar la seccion siguiente: mas abajo el informe tiene
    # otras tablas (cronograma, costos) que no son del modelo de datos.
    fin = texto.find('# **Propuesta Comercial**')
    if fin > 0:
        texto = texto[:fin]
    texto = texto.replace('\\_', '_').replace('\\-', '-')

    tablas, actual = {}, None
    for linea in texto.split('\n'):
        m = re.match(r'\s*Tabla:\s*(\w+)\s*$', linea)
        if m:
            actual = m.group(1)
            tablas[actual] = []
            continue
        if actual and linea.startswith('|'):
            celdas = [c.strip() for c in linea.strip().strip('|').split('|')]
            if len(celdas) < 3:
                continue
            campo, tipo, clave = celdas[0], celdas[1], celdas[2]
            if campo in ('Campo', '-----') or set(campo) <= set('- '):
                continue
            tablas[actual].append((campo, tipo.upper().replace(' ', ''), clave))
    return tablas


# ------------------------------------------------------------------ la base
def consultar(sql):
    r = subprocess.run([PSQL, '-U', 'postgres', '-h', 'localhost', '-d', 'sigco_dev',
                        '-t', '-A', '-F', '|', '-c', sql],
                       capture_output=True, env={'PGPASSWORD': 'sigco2026',
                                                 'SYSTEMROOT': r'C:\Windows'})
    salida = r.stdout.decode('utf-8', 'replace')
    limpio = salida.replace(chr(13), '')
    return [[c.strip() for c in l.split('|')]
            for l in limpio.split(chr(10)) if l.strip()]


def leer_base():
    filas = consultar("""
        SELECT c.table_name, c.column_name,
          CASE c.data_type
            WHEN 'character varying' THEN 'VARCHAR('||c.character_maximum_length||')'
            WHEN 'numeric' THEN 'NUMERIC('||c.numeric_precision||','||c.numeric_scale||')'
            WHEN 'timestamp without time zone' THEN 'TIMESTAMP'
            WHEN 'bigint' THEN CASE WHEN c.column_default LIKE 'nextval%'
                                    THEN 'BIGSERIAL' ELSE 'BIGINT' END
            ELSE upper(c.data_type) END,
          CASE WHEN c.is_nullable='NO' THEN 'NOT NULL' ELSE '' END
        FROM information_schema.columns c
        WHERE c.table_schema='public' AND c.table_name <> 'flyway_schema_history'
        ORDER BY c.table_name, c.ordinal_position""")
    base = {}
    for t, col, tipo, nulo in filas:
        base.setdefault(t, []).append((col, tipo.replace(' ', ''), nulo))

    claves = {}
    for t, col, tipo in consultar("""
        SELECT tc.table_name, kcu.column_name, tc.constraint_type
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON kcu.constraint_name = tc.constraint_name
         AND kcu.constraint_schema = tc.constraint_schema
        WHERE tc.table_schema='public'
          AND tc.constraint_type IN ('PRIMARY KEY','FOREIGN KEY')"""):
        claves.setdefault((t, col), set()).add('PK' if tipo == 'PRIMARY KEY' else 'FK')
    return base, claves


# ------------------------------------------------------------------ compara
dicc = leer_diccionario()
base, claves = leer_base()

print('=' * 78)
print('AUDITORIA: Diccionario de Datos  vs  esquema real de PostgreSQL')
print('=' * 78)

hallazgos = []

for tabla in sorted(base):
    cols_base = base[tabla]
    if tabla not in dicc:
        hallazgos.append((tabla, '-', 'La tabla existe en la base pero NO esta en el Diccionario'))
        continue

    d = {c: (t, k) for c, t, k in dicc[tabla]}
    b = {c: (t, n) for c, t, n in cols_base}

    faltan_doc = [c for c, _, _ in cols_base if c not in d]
    sobran_doc = [c for c, _, _ in dicc[tabla] if c not in b]

    for c in faltan_doc:
        hallazgos.append((tabla, c, 'EN LA BASE pero NO en el Diccionario'))
    for c in sobran_doc:
        hallazgos.append((tabla, c, 'EN EL DICCIONARIO pero NO en la base'))

    for c, (tipo_b, _) in b.items():
        if c in d:
            tipo_d = d[c][0]
            if tipo_d != tipo_b:
                hallazgos.append((tabla, c, 'TIPO distinto: doc=%s  base=%s' % (tipo_d, tipo_b)))
            clave_real = claves.get((tabla, c), set())
            clave_doc = set(x.strip() for x in d[c][1].replace(',', ' ').split() if x.strip())
            if clave_real != clave_doc:
                hallazgos.append((tabla, c, 'CLAVE distinta: doc=%s  base=%s'
                                  % (','.join(sorted(clave_doc)) or '-',
                                     ','.join(sorted(clave_real)) or '-')))

no_creadas = sorted(set(dicc) - set(base))

if hallazgos:
    print('\nDIFERENCIAS (%d):\n' % len(hallazgos))
    for t, c, d_ in hallazgos:
        print('  %-22s %-22s %s' % (t, c, d_))
else:
    print('\nSin diferencias: las %d tablas coinciden exactamente con el Diccionario.' % len(base))

print('\n' + '-' * 78)
print('TABLAS AUDITADAS (ya creadas): %d' % len(base))
print('  ' + ', '.join(sorted(base)))
print('\nTABLAS DEL DICCIONARIO TODAVIA NO CREADAS: %d' % len(no_creadas))
print('  ' + ', '.join(no_creadas))
print('=' * 78)
