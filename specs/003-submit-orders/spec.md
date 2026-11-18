# Especificación 003 — Envío y cancelación de órdenes

## Contexto y objetivo

Permitir que un usuario envíe órdenes de compra y venta de acciones y cancele órdenes pendientes. Las órdenes `MARKET` se ejecutan inmediatamente al último precio de cierre utilizable; las órdenes `LIMIT` quedan pendientes (`NEW`) hasta que se cancelen. La funcionalidad valida fondos y acciones disponibles para evitar aceptar operaciones que excedan los recursos libres del usuario, sin simular el mercado ni ejecutar órdenes `LIMIT`.

El identificador de usuario permite asociar la orden con una cuenta, pero no acredita autenticación ni identidad.

## Usuarios

- Usuario o cliente de la API que envía una orden para un `userId`.
- Usuario o cliente de la API que solicita cancelar una orden para un `userId`.

## Historias de usuario

- Como usuario, quiero enviar una orden de compra o venta indicando acciones o un monto en pesos, para operar según mis necesidades.
- Como usuario, quiero que una orden de mercado se ejecute al último precio de cierre disponible, para conocer su resultado inmediato.
- Como usuario, quiero dejar una orden límite pendiente al precio que elija, para que quede registrada con ese precio.
- Como usuario, quiero que, al enviar una orden, el sistema valide los pesos o acciones disponibles y me informe en la misma respuesta si fue rechazada.
- Como usuario, quiero cancelar una orden pendiente para volver a disponer de los pesos o las acciones reservados.

## Requisitos funcionales

### RF-1 — Identificación y alcance de la orden

- CUANDO se envíe una orden, EL SISTEMA deberá recibirla mediante `POST /api/v1/users/{userId}/orders`, con `instrumentId`, `side`, `type`, una de las alternativas `size` o `amount`, y `price` para una orden `LIMIT`.
- CUANDO se envíe una orden, EL SISTEMA deberá asociarla al `userId` y al instrumento indicados.
- SI el `userId` o el identificador del instrumento falta, no es un entero o no es positivo, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- SI el `userId` o el instrumento no existen, ENTONCES EL SISTEMA deberá responder HTTP 404 y no crear una orden.
- SI el instrumento existe, pero no es de tipo `ACCIONES`, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- SI el side no es `BUY` ni `SELL`, o el tipo no es `MARKET` ni `LIMIT`, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- CUANDO se envíe una orden, EL SISTEMA no deberá requerir autenticación; el `userId` identifica la cuenta solicitada, pero no se considerará una prueba de identidad.

### RF-2 — Validación de cantidad, monto y precio

- CUANDO se envíe una orden, EL SISTEMA deberá requerir exactamente una alternativa de tamaño: una cantidad entera positiva de acciones o un monto positivo en pesos.
- SI faltan ambas alternativas, se envían ambas, la cantidad no es entera o alguna cantidad o monto es cero o negativo, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- CUANDO se envíe una orden `LIMIT`, EL SISTEMA deberá requerir un precio límite positivo.
- SI el precio límite contiene más de dos decimales o supera $99.999.999,99, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- SI la cantidad indicada o calculada supera 2.147.483.647 acciones, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- SI una orden `MARKET` incluye un precio enviado por el cliente, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- SI el monto solicitado, dividido por el precio de referencia aplicable, da como resultado menos de una acción entera, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden, aun cuando el monto solicitado supere los pesos disponibles.
- SI un dato requerido está mal formado o no cumple las validaciones anteriores, ENTONCES EL SISTEMA deberá responder HTTP 400 y no persistir la solicitud.

### RF-3 — Conversión de monto a acciones

- CUANDO el usuario exprese el tamaño mediante un monto y exista un precio de referencia, EL SISTEMA deberá calcular la cantidad como el máximo entero de acciones cuyo precio total no supere ese monto.
- CUANDO el monto corresponda a una orden `MARKET`, EL SISTEMA deberá usar el `close` positivo seleccionado para su ejecución como precio de referencia para calcular las acciones.
- CUANDO el monto corresponda a una orden `LIMIT`, EL SISTEMA deberá usar exclusivamente el precio límite enviado por el usuario como precio de referencia para calcular las acciones.
- CUANDO se calcule una venta `SELL` por monto, EL SISTEMA deberá comparar las acciones enteras calculadas con las acciones libres disponibles; no deberá comparar el monto bruto solicitado con el valor de mercado de toda la tenencia.
- CUANDO se calcule una compra `BUY` por monto, EL SISTEMA deberá comparar el monto solicitado completo con los pesos disponibles.
- SI el monto solicitado de una compra supera los pesos disponibles y se cumple la validación de al menos una acción, ENTONCES EL SISTEMA deberá persistir la orden como `REJECTED` y responder HTTP 422, aunque el costo efectivo de las acciones calculadas sea menor o igual a los pesos disponibles.

### RF-4 — Órdenes MARKET

- CUANDO se envíe una orden `MARKET`, EL SISTEMA deberá seleccionar el `close` positivo más reciente del instrumento entre los registros con fecha; ante fechas iguales, deberá seleccionar el de mayor ID.
- CUANDO exista un `close` positivo utilizable y haya recursos suficientes, EL SISTEMA deberá registrar el precio usado y ejecutar inmediatamente la orden con estado `FILLED`.
- SI no existe un `close` positivo utilizable, ENTONCES EL SISTEMA deberá persistir la orden como `REJECTED`, responder HTTP 422 y no modificar los pesos ni las tenencias.
- SI una `MARKET` sin `close` fue expresada mediante una cantidad de acciones, EL SISTEMA deberá conservar esa cantidad y registrar el precio como no disponible.
- SI una `MARKET` sin `close` fue expresada mediante un monto, ENTONCES EL SISTEMA deberá persistirla como `REJECTED` con la cantidad (`size`) nula y el precio no disponible, ya que no puede calcularse la cantidad de acciones.
- CUANDO se ejecute una orden `MARKET`, EL SISTEMA no deberá usar `previousClose` como precio de ejecución ni realizar una simulación del mercado.

### RF-5 — Órdenes LIMIT

- CUANDO se envíe una orden `LIMIT` válida y haya recursos suficientes, EL SISTEMA deberá persistirla con el precio límite y estado `NEW`.
- CUANDO una orden `LIMIT` sea aceptada, EL SISTEMA deberá mantenerla en estado `NEW` independientemente de su relación con el último precio de mercado, salvo que se cancele; no deberá simular su ejecución.
- MIENTRAS una orden `LIMIT` de compra permanezca en estado `NEW`, EL SISTEMA deberá reservar el importe `precio límite × cantidad` y excluirlo de los pesos disponibles.
- MIENTRAS una orden `LIMIT` de venta permanezca en estado `NEW`, EL SISTEMA deberá reservar su cantidad de acciones y excluirla de las acciones disponibles para nuevas ventas.

### RF-6 — Disponibilidad de pesos y acciones

- CUANDO se determine el efectivo disponible, EL SISTEMA deberá considerar los ingresos y egresos de efectivo y las operaciones `BUY`/`SELL` en estado `FILLED`.
- CUANDO se contabilice un ingreso `CASH_IN` o egreso `CASH_OUT` en estado `FILLED`, EL SISTEMA deberá sumar o restar, respectivamente, su importe en pesos.
- CUANDO se contabilice una compra o venta en estado `FILLED`, EL SISTEMA deberá restar o sumar, respectivamente, `precio × cantidad` al efectivo.
- CUANDO se determine la cantidad de acciones disponibles para vender, EL SISTEMA deberá considerar las compras y ventas `FILLED` y restar las acciones reservadas por órdenes `SELL` en estado `NEW`.
- SI el cálculo de acciones disponibles para vender es negativo, ENTONCES EL SISTEMA deberá considerar cero acciones disponibles.
- SI una orden válida excede los pesos disponibles para una compra o las acciones disponibles para una venta, ENTONCES EL SISTEMA deberá persistirla en estado `REJECTED`, responder HTTP 422 y no modificar efectivo, tenencias ni reservas.
- CUANDO una orden `MARKET` se ejecute, EL SISTEMA deberá reflejar de inmediato su movimiento en los pesos y las tenencias.
- CUANDO una orden `LIMIT` quede en estado `NEW`, EL SISTEMA no deberá reflejarla como operación ejecutada en los pesos ni en las tenencias; sus recursos reservados sí deberán dejar de estar disponibles.

### RF-7 — Persistencia y respuesta al crear una orden

- CUANDO se acepte una orden `MARKET` o `LIMIT`, EL SISTEMA deberá responder HTTP 201 e incluir la orden creada con el estado `FILLED` o `NEW`, respectivamente.
- CUANDO se rechace una orden válida por una regla de negocio, EL SISTEMA deberá responder HTTP 422 con un `ProblemDetail` que incluya la orden persistida en estado `REJECTED`, un código de motivo estable en inglés y una descripción en español.
- CUANDO se rechace una orden válida por una regla de negocio, EL SISTEMA deberá registrar el motivo del rechazo sin registrar el monto solicitado ni la clave de idempotencia.
- CUANDO se registre una orden, EL SISTEMA deberá generar la fecha y hora de recepción; no deberá aceptar del cliente la fecha y hora persistida.
- CUANDO se persista una orden, EL SISTEMA deberá registrar el usuario, el instrumento, el side, el tipo, la cantidad calculada o indicada cuando pueda determinarse, el precio aplicable cuando esté disponible y el estado resultante.
- CUANDO se devuelva una orden creada, rechazada o cancelada, EL SISTEMA deberá incluir `id`, `userId`, `instrumentId`, `side`, `type`, `size`, `price`, `status` y `datetime`. `size` y `price` podrán ser nulos en un rechazo por falta de cotización, conforme a RF-4.
- CUANDO se persista una orden expresada por monto, EL SISTEMA no deberá almacenar ni devolver el monto solicitado como dato de la orden.
- SI una solicitud no supera una validación de entrada, ENTONCES EL SISTEMA no deberá crear ni persistir una orden.

### RF-8 — Cancelación de órdenes

- CUANDO se solicite cancelar una orden, EL SISTEMA deberá recibir un `PATCH /api/v1/users/{userId}/orders/{orderId}` con `status: CANCELLED`.
- SI falta `status` o se solicita un estado distinto de `CANCELLED`, ENTONCES EL SISTEMA deberá responder HTTP 400 y no modificar la orden.
- CUANDO se solicite cancelar una orden `NEW` asociada al `userId` indicado, EL SISTEMA deberá cambiar su estado a `CANCELLED`, liberar los pesos o las acciones que tenía reservados y responder HTTP 200 con la orden actualizada.
- SI el identificador de la orden o el `userId` falta, no es un entero o no es positivo, ENTONCES EL SISTEMA deberá responder HTTP 400 y no modificar ninguna orden.
- SI la orden o el usuario no existen, o la orden está asociada a otro `userId`, ENTONCES EL SISTEMA deberá responder HTTP 404 y no modificarla.
- SI la orden existe y está asociada al `userId` indicado, pero su estado no es `NEW`, ENTONCES EL SISTEMA deberá responder HTTP 409 y conservar su estado y sus efectos actuales.
- CUANDO se cancele una orden, EL SISTEMA deberá mantener sin cambios las tenencias y los movimientos de efectivo ejecutados previamente.
- CUANDO se solicite una cancelación, EL SISTEMA no deberá requerir autenticación; el `userId` identifica la cuenta solicitada, pero no se considerará una prueba de identidad.

### RF-9 — Idempotencia de creación

- CUANDO se envíe una orden, EL SISTEMA deberá requerir el encabezado `Idempotency-Key` con una cadena de 1 a 128 caracteres ASCII visibles sin espacios.
- SI falta la clave o su formato es inválido, ENTONCES EL SISTEMA deberá responder HTTP 400 y no crear una orden.
- CUANDO se persista una orden, incluso una `REJECTED`, EL SISTEMA deberá asociar su resultado original a la clave y al `userId`, sin vencimiento en esta versión.
- SI el mismo usuario reenvía una solicitud válida con la misma clave y los mismos datos de negocio, ENTONCES EL SISTEMA deberá devolver el código y cuerpo originales sin crear otra orden ni volver a aplicar sus efectos.
- CUANDO se reenvíe una creación con la misma clave y datos de negocio, EL SISTEMA deberá recuperar el resultado original sin reevaluar los recursos disponibles ni la cotización actual.
- CUANDO se compare una solicitud reenviada, EL SISTEMA deberá comparar sus valores de negocio validados; el orden de los campos JSON y las representaciones numéricas equivalentes no deberán cambiar la comparación.
- SI el mismo usuario reutiliza una clave con datos de negocio diferentes, ENTONCES EL SISTEMA deberá responder HTTP 409 y no crear ni modificar una orden.
- CUANDO usuarios distintos utilicen la misma clave, EL SISTEMA deberá tratar sus solicitudes de forma independiente.
- SI una solicitud falla con HTTP 400 o 404 antes de crear una orden, ENTONCES EL SISTEMA no deberá consumir la clave.
- CUANDO se reenvíe una creación cuya orden fue cancelada posteriormente, EL SISTEMA deberá devolver el resultado original de la creación, no el estado actual.
- CUANDO se solicite una cancelación, EL SISTEMA no deberá requerir una clave de idempotencia; una nueva solicitud sobre una orden ya cancelada deberá responder HTTP 409 sin modificarla.

## Requisitos no funcionales

- El cálculo de importes deberá ser consistente con los precios y movimientos registrados, sin cargos adicionales.
- Las cantidades ejecutadas y solicitadas deberán expresarse en acciones enteras; no se admitirán fracciones de acciones.
- Una operación deberá dejar un resultado coherente: una orden `REJECTED` no alterará efectivo, tenencias ni reservas.
- Las solicitudes simultáneas del mismo usuario no deberán aceptar órdenes que, consideradas conjuntamente, excedan sus recursos disponibles.
- La persistencia de una orden y su resultado de idempotencia deberá ser atómica; un fallo no deberá dejar uno registrado sin el otro.
- Las respuestas deberán limitarse a la información necesaria para identificar y conocer el resultado de la orden; no deberán exponer datos personales innecesarios.
- Los identificadores y términos del contrato destinados a la API deberán estar en inglés; la documentación y los textos dirigidos a las personas deberán estar en español.

## Casos límite

- Falta o invalidez de `userId`, instrumento, lado, tipo, cantidad o monto: HTTP 400 cuando el dato es inválido; HTTP 404 cuando el usuario o instrumento no existe.
- Instrumento existente que no sea de tipo `ACCIONES`: HTTP 400, sin crear la orden.
- Cantidad no entera, cantidad o monto no positivo, o precio límite ausente/no positivo: HTTP 400, sin crear la orden.
- Precio límite con más de dos decimales o superior a $99.999.999,99, o cantidad indicada/calculada superior a 2.147.483.647: HTTP 400, sin crear la orden.
- Cantidad y monto incluidos a la vez, o ambos ausentes: HTTP 400, sin crear la orden.
- Orden `MARKET` con un precio enviado por el cliente: HTTP 400, sin crear la orden.
- Monto que no alcanza para una acción entera al precio de referencia: HTTP 400, sin crear la orden.
- Orden `MARKET` sin `close` positivo: persistir `REJECTED`, responder HTTP 422 y no alterar recursos; para monto, dejar `size` nulo.
- Compra por monto mayor al efectivo disponible, aunque el costo efectivo de las acciones entre en el saldo: persistir `REJECTED` y responder HTTP 422, siempre que se alcance al menos una acción.
- Compra por monto mayor al efectivo disponible que no alcanza para una acción: responder HTTP 400 sin crear la orden.
- Venta por monto cuyo monto bruto supera la valuación de la tenencia libre, pero cuya cantidad entera calculada sí está disponible: validar por las acciones calculadas.
- Compra con fondos insuficientes o venta con acciones libres insuficientes: persistir `REJECTED`, responder HTTP 422 y no alterar recursos.
- Compra `LIMIT` pendiente: reservar sus pesos mientras permanezca `NEW`; venta `LIMIT` pendiente: reservar sus acciones mientras permanezca `NEW`.
- Cancelación de una orden `NEW`: responder HTTP 200 con estado `CANCELLED` y liberar su reserva.
- Cancelación de una orden inexistente o asociada a otro usuario: responder HTTP 404 sin modificarla.
- Cancelación de una orden que no está `NEW`, incluida una ya cancelada: responder HTTP 409 sin modificarla.
- Misma clave de creación y mismos datos para el mismo usuario: devolver el resultado original, incluso si la orden se canceló después, sin crear otra orden.
- Misma clave de creación y datos diferentes para el mismo usuario: responder HTTP 409 sin crear otra orden.
- Clave de creación faltante o inválida: responder HTTP 400 sin crear la orden.

## Fuera de alcance

- Crear órdenes `CASH_IN` o `CASH_OUT`; estos movimientos solo se consideran al calcular la disponibilidad.
- Simular el mercado, ejecutar órdenes `LIMIT`, realizar ejecuciones parciales o mantener un libro de ofertas.
- Notificar cambios de estado de forma posterior o independiente de las respuestas a las solicitudes de envío o cancelación.
- Autenticación o autorización; el identificador de usuario no se considera una credencial.
- Órdenes sobre instrumentos que no sean de tipo `ACCIONES`.
- Acciones fraccionarias.
- Comisiones, impuestos y otros cargos.
- Buscar instrumentos o administrar el catálogo. La consulta del portfolio y su campo `availableQuantity` se definen en la spec de portfolio.

## Criterios de finalización

- Cada requisito funcional describe un resultado observable y verificable mediante sus criterios de aceptación.
- Las solicitudes inválidas responden HTTP 400 sin persistir órdenes; las referencias inexistentes responden HTTP 404.
- Las órdenes aceptadas responden HTTP 201 con estado `FILLED` para `MARKET` y `NEW` para `LIMIT`.
- Las órdenes válidas rechazadas por falta de recursos o de precio `close` se persisten como `REJECTED` y responden HTTP 422, sin alterar recursos.
- La conversión de monto produce acciones enteras; una compra por monto valida el monto solicitado completo y una venta por monto valida las acciones calculadas.
- Las órdenes pendientes reservan los recursos correspondientes mientras permanezcan `NEW`.
- La cancelación de una orden `NEW` libera su reserva y responde HTTP 200 con el estado `CANCELLED`; las solicitudes por órdenes inexistentes o de otro usuario responden HTTP 404 y las de órdenes que no están `NEW`, HTTP 409.
- La creación es idempotente por usuario y clave, conserva el resultado original sin vencimiento y rechaza con HTTP 409 la reutilización de una clave con datos diferentes.
- El plan de implementación contempla el envío y la cancelación de órdenes, incluida la liberación de reservas.
