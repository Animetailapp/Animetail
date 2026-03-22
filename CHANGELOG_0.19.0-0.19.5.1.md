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
### ✨ 0.19.2 — Novedades y arreglos destacados
- Nuevas opciones de concurrencia para descargas; por defecto se incrementó el número de descargas paralelas de páginas.
- Mejor soporte multi-ventana en WebView y mejoras en el spoofing del header `X-Requested-With`.
- Mejoras en lector: edge-to-edge, correcciones de zonas táctiles y soporte para recorte en Android 15+.
- Correcciones de migraciones y varios arreglos de estabilidad (incluye fixes en migraciones y errores de scrollbar/animator).
- Actualizaciones masivas de dependencias y traducciones.

Lista resumida:
- Personalización del número de descargas concurrentes.
- Soporte multi-ventana en WebView.
- Arreglos en la UI del lector y corrección de errores de migración.

### 0.19.3
### ✨ 0.19.3 — Mejoras y correcciones
- Mejoras adicionales en WebView multi-ventana y estabilidad.
- Reversión de un cambio que rompía el comportamiento de zonas táctiles en el lector; fixes relacionados con padding y scroll en lectores long-strip.
- Actualizaciones de dependencias y correcciones menores.

### 0.19.4
### ✨ 0.19.4 — Nuevas características y mejoras
- Funcionalidad: eliminación automática de descargas en Suwayomi tras leer, configurable por extensión.
- Añadido soporte para mostrar autores/artistas en resultados MAL; filtros nuevos en Updates; opción para añadir páginas adyacentes completas al descargar capítulos.
- Mejoras de rendimiento y optimizaciones en búsquedas y filtros de biblioteca.
- Múltiples correcciones de estabilidad y migraciones.

### 0.19.5.0
### ✨ 0.19.5 — Cambios principales
- Correcciones de rendimiento y regresiones detectadas en 0.19.4.
- Fixes en detección de duplicados y en integración con MangaUpdates/servicios remotos.
- Mejoras en la robustez de WebView y acciones/CI.

### 0.19.5.1
### ✨ 0.19.5.1 — Release del fork (parche)
- Bumped a `0.19.5.1` en `app/build.gradle.kts`.
- Correcciones específicas del fork:
  - Corrección de la condición del workflow para crear releases en Animetail.
  - Manejo de campos nullables en DTO `MALAuthorNode` (`first_name`/`last_name` ahora por defecto vacíos).
  - Limpiezas y arreglos de warnings de Kotlin/Compose, y correcciones i18n.

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

