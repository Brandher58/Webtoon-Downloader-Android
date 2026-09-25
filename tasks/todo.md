# Plan — Detección automática de capítulos descargados (sin Internet)

## Objetivo
Que al abrir un webtoon, la app detecte **automáticamente** (usando el disco como fuente de verdad,
sin red) que un capítulo ya está descargado aunque Room diga `NONE`, y lo muestre como `✓ ` listo para leer.

## Estado
- [x] `SeriesRepository.reconcileDownloads(seriesId)` (nuevo): audita disco y marca COMPLETED los `NONE` con archivos (sin red).
- [x] `SeriesRepositoryImpl` inyecta `StorageManager`.
- [ ] Compilar + tests unitarios.
- [ ] Instalar en el dispositivo.
- [ ] Verificar en vivo: crear archivos de un capítulo NONE → abrir la serie OFFLINE → debe marcar `✓ N` automáticamente.
- [ ] Commit/push.

## Archivos afectados
- `domain/repo/SeriesRepository.kt`
- `data/repository/SeriesRepositoryImpl.kt`
- `ui/series/SeriesViewModel.kt` (init: auditoría una vez al abrir, sin bloquear)

## Notas de diseño
- Solo actúa sobre capítulos `NONE` (seguro). `FAILED` queda para el motor; `COMPLETED` sin archivos → el lector ofrece "Re-descargar" (ya implementado).
- La auditoría es local y rápida (listar directorios); no toca red ni espera.