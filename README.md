# Backend Académico — Unidad 1

Entrega de la Unidad 1: entorno reproducible y primer arranque.

Aplicación Spring Boot que levanta un servidor web embebido en el puerto 8080.
Compilada con **JDK 25** y destino **Java 21**, sobre **Spring Boot 4.1.0**.

---

## Contenido de la entrega

| Lo solicitado | Dónde está |
|---|---|
| **Código y solución** | Este repositorio, rama `main` |
| **Evidencia de ejecución** | [`EVIDENCIAS.md`](EVIDENCIAS.md) |
| **Reto** | [`RETO.md`](../../blob/reto/u01-puerto-8081/RETO.md) — en la rama `reto/u01-puerto-8081` |

> **El reto está en una rama aparte**, como pide el manual de la unidad.
> Para verlo, cambia a la rama `reto/u01-puerto-8081` con el selector de ramas,
> o abre el enlace directo de la tabla.

---

## Estructura

```
pom.xml                                      Maven: padre Spring Boot 4.1.0, release 21
.gitignore                                   excluye target/, .idea/, *.iml, .env
mvnw, mvnw.cmd, .mvn/                        Maven Wrapper (fija la versión de Maven)
src/main/java/mx/edu/backendacademico/
    BackendAcademicoApplication.java         punto de entrada, paquete raíz
src/main/resources/
    application.properties                   nombre de la aplicación y puerto
src/test/java/mx/edu/backendacademico/
    BackendAcademicoApplicationTests.java    contextLoads(): comprueba el ensamblado
EVIDENCIAS.md                                salida real de ejecución
```

La clase principal vive en `mx.edu.backendacademico`, por encima de los paquetes
`controller`, `service` y `repository` que se crearán en unidades siguientes, para
que el escaneo de componentes los alcance.

---

## Cómo ejecutarlo

Requiere JDK 25 instalado y `JAVA_HOME` apuntando a él. Maven no hace falta
instalarlo: lo descarga el Wrapper.

```
.\mvnw.cmd test              ejecuta las pruebas
.\mvnw.cmd spring-boot:run   levanta el servidor en http://localhost:8080
```

Se detiene con Ctrl+C.

**Nota:** abrir `http://localhost:8080/` devuelve **404**, y es el resultado
esperado. La Unidad 1 no define ninguna ruta todavía; los controladores llegan en
la Unidad 2. Aun así el 404 llega como un JSON generado por Spring, lo que
demuestra que la cadena completa está ensamblada:

```json
{"timestamp":"2026-09-25T23:54:35.233Z","status":404,"error":"Not Found","path":"/"}
```

Un fallo de arranque habría dado `connection refused`, no una respuesta con formato.

---

## Resultados obtenidos

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

Tomcat started on port 8080 (http) with context path '/'
Started BackendAcademicoApplication in 2.306 seconds
```

El detalle completo —versiones del entorno, verificación de que el bytecode es
Java 21, arranque en 8080 y en 8081, y estado del repositorio— está en
[`EVIDENCIAS.md`](EVIDENCIAS.md).

---

## Ramas

- **`main`** — la solución de la construcción guiada, con `server.port=8080`.
  Es el punto de partida de la Unidad 2.
- **`reto/u01-puerto-8081`** — el reto: ejecutar en el puerto 8081 mediante
  configuración externa, sin modificar la clase principal.
