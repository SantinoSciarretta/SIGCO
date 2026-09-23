# Preguntas para Ricardo

> Tres cosas que el sistema no puede decidir solo, porque son decisiones de
> negocio y no de programación. Dos salieron de contradicciones dentro del
> propio informe; la tercera es una funcionalidad que se agregó y conviene que
> él sepa que existe.
>
> Ninguna bloquea el uso del sistema: las tres tienen hoy una respuesta
> implementada, y lo que hace falta es confirmarla o cambiarla.

---

## 1. ¿El subrubro de un gasto es obligatorio?

**Hoy está implementado como OPCIONAL.**

### El problema

El informe se contradice consigo mismo:

- La sección *Validaciones y Lógica* del módulo Gastos dice: *"El subrubro es
  obligatorio en todo gasto, ya que el presupuesto definitivo contra el que se
  compara siempre está desglosado a ese nivel de detalle."*
- La tabla *Campos del Formulario de Gasto* dice: `Subrubro_Asociado —
  Relación (opcional)`.
- El Diccionario de Datos lo define como columna que admite nulos.

Dos partes del mismo documento piden cosas distintas.

### La pregunta concreta

**Cuando cargás un gasto, ¿siempre sabés a qué subrubro corresponde?**

Por ejemplo: una compra de cemento entra claramente en Albañilería →
Materiales. Pero un pago de $50.000 a un operario que estuvo toda la semana
haciendo de todo, ¿a qué subrubro va? ¿O un flete que sirvió para tres rubros
distintos?

### Qué cambia según la respuesta

| Si respondés | Qué pasa |
| --- | --- |
| **"Siempre lo sé"** | Se vuelve obligatorio. Ventaja: la comparación contra el presupuesto queda completa hasta el último nivel. Costo: no vas a poder cargar un gasto hasta decidir el subrubro, y si dudás, vas a terminar poniendo cualquiera con tal de guardar. |
| **"A veces no"** (hoy) | Queda como está. Ventaja: el gasto se carga en el momento, que es lo que evita que se pierda. Costo: esos gastos comparan contra el rubro pero no contra el subrubro. |

**Recomendación:** dejarlo opcional. El problema central que el sistema viene a
resolver es que los gastos chicos se pierden porque cargarlos cuesta trabajo.
Una obligación que aparece justo cuando hay dudas empuja a no cargar el gasto, o
a cargarlo mal — y un subrubro inventado es peor que ninguno.

---

## 2. ¿El Capataz General puede registrar gastos?

**Hoy está implementado que NO: solo puede consultarlos.**

### El problema

Otra vez el informe dice las dos cosas:

- La sección *Validaciones y Lógica* de Gastos dice: *"Solo los usuarios con rol
  de dueño o capataz general pueden registrar gastos."*
- La *Matriz de Permisos* le da al Capataz General, en Gastos, **Consulta** —o
  sea, solo lectura.

Se implementó según la matriz, porque es la que define los permisos de todo el
sistema y cambiarla afectaría al resto.

### La pregunta concreta

**¿Querés que Martín (o quien sea capataz general) pueda cargar gastos, o
preferís que los gastos los cargues solo vos?**

Pensalo así: el capataz general está en las obras y ve los gastos ocurrir. Si no
puede cargarlos, te los tiene que pasar a vos, y ahí volvés a ser el cuello de
botella que el sistema quiere aliviar. Pero cargar gastos es tocar información
financiera.

### Qué cambia según la respuesta

| Si respondés | Qué pasa |
| --- | --- |
| **"Que pueda cargar"** | Se le agrega el permiso `gastos.editar`. Es un cambio de una línea en la pantalla de Accesos, sin tocar código. Va a poder cargar y anular gastos de cualquier obra. |
| **"Solo yo"** (hoy) | Queda como está. El capataz general ve los gastos y el semáforo, pero no carga. |

**Nota:** hay un punto intermedio. Si querés que cargue pero no que anule, hoy no
se puede: `gastos.editar` habilita las dos cosas. Separarlo sí requiere tocar
código; decime si te interesa y se hace.

---

## 3. Se agregó algo que el informe no pedía: pagos parciales

**Esto no es una pregunta, es un aviso.** Pero conviene que lo sepas porque el
sistema hace algo que el informe no describe.

### Qué cambió

Hasta ahora una cuota se cobraba **entera o nada**: se marcaba como Abonada y
listo. Es exactamente lo que dice el informe, que define la cuota con estados
Pendiente / Abonada / Vencida.

Ahora se puede registrar que un cliente pagó **una parte**. La cuota queda como
"Parcial", muestra cuánto entró y cuánto falta, y el saldo de la obra refleja lo
que realmente se debe.

### Por qué se agregó

Porque pasa en la práctica: el cliente transfiere una parte y dice que el resto
la semana que viene. Antes había dos opciones, las dos malas: marcar la cuota
como cobrada entera (y el sistema decía que no debía nada) o dejarla como impaga
(y el sistema decía que debía todo).

### Lo que conviene que sepas

- **Se puede seguir usando como antes.** Al cobrar, el sistema propone el monto
  completo: si el cliente pagó todo, aceptás y listo. El monto solo se edita
  cuando pagó una parte.
- **El ajuste por índice CAC se aplica solo sobre lo que falta**, no sobre lo ya
  pagado. Si el cliente pagó la mitad de una cuota y después hay que actualizar
  por CAC, esa mitad ya pagada no se encarece. Sería cobrarle dos veces.
- **Una cuota pagada a medias cuyo vencimiento ya pasó sigue figurando como
  vencida**, porque el resto se sigue debiendo y hay que reclamarlo.

**La pregunta, si querés hacerla:** ¿te sirve, o preferís que lo saquemos para
que el sistema quede exactamente como el informe lo describe?

---

## Una cosa más, que no es pregunta

El sistema ahora **obliga a cambiar la contraseña** la primera vez que entrás, y
no acepta contraseñas previsibles: nada que contenga "granica", ni tu nombre de
usuario, ni secuencias como "1234567890". Mínimo diez caracteres.

Puede resultar molesto la primera vez. El motivo es concreto: la contraseña con
la que arranca el sistema está escrita en el código, que está publicado en
internet, así que la conoce cualquiera que lo mire.

Elegí una y anotala donde guardes las importantes. **El sistema no puede
recuperártela**: se guarda cifrada de una forma que no se puede revertir, ni
siquiera entrando a la base de datos.
