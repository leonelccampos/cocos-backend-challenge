# Consultas para los owners del challenge

Estas consultas buscan validar los supuestos de la implementación de portfolio; no modifican la consigna ni las decisiones aprobadas en [la spec](./specs/001-get-portfolio/spec.md). Los importes de los ejemplos están en pesos.

## 1. Historial con posición negativa

**Contexto:** los datos entregados incluyen BMA con 20 acciones compradas y 30 vendidas `FILLED`. Una compra `NEW` de 60 no modifica esa cantidad.

**Tratamiento actual:** se informa una posición de −10, sin asumir que sea una venta en corto ni que exista una tenencia inicial. Su valor de mercado se calcula con signo y reduce el valor total de la cuenta.

**Consulta:** ¿es correcto interpretar así el historial? ¿La posición corresponde a una venta en corto o falta información inicial? ¿Debe su valuación negativa reducir el total?

La consigna exige rechazar futuras ventas sin acciones suficientes; aceptar el historial negativo en esta consulta no modifica esa regla.

## 2. Rendimiento acumulado

**Tratamiento actual:** para cada posición abierta, el resultado es ventas ejecutadas + valor de mercado con signo − compras ejecutadas; `performance` es ese resultado dividido por las compras ejecutadas × 100. Si no hubo compras, el porcentaje es `null`. Esta fórmula no pretende medir rentabilidad sobre garantías o capital de una venta en corto.

**Ejemplo:** compras por $2.000, ventas por $3.600 y valor de mercado de −$1.000 producen $600 de resultado y 30 % de `performance`.

**Consulta:** ¿son adecuados la fórmula y el denominador para posiciones positivas y negativas? Si no hubo compras, ¿debe mantenerse `null` o usarse otra base?

## 3. Posiciones cerradas

**Tratamiento actual:** si la cantidad neta es cero, el instrumento y su resultado realizado no aparecen en el detalle. Las compras y ventas ejecutadas sí afectan el efectivo reconstruido de la cuenta.

**Consulta:** ¿es suficiente reflejar esos movimientos en el efectivo o se necesita exponer el resultado realizado en otro campo o reporte?

## 4. Rendimiento diario

**Tratamiento actual:** `dailyReturn` es una estimación por posición en pesos: cantidad neta × (`close` de hoy − `previousClose` de la misma fila). Es `null` si no hay cierre válido de hoy, se usa una alternativa, falta un `previousClose` positivo o alguna compra/venta `FILLED` tiene fecha de hoy, futura o desconocida. No se calcula un total diario consolidado.

La fecha de `orders.datetime` se interpreta en Buenos Aires y se usa como aproximación de ejecución. Como el esquema no registra esa fecha, no se puede detectar una orden creada antes y ejecutada hoy.

**Consulta:** ¿son aceptables la estimación y esta limitación? De no serlo, ¿se necesita registrar la fecha de ejecución y contemplar los movimientos intradía? ¿Hace falta un rendimiento diario consolidado?

## 5. Reconstrucción del efectivo y reservas

**Tratamiento actual:** los movimientos `CASH_IN`/`CASH_OUT` `FILLED` usan `size` como importe; las compras y ventas `FILLED` usan `price × size`. Una compra `NEW` reserva `price × size`: reduce el efectivo disponible, pero permanece dentro del valor total. La reserva no se expone por separado.

**Consulta:** ¿son suficientes estos movimientos para reconstruir el efectivo o existe un saldo inicial u otro movimiento relevante? ¿Es correcta la reserva y se necesita exponer su importe?

## 6. Ventas pendientes

**Tratamiento actual:** una venta `NEW` no reduce la cantidad ejecutada ni el valor del portfolio; las órdenes canceladas o rechazadas tampoco modifican la tenencia.

**Consulta:** ¿debe exponerse también la cantidad disponible para vender, descontando las ventas pendientes?

## 7. Precio de valuación

**Tratamiento actual:** se usa el `close` positivo fechado más reciente, con desempate por ID mayor. Si no existe ninguno, se usa `previousClose` positivo del registro fechado más reciente; el esquema no indica la fecha exacta de ese precio. No se impone un límite de antigüedad.

**Consulta:** ¿son aceptables esta prioridad y el uso de `previousClose` sin fecha exacta? ¿Debe limitarse la antigüedad del precio?

## 8. Posición sin precio utilizable

**Tratamiento actual:** se conserva la posición con `valuationStatus: UNAVAILABLE`, valuación y rendimientos `null`, y se excluye su valor del total. Por lo tanto, el total puede ser parcial.

**Consulta:** ¿basta con indicarlo en el detalle o se requiere una señal explícita de total parcial u otro tratamiento?

## 9. Identificación y cuenta sin movimientos

**Tratamiento actual:** una cuenta existente sin movimientos devuelve HTTP 200, efectivo y total en cero y lista vacía. Una pareja ID/número de cuenta inexistente o no asociada devuelve 404. Estos datos identifican la consulta y no constituyen autenticación.

**Consulta:** ¿son correctas esas respuestas y la identificación con ambos datos?

## 10. Precisión y datos faltantes

**Tratamiento actual:** se conserva la precisión de origen en importes y se redondea el porcentaje a 8 decimales con `HALF_EVEN`. Si falta un dato indispensable, como el precio de una compra ejecutada, se devuelve un error interno genérico, sin inventar valores ni devolver un resultado parcial.

**Consulta:** ¿se requiere otra precisión o una respuesta de negocio específica para datos indispensables faltantes?
