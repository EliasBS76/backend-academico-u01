package mx.edu.backendacademico.exception;

// Reto U10: la identidad es válida, pero no tiene permiso sobre ESTE recurso concreto.
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException() {
        super("No tienes permiso para consultar este expediente");
    }
}
