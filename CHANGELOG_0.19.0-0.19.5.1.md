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
- Translations update from Hosted Weblate (#2639)
- Migrated to the Android specific about libraries gradle plugin
- Handle reader cutout setting with Insets to support Android 15+ (#2640)
- Make reader edge-to-edge (#1908)
- Fix reader tap zones triggering after scrolling was stopped by the user (#2518)
- Update Suwayomi tracker to use GraphQL API instead of REST API (#2585)
- Added proper multi window support in WebView (#2584)
- Add option to customize concurrent downloads, increase page concurrency (#2637)
- Add subtitle support to slider preference and general cleanup (#2635)
- Fix migration "Attempt to invoke virtual method" crash (#2632)
- (varias actualizaciones de dependencias y traducciones)

### 0.19.3
- Fix extra padding appearing in reader after user interactions (#2669)
- Improve WebView multi-window UX (#2662)
- Fix long strip reader not scrolling on consecutive taps (#2650)
- Fix WebView crash introduced in v0.19.2 (#2649)
- (varias actualizaciones de dependencias y traducciones)

### 0.19.4
- Fix extension install/update stuck at pending (#3000)
- Add all pages of adjacent chapters in the UI instead of only the first or last three (#2995)
- Optimize tracked library filter (#2977)
- Fix cache invalidation and other migration fixes
- (varias actualizaciones de dependencias)

### 0.19.5.0
- Actualizaciones automáticas de dependencias y acciones de GitHub
- Correcciones en WebView, migraciones, y mejoras en la gestión de descargas

### 0.19.5.1
- Bumped to 0.19.5.1 (versión de app en `app/build.gradle.kts`)
- Correcciones específicas del fork:
	- Corrección de la condición del workflow para crear releases en Animetail
	- Manejo de campos nullables en DTO MAL (`MALAuthorNode`) — `first_name`/`last_name` por defecto vacíos
	- Limpiezas y arreglos de warnings de Kotlin/Compose y ajustes i18n

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

