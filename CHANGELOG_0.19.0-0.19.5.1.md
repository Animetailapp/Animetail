# Animetail — Release notes: 0.19.2 → 0.19.5.1

Este borrador sigue el estilo de las notas de release de Mihon y agrupa los cambios relevantes entre las versiones 0.19.2 y 0.19.5.1.

Resumen de la serie 0.19.x
- Mejoras en el lector: edge-to-edge, arreglos de zonas táctiles y compatibilidad con recorte de pantalla en Android 15+.
- Mejoras en WebView: soporte multi-ventana y correcciones de estabilidad.
- Descargas: opciones de concurrencia y correcciones en estado "pending".
- Integraciones y trackers: migraciones de APIs (Kitsu, Suwayomi) y mejoras en búsqueda MAL.
- Actualizaciones de dependencias y acciones de CI.

> Nota: las entradas están compiladas automáticamente desde los commits/PRs del rango `v0.19.2` → `v0.19.5.1`. Revisa y ajusta texto o prioridades antes de publicar.

---

## Destacados (selección)
- Soporte edge-to-edge en el lector y mejoras de UX en el scroll.
- Mejora de la UX de WebView para multi-ventana.
- Añadida opción para personalizar concurrencia de descargas.
- Correcciones críticas de migraciones y crash fixes.

---

## Changelog completo por versión

### 0.19.2
### ✨ 0.19.2 — Novedades, mejoras y fixes (detallado)
- New features:
  - Personalización del número de descargas concurrentes (fuentes y páginas) y opción para restringir nombres de fichero a ASCII.
  - Mejora en el manejo de archivos con metadatos idénticos: ahora se añade un hash a los nombres para distinguirlos.
  - Soporte multi-ventana en WebView (ya no se tratan todas las ventanas emergentes como redirects).

- Changes / Improvements:
  - Incremento por defecto del número de descargas de páginas de 2 → 5.
  - Mejor spoofing del header `X-Requested-With` para compatibilidad con WebView más recientes.
  - Varias actualizaciones automáticas de dependencias y mejoras en Actions/CI.

- Fixes:
  - Correcciones en la UI del lector (indicadores de página parcialmente visibles, fondo del app/system bar en Android 15+).
  - Fixes en migraciones (varios crashes y condiciones incorrectas), fix en scrollbar cuando la animación está desactivada.
  - Reparado caso donde las descargas de extensiones podían quedarse en estado "pending".
  - Mejor manejo de incognito desde notificaciones.

### Contribuidores relevantes
- @AntsyLich, @raxod502, @TheUnlocked, @Guzmazow, @c2y5, @Naputt1, @NGB-Was-Taken, y otros.

### ✨ 0.19.3 — Mejoras y correcciones (detallado)
- Improvements:
  - Mejoras en la estabilidad y rendimiento de WebView multi-ventana.

- Removals:
  - Reversión de un cambio que introdujo una regresión en las zonas táctiles del lector.

- Fixes:
  - Arreglado crash de WebView introducido en v0.19.2.
  - Corregido padding inesperado en el lector tras interacciones.
  - Fix en scroll de long-strip reader cuando se tocaba consecutivamente.

### Contribuidores
- @TheUnlocked, @AntsyLich, @bapeey, y otros.

### ✨ 0.19.4 — Nuevas características, mejoras y fixes (detallado)
- New features:
  - Eliminación automática de descargas en Suwayomi tras leer (configurable por extensión).
  - Mostrar autores y artistas en resultados MAL.
  - Nuevas opciones de filtrado en Updates; `src:` prefix para buscar por source ID (incluye `src:local`).
  - Añadida opción para descargar páginas adyacentes completas al descargar capítulos.

- Improvements:
  - Optimización del uso de memoria (cache de covers), optimización de queries de MAL y mejoras en UX de descargas.
  - Actualizaciones en iconografía de trackers y ajustes menores en comportamientos de UI.

- Fixes:
  - Correcciones en instalador de Shizuku, fixes en migraciones, y resolución de diversos crashes (incluyendo descargas multi-capítulo en locales con idioma árabe).

### Contribuidores
- @cuong-tran, @Lolle2000la, @NarwhalHorns, @MajorTanya, @cpiber, @AntsyLich, y otros.

### ✨ 0.19.5 — Cambios principales (detallado)
- Changes:
  - Ajustes y mejoras en el reader para reintentos de carga de imagen y otras correcciones de fiabilidad.

- Fixes:
  - Corrección de regresión de rendimiento introducida en v0.19.4.
  - Fix en detección de claves duplicadas en duplicate detection y correcciones en MangaUpdates HTTP 4XX.
  - Correcciones en diálogos JS de WebView que aparecían tras cerrar pantalla y en acciones de extensiones que desaparecían tras instalar/desinstalar en la misma sesión.

### Contribuidores
- @AntsyLich, @leodyversemilla07, y colaboradores.

### ✨ 0.19.5.1 — Release del fork (parche)
  - Bumped a `0.19.5.1` en `app/build.gradle.kts`.
  - Corrección de condición en workflow `build_push.yml` para que las releases por tag se creen correctamente en Animetail.
  - Fix en DTO `MALAuthorNode` para evitar fallos de serialización cuando `first_name`/`last_name` faltan.
  - Limpiezas de warnings, ajustes i18n, y pequeñas mejoras locales.

### Cambios implementados en este repositorio durante la sesión
- Actualizado el guard del workflow de release: [.github/workflows/build_push.yml](.github/workflows/build_push.yml) — se corrigió la condición `github.repository` para que coincida con `Animetailapp/Animetail` y las releases por tag ya no se omitan.
- Ajuste en DTO MAL para evitar crashes: [app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/dto/MALManga.kt](app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/dto/MALManga.kt) — `MALAuthorNode` ahora tiene valores por defecto para `firstName`/`lastName`.
- Bumped de versión local: [app/build.gradle.kts](app/build.gradle.kts) a `0.19.5.1`.
- Creación y expansión del changelog: [CHANGELOG_0.19.0-0.19.5.1.md](CHANGELOG_0.19.0-0.19.5.1.md) (este archivo), con notas estilo Mihon y bullets traducidos al español.
- Limpiezas y correcciones menores en múltiples módulos: `app`, `core`, `source-api`, `source-local`, `domain`, `i18n` — correcciones de warnings, ajustes en recursos y fixes de migraciones aplicados durante la sesión.
- Commits aplicados en `master` con los cambios mencionados (workflow, DTO, changelog, y limpiezas diversas).


---

## Publicar la release en GitHub

Opciones seguras para publicar la release desde este repositorio local:

1) Crear y push del nuevo tag `v0.19.5.1` (recomendado si no quieres tocar tags existentes):

```bash
git add CHANGELOG_0.19.0-0.19.5.1.md
git commit -m "Add changelog: 0.19.2 → 0.19.5.1"
git tag -a v0.19.5.1 -m "Release v0.19.5.1"
git push origin HEAD
git push origin v0.19.5.1
```

2) (Opcional y peligroso) Re-referenciar `v0.19.5.0` al commit actual — esto reescribirá un tag público y podría afectar a usuarios:

```bash
git tag -d v0.19.5.0
git push --delete origin v0.19.5.0
git tag -a v0.19.5.0 -m "Release v0.19.5.0"
git push origin v0.19.5.0
```

Si quieres, puedo:
- Traducir y pulir los bullets al español formal y añadir enlaces a PRs/commits.
- Mover este contenido a `CHANGELOG.md` o a la descripción de una Release y pushearla.
- Ejecutar los comandos para crear el tag y pushearlo (necesitarás confirmación para sobrescribir tags remotos si eliges la opción 2).

