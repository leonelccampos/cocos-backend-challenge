# coco-bank-challenge

API backend de un sistema bancario. La consulta de portfolio está implementada; búsqueda y órdenes se abordarán mediante sus especificaciones de feature.

La consigna original solicita Node.js. Este proyecto usa Java por decisión documentada en [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

## Documentación y decisiones

- [`docs/constitution.md`](./docs/constitution.md): principios del proyecto.
- [`PROJECT_SPEC.md`](./PROJECT_SPEC.md): decisiones técnicas y arquitectónicas aprobadas.
- [`AGENTS.md`](./AGENTS.md): reglas operativas y ubicación de las fuentes de verdad.
- [`backend-challenge.md`](./backend-challenge.md): consigna funcional original.
- [`database.sql`](./database.sql): esquema y datos de referencia entregados.
- [`specs/`](./specs/): especificaciones, planes y tareas por feature.
- [`preguntas.md`](./preguntas.md): decisiones de negocio pendientes de validación por los owners.

El stack aprobado está detallado en [`PROJECT_SPEC.md`](./PROJECT_SPEC.md).

## Requisitos

- JDK 25.
- Docker con Docker Compose.

En macOS, si el JDK 25 ya está instalado con Homebrew pero `java -version` no lo encuentra,
registra su instalación para el usuario actual:

```bash
mkdir -p "$HOME/Library/Java/JavaVirtualMachines"
ln -s "$(brew --prefix openjdk@25)/libexec/openjdk.jdk" \
  "$HOME/Library/Java/JavaVirtualMachines/openjdk-25.jdk"
/usr/libexec/java_home -V
java -version
```

El enlace usa la ruta estable de Homebrew y no modifica las instalaciones de otros usuarios.
Si el destino ya existe, comprueba a qué apunta antes de cambiarlo.

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

La aplicación escucha por defecto en `http://localhost:8080`. El endpoint de portfolio es `GET /api/v1/users/{userId}/portfolio?accountNumber=...`.
Las posiciones netas negativas se incluyen con cantidad y valor de mercado negativos, según la spec de portfolio.
El porcentaje de rendimiento acumulado de cada instrumento se devuelve en el campo `performance`.
`dailyReturn` es la variación diaria estimada en pesos cuando hay un cierre válido de hoy y la posición no registra operaciones `FILLED` hoy; si no puede calcularse, es `null`. La fecha de la orden se usa como proxy de ejecución y puede no reflejar ejecuciones posteriores; ver la spec para las condiciones y limitaciones.
La valuación usa el último `close` positivo fechado o, si no existe, el `previousClose` positivo del registro fechado más reciente; este último no tiene fecha exacta en el esquema. Si no hay precio utilizable, la posición permanece en la respuesta como `UNAVAILABLE` y se excluye del total.

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

Portfolio implementado según [`specs/001-get-portfolio/`](./specs/001-get-portfolio/). Búsqueda, órdenes y autenticación no están implementados; las decisiones funcionales pendientes se resuelven en la spec de cada feature.
