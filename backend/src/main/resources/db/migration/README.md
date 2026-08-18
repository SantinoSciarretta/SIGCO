# Migraciones de la base de datos

Flyway ejecuta los archivos de esta carpeta, en orden de numeración, cada vez
que arranca la aplicación. Lleva su propio registro de cuáles ya aplicó en la
tabla `flyway_schema_history`, así que solo ejecuta los nuevos.

## Convención de nombres

```
V<numero>__<descripcion_en_minusculas>.sql
```

Dos guiones bajos separan el número de la descripción. Ejemplos:

```
V1__cliente.sql
V2__obra.sql
V3__rubro_subrubro.sql
```

Se crea una migración por módulo, en el orden en que se desarrollan.

## Reglas

1. **Un archivo ya aplicado no se edita nunca.** Flyway guarda una suma de
   verificación de cada migración; si el contenido cambia después de haberse
   aplicado, la aplicación no arranca. Para modificar algo, se agrega una
   migración nueva con el número siguiente.
2. **Los nombres de tablas y columnas salen del Diccionario de Datos**
   (`docs/informe-sigco.md`), sin excepciones. Hibernate está configurado con
   `ddl-auto=validate`: si una entidad Java no coincide con la tabla creada
   acá, la aplicación no inicia e indica qué campo difiere.
3. **Las restricciones se declaran acá**, no se dejan libradas a la aplicación:
   `NOT NULL` en los campos que el informe marca como obligatorios, `UNIQUE`
   donde corresponda (por ejemplo `rubro.nombre_rubro`), claves foráneas e
   índices sobre las columnas de búsqueda frecuente.
