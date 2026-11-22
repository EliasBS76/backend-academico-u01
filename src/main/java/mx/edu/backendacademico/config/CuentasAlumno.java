package mx.edu.backendacademico.config;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reto U10: vínculo entre una cuenta autenticada y la matrícula del alumno que representa.
 * Es un directorio de laboratorio fijo, igual que las cuentas en memoria de SecurityConfig;
 * en un sistema real vendría del directorio institucional o de un claim del emisor.
 */
@Component
public class CuentasAlumno {
    private final Map<String, String> matriculaPorUsuario = Map.of(
            "alumno", "A001",
            "alumno2", "A002");

    public Optional<String> matriculaDe(String usuario) {
        return Optional.ofNullable(matriculaPorUsuario.get(usuario));
    }
}
