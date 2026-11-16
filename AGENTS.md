# AGENTS.md — coco-bank-challenge

Guía operativa breve para cambios en el proyecto. La constitución define principios; este archivo indica cómo aplicarlos y dónde consultar cada decisión.

## Fuente de verdad

- [`docs/constitution.md`](./docs/constitution.md): principios innegociables.
- [`PROJECT_SPEC.md`](./PROJECT_SPEC.md): decisiones técnicas y de arquitectura aprobadas para todo el proyecto.
- [`backend-challenge.md`](./backend-challenge.md) y [`database.sql`](./database.sql): consigna funcional original y esquema/datos de referencia.
- `specs/NNN-*/spec.md`: requisitos, aceptación y dudas de una feature; `plan.md`: diseño de implementación; `tasks.md`: trabajo y estado de verificación.
- [`README.md`](./README.md): comandos de uso y verificación.

No dupliques contratos, decisiones ni justificaciones: actualiza la fuente de verdad correspondiente.

## Reglas al cambiar código

- Lee la constitución y la spec activa antes de implementar. Si falta una decisión de comportamiento, resuélvela en la spec antes de codificar.
- Mantén la dirección de dependencias hacia el dominio: aplicación y adaptadores pueden depender del dominio; el dominio no depende de aplicación, adaptadores ni frameworks.
- DTOs, proyecciones y modelos de lectura pertenecen a la capa que define su contrato. No los muevas al dominio para resolver una dependencia. El dominio recibe conceptos de dominio; transforma o mapea los datos en el límite correspondiente.
- Evita modelos de dominio anémicos: coloca invariantes y comportamiento en el concepto que los gobierna; reserva servicios de dominio para reglas que coordinan varios conceptos o no pertenecen naturalmente a uno. No agregues comportamiento a resultados de lectura solo para evitar que sean datos.
- Mantén un tipo de producción con nombre por archivo. Anida tipos solo cuando sean detalles internos usados exclusivamente por su contenedor.
- Declara primero los métodos públicos y deja los privados al final de la clase. Los métodos usados solo dentro de su clase deben ser `private`; no uses acceso de paquete como seam para las pruebas.
- Añade o ajusta pruebas enfocadas por comportamiento y conserva una clase de prueba por clase de producción.
- Al finalizar cada tarea o spec de implementación, verifica los cambios con pruebas unitarias, de integración y solicitudes en Postman; informa los resultados y cualquier verificación bloqueada o no ejecutada.
- No agregues dependencias, cambies el esquema o los datos ni alteres contratos aprobados sin autorización.
- No ocultes errores con valores por defecto ni expongas secretos o datos personales en logs.

Documentación del proyecto en español; identificadores de código y contratos API en inglés.
