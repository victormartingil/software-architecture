# Contribuir al código y a la documentación

La [especificación](specification.md) y los [criterios de aceptación](acceptance.md) fijan los contratos. Antes de modificar una capacidad, revisar sus criterios y la [evidencia disponible](verification.md). Las instrucciones completas están en `AGENTS.md`, en la raíz del repositorio.

<a id="redactar-un-capitulo"></a>
<a id="redactar-un-capítulo"></a>

## Documentar una decisión

Presentar decisiones respaldadas por código y evidencia: problema, restricciones, alternativas, decisión, implementación, verificación y límites. Mantener un problema concreto de la tienda como hilo conductor. Conservar las explicaciones iniciales, ejemplos y casos de aplicación para repasar y compartir criterios; no atribuir experiencia productiva ni resultados que la evidencia no acredite. Introducir el término en español antes de utilizar su nombre técnico; conservar en inglés los identificadores del código. Usar títulos que indiquen el contenido y frases que expliquen una acción o una relación concreta.

Un capítulo debe permitir entender el caso, seguir la implementación y comprobar un resultado. Los ejercicios de aplicación deben pedir un entregable y ofrecer pista, criterios de éxito y solución razonada; no convertir todos los capítulos en talleres ni confundir ejecutar pruebas con demostrar aprendizaje. Indicar requisitos de la práctica, resultado esperado y límites. Al cambiar títulos, conservar las anclas explícitas para no romper enlaces. Mantener los enlaces de capítulo anterior, índice y siguiente.

La organización toma como referencia la [introducción y orden de estudio de The Rust Book](https://github.com/rust-lang/book/blob/main/src/ch00-00-introduction.md), el [tutorial progresivo de Django](https://github.com/django/django/blob/main/docs/intro/tutorial01.txt) y la [distinción de Diátaxis entre tutorial y consulta](https://diataxis.fr/tutorials/). Se adaptan sus criterios de enseñanza; el contenido y el ejemplo son propios.

## Modificar diagramas

El [maestro editable](diagrams/architecture.drawio) contiene dieciséis páginas. Editar el `.drawio` con draw.io y volver a exportar el SVG de la página modificada. Mantener nombres, objetos editables y relaciones; no corregir únicamente la imagen exportada.

Abrir el SVG final y comprobarlo al tamaño al que se muestra en el capítulo: texto legible, etiquetas completas y flechas sin cruces ambiguos. El texto adyacente debe transmitir las relaciones esenciales aunque se oculte la imagen, siguiendo [W3C para imágenes complejas](https://www.w3.org/WAI/tutorials/images/complex/). Los capítulos muestran el diagrama y explican cómo leerlo; las instrucciones de edición se mantienen aquí.

## Comprobar y entregar

Para cambios de documentación:

```bash
python3 scripts/check_repository.py
uv run --project tools/docs mkdocs build --strict -f mkdocs.yml
python3 scripts/check_doc_navigation.py
python3 -m unittest discover -s scripts/tests -p test_doc_navigation.py
git diff --check
```

Estos comandos revisan higiene, construcción estricta y navegación del HTML generado: destinos internos, fragmentos e IDs únicos. El verificador usa solo la biblioteca estándar de Python, no consulta URLs externas y sus negativos deben rechazar un destino, fragmento o ID alterado. No comprueba el renderizado de GitHub ni acredita accesibilidad visual. Las anclas y la presentación deben comprobarse también en el HTML resultante y en GitHub. Si cambia código o configuración, ejecutar las pruebas de la capacidad afectada y registrar el resultado real en la verificación. El [capítulo 9](handbook/delivery.md) distingue esos niveles de comprobación.

No versionar credenciales, contexto privado ni informes regenerables. Los resultados locales se guardan en `reports/`, que está excluido de Git.

<a id="topic-reutilizacion-y-referencias"></a>

## Reutilización y referencias

El código y la documentación originales se distribuyen bajo [MIT](https://github.com/victormartingil/software-architecture/blob/main/LICENSE). Se conservan las licencias y atribuciones de terceros, incluida [metrics-server](https://github.com/victormartingil/software-architecture/blob/main/infra/kubernetes/LICENSE.metrics-server). Enlazar una fuente externa no incorpora su contenido al proyecto ni cambia su licencia.

Cada lectura debe indicar qué observar y para qué sirve. Mantener una lectura prioritaria y, cuando aporte contraste, una alternativa al final de cada capítulo; las correspondencias completas viven en la [bibliografía](bibliography.md). Identificar edición, capítulos contrastados y límites de actualidad; no inventar páginas ni presentar un índice consultado como lectura integral. El catálogo es consulta opcional. Los ejemplos GitHub identifican fragmento/commit, fecha y diferencias, sin mantener recuentos de estrellas. Para un extracto externo descrito con precisión, preferir tag o commit consultado; un enlace a la rama principal sirve para explorar, pero su contenido puede cambiar. No duplicar clases ni capítulos de libros. La licencia MIT permite reutilizar el material original conservando el aviso de copyright y la licencia, sin garantía; su texto completo prevalece sobre este resumen.
