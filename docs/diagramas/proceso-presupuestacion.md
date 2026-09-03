# Proceso de Presupuestación — diagramas

Presupuestación es el proceso **core** de SIGCO: es el más complejo de los 14
módulos, el que más problemas del relevamiento resuelve, y del que dependen
Gastos (para comparar) y Cobros (para el plan de cuotas).

Los diagramas de este documento describen el proceso **tal como está
implementado y funcionando**, no una intención de diseño.

> **Por qué este proceso y no otro.** El informe releva que cada presupuesto se
> arma desde cero en Excel, que las versiones se pisan entre sí y que no queda
> registro de cuál aprobó el cliente. Es el problema más costoso que identifica
> el relevamiento, y el que justifica la parte más elaborada del modelo de datos
> (la autorreferencia de `presupuesto`).

---

## 1. Diagrama de casos de uso

Quién puede hacer qué en el módulo. Los permisos salen de la matriz de roles del
informe.

```mermaid
flowchart LR
    dueno(("Dueño"))
    cliente(("Cliente"))

    subgraph MOD["MÓDULO PRESUPUESTACIÓN"]
        UC1["Administrar catálogo<br/>de rubros y subrubros"]
        UC2["Registrar<br/>cotización inicial"]
        UC3["Generar<br/>anteproyecto"]
        UC4["Generar presupuesto<br/>definitivo"]
        UC5["Cargar ítems<br/>del presupuesto"]
        UC6["Definir<br/>plan de pago"]
        UC7["Enviar presupuesto<br/>al cliente"]
        UC8["Aprobar<br/>presupuesto"]
        UC9["Rechazar<br/>presupuesto"]
        UC11["Consultar historial<br/>de versiones"]
        UC10["Generar PDF<br/>con membrete"]
        UC12["Seleccionar material<br/>del catálogo"]
        UC13["Poner la obra<br/>en ejecución"]
    end

    dueno --- UC1
    dueno --- UC2
    dueno --- UC3
    dueno --- UC4
    dueno --- UC5
    dueno --- UC6
    dueno --- UC7
    dueno --- UC8
    dueno --- UC9
    dueno --- UC11

    UC3 -. "«extend»<br/>solo reformas" .-> UC4
    UC5 -. "«include»" .-> UC12
    UC7 -. "«include»" .-> UC10
    UC8 -. "«include»" .-> UC13

    UC10 --> cliente

    classDef caso fill:#eef6ff,stroke:#5980a6,stroke-width:1px,color:#1d2d3d
    classDef derivado fill:#e2ecf7,stroke:#416180,stroke-width:1px,color:#1d2d3d
    classDef actor fill:#1d2d3d,stroke:#1d2d3d,color:#ffffff
    class UC1,UC2,UC3,UC4,UC5,UC6,UC7,UC8,UC9,UC11 caso
    class UC10,UC12,UC13 derivado
    class dueno,cliente actor
    style MOD fill:#fbfcfd,stroke:#b7b7ba,stroke-dasharray:4 4
```

### Notas sobre los actores

**El Dueño es el único actor con acceso al módulo.** No es una simplificación
del diagrama: la matriz de permisos del informe le da acceso «Total» al dueño y
«sin acceso» a los dos roles de capataz. Presupuestación maneja precios y
márgenes, que es justamente la información que la delegación no debe alcanzar.

**El Cliente es un actor externo que no opera el sistema.** Recibe el PDF y
responde por fuera (WhatsApp, teléfono). El dueño registra esa respuesta como
Aprobado o Rechazado. Modelarlo como usuario del sistema sería inventar un
requerimiento que el relevamiento no pide.

**`UC8 Aprobar` es no delegable.** El informe lo dice explícitamente, y por eso
aparece solo conectado al dueño aunque otras acciones pudieran delegarse en el
futuro.

---

## 2. Diagrama de actividad — el proceso completo

Las tres instancias del proceso, con el punto de decisión que las separa: el
tipo de obra.

```mermaid
flowchart TD
    inicio([Cliente consulta por una obra]) --> crearObra[/"Registrar cliente y obra<br/>módulo Obras"/]
    crearObra --> cotiza["Cargar metros cuadrados<br/>y valor de referencia por m²"]
    cotiza --> calcula["Sistema calcula<br/>precio estimativo"]
    calcula --> enviaCot["Enviar cotización inicial"]
    enviaCot --> avanza{"¿El cliente<br/>quiere avanzar?"}

    avanza -->|No| rechazaCot["Marcar Rechazado"]
    rechazaCot --> finNo([Queda registrada la consulta])

    avanza -->|Sí| tipoObra{"¿Qué tipo<br/>de obra es?"}

    tipoObra -->|Reforma| anteproyecto["Generar ANTEPROYECTO"]
    tipoObra -->|Construcción nueva| saltaAnte["El sistema NO habilita anteproyecto"]

    anteproyecto --> itemsAnte["Cargar ítems<br/>a nivel de rubro"]
    itemsAnte --> enviaAnte["Enviar al cliente"]
    enviaAnte --> apruebaAnte{"¿Aprueba<br/>el anteproyecto?"}

    apruebaAnte -->|No| ajusta["Generar versión nueva<br/>ajustando alcance o terminaciones"]
    ajusta --> itemsAnte

    apruebaAnte -->|Sí| defDesdeAnte["Generar DEFINITIVO<br/>tomando el anteproyecto como base"]
    saltaAnte --> defDirecto["Generar DEFINITIVO<br/>sin base previa"]

    defDesdeAnte --> detalle
    defDirecto --> detalle

    detalle["Detallar por rubro, subrubro e ítem<br/>vinculando materiales del catálogo"]
    detalle --> plan["Definir plan de pago<br/>anticipo + cuotas"]
    plan --> cierra{"¿Anticipo + cuotas<br/>cierra al 100%?"}
    cierra -->|No| errorPlan["El sistema rechaza el plan"]
    errorPlan --> plan

    cierra -->|Sí| pdf["Generar PDF con membrete<br/>SIN precios unitarios"]
    pdf --> enviaDef["Enviar al cliente"]
    enviaDef --> apruebaDef{"¿Aprueba el<br/>definitivo?"}

    apruebaDef -->|No| ajustaDef["Generar versión nueva<br/>el rechazado se conserva"]
    ajustaDef --> detalle

    apruebaDef -->|Sí| aprueba["Marcar APROBADO<br/>acción no delegable"]
    aprueba --> ejecucion[/"La obra pasa automáticamente<br/>a En ejecución"/]
    ejecucion --> fin([Habilita Gastos, Cobros y Seguimiento])

    classDef accion fill:#ffffff,stroke:#5d5d60,stroke-width:1px,color:#1d1f20
    classDef decision fill:#fff8e6,stroke:#c08a2e,stroke-width:1px,color:#1d1f20
    classDef sistema fill:#eef6ff,stroke:#5980a6,stroke-width:1px,color:#1d2d3d
    classDef terminal fill:#1d2d3d,stroke:#1d2d3d,color:#ffffff
    classDef error fill:#fbecea,stroke:#ad4d3d,stroke-width:1px,color:#1d1f20

    class crearObra,cotiza,enviaCot,rechazaCot,anteproyecto,itemsAnte,enviaAnte,ajusta,defDesdeAnte,defDirecto,detalle,plan,enviaDef,ajustaDef,aprueba accion
    class avanza,tipoObra,apruebaAnte,apruebaDef,cierra decision
    class calcula,saltaAnte,ejecucion,pdf sistema
    class inicio,fin,finNo terminal
    class errorPlan error
```

### Las tres decisiones que definen el proceso

**`¿Qué tipo de obra es?`** — Es la bifurcación central. En una reforma hay que
relevar lo existente antes de poder detallar, y de ahí el anteproyecto. En una
construcción nueva se parte de planos y no hay nada que relevar, así que el
sistema directamente **no habilita** generar un anteproyecto. Esto no es una
sugerencia de la interfaz: el backend rechaza la operación con `409`.

**`¿Anticipo + cuotas cierra al 100%?`** — El plan de pago no se guarda si no
cierra matemáticamente. Es la regla que evita el error de planilla que hoy pasa
desapercibido hasta que falta plata al final de la obra.

**`¿Aprueba el definitivo?`** — Un «No» **no borra nada**. Genera una versión
nueva y el rechazado queda como registro de que ese precio se ofreció. Eso es
exactamente lo que se pierde hoy cuando la nueva versión de Excel pisa la
anterior.

---

## 3. Diagrama de estados del presupuesto

El ciclo de vida de un presupuesto y qué habilita cada transición.

```mermaid
stateDiagram-v2
    [*] --> Borrador : crear

    Borrador --> Borrador : agregar / editar / quitar ítems<br/>definir plan de pago
    Borrador --> Enviado : enviar (requiere al menos un ítem)

    Enviado --> Aprobado : aprobar (solo el dueño)
    Enviado --> Rechazado : rechazar

    Aprobado --> [*]
    Rechazado --> [*]

    note right of Borrador
        Es el único estado que admite
        cambios. Una vez enviado, el
        cliente ya vio ese documento.
    end note

    note right of Aprobado
        Si es el DEFINITIVO, la obra
        pasa a "En ejecución" y se
        habilitan Gastos y Cobros.
        Una obra no puede tener dos
        definitivos aprobados.
    end note

    note left of Rechazado
        No se elimina: queda como
        registro de que ese precio
        se ofreció. Para seguir, se
        genera una versión nueva.
    end note
```

**Aprobado y Rechazado son estados finales.** No hay transición de salida: un
presupuesto cerrado no vuelve a Borrador. Si hace falta cambiar algo, se genera
una versión nueva vinculada por `id_presupuesto_base`, y así queda registrado
que hubo un cambio y cuál fue.

---

## 4. Diagrama de secuencia — aprobar el presupuesto definitivo

Cómo ejecuta el sistema la operación más importante del módulo. Los componentes
son los reales, con sus nombres de archivo.

```mermaid
sequenceDiagram
    autonumber
    actor D as Dueño
    participant UI as PresupuestoDetalle.jsx
    participant AX as presupuestosApi.js<br/>(Axios)
    participant CT as PresupuestoController
    participant SV as PresupuestoService
    participant RP as PresupuestoRepository
    participant BD as PostgreSQL

    D->>UI: Clic en "Marcar aprobado"
    UI->>AX: cambiarEstadoPresupuesto(id, "Aprobado")
    AX->>CT: PATCH /api/presupuestos/{id}/estado

    CT->>SV: cambiarEstado(id, cambio)
    Note over CT: El controlador no valida nada:<br/>solo traduce HTTP

    SV->>RP: buscarCompleto(id)
    RP->>BD: SELECT con JOIN FETCH<br/>de ítems, rubros y obra
    BD-->>RP: presupuesto + ítems
    RP-->>SV: Presupuesto

    alt No existe
        SV-->>CT: RecursoNoEncontradoException
        CT-->>AX: 404 + RespuestaError
    else Ya está cerrado
        SV-->>CT: ReglaDeNegocioException
        CT-->>AX: 409 "ya está aprobado / rechazado"
    else La obra ya tiene otro definitivo aprobado
        SV->>RP: findByObraIdObraAndTipoPresupuestoAndEstado(...)
        RP->>BD: SELECT definitivos aprobados de la obra
        BD-->>RP: lista
        RP-->>SV: no vacía
        SV-->>CT: ReglaDeNegocioException
        CT-->>AX: 409 "generá un adicional"
    else Se puede aprobar
        SV->>SV: presupuesto.aprobar()
        Note over SV: El estado lo cambia la entidad,<br/>no el servicio: la regla vive<br/>donde vive el dato

        opt Es el definitivo y la obra está en presupuestación
            SV->>SV: obra.pasarAEjecucion()
            Note over SV: Efecto encadenado: aprobar el<br/>definitivo es lo que da inicio<br/>a la obra
        end

        SV->>BD: UPDATE presupuesto, UPDATE obra
        Note over SV,BD: Una sola transacción: si falla<br/>el segundo UPDATE, no queda<br/>el presupuesto aprobado con<br/>la obra sin arrancar

        SV-->>CT: PresupuestoRespuesta
        CT-->>AX: 200 + JSON
        AX-->>UI: presupuesto actualizado
        UI-->>D: Badge "Aprobado" + obra "En ejecución"
    end
```

### Lo que este diagrama deja ver

**El controlador no tiene lógica.** Recibe, delega, devuelve. No hay `try/catch`:
las excepciones las traduce a códigos HTTP un manejador global
(`ManejadorGlobalDeErrores`), así que el formato de error es idéntico en los 45
endpoints del sistema.

**Las tres validaciones ocurren antes de tocar la base.** El diagrama las muestra
como ramas `alt` porque son excluyentes: la primera que falla corta.

**El cambio de estado de la obra está dentro de la misma transacción.** El método
es `@Transactional`, así que o se guardan los dos cambios o no se guarda
ninguno. Sin eso podría quedar un presupuesto aprobado con la obra todavía en
presupuestación — un estado que ningún registro justificaría.

---

## 5. Diagrama de secuencia — generar el PDF para el cliente

La segunda operación crítica, porque es la única salida del sistema que ve una
persona ajena a la empresa.

```mermaid
sequenceDiagram
    autonumber
    actor D as Dueño
    participant UI as PresupuestoDetalle.jsx
    participant CT as PresupuestoController
    participant SV as PresupuestoService
    participant PDF as GeneradorDePdf<br/>(OpenPDF)
    participant BD as PostgreSQL
    actor C as Cliente

    D->>UI: Clic en "Ver PDF"
    UI->>CT: GET /api/presupuestos/{id}/pdf
    Note over UI,CT: Se abre en pestaña nueva, no por Axios:<br/>el navegador ya sabe mostrar un PDF

    CT->>SV: generarPdf(id)
    SV->>BD: SELECT presupuesto + ítems + obra + cliente
    BD-->>SV: datos completos

    SV->>PDF: generar(presupuesto)
    PDF->>PDF: Membrete y datos del cliente
    PDF->>PDF: Agrupar ítems por rubro
    PDF->>PDF: Imprimir SOLO el subtotal de cada rubro
    Note over PDF: NO se imprime valor_unitario<br/>ni cantidad: son internos.<br/>Regla explícita del informe.
    PDF->>PDF: Total, anticipo y cuotas
    PDF-->>SV: byte[]

    SV-->>CT: byte[]
    CT-->>UI: 200 application/pdf
    UI-->>D: PDF en pantalla
    D->>C: Envía el PDF por WhatsApp
```

**La regla que este diagrama existe para mostrar:** el PDF imprime subtotales por
rubro, total, anticipo y cuota — nada más. Verificado sobre un presupuesto real
de 16 ítems descomprimiendo el archivo generado: aparecen exactamente 6
importes, ninguno de ellos un precio unitario.

Es la regla de negocio más delicada del módulo. Si se filtrara el valor
unitario, el cliente vería el margen sobre cada ítem.

---

## 6. Correspondencia con el código

Para poder rastrear cada diagrama hasta su implementación:

| Elemento del diagrama | Dónde está en el código |
| --- | --- |
| Circuito por tipo de obra | `PresupuestoService.validarCircuito()` |
| Versionado con base | `PresupuestoService.duplicar()` |
| Plan de pago que cierra al 100% | `PresupuestoService.definirPlanDePago()` |
| Transiciones de estado | `Presupuesto.enviar() / aprobar() / rechazar()` |
| Un solo definitivo aprobado | `PresupuestoService.exigirUnSoloDefinitivoAprobado()` |
| Obra a "En ejecución" | `PresupuestoService.aprobar()` → `Obra.pasarAEjecucion()` |
| PDF sin precios unitarios | `GeneradorDePdf` |
| Material del rubro correcto | `PresupuestoService.resolverMaterial()` |

Cada uno tiene tests en `PresupuestoServiceTest` (45 casos).
