package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mx.edu.backendacademico.exception.GlobalExceptionHandler;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// Reto U06: la matricula no admite espacios internos. Prueba HTTP: la solicitud pasa por
// la deserializacion, @Valid, el controlador y el GlobalExceptionHandler, sin levantar el servidor.
class AlumnoMatriculaHttpTest {

    private AlumnoRepositoryEnMemoria repositorio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = new AlumnoRepositoryEnMemoria();
        var controlador = new AlumnoController(new AlumnoService(repositorio));
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // El texto JSON se arma con la matricula ya escapada (\\t, \\u00A0) para enviar el caracter real.
    private ResultActions crear(String matriculaJson) throws Exception {
        String cuerpo = "{\"matricula\":\"" + matriculaJson + "\",\"nombre\":\"Ada Lovelace\",\"correo\":\"ada@universidad.mx\"}";
        return mvc.perform(post("/api/v1/alumnos").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void assertRechazadaPorEspaciosInternos(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))        // codigo ya publicado, sin cambios
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].campo").value("matricula"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("La matrícula no puede contener espacios internos"));
        assertEquals(0, repositorio.buscarTodos().size(), "Una solicitud rechazada no debe guardar nada");
    }

    @Test
    void espacioInternoSeRechazaConDatosInvalidos() throws Exception {
        assertRechazadaPorEspaciosInternos(crear("A 001"));
    }

    @Test
    void tabuladorInternoSeRechaza() throws Exception {
        assertRechazadaPorEspaciosInternos(crear("A\\t001"));
    }

    @Test
    void espacioNoSeparableInternoSeRechaza() throws Exception {
        assertRechazadaPorEspaciosInternos(crear("A\\u00A0001"));
    }

    @Test
    void matriculaSinEspaciosSeAcepta() throws Exception {
        crear("A001").andExpect(status().isCreated());
        assertEquals(1, repositorio.buscarTodos().size());
    }

    @Test
    void espaciosSoloEnLosExtremosSeToleranYElDominioLosNormaliza() throws Exception {
        crear("  A001  ").andExpect(status().isCreated());
        assertEquals(1, repositorio.buscarTodos().size());
    }

    @Test
    void matriculaEnBlancoSigueSiendoUnSoloErrorDeNotBlank() throws Exception {
        // El patron deja pasar el blanco: @NotBlank ya lo rechaza y no se duplica el error del campo.
        crear("").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos.length()").value(1))
                .andExpect(jsonPath("$.campos[0].campo").value("matricula"));
    }

    @Test
    void matriculaRepetidaSigueDandoConflicto409() throws Exception {
        crear("A001").andExpect(status().isCreated());
        crear("A001").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATRICULA_DUPLICADA"));   // codigo ya publicado, sin cambios
    }
}
