---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-es.html
---
<div lang="es" dir="ltr" markdown="1">

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![Licencia](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

{% include lang-switcher.html doc="README" dir="/docs/" current="es" %}

**📦 Descarga:** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Consíguelo en IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

¿Vas a instalar el APK directamente? Android avisa sobre un paquete que no ha visto antes - [por qué aparece el aviso y qué debes pulsar](INSTALL_TRUST.md).

**📘 Documentación de usuario:** [guías paso a paso de cada función, con buscador](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## Sobre el proyecto

**FastMediaSorter v2** es una shell completa para un dispositivo Android. Toma el control de la pantalla de inicio, reproduce tus archivos multimedia, abre transmisiones en directo, inicia tus aplicaciones, se comunica con tu reloj, vigila el dispositivo y gestiona todos los archivos que tienes - en carpetas locales, en unidades de red (SMB, SFTP, FTP) y en almacenamiento en la nube (Google Drive, OneDrive, Dropbox).

Se apoya en ocho pilares: shell del dispositivo, reproductor multimedia, transmisiones en directo, lanzador de aplicaciones, sustituto de las apps de fábrica, compañero en el reloj, monitorización del dispositivo y gestor de archivos completo. Ordenar archivos entre todas esas fuentes es el origen de la app, y sigue siendo la base sobre la que se construye todo lo demás - pero ya no es lo único que hace.

Este manual sigue ahora el mismo vocabulario público que el inventario canónico de funciones en [FEATURES.md](FEATURES.md) y el mapa de documentación en [DOCS_MAP.md](DOCS_MAP.md). Usa esas dos páginas como fuente de verdad actual para la historia de la app, las ediciones disponibles y la superficie de funciones vigente.

## Versión para Windows 🖥️

¿Buscas una solución de escritorio? Echa un vistazo a **Fast Media Sorter for Windows** (antes FastMediaSorter LITE) - una aplicación ligera de Windows Forms para ordenar, ver y gestionar rápidamente archivos de imagen y vídeo:

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [Cómo publicar carpetas del PC en Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - comparte las carpetas de tu PC con la app por SFTP (importación desde el companion / escaneo de código QR en el teléfono).

Incluye las siguientes funciones:

- Navegación rápida por carpetas grandes de imágenes y vídeos
- Modos de presentación de diapositivas y visualización aleatoria de archivos
- Seguimiento de archivos y carpetas recientes
- Operaciones de archivos: mover, copiar, renombrar y eliminar
- Panel de imágenes para una navegación visual rápida
- Atajos de teclado personalizables para un flujo de trabajo eficiente
- Compatibilidad multilingüe (inglés/ruso)
- Compatible con Windows 7/10/11 con .NET Framework 4.8

## Tabla de contenidos

- [Descarga](#download-)
- [Ediciones](#editions-)
- [Funciones principales](#key-features)
- [Formatos multimedia compatibles](#supported-media-formats-)
- [Capturas de pantalla](#screenshots-)
- [Escenarios de uso](#usage-scenarios-)
- [Documentación](#documentation-)
- [Compañero Wear OS](#wear-os-companion-)
- [Instrucciones de compilación](#build-instructions)
- [Pruebas](#testing-)
- [Primeros pasos](#first-steps-quick-usage-guide-)
- [Pila tecnológica](#technology-stack)

## Ediciones 🎯 {#editions-}

FastMediaSorter v2 se distribuye en **siete ediciones** - cinco para teléfonos y tablets de uso diario (Standard, Lite, Photos, Legacy, FOSS) más dos compilaciones para visores y carga lateral, VR y noLegal. La cuadrícula de capacidades canónica se genera a partir de la propia compilación en [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md):

| Edición | Descripción | Notas |
|--------|-------------|-------|
| **Standard** | Versión con todas las funciones | El conjunto de funciones más amplio, para multimedia, documentos, OCR, traducción y acceso a la nube |
| **Lite** | Versión ligera | Solo archivos locales - vídeo, audio e imágenes; sin fuentes de red, nube, documentos ni Streams |
| **Photos** | Versión centrada en fotos | Solo imágenes, con SMB/FTP/SFTP y nube; sin vídeo ni audio |
| **Legacy** | Versión centrada en compatibilidad | Mismo conjunto de funciones que Standard, incluidos SMB/FTP/SFTP y nube (Google Drive, Dropbox, OneDrive); creada para Android 6/7 (API 23+) |
| **FOSS** | Versión del catálogo F-Droid | Sin SDK propietarios: multimedia local, documentos, EPUB y SMB/FTP/SFTP; sin nube, sin Streams, sin OCR, sin traducción y sin compañero Wear OS |
| **VR** | Compilación para visores, limpia de tienda | Conjunto multimedia completo para visores; sin Google Cast y sin compañero Wear OS |
| **noLegal** | Compilación de carga lateral | Todo lo de Standard más el reproductor inmersivo OpenXR y extras exclusivos de carga lateral |

### ¿Qué edición debería descargar?

- **Standard** ⭐ **(Recomendada)**: la mejor opción por defecto para la mayoría de usuarios
- **Lite**: prefiérela si quieres un paquete más ligero y una configuración más sencilla
- **Photos**: prefiérela para flujos de trabajo centrados en fotos
- **Legacy**: elígela para dispositivos Android 6/7 (API 23+) - incluye red y nube
- **FOSS**: elígela desde el catálogo F-Droid cuando quieras una compilación libre de SDK propietarios
- **VR**: elígela para un visor XR - la compilación de tienda sin Cast ni compatibilidad con Wear
- **noLegal**: solo carga lateral - elígela cuando necesites el reproductor inmersivo OpenXR

Para conocer la disponibilidad exacta de funciones por edición, usa la documentación canónica:

- [Inventario de funciones (canónico)](FEATURES.md)
- [How-To (tabla de disponibilidad de funciones)](HOW_TO-es.md)
- [Inicio rápido (selector de edición)](QUICK_START-es.md)
- [Limitaciones del programa](LIMITATIONS.md)

> 🧭 **Primer inicio:** justo debajo del selector de idioma, la app te permite elegir un **perfil de dispositivo** (teléfono, tablet, TV, coche, marco de fotos, VR y más) que adapta los valores iniciales a tu caso - se puede cambiar en cualquier momento en Ajustes. Consulta [Primer inicio: elige tu perfil de dispositivo](QUICK_START-es.md#first-launch-choose-your-device-profile-30-seconds-).

## Descarga 📥 {#download-}

📲 **[Consíguelo en Google Play](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**Los archivos APK compilados NO se almacenan en este repositorio de GitHub.** Todas las compilaciones están disponibles en **Google Drive**:

🔗 **[Descargar todas las compilaciones desde Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Edición | Nombre de archivo | Descripción |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Todas las funciones (nube, OCR, EPUB, traducción) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Solo multimedia local (vídeos, audio, imágenes; sin red, nube, documentos ni Streams) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Solo imágenes, con red (SMB/FTP/SFTP) y nube |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Mismas funciones que Standard, incluidas red (SMB/FTP/SFTP) y nube; Android 6/7 (API 23+) |

> **Nota**: todas las compilaciones se suben automáticamente a Google Drive tras cada compilación exitosa.
>
> 🔐 **Contraseña del ZIP: `1`** (los archivos APK se empaquetan en archivos ZIP protegidos con contraseña para evitar las restricciones de Google Drive)

## Capturas de pantalla 📱 {#screenshots-}

| Pantalla principal | Acciones de archivo | Ajustes |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **Vista del reproductor** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

Imágenes a tamaño completo:

- [Pantalla principal](images/Screenshot_20251109_000251.png)
- [Acciones de archivo](images/Screenshot_20251109_000314.png)
- [Ajustes](images/Screenshot_20251109_000323.png)
- [Vista del reproductor](images/Screenshot_20251114_184930.png)

## Funciones principales {#key-features}

- 🗂️ **Interfaz unificada:** ve y gestiona archivos de todas las fuentes en una sola ventana.
- ⚡ **Clasificación rápida:** copia o mueve archivos a carpetas de destino preconfiguradas con un solo toque.
- ⭐ **Sistema de favoritos:** marca los archivos importantes como favoritos y accede a ellos rápidamente desde una pestaña dedicada que agrupa los favoritos de todas las fuentes.
- 🔒 **Protección por PIN:** protege recursos individuales con códigos PIN de acceso para evitar la navegación y edición no autorizadas.
- ⚙️ **Configuración por recurso:** personaliza el intervalo de la presentación, la profundidad de escaneo (subcarpetas) y la generación de miniaturas para cada carpeta individualmente.
- 🧭 **Configuración del perfil de dispositivo:** elige un perfil de primer inicio para teléfonos, tablets, TV/reproductores multimedia, unidades centrales de coche, reproductores multimedia, marcos de fotos, reproductores de audio, lectores de libros electrónicos, visores VR o valores personalizados; la app aplica los valores predeterminados de seguridad, pantalla, contenido y prioridad de comandos que correspondan.
- 📋 **Recursos inteligentes predefinidos:** recursos virtuales integrados - **Toda la música**, **Todos los vídeos**, **Todas las fotos** - que agrupan la multimedia de todo tu dispositivo sin ninguna configuración. Accede al instante a toda tu biblioteca multimedia sin añadir carpetas individuales manualmente.
- 🖥️ **Compatibilidad con red y nube:** trabaja con archivos en tus unidades de red (SMB con escaneo automático de red), servidores SFTP, FTP y en almacenamiento en la nube (Google Drive, Dropbox, OneDrive).
- 🖼️ **Visualización flexible:** muestra los archivos en una cuadrícula personalizable o en una lista detallada con paginación para colecciones grandes (más de 1000 archivos).
- ▶️ **Reproductor integrado:** reproducción de vídeo y audio, visualización de imágenes y GIF sin salir de la app. Admite presentación de diapositivas y zoom a pantalla completa.
- 🧩 **Integración como reproductor predeterminado:** unos interruptores opcionales de reproducción permiten que FastMediaSorter actúe como gestor multimedia del sistema para los intents de abrir/compartir (ACTION_VIEW / ACTION_SEND), y dirigen los eventos de activación por botón multimedia de hardware al servicio de reproducción de audio.
- 🗣️ **Assistant AppFunctions (Android 16+):** la app declara acciones invocables por el asistente - buscar tu multimedia, abrir un archivo o abrir una carpeta del ordenador - para que el asistente del sistema de tu dispositivo pueda encontrar y abrir tu contenido con solo pedirlo.
- 🎛️ **Compatibilidad con botones de hardware:** los controles del volante, los botones del auricular y las teclas multimedia físicas (Reproducir/Pausa, Siguiente, Anterior) son totalmente compatibles a través del servicio de audio en segundo plano - sin necesidad de tocar la pantalla.
- 📻 **Emisiones de Internet (pantalla Streams):** reproduce radio por Internet (http/https, Icecast/Shoutcast con metadatos ICY de reproducción actual), emisiones HLS/DASH y fuentes RTSP directamente desde una pantalla Streams dedicada. Añade URL manualmente, importa una lista de reproducción `.m3u` o descarga un catálogo curado de FastMediaSorter. Fija tus favoritas arriba; filtra por categoría e idioma. Audio en línea: la radio se reproduce desde la lista mediante un mini-control inferior fijo mientras la lista se mantiene desplazable. El vídeo y RTSP se abren en el reproductor a pantalla completa. Disponible en Standard, Legacy, VR y noLegal; ausente en Lite y Photos.
- 🎵 **Compatibilidad con letras:** ve las letras de la canción que se está reproduciendo. Las busca automáticamente por metadatos (Artista/Título) usando `api.lyrics.ovh`, con respaldo por análisis del nombre del archivo.
- 🎶 **Música de fondo en la presentación:** reproduce música de fondo durante las presentaciones de imágenes. Selecciona cualquier recurso de audio como fuente de música, con reproducción aleatoria de pistas, control de volumen y visualización del nombre de la pista. Toca el nombre de la pista para saltar a otra pista aleatoria. Funciona sin problemas con archivos de red y de la nube.
- ✏️ **Edición de imágenes:** rota, voltea, aplica filtros (escala de grises, sepia, negativo), ajusta brillo/contraste/saturación - tanto para archivos locales como de red.
- 🗂️ **Compatibilidad con archivos binarios:** ve y gestiona archivos binarios (ZIP, RAR, APK, ISO, EXE, DLL, etc.) con miniaturas generadas que muestran la extensión del archivo. Menú contextual con Compartir/Abrir con/Copiar/Mover/Renombrar/Eliminar. Disponible solo en el "modo Todos los archivos".
- ⌨️ **Teclado, ratón y mando:** entrada completa de teclado, ratón y mando en todas las pantallas - Explorar, Reproductor, Ajustes, diálogos. Totalmente reasignable desde Ajustes → Gestión → Controles y asignación de teclas; pulsa F1 en cualquier pantalla para ver una superposición de ayuda propia de esa pantalla. Navegación de listas con D-pad; menú contextual de clic derecho y efectos al pasar el ratón.
- 🔍 **Ordenación y filtrado:** ordena los archivos por nombre, fecha, tamaño y duración. Aplica filtros para una búsqueda rápida. Compatibilidad con archivos ocultos (que empiezan por `.`) mediante un interruptor dedicado.
- ↩️ **Deshacer y papelera:** posibilidad de deshacer la última acción (copiar, mover, eliminar) con eliminación reversible a la carpeta `.trash/`. Incluye la función "Vaciar papelera" para los recursos.
- 🎨 **Interfaz moderna:** admite temas claro y oscuro, controles intuitivos, Material Design 3.
- 💾 **Caché inteligente:** carga de metadatos de vídeo en dos etapas (1 MB inicial, 5 MB ampliada) y caché de miniaturas configurable (2 GB por defecto, hasta 16 GB).
- 📄 **Visor de documentos:** visor integrado para archivos de texto (.txt, .md, .log, .json, .xml) y documentos PDF con zoom, desplazamiento y navegación por gestos.
- 📚 **Lector de libros electrónicos EPUB:** lector EPUB nativo con navegación por capítulos, tabla de contenidos, control del tamaño de fuente, búsqueda dentro del libro y compatibilidad con tema claro/oscuro. Funciona con archivos locales y de red.
- 📥 **Descargar y abrir:** descarga archivos de red (SMB/SFTP/FTP) al almacenamiento local y ábrelos en aplicaciones externas con seguimiento del progreso.
- 🌐 **Traducción automática:** traduce al instante el texto de imágenes, PDF y archivos de texto, todo en el propio dispositivo: **Tesseract** lee el texto en escritura latina y cirílica, y Google ML Kit lo traduce. Admite tanto el modo estándar como el **modo de superposición estilo lente** para traducciones en el mismo lugar.
- 📱 **Compatibilidad con widgets:** más de una docena de widgets de pantalla de inicio que cubren una amplia gama - accesos directos a recursos, reproductores multimedia, captura de cámara, calculadoras, tareas programadas, favoritos, minijuegos y más. Explora la selección completa en el selector de widgets de tu lanzador.
- 🏠 **Modo pantalla de inicio:** deja que la app sea la pantalla de inicio de tu dispositivo (compilaciones Standard y noLegal): su propio escritorio con accesos directos a recursos que abren directamente en explorar, presentación o reproducción, gadgets redimensionables como un reloj y el tiempo, celdas de contacto que no necesitan permiso de contactos, una cuadrícula de apps y una barra de tareas. Desactívalo cuando quieras y Android restaura tu pantalla de inicio anterior.
- ⏰ **Operaciones de archivo programadas:** automatiza operaciones de archivo (copiar/mover/eliminar) mediante reglas basadas en tiempo con filtros flexibles y ejecución en segundo plano.
- 👆 **Gestos avanzados:** controles de zoom inteligente (2x/3x/4x) para imágenes y zonas táctiles intuitivas para la navegación de archivos.
- 📸 **Guardar fotograma:** captura el fotograma de vídeo actual como una instantánea PNG o JPG y guárdala en cualquier recurso configurado - local o de red. El formato de salida y el recurso de destino se configuran en Ajustes de vídeo.
- 🖨️ **Imprimir:** envía documentos (PDF, TXT) e imágenes a una impresora directamente desde el reproductor integrado. Los archivos de red y de la nube se guardan en caché localmente antes de imprimir.
- ⬇️ **Descarga de emisiones:** descarga un archivo de red a la caché local con un diálogo de progreso en tiempo real antes o durante la reproducción. Un aviso de limpieza opcional recupera espacio de almacenamiento después.
- 🔊 **Audio DTS/DTS-HD:** las pistas de audio DTS y DTS-HD se decodifican por software mediante una compilación personalizada de FFmpeg - no se necesita hardware especial.
- 🎨 **Color y brillo de vídeo:** ajusta el tono y el brillo en tiempo real usando los efectos de GPU de Media3. Los ajustes persisten entre archivos de vídeo durante la sesión.
- 📤 **Compartir con FastMediaSorter:** recibe archivos de cualquier app a través del panel de compartir estándar de Android y cópialos a un recurso seleccionado con un solo toque.
- 📷 **Captura de cámara en Explorar:** haz una foto con la cámara del dispositivo y guárdala directamente en el recurso actual - local o de red - sin salir de la app.
- 🔗 **Descarga automática de enlaces:** comparte cualquier URL http(s) con la app mediante el panel de compartir de Android; el archivo multimedia se descarga y se guarda automáticamente en un recurso seleccionado.
- 👁️ **Modo 3D de un solo ojo:** recorta el contenido estéreo (SBS/OU) a un solo ojo para verlo cómodamente en pantallas planas; funciona tanto para vídeo como para imágenes.
- 📲 **Captura y grabación de pantalla:** franja de gestos en el borde izquierdo para capturas de pantalla, fotos rápidas, recortar y compartir, y grabación de pantalla/voz/vídeo sin salir del archivo actual.
- 📊 **Estadísticas de uso (opcional):** panel local de archivos ordenados, espacio liberado y tiempo de reproducción - nada sale del dispositivo salvo que lo exportes.
- 🧹 **Buscador de duplicados y limpieza por tamaño:** escaneo de duplicados basado en el contenido (tamaño, hash rápido, SHA-256) con eliminación manual o automática, además de un barrido de eliminación por tamaño.

## Formatos multimedia compatibles 🎞️ {#supported-media-formats-}

FastMediaSorter v2 admite una amplia variedad de formatos:

- **Imágenes:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF
- **Vídeo:** MP4, MKV, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG
- **Audio:** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, DTS, DTS-HD
- **Documentos:** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **Archivos binarios** (modo Todos los archivos): ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO, y más de 60 formatos adicionales

## Escenarios de uso 💡 {#usage-scenarios-}

Aquí tienes algunas formas en las que FastMediaSorter v2 puede ayudarte:

### 1. 📸 Organizar fotos de la cámara

Conecta tu teléfono o abre una carpeta de cámara local. Configura una carpeta de destino "Mejores fotos". Abre el visor, pasa rápidamente por miles de fotos con deslizamientos y toca el botón de destino para copiar al instante las mejores tomas.

### 2. 🏠 Copia de seguridad en red (NAS)

Añade tu NAS doméstico por SMB. Explora tus archivos multimedia locales. Selecciona varios archivos o un rango y "muévelos" a tu NAS para guardarlos a salvo, liberando espacio en tu dispositivo.

### 3. ☁️ Gestión en la nube

Conecta tu cuenta de Google Drive, Dropbox o OneDrive. Explora tus archivos en la nube sin descargarlos todos. Elimina archivos que no quieras u organízalos en carpetas directamente en la nube.

### 4. 📺 Presentación de diapositivas

Abre una carpeta con fotos familiares o diapositivas de una presentación. Pulsa "Reproducir" para iniciar una presentación de diapositivas. Usa los ajustes por recurso para ajustar la duración de cada diapositiva a tu gusto.

### 5. ⭐ Gestionar favoritos

Marca los archivos importantes con el botón de estrella mientras navegas. Después, toca la pestaña "Favoritos" en el menú principal para acceder al instante a todos tus archivos favoritos de todas las fuentes en un solo lugar - perfecto para crear una colección seleccionada de tu mejor contenido multimedia.

### 6. 🎶 Presentación con música de fondo

Añade tu colección de música como recurso. En **Ajustes → Multimedia → Imágenes**, activa **"Reproducir música durante la presentación"** y selecciona tu recurso de música. Ahora, cuando inicies una presentación de tus fotos, sonarán tus pistas favoritas de fondo. Toca el nombre de la pista para saltar a otra canción aleatoria, creando el ambiente perfecto para tus presentaciones de fotos.

### 7. 🖼️ Marco de fotos digital en una tablet

Convierte cualquier **tablet** Android en un precioso marco de fotos digital siempre encendido. Colócala en un soporte, conéctala a tu PC doméstico (SMB) o a almacenamiento en la nube - las fotos se transmiten directamente sin ocupar almacenamiento local. Ajusta el intervalo de las diapositivas, mantén la pantalla siempre encendida, añade música de fondo y disfruta de tus recuerdos. Incluso las tablets económicas, viejas y lentas funcionan perfectamente para este propósito - la app está optimizada para la reproducción continua con pocos recursos.

### 8. 🍿 Cine en casa y VR

Ve tus series favoritas guardadas en tu PC o en la nube directamente en tu teléfono o visor VR. Sin esperas por la copia ni preocupaciones por el espacio libre. Solo pulsa reproducir y el siguiente episodio empezará automáticamente.

**Casos de uso con visor VR** - FastMediaSorter funciona de forma nativa en visores VR basados en Android (Meta Quest, Pico y similares) sin ninguna modificación:

- **🎬 Cine virtual gigante**: abre un vídeo de tu NAS doméstico o de almacenamiento en la nube y velo en una pantalla virtual del tamaño de una pared entera. No hace falta copiar archivos de varios gigabytes al visor - la app los transmite directamente por tu red doméstica. Cuando termina un episodio, el siguiente empieza automáticamente.
- **🎵 Reproductor de música inmersivo**: inicia tu colección de música en el entorno VR. El servicio de audio en segundo plano mantiene la música sonando incluso cuando cambias entre apps o abres la pantalla de inicio de VR. Los botones físicos del visor (reproducir/pausa, siguiente pista) funcionan sin tocar el mando.
- **🖼️ Marco de fotos VR del tamaño de una pared**: convierte tu visor VR en una experiencia fotográfica inmersiva - inicia una presentación y tus fotos llenan una enorme pared virtual a tu alrededor. Combínalo con música de fondo para una experiencia de recuerdos cinematográfica que llena toda la sala. Transmite las fotos directamente desde tu PC doméstico o la nube para que el almacenamiento del visor quede libre.

### 9. 🧹 Organizador de descargas

¿Tu carpeta de descargas está desordenada? Ábrela en el panel de origen, configura botones de destino para "Documentos", "Imágenes" e "Instaladores". Revisa rápidamente los archivos, previsualízalos y ordénalos en el lugar correcto con un solo toque. Incluso puedes ordenar archivos directamente en tu ordenador de red usando tu teléfono como mando a distancia.

### 10. 🚗 Música en el coche con una unidad central Android

Instala FastMediaSorter en tu radio o unidad central de coche con Android. Añade carpetas de música desde una unidad USB o una tarjeta SD - o usa el recurso virtual integrado **Toda la música** para acceder al instante a toda tu colección sin ninguna configuración. Los botones multimedia de hardware (controles del volante, ruedas de volumen) funcionan sin problemas a través del servicio de audio en segundo plano: reproducir/pausa, pista siguiente/anterior, todo sin tocar la pantalla. La app recuerda la posición de reproducción y la reanuda automáticamente al iniciarse.

Con la pantalla **Streams** activada, la misma unidad central también reproduce emisoras de radio por Internet directamente a través de los datos móviles o el Wi-Fi - sin necesidad de una app aparte como TuneIn o RadioDroid. Añade cualquier URL de radio o importa un catálogo curado de emisoras desde la pantalla Extensiones. El mini-control fijo muestra el nombre de la pista ICY actual mientras la lista de emisoras permanece visible.

### 11. 📺 Centro multimedia en una caja Android TV

Instala FastMediaSorter en cualquier caja Android TV (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV o una caja Android genérica). Conéctate a un NAS doméstico por SMB, añade Google Drive o Dropbox, o conecta una unidad USB - todo desde una sola app. Controla todo el flujo con un mando o un teclado Bluetooth: el D-pad mueve el foco, **OK** abre los elementos, **Back** vuelve al nivel anterior y **Backspace** sube una carpeta en el explorador. Los botones de color del mando se corresponden con acciones de archivo comunes (**Rojo** = Eliminar, **Verde** = Copiar, **Amarillo** = Mover, **Azul** = Renombrar). Inicia una presentación a pantalla completa con música de fondo en el televisor, o cambia a reproducción de audio con carátula y letras. No hace falta pantalla táctil.

## Documentación 📚 {#documentation-}

**🗺️ Documentation Map / Карта документации:** [Ver todos los documentos / Все документы](DOCS_MAP.md)

**🌐 Sitio web oficial:** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### Fuentes canónicas (fuente única de verdad)

Los siguientes archivos deben tratarse como las fuentes de referencia para los detalles de cara al usuario:

- [Lista completa de funciones](FEATURES.md)
- [Mapa de documentación](DOCS_MAP.md)
- [Historia del producto](PRODUCT_HISTORY.md)
- [Descargas (EN)](DOWNLOADS.md)
- [Guías prácticas](HOW_TO-es.md)
- [Limitaciones del programa](LIMITATIONS.md)
- [Guía de inicio rápido](QUICK_START-es.md)
- [Términos del servicio](TERMS_OF_SERVICE.md)

Hay guías detalladas disponibles en varios idiomas:

**🇺🇸 English:**

- [Product History](PRODUCT_HISTORY.md)
- [How-To Guides](HOW_TO.md)
- [Launcher Web Portal](launcher/index.md)
- [Wear OS Web Portal](wear/index.md)
- [Quick Start](QUICK_START.md)
- [FAQ](FAQ.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Program Limitations](LIMITATIONS.md)
- [Downloads Guide](DOWNLOADS.md)
- [Complete Feature List](FEATURES.md)

**🇷🇺 Русский:**

- [История продукта](PRODUCT_HISTORY-ru.md)
- [Руководства](HOW_TO-ru.md)
- [Быстрый Старт](QUICK_START-ru.md)
- [FAQ](FAQ-ru.md)
- [Устранение неполадок](TROUBLESHOOTING-ru.md)
- [Ограничения программы](LIMITATIONS-ru.md)
- [Скачивание сборок](DOWNLOADS-ru.md)

**🇺🇦 Українська:**

- [Історія продукту](PRODUCT_HISTORY-uk.md)
- [Посібники](HOW_TO-uk.md)
- [Швидкий Старт](QUICK_START-uk.md)
- [FAQ](FAQ-uk.md)
- [Вирішення проблем](TROUBLESHOOTING-uk.md)
- [Обмеження програми](LIMITATIONS-uk.md)
- [Завантаження збірок](DOWNLOADS-uk.md)

**Documentación técnica / para desarrolladores:**

- [Resumen de la arquitectura](ARCHITECTURE.md)
- [DevOps y scripts de compilación](DEV_OPS.md)
- [Pila tecnológica](TECH_STACK.md)
- [Documentación de Wear OS](WEAR_OS_QUICK_START.md)
- [Componentes de código abierto](OPEN_SOURCE.md)

## Compañero Wear OS ⌚ {#wear-os-companion-}

FastMediaSorter incluye una app independiente de Wear OS con todas las funciones y un companion para el teléfono diseñados para los formatos de los relojes inteligentes.

- Explora y reproduce carpetas y favoritos del teléfono emparejado, del propio almacenamiento del reloj y de recursos compartidos SMB/FTP/SFTP a los que el reloj llega directamente por Wi-Fi
- Los recursos en la nube se quedan en el teléfono - el reloj no tiene cliente de nube propio; un archivo de la nube solo le llega cuando lo envías desde el teléfono con "Enviar a.."
- Mueve archivos entre el teléfono y el reloj, transmite en directo desde el reloj y usa pequeñas herramientas integradas (calculadora, monitor de red, minijuego) sin abrir la app del teléfono
- Interfaz y comportamiento en tiempo de ejecución optimizados para pantallas redondas y compactas
- Portal web dedicado, guías de configuración y solución de problemas para los flujos de trabajo del reloj

La multimedia, los recursos compartidos de red y la transferencia de archivos están en la versión completa de la app del reloj (APK directo). La versión de Google Play es un primer lanzamiento reducido - calculadora, cronómetro, minijuego y ajustes; el [portal de Wear OS](wear/index.md) indica qué tiene cada versión.

Documentación de Wear OS:

- 🌟 **[Portal web de Wear OS](wear/index.md)** - escaparate completo de funciones, capturas de pantalla y descargas de tiendas de apps
- [Inicio rápido de Wear OS](WEAR_OS_QUICK_START.md) - guía paso a paso de emparejamiento y configuración
- [Configuración de Wear OS](WEAR_OS_SETUP.md) - arquitectura del módulo y configuración del puente del companion
- [Sección de Wear OS en Funciones](FEATURES.md#16-settings--navigation)

## Instrucciones de compilación {#build-instructions}

### Requisitos

- Android Studio Hedgehog (2023.1.1) o más reciente

- JDK 17+
- Android SDK 35
- Versión mínima de Android: 8.0 (API 26) para Standard/Lite/Photos/VR/noLegal; 6.0 (API 23) para Legacy

### Compilación

1. Clona el repositorio:

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Abre el proyecto en Android Studio.
3. Espera a que termine la sincronización de Gradle.
4. Ejecuta la app en un emulador o en un dispositivo físico.

### Comandos de compilación recomendados (Windows / PowerShell)

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### APK compilados 📦

Después de cada compilación correcta, el archivo APK generado se copia automáticamente a la carpeta `DOWNLOADS` en la raíz del proyecto, con marca de tiempo. Ahí encontrarás todo tu historial de compilaciones.

## Pruebas 🧪 {#testing-}

FastMediaSorter v2 usa **Maestro** para pruebas de extremo a extremo, con el fin de garantizar la calidad y fiabilidad de la app.

### Ejecución rápida de pruebas

```bash
# Instalar Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# O Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell como administrador)
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1  # External: Maestro installer
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Ejecutar pruebas de humo (2-3 minutos)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# O usar el atajo
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**Nota**: NO uses `npm install -g maestro-cli` - ¡es un paquete distinto y no relacionado!

### Conjuntos de pruebas

- **Pruebas de humo** (`maestro/smoke/`): pruebas de funcionalidad principal (~2-3 min)
  - Inicio de la app y permisos
  - Navegación por archivos locales
  - Reproducción multimedia
  - Visualización de imágenes

- **Pruebas de ruta crítica** (`maestro/critical/`): operaciones esenciales (~1-2 min)
  - Operaciones de archivo (copiar, mover, eliminar)
  - Persistencia de los ajustes

### Documentación

- 📚 [Guía de inicio rápido](../maestro/QUICK_START.md)
- 📝 [Escribir pruebas](../maestro/WRITING_TESTS.md)
- 🔍 [Ejemplos de pruebas](../maestro/EXAMPLES.md)
- 🔧 [Solución de problemas](../maestro/TROUBLESHOOTING.md)
- 📖 [Documentación completa](../maestro/README.md)

### Integración CI/CD

Las pruebas se ejecutan automáticamente en cada push mediante GitHub Actions. Consulta [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml).

## Primeros pasos (guía rápida de uso) 🚀 {#first-steps-quick-usage-guide-}

1. **Añadir una carpeta (recurso):**
    - En la pantalla principal, pulsa el botón con el icono "Más" (+) para añadir un nuevo recurso.
    - Selecciona el tipo de recurso (por ejemplo, "Carpeta local").
    - Usa el escaneo o añade la carpeta manualmente. Tras añadirla, aparecerá en la lista de la pantalla principal.

2. **Ver archivos:**
    - Toca dos veces (o mantén pulsado) el recurso que acabas de añadir en la lista.
    - Se abrirá la pantalla de exploración, donde verás todos los archivos multimedia de esa carpeta en forma de lista o cuadrícula.
    - Usa los botones del panel superior para ordenar, filtrar o cambiar la vista.

3. **Reproducción y clasificación:**
    - Toca cualquier archivo para abrirlo en el reproductor a pantalla completa.
    - Usa los deslizamientos a izquierda/derecha o las zonas táctiles para navegar entre archivos.
    - Para las operaciones (copiar, mover), usa las zonas táctiles correspondientes o los botones del panel de control.

4. **Configurar carpetas de destino (Destinos):**
    - En ajustes, en la pestaña "Destinos", puedes indicar hasta 30 carpetas que se usarán para la clasificación rápida.
    - También puedes activar "Es destino" en la pantalla de edición de cualquier recurso para añadirlo a la lista de clasificación rápida.
    - Después de eso, aparecerán botones para copiar o mover archivos rápidamente a esas carpetas en la pantalla del reproductor.

## Pila tecnológica {#technology-stack}

- **Lenguaje**: Kotlin
- **Arquitectura**: Clean Architecture, MVVM
- **Interfaz**: Android View System (XML), Material Design 3
- **Asincronía**: Kotlin Coroutines y Flow
- **Inyección de dependencias**: Hilt (Dagger)
- **Base de datos**: Room 2.7.0
- **Navegación**: AndroidX Navigation Component
- **Multimedia**: ExoPlayer (Media3 1.2.1)
- **Carga de imágenes**: Glide 5.0.9 con NetworkFileModelLoader personalizado
- **Protocolos de red**:
  - SMB: SMBJ 0.12.1 con BouncyCastle (transitivo)
  - SFTP: JSch 0.2.26 (fork de com.github.mwiede, con Ed25519 integrado)
  - FTP: Apache Commons Net 3.10.0
- **Nube**: Google Drive API, OneDrive (MSAL), Dropbox API con OAuth 2.0
- **OCR y traducción**:
  - Tesseract4Android (Tesseract 5.3.x) - extracción de texto en escritura latina y cirílica
  - Google ML Kit (traducción, identificación de idioma) - traducción del texto extraído
- **Búsqueda y letras**: api.lyrics.ovh (API JSON)

## Versión de compilación

Formato de versión: `Y.YM.MDDH.Hmm` (por ejemplo, `2.60.1102.207` para el 10/01/2026 20:07)

Consulta [dev/CHANGELOG.md](../dev/CHANGELOG.md) para ver las notas detalladas de la versión.

---

## Contribuciones 🤝

Las pull requests son bienvenidas. Para cambios importantes, abre primero un issue para hablar de lo que te gustaría cambiar.

## Contacto 📧

- **Desarrollador**: <sza@ukr.net>
- **Sitio web**: [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **Issues de GitHub**: [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## Licencia 📄

Información legal del proyecto:

- [Términos del servicio](TERMS_OF_SERVICE.md)
- [Política de privacidad](PRIVACY_POLICY.md)
- [Componentes de código abierto](OPEN_SOURCE.md)

</div>
