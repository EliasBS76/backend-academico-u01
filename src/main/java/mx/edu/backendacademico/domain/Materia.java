package mx.edu.backendacademico.domain;

public record Materia(Long id, String clave, String nombre, int creditos) {
    public Materia {
        if (clave == null || clave.isBlank() || nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Clave y nombre son obligatorios");
        }
        if (creditos <= 0) throw new IllegalArgumentException("Los créditos deben ser positivos");
    }
}
