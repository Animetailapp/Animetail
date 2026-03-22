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

---

## Cambios completos desde v0.18.1.5

Lista de commits (formato: SHA Fecha Autor Mensaje) desde `v0.18.1.5` hasta `HEAD`:

- 29e09c652 2026-03-22 dark25 chore(release): add implemented changes to changelog
- f5688d378 2026-03-22 dark25 chore(release): expand changelog with full Mihon v0.19.0ÔåÆv0.19.5 details
- 721250b66 2026-03-22 dark25 chore(release): expand changelog with Mihon v0.19.x notes
- 0f7aa5ec5 2026-03-22 dark25 chore(release): add changelog 0.19.2 ÔåÆ 0.19.5.1
- dc3a67b8c 2026-03-22 dark25 chore: update version to 0.19.5.1 and fix repository name casing in build_push.yml
- 9c36a4519 2026-03-22 dark25 chore(app): bump version to 0.19.5.0 (versionCode 133)
- fc73ecad1 2026-03-22 Dark25 Merge pull request #364 from Animetailapp/Mihon-merge-2
- 9af4b65b3 2026-03-22 dark25 run spotlessApply
- f1e0d9a5e 2026-03-22 dark25 refactor: Update variable names for clarity and remove deprecated permissions
- 250ded194 2026-03-22 dark25 fix: Update string formatting for player skip messages in multiple languages
- c63010d1b 2026-03-22 dark25 refactor: Add re2j library dependency and update restore_duration string formatting
- 6fbe7c8a9 2026-03-22 dark25 fix: Update junit-platform-launcher version reference in libs.versions.toml
- 8fe13f090 2026-03-21 dark25 run spotless
- 4026a84d4 2026-03-20 AntsyLich Replace preference getter functions with properties (#3091)
- 4226d5ea2 2026-03-20 AntsyLich Merge and cleanup version catalogs (#3103)
- f6268ff5f 2026-03-20 AntsyLich Release v0.19.5
- b1a7e1efd 2026-03-20 AntsyLich Revert "Fix thread starvation caused by not yielding or using an inappropriate thread pool (#2955)"
- c16267a07 2026-03-20 AntsyLich Revert "Fix cache invalidation isn't done at startup (#2970)"
- 4de8c2570 2026-03-20 Mend Renovate Update dependency com.google.firebase:firebase-bom to v34.11.0 (#3094)
- 9dcc82ae2 2026-03-20 dark25 feat: Integrate rich text editor for anime and manga notes with formatting options
- 2289c4507 2026-03-20 dark25 feat: Add new preferences for hiding missing chapters and updating manga titles
- a7fd49a31 2026-03-20 dark25 Merge remote-tracking branch 'origin/Mihon-merge-2' into Mihon-merge-2
- dc60da1b7 2025-10-12 LuftVerbot [skip ci] Fix next chapter button occasionally jumping to the last page of the current chapter (#1920)
- cba081263 2025-10-12 LuftVerbot [skip ci] Fix backup sharing from notifications not working when app is in background (#1929)
- a99fcf9be 2025-10-12 LuftVerbot Fix app bar action tooltips blocking clicks (#1928) [skip ci]
- 9998ad5b3 2025-10-12 LuftVerbot Fix mark existing duplicate read chapters as read option not working in some cases (#1944) [skip ci]
- d9b9f8e29 2025-10-12 LuftVerbot Fix user notes not restoring when manga doesn't exist in DB (#1945) [skip ci]
- 12188e39b 2024-07-02 Secozzi Rework Duplicate Dialog and Allow Migration
- 93eb065a2 2025-10-12 LuftVerbot [skip ci] Add user manga notes (#428)
- 46fd0bc6f 2026-03-19 Dark25 Update app/src/main/java/eu/kanade/presentation/more/settings/screen/debug/DebugInfoScreen.kt
- a89e7b900 2026-03-19 dark25 Run spotless
- d85d3dcf9 2026-03-19 Leodyver Semilla Fix extension actions disappearing after installing and uninstalling in same session (#3049)
- 87fbf7669 2026-03-19 AntsyLich Make retry in reader redownload image (#3089)
- 8c293e7bd 2026-03-19 Leodyver Semilla Fix WebView JavaScript dialogs popup after screen is closed (#3041)
- 6dfe53a3b 2026-03-19 AntsyLich Update CHANGELOG.md
- f85763bd8 2026-03-19 Leodyver Semilla MangaUpdates API content-type heade (#3021)
- 05b7a3c02 2026-03-19 Leodyver Semilla Fix tracker-induced duplicate key crash in duplicate detection (#3040)
- f62c3f5b8 2026-03-19 Mend Renovate Update dependency com.diffplug.spotless:spotless-plugin-gradle to v8.4.0 (#3086)
- fb38f1e70 2026-03-18 AntsyLich Switch to AndroidX bundled sqlite driver (#3082)
- 096483f42 2026-03-18 Mend Renovate Update softprops/action-gh-release action to v2.6.1 (#3072)
- e0da26bbf 2026-03-18 Mend Renovate Update paging.version to v3.4.2 (#3063)
- 2c83efd81 2026-03-18 Mend Renovate Update kotlin monorepo to v2.3.20 (#3074)
- f10b5f6b5 2026-03-18 Mend Renovate Update sqldelight to v2.3.2 (#3077)
- e72a94f02 2026-03-18 MajorTanya Address bundleOf deprecation (#3073)
- 89a0b2ca0 2026-03-16 Mend Renovate Update sqldelight to v2.3.1 (#3071)
- e75bb85f5 2026-03-16 Mend Renovate Update dependency io.kotest:kotest-assertions-core to v6.1.7 (#3062)
- d74e4b99e 2026-03-16 Mend Renovate Update moko to v0.26.1 (#3068)
- 55be79ff3 2026-03-15 Mend Renovate Update dependency com.squareup.okio:okio to v3.17.0 (#3070)
- 8429e9f57 2026-03-15 Mend Renovate Update softprops/action-gh-release action to v2.5.3 (#3064)
- 660427d17 2026-03-15 Mend Renovate Update dependency androidx.activity:activity-compose to v1.13.0 (#3065)
- 0f3237256 2026-03-15 Mend Renovate Update dependency androidx.core:core-ktx to v1.18.0 (#3067)
- 0b71ca940 2026-03-15 Mend Renovate Update dependency androidx.compose:compose-bom to v2026.03.00 (#3066)
- 0ae0085aa 2026-03-14 AntsyLich Bump workflows JDK to 21 (#3053)
- c8ec2a5cf 2026-03-10 AntsyLich Add installation id for feature flags (#3052)
- 37b2ed733 2026-03-06 Mend Renovate Update dependency androidx.compose:compose-bom to v2026.02.01 (#3009)
- 173700a41 2026-03-06 Mend Renovate Update dependency com.google.firebase:firebase-bom to v34.10.0 (#3006)
- dbb971b5e 2026-03-06 Mend Renovate Update dependency com.diffplug.spotless:spotless-plugin-gradle to v8.3.0 (#3029)
- e6096020e 2026-03-06 Mend Renovate Update dependency com.materialkolor:material-kolor to v5.0.0-alpha07 (#3024)
- d133b12bb 2026-03-06 Mend Renovate Update actions/dependency-review-action action to v4.9.0 (#3036)
- dc9ca7ec5 2026-02-26 AntsyLich Reapply "Fix cache invalidation isn't done at startup (#2970)"
- 830486eb3 2026-02-26 AntsyLich Reapply "Fix thread starvation caused by not yielding or using an inappropriate thread pool (#2955)"
- 8d2a63211 2026-03-19 Dark25 feat(player): Add preferences for double tap seek ovals, seek icon, and seek time display (#342)
- 06c9da7ff 2026-03-19 Dark25 feat: Add option to hide episode/chapter timestamps (#345)
- 7a3f5f798 2026-03-19 Dark25 feat: Add network stream player (#346)
- cbfd0b1e8 2026-03-19 Dark25 Merge pull request #362 from Animetailapp/mihon-merge
- 97e48f419 2026-03-19 dark25 feat: Add version reference for JUnit platform launcher in libs.versions.toml
- 813389e70 2026-03-19 dark25 feat: Add JUnit platform launcher as a test runtime dependency
- fbb54c9cc 2026-03-19 dark25 feat: Add JUnit platform launcher as a test runtime dependency
- 8a8f922bb 2026-03-19 dark25 Run spotless
- 588d906de 2026-03-19 dark25 feat: Improve image loading and fallback handling in Anime and Manga fetchers fix: Enhance episode loading path resolution refactor: Streamline local source directory retrieval in StorageManager chore: Update local anime and manga source file systems for better directory handling
- 2b71466f3 2026-03-17 dark25 chore: Update dependencies and improve storage management features
- cf1e8e261 2026-03-17 dark25 chore: Update dependencies and improve storage management features
- 47ecf3efc 2026-02-25 AntsyLich Release v0.19.4
- 0b19e617f 2026-02-25 AntsyLich Revert "Fix thread starvation caused by not yielding or using an inappropriate thread pool (#2955)"


