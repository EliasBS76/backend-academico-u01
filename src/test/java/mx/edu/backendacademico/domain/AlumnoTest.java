package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
                new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }

    @Test void reactivarRestauraActivoSinModificarElOriginal() {
        var baja = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.BAJA);
        var reactivado = baja.reactivar();
        assertEquals(EstatusAlumno.ACTIVO, reactivado.estatus());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(baja.id(), reactivado.id());
        assertEquals(baja.matricula(), reactivado.matricula());
        assertEquals(baja.nombre(), reactivado.nombre());
        assertEquals(baja.correo(), reactivado.correo());
    }

    @Test void mismoIdConDistintoCorreoNoSonIguales() {
        var anterior = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var actualizado = new Alumno(1L, "A001", "Ada", "ada@universidad.mx", EstatusAlumno.ACTIVO);
        assertNotEquals(anterior, actualizado);
        assertEquals(anterior.id(), actualizado.id());
        assertEquals(anterior.matricula(), actualizado.matricula());
    }
}
