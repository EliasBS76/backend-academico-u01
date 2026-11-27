package mx.edu.backendacademico.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import mx.edu.backendacademico.dto.CrearAlumnoRequest;
import mx.edu.backendacademico.exception.ClaveIdempotenciaInvalidaException;
import mx.edu.backendacademico.service.CreacionIdempotenteService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reto U11: POST /api/v1/alumnos CON cabecera Idempotency-Key.
 * La condición headers hace que Spring elija este método sólo cuando la cabecera viene;
 * sin ella responde AlumnoController igual que en U10, así los clientes actuales no cambian.
 */
@RestController
@Profile("jpa")
@RequestMapping("/api/v1/alumnos")
public class AlumnoIdempotenteController {
    private static final String CABECERA = "Idempotency-Key";
    private final CreacionIdempotenteService service;

    public AlumnoIdempotenteController(CreacionIdempotenteService service) {
        this.service = service;
    }

    @PostMapping(headers = CABECERA)
    public ResponseEntity<String> crear(@RequestHeader(CABECERA) String clave,
                                        @RequestBody @Valid CrearAlumnoRequest request,
                                        Principal principal) {
        // ASCII visible y longitud acotada: la clave se guarda y se compara tal cual.
        if (!clave.matches("[\\x21-\\x7E]{1,100}")) throw new ClaveIdempotenciaInvalidaException();

        // El solicitante sale de la credencial autenticada, nunca del cuerpo.
        var r = service.crear(principal.getName(), clave,
                request.matricula(), request.nombre(), request.correo());

        var respuesta = ResponseEntity.status(r.estado())
                .location(URI.create("/api/v1/alumnos/" + r.recursoId()))
                .contentType(MediaType.APPLICATION_JSON);
        if (r.repetida()) respuesta.header("Idempotent-Replayed", "true");
        // Cuerpo guardado tal cual: la repetición es idéntica a la respuesta original.
        return respuesta.body(r.cuerpo());
    }
}
