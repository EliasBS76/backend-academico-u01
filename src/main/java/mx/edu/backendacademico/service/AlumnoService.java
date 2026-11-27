package mx.edu.backendacademico.service;

// Coordina los casos de uso y conserva las reglas fuera del transporte HTTP.
import java.util.List;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlumnoService {
    private final AlumnoRepository repository;

    public AlumnoService(AlumnoRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "alumnosLista")
    public List<Alumno> listar() {
        // Devuelve una instantánea de los alumnos desde el adaptador activo.
        return repository.buscarTodos();
    }

    @Cacheable(cacheNames = "alumnos", key = "#id")
    public Alumno buscar(Long id) {
        // La ausencia se interpreta como un error de aplicación explícito.
        return repository.buscarPorId(id).orElseThrow(() -> new AlumnoNoEncontradoException(id));
    }

    // Reto U12: crear sólo invalida la lista. Un id nuevo no puede estar en "alumnos"
    // porque una búsqueda fallida (404) lanza excepción y no se guarda en la caché.
    @CacheEvict(cacheNames = "alumnosLista", allEntries = true)
    @Transactional
    public Alumno crear(String matricula, String nombre, String correo) {
        // Esta comprobación mejora el mensaje; el almacén vuelve a proteger unicidad.
        if (repository.existePorMatricula(matricula)) {
            throw new MatriculaDuplicadaException(matricula);
        }
        return repository.guardar(new Alumno(null, matricula, nombre, correo, EstatusAlumno.ACTIVO));
    }

    // Reto U12: invalida sólo la entrada del alumno modificado y la lista, no la caché completa.
    @Caching(evict = {
        @CacheEvict(cacheNames = "alumnos", key = "#id"),
        @CacheEvict(cacheNames = "alumnosLista", allEntries = true)})
    @Transactional
    public Alumno actualizar(Long id, String nombre, String correo) {
        // Recupera el estado actual y conserva identidad, matrícula y estatus.
        var actual = buscar(id);
        return repository.guardar(new Alumno(id, actual.matricula(), nombre, correo, actual.estatus()));
    }

    // Reto U12: invalida sólo la entrada del alumno modificado y la lista, no la caché completa.
    @Caching(evict = {
        @CacheEvict(cacheNames = "alumnos", key = "#id"),
        @CacheEvict(cacheNames = "alumnosLista", allEntries = true)})
    @Transactional
    public void eliminar(Long id) {
        // Si no existe, comunica ausencia antes de solicitar la eliminación.
        buscar(id);
        repository.eliminarPorId(id);
    }
}
