# Plan de implementación — Búsqueda de activos

## Alcance y restricciones

Implementar [la spec](./spec.md) bajo [la constitución](../../docs/constitution.md) y
[las decisiones técnicas del proyecto](../../PROJECT_SPEC.md). La búsqueda es una lectura
sin paginación, autenticación ni efectos secundarios. No se modifican el esquema, los datos
ni las dependencias.

## Responsabilidades

| Componente | Responsabilidad | RF |
|---|---|---|
| `InstrumentSearchController` y representaciones REST | Validar parámetros, adaptar el campo de búsqueda y mapear la respuesta HTTP. | RF-1, RF-2, RF-4, RF-8 |
| `SearchInstrumentsUseCase` | Recortar espacios exteriores, delegar al puerto y señalar la ausencia de coincidencias. | RF-2, RF-6 |
| `InstrumentSearchReadPort` y DTOs de aplicación | Definir el criterio y el resultado que necesita el caso de uso, sin exponer JPA ni HTTP. | RF-3–RF-5 |
| `InstrumentSearchReadAdapter` | Traducir el campo para el repositorio y mapear su proyección al resultado de aplicación en una transacción de solo lectura. | RF-3–RF-5, RF-7 |
| `InstrumentSearchRepository` y `InstrumentSearchProjection` | Buscar, ordenar y seleccionar el precio en PostgreSQL, devolviendo solo los campos de la respuesta. | RF-3–RF-5, RF-7 |
| `InstrumentSearchExceptionHandler` | Traducir la ausencia de coincidencias a HTTP 404. | RF-6 |

No se necesita una política ni un modelo de candidatos en el dominio o la aplicación.
La proyección es un contrato de lectura de persistencia, no una entidad de dominio.

## Algoritmo y decisiones

1. El adaptador HTTP valida `query` y `field`. El caso de uso recorta `query` con
   `strip()` y solicita al puerto los resultados para el campo elegido.
2. PostgreSQL filtra `ACCIONES` por subcadena literal del campo elegido. Normaliza
   ambos lados con NFD, elimina las marcas combinantes U+0300–U+036F y aplica
   minúsculas con la collation ICU `und-x-icu`. La comparación cubre los acentos
   latinos comunes del catálogo de referencia; otras marcas Unicode no están
   garantizadas. No se usan comodines `LIKE` para interpretar la entrada.
3. La consulta selecciona por instrumento el último `close` positivo fechado o,
   en su ausencia, el `previousClose` positivo de la última fila fechada. Ambas
   selecciones desempatan por ID descendente. Si se usa `previousClose`, la fecha
   resultante es nula; si no hay precio válido, precio y fecha son nulos.
4. Los resultados se ordenan por ticker con collation `C`, nulos al final, y luego
   por ID ascendente. Esta collation conserva el orden del catálogo de referencia;
   no se garantiza que coincida con el orden UTF-16 de Java para todo Unicode.
5. El caso de uso lanza `InstrumentSearchNotFoundException` si el puerto devuelve
   una lista vacía. El adaptador HTTP responde 404.

La consulta usa `LEFT JOIN LATERAL` y los nombres físicos existentes del esquema;
no carga el historial completo ni realiza una consulta por resultado desde Java.
Requiere PostgreSQL con UTF-8 y soporte ICU. No requiere `unaccent`, `pg_trgm`,
nuevos índices ni cambios de esquema. La búsqueda por subcadena puede requerir recorrer
el catálogo; una optimización adicional debe justificarse con mediciones y requiere
aprobación cuando implique cambiar infraestructura o esquema.

## Verificación

- Pruebas del caso de uso y adaptador: delegación del criterio, recorte, mapeo,
  ausencia de resultados y propagación de errores.
- Integración con PostgreSQL real: búsqueda por ticker y nombre, acentos latinos
  compuestos y descompuestos, subcadenas literales, tipos, orden y selección de
  precios, incluidos empates, nulos y fallback.
- Integración HTTP: contrato 200/400/404, respuestas y ausencia de escrituras.
- Ejecutar Gradle y comprobar solicitudes en Postman conforme a
  [las reglas del proyecto](../../AGENTS.md).
