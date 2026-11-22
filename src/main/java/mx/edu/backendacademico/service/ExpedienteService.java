package mx.edu.backendacademico.service;

import mx.edu.backendacademico.config.CuentasAlumno;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.AccesoDenegadoException;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.exception.ExpedienteNoAsociadoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reto U10 — regla de acceso por objeto:
 *   permitir(principal, LEER, expediente) = principal es ADMIN
 *                                           o matriculaDe(principal) == expediente.matricula
 * - La identidad llega SIEMPRE del principal autenticado; nunca de un parámetro del cliente.
 * - Para quien no es ADMIN, "no existe" y "no es tuyo" responden igual (403), para no revelar
 *   qué ids existen. Sólo ADMIN distingue 404.
 * - DOCENTE no es propietario de expedientes: usa el catálogo /alumnos, no esta operación.
 */
@Service
@Transactional(readOnly = true)
public class ExpedienteService {
    private final AlumnoRepository repository;
    private final CuentasAlumno cuentas;

    public ExpedienteService(AlumnoRepository repository, CuentasAlumno cuentas) {
        this.repository = repository;
        this.cuentas = cuentas;
    }

    public Alumno miExpediente(String usuario) {
        // El propietario es, por definición, el usuario autenticado.
        return cuentas.matriculaDe(usuario)
                .flatMap(repository::buscarPorMatricula)
                .orElseThrow(() -> new ExpedienteNoAsociadoException(usuario));
    }

    public Alumno consultar(Long alumnoId, String usuario, boolean esAdmin) {
        if (esAdmin) {
            return repository.buscarPorId(alumnoId).orElseThrow(() -> new AlumnoNoEncontradoException(alumnoId));
        }
        var propia = cuentas.matriculaDe(usuario).orElseThrow(AccesoDenegadoException::new);
        return repository.buscarPorId(alumnoId)
                .filter(alumno -> alumno.matricula().equalsIgnoreCase(propia))
                .orElseThrow(AccesoDenegadoException::new);
    }
}
