# Constitución

1. **Stack simple:** usar el stack aprobado en `PROJECT_SPEC.md`; toda dependencia, herramienta o capa nueva requiere justificación y aprobación.
2. **Spec antes que código:** cada feature requiere un SDD aprobado; implementar sus criterios de aceptación y resolver antes las decisiones abiertas.
3. **Código claro y modular:** aplicar KISS, SOLID y Clean Code de forma proporcional; respetar SLA (un nivel de abstracción por método) y Tell, Don't Ask (delegar el comportamiento, no manipular datos ajenos). Evitar modelos de dominio anémicos: ubicar invariantes y comportamiento en el concepto que los gobierna; usar servicios de dominio para reglas transversales.
   Mantener la lógica de negocio en dominio y aplicación, HTTP y persistencia en adaptadores, y evitar abstracciones innecesarias.
   Respetar Spotless/Google Java Format: límite de 100 columnas y saltos de línea definidos por el formateador.
4. **Pruebas reproducibles:** cada cambio de comportamiento incluye pruebas enfocadas; ejecutar las tareas pertinentes del Gradle Wrapper sin instalar dependencias adicionales. La integración usa PostgreSQL con Testcontainers, nunca una base embebida como sustituto.
5. **Datos protegidos:** no versionar secretos ni exponer o registrar datos de usuario innecesarios; limitar su tratamiento a lo requerido por la funcionalidad aprobada.
6. **Idioma consistente:** identificadores y contratos API en inglés; documentación y textos dirigidos a personas en español.