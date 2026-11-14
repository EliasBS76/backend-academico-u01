-- Reto U07: la regla nueva va en una migración nueva. V1 ya fue aplicada y es parte del historial: no se edita.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
