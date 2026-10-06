---
layout: default
title: "❓ Preguntas frecuentes (FAQ)"
permalink: /docs/FAQ-es.html
lang: es
---

<div lang="es" markdown="1">

<div lang="es" dir="ltr" markdown="1">

# ❓ Preguntas frecuentes (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="es" %}

---

## Preguntas generales

### ¿Qué es FastMediaSorter?
FastMediaSorter combina reproducción y gestión de archivos locales, de red y nube. El lanzador, las emisiones y el reloj dependen de la edición. Se requieren permisos para archivos y una elección explícita en Android para sustituir la pantalla de inicio.

### ¿Es gratis?
¡Sí! FastMediaSorter v2 es completamente gratuito y de código abierto.

### ¿Qué versión de Android necesito?
Abajo figuran los mínimos de instalación del código actual. El uso inmersivo VR/XR exige además visor/runtime compatible; **noLegal no está limitada a visores**. Una variante en el código no garantiza un APK publicado.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK (EN)](TECHNICAL_REQUIREMENTS.html)

### ¿Necesita Internet?
Los archivos locales no necesitan internet. SMB/SFTP/FTP en LAN necesitan una red accesible, no internet público. La nube y las emisiones de internet sí necesitan internet; depende de la edición.

### ¿La app tiene widgets?
¡Sí! FastMediaSorter v2 incluye una variedad de widgets de pantalla de inicio - encuéntralos manteniendo pulsada la pantalla de inicio → Widgets → FastMediaSorter. Incluyen accesos directos a recursos, lanzadores de presentaciones y más.

### ¿Puede la app sustituir mi pantalla de inicio?
En **Standard/noLegal**, elija **Ajustes → General → Ventana de inicio principal → Pantalla de inicio del dispositivo** y FastMediaSorter como aplicación de inicio de Android. El **Escritorio como ventana principal** no sustituye al lanzador del sistema.

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### ¿Cómo dejo de usar la app como pantalla de inicio?
Use **Salir del modo lanzador** en Inicio, otra ventana inicial o elija otra aplicación de inicio predeterminada en Android. Se conserva el diseño; la ruta del ajuste depende del dispositivo.

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### ¿Por qué mi tablet volvió a su antigua pantalla de inicio tras reiniciarse?
Compruebe la aplicación de inicio predeterminada y la ventana inicial. Elija **Siempre** si aparece. Firmware, actualizaciones o ajustes restablecidos pueden cambiar la selección; reiniciar no prueba la causa. Indique modelo y Android al informar.

### ¿Puedo poner mis propias carpetas y listas de reproducción en el escritorio?
Sí, para eso está el escritorio. Mantén pulsado un cuadro vacío y elige **Añadir un elemento..**, luego elige lo que quieras: una de tus carpetas, una emisión de radio, una app, una persona o un gadget como el reloj o el tiempo. La nueva celda aparece en el cuadro que pulsaste, y para una carpeta también eliges si se abre en modo explorar, presentación o reproducción. Para reorganizar las cosas después, elige **Editar el escritorio** desde el mismo menú de pulsación larga. Consulta [HOW_TO](HOW_TO-es.html#how-to-use-the-app-as-your-home-screen) para el recorrido completo.

---

## Operaciones con archivos

### ¿Adónde van los archivos eliminados?
Con papelera activada, las rutas locales ordinarias compatibles usan `.trash/`. El **borrado permanente**, `content://`, SMB/SFTP/FTP/nube y rutas protegidas `/Android/media/` no usan esta política. Vaciar la papelera es irreversible; no todo borrado se puede restaurar.

### ¿Puedo deshacer una eliminación/movimiento?
Use **Deshacer** inmediatamente, solo si se ofrece. Depende de la operación, pantalla y rutas. El borrado permanente o de red/document-tree no se revierte mediante papelera local; las transferencias de archivos de red/nube no siempre son reversibles. No sustituye una copia de seguridad.

### ¿Cuál es la diferencia entre Copiar y Mover?
- **Copiar:** crea un duplicado, el original se queda donde estaba
- **Mover:** reubica el archivo, lo elimina de la ubicación original

### ¿Qué es el modo Todos los archivos?
Todos los archivos elimina filtros multimedia **en recursos accesibles**, sin eludir permisos Android. Otros formatos se gestionan o abren con aplicaciones compatibles; mostrar APK/EXE/archivos comprimidos no implica ejecutarlos o extraerlos.

### ¿Cómo encuentro y elimino archivos duplicados?
Abre una carpeta, toca el menú de opciones y elige **Buscar duplicados** para revisar tú mismo las coincidencias, o **Buscar y eliminar duplicados** para eliminarlos de inmediato. También hay **Eliminar por tamaño..** para un barrido de limpieza rápido basado solo en el tamaño del archivo. La opción automática omite la confirmación, así que usa primero **Buscar duplicados** si quieres comprobarlo todo antes de eliminar nada. La comparación se basa en el contenido - tamaño, luego un hash rápido, luego una comprobación SHA-256 completa - así que las copias renombradas también se encuentran.

---

## Red y nube

### ¿Cómo me conecto a mi NAS doméstico (unidad de red)?
1. Toca **"+"** → **Red** → **SMB / Unidad de red**
2. **Opción A - Automática:** toca **"Escanear red"** para descubrir automáticamente los dispositivos disponibles en tu red
3. **Opción B - Manual:** introduce la dirección del servidor: `\\192.168.1.100\share` o `smb://192.168.1.100/share`
4. Introduce el usuario y la contraseña
5. Toca "Conectar"

Use una cuenta autorizada NAS/Windows y una carpeta SMB accesible. Permita TCP **445** solo en una red privada fiable y la subred necesaria; **no desactive el firewall ni exponga SMB a internet**. Revise permisos, dirección, rutas VPN y aislamiento de invitados. Una VPN privada permite acceso remoto incluso por datos móviles.

[Guía de configuración SMB](howto/scenario-smb-setup-es.html)

### ¿Cómo me conecto a Google Drive?
1. Toca **"+"** → **Nube** → **Google Drive**
2. Toca "Iniciar sesión con Google"
3. Concede los permisos cuando se te pidan
4. Aparecerán tus carpetas de Drive

Los archivos se abren bajo demanda, pero visualización, miniaturas o reproducción pueden descargar datos a la caché. No es sincronización automática de todo Drive.

### ¿Cómo me conecto a OneDrive?
1. Toca **"+"** → **Nube** → **OneDrive**
2. Toca "Iniciar sesión con Microsoft"
3. Concede los permisos cuando se te pidan
4. Aparecerán tus carpetas de OneDrive

### ¿Cómo me conecto a Dropbox?
1. Toca **"+"** → **Nube** → **Dropbox**
2. Toca "Iniciar sesión con Dropbox"
3. Concede los permisos cuando se te pidan
4. Aparecerán tus carpetas de Dropbox

### ¿Puedo usar SFTP o FTP?
¡Sí! Selecciona **SFTP** o **FTP** al añadir una carpeta:
- **SFTP:** seguro, requiere un servidor SSH (puerto 22)
- **FTP:** menos seguro, protocolo más antiguo (puerto 21)

### ¿Puedo compartir carpetas del PC con la app?
**Sí** - Fast Media Sorter for Windows publica las carpetas del PC que elijas mediante SFTP y muestra un código QR / configuración `.fmscfg`. En el teléfono, usa **Importar desde companion** o **Escanear código QR** en la pantalla Añadir recurso. Consulta la guía del lado del PC: [Cómo publicar carpetas del PC en Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### ¿Por qué no cargan las miniaturas de los archivos de red?
Las miniaturas de red se generan **bajo demanda** para ahorrar ancho de banda. Desplázate despacio o espera unos segundos a que aparezcan.

Si las miniaturas nunca llegan a cargar:
- Comprueba que la conexión esté activa: toca el recurso → si la carpeta se abre, la conexión funciona bien
- Edita el recurso → asegúrate de que **"Cargar miniaturas"** esté activado
- Para conexiones muy lentas: desactiva las miniaturas por completo para evitar tiempos de espera agotados (Editar recurso → desactivar miniaturas)

### La conexión se corta / los archivos fallan al abrirse a mitad de reproducción
Revise Wi-Fi, servidor, credenciales y rutas VPN. Pruebe la velocidad del recurso y reduzca miniaturas. El ancho de banda depende del bitrate y códec, no solo de resolución; **10 Mbps no es un requisito universal de 1080p**.

---

## Clasificación rápida y destinos

### ¿Qué son las carpetas de "clasificación rápida"?
Las carpetas de clasificación rápida son carpetas de destino preconfiguradas para clasificar archivos rápidamente. Puedes asignar hasta 10 carpetas con botones numerados.

### ¿Cómo configuro la clasificación rápida?
**Método 1:** Ajustes → Gestión → Destinos de clasificación rápida, luego toca **"Añadir a clasificación rápida"**  
**Método 2:** edita cualquier carpeta → activa "Marcar para clasificación rápida"

### ¿Cómo uso la clasificación rápida mientras veo archivos?
Abra un archivo y elija destino en el panel de comandos. Compruebe **Copiar** o **Mover** antes de confirmar. Las zonas táctiles dependen del medio/modo; abajo a la izquierda no siempre copia.

### ¿Puedo usar las teclas numéricas en lugar de tocar?
Sí - conecta un teclado físico, un mando o un mando de TV y tus botones de clasificación rápida se numeran automáticamente (0-9). Pulsa el dígito correspondiente para copiar o mover el archivo a ese destino al instante, igual que al tocar el botón.

### Los botones de clasificación rápida no aparecen
Asegúrate de haber añadido al menos una carpeta de destino primero: Ajustes → Gestión → Destinos de clasificación rápida, luego **"Añadir a clasificación rápida"**. Los botones solo aparecen cuando hay al menos un destino configurado.

### Envié un archivo a la carpeta equivocada por accidente
Use **Deshacer** si aparece. Si no, compruebe origen/destino y devuelva el archivo manualmente. Copiar conserva el original; no borre ninguna copia antes de verificar.

---

## Zonas táctiles

### ¿Qué son las "Zonas táctiles"?
El mapa depende del medio/modo: imágenes pueden usar 3×3; audio/vídeo reservan controles y el centro pausa/reanuda. Con panel de comandos hay tres columnas; documentos usan deslizamientos sin zonas de toque. La superposición muestra el mapa activo.

### ¿Cómo veo las zonas táctiles?
Ajustes → Reproductor → **"Mostrar siempre la superposición de zonas táctiles"**

### ¿Puedo desactivar las zonas táctiles?
Puede desactivar la cuadrícula de nueve zonas y usar el panel. **No desactiva todos los gestos**: quedan navegación/controles en tres columnas y deslizamientos propios de documentos.

---

## Captura de pantalla y voz

### ¿Qué es la franja de gestos del borde izquierdo?
Es un menú de captura rápida que abres con un deslizamiento diagonal desde el borde izquierdo de la pantalla. Actívalo en **Ajustes → Gestión → Gestos de borde de pantalla → Superposición de gestos**. Desde el menú puedes hacer una captura de pantalla, tomar una foto, recortar y compartir la imagen actual, abrir un acceso directo a una app o panel, o iniciar una grabación de pantalla, vídeo o voz - todo sin dejar de ver lo que tienes delante. Disponible en Standard y XR/noLegal.

### ¿Cómo grabo una nota de voz rápida?
Tres formas: el elemento **Grabación de voz** en el menú de opciones, el widget de pantalla de inicio **Grabadora rápida**, o la acción **Iniciar grabación de audio** del gesto de borde. Sea cual sea la forma en que la inicies, un control flotante de **Detener** permanece en pantalla - incluso sobre otra app - hasta que lo toques para guardarla.

---

## Entrada y controles

### ¿Admite teclados físicos y mandos?
Teclado, ratón y mando dependen de pantalla/dispositivo. **F1** muestra asignaciones en pantallas compatibles, no garantiza cada tecla en todos los diálogos.

### ¿Cómo reasigno los controles / cambio las asignaciones de teclas?
En **Ajustes → Gestión → Controles y teclas**, cambie acciones compatibles. **Reset** restaura valores; los conflictos se destacan. Consulte la lista actual, no un número fijo de 70.

### ¿Cómo descargo un archivo multimedia desde una URL?
Comparta una URL `http(s)` compatible en Android. Un archivo descargable no es una página, vídeo con acceso protegido o DRM. La descarga y destinos escribibles dependen de edición, URL y permisos.

---

## Rendimiento y almacenamiento

### ¿Cómo encuentro un archivo concreto por su nombre?
Usa el panel de **Filtro** en Explorar: toca el icono de filtro en la barra de herramientas, escribe cualquier parte del nombre del archivo en el campo de nombre - la lista se actualiza al instante. No hace falta una barra de búsqueda aparte; el filtro cubre por completo este caso.

### ¿Por qué la app va lenta con más de 5000 archivos?
Carpetas grandes requieren listado, metadatos y miniaturas. Acote recursos, filtre y reduzca miniaturas. Ordenar por fecha **no garantiza** evitar escanear toda la carpeta; depende de fuente y formatos.

### La app se bloquea o se congela
Reabra la app y compruebe espacio, permisos y conexión. Limpiar caché ayuda con miniaturas antiguas, no arregla todos los fallos. Informe versión/edición, Android, recurso y pasos; quite credenciales/rutas privadas de los registros.

### ¿Cuánto almacenamiento usa la caché de miniaturas?
**Por defecto:** 2 GB (configurable en Ajustes)

### ¿Cómo borro la caché?
Ajustes → General → **"Borrar caché"**

---

## Favoritos

### ¿Cómo marco archivos como favoritos?
Toca el **icono de estrella** mientras ves un archivo.

### ¿Dónde puedo ver todos mis favoritos?
Menú principal → pestaña **"Favoritos"**

---

## Seguridad y privacidad

### ¿Puedo proteger carpetas con contraseña?
El **PIN del recurso** restringe su acceso en la app, pero **no cifra archivos** ni bloquea otras aplicaciones o usuarios autorizados del servidor. Use cifrado del dispositivo/almacenamiento para protección externa.

### ¿Se recogen mis datos?
La app no envía estadísticas al autor automáticamente. Son locales hasta exportarlas/enviarlas. Nube, emisiones y tiempo contactan proveedores elegidos con las solicitudes necesarias. Consulte la política de privacidad.

[Política de privacidad (EN)](PRIVACY_POLICY.html)

### ¿Los accesos directos de contactos en el escritorio del lanzador necesitan acceso a mis contactos?
**No.** Fijar una persona en el escritorio del lanzador no pide ningún permiso de contactos en absoluto. Eliges a la persona en el propio selector de contactos de Android, la app lee ese registro una sola vez y lo guarda como una instantánea en la celda - nunca llega a examinar tu agenda. Al llamar se usa el número que elegiste en el selector, así que la celda marca exactamente ese número.

Existe un grupo de permisos opcional de **Contactos**, solicitable bajo demanda, en **Ajustes → General → Permisos y acceso**. Denegarlo no cambia nada del comportamiento anterior - los accesos directos siguen funcionando igual, sin necesidad de permiso.

### ¿La app guarda la ubicación GPS en mis fotos?
Solo si lo activas. En **Ajustes → Gestión → Fotografía**, activa la captura de fotos y luego activa **Geoetiquetar fotos** justo debajo - la app pide el permiso de ubicación en ese momento, no al disparar. La pantalla de información de archivo de una foto geoetiquetada muestra la fecha de captura y el punto GPS de los datos EXIF de la foto como un enlace pulsable que abre tu app de mapas o el navegador.

### ¿Puedo ver cómo uso la app?
Sí - es opcional y está desactivado por defecto: activa **Recogida de estadísticas** en **Ajustes → General** para abrir un panel local de uso: archivos ordenados, espacio liberado, tiempo de reproducción y más, desglosado por tipo de contenido. Nada se envía automáticamente; **Enviar al autor** o **Exportar** solo comparten un resumen si tú eliges hacerlo.

---

## Traducción automática

### ¿Cómo funciona la traducción?
Dos pasos, ambos en tu dispositivo:
- **Tesseract** lee el texto de la imagen, en todos los idiomas admitidos (inglés, ruso, ucraniano, búlgaro, bielorruso).
- **Google ML Kit** traduce después lo que se ha leído.

Depende de edición/dispositivo. Traducción ML Kit solo en teléfonos, tabletas, Chromebook y equipos de escritorio, no TV, coches, relojes ni XR. El OCR separado exige API 26+, al menos 3 GB RAM y no estar marcado low-RAM.

### ¿Qué hace el idioma de origen "Auto"?
"Auto" lee el texto con el modelo de inglés y después deduce el idioma de lo leído para la traducción. Para texto en cirílico, elige el idioma de origen explícitamente (por ejemplo **Ruso** o **Ucraniano**) - de lo contrario, las letras se leen como sus equivalentes visuales en latín.

### ¿Funciona sin conexión?
**Sí.** Solo necesitas Internet una vez, para descargar el modelo de texto de tu idioma de origen y el modelo de traducción para tu par de idiomas.

### ¿Por qué la traducción a veces es más lenta?
El primer uso de un idioma carga su modelo de texto, y las imágenes grandes o muy detalladas tardan más en leerse. Las siguientes ejecuciones con el mismo idioma empiezan más rápido.

### ¿Qué es el modo de traducción estilo lente?
La superposición pone bloques traducidos sobre la imagen; el modo estándar muestra texto separado. En dispositivos compatibles: **Ajustes → Multimedia → Traducción, digitalización (OCR) → Resultado de la traducción en bloques**.

---

## Música de fondo en la presentación

### ¿Cómo añado música de fondo a las presentaciones?
1. Añade una carpeta que contenga archivos de audio como recurso
2. Ve a **Ajustes → Multimedia → Imágenes**
3. Activa **"Reproducir música durante la presentación"**
4. Selecciona tu recurso de música en el menú desplegable
5. Inicia cualquier presentación - ¡la música se reproducirá automáticamente!

### ¿Puedo usar música de unidades de red o almacenamiento en la nube?
¡Sí! La app gestiona automáticamente los archivos de red descargándolos a la caché antes de reproducirlos. Funciona con SMB, SFTP, FTP, Google Drive, OneDrive y Dropbox.

### ¿Cómo salto de pista durante la presentación?
Toca el **nombre de la pista** que se muestra durante la presentación para saltar a otra pista aleatoria de tu recurso de música.

### ¿Funciona con todas las variantes?
Standard, noLegal, Legacy, VR, XR y FOSS permiten audio persistente en segundo plano. Lite reproduce audio local sin servicio persistente; Photos no admite audio. Red/nube dependen además de la edición.

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Emisiones de Internet

### ¿FastMediaSorter reproduce radio por Internet?
**Emisiones** admite radio HTTP(S)/ICY, HLS/DASH y RTSP según fuente/códec. Disponible en **Standard, noLegal, Legacy, VR y XR**, no **Lite, Photos ni FOSS**. Pegar una URL no elimina incompatibilidades ni DRM.

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### ¿Cómo abro la pantalla de Streams?
Toca **Streams** en el menú desplegable de la ventana principal (visible cuando Streams está activado). También puedes acceder mediante **Ajustes > Multimedia > Streams**, donde está el interruptor principal.

### ¿Cómo añado una emisora de radio?
En la pantalla de Streams, toca **⋮** al final de la barra de herramientas, elige **Añadir emisión** y pega la URL de la emisora. Toca Guardar. La emisora aparece en la lista inmediatamente.

### ¿Puedo importar una lista de reproducción?
Sí - toca **⋮ > Importar desde URL** e introduce una dirección `.m3u` remota. El mismo menú tiene **Actualizar catálogo de FastMediaSorter** para la lista curada (con chips de tema e idioma), también disponible desde **Ajustes > Extensiones** o desde la pantalla de bienvenida inicial.

### Una emisión no se reproduce - ¿qué hago?
Si una emisión falla, aparece un diálogo con las opciones **Reintentar**, **Eliminar** y **Cancelar**. Las redirecciones 301 entre protocolos se gestionan automáticamente. Si el host está caído o va muy lento, la importación del catálogo agota el tiempo de espera rápidamente en lugar de quedarse colgada.

### ¿La radio sigue sonando si salgo de la pantalla de Streams?
Con audio persistente compatible y activado, al salir rige Parar / Continuar / Preguntar. Sin él, el audio se detiene al salir del primer plano. Revise Ajustes → Reproductor.

### ¿Puedo ver miniaturas en directo de las emisiones?
Cambia el interruptor de la barra de herramientas de Streams a la vista **Cuadrícula** - cada canal aparece como una ficha con su último fotograma capturado, para que puedas ver de un vistazo qué se está emitiendo. La ficha sigue visible incluso después de cerrar y volver a abrir la app, y se actualiza con una nueva captura en cuanto la emisión vuelve a estar en directo.

### ¿Puedo enviar una emisión a mi TV con Cast?
Sí, para emisiones de vídeo - toca **Cast** en el reproductor y elige un Chromecast en la misma red Wi-Fi. Las emisiones RTSP no se pueden enviar con Cast; el botón solo aparece para los formatos que admite el receptor Chromecast.

---

## Wear OS

### ¿FastMediaSorter funciona en relojes inteligentes Wear OS?
La **app Wear OS** separada exige API 28 mínimo; instale el APK en el reloj. La conexión desde teléfono requiere **Standard/noLegal** e identificadores/firmas compatibles. La **esfera WFF v4** separada exige Wear OS 6 / API 36.

[Matriz de funciones (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### ¿Qué puedo hacer en el reloj?
El código Wear actual incluye medios locales/de red, transferencias con teléfono, emisiones, grabación y herramientas. Sincroniza ajustes/recursos compatibles, no todos los ajustes. No tiene cliente de nube propio. **La versión publicada puede ofrecer menos que el código**; compruebe la descripción de descarga/versión.

---

## Libros electrónicos EPUB

### ¿Cómo activo la compatibilidad con EPUB?
Ajustes → Multimedia → **Documentos** → **"Admitir libros electrónicos EPUB"**

**Nota:** reinicia la app después de activarlo para que los cambios surtan efecto.

### ¿Cómo leo un libro EPUB?
1. Añade una carpeta que contenga archivos .epub como recurso
2. Abre la carpeta - verás los archivos EPUB con la insignia "E"
3. Toca cualquier archivo EPUB para abrirlo en el lector

### ¿Puedo navegar entre capítulos?
¡Sí! Usa:
- Los **botones Anterior/Siguiente** en la parte inferior
- **Desliza a izquierda/derecha** para cambiar de capítulo
- El **botón de tabla de contenidos** (icono 📋) para abrir el índice

### ¿Puedo ajustar el tamaño de la fuente?
¡Sí! Mientras lees, usa los **botones -A/+A** de la parte inferior para reducir/aumentar el tamaño de la fuente (rango de 6-144px). Los ajustes se guardan por libro.

### ¿Puedo buscar texto en un EPUB?
¡Sí! Toca el **botón de búsqueda** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> para abrir el panel de búsqueda. Escribe tu consulta y navega entre las coincidencias con los botones Anterior/Siguiente.

### ¿Funciona con archivos de red/nube?
¡Sí! Los archivos EPUB se descargan automáticamente a la caché al abrirlos desde almacenamiento SMB/SFTP/FTP/nube.

### ¿Recuerda mi posición de lectura?
¡Sí! La app guarda el último capítulo que estabas leyendo. Al reabrir el libro, continúa desde donde lo dejaste.

### ¿Qué hay del tema claro/oscuro?
El lector de EPUB se adapta automáticamente al tema de tu app (Ajustes → General → Tema de color).

---

## Operaciones programadas

### ¿Qué son las Operaciones programadas?
Las reglas ejecutan Copiar, Mover o Borrar compatibles en recursos accesibles. Permisos, credenciales y disponibilidad siguen siendo necesarios. Pruebe primero con **Copiar**; borrar por horario no es automáticamente reversible.

### ¿Dónde configuro las Operaciones programadas?
Ajustes → **Gestión** → **Operaciones programadas por horario**. Toca **"+"** para añadir una regla nueva.

### ¿Se ejecutará si mi app está cerrada?
WorkManager puede ejecutar tras salir, pero no garantiza ejecución después de **Forzar detención** en Android, con dispositivo apagado o sin permisos/condiciones. Reabra y revise regla/registro tras detenerla.

### ¿Por qué una operación programada no se ejecutó a la hora exacta?
WorkManager no es una alarma exacta. Batería, red y dispositivo pueden retrasar más de unos minutos. Intervalo mínimo: **15 minutos**; excluir optimización no garantiza la hora exacta.

### La operación programada se ejecutó pero copió 0 archivos
Revise el registro: cero copias puede significar existentes omitidos, ningún resultado, recurso inaccesible o permisos fallidos. Compruebe origen, filtros, destino y credenciales; cero no siempre significa éxito.

### ¿Puedo ver qué se procesó?
**Sí.** Toca **"Ver registro"** en la sección de Operaciones programadas para ver un historial con marca de tiempo de cada ejecución, incluidos los resultados por archivo.

---

## Bloque del tiempo

### ¿De dónde viene el tiempo?
El bloque del tiempo del escritorio usa **Open-Meteo.com** - un servicio meteorológico gratuito y sin clave de acceso. Datos meteorológicos de Open-Meteo.com (CC-BY 4.0).

### ¿La app rastrea mi ubicación?
El **bloque del tiempo** usa el lugar introducido, sin seguimiento GPS, y lo envía al servicio meteorológico. El geoetiquetado opcional de fotos es distinto y requiere permiso de ubicación.

---
## ¿Sigues teniendo preguntas?

¿No has encontrado una respuesta arriba, o algo no funciona como se describe? **Escríbenos** - todos los mensajes se leen y la mayoría de los problemas se solucionan.

- 📖 **Guías prácticas** (tareas paso a paso): [HOW_TO.md](HOW_TO-es.html)
- 🚀 **Inicio rápido:** [QUICK_START.md](QUICK_START-es.html)
- 🔧 **Solución de problemas:** [TROUBLESHOOTING.md](TROUBLESHOOTING-es.html)
- 📧 **Correo:** [sza@ukr.net](mailto:sza@ukr.net) - para cualquier cosa: ayuda de configuración, descripciones de errores, sugerencias de funciones
- 🌐 **Página del autor:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Reportar un error:** [Issues de GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferido para errores reproducibles; incluye la versión de Android y qué estabas haciendo
- 📖 **Documentación completa:** [Portal de documentación](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **¿Quieres una función que todavía no existe?** Escríbenos - muchas funciones de la app se añadieron porque alguien las pidió. Si tiene sentido para el caso de uso, se construye.

</div>

</div>
