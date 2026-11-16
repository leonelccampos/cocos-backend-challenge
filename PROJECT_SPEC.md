# Especificación inicial — coco-bank-challenge

**Estado:** aprobado para construir el scaffold técnico. No incluye funcionalidades de negocio.

## 1. Objetivo y alcance

Construir una API backend para el challenge bancario, con endpoints para consultar el portfolio de un usuario, buscar instrumentos y enviar/cancelar órdenes. La aplicación no simulará un mercado ni implementará autenticación, según la consigna.

Esta etapa define el stack, las restricciones y las preguntas que deberán resolverse al especificar cada funcionalidad. No define todavía contratos HTTP detallados ni reglas de negocio que la consigna deja ambiguas.

## 2. Requisitos funcionales de referencia

La fuente de requisitos es [`backend-challenge.md`](./backend-challenge.md), junto con el esquema y los datos de ejemplo en [`database.sql`](./database.sql).

- **Portfolio:** informar valor de la cuenta, pesos disponibles y activos con cantidad, valor monetario de posición y rendimiento.
- **Búsqueda:** encontrar instrumentos por ticker y/o nombre.
- **Órdenes:** admitir compra y venta, tipos `MARKET` y `LIMIT`, cantidad entera de acciones o monto de inversión en pesos. Una orden MARKET se ejecuta inmediatamente al último `close`; una orden LIMIT se registra como `NEW`.
- **Estados:** `NEW`, `FILLED`, `REJECTED` y `CANCELLED`. Solo se puede cancelar una orden `NEW`. Los rechazos también se persisten.
- **Transferencias:** representar ingresos y egresos de efectivo con `CASH_IN` y `CASH_OUT`.
- **Tenencias:** derivar pesos y posiciones de los movimientos pertinentes de `orders`; usar órdenes `FILLED` para cantidades, valor de mercado y rendimiento de posiciones.
- **Precios:** expresar los precios en pesos. `marketdata.close` es el último precio disponible y `previousClose` permite calcular el retorno entre cierres.
- **Autenticación:** fuera del alcance del challenge.
- **Nice to have:** colección Postman, Insomnia o REST Client con ejemplos. Se considera una entrega complementaria, no un prerequisito del scaffold.

La consigna exige al menos una prueba funcional de envío de órdenes; la estrategia propuesta está en la sección de pruebas.

## 3. Stack propuesto

Versiones consultadas el **15 de noviembre de 2026**. Las versiones de Spring Data JPA, Hibernate, el driver PostgreSQL y dependencias de testing deben venir del BOM de Spring Boot, salvo que una incompatibilidad demostrada requiera una excepción documentada.

| Área | Decisión | Motivo / límite |
|---|---|---|
| Lenguaje | Java 25 LTS | Elegir LTS en vez de perseguir el número de feature release más alto. Java 25 es LTS; el soporte de runtime debe revisarse antes de actualizar la toolchain. |
| Framework | Spring Boot 4.1.2 | Aporta configuración, servidor web, integración con persistencia y testing. La documentación consultada declara compatibilidad con Java 25 y Gradle 9.x. |
| Build | Gradle Wrapper 9.9.1, Kotlin DSL | Reproducir la versión de Gradle sin depender de una instalación global. Kotlin DSL ofrece configuración tipada y buen soporte de IDE; no implica usar Kotlin en la aplicación. Usar Java toolchain 25. |
| API | Spring MVC, REST `/api/v1` | Contratos HTTP explícitos y versionado inicial para poder evolucionarlos. Métodos, rutas, DTOs y códigos de respuesta se definirán por funcionalidad en SDD. |
| Contrato/documentación API | OpenAPI 3 y Swagger UI mediante `springdoc-openapi` 3.2.0 | Documentación navegable de la API. Evitar que las anotaciones sustituyan la definición y revisión del contrato. |
| Persistencia | Spring Data JPA | Abstracción de repositorios integrada con Spring; permite mantener el dominio separado de los detalles de persistencia. |
| ORM | Hibernate, versión gestionada por Spring Boot | Implementación JPA. No fijar una versión independiente sin motivo de compatibilidad. Usar `BigDecimal` para valores monetarios; no `float`/`double`. |
| Base de datos | PostgreSQL | El SQL de referencia usa construcciones compatibles con PostgreSQL, como `SERIAL` y `NUMERIC`. Mantener la estructura de tablas/columnas provista. |
| Migraciones | Flyway, versión gestionada por Spring Boot | Aplicar y versionar el esquema de forma reproducible. La migración inicial deberá reflejar el SQL provisto sin cambiar sus tablas ni columnas; cualquier ajuste de esquema requiere aprobación y justificación. |
| Desarrollo local | Docker Compose para PostgreSQL | Entorno reproducible, con configuración local fuera del código y sin credenciales reales versionadas. |
| Pruebas | JUnit 5, Mockito y MockMvc, en versiones gestionadas por Spring Boot | Pruebas unitarias de lógica con dobles, y pruebas HTTP del adaptador REST. |
| Integración de persistencia | Testcontainers con PostgreSQL | Verifica la aplicación contra el mismo motor SQL esperado. Requiere Docker disponible al ejecutar esa suite. No se propone H2: H2 también sería una base de integración, no una prueba unitaria, y no reemplaza la compatibilidad con PostgreSQL. |
| Formato | Spotless Gradle Plugin 8.11.0 + Google Java Format | Aplicar y comprobar formato con tareas automatizables de Gradle, en lugar de depender solo de instrucciones. |

### Fuentes oficiales consultadas

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html): clasificación de Java 25 como LTS.
- [Spring Boot — System Requirements](https://docs.spring.io/spring-boot/system-requirements.html): requisitos de Java y Gradle para Spring Boot 4.1.2.
- [Gradle — Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html): compatibilidad de Gradle 9.9.1 con Java 25.
- [Spring Boot — SQL Databases](https://docs.spring.io/spring-boot/reference/data/sql.html): soporte de SQL, JPA/Hibernate, Spring Data y bases embebidas.
- [Spring Boot — Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html): uso de servicios reales en contenedores para tests de integración.
- [springdoc-openapi](https://springdoc.org/): integración de springdoc 3.2.0 con Spring Boot 4 y Swagger UI.

Las versiones son una referencia reproducible para iniciar el scaffold, no una política de actualización automática. Antes de cambiar una versión, validar compatibilidad entre Java, Spring Boot, Gradle y plugins.

## 4. Arquitectura y estructura objetivo

Se propone una arquitectura **hexagonal liviana**: aislar reglas de aplicación/dominio de HTTP y persistencia, sin multiplicar capas, abstracciones o módulos sin necesidad.

Distribución conceptual (los paquetes/directorios concretos se establecen al construir el scaffold):

- **Dominio:** conceptos y reglas propias del negocio; sin dependencia de Spring, HTTP ni JPA.
- **Aplicación:** casos de uso y puertos requeridos por el dominio.
- **Adaptadores de entrada:** controladores REST, validación de requests y mapeo de DTOs.
- **Adaptadores de salida:** persistencia JPA/PostgreSQL y otros accesos externos que se justifiquen.
- **Configuración:** ensamblado de adaptadores, configuración de aplicación y OpenAPI.
- **Pruebas:** pruebas unitarias junto a los módulos probados y pruebas de integración claramente identificadas.

La estructura no implica que cada concepto deba tener una interfaz o clase adicional: mantenerla proporcional al challenge.

## 5. Persistencia y compatibilidad con el SQL provisto

- Preservar el modelo entregado: `users`, `instruments`, `orders` y `marketdata`, con sus tablas y columnas existentes.
- Aplicar el esquema mediante Flyway: la migración inicial reproduce la estructura provista y una migración posterior carga los datos de ejemplo de `database.sql`, sin alterar los datos ni el significado de las tablas.
- Mapear explícitamente en JPA los nombres físicos del esquema existente. PostgreSQL convierte a minúsculas los identificadores camelCase no entrecomillados del SQL (por ejemplo, `instrumentId` pasa a `instrumentid`); respetar ese nombre físico al mapear y no asumir que conserva mayúsculas.
- Evitar que Hibernate cree o modifique el esquema en runtime; las migraciones son la fuente de verdad.
- No guardar secretos ni credenciales de entorno en archivos versionados.
- No agregar índices, constraints, columnas ni tablas sin decisión explícita y justificación en la documentación.

## 6. Decisiones de negocio pendientes

No asumir estas reglas al crear entidades, servicios o contratos. Resolverlas en el SDD de la funcionalidad correspondiente, con criterios de aceptación y pruebas:

1. **Órdenes LIMIT pendientes:** si `NEW` reserva pesos/acciones, cómo se liberan al cancelar/rechazar y cómo se evita gastar o vender dos veces.
2. **Precio y monto de una orden:** cómo derivar cantidad entera al recibir monto en pesos para MARKET y LIMIT; qué precio usar para calcular el máximo de acciones de una orden LIMIT.
3. **Rendimiento total:** definir costo base, tratamiento de compras y ventas, rendimiento realizado/no realizado y cómo se relaciona con el retorno diario `close`/`previousClose`. La consigna no determina una fórmula única para el rendimiento total del portfolio.
4. **Cálculo de efectivo:** detallar cómo afectan `CASH_IN`, `CASH_OUT`, compras, ventas, órdenes abiertas y rechazadas a los pesos disponibles.
5. **Momento y precio de ejecución de límites:** la consigna indica que no se simula el mercado y que LIMIT queda `NEW`; establecer si existe algún flujo posterior de ejecución dentro del alcance.
6. **Validación y errores:** códigos HTTP, formato de errores, respuesta de una orden rechazada y distinción entre request inválido y rechazo de negocio.
7. **Concurrencia e idempotencia:** definir el comportamiento de solicitudes concurrentes sobre la misma cuenta y si se requieren claves de idempotencia.
8. **Datos de mercado incompletos/obsoletos:** definir la respuesta si falta un `close`, hay más de un registro para la fecha más reciente, o los datos sembrados no son actuales.
9. **Usuario:** cómo se identifica en cada operación dado que no hay autenticación; qué respuesta corresponde para un usuario inexistente.
10. **Representación de datos y búsqueda:** precisión/escala monetaria, redondeo, límites de cantidad, búsqueda parcial/case-insensitive, ordenamiento y paginación.

El paquete Java base aprobado para el scaffold es `com.cocobank.challenge`. Las migraciones iniciales deben mantener el esquema y los datos de referencia separados.

## 7. Flujo de trabajo por etapas

1. **Scaffold aprobado:** crear build/wrapper, estructura mínima, configuración local de PostgreSQL, migraciones, herramientas de formato y pruebas base. No incluir lógica de negocio.
2. **Luego:** abordar cada funcionalidad en un SDD independiente; cerrar sus preguntas de negocio antes de implementar, y verificarla con pruebas adecuadas.

Los detalles del proceso SDD se acordarán al iniciar esa etapa. No se fijan en este documento de stack.

## 8. Criterios para aprobar la especificación

- El lenguaje Java, el framework, la base de datos, la persistencia, las pruebas, el contrato REST y las herramientas están definidos con motivos y aprobados para el scaffold.
- Queda explícito que la consigna original solicita Node.js y que este proyecto elige Java como decisión deliberada.
- Se distinguen las decisiones confirmadas de los interrogantes de negocio pendientes.
- El scaffold no debe incluir código funcional del portfolio, la búsqueda ni las órdenes.
- Cualquier cambio de esquema futuro necesita aprobación y documentación.
