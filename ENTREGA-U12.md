# Entrega final (Unidad 12)

Esto explica cómo levantar el sistema desde cero, sin IDE, y cómo repetir cada observación del reto.
Todos los comandos se ejecutan en PowerShell desde la raíz del proyecto.

## 1. Lo que necesitas

- **JDK 25** instalado y `JAVA_HOME` apuntando a él. El proyecto compila con `maven.compiler.release=21`,
  así que el bytecode y la API usada son de Java 21 aunque el compilador sea el 25. Maven no se instala: lo
  descarga el wrapper (`mvnw.cmd`).
- **Docker Desktop** encendido. Las pruebas de integración usan Testcontainers y la aplicación corre con Compose.
- Nada más. No hace falta una base PostgreSQL instalada: la imagen `postgres:17-alpine` la pone Compose.

Comprobación rápida:

```powershell
java -version            # debe decir 25
docker info --format "{{.ServerVersion}}"
```

## 2. Obtener el código en una carpeta limpia

```powershell
git clone https://github.com/EliasBS76/backend-academico-u01.git backend-academico
cd backend-academico
```

## 3. Construcción y pruebas

```powershell
.\mvnw.cmd clean test package
```

Corre las 63 pruebas (unitarias, MVC con filtros de seguridad reales y de integración con PostgreSQL en
Testcontainers) y deja `target\backend-academico-0.0.12-SNAPSHOT.jar`. Se puede comprobar que el JAR es Java 21
con `javap -v -cp target\classes mx.edu.backendacademico.BackendAcademicoApplication | Select-String major`
(major version 65 = Java 21).

## 4. Inicio

```powershell
docker compose build
docker compose up -d
docker compose ps
docker compose logs app
```

La imagen ejecuta el JAR sobre `eclipse-temurin:25-jre`. La app espera a que PostgreSQL esté saludable,
Flyway aplica las migraciones V1 a V4 y queda escuchando en `http://localhost:8080`.

Si el 8080 ya está ocupado en tu equipo, publica otro puerto:

```powershell
$env:APP_PORT = "8081"; docker compose up -d
```

Para ver cada `SELECT` (útil para observar la caché) arranca con `$env:SQL_LOG = "DEBUG"` antes de `up -d`.

## 5. Peticiones y evidencia

El script hace todas las peticiones con `curl.exe` (viene con Windows 10/11) y compara cada status con el esperado:

```powershell
.\evidencia\evidencia-u12.ps1                      # agrega -Base http://localhost:8081 si cambiaste el puerto
```

Cubre: éxito 201/200, validación 400, ausencia 404, conflicto 409, rechazos de seguridad (401 sin credenciales,
401 con contraseña incorrecta, 403 por rol y 403 por falta de CSRF), la regla por propietario del historial,
salud, liveness, readiness, métricas restringidas a ADMIN, el contador de la tarea programada y la caché.

Las mismas peticiones, una por una, están en `requests.http`.

Falla controlada de PostgreSQL:

```powershell
.\evidencia\evidencia-u12.ps1 -Falla
```

Persistencia después de reiniciar la aplicación:

```powershell
.\evidencia\evidencia-u12.ps1 -Persistencia
```

La tarea programada empieza un minuto después del arranque. Para verla:

```powershell
docker compose logs app | Select-String "Total de inscripciones"
```

## 6. Cierre

```powershell
docker compose down        # conserva el volumen datos_academicos
```

`docker compose down -v` borraría también los datos. El volumen no es un respaldo: para eso habría que sacar
una copia (por ejemplo con `pg_dump`) y probar que se puede restaurar.

## 7. Qué cambió en el reto

- **Mejora por propietario:** `GET /api/v1/inscripciones/alumno/{id}` ahora lo puede usar un ALUMNO, pero sólo
  para su propio historial. Uno ajeno o inexistente responde 403 igual, para no revelar qué ids existen.
  DOCENTE y ADMIN siguen viendo cualquiera. Pruebas en `HistorialPropietarioTest`.
- **Caché:** se mantiene para el catálogo común de alumnos, pero la invalidación es por entrada: actualizar o
  borrar un alumno expulsa sólo ese id y la lista; crear expulsa sólo la lista. Las operaciones se difieren al
  commit. Pruebas en `AlumnoCacheRetoTest` (contra PostgreSQL real).
- **Falla de base:** `spring.datasource.hikari.connection-timeout=3000` para que, con PostgreSQL caído, readiness
  y la API respondan en unos segundos en lugar de esperar 30.
- `InscripcionConcurrenciaRetoTest` usa Testcontainers, porque Compose ya no publica PostgreSQL en el anfitrión.
