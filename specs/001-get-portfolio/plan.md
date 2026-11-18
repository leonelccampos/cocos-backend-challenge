# Plan de implementación — Get Portfolio

> Estado actual: la lectura usa puertos de aplicación por entidad (`UserPort`, `OrderPort`,
> `InstrumentPort`, `MarketDataPort`), compartidos con la feature de envío de órdenes y
> ubicados en `application.port.out`. El dominio (`PortfolioCalculator`, `PortfolioCashSummary`,
> `InstrumentPosition`, `PortfolioValuation`, `PortfolioPerformance`, `MarketDataSelection`)
> calcula a partir de datos reales de `Order`, `Instrument` y `MarketData`; no existen modelos
> de lectura intermedios como `PortfolioReadData`/`PortfolioReadPort`/`PortfolioReadAdapter`
> (reemplazados por los puertos de entidad). `application` y `adapter` se organizan por tipo
> técnico (`usecase`, `port.out`, `dto`, `exception`; `in.rest.{controller,handler,representation,filter}`,
> `out.persistence.{adapter,entity,repository}`), no por feature — ver
> [PROJECT_SPEC.md](../../PROJECT_SPEC.md). La sección "Archivos previstos" de más abajo
> refleja esa estructura actual, no la del scaffold original. La ampliación de
> `availableQuantity` se describe en [el plan de órdenes](../003-submit-orders/plan.md).

## Alcance y restricciones

Implementar la consulta de portfolio definida en [spec.md](./spec.md), respetando [constitution.md](../../docs/constitution.md), el stack aprobado y la arquitectura hexagonal liviana de [PROJECT_SPEC.md](../../PROJECT_SPEC.md).

- El caso de uso será de solo lectura y no modificará órdenes ni saldos.
- Se preservará el esquema y los datos de referencia. No se agregan dependencias.
- Los identificadores y tipos de código estarán en inglés; esta documentación, en español.
- La lógica de cálculo pertenecerá al dominio/aplicación. HTTP y PostgreSQL quedarán en sus adaptadores.
- Todos los cálculos monetarios usarán `BigDecimal`; la cantidad de acciones será entera.

## Flujo y límites hexagonales

1. El adaptador REST valida el ID y el número de cuenta y crea la consulta de aplicación. La ruta acordada es `GET /api/v1/users/{userId}/portfolio?accountNumber=...`, por ser una lectura idempotente.
2. `GetPortfolioUseCase` consulta `UserPort` y valida la pareja usuario/cuenta. Si no coincide, lanza `PortfolioNotFoundException`, que se transforma en 404.
3. El caso de uso consulta todas las órdenes en `OrderPort`, obtiene los IDs de instrumentos de compras y ventas ejecutadas y consulta `InstrumentPort` y `MarketDataPort` en lote. Los adaptadores JPA devuelven objetos de dominio y propagan los errores técnicos.
4. Funciones puras del dominio calculan efectivo, acciones, valuaciones, rendimiento y valor total. Una cotización ausente/inválida y un ROI no calculable son estados de resultado, no excepciones; datos imprescindibles inconsistentes generan `InvalidPortfolioDataException`.
5. El controlador mapea el resultado a la respuesta REST. No contiene cálculos financieros ni consultas de persistencia y no captura excepciones para construir respuestas de éxito.

El caso de uso ejecuta las lecturas dentro de una transacción Spring de solo lectura con aislamiento `REPEATABLE_READ`, para conservar una instantánea consistente entre los puertos. El dominio permanece independiente de Spring.

## Archivos relevantes (estado actual)

Esta feature comparte código con [003-submit-orders](../003-submit-orders/plan.md): los puertos de entidad, sus adaptadores JPA y varias clases de dominio de `Order` se implementaron para el envío de órdenes y se reutilizan aquí sin duplicarse. La tabla marca con (compartido) lo que no es exclusivo de portfolio.

| Archivo actual | Responsabilidad | RF |
|---|---|---|
| `src/main/java/com/cocobank/challenge/domain/portfolio/Portfolio.java` | Resultado inmutable del cálculo: pesos disponibles, efectivo reservado incluido en el total, valor total y detalle de instrumentos. | RF-2, RF-5 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/PortfolioInstrument.java` | Datos de un instrumento con cantidad distinta de cero y con signo, valuación y rendimiento; representa explícitamente la valuación no disponible, nunca como cero. | RF-3, RF-4, RF-6 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/ValuationStatus.java` | Estado compartido de valuación disponible/no disponible. | RF-4, RF-5, RF-6 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/PortfolioCashSummary.java` | Resultado tipado del cálculo de efectivo disponible y reservado; factory estático `calculate(orders)` (compartido con 003-submit-orders). | RF-2, RF-5 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/InstrumentPosition.java` | Resultado tipado de la posición neta y disponible de un instrumento; factory estático `calculate(instrumentId, orders)` (compartido con 003-submit-orders). | RF-3 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/PortfolioValuation.java` | Resultado tipado de la valuación de mercado de un instrumento; factory estático `calculate(quantity, close)`. | RF-4, RF-5 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/PortfolioPerformance.java` | Resultado tipado del cálculo de rendimiento acumulado; factory estático `calculate(orders, valuation)`. | RF-6 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/InvalidPortfolioDataException.java` | Señalar datos persistidos imprescindibles e inconsistentes para el cálculo, sin depender de Spring ni de HTTP. | RF-2–RF-6 |
| `src/main/java/com/cocobank/challenge/domain/portfolio/PortfolioCalculator.java` | Orquesta las funciones puras anteriores; único método público `calculatePortfolio(orders, instruments, selectionsByInstrumentId)`. Sin Spring, HTTP, reloj ni logs. | RF-2–RF-6 |
| `src/main/java/com/cocobank/challenge/domain/market/MarketDataSelection.java` *(compartido)* | Selecciona el cierre utilizable (histórico o `previousClose`) a partir de los registros de mercado de un instrumento; `selectByInstrumentId(marketData)` agrupa y selecciona en un solo paso para toda la cuenta. | RF-4, RF-7 |
| `src/main/java/com/cocobank/challenge/domain/user/User.java` *(compartido)* | Identidad y número de cuenta del usuario; `hasAccountNumber(accountNumber)` resuelve la coincidencia. | RF-1 |
| `src/main/java/com/cocobank/challenge/application/usecase/GetPortfolioUseCase.java` | Orquesta lectura (vía puertos), cálculo y resultado; no conoce entidades JPA ni DTOs HTTP. Transacción `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`. | RF-1–RF-6 |
| `src/main/java/com/cocobank/challenge/application/exception/PortfolioNotFoundException.java` | Representar que no existe la pareja de usuario y cuenta solicitada, sin acoplar la aplicación al transporte HTTP. | RF-1 |
| `src/main/java/com/cocobank/challenge/application/port/out/UserPort.java` *(compartido)* | `findById(userId)` devuelve `Optional<User>`. | RF-1 |
| `src/main/java/com/cocobank/challenge/application/port/out/OrderPort.java` *(compartido)* | `findByUserId(userId)` devuelve todas las órdenes del usuario como `Order`; el dominio determina su efecto. | RF-2, RF-3, RF-6 |
| `src/main/java/com/cocobank/challenge/application/port/out/InstrumentPort.java` *(compartido)* | `findByIds(instrumentIds)` en lote, devuelve `Instrument` con ticker/nombre/tipo. | RF-3, RF-4 |
| `src/main/java/com/cocobank/challenge/application/port/out/MarketDataPort.java` *(compartido)* | `findByInstrumentIds(instrumentIds)` en lote, devuelve los registros de mercado fechados de esos instrumentos. | RF-4, RF-7 |
| `src/main/java/com/cocobank/challenge/adapter/in/rest/controller/PortfolioController.java` | Exponer la ruta GET acordada y delegar al caso de uso. Documentar el contrato aprobado en OpenAPI. | RF-1–RF-6 |
| `src/main/java/com/cocobank/challenge/adapter/in/rest/representation/PortfolioRequest.java` | Recibir y validar los campos requeridos; errores de ausencia o formato inválido se traducen a 400. | RF-1 |
| `src/main/java/com/cocobank/challenge/adapter/in/rest/representation/PortfolioResponse.java` | DTO en inglés. Expone efectivo disponible, total y activos; si no se puede valuar un instrumento, sus importes de mercado y rendimiento serán `null` y `valuationStatus` será `UNAVAILABLE`, nunca cero. | RF-1–RF-6 |
| `src/main/java/com/cocobank/challenge/adapter/in/rest/handler/PortfolioExceptionHandler.java` | Traducir validación a 400, cuenta inexistente/no asociada a 404 y errores internos conocidos a 500 genérico, sin exponer detalles. | RF-1–RF-6 |
| `src/main/java/com/cocobank/challenge/adapter/in/rest/filter/RequestCorrelationFilter.java` | Generar un ID de request no controlado por el cliente, publicarlo en `X-Request-Id` y mantenerlo en MDC durante la petición. | RF-1–RF-6 |
| `src/main/java/com/cocobank/challenge/adapter/out/persistence/entity/UserEntity.java` *(compartido)* | Mapeo mínimo de `users.id` y `users.accountnumber`; no cargar ni exponer email. | RF-1 |
| `src/main/java/com/cocobank/challenge/adapter/out/persistence/entity/{InstrumentEntity,MarketDataEntity,OrderEntity}.java` *(compartidas)* | Mapeo JPA de instrumentos, registros de mercado y órdenes. | RF-2–RF-4, RF-6, RF-7 |
| `src/main/java/com/cocobank/challenge/adapter/out/persistence/adapter/{UserPersistenceAdapter,OrderPersistenceAdapter,InstrumentPersistenceAdapter,MarketDataPersistenceAdapter}.java` *(compartidos)* | Implementan los puertos de entidad anteriores. | RF-1–RF-4, RF-6, RF-7 |
| `src/test/java/com/cocobank/challenge/domain/portfolio/{PortfolioTest,PortfolioInstrumentTest,PortfolioCalculatorTest,PortfolioCashSummaryTest,InstrumentPositionTest,InvalidPortfolioDataExceptionTest}.java` | Pruebas unitarias de las funciones puras y casos límite monetarios, una clase de test por clase de producción. | RF-2–RF-6 |
| `src/test/java/com/cocobank/challenge/domain/market/MarketDataSelectionTest.java` *(compartido)* | Cierres históricos, `previousClose`, ausencia de precio y cálculo de rendimiento diario. | RF-4, RF-7 |
| `src/test/java/com/cocobank/challenge/application/usecase/GetPortfolioUseCaseTest.java` | Probar orquestación de puertos, cuenta inexistente, propagación de errores y transacción de solo lectura, usando dobles de cada puerto. | RF-1–RF-6 |
| `src/test/java/com/cocobank/challenge/application/exception/PortfolioNotFoundExceptionTest.java` | Verificar que el error de cuenta ausente no contiene datos de la cuenta solicitada. | RF-1 |
| `src/test/java/com/cocobank/challenge/adapter/in/rest/representation/{PortfolioRequestTest,PortfolioResponseTest}.java` | Validación de campos y serialización JSON sin sustituir valores no disponibles por cero. | RF-1, RF-2, RF-4–RF-6 |
| `src/test/java/com/cocobank/challenge/adapter/in/rest/controller/PortfolioControllerTest.java` | Pruebas HTTP con MockMvc: validación, 400/404, errores internos 500 sin detalles y serialización de valores no disponibles. | RF-1–RF-6 |
| `src/test/java/com/cocobank/challenge/adapter/in/rest/controller/PortfolioControllerIT.java` | Pruebas HTTP reales contra PostgreSQL: valuación con ambas alternativas, posición sin precio, rendimiento diario y contrato OpenAPI. | RF-1–RF-7 |
| `src/test/java/com/cocobank/challenge/adapter/in/rest/handler/PortfolioExceptionHandlerTest.java` | Verificar respuestas genéricas y niveles de log para los errores tipados que el manejador traduce a HTTP. | RF-1–RF-6 |
| `src/test/java/com/cocobank/challenge/adapter/in/rest/filter/RequestCorrelationFilterTest.java` | Verificar generación de un ID nuevo, propagación a MDC/response header y rechazo de IDs suministrados por el cliente. | RF-1–RF-6 |
| `src/test/java/com/cocobank/challenge/adapter/out/persistence/entity/UserEntityIT.java` *(compartido)* | Verificar con PostgreSQL real el mapeo de `accountnumber` sin mapear ni leer el email. | RF-1 |
| `src/test/java/com/cocobank/challenge/adapter/out/persistence/adapter/PortfolioEntityPortsIT.java` | Verificar con PostgreSQL real la cuenta de referencia completa (efectivo, posición negativa, rendimiento), selección de precios por fecha e ID, y propagación de un dato de orden inconsistente. | RF-1–RF-7 |

No se modifican `CocoBankChallengeApplication`, `build.gradle.kts`, las migraciones existentes ni `DatabaseMigrationIT`. El escaneo de componentes ya parte del paquete raíz; las dependencias de testing y persistencia ya están aprobadas.


## Modelo de dominio y funciones puras

Se mantiene un modelo de lectura pequeño e inmutable, no un agregado con comportamiento de escritura. Cada cálculo derivado (efectivo, posición, valuación, rendimiento) es un factory estático en su propio value object (`PortfolioCashSummary.calculate`, `InstrumentPosition.calculate`, `PortfolioValuation.calculate`, `PortfolioPerformance.calculate`), siguiendo el mismo patrón que `OrderSubmissionResult`; `PortfolioCalculator` orquesta esas piezas sin duplicar su lógica. `PortfolioCashSummary` e `InstrumentPosition` se comparten con 003-submit-orders, que valida disponibilidad con los mismos cálculos antes de aceptar una orden nueva.

Funciones puras previstas:

- **Resumen de efectivo:** separar ingresos/egresos ejecutados, compras/ventas `FILLED` y compras `NEW` reservadas. Efectivo disponible = importes `CASH_IN` − importes `CASH_OUT` − costo de compras `FILLED` + ingresos de ventas `FILLED` − reservas de compras `NEW`. Los ingresos y egresos usan `size`; las compras y ventas ejecutadas usan `price × size`. Efectivo reservado = precio límite × cantidad para compras `NEW`. El efectivo total que contribuye al valor de cuenta = disponible + reservado.
- **Tenencias:** por instrumento, cantidad ejecutada = compras `FILLED` − ventas `FILLED`. Una venta `NEW` no reduce las acciones conservadas; impedir una venta sin acciones suficientes es responsabilidad de `SubmitOrderUseCase` (003-submit-orders), no de esta consulta. Se omiten cantidades netas iguales a cero. No se introducen fracciones.
- **Valuación:** para cantidad distinta de cero y precio de cierre válido (> 0), valor de mercado = cantidad con signo × precio. Precio ausente, cero o negativo produce estado no disponible, no un monto cero.
- **Rendimiento acumulado:** resultado = importes recibidos por ventas `FILLED` + valor actual con signo − costo total de compras `FILLED`. Porcentaje = resultado / costo total de compras × 100, incluso si la cantidad es negativa; no representa necesariamente retorno sobre capital o garantías. Si el costo total es cero, el porcentaje es no disponible; si falta valuación, el rendimiento del instrumento es no disponible.
- **Rendimiento diario estimado:** `dailyReturn` en pesos = cantidad neta con signo × (`close` de hoy − `previousClose` de la misma fila), sin redondeo intermedio. Se devuelve `null` si el precio es una alternativa, no corresponde a hoy, `previousClose` no es positivo o alguna operación `FILLED` del instrumento tiene fecha de hoy, futura o desconocida. Hoy se determina en `America/Argentina/Buenos_Aires`; el `datetime` sin zona de la orden se interpreta en esa zona y se usa como proxy de ejecución, con el riesgo documentado en RF-7.
- **Total de cuenta:** efectivo disponible + efectivo reservado + suma de valores de mercado disponibles. Los instrumentos no valuados se excluyen y se indican en su detalle.

Todos los importes usarán `BigDecimal` y conservarán la escala/precisión de origen, sin redondeo intermedio. El porcentaje, cuya división puede ser periódica, se redondeará a 8 decimales con `HALF_EVEN`, según decisión aprobada para esta feature. Los datos persistidos inconsistentes necesarios para un cálculo (por ejemplo, precio ausente en una compra/venta de instrumento `FILLED`) no se reemplazarán silenciosamente por cero: se reportarán como `InvalidPortfolioDataException`.

La implementación de `InstrumentPosition.calculate(instrumentId, orders)` (compartida con 003-submit-orders) suma compras `FILLED` y resta ventas `FILLED` por instrumento con aritmética entera exacta; `PortfolioCalculator` agrupa primero todas las órdenes por instrumento en un solo paso y omite del resultado los instrumentos cuya posición neta quede en cero. Las pruebas cubren también que `NEW`, `CANCELLED`, `REJECTED` y los movimientos de efectivo no alteran las tenencias.

`PortfolioCashSummary.calculate(orders)` (compartido con 003-submit-orders) procesa ingresos/egresos `FILLED` usando `size`, resta compras `FILLED`, suma ventas `FILLED` y descuenta las compras `NEW` del disponible mientras las conserva como reserva. Las órdenes canceladas o rechazadas dejan de reservar; las compras ejecutadas pasan a reflejar el precio efectivo. Si una operación que afecta al efectivo carece de precio, lanza `InvalidPortfolioDataException`.

`PortfolioValuation.calculate(quantity, close)` multiplica la cantidad distinta de cero y con signo por un precio de cierre positivo; si el precio falta o no es positivo, retorna `UNAVAILABLE` sin monto de mercado. El `close` proviene de `MarketDataSelection`, que ya resolvió la alternativa histórica/`previousClose` para ese instrumento.

`PortfolioPerformance.calculate(orders, valuation)` suma los ingresos de ventas `FILLED`, resta el costo total de compras `FILLED` y agrega el valor de mercado actual; calcula el porcentaje solo si el costo de compras es positivo, con escala 8 y `HALF_EVEN`.

`PortfolioCalculator.calculatePortfolio(orders, instruments, selectionsByInstrumentId)` es el único método público: agrupa órdenes e instrumentos por ID una sola vez, arma el resultado inmutable a partir de los cálculos anteriores, conserva instrumentos no valuados en el detalle y los excluye del total, ordena el detalle por ID y falla explícitamente ante metadatos de instrumento faltantes. Una cantidad neta negativa es válida y aporta una valuación negativa al total.

### Límite respecto a ventas pendientes

Las acciones de una venta `NEW` siguen formando parte de las acciones conservadas porque la orden todavía no está ejecutada. `availableQuantity` (cantidad menos reservas de ventas `NEW`) se calcula y expone desde la ampliación aprobada en [el plan de órdenes](../003-submit-orders/plan.md), pero esta consulta sigue sin validar ni impedir una venta futura; esa regla la aplica `SubmitOrderUseCase` al enviar una orden nueva.
Aceptar cantidades negativas en el historial de lectura no modifica esa regla de envío de órdenes.

## Consultas de persistencia

Se usan repositorios JPA por entidad y se conservan los nombres físicos en minúsculas, sin cambiar el esquema:

1. Se busca el usuario por ID y se verifica su número de cuenta, sin cargar email.
2. Se leen todas las órdenes del usuario; el dominio determina su efecto en efectivo y posiciones.
3. Se leen instrumentos y registros de mercado fechados en lote, sin consultas N+1. El dominio selecciona el cierre positivo más reciente por fecha e ID; si no existe, usa únicamente el `previousClose` positivo del registro más reciente. Se conservan las filas originales y se registran las alternativas sin importes.
4. El dominio calcula la valuación, los rendimientos y el total. La lectura actual carga el historial fechado de los instrumentos consultados; su volumen debe evaluarse si crece significativamente.

## Patrones y decisiones técnicas

- **Puertos y adaptadores:** REST es adaptador de entrada; JPA/PostgreSQL es adaptador de salida; el caso de uso depende del puerto, no de ambos detalles.
- **Caso de uso:** `GetPortfolioUseCase` coordina una operación de consulta; no se introduce CQRS, bus de eventos ni un módulo adicional para una sola lectura.
- **Calculadora stateless:** el caso de uso crea `PortfolioCalculator`, que no tiene dependencias ni estado; se evita acoplar la lógica pura a Spring o agregar configuración de beans sin necesidad.
- **Modelos de dominio:** los adaptadores mapean entidades JPA a objetos de dominio, separados de los DTOs REST.
- **Funciones puras:** probar aritmética y reglas sin Spring ni base de datos. Se descarta colocarlas en el controlador o en consultas SQL.
- **Errores explícitos:** request incompleto/inválido → 400; usuario/cuenta inexistente o no asociado → 404; fallo de persistencia o dato persistido inconsistente → 500 genérico, nunca respuesta parcial con importes inventados.
- **Sin dependencias nuevas:** usar Spring MVC, Spring Data JPA, JUnit, Mockito, MockMvc y Testcontainers ya declarados.
- **Contrato REST:** usar la ruta GET acordada y documentar en OpenAPI el ID y número de cuenta requeridos. El porcentaje de rendimiento acumulado se expone como `performance`, sin el nombre anterior `accumulatedReturnPercentage`; `dailyReturn` es el importe diario estimado en pesos o `null`. Para una valuación no disponible, devolver importes `null` y `valuationStatus: UNAVAILABLE`, nunca cero. `availableQuantity` por instrumento sí se expone (ampliación aprobada en 003-submit-orders); no agregar el efectivo reservado como campo separado, solo se refleja en `totalAccountValue`.

## Manejo de errores por capa

| Capa | Condición | Mecanismo y resultado |
|---|---|---|
| Adaptador REST de entrada | `accountNumber` falta, está vacío o supera 20 caracteres; `userId` no es entero positivo. | Validar el request y el parámetro de ruta; `PortfolioExceptionHandler` traduce los errores de validación a 400. Si falta el segmento `userId`, la ruta no coincide y Spring responde 404. |
| Aplicación | No existe la pareja usuario/cuenta. | El puerto de lectura informa ausencia; `GetPortfolioUseCase` lanza `PortfolioNotFoundException` antes de calcular. El adaptador HTTP la traduce a 404. |
| Adaptador de persistencia | La consulta no encuentra cuenta/precio o falla por un error de base de datos. | La cuenta ausente se representa como ausencia, no como una instantánea vacía; un precio ausente se conserva como dato no disponible. Las excepciones técnicas de Spring Data/JPA se propagan sin capturas locales ni valores de reemplazo. |
| Dominio | Cotización ausente, cero o negativa; costo de compras igual a cero para el porcentaje. | Son resultados previstos: marcar la valuación o el porcentaje como no disponible según corresponda; no lanzar una excepción ni sustituir por cero un importe faltante. |
| Dominio | Falta o es inconsistente un dato persistido indispensable para calcular, como el precio de una compra/venta de instrumento `FILLED`. | Lanzar `InvalidPortfolioDataException`; no producir una respuesta parcial ni inventar importes. |
| Adaptador REST de salida | Se recibe `PortfolioNotFoundException`, `InvalidPortfolioDataException` o un error técnico de persistencia. | Responder respectivamente 404 o 500 genérico. No incluir mensajes internos, parámetros, consultas ni stack traces en el cuerpo HTTP. |
| Errores no previstos | Se produce una excepción no clasificada explícitamente. | No agregar capturas amplias ni convertirla en éxito; dejar que el manejo estándar de Spring Boot produzca 500. La configuración actual no activa la inclusión de mensajes o stack traces. |

`PortfolioExceptionHandler` maneja únicamente categorías conocidas: validación, `PortfolioNotFoundException`, `InvalidPortfolioDataException`, `DataAccessException` y `TransactionException`. No se agrega un manejador genérico de `Exception`. Las pruebas HTTP verificarán estados y que los errores 500 no revelen detalles internos; las pruebas de aplicación y dominio verificarán que los errores tipados se propaguen sin ocultarse.

## Logs y protección de datos

Usar SLF4J/Logback provisto por Spring Boot, sin instalar librerías ni registrar dentro de funciones puras. `RequestCorrelationFilter` genera un UUID por request (no acepta un valor provisto por el cliente), lo publica en la cabecera `X-Request-Id` y lo mantiene en MDC con la clave `request_id`; el patrón de Logback incluye esa clave para correlacionar todas las entradas del request sin usar datos de usuario o cuenta.

- **INFO:** una entrada al completar la consulta con operación, resultado HTTP, cantidad de instrumentos y cantidad de valuaciones no disponibles. No registrar ID de usuario, número de cuenta, email, montos, cantidades de acciones ni el contenido de órdenes.
- **WARN:** precio faltante, cero o negativo, con identificador del instrumento y fecha si existe; emitir como máximo una advertencia por instrumento por consulta. No incluir datos de usuario.
- **WARN:** uso de cierre histórico o `previousClose` como alternativa, con identificador del instrumento y fecha del registro seleccionado; no registrar importes.
- **ERROR:** fallo de PostgreSQL o dato persistido inconsistente que impida calcular. Registrar la excepción y el `request_id` generado para la petición; omitir datos de la request y no ocultar el error.
- No habilitar logs de parámetros SQL de Hibernate en entornos que puedan contener datos de usuario o financieros.

La implementación registra el resumen INFO de éxito en el controlador, las advertencias WARN en el caso de uso y los errores tipados ERROR junto con la excepción en `PortfolioExceptionHandler`. No se registran duraciones ni cantidades de filas por lectura: se descartó por bajo valor diagnóstico frente al ruido que agregaba al caso de uso. Los tests verifican niveles, contenido mínimo, presencia del `request_id` en el contexto de la petición y ausencia de parámetros de cuenta/usuario en los mensajes agregados.

## Estrategia de pruebas

### Unitarias

`PortfolioTest` verificará la inmutabilidad de las colecciones e importes del portfolio. `PortfolioInstrumentTest` verificará las cantidades distintas de cero, la coherencia de signos y el estado de valuación.

`PortfolioCalculatorTest` cubrirá:

- Combinaciones de `CASH_IN`, `CASH_OUT`, compras y ventas `FILLED`.
- Reserva de compra `NEW`: reduce disponible y permanece incluida en efectivo total; al cambiar a `CANCELLED`/`REJECTED` desaparece la reserva; al pasar a `FILLED` se refleja la compra ejecutada.
- Venta `NEW`: no reduce las acciones conservadas; impedir una venta duplicada queda fuera de esta funcionalidad.
- Cantidad entera con signo, cálculo de cantidad neta y omisión de cero.
- Precio válido, ausente, cero y negativo; exclusión de valores no disponibles del total.
- Precio ausente en compra/venta de instrumento `FILLED` u otro dato persistido indispensable inconsistente: se informa mediante `InvalidPortfolioDataException`, no como resultado parcial.
- Selección de la valuación y cálculo de resultado y porcentaje acumulados, incluyendo ventas parciales y costo de compras cero.
- Rendimiento diario de posiciones positivas y negativas; indisponibilidad por precio o fecha de operación no elegibles.
- Total de cuenta con efectivo reservado e instrumentos valuados/no valuados.

### Aplicación y HTTP

- `GetPortfolioUseCaseTest`: consultas a los puertos de entidad, pareja usuario/cuenta inexistente como `PortfolioNotFoundException`, propagación de errores y transacción de lectura.
- `PortfolioControllerTest` con MockMvc: query faltante/vacía/sobre 20 caracteres y `userId` no entero/no positivo → 400; ruta sin segmento de usuario o usuario/cuenta inexistente → 404; dato inconsistente/fallo técnico → 500 genérico sin mensajes internos; serialización de valores no disponibles sin sustituirlos por cero; respuesta correcta para cuenta sin posiciones.

### Integración PostgreSQL

`UserEntityIT` y `PortfolioEntityPortsIT` usan Testcontainers PostgreSQL y las migraciones reales, sin H2. Verifican:

- Mapeo de `users.id` y `users.accountnumber`, sin seleccionar email.
- Coincidencia exacta de ID con número de cuenta; una pareja incorrecta o inexistente no coincide.
- Consulta de todas las órdenes del usuario y mapeo de instrumentos.
- Lectura de cotizaciones fechadas y selección por fecha e ID, sin fabricar registros.
- Dato de orden indispensable ausente y propagación explícita del error.

`MarketDataSelectionTest` cubre cierres históricos, `previousClose`, ausencia de precio y rendimiento diario. `GetPortfolioUseCaseTest` verifica la propagación de errores, las advertencias sin importes y la transacción de solo lectura con `REPEATABLE_READ`. `PortfolioControllerIT` comprueba por HTTP real la valuación con ambas alternativas, una posición sin precio, el rendimiento diario y su contrato OpenAPI.

La suite `DatabaseMigrationIT` existente continúa cubriendo la carga de esquema y datos. No se agregan dependencias ni se cambia la configuración de pruebas.

### Cobertura por requisito

| Requisito | Componentes principales | Pruebas |
|---|---|---|
| RF-1 | Solicitud/controlador, caso de uso, puerto y consulta de cuenta | Unitarias de aplicación y MockMvc 400/404/500; integración de coincidencia ID/cuenta |
| RF-2 | Consulta de movimientos y cálculo de efectivo/reservas | Unitarias de efectivo, estados y datos inconsistentes; integración de estados y movimientos |
| RF-3 | Cálculo de tenencias; prevención de ventas duplicadas fuera de alcance | Unitarias de cantidades y cero; integración de movimientos ejecutados |
| RF-4 | Consulta de último precio y valuación pura | Unitarias de precio válido/inválido; integración de fecha e ID |
| RF-5 | Cálculo puro del total y DTO de salida | Unitarias de reservas y valuaciones omitidas; MockMvc de respuesta |
| RF-6 | Cálculo puro de resultado y ROI | Unitarias de ventas parciales y denominador cero; integración de movimientos |
| RF-7 | Selección de precio y fechas, cálculo diario y DTO de salida | Unitarias de cálculo y condiciones de indisponibilidad; integración PostgreSQL, HTTP y OpenAPI |
