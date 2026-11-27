package mx.edu.backendacademico.repository.jpa;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reto U11: acceso a claves_idempotencia con SQL explícito.
 * Se usa JdbcTemplate en lugar de una entidad porque la pieza central es
 * INSERT ... ON CONFLICT DO NOTHING, que JPA no expresa. Comparte la transacción
 * de JPA (mismo DataSource y mismo JpaTransactionManager).
 */
@Repository
@Profile("jpa")
public class ClaveIdempotenciaRepository {
    private final JdbcTemplate jdbc;

    public ClaveIdempotenciaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Intenta reservar la clave. Devuelve true si esta transacción se quedó con ella.
     * Si otra transacción insertó la misma clave y aún no confirma, PostgreSQL hace esperar
     * a este INSERT en el índice único; cuando la otra confirma, aquí no se inserta nada (false)
     * y la transacción sigue sana para leer la fila ganadora.
     */
    public boolean reservar(String solicitante, String operacion, String clave, String huella) {
        int filas = jdbc.update("""
                INSERT INTO claves_idempotencia (solicitante, operacion, clave, huella, creada_en)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (solicitante, operacion, clave) DO NOTHING
                """, solicitante, operacion, clave, huella, Timestamp.valueOf(LocalDateTime.now()));
        return filas == 1;
    }

    public void completar(String solicitante, String operacion, String clave,
                          int estado, Long recursoId, String cuerpo) {
        jdbc.update("""
                UPDATE claves_idempotencia
                SET respuesta_estado = ?, recurso_id = ?, respuesta_cuerpo = ?
                WHERE solicitante = ? AND operacion = ? AND clave = ?
                """, estado, recursoId, cuerpo, solicitante, operacion, clave);
    }

    public Optional<Registro> buscar(String solicitante, String operacion, String clave) {
        return jdbc.query("""
                SELECT huella, respuesta_estado, recurso_id, respuesta_cuerpo
                FROM claves_idempotencia
                WHERE solicitante = ? AND operacion = ? AND clave = ?
                """, (rs, i) -> new Registro(rs.getString(1), rs.getInt(2),
                        rs.getLong(3), rs.getString(4)),
                solicitante, operacion, clave).stream().findFirst();
    }

    public record Registro(String huella, int estado, Long recursoId, String cuerpo) { }
}
