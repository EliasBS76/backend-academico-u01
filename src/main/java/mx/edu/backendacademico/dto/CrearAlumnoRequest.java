package mx.edu.backendacademico.dto;

// Define exclusivamente los campos que el cliente puede enviar al crear.

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearAlumnoRequest(
    // Reto U06: la matrícula no admite espacios internos. Los espacios en los extremos
    // se toleran en la frontera porque el dominio los normaliza; un texto en blanco
    // lo rechaza @NotBlank, por eso el patrón lo deja pasar y no duplica el error.
    @NotBlank @Size(max = 30)
    @Pattern(regexp = "(?U)^\\s*(?:\\S+\\s*)?$", message = "La matrícula no puede contener espacios internos")
    String matricula,
    @NotBlank @Size(min = 3, max = 120) String nombre,
    @NotBlank @Email @Size(max = 160) String correo
) {}
