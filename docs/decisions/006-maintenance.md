# ADR 006 — Baseline mantenido y comprobación de dependencias

**Estado:** aceptado. **Contexto:** la primera base Java 21/Boot 3.5.16 conservaba compatibilidad con convenciones previas. El escaneo de los artefactos encontró avisos que la revisión de POM no revelaba. Algunos parches de Spring 6.2 solo estaban disponibles comercialmente.

**Decisión:** migrar a Boot 4.1.1/Spring 7.0.9, conservar contratos y Java 21. Adaptar starters modulares y separar Jackson 2 del codec de eventos de Jackson 3 para HTTP. Aplicar parches compatibles del driver, Tomcat, Jackson y LZ4. Escanear manifests y JARs/runtime transitivo; una aplicación sombreada puede ocultar metadatos al scanner.

**Alternativas:** ignorar avisos por conservar la major no es aceptable; adquirir soporte comercial no pertenece a esta referencia. Spring clasifica los casos XSLT/SSE según condiciones concretas ausentes del ejemplo, mientras el scanner los eleva a CRITICAL. Se conserva esa distinción y se actualiza la base sin suprimir el hallazgo.

**Consecuencias:** reaplicar tests reales de transacciones, autorización y Kafka, además de smoke/OTLP sobre imagen nueva. Lockfiles y parches reducen deriva, pero necesitan mantenimiento. Scans sin hallazgos conocidos en su alcance no equivalen a un pentest ni acreditan la seguridad del sistema operativo de cada imagen.

**Comprobación:** suite Java, smoke de contratos reales, trazas Tempo y scans exactos en [verificación](../verification.md).

Fuentes: [Spring CVE-2026-47884](https://spring.io/security/cve-2026-47884/), [Spring CVE-2026-47890](https://spring.io/security/cve-2026-47890/), [migración Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [releases kind](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0).

La preparación CI usa acciones mantenidas con runtime Node24 y commits inmutables: [checkout7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1), [setup-java6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0), [setup-python7.0.0](https://github.com/actions/setup-python/releases/tag/v7.0.0) y [setup-uv10.3.0](https://github.com/astral-sh/setup-uv/releases/tag/v10.3.0). Los action.yml oficiales conservan los inputs usados; runners GitHub alojados cumplen sus requisitos. Se mantienen JDK21, Python3.12 y uv0.8.22, permisos contents:read y checkout sin persistir credenciales. Node de Actions no es una dependencia de las aplicaciones.
