package mx.edu.backendacademico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Reto U12: la decisión de caché, comprobada contra PostgreSQL real.
 * El repositorio real queda envuelto en un spy para contar cuántas veces se llega a la base.
 */
@SpringBootTest
@Testcontainers
class AlumnoCacheRetoTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AlumnoService servicio;
    @Autowired CacheManager caches;
    @Autowired PlatformTransactionManager tm;
    @MockitoSpyBean AlumnoRepository repository;

    private Long a;
    private Long b;

    @BeforeEach void preparar() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        a = servicio.crear("CA" + s, "Ada Lovelace", "ada@u.mx").id();
        b = servicio.crear("CB" + s, "Grace Hopper", "grace@u.mx").id();
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
        clearInvocations(repository);
    }

    @Test void laSegundaConsultaNoLlegaALaBase() {
        servicio.buscar(a);
        servicio.buscar(a);
        verify(repository, times(1)).buscarPorId(a);
    }

    @Test void actualizarUnAlumnoSoloInvalidaSuEntrada() {
        servicio.buscar(a);
        servicio.buscar(b);
        servicio.actualizar(a, "Ada Byron", "ada.byron@u.mx");
        clearInvocations(repository);

        // El modificado vuelve a la base y trae el valor nuevo...
        assertThat(servicio.buscar(a).nombre()).isEqualTo("Ada Byron");
        verify(repository, times(1)).buscarPorId(a);
        // ...y el otro sigue en caché: con allEntries=true de la guía también se habría expulsado.
        servicio.buscar(b);
        verify(repository, times(0)).buscarPorId(b);
    }

    @Test void unRollbackNoDejaLaCacheConUnValorQueNuncaSeConfirmo() {
        servicio.buscar(a);   // en caché: "Ada Lovelace"
        var tx = new TransactionTemplate(tm);
        tx.executeWithoutResult(status -> {
            servicio.actualizar(a, "Nombre que se revierte", "x@u.mx");
            status.setRollbackOnly();
        });
        // La invalidación se difería al commit; sin commit, caché y base siguen coincidiendo.
        assertThat(servicio.buscar(a).nombre()).isEqualTo("Ada Lovelace");
        assertThat(repository.buscarPorId(a).orElseThrow().nombre()).isEqualTo("Ada Lovelace");
    }

    @Test void crearInvalidaLaListaPeroNoLasEntradasIndividuales() {
        servicio.buscar(a);
        int antes = servicio.listar().size();
        servicio.crear("CN" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "Emmy Noether", "emmy@u.mx");
        clearInvocations(repository);
        assertThat(servicio.listar()).hasSize(antes + 1);
        verify(repository, times(1)).buscarTodos();
        servicio.buscar(a);
        verify(repository, times(0)).buscarPorId(a);
    }
}
