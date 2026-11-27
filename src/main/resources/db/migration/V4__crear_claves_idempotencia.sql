-- Reto U11: claves de idempotencia para la creación de alumnos.
-- Una fila representa "este solicitante ya ejecutó esta operación con esta clave".
-- La fila se inserta y se completa en la MISMA transacción que crea al alumno:
-- si la creación falla, la reserva desaparece con el rollback y la clave queda libre.
CREATE TABLE claves_idempotencia (
    id BIGSERIAL PRIMARY KEY,
    solicitante VARCHAR(100) NOT NULL,       -- usuario autenticado que envió la clave
    operacion VARCHAR(50) NOT NULL,          -- p. ej. CREAR_ALUMNO; permite reutilizar la tabla
    clave VARCHAR(100) NOT NULL,             -- valor de la cabecera Idempotency-Key
    huella CHAR(64) NOT NULL,                -- SHA-256 (hex) del contenido normalizado
    respuesta_estado INTEGER,                -- status HTTP original (201)
    recurso_id BIGINT,                       -- id del alumno creado; sin FK para no impedir su borrado
    respuesta_cuerpo TEXT,                   -- cuerpo JSON original que se repite tal cual
    creada_en TIMESTAMP NOT NULL,
    -- La restricción que hace el trabajo: dos reservas iguales no pueden confirmarse.
    CONSTRAINT uk_idempotencia UNIQUE (solicitante, operacion, clave),
    CONSTRAINT ck_huella_hex CHECK (huella ~ '^[0-9a-f]{64}$')
);
