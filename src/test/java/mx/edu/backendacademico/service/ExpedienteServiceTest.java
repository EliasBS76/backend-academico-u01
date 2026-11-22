package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumno;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.AccesoDenegadoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.Test;

// Reto U10: la regla por objeto aislada, sin Spring ni filtros.
class ExpedienteServiceTest {
    private final AlumnoRepository repository = mock(AlumnoRepository.class);
    private final ExpedienteService servicio = new ExpedienteService(repository, new CuentasAlumno());
    private final Alumno ada = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);

    @Test void propietarioPuedeLeer() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada));
        assertEquals("A001", servicio.consultar(1L, "alumno", false).matricula());
    }

    @Test void ajenoEsRechazado() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada));
        assertThrows(AccesoDenegadoException.class, () -> servicio.consultar(1L, "alumno2", false));
    }

    @Test void administradorPuedeLeerSinSerPropietario() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada));
        assertEquals("A001", servicio.consultar(1L, "admin", true).matricula());
    }
}
