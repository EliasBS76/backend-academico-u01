package mx.edu.backendacademico.exception;

// Reto U10: la cuenta autenticada no está vinculada a ningún alumno (p. ej. docente o admin).
public class ExpedienteNoAsociadoException extends RuntimeException {
    public ExpedienteNoAsociadoException(String usuario) {
        super("La cuenta " + usuario + " no tiene un expediente de alumno asociado");
    }
}
