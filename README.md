# coco-bank-challenge

API backend de un sistema bancario. Este repositorio contiene el scaffold técnico aprobado; las funcionalidades de portfolio, búsqueda y órdenes se implementarán luego, cada una con su SDD.

La consigna original solicita Node.js. Este proyecto usa Java por decisión documentada en [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

## Documentación

- [`PROJECT_SPEC.md`](./PROJECT_SPEC.md): stack, arquitectura y decisiones de diseño.
- [`AGENTS.md`](./AGENTS.md): instrucciones operativas para agentes de código.
- [`backend-challenge.md`](./backend-challenge.md): consigna funcional original.
- [`database.sql`](./database.sql): esquema y datos de referencia entregados.

## Stack

Java 25, Spring Boot 4.1.2, Gradle 9.9.1 (Kotlin DSL), Spring Data JPA/Hibernate, PostgreSQL, Flyway, OpenAPI/Swagger UI, JUnit/Mockito/MockMvc, Testcontainers y Spotless. Las decisiones y motivos están en [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

## Requisitos

- JDK 25.
- Docker con Docker Compose.

## Ejecutar localmente

Copiar la configuración de ejemplo y levantar PostgreSQL:

```bash
cp .env.example .env
docker compose up -d
```

Iniciar la aplicación:

```bash
set -a
. ./.env
set +a
./gradlew bootRun
```

La aplicación escucha por defecto en `http://localhost:8080`. Todavía no hay endpoints de negocio.

### Comprobar que la aplicación está levantada

Deja `bootRun` ejecutándose y, desde el navegador, abre Swagger UI en `http://localhost:8080/swagger-ui.html`. La especificación OpenAPI está disponible en `http://localhost:8080/v3/api-docs`.

También puedes comprobar ambas rutas desde otra terminal:

```bash
curl -sS -L -o /dev/null -w 'Swagger UI: HTTP %{http_code}\n' http://localhost:8080/swagger-ui.html
curl -sS -o /dev/null -w 'OpenAPI: HTTP %{http_code}\n' http://localhost:8080/v3/api-docs
```

Ambas deben responder con HTTP `200`. Es normal que `bootRun` permanezca ejecutándose en primer plano mientras la aplicación está activa.

Flyway aplica el esquema de referencia y, a continuación, los datos de ejemplo provistos. El bloque de inicio exporta las variables de `.env` al proceso de la aplicación para que use las mismas credenciales que Docker Compose. Si cambia el nombre de la base o el puerto, actualizar también `DB_URL`. Las credenciales de `.env.example` son exclusivamente para desarrollo local; no usar en otros entornos.

## Verificación

```bash
./gradlew test
./gradlew integrationTest
./gradlew spotlessCheck
./gradlew check
./gradlew build
```

`integrationTest` inicia PostgreSQL con Testcontainers y requiere que Docker esté disponible. La tarea `test` no inicia contenedores.

Aplicar formato:

```bash
./gradlew spotlessApply
```

## Estado

Scaffold técnico únicamente. No se implementaron reglas de negocio ni autenticación. Las reglas pendientes deben resolverse en el SDD de cada funcionalidad antes de codificarlas.
