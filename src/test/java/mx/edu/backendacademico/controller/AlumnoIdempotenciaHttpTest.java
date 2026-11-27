package mx.edu.backendacademico.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Reto U11: el contrato HTTP de Idempotency-Key, con filtros de seguridad y PostgreSQL reales. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AlumnoIdempotenciaHttpTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;

    private ResultActions crear(String clave, String matricula, String nombre) throws Exception {
        var peticion = post("/api/v1/alumnos").with(httpBasic("docente", "docente123")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"matricula":"%s","nombre":"%s","correo":"%s@u.mx"}
                    """.formatted(matricula, nombre, matricula.toLowerCase()));
        if (clave != null) peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion);
    }

    @Test
    void laRepeticionDevuelveLaMismaRespuestaYElContenidoDistintoRecibe422() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String clave = UUID.randomUUID().toString();

        var original = crear(clave, "H" + s, "Ada Lovelace")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse();

        // Mismo contenido: misma respuesta, mismo Location, marcada como repetición.
        crear(clave, "H" + s, "Ada Lovelace")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", original.getHeader("Location")))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(content().json(original.getContentAsString()));

        // Misma clave, contenido distinto: 422 con ProblemDetail.
        crear(clave, "X" + s, "Grace Hopper")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CONTENIDO_DISTINTO"));
    }

    @Test
    void sinCabeceraSeConservaElContratoAnterior() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        crear(null, "N" + s, "Ada Lovelace").andExpect(status().isCreated());
        // Sin clave no hay deduplicación: la repetición choca con la matrícula, como en U10.
        crear(null, "N" + s, "Ada Lovelace").andExpect(status().isConflict());
    }

    @Test
    void unaClaveMalFormadaRecibe400() throws Exception {
        crear("x".repeat(101), "M" + UUID.randomUUID().toString().substring(0, 8), "Ada Lovelace")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_INVALIDA"));
    }
}
