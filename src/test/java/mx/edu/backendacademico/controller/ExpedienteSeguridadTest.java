package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumno;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.repository.AlumnoRepository;
import mx.edu.backendacademico.service.ExpedienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reto U10: filtros de seguridad REALES + servicio real con la regla por objeto.
 * Sólo se sustituye el almacenamiento. Expediente 1 = A001 (cuenta "alumno"),
 * expediente 2 = A002 (cuenta "alumno2").
 */
@WebMvcTest(ExpedienteController.class)
@Import({SecurityConfig.class, ExpedienteService.class, CuentasAlumno.class})
class ExpedienteSeguridadTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoRepository repository;

    @BeforeEach void datos() {
        var ada = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var emmy = new Alumno(2L, "A002", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO);
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada));
        when(repository.buscarPorId(2L)).thenReturn(Optional.of(emmy));
        when(repository.buscarPorMatricula("A001")).thenReturn(Optional.of(ada));
        when(repository.buscarPorMatricula("A002")).thenReturn(Optional.of(emmy));
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/expediente")).andExpect(status().isUnauthorized());
    }

    // ---- Propietario ----
    @Test void propietarioObtieneSuExpedienteDesdeElPrincipal() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A001"));
        mvc.perform(get("/api/v1/expediente").with(httpBasic("alumno2", "alumno2123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A002"));
    }

    @Test void propietarioPuedeConsultarSuExpedientePorId() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void unParametroDeUsuarioEnviadoPorElClienteSeIgnora() throws Exception {
        // Intento de suplantación: la identidad sigue siendo la de la credencial.
        mvc.perform(get("/api/v1/expediente").param("usuario", "alumno2").param("matricula", "A002")
                .with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A001"));
    }

    // ---- Usuario ajeno ----
    @Test void usuarioAjenoRecibe403() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("alumno2", "alumno2123")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void usuarioAjenoNoDistingueInexistenteDeAjeno() throws Exception {
        // id 999 no existe: el no-propietario recibe lo mismo que para un expediente ajeno.
        mvc.perform(get("/api/v1/expedientes/999").with(httpBasic("alumno2", "alumno2123")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void docenteNoEsPropietarioDeExpedientes() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("docente", "docente123")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // ---- Administrador ----
    @Test void administradorConsultaCualquierExpediente() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A001"));
        mvc.perform(get("/api/v1/expedientes/2").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("A002"));
    }

    @Test void administradorVe404ParaUnExpedienteInexistente() throws Exception {
        mvc.perform(get("/api/v1/expedientes/999").with(httpBasic("admin", "admin123")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test void administradorNoTieneExpedientePropio() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("admin", "admin123")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("EXPEDIENTE_NO_ASOCIADO"));
    }
}
