package mx.edu.backendacademico.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SaludoController {
    @GetMapping("/saludo")
    public Map<String, String> saludar(@RequestParam(defaultValue = "Mundo") String nombre) {
        return Map.of(
                "mensaje", "Hola " + nombre,
                "alumno", "Elias Bernal Solis");
    }
}
