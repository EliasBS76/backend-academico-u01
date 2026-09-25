# Reto o tarea final — Unidad 1

**Objetivo:** ejecutar la aplicación en el puerto **8081** mediante configuración
externa, **sin modificar** `BackendAcademicoApplication.java`.

Todo lo que sigue es salida real capturada en este equipo
(Windows 11, JDK 25 Temurin, Maven Wrapper 3.9.11, Spring Boot 4.1.0).

---

## 1. Versiones del entorno

```
$ java -version
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)

$ .\mvnw.cmd --version
Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
Maven home: C:\Users\elias\.m2\wrapper\dists\apache-maven-3.9.11\...
Java version: 25.0.4.1, vendor: Eclipse Adoptium,
  runtime: C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot
Default locale: es_ES, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

El JDK que ejecuta la herramienta es el 25; el destino de compilación es Java 21.
Comprobación directa sobre el bytecode generado:

```
$ javap -verbose -cp target\classes mx.edu.backendacademico.BackendAcademicoApplication
  minor version: 0
  major version: 65        <-- 65 = Java 21
```

Es decir: **JDK 25 como herramienta, Java 21 como plataforma objetivo**, tal como
fija `maven.compiler.release` en el `pom.xml`. Son dos decisiones distintas.

---

## 2. Resultado de las pruebas

```
$ .\mvnw.cmd -B test

[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running mx.edu.backendacademico.BackendAcademicoApplicationTests
 :: Spring Boot ::                (v4.1.0)
INFO m.e.b.BackendAcademicoApplicationTests : Starting BackendAcademicoApplicationTests using Java 25.0.4.1
INFO m.e.b.BackendAcademicoApplicationTests : Started BackendAcademicoApplicationTests in 2.344 seconds
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  27.598 s
```

`contextLoads()` en verde: el contexto de Spring se ensambla sin errores.

---

## 3. Arranque en el puerto base 8080

Con `server.port=8080` de `src/main/resources/application.properties`:

```
$ .\mvnw.cmd -B spring-boot:run

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat initialized with port 8080 (http)
INFO o.apache.catalina.core.StandardEngine : Starting Servlet engine: [Apache Tomcat/11.0.22]
INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
INFO m.e.b.BackendAcademicoApplication : Started BackendAcademicoApplication in 2.306 seconds

$ curl http://localhost:8080/
404
```

El 404 es el resultado esperado: la Unidad 1 todavía no define ninguna ruta.
Que el servidor esté iniciado y que la aplicación haga algo útil son evidencias
distintas — aquí solo se demuestra lo primero.

---

## 4. El reto: puerto 8081 por configuración externa

Se verificaron las dos vías. En **ninguna** se tocó un archivo `.java`.

### Opción A — argumento de línea de comandos

```
$ .\mvnw.cmd -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat initialized with port 8081 (http)
INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http) with context path '/'
INFO m.e.b.BackendAcademicoApplication : Started BackendAcademicoApplication in 2.285 seconds
```

Estado de los puertos durante esa ejecución:

```
LocalPort  State   OwningProcess
---------  -----   -------------
     8081  Listen  12620

8080: libre  (no se usó el valor de application.properties)
```

Con el JAR empaquetado la forma equivalente es:

```
$ .\mvnw.cmd clean package
$ java -jar target\backend-academico-0.0.1-SNAPSHOT.jar --server.port=8081
```

### Opción B — variable de entorno

Spring Boot relaja los nombres de propiedad, así que `SERVER_PORT` se resuelve a
`server.port`:

```
$ $env:SERVER_PORT = "8081"      # PowerShell
$ .\mvnw.cmd -B spring-boot:run

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http) with context path '/'
```

En CMD sería `set SERVER_PORT=8081`; en bash/zsh, `export SERVER_PORT=8081`.

Ambas vías sobrescriben el `server.port=8080` del archivo **solo durante esa
ejecución**; el archivo no se modifica.

---

## 5. Por qué cambiar el puerto no exige recompilar la lógica Java

`server.port` es un **dato de entrada al arranque**, no una constante del programa.

`SpringApplication.run(...)` construye el `ApplicationContext` y, como parte de ese
arranque, Spring Boot consulta las fuentes de propiedades en un orden de
precedencia definido:

```
argumentos de línea de comandos  >  variables de entorno  >  application.properties  >  valores por defecto
```

El servidor embebido (Tomcat, que llega por `spring-boot-starter-webmvc`) se
configura a partir de esa propiedad **ya resuelta, en tiempo de ejecución**.
`BackendAcademicoApplication` solo delega el ensamblado a Spring: no contiene
ningún número de puerto, así que no hay nada que recompilar cuando el puerto
cambia.

Esto no es solo un argumento teórico; se comprobó con el hash del bytecode antes y
después de arrancar en 8081:

```
SHA256 de target\classes\...\BackendAcademicoApplication.class

ANTES   (tras la ejecución en 8080): 6E9C6C0618F6C53168519DCC9314E92419F993E592923966E13568EDD1C3F8C2
DESPUÉS (tras la ejecución en 8081): 6E9C6C0618F6C53168519DCC9314E92419F993E592923966E13568EDD1C3F8C2
```

Hash idéntico: **el mismo bytecode, byte por byte, sirvió en 8080 y en 8081**.
Lo que cambió fue la configuración, no el programa.

Corolario práctico: el mismo artefacto compilado puede desplegarse en desarrollo,
pruebas y producción con puertos, credenciales y URLs distintas, sin reconstruirlo.

---

## 6. Vuelta a la configuración base antes de U02

`src/main/resources/application.properties` conserva en `main` la configuración
base, intacta:

```properties
spring.application.name=backend-academico
server.port=8080
```

El trabajo del reto queda en la rama `reto/u01-puerto-8081`; la rama `main` guarda
la solución de la construcción guiada, que es el punto de partida de la Unidad 2.
