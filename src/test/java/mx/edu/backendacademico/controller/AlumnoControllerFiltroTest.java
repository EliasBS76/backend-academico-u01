package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;

// Reto U05: filtro opcional de estatus en el GET de coleccion.
// Se prueba el controlador como objeto Java ordinario (sin servidor), con un repositorio real en memoria.
class AlumnoControllerFiltroTest {

    private final AlumnoRepositoryEnMemoria repositorio = new AlumnoRepositoryEnMemoria();
    private final AlumnoController controlador = new AlumnoController(new AlumnoService(repositorio));

    AlumnoControllerFiltroTest() {
        // Dos alumnos ACTIVO y uno BAJA. La API aun no tiene operacion de baja,
        // por eso el alumno BAJA se siembra directamente en el repositorio.
        repositorio.guardar(new Alumno(null, "A001", "Ada Lovelace", "ada@universidad.mx", EstatusAlumno.ACTIVO));
        repositorio.guardar(new Alumno(null, "A002", "Grace Hopper", "grace@universidad.mx", EstatusAlumno.ACTIVO));
        repositorio.guardar(new Alumno(null, "B001", "Alan Turing", "alan@universidad.mx", EstatusAlumno.BAJA));
    }

    private static List<String> matriculas(List<AlumnoResponse> respuesta) {
        return respuesta.stream().map(AlumnoResponse::matricula).toList();
    }

    @Test
    void sinFiltroDevuelveTodosLosAlumnos() {
        assertEquals(List.of("A001", "A002", "B001"), matriculas(controlador.listar(null)));
    }

    @Test
    void filtroActivoDevuelveSoloActivos() {
        var respuesta = controlador.listar(EstatusAlumno.ACTIVO);
        assertEquals(List.of("A001", "A002"), matriculas(respuesta));
        assertTrue(respuesta.stream().allMatch(a -> a.estatus() == EstatusAlumno.ACTIVO));
    }

    @Test
    void filtroBajaDevuelveSoloBaja() {
        var respuesta = controlador.listar(EstatusAlumno.BAJA);
        assertEquals(List.of("B001"), matriculas(respuesta));
        assertTrue(respuesta.stream().allMatch(a -> a.estatus() == EstatusAlumno.BAJA));
    }

    @Test
    void filtrarSinCoincidenciasDevuelveListaVaciaNoError() {
        var soloActivos = new AlumnoRepositoryEnMemoria();
        soloActivos.guardar(new Alumno(null, "A001", "Ada Lovelace", "ada@universidad.mx", EstatusAlumno.ACTIVO));
        var otro = new AlumnoController(new AlumnoService(soloActivos));
        assertEquals(List.of(), otro.listar(EstatusAlumno.BAJA));
    }

    @Test
    void consultarNoModificaElRepositorio() {
        List<Alumno> antes = repositorio.buscarTodos();

        controlador.listar(null);
        controlador.listar(EstatusAlumno.ACTIVO);
        controlador.listar(EstatusAlumno.BAJA);

        List<Alumno> despues = repositorio.buscarTodos();
        assertEquals(3, despues.size());
        assertEquals(antes, despues);  // mismos registros, mismo orden, mismos valores
    }
}
