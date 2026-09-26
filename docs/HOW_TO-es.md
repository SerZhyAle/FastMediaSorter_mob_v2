---
layout: default
title: "📖 Guías prácticas"
permalink: /docs/HOW_TO-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 📖 Guías prácticas

Instrucciones paso a paso para tareas habituales.

Esta guía tiene ahora dos niveles:

- **Grupos de escenarios**, para flujos de trabajo más completos de la vida real y combinaciones de funciones.
- **Referencia de tareas principales**, con recetas directas de una sola función, más abajo.

{% include lang-switcher.html doc="HOW_TO" dir="/docs/" current="es" %}

---

## Nota: disponibilidad de funciones por variante

Algunas funciones solo están disponibles en variantes concretas. La tabla siguiente se deriva de [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), que se genera a partir de la propia compilación; la superficie XR / noLegal se mantiene deliberadamente como una sola columna porque depende del hardware del visor y de las reglas de compilación para carga lateral (sideload).

| Función | Standard | Lite | Photos | Legacy | XR / noLegal | FOSS |
|---------|----------|------|--------|--------|--------------|------|
| Carpetas de red (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Almacenamiento en la nube (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ |
| Reproducción de audio y letras | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ |
| Reproducción de audio en segundo plano | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Emisiones de Internet (radio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Visor de documentos (PDF, texto) | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Lector de EPUB | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Traducción y OCR | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Edición de imágenes | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Modo pantalla de inicio (launcher) | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |

El modo pantalla de inicio es la única fila donde la columna combinada final se divide: está disponible en la compilación de carga lateral **noLegal**, pero no en la compilación VR/XR, donde el propio visor aporta su entorno de inicio.

Hay dos filas para el audio porque son dos decisiones de compilación distintas: **Lite reproduce archivos de audio locales**, letras incluidas, pero se detiene cuando la app deja de estar en primer plano - no tiene servicio de reproducción en segundo plano. Lite no tiene en absoluto pantalla de Emisiones de Internet, así que la radio y HLS/DASH/RTSP no están limitadas ahí, sino que están ausentes.

Si una función está marcada con "✗", elige la compilación **Standard** o **XR / noLegal** que se ajuste a tu hardware y a tu vía de distribución.

---

## Tabla de contenidos

### Grupos de escenarios

#### Cine en casa, TV y flujos de salón

1. [Convierte un NAS en una videoteca de salón](#turn-a-nas-into-a-living-room-media-shelf)
2. [Ejecuta una presentación de diapositivas con música de fondo para una pantalla de sala](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Usa FMS en un Android TV Box](#how-to-use-fms-on-android-tv-box)
4. [Cine inmersivo OpenXR VR](#openxr-vr-immersive-cinema)

#### Viajes, lectura y flujos de trabajo con documentos

5. [Prepara una carpeta para viajar sin internet estable](#prepare-a-folder-for-travel-without-stable-internet)
6. [Lee documentos y EPUB en la nube sobre la marcha](#read-cloud-documents-and-epubs-on-the-go)
7. [Traduce carteles, escaneos y capturas de pantalla con OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Entrega archivos de red a aplicaciones especializadas](#hand-network-files-off-to-specialist-apps)
9. [Cálculos matemáticos y de texto rápidos](#quick-math-and-text-calculations)
10. [Notas en la nube en Markdown y código](#cloud-markdown-and-code-notes)

#### Flujos de trabajo avanzados y de medios mixtos

11. [Ordena un archivo fotográfico familiar con Quick Sort](#sort-a-family-photo-archive-with-quick-sort)
12. [Captura la pantalla con gestos de borde](#capture-the-screen-with-edge-gestures)
13. [Crea una presentación de diapositivas con música de fondo](#how-to-create-slideshow-with-background-music)
14. [Lee libros electrónicos (EPUB)](#how-to-read-e-books-epub)
15. [Traducción automática](#auto-translation)
16. [Widgets inteligentes de pantalla de inicio](#home-screen-smart-widgets)

### Referencia de tareas principales

17. [Conectar a una unidad de red (SMB)](#how-to-connect-to-network-drive-smb)
18. [Conectar a un servidor SFTP/FTP](#how-to-connect-to-sftpftp-server)
19. [Importar un recurso compartido del compañero de Windows (escanear un código o importar un archivo)](#how-to-import-a-windows-companion-share)
20. [Conectar a almacenamiento en la nube](#how-to-connect-to-cloud-storage)
21. [Configurar las carpetas de Quick Sort](#how-to-set-up-quick-sort-folders)
22. [Usar las zonas táctiles](#how-to-use-touch-zones)
23. [Editar fotos](#how-to-edit-photos)
24. [Crear una presentación de diapositivas](#how-to-create-slideshow)
25. [Proteger una carpeta con PIN](#how-to-protect-folder-with-pin)
26. [Vaciar la papelera](#how-to-empty-trash)
27. [Hacer copia de seguridad de los ajustes](#how-to-backup-settings)
28. [Ver archivos de texto y PDF](#how-to-view-text-and-pdf-files)
29. [Abrir archivos de red en aplicaciones externas](#how-to-open-network-files-in-external-apps)
30. [Ver la letra de una canción](#how-to-view-song-lyrics)
31. [Grabar tu pantalla](#how-to-record-your-screen)
32. [Grabar una nota de voz](#how-to-record-a-voice-note)
33. [Usar la cámara integrada](#how-to-use-the-in-app-camera)
34. [Buscar y eliminar archivos duplicados](#how-to-find-and-delete-duplicate-files)
35. [Ver tus estadísticas de uso](#how-to-view-your-usage-statistics)
36. [Usar una tarjeta SD o una unidad conectada](#how-to-use-an-sd-card-or-connected-drive)
37. [Reconectar una carpeta añadida por ruta directa](#how-to-reconnect-a-folder-added-by-direct-path)
38. [Usar la app como tu pantalla de inicio](#how-to-use-the-app-as-your-home-screen)
39. [Elegir dónde se guardan las capturas y las descargas](#how-to-choose-where-captures-and-downloads-are-saved)
40. [Recibir archivos compartidos desde otra app](#how-to-receive-files-shared-from-another-app)
41. [Usar los programas integrados](#how-to-use-the-built-in-programs)
42. [Pedirle a tu asistente que busque y abra contenido multimedia](#how-to-ask-your-assistant-to-find-and-open-media)
43. [Cifrar un archivo con FileDO](#how-to-encrypt-a-file-with-filedo)

---

## Grupos de escenarios

Estas secciones son deliberadamente más variadas que los bloques de referencia principal de más abajo. Cada escenario combina una vía rápida con contexto, compensaciones y las situaciones en las que FastMediaSorter destaca especialmente.

> **⭐ Destacado: lleva las carpetas de tu PC al teléfono con un solo escaneo.** Ejecuta el compañero gratuito [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) en tu PC, elige las carpetas con tus vídeos, música, documentos o fotos, y mostrará un código en pantalla. En el teléfono, toca **Añadir**, elige **Importar por código de barras**, apunta la cámara al código - las carpetas del PC se conectan al instante, sin escribir dirección, puerto ni contraseña. Guía completa: [Abre carpetas del PC escaneando un código](howto/scenario-companion-share-es.md) &bull; receta rápida: [Importar un recurso compartido del compañero de Windows](#how-to-import-a-windows-companion-share).

> **⌚ Relojes inteligentes Wear OS:** ¿Usas un reloj Wear OS? Consulta nuestras guías paso a paso [Escuchar música en tu reloj](howto/scenario-watch-music-es.md) y [Conectar el reloj inteligente a NAS y recursos compartidos de PC](howto/scenario-watch-network-es.md).

## Cine en casa, TV y flujos de salón

## Convierte un NAS en una videoteca de salón {#turn-a-nas-into-a-living-room-media-shelf}

**Disponible en:** Standard, Photos, Legacy, XR/noLegal

**Vía rápida**

1. Añade tu NAS como recurso SMB.
2. Ejecuta **Buscar en la red** si no quieres escribir la IP a mano.
3. Abre el recurso desde un TV Box, una tablet o un teléfono.
4. Empieza a explorar vídeos, fotos o documentos directamente desde el NAS.

**Recorrido del escenario**

- Mantén un solo recurso SMB para toda la biblioteca familiar y separa las subcarpetas por uso: Películas, Fotos familiares, Escaneos, Manuales.
- Ejecuta **Probar conexión** una vez durante la configuración para que el recurso sea estable antes de confiar en él desde el sofá.
- Si usas el NAS desde un TV Box, empareja un teclado Bluetooth o el mando del televisor para navegar rápido.
- Si la navegación va lenta, abre los ajustes del recurso y ejecuta la comprobación de velocidad integrada antes de cambiar nada más.

**Cuándo ayuda**

- Quieres una única fuente central de contenido multimedia en lugar de copiar los mismos archivos en varios dispositivos.
- Quieres que la misma biblioteca sirva para presentaciones de diapositivas, lectura de documentos y reproducción.

**Evita esto**

- No empieces solucionando problemas de nombre de host. Usa primero una dirección IP y optimiza después.
- No esperes que la variante Lite pueda explorar recursos SMB - esa compilación no tiene ninguna fuente de red.

## Ejecuta una presentación de diapositivas con música de fondo para una pantalla de sala {#run-a-slideshow-with-background-music-for-a-room-display}

**Disponible en:** Standard, Lite, Legacy, XR / noLegal (Photos no tiene compatibilidad con audio)

**Vía rápida**

1. Añade una fuente de imágenes y una fuente de música.
2. En **Ajustes → Multimedia → Imágenes**, activa **Reproducir música durante la presentación**.
3. Elige el recurso de música.
4. Abre una carpeta de fotos y pulsa **Reproducir**.

**Recorrido del escenario**

- Usa una carpeta de imágenes local o un recurso NAS rápido para lograr las transiciones más fluidas.
- Mantén un recurso de música aparte para pistas de fondo tranquilas, así el audio de la presentación es predecible.
- Si la carpeta contiene tanto imágenes como vídeos, recuerda que la música se pausa automáticamente cuando empieza un vídeo.

**Cuándo ayuda**

- Quieres que un TV Box, una tablet o un teléfono viejo hagan de marco digital para una sala.
- Quieres una sola configuración que pueda rotar fotos familiares, fotos de eventos o álbumes de viaje sin construir una cola manualmente.

**Evita esto**

- No uses un recurso de red muy lento tanto para imágenes como para música si la reproducción fluida te importa.

## Cine inmersivo OpenXR VR {#openxr-vr-immersive-cinema}

**Disponible en:** Standard, Lite, Legacy, `vr`, noLegal (3D de un solo ojo); `vr` y noLegal (inmersión completa con visor - ambas compilaciones incluyen la vista inmersiva, y se abre cuando la app detecta un visor OpenXR y el interruptor maestro de VR está activado)

**Vía rápida - activar, configurar, ver en 3D**

1. **3D de un solo ojo (en todas las variantes, nada que activar):** abre cualquier archivo SBS/OU/180°/360° - se detecta automáticamente y se recorta a un solo ojo para que se vea bien en una pantalla plana normal. Esto lo controla **Ajustes > Reproductor > "Mostrar contenido 3D desde un solo ojo"** (activado por defecto). Para forzar un formato concreto en lugar de confiar en la detección automática, abre el diálogo de control del reproductor en una compilación `vr`/XR-noLegal y elige un modo en la pestaña 3D - **Detección automática**, **Lado a lado (SBS)**, **Superior-inferior (OU)** o **Mono (desactivado)**; la elección se recuerda para ese archivo.
2. **Inmersión completa en una Quest (compilación `vr` o XR/noLegal):** con el visor puesto, toca la insignia VR en el reproductor mientras hay un archivo 3D abierto, elige **Abrir en VR Cinema** en el menú desplegable de un archivo en Explorar, o abre **Ajustes > Multimedia** y toca **Probar inmersión** para probar una muestra. Cualquiera de las tres opciones abre una vista OpenXR por ojo de ese contenido.
3. **Ver:** dentro de la vista inmersiva, una franja HUD contiene los controles - una barra de posición que arrastras con el rayo del mando para buscar (con el tiempo transcurrido y el total al lado), además de los selectores que se aplican a ese archivo: pista de audio solo cuando hay más de una, subtítulos solo cuando el archivo los tiene, profundidad estéreo solo para contenido estéreo. **OCULTAR** y **SALIR** están en los extremos opuestos de la franja; ocultarla la elimina por completo y un apretón del gatillo la trae de vuelta sin activar lo que hay debajo. El joystick busca en pasos de 10 segundos; mantén pulsado **grip** mientras lo mueves para pasar de un archivo a otro - siguiente y anterior recorren toda la lista del recurso, no solo el archivo que abriste. En la primera entrada inmersiva tras la instalación aparece una leyenda con todas las asignaciones del mando; cualquier pulsación la cierra, y el botón **AYUDA** de la franja la vuelve a mostrar en cualquier momento.

**Recorrido del escenario**

- El 3D de un solo ojo no necesita ningún visor - es la forma más fácil de revisitar viejas grabaciones SBS/OU en un teléfono o una tablet.
- La inmersión completa necesita una Quest u otro visor OpenXR y una compilación que la incluya - la compilación `vr` o la compilación de carga lateral XR/noLegal (consulta la [Guía de carga lateral para VR](VR_SIDELOAD.md)).
- Las fotos y vídeos 360°/180° se representan como una esfera/hemisferio a tu alrededor una vez dentro de la vista inmersiva; los archivos planos en 2D simplemente se reproducen planos.

**Cuándo ayuda**

- Quieres revisitar grabaciones archivadas en SBS/OU/360°/180° sin una app de vídeo VR aparte.
- Tienes una Quest y quieres probar hoy la inmersión completa con tus propios archivos, aceptando que por ahora la navegación es solo siguiente/anterior.

**Evita esto**

- No esperes que la compilación `vr` de Meta Horizon Store / Google Play entre en modo inmersivo todavía - esa parte sigue en desarrollo.
- La búsqueda, la selección de pista y subtítulos, y la profundidad estéreo están en la franja HUD dentro del visor. Las operaciones con archivos no lo están - vuelve al panel plano para copiar, mover o eliminar.

## Escucha radio por Internet en un equipo de coche o reproductor de audio

**Disponible en:** Standard, Legacy, XR / noLegal - la pantalla de Emisiones está ausente en Lite y Photos

**Vía rápida**

1. Abre el menú desplegable de la ventana principal y toca **Emisiones**, o ve a **Ajustes > Multimedia > Emisiones** y activa el interruptor si está apagado.
2. Toca **⋮** al final de la barra de herramientas, elige **Añadir emisión** y pega la URL de cualquier emisora de radio (http:// o https://, .m3u8, rtsp://).
3. Toca la fila de la emisora - el audio empieza en el minicontrol inferior fijo. La lista sigue siendo desplazable.
4. Para un catálogo más amplio, toca **Importar** e introduce una URL `.m3u` remota, o descarga el catálogo seleccionado de FastMediaSorter desde la pantalla **Extensiones**.

**Recorrido del escenario**

- El catálogo seleccionado llega con chips de tema e idioma; filtra por género o idioma con el botón de filtro (con un punto indicador cuando está activo). El interruptor Y/O te permite buscar emisoras que cumplan todos los criterios o cualquiera de ellos.
- El catálogo también llega agrupado en colecciones con nombre - "TV rusa", "Radio de la antigua URSS", "TV africana" y más. Aparecen como una tira de chips desplazable justo bajo la barra de herramientas; toca uno para ver solo sus canales, en el orden en que los organizó el curador, y toca **Todo** para volver. Un mismo canal puede pertenecer a varias colecciones, así que puedes encontrarlo tanto bajo un país como bajo un continente. Una colección es una condición de filtro más, no una pantalla aparte: la búsqueda, la ordenación, los filtros de género e idioma y tus fijados siguen funcionando dentro de ella. Si el catálogo descargado no trae colecciones, la tira simplemente no aparece.
- Los dos pequeños iconos a la derecha del campo de búsqueda separan la radio del vídeo con un toque: toca el icono de audio o de vídeo para quedarte solo con ese tipo, toca de nuevo el que está activo para mostrar todo otra vez.
- Fija tus emisoras favoritas arriba con el icono de fijar - el orden es independiente de los Favoritos globales.
- Cambia el interruptor de vista de la barra de herramientas a **Cuadrícula** para ver los canales como mosaicos con su último fotograma capturado - útil para explorar emisiones de vídeo de un vistazo. Tu elección de lista o cuadrícula se recuerda la próxima vez que abras Emisiones.
- Si una emisión admite Cast y tu teléfono está en Wi-Fi, toca **Cast** en el reproductor para enviarla a un Chromecast en la misma red. Las emisiones RTSP no se pueden enviar por Cast.
- Los metadatos ICY de "sonando ahora" (nombre de la emisora, pista actual) se muestran en el minicontrol inferior.
- Una emisora que hayas añadido tú puede enviarse a tu reloj Wear OS: abre el menú **⋮** de la fila y toca **Enviar al reloj** (el comando aparece cuando la opción de Compañero Wear está activada). La emisora transferida permanece en el reloj a través de las actualizaciones del catálogo; si esa misma dirección aparece más tarde en el catálogo en línea, la entrada del catálogo toma el relevo.
- Las emisiones de vídeo y RTSP se abren en el reproductor a pantalla completa; al pulsar Atrás se vuelve a la lista de Emisiones con la posición de desplazamiento conservada.
- El comportamiento del audio en segundo plano sigue **Ajustes > Reproductor > Reproducción de audio en segundo plano**: con él desactivado, el audio se detiene al salir de la pantalla y la app ofrece elegir entre Detener y Seguir reproduciendo.

**Cuándo ayuda**

- Autorradios Android, reproductores de audio y cajas multimedia donde quieres radio por Internet sin una app aparte (TuneIn, RadioDroid, emisiones de red de VLC).
- Uso tipo IPTV ligero: las emisiones VOD de HLS/DASH se reproducen en el reproductor a pantalla completa.

**Evita esto**

- No esperes reproducción en directo de HLS/DASH con desfase en vivo (live-edge) - en esta versión solo se admite HLS/DASH en VOD.
- No uses la variante Lite o Photos para Emisiones; ninguna de las dos compilaciones tiene entrada de Emisiones, así que ningún protocolo funciona ahí.

## Viajes, lectura y flujos de trabajo con documentos

## Prepara una carpeta para viajar sin internet estable {#prepare-a-folder-for-travel-without-stable-internet}

**Disponible en:** Standard, Lite, Photos, Legacy, XR / noLegal (leer PDF y EPUB necesita Standard, Legacy o XR / noLegal)

**Vía rápida**

1. Crea o elige una carpeta local para el viaje.
2. Copia en ella el contenido multimedia, los PDF, los EPUB o las notas que necesites antes de salir del Wi-Fi.
3. Abre esa carpeta una vez en FastMediaSorter para que las miniaturas y las últimas posiciones queden listas.
4. Usa la carpeta sin conexión durante el viaje.

**Recorrido del escenario**

- Mantén el contenido de viaje en una sola carpeta local aunque los originales vivan normalmente en NAS o en la nube.
- Mezcla formatos a propósito: PDF de embarque, EPUB de lectura, capturas de pantalla y música sin conexión pueden convivir.
- Usa el panel de filtro si quieres alternar entre solo imágenes, solo documentos o solo audio mientras estás sin conexión.

**Cuándo ayuda**

- Vuelos, trenes, hoteles y zonas rurales donde la transmisión en la nube no es fiable.
- Situaciones en las que prefieres un único paquete sin conexión en lugar de buscar en varias apps.

**Evita esto**

- No esperes al último minuto para comprobar si los archivos realmente se abren sin internet.

## Lee documentos y EPUB en la nube sobre la marcha {#read-cloud-documents-and-epubs-on-the-go}

**Disponible en:** Standard, Legacy, XR / noLegal - Lite y Photos no pueden leer documentos ni EPUB en absoluto; en Lite además falta el almacenamiento en la nube

**Vía rápida**

1. Añade tu proveedor de nube en **Almacenamiento en la nube**.
2. Abre la carpeta que contiene los PDF o los EPUB.
3. Toca el archivo directamente desde el recurso de la nube.
4. Sigue leyendo desde tu última posición guardada más tarde.

**Recorrido del escenario**

- Úsalo cuando tus documentos de trabajo ya vivan en Google Drive, OneDrive o Dropbox y no quieras un flujo de lectura aparte.
- El PDF es mejor para archivos de maquetación fija como billetes, manuales y contratos escaneados.
- El EPUB es mejor para lectura de formato largo, donde el tamaño de letra ajustable y la navegación por capítulos importan más que la fidelidad del diseño.

**Cuándo ayuda**

- Te mueves entre documentos de trabajo y lectura personal sin salir de la app.
- Guardas los archivos de viaje o de cliente en la nube, pero aun así quieres una interfaz pensada para leer.

**Evita esto**

- No esperes lectura en la nube en Lite - esa compilación no tiene ni almacenamiento en la nube ni compatibilidad con documentos. Photos y Legacy sí tienen almacenamiento en la nube, pero solo Legacy puede abrir documentos.
- No trates los datos móviles lentos como una experiencia de lectura garantizada para archivos muy grandes.

## Traduce carteles, escaneos y capturas de pantalla con OCR {#translate-signs-scans-and-screenshots-with-ocr}

**Disponible en:** Standard, Legacy, XR / noLegal

**Vía rápida**

1. Abre una imagen, un PDF o un archivo de texto.
2. Muestra el panel de comandos.
3. Toca **Traducir**.
4. Confirma la descarga del modelo la primera vez, si hace falta.

**Recorrido del escenario**

- La app lee el texto con Tesseract en el propio dispositivo y lo traduce con Google ML Kit.
- Para material en cirílico, elige el idioma de origen explícitamente (por ejemplo, ruso o ucraniano) - "Automático" lee con el modelo en inglés.
- Las capturas de pantalla, los recibos, los menús y las páginas escaneadas funcionan especialmente bien cuando el texto de origen es razonablemente nítido.

**Cuándo ayuda**

- Estás de viaje, leyendo manuales extranjeros o descifrando capturas de pantalla de chats y apps.
- Necesitas la traducción en el sitio en lugar de copiar el texto antes a otra herramienta aparte.

**Evita esto**

- No juzgues la calidad del OCR a partir de una foto nocturna borrosa o un escaneo mal recortado.

## Entrega archivos de red a aplicaciones especializadas {#hand-network-files-off-to-specialist-apps}

**Disponible en:** Standard, Photos, Legacy, XR/noLegal

**Vía rápida**

1. Abre un archivo desde SMB, SFTP o FTP.
2. Toca **ⓘ Información**.
3. Toca **Descargar y abrir**.
4. Elige la app especializada en el selector de Android.

**Recorrido del escenario**

- Úsalo cuando FastMediaSorter sea el mejor navegador para el almacenamiento remoto, pero otra app sea el mejor editor o visor para ese tipo de archivo.
- Los casos de entrega típicos son documentos de oficina, PDF avanzados, vídeos con códecs exigentes y formatos multimedia poco comunes.
- La copia descargada se queda en `Downloads`, así que puedes volver a abrirla más tarde aunque la fuente remota deje de estar disponible.

**Cuándo ayuda**

- Quieres un único centro de archivos remotos sin renunciar a las mejores herramientas especializadas.

**Evita esto**

- No esperes todavía la entrega desde la nube por esta vía exacta.

## Cálculos matemáticos y de texto rápidos {#quick-math-and-text-calculations}

**Disponible en:** Standard, Legacy, XR / noLegal

**Vía rápida**

1. Abre cualquier documento PDF, libro electrónico EPUB, archivo de texto, o ejecuta la traducción OCR sobre una imagen.
2. Mantén pulsado para seleccionar cualquier bloque de texto que contenga números o ecuaciones matemáticas.
3. En el menú flotante de acciones de texto, toca el botón **Calculadora**.
4. La calculadora evalúa la fórmula matemática al instante en una superposición emergente.

**Recorrido del escenario**

- Selecciona una línea de texto con números y símbolos de operador (como `(45 + 12) * 3`) en un PDF o en un resultado de traducción OCR.
- Usa el menú de funciones de la calculadora científica integrada para operaciones complejas (trigonometría, raíces, potencias, logaritmos).
- La calculadora conserva el historial de cálculos entre sesiones y admite ranuras de memoria (M+/M-/MR/MC) para seguimiento rápido de datos.

**Cuándo ayuda**

- Estás leyendo un manual, un escaneo de captura de pantalla o un documento, y necesitas resolver fórmulas rápidamente o sumar divisas/cifras sin cambiar a otra app de calculadora.

**Evita esto**

- No pegues cadenas alfabéticas sin más; solo se pueden analizar números válidos, paréntesis y operadores matemáticos.

## Notas en la nube en Markdown y código {#cloud-markdown-and-code-notes}

**Disponible en:** Standard, Photos, Legacy, XR / noLegal (local, red y nube); Lite (solo carpetas locales)

**Vía rápida**

1. Navega a cualquier carpeta local, NAS doméstico (SMB), servidor FTP/SFTP o unidad en la nube (Google Drive).
2. Toca el botón **Nueva nota <img src="icons/doc/ic_create_text_file.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas de la carpeta.
3. Escribe tu contenido dentro del editor. La app resalta las etiquetas Markdown y la sintaxis de código.
4. Toca **Guardar** (o deja que se guarde automáticamente) para escribir los cambios directamente en la fuente remota.

**Recorrido del escenario**

- Mantén un archivo de diario `.md` en tu Google Drive o en el NAS doméstico y edítalo desde cualquier dispositivo con edición en el propio lugar.
- Crea notas nuevas en los recursos clave con resolución automática de conflictos de nombre (por ejemplo, `Note_1.txt`, `Note_2.txt`).
- Consulta el Markdown ya renderizado en modo de solo lectura, o exporta las notas directamente a servicios externos como Google Keep.

**Cuándo ayuda**

- Quieres mantener notas sencillas, fragmentos de código o listas de tareas directamente en tus unidades centrales de red o nube, sin flujos de copiar y pegar en local.

**Evita esto**

- No esperes crear notas en almacenamiento en la nube en Lite - esa compilación no tiene almacenamiento en la nube ni fuentes de red.

## Flujos de trabajo avanzados y de medios mixtos

## Ordena un archivo fotográfico familiar con Quick Sort {#sort-a-family-photo-archive-with-quick-sort}

**Disponible en:** Standard, Lite, Photos, Legacy, XR / noLegal

**Vía rápida**

1. Añade tus carpetas de destino a **Quick Sort**.
2. Abre la carpeta de origen con las fotos familiares sin ordenar.
3. Usa los botones numerados o las zonas táctiles mientras revisas las imágenes.
4. Envía las que quieras conservar a las carpetas de destino al instante.

**Recorrido del escenario**

- Crea carpetas de destino por resultado, no solo por fecha: `Mejores`, `Imprimir`, `Enviar a la familia`, `Archivo`.
- Revisa a pantalla completa para poder decidir rápido y mover o copiar sin volver a la lista de archivos.
- Si varias personas cuidan el mismo archivo, mantén un esquema de nombres de destino coherente antes de una sesión de ordenación grande.

**Cuándo ayuda**

- Tienes pendiente material de cumpleaños, viajes, eventos escolares o importaciones de teléfonos antiguos.
- Quieres un flujo de selección rápido en lugar de arrastrar archivos a mano en un gestor de archivos.

**Evita esto**

- No empieces a ordenar antes de nombrar claramente los destinos.
- No uses Mover de inmediato si todavía no tienes claro qué carpetas deben quedar como archivo a largo plazo.

## Captura la pantalla con gestos de borde {#capture-the-screen-with-edge-gestures}

**Disponible en:** Standard, XR/noLegal

**Vía rápida**

1. Ve a **Ajustes → Gestión → Gestos de borde de pantalla → Superposición de gestos** y actívala.
2. Mientras ves cualquier archivo, desliza desde el borde izquierdo para abrir el menú de captura.
3. Elige una acción - la franja se cierra y la acción se ejecuta.

**Lo que puede hacer la franja**

- Tomar una **captura de pantalla** de la pantalla actual - verla, editarla, compartirla, enviarla a otra app o ejecutar traducción OCR sobre ella, además de una opción de captura silenciosa.
- **Tomar una foto** con la cámara y luego enviarla, editarla o ejecutar OCR-traducción sobre ella sin salir de la app.
- Iniciar una grabación de **pantalla**, de **vídeo** o de **audio/voz** - consulta [Grabar tu pantalla](#how-to-record-your-screen) y [Grabar una nota de voz](#how-to-record-a-voice-note).
- **Abrir una app o un panel** que uses a menudo.
- **Recortar y compartir** una región de la imagen actual.

**Bueno saberlo**

- Mientras la franja está activa, un deslizamiento desde el borde izquierdo abre el menú de captura en lugar de pasar de página.
- La franja está pensada para captura con una sola mano mientras navegas - desactívala si dependes de los deslizamientos de página por el borde izquierdo.
- Android confirma la captura o la grabación cada vez que usas este gesto, incluso para la opción de captura silenciosa - es una salvaguarda del sistema, no algo que la app controle.

**Cuándo ayuda**

- Quieres una captura de pantalla, una foto rápida o una grabación sin salir del archivo que estás viendo.

## Referencia de tareas principales

## Cómo añadir o importar una emisión de Internet

**Disponible en:** Standard, Legacy, XR / noLegal (todos los protocolos) - la pantalla de Emisiones está ausente en Lite y Photos

**Añadir una sola URL:**

1. Abre **Emisiones** en el menú desplegable de la ventana principal.
2. Toca el botón **⋮** al final de la barra de herramientas y luego **Añadir emisión**.
3. Pega la URL de la emisión (radio http/https, .m3u8, rtsp://). Toca **Guardar**.
4. Toca la fila para empezar la reproducción.

**Importar una lista de reproducción .m3u remota:**

1. En la pantalla de Emisiones, toca **⋮ > Importar desde URL**.
2. Introduce la dirección .m3u remota. Toca **Importar**.
3. Todas las emisoras del archivo aparecen en la lista.

**Descargar el catálogo seleccionado de FastMediaSorter:**

1. Abre **Ajustes > Extensiones** (o la fila de Emisiones de la bienvenida inicial).
2. Toca **Descargar** junto a la entrada del catálogo de Emisiones.
3. Tras la descarga, las filas del catálogo aparecen en Emisiones con chips de tema/idioma, y se pueden buscar y ordenar.

---

## Cómo conectar a una unidad de red (SMB) {#how-to-connect-to-network-drive-smb}

**Lo que necesitas:**

- Un NAS o un PC con Windows con una carpeta compartida
- Ambos dispositivos en la misma red Wi-Fi
- Nombre de usuario y contraseña para el recurso compartido

**Disponible en:** las variantes Standard, Photos, Legacy, XR / noLegal

**Pasos:**

1. **Toca el botón "+"** en la pantalla principal
2. Selecciona **"Carpeta de red (SMB)"**
3. Rellena los datos:
   - **Detección automática (nueva):**
     1. Toca el botón **"Buscar en la red"**
     2. Espera a que aparezcan los dispositivos en la lista
     3. Selecciona tu dispositivo en la lista
     4. La dirección IP se rellenará automáticamente

   - **Entrada manual:**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Toca **"Probar conexión"** para verificar
5. Toca **"Guardar"**

**Formatos de dirección del servidor:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- Con puerto: `smb://192.168.1.100:445/share`

**Consejos:**

- Usa la dirección IP (no el nombre de host) para mayor fiabilidad
- Activa SMB v2/v3 en el NAS por seguridad
- Puerto SMB por defecto: 445

**Solución de problemas:**
→ Consulta [TROUBLESHOOTING-es.md](TROUBLESHOOTING-es.md)

---

## Cómo conectar a un servidor SFTP/FTP {#how-to-connect-to-sftpftp-server}

**Lo que necesitas:**

- Un servidor con SSH (SFTP) o FTP activado
- El puerto 22 (SFTP) o 21 (FTP) abierto
- Nombre de usuario y contraseña (o clave para SFTP)

**Pasos:**

1. **Toca el botón "+"** en la pantalla principal
2. Selecciona **"SFTP / FTP"**
3. Elige el protocolo: **SFTP** o **FTP**
4. Rellena los datos:

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Toca **"Conectar"**

**Avanzado:**

- **Autenticación con clave SSH:** actualmente no admitida (solo contraseña)
- **Puerto personalizado:** cambia el número de puerto si el servidor usa uno distinto del predeterminado

**Solución de problemas:**
→ Consulta [TROUBLESHOOTING-es.md](TROUBLESHOOTING-es.md)

---

## Cómo importar un recurso compartido del compañero de Windows {#how-to-import-a-windows-companion-share}

**Qué es:** el compañero es una función de [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (antes FastMediaSorter LITE) - el sorteador de medios gratuito para Windows del mismo autor. Comparte las carpetas del PC que elijas por SFTP y exporta una conexión ya preparada - sin configuración manual de servidor, sin escribir host, puerto ni clave en el teléfono. Lo llevas al teléfono **escaneando un código QR** en la pantalla del PC, o **importando un archivo `.fmscfg`**.

**Disponible en:** Standard, Photos, Legacy, XR/noLegal (escanear el código de barras necesita cámara; el método de archivo funciona en todas partes, incluida VR)

> ¿Prefieres una versión guiada y con capturas de pantalla? Consulta la guía de escenario [Abre carpetas del PC escaneando un código](howto/scenario-companion-share-es.md).

**Consigue Fast Media Sorter for Windows:**

- Sitio web: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Guía de publicación de carpetas: [Cómo publicar carpetas del PC en Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [última versión](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (instalador o ZIP portable)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: busca "FastMediaSorter LITE" (aún aparece con el nombre anterior)

**En el PC:**

1. Instala y ejecuta **Fast Media Sorter for Windows**, abre la pestaña **Compartir** en los ajustes.
2. Elige la carpeta o carpetas a compartir - la app inicia el servidor SFTP, genera las claves y configura el inicio automático por sí sola.
3. Muestra un **código QR** en pantalla. También puede **Guardar .fmscfg** si prefieres un archivo.

**En el teléfono - Método A, escanear el código (el más rápido):**

1. **Toca el botón "+"** en la pantalla principal.
2. Toca **"Importar por código de barras"** - está junto a las cuatro tarjetas de tipo de recurso y en la cabecera del formulario SFTP.
3. Apunta la cámara al QR del PC (toca **Linterna** en una sala oscura) y confirma el diálogo **Importar acceso**.
4. Listo - aparece un recurso de solo lectura por cada carpeta compartida, con la clave del servidor fijada automáticamente.

**En el teléfono - Método B, importar el archivo:**

1. En el PC, usa **Guardar .fmscfg** y transfiere el archivo al teléfono (correo, Telegram o una ubicación compartida).
2. **Toca "+"** -> **"SFTP / FTP"** -> **"Importar desde archivo"** y elige el archivo `.fmscfg`. Si llegó como adjunto de Telegram o correo, simplemente toca el adjunto.
3. Confirma el diálogo **Importar acceso** - aparecen los recursos de solo lectura.

**Nota:** tanto el código QR como el archivo de configuración incluyen la contraseña de acceso - trátalos como una llave, no publiques la captura de pantalla ni el archivo. La entrada **Importar por código de barras** está oculta en dispositivos sin cámara y en visores VR; usa el Método B en esos casos.

---

## Cómo conectar a almacenamiento en la nube {#how-to-connect-to-cloud-storage}

**Proveedores admitidos:**

- Google Drive
- OneDrive
- Dropbox

**Pasos:**

1. **Toca el botón "+"** en la pantalla principal
2. Selecciona **"Almacenamiento en la nube"**
3. Selecciona el proveedor: **Google Drive**, **OneDrive** o **Dropbox**
4. Toca el botón **"Iniciar sesión.."**
5. Sigue el flujo de autenticación del navegador/app
6. Concede los permisos necesarios
7. **Selecciona las carpetas** a sincronizar
8. Toca **"Hecho"**

**Notas:**

- Los archivos se **transmiten**, no se descargan
- Requiere conexión a internet
- Las ediciones se sincronizan automáticamente
- Puedes desconectar cuando quieras: Editar carpeta → Eliminar

**Privacidad:**

- No se guarda ninguna contraseña (usa tokens OAuth)
- Los tokens se pueden revocar en los ajustes de seguridad de tu proveedor de nube

---

## Comprobar la velocidad de la red

**Compatible con:** SMB, SFTP, FTP, nube (Google Drive)

**Comprobación automática:**
Cuando añades un nuevo recurso de red, la app ejecuta automáticamente una prueba de velocidad en segundo plano. Los resultados (velocidad de lectura/escritura) se guardan en los ajustes del recurso.

**Comprobación manual:**

1. Ve a **Gestionar recursos**
2. Edita un recurso de red (icono del lápiz)
3. Desplázate hasta el final
4. Toca el botón **"Velocidad"**
5. Espera unos 15 segundos mientras dice "Analizando velocidad.."
6. Consulta los resultados:
   - **Velocidad de lectura (Mbps)**
   - **Velocidad de escritura (Mbps)**
   - **Hilos recomendados** (para un rendimiento óptimo)

---

## Cómo configurar las carpetas de Quick Sort {#how-to-set-up-quick-sort-folders}

**Método 1: desde Ajustes**

1. **Ajustes** → pestaña **Gestión** → **Destinos de Quick Sort**
2. Toca **"Añadir a Quick Sort"**
3. Selecciona una carpeta existente de la lista
4. La carpeta recibe un número (0-9) y un color
5. Repite para hasta 30 carpetas

**Método 2: desde los ajustes de la carpeta**

1. Pantalla principal → **mantén pulsada la carpeta**
2. Toca **"Editar"** (icono del lápiz)
3. Activa **"Marcar para Quick Sort"**
4. Toca **"Guardar"**

**Usando Quick Sort:**

Mientras ves los archivos:

- Toca el **botón numerado** (0-9) en el panel de comandos
- O toca la **esquina inferior izquierda** (zona COPIAR)
- O toca la **esquina inferior central** (zona MOVER)

¡El archivo se copia/mueve al instante a esa carpeta!

**Con un teclado o el mando de la TV:** conecta uno y los botones de destino reciben una insignia con un dígito - pulsa la tecla numérica correspondiente para disparar ese destino al instante, sin necesidad de tocar.

---

## Cómo usar las zonas táctiles {#how-to-use-touch-zones}

**¿Qué son las zonas táctiles?**

La pantalla se divide en 9 áreas invisibles para acciones rápidas:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Leyenda:**

1. **BACK** (ATRÁS) - Vuelve a la lista de archivos
2. **COPY** (COPIAR) - Copia el archivo al destino
3. **RENAME** (RENOMBRAR) - Renombra el archivo actual
4. **PREV** (ANTERIOR) - Va al archivo anterior
5. **MOVE** (MOVER) - Mueve el archivo al destino
6. **NEXT** (SIGUIENTE) - Va al archivo siguiente
7. **COMMAND** (COMANDO) - Abre el menú de comandos
8. **DELETE** (ELIMINAR) - Elimina el archivo actual
9. **PLAY** (REPRODUCIR) - Inicia/detiene la presentación de diapositivas

**Activar la superposición (recomendado para principiantes):**

1. Ajustes → Reproductor
2. Activa **"Mostrar siempre la superposición de zonas táctiles"**
3. Ahora verás una cuadrícula semitransparente

**Pruébalo:**

1. Abre cualquier foto
2. **Toca la esquina superior derecha** → archivo siguiente
3. **Toca la esquina superior izquierda** → archivo anterior
4. **Toca la esquina central derecha** → eliminar archivo
5. **Toca la esquina central izquierda** → copiar archivo

**Desactivar si no la necesitas:**
Ajustes → Reproductor → "Mostrar siempre la superposición de zonas táctiles" = DESACTIVADO

Luego usa los **botones del panel de comandos** en su lugar.

---

## Cómo editar fotos {#how-to-edit-photos}

**Operaciones admitidas:**

- Girar (90°, 180°, 270°)
- Voltear (horizontal, vertical)
- Filtros (Escala de grises, Sepia, Negativo)
- Ajustar (Brillo, Contraste, Saturación)

**Pasos:**

1. **Abre una foto** en el visor a pantalla completa
2. Toca el botón **"Editar"** (o la zona táctil central izquierda)
3. **Elige la operación:**
   - Girar: toca el icono de girar
   - Voltear: toca el icono de voltear
   - Filtro: selecciona de la lista
   - Ajustar: usa los deslizadores
4. Toca **"Guardar"**

**Notas:**

- El archivo original se **sobrescribe** (¡sin deshacer!)
- Funciona con **archivos locales y de red**
- Admite: JPG, PNG, WEBP

---

## Cómo crear una presentación de diapositivas {#how-to-create-slideshow}

**Pasos:**

1. **Abre cualquier carpeta** con fotos
2. Toca la **primera foto** para abrir el visor
3. Toca el botón **"Reproducir"** (o la zona táctil inferior derecha)
4. La presentación empieza automáticamente

**Personalizar la velocidad:**

1. **Edita los ajustes de la carpeta:**
   - Pantalla principal → mantén pulsada la carpeta → Editar
2. Cambia el **"Intervalo de la presentación":**
   - Rápido: 2 segundos
   - Normal: 5 segundos
   - Lento: 10 segundos
3. Toca **"Guardar"**

**Controles durante la presentación:**

- **Toca la pantalla** → Pausar/Reanudar
- **Desliza a izquierda/derecha** → Saltar archivos
- **Toca "Detener"** → Salir de la presentación

---

## Cómo crear una presentación de diapositivas con música de fondo {#how-to-create-slideshow-with-background-music}

**Requisitos:**

- Al menos una carpeta o recurso con archivos de audio (MP3, FLAC, etc.)
- **Disponible en:** Standard, Lite, Legacy, XR / noLegal (Photos no tiene compatibilidad con audio)

**Configuración:**

1. **Ajustes** → pestaña **Multimedia** → **Imágenes**
2. Activa **"Reproducir música durante la presentación"**
3. Toca el botón **"Seleccionar fuente de música"**
4. Elige un recurso que contenga tus archivos de música
5. Toca **"Guardar"** o cierra los ajustes

**Reproducir la presentación con música:**

1. **Abre cualquier carpeta** con fotos/imágenes
2. Toca la **primera foto** para abrir el visor
3. Toca el botón **"Reproducir"** (o la zona táctil inferior derecha)
4. La presentación empieza con música de fondo sonando

**Cómo funciona:**

- La música se reproduce de forma aleatoria desde el recurso de música seleccionado
- Cuando una pista termina, empieza automáticamente otra al azar
- La música continúa durante las transiciones de imagen
- La música se detiene al salir de la presentación o al pausarla

**Notas:**

- La música solo suena para **imágenes y GIF** (no para vídeos ni audio)
- Cuando la presentación muestra un vídeo, la música se pausa automáticamente
- La música se reanuda al volver a las imágenes
- Funciona con fuentes de música locales y de red (SMB, SFTP, FTP)

**Personalizar la selección de música:**

- Añade varios archivos de música a tu carpeta de recurso de música
- La app mezclará aleatoriamente todos los archivos de audio
- Organiza la música en subcarpetas si el recurso tiene activado "Incluir subcarpetas"

**Solución de problemas:**

- Si no suena música: comprueba que el recurso de música contenga al menos un archivo de audio
- Si la música se entrecorta en la red: usa una carpeta local o una conexión de red más rápida
- Para música por SMB: asegúrate de que el recurso SMB use el protocolo `file://` (consulta TROUBLESHOOTING-es.md)

---

## Cómo proteger una carpeta con PIN {#how-to-protect-folder-with-pin}

**Pasos:**

1. Pantalla principal → **mantén pulsada la carpeta**
2. Toca **"Editar"** (icono del lápiz)
3. Desplázate hasta el campo **"Código PIN"**
4. Introduce un **PIN de 4 a 6 dígitos** (por ejemplo, 1234)
5. Toca **"Guardar"**

**Ahora:**

- Abrir esta carpeta requiere el PIN
- Evita el acceso no autorizado
- Se aplica tanto a la navegación como a la edición

**Eliminar el PIN:**

- Editar la carpeta → borrar el campo PIN → Guardar

**¿Olvidaste el PIN?**

- No hay opción de recuperación (así está diseñado, por seguridad)
- Tendrás que eliminar la carpeta y volver a añadirla

---

## Cómo cifrar un archivo con FileDO {#how-to-encrypt-a-file-with-filedo}

Un contenedor FileDO es un único archivo con la extensión `.fd-sec` que guarda otro archivo bloqueado con una contraseña. El formato es el mismo que usa la app de escritorio FileDO, así que un contenedor creado aquí se abre en FileDO, y al revés también.

**Activar los comandos:** **Ajustes** → pestaña **Gestión** → **Operaciones de cifrado FileDO**. Abrir un contenedor funciona esté este interruptor activado o no.

**Cifrar un archivo:**

1. En Explorar, abre el menú **⋮** del archivo.
2. Toca **Cifrar con FileDO**.
3. Introduce la contraseña dos veces y confírmala.
4. El contenedor aparece junto al archivo como `<name>.fd-sec`. El archivo original queda intacto - elimínalo tú mismo si ya no lo necesitas.

**Descifrar un archivo:** abre el menú **⋮** del archivo `.fd-sec`, toca **Descifrar con FileDO** e introduce la contraseña. El archivo restaurado aparece junto al contenedor.

**Abrir un contenedor sin restaurarlo:** toca el archivo `.fd-sec` en cualquier carpeta que muestre todos los tipos de archivo. La app solo pide la contraseña y abre el archivo interior en el visor. La copia descifrada se queda en el almacenamiento privado de la app y se elimina al volver a la lista. Marca **Recordar la contraseña y probarla en cada archivo .fd-sec** para saltarte el aviso la próxima vez.

**Dónde funciona:** carpetas del dispositivo, carpetas elegidas mediante el selector de carpetas del sistema, y recursos SMB, SFTP y FTP. En una carpeta del selector o en un recurso de red, el archivo se procesa como una copia privada, el resultado se escribe con un nombre temporal, se relee y se comprueba, y solo entonces se renombra a su lugar definitivo - un archivo existente nunca se sobrescribe.

**Si no se abre:** el mensaje nombra tres posibles causas - una contraseña incorrecta, un archivo que nunca fue un contenedor, o un contenedor que fue modificado. No se pueden distinguir entre sí. Un contenedor que guarda un programa o un script no se abre.

**¿Olvidaste la contraseña?** No hay forma de recuperarla. Una contraseña vacía solo oculta el archivo de una mirada casual.

---

## Cómo trabajar con carpetas (seleccionar, copiar, mover)

Cuando las subcarpetas se muestran como elementos independientes en la lista, una fila de carpeta se comporta como una fila de archivo.

**Activar las filas de carpeta:** **Ajustes** → **General** → **Mostrar subcarpetas por separado**. El mismo interruptor existe por recurso en el editor de recursos.

**Pasos:**

1. Toca la casilla de una fila de carpeta, o mantén pulsada la fila, para seleccionar una sola carpeta. Un toque corto sigue abriendo la carpeta.
2. Usa el menú **⋮** de la fila, o la barra de acciones de selección, para elegir **Copiar**, **Mover**, **Renombrar** o **Eliminar**.
3. Elige el destino. Los archivos y carpetas de la misma selección viajan juntos en una sola operación.
4. El destino recibe toda la estructura - cada subcarpeta y archivo dentro de la carpeta de origen.

**Entre tipos de recurso:** una carpeta se puede copiar o mover entre el dispositivo, SMB, SFTP, FTP y recursos en la nube - la estructura se recrea en el lado receptor.

**Qué se rechaza, y por qué:** un destino dentro de la propia carpeta, o la ubicación actual de la propia carpeta, se rechaza antes de copiar nada; un destino elegido mediante el selector de carpetas del sistema que no tenga una ruta de archivo real no puede recibir carpetas. El mensaje indica el motivo para que puedas elegir otro destino.

**Cancelar:** una transferencia de carpetas muestra el progreso y se puede detener. Lo que ya se había escrito permanece en el destino - revisa la carpeta antes de volver a empezar. Una operación de mover elimina cada elemento de origen solo después de que su copia se haya completado con éxito, así que nada se pierde por el camino.

**Enviarlo a segundo plano:** no tienes que quedarte mirando el diálogo de progreso. Ciérralo y la transferencia sigue en marcha, visible en Explorar como una franja al pie de la pantalla que muestra la operación, el porcentaje y el archivo en el que está trabajando ahora mismo. Toca esa franja para volver a traer el diálogo de progreso completo, cancelación incluida.

---

## Cómo vaciar la papelera {#how-to-empty-trash}

Los archivos eliminados van a carpetas `.trash/` y permanecen ahí hasta que se vacían manualmente.

**Método 1: vaciar toda la papelera**

1. **Ajustes** → pestaña **Gestión** → **Eliminación de archivos y papelera**
2. Toca **"Vaciar papelera"**
3. Confirma la eliminación
4. Todas las carpetas `.trash/` de todos los recursos se vacían

**Método 2: por carpeta**

1. Usa una app gestora de archivos
2. Ve a la carpeta (por ejemplo, `/storage/emulated/0/DCIM/Camera`)
3. Busca la subcarpeta `.trash/`
4. Elimínala manualmente

**Advertencia:** ¡Esto es una **eliminación permanente**! Los archivos no se pueden recuperar.

---

## Cómo hacer copia de seguridad de los ajustes {#how-to-backup-settings}

**Exportar ajustes:**

1. **Ajustes** → pestaña **General** → **Copias de seguridad, restauración y exportación de ajustes**
2. Toca **"Exportar todos los ajustes a un archivo"**
4. Elige la ubicación (por ejemplo, Descargas)
5. El archivo se guarda como `fastmediasorter_backup.xml`

**Restaurar ajustes:**

1. **Ajustes** → pestaña **General** → **Copias de seguridad, restauración y exportación de ajustes**
2. Toca **"Importar ajustes desde un archivo"**
4. Selecciona el archivo de copia de seguridad
5. Toca **"Restaurar"**
6. La app se reinicia con los ajustes restaurados

**Qué se incluye:**
✅ Carpetas de Quick Sort
✅ Preferencias de visualización
✅ Intervalos de presentación
✅ Credenciales de red (cifradas)
✅ Favoritos
✅ Ajustes del Modo seguro

**NO incluido:**
❌ Caché de miniaturas
❌ Contenido de la papelera  

---

## Cómo ver archivos de texto y PDF {#how-to-view-text-and-pdf-files}

**1. Activar la compatibilidad:**

1. **Ajustes** → pestaña **Multimedia** → **Documentos**
2. Activa **"Compatibilidad con archivos de texto (.txt, .md, .log, .json, .xml)"** y **"Compatibilidad con documentos PDF"**
3. **Vuelve a escanear** tus carpetas para encontrar los archivos nuevos.

**2. Filtrar por tipo de contenido:**

1. Toca el **icono de filtro** (embudo) en la pantalla principal (arriba a la derecha).
2. Usa las casillas para seleccionar los tipos de contenido:
   - Imágenes
   - Vídeos
   - Audio
   - GIF
   - **Texto** (Nuevo)
   - **PDF** (Nuevo)
3. Toca **"Aplicar"** para ver solo los archivos seleccionados.

**3. Visor de texto:**

- Toca cualquier archivo **.txt, .md, .log, .json, .xml**.
- **Desplázate** para leer.
- **Copiar texto:** mantén pulsado para seleccionar y copiar.

**4. Visor de PDF (funciones nuevas):**

- Toca cualquier archivo **.pdf**.
- **Barra de navegación (abajo):**
  - **Anterior/Siguiente:** botones grandes en los extremos.
  - **Acercar (+):** amplía la página.
  - **Alejar (-):** reduce la página.
- **Gestos:**
  - **Deslizar hacia ARRIBA:** ir a la página siguiente.
  - **Deslizar hacia ABAJO:** ir a la página anterior.
  - **Pellizcar:** acercar/alejar de forma natural.
  - **Doble toque:** restablecer el zoom.
  - **El zoom se traslada:** la página siguiente se abre con el zoom y la posición con la que estabas leyendo; el doble toque devuelve la página completa.
- **Desplazamiento:** arrastra para moverte cuando estás con zoom.
- **Selecciona texto manteniendo pulsado (Android 15+):** mantén el dedo sobre una palabra para seleccionarla directamente desde la propia capa de texto de la página - sin pasada de OCR, sin esperas. Si la misma palabra aparece varias veces en la página, se selecciona la que está bajo tu dedo, no la primera. Arrastra los tiradores para ampliar la selección, y luego cópiala o tradúcela.

---

## Cómo leer libros electrónicos (EPUB) {#how-to-read-e-books-epub}

**Requisitos:**

- **Ajustes** → pestaña **Multimedia** → **Documentos** → **Compatibilidad con libros electrónicos EPUB** debe estar activada (activada por defecto)
- Formato admitido: `.epub` (sin DRM)

**Funciones:**

- **Navegación por capítulos:** desliza a izquierda/derecha o usa los botones del panel de comandos
- **Tabla de contenidos:** toca el icono de lista <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> para saltar a un capítulo concreto
- **Tamaño de letra:** ajustable (6px - 144px, por defecto 18px)
- **Búsqueda:** busca texto dentro del libro actual
- **Temas:** se adapta automáticamente al modo claro/oscuro

**Controles:**

1. **Abre un archivo EPUB** desde la lista de archivos
2. **Toca la pantalla** para alternar el panel de comandos
3. **Usa los controles inferiores:**
   - `Anterior` / `siguiente`: navega entre capítulos
   - `- A` / `+ A`: reduce/aumenta el tamaño de letra
   - `Buscar` <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: busca texto
   - `TOC` <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: abre la tabla de contenidos
4. **Gesto de deslizar:** cambia de capítulo con naturalidad

**Nota:** Funciona sin problemas con archivos locales y con recursos de red (SMB/SFTP/Nube). Los libros grandes (>50MB) en redes lentas pueden tardar unos segundos en cargar al principio.

---

## Cómo abrir archivos de red en aplicaciones externas {#how-to-open-network-files-in-external-apps}

**Disponible para:** archivos SMB, SFTP, FTP

**Caso de uso:** quieres abrir un documento, una foto o un vídeo de tu unidad de red en una app externa especializada (por ejemplo, MS Office, Adobe Acrobat, VLC Player).

**Pasos:**

1. **Navega hasta el archivo** en tu recurso de red
2. **Toca el archivo** para abrirlo en el reproductor/visor
3. **Toca el botón ⓘ (Información)** en la barra de herramientas superior
4. **Toca el botón "Descargar y abrir"**
5. **Espera la descarga** - el diálogo de progreso muestra el porcentaje
6. **Elige la app** en el selector de apps de Android

**Qué ocurre:**

- El archivo se descarga a tu carpeta `Downloads`
- El progreso se muestra en un diálogo (0-100%)
- Al terminar la descarga, Android muestra el selector de apps
- Puedes abrir el archivo en cualquier app compatible

**Protocolos admitidos:**

- ✅ Recursos compartidos de red SMB/CIFS
- ✅ Servidores SFTP
- ✅ Servidores FTP
- ❌ Almacenamiento en la nube (aún no implementado)

**Consejos:**

- Los archivos descargados permanecen en la carpeta `Downloads`
- Puedes eliminarlos manualmente más tarde con un gestor de archivos
- Funciona con todos los tipos de archivo (imágenes, vídeos, documentos, etc.)
- Para archivos grandes, la descarga puede tardar varios minutos

**Ejemplos de uso:**

- Editar un documento de red en MS Word
- Reproducir un vídeo de red en VLC Player
- Ver un PDF de red en Adobe Acrobat
- Compartir una foto de red por apps de mensajería

---

## Cómo ver la letra de una canción {#how-to-view-song-lyrics}

**Requisitos:**

- Un archivo de audio (MP3, FLAC, etc.) con metadatos de artista y título.
- Se requiere **conexión a internet** (usa api.lyrics.ovh).

**Pasos:**

1. **Reproduce un archivo de audio** en el reproductor a pantalla completa.
2. Toca el botón **"Letra"** en el panel de comandos superior (o en el menú de comandos).
   - *Nota: el botón solo es visible para archivos de audio.*
3. Espera a que termine la búsqueda.
4. La letra se mostrará en un diálogo desplazable.

**Lógica de búsqueda:**

1. La app busca por la etiqueta **Artista + Título**.
2. Si faltan las etiquetas, intenta analizar el **nombre del archivo**.

---

## Traducción automática {#auto-translation}

Traduce automáticamente texto de imágenes, PDF y archivos de texto: **Tesseract** lee el texto, Google ML Kit lo traduce.

**Características clave:**

- **Un solo motor de lectura:** **Tesseract** lee texto latino y cirílico (inglés, ruso, ucraniano, búlgaro, bielorruso); Google ML Kit traduce el resultado e identifica su idioma.
- **Sin conexión:** funciona enteramente en el dispositivo (tras la descarga inicial del modelo).
- **Superposición inteligente:** el texto traducido se superpone al texto original en párrafos legibles.

**Configuración:**

1. **Ajustes** → pestaña **Multimedia** → **Otros**
2. Activa **"Activar traducción"**
3. Selecciona el **idioma de origen**:
   - **"Automático":** lee el texto con el modelo en inglés y luego detecta el idioma de lo leído para la traducción.
   - **Idioma específico:** lee con el modelo de ese idioma - elígelo para texto en cirílico (por ejemplo, "Ruso").
4. Selecciona el **idioma de destino** (por ejemplo, español).

**Cómo usarlo:**

1. Abre una **imagen**, un **PDF** o un archivo de **texto**.
2. Toca la pantalla para mostrar el **panel de comandos**.
3. Toca el botón **"Traducir"** (icono A→文).
4. **Primera vez:**
   - Confirma la descarga del modelo de texto para el idioma de origen.
   - Confirma la descarga del modelo de traducción para el par de idiomas.
5. El texto traducido aparecerá en una superposición.

**Nota:** el primer uso de un idioma carga su modelo de texto, lo que añade un breve retraso.

## Widgets inteligentes de pantalla de inicio {#home-screen-smart-widgets}

**Disponible en:** todas las variantes - el conjunto de widgets está en todas las compilaciones; cada widget sigue su propia capacidad, así que el widget de grabadora de voz necesita una compilación con soporte de micrófono (no Lite ni Photos), mientras que el marco de fotos y los widgets de recursos funcionan en todas partes

**Vía rápida**

1. Ve a la pantalla de inicio de Android, mantenla pulsada y selecciona **Widgets**.
2. Arrastra un widget de **FastMediaSorter** (como Grabadora de voz rápida 1×1 u OCR de cámara) a tu pantalla.
3. Configura la carpeta de destino y los ajustes de captura, y luego toca **Guardar**.
4. Usa el widget para ejecutar tareas de un toque directamente desde tu pantalla de inicio.

**Recorrido del escenario**

- Usa widgets 1×1 como iconos de lanzamiento dedicados para iniciar acciones en segundo plano al instante (por ejemplo, toca una vez para empezar a grabar voz, toca de nuevo para guardarla en tu NAS).
- Configura un **widget de operaciones programadas** para vigilar transferencias de archivos en segundo plano o disparar una operación "Ejecutar todo".
- Coloca un **widget de marco de fotos aleatorio** para mostrar una presentación rotativa de fotos familiares obtenidas directamente de un recurso SMB.

**Cuándo ayuda**

- Quieres accesos directos rápidos en tu pantalla de inicio para capturas diarias (recibos, notas de voz) sin abrir la interfaz principal de la app.
- Necesitas widgets claros para controlar el contenido multimedia o disparar operaciones programadas al instante.

**Evita esto**

- No intentes añadir widgets si tu lanzador de Android restringe la creación de widgets personalizados.

---

## Cómo usar la app como tu pantalla de inicio {#how-to-use-the-app-as-your-home-screen}

FastMediaSorter puede sustituir la pantalla de inicio de tu dispositivo y mostrar su propio escritorio en su lugar - tus carpetas, un reloj, el tiempo, tus apps y una barra de tareas a lo largo de un borde. Si alguna vez has usado un escritorio de Windows, te resultará familiar: las cosas se quedan donde las pones, y un botón de Inicio abre el menú. Esto se llama modo lanzador, y solo está disponible en las compilaciones **Standard** y **noLegal**.

**Activarlo:**

1. Abre **Ajustes → General** y activa **Hacer de esta app la pantalla de inicio**.
2. Android te pide que lo confirmes. En Android 10 y versiones posteriores es una sola pregunta - "¿Permitir que FastMediaSorter sea tu app de Inicio?" - así que simplemente permítelo. En versiones anteriores aparece la elección clásica la próxima vez que pulses Inicio: elige FastMediaSorter y toca **Siempre**, o **Solo esta vez** si solo quieres probarlo por ahora.
3. Pulsa Inicio. El escritorio aparece, ya lleno con una docena de cosas útiles - un reloj, el tiempo, tus carpetas, un cuadro de búsqueda - así que el primer día no es una cuadrícula vacía.

En una instalación recién hecha hay un atajo: marca **Usar como pantalla de inicio** en la primera página de bienvenida. Eso no interrumpe la configuración con un diálogo del sistema - la confirmación de Android aparece la primera vez que abres **Ajustes → General** después.

**Qué hay en el escritorio:**

| Tipo de celda | Qué hace |
|-----------|--------------|
| Acceso directo a recurso | Abre una carpeta que has añadido - y eliges si se abre en modo explorar, presentación o reproducción |
| Gadget | Un reloj con segundos (toca para las alarmas), el tiempo donde vives, lo que está sonando ahora mismo, un traductor, y dos docenas más |
| Acceso directo a app | Inicia cualquier app instalada; mantener pulsado muestra las acciones rápidas propias de esa app |
| Celda de contacto | Abre la ficha de una persona, la llama, le envía un SMS o abre su conversación de mensajería |
| Widget de app | Los mismos widgets que la app ofrece para la pantalla de inicio de Android, colocados aquí en su lugar |

**La barra de tareas y el menú de Inicio:**

- La barra de tareas se sitúa a lo largo del borde inferior y contiene el botón de Inicio, las apps que has usado recientemente, las que has fijado, y una pequeña bandeja con el reloj, la batería, la red y la señal de la SIM.
- ¿Prefieres tenerla arriba? **Ajustes → General → Ajustes del lanzador del sistema → Barra de tareas → Posición de la barra de tareas** cambia entre **Abajo** y **Arriba**. El menú de Inicio sigue a la barra y se despliega desde arriba cuando la barra está ahí.
- El botón de Inicio abre el menú: abrir FastMediaSorter, tus recursos, añadir un recurso, ajustes de Android, ajustes de la app, ajustes del lanzador, editar el contenido del escritorio y, al final, reiniciar, apagar y **Salir del modo lanzador**. Reiniciar y apagar solo funcionan si tu dispositivo permite que una app normal lo haga - en la mayoría de los teléfonos simplemente no harán nada.

**Tus apps:** la cuadrícula de apps agrupa las apps en secciones, cada una con un pequeño encabezado. Toca un encabezado para colapsar una sección que abres poco; los encabezados colapsados se acomodan unos junto a otros, así el escritorio se acorta en lugar de dejar huecos. Un escritorio recién creado divide las apps que siembra en dos: una sección **Google** para las apps de Google que ya tienes instaladas, y una sección **Apps** para las tuyas propias - mensajería, juegos y lo que sea que hayas puesto en el dispositivo. Ninguna app aparece en las dos. Mantén pulsada cualquier app de la lista para ver **Poner en el escritorio** y **Fijar a la barra de tareas**.

**Reorganizarlo:**

- Mantén pulsado un cuadro vacío del escritorio. Aparecen cuatro opciones: **Añadir un elemento..**, **Editar el escritorio**, **Fondo de pantalla**, **Ajustes del lanzador**. La celda nueva se coloca exactamente en el cuadro que pulsaste.
- **Añadir un elemento..** abre un selector: una app, una función, una de tus carpetas, una emisión de radio, una persona, una acción del sistema, una operación programada, un gadget o una acción. Entre los gadgets está la tarjeta de "sonando ahora" - muestra lo que se esté reproduciendo en el dispositivo y te lleva a ese reproductor con un toque - y la celda del traductor.
- **Editar el escritorio** activa el modo edición, lo mismo que **Editar el contenido del escritorio** en el menú de Inicio. Mientras editas: arrastra una celda para moverla, arrastra el tirador de la esquina de un gadget para redimensionarlo, toca **+** para añadir algo, y elige **Quitar del escritorio** en una celda para retirarla. Toca **Hecho** cuando termines.
- ¿Compartes el dispositivo con alguien? Activa **Bloquear escritorio** en los ajustes del lanzador - así mantener pulsado no hace nada, y el diseño no se puede mover por accidente.
- Otras apps pueden colocar aquí sus propios accesos directos, exactamente igual que en cualquier otra pantalla de inicio.

**El modo vertical y el horizontal son dos escritorios distintos.** Lo que organizas en vertical no es lo que obtienes al girar el dispositivo de lado - cada orientación conserva su propio diseño y sus propias secciones colapsadas. La app lo menciona una vez, la primera vez que giras un escritorio que has organizado. Los propios ajustes - posición de la barra de tareas, densidad, fondo de pantalla - se comparten entre ambas.

**Aprovechar más, o menos, la pantalla:** **Ajustes → General → Ajustes del lanzador del sistema → Escritorio → Densidad de la cuadrícula** ofrece **Dispersa**, **Estándar**, **Densa** y **Muy densa** - celdas más amplias, o más accesos directos por pantalla.

**Volver a tu antigua pantalla de inicio** - cualquiera de estas tres:

- Abre el menú de Inicio, elige **Salir del modo lanzador** y confirma.
- Desactiva **Hacer de esta app la pantalla de inicio** en **Ajustes → General**.
- Ve directamente a la propia lista de Android de apps de inicio: **Ajustes → General → Ajustes del lanzador del sistema → Sistema → Cambiar pantalla de inicio**.

Tu diseño de escritorio se conserva de todos modos, así que volver a activar el modo lo trae de vuelta exactamente como lo dejaste.

**Una advertencia sincera.** Algunos dispositivos se niegan a recordar la elección. Algunas radios de coche baratas de otras marcas y otras cajas Android integradas fuerzan de vuelta su pantalla de inicio de fábrica cada vez que arrancan, sea lo que sea que hayas elegido. Eso es el propio firmware del dispositivo anulándote, no un fallo de la app, y ninguna app puede evitarlo. Si el tuyo se comporta así, vuelve a elegir FastMediaSorter como app de inicio después de un reinicio - y si aun así no se mantiene, ese dispositivo simplemente no lo permite.

---

## Cómo usar FMS en un Android TV Box {#how-to-use-fms-on-android-tv-box}

FastMediaSorter funciona en cualquier Android TV Box o decodificador (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, cajas Android genéricas). No hace falta pantalla táctil - la app se maneja por completo con el mando de la TV o un teclado Bluetooth.

**Lo que necesitas:**

- Un Android TV Box con Android 8.0+ (Standard/Lite/Photos) o Android 6.0+ (variante Legacy)
- Un mando de TV con D-pad, o un teclado Bluetooth
- Opcional: un NAS doméstico (SMB), una unidad USB o una tarjeta SD con contenido multimedia

**Navegación con un mando de TV:**

| Botón | Acción |
|--------|--------|
| D-pad arriba/abajo/izquierda/derecha | Mueve el foco entre elementos |
| OK / Intro | Abre el elemento o confirma |
| Atrás | Va a la pantalla anterior |
| Retroceso | Sube una carpeta en Explorar |
| Rojo | Elimina el/los archivo(s) seleccionado(s) |
| Verde | Copia el/los archivo(s) seleccionado(s) |
| Amarillo | Mueve el/los archivo(s) seleccionado(s) |
| Azul | Renombra el archivo seleccionado |
| Canal arriba / Canal abajo | Archivo anterior / siguiente en el reproductor |

**Pasos:**

1. Instala la app desde Google Play o carga lateralmente un APK. Se recomienda la variante Standard.
2. En la pantalla principal, pulsa **OK** sobre el botón (+) para añadir un recurso.
3. Elige **Carpeta local** para almacenamiento USB/SD, o **Carpeta de red** para conectar a un NAS por SMB.
4. Tras añadir el recurso, navega dentro con D-pad + OK para explorar los archivos.
5. Abre cualquier vídeo, imagen o archivo de audio - el reproductor funciona completamente con el mando.
6. Para iniciar una presentación de diapositivas, abre una carpeta de imágenes y navega hasta el botón **Presentación** en la barra de comandos.
7. Para añadir música de fondo a la presentación, ve a **Ajustes → Multimedia → Imágenes**, activa **Reproducir música durante la presentación**, y selecciona tu recurso de música.

**Consejos:**

- Mantén pulsado D-pad arriba/abajo para acelerar el desplazamiento en listas de archivos largas.
- Pulsa **F1** en un teclado Bluetooth para abrir una referencia de atajos específica de la pantalla actual.
- Las teclas de color del mando de TV se pueden reasignar en **Ajustes → Gestión → Controles y asignación de teclas**.

---

## Cómo grabar tu pantalla {#how-to-record-your-screen}

**Disponible en:** Standard, XR/noLegal

**Pasos:**

1. Inícialo desde el menú desplegable de la pantalla principal (**Grabación de vídeo de pantalla**), el panel de Inicio rápido, o la acción **Iniciar grabación de pantalla** del gesto de borde.
2. Confirma el aviso de Android para compartir tu pantalla o solo esta app - aparece cada vez que inicias una grabación y no se puede omitir.
3. Una pequeña píldora en la esquina muestra **Grabando pantalla**, con controles de pausa/reanudar y detener. Una notificación también ofrece **Detener**.
4. Toca **Detener** cuando termines.

**Qué ocurre:**

- La grabación captura todo lo que hay en pantalla, incluidas otras apps a las que cambies, junto con el audio.
- El vídeo terminado se guarda en la carpeta Películas de tu dispositivo.

**Nota:** el paso de confirmación de Android es una salvaguarda del sistema para cualquier cosa que grabe tu pantalla - no es algo que la app pueda desactivar.

---

## Cómo grabar una nota de voz {#how-to-record-a-voice-note}

**Disponible en:** Standard, Legacy, XR / noLegal

**Pasos:**

1. Inicia una grabación desde el elemento **Grabación de voz** en el menú desplegable, el widget de pantalla de inicio **Grabadora rápida**, o la acción **Iniciar grabación de audio** del gesto de borde.
2. Habla - un indicador **Grabando..** (o una píldora flotante sobre la app que esté delante) muestra que está en marcha.
3. Toca **Detener y guardar** (o vuelve a tocar el widget/gesto) para terminar.

**Qué ocurre:**

- La grabación se guarda en el destino de micrófono que hayas elegido en Ajustes, o en la carpeta Grabaciones de tu dispositivo si no has definido ninguno.
- Iniciar una nota de voz desde el widget o el gesto de borde funciona incluso mientras usas otra app - un pequeño control flotante se queda encima para que puedas detenerla sin volver a la app.

**Dónde definir la carpeta de guardado:** Ajustes → Gestión → Grabadora de voz.

---

## Cómo usar la cámara integrada {#how-to-use-the-in-app-camera}

**Disponible en:** Standard, Lite, Photos (solo foto), Legacy, XR/noLegal

**Pasos:**

1. En Explorar, abre la barra de herramientas o el menú desplegable y toca **Capturar con la cámara** (foto) o **Grabar vídeo**.
2. Cambia entre **Foto** y **Vídeo** directamente en la pantalla de la cámara si cambias de opinión.
3. Ajusta el zoom con un chip preestablecido (0.5x/1x/2x..) o con el deslizador de debajo - ambos se mantienen sincronizados.
4. Toca el botón de proporción para dar forma al encuadre - **4:3**, **16:9** o **Pantalla completa**. El propio visor cambia, así que lo que ves es lo que será la foto guardada, y la elección se recuerda la próxima vez que abras la cámara (16:9 hasta que lo cambies).
5. Toca el botón de escenario de disparo para elegir cómo se toma la foto - normal, noche, retrato, selfie, macro, deporte o documento. Macro salta a la lente dedicada de enfoque cercano, selfie cambia a la cámara frontal, deporte mantiene la exposición corta para que el movimiento se congele, y documento está ajustado para fotografiar páginas y pantallas planas. Solo se listan los escenarios que tu dispositivo puede ofrecer de verdad, el activo se nombra en el botón, y cambiar de lente a mano devuelve la cámara a normal.
6. Toca el disparador (o el botón de grabar) para capturar. El resultado se guarda directamente en el recurso - local o de red - que estabas explorando.

**Consejos:**

- Toca en cualquier punto del visor para enfocar y fijar la exposición ahí - un pequeño anillo marca el lugar.
- La acción **Iniciar grabación de vídeo** del gesto de borde abre la cámara ya en modo Vídeo y empieza a grabar en cuanto la vista previa está lista - rápido, pero ese atajo en concreto guarda en la carpeta Películas de tu dispositivo en lugar del recurso que estabas explorando.
- Activa **Geoetiquetar fotos** junto a los ajustes de la cámara para incrustar la ubicación GPS en cada JPEG capturado - está desactivado hasta que lo actives. **Información del archivo** muestra entonces la fecha de captura y el punto GPS del EXIF de la foto como un enlace pulsable que se abre en tu app de mapas o navegador.

**Dónde encontrar los ajustes de la cámara:** Ajustes → Gestión → Fotografía.

---

## Cómo buscar y eliminar archivos duplicados {#how-to-find-and-delete-duplicate-files}

**Pasos:**

1. Abre una carpeta en Explorar y luego abre el **menú desplegable** <img src="icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> de la barra de herramientas.
2. Toca **Buscar duplicados** para revisar las coincidencias tú mismo, o **Buscar y eliminar duplicados** para quitarlos de inmediato.
3. En **Buscar duplicados**, la app preselecciona todas las copias excepto la más antigua de cada grupo - ajusta la selección y luego toca **Eliminar selección** y confirma.
4. **Buscar y eliminar duplicados** elimina esas mismas copias preseleccionadas justo después del escaneo, sin paso de confirmación - usa primero **Buscar duplicados** si quieres comprobarlo dos veces antes de que se elimine nada.

**Limpiar por tamaño en su lugar:**

1. En el mismo menú desplegable, toca **Eliminar por tamaño..**
2. Elige **Menor que** o **Mayor que**, define un tamaño y toca **Analizar**.
3. Revisa el recuento y el espacio que se liberaría, y luego toca **Eliminar archivos** para confirmar.

**Notas:**

- El escaneo compara archivos por contenido en tres pasadas - tamaño, luego un hash rápido, luego una comprobación SHA-256 completa - así que los duplicados renombrados también se detectan.
- Eliminar por tamaño muestra cuánto espacio liberarás antes de eliminar nada; las fuentes de red y de nube se saltan la papelera, así que esa eliminación es inmediata y permanente.

---

## Cómo ver tus estadísticas de uso {#how-to-view-your-usage-statistics}

**Pasos:**

1. Ve a **Ajustes → General → Recogida de estadísticas** y actívala.
2. Toca **Estadísticas** (aparece justo debajo del interruptor) para abrir el panel.

**Qué verás:**

- Tarjetas de resumen de archivos ordenados, espacio liberado y tiempo dedicado a reproducir contenido multimedia.
- Un desglose por tipo (imágenes, vídeos, audio, documentos..).
- Secciones plegables con más detalle: operaciones, captura, visualización, edición, fuentes y uso general.

**Compartir un informe:**

- **Enviar al autor** abre tu app de correo con un resumen adjunto, dirigido al desarrollador.
- **Exportar** comparte el mismo resumen a través del panel de compartir habitual de Android, así puedes guardarlo o enviarlo a donde quieras.

**Nota:** todo se queda en tu dispositivo hasta que decidas enviarlo o exportarlo - consulta las FAQ para los detalles de privacidad.

---

## Cómo usar una tarjeta SD o una unidad conectada {#how-to-use-an-sd-card-or-connected-drive}

Una tarjeta de memoria o una unidad USB que el teléfono ha montado contiene recursos exactamente igual que el almacenamiento integrado.

**Pasos:**

1. Abre **Añadir recurso** y empieza a añadir una carpeta local. La sección **Medios extraíbles** solo aparece mientras algo está conectado, y lista cada volumen con su nombre y espacio libre.
2. Toca el volumen. Si la app no puede acceder a él por ruta, explica por qué y abre el selector de carpetas del sistema - elige ahí el mismo volumen y concede acceso a la carpeta que quieras.
3. El recurso se une a la lista con un icono de medio extraíble, así un recurso de tarjeta se reconoce de un vistazo.

**Mover y copiar:** las carpetas completas viajan a una tarjeta y de vuelta con toda su estructura de subcarpetas, igual que entre el dispositivo y un recurso de red.

**No hay suficiente espacio:** una copia o un movimiento que no cabe se rechaza antes de empezar, y el mensaje indica el medio y cuánto espacio falta - libera espacio ahí o elige otro destino.

**Cuando se expulsa el medio:** sus recursos se marcan como no disponibles en lugar de eliminarse. Vuelve a conectar la tarjeta y funcionarán sin tener que configurarlos de nuevo.

**En Android 6:** el sistema no informa a las apps de los volúmenes montados, así que la sección de extraíbles se queda vacía en esos dispositivos.

---

## Cómo reconectar una carpeta añadida por ruta directa {#how-to-reconnect-a-folder-added-by-direct-path}

Una carpeta que añadiste escribiendo o navegando su ruta puede mostrar tus fotos, vídeos y música, pero ninguno de tus documentos. Eso no es un escaneo que se los haya saltado: una compilación de tienda lee archivos de texto, PDF y libros electrónicos solo a través de una carpeta que conectaste con el selector del sistema. Reconectar apunta el mismo recurso a la misma carpeta a través de ese selector, y los documentos aparecen.

**Pasos:**

1. Toca el menú de tres puntos en la tarjeta de la carpeta en la lista principal.
2. Elige **Reconectar recurso**. Se abre el selector de carpetas del sistema, ya dentro de esa carpeta donde el teléfono lo permite.
3. Elige la misma carpeta y confirma.
4. Si eliges una carpeta distinta, la app nombra ambas carpetas y pregunta antes de cambiar nada.

**Qué se conserva:** el nombre, la posición en tu lista, el PIN, el icono, el papel en Quick Sort, tus favoritos y tus programaciones sobreviven todos - el recurso se redirecciona, no se crea de nuevo.

**Dónde no lo verás:** en compilaciones que todavía leen las carpetas por ruta directamente, y en Android 10 y anteriores, la entrada está ausente porque ahí no falta nada.

---

## Cómo elegir dónde se guardan las capturas y las descargas {#how-to-choose-where-captures-and-downloads-are-saved}

Las fotos de la cámara integrada, las capturas de pantalla, las instantáneas y los archivos descargados automáticamente escriben cada uno en una carpeta que eliges, y esa carpeta no tiene por qué ser uno de tus recursos.

**Pasos:**

1. Abre el ajuste de lo que estás guardando - captura, captura de pantalla, instantánea o descarga automática.
2. Elige la carpeta de destino. Se abre el explorador de carpetas del sistema, así puedes apuntar a cualquier carpeta local, incluida una que nunca hayas añadido a la app.
3. Esa carpeta se convierte en el destino de escritura solo para ese ajuste. Se queda fuera de tu lista general de recursos, así que elegir una carpeta provisional para las capturas de pantalla no llena la pantalla principal.

**Consejos:**

- Cada uno de los cuatro ajustes tiene su propio destino - las capturas de pantalla y las fotos de la cámara pueden acabar en lugares completamente distintos.
- Apuntar varios de ellos a una sola carpeta está bien si prefieres tenerlo todo en un mismo sitio.

### Nombres de los archivos de captura

Las capturas nuevas usan el patrón `prefix_yyMMdd_HHmmss`. Los prefijos estables son `photo`, `screenshot`, `audio`, `video`, `screen_video` y `video_frame`, así que el nombre del archivo identifica su origen. Si ya existe un nombre en la carpeta de destino, la app añade el sufijo ` (2)` antes de la extensión. Un nombre de archivo introducido a mano en la cámara sigue siendo una anulación y no se cambia.

---

## Cómo recibir archivos compartidos desde otra app {#how-to-receive-files-shared-from-another-app}

El panel de compartir de cualquier app puede enviar archivos a FastMediaSorter, que luego los copia donde tú quieras.

**Pasos:**

1. En la otra app, comparte el archivo o los archivos y elige **FastMediaSorter**.
2. Elige la carpeta de destino en la pantalla de recepción.
3. Inicia la copia.

**No tienes que esperarla.** La copia sigue en marcha después de cerrar la pantalla de recepción, con una notificación que muestra el progreso mientras trabaja y una notificación de resultado cuando termina. Sal de la app, bloquea el dispositivo, sigue con lo tuyo - la transferencia no depende de que esa pantalla se quede abierta.

Disponible en las compilaciones Standard, Lite, Photos y Legacy.

---

## Cómo usar los programas integrados {#how-to-use-the-built-in-programs}

**Disponible en:** todas las variantes - el menú de programas y el panel están en todas las compilaciones, pero cada programa sigue su propia capacidad: el Monitor de red necesita Standard o noLegal, el compañero Wear necesita Standard o noLegal, el minijuego está ausente en XR y noLegal, y Espejo necesita una cámara frontal con la captura de cámara activada en Ajustes. La calculadora, la linterna frontal y la Información del sistema están en todas las compilaciones.

Además de explorar y reproducir archivos, la app incluye un conjunto de pequeños programas integrados - una calculadora, una lámpara de pantalla, un monitor de red, una grabadora de voz y más. Están desactivados por defecto: cada uno se activa con su propio ajuste, y la mayoría de los interruptores dedicados están juntos en **Ajustes → Gestión**.

**Vía rápida**

1. Ve a **Ajustes → Gestión** y activa lo que quieras - por ejemplo **Calculadora**, **Linterna frontal**, **Monitor de red**, **Minijuego** o **Información del sistema**.
2. Abre el menú desplegable de la ventana principal. Los programas que activaste aparecen ahí.
3. Toca uno para ejecutarlo.

**Dónde aparece un programa**

Un programa puede ofrecerse hasta en cuatro superficies, y cada superficie toma su contenido y su orden de la misma lista única, así que nunca se desincronizan:

- **Menú de programas** - el desplegable de la ventana principal, y el panel de programas que lo repite.
- **Panel de inicio rápido de apps** - la superposición de acceso rápido.
- **Widget de pantalla de inicio** - solo para los programas que tienen uno; fíjalo desde el propio selector de widgets de la app.
- **Escritorio del lanzador** - cuando usas la app como tu pantalla de inicio, activar un programa añade su celda automáticamente.

**Qué hay en el conjunto**

En el orden en que aparecen:

- **Captura rápida** - toma una foto directamente en la app.
- **Grabación de voz** - graba una nota de voz.
- **Calculadora** - una calculadora científica con historial y ranuras de memoria.
- **Monitor de red** - lecturas en vivo del enlace activo, Wi-Fi, datos móviles, Bluetooth y ubicación, además de un traceroute que recorre la ruta hasta un host salto a salto y sigue contando cuando un salto no responde nada.
- **OCR y traducción de fotos** - fotografía texto y tradúcelo.
- **Grabación de vídeo de pantalla** - graba la pantalla.
- **Descargar por enlace** - obtén un archivo a partir de un enlace pegado.
- **Minijuego** - el pequeño juego integrado en la app.
- **Información del sistema** - un informe del dispositivo accesible sin abrir Ajustes.
- **Compañero Wear** - la pantalla del reloj, en las compilaciones que incluyen el puente con el reloj.
- **Linterna frontal** - convierte la propia pantalla en una lámpara: se abre en blanco con el brillo máximo de la ventana, un deslizamiento vertical cambia el brillo, un pequeño botón arriba a la izquierda elige y recuerda otro color, y un solo toque la cierra. Solo se toca el brillo de la ventana, así que el ajuste de tu dispositivo queda sin cambios después.
- **Linterna de agua** - la misma luz para manos mojadas. Enciende el flash de la cámara y la pantalla juntos, y luego bloquea la pantalla: la hora y un breve recordatorio son todo lo que ves, y tocar el cristal no hace absolutamente nada - un botón de volumen la cierra. Las barras del sistema también desaparecen, así una mano mojada no se encuentra con un botón de navegación; un deslizamiento deliberado aún puede traerlas de vuelta. Pensada para la lluvia y para la ducha - los dos lugares donde el cristal reacciona al agua en lugar de a ti. Salir mediante un gesto del sistema también apaga la luz, así que nunca se queda encendida en un bolsillo. En el reloj no hay flash, así que la pantalla sola es la luz. No sustituye al modo de bloqueo por agua integrado en un reloj o un teléfono; ninguna app puede activar ese.
- **Espejo** - convierte el teléfono en un espejo iluminado: la cámara frontal llena la pantalla dentro de un campo blanco brillante que ilumina tu cara, y la imagen se invierte tal como la muestra un espejo real, con un botón en la esquina para apagar la luz sin salir de la pantalla. Los preajustes de zoom - x1, x2, x3, x5 - están abajo a la izquierda y se abren en x3. Un botón de foto y un botón de vídeo guardan directamente en la misma carpeta que usa Captura, el vídeo con sonido. El zoom, el volteo y el estado de la retroiluminación se recuerdan entre usos. Solo sube el brillo de la ventana, nunca el ajuste propio de tu dispositivo, así que el teléfono vuelve a la normalidad en cuanto sales de la pantalla. Activado por defecto en cualquier dispositivo con cámara frontal, siempre que la propia captura de cámara no esté desactivada en Ajustes.

El panel y el lanzador incluyen además accesos directos de cámara: tomar una foto y enviarla, tomar una foto y editarla, tomar una foto y traducirla, iniciar una grabación de vídeo, y abrir la carpeta de la cámara.

**Cuándo ayuda**

- Quieres una calculadora o una linterna sin salir de la app, o sin buscar una app aparte en un teléfono lleno.
- Usas la app como tu pantalla de inicio y quieres una celda de un toque para una herramienta que usas a menudo.

**Evita esto**

- No esperes que la linterna de agua sobreviva a un deslizamiento a inicio - un gesto de navegación del sistema aún la cierra, y la luz se apaga con ella.
- No esperes cada programa en cada compilación - la lista anterior es el conjunto completo, y una compilación sin la capacidad subyacente simplemente no muestra esa entrada.

---

## Cómo pedirle a tu asistente que busque y abra contenido multimedia {#how-to-ask-your-assistant-to-find-and-open-media}

**Disponible en:** todas las compilaciones, en Android 16 y posteriores. Las versiones anteriores de Android simplemente no ofrecen la función, y no hace falta activar nada en la app.

En Android 16+ la app registra en el sistema un conjunto de acciones de asistente - AppFunctions, en la terminología propia de Android. El asistente de tu dispositivo puede entonces invocarlas por nombre, así que puedes pedir en voz alta una foto, un vídeo o una carpeta de ordenador en lugar de abrir la app y buscarlo tú mismo.

**Qué puedes pedir**

- **Buscar en tu contenido multimedia** - el asistente pasa tus palabras a la búsqueda de la app y muestra lo que coincidió.
- **Abrir un archivo multimedia** - una foto, un vídeo o una pista se abre directamente en el visor o reproductor de la app.
- **Abrir una carpeta de ordenador** - una de tus carpetas de red o de la nube se abre en la pantalla del explorador.

**Vía rápida**

1. Asegúrate de que el dispositivo ejecuta Android 16 o posterior y tiene un asistente del sistema configurado.
2. Pídele al asistente el contenido multimedia que quieras, nombrando FastMediaSorter si el dispositivo aloja varias apps multimedia.
3. La app se abre en el resultado - la lista de búsqueda, el archivo o la carpeta que pediste.

**Cuándo ayuda**

- Tienes las manos ocupadas - cocinando, conduciendo, sujetando a un niño - y navegar por carpetas no es una opción.
- Recuerdas cómo se llama un archivo, pero no dónde lo guardaste.

**Evita esto**

- No lo esperes por debajo de Android 16: las acciones de asistente forman parte del sistema más reciente, así que en un teléfono más antiguo el asistente no las verá.
- No esperes que el asistente llegue a una carpeta protegida con PIN - el bloqueo se sigue aplicando, y la carpeta pedirá su PIN como de costumbre.

---

## ¿Necesitas más ayuda?

- 📖 **Inicio rápido:** [QUICK_START-es.md](QUICK_START-es.md)
- ❓ **FAQ:** [FAQ-es.md](FAQ-es.md)
- 🔧 **Solución de problemas:** [TROUBLESHOOTING-es.md](TROUBLESHOOTING-es.md)

</div>
