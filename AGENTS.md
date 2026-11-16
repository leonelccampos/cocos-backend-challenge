# AGENTS.md — coco-bank-challenge

Guía operativa para los agentes que trabajen en este backend de challenge. Ayuda a aplicar las decisiones del proyecto; no reemplaza las pruebas, la revisión de cambios ni la aprobación del usuario.

## Fuentes de contexto

- [`PROJECT_SPEC.md`](./PROJECT_SPEC.md): decisiones técnicas aprobadas para el scaffold, alcance y cuestiones pendientes de negocio.
- [`backend-challenge.md`](./backend-challenge.md): consigna funcional original. No hace falta consultarla para tareas exclusivamente de scaffold; sí al preparar un SDD o implementar una funcionalidad, para verificar y trazar sus requisitos.
- [`database.sql`](./database.sql): esquema y datos de referencia. Consultarlo para tareas de persistencia, migraciones o mapeo de entidades.
- SDD de una funcionalidad, cuando exista: criterios y decisiones aprobados para esa funcionalidad. Si contradice un requisito original o una decisión de proyecto, señalar el conflicto y pedir resolución; no elegir silenciosamente.

No dupliques en este archivo la especificación, los contratos de cada funcionalidad ni su justificación técnica. Actualiza la fuente correspondiente cuando se apruebe una decisión.

## Reglas de trabajo

- Construir solo la base técnica aprobada en `PROJECT_SPEC.md`; no agregar funcionalidades de negocio durante el scaffold.
- Antes de implementar una funcionalidad, trabajar sobre su SDD y resolver primero las preguntas abiertas que afecten su comportamiento.
- Mantener cambios pequeños y enfocados. No añadir capas, módulos o abstracciones sin una necesidad concreta.
- Añadir o actualizar pruebas para el comportamiento cambiado. Ejecutar la verificación pertinente más pequeña y ampliar si hace falta.
- Informar qué cambió y qué comandos se ejecutaron realmente. No presentar verificaciones no ejecutadas como exitosas.

## Convenciones y límites

- Documentación del proyecto en español; identificadores de código y contrato API en inglés.
- Usar `BigDecimal` para dinero y precios; no usar `float` ni `double` en cálculos monetarios.
- Mantener la lógica de negocio en los casos de uso/dominio y el transporte HTTP en los adaptadores REST, siguiendo la arquitectura que apruebe la especificación.
- No decidir reglas de negocio que la consigna deje abiertas. Consultar `PROJECT_SPEC.md` y el SDD pertinente; si falta una decisión necesaria, preguntar antes de implementarla.
- Preservar el esquema de `database.sql` salvo aprobación explícita. PostgreSQL convierte a minúsculas identificadores no entrecomillados como `instrumentId`; mapear el nombre físico real y evitar que Hibernate altere el esquema.
- La consigna excluye la simulación del mercado y la autenticación. No introducirlas por iniciativa propia ni presentar un identificador de usuario como autenticado.
- Preguntar antes de agregar dependencias fuera del stack aprobado, cambiar el esquema o los datos, hacer cambios incompatibles en un contrato API o cerrar una decisión de negocio pendiente.
- No ocultar errores con valores por defecto ni devolver respuestas de éxito ante fallos. No versionar secretos ni credenciales reales.

## Comandos y verificación

Al generar o cambiar el scaffold, mantener aquí los comandos exactos y verificados para ejecutar, probar, formatear y compilar. Las pruebas con Testcontainers requieren Docker y PostgreSQL real; no sustituirlas por una base embebida y afirmar compatibilidad con PostgreSQL.
