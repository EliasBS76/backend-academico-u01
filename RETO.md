# Reto — Unidad 1

**Lo que se pedía:** hacer que la aplicación corra en el puerto 8081 sin tocar la
clase principal `BackendAcademicoApplication.java`.

## Cómo lo hice

Le paso el puerto como argumento al arrancar, en vez de cambiarlo en el código:

```
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Y en la consola salió:

```
Tomcat started on port 8081 (http) with context path '/'
```

También funciona con una variable de entorno, porque Spring Boot entiende
`SERVER_PORT` como si fuera `server.port`:

```
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

Las dos formas arrancan en 8081 y en ningún momento abrí un archivo `.java`.

## Las versiones que usé

```
java -version    →  openjdk version "25.0.4.1"
mvnw --version   →  Apache Maven 3.9.11, Java version: 25.0.4.1
```

## Las pruebas

```
.\mvnw.cmd test

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

La prueba `contextLoads()` pasó, o sea que Spring pudo armar el contexto sin
problemas.

## Por qué no hay que recompilar para cambiar el puerto

Porque el puerto no está escrito en el código Java. La clase principal solo
llama a `SpringApplication.run(...)` y ya; ahí no aparece ningún 8080 ni 8081.

El número del puerto es configuración, algo que Spring Boot lee **cuando la
aplicación arranca**, no cuando se compila. Y lo busca en varios lugares, en este
orden de prioridad:

1. Los argumentos que escribo en la terminal
2. Las variables de entorno
3. El archivo `application.properties`

Por eso, cuando le paso `--server.port=8081`, ese valor le gana al 8080 que está
en el archivo. Spring toma el puerto que ganó y con ese levanta Tomcat.

Dicho fácil: el programa compilado es el mismo, solo le cambio la información que
le doy al momento de encenderlo. Es parecido a un ventilador con distintas
velocidades — no lo desarmas ni lo reconstruyes para ponerlo en velocidad 2, solo
mueves la perilla. El aparato es el mismo.

Esto sirve en la vida real para poder usar la misma aplicación en la computadora
de uno, en la de pruebas y en la del servidor final, cada una con su puerto y su
configuración, sin tener que compilarla de nuevo cada vez.

## Antes de pasar a la Unidad 2

El archivo `application.properties` quedó con la configuración normal:

```properties
spring.application.name=backend-academico
server.port=8080
```

Y el reto está guardado en la rama `reto/u01-puerto-8081`, aparte de `main`.
