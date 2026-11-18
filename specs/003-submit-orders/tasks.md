# Tareas de implementación — Envío y cancelación de órdenes

El alcance y los criterios completos están en [la spec de órdenes](./spec.md),
[el plan](./plan.md) y [la spec de portfolio](../001-get-portfolio/spec.md).
Cada tarea agrupa como máximo tres subtareas; los requisitos de las specs siguen
siendo obligatorios aunque no se repitan aquí.

## T01 — Crear una orden

- [x] **T01.1 — Unificar la disponibilidad y actualizar el portfolio.**
  - **RF:** órdenes RF-5 y RF-6; portfolio RF-2, RF-3 y RF-5.
  - **Hecho cuando:** efectivo y posición comparten reglas de dominio para movimientos
    y reservas; el portfolio incluye ventas `NEW` y expone `availableQuantity` con
    mínimo cero, sin alterar la cantidad conservada, las posiciones negativas ni
    los demás cálculos y campos acordados. Las compras y ventas pueden reconstruir
    solo la disponibilidad que necesitan, sin valorar todo el portfolio.
  - **Verificación:** pruebas unitarias, HTTP y de integración PostgreSQL para
    movimientos, reservas, estados, posiciones negativas y regresión del portfolio.

- [x] **T01.2 — Implementar el envío transaccional e idempotente.**
  - **Depende de:** T01.1.
  - **RF:** órdenes RF-1–RF-7 y RF-9.
  - **Hecho cuando:** el dominio y el caso de uso aplican las validaciones, conversión
    de monto, selección de `close`, disponibilidad y estados acordados. Se bloquea
    al usuario antes de consultar la clave y la disponibilidad; orden y resultado
    original se persisten atómicamente, incluidos los rechazos, usando la tabla de
    idempotencia autorizada. Los reintentos no recalculan la operación, los datos
    equivalentes se normalizan y una clave reutilizada con datos distintos produce
    conflicto. Los errores técnicos no se convierten en rechazos de negocio.
  - **Verificación:** pruebas de dominio, aplicación e integración PostgreSQL para
    límites y precedencia de validaciones, compras y ventas por cantidad o monto,
    precios ausentes, reservas, rollback, replay, conflictos y concurrencia por
    usuario y entre usuarios distintos.

- [x] **T01.3 — Exponer y verificar `POST /api/v1/users/{userId}/orders`.**
  - **Depende de:** T01.2.
  - **RF:** órdenes RF-1–RF-7 y RF-9.
  - **Hecho cuando:** el endpoint valida la entrada y `Idempotency-Key`, traduce
    los resultados a 201, 400, 404, 409 o 422 según corresponda y devuelve los
    campos acordados. El 422 incluye `ProblemDetail`, motivo estable y orden
    rechazada ya confirmada; los logs no contienen el monto solicitado ni la clave.
    OpenAPI y los ejemplos del proyecto describen el contrato, sin autenticación.
  - **Verificación:** MockMvc e integración HTTP con PostgreSQL; comprobar en
    Postman envío, rechazo, replay y conflicto, además del portfolio actualizado.

## T02 — Cancelar una orden

- [ ] **T02.1 — Implementar la cancelación y liberación de reservas.**
  - **Depende de:** T01.1 y T01.2.
  - **RF:** órdenes RF-5, RF-6 y RF-8; portfolio RF-2 y RF-3.
  - **Hecho cuando:** bajo el mismo bloqueo por usuario y transacción de escritura,
    se verifica la asociación y la orden de dominio solo transiciona de `NEW` a
    `CANCELLED`. La reserva deja de afectar la disponibilidad sin crear movimientos
    ni modificar tenencias ejecutadas; las órdenes inexistentes, ajenas o en otro
    estado conservan sus efectos.
  - **Verificación:** pruebas de dominio, aplicación e integración PostgreSQL para
    compras y ventas, estados no cancelables, asociación y concurrencia entre
    creación y cancelación.

- [ ] **T02.2 — Exponer y verificar `PATCH /api/v1/users/{userId}/orders/{orderId}`.**
  - **Depende de:** T02.1.
  - **RF:** órdenes RF-8 y RF-9.
  - **Hecho cuando:** el endpoint exige `status: CANCELLED`, no requiere autenticación
    ni clave de idempotencia y responde 200, 400, 404 o 409 según la spec, con la
    orden actualizada en caso de éxito. OpenAPI y los ejemplos documentan el contrato.
  - **Verificación:** MockMvc e integración HTTP con PostgreSQL; comprobar en Postman
    cancelación, repetición y liberación de reservas mediante el portfolio.

- [ ] **T02.3 — Verificar la iniciativa completa.**
  - **Depende de:** T01.3 y T02.2.
  - **RF:** órdenes RF-1–RF-9; portfolio RF-2 y RF-3.
  - **Hecho cuando:** el replay de una creación tras cancelar devuelve la respuesta
    original, la concurrencia no sobreasigna recursos y todas las verificaciones
    de ambas tareas están completas. La única modificación de esquema es la tabla
    de idempotencia autorizada y no se añadieron dependencias.
  - **Verificación:** ejecutar `./gradlew test`, `./gradlew integrationTest` y
    `./gradlew spotlessCheck`; ampliar a `./gradlew check` y `./gradlew build` cuando
    corresponda. Completar las solicitudes reales en Postman; si la API está detenida,
    levantarla. Informar comandos, resultados y bloqueos sin marcar como ejecutado
    lo que no se verificó.
