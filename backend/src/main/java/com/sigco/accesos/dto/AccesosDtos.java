package com.sigco.accesos.dto;

import com.sigco.accesos.Permiso;
import com.sigco.accesos.RegistroAuditoria;
import com.sigco.accesos.Rol;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** Entradas y salidas del modulo Accesos. */
public final class AccesosDtos {

    /**
     * Constructor privado: esta clase solo agrupa los formatos de datos que
     * entran y salen del módulo Accesos, no se crean objetos de ella.
     */
    private AccesosDtos() {
    }

    // ------------------------------------------------------------------
    //  Salidas
    // ------------------------------------------------------------------

    public record PermisoRespuesta(
            Long idPermiso,
            String nombrePermiso,
            String modulo,
            String descripcion) {

        /**
         * Convierte un permiso guardado en la base en el formato que se le
         * envía a la pantalla.
         */
        public static PermisoRespuesta desde(Permiso p) {
            return new PermisoRespuesta(p.getIdPermiso(), p.getNombrePermiso(),
                    p.getModulo(), p.getDescripcion());
        }
    }

    /**
     * Un rol con los ids de sus permisos.
     *
     * Manda los ids y no los permisos completos porque la pantalla ya tiene el
     * catalogo entero: repetir la descripcion de cada permiso dentro de cada
     * rol multiplicaria por tres la misma informacion.
     */
    public record RolRespuesta(
            Long idRol,
            String nombreRol,
            String descripcion,
            List<Long> idsPermisos,
            /** Cuantas cuentas tienen este rol. Se muestra antes de quitar un permiso. */
            long cuentasActivas) {

        /**
         * Convierte un rol guardado en la base en el formato que se le envía a
         * la pantalla, sumándole la cantidad de cuentas activas.
         */
        public static RolRespuesta desde(Rol r, long cuentasActivas) {
            return new RolRespuesta(
                    r.getIdRol(), r.getNombreRol(), r.getDescripcion(),
                    r.getPermisos().stream().map(Permiso::getIdPermiso).sorted().toList(),
                    cuentasActivas);
        }
    }

    public record AuditoriaRespuesta(
            Long idAuditoria,
            Long idUsuario,
            String nombreUsuario,
            String accionRealizada,
            String moduloAfectado,
            LocalDateTime fechaHora) {

        /**
         * Convierte un registro de auditoría en el formato que se muestra en la
         * pantalla, con el nombre del usuario que hizo la acción.
         */
        public static AuditoriaRespuesta desde(RegistroAuditoria a) {
            return new AuditoriaRespuesta(
                    a.getIdAuditoria(),
                    a.getUsuario().getIdUsuario(),
                    a.getUsuario().getNombreUsuario(),
                    a.getAccionRealizada(),
                    a.getModuloAfectado(),
                    a.getFechaHora());
        }
    }

    // ------------------------------------------------------------------
    //  Entradas
    // ------------------------------------------------------------------

    /**
     * Los permisos que queda teniendo un rol.
     *
     * Se manda la lista COMPLETA y no "agregar este" / "quitar aquel": la
     * pantalla es una grilla de casillas donde el dueño marca y desmarca varias
     * antes de guardar. Aplicar cada cambio por separado dejaria el rol en
     * estados intermedios que nadie pidio, y ademas dos personas editando a la
     * vez terminarian con una mezcla de ambas ediciones.
     */
    public record PermisosDelRol(
            @NotNull(message = "Hay que indicar la lista de permisos")
            List<Long> idsPermisos) {
    }
}
