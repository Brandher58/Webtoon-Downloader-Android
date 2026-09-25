# Webtoon Downloader (Android)

Aplicación para Android que te permite **descargar webtoons y manhwas de Webtoons** y
**leerlos sin conexión**, sin publicidad y sin estar conectado.

- 📲 **Totalmente en tu teléfono**: no necesitas PC, ni Python, ni termux.
- 🌐 Funciona con **Webtoons** (el sitio oficial).
- 🆓 Leer los capítulos que elijas, cuando quieras, incluso en el avión.

> Este proyecto es una versión para Android de la idea de [Webtoon-Downloader](https://github.com/Brandher58/Webtoon-Downloader)
> (escritorio), rediseñada para que todo ocurra en el propio dispositivo.

---

## Qué puedes hacer

- 🔎 **Buscar** webtoons por nombre o **agregarlos por URL**.
- 💾 **Descargar** capítulos para leerlos sin internet (uno, un rango o todos).
- 📚 **Biblioteca** con tu progreso y "Continuar leyendo".
- 📖 **Lector vertical** con zoom, pantalla completa y memoria de posición.
- 📦 **Exportar** lo que quieras (imágenes, CBZ o PDF) a la carpeta que tú elijas, aunque no lo hayas
  descargado: la app usa lo que ya tienes y descarga del web lo que falte.
- 🌍 Tu **Biblioteca siempre refleja lo que tienes descargado**, incluso después de reinstalar la app.

---

## Cómo instalarla (APK)

1. Abre la sección **Releases** de este repositorio.
2. Descarga `app-release.apk` de la última versión.
3. Ábrelo en tu teléfono y permite **"Instalar apps desconocidas"** cuando Android te lo pida.

Requisitos: **Android 7.0 o superior**.

> 💡 Sugerencia: guárdalo también en tu cuenta de almacenamiento en la nube para tener el APK a mano.

---

## Uso rápido

| Quiero… | Cómo |
|---|---|
| Leer un webtoon | Busca por nombre o pega el enlace del capítulo en "Agregar por URL". |
| Llevarlo offline | Abre la serie → elige capítulos → **Descargar**. |
| Ver mis descargas | Pestaña **Biblioteca** (solo muestra lo que tienes descargado). |
| Leer sin internet | Abre la serie → **Leer**. El lector recuerda dónde ibas. |
| Llevarlo a otro lado | **Exportar** → elige formato → elige carpeta. |
| Liberar espacio | Borra capítulos o la serie completa desde la ficha/Biblioteca. |

---

## Para desarrolladores

Repositorio Android nativo (**Kotlin + Jetpack Compose**), listo para compilar con [Android Studio](https://developer.android.com/studio).

```bash
./gradlew :app:assembleDebug      # APK de depuración
./gradlew :app:testDebugUnitTest  # pruebas unitarias
./gradlew :app:assembleRelease    # APK de release (requiere keystore.properties)
```

Para generar un release firmado consulta `tasks/lessons.md` (keystore, versionado y publicación en GitHub).

### Nota sobre el contenido

La app usa **webtoons.com** (contenido público) y está pensada para **uso personal**. Respeta los
términos de uso del sitio; la idea es tener tus series favoritas a mano cuando no hay internet.

---

## Licencia

MIT.