package mx.edu.backendacademico.exception;

// Reto U11: la cabecera llegó vacía, demasiado larga o con caracteres no visibles.
public class ClaveIdempotenciaInvalidaException extends RuntimeException {
    public ClaveIdempotenciaInvalidaException() {
        super("La Idempotency-Key debe tener entre 1 y 100 caracteres ASCII visibles");
    }
}
