# Animetail — Release notes: 0.18.1.5 → 0.19.5.1

Estas notas están estructuradas al estilo de Mihon: por versión, con una visión corta de los cambios más importantes y un apartado de merges relevantes.

## Resumen general
- Mejoras en el lector: edge-to-edge, ajustes de zonas táctiles, redescarga de imágenes y correcciones de scroll.
- Mejoras en WebView: soporte multi-ventana, popups mejor tratados y menos crashes relacionados con diálogos JavaScript.
- Descargas y almacenamiento: concurrencia configurable, fixes de estados pendientes y mejoras en gestión de archivos locales.
- Tracker y búsqueda: soporte para autores/artistas en MAL, optimización de consultas y correcciones en duplicate detection.
- Infra y tooling: actualizaciones de dependencias, workflows y acciones de release.

## 0.19.5.1
### New features
- Ajustes internos del fork para consolidar la release 0.19.5.1.

### Improvements
- Bump de versión a 0.19.5.1.
- Corrección del guard de `.github/workflows/build_push.yml` para que las releases por tag se creen correctamente en Animetail.

### Fixes
- Fix en `MALAuthorNode` para evitar fallos de serialización cuando `first_name` o `last_name` faltan.
- Limpieza de warnings y pequeños ajustes locales en recursos e i18n.

## 0.19.5
### New features
- Mejoras en el reader para hacer más fiables los reintentos de carga de imagen.

### Improvements
- Ajustes en dependencias y en el flujo de publicación.
- Limpieza de catálogos de versiones y propiedades de preferencias.

### Fixes
- Corrección de regresión de rendimiento introducida en 0.19.4.
- Fixes en duplicate detection y en errores HTTP de MangaUpdates.
- Correcciones en diálogos JavaScript de WebView y en acciones de extensiones que desaparecían tras instalar o desinstalar en la misma sesión.

## 0.19.4
### New features
- Eliminación automática de descargas en Suwayomi tras leer.
- Mostrar autores y artistas en resultados de MAL.
- Nuevos filtros en Updates, incluyendo `src:` para buscar por source ID.
- Opción para descargar páginas adyacentes completas al descargar capítulos.

### Improvements
- Optimización del uso de memoria y de consultas de MAL.
- Mejoras en UX de descargas y en iconografía de trackers.
- Actualizaciones de dependencias y acciones de CI.

### Fixes
- Correcciones en instalador de Shizuku.
- Fixes en migraciones y varios crashes, incluidos casos en locales con idioma árabe.

## 0.19.3
### Improvements
- Mejoras de estabilidad y rendimiento en WebView multi-ventana.

### Fixes
- Arreglo de crash de WebView introducido en 0.19.2.
- Correcciones de padding inesperado en el lector y del scroll en long-strip reader.
- Reversión de un cambio que introducía regresión en zonas táctiles del lector.

## 0.19.2
### New features
- Configuración de descargas concurrentes para fuentes y páginas.
- Opción para restringir nombres de archivo a ASCII.
- Soporte multi-ventana en WebView.

### Improvements
- Incremento por defecto del número de descargas de páginas de 2 a 5.
- Mejor spoofing del header `X-Requested-With`.
- Actualizaciones automáticas de dependencias y mejoras en Actions/CI.

### Fixes
- Correcciones en la UI del lector para Android 15+.
- Fixes en migraciones y scrollbar cuando la animación está desactivada.
- Corrección del estado `pending` en descargas de extensiones.
- Mejor manejo de incognito desde notificaciones.

## Merges relevantes
- Merge PR #364: integración de Mihon-merge-2.
- Merge PR #362: integración de mihon-merge.
- Merge PR #356: concurrent-fix.
- Merge PR #355: webview-fix.

## Contribuidores destacados
- @AntsyLich, @Dark25, @LeodyverSemilla07, @TheUnlocked, @mklive, @NarwhalHorns, @LuftVerbot, @MajorTanya, @Cuong-Tran, y colaboradores.

## Nota
- Si quieres, puedo convertir este mismo contenido a `CHANGELOG.md` o dejarlo listo como descripción de la release en GitHub.


