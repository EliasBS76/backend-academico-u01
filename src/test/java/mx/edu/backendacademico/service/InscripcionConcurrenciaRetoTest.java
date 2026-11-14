package mx.edu.backendacademico.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.AlumnoEntity;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.InscripcionEntity;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reto U08: admisiones simultáneas sobre una materia con UN solo lugar.
 * Requiere PostgreSQL real (docker compose up -d); no usa H2 ni perfil de prueba,
 * porque la evidencia se toma de pg_stat_activity / pg_blocking_pids.
 * La clase NO es @Transactional: cada hilo debe confirmar su propia transacción.
 */
@SpringBootTest
class InscripcionConcurrenciaRetoTest {

    @Autowired InscripcionService servicio;          // bean real (pasa por el proxy @Transactional)
    @Autowired MateriaRepository materias;
    @Autowired InscripcionRepository inscripciones;
    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    private Long materiaId;
    private Long alumnoAId;
    private Long alumnoBId;

    @BeforeEach
    void preparar() {
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        materiaId = materias.save(new MateriaEntity("RETO-" + s, "Materia de un lugar", 5, 1)).getId();
        alumnoAId = alumnos.save(new AlumnoEntity(new Alumno(null, "RA" + s, "Alumno A",
                "a" + s.toLowerCase() + "@universidad.mx", EstatusAlumno.ACTIVO))).getId();
        alumnoBId = alumnos.save(new AlumnoEntity(new Alumno(null, "RB" + s, "Alumno B",
                "b" + s.toLowerCase() + "@universidad.mx", EstatusAlumno.ACTIVO))).getId();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM inscripciones WHERE materia_id = ?", materiaId);
        jdbc.update("DELETE FROM alumnos WHERE id IN (?, ?)", alumnoAId, alumnoBId);
        jdbc.update("DELETE FROM materias WHERE id = ?", materiaId);
    }

    /** Resultado de una admisión: "OK" o el código de la causa académica. */
    private Callable<String> admision(Long alumnoId) {
        return () -> {
            try {
                servicio.inscribir(alumnoId, materiaId);
                return "OK";
            } catch (OperacionAcademicaException e) {
                return e.getCodigo();
            }
        };
    }

    // ---------------------------------------------------------------------------------
    // Parte 1 del reto: dos tareas coordinadas, un lugar, dos alumnos distintos.
    // ---------------------------------------------------------------------------------
    @Test
    void dosAdmisionesSimultaneasPersistenUnaYRechazanLaOtraPorCupo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch listos = new CountDownLatch(2);   // ambos hilos preparados
            CountDownLatch salida = new CountDownLatch(1);   // disparo simultáneo
            Callable<String> a = admision(alumnoAId);
            Callable<String> b = admision(alumnoBId);

            Future<String> fa = pool.submit(() -> { listos.countDown(); salida.await(); return a.call(); });
            Future<String> fb = pool.submit(() -> { listos.countDown(); salida.await(); return b.call(); });

            assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();

            List<String> resultados = List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList();

            assertThat(resultados).containsExactly("OK", "SIN_CUPO");
            assertThat(inscripciones.countByMateriaId(materiaId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------------------
    // Parte 2 del reto: evidencia de DÓNDE esperan los hilos.
    // El hilo de prueba toma el bloqueo de la materia en PostgreSQL y lo sostiene;
    // la admisión del alumno B queda esperando dentro de la base de datos.
    // ---------------------------------------------------------------------------------
    @Test
    void laSegundaAdmisionEsperaElBloqueoDeFilaEnPostgreSQL() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TransactionTemplate tx = new TransactionTemplate(tm);   // READ_COMMITTED por defecto en PostgreSQL

            Future<String> segunda = tx.execute(status -> {
                // 1. La "primera admisión" adquiere el bloqueo PESSIMISTIC_WRITE y escribe.
                MateriaEntity materia = materias.bloquearPorId(materiaId).orElseThrow();
                Integer pidTitular = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                inscripciones.saveAndFlush(new InscripcionEntity(
                        alumnos.getReferenceById(alumnoAId), materia, LocalDateTime.now()));

                // 2. La segunda admisión (alumno B) entra por el bean real del servicio.
                Future<String> f = pool.submit(admision(alumnoBId));

                // 3. Evidencia: PostgreSQL reporta un backend esperando un Lock bloqueado por el titular.
                esperarBloqueoEnPostgres(pidTitular);
                assertThat(f.isDone()).as("la segunda admisión debe seguir esperando").isFalse();
                return f;
            });   // <- al salir del callback se hace COMMIT y se libera el bloqueo

            // 4. Con READ COMMITTED, B ve la inscripción confirmada de A y es rechazada por cupo.
            assertThat(segunda.get(10, TimeUnit.SECONDS)).isEqualTo("SIN_CUPO");
            assertThat(inscripciones.countByMateriaId(materiaId)).isEqualTo(1);
            assertThat(inscripciones.existsByAlumnoIdAndMateriaId(alumnoAId, materiaId)).isTrue();
            assertThat(inscripciones.existsByAlumnoIdAndMateriaId(alumnoBId, materiaId)).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    private void esperarBloqueoEnPostgres(Integer pidTitular) {
        String sql = """
                select pid, wait_event_type, wait_event, left(query, 120) as consulta
                from pg_stat_activity
                where datname = current_database()
                  and wait_event_type = 'Lock'
                  and ?::int = any(pg_blocking_pids(pid))
                """;
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            var filas = jdbc.queryForList(sql, pidTitular);
            if (!filas.isEmpty()) {
                // Esta línea es la evidencia que debe capturarse en la consola.
                System.out.println("[EVIDENCIA PG] titular pid=" + pidTitular + " bloquea a: " + filas);
                return;
            }
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("PostgreSQL no mostró ningún backend esperando el bloqueo de la materia");
    }
}
