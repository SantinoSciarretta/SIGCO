# Diagramas de secuencia de los procesos centrales de SIGCO, para el informe.
# Uso: python secuencias.py [numero ...]   (sin argumentos genera todos)
#
# Cada mensaje corresponde a algo que existe en el codigo: los endpoints son los
# de los controladores y los pasos internos, los metodos de cada servicio. Las
# respuestas de error se dibujan directo hacia la pantalla: la excepcion del
# servicio vuelve como una respuesta 400 sin que el controlador intervenga.
import sys
from secuencia_lib import Secuencia

PANTALLA_DB = ('bd', 'Base de datos', 'PostgreSQL', 'data')


def ingreso():
    s = Secuencia('Secuencia 1: Ingreso al sistema', [
        ('u', 'Usuario', None, 'actor'),
        ('p', 'Pantalla de ingreso', 'React', 'ui'),
        ('c', 'AutenticacionController', 'recibe el pedido', 'srv'),
        ('s', 'AutenticacionService', 'reglas del negocio', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Escribe su usuario y su contraseña')
    s.msg('p', 'c', 'Envía usuario y contraseña\n(POST /api/sesion)')
    s.msg('c', 's', 'Pide validar el ingreso')
    s.msg('s', 'bd', 'Busca la cuenta por nombre de usuario')
    s.msg('bd', 's', 'Cuenta (o ninguna)', ret=True)
    s.frame('opt', 'La cuenta está bloqueada por intentos fallidos', 'p', 's')
    s.msg('s', 'p', 'Error: cuenta bloqueada durante 15 minutos', ret=True)
    s.fin()
    s.self_('s', 'Compara la contraseña con el hash BCrypt, aunque la cuenta no exista')
    s.frame('alt', 'Contraseña incorrecta o cuenta inexistente', 'p', 'bd')
    s.msg('s', 'bd', 'Suma un intento fallido (transacción propia)')
    s.msg('s', 'p', 'Error: "Usuario o contraseña incorrectos"', ret=True)
    s.otro('Contraseña correcta')
    s.msg('s', 'bd', 'Limpia los intentos, registra el ingreso y lo audita')
    s.self_('s', 'Firma el token JWT: vale 8 horas, con un tope de 24')
    s.msg('s', 'c', 'Sesión: token, rol y permisos', ret=True)
    s.msg('c', 'p', 'Sesión iniciada\n(respuesta 200: salió bien)', ret=True)
    s.self_('p', 'Guarda el token y arma el menú con los permisos')
    s.fin()
    s.frame('opt', 'La cuenta debe cambiar su contraseña', 'u', 'p')
    s.msg('p', 'u', 'Pide elegir una contraseña nueva antes de seguir', ret=True)
    s.fin()
    return s


def peticion():
    s = Secuencia('Secuencia 2: Validación de cada petición', [
        ('u', 'Usuario', None, 'actor'),
        ('p', 'Pantalla', 'React y Axios', 'ui'),
        ('f', 'FiltroJwt', 'controla el token', 'srv'),
        PANTALLA_DB,
        ('c', 'Controlador', 'controla el permiso', 'srv'),
        ('s', 'Servicio', 'reglas de negocio', 'srv'),
    ], separacion=190)
    s.msg('u', 'p', 'Usa una función del sistema')
    s.msg('p', 'f', 'Pedido con el token de la sesión')
    s.self_('f', 'Verifica la firma, el vencimiento y el tope de 24 horas')
    s.frame('alt', 'Token inválido o vencido', 'p', 's')
    s.msg('f', 'p', 'La sesión venció\n(respuesta 401)', ret=True)
    s.self_('p', 'Vuelve a la pantalla de ingreso')
    s.otro('Token válido')
    s.msg('f', 'bd', 'Lee la cuenta con su rol y sus permisos')
    s.msg('bd', 'f', 'Cuenta, rol y permisos', ret=True)
    s.self_('f', 'Compara la versión de sesión: cambiar la contraseña corta las sesiones')
    s.msg('f', 'c', 'Petición autenticada')
    s.self_('c', '¿El rol tiene el permiso que pide este endpoint?')
    s.frame('alt', 'Sin permiso', 'p', 's')
    s.msg('c', 'p', 'No tiene permiso para esta acción\n(respuesta 403)', ret=True)
    s.otro('Con permiso')
    s.msg('c', 's', 'Pide ejecutar la acción')
    s.msg('s', 'c', 'Resultado', ret=True)
    s.msg('c', 'p', 'Resultado y, si faltan menos de 2 horas, un token renovado\n(respuesta 200)', ret=True)
    s.fin()
    s.fin()
    return s


def aprobar_presupuesto():
    s = Secuencia('Secuencia 3: Aprobación del presupuesto definitivo', [
        ('u', 'Dueño', None, 'actor'),
        ('p', 'Pantalla de Presupuestación', 'React', 'ui'),
        ('c', 'PresupuestoController', 'recibe el pedido', 'srv'),
        ('s', 'PresupuestoService', 'reglas del negocio', 'srv'),
        ('a', 'ServicioAuditoria', 'registra quién hizo qué', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Toca "Aprobar" en el definitivo enviado')
    s.msg('p', 'c', 'Pide aprobar el presupuesto\n(PATCH /api/presupuestos/{id}/estado)')
    s.self_('c', 'Controla el permiso: solo el dueño aprueba')
    s.msg('c', 's', 'Pide aprobar el presupuesto')
    s.msg('s', 'bd', 'Busca el presupuesto con sus ítems y su obra')
    s.msg('bd', 's', 'Presupuesto', ret=True)
    s.frame('opt', 'Ya está aprobado o rechazado', 'p', 's')
    s.msg('s', 'p', 'Error: ya no admite cambios de estado', ret=True)
    s.fin()
    s.msg('s', 'bd', 'Busca otro definitivo aprobado de la obra')
    s.msg('bd', 's', 'Definitivos aprobados', ret=True)
    s.frame('alt', 'La obra ya tiene un definitivo aprobado', 'p', 'bd')
    s.msg('s', 'p', 'Error: los cambios van como adicional', ret=True)
    s.otro('No hay otro aprobado')
    s.self_('s', 'Marca el presupuesto como aprobado')
    s.self_('s', 'Pasa la obra a "En ejecución"')
    s.msg('s', 'a', 'Pide registrar la aprobación')
    s.msg('a', 'bd', 'Guarda quién aprobó, qué monto y cuándo')
    s.msg('s', 'bd', 'Confirma la transacción: presupuesto y obra')
    s.msg('s', 'c', 'Presupuesto aprobado', ret=True)
    s.msg('c', 'p', 'Presupuesto aprobado\n(respuesta 200: salió bien)', ret=True)
    s.msg('p', 'u', 'Muestra "Aprobado" y la obra en ejecución', ret=True)
    s.fin()
    return s


def aprobar_pedido():
    s = Secuencia('Secuencia 4: Aprobación del pedido de materiales', [
        ('u', 'Dueño', None, 'actor'),
        ('p', 'Pantalla de Compras', 'React', 'ui'),
        ('c', 'PedidoController', 'recibe el pedido', 'srv'),
        ('s', 'PedidoService', 'reglas del negocio', 'srv'),
        ('g', 'GastoService', 'reglas de Gastos', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Abre el pedido pendiente para aprobarlo')
    s.msg('p', 'c', 'Pide los precios sugeridos\n(GET /api/pedidos/{id}/precios-sugeridos)')
    s.msg('c', 's', 'Pide los precios sugeridos')
    s.msg('s', 'bd', 'Última cotización del corralón por material')
    s.msg('bd', 's', 'Precios', ret=True)
    s.msg('s', 'p', 'Precios sugeridos', ret=True)
    s.msg('u', 'p', 'Confirma o corrige los precios sin IVA y aprueba')
    s.msg('p', 'c', 'Envía la aprobación con los precios\n(PATCH /api/pedidos/{id}/aprobacion)')
    s.self_('c', 'Controla el permiso: solo el dueño aprueba')
    s.msg('c', 's', 'Pide aprobar el pedido')
    s.msg('s', 'bd', 'Busca el pedido con sus materiales')
    s.msg('bd', 's', 'Pedido y sus materiales', ret=True)
    s.frame('alt', 'No está pendiente o falta el precio de un material', 'p', 'bd')
    s.msg('s', 'p', 'Error: no se aprueba', ret=True)
    s.otro('Todo en orden')
    s.self_('s', 'Pone los precios y marca el pedido como aprobado')
    s.msg('s', 'bd', 'Guarda cada precio como cotización nueva')
    s.msg('s', 'g', 'Pide cargar el gasto de la compra')
    s.msg('g', 'bd', 'Un gasto por rubro, sin IVA')
    s.msg('g', 's', 'Gastos generados', ret=True)
    s.msg('s', 'bd', 'Audita la aprobación y confirma la transacción')
    s.msg('s', 'c', 'Pedido "Enviado al proveedor"', ret=True)
    s.msg('c', 'p', 'Pedido aprobado\n(respuesta 200: salió bien)', ret=True)
    s.fin()
    return s


def recepcion():
    s = Secuencia('Secuencia 5: Confirmación de la recepción con el remito', [
        ('u', 'Capataz', None, 'actor'),
        ('p', 'Pantalla de Compras', 'celular', 'ui'),
        ('ac', 'ArchivoController', 'recibe la foto', 'srv'),
        ('st', 'Supabase Storage', 'archivos', 'data'),
        ('c', 'PedidoController', 'recibe el pedido', 'srv'),
        ('s', 'PedidoService', 'reglas del negocio', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Le saca una foto al remito')
    s.msg('p', 'ac', 'Sube la foto\n(POST /api/archivos)')
    s.self_('ac', 'Valida el tipo real del archivo y su tamaño')
    s.msg('ac', 'st', 'Guarda la imagen')
    s.msg('st', 'ac', 'Guardada', ret=True)
    s.msg('ac', 'p', 'Referencia de la foto', ret=True)
    s.msg('u', 'p', 'Indica si hubo diferencias y confirma')
    s.msg('p', 'c', 'Confirma la recepción con la foto y la nota\n(PATCH /api/pedidos/{id}/recepcion)')
    s.msg('c', 's', 'Pide registrar la recepción')
    s.msg('s', 'bd', 'Busca el pedido con su obra')
    s.msg('bd', 's', 'Pedido y su obra', ret=True)
    s.self_('s', 'Revisa que el capataz trabaje en esa obra')
    s.frame('alt', 'Fuera de su obra, o pedido no aprobado', 'p', 'bd')
    s.msg('s', 'p', 'Error: no se registra la recepción', ret=True)
    s.otro('Pedido enviado al proveedor')
    s.self_('s', 'Con nota queda "Recibido con diferencias", sin nota "Recibido completo"')
    s.msg('s', 'bd', 'Guarda la foto, quién recibió y la fecha')
    s.msg('s', 'c', 'Pedido recibido', ret=True)
    s.msg('c', 'p', 'Recepción confirmada\n(respuesta 200: salió bien)', ret=True)
    s.fin()
    return s


def gasto():
    s = Secuencia('Secuencia 6: Registro de un gasto y cálculo del semáforo', [
        ('u', 'Dueño', None, 'actor'),
        ('p', 'Pantalla de Gastos', 'React', 'ui'),
        ('c', 'Controladores de Gastos', 'reciben el pedido', 'srv'),
        ('s', 'GastoService', 'reglas del negocio', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Carga obra, rubro, tipo, monto y fecha')
    s.msg('p', 'c', 'Envía el gasto nuevo\n(POST /api/gastos)')
    s.msg('c', 's', 'Pide registrar el gasto')
    s.msg('s', 'bd', 'Busca la obra y su definitivo aprobado')
    s.msg('bd', 's', 'Obra y presupuesto aprobado', ret=True)
    s.frame('alt', 'La obra no está en ejecución o no tiene definitivo aprobado', 'p', 'bd')
    s.msg('s', 'p', 'Error: no hay presupuesto contra el que comparar', ret=True)
    s.otro('Obra en ejecución con presupuesto')
    s.self_('s', 'Verifica que el subrubro sea del rubro elegido')
    s.msg('s', 'bd', 'Guarda el gasto con quién lo registró')
    s.msg('s', 'c', 'Gasto guardado', ret=True)
    s.msg('c', 'p', 'Gasto registrado\n(respuesta 201: se creó)', ret=True)
    s.fin()
    s.msg('p', 'c', 'Pide el estado financiero de la obra\n(GET /api/obras/{id}/estado-financiero)')
    s.msg('c', 's', 'Pide el estado financiero')
    s.msg('s', 'bd', 'Ítems del definitivo aprobado, por rubro')
    s.msg('s', 'bd', 'Total gastado por rubro (sin los anulados)')
    s.msg('bd', 's', 'Presupuestado y gastado', ret=True)
    s.self_('s', 'Por rubro: porcentaje gastado sobre lo presupuestado')
    s.self_('s', 'Semáforo: rojo si pasa el 100%, amarillo desde el 90%, verde por debajo')
    s.self_('s', 'Ganancia estimada: subtotal presupuestado menos lo gastado')
    s.msg('s', 'p', 'Estado financiero con los semáforos', ret=True)
    s.msg('p', 'u', 'Muestra el semáforo de cada rubro', ret=True)
    return s


def etapa():
    s = Secuencia('Secuencia 7: Cumplimiento de una etapa de obra', [
        ('u', 'Dueño o capataz', None, 'actor'),
        ('p', 'Pantalla de Seguimiento', 'React', 'ui'),
        ('c', 'SeguimientoController', 'recibe el pedido', 'srv'),
        ('s', 'SeguimientoService', 'reglas del negocio', 'srv'),
        ('g', 'GastoService', 'reglas de Gastos', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Marca la etapa como completada, con la fecha')
    s.msg('p', 'c', 'Envía la etapa completada\n(PATCH /api/hitos/{id}/cumplimiento)')
    s.msg('c', 's', 'Pide completar la etapa')
    s.msg('s', 'bd', 'Busca la etapa, su obra y las demás etapas')
    s.msg('bd', 's', 'Etapas y obra', ret=True)
    s.self_('s', 'Revisa que pueda trabajar en esa obra y que no esté finalizada')
    s.frame('alt', 'Hay etapas anteriores pendientes y no confirmó', 'p', 'bd')
    s.msg('s', 'p', 'Pide confirmar que esta etapa se adelantó', ret=True)
    s.otro('Sin pendientes anteriores, o confirmado')
    s.self_('s', 'Marca la etapa como completada, con la fecha y quién la completó')
    s.frame('opt', 'Era la última etapa pendiente', 's', 's')
    s.self_('s', 'Pasa la obra a "Finalizada"')
    s.fin()
    s.msg('s', 'g', 'Pide cuánto se gastó del presupuesto')
    s.msg('g', 'bd', 'Total gastado y subtotal del definitivo')
    s.msg('bd', 'g', 'Totales', ret=True)
    s.msg('g', 's', 'Avance financiero (%)', ret=True)
    s.self_('s', 'Avance físico, desfasaje (más de 15 puntos es alerta) y atraso contra el plazo')
    s.msg('s', 'bd', 'Confirma la transacción')
    s.msg('s', 'p', 'Avance de la obra', ret=True)
    s.msg('p', 'u', 'Muestra la barra de avance y las alertas', ret=True)
    s.fin()
    return s


def pago():
    s = Secuencia('Secuencia 8: Registro de un pago', [
        ('u', 'Dueño', None, 'actor'),
        ('p', 'Pantalla de Cobros', 'React', 'ui'),
        ('c', 'CobrosController', 'recibe el pedido', 'srv'),
        ('s', 'CobrosService', 'reglas del negocio', 'srv'),
        PANTALLA_DB,
    ], separacion=190)
    s.msg('u', 'p', 'Carga fecha, monto, medio de pago y comprobante')
    s.msg('p', 'c', 'Envía el pago de la cuota\n(PATCH /api/cuotas/{id}/pago)')
    s.self_('c', 'Controla el permiso: solo el dueño cobra')
    s.msg('c', 's', 'Pide registrar el pago')
    s.msg('s', 'bd', 'Busca la cuota con sus pagos')
    s.msg('bd', 's', 'Cuota y sus pagos', ret=True)
    s.frame('alt', 'La cuota ya está abonada o el monto supera el saldo', 'p', 'bd')
    s.msg('s', 'p', 'Error: no se puede cobrar más de lo que se debe', ret=True)
    s.otro('Monto válido')
    s.self_('s', 'Suma el pago a la cuota')
    s.self_('s', 'El estado sale de los pagos: con saldo queda Parcial, sin saldo Abonada')
    s.msg('s', 'bd', 'Guarda el pago y lo audita con el saldo que queda')
    s.msg('s', 'bd', 'Lee el plan de cobro de la obra')
    s.msg('bd', 's', 'Cuotas de la obra', ret=True)
    s.self_('s', 'Recalcula vencimientos, lo cobrado y lo que falta cobrar')
    s.msg('s', 'c', 'Plan de cobro actualizado', ret=True)
    s.msg('c', 'p', 'Pago registrado\n(respuesta 200: salió bien)', ret=True)
    s.msg('p', 'u', 'Muestra la cuota y lo que resta cobrar', ret=True)
    s.fin()
    return s


TODOS = [ingreso, peticion, aprobar_presupuesto, aprobar_pedido, recepcion, gasto, etapa, pago]
NOMBRES = ['ingreso', 'validacion-peticion', 'aprobar-presupuesto', 'aprobar-pedido',
           'recepcion-pedido', 'registrar-gasto', 'completar-etapa', 'registrar-pago']

if __name__ == '__main__':
    pedidos = [int(a) for a in sys.argv[1:]] or list(range(1, len(TODOS) + 1))
    for i in pedidos:
        TODOS[i - 1]().save(f'secuencia-{i}-{NOMBRES[i - 1]}')
