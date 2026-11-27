package mx.edu.backendacademico.exception;

// Reto U11: la clave ya se usó con otro contenido; repetirla no puede "convertirse" en otra creación.
public class ContenidoIdempotenteDistintoException extends RuntimeException {
    public ContenidoIdempotenteDistintoException() {
        super("La Idempotency-Key ya se usó con un contenido distinto; usa una clave nueva");
    }
}
