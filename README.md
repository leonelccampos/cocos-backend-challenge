# coco-bank-challenge

API backend de un sistema bancario. Están implementadas la consulta de portfolio, la búsqueda de activos y el envío idempotente de órdenes.

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
Cada posición expone `quantity` y `availableQuantity`; esta última descuenta ventas `NEW`
sin modificar las acciones conservadas y nunca puede ser negativa.
El porcentaje de rendimiento acumulado de cada instrumento se devuelve en el campo `performance`.
`dailyReturn` es la variación diaria estimada en pesos cuando hay un cierre válido de hoy y la posición no registra operaciones `FILLED` hoy; si no puede calcularse, es `null`. La fecha de la orden se usa como proxy de ejecución y puede no reflejar ejecuciones posteriores; ver la spec para las condiciones y limitaciones.
La valuación usa el último `close` positivo fechado o, si no existe, el `previousClose` positivo del registro fechado más reciente; este último no tiene fecha exacta en el esquema. Si no hay precio utilizable, la posición permanece en la respuesta como `UNAVAILABLE` y se excluye del total.

### Buscar activos

`GET /api/v1/instruments?query=...&field=...` no requiere autenticación. `query` debe
contener texto no blanco y `field` debe ser exactamente `ticker` o `name`. La búsqueda
por subcadena no distingue mayúsculas ni acentos. Devuelve todos los instrumentos de tipo
`ACCIONES`, ordenados por ticker e ID, con `id`, `ticker`, `name`, `type`, `marketPrice`
y `closeDate`. Los parámetros inválidos producen HTTP 400; sin coincidencias se responde 404.
La comparación de acentos cubre los caracteres latinos comunes del catálogo de referencia;
otras marcas Unicode pueden comportarse de forma diferente.

El precio es el `close` positivo fechado más reciente. Si no existe, se usa el
`previousClose` positivo de la fila fechada más reciente, con `closeDate: null`; si
ninguno es utilizable, ambos campos son `null`.

```bash
curl 'http://localhost:8080/api/v1/instruments?query=GGAL&field=ticker'
curl 'http://localhost:8080/api/v1/instruments?query=banco&field=name'
```

La [colección Postman](./src/main/resources/postman/Coco%20Bank%20-%20Portfolio.postman_collection.json)
incluye ejemplos de éxito y errores de búsqueda. Las cotizaciones dependen de la base local.

### Enviar órdenes

`POST /api/v1/users/{userId}/orders` envía una orden `MARKET` o `LIMIT` con
`instrumentId`, `side`, exactamente una alternativa entre `size` y `amount`, y `price`
para órdenes `LIMIT`. Requiere `Idempotency-Key`. No requiere autenticación. Las órdenes
aceptadas responden 201; los rechazos de negocio persistidos responden 422 con la orden
y un motivo estable; repetir una solicitud equivalente devuelve el resultado original.

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

## Verificación con POSTMAN

En `src/main/resources/postman` están las colecciones `Coco Bank - Portfolio`,
`Coco Bank - Orders` y `Coco Bank - Order Portfolio Impact` (esta última combina
envío de órdenes con consulta de portfolio para verificar cómo cada estado de
orden —pendiente, ejecutada, rechazada— afecta el efectivo disponible, el total
de cuenta y las tenencias; usa claves de idempotencia dinámicas para poder
re-ejecutarse, pero modifica el estado real de la cuenta de referencia en la
base local).


## Estado

Portfolio, búsqueda y envío de órdenes implementados según sus specs.
