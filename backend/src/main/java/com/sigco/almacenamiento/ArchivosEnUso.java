package com.sigco.almacenamiento;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responde si algún registro del sistema apunta a un archivo.
 *
 * ------------------------------------------------------------------
 *  Para qué existe
 * ------------------------------------------------------------------
 *
 * Los archivos se suben ANTES de guardar el formulario: el capataz saca la foto
 * del remito, se sube y recién después toca "Confirmar recepción". Es lo
 * correcto para el usuario —ve la foto antes de confirmar— pero deja un cabo
 * suelto: si cierra el formulario sin guardar, el archivo ya está en el
 * almacenamiento y no lo referencia nadie. Ocupa lugar para siempre.
 *
 * Para poder borrarlos hace falta un endpoint de borrado, y un endpoint de
 * borrado sin control sería un problema peor que el que resuelve: cualquiera
 * con una referencia podría borrar la foto del remito de una recepción ya
 * confirmada, o una imagen del portfolio.
 *
 * Esta clase es ese control. Un archivo se puede borrar solamente si NINGUNA
 * tabla lo referencia, es decir, solo si es huérfano. Un adjunto ya guardado
 * queda protegido por el propio sistema, sin depender de quién llame.
 *
 * ------------------------------------------------------------------
 *  Por qué una consulta y no un repositorio por módulo
 * ------------------------------------------------------------------
 *
 * Las tres columnas que guardan referencias viven en módulos distintos
 * (gasto, pedido, imagen_portfolio) y ninguna tiene un repositorio con un
 * método para esto. Agregar tres métodos repartidos en tres módulos para una
 * pregunta que es una sola dejaría la respuesta partida en tres lugares: el día
 * que se sume una cuarta columna que guarde archivos, habría que acordarse de
 * los tres.
 *
 * Acá está la lista completa en un solo lugar, y es el lugar donde hay que
 * agregar la próxima.
 */
@Component
public class ArchivosEnUso {

    @PersistenceContext
    private EntityManager em;

    /**
     * Si algún registro apunta a esta referencia.
     *
     * Ante la duda contesta que SÍ está en uso: si por lo que fuera la consulta
     * fallara, no borrar un archivo huérfano es un desperdicio de unos kilobytes;
     * borrar uno en uso deja un remito sin su foto y no hay forma de recuperarlo.
     */
    @Transactional(readOnly = true)
    public boolean estaEnUso(String referencia) {
        try {
            return contar("select count(g) from Gasto g where g.comprobanteAdjunto = :ref",
                          referencia)
                || contar("select count(p) from Pedido p where p.fotoRemito = :ref", referencia)
                || contar("select count(i) from ImagenPortfolio i where i.urlImagen = :ref",
                          referencia);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private boolean contar(String consulta, String referencia) {
        Long cantidad = em.createQuery(consulta, Long.class)
                .setParameter("ref", referencia)
                .getSingleResult();
        return cantidad != null && cantidad > 0;
    }
}
