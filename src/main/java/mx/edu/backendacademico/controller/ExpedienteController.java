package mx.edu.backendacademico.controller;

import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.service.ExpedienteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Reto U10: el controlador extrae la identidad del principal; no acepta un usuario enviado por el cliente.
@RestController
@RequestMapping("/api/v1")
public class ExpedienteController {
    private final ExpedienteService service;

    public ExpedienteController(ExpedienteService service) { this.service = service; }

    @GetMapping("/expediente")
    public AlumnoResponse miExpediente(Authentication auth) {
        return AlumnoMapper.toResponse(service.miExpediente(auth.getName()));
    }

    @GetMapping("/expedientes/{alumnoId}")
    public AlumnoResponse consultar(@PathVariable Long alumnoId, Authentication auth) {
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return AlumnoMapper.toResponse(service.consultar(alumnoId, auth.getName(), esAdmin));
    }
}
