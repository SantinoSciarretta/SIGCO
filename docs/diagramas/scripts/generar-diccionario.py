# -*- coding: utf-8 -*-
"""
Genera el Diccionario de Datos de SIGCO a partir del esquema REAL de PostgreSQL.

El documento no se escribe a mano: se arma leyendo la base. Si una migracion
agrega una columna y nadie actualiza el diccionario, el diccionario queda mal.
Generandolo desde el esquema, eso no puede pasar.

Lo unico escrito a mano son las descripciones de negocio (que significa cada
campo), porque eso no vive en la base.

Uso:  python generar-diccionario.py > diccionario-de-datos.md
"""
import io
import re
import subprocess
import sys
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
        WHEN 'character varying' THEN 'VARCHAR('||c.character_maximum_length||')'
        WHEN 'numeric' THEN 'NUMERIC('||c.numeric_precision||','||c.numeric_scale||')'
        WHEN 'timestamp without time zone' THEN 'TIMESTAMP'
        WHEN 'bigint' THEN CASE WHEN c.column_default LIKE 'nextval%'
                                THEN 'BIGSERIAL' ELSE 'BIGINT' END
        ELSE upper(c.data_type) END,
      c.is_nullable, coalesce(c.column_default,'')
    FROM information_schema.columns c
    WHERE c.table_schema='public' AND c.table_name <> 'flyway_schema_history'
    ORDER BY c.table_name, c.ordinal_position""")

CLAVES = {}
for t, col, tipo in consultar("""
    SELECT tc.table_name, kcu.column_name, tc.constraint_type
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name=tc.constraint_name AND kcu.constraint_schema=tc.constraint_schema
    WHERE tc.table_schema='public' AND tc.constraint_type IN ('PRIMARY KEY','FOREIGN KEY')"""):
    CLAVES.setdefault((t, col), set()).add('PK' if tipo == 'PRIMARY KEY' else 'FK')

REFERENCIA = {}
for o, co, d, cd in consultar("""
    SELECT tc.table_name, kcu.column_name, ccu.table_name, ccu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name=tc.constraint_name AND kcu.constraint_schema=tc.constraint_schema
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name=tc.constraint_name AND ccu.constraint_schema=tc.constraint_schema
    WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'"""):
    REFERENCIA[(o, co)] = (d, cd)

RESTRICCIONES = {}
for t, nombre, definicion in consultar("""
    SELECT c.relname, con.conname, pg_get_constraintdef(con.oid)
    FROM pg_constraint con
    JOIN pg_class c ON c.oid=con.conrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND con.contype='c'
    ORDER BY c.relname, con.conname"""):
    RESTRICCIONES.setdefault(t, []).append(definicion)

UNICOS = {}
for t, definicion in consultar("""
    SELECT tablename, indexdef FROM pg_indexes
    WHERE schemaname='public' AND indexdef LIKE 'CREATE UNIQUE%%'
      AND indexdef NOT LIKE '%%_pkey%%' AND indexdef NOT LIKE '%%pk_%%'
    ORDER BY tablename"""):
    UNICOS.setdefault(t, []).append(definicion)

tablas = OrderedDict()
for t, col, tipo, nulo, defecto in COLUMNAS:
    tablas.setdefault(t, []).append((col, tipo, nulo, defecto))


# ==================================================== traduccion de reglas ====

def sin_parentesis_externos(d):
    """Saca los parentesis que envuelven toda la expresion, si los hay."""
    while d.startswith('(') and d.endswith(')'):
        nivel = 0
        for i, c in enumerate(d):
            nivel += (c == '(') - (c == ')')
            if nivel == 0 and i < len(d) - 1:
                return d          # el primer parentesis cierra antes del final
        d = d[1:-1].strip()
    return d


def normalizar(definicion):
    """Deja el CHECK en una forma limpia y comparable.

    PostgreSQL devuelve la definicion con casteos y parentesis de sobra:
        CHECK (((estado)::text = ANY ((ARRAY['Activo'::character varying])::text[])))
    Esto lo reduce a:
        estado = ANY (ARRAY['Activo'])
    """
    # 'character varying' es el unico tipo de dos palabras que aparece; sacar
    # `::[a-z ]+` de una se comia tambien el espacio previo al operador.
    d = definicion.replace('::character varying', '')
    d = re.sub(r'::[a-z]+(\[\])?', '', d)
    d = d.replace('CHECK ', '').strip()
    for _ in range(8):
        d = re.sub(r'\((\w+)\)', r'\1', d)                   # (estado) -> estado
        d = re.sub(r'\((\d+)\)', r'\1', d)                   # (0) -> 0
        # (fecha_pago IS NOT NULL) -> fecha_pago IS NOT NULL
        d = re.sub(r'\((\w+ IS (?:NOT )?NULL)\)', r'\1', d)
        # (monto > 0) -> monto > 0
        d = re.sub(r'\((\w+ (?:>=|<=|<>|>|<|=) [^()]+?)\)', r'\1', d)
    d = re.sub(r'ANY \(+ARRAY\[', 'ANY (ARRAY[', d)
    d = re.sub(r'\]\)+', '])', d)
    # Colapsar los cierres del ARRAY puede haberse llevado parentesis que no
    # eran suyos: se reponen los que falten para que la expresion cierre.
    faltan = d.count('(') - d.count(')')
    d += ')' * max(0, faltan)
    d = sin_parentesis_externos(d)
    # (estado = ANY (ARRAY[...])) -> estado = ANY (ARRAY[...])
    for _ in range(3):
        d = re.sub(r'\((\w+ = ANY \(ARRAY\[[^\]]*\]\))\)', r'\1', d)
    return sin_parentesis_externos(d)


def lista(texto):
    return ' / '.join(re.findall(r"'([^']*)'", texto))


def legible(definicion):
    """Pasa un CHECK de PostgreSQL a una frase entendible."""
    d = normalizar(definicion)

    # Conjunto cerrado de valores.
    m = re.match(r"^(\w+) = ANY \(ARRAY\[(.+)\]\)$", d)
    if m:
        return "`%s` solo admite: %s" % (m.group(1), lista(m.group(2)))

    m = re.match(r"^(\w+) IS NULL OR (\w+) = ANY \(ARRAY\[(.+)\]\)$", d)
    if m:
        return "`%s`, si se completa, solo admite: %s" % (m.group(1), lista(m.group(3)))

    # Campo obligatorio solo en cierto estado.
    m = re.match(r"^(\w+) <> '([^']+)' OR (\w+) IS NOT NULL$", d)
    if m:
        return ("`%s` es obligatorio cuando `%s` = **%s**"
                % (m.group(3), m.group(1), m.group(2)))

    # El proveedor recien es obligatorio cuando el pedido sale de aprobacion.
    m = re.match(r"^(\w+) = '([^']+)' OR \w+ = '([^']+)' OR (\w+) IS NOT NULL$", d)
    if m:
        return ("`%s` es obligatorio salvo que `%s` sea **%s** o **%s**"
                % (m.group(4), m.group(1), m.group(2), m.group(3)))

    # Una fecha no puede ser anterior a la otra.
    m = re.match(r"^(\w+) IS NULL OR (\w+) IS NULL OR (\w+) >= (\w+)$", d)
    if m and m.group(3) != m.group(4):
        return "`%s` no puede ser anterior a `%s`" % (m.group(3), m.group(4))
    m = re.match(r"^(\w+) IS NULL OR (\w+) >= (\w+)$", d)
    if m and not m.group(3).isdigit():
        return "`%s` no puede ser anterior a `%s`" % (m.group(2), m.group(3))

    # Autorreferencia: nada puede derivar de si mismo.
    m = re.match(r"^(\w+) IS NULL OR (\w+) <> (\w+)$", d)
    if m:
        return "`%s` no puede apuntar al propio registro" % m.group(2)

    # Rangos numericos.
    m = re.match(r"^(\w+) > 0 AND \w+ <= (\d+)$", d)
    if m:
        return "`%s` tiene que estar entre 0 (exclusive) y %s" % (m.group(1), m.group(2))
    m = re.match(r"^(\w+) IS NULL OR \(?(\w+) >= 0 AND \w+ <= (\d+)\)?$", d)
    if m:
        return "`%s`, si se completa, tiene que estar entre 0 y %s" % (m.group(1), m.group(3))

    m = re.match(r"^(\w+) IS NULL OR (\w+) >= (\d+)$", d)
    if m:
        return ("`%s`, si se completa, no puede ser negativo" % m.group(1)
                if m.group(3) == '0'
                else "`%s`, si se completa, no puede ser menor a %s" % (m.group(1), m.group(3)))

    m = re.match(r"^(\w+) > 0$", d)
    if m:
        return "`%s` tiene que ser mayor a cero" % m.group(1)
    m = re.match(r"^(\w+) >= 0$", d)
    if m:
        return "`%s` no puede ser negativo" % m.group(1)
    m = re.match(r"^(\w+) >= (\d+)$", d)
    if m:
        return "`%s` no puede ser menor a %s" % (m.group(1), m.group(2))

    return '`%s`' % d


def unico_legible(definicion):
    """Pasa un indice unico a una frase entendible."""
    cuerpo = definicion.split(' USING btree ', 1)[1]
    parcial = ' (solo cuando está cargado)' if ' WHERE ' in cuerpo else ''
    cuerpo = cuerpo.split(' WHERE ')[0].strip()
    cuerpo = sin_parentesis_externos(cuerpo)

    # lower() en el indice significa que la unicidad no distingue mayusculas:
    # "Albañilería" y "albañilería" cuentan como el mismo rubro.
    sin_mayusculas = 'lower(' in cuerpo
    cols = cuerpo.replace('::text', '')
    for _ in range(4):
        cols = re.sub(r'lower\(\(?(\w+)\)?\)', r'\1', cols)

    return ('No se repite `%s`%s%s'
            % (cols, ', sin distinguir mayúsculas' if sin_mayusculas else '',
               parcial))


# ====================================================== descripciones a mano ==

# Viven en su propio modulo porque tambien las usa generar-dbml.py.
from descripciones import GENERICAS, D, TABLA, MODULOS

def describir(tabla, col):
    if (tabla, col) in D:
        return D[(tabla, col)]
    if col in GENERICAS:
        return GENERICAS[col]
    if col in REFERENCIA.get(tabla, {}):
        pass
    if (tabla, col) in REFERENCIA:
        return 'Referencia a `%s`.' % REFERENCIA[(tabla, col)][0]
    return ''


# ================================================================ el documento

sal = []
w = sal.append

w('# Diccionario de Datos — SIGCO')
w('')
w('**Sistema Integral de Gestión de Obras — Granica SRL**  ')
w('Santino Sciarretta · Proyecto Integrador Profesional · Ingeniería Informática')
w('')
w('---')
w('')
w('## Cómo se generó este documento')
w('')
w('Este diccionario **no está escrito a mano**: se genera leyendo el esquema real')
w('de la base de datos `sigco_dev` en PostgreSQL 17 (`docs/diagramas/scripts/')
w('generar-diccionario.py`).')
w('')
w('La razón es simple: un diccionario escrito aparte queda desactualizado apenas')
w('una migración agrega una columna y nadie se acuerda de actualizarlo.')
w('Generándolo desde la base, los tipos, los tamaños, las claves y las')
w('restricciones que figuran acá **son exactamente los que la base tiene hoy**.')
w('')
w('Lo único escrito a mano son las descripciones de negocio, porque el')
w('significado de un campo no vive en el esquema.')
w('')
w('El esquema, a su vez, lo define Flyway con migraciones versionadas')
w('(`backend/src/main/resources/db/migration/`), y Hibernate está configurado en')
w('`ddl-auto=validate`: **la aplicación no crea ni modifica tablas**, solo verifica')
w('al arrancar que las entidades coincidan con la base. Si no coinciden, no')
w('levanta.')
w('')
w('## Diagrama entidad-relación')
w('')
w('![Diagrama entidad-relación de SIGCO](img/entidad-relacion-tablas.png)')
w('')
w('Las 28 tablas con sus columnas y las 41 claves foráneas que las vinculan.')
w('Cada recuadro es una tabla, con su nombre en la cabecera; las columnas')
w('marcadas `PK` son clave primaria y las `FK`, clave foránea, y de ellas salen')
w('las flechas hacia la tabla que referencian.')
w('')
w('La imagen se genera con `scripts/generar-erd.py`, que también lee la base')
w('real. Hay una versión vectorial en `img/entidad-relacion-tablas.svg` para')
w('imprimir o hacer zoom sin que se pixele.')
w('')
w('---')
w('')
w('## Convenciones')
w('')
w('| Convención | Criterio |')
w('| --- | --- |')
w('| Idioma | Tablas y columnas en español, igual que en la documentación entregada. |')
w('| Nombres | `snake_case` en la base, `PascalCase` en las clases Java. |')
w('| Claves primarias | `BIGSERIAL` autoincremental, siempre `id_<entidad>`. |')
w('| Importes | `NUMERIC` en la base, `BigDecimal` en Java. **Nunca `double`**, por los errores de redondeo. |')
w('| Fechas | `DATE` cuando solo importa el día, `TIMESTAMP` cuando importa el momento exacto. |')
w('| Estados | `VARCHAR` con una restricción `CHECK` que los limita a un conjunto cerrado. |')
w('| Imágenes | La base guarda **la referencia** al archivo en Supabase Storage, nunca el binario. |')
w('| Bajas | Casi nada se elimina: se desactiva, se cancela o se anula, para no perder la trazabilidad. |')
w('')
w('**Columna "Nulo":** `NO` significa que el campo es obligatorio.  ')
w('**Columna "Clave":** `PK` clave primaria, `FK` clave foránea, `PK, FK` ambas')
w('(típico de las tablas intermedias).')
w('')
w('---')
w('')
w('## Índice')
w('')
for modulo, sus in MODULOS.items():
    presentes = [t for t in sus if t in tablas]
    w('- **%s** — %s' % (modulo, ', '.join('`%s`' % t for t in presentes)))
w('')
w('**%d tablas · %d columnas · %d claves foráneas.**'
  % (len(tablas), len(COLUMNAS), len(REFERENCIA)))
w('')
w('---')

for modulo, sus in MODULOS.items():
    w('')
    w('## Módulo %s' % modulo)
    for t in sus:
        if t not in tablas:
            continue
        w('')
        w('### `%s`' % t)
        w('')
        if t in TABLA:
            w(TABLA[t])
            w('')
        w('| Columna | Tipo | Nulo | Clave | Referencia | Descripción |')
        w('| --- | --- | :-: | :-: | --- | --- |')
        for col, tipo, nulo, defecto in tablas[t]:
            claves = CLAVES.get((t, col), set())
            marca = ', '.join(x for x in ('PK', 'FK') if x in claves)
            ref = REFERENCIA.get((t, col))
            refTexto = '`%s.%s`' % ref if ref else ''
            w('| `%s` | %s | %s | %s | %s | %s |'
              % (col, tipo, 'NO' if nulo == 'NO' else 'Sí', marca, refTexto,
                 describir(t, col)))

        reglas = [legible(d) for d in RESTRICCIONES.get(t, [])]
        reglas += [unico_legible(d) for d in UNICOS.get(t, [])]
        if reglas:
            w('')
            w('**Reglas que impone la base:**')
            w('')
            for r in sorted(set(reglas)):
                w('- %s' % r)

    w('')
    w('---')

w('')
w('## Diferencias respecto del Diccionario de la Propuesta Técnica')
w('')
w('El Diccionario original se escribió durante el análisis, antes de programar.')
w('Al implementarlo aparecieron ocho puntos donde el modelo no alcanzaba para')
w('sostener una regla que el propio informe pide. Cada cambio es una migración')
w('de Flyway, y está justificado en el `.md` del módulo correspondiente.')
w('')
w('**Los ocho ya están incorporados al informe** (`docs/informe-sigco.md`), así')
w('que el Diccionario de la Propuesta Técnica y el esquema real coinciden campo')
w('por campo. Se verifica con `scripts/auditar-diccionario.py`, que compara las')
w('dos cosas y hoy no reporta diferencias en ninguna de las 28 tablas. Esta')
w('tabla queda como registro de qué se cambió y por qué.')
w('')
w('| Migración | Cambio | Por qué |')
w('| --- | --- | --- |')
w('| `V7` | `item_presupuesto.id_material` | El informe dice que los ítems se eligen del catálogo de Materiales, pero no había columna para guardar cuál: la descripción quedaba como texto libre, que es el problema que Materiales resuelve. |')
w('| `V9` | `pedido.id_proveedor` pasa a opcional | El dueño elige el proveedor **al aprobar** el pedido, no al crearlo. Exigirlo antes obligaba a inventar un dato. |')
w('| `V9` | `pedido.motivo_anulacion` | No había dónde registrar por qué se anuló un pedido. |')
w('| `V9` | `pedido_material.precio_unitario` | Al recibir un pedido se genera el gasto automáticamente, y no había de dónde sacar el importe. |')
w('| `V10` | `gasto.estado`: Registrado → Confirmado | El informe usa Confirmado / Anulado. |')
w('| `V11` | `operario_obra.fecha_desasignacion` | Desasignar borrando la fila destruía el historial que el informe pide conservar al dar de baja a un operario. |')
w('| `V12` | `cuota.motivo_anulacion` | Una cuota abonada se anula dejando motivo, y no había columna. |')
w('| `V12` | `publicacion_portfolio.estado` queda en dos valores | El informe enumera Publicada / Despublicada; el tercer estado no agregaba nada. |')
w('')
w('Fuera de eso, **las 28 tablas, sus nombres, sus tipos y sus claves son los')
w('del Diccionario original**: se verificó columna por columna contra el')
w('documento entregado.')
w('')

io.open(enDiagramas('diccionario-de-datos.md'), 'w', encoding='utf-8', newline='\n').write(
    '\n'.join(sal) + '\n')
print('diccionario-de-datos.md: %d tablas, %d columnas, %d FK'
      % (len(tablas), len(COLUMNAS), len(REFERENCIA)), file=sys.stderr)
