# Tareas de implementación — Get Portfolio

Las tareas están ordenadas por dependencia. Cada una debe poder completarse en un bloque de hasta 30 minutos; si una se amplía, dividirla antes de continuar. No agregar dependencias ni modificar el esquema.

## Contratos y persistencia

- [x] **T01 (20 min) — Definir los modelos de resultado del dominio.** **RF:** RF-3, RF-4, RF-5, RF-6.
  - **Hecho cuando:** `Portfolio` y `PortfolioInstrument` representan cantidades, importes y estados de valuación no disponible sin usar cero como sustituto.

- [x] **T02 (20 min) — Definir el modelo de lectura de aplicación.** **RF:** RF-2, RF-3, RF-4, RF-6.
  - **Hecho cuando:** `PortfolioReadData` agrupa de forma inmutable los movimientos y precios obtenidos por el puerto sin convertirse en un tipo del dominio.

- [x] **T03 (20 min) — Definir el puerto de lectura del portfolio.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-6.
  - **Depende de:** T02.
  - **Hecho cuando:** `PortfolioReadPort` permite consultar la pareja usuario/cuenta y obtener los datos de lectura sin exponer tipos HTTP o JPA; distingue cuenta ausente de una lectura vacía.

- [x] **T04 (25 min) — Mapear el usuario y validar la pareja de cuenta.** **RF:** RF-1.
  - **Depende de:** T03.
  - **Hecho cuando:** la consulta filtra simultáneamente por ID y número de cuenta, mapea `accountnumber` y las pruebas distinguen coincidencia de cuenta inexistente/no asociada.

- [x] **T05 (25 min) — Consultar los movimientos pertinentes.** **RF:** RF-2, RF-3, RF-6.
  - **Depende de:** T02, T03.
  - **Hecho cuando:** la consulta obtiene los campos necesarios de `orders` e `instruments`, incluye movimientos `FILLED` y compras `NEW`, y excluye las ventas `NEW` de los cálculos del portfolio.

- [x] **T06 (25 min) — Consultar el último precio por instrumento.** **RF:** RF-4, RF-5, RF-6.
  - **Depende de:** T02, T03.
  - **Hecho cuando:** la consulta aplica la selección de precios aprobada en RF-4; una ausencia de precio utilizable se conserva como no disponible.

- [x] **T07 (25 min) — Implementar el adaptador de lectura transaccional.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-6.
  - **Depende de:** T04, T05, T06.
  - **Hecho cuando:** el adaptador implementa el puerto, mapea las proyecciones y ejecuta la lectura en una transacción de solo lectura con `REPEATABLE_READ`; los errores técnicos se propagan sin convertirse en resultados vacíos.

## Cálculos de dominio

- [x] **T08 (25 min) — Calcular las tenencias ejecutadas.** **RF:** RF-3.
  - **Depende de:** T01, T02.
  - **Hecho cuando:** pruebas unitarias confirman compras menos ventas `FILLED`, ignoran estados no ejecutados, omiten cantidad neta cero y conservan la cantidad ante una venta `NEW`.

- [x] **T09 (25 min) — Calcular efectivo disponible y reservado.** **RF:** RF-2.
  - **Depende de:** T01, T02.
  - **Hecho cuando:** pruebas unitarias verifican `size` para `CASH_IN`/`CASH_OUT`, `price × size` para operaciones ejecutadas y reserva de compras `NEW`, incluida su liberación o ejecución; datos imprescindibles inconsistentes producen `InvalidPortfolioDataException`.

- [x] **T10 (25 min) — Calcular la valuación de cada instrumento.** **RF:** RF-4.
  - **Depende de:** T01, T02.
  - **Hecho cuando:** pruebas unitarias verifican cantidad por precio para precios positivos y estado no disponible para precio ausente, cero o negativo.

- [x] **T11 (25 min) — Calcular el rendimiento acumulado.** **RF:** RF-6.
  - **Depende de:** T08, T10.
  - **Hecho cuando:** pruebas unitarias verifican resultado y porcentaje con ventas parciales, y porcentaje no disponible cuando el costo de compras es cero.

- [x] **T12 (25 min) — Calcular el valor total del portfolio.** **RF:** RF-5.
  - **Depende de:** T09, T10.
  - **Hecho cuando:** pruebas unitarias verifican efectivo disponible más reservado más valuaciones disponibles, excluyendo del total los instrumentos no valuados.

## Aplicación y contrato HTTP

- [x] **T13 (20 min) — Orquestar la consulta de portfolio.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T03, T07, T08, T09, T10, T11, T12.
  - **Hecho cuando:** las pruebas del caso de uso verifican la lectura, el cálculo, `PortfolioNotFoundException` ante cuenta ausente y la propagación de errores de lectura/dominio sin depender de Spring ni de JPA.

- [x] **T14 (20 min) — Validar los parámetros requeridos de la solicitud.** **RF:** RF-1.
  - **Hecho cuando:** `PortfolioRequestTest` confirma que `accountNumber` es requerido, no vacío y admite hasta 20 caracteres sin restringirlo a dígitos; la validación del ID en HTTP se verificará con el controlador en T15.

- [x] **T15 (20 min) — Exponer la ruta GET acordada.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T13, T14.
  - **Hecho cuando:** `GET /api/v1/users/{userId}/portfolio?accountNumber=...` delega al caso de uso y no contiene cálculos ni consultas.

- [x] **T16 (20 min) — Mapear el resultado al contrato REST.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T01, T15.
  - **Hecho cuando:** pruebas de serialización confirman que los valores no disponibles se envían como `null` con `valuationStatus: UNAVAILABLE`, nunca como cero, y que no se exponen campos de reservas no aprobados.

- [x] **T17 (20 min) — Traducir errores de aplicación a respuestas HTTP.** **RF:** RF-1.
  - **Depende de:** T13, T15.
  - **Hecho cuando:** pruebas HTTP confirman 404 para usuario/cuenta inexistente o no asociado, 500 genérico para errores internos conocidos, ausencia de detalles internos en la respuesta y que ningún error se convierte en una respuesta exitosa.

## Protección de datos y verificación

- [x] **T18 (20 min) — Incorporar logs y correlación por request.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T07, T13, T15.
  - **Hecho cuando:** los puntos de entrada, lecturas y errores usan los niveles definidos en el plan, comparten un `request_id` y no registran identificadores de usuario, números de cuenta, datos personales ni importes.

- [x] **T19 (25 min) — Completar la verificación de integración PostgreSQL.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T07, T13, T15, T16, T17.
  - **Hecho cuando:** `PortfolioReadAdapterIT` pasa con PostgreSQL real y cubre asociación de cuenta, estados de órdenes, movimientos del usuario correcto y desempate de precios por ID.

- [x] **T20 (25 min) — Ejecutar la verificación final de la feature.** **RF:** RF-1, RF-2, RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T01–T19.
  - **Hecho cuando:** pasan las pruebas unitarias, HTTP e integración pertinentes, sin instalar dependencias ni alterar esquema o datos de referencia.

## Posiciones netas negativas

- [x] **T21 (25 min) — Soportar posiciones netas negativas.** **RF:** RF-3, RF-4, RF-5, RF-6.
  - **Hecho cuando:** se conservan cantidades negativas, la valuación y el total usan su signo, el rendimiento mantiene la fórmula aprobada y las cantidades cero se omiten.
- [ ] **T22 (25 min) — Verificar posiciones negativas y documentar dudas.** **RF:** RF-3, RF-4, RF-5, RF-6.
  - **Depende de:** T21.
  - **Hecho cuando:** pasan las pruebas unitarias y de integración y se verifica el endpoint mediante Postman o se informa el bloqueo; [preguntas.md](../../preguntas.md) recoge ejemplos y decisiones para consultar a los owners.
  - **Estado:** pruebas unitarias y de integración aprobadas; valores verificados por HTTP local. El 16/11/2026 la solicitud de posición negativa en Postman respondió 200; queda pendiente corroborar sus valores en la respuesta de Postman.

- [x] **T23 (20 min) — Renombrar el porcentaje de rendimiento en HTTP.** **RF:** RF-6.
  - **Hecho cuando:** el JSON expone el porcentaje como `performance`, no incluye `accumulatedReturnPercentage` y conserva los valores numéricos y `null`.
  - **Verificación:** pruebas unitarias, integración y HTTP local aprobadas. El 16/11/2026 se ejecutaron solicitudes en Postman; queda pendiente corroborar el campo en la respuesta de posición negativa desde la interfaz.

## Precios históricos

- [ ] **T24 (25 min) — Aplicar la selección de precios de RF-4.**
  - **Hecho cuando:** se selecciona el último `close` positivo fechado y, si no existe, el `previousClose` positivo del registro fechado más reciente; sin precio utilizable se conserva `UNAVAILABLE`. Las alternativas se registran sin importes.
  - **Verificación:** pruebas unitarias y de integración PostgreSQL, y solicitudes desde Postman; informar cualquier bloqueo.
  - **Estado:** pruebas unitarias, integración PostgreSQL y HTTP real aprobadas. Queda pendiente comprobar los casos específicos de alternativas de precio desde Postman.

## Rendimiento diario estimado

- [ ] **T25 — Implementar y verificar RF-7.**
  - **Hecho cuando:** el endpoint expone `dailyReturn` en pesos o `null` con los criterios, zona horaria y limitaciones de RF-7.
  - **Verificación:** pruebas unitarias, integración PostgreSQL y solicitudes en Postman; informar cualquier bloqueo.
  - **Estado:** pruebas unitarias, integración PostgreSQL, HTTP y OpenAPI aprobadas. El 16/11/2026 Postman devolvió `dailyReturn: 100.00` para la posición sintética y `null` para las posiciones con cierres históricos; quedan pendientes otros casos límite desde Postman.
