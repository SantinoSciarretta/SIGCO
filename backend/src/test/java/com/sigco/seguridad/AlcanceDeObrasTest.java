package com.sigco.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sigco.accesos.Rol;
import com.sigco.common.exception.ReglaDeNegocioException;
import com.sigco.personal.OperarioObraRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del alcance por obra.
 *
 * Es el "(su obra)" de la matriz del informe, que un permiso por modulo no
 * puede expresar: `compras.ver` habilita el modulo Compras entero, y lo que
 * falta responder es si puede ver ESTE pedido.
 *
 * Lo que se verifica es que el limite exista solo donde corresponde y que el
 * caso borde —un capataz sin operario vinculado— no termine dandole acceso a
 * todo, que es justo lo contrario de lo que la regla busca.
 */
@ExtendWith(MockitoExtension.class)
class AlcanceDeObrasTest {

    @Mock private SesionActual sesion;
    @Mock private OperarioObraRepository asignaciones;

    @InjectMocks private AlcanceDeObras alcance;

    /** Deja la sesion como si hubiera entrado alguien con ese rol. */
    private void comoRol(String nombreRol, Long idOperario) {
        UsuarioAutenticado usuario = org.mockito.Mockito.mock(UsuarioAutenticado.class);
        lenient().when(usuario.getNombreRol()).thenReturn(nombreRol);
        lenient().when(usuario.getIdOperario()).thenReturn(idOperario);
        when(sesion.autenticado()).thenReturn(Optional.of(usuario));
        lenient().when(sesion.idOperario()).thenReturn(Optional.ofNullable(idOperario));
    }

    // ------------------------------------------------------------------
    //  Los roles sin límite
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El dueño alcanza cualquier obra")
    void elDuenoAlcanzaTodo() {
        comoRol(Rol.DUENO, null);

        assertThat(alcance.alcanza(1L)).isTrue();
        assertThat(alcance.alcanza(999L)).isTrue();
        assertThat(alcance.obrasPermitidas()).isEmpty();

        // Y no se consulta a Personal: seria una consulta por peticion que no
        // cambia el resultado, en el caso mas frecuente del sistema.
        verify(asignaciones, never()).obrasVigentesDe(org.mockito.ArgumentMatchers.anyLong());
    }

    /**
     * El informe: "ve seguimiento de TODAS las obras activas". Su limite es por
     * modulo (no ve cobros ni presupuestos), no por obra.
     */
    @Test
    @DisplayName("El capataz general alcanza cualquier obra")
    void elCapatazGeneralAlcanzaTodo() {
        comoRol(Rol.CAPATAZ_GENERAL, 7L);

        assertThat(alcance.alcanza(42L)).isTrue();
        verify(asignaciones, never()).obrasVigentesDe(org.mockito.ArgumentMatchers.anyLong());
    }

    // ------------------------------------------------------------------
    //  El capataz de obra
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El capataz de obra alcanza solo las obras que tiene asignadas")
    void elCapatazDeObraSoloLasSuyas() {
        comoRol(Rol.CAPATAZ_DE_OBRA, 7L);
        when(asignaciones.obrasVigentesDe(7L)).thenReturn(List.of(3L, 5L));

        assertThat(alcance.alcanza(3L)).isTrue();
        assertThat(alcance.alcanza(5L)).isTrue();
        assertThat(alcance.alcanza(9L)).isFalse();
    }

    @Test
    @DisplayName("Operar sobre una obra ajena se rechaza")
    void obraAjenaSeRechaza() {
        comoRol(Rol.CAPATAZ_DE_OBRA, 7L);
        when(asignaciones.obrasVigentesDe(7L)).thenReturn(List.of(3L));

        assertThatThrownBy(() -> alcance.exigirAlcance(9L))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("obras que tenés asignadas");

        // Sobre la propia no falla.
        alcance.exigirAlcance(3L);
    }

    /**
     * El caso borde importante: sin el vinculo usuario -> operario no hay forma
     * de saber cual es su obra. Suponer que son todas seria darle justo lo que
     * la regla le niega.
     */
    @Test
    @DisplayName("Un capataz de obra SIN operario vinculado no alcanza ninguna obra")
    void sinOperarioVinculadoNoAlcanzaNada() {
        comoRol(Rol.CAPATAZ_DE_OBRA, null);

        assertThat(alcance.alcanza(1L)).isFalse();
        assertThat(alcance.obrasPermitidas()).contains(List.of());
    }

    @Test
    @DisplayName("Un capataz de obra sin obras asignadas no alcanza ninguna")
    void sinObrasAsignadasNoAlcanzaNada() {
        comoRol(Rol.CAPATAZ_DE_OBRA, 7L);
        when(asignaciones.obrasVigentesDe(7L)).thenReturn(List.of());

        assertThat(alcance.alcanza(1L)).isFalse();
    }

    // ------------------------------------------------------------------
    //  Sin sesión
    // ------------------------------------------------------------------

    /**
     * Sin sesion no hay limite por obra, y esta bien: llegar hasta aca sin
     * autenticarse ya deberia ser imposible (lo corta la cadena de filtros), y
     * si pasara, el problema seria ese y no el alcance. Poner la decision en
     * dos lugares es lo que hace que una de las dos quede desactualizada.
     */
    @Test
    @DisplayName("Sin sesión no se aplica límite por obra")
    void sinSesionNoHayLimite() {
        when(sesion.autenticado()).thenReturn(Optional.empty());

        assertThat(alcance.alcanza(1L)).isTrue();
    }
}
