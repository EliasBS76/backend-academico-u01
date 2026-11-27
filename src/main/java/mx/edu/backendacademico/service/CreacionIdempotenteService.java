package mx.edu.backendacademico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.exception.ContenidoIdempotenteDistintoException;
import mx.edu.backendacademico.repository.jpa.ClaveIdempotenciaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reto U11: creación de alumnos con Idempotency-Key.
 *
 * Todo ocurre en UNA transacción: reservar la clave, crear el alumno y guardar la respuesta.
 * Así no existe un estado "reservada pero sin efecto" visible para otros: o se confirman las
 * tres cosas juntas o el rollback se las lleva juntas.
 */
@Service
@Profile("jpa")
public class CreacionIdempotenteService {
    static final String OPERACION = "CREAR_ALUMNO";

    private final ClaveIdempotenciaRepository claves;
    private final AlumnoService alumnos;
    private final JsonMapper json;

    public CreacionIdempotenteService(ClaveIdempotenciaRepository claves, AlumnoService alumnos, JsonMapper json) {
        this.claves = claves;
        this.alumnos = alumnos;
        this.json = json;
    }

    @Transactional
    public Resultado crear(String solicitante, String clave, String matricula, String nombre, String correo) {
        String huella = huella(matricula, nombre, correo);

        // 1. Reserva primero. Una solicitud gemela espera aquí hasta que esta confirme o revierta.
        if (!claves.reservar(solicitante, OPERACION, clave, huella)) {
            // 2a. La clave ya tenía dueño confirmado: se repite su respuesta o se rechaza.
            var previa = claves.buscar(solicitante, OPERACION, clave).orElseThrow(() ->
                    new IllegalStateException("La reserva existente no es visible"));
            if (!previa.huella().equals(huella)) throw new ContenidoIdempotenteDistintoException();
            return new Resultado(previa.estado(), previa.recursoId(), previa.cuerpo(), true);
        }

        // 2b. Esta transacción es la dueña: ejecuta el efecto real. Si falla (p. ej. matrícula
        // duplicada) la excepción revierte también la reserva y la clave queda libre.
        var creado = alumnos.crear(matricula, nombre, correo);
        String cuerpo = json.writeValueAsString(AlumnoMapper.toResponse(creado));

        // 3. Guarda la respuesta original para repetirla sin volver a ejecutar nada.
        claves.completar(solicitante, OPERACION, clave, 201, creado.id(), cuerpo);
        return new Resultado(201, creado.id(), cuerpo, false);
    }

    /**
     * Huella del contenido NORMALIZADO, igual que lo normaliza el dominio: " a001 " y "A001"
     * son la misma solicitud. Cada campo va precedido de su longitud para que no haya dos
     * combinaciones distintas que produzcan el mismo texto concatenado.
     */
    static String huella(String matricula, String nombre, String correo) {
        String canonico = campo(matricula.strip().toUpperCase(Locale.ROOT))
                + campo(nombre.strip()) + campo(correo.strip());
        try {
            var sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String campo(String valor) {
        return valor.length() + ":" + valor + ";";
    }

    /** Respuesta que el controlador publica; repetida indica que viene de una creación anterior. */
    public record Resultado(int estado, Long recursoId, String cuerpo, boolean repetida) { }
}
