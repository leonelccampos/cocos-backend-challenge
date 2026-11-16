# Plan de implementación — Get Portfolio

## Alcance y restricciones

Implementar la consulta de portfolio definida en [spec.md](./spec.md), respetando [constitution.md](../../docs/constitution.md), el stack aprobado y la arquitectura hexagonal liviana de [PROJECT_SPEC.md](../../PROJECT_SPEC.md).

- El caso de uso será de solo lectura y no modificará órdenes ni saldos.
- Se preservará el esquema y los datos de referencia. No se agregan dependencias.
- Los identificadores y tipos de código estarán en inglés; esta documentación, en español.
- La lógica de cálculo pertenecerá al dominio/aplicación. HTTP y PostgreSQL quedarán en sus adaptadores.
- Todos los cálculos monetarios usarán `BigDecimal`; la cantidad de acciones será entera.

## Flujo y límites hexagonales

1. El adaptador REST valida el ID y el número de cuenta y crea la consulta de aplicación. La ruta acordada es `GET /api/v1/users/{userId}/portfolio?accountNumber=...`, por ser una lectura idempotente.
2. `GetPortfolioUseCase` solicita una instantánea al puerto de salida `PortfolioReadPort`. Si no existe la pareja usuario/cuenta, lanza `PortfolioNotFoundException`, que se transforma en 404.
3. El adaptador de persistencia obtiene movimientos y precios con consultas de solo lectura, y mapea proyecciones a tipos de entrada del dominio. El dominio no depende de tipos de aplicación ni de adaptadores. Los errores técnicos de persistencia se propagan; no se convierten en resultados vacíos.
4. Funciones puras del dominio calculan efectivo, acciones, valuaciones, rendimiento y valor total. Una cotización ausente/inválida y un ROI no calculable son estados de resultado, no excepciones; datos imprescindibles inconsistentes generan `InvalidPortfolioDataException`.
5. El controlador mapea el resultado a la respuesta REST. No contiene cálculos financieros ni consultas de persistencia y no captura excepciones para construir respuestas de éxito.

El adaptador de persistencia ejecutará la verificación de cuenta, lectura de órdenes y lectura de precios dentro de una transacción de solo lectura con aislamiento `REPEATABLE_READ`, para que correspondan a una instantánea consistente. La alternativa de ejecutar consultas independientes con el aislamiento predeterminado se descarta porque una actualización concurrente podría mezclar estados de distintas lecturas. El dominio y el caso de uso no dependerán de Spring.

## Archivos previstos

Todos los archivos de producción de esta feature son nuevos: el scaffold solo contiene el punto de entrada, sin lógica de portfolio. No se prevén cambios al build, dependencias, migraciones, esquema ni datos. La configuración agrega el formato del ID de correlación en los logs.

| Acción y archivo | Responsabilidad | RF |
|---|---|---|
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/Portfolio.java` | Resultado inmutable del cálculo: pesos disponibles, efectivo reservado incluido en el total, valor total y detalle de instrumentos. | RF-2, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/PortfolioInstrument.java` | Datos de un instrumento con cantidad distinta de cero y con signo, valuación y rendimiento; representa explícitamente la valuación no disponible, nunca como cero. | RF-3, RF-4, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/ValuationStatus.java` | Estado compartido de valuación disponible/no disponible. | RF-4, RF-5, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/OrderMovement.java` | Movimiento de orden tipado que consume el cálculo del dominio. | RF-2–RF-4, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/MarketPrice.java` | Precio de mercado y fecha asociados al cálculo del dominio. | RF-4–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/OrderSide.java` | Tipos de movimiento de orden conocidos por el dominio. | RF-2, RF-3, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/OrderStatus.java` | Estados de orden conocidos por el dominio. | RF-2, RF-3, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/PortfolioCashSummary.java` | Resultado tipado del cálculo de efectivo disponible y reservado. | RF-2, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/PortfolioValuation.java` | Resultado tipado de la valuación de mercado de un instrumento. | RF-4, RF-5 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/PortfolioPerformance.java` | Resultado tipado del cálculo de rendimiento acumulado. | RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/InvalidPortfolioDataException.java` | Señalar datos persistidos imprescindibles e inconsistentes para el cálculo, sin depender de Spring ni de HTTP. | RF-2–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/domain/PortfolioCalculator.java` | Funciones puras de cálculo, sin Spring, HTTP, reloj, consultas ni logs. | RF-2–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/application/usecase/GetPortfolioUseCase.java` | Orquestar lectura, cálculo y resultado; no conoce entidades JPA ni DTOs HTTP. | RF-1–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/application/exception/PortfolioNotFoundException.java` | Representar que no existe la pareja de usuario y cuenta solicitada, sin acoplar la aplicación al transporte HTTP. | RF-1 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/application/dto/PortfolioReadData.java` | Modelo de lectura inmutable de aplicación; agrupa los movimientos y precios obtenidos por el puerto. | RF-2–RF-4, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/application/port/out/PortfolioReadPort.java` | Puerto de salida que consulta por ID de usuario y número de cuenta y devuelve `Optional<PortfolioReadData>`: vacío significa pareja inexistente; presente con colecciones vacías significa cuenta existente sin movimientos ni cotizaciones. No depende de HTTP ni JPA. | RF-1–RF-4, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/in/rest/controller/PortfolioController.java` | Exponer la ruta GET acordada y delegar al caso de uso. Documentar el contrato aprobado en OpenAPI. | RF-1–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/in/rest/representation/PortfolioRequest.java` | Recibir y validar los campos requeridos; errores de ausencia o formato inválido se traducen a 400. | RF-1 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/in/rest/representation/PortfolioResponse.java` | DTO en inglés. Expone efectivo disponible, total y activos; si no se puede valuar un instrumento, sus importes de mercado y rendimiento serán `null` y `valuationStatus` será `UNAVAILABLE`, nunca cero. | RF-1–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/in/rest/controller/PortfolioExceptionHandler.java` | Traducir validación a 400, cuenta inexistente/no asociada a 404 y errores internos conocidos a 500 genérico, sin exponer detalles. | RF-1–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/in/rest/filter/RequestCorrelationFilter.java` | Generar un ID de request no controlado por el cliente, publicarlo en `X-Request-Id` y mantenerlo en MDC durante la petición. | RF-1–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/out/persistence/entity/UserEntity.java` | Mapeo mínimo de `users.id` y `users.accountnumber`; no cargar ni exponer email. | RF-1 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/out/persistence/repository/PortfolioReadRepository.java` | Consultas de solo lectura: valida la cuenta y obtiene movimientos y cotizaciones mediante proyecciones tipadas. | RF-1–RF-4, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/out/persistence/repository/OrderMovementProjection.java` | Proyección de persistencia para los movimientos consultados; se mantiene separada del repositorio. | RF-2, RF-3, RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/out/persistence/repository/MarketPriceProjection.java` | Proyección de persistencia para el último precio consultado; se mantiene separada del repositorio. | RF-4–RF-6 |
| Crear `src/main/java/com/cocobank/challenge/portfolio/adapter/out/persistence/adapter/PortfolioReadAdapter.java` | Implementar el puerto, ejecutar las consultas dentro de la transacción de lectura y mapear proyecciones sin filtrar lógica HTTP al dominio. | RF-1–RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/domain/PortfolioTest.java` | Verificar que `Portfolio` conserva importes y expone una lista de instrumentos inmutable. | RF-2, RF-5 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/domain/PortfolioInstrumentTest.java` | Verificar cantidad distinta de cero, coherencia de signos y estado de valuación, y representación de porcentajes no disponibles. | RF-3, RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/domain/PortfolioCalculatorTest.java` | Pruebas unitarias de las funciones puras y casos límite monetarios. | RF-2–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/domain/InvalidPortfolioDataExceptionTest.java` | Verificar que la excepción tipada conserva el mensaje diagnóstico indicado por dominio. | RF-2–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/application/dto/PortfolioReadDataTest.java` | Verificar copias inmutables de movimientos/cotizaciones y conservar cotizaciones sin cierre disponible. | RF-2, RF-3, RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/application/port/out/PortfolioReadPortTest.java` | Verificar que la ausencia de cuenta se distingue de una cuenta existente sin movimientos. | RF-1–RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/application/usecase/GetPortfolioUseCaseTest.java` | Probar orquestación, cuenta inexistente y manejo de instantáneas usando un doble del puerto. | RF-1–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/application/exception/PortfolioNotFoundExceptionTest.java` | Verificar que el error de cuenta ausente no contiene datos de la cuenta solicitada. | RF-1 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/in/rest/representation/PortfolioRequestTest.java` | Verificar que el número de cuenta no sea nulo/blanco y respete el máximo físico de 20 caracteres sin restringir el alfabeto. | RF-1 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/in/rest/representation/PortfolioResponseTest.java` | Verificar mapeo JSON, valores no disponibles como `null` y ausencia de efectivo reservado como campo. | RF-2, RF-4–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/in/rest/controller/PortfolioControllerTest.java` | Pruebas HTTP con MockMvc: validación, 400/404, errores internos 500 sin detalles y serialización de valores no disponibles. | RF-1–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/in/rest/filter/RequestCorrelationFilterTest.java` | Verificar generación de un ID nuevo, propagación a MDC/response header y rechazo de IDs suministrados por el cliente. | RF-1–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/in/rest/controller/PortfolioExceptionHandlerTest.java` | Verificar respuestas genéricas para los errores tipados que el manejador traduce a HTTP. | RF-1–RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/out/persistence/entity/UserEntityIT.java` | Verificar con PostgreSQL real el mapeo de `accountnumber` sin mapear ni leer el email. | RF-1 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/out/persistence/repository/PortfolioReadRepositoryIT.java` | Verificar coincidencia de cuenta, filtros de movimientos y selección de cotización con PostgreSQL real. | RF-1–RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/out/persistence/adapter/PortfolioReadAdapterTest.java` | Verificar propagación de errores técnicos y configuración transaccional del adaptador. | RF-1–RF-4, RF-6 |
| Crear `src/test/java/com/cocobank/challenge/portfolio/adapter/out/persistence/adapter/PortfolioReadAdapterIT.java` | Verificar consultas y mapeos contra PostgreSQL real con Testcontainers y migraciones existentes. | RF-1–RF-6 |

No se modifican `CocoBankChallengeApplication`, `build.gradle.kts`, las migraciones existentes ni `DatabaseMigrationIT`. El escaneo de componentes ya parte del paquete raíz; las dependencias de testing y persistencia ya están aprobadas.

## Modelo de dominio y funciones puras

Se mantendrá un modelo de lectura pequeño e inmutable, no un agregado con comportamiento de escritura. No se propone un servicio de dominio separado: las reglas de cálculo forman una unidad cohesiva en `PortfolioCalculator`; dividirlas en helpers o servicios adicionales no agrega una responsabilidad distinta.

Funciones puras previstas:

- **Resumen de efectivo:** separar ingresos/egresos ejecutados, compras/ventas `FILLED` y compras `NEW` reservadas. Efectivo disponible = importes `CASH_IN` − importes `CASH_OUT` − costo de compras `FILLED` + ingresos de ventas `FILLED` − reservas de compras `NEW`. Los ingresos y egresos usan `size`; las compras y ventas ejecutadas usan `price × size`. Efectivo reservado = precio límite × cantidad para compras `NEW`. El efectivo total que contribuye al valor de cuenta = disponible + reservado.
- **Tenencias:** por instrumento, cantidad ejecutada = compras `FILLED` − ventas `FILLED`. Una venta `NEW` no reduce las acciones conservadas. La prevención de que esas acciones se oferten en otra orden pertenece a la futura funcionalidad de envío de órdenes; el portfolio no calcula acciones vendibles ni las expone. Se omiten cantidades netas iguales a cero. No se introducen fracciones.
- **Valuación:** para cantidad distinta de cero y precio de cierre válido (> 0), valor de mercado = cantidad con signo × precio. Precio ausente, cero o negativo produce estado no disponible, no un monto cero.
- **Rendimiento acumulado:** resultado = importes recibidos por ventas `FILLED` + valor actual con signo − costo total de compras `FILLED`. Porcentaje = resultado / costo total de compras × 100, incluso si la cantidad es negativa; no representa necesariamente retorno sobre capital o garantías. Si el costo total es cero, el porcentaje es no disponible; si falta valuación, el rendimiento del instrumento es no disponible.
- **Rendimiento diario estimado:** `dailyReturn` en pesos = cantidad neta con signo × (`close` de hoy − `previousClose` de la misma fila), sin redondeo intermedio. Se devuelve `null` si el precio es una alternativa, no corresponde a hoy, `previousClose` no es positivo o alguna operación `FILLED` del instrumento tiene fecha de hoy, futura o desconocida. Hoy se determina en `America/Argentina/Buenos_Aires`; el `datetime` sin zona de la orden se interpreta en esa zona y se usa como proxy de ejecución, con el riesgo documentado en RF-7.
- **Total de cuenta:** efectivo disponible + efectivo reservado + suma de valores de mercado disponibles. Los instrumentos no valuados se excluyen y se indican en su detalle.

Todos los importes usarán `BigDecimal` y conservarán la escala/precisión de origen, sin redondeo intermedio. El porcentaje, cuya división puede ser periódica, se redondeará a 8 decimales con `HALF_EVEN`, según decisión aprobada para esta feature. Los datos persistidos inconsistentes necesarios para un cálculo (por ejemplo, precio ausente en una compra/venta de instrumento `FILLED`) no se reemplazarán silenciosamente por cero: se reportarán como `InvalidPortfolioDataException`.

La implementación de `calculateHoldings` filtra órdenes `FILLED`, suma compras y resta ventas por instrumento con aritmética entera exacta, y omite resultados netos cero. Las pruebas cubren también que `NEW`, `CANCELLED`, `REJECTED` y los movimientos de efectivo no alteran las tenencias.

`calculateCashSummary` procesa ingresos/egresos `FILLED` usando `size`, resta compras `FILLED`, suma ventas `FILLED` y descuenta las compras `NEW` del disponible mientras las conserva como reserva. Las órdenes canceladas o rechazadas dejan de reservar; las compras ejecutadas pasan a reflejar el precio efectivo. Si una operación que afecta al efectivo carece de precio, lanza `InvalidPortfolioDataException`.

`calculateValuation` multiplica la cantidad distinta de cero y con signo por un precio de cierre positivo; si el precio falta o no es positivo, retorna `UNAVAILABLE` sin monto de mercado.

`calculatePerformance` suma los ingresos de ventas `FILLED`, resta el costo total de compras `FILLED` y agrega el valor de mercado actual; calcula el porcentaje solo si el costo de compras es positivo, con escala 8 y `HALF_EVEN`.

`calculatePortfolio` ensambla el resultado inmutable a partir de esos cálculos, conserva instrumentos no valuados en el detalle y los excluye del total, ordena el detalle por ID y falla explícitamente ante metadatos de instrumento faltantes. Una cantidad neta negativa es válida y aporta una valuación negativa al total.

### Límite respecto a ventas pendientes

Las acciones de una venta `NEW` siguen formando parte de las acciones conservadas porque la orden todavía no está ejecutada. La consulta no valida ni impide una venta futura y no calcula ni expone acciones vendibles; esa regla corresponde al caso de uso de envío de órdenes, fuera del alcance de esta feature.
Aceptar cantidades negativas en el historial de lectura no modifica la regla original que exige rechazar nuevas ventas sin acciones suficientes.

## Consultas de persistencia

Se consultarán proyecciones con columnas explícitas, no grafos de entidades ni `SELECT *`. Se mapearán los nombres físicos de PostgreSQL en minúsculas (`userid`, `accountnumber`, `instrumentid`); no se cambiará el esquema.

1. **Validar la pareja de cuenta (`RF-1`):** `SELECT EXISTS` sobre `users`, filtrando simultáneamente por `id` y `accountnumber`. No seleccionar email. Si no existe coincidencia, el puerto devuelve “no encontrado”; no se confunde con un portfolio vacío.
2. **Leer movimientos (`RF-2`, `RF-3`, `RF-6`, `RF-7`):** seleccionar de `orders` `instrumentid`, `size`, `price`, `side`, `status` y `datetime`, junto con `ticker`, `name` y `type` mediante una unión con `instruments`. Filtrar por `userid`, movimientos `FILLED` pertinentes y compras `NEW` necesarias para calcular reservas. Las ventas `NEW` no modifican las tenencias y no son necesarias en la respuesta de esta funcionalidad. `CANCELLED` y `REJECTED` se excluyen de los cálculos; al dejar de estar `NEW`, una compra pendiente deja de reservar efectivo. `CASH_IN`/`CASH_OUT` aportan su importe de `size`; compras y ventas usan `price × size`.
3. **Leer último precio (`RF-4`, `RF-5`, `RF-6`, `RF-7`):** para cada instrumento, elegir el `close` positivo más reciente entre filas fechadas, con desempate por ID mayor. Si no existe, evaluar `previousclose` de la fila fechada más reciente, con el mismo desempate; usarlo solo si es positivo. La fecha asociada en este último caso identifica la fila, no la fecha exacta de ese precio. Si tampoco es válido, conservar el dato no disponible; si no hay fila fechada, no devolver precio. Para RF-7 leer el `previousclose` de la misma fila solo cuando no se usó una alternativa. Registrar una advertencia cuando se use un cierre histórico o `previousclose`, sin incluir importes.
4. **Total (`RF-5`):** calcularlo en dominio usando el efectivo derivado de movimientos y las valuaciones válidas; no pedir a SQL que aplique reglas de negocio.

Se descarta agregar tablas/campos, índices o constraints: no son necesarios para esta primera lectura y requerirían aprobación. También se descarta cargar todas las entidades y navegar relaciones lazy: puede recuperar datos innecesarios y producir consultas N+1. Se eligen proyecciones nativas de Spring Data JPA para mantener el stack aprobado, hacer explícitos los nombres físicos y evitar hidratar entidades de escritura. Se descarta trasladar todos los cálculos a agregaciones SQL: haría más difícil probar las reglas de negocio como funciones puras. La lectura de movimientos trae los campos mínimos de la historia necesaria para el cálculo; no se pagina un conjunto cuyo total completo define el portfolio.

## Patrones y decisiones técnicas

- **Puertos y adaptadores:** REST es adaptador de entrada; JPA/PostgreSQL es adaptador de salida; el caso de uso depende del puerto, no de ambos detalles.
- **Caso de uso:** `GetPortfolioUseCase` coordina una operación de consulta; no se introduce CQRS, bus de eventos ni un módulo adicional para una sola lectura.
- **Calculadora stateless:** el caso de uso crea `PortfolioCalculator`, que no tiene dependencias ni estado; se evita acoplar la lógica pura a Spring o agregar configuración de beans sin necesidad.
- **Proyecciones inmutables:** aislar el modelo de lectura de entidades JPA y DTOs REST. No se agrega una capa de mapeo genérica.
- **Funciones puras:** probar aritmética y reglas sin Spring ni base de datos. Se descarta colocarlas en el controlador o en consultas SQL.
- **Errores explícitos:** request incompleto/inválido → 400; usuario/cuenta inexistente o no asociado → 404; fallo de persistencia o dato persistido inconsistente → 500 genérico, nunca respuesta parcial con importes inventados.
- **Sin dependencias nuevas:** usar Spring MVC, Spring Data JPA, JUnit, Mockito, MockMvc y Testcontainers ya declarados.
- **Contrato REST:** usar la ruta GET acordada y documentar en OpenAPI el ID y número de cuenta requeridos. El porcentaje de rendimiento acumulado se expone como `performance`, sin el nombre anterior `accumulatedReturnPercentage`; `dailyReturn` es el importe diario estimado en pesos o `null`. Para una valuación no disponible, devolver importes `null` y `valuationStatus: UNAVAILABLE`, nunca cero. No agregar campos de negocio como acciones vendibles o efectivo reservado por separado.

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
- **DEBUG:** duración y cantidad de filas de cada lectura, sin parámetros SQL ni valores financieros. Los errores 400/404 no necesitan stack trace ni log de nivel WARN.
- **WARN:** precio faltante, cero o negativo, con identificador del instrumento y fecha si existe; emitir como máximo una advertencia por instrumento por consulta. No incluir datos de usuario.
- **WARN:** uso de cierre histórico o `previousClose` como alternativa, con identificador del instrumento y fecha del registro seleccionado; no registrar importes.
- **ERROR:** fallo de PostgreSQL o dato persistido inconsistente que impida calcular. Registrar la excepción y el `request_id` generado para la petición; omitir datos de la request y no ocultar el error.
- No habilitar logs de parámetros SQL de Hibernate en entornos que puedan contener datos de usuario o financieros.

La implementación registra el resumen INFO de éxito en el controlador, la advertencia WARN por instrumento no valuado en el caso de uso, las duraciones/filas DEBUG en el adaptador y los errores tipados ERROR junto con la excepción en `PortfolioExceptionHandler`. Los tests verifican niveles, contenido mínimo, presencia del `request_id` en el contexto de la petición y ausencia de parámetros de cuenta/usuario en los mensajes agregados.

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

- `PortfolioReadPortTest`: una respuesta vacía del puerto representa cuenta inexistente; una respuesta presente con movimientos y precios vacíos representa cuenta existente sin actividad.
- `GetPortfolioUseCaseTest`: datos del puerto convertidos correctamente, pareja usuario/cuenta inexistente como `PortfolioNotFoundException`, propagación de errores de lectura/dominio e instantánea sin acciones.
- `PortfolioControllerTest` con MockMvc: query faltante/vacía/sobre 20 caracteres y `userId` no entero/no positivo → 400; ruta sin segmento de usuario o usuario/cuenta inexistente → 404; dato inconsistente/fallo técnico → 500 genérico sin mensajes internos; serialización de valores no disponibles sin sustituirlos por cero; respuesta correcta para cuenta sin posiciones.

### Integración PostgreSQL

`UserEntityIT`, `PortfolioReadRepositoryIT` y `PortfolioReadAdapterIT` usarán Testcontainers PostgreSQL y las migraciones reales, sin H2. `UserEntityIT` y `PortfolioReadRepositoryIT` verificarán:

- Mapeo de `users.id` y `users.accountnumber`, sin seleccionar email.
- Coincidencia exacta de ID con número de cuenta; una pareja incorrecta o inexistente no coincide.
- Consulta de movimientos: incluir `FILLED` pertinentes y compras `NEW`, excluir órdenes `CANCELLED`/`REJECTED` y ventas `NEW`, y limitar la lectura al usuario consultado.
- Consulta de cotizaciones: último `close` positivo por fecha e ID; alternativa `previousClose` de la última fila fechada; ausencia de precio utilizable y exclusión de filas sin fecha.

`PortfolioReadAdapterIT` completará las verificaciones de consulta y mapeo en las tareas posteriores:

- Mapeo físico de columnas PostgreSQL en minúsculas.
- Inclusión/exclusión de estados `FILLED`, `NEW`, `CANCELLED` y `REJECTED` en el mapeo integral.
- Movimientos de efectivo e instrumentos con órdenes del usuario correcto, sin incluir los de otro usuario.
- Selección de fecha más reciente y último `id` para valores del mismo día.
- Ausencia, cero y precio negativo sin fallos de consulta ni valores por defecto.

`PortfolioReadAdapterTest` verifica con dobles que los errores técnicos se propagan, que se advierte el uso de una alternativa sin registrar importes y que el adaptador declara la transacción de solo lectura con `REPEATABLE_READ`. `PortfolioControllerIT` comprueba por HTTP real la valuación con ambas alternativas, una posición sin precio, el rendimiento diario y su contrato OpenAPI.

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
