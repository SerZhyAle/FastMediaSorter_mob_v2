---
layout: default
title: "❓ Preguntas frecuentes (FAQ)"
permalink: /docs/FAQ-es.html
---
<div lang="es" dir="ltr" markdown="1">

# ❓ Preguntas frecuentes (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="es" %}

---

## Preguntas generales

### ¿Qué es FastMediaSorter?
FastMediaSorter v2 es una shell completa para un dispositivo Android: toma el control de la pantalla de inicio, reproduce tu contenido multimedia, abre transmisiones en directo, inicia tus aplicaciones, se comunica con tu reloj, vigila el dispositivo y gestiona todos los archivos que tienes - en carpetas locales, en unidades de red (SMB/SFTP/FTP) y en almacenamiento en la nube (Google Drive, OneDrive, Dropbox).

### ¿Es gratis?
¡Sí! FastMediaSorter v2 es completamente gratuito y de código abierto.

### ¿Qué versión de Android necesito?
Standard, Lite y Photos requieren Android 8.0 (API 26) o más reciente. La variante **Legacy** admite Android 6.0 (API 23) o más reciente. **XR / noLegal** requiere además hardware de visor compatible y la ruta actual de tiempo de ejecución para carga lateral.

### ¿Necesita Internet?
**No**, para archivos locales. **Sí**, para unidades de red y almacenamiento en la nube.

### ¿La app tiene widgets?
¡Sí! FastMediaSorter v2 incluye una variedad de widgets de pantalla de inicio - encuéntralos manteniendo pulsada la pantalla de inicio → Widgets → FastMediaSorter. Incluyen accesos directos a recursos, lanzadores de presentaciones y más.

### ¿Puede la app sustituir mi pantalla de inicio?
Sí, en las compilaciones **Standard** y **noLegal**. Activa **Hacer que esta app sea la pantalla de inicio** en **Ajustes → General** y elige FastMediaSorter cuando Android te pregunte qué pantalla de inicio usar. Obtienes un escritorio con accesos directos a tus carpetas, gadgets como un reloj y el tiempo, una cuadrícula de apps y una barra de tareas. Desactiva el ajuste, o elige **Salir del modo lanzador**, y Android restaura tu pantalla de inicio anterior - el diseño de tu escritorio se conserva para la próxima vez. Consulta [HOW_TO](HOW_TO-es.md#how-to-use-the-app-as-your-home-screen) para el recorrido completo.

### ¿Cómo dejo de usar la app como pantalla de inicio?
Tres formas, la que encuentres primero:

- Abre el menú Inicio en el escritorio, elige **Salir del modo lanzador** y confirma.
- Vuelve a desactivar **Hacer que esta app sea la pantalla de inicio** en **Ajustes → General**.
- Abre la propia lista de apps de inicio de Android en **Ajustes → General → Ajustes del lanzador del sistema → Sistema → Cambiar pantalla de inicio** y elige el lanzador que prefieras.

El diseño de tu escritorio se conserva en todos los casos, así que si vuelves a activar el modo, lo recuperarás tal y como lo dejaste.

### ¿Por qué mi tablet volvió a su antigua pantalla de inicio tras reiniciarse?
Porque el firmware de ese dispositivo la restauró, no porque la app haya perdido el ajuste. Algunas radios de coche baratas y cajas Android integradas restablecen la app de inicio a la de fábrica en cada arranque, sea cual sea tu elección - ninguna app puede evitarlo. Vuelve a elegir FastMediaSorter como app de inicio después de reiniciar, y si tu dispositivo ofrece **Siempre** en lugar de **Solo una vez**, elige **Siempre**. Si aun así se niega a mantenerlo, ese dispositivo simplemente no permite sustituir la pantalla de inicio.

### ¿Puedo poner mis propias carpetas y listas de reproducción en el escritorio?
Sí, para eso está el escritorio. Mantén pulsado un cuadro vacío y elige **Añadir un elemento..**, luego elige lo que quieras: una de tus carpetas, una emisión de radio, una app, una persona o un gadget como el reloj o el tiempo. La nueva celda aparece en el cuadro que pulsaste, y para una carpeta también eliges si se abre en modo explorar, presentación o reproducción. Para reorganizar las cosas después, elige **Editar el escritorio** desde el mismo menú de pulsación larga. Consulta [HOW_TO](HOW_TO-es.md#how-to-use-the-app-as-your-home-screen) para el recorrido completo.

---

## Operaciones con archivos

### ¿Adónde van los archivos eliminados?
Los archivos eliminados se mueven a una carpeta `.trash/` en la misma ubicación (eliminación reversible). No se eliminan de forma permanente hasta que:
- Tocas **"Vaciar papelera"** en Ajustes → Gestión → Eliminación de archivos y papelera, O
- Eliminas manualmente la carpeta `.trash/`

### ¿Puedo deshacer una eliminación/movimiento?
¡Sí! Toca el botón **"Deshacer"** (o la zona táctil inferior derecha) en los segundos siguientes a la operación.

> ⚠️ **Nota:** Deshacer no está disponible para eliminaciones de archivos de red (se eliminan de forma permanente al instante).

### ¿Cuál es la diferencia entre Copiar y Mover?
- **Copiar:** crea un duplicado, el original se queda donde estaba
- **Mover:** reubica el archivo, lo elimina de la ubicación original

### ¿Qué es el modo Todos los archivos?
El **modo Todos los archivos** te permite usar la app como un gestor de archivos completo en todos los directorios. En este modo, la app ignora los filtros multimedia estándar y muestra todos los archivos (incluidos ZIP, RAR, APK, EXE, PDF, etc.). Puedes realizar operaciones de archivo estándar como copiar, mover, renombrar, compartir y eliminar. Para archivos binarios no compatibles, se abre automáticamente un panel inferior que te permite gestionar el archivo o abrirlo con aplicaciones externas.

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

**Problemas comunes y soluciones:**

| Problema | Qué probar |
|---------|------------|
| "Conexión rechazada" | Abre el cortafuegos de Windows → permite el **puerto TCP 445** entrante. O desactiva temporalmente el cortafuegos para probar |
| "Contraseña incorrecta" | Prueba a dejar el usuario en blanco (acceso de invitado). Si usas una cuenta de Microsoft, introduce tu **correo completo** como usuario |
| "Host no encontrado" | Asegúrate de que el teléfono y el PC estén en el **mismo router Wi-Fi**. El aislamiento de AP (un ajuste de seguridad del router) puede bloquear el tráfico entre dispositivos - desactívalo en los ajustes del router |
| El escaneo no encuentra nada | Desactiva la VPN en el teléfono. Activa **Detección de red** en Windows (Panel de control → Centro de redes y recursos compartidos → Configuración de uso compartido avanzado). Luego prueba a introducir la IP manualmente |
| Navegación muy lenta | Edita el recurso → ejecuta **Prueba de velocidad**. Si es inferior a 5 Mbps, cambia el teléfono a la banda Wi-Fi de 5 GHz. Desactiva las miniaturas de vídeo para conexiones lentas |
| Funciona en Wi-Fi pero no con datos móviles | Es normal - SMB es un protocolo solo de red local, no puede funcionar con datos móviles |

→ Recorrido completo: [Guía de configuración SMB](howto/scenario-smb-setup-es.md)

### ¿Cómo me conecto a Google Drive?
1. Toca **"+"** → **Nube** → **Google Drive**
2. Toca "Iniciar sesión con Google"
3. Concede los permisos cuando se te pidan
4. Aparecerán tus carpetas de Drive

**Nota:** los archivos NO se descargan automáticamente - se transmiten bajo demanda.

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
**Sí** - Fast Media Sorter for Windows publica las carpetas del PC que elijas mediante SFTP y muestra un código QR / configuración `.fmscfg`. En el teléfono, usa **Importar desde companion** o **Escanear código QR** en la pantalla Añadir recurso. Consulta la guía del lado del PC: [Cómo publicar carpetas del PC en Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html). Disponible en Standard, Photos, Legacy, XR/noLegal.

### ¿Por qué no cargan las miniaturas de los archivos de red?
Las miniaturas de red se generan **bajo demanda** para ahorrar ancho de banda. Desplázate despacio o espera unos segundos a que aparezcan.

Si las miniaturas nunca llegan a cargar:
- Comprueba que la conexión esté activa: toca el recurso → si la carpeta se abre, la conexión funciona bien
- Edita el recurso → asegúrate de que **"Cargar miniaturas"** esté activado
- Para conexiones muy lentas: desactiva las miniaturas por completo para evitar tiempos de espera agotados (Editar recurso → desactivar miniaturas)

### La conexión se corta / los archivos fallan al abrirse a mitad de reproducción
- Comprueba que la Wi-Fi de tu teléfono sea estable (que no cambie entre las bandas de 2,4 y 5 GHz)
- Algunos routers desconectan las sesiones SMB inactivas - edita el recurso → activa **"Reconectar al fallar"** si está disponible
- Para reproducción de vídeo por SMB: ejecuta la prueba de velocidad (Editar recurso → Prueba de velocidad). Necesitas al menos 10 Mbps para vídeo 1080p

---

## Clasificación rápida y destinos

### ¿Qué son las carpetas de "clasificación rápida"?
Las carpetas de clasificación rápida son carpetas de destino preconfiguradas para clasificar archivos rápidamente. Puedes asignar hasta 30 carpetas con botones numerados.

### ¿Cómo configuro la clasificación rápida?
**Método 1:** Ajustes → Gestión → Destinos de clasificación rápida, luego toca **"Añadir a clasificación rápida"**  
**Método 2:** edita cualquier carpeta → activa "Marcar para clasificación rápida"

### ¿Cómo uso la clasificación rápida mientras veo archivos?
1. Abre una foto/vídeo a pantalla completa
2. Toca un **botón numerado** (0-9) en el panel de comandos, O
3. Toca la **esquina inferior izquierda** (zona COPY) o el **centro inferior** (zona MOVE)

### ¿Puedo usar las teclas numéricas en lugar de tocar?
Sí - conecta un teclado físico, un mando o un mando de TV y tus botones de clasificación rápida se numeran automáticamente (0-9). Pulsa el dígito correspondiente para copiar o mover el archivo a ese destino al instante, igual que al tocar el botón.

### Los botones de clasificación rápida no aparecen
Asegúrate de haber añadido al menos una carpeta de destino primero: Ajustes → Gestión → Destinos de clasificación rápida, luego **"Añadir a clasificación rápida"**. Los botones solo aparecen cuando hay al menos un destino configurado.

### Envié un archivo a la carpeta equivocada por accidente
Toca **Deshacer** de inmediato (esquina inferior derecha del panel de comandos) - disponible durante unos segundos después de cada operación. Si te has quedado sin tiempo, ve a la carpeta de destino y mueve el archivo de vuelta manualmente.

---

## Zonas táctiles

### ¿Qué son las "Zonas táctiles"?
Las zonas táctiles son áreas invisibles de la pantalla que activan acciones al tocarlas. La pantalla se divide en una cuadrícula de 3x3:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
└─────────┴─────────┴─────────┘
```

### ¿Cómo veo las zonas táctiles?
Ajustes → Reproductor → **"Mostrar siempre la superposición de zonas táctiles"**

### ¿Puedo desactivar las zonas táctiles?
Sí, simplemente usa los **botones del panel de comandos** en su lugar. Las zonas táctiles son opcionales.

---

## Captura de pantalla y voz

### ¿Qué es la franja de gestos del borde izquierdo?
Es un menú de captura rápida que abres con un deslizamiento diagonal desde el borde izquierdo de la pantalla. Actívalo en **Ajustes → Gestión → Gestos de borde de pantalla → Superposición de gestos**. Desde el menú puedes hacer una captura de pantalla, tomar una foto, recortar y compartir la imagen actual, abrir un acceso directo a una app o panel, o iniciar una grabación de pantalla, vídeo o voz - todo sin dejar de ver lo que tienes delante. Disponible en Standard y XR/noLegal.

### ¿Cómo grabo una nota de voz rápida?
Tres formas: el elemento **Grabación de voz** en el menú de opciones, el widget de pantalla de inicio **Grabadora rápida**, o la acción **Iniciar grabación de audio** del gesto de borde. Sea cual sea la forma en que la inicies, un control flotante de **Detener** permanece en pantalla - incluso sobre otra app - hasta que lo toques para guardarla.

---

## Entrada y controles

### ¿Admite teclados físicos y mandos?
¡Sí! Hay entrada completa de teclado, ratón y mando disponible en todas las pantallas. Pulsa **F1** en cualquier pantalla para ver las asignaciones de teclas activas en esa pantalla.

### ¿Cómo reasigno los controles / cambio las asignaciones de teclas?
Ajustes → **Gestión** → **Controles y asignación de teclas** - reasigna cualquier acción a una tecla, botón o entrada de mando distinta. La app incluye 70 valores predeterminados; toca **Restablecer** para recuperarlos. Los conflictos se resaltan automáticamente.

### ¿Cómo descargo un archivo multimedia desde una URL?
Comparte cualquier enlace `http(s)` con FastMediaSorter mediante el **panel de compartir** de Android (desde un navegador, una app de mensajería o cualquier otra app). FastMediaSorter descargará el archivo y ofrecerá guardarlo en cualquiera de tus recursos configurados.

---

## Rendimiento y almacenamiento

### ¿Cómo encuentro un archivo concreto por su nombre?
Usa el panel de **Filtro** en Explorar: toca el icono de filtro en la barra de herramientas, escribe cualquier parte del nombre del archivo en el campo de nombre - la lista se actualiza al instante. No hace falta una barra de búsqueda aparte; el filtro cubre por completo este caso.

### ¿Por qué la app va lenta con más de 5000 archivos?
La app usa **paginación** para cargar los archivos en lotes. Para colecciones muy grandes:
- Activa "Desactivar miniaturas" para esa carpeta
- Usa filtros para acotar los resultados
- Ordena por Fecha (más recientes primero) - esto carga primero los archivos recientes y evita escanear toda la carpeta de antemano

### La app se bloquea o se congela
1. Fuerza el cierre y vuelve a abrir la app
2. Si se bloquea en una carpeta concreta: esa carpeta puede contener un archivo corrupto - prueba a abrir los archivos uno por uno para identificarlo
3. Borra la caché: Ajustes → General → **"Borrar caché"** - esto resuelve la mayoría de los problemas de estabilidad tras las actualizaciones
4. Si los bloqueos persisten: repórtalo mediante Issues de GitHub (enlace al final de esta página) - adjunta una descripción de lo que estabas haciendo cuando se bloqueó

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
¡Sí! Edita la carpeta → establece un **código PIN** (4-6 dígitos)

### ¿Se recogen mis datos?
**No.** FastMediaSorter NO recoge ni envía ningún dato personal.

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

### ¿Qué hace el idioma de origen "Auto"?
"Auto" lee el texto con el modelo de inglés y después deduce el idioma de lo leído para la traducción. Para texto en cirílico, elige el idioma de origen explícitamente (por ejemplo **Ruso** o **Ucraniano**) - de lo contrario, las letras se leen como sus equivalentes visuales en latín.

### ¿Funciona sin conexión?
**Sí.** Solo necesitas Internet una vez, para descargar el modelo de texto de tu idioma de origen y el modelo de traducción para tu par de idiomas.

### ¿Por qué la traducción a veces es más lenta?
El primer uso de un idioma carga su modelo de texto, y las imágenes grandes o muy detalladas tardan más en leerse. Las siguientes ejecuciones con el mismo idioma empiezan más rápido.

### ¿Qué es el modo de traducción estilo lente?
El **modo estilo lente** muestra las traducciones como una superposición sobre la imagen original, de forma similar a Google Lens. Esto te permite ver el texto traducido en su contexto y posición originales. Puedes activarlo en **Ajustes → Multimedia → Otros** (el interruptor "Superposición estilo lente").

El **modo estándar** muestra las traducciones en una vista de texto separada debajo de la imagen.

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
**Casi.** La música de la presentación necesita compatibilidad de audio:
- **Standard**, **Legacy**, **XR / noLegal** - compatibilidad de audio completa, incluida la reproducción que continúa en segundo plano
- **Lite** - reproduce archivos de audio locales y letras, pero no tiene servicio de reproducción en segundo plano, así que el sonido se detiene cuando la app deja de estar en primer plano
- **Photos** - sin ninguna compatibilidad de audio, así que no hay música en la presentación

---

## Emisiones de Internet

### ¿FastMediaSorter reproduce radio por Internet?
Sí. La pantalla **Streams** reproduce emisiones de audio http/https (mp3/aac), radio Icecast/Shoutcast con metadatos ICY de reproducción actual, HLS (.m3u8) y DASH VOD, y fuentes RTSP. Disponible en Standard, Legacy y XR / noLegal. Lite y Photos no tienen pantalla de Streams en absoluto - la función está ausente ahí, no solo limitada a algunos protocolos.

### ¿Cómo abro la pantalla de Streams?
Toca **Streams** en el menú desplegable de la ventana principal (visible cuando Streams está activado). También puedes acceder mediante **Ajustes > Multimedia > Streams**, donde está el interruptor principal.

### ¿Cómo añado una emisora de radio?
En la pantalla de Streams, toca **⋮** al final de la barra de herramientas, elige **Añadir emisión** y pega la URL de la emisora. Toca Guardar. La emisora aparece en la lista inmediatamente.

### ¿Puedo importar una lista de reproducción?
Sí - toca **⋮ > Importar desde URL** e introduce una dirección `.m3u` remota. El mismo menú tiene **Actualizar catálogo de FastMediaSorter** para la lista curada (con chips de tema e idioma), también disponible desde **Ajustes > Extensiones** o desde la pantalla de bienvenida inicial.

### Una emisión no se reproduce - ¿qué hago?
Si una emisión falla, aparece un diálogo con las opciones **Reintentar**, **Eliminar** y **Cancelar**. Las redirecciones 301 entre protocolos se gestionan automáticamente. Si el host está caído o va muy lento, la importación del catálogo agota el tiempo de espera rápidamente en lugar de quedarse colgada.

### ¿La radio sigue sonando si salgo de la pantalla de Streams?
Depende de **Ajustes > Reproductor > Reproducción de audio en segundo plano**. Con el audio en segundo plano ACTIVADO, la reproducción continúa. Con él DESACTIVADO, salir de la pantalla detiene la emisión y ofrece elegir entre Detener y Seguir reproduciendo - el mismo comportamiento que el reproductor de audio local.

### ¿Puedo ver miniaturas en directo de las emisiones?
Cambia el interruptor de la barra de herramientas de Streams a la vista **Cuadrícula** - cada canal aparece como una ficha con su último fotograma capturado, para que puedas ver de un vistazo qué se está emitiendo. La ficha sigue visible incluso después de cerrar y volver a abrir la app, y se actualiza con una nueva captura en cuanto la emisión vuelve a estar en directo.

### ¿Puedo enviar una emisión a mi TV con Cast?
Sí, para emisiones de vídeo - toca **Cast** en el reproductor y elige un Chromecast en la misma red Wi-Fi. Las emisiones RTSP no se pueden enviar con Cast; el botón solo aparece para los formatos que admite el receptor Chromecast.

---

## Wear OS

### ¿FastMediaSorter funciona en relojes inteligentes Wear OS?
¡Sí! FastMediaSorter v2 incluye una app companion para Wear OS, y ha pasado de ser un simple visor de archivos locales a una auténtica segunda pantalla para tu contenido multimedia.

### ¿Qué puedo hacer en el reloj?
- **Explorar y reproducir** - las carpetas y favoritos de tu teléfono emparejado, o el propio almacenamiento local del reloj, como una cuadrícula de miniaturas con búsqueda, filtro y orden. El audio y el vídeo se reproducen con aleatorio, volumen por bisel y un modo de pantalla apagada que mantiene el sonido activo.
- **Mover archivos en ambos sentidos** - envía una foto, un vídeo o una pista del teléfono directamente al reloj (desde el propio panel de compartir), o copia un archivo del reloj de vuelta a una carpeta del teléfono que elijas.
- **Grabar una nota de voz en la muñeca** - se guarda en el reloj hasta que la envías al teléfono, así que nada se pierde a mitad de la grabación.
- **Reproducir emisiones en directo** - la radio y el vídeo de tu catálogo de Streams se reproducen directamente desde la propia lista de canales del reloj, con tus favoritos fijados arriba.
- **Echar un vistazo sin abrir la app** - añade una ficha de FastMediaSorter al panel de deslizamiento de tu esfera, o su complicación a una esfera compatible.
- **Pequeñas herramientas integradas** - una calculadora, un monitor de red y el minijuego tienen cada uno su propia pantalla en el reloj.

Los ajustes que cambias en el teléfono se sincronizan con el reloj y viceversa, así que solo tienes que configurarlo una vez.

**Nota:** el reloj nunca abre recursos en la nube por su cuenta - no tiene cliente de nube propio, y el teléfono no le pasa sus carpetas en la nube; un archivo de la nube solo llega al reloj cuando lo abres en el teléfono y eliges tu reloj en "Enviar a..". En la versión completa de la app del reloj (APK directo) el reloj sí se conecta por su cuenta a recursos compartidos SMB, FTP y SFTP por Wi-Fi - los recursos de red que le envías desde el teléfono. La versión de Google Play de la app del reloj es un primer lanzamiento reducido (calculadora, cronómetro, minijuego y ajustes) y todavía no explora contenido multimedia.

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
Reglas de automatización basadas en tiempo que ejecutan operaciones de Copiar, Mover o Eliminar entre cualquiera de tus recursos (carpetas locales, NAS, nube) según una programación repetitiva - incluso con la app cerrada.

### ¿Dónde configuro las Operaciones programadas?
Ajustes → **Gestión** → **Operaciones programadas por horario**. Toca **"+"** para añadir una regla nueva.

### ¿Se ejecutará si mi app está cerrada?
**Sí.** Las operaciones se programan mediante **WorkManager** de Android, que las ejecuta en segundo plano independientemente de si la app está abierta.

### ¿Por qué una operación programada no se ejecutó a la hora exacta?
Android puede retrasar las tareas de WorkManager unos minutos para optimizar la batería. Para una temporización más fiable, concede a la app la exención de **Optimización de batería** (Ajustes → General → Optimización de batería). El intervalo mínimo es de 15 minutos.

### La operación programada se ejecutó pero copió 0 archivos
Normalmente es correcto - significa que todos los archivos ya estaban presentes en el destino (la operación usa "omitir existentes" por defecto). Para verificarlo: revisa el registro de la operación y compara el número de "omitidos" con el de "copiados".

Si esperabas que se copiaran archivos nuevos y no fue así:
- Asegúrate de que el **origen** esté configurado en el recurso correcto (por ejemplo, el recurso virtual "Fotos de la cámara" - no una ruta manual que podría estar equivocada)
- Comprueba que el recurso de destino (SMB / nube) estuviera accesible a la hora programada - si la Wi-Fi estaba apagada, la ejecución se omite y se reintenta la próxima vez

### ¿Puedo ver qué se procesó?
**Sí.** Toca **"Ver registro"** en la sección de Operaciones programadas para ver un historial con marca de tiempo de cada ejecución, incluidos los resultados por archivo.

---

## Bloque del tiempo

### ¿De dónde viene el tiempo?
El bloque del tiempo del escritorio usa **Open-Meteo.com** - un servicio meteorológico gratuito y sin clave de acceso. Datos meteorológicos de Open-Meteo.com (CC-BY 4.0).

### ¿La app rastrea mi ubicación?
**No.** El lugar es el que escribes tú mismo, y no se solicita ningún permiso de ubicación. El bloque se actualiza aproximadamente cada 20 minutos y muestra la última lectura con una nota "Último dato conocido" cuando no hay conexión. Al tocarlo se abre la app de mapas del dispositivo.

---
## ¿Sigues teniendo preguntas?

¿No has encontrado una respuesta arriba, o algo no funciona como se describe? **Escríbenos** - todos los mensajes se leen y la mayoría de los problemas se solucionan.

- � **Guías prácticas** (tareas paso a paso): [HOW_TO.md](HOW_TO-es.md)
- 🚀 **Inicio rápido:** [QUICK_START.md](QUICK_START-es.md)
- 🔧 **Solución de problemas:** [TROUBLESHOOTING.md](TROUBLESHOOTING-es.md)
- �📧 **Correo:** [sza@ukr.net](mailto:sza@ukr.net) - para cualquier cosa: ayuda de configuración, descripciones de errores, sugerencias de funciones
- 🌐 **Página del autor:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Reportar un error:** [Issues de GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferido para errores reproducibles; incluye la versión de Android y qué estabas haciendo
- 📖 **Documentación completa:** [Portal de documentación](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **¿Quieres una función que todavía no existe?** Escríbenos - muchas funciones de la app se añadieron porque alguien las pidió. Si tiene sentido para el caso de uso, se construye.

</div>
