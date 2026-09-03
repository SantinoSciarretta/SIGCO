# Diagrama del modelo de datos — SIGCO

**28 tablas**, creadas por 8 migraciones de Flyway sobre PostgreSQL 17.

Este diagrama es el reflejo exacto de la base `sigco_dev`, verificado campo por
campo contra el Diccionario de Datos del informe: **cero diferencias** en
nombres, tipos, obligatoriedad, claves primarias y foráneas.

No es un dibujo que haya que mantener a mano: se puede volver a verificar contra
la base en cualquier momento.

> **Estado de desarrollo.** Las 28 tablas existen, pero solo 5 módulos están
> implementados (Clientes, Obras, Presupuestación, Materiales, Proveedores). Las
> tablas de los 9 módulos restantes están creadas y vacías, esperando su módulo.

---

## 1. Diagrama entidad-relación

Las 28 entidades y sus 40 relaciones, en notación pata de gallo (*crow's foot*).

![Diagrama entidad-relación de SIGCO](img/entidad-relacion.svg)

> Si el diagrama no se ve, está también en
> [`img/entidad-relacion.png`](img/entidad-relacion.png).

**Cómo leerlo**

| Símbolo | Significa |
| --- | --- |
| Trazo perpendicular (⊢) | Lado «uno» de la relación |
| Pata de gallo (⋔) | Lado «muchos» |
| Círculo (○) | Opcional: puede no haber ninguno |
| Línea llena | Relación del negocio |
| Línea punteada | Trazabilidad: qué usuario registró el movimiento |
| Borde azul marcado | Entidad de un módulo ya desarrollado |
| Borde gris | Tabla creada, módulo pendiente |

**Qué se ve a simple vista**

`obra` es el centro del sistema: de ella cuelgan presupuestos, pedidos, gastos,
hitos, cuotas, asignaciones de personal y la publicación del portfolio. Es la
razón por la que Obras es el segundo módulo del orden de desarrollo y por la que
casi ningún otro puede funcionar sin él.

`rubro` es el segundo punto de convergencia: clasifica ítems de presupuesto,
materiales y gastos. Esa clasificación compartida es lo que hace posible
comparar lo presupuestado contra lo gastado, que es el corazón del módulo Gastos.

Las cuatro relaciones punteadas hacia `usuario` se dibujan aparte a propósito:
son trazabilidad («quién cargó esto»), no relaciones del negocio, y mezclarlas
con las llenas haría ilegible el diagrama sin agregar información.

`registro_cac` aparece suelta porque lo está: guarda el índice de la Cámara
Argentina de la Construcción mes a mes y no pertenece a ninguna obra en
particular, sirve a todas.

**Fuente:** [`entidad-relacion.dot`](entidad-relacion.dot), en formato Graphviz.
Se regenera con `dot -Kneato -Tsvg entidad-relacion.dot -o img/entidad-relacion.svg`.

---

## 2. Mapa por módulos

Las 28 entidades y sus relaciones, agrupadas por módulo.

```mermaid
flowchart LR
    subgraph CLI["CLIENTES"]
        cliente[("cliente")]
    end
    subgraph OBR["OBRAS"]
        obra[("obra")]
    end
    subgraph PRE["PRESUPUESTACIÓN"]
        rubro[("rubro")]
        subrubro[("subrubro")]
        presupuesto[("presupuesto")]
        item[("item_presupuesto")]
    end
    subgraph MAT["MATERIALES"]
        material[("material")]
    end
    subgraph PRO["PROVEEDORES"]
        proveedor[("proveedor")]
        cotizacion[("cotizacion")]
        observacion[("observacion_proveedor")]
    end
    subgraph COM["COMPRAS"]
        pedido[("pedido")]
        pedmat[("pedido_material")]
    end
    subgraph GAS["GASTOS"]
        gasto[("gasto")]
    end
    subgraph PER["PERSONAL"]
        operario[("operario")]
        opobra[("operario_obra")]
        inasistencia[("inasistencia")]
    end
    subgraph SEG["SEGUIMIENTO"]
        hito[("hito")]
        plantilla[("plantilla_hito")]
        plantdet[("plantilla_hito_detalle")]
    end
    subgraph COB["COBROS"]
        cuota[("cuota")]
        cac[("registro_cac")]
    end
    subgraph POR["PORTFOLIO"]
        publicacion[("publicacion_portfolio")]
        imagen[("imagen_portfolio")]
    end
    subgraph USU["USUARIOS Y ACCESOS"]
        usuario[("usuario")]
        rol[("rol")]
        permiso[("permiso")]
        rolperm[("rol_permiso")]
        auditoria[("registro_auditoria")]
    end

    cliente --> obra
    obra --> presupuesto
    presupuesto --> presupuesto
    presupuesto --> item
    rubro --> subrubro
    rubro --> item
    subrubro --> item
    rubro --> material
    material --> item
    material --> cotizacion
    proveedor --> cotizacion
    proveedor --> observacion
    pedido --> observacion
    obra --> pedido
    proveedor --> pedido
    pedido --> pedmat
    material --> pedmat
    obra --> gasto
    rubro --> gasto
    pedido --> gasto
    operario --> gasto
    operario --> opobra
    obra --> opobra
    operario --> inasistencia
    obra --> inasistencia
    obra --> hito
    plantilla --> plantdet
    obra --> cuota
    obra --> publicacion
    publicacion --> imagen
    rol --> rolperm
    permiso --> rolperm
    rol --> usuario
    operario --> usuario
    usuario --> auditoria

    classDef listo fill:#eef6ff,stroke:#5980a6,stroke-width:1.5px,color:#1d2d3d
    classDef pendiente fill:#f5f5f8,stroke:#b7b7ba,stroke-width:1px,color:#5d5d60
    class cliente,obra,rubro,subrubro,presupuesto,item,material,proveedor,cotizacion,observacion listo
    class pedido,pedmat,gasto,operario,opobra,inasistencia,hito,plantilla,plantdet,cuota,cac,publicacion,imagen,usuario,rol,permiso,rolperm,auditoria pendiente
```

> **Azul: módulo desarrollado. Gris: tabla creada, módulo pendiente.**
> `registro_cac` aparece sin conexiones a propósito: es una tabla de referencia
> que no pertenece a ninguna obra en particular, sirve a todas.

---

## 3. Núcleo del negocio — Clientes, Obras y Presupuestación

Es el corazón del sistema: sin obra no hay presupuesto, y sin presupuesto no hay
contra qué comparar los gastos ni de dónde sacar el plan de cobro.

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontSize':'20px','primaryColor':'#eef6ff','primaryBorderColor':'#5980a6','lineColor':'#5980a6','textColor':'#1d1f20','background':'#ffffff'}}}%%
erDiagram
    cliente ||--o{ obra : "tiene"
    obra ||--o{ presupuesto : "recibe"
    presupuesto ||--o{ presupuesto : "versiona"
    presupuesto ||--o{ item_presupuesto : "detalla"
    rubro ||--o{ subrubro : "agrupa"
    rubro ||--o{ item_presupuesto : "clasifica"
    subrubro ||--o{ item_presupuesto : "clasifica"

    cliente {
        BIGSERIAL id_cliente PK
        VARCHAR_150 nombre_apellido "obligatorio"
        VARCHAR_30 telefono_contacto
        VARCHAR_100 email_contacto
        VARCHAR_20 origen_recomendacion
        VARCHAR_150 recomendado_por
        VARCHAR_10 estado "obligatorio"
        TIMESTAMP fecha_alta "obligatorio"
    }

    obra {
        BIGSERIAL id_obra PK
        BIGINT id_cliente FK "obligatorio"
        VARCHAR_200 direccion_obra "obligatorio"
        VARCHAR_15 tipo_inmueble "obligatorio"
        VARCHAR_15 tipo_obra "obligatorio"
        DATE fecha_inicio_real
        DATE fecha_fin_estimada
        TEXT notas
        VARCHAR_20 estado "obligatorio"
        VARCHAR_200 motivo_cancelacion
        TIMESTAMP fecha_creacion "obligatorio"
    }

    presupuesto {
        BIGSERIAL id_presupuesto PK
        BIGINT id_obra FK "obligatorio"
        VARCHAR_20 tipo_presupuesto "obligatorio"
        BIGINT id_presupuesto_base FK "autorreferencia"
        INTEGER version "obligatorio"
        VARCHAR_15 estado "obligatorio"
        NUMERIC_10_2 metros_cuadrados
        NUMERIC_12_2 valor_por_m2
        NUMERIC_14_2 total_presupuesto
        NUMERIC_5_2 anticipo_porcentaje
        INTEGER cantidad_cuotas
        VARCHAR_100 plazo_estimado_obra
        TIMESTAMP fecha_creacion "obligatorio"
    }

    item_presupuesto {
        BIGSERIAL id_item PK
        BIGINT id_presupuesto FK "obligatorio"
        BIGINT id_rubro FK "obligatorio"
        BIGINT id_subrubro FK
        BIGINT id_material FK "opcional"
        VARCHAR_250 descripcion "obligatorio"
        VARCHAR_20 unidad_medida "obligatorio"
        NUMERIC_12_2 cantidad "obligatorio"
        NUMERIC_12_2 valor_unitario "interno"
        NUMERIC_14_2 subtotal "obligatorio"
    }

    rubro {
        BIGSERIAL id_rubro PK
        VARCHAR_100 nombre_rubro "obligatorio, unico"
        VARCHAR_10 estado "obligatorio"
    }

    subrubro {
        BIGSERIAL id_subrubro PK
        BIGINT id_rubro FK "obligatorio"
        VARCHAR_100 nombre_subrubro "obligatorio"
        VARCHAR_10 estado "obligatorio"
    }
```

**La autorreferencia de `presupuesto` es la pieza más importante del modelo.**
`id_presupuesto_base` apunta al presupuesto del que salió éste. El definitivo no
sobrescribe al anteproyecto: son dos registros independientes vinculados. Así se
reconstruye la negociación completa, que es exactamente lo que hoy se pierde
cuando cada versión pisa el archivo de Excel anterior.

---

## 4. Cadena de abastecimiento — Materiales, Proveedores y Compras

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontSize':'20px','primaryColor':'#eef6ff','primaryBorderColor':'#5980a6','lineColor':'#5980a6','textColor':'#1d1f20','background':'#ffffff'}}}%%
erDiagram
    rubro ||--o{ material : "clasifica"
    material ||--o{ cotizacion : "se cotiza en"
    proveedor ||--o{ cotizacion : "informa"
    proveedor ||--o{ observacion_proveedor : "registra"
    pedido ||--o{ observacion_proveedor : "origina"
    obra ||--o{ pedido : "genera"
    proveedor ||--o{ pedido : "recibe"
    pedido ||--o{ pedido_material : "detalla"
    material ||--o{ pedido_material : "se pide en"

    material {
        BIGSERIAL id_material PK
        VARCHAR_150 nombre_material "obligatorio"
        BIGINT id_rubro FK "obligatorio"
        VARCHAR_20 unidad_medida "obligatorio"
        VARCHAR_10 estado "obligatorio"
        TIMESTAMP fecha_alta "obligatorio"
    }

    proveedor {
        BIGSERIAL id_proveedor PK
        VARCHAR_150 nombre_proveedor "obligatorio"
        VARCHAR_100 zona_cobertura "obligatorio"
        VARCHAR_30 telefono_contacto
        VARCHAR_100 email_contacto
        VARCHAR_10 estado "obligatorio"
        TIMESTAMP fecha_alta "obligatorio"
    }

    cotizacion {
        BIGSERIAL id_cotizacion PK
        BIGINT id_proveedor FK "obligatorio"
        BIGINT id_material FK "obligatorio"
        NUMERIC_12_2 precio_cotizado "obligatorio"
        TIMESTAMP fecha_cotizacion "obligatorio"
    }

    observacion_proveedor {
        BIGSERIAL id_observacion PK
        BIGINT id_proveedor FK "obligatorio"
        BIGINT id_pedido FK
        VARCHAR_300 descripcion "obligatorio"
        TIMESTAMP fecha "obligatorio"
    }

    pedido {
        BIGSERIAL id_pedido PK
        BIGINT id_obra FK "obligatorio"
        BIGINT id_proveedor FK "obligatorio"
        BIGINT id_usuario_solicita FK
        BIGINT id_usuario_recibe FK
        VARCHAR_25 estado "obligatorio"
        VARCHAR_255 foto_remito "Supabase"
        VARCHAR_300 nota_diferencia
        TIMESTAMP fecha_solicitud "obligatorio"
        TIMESTAMP fecha_aprobacion
        TIMESTAMP fecha_recepcion
    }

    pedido_material {
        BIGINT id_pedido PK_FK
        BIGINT id_material PK_FK
        NUMERIC_12_2 cantidad "obligatorio"
    }
```

**Una cotización no se corrige: se agrega otra.** Por eso `cotizacion` no tiene
campo de modificación. Cada precio informado queda como registro con su fecha, y
la evolución del precio de un material es información que hoy la empresa no
tiene de ninguna forma.

---

## 5. Ejecución y control — Gastos, Personal y Seguimiento

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontSize':'20px','primaryColor':'#eef6ff','primaryBorderColor':'#5980a6','lineColor':'#5980a6','textColor':'#1d1f20','background':'#ffffff'}}}%%
erDiagram
    obra ||--o{ gasto : "acumula"
    rubro ||--o{ gasto : "clasifica"
    subrubro ||--o{ gasto : "clasifica"
    pedido ||--o{ gasto : "origina"
    operario ||--o{ gasto : "atribuye"
    operario ||--o{ operario_obra : "se asigna"
    obra ||--o{ operario_obra : "asigna"
    operario ||--o{ inasistencia : "falta"
    obra ||--o{ inasistencia : "registra"
    obra ||--o{ hito : "avanza por"
    plantilla_hito ||--o{ plantilla_hito_detalle : "define"

    gasto {
        BIGSERIAL id_gasto PK
        BIGINT id_obra FK "obligatorio"
        BIGINT id_rubro FK "obligatorio"
        BIGINT id_subrubro FK
        VARCHAR_15 tipo_gasto "obligatorio"
        NUMERIC_14_2 monto "obligatorio"
        DATE fecha_gasto "obligatorio"
        TIMESTAMP fecha_carga "obligatorio"
        BIGINT id_pedido FK
        BIGINT id_operario FK
        VARCHAR_250 descripcion
        VARCHAR_255 comprobante_adjunto "Supabase"
        BIGINT id_usuario_registro FK
        VARCHAR_12 estado "obligatorio"
        VARCHAR_200 motivo_anulacion
    }

    operario {
        BIGSERIAL id_operario PK
        VARCHAR_150 nombre_apellido "obligatorio"
        VARCHAR_30 telefono_contacto
        VARCHAR_10 estado "obligatorio"
        TIMESTAMP fecha_alta "obligatorio"
    }

    operario_obra {
        BIGINT id_operario PK_FK
        BIGINT id_obra PK_FK
        DATE fecha_asignacion "obligatorio"
    }

    inasistencia {
        BIGSERIAL id_inasistencia PK
        BIGINT id_operario FK "obligatorio"
        BIGINT id_obra FK "obligatorio"
        DATE fecha_falta "obligatorio"
        VARCHAR_200 motivo "opcional"
        BIGINT id_usuario_registro FK
    }

    hito {
        BIGSERIAL id_hito PK
        BIGINT id_obra FK "obligatorio"
        VARCHAR_150 nombre_hito "obligatorio"
        NUMERIC_5_2 ponderacion "obligatorio"
        INTEGER orden "obligatorio"
        VARCHAR_12 estado "obligatorio"
        DATE fecha_cumplimiento
        VARCHAR_250 observacion
        BIGINT id_usuario_completa FK
    }

    plantilla_hito {
        BIGSERIAL id_plantilla PK
        VARCHAR_100 nombre_plantilla "obligatorio"
        TIMESTAMP fecha_creacion "obligatorio"
    }

    plantilla_hito_detalle {
        BIGSERIAL id_detalle PK
        BIGINT id_plantilla FK "obligatorio"
        VARCHAR_150 nombre_hito "obligatorio"
        NUMERIC_5_2 ponderacion "obligatorio"
        INTEGER orden "obligatorio"
    }
```

**`gasto` comparte `id_rubro` con `item_presupuesto`.** Ésa es la clave del
semáforo de desvío: la misma clasificación de un lado y del otro permite
comparar, por rubro, lo presupuestado contra lo gastado. Si cada módulo tuviera
su propia lista de rubros, la comparación no existiría.

---

## 6. Cobranza y difusión — Cobros y Portfolio

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontSize':'20px','primaryColor':'#eef6ff','primaryBorderColor':'#5980a6','lineColor':'#5980a6','textColor':'#1d1f20','background':'#ffffff'}}}%%
erDiagram
    obra ||--o{ cuota : "se cobra en"
    obra ||--|| publicacion_portfolio : "se publica como"
    publicacion_portfolio ||--o{ imagen_portfolio : "muestra"

    cuota {
        BIGSERIAL id_cuota PK
        BIGINT id_obra FK "obligatorio"
        INTEGER numero_cuota "0 = anticipo"
        NUMERIC_14_2 monto_cuota "obligatorio"
        DATE fecha_vencimiento "obligatorio"
        VARCHAR_12 estado "obligatorio"
        DATE fecha_pago
        VARCHAR_15 medio_pago
        VARCHAR_20 comprobante_emitido
    }

    registro_cac {
        BIGSERIAL id_cac PK
        DATE mes_correspondiente "obligatorio, unico"
        NUMERIC_8_4 valor_indice "obligatorio"
        TIMESTAMP fecha_carga "obligatorio"
    }

    publicacion_portfolio {
        BIGSERIAL id_publicacion PK
        BIGINT id_obra FK "obligatorio, unico"
        VARCHAR_30 tipo_trabajo "obligatorio"
        VARCHAR_15 estado "obligatorio"
        TIMESTAMP fecha_publicacion "obligatorio"
    }

    imagen_portfolio {
        BIGSERIAL id_imagen PK
        BIGINT id_publicacion FK "obligatorio"
        VARCHAR_255 url_imagen "Supabase"
        INTEGER orden "obligatorio"
    }
```

**`registro_cac` no se relaciona con nada.** Es una tabla de referencia: guarda
el índice de la Cámara Argentina de la Construcción mes a mes, cargado a mano, y
Cobros lo usa para actualizar el saldo. No tiene FK porque no pertenece a
ninguna obra en particular: sirve a todas.

---

## 7. Seguridad — Usuarios y Accesos

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontSize':'20px','primaryColor':'#eef6ff','primaryBorderColor':'#5980a6','lineColor':'#5980a6','textColor':'#1d1f20','background':'#ffffff'}}}%%
erDiagram
    rol ||--o{ rol_permiso : "tiene"
    permiso ||--o{ rol_permiso : "se asigna en"
    rol ||--o{ usuario : "define"
    operario |o--o| usuario : "puede acceder como"
    usuario ||--o{ registro_auditoria : "genera"

    rol {
        BIGSERIAL id_rol PK
        VARCHAR_50 nombre_rol "obligatorio, unico"
        VARCHAR_200 descripcion
    }

    permiso {
        BIGSERIAL id_permiso PK
        VARCHAR_100 nombre_permiso "obligatorio, unico"
        VARCHAR_50 modulo "obligatorio"
        VARCHAR_200 descripcion
    }

    rol_permiso {
        BIGINT id_rol PK_FK
        BIGINT id_permiso PK_FK
    }

    usuario {
        BIGSERIAL id_usuario PK
        VARCHAR_50 nombre_usuario "obligatorio, unico"
        VARCHAR_255 contrasena_hash "BCrypt"
        BIGINT id_rol FK "obligatorio"
        BIGINT id_operario FK "opcional"
        VARCHAR_10 estado "obligatorio"
        TIMESTAMP ultima_fecha_acceso
        TIMESTAMP fecha_alta "obligatorio"
    }

    registro_auditoria {
        BIGSERIAL id_auditoria PK
        BIGINT id_usuario FK "obligatorio"
        VARCHAR_150 accion_realizada "obligatorio"
        VARCHAR_50 modulo_afectado "obligatorio"
        TIMESTAMP fecha_hora "obligatorio"
    }
```

**`usuario` y `operario` son cosas distintas.** Personal registra a todos los
operarios, trabajen o no con el sistema. Usuarios gestiona solo las cuentas de
acceso. La relación es 0..1 a 0..1: un operario puede tener cuenta (para
confirmar recepciones desde el celular) o no tenerla, y el dueño tiene cuenta
sin ser operario.

---

## 8. Restricciones que el diagrama no muestra

Un diagrama entidad-relación muestra estructura, no reglas. Éstas están en la
base como `CHECK` e índices únicos, y son parte del modelo tanto como las FK.

### Unicidad insensible a mayúsculas

`rubro`, `material`, `proveedor`, `rol`, `permiso`, `usuario` y `plantilla_hito`
tienen índices únicos sobre `LOWER(nombre)`. Un `UNIQUE` común distingue
mayúsculas y dejaría entrar "Albañilería" y "albañilería" como registros
distintos — exactamente el problema que estos catálogos vienen a eliminar.

`proveedor` es el caso especial: la unicidad es sobre **nombre + zona**, porque
una cadena de corralones puede tener sucursales homónimas en zonas distintas y,
a efectos de a quién pedirle, son proveedores diferentes.

### Reglas condicionales

| Tabla | Restricción | Regla del informe |
| --- | --- | --- |
| `obra` | `estado <> 'Cancelada' OR motivo_cancelacion IS NOT NULL` | No se cancela una obra sin dejar registrado por qué. |
| `gasto` | `estado <> 'Anulado' OR motivo_anulacion IS NOT NULL` | Mismo criterio para la anulación de un gasto. |
| `hito` | `estado <> 'Completado' OR fecha_cumplimiento IS NOT NULL` | La fecha de cumplimiento es obligatoria al completar un hito. |
| `cuota` | `estado <> 'Abonada' OR fecha_pago IS NOT NULL` | La fecha de pago es obligatoria al marcar una cuota abonada. |
| `pedido` | `estado <> 'Recibido con Diferencias' OR nota_diferencia IS NOT NULL` | Si se recibió con diferencias, hay que decir cuáles. |
| `presupuesto` | `id_presupuesto_base <> id_presupuesto` | Un presupuesto no puede ser su propia base: cerraría la cadena de versionado en un ciclo. |

### Unicidad compuesta

| Tabla | Restricción | Por qué |
| --- | --- | --- |
| `inasistencia` | `(id_operario, id_obra, fecha_falta)` | No dos inasistencias del mismo operario, misma fecha y obra. |
| `cuota` | `(id_obra, numero_cuota)` | Una obra no puede tener dos veces la misma cuota. |
| `registro_cac` | `(mes_correspondiente)` | Un solo índice por mes: dos daría dos actualizaciones del mismo saldo. |
| `usuario` | `(id_operario)` parcial | Un operario no puede tener dos cuentas de acceso. |
| `publicacion_portfolio` | `(id_obra)` | Una obra se publica una sola vez. |

### Rangos

`presupuesto.anticipo_porcentaje` entre 0 y 100 · `presupuesto.version` ≥ 1 ·
`item_presupuesto.cantidad` > 0 · `gasto.monto` > 0 · `cuota.numero_cuota` ≥ 0
(el anticipo es la cuota cero) · `hito.ponderacion` entre 0 y 100 ·
`registro_cac.valor_indice` > 0.

---

## 9. Cómo verificar este diagrama contra la base

En **pgAdmin 4**: clic derecho sobre la base `sigco_dev` → **«ERD For Database»**.
Genera el diagrama desde el esquema real y permite exportarlo como imagen.

Por consola, para ver la definición completa de una tabla:

```
psql -U postgres -d sigco_dev -c "\d+ presupuesto"
```

---

## 10. Migraciones que construyen este modelo

| Migración | Crea |
| --- | --- |
| `V1__cliente.sql` | `cliente` |
| `V2__obra.sql` | `obra` |
| `V3__rubro_subrubro.sql` | `rubro`, `subrubro` |
| `V4__presupuesto.sql` | `presupuesto`, `item_presupuesto` |
| `V5__material.sql` | `material` |
| `V6__proveedor.sql` | `proveedor`, `cotizacion`, `observacion_proveedor` |
| `V7__item_presupuesto_material.sql` | Vínculo `item_presupuesto` → `material` |
| `V8__resto_del_modelo.sql` | Las 18 tablas de los módulos pendientes, y la FK `observacion_proveedor` → `pedido` que V6 dejó anotada |

El esquema **no lo genera Hibernate**: la aplicación corre con
`ddl-auto=validate`, que solo verifica que las entidades coincidan con las
tablas y se niega a arrancar si difieren. La base es literalmente el Diccionario
de Datos, y cada arranque lo comprueba.
