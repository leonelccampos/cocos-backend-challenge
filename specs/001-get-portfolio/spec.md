# Portfolio

## Contexto y objetivo

Permitir consultar el portfolio de una cuenta mediante el ID del usuario y su número de cuenta. La respuesta muestra los pesos disponibles, el valor total calculado de la cuenta y las posiciones netas de instrumentos, con su cantidad, valor de mercado y rendimiento acumulado y diario.

Los registros de referencia representan una cuenta por usuario. `performance` es el porcentaje acumulado definido en estos requisitos; `dailyReturn` es una estimación diaria en pesos.

## Usuarios

- Usuario que consulta la cuenta asociada al ID y número de cuenta indicados. Esos datos identifican la cuenta consultada, pero no acreditan autenticación.

## Historias de usuario

- Como usuario, quiero consultar una cuenta mediante su ID y número de cuenta para conocer mi portfolio.
- Como usuario, quiero distinguir los pesos disponibles para operar del valor total de la cuenta.
- Como usuario, quiero conocer la cantidad, el valor de mercado y el rendimiento acumulado de los instrumentos que conservo.
- Como usuario, quiero conocer la variación diaria estimada en pesos de cada posición cuando pueda calcularse.
- Como usuario, quiero saber cuándo un instrumento no pudo valuarse y si fue excluido del total.

## Requisitos funcionales

### RF-1 — Identificación de la cuenta

- Cuando se solicite el portfolio, el sistema deberá recibir el ID del usuario y el número de cuenta.
- Cuando ambos datos correspondan a una cuenta existente, el sistema deberá devolver el portfolio de esa cuenta.
- Si falta el parámetro `accountNumber`, está vacío, supera 20 caracteres o el ID no es un entero positivo, el sistema deberá responder con error 400. Si el segmento `userId` no está presente, la ruta no coincide y el sistema deberá responder con error 404.
- Si el usuario o la cuenta no existen, o no corresponden entre sí, el sistema deberá responder con error 404 y no devolver un portfolio vacío como si la consulta hubiera sido exitosa.

### RF-2 — Pesos disponibles y órdenes pendientes

- Cuando se calculen los pesos disponibles, el sistema deberá considerar los ingresos y egresos de efectivo y las compras y ventas `FILLED`.
- Cuando se procese un ingreso `CASH_IN` o egreso `CASH_OUT` `FILLED`, el sistema deberá considerar `size` como el importe en pesos que se suma o resta, respectivamente.
- Cuando se procese una compra o venta `FILLED`, el sistema deberá calcular su movimiento de efectivo como `price × size`.
- Mientras una compra `NEW` permanezca pendiente, el sistema deberá excluir el efectivo reservado de los pesos disponibles.
- Mientras una compra `NEW` permanezca pendiente, el importe reservado deberá ser `price × size`, usando el precio límite y la cantidad de la orden.
- Cuando una orden pendiente se cancele o rechace, el sistema deberá liberar la reserva correspondiente.
- Cuando una orden pendiente se ejecute, el sistema deberá reflejar el movimiento ejecutado en el efectivo y las tenencias.

### RF-3 — Instrumentos y cantidades

- Cuando se calcule la cantidad de acciones de un instrumento, el sistema deberá considerar las compras y ventas `FILLED`.
- Mientras una venta `NEW` permanezca pendiente, sus acciones deberán seguir formando parte de las acciones que conserva el usuario.
- Las órdenes `NEW`, `CANCELLED` y `REJECTED` no deberán alterar la cantidad ejecutada.
- Cuando una operación expresada en pesos se ejecute, la cantidad reflejada deberá corresponder a acciones enteras efectivamente ejecutadas; no se admitirán fracciones de acciones.
- Una cantidad neta negativa deberá incluirse con su signo; no se inferirá a partir de ella que el envío de nuevas ventas sin acciones está permitido.
- Si la cantidad neta de un instrumento es cero, el sistema deberá omitirlo del listado.

### RF-4 — Valor de mercado

- Cuando haya una cantidad neta distinta de cero y un precio de cierre válido disponible, el sistema deberá calcular su valor de mercado multiplicando la cantidad con signo por el precio.
- El sistema deberá usar el `close` positivo más reciente entre los registros con fecha, ordenados por fecha e ID descendentes.
- Si no existe ningún `close` positivo fechado, deberá usar `previousClose` del registro fechado más reciente, con desempate por ID mayor, únicamente si es positivo. La fecha exacta de ese precio no está disponible en el esquema.
- Si no hay ningún precio utilizable, el sistema deberá conservar la posición e indicar que su valor no está disponible. Las filas sin fecha no participan en la selección.

### RF-5 — Valor total de la cuenta

- Cuando se calcule el valor total, el sistema deberá sumar los pesos disponibles, el efectivo reservado por compras `NEW` y los valores de mercado disponibles de los instrumentos.
- El valor de mercado negativo de una posición neta negativa deberá restarse del total.
- Si el valor de un instrumento no está disponible, el sistema deberá excluirlo de la suma e indicarlo en el detalle.
- El efectivo reservado no deberá mostrarse como pesos disponibles ni en un campo separado.

### RF-6 — Rendimiento acumulado

- Cuando existan compras y ventas `FILLED` y una valuación actual disponible, el sistema deberá calcular el resultado acumulado por instrumento como: importe de ventas ejecutadas + valor actual de las acciones conservadas − importe total de compras ejecutadas.
- El sistema deberá calcular el porcentaje como resultado acumulado dividido por el importe total de compras ejecutadas, multiplicado por 100.
- La respuesta HTTP deberá exponer ese porcentaje en el campo `performance`; no deberá incluir `accumulatedReturnPercentage`.
- La misma fórmula se aplicará a posiciones netas negativas, utilizando su valor de mercado con signo. Este porcentaje no implica una rentabilidad sobre garantías o capital de una venta en corto.
- Si el importe total de compras ejecutadas es cero, el sistema deberá indicar que el porcentaje no está disponible.
- Si no hay valuación actual disponible, el sistema deberá indicar que el rendimiento del instrumento no está disponible.
- Si la cantidad actual de acciones es cero, el sistema deberá omitir el instrumento, aunque haya tenido ventas anteriores.
- El rendimiento informado no deberá interpretarse como diario ni anualizado.

### RF-7 — Rendimiento diario estimado

- La respuesta deberá exponer `dailyReturn` por instrumento, en pesos, distinto del porcentaje acumulado `performance`.
- Si el cierre seleccionado corresponde a hoy, no es una alternativa de precio y `previousClose` de esa misma fila es positivo, el sistema deberá calcular `cantidad neta × (close − previousClose)`, siempre que todas las compras y ventas `FILLED` del instrumento tengan fecha anterior a hoy.
- Para determinar hoy se utilizará `America/Argentina/Buenos_Aires`. El `datetime` sin zona de las órdenes se interpretará como fecha y hora local de Buenos Aires.
- Como el esquema no tiene fecha de ejecución, se usa el `datetime` de cada orden `FILLED` como aproximación de su fecha de ejecución. Esta heurística puede no detectar una orden creada en una rueda anterior y ejecutada hoy, por lo que el resultado es una estimación, no un resultado contable garantizado.
- Si una compra o venta ejecutada tiene fecha de hoy, futura o desconocida; si no existe un cierre válido de hoy; si se utiliza una alternativa de precio; o si falta un `previousClose` positivo, el rendimiento diario deberá ser `null`. No se consulta un calendario de mercado.
- Una orden pendiente, cancelada o rechazada no cuenta como operación ejecutada. Una posición negativa utiliza su cantidad con signo.

## Requisitos no funcionales

- La respuesta deberá limitarse a los datos de la cuenta consultada y no exponer datos personales innecesarios.
- Los cálculos de dinero, cantidades y porcentajes deberán ser consistentes con los movimientos y precios disponibles.
- Los importes deberán conservar la precisión disponible en los datos de origen. Los porcentajes se redondearán a 8 decimales con `HALF_EVEN`.
- La documentación y los textos dirigidos a personas deberán estar en español.

## Casos límite

- `accountNumber` faltante, vacío o de más de 20 caracteres; `userId` no entero o no positivo: responder 400.
- Segmento `userId` ausente y ruta no coincidente: responder 404.
- Usuario inexistente, cuenta inexistente o número de cuenta no asociado al ID: responder 404.
- Cuenta sin acciones ejecutadas: informar sus pesos disponibles y un listado vacío.
- Posición neta negativa: incluir su cantidad y valor de mercado con signo, y restar este valor del total; aplicar la fórmula de rendimiento acordada.
- Cierres faltantes, cero o negativos: buscar el último `close` positivo fechado; si no existe, probar el `previousClose` positivo del registro fechado más reciente.
- Ningún precio utilizable: conservar la posición sin valuación y excluirla del valor total.
- Varios cierres positivos para el mismo instrumento en un día: usar el de mayor ID.
- Orden `NEW` de compra: el efectivo reservado no está disponible para operar, pero sigue contando en el valor total.
- Orden `NEW` de venta: las acciones siguen contando en la cantidad y el valor total. La prevención de que se oferten en otra orden queda fuera de esta funcionalidad.
- Importe de compras ejecutadas igual a cero: mostrar el porcentaje como no disponible.
- Cotizaciones históricas estáticas o fecha de ejecución no comprobable mediante el proxy acordado: mostrar `dailyReturn: null`.

## Fuera de alcance

- Autenticación o autorización del usuario.
- Soporte de varias cuentas por usuario.
- Envío, ejecución o cancelación de órdenes.
- Prevención de que las acciones reservadas por una venta `NEW` se oferten en otra orden; corresponde a la funcionalidad de envío de órdenes.
- Simulación del mercado.
- Rendimiento diario consolidado de la cuenta, resultado diario contable con operaciones intradía y rendimiento anualizado.
- Mostrar instrumentos cuya cantidad neta actual sea cero.

## Criterios de finalización

- Cada requisito funcional tiene criterios de aceptación verificables.
- El cálculo de pesos disponibles refleja los movimientos ejecutados y las reservas de órdenes pendientes.
- El valor total incluye las reservas y excluye del cálculo los instrumentos que no pueden valuarse, indicándolo en el detalle.
- Las cantidades reflejan acciones enteras ejecutadas.
- El rendimiento acumulado incluye resultados de ventas ejecutadas y valor actual de las acciones conservadas.
- Las solicitudes con parámetros inválidos responden 400; la ruta sin `userId` y las cuentas inexistentes o no coincidentes responden 404, según RF-1.

## Dudas abiertas

Las decisiones de negocio adoptadas están pendientes de confirmación por los owners y se recopilan con ejemplos en [preguntas.md](../../preguntas.md).
