package mx.edu.backendacademico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.exception.ContenidoIdempotenteDistintoException;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Reto U11: clave de idempotencia para crear alumnos.
 * Se escribió ANTES que la implementación. Usa PostgreSQL real (Testcontainers) porque lo que
 * se prueba es justamente cómo la base serializa dos reservas de la misma clave; H2 o un mapa
 * en memoria no demostrarían nada de eso.
 * La clase no es @Transactional: cada hilo tiene que confirmar su propia transacción.
 */
@SpringBootTest
@Testcontainers
class CreacionIdempotenteRetoTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CreacionIdempotenteService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Lanza las dos tareas a la vez: ambas esperan la misma señal de salida. */
    private static List<Object> simultaneas(Callable<Object> a, Callable<Object> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var listos = new CountDownLatch(2);
            var salida = new CountDownLatch(1);
            Future<Object> fa = pool.submit(() -> { listos.countDown(); salida.await(); return a.call(); });
            Future<Object> fb = pool.submit(() -> { listos.countDown(); salida.await(); return b.call(); });
            assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            return List.of(fa.get(15, TimeUnit.SECONDS), fb.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    /** Devuelve el resultado o, si fue rechazada por contenido distinto, el código del rechazo. */
    private Callable<Object> crear(String usuario, String clave, String matricula, String nombre) {
        return () -> {
            try {
                return servicio.crear(usuario, clave, matricula, nombre, matricula.toLowerCase() + "@u.mx");
            } catch (ContenidoIdempotenteDistintoException e) {
                return "IDEMPOTENCIA_CONTENIDO_DISTINTO";
            }
        };
    }

    private int alumnosConMatricula(String... matriculas) {
        int total = 0;
        for (String m : matriculas) {
            total += jdbc.queryForObject("select count(*) from alumnos where matricula = ?", Integer.class, m);
        }
        return total;
    }

    // ---------------------------------------------------------------------------------
    // 1. Dos solicitudes simultáneas, misma clave, mismo contenido -> un solo alumno.
    // ---------------------------------------------------------------------------------
    @Test
    void dosSolicitudesSimultaneasConLaMismaClaveCreanUnSoloAlumno() throws Exception {
        String s = sufijo();
        String clave = "clave-" + s;
        String matricula = "ID" + s;

        var resultados = simultaneas(
                crear("docente", clave, matricula, "Ada Lovelace"),
                crear("docente", clave, matricula, "Ada Lovelace"));

        var r1 = (CreacionIdempotenteService.Resultado) resultados.get(0);
        var r2 = (CreacionIdempotenteService.Resultado) resultados.get(1);

        // Las dos reciben el MISMO alumno y el mismo cuerpo; exactamente una es la original.
        assertThat(r1.recursoId()).isEqualTo(r2.recursoId());
        assertThat(r1.cuerpo()).isEqualTo(r2.cuerpo());
        assertThat(r1.estado()).isEqualTo(201);
        assertThat(r2.estado()).isEqualTo(201);
        assertThat(List.of(r1.repetida(), r2.repetida())).containsExactlyInAnyOrder(true, false);

        // Observación independiente en la base: una fila de alumno y una de clave.
        assertThat(alumnosConMatricula(matricula)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from claves_idempotencia where clave = ?", Integer.class, clave)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------
    // 2. Misma clave, distinto contenido (también simultáneas) -> una se crea, la otra se rechaza.
    // ---------------------------------------------------------------------------------
    @Test
    void mismaClaveConDistintoContenidoSeRechazaYNoCreaNada() throws Exception {
        String s = sufijo();
        String clave = "clave-" + s;
        String matriculaA = "IA" + s;
        String matriculaB = "IB" + s;

        var resultados = simultaneas(
                crear("docente", clave, matriculaA, "Ada Lovelace"),
                crear("docente", clave, matriculaB, "Grace Hopper"));

        // No importa cuál gane la carrera: la otra siempre se rechaza por contenido distinto.
        assertThat(resultados).filteredOn(r -> r instanceof CreacionIdempotenteService.Resultado).hasSize(1);
        assertThat(resultados).filteredOn("IDEMPOTENCIA_CONTENIDO_DISTINTO"::equals).hasSize(1);
        assertThat(alumnosConMatricula(matriculaA, matriculaB)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------
    // 3. Evidencia de DÓNDE espera la segunda: en el índice único de PostgreSQL.
    // El hilo de prueba hace la primera creación dentro de una transacción que NO confirma
    // todavía; la segunda solicitud queda bloqueada en el INSERT de su reserva.
    // ---------------------------------------------------------------------------------
    @Test
    void laRepeticionEsperaLaConfirmacionDeLaPrimeraYDevuelveSuRespuesta() throws Exception {
        String s = sufijo();
        String clave = "clave-" + s;
        String matricula = "IW" + s;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var tx = new TransactionTemplate(tm);
            CreacionIdempotenteService.Resultado[] primera = new CreacionIdempotenteService.Resultado[1];

            Future<Object> segunda = tx.execute(status -> {
                // Mismo contenido exacto que enviará la gemela (crear() arma el correo con la matrícula).
                primera[0] = servicio.crear("docente", clave, matricula, "Ada Lovelace",
                        matricula.toLowerCase() + "@u.mx");
                Integer pidTitular = jdbc.queryForObject("select pg_backend_pid()", Integer.class);

                Future<Object> f = pool.submit(crear("docente", clave, matricula, "Ada Lovelace"));
                esperarBloqueoEnPostgres(pidTitular);
                assertThat(f.isDone()).as("la repetición debe esperar a la primera").isFalse();
                return f;
            });   // <- COMMIT: la reserva y el alumno se confirman juntos

            var repetida = (CreacionIdempotenteService.Resultado) segunda.get(10, TimeUnit.SECONDS);
            assertThat(primera[0].repetida()).isFalse();
            assertThat(repetida.repetida()).isTrue();
            assertThat(repetida.recursoId()).isEqualTo(primera[0].recursoId());
            assertThat(repetida.cuerpo()).isEqualTo(primera[0].cuerpo());
            assertThat(alumnosConMatricula(matricula)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------------------
    // 4. Si la creación falla, la reserva se deshace con ella: la clave no queda "quemada".
    // ---------------------------------------------------------------------------------
    @Test
    void unaCreacionFallidaNoDejaLaClaveReservada() throws Exception {
        String s = sufijo();
        String matricula = "IF" + s;
        servicio.crear("docente", "previa-" + s, matricula, "Ada Lovelace", "ada@u.mx");

        String clave = "clave-" + s;
        // Misma matrícula con otra clave: el alumno ya existe -> 409 y rollback completo.
        assertThatThrownBy(crear("docente", clave, matricula, "Ada Lovelace")::call)
                .isInstanceOf(MatriculaDuplicadaException.class);
        assertThat(jdbc.queryForObject(
                "select count(*) from claves_idempotencia where clave = ?", Integer.class, clave)).isZero();
    }

    // ---------------------------------------------------------------------------------
    // 5. La clave pertenece al solicitante: otro usuario puede usar el mismo texto.
    // ---------------------------------------------------------------------------------
    @Test
    void laMismaClaveDeOtroUsuarioEsIndependiente() {
        String s = sufijo();
        String clave = "compartida-" + s;
        var delDocente = servicio.crear("docente", clave, "IU" + s, "Ada Lovelace", "ada@u.mx");
        var delAdmin = servicio.crear("admin", clave, "IV" + s, "Grace Hopper", "grace@u.mx");
        assertThat(delDocente.repetida()).isFalse();
        assertThat(delAdmin.repetida()).isFalse();
        assertThat(delAdmin.recursoId()).isNotEqualTo(delDocente.recursoId());
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
                System.out.println("[EVIDENCIA PG] titular pid=" + pidTitular + " bloquea a: " + filas);
                return;
            }
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("PostgreSQL no mostró ninguna reserva esperando a la primera");
    }
}
