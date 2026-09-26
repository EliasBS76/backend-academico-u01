# Entrega — Unidad 1: Entorno reproducible y primer arranque

**Alumno:** Elias Bernal
**Materia:** Desarrollo Web — Back-end
**Fecha:** 25 de septiembre de 2026
**Repositorio:** https://github.com/EliasBS76/backend-academico-u01

Documento autocontenido: incluye el código completo, la evidencia de ejecución y
el reto. Todas las salidas de consola son reales, capturadas en el equipo.

**Entorno:** Windows 11 · Temurin JDK 25.0.4.1 · Maven Wrapper 3.9.11 · Spring Boot 4.1.0

---

# Índice

1. [Objetivo de la unidad](#1-objetivo-de-la-unidad)
2. [Solución: código fuente completo](#2-solución-código-fuente-completo)
3. [Evidencia de ejecución](#3-evidencia-de-ejecución)
4. [Reto: ejecutar en el puerto 8081](#4-reto-ejecutar-en-el-puerto-8081)
5. [Control de versiones](#5-control-de-versiones)

---

# 1. Objetivo de la unidad

Transformar el programa de verificación de `U01/inicio` en una aplicación Spring
Boot funcional, compilada con **JDK 25** con destino **Java 21**, que levante un
servidor web embebido en el puerto 8080.

Archivos modificados respecto al punto de partida:

| Acción | Archivo |
|---|---|
| Crear | `.gitignore` |
| Modificar | `pom.xml` |
| Eliminar | `README.md` (heredado del programa de verificación) |
| Eliminar | `src/main/java/mx/edu/VerificacionJava.java` |
| Crear | `src/main/java/mx/edu/backendacademico/BackendAcademicoApplication.java` |
| Crear | `src/main/resources/application.properties` |
| Crear | `src/test/java/mx/edu/backendacademico/BackendAcademicoApplicationTests.java` |

---

# 2. Solución: código fuente completo

## Estructura del proyecto

```
pom.xml
.gitignore
mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
src/main/java/mx/edu/backendacademico/BackendAcademicoApplication.java
src/main/resources/application.properties
src/test/java/mx/edu/backendacademico/BackendAcademicoApplicationTests.java
```

## `pom.xml`

El padre `spring-boot-starter-parent` administra las versiones de Spring Boot,
Spring Framework, Hibernate y el servidor web, que no coinciden entre sí. La
propiedad `maven.compiler.release` fija el destino en Java 21 aunque el compilador
provenga del JDK 25.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath />
    </parent>
    <groupId>mx.edu</groupId>
    <artifactId>backend-academico</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>backend-academico-u01</name>
    <description>Proyecto acumulativo de Desarrollo Web - Unidad 01</description>
    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-boot-starter-webmvc` incorpora la infraestructura MVC y el servidor
Servlet. `spring-boot-starter-test` va con `scope test`, por lo que no viaja en el
artefacto final.

## `src/main/java/mx/edu/backendacademico/BackendAcademicoApplication.java`

Punto de entrada. Vive en el paquete raíz `mx.edu.backendacademico`, por encima de
los paquetes `controller`, `service` y `repository` que se crearán más adelante,
para que el escaneo de componentes los alcance.

```java
package mx.edu.backendacademico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendAcademicoApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendAcademicoApplication.class, args);
    }
}
```

`@SpringBootApplication` combina configuración, búsqueda de componentes y
autoconfiguración. El método `main` es estático porque la JVM lo invoca sin
construir una instancia de la clase; que el proceso siga vivo después de esa
llamada se debe a los hilos del servidor embebido.

## `src/main/resources/application.properties`

```properties
spring.application.name=backend-academico
server.port=8080
```

## `src/test/java/mx/edu/backendacademico/BackendAcademicoApplicationTests.java`

```java
package mx.edu.backendacademico;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendAcademicoApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

El cuerpo está vacío a propósito: la prueba no verifica reglas de negocio, sino que
el contexto de Spring **pueda construirse**. Si falta un bean o hay un error de
ensamblado, la prueba falla y detiene la construcción.

## `.gitignore`

```
target/
.idea/
*.iml
.env
```

Excluye productos de compilación y configuración local del IDE. Las fuentes, las
pruebas y el Maven Wrapper sí se versionan.

---

# 3. Evidencia de ejecución

## 3.1 Versiones del entorno

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

Ambas salidas identifican Java 25, como pide la preparación del equipo.

## 3.2 El bytecode apunta a Java 21

Usar JDK 25 no significa escribir ni generar Java 25. Comprobación directa sobre la
clase compilada:

```
$ javap -verbose -cp target\classes mx.edu.backendacademico.BackendAcademicoApplication
  minor version: 0
  major version: 65
```

`major version: 65` corresponde a **Java 21**. Confirma que `maven.compiler.release=21`
hizo efecto: la herramienta es JDK 25, el destino de compilación es Java 21. Son dos
decisiones distintas.

## 3.3 Pruebas

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

`contextLoads()` en verde.

## 3.4 Arranque del servidor en 8080

```
$ .\mvnw.cmd -B spring-boot:run

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.0)

INFO m.e.b.BackendAcademicoApplication     : Starting BackendAcademicoApplication
                                             using Java 25.0.4.1 with PID 15872
INFO o.s.boot.tomcat.TomcatWebServer       : Tomcat initialized with port 8080 (http)
INFO o.apache.catalina.core.StandardEngine : Starting Servlet engine: [Apache Tomcat/11.0.22]
INFO o.s.boot.tomcat.TomcatWebServer       : Tomcat started on port 8080 (http)
                                             with context path '/'
INFO m.e.b.BackendAcademicoApplication     : Started BackendAcademicoApplication
                                             in 2.306 seconds (process running for 3.164)
```

Puerto en escucha:

```
LocalPort  State   OwningProcess
---------  -----   -------------
     8080  Listen  15872
```

## 3.5 Respuesta del servidor

```
$ curl http://localhost:8080/
HTTP 404
Content-Type: application/json

{"timestamp":"2026-09-25T23:54:35.233Z","status":404,"error":"Not Found","path":"/"}
```

**El 404 es el resultado esperado.** La Unidad 1 no define ninguna ruta todavía;
los controladores corresponden a la Unidad 2.

Conviene notar qué demuestra ese 404: es un JSON generado por Spring, no un error
del navegador. Para producirlo tuvo que funcionar la cadena completa — Tomcat
aceptó la conexión, el `DispatcherServlet` procesó la petición, buscó un handler,
no encontró ninguno y delegó en el manejador de errores. Un fallo de arranque
habría dado `connection refused`, no una respuesta con formato.

Servidor iniciado y aplicación funcional son evidencias diferentes: aquí queda
demostrada la primera.

---

# 4. Reto: ejecutar en el puerto 8081

**Consigna:** ejecutar la aplicación en el puerto 8081 mediante configuración
externa, sin editar la clase principal.

Se verificaron las dos vías. En ninguna se modificó un archivo `.java`.

## Opción A — argumento de línea de comandos

```
$ .\mvnw.cmd -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat initialized with port 8081 (http)
INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http)
                                       with context path '/'
INFO m.e.b.BackendAcademicoApplication : Started BackendAcademicoApplication
                                         in 2.285 seconds (process running for 2.765)
```

Estado de los puertos durante esa ejecución:

```
LocalPort  State   OwningProcess
---------  -----   -------------
     8081  Listen  12620

8080: libre  (no se usó el valor de application.properties)
```

Con el JAR ya empaquetado, la forma equivalente sería:

```
$ .\mvnw.cmd clean package
$ java -jar target\backend-academico-0.0.1-SNAPSHOT.jar --server.port=8081
```

## Opción B — variable de entorno

Spring Boot relaja los nombres de propiedad, así que `SERVER_PORT` se resuelve a
`server.port`:

```
$ $env:SERVER_PORT = "8081"      # PowerShell
$ .\mvnw.cmd -B spring-boot:run

INFO o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8081 (http)
                                       with context path '/'
```

En CMD sería `set SERVER_PORT=8081`; en bash, `export SERVER_PORT=8081`.

## Por qué cambiar el puerto no exige recompilar la lógica Java

El puerto no está escrito en el código. La clase principal solo llama a
`SpringApplication.run(...)`; ahí no aparece ningún 8080 ni 8081.

El número de puerto es **configuración**, un dato que Spring Boot lee **cuando la
aplicación arranca**, no cuando se compila. Y lo busca en varias fuentes, con este
orden de prioridad:

```
argumentos de línea de comandos  >  variables de entorno  >  application.properties  >  valores por defecto
```

Por eso `--server.port=8081` le gana al `8080` del archivo. Spring toma la
propiedad ya resuelta y con ella configura el servidor embebido, en tiempo de
ejecución. El programa compilado es el mismo; lo único que cambia es la
información que recibe al encenderse.

### Comprobación empírica

No es solo un argumento teórico. Se comparó el hash SHA256 del archivo `.class`
antes y después de arrancar en 8081:

```
SHA256 de target\classes\mx\edu\backendacademico\BackendAcademicoApplication.class

ANTES   (tras ejecutar en 8080): 5711D44B8D009F4EDF79FD8A1DB3AF3632654B9D0B3AC516D50AAE547593C671
DESPUÉS (tras ejecutar en 8081): 5711D44B8D009F4EDF79FD8A1DB3AF3632654B9D0B3AC516D50AAE547593C671
```

Hash idéntico: **el mismo bytecode, byte por byte, sirvió en 8080 y en 8081.**

La utilidad práctica es directa: el mismo artefacto compilado puede desplegarse en
desarrollo, pruebas y producción con puertos, credenciales y URLs distintas, sin
reconstruirlo cada vez.

## Vuelta a la configuración base

Antes de continuar a la Unidad 2, `application.properties` conserva el valor base:

```properties
spring.application.name=backend-academico
server.port=8080
```

---

# 5. Control de versiones

El repositorio excluye `target/` mediante `.gitignore`. Archivos versionados:

```
.gitignore
.mvn/wrapper/maven-wrapper.properties
mvnw
mvnw.cmd
pom.xml
src/main/java/mx/edu/backendacademico/BackendAcademicoApplication.java
src/main/resources/application.properties
src/test/java/mx/edu/backendacademico/BackendAcademicoApplicationTests.java
```

El trabajo está en dos ramas, como pide la unidad:

- **`main`** — la solución de la construcción guiada, con `server.port=8080`.
  Es el punto de partida de la Unidad 2.
- **`reto/u01-puerto-8081`** — el reto, guardado en rama aparte.

Repositorio: https://github.com/EliasBS76/backend-academico-u01

---

# Autoevaluación

**¿Puede JDK 25 producir código que se ejecute en Java 21?** Sí, compilando con
`--release 21` y usando dependencias compatibles. Quedó verificado con
`major version: 65` en el bytecode.

**¿Una prueba vacía con `@SpringBootTest` es inútil?** No. Su cuerpo no comprueba
reglas de negocio, pero el intento de iniciar el contexto detecta problemas de
ensamblado antes de que lleguen a ejecución.
