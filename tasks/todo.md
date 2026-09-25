# Plan — Detección automática de capítulos descargados (sin Internet)

## Objetivo
Detectar automáticamente (disco como fuente de verdad, sin red) los capítulos descargados,
tanto al abrir una ficha como **sin necesidad de abrirla** (al arrancar y al ver la Biblioteca).

## Estado
- [x] `reconcileDownloads(seriesId)` al abrir la ficha.
- [x] `reconcileAllDownloads()` global (sin red) lanzado:
  - al arrancar la app (`LocalLibraryAuditor` + EntryPoint),
  - al abrir la Biblioteca (`LibraryViewModel.init`).
- [x] Compilar + tests unitarios + lint (0 issues).
- [x] Instalado y verificado OFFLINE en dispositivo:
  - archivos pusheados al cap. 2 → reinicio sin red → Biblioteca muestra **"Descargados: 2/652"** sin abrir el webtoon.
  - al abrir la ficha, el cap. 1 con archivos se marcó "✓ 2 · Leer" automáticamente.
- [x] Commit/push (`b209741`, auxiliares; este paso commiteado).

## Archivos afectados
- `domain/repo/SeriesRepository.kt`, `data/repository/SeriesRepositoryImpl.kt`
- `data/repository/LocalLibraryAuditor.kt` (nuevo)
- `App.kt` (EntryPoint al arranque), `ui/library/LibraryViewModel.kt`
- `ui/series/SeriesViewModel.kt`, `data/db/dao/SeriesDao.kt`

## Notas de diseño
- Solo actúa sobre capítulos `NONE` (no toca FAILED ni los válidos). `COMPLETED` sin archivos → el lector ofrece "Re-descargar".
- `reconcileAllAsync()` es idempotente por proceso; corre en `Dispatchers.IO`.
- Guarda el estado en la fila del capítulo para que Biblioteca/ficha/lector lo reflejen.