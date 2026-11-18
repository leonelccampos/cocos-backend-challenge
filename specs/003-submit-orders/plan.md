# Plan de implementación — Envío y cancelación de órdenes

## Alcance y restricciones

Diseñar la implementación de [la spec de órdenes](./spec.md) y la ampliación de
[la spec de portfolio](../001-get-portfolio/spec.md), respetando
[la constitución](../../docs/constitution.md) y
[las decisiones del proyecto](../../PROJECT_SPEC.md). La secuencia de trabajo detallada
se definirá posteriormente en las tareas de la iniciativa.

- La primera etapa será actualizar y refactorizar el dominio de disponibilidad del
  portfolio e incorporar `availableQuantity` a su respuesta.
- No se simula el mercado ni se ejecutan órdenes `LIMIT`. No se agrega autenticación.
- Se mantiene el stack aprobado y se preservan las tablas y datos existentes. Se aprobó
  agregar una tabla para idempotencia mediante una nueva migración; ningún otro cambio
  de esquema está autorizado.
- Se usan `BigDecimal` para dinero y cantidades enteras para acciones. No se guarda
  el monto solicitado como dato de la orden.

## Límites y responsabilidades

Las flechas de dependencia apuntan de los adaptadores hacia aplicación y dominio.
El dominio no depende de Spring, HTTP, JPA, SQL ni de tipos de aplicación. Sus tipos
deben representar conceptos con significado de negocio: entidades, objetos de valor,
agregados y servicios de dominio cuando sean necesarios. DTOs, proyecciones, contratos
HTTP y modelos de lectura permanecen fuera del dominio.

| Capa y componente | Responsabilidad |
|---|---|
| Dominio: orden | Entidad de dominio que representa identidad, datos y estado; gobierna su transición de `NEW` a `CANCELLED` y las invariantes de su ciclo de vida. Puede representar un rechazo sin precio o cantidad cuando la spec lo permite. |
| Dominio: efectivo y posición | Conceptos de dominio con comportamiento para calcular efectivo disponible, tenencia y acciones disponibles a partir de movimientos y reservas. La disponibilidad vendible nunca es negativa; una tenencia histórica sí puede serlo. |
| Dominio: valores y reglas de orden | Modelar precios, cantidades, lados y estados según su significado; calcular acciones enteras y validar los recursos mediante el concepto que gobierna cada regla. Usar servicios de dominio solo para reglas que no pertenezcan naturalmente a una entidad u objeto de valor. |
| Dominio: valuación de portfolio | Mantener separadas las reglas de valuación y rendimiento de las reglas de disponibilidad operativa. Revisar la responsabilidad actual de `PortfolioCalculator` sin convertir automáticamente los resultados de lectura en agregados ni mover reglas a DTOs. |
| Aplicación: envío de orden | Coordinar validación de la solicitud, idempotencia, lectura de datos necesarios, reglas del dominio y persistencia. Tras confirmar la transacción, comunicar un rechazo persistido como error de negocio, independiente de HTTP. |
| Aplicación: cancelación de orden | Coordinar identificación, lectura y cancelación mediante la orden de dominio; asegurar que el cambio de estado y la liberación de la reserva sean coherentes. |
| Aplicación: consulta de portfolio | Orquestar la consulta y emplear las mismas reglas de efectivo y posición; la representación de lectura incluye `quantity` y `availableQuantity` sin alterar los demás campos acordados. |
| Aplicación: puertos y contratos | Definir las capacidades de lectura, escritura, idempotencia y delimitación transaccional necesarias para los casos de uso. Mantener comandos, resultados y errores de aplicación fuera del dominio; evitar interfaces o clases adicionales que no aporten una necesidad concreta. |
| Adaptador REST | Recibir y validar solicitudes; mapearlas al caso de uso y traducir sus resultados y errores a los contratos de las specs. No decide reglas de negocio ni usa directamente persistencia. |
| Adaptadores de persistencia | Implementar los puertos, mapear datos al dominio y viceversa, gestionar consultas, migración, bloqueo y transacción con PostgreSQL. Las entidades JPA y proyecciones no son entidades de dominio. |
| Configuración | Conectar los casos de uso, puertos y adaptadores sin introducir dependencias del dominio a infraestructura. |

`Portfolio` es actualmente un resultado inmutable de lectura, no una raíz de agregado
por el solo hecho de existir. El modelo operativo podrá componerse de efectivo y posiciones
reconstruidos según el caso de uso; no se exige materializar una entidad global de
portfolio ni decidir un agregado adicional sin una invariante que lo justifique.
`Order` gobierna las transiciones de la orden; la consistencia entre órdenes y
disponibilidad de una misma cuenta requiere además la coordinación transaccional
descrita abajo.

## Portfolio y disponibilidad compartida

1. Reutilizar una sola definición de movimientos y reservas para cartera y órdenes.
   Extraer las reglas comunes actuales de `PortfolioCalculator` hacia conceptos de
   dominio con comportamiento, sin duplicarlas en SQL ni en los controladores.
2. Para consultar el portfolio, leer también las ventas `NEW`. La cantidad conservada
   sigue siendo compras `FILLED` menos ventas `FILLED`; `availableQuantity` descuenta
   las ventas `NEW` del instrumento y tiene mínimo cero.
3. Preservar el resto del contrato y de los cálculos del portfolio, incluida la
   presentación con signo de las posiciones históricas negativas, el valor de cuenta,
   sus rendimientos y la omisión de posiciones netas cero.
4. Para una compra, reconstruir únicamente el efectivo a partir de los movimientos
   pertinentes y las compras `NEW`. Para una venta, reconstruir únicamente la posición
   del instrumento solicitado a partir de sus operaciones `FILLED` y ventas `NEW`.
   Ninguna orden requiere valorar todos los instrumentos ni calcular rendimientos.
5. Leer los movimientos necesarios bajo la transacción de escritura descrita abajo;
   no usar la respuesta HTTP ni una instantánea del portfolio obtenida antes del
   bloqueo como fuente de disponibilidad.

## Contratos de entrada y salida

- Creación: `POST /api/v1/users/{userId}/orders`, con `Idempotency-Key` obligatorio y
  los datos definidos en RF-1 y RF-2.
- Cancelación: `PATCH /api/v1/users/{userId}/orders/{orderId}`, con
  `status: CANCELLED`.
- El adaptador HTTP traduce solicitudes inválidas a 400, referencias inexistentes a
  404 y conflictos a 409. Las órdenes aceptadas responden 201; las canceladas, 200.
  Los rechazos de negocio persistidos responden 422 con `ProblemDetail`, motivo y
  orden, conforme a RF-7. Los errores técnicos no se convierten en rechazos de negocio.
- El caso de uso decide el rechazo como error de negocio; el adaptador solo lo traduce
  a HTTP. La orden rechazada se confirma antes de propagar el error: no debe lanzarse
  una excepción que revierta accidentalmente la transacción de persistencia.
- El motivo de rechazo se informa al cliente y se registra sin incluir importes
  solicitados ni claves de idempotencia. No se registran datos personales innecesarios.
- Documentar el contrato aprobado en OpenAPI y actualizar los ejemplos del proyecto
  durante la implementación, sin añadir dependencias.

## Flujo de envío

1. Validar el formato de los identificadores, campos, rangos independientes de
   cotizaciones, exclusividad de `size`/`amount` y clave. Las solicitudes inválidas
   no crean orden ni consumen la clave.
2. Dentro de la transacción, verificar y bloquear al usuario y consultar la clave
   antes de volver a evaluar datos mutables. Si ya existe, comparar la solicitud
   válida normalizada y devolver el resultado original o el conflicto 409.
3. Para una solicitud nueva, verificar que el instrumento exista y sea apto y, para
   `MARKET`, obtener el último `close` positivo fechado según RF-4. Una referencia
   inexistente no crea orden ni consume la clave. No usar `previousClose` para ejecutar.
4. Si hay monto y precio aplicable, calcular las acciones enteras y validar que sean
   al menos una y estén dentro del rango; esa validación precede a la de solvencia.
   Si falta el `close` de una `MARKET`, registrar el rechazo conforme a RF-4.
5. Reconstruir solo la disponibilidad requerida. Una compra por monto compara el
   monto bruto solicitado con el efectivo disponible; una venta por monto compara las
   acciones enteras calculadas con las disponibles. Las órdenes por cantidad se
   validan contra el costo efectivo o las acciones, según el lado.
6. Persistir la orden `FILLED`, `NEW` o `REJECTED` y el resultado original asociado
   a la clave en una misma transacción. Confirmar antes de producir la respuesta o
   propagar el error de negocio correspondiente al rechazo.

## Cancelación y consistencia

- El caso de uso adquiere el mismo bloqueo por usuario usado en creación antes de
  leer y cambiar la orden. Comprueba la asociación con el usuario y delega en el
  dominio la transición permitida de `NEW` a `CANCELLED`.
- Al persistir el nuevo estado, los cálculos posteriores dejan de considerar la
  reserva de esa orden. No se registran movimientos adicionales de efectivo ni
  se alteran tenencias ejecutadas. Una orden ya cancelada responde 409.
- Usar una transacción corta de lectura/escritura por operación, con aislamiento
  `READ_COMMITTED` y bloqueo exclusivo de la fila del usuario antes de leer
  movimientos y clave. La lectura posterior al bloqueo debe observar los cambios
  confirmados por la operación anterior. La consulta de portfolio conserva su
  instantánea de lectura consistente.
- Todas las rutas de escritura de esta iniciativa que afecten disponibilidad
  deben seguir el mismo protocolo de bloqueo. El bloqueo no protege frente a
  escritores externos que lo ignoren; no se promete esa garantía fuera del
  protocolo. Las operaciones de usuarios distintos no comparten ese bloqueo.
- Si falla la persistencia, revertir tanto la orden como el registro idempotente.
  Evitar llamadas externas o trabajo ajeno a la operación mientras se mantiene el
  bloqueo. No agregar simulación de mercado ni un proceso asíncrono.

## Persistencia de idempotencia

- Crear una tabla nueva mediante Flyway con unicidad de `userId` y clave. Mantener
  las tablas de referencia sin cambios. La tabla relaciona la clave con una huella
  de los datos de negocio validados y con el código y cuerpo originales de respuesta.
- Normalizar los valores antes de calcular la huella, de manera que el orden JSON y
  escalas numéricas equivalentes no cambien la comparación. No persistir el monto
  bruto como dato de la orden ni registrar en logs el cuerpo o la clave. Una huella
  no debe tratarse como cifrado ni exponerse en logs o respuestas.
- Guardar orden, huella y resultado en la misma transacción, incluyendo respuestas
  422 de órdenes `REJECTED`. Las validaciones 400 y referencias inexistentes 404
  anteriores a la creación no consumen la clave.
- En un reintento válido, devolver la instantánea original de respuesta aunque
  cambien la disponibilidad, la cotización o el estado actual de la orden. Nunca
  recalcular la operación ni crear otra orden. Con la misma clave y datos distintos,
  devolver 409. Las claves son independientes entre usuarios y no vencen en esta
  versión.

## Verificación prevista

- Dominio: efectivo y reservas, cantidad conservada y disponible, posiciones
  negativas, compras y ventas por monto/cantidad, validación de lote mínimo,
  estados y cancelación. Comprobar el caso de monto bruto mayor al saldo aunque
  el costo efectivo alcance.
- Portfolio: preservar cálculos y contrato previo, con pruebas para
  `availableQuantity`, ventas `NEW`, canceladas y ejecutadas; incluir una posición
  negativa y una posición con reserva.
- Aplicación y HTTP: validaciones y códigos acordados, `ProblemDetail` con la orden
  y el motivo, ausencia de autenticación, y rechazo persistido antes de emitir 422.
- PostgreSQL real con Testcontainers: migración, atomicidad, reintentos idénticos y
  conflictos, replay después de cancelar, errores sin consumo de clave y fallos con
  rollback. Probar concurrencia para compras, ventas, cancelación y duplicados,
  incluyendo que usuarios distintos puedan avanzar independientemente.
- Verificar solicitudes reales en Postman para envío, rechazo, replay, conflicto,
  cancelación y consulta del portfolio. Si la API está detenida, levantarla para
  esta verificación conforme a las reglas del proyecto.
- Ejecutar `./gradlew test`, `./gradlew integrationTest` y `./gradlew spotlessCheck`;
  ampliar a `./gradlew check` y `./gradlew build` cuando corresponda. Informar los
  comandos y resultados reales. La integración requiere Docker y PostgreSQL real.

Este plan no implementa código ni reemplaza la posterior definición de tareas.
