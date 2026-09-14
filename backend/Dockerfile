# =============================================================================
#  Imagen del backend de SIGCO para Railway.
#
#  Se construye en DOS etapas, y la separacion tiene un motivo concreto: para
#  compilar hace falta Maven y el JDK completo (unos 400 MB de herramientas),
#  pero para EJECUTAR alcanza con el runtime de Java. Con una sola etapa, todo
#  eso viajaria a produccion en cada despliegue.
# =============================================================================


# -----------------------------------------------------------------------------
#  Etapa 1: compilar
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS construccion

WORKDIR /build

# El pom va PRIMERO y solo, antes que el codigo. Docker guarda el resultado de
# cada paso y lo reutiliza mientras lo que entra no cambie: asi, un cambio en el
# codigo no vuelve a descargar las dependencias, que es lo que mas tarda.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# -DskipTests: los tests ya corrieron antes de subir el codigo. Volverlos a
# correr aca alargaria cada despliegue sin agregar informacion, y ademas los que
# tocan la base necesitan una que en el contenedor de build no existe.
RUN mvn -B clean package -DskipTests


# -----------------------------------------------------------------------------
#  Etapa 2: ejecutar
# -----------------------------------------------------------------------------
# JRE y no JDK: el compilador no hace falta para correr la aplicacion.
# "alpine" es una distribucion minima; la imagen final ronda los 180 MB contra
# los ~500 MB de la de construccion.
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario sin privilegios. Por defecto los contenedores corren como root, y si
# alguien lograra ejecutar codigo dentro, lo haria con todos los permisos.
RUN addgroup -S sigco && adduser -S sigco -G sigco
USER sigco

COPY --from=construccion /build/target/*.jar aplicacion.jar

# Railway asigna el puerto por la variable PORT y espera que la aplicacion
# escuche ahi. El 8080 es solo el valor por defecto para correr la imagen a mano.
ENV PORT=8080
EXPOSE 8080

# El perfil prod se activa aca y no se puede olvidar: es el que apaga el log de
# SQL y el detalle de los errores.
ENV SPRING_PROFILES_ACTIVE=prod

# exec para que Java sea el proceso principal del contenedor. Sin esto queda
# debajo de un shell, y las señales de apagado que manda Railway no le llegan:
# el contenedor se mata de golpe en lugar de cerrar bien las conexiones.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT} -jar aplicacion.jar"]
