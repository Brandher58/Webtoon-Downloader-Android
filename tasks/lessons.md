# Lecciones

- Los tests de instrumentación usan un contexto/almacén distinto al de la app real en este
  dispositivo; NO usar androidTest para sembrar archivos/datos que la app deba ver.
- No usar `@Upsert` de Room con listas en esta versión: no inserta filas nuevas (sí lo hace `@Insert(REPLACE)`).
  Al persistir capítulos/páginas, usar lote con REPLACE y validar con test instrumentado.
- `@Insert(IGNORE)` en capítulos tampoco insertó: la escritura correcta fue `@Insert(REPLACE)` + merge de estados.
- El `@Upsert`/REPLACE sobre la fila de SERIE borra por CASCADE sus capítulos; la serie debe escribirse
  con `INSERT si no existe` + `UPDATE de metadatos` (nunca eliminar la fila).
- Respuestas vacías transitorias de la API NO deben sobrescribir una caché buena (DataStore): guardar solo si no vacío.
- Evitar variables PowerShell reservadas (`$HOME`) y `Select-String` con comillas anidadas (escribir scripts .ps1 en archivo).
- `adb shell` binary (screencap/tar/cat) con redirección de PowerShell corrompe; usar `cmd /c` para binario.
- En el Samsung, el modo avión mantiene Wi-Fi activo: para forzar offline real, además `svc wifi/data disable`.
- Verificación clave del jugador: siempre probar el flujo real en el dispositivo; la compilación no basta.