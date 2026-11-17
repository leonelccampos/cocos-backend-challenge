# Tareas de implementación — Búsqueda de activos

Las tareas están ordenadas por dependencia. Los requisitos, el diseño y los casos de
prueba se detallan en [la spec](./spec.md) y [el plan](./plan.md). No agregar
dependencias ni modificar el esquema o los datos de referencia.

## Dominio y aplicación

- [x] **T01 — Definir los modelos de búsqueda.** **RF:** RF-1, RF-3, RF-4, RF-5.
  - **Hecho cuando:** el dominio representa el campo de búsqueda, los datos candidatos y
    los seis campos del resultado, incluyendo precio y fecha nulos, sin depender de HTTP
    ni de JPA.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; Postman
    verificó el portfolio existente con HTTP 200. La ruta de búsqueda aún no existe.

- [x] **T02 — Implementar la coincidencia y el orden.** **RF:** RF-3, RF-4.
  - **Depende de:** T01.
  - **Hecho cuando:** las pruebas unitarias verifican subcadenas por el campo elegido,
    recorte, mayúsculas y acentos, campo nulo, filtro exclusivo de `ACCIONES`, todas las
    coincidencias y orden por ticker e ID, incluidos tickers repetidos.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; Postman
    verificó el portfolio existente con HTTP 200. La ruta de búsqueda aún no existe.

- [x] **T03 — Seleccionar el precio del resultado.** **RF:** RF-4, RF-5.
  - **Depende de:** T01.
  - **Hecho cuando:** las pruebas unitarias verifican la prioridad del `close` positivo
    fechado, el `previousClose` positivo con fecha nula y la ausencia de precio con
    ambos campos nulos.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; Postman
    verificó el portfolio existente con HTTP 200. La ruta de búsqueda aún no existe.

- [x] **T04 — Definir el puerto y orquestar la búsqueda.** **RF:** RF-4, RF-5, RF-6, RF-7.
  - **Depende de:** T01, T02, T03.
  - **Hecho cuando:** el caso de uso lee los candidatos mediante el puerto, aplica la
    política, devuelve los resultados o lanza la excepción de búsqueda sin coincidencias;
    sus pruebas verifican la delegación y la propagación de errores de lectura.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; Postman
    verificó el portfolio existente con HTTP 200. La ruta de búsqueda aún no existe.

## Persistencia

- [x] **T05 — Consultar el catálogo y los candidatos de cotización.** **RF:** RF-4, RF-5, RF-7.
  - **Depende de:** T01.
  - **Hecho cuando:** la consulta y su proyección devuelven un candidato por instrumento,
    con el `close` positivo fechado más reciente y el `previousClose` de la fila fechada
    más reciente, ambos con desempate por ID; usan nombres físicos y alias explícitos,
    sin consultar toda la historia ni generar consultas N+1.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; la prueba
    inicial usa PostgreSQL real. Postman verificó el portfolio existente con HTTP 200;
    la ruta de búsqueda aún no existe. Los casos adicionales quedan para T06.

- [x] **T06 — Verificar la consulta con PostgreSQL.** **RF:** RF-4, RF-5, RF-7.
  - **Depende de:** T05.
  - **Hecho cuando:** las pruebas con Testcontainers y PostgreSQL real verifican
    desempates, filas sin fecha, precios no positivos, fallback, columnas nulas, tipos
    y ausencia de escrituras.
  - **Verificación:** pruebas unitarias, integración con PostgreSQL y formato Gradle
    aprobados; Postman verificó el portfolio existente con HTTP 200.

- [x] **T07 — Implementar el adaptador de lectura.** **RF:** RF-4, RF-5, RF-7.
  - **Depende de:** T04, T05.
  - **Hecho cuando:** el adaptador implementa el puerto, mapea la proyección a candidatos,
    ejecuta la lectura en una transacción de solo lectura y propaga los errores de
    persistencia sin convertirlos en resultados vacíos; el caso de uso queda registrado
    en Spring con el adaptador disponible.
  - **Verificación:** pruebas unitarias, integración con PostgreSQL y formato Gradle
    aprobados; Postman verificó el portfolio existente con HTTP 200.

## Contrato HTTP

- [x] **T08 — Validar los parámetros de búsqueda.** **RF:** RF-1, RF-2.
  - **Hecho cuando:** la validación HTTP exige `query` no blanco tras recortar y `field`
    exactamente `ticker` o `name`; las solicitudes inválidas responden 400 sin invocar
    el caso de uso.
  - **Verificación:** pruebas unitarias y MockMvc de la ruta aprobadas; Postman verificó
    un campo inválido con HTTP 400.

- [x] **T09 — Exponer la búsqueda y mapear la respuesta.** **RF:** RF-1, RF-4, RF-5, RF-8.
  - **Depende de:** T04, T08.
  - **Hecho cuando:** `GET /api/v1/instruments` delega al caso de uso y devuelve HTTP
    200 sin autenticación, con un arreglo JSON de los seis campos acordados, incluidos
    los valores nulos de precio y fecha cuando correspondan.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; curl
    verificó la búsqueda HTTP 200 y Postman un campo inválido con HTTP 400.

- [x] **T10 — Traducir la ausencia de resultados.** **RF:** RF-2, RF-6.
  - **Depende de:** T04, T08, T09.
  - **Hecho cuando:** una búsqueda válida sin coincidencias responde 404 con
    `ProblemDetail`, mientras que los parámetros inválidos siguen respondiendo 400.
  - **Verificación:** pruebas unitarias, integración y formato Gradle aprobados; curl
    verificó el 404 y Postman el 400 por campo inválido.

- [x] **T11 — Verificar el controlador y documentar OpenAPI.** **RF:** RF-1, RF-2, RF-4,
  RF-5, RF-6, RF-8.
  - **Depende de:** T08, T09, T10.
  - **Hecho cuando:** MockMvc comprueba la ruta, método, parámetros, estados 200/400/404,
    estructura y valores de la respuesta, ausencia de autenticación y no invocación del
    caso de uso ante datos inválidos; OpenAPI describe la operación y esos estados.
  - **Verificación:** MockMvc, OpenAPI con PostgreSQL, pruebas Gradle y formato aprobados;
    Postman verificó HTTP 400 para un campo inválido.

## Verificación y documentación

- [x] **T12 — Verificar el flujo HTTP completo con PostgreSQL.** **RF:** RF-1–RF-8.
  - **Depende de:** T02, T03, T06, T07, T11.
  - **Hecho cuando:** las pruebas HTTP con Testcontainers pasan para ticker y nombre con
    acentos, 200/400/404, filtro, orden, cotizaciones y consulta sin credenciales; al
    comparar instrumentos, órdenes y cotizaciones antes y después no hay cambios.
  - **Verificación:** pruebas HTTP con PostgreSQL, unitarias y formato Gradle aprobados;
    Postman verificó HTTP 400 para un campo inválido.

- [x] **T13 — Documentar y probar los ejemplos en Postman.** **RF:** RF-1–RF-6, RF-8.
  - **Depende de:** T12.
  - **Hecho cuando:** el README describe la ruta y sus parámetros, la colección existente
    incluye búsquedas por ticker y nombre y casos 400/404, y las solicitudes ejecutadas
    en Postman confirman los estados y los campos de cotización frente a PostgreSQL.
  - **Verificación:** el usuario ejecutó los cinco ejemplos y confirmó estados, precios
    y fechas frente a PostgreSQL; pruebas unitarias, integración y formato Gradle aprobados.

- [x] **T14 — Ejecutar la regresión y cerrar la iniciativa.** **RF:** RF-1–RF-8.
  - **Depende de:** T01–T13.
  - **Hecho cuando:** `./gradlew test`, `./gradlew integrationTest`,
    `./gradlew spotlessCheck` y `./gradlew check` pasan; la verificación en Postman
    está completada y todos los RF tienen pruebas correspondientes sin cambios de esquema
    ni dependencias nuevas.
  - **Verificación:** `./gradlew test integrationTest spotlessCheck check` aprobó 86 pruebas
    unitarias y 30 de integración, sin fallos ni omisiones. Los ejemplos de Postman fueron
    confirmados por el usuario; el esquema y las dependencias no cambiaron en la iniciativa.
