# Decisiones técnicas del proyecto

**Estado:** aprobadas para el backend del challenge. Este documento registra decisiones técnicas y arquitectónicas compartidas; los requisitos funcionales y el proceso de trabajo viven en sus fuentes respectivas.

## Stack aprobado

| Área | Decisión |
|---|---|
| Lenguaje | Java 25 LTS. Se elige Java aunque la consigna original solicita Node.js. |
| Framework y API | Spring Boot 4.1.2, Spring MVC y REST bajo `/api/v1`. |
| Build | Gradle Wrapper 9.9.1 con Kotlin DSL y Java toolchain 25. |
| Contrato HTTP | OpenAPI 3 y Swagger UI con `springdoc-openapi` 3.2.0. |
| Persistencia | PostgreSQL, Spring Data JPA y Hibernate gestionado por el BOM de Spring Boot. |
| Migraciones | Flyway gestionado por Spring Boot. |
| Pruebas | JUnit 5, Mockito, MockMvc y Testcontainers con PostgreSQL. |
| Formato | Spotless Gradle Plugin 8.11.0 con Google Java Format. |
| Desarrollo local | Docker Compose para PostgreSQL. |

Las versiones fijan una base reproducible; una actualización requiere comprobar compatibilidad entre Java, Spring Boot, Gradle y plugins. Spring Boot gestiona las versiones de sus dependencias, salvo excepción justificada.

## Arquitectura

- Arquitectura hexagonal liviana, sin módulos o capas adicionales por convención.
- Dirección de dependencias: adaptadores → aplicación → dominio. Los puertos de aplicación describen accesos externos y los adaptadores los implementan.
- El dominio contiene conceptos y reglas de negocio, independiente de HTTP, persistencia, frameworks y tipos de aplicación.
- La aplicación coordina casos de uso; los adaptadores contienen detalles de transporte, persistencia e integración.
- El paquete Java base es `com.cocobank.challenge`.

## Persistencia

- El esquema de referencia es `database.sql`: `users`, `instruments`, `orders` y `marketdata`.
- Flyway aplica el esquema y los datos de referencia. Hibernate valida, pero no crea ni modifica el esquema en runtime.
- Los nombres físicos se mapean explícitamente. PostgreSQL convierte a minúsculas los identificadores no entrecomillados; por ejemplo, `instrumentId` se almacena como `instrumentid`.
- El esquema y los datos se preservan. Toda modificación requiere aprobación explícita.
- Las credenciales se obtienen de configuración externa y no se versionan.

## Verificación técnica

- Las pruebas de integración de persistencia usan PostgreSQL real mediante Testcontainers y requieren Docker; una base embebida no sustituye esa verificación.
- El formato se aplica y comprueba mediante las tareas Spotless del Gradle Wrapper.

Las versiones se contrastaron con la documentación oficial de [Java](https://www.oracle.com/java/technologies/java-se-support-roadmap.html), [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Gradle](https://docs.gradle.org/current/userguide/compatibility.html) y [Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html).
