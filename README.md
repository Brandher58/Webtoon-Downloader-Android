# Webtoon Downloader (Android)

Aplicación **100% nativa para Android** (Kotlin + Jetpack Compose) para descargar webtoons/manhwas
desde el sitio oficial de [Webtoons](https://www.webtoons.com/) y leerlos **sin conexión**.

> Inspirada en la idea y el flujo del proyecto de escritorio
> [Webtoon-Downloader](https://github.com/Brandher58/Webtoon-Downloader) (Python), pero
> rediseñada desde cero para Android: la descarga y la lectura ocurren **en el propio dispositivo**,
> sin PC, sin Python, sin Termux y sin servidor local.

## Características

- **Agregar por URL** o **buscar por nombre**; recomendaciones de la portada en el idioma del teléfono.
- **Lista de capítulos** con selección individual, por rango o todos.
- **Descarga local** (imágenes) para leer sin conexión; cola de descargas con progreso por capítulo y global.
- **Pausar / reanudar / cancelar**; reanudación de descargas interrumpidas y omisión de lo ya descargado.
- **Exportar** una serie a Imágenes / CBZ / PDF a la carpeta que elijas (SAF), solo cuando lo necesites.
- **Lector vertical** con ajuste al ancho, zoom (doble toque y pinch), pantalla completa,
  memoria de posición y paso automático al capítulo siguiente.
- **Biblioteca** con progreso, "Continuar leyendo" y borrado de capítulos/series.

## Descargar e instalar (APK)

1. Ve a la sección **Releases** del repositorio.
2. Descarga el archivo `app-release.apk` de la última versión.
3. En el teléfono, abre el APK y permite **"Instalar apps desconocidas"** para tu navegador/gestor de archivos.

Requisitos: **Android 7.0 (API 24)** o superior.

## Cómo se genera un release (APK descargable)

El APK de release se firma con un keystore. La configuración de firma se lee de `keystore.properties`
(no versionado). Pasos:

```bash
# 1. Crear el keystore (solo la primera vez)
keytool -genkeypair -v -keystore keystore/release.jks -alias webtoondl \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Crear keystore.properties en la raíz con:
#    storeFile=keystore/release.jks
#    storePassword=...
#    keyAlias=webtoondl
#    keyPassword=...

# 3. Compilar el APK de release
./gradlew :app:assembleRelease
# Salida: app/build/outputs/apk/release/app-release.apk
```

Publicarlo en GitHub (elige una opción):

- **Web:** Repo → *Releases* → *Draft a new release* → etiqueta `vX.Y.Z` → adjunta `app-release.apk` → *Publish release*.
- **CLI (`gh`):**
  ```bash
  gh release create v1.0.0 app/build/outputs/apk/release/app-release.apk \
    --title "v1.0.0" --notes "Primera versión"
  ```

## Compilar desde el código

Requisitos: **JDK 17+** y **Android SDK** (compileSdk 36).

```bash
./gradlew :app:assembleDebug      # APK de depuración
./gradlew :app:testDebugUnitTest  # pruebas unitarias
./gradlew :app:assembleRelease    # APK de release (requiere keystore.properties)
```

## Arquitectura

```
UI (Compose) → ViewModels → Repositorios → { Fuentes (Sources), Room, Almacenamiento, Descargas }
```

- **Fuentes/adaptadores** (`data/source`): cada fuente implementa `Source`
  (`canHandle`, `fetchSeries`, `fetchChapters`, `fetchPages`, `search`, `homeSections`).
  Hoy incluye **Webtoon**; añadir otra fuente no requiere tocar la app.
- **Motor de descargas** (`download`): cola persistida en Room, concurrencia acotada, reintentos con
  backoff, servicio en primer plano y empaquetado CBZ/PDF.
- **Almacenamiento** (`data/storage`): carpeta privada de la app (sin permisos), escritura atómica.
- **Lector** (`ui/reader`): scroll vertical con Coil y zoom nativo en Compose.

Estructura de archivos descargados:

```
Android/data/com.brandher.webtoondl/files/library/
 └─ webtoon_{id}/
     └─ Chapter 001/
         ├─ 0001.jpg
         └─ ...
```

## Fuentes y legalidad

Incluye únicamente la fuente **webtoons.com** (contenido público). El sistema de `Source` está preparado
para añadir más fuentes solo si son técnica y legalmente apropiadas. Respeta los términos de uso del sitio
y usa la aplicación de forma personal.

## Licencia

MIT.
