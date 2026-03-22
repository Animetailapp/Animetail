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
### Merges importantes
Estos merges integran funciones y correcciones relevantes que se incorporaron durante el ciclo:

- fc73ecad1 2026-03-22 Dark25 Merge pull request #364 from Animetailapp/Mihon-merge-2
- a7fd49a31 2026-03-20 dark25 Merge remote-tracking branch 'origin/Mihon-merge-2' into Mihon-merge-2
- cbfd0b1e8 2026-03-19 Dark25 Merge pull request #362 from Animetailapp/mihon-merge
- c613a4ef6 2026-02-23 Dark25 Merge pull request #356 from mklive/concurrent-fix
- 4de99bde7 2026-02-22 Dark25 Merge pull request #355 from mklive/webview-fix
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
Detalle estilo Mihon — resumen de cambios (v0.18.1.5 → HEAD)

New features
- Integración de editor de texto enriquecido para notas de anime/manga (rich text). (#9dcc82ae)
- Preferencias nuevas: ocultar capítulos faltantes y actualización automática de títulos. (#2289c450)
- Nuevas opciones de reproducción: doble-tap seek, ocultar timestamps y soporte para streaming de red. (#8d2a632, #06c9da7, #7a3f5f7)

Improvements
- Soporte multi-ventana y mejor manejo de popups en WebView; mejoras de carga y fallback de imágenes. (PRs: #355, #4de99bde7)
- Aumentos y optimizaciones en concurrencia de descargas, almacenamiento y gestión de archivos locales. (#c613a4ef6, #a30ee0ff)
- Optimización de consultas MAL y mejoras de rendimiento en búsquedas y cache de covers. (#a2d0e322)
- Actualizaciones de dependencias y mejoras en Actions/CI (Spotless, Renovate, compose-bom, firebase-bom, etc.).

Fixes
- Reintentos en el reader para redescarga de imágenes y correcciones en scroll/zonas táctiles. (#87fbf766, #cbfd0b1e8)
- Corrección de diálogos JS de WebView que aparecían tras cerrar pantalla y fixes en acciones de extensiones que desaparecían. (#8c293e7b, #d85d3dcf)
- Fix en detección de claves duplicadas y crash por trackers en duplicate detection. (#05b7a3c02)
- Correcciones en migraciones, backup-sharing en background y restoring de notas de usuario. (#cba081263, #d9b9f8e29)

Infra y tooling
- Bumped de versión a `0.19.5.1` y corrección del guard del workflow (`.github/workflows/build_push.yml`) para releases por tag.
- Actualizaciones de acciones/acciones del release (`softprops/action-gh-release`), JDK en workflows y otras dependencias de build.

Merges relevantes
- Merge PR #364, #362 y otros merges de integración con Mihon (ver PRs #364, #362, #356, #355).

Contribuidores destacados
- @AntsyLich, @Dark25, @LeodyverSemilla07, @TheUnlocked, @mklive, @NarwhalHorns, y colaboradores.

Apéndice: lista completa de commits disponible en la sección de historial (raw) si necesitas referencias SHA individuales.


