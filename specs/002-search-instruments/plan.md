# Plan de implementación — Búsqueda de activos

## Alcance y restricciones

Implementar [la spec](./spec.md) bajo [la constitución](../../docs/constitution.md) y
[las decisiones técnicas del proyecto](../../PROJECT_SPEC.md).

- La operación será una lectura sin efectos secundarios. No se modificarán órdenes,
  instrumentos, cotizaciones, migraciones ni el esquema.
- Se usarán Java 25, Spring MVC, Spring Data JPA, PostgreSQL, JUnit 5, Mockito,
  MockMvc y Testcontainers ya aprobados. No se agregan dependencias.
- Las capas siguen el patrón existente: adaptadores → aplicación → dominio.
- El contrato HTTP y los identificadores de código estarán en inglés; esta documentación,
  en español.
- La búsqueda devuelve todos los resultados, como exige la spec. No se introduce
  paginación ni un límite implícito.
- El precio y su fecha se seleccionan según RF-5; una alternativa basada en
  `previousClose` no tendrá fecha exacta y se expondrá con `closeDate: null`.

## Archivos que se crearán o modificarán

### Producción

| Acción y archivo | Responsabilidad | RF |
|---|---|---|
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/domain/InstrumentSearchField.java` | Enum de dominio para elegir `TICKER` o `NAME`; no depende de la representación HTTP. | RF-1, RF-3 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/domain/InstrumentSearchCandidate.java` | Datos de lectura del instrumento y los candidatos de cotización que necesita la selección pura. | RF-4, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/domain/InstrumentSearchResult.java` | Resultado de dominio con `id`, `ticker`, `name`, `type`, `marketPrice` y `closeDate`; los dos últimos admiten `null` según RF-5. | RF-4, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/domain/InstrumentSearchPolicy.java` | Lógica pura de normalización, coincidencia, filtro por tipo, selección de precio y ordenamiento. | RF-3–RF-5 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/application/port/out/InstrumentSearchReadPort.java` | Puerto de salida para leer candidatos del catálogo y sus datos de cotización, sin exponer JPA ni HTTP. | RF-4, RF-5, RF-7 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/application/usecase/SearchInstrumentsUseCase.java` | Orquestar lectura, política de búsqueda y resultado; producir el caso “sin coincidencias” como excepción de aplicación. | RF-4–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/application/exception/InstrumentSearchNotFoundException.java` | Representar que la búsqueda válida no encontró instrumentos, sin dependencia de HTTP. | RF-6 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/out/persistence/entity/InstrumentEntity.java` | Identificar `instruments` para el repositorio Spring Data sin depender de entidades de otra funcionalidad. | RF-4, RF-5, RF-7 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/out/persistence/repository/InstrumentSearchRepository.java` | Consulta nativa de solo lectura con proyección explícita del catálogo y candidatos de `marketdata`; seleccionar cotizaciones con `LEFT JOIN LATERAL`, sin N+1. | RF-4, RF-5, RF-7 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/out/persistence/repository/InstrumentSearchProjection.java` | Proyección de persistencia tipada para las columnas del repositorio. Los alias se escribirán explícitamente para preservar los nombres Java. | RF-4, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/out/persistence/adapter/InstrumentSearchReadAdapter.java` | Implementar el puerto, mapear proyecciones a candidatos de dominio y ejecutar la lectura en una transacción `readOnly`. | RF-4, RF-5, RF-7 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/controller/InstrumentSearchController.java` | Exponer `GET /api/v1/instruments`, mapear `field` validado al enum y devolver una lista JSON; documentar 200, 400 y 404 en OpenAPI. | RF-1, RF-2, RF-4, RF-6, RF-8 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/representation/InstrumentSearchRequest.java` | Enlazar y validar `query` no blanco y `field` con los valores exactos `ticker` o `name`; Spring responderá 400 ante ausencia o valor inválido. | RF-1, RF-2 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/representation/InstrumentSearchResponse.java` | Mapear cada resultado al contrato REST en inglés, incluyendo cotización y fecha nulas cuando corresponda. La lista se serializa como un arreglo JSON. | RF-4, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/controller/InstrumentSearchExceptionHandler.java` | Traducir únicamente `InstrumentSearchNotFoundException` a 404 con `ProblemDetail`; los errores de validación reutilizan el manejador REST existente. | RF-2, RF-6 |
| Modificar `README.md` | Documentar ruta, parámetros y ejemplos de búsqueda; actualizar el estado de la funcionalidad. | RF-1–RF-6, RF-8 |
| Modificar `src/main/resources/postman/Coco Bank - Portfolio.postman_collection.json` | Agregar solicitudes manuales de éxito por ticker/nombre y casos 400/404, sin credenciales ni autenticación. | RF-1–RF-6, RF-8 |

No se modifican `build.gradle.kts`, `src/main/resources/db/migration/**` ni
`database.sql`. La consulta usa los nombres físicos existentes de PostgreSQL, incluidos
`instruments.type`, `marketdata.instrumentid` y `marketdata.date`.

### Pruebas

| Acción y archivo | Responsabilidad | RF |
|---|---|---|
| Crear `src/test/java/com/cocobank/challenge/instrumentsearch/domain/InstrumentSearchPolicyTest.java` | Probar normalización, coincidencia, filtro de `ACCIONES`, selección del precio, nulos y orden determinista sin mocks. | RF-3–RF-5 |
| Crear `src/test/java/com/cocobank/challenge/instrumentsearch/application/usecase/SearchInstrumentsUseCaseTest.java` | Probar la orquestación, la delegación al puerto, el resultado y la excepción cuando no hay coincidencias. | RF-4–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/controller/InstrumentSearchControllerTest.java` | Probar con MockMvc la validación 400, los valores de respuesta y los estados 200/404; verificar que no se invoque el caso de uso ante parámetros inválidos. | RF-1, RF-2, RF-4, RF-6, RF-8 |
| Crear `src/test/java/com/cocobank/challenge/instrumentsearch/adapter/out/persistence/repository/InstrumentSearchRepositoryIT.java` | Verificar la proyección y la consulta nativa contra PostgreSQL real: cotización y desempates, filas sin fecha, fallback, tipos y columnas nulas. | RF-4, RF-5 |
| Crear `src/test/java/com/cocobank/challenge/instrumentsearch/adapter/in/rest/controller/InstrumentSearchControllerIT.java` | Verificar el flujo HTTP completo contra PostgreSQL con Testcontainers, incluidas búsquedas con acentos, contrato, errores y ausencia de escrituras. | RF-1–RF-8 |

El mapeo del adaptador de persistencia se verifica en la prueba de integración HTTP;
no se agrega una prueba aislada de getters o constructores.

## Funciones puras de lógica

Se concentran en `InstrumentSearchPolicy`; no leen reloj, base de datos, variables de
entorno ni estado global.

- `normalizeForSearch(value)`: recortar espacios exteriores, normalizar Unicode a NFD,
  eliminar marcas diacríticas y convertir a minúsculas con `Locale.ROOT`.
- `matches(candidate, normalizedQuery, field)`: tomar únicamente `ticker` o `name`
  según `field`, normalizarlo y buscar la consulta como subcadena. Un campo nulo no
  coincide.
- `selectMarketPrice(candidate)`: usar el `close` positivo más reciente con fecha y su
  fecha; si no existe, usar el `previousClose` positivo del registro fechado más reciente
  y devolver `closeDate: null`; si ninguno sirve, devolver ambos campos nulos.
- `search(candidates, query, field)`: normalizar, filtrar solo `ACCIONES`, aplicar
  `matches`, mapear la cotización, ordenar por ticker ascendente (nulos al final) y por
  ID ascendente. No mutar la colección recibida.

La validación de parámetros y la traducción a HTTP quedan en el adaptador REST; la
política pura también protege el mapeo de campos nulos admitidos por el esquema.

## Algoritmo de búsqueda y mapeo

La consulta de persistencia produce un candidato por instrumento e incluye, como máximo,
dos valores de mercado: el `close` positivo más reciente y su fecha, y el
`previousClose` del registro fechado más reciente. No se cargan todas las cotizaciones
históricas.

```text
GET /api/v1/instruments?query=...&field=...

enlazar query y field
si query falta, queda blanco tras strip, field falta
   o field no es exactamente "ticker" ni "name":
    responder 400

fieldDomain = TICKER si field == "ticker"; de lo contrario NAME
candidates = puerto.findAllInstrumentSearchCandidates()
results = InstrumentSearchPolicy.search(candidates, query, fieldDomain)

si results está vacío:
    lanzar InstrumentSearchNotFoundException

responder 200 con results como arreglo JSON
```

```text
search(candidates, query, field):
    normalizedQuery = normalizeForSearch(query)
    results = []

    para cada candidate en candidates:
        si candidate.type != "ACCIONES":
            continuar

        value = candidate.ticker si field == TICKER; si no candidate.name
        si value es null o normalizeForSearch(value) no contiene normalizedQuery:
            continuar

        si candidate.latestPositiveClose existe:
            marketPrice = latestPositiveClose
            closeDate = latestPositiveCloseDate
        si no, si candidate.latestDatedPreviousClose > 0:
            marketPrice = latestDatedPreviousClose
            closeDate = null
        si no:
            marketPrice = null
            closeDate = null

        agregar InstrumentSearchResult(candidate.id, candidate.ticker,
            candidate.name, candidate.type, marketPrice, closeDate)

    ordenar results por ticker ascendente, nulos al final,
        y luego por id ascendente
    devolver lista inmutable results
```

El repositorio selecciona candidatos de cotización con subconsultas correlacionadas:
una para el `close > 0` más reciente ordenado por `date DESC, id DESC`, y otra para
la fila fechada más reciente, también ordenada por `date DESC, id DESC`. Solo filas con
`date IS NOT NULL` participan. La política pura elige entre ambos candidatos conforme a
RF-5; así el mapeo conserva `closeDate: null` para el fallback `previousClose`.

## Decisiones técnicas y alternativas descartadas

1. **Arquitectura hexagonal liviana.** El controlador valida y adapta HTTP; el caso de
   uso orquesta el puerto; la política pura aplica las reglas; el adaptador consulta
   PostgreSQL. Se descarta colocar SQL en el caso de uso o lógica de búsqueda en el
   controlador porque acopla reglas de negocio al transporte o a persistencia.
2. **Normalización en Java, sin extensión ni dependencia.** Se usa `Normalizer` de la
   biblioteca estándar para ignorar diacríticos y mayúsculas. Se descarta agregar la
   extensión PostgreSQL `unaccent`, una columna normalizada o un índice: la spec y la
   constitución prohíben alterar esquema/infraestructura sin aprobación. Para el catálogo
   de referencia acotado y una respuesta sin paginación se lee la proyección de
   instrumentos y se filtra en memoria. Si el catálogo crece, optimizar la búsqueda
   requiere una decisión y aprobación separadas; no se introduce una optimización que
   cambie el esquema en esta iteración.
3. **Proyección nativa explícita con `LEFT JOIN LATERAL`.** La consulta obtiene los
   instrumentos y como máximo dos candidatos de precio por instrumento, sin cargar
   entidades completas ni ejecutar consultas N+1. Se descarta leer toda la historia de
   `marketdata` o hacer una consulta de precio por cada resultado. Alias y nombres físicos
   se mapean explícitamente; Hibernate no modifica el esquema.
4. **Selección de precio en política pura.** El adaptador entrega candidatos y la política
   aplica la prioridad acordada. Se descarta usar sin adaptación la consulta de portfolio:
   su proyección de fallback incluye la fecha del registro fuente, mientras que esta spec
   exige `closeDate: null` cuando se usa `previousClose`.
5. **404 para lista vacía.** El caso de uso transforma cero coincidencias en una excepción
   de aplicación y un adaptador la representa como 404, como acordó RF-6. Se descarta
   devolver 200 con arreglo vacío, aunque sea una convención frecuente para búsquedas.
6. **Lectura estricta y sin autenticación.** El puerto solo expone lectura y el endpoint
   no agrega autenticación. Se descarta introducir credenciales, usuario implícito,
   escritura, caché o nuevas dependencias, pues están fuera de la spec o no se necesitan.
7. **Respuesta como arreglo JSON.** Al ser una búsqueda que devuelve una lista, el
   controlador serializa `List<InstrumentSearchResponse>` directamente, sin envoltorio
   adicional ni campos no especificados.

## Estrategia de pruebas y cobertura de RF

- **Unitarias de dominio:** `InstrumentSearchPolicyTest` cubre `query` recortado,
  subcadenas, búsqueda por ambos valores de `field`, mayúsculas, acentos, campo nulo,
  exclusión de `MONEDA`, ticker duplicado, y orden ticker/ID. Prueba `close` positivo
  seleccionado por fecha e ID, fallback de `previousClose` con fecha nula y ausencia de
  precio con ambos campos nulos. (RF-3–RF-5)
- **Unitarias de aplicación:** `SearchInstrumentsUseCaseTest` usa un mock del puerto
  para confirmar que delega una vez, devuelve los resultados y lanza la excepción
  tipada para la lista vacía; no sustituye errores del puerto por resultados exitosos.
  (RF-4–RF-7)
- **HTTP con MockMvc:** `InstrumentSearchControllerTest` prueba ruta y método, respuesta
  en arreglo con los seis campos, ausencia de autenticación, `query`/`field` faltantes,
  query en blanco, campo inválido y traducción de cero coincidencias a 404. Verifica
  además que solicitudes inválidas no consulten el caso de uso. (RF-1, RF-2, RF-4,
  RF-6, RF-8)
- **Persistencia con Testcontainers/PostgreSQL:** `InstrumentSearchRepositoryIT` verifica
  los nombres físicos reales, inclusión del tipo y lectura sin escrituras, una proyección
  por instrumento, desempate por ID, ignorar filas sin fecha, selección de `close`,
  fallback y valores nulos. No se usa base embebida. (RF-4, RF-5)
- **Integración HTTP completa:** `InstrumentSearchControllerIT` recorre request,
  validación, caso de uso y PostgreSQL. Prueba `ticker`, nombre con acento, 200/400/404,
  orden, campos y cotizaciones; compara el estado/datos de catálogo y cotizaciones antes
  y después para confirmar que no hubo mutaciones. La solicitud sin credenciales prueba
  RF-8. (RF-1–RF-8)
- **Verificación manual en Postman:** agregar a la colección existente casos 200 por
  ticker/nombre, 400 por query vacía/field inválido y 404 sin coincidencias; comprobar
  `marketPrice` y `closeDate` con los datos de PostgreSQL. La prueba no requiere
  autenticación. (RF-1–RF-6, RF-8)
- **Formato y regresión:** ejecutar `./gradlew spotlessCheck`, las pruebas `test` e
  `integrationTest` y `./gradlew check` según [README](../../README.md). Docker es
  requisito para Testcontainers.

La feature queda lista cuando todos los RF tienen pruebas enfocadas en verde, las
integraciones se ejecutan contra PostgreSQL real, la solicitud manual de Postman coincide
con la spec y no se requieren cambios al esquema ni dependencias.
