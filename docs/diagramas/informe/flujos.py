# Diagramas de flujo de los procesos centrales de SIGCO, para el informe.
# Uso: python flujos.py [nombre ...]   (sin argumentos genera todos)
#
# Cada proceso va en dos partes unidas por un conector (circulo con una letra):
# completo en una sola figura no se leeria en una hoja. Los pasos y las
# validaciones salen del codigo de cada servicio (ObraService, PresupuestoService,
# PedidoService, GastoService, SeguimientoService y CobrosService).
#
# Disposicion, como en Lucidchart: una columna principal con el camino normal,
# una columna a la derecha para las ramas y un carril a la izquierda para los
# retornos (flecha punteada). Las notas van del lado que queda libre.
import sys
from lucid import Diagram

LEYENDA = [('user', 'Acción de una persona'), ('sys', 'Acción automática del sistema'),
           ('conn', 'Conector con la otra parte')]
X, XR, XN = 560, 975, 182          # columna principal, derecha y de notas a la izquierda
W, WR, WN = 430, 330, 260
LANE, LANE_LEJOS = 105, 34          # carriles del retorno (el lejano deja lugar a notas)
TOP, GAP = 128, 56


def nuevo(titulo, parte):
    d = Diagram(1200, 100, f'{titulo} (parte {parte} de 2)')
    d.legend(30, 74, LEYENDA)
    return d


def stub(d, nodo, dy=28):
    return d.port(nodo, 't')[1] - dy


def cadena(d, *nombres):
    for a, b in zip(nombres, nombres[1:]):
        d.edge(a, 'b', b, 't')


def unir(d, desde, hacia):
    """Una rama lateral vuelve al camino principal justo arriba de 'hacia'."""
    x = d.nodes[desde]['x']
    d.edge(desde, 'b', hacia, 't', via=[(x, stub(d, hacia)), (X, stub(d, hacia))])


def nota(d, nombre, ref, texto, lado='l', angosta=False):
    if lado == 'l':
        x, w = (XN + 18, WN - 40) if angosta else (XN, WN)
    else:
        x, w = XR, WR
    d.note(nombre, x, 0, texto, w)
    d.align(nombre, ref)
    if lado == 'l':
        d.edge(ref, 'l', nombre, 'r', kind='note', arrow=False)
    else:
        d.edge(ref, 'r', nombre, 'l', kind='note', arrow=False)


def retorno(d, desde, hacia, carril=LANE, label='No'):
    d.edge(desde, 'l', hacia, 'l', via=[(carril, d.nodes[desde]['y']), (carril, d.nodes[hacia]['y'])],
           label=label, dashed=True)


# ======================================================================
#  1. Ciclo de vida de la obra
# ======================================================================

def obra_a():
    d = nuevo('Ciclo de vida de la obra', 1)
    d.term('ini', X, 0, 'INICIO')
    d.box('u0', X, 0, 'Llega la consulta de un cliente y el dueño abre el alta de una obra nueva', 'user', W)
    d.diamond('d1', X, 0, '¿El cliente ya está cargado?')
    d.box('u1', X, 0, 'Elige el cliente y carga la dirección, el tipo de inmueble y el tipo de obra', 'user', W)
    d.box('u2', X, 0, 'Si ya lo sabe, indica cuándo estima arrancar y cuántos meses va a durar', 'user', W)
    d.box('s1', X, 0, 'El sistema guarda la obra "En presupuestación" y, si hay plazo, calcula la fecha de fin estimada', 'sys', W)
    d.box('u3', X, 0, 'El dueño arma los presupuestos y se los manda al cliente', 'user', W)
    d.diamond('d2', X, 0, '¿El cliente aprobó el presupuesto definitivo?', w=340, h=170)
    d.box('s2', X, 0, 'La obra pasa sola a "En ejecución"', 'sys', W, minh=76)
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 'u0', 'd1', 'u1', 'u2', 's1', 'u3', 'd2', 's2', 'cA'], TOP, GAP)

    d.box('uc', XR, 0, 'Lo da de alta desde el mismo formulario, con sus datos y quién lo recomendó', 'user', WR)
    d.align('uc', 'd1')
    d.diamond('d3', XR, 0, '¿Sigue la negociación?', w=270, h=150)
    d.align('d3', 'd2')
    d.box('u4', XR, 0, 'El dueño cancela la obra e indica el motivo', 'user', WR)
    d.box('s3', XR, 0, 'La obra queda "Cancelada"', 'sys', WR, minh=70)
    d.term('fin', XR, 0, 'FIN', w=180, h=70)
    d.stack(['u4', 's3', 'fin'], d.nodes['d3']['y'] + 75 + GAP, GAP)

    cadena(d, 'ini', 'u0', 'd1')
    d.edge('d1', 'r', 'uc', 'l', label='No')
    unir(d, 'uc', 'u1')
    d.edge('d1', 'b', 'u1', 't', label='Sí')
    cadena(d, 'u1', 'u2', 's1', 'u3', 'd2')
    d.edge('d2', 'r', 'd3', 'l', label='No')
    d.edge('d3', 't', 'u3', 'r', label='Sí', dashed=True)
    d.edge('d3', 'b', 'u4', 't', label='No')
    cadena(d, 'u4', 's3', 'fin')
    d.edge('d2', 'b', 's2', 't', label='Sí')
    cadena(d, 's2', 'cA')
    nota(d, 'n1', 'u1', 'El tipo de obra se puede corregir mientras la obra no tenga presupuestos')
    nota(d, 'n2', 'u3', 'Ver el Proceso de Presupuestación')
    d.fit_height()
    return d


def obra_b():
    d = nuevo('Ciclo de vida de la obra', 2)
    d.conn('cA', X, 0, 'A')
    d.box('u1', X, 0, 'El dueño carga la fecha real de inicio', 'user', W, minh=76)
    d.box('s1', X, 0, 'El sistema recalcula la fecha de fin estimada desde el inicio real', 'sys', W)
    d.box('u2', X, 0, 'Durante la obra se cargan las etapas, los pedidos, los gastos y los cobros', 'user', W)
    d.diamond('d1', X, 0, '¿Hay que interrumpir la obra?')
    d.diamond('d2', X, 0, '¿Se completó la última etapa?')
    d.box('s2', X, 0, 'La obra pasa a "Finalizada" y sus etapas quedan bloqueadas', 'sys', W)
    d.box('u3', X, 0, 'El dueño revisa el balance de cierre: ganancia estimada, resultado de caja y saldo por cobrar', 'user', W)
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 'u1', 's1', 'u2', 'd1', 'd2', 's2', 'u3', 'fin'], TOP + 40, GAP)

    d.box('ui', XN, 0, 'El dueño la cancela con el motivo y confirma que interrumpe una obra en marcha', 'user', WN)
    d.box('si', XN, 0, 'La obra queda "Cancelada" y se registra en la auditoría', 'sys', WN)
    d.term('fin2', XN, 0, 'FIN', w=170, h=70)
    d.stack(['ui', 'si', 'fin2'], d.nodes['d1']['y'] - d.nodes['ui']['h'] / 2, 46)
    d.diamond('d3', XR, 0, '¿El dueño la da por terminada igual?', w=290, h=160)
    d.align('d3', 'd2')
    d.box('uf', XR, 0, 'Confirma que quedan etapas sin completar', 'user', WR)
    d.align('uf', 's2')

    cadena(d, 'cA', 'u1', 's1', 'u2', 'd1')
    d.edge('d1', 'l', 'ui', 'r', label='Sí')
    cadena(d, 'ui', 'si', 'fin2')
    d.edge('d1', 'b', 'd2', 't', label='No')
    d.edge('d2', 'b', 's2', 't', label='Sí')
    d.edge('d2', 'r', 'd3', 'l', label='No')
    d.edge('d3', 'b', 'uf', 't', label='Sí')
    d.edge('uf', 'l', 's2', 'r')
    d.edge('d3', 't', 'u2', 'r', label='No', dashed=True)
    cadena(d, 's2', 'u3', 'fin')
    nota(d, 'n1', 'u1', 'Solo se puede cargar con el presupuesto definitivo aprobado')
    nota(d, 'n2', 'u3', 'Cerrar la obra no cancela lo que falta cobrar')
    d.fit_height()
    return d


# ======================================================================
#  2. Presupuestacion
# ======================================================================

def presupuestacion_a():
    d = nuevo('Proceso de Presupuestación', 1)
    d.term('ini', X, 0, 'INICIO')
    d.note('n0', XR, 0, 'La obra tiene que estar "En presupuestación"', WR)
    d.diamond('d1', X, 0, '¿Hace falta dar primero un precio estimativo?', w=340, h=170)
    d.diamond('d2', X, 0, '¿Es una reforma que necesita anteproyecto?', w=340, h=170)
    d.box('u1', X, 0, 'Crea el presupuesto definitivo', 'user', W, minh=76)
    d.diamond('d3', X, 0, '¿Parte de una versión anterior?')
    d.box('s1', X, 0, 'El sistema numera la versión y toma el plazo de la duración cargada en la obra', 'sys', W)
    d.box('u2', X, 0, 'Elige un rubro y completa su planilla: cantidad y precio de los materiales del catálogo', 'user', W)
    d.diamond('d4', X, 0, '¿Falta algo que no está en el catálogo?', w=330)
    d.box('u3', X, 0, 'Carga la mano de obra, con un total por rubro y subrubro, y los imprevistos, con un porcentaje sobre cada rubro', 'user', W)
    d.box('s2', X, 0, 'El sistema recalcula en el momento los subtotales, los honorarios, el IVA (salvo la mano de obra) y el total', 'sys', W)
    d.diamond('d5', X, 0, '¿Quedan rubros por completar?')
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 'd1', 'd2', 'u1', 'd3', 's1', 'u2', 'd4', 'u3', 's2', 'd5', 'cA'], TOP, 50)

    d.box('uc', XR, 0, 'Arma la cotización inicial con los m² y el valor por m², y el sistema calcula el precio', 'user', WR)
    d.align('uc', 'd1')
    d.box('ua', XR, 0, 'Arma el anteproyecto por rubro, con los planos del arquitecto', 'user', WR)
    d.align('ua', 'd2')
    d.conn('cB', XR, 0, 'B')
    d.align('cB', 'u1')
    d.box('sd', XR, 0, 'El sistema copia sus ítems en un presupuesto nuevo, sin tocar el original', 'sys', WR)
    d.align('sd', 'd3')
    d.box('us', XR, 0, 'Lo agrega como ítem suelto, con su rubro y su unidad', 'user', WR)
    d.align('us', 'd4')

    cadena(d, 'ini', 'd1')
    d.edge('d1', 'r', 'uc', 'l', label='Sí')
    unir(d, 'uc', 'd2')
    d.edge('d1', 'b', 'd2', 't', label='No')
    d.edge('d2', 'r', 'ua', 'l', label='Sí')
    unir(d, 'ua', 'u1')
    d.edge('d2', 'b', 'u1', 't', label='No')
    d.edge('cB', 'l', 'u1', 'r')
    cadena(d, 'u1', 'd3')
    d.edge('d3', 'r', 'sd', 'l', label='Sí')
    unir(d, 'sd', 's1')
    d.edge('d3', 'b', 's1', 't', label='No')
    cadena(d, 's1', 'u2', 'd4')
    d.edge('d4', 'r', 'us', 'l', label='Sí')
    unir(d, 'us', 'u3')
    d.edge('d4', 'b', 'u3', 't', label='No')
    cadena(d, 'u3', 's2', 'd5')
    retorno(d, 'd5', 'u2', LANE_LEJOS, label='Sí')
    d.edge('d5', 'b', 'cA', 't', label='No')
    d.align('n0', 'ini')
    d.edge('ini', 'r', 'n0', 'l', kind='note', arrow=False)
    nota(d, 'n1', 'd2', 'En una construcción nueva el sistema no habilita el anteproyecto', angosta=True)
    nota(d, 'n2', 'u3', 'El monto de los imprevistos se recalcula solo cuando cambia el rubro', angosta=True)
    d.fit_height()
    return d


def presupuestacion_b():
    d = nuevo('Proceso de Presupuestación', 2)
    d.conn('cA', X, 0, 'A')
    d.box('u1', X, 0, 'El dueño define el porcentaje de honorarios, el anticipo y la cantidad de cuotas', 'user', W)
    d.diamond('d1', X, 0, '¿Anticipo y cuotas cierran al 100% del total?', w=340, h=170)
    d.box('u2', X, 0, 'Descarga el PDF con membrete y se lo manda al cliente por fuera del sistema', 'user', W)
    d.box('s1', X, 0, 'Al marcarlo enviado, el sistema verifica que tenga ítems y lo bloquea para edición', 'sys', W)
    d.diamond('d2', X, 0, '¿El cliente lo aprueba?')
    d.box('s2', X, 0, 'Queda "Aprobado", se audita y la obra pasa sola a "En ejecución"', 'sys', W)
    d.box('s3', X, 0, 'Se habilitan Gastos, Compras, Seguimiento y Cobros para la obra', 'sys', W)
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 'u1', 'd1', 'u2', 's1', 'd2', 's2', 's3', 'fin'], TOP + 40, GAP)

    d.box('sx', XR, 0, 'El sistema no lo guarda y pide corregirlo', 'sys', WR)
    d.align('sx', 'd1')
    d.conn('cB', XR, 0, 'B')
    d.align('cB', 'u2')
    d.box('u3', XR, 0, 'Arma una versión nueva tomando la anterior como base', 'user', WR)
    d.align('u3', 's1')
    d.diamond('d3', XR, 0, '¿Pide cambios?', w=260, h=140)
    d.align('d3', 'd2')
    d.box('s4', XR, 0, 'Queda "Rechazado"', 'sys', WR, minh=70)
    d.align('s4', 's3')

    cadena(d, 'cA', 'u1', 'd1')
    d.edge('d1', 'r', 'sx', 'l', label='No')
    d.edge('sx', 't', 'u1', 'r', dashed=True)
    d.edge('d1', 'b', 'u2', 't', label='Sí')
    cadena(d, 'u2', 's1', 'd2')
    d.edge('d2', 'b', 's2', 't', label='Sí')
    d.edge('d2', 'r', 'd3', 'l', label='No')
    d.edge('d3', 't', 'u3', 'b', label='Sí')
    d.edge('u3', 't', 'cB', 'b')
    d.edge('d3', 'b', 's4', 't', label='No')
    d.edge('s4', 'b', 'fin', 'r')
    cadena(d, 's2', 's3', 'fin')
    nota(d, 'n1', 'u2', 'El PDF no muestra precios unitarios: solo los subtotales por rubro')
    nota(d, 'n2', 'd2', 'Pasarlo a aprobado es una acción que solo puede hacer el dueño')
    nota(d, 'n3', 's2', 'No puede haber dos definitivos aprobados: los cambios posteriores van como adicional')
    d.fit_height()
    return d


# ======================================================================
#  3. Pedido de materiales
# ======================================================================

def compras_a():
    d = nuevo('Proceso de Pedido de Materiales', 1)
    d.term('ini', X, 0, 'INICIO')
    d.box('u0', X, 0, 'Quien está en la obra detecta que falta material y abre un pedido nuevo', 'user', W)
    d.diamond('d1', X, 0, '¿La obra está en ejecución?')
    d.box('u1', X, 0, 'Elige los materiales del catálogo y la cantidad de cada uno', 'user', W)
    d.diamond('d2', X, 0, '¿El pedido lo carga el dueño?')
    d.box('s1', X, 0, 'El sistema controla que los materiales estén activos y no se repitan, y registra quién pidió', 'sys', W)
    d.box('s2', X, 0, 'El pedido queda "Pendiente de aprobación" y aparece en el Tablero del dueño', 'sys', W)
    d.box('u2', X, 0, 'El dueño abre el pedido y toca "Pedir cotización"', 'user', W)
    d.diamond('d3', X, 0, '¿El teléfono del corralón se puede interpretar?', w=340, h=170)
    d.box('s3', X, 0, 'El sistema abre WhatsApp con un mensaje que lleva los materiales y las cantidades, sin precios', 'sys', W)
    d.box('u3', X, 0, 'El dueño manda el mensaje desde su teléfono y espera la respuesta del corralón', 'user', W)
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 'u0', 'd1', 'u1', 'd2', 's1', 's2', 'u2', 'd3', 's3', 'u3', 'cA'], TOP, GAP)

    d.box('sr', XR, 0, 'El sistema no permite pedir para esa obra', 'sys', WR)
    d.term('fin', XR, 0, 'FIN', w=180, h=70)
    d.stack(['sr', 'fin'], d.nodes['d1']['y'] - d.nodes['sr']['h'] / 2, 46)
    d.box('up', XR, 0, 'Elige el corralón al que le va a pedir precio', 'user', WR)
    d.align('up', 'd2')
    d.box('st', XR, 0, 'El sistema avisa y muestra el número para escribirle a mano', 'sys', WR)
    d.align('st', 'd3')

    cadena(d, 'ini', 'u0', 'd1')
    d.edge('d1', 'r', 'sr', 'l', label='No')
    cadena(d, 'sr', 'fin')
    d.edge('d1', 'b', 'u1', 't', label='Sí')
    cadena(d, 'u1', 'd2')
    d.edge('d2', 'r', 'up', 'l', label='Sí')
    unir(d, 'up', 's1')
    d.edge('d2', 'b', 's1', 't', label='No')
    cadena(d, 's1', 's2', 'u2', 'd3')
    d.edge('d3', 'r', 'st', 'l', label='No')
    unir(d, 'st', 'u3')
    d.edge('d3', 'b', 's3', 't', label='Sí')
    cadena(d, 's3', 'u3', 'cA')
    nota(d, 'n1', 'u0', 'Lo puede cargar el capataz desde el celular')
    nota(d, 'n2', 'd2', 'El capataz no ve los proveedores: el corralón lo elige el dueño al aprobar')
    nota(d, 'n3', 'u3', 'SIGCO no envía nada: el mensaje lo manda una persona')
    d.fit_height()
    return d


def compras_b():
    d = nuevo('Proceso de Pedido de Materiales', 2)
    d.conn('cA', X, 0, 'A')
    d.diamond('d1', X, 0, '¿El corralón pasó precio y se compra?', w=330)
    d.box('s1', X, 0, 'El sistema propone como precio de cada material la última cotización de ese corralón', 'sys', W)
    d.box('u1', X, 0, 'El dueño confirma o corrige el precio sin IVA de cada material', 'user', W)
    d.diamond('d2', X, 0, '¿Están los precios de todos los materiales?', w=330, h=170)
    d.box('u2', X, 0, 'El dueño aprueba el pedido', 'user', W, minh=70)
    d.box('s2', X, 0, 'El sistema guarda los precios como cotización, carga el gasto por rubro, audita la aprobación y pasa el pedido a "Enviado al proveedor"', 'sys', W)
    d.box('u3', X, 0, 'Al llegar el material, quien lo recibe lo controla contra el remito, le saca una foto y confirma', 'user', W)
    d.diamond('d3', X, 0, '¿Hubo diferencias?')
    d.box('s3', X, 0, 'El pedido queda "Recibido completo"', 'sys', W, minh=70)
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 'd1', 's1', 'u1', 'd2', 'u2', 's2', 'u3', 'd3', 's3', 'fin'], TOP + 40, GAP)

    d.box('ua', XR, 0, 'El dueño anula el pedido e indica el motivo', 'user', WR)
    d.box('sa', XR, 0, 'Queda "Anulado" y, si ya tenía gasto, el gasto se anula también', 'sys', WR)
    d.term('fin2', XR, 0, 'FIN', w=180, h=70)
    d.stack(['ua', 'sa', 'fin2'], d.nodes['d1']['y'] - d.nodes['ua']['h'] / 2, 46)
    d.box('ud', XR, 0, 'Marca que hubo diferencias y deja una nota', 'user', WR)
    d.align('ud', 'd3')
    d.box('sd', XR, 0, 'Queda "Recibido con diferencias"', 'sys', WR, minh=70)
    d.align('sd', 's3')
    d.box('uo', XR, 0, 'El dueño puede dejar una observación en la ficha del proveedor', 'user', WR)
    d.nodes['fin']['y'] = d.nodes['uo']['y'] = d.nodes['sd']['y'] + d.nodes['sd']['h'] / 2 + GAP + d.nodes['uo']['h'] / 2

    cadena(d, 'cA', 'd1')
    d.edge('d1', 'r', 'ua', 'l', label='No')
    cadena(d, 'ua', 'sa', 'fin2')
    d.edge('d1', 'b', 's1', 't', label='Sí')
    cadena(d, 's1', 'u1', 'd2')
    retorno(d, 'd2', 'u1')
    d.edge('d2', 'b', 'u2', 't', label='Sí')
    cadena(d, 'u2', 's2', 'u3', 'd3')
    d.edge('d3', 'r', 'ud', 'l', label='Sí')
    cadena(d, 'ud', 'sd', 'uo')
    d.edge('uo', 'l', 'fin', 'r')
    d.edge('d3', 'b', 's3', 't', label='No')
    cadena(d, 's3', 'fin')
    nota(d, 'n1', 's1', 'Muestra el subtotal, el IVA 21% y el total, que es lo que se le paga al corralón')
    nota(d, 'n2', 'u2', 'Aprobar es una acción que solo puede hacer el dueño')
    nota(d, 'n3', 's2', 'El gasto va sin IVA, igual que el presupuesto contra el que se compara')
    nota(d, 'n4', 'u3', 'Si hace falta, el dueño descarga la orden en PDF para el corralón')
    d.fit_height()
    return d


# ======================================================================
#  4. Registro de gastos
# ======================================================================

def gastos_a():
    d = nuevo('Proceso de Registro de Gastos', 1)
    d.term('ini', X, 0, 'INICIO')
    d.box('u0', X, 0, 'Cuando se produce un gasto, el dueño lo registra desde la computadora o el celular', 'user', W)
    d.diamond('d1', X, 0, '¿La obra está en ejecución con el definitivo aprobado?', w=350, h=180)
    d.box('u1', X, 0, 'Elige la obra, el rubro y, si quiere, el subrubro, del catálogo de Presupuestación', 'user', W)
    d.box('u2', X, 0, 'Indica el tipo de gasto (material, mano de obra, gasto hormiga u otro), el monto y la fecha', 'user', W)
    d.diamond('d2', X, 0, '¿Es un pago de mano de obra?')
    d.diamond('d3', X, 0, '¿Tiene comprobante?')
    d.box('s1', X, 0, 'El sistema guarda el gasto y registra quién lo cargó', 'sys', W)
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 'u0', 'd1', 'u1', 'u2', 'd2', 'd3', 's1', 'cA'], TOP, GAP)

    d.box('sr', XR, 0, 'El sistema no admite el gasto: todavía no hay presupuesto contra el que compararlo', 'sys', WR)
    d.term('fin', XR, 0, 'FIN', w=180, h=70)
    d.stack(['sr', 'fin'], d.nodes['d1']['y'] - d.nodes['sr']['h'] / 2, 46)
    d.box('uo', XR, 0, 'Lo asocia al operario que lo cobró', 'user', WR, minh=76)
    d.align('uo', 'd2')
    d.box('uf', XR, 0, 'Le saca una foto y la adjunta', 'user', WR, minh=76)
    d.align('uf', 'd3')

    cadena(d, 'ini', 'u0', 'd1')
    d.edge('d1', 'r', 'sr', 'l', label='No')
    cadena(d, 'sr', 'fin')
    d.edge('d1', 'b', 'u1', 't', label='Sí')
    cadena(d, 'u1', 'u2', 'd2')
    d.edge('d2', 'r', 'uo', 'l', label='Sí')
    unir(d, 'uo', 'd3')
    d.edge('d2', 'b', 'd3', 't', label='No')
    d.edge('d3', 'r', 'uf', 'l', label='Sí')
    unir(d, 'uf', 's1')
    d.edge('d3', 'b', 's1', 't', label='No')
    cadena(d, 's1', 'cA')
    nota(d, 'n1', 'u0', 'Las compras de materiales no se cargan acá: las genera Compras al aprobar el pedido')
    nota(d, 'n2', 'u1', 'El subrubro tiene que pertenecer al rubro elegido')
    d.fit_height()
    return d


def gastos_b():
    d = nuevo('Proceso de Registro de Gastos', 2)
    d.conn('cA', X, 0, 'A')
    d.box('s1', X, 0, 'El sistema suma el gasto al rubro y lo compara con lo presupuestado en el definitivo aprobado', 'sys', W)
    d.diamond('d1', X, 0, '¿Lo gastado supera el 100% del rubro?', w=330, h=170)
    d.diamond('d2', X, 0, '¿Llega al 90% del rubro?')
    d.status('verde', X, 0, 'Semáforo verde', '#2e7d32', w=260)
    d.box('s2', X, 0, 'El semáforo se actualiza en la ficha de la obra y en el Tablero', 'sys', W)
    d.box('s3', X, 0, 'El sistema recalcula la ganancia estimada y el total de gastos hormiga', 'sys', W)
    d.diamond('d3', X, 0, '¿Hay un gasto mal cargado?')
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 's1', 'd1', 'd2', 'verde', 's2', 's3', 'd3', 'fin'], TOP + 40, GAP)

    d.status('rojo', XN, 0, 'Semáforo rojo', '#c62828', w=230)
    d.align('rojo', 'd1')
    d.status('amarillo', XR, 0, 'Semáforo amarillo', '#e0a000', w=260)
    d.align('amarillo', 'd2')
    d.box('ua', XR, 0, 'El dueño lo anula indicando el motivo y lo vuelve a cargar', 'user', WR)
    d.box('sa', XR, 0, 'El gasto deja de contar y la anulación queda en la auditoría', 'sys', WR)
    d.stack(['ua', 'sa'], d.nodes['d3']['y'] - d.nodes['ua']['h'] / 2, GAP)
    abajo = d.nodes['d3']['y'] + d.nodes['d3']['h'] / 2 + GAP + d.nodes['fin']['h'] / 2
    d.nodes['fin']['y'] = d.nodes['sa']['y'] = max(d.nodes['sa']['y'], abajo)

    cadena(d, 'cA', 's1', 'd1')
    d.edge('d1', 'l', 'rojo', 'r', label='Sí')
    d.edge('d1', 'b', 'd2', 't', label='No')
    d.edge('d2', 'r', 'amarillo', 'l', label='Sí')
    d.edge('d2', 'b', 'verde', 't', label='No')
    cadena(d, 'verde', 's2')
    d.edge('rojo', 'b', 's2', 'l')
    d.edge('amarillo', 'b', 's2', 'r')
    cadena(d, 's2', 's3', 'd3')
    d.edge('d3', 'r', 'ua', 'l', label='Sí')
    cadena(d, 'ua', 'sa')
    d.edge('sa', 'l', 'fin', 'r')
    d.edge('d3', 'b', 'fin', 't', label='No')
    nota(d, 'n1', 's1', 'Se compara contra el subtotal: ni el IVA ni los honorarios son costo de la obra', lado='r')
    nota(d, 'n2', 's3', 'Un rubro que no estaba presupuestado se muestra sin semáforo, como gasto no previsto')
    d.fit_height()
    return d


# ======================================================================
#  5. Seguimiento de obras
# ======================================================================

def seguimiento_a():
    d = nuevo('Proceso de Seguimiento de Obras', 1)
    d.term('ini', X, 0, 'INICIO')
    d.box('s0', X, 0, 'La obra pasa a "En ejecución" al aprobarse su presupuesto definitivo', 'sys', W)
    d.box('u0', X, 0, 'El dueño abre la pestaña de etapas de la obra', 'user', W, minh=76)
    d.diamond('d1', X, 0, '¿Usa una plantilla de etapas típicas?')
    d.box('u1', X, 0, 'Para cada etapa indica qué hay que hacer, el rubro, los días que lleva y, si se superpone con otra, la fecha de inicio', 'user', W)
    d.box('s1', X, 0, 'El sistema reparte el 100% del avance en proporción a la duración de cada etapa', 'sys', W)
    d.box('s2', X, 0, 'Ordena las etapas por fecha de inicio y calcula cuándo empieza y termina cada una', 'sys', W)
    d.box('s3', X, 0, 'Las etapas quedan pendientes y el avance físico arranca en 0%', 'sys', W)
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 's0', 'u0', 'd1', 'u1', 's1', 's2', 's3', 'cA'], TOP, GAP)

    d.box('up', XR, 0, 'Elige una de las plantillas guardadas', 'user', WR, minh=76)
    d.box('sp', XR, 0, 'El sistema crea las etapas con los porcentajes de la plantilla', 'sys', WR)
    d.stack(['up', 'sp'], d.nodes['d1']['y'] - d.nodes['up']['h'] / 2, GAP)

    cadena(d, 'ini', 's0', 'u0', 'd1')
    d.edge('d1', 'r', 'up', 'l', label='Sí')
    cadena(d, 'up', 'sp')
    unir(d, 'sp', 's3')
    d.edge('d1', 'b', 'u1', 't', label='No')
    cadena(d, 'u1', 's1', 's2', 's3', 'cA')
    nota(d, 'n1', 'u0', 'Solo una obra en ejecución admite etapas')
    nota(d, 'n2', 'u1', 'También se pueden cargar los porcentajes a mano, siempre que sumen 100')
    nota(d, 'n3', 's1', 'El centavo que sobra del redondeo va a la etapa más larga')
    nota(d, 'n4', 's2', 'Una etapa sin fecha arranca el día siguiente a que termina la anterior')
    nota(d, 'n5', 's3', 'Si ya hay una etapa completada, las etapas no se pueden redefinir')
    d.fit_height()
    return d


def seguimiento_b():
    d = nuevo('Proceso de Seguimiento de Obras', 2)
    d.conn('cA', X, 0, 'A')
    d.box('u1', X, 0, 'El dueño o el capataz general marca una etapa como completada, con la fecha y una observación opcional', 'user', W)
    d.diamond('d1', X, 0, '¿Quedan etapas anteriores sin completar?', w=330, h=170)
    d.box('s1', X, 0, 'El sistema registra la fecha y quién la completó, y recalcula el avance físico', 'sys', W)
    d.box('s2', X, 0, 'Compara el avance físico con el financiero que aporta Gastos y con el plazo de la obra', 'sys', W)
    d.diamond('d2', X, 0, '¿Lo gastado supera al avance por más de 15 puntos?', w=340, h=180)
    d.diamond('d3', X, 0, '¿Se pasó la fecha de fin con obra pendiente?', w=340, h=170)
    d.diamond('d4', X, 0, '¿Era la última etapa pendiente?')
    d.box('s3', X, 0, 'La obra pasa sola a "Finalizada" y sus etapas quedan bloqueadas', 'sys', W)
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 'u1', 'd1', 's1', 's2', 'd2', 'd3', 'd4', 's3', 'fin'], TOP + 40, GAP)

    d.diamond('d5', XR, 0, '¿Confirma que esta etapa se adelantó?', w=290, h=160)
    d.align('d5', 'd1')
    d.box('sa', XR, 0, 'Alerta de desfasaje en el Tablero y en la ficha de la obra', 'sys', WR)
    d.align('sa', 'd2')
    d.box('st', XR, 0, 'La obra figura como atrasada', 'sys', WR, minh=76)
    d.align('st', 'd3')

    cadena(d, 'cA', 'u1', 'd1')
    d.edge('d1', 'r', 'd5', 'l', label='Sí')
    d.edge('d5', 't', 'u1', 'r', label='No', dashed=True)
    unir(d, 'd5', 's1')
    d.edge('d1', 'b', 's1', 't', label='No')
    cadena(d, 's1', 's2', 'd2')
    d.edge('d2', 'r', 'sa', 'l', label='Sí')
    unir(d, 'sa', 'd3')
    d.edge('d2', 'b', 'd3', 't', label='No')
    d.edge('d3', 'r', 'st', 'l', label='Sí')
    unir(d, 'st', 'd4')
    d.edge('d3', 'b', 'd4', 't', label='No')
    retorno(d, 'd4', 'u1', LANE_LEJOS)
    d.edge('d4', 'b', 's3', 't', label='Sí')
    cadena(d, 's3', 'fin')
    nota(d, 'n1', 's1', 'Una etapa marcada por error se puede reabrir mientras la obra no esté finalizada', angosta=True)
    nota(d, 'n2', 's3', 'Si el dueño cierra la obra con etapas pendientes, el sistema le pide confirmación')
    d.fit_height()
    return d


# ======================================================================
#  6. Cobros
# ======================================================================

def cobros_a():
    d = nuevo('Proceso de Cobros', 1)
    d.term('ini', X, 0, 'INICIO')
    d.box('u0', X, 0, 'Con el definitivo aprobado, el dueño abre Cobros y toca "Generar plan"', 'user', W)
    d.diamond('d1', X, 0, '¿El presupuesto tiene definidos anticipo y cuotas?', w=340, h=170)
    d.box('u1', X, 0, 'Indica la fecha del primer vencimiento, acordada con el cliente', 'user', W)
    d.box('s1', X, 0, 'El sistema arma el anticipo como cuota cero y las cuotas cada 15 días, con el total del presupuesto', 'sys', W)
    d.box('s2', X, 0, 'Ajusta los centavos del redondeo en la última cuota para que el plan cierre exacto', 'sys', W)
    d.box('u2', X, 0, 'Cuando el cliente paga, el dueño registra el pago: fecha, monto, medio de pago y comprobante', 'user', W)
    d.diamond('d2', X, 0, '¿El monto supera el saldo de la cuota?', w=330, h=170)
    d.box('s3', X, 0, 'El sistema guarda el pago, lo audita y lo descuenta del saldo', 'sys', W)
    d.diamond('d3', X, 0, '¿La cuota todavía tiene saldo?')
    d.box('s4', X, 0, 'La cuota queda "Abonada"', 'sys', W, minh=70)
    d.conn('cA', X, 0, 'A')
    d.stack(['ini', 'u0', 'd1', 'u1', 's1', 's2', 'u2', 'd2', 's3', 'd3', 's4', 'cA'], TOP, GAP)

    d.box('sr', XR, 0, 'El sistema pide cargar el plan de pago en el presupuesto antes de generar el cobro', 'sys', WR)
    d.term('fin', XR, 0, 'FIN', w=180, h=70)
    d.stack(['sr', 'fin'], d.nodes['d1']['y'] - d.nodes['sr']['h'] / 2, 46)
    d.conn('cB', XN, 0, 'B')
    d.align('cB', 'u2')
    d.box('sx', XR, 0, 'El sistema lo rechaza: no se puede cobrar más de lo que se debe', 'sys', WR)
    d.align('sx', 'd2')
    d.box('sp', XR, 0, 'La cuota queda "Parcial" con su saldo', 'sys', WR, minh=76)
    d.align('sp', 'd3')

    cadena(d, 'ini', 'u0', 'd1')
    d.edge('d1', 'r', 'sr', 'l', label='No')
    cadena(d, 'sr', 'fin')
    d.edge('d1', 'b', 'u1', 't', label='Sí')
    cadena(d, 'u1', 's1', 's2', 'u2', 'd2')
    d.edge('cB', 'r', 'u2', 'l')
    d.edge('d2', 'r', 'sx', 'l', label='Sí')
    d.edge('sx', 't', 'u2', 'r', dashed=True)
    d.edge('d2', 'b', 's3', 't', label='No')
    cadena(d, 's3', 'd3')
    d.edge('d3', 'r', 'sp', 'l', label='Sí')
    unir(d, 'sp', 'cA')
    d.edge('d3', 'b', 's4', 't', label='No')
    cadena(d, 's4', 'cA')
    nota(d, 'n1', 's1', 'Todos los montos salen del presupuesto aprobado: no se vuelven a escribir')
    nota(d, 'n2', 's3', 'Un pago mal cargado se anula con motivo y la cuota vuelve a deberse entera')
    nota(d, 'n3', 's4', 'El estado de la cuota sale de sus pagos: nadie lo marca a mano')
    d.fit_height()
    return d


def cobros_b():
    d = nuevo('Proceso de Cobros', 2)
    d.conn('cA', X, 0, 'A')
    d.diamond('d1', X, 0, '¿Corresponde actualizar por CAC este mes?', w=330, h=170)
    d.box('u1', X, 0, 'El dueño genera la planilla de pagos en PDF y se la manda al cliente', 'user', W)
    d.box('s1', X, 0, 'El sistema marca vencidas las cuotas con saldo y fecha pasada, y avisa las que vencen en los próximos 7 días', 'sys', W)
    d.diamond('d2', X, 0, '¿Quedan cuotas por cobrar?')
    d.box('s2', X, 0, 'La obra queda sin saldo por cobrar', 'sys', W, minh=70)
    d.term('fin', X, 0, 'FIN')
    d.stack(['cA', 'd1'], TOP + 40, GAP)

    d.box('uc', XR, 0, 'El dueño carga el coeficiente del mes', 'user', WR, minh=76)
    d.box('sc', XR, 0, 'El sistema muestra en pesos cuánto pasa a deberse', 'sys', WR)
    d.box('uc2', XR, 0, 'El dueño revisa el monto y confirma', 'user', WR, minh=76)
    d.box('sc2', XR, 0, 'Actualiza solo el saldo impago de las cuotas pendientes. El mismo coeficiente no se aplica dos veces', 'sys', WR)
    fondo = d.stack(['uc', 'sc', 'uc2', 'sc2'], d.nodes['d1']['y'] - d.nodes['uc']['h'] / 2, GAP)
    d.stack(['u1', 's1', 'd2', 's2', 'fin'], fondo + GAP + 28, GAP)
    d.conn('cB', XN, 0, 'B')
    d.align('cB', 'd2')

    cadena(d, 'cA', 'd1')
    d.edge('d1', 'r', 'uc', 'l', label='Sí')
    cadena(d, 'uc', 'sc', 'uc2', 'sc2')
    unir(d, 'sc2', 'u1')
    d.edge('d1', 'b', 'u1', 't', label='No')
    cadena(d, 'u1', 's1', 'd2')
    d.edge('d2', 'l', 'cB', 'r', label='Sí')
    d.edge('d2', 'b', 's2', 't', label='No')
    cadena(d, 's2', 'fin')
    nota(d, 'n1', 'd1', 'El coeficiente es por cuánto se multiplica el saldo: 1,04 es un aumento del 4%')
    d.fit_height()
    return d


TODOS = {
    'obra': (obra_a, obra_b),
    'presupuestacion': (presupuestacion_a, presupuestacion_b),
    'compras': (compras_a, compras_b),
    'gastos': (gastos_a, gastos_b),
    'seguimiento': (seguimiento_a, seguimiento_b),
    'cobros': (cobros_a, cobros_b),
}

if __name__ == '__main__':
    pedidos = sys.argv[1:] or list(TODOS)
    for i, nombre in enumerate(TODOS, 1):
        if nombre in pedidos:
            for parte, fn in zip('ab', TODOS[nombre]):
                fn().save(f'flujo-{i}-{nombre}-{parte}')
