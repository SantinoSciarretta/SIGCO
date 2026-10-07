# Diagrama de Gantt final del proyecto, con las horas reales de la planilla.
# Las fechas del desarrollo siguen los commits de cada modulo, agrupadas en a lo
# sumo tres tramos por modulo para que el diagrama se lea de corrido.
from datetime import date, timedelta
from html import escape
from lucid import render, text_w

INICIO = date(2026, 3, 2)          # lunes de la semana 1
SEMANAS = 35                       # hasta el domingo 1 de noviembre
W = 1500
LX = 372                           # fin de la columna de actividades
X0, X1 = 392, 1372                 # area de barras
HX = 1468                          # columna de horas (alineada a la derecha)
WK = (X1 - X0) / SEMANAS
ROW, HEAD = 31, 40

ETAPAS = [
    ('Inicio y Análisis', '#2e8b57', 38, [
        ('Estudio de Prefactibilidad', 5, [('03-02', '03-31')]),
        ('Relevamiento', 9, [('03-16', '04-10')]),
        ('Entrevistas de validación adicionales', 2, [('04-06', '04-10')]),
        ('Redacción del Informe de Relevamiento', 16, [('03-23', '04-15')]),
        ('Identificación de requerimientos', 4, [('04-08', '04-14')]),
        ('Elaboración del Diagrama de Gantt', 2, [('04-13', '04-15')]),
    ]),
    ('Diseño', '#15918a', 50, [
        ('Desarrollo de la Propuesta Técnica', 28, [('04-13', '04-30')]),
        ('Desarrollo de la Propuesta Comercial', 8, [('04-20', '04-30')]),
        ('Modelo Relacional de Base de Datos', 10, [('05-04', '06-05')]),
        ('Normalización del modelo lógico', 4, [('06-08', '06-26')]),
    ]),
    ('Desarrollo', '#2176b5', 321, [
        ('Implementación de la BD (PostgreSQL)', 22, [('08-17', '08-28'), ('09-07', '09-18'), ('09-28', '10-06')]),
        ('Módulo Obras', 16, [('08-17', '08-27'), ('09-08', '09-18')]),
        ('Módulo Personal', 13, [('09-01', '09-09')]),
        ('Módulo Clientes', 14, [('08-17', '08-26')]),
        ('Módulo Presupuestación', 42, [('08-18', '09-04'), ('09-14', '10-06')]),
        ('Módulo Gastos', 20, [('09-01', '09-11')]),
        ('Módulo Seguimiento de Obras', 24, [('09-02', '09-11'), ('09-21', '10-06')]),
        ('Módulo Proveedores', 12, [('08-24', '09-02')]),
        ('Módulo Materiales', 8, [('08-19', '08-25')]),
        ('Módulo Compras', 30, [('09-03', '09-17'), ('09-23', '10-06')]),
        ('Módulo Cobros', 26, [('09-04', '09-15'), ('09-21', '10-01')]),
        ('Módulo Portfolio Web', 12, [('09-04', '09-11')]),
        ('Módulo Dashboard', 14, [('09-09', '09-18')]),
        # Usuarios y Accesos van al final: la seguridad se monta sobre los
        # modulos ya terminados (orden de desarrollo del proyecto).
        ('Módulo Usuarios', 16, [('09-19', '09-25')]),
        ('Módulo Accesos', 28, [('09-24', '10-03')]),
        ('Integración de módulos y debugging', 24, [('09-21', '10-06')]),
    ]),
    ('Pruebas y Documentación', '#e07b10', 59, [
        ('Pruebas funcionales', 24, [('08-18', '10-06')]),
        ('Corrección de errores detectados', 18, [('09-22', '10-06')]),
        ('Manual de usuario', 17, [('10-02', '10-07')]),
    ]),
    ('Cierre', '#c8205a', 17, [
        ('Presentación final del proyecto', 9, [('10-19', '10-23')]),
        ('Despliegue final en producción', 8, [('10-06', '10-07')]),
    ]),
]
MESES = [('MARZO', 3), ('ABRIL', 4), ('MAYO', 5), ('JUNIO', 6), ('JULIO', 7), ('AGOSTO', 8), ('SEPTIEMBRE', 9), ('OCTUBRE', 10)]


def dx(d):
    return X0 + (d - INICIO).days / 7 * WK


def dia(s):
    m, d = s.split('-')
    return date(2026, int(m), int(d))


def build():
    filas = sum(len(a) for _, _, _, a in ETAPAS)
    top = 214
    H = int(top + len(ETAPAS) * HEAD + filas * ROW + 16 * len(ETAPAS) + 40)
    o = [f'<rect width="{W}" height="{H}" fill="#faf9f6"/>',
         ('' if __import__('os').environ.get('SIN_TITULO') else f'<text x="36" y="58" font-size="38" font-weight="bold" fill="#1f1f1f">Diagrama de Gantt</text>'),
         ('' if __import__('os').environ.get('SIN_TITULO') else f'<text x="36" y="90" font-size="17" fill="#666">Proyecto terminado. Horas reales de la planilla de tiempos: 485 hs (estimadas: 450 hs)</text>')]
    # leyenda
    lx = 36
    for nombre, col, _, _ in ETAPAS:
        o.append(f'<rect x="{lx}" y="112" width="16" height="16" rx="3" fill="{col}"/>')
        o.append(f'<text x="{lx+24}" y="125" font-size="15" fill="#333">{escape(nombre)}</text>')
        lx += 24 + text_w(nombre, 15) + 26
    # meses y semanas
    for nombre, m in MESES:
        a = date(2026, m, 1)
        b = date(2026, m + 1, 1)
        xa, xb = max(dx(a), X0), min(dx(b), X1)
        o.append(f'<text x="{(xa+xb)/2:.1f}" y="168" font-size="15" font-weight="bold" fill="#444" text-anchor="middle" letter-spacing="0.5">{nombre}</text>')
    for s in [1] + list(range(5, SEMANAS + 1, 5)):
        x = X0 + (s - 1) * WK
        o.append(f'<text x="{x:.1f}" y="194" font-size="12" fill="#888" text-anchor="middle">S{s}</text>')
        o.append(f'<line x1="{x:.1f}" y1="204" x2="{x:.1f}" y2="{H-30}" stroke="#e4e2dc" stroke-width="1"/>')
    o.append(f'<line x1="{X0}" y1="204" x2="{X1}" y2="204" stroke="#cfcdc6" stroke-width="1.4"/>')
    o.append(f'<text x="{HX}" y="194" font-size="13" font-weight="bold" fill="#555" text-anchor="end">HORAS</text>')
    y = top
    for k, (nombre, col, sub, acts) in enumerate(ETAPAS):
        if k:
            o.append(f'<line x1="36" y1="{y-8}" x2="{HX}" y2="{y-8}" stroke="#dedcd5" stroke-width="1.2"/>')
        o.append(f'<text x="36" y="{y+24}" font-size="16" font-weight="bold" fill="{col}" letter-spacing="0.6">{escape(nombre.upper())}</text>')
        o.append(f'<text x="{HX}" y="{y+24}" font-size="16" font-weight="bold" fill="{col}" text-anchor="end">{sub} hs</text>')
        y += HEAD
        for act, hs, tramos in acts:
            cy = y + ROW / 2
            o.append(f'<text x="{LX}" y="{cy+6:.1f}" font-size="16" fill="#2b2b2b" text-anchor="end">{escape(act)}</text>')
            for a, b in tramos:
                xa, xb = dx(dia(a)), dx(dia(b) + timedelta(days=1))
                o.append(f'<rect x="{xa:.1f}" y="{cy-9:.1f}" width="{max(xb-xa, 5):.1f}" height="18" rx="3.5" fill="{col}"/>')
            o.append(f'<text x="{HX}" y="{cy+6:.1f}" font-size="16" fill="#444" text-anchor="end">{hs}</text>')
            y += ROW
        y += 16
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Arial, Helvetica, sans-serif">'
           + '\n'.join(o) + '</svg>')
    return svg, H


if __name__ == '__main__':
    svg, H = build()
    with open('gantt-final.svg', 'w', encoding='utf-8') as f:
        f.write(svg)
    render('gantt-final.svg', 'gantt-final.png', W, H, 2)
    print('gantt-final', W, H)
