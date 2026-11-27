package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumno;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.PaginaResponse;
import mx.edu.backendacademico.repository.AlumnoRepository;
import mx.edu.backendacademico.service.ExpedienteService;
import mx.edu.backendacademico.service.InscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reto U12: mejora de acceso por propietario en GET /api/v1/inscripciones/alumno/{id}.
 * Filtros reales + regla real de ExpedienteService; sólo se sustituyen los almacenes.
 * Alumno 1 = A001 (cuenta "alumno"), alumno 2 = A002 (cuenta "alumno2").
 */
@WebMvcTest(InscripcionController.class)
@Import({SecurityConfig.class, ExpedienteService.class, CuentasAlumno.class})
class HistorialPropietarioTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoRepository repository;
    @MockitoBean InscripcionService inscripciones;

    @BeforeEach void datos() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(
                new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO)));
        when(repository.buscarPorId(2L)).thenReturn(Optional.of(
                new Alumno(2L, "A002", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO)));
        when(inscripciones.historial(any(), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));
    }

    @Test void elAlumnoVeSuPropioHistorial() throws Exception {
        mvc.perform(get("/api/v1/inscripciones/alumno/1").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk());
    }

    @Test void elAlumnoNoVeElHistorialAjenoNiSeConsultaLaBase() throws Exception {
        mvc.perform(get("/api/v1/inscripciones/alumno/2").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verify(inscripciones, never()).historial(eq(2L), any());
    }

    @Test void unIdInexistenteSeVeIgualQueUnoAjenoParaElAlumno() throws Exception {
        // No revela qué ids existen: 403, no 404.
        mvc.perform(get("/api/v1/inscripciones/alumno/999").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden());
    }

    @Test void docenteYAdminVenCualquierHistorial() throws Exception {
        mvc.perform(get("/api/v1/inscripciones/alumno/2").with(httpBasic("docente", "docente123")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/inscripciones/alumno/2").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk());
    }

    @Test void sinCredencialesSigueSiendo401() throws Exception {
        mvc.perform(get("/api/v1/inscripciones/alumno/1")).andExpect(status().isUnauthorized());
    }
}
