package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.*;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.*;
import mx.edu.backendacademico.service.InscripcionService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgresIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Un puerto efímero impide depender de una base compartida de tu equipo.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired MateriaRepository materias;
    @Autowired InscripcionRepository inscripciones;
    @Autowired InscripcionService servicio;
    @Autowired AlumnoRepository adaptador;
    @Autowired JdbcTemplate jdbc;

    @Test void noSobrepasaElCupoNiRepiteUnaInscripcion() {
        var a = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(null, "P1", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO)));
        var b = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(null, "P2", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO)));
        var materia = materias.saveAndFlush(new MateriaEntity("P101", "Pruebas", 4, 1));
        servicio.inscribir(a.getId(), materia.getId());
        var repetida = assertThrows(OperacionAcademicaException.class, () -> servicio.inscribir(a.getId(), materia.getId()));
        assertEquals("INSCRIPCION_DUPLICADA", repetida.getCodigo());
        var llena = assertThrows(OperacionAcademicaException.class, () -> servicio.inscribir(b.getId(), materia.getId()));
        assertEquals("SIN_CUPO", llena.getCodigo());
        // Las llamadas anteriores pasaron por el proxy real y terminaron su transacción.
        assertEquals(1, inscripciones.countByMateriaId(materia.getId()));
    }

    // Reto U09: violación REAL de unicidad. Se omite a propósito la comprobación previa del
    // servicio (existePorMatricula) para que sea PostgreSQL quien rechace la segunda fila.
    @Test void postgresRechazaUnaMatriculaDuplicadaMedianteSuRestriccionUnique() {
        alumnos.saveAndFlush(new AlumnoEntity(new Alumno(null, "U1", "Ada", "ada.u1@u.mx", EstatusAlumno.ACTIVO)));

        // 1. Spring Data directo: la excepción proviene del motor y nombra la restricción.
        var ex = assertThrows(DataIntegrityViolationException.class, () -> alumnos.saveAndFlush(
                new AlumnoEntity(new Alumno(null, "U1", "Grace", "grace.u1@u.mx", EstatusAlumno.ACTIVO))));
        assertEquals("uk_alumnos_matricula", restriccionVioladaEn(ex));

        // 2. El adaptador traduce ESA restricción concreta al error de dominio.
        assertThrows(MatriculaDuplicadaException.class, () -> adaptador.guardar(
                new Alumno(null, "U1", "Emmy", "emmy.u1@u.mx", EstatusAlumno.ACTIVO)));

        // 3. Observación posterior e independiente: sólo existe la fila confirmada original.
        assertEquals(1, jdbc.queryForObject("select count(*) from alumnos where matricula = 'U1'", Integer.class));
    }

    private static String restriccionVioladaEn(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cve) {
                // SQLState 23505 = unique_violation en PostgreSQL.
                assertEquals("23505", cve.getSQLState());
                return cve.getConstraintName();
            }
        }
        return fail("La causa no contiene una violación de restricción: " + ex);
    }
}
