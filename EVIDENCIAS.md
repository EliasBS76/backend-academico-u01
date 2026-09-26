# Evidencias de ejecución — Unidad 1

Salida real capturada en el equipo el 25 de septiembre de 2026.
Entorno: Windows 11, Temurin JDK 25.0.4.1, Maven Wrapper 3.9.11, Spring Boot 4.1.0.

---

## 1. Versiones del entorno

```
$ java -version
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

```
$ .\mvnw.cmd --version
Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
Maven home: C:\Users\elias\.m2\wrapper\dists\apache-maven-3.9.11\...
Java version: 25.0.4.1, vendor: Eclipse Adoptium,
  runtime: C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot
Default locale: es_ES, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

Las dos salidas identifican Java 25, como pide la preparación del equipo.

---

## 2. El bytecode apunta a Java 21

```
$ javap -verbose -cp target\classes mx.edu.backendacademico.BackendAcademicoApplication
  minor version: 0
  major version: 65
```

`major version: 65` corresponde a Java 21. Confirma que `maven.compiler.release=21`
hizo su trabajo: la herramienta es JDK 25, el destino de compilación es Java 21.

---

## 3. Pruebas

```
$ .\mvnw.cmd -B test

[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running mx.edu.backendacademico.BackendAcademicoApplicationTests

 :: Spring Boot ::                (v4.1.0)

INFO m.e.b.BackendAcademicoApplicationTests : Starting BackendAcademicoApplicationTests
                                              using Java 25.0.4.1 with PID 2212
INFO m.e.b.BackendAcademicoApplicationTests : No active profile set, falling back to
                                              1 default profile: "default"
INFO m.e.b.BackendAcademicoApplicationTests : Started BackendAcademicoApplicationTests
                                              in 2.344 seconds (process running for 4.686)

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  27.598 s
```

`contextLoads()` en verde: el contexto de Spring se ensambla sin errores.

---

## 4. Arranque en el puerto base 8080

```
$ .\mvnw.cmd -B spring-boot:run

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.0)

INFO m.e.b.BackendAcademicoApplication   : Starting BackendAcademicoApplication
                                           using Java 25.0.4.1 with PID 15872
INFO o.s.boot.tomcat.TomcatWebServer     : Tomcat initialized with port 8080 (http)
INFO o.apache.catalina.core.StandardEngine : Starting Servlet engine: [Apache Tomcat/11.0.22]
INFO o.s.boot.tomcat.TomcatWebServer     : Tomcat started on port 8080 (http)
                                           with context path '/'
INFO m.e.b.BackendAcademicoApplication   : Started BackendAcademicoApplication
                                           in 2.306 seconds (process running for 3.164)
```

Puerto en escucha durante la ejecución:

```
LocalPort  State   OwningProcess
---------  -----   -------------
     8080  Listen  15872
```

Respuesta del servidor:

```
$ curl http://localhost:8080/
HTTP 404
Content-Type: application/json

{"timestamp":"2026-09-25T23:54:35.233Z","status":404,"error":"Not Found","path":"/"}
```

El 404 es el resultado esperado: la Unidad 1 no define ninguna ruta todavía.
Aun así demuestra que la cadena completa funciona — Tomcat aceptó la conexión, el
`DispatcherServlet` procesó la petición, no encontró un handler y devolvió un error
bien formado. Un fallo de arranque habría dado `connection refused`, no un JSON.

---

## 5. Reto: arranque en 8081 por configuración externa

### Opción A — argumento de línea de comandos

```
$ .\mvnw.cmd -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat initialized with port 8081 (http)
INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http)
                                       with context path '/'
INFO m.e.b.BackendAcademicoApplication : Started BackendAcademicoApplication
                                         in 2.285 seconds (process running for 2.765)
```

```
LocalPort  State   OwningProcess
---------  -----   -------------
     8081  Listen  12620

8080: libre  (no se usó el valor de application.properties)
```

### Opción B — variable de entorno

```
$ $env:SERVER_PORT = "8081"
$ .\mvnw.cmd -B spring-boot:run

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http)
                                       with context path '/'
```

Ninguna de las dos vías modificó un archivo `.java`.

### Comprobación de que no hubo recompilación

SHA256 de `target\classes\mx\edu\backendacademico\BackendAcademicoApplication.class`:

```
ANTES   (tras ejecutar en 8080): 5711D44B8D009F4EDF79FD8A1DB3AF3632654B9D0B3AC516D50AAE547593C671
DESPUÉS (tras ejecutar en 8081): 5711D44B8D009F4EDF79FD8A1DB3AF3632654B9D0B3AC516D50AAE547593C671
```

Hash idéntico: el mismo bytecode, byte por byte, sirvió en 8080 y en 8081.
Lo que cambió fue la configuración de arranque, no el programa.

---

## 6. Control de versiones

```
$ git log --oneline --all --decorate
8d109f4 (reto/u01-puerto-8081) Reto U01: reescribe la explicacion en lenguaje sencillo
3d64b13 Reto U01: ejecutar en 8081 mediante configuracion externa
9dc3c47 (main) U01: primer arranque de la aplicacion Spring Boot
```

```
$ git ls-files
.gitignore
.mvn/wrapper/maven-wrapper.properties
mvnw
mvnw.cmd
pom.xml
src/main/java/mx/edu/backendacademico/BackendAcademicoApplication.java
src/main/resources/application.properties
src/test/java/mx/edu/backendacademico/BackendAcademicoApplicationTests.java
```

`target/` no aparece: queda excluido por `.gitignore`, junto con `.idea/`, `*.iml` y `.env`.

- **`main`** — la solución de la construcción guiada, con `server.port=8080`.
  Es el punto de partida de la Unidad 2.
- **`reto/u01-puerto-8081`** — el reto y su explicación, en rama aparte.
