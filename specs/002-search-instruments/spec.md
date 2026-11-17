# Spec 002 — Búsqueda de activos

## Contexto y objetivo

Permitir que un usuario encuentre instrumentos disponibles en el catálogo del mercado a partir de su ticker o nombre. La búsqueda facilita identificar un activo antes de consultar información relacionada o enviar una orden, sin simular el mercado ni modificar datos.

## Usuarios / actores

- Usuario o cliente de la API que busca instrumentos por ticker o nombre.

## Historias de usuario

- H1: Como usuario, quiero buscar por ticker para encontrar rápidamente un activo conocido.
- H2: Como usuario, quiero buscar por nombre para encontrar un activo cuando no conozco su ticker.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: CUANDO el cliente solicite `GET /api/v1/instruments?query=...&field=...`, EL SISTEMA requerirá ambos parámetros; `field` deberá ser `ticker` o `name`.
- RF-2: SI `query` falta, está vacío o contiene solo espacios después de recortar los espacios iniciales y finales, o si `field` falta o no es `ticker` ni `name`, ENTONCES EL SISTEMA responderá con HTTP 400.
- RF-3: CUANDO los parámetros sean válidos, EL SISTEMA buscará `query` como subcadena del campo indicado por `field`, sin distinguir mayúsculas/minúsculas ni acentos.
- RF-4: CUANDO existan instrumentos `ACCIONES` que coincidan, EL SISTEMA responderá HTTP 200 con todos los resultados, sin paginación, con los campos `id`, `ticker`, `name`, `type`, `marketPrice` y `closeDate`. `marketPrice` será el precio de cierre en pesos. Los resultados se ordenarán por ticker ascendente y, en caso de empate, por ID ascendente.
- RF-5: CUANDO se determine `marketPrice`, EL SISTEMA usará el `close` positivo más reciente entre los registros con fecha, ordenados por fecha e ID descendentes; `closeDate` será la fecha de ese registro. SI no existe un `close` positivo utilizable, ENTONCES EL SISTEMA usará `previousClose` del registro con fecha más reciente, con desempate por ID descendente, únicamente si es positivo. Si usa `previousClose`, `closeDate` será `null` porque el esquema no permite conocer la fecha exacta de ese precio. Si no hay un precio utilizable, `marketPrice` y `closeDate` serán `null`.
- RF-6: SI no existen instrumentos coincidentes, ENTONCES EL SISTEMA responderá con HTTP 404.
- RF-7: CUANDO se consulte el catálogo, EL SISTEMA no modificará instrumentos, órdenes ni cotizaciones.
- RF-8: EL SISTEMA permitirá consultar instrumentos sin requerir un usuario autenticado.

## Requisitos no funcionales

- La búsqueda deberá consultar el catálogo de instrumentos provisto por el proyecto y preservar el esquema de base de datos.
- La documentación estará en español; los identificadores y campos del contrato API, en inglés.
- La respuesta no deberá incluir datos personales ni información ajena a la identificación de los instrumentos y su cotización de cierre.

## Casos límite

- `query` faltante, vacío o compuesto solo por espacios después de recortar espacios exteriores: responder 400.
- `field` faltante o distinto de `ticker` y `name`: responder 400.
- Ninguna coincidencia: responder 404.
- Términos con diferencias de mayúsculas, minúsculas o acentos: buscar sin distinguirlas.
- Coincidencias en más de un instrumento: devolver todas, sin paginación.
- Ticker repetido: ordenar por ID ascendente dentro del mismo ticker.
- Sin `close` positivo disponible: usar `previousClose` positivo del registro fechado más reciente; si se usa, `closeDate` es `null`.
- Sin ningún precio utilizable: devolver `marketPrice: null` y `closeDate: null`.
- El catálogo incluye instrumentos de varios tipos: devolver solo los de tipo `ACCIONES`.

## Fuera de alcance

- Enviar, ejecutar o cancelar órdenes.
- Consultar o calcular posiciones, portfolio o rendimientos; la cotización de cierre definida en RF-5 sí forma parte de esta búsqueda.
- Simular el mercado.
- Autenticación o autorización de usuarios.
- Crear o modificar instrumentos, cotizaciones o el esquema de base de datos.

## Criterios de finalización

- Cada requisito funcional tiene criterios de aceptación verificables y pruebas correspondientes.
- La ruta, los parámetros, la validación y la regla de coincidencia están documentados y son verificables.
- Los resultados incluyen los campos acordados, respetan el filtro de tipo y el orden establecido, e informan la cotización conforme a RF-5.
- Las consultas sin coincidencias y los criterios inválidos responden 404 y 400, respectivamente.
- La consulta no altera los datos del catálogo, órdenes ni cotizaciones.

## Dudas abiertas

- Ninguna por el momento; las decisiones funcionales y del contrato quedaron acordadas.
