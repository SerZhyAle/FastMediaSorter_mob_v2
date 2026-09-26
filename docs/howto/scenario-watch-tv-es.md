---
layout: default
title: "Mira Canales de TV en tu Smartwatch - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-tv-es.html
---
<div lang="es" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_stream.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Mira Canales de TV en tu Smartwatch

> **Nivel:** Principiante &bull; **Tiempo:** ~10 minutos &bull; **Dispositivo:** Smartwatch Wear OS

> **Solo versión completa** - esta guía no está implementada en la versión distribuida a través de Google Play. Aplica a la versión completa, una descarga directa de APK desde [Descargas](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-tv" dir="/docs/howto/" current="es" %}

FastMedia Wear reproduce canales de TV y radio en vivo directamente en tu muñeca. El reloj abre la transmisión con su propio Wi-Fi, así que una vez que un canal está en la lista puedes verlo con el teléfono en otra habitación, en una bolsa, o completamente apagado.

> **¿Buscas música almacenada en su lugar?** Consulta [Música en el Smartwatch](scenario-watch-music-es.md). Para tus propios archivos en un NAS o recurso de PC, consulta [Conectar el Reloj a Recursos de Red](scenario-watch-network-es.md).

---

## Lo Que Necesitarás

- Un smartwatch con **Wear OS 2.0** o más nuevo con FastMedia Wear instalado
- Una red Wi-Fi a la que el reloj pueda unirse, o un teléfono emparejado para retransmitir la conexión
- Opcional: FastMediaSorter en tu teléfono Android, si quieres enviar tus propios canales al reloj

---

## Paso 1 - Abre Streams

1. Abre **FastMedia Wear** en tu reloj.
2. En la pantalla principal, toca **Streams**.

![Pantalla principal de FastMedia Wear con la sección Streams](screenshots/screenshot-wear-tv-step1.png)

La pantalla principal mantiene las mismas seis secciones en los mismos lugares, así que Streams siempre está en la fila inferior sin importar el tamaño de cuadrícula que elijas. Encima de ellas hay una fila con los recursos que abriste más recientemente - una vez que hayas visto algo, el canal que dejaste aparece ahí con un solo toque.

---

## Paso 2 - Llena la Lista de Canales

Una instalación nueva no tiene canales todavía, y la pantalla lo indica.

![Pantalla de Streams vacía con el botón Actualizar catálogo](screenshots/screenshot-wear-tv-step2.png)

Hay dos formas de llenarla, y funcionan juntas:

- **Descarga el catálogo compartido.** Toca **Actualizar catálogo**. El reloj obtiene el banco de canales publicado en un solo archivo - muchos miles de canales de TV y radio con sus temas, idiomas y países.
- **Envía canales desde tu teléfono.** Un canal que agregaste tú mismo en FastMediaSorter en el teléfono se puede enviar con **Enviar al reloj** desde la lista de streams del teléfono. Los canales que fijas en el teléfono también se elevan hacia la parte superior de la lista del reloj, justo detrás de los que fijaste en el propio reloj, así que los dos o tres que realmente ves están al alcance sin desplazarte. Al desfijar en el teléfono, el canal se retira de ese grupo de nuevo, y un canal que el propio catálogo del reloj no tiene simplemente se omite.

Los canales enviados desde el teléfono sobreviven a una actualización del catálogo - la actualización reemplaza el banco compartido y deja intactas tus propias filas.

---

## Paso 3 - Encuentra el Canal Que Quieres

Los tres botones en la parte superior de la lista permanecen fijos mientras la lista se desplaza, así que nunca se desplazan fuera de alcance.

- **Buscar** filtra la lista mientras escribes.
- **Filtrar** reduce por tema y por idioma. Los nombres se muestran en tu idioma de interfaz en lugar del inglés bruto del catálogo, los más poblados primero, con el conteo de canales en cada fila, y los tres idiomas propios de la app arriba.
- **Filtrar** también lista las colecciones curadas que vinieron con el catálogo - "TV Rusa", "Radio de la ex URSS", "TV Africana" y el resto, las mismas que muestra el teléfono. Elige una para ver solo sus canales, o elige **Todos** para quitar la restricción. Un canal puede pertenecer a varias colecciones, así que la misma estación aparece bajo más de una. Si el catálogo descargado no lleva colecciones, la entrada no se muestra en absoluto.
- **Ordenar** ofrece Más usados, Nombre A-Z, Nombre Z-A y Por tipo de medio. Más usados es el predeterminado y se eleva con los canales que realmente inicias en el reloj, así que la lista aprende tus hábitos por sí sola.

Encima de la lista, un pequeño contador de dos líneas muestra cuántos canales dejan la búsqueda y los filtros actuales, sobre el tamaño de todo el catálogo.

![Lista de canales con el contador y la barra de herramientas fija](screenshots/screenshot-wear-tv-step3.png)

En modo de cuadrícula, un canal de video muestra una imagen de vista previa antes de que lo hayas abierto alguna vez, tomada de un conjunto de vistas previas descargable. Después de tu primera visualización, la vista previa es reemplazada por un fotograma capturado del propio canal.

---

## Paso 4 - Mira

1. Toca un canal. El reproductor de video se abre a pantalla completa.
2. **Volumen:** gira el bisel giratorio o la corona.
3. **Buscar:** mantén presionado el botón anterior o siguiente. Ambos botones permanecen en pantalla incluso para un solo canal.
4. **Encuadre:** el botón de modo de encuadre alterna entre ajustar toda la imagen dentro del vidrio redondo y recortarla para llenar la pantalla. El reloj recuerda tu elección - sobrevive a salir del reproductor y reiniciar la app, y la misma elección cubre tus propios archivos de video.
5. **Apagar pantalla:** el menú del reproductor tiene una entrada **Apagar pantalla**. La pantalla se pone completamente negra - sin reloj, sin controles - mientras el canal sigue reproduciéndose, y el reloj no se dormirá. Un solo toque solo marca el punto que tocaste con un pequeño punto blanco; un doble toque, una pulsación prolongada, o el propio botón del reloj trae de vuelta la imagen y los controles exactamente como los dejaste.
6. **Fijar:** la marca en el reproductor fija el canal. Los canales fijados se listan primero la próxima vez que abras Streams: los que fijaste aquí en el reloj van primero, los fijados en el teléfono los siguen, y todo lo demás mantiene el orden que da tu clasificación elegida. El fijado está vinculado a la dirección del canal, así que sobrevive a una reimportación del catálogo.

> **El video necesita la pantalla.** La reproducción en segundo plano mantiene el **audio** sonando después de que sales de la app - útil para canales de radio - pero el video y las presentaciones se detienen cuando la app abandona la pantalla. Eso es deliberado: un video que no puedes ver solo drena la batería.

---

## Paso 5 - Vuelve con un Solo Toque

- La **fila reciente de la pantalla principal** lista el último canal que reprodujiste junto a los recursos de red que abriste recientemente, con el ícono propio del canal. Tocarlo reabre el reproductor.
- Se puede agregar un **tile de Stream** al carrusel de tiles de Wear OS y apuntarlo a un canal desde el propio reloj. Desde entonces el canal está a un deslizamiento de la esfera del reloj, sin necesidad de abrir la app primero.
- La **complicación del último recurso** también muestra el canal, así que puede estar en la esfera del reloj.

---

## Paso 6 - Cuando la Señal Es Débil

Las transmisiones en vivo son lo más exigente que un reloj hace con su red, así que la app es explícita al respecto:

- Mientras se reproduce una transmisión, el reloj pide al sistema una red de banda ancha y la libera cuando la reproducción termina.
- Si el enlace actual no puede soportar la transmisión, el reloj lo indica en lugar de fallar en silencio.
- Si una transmisión se congela sin ningún error - la forma habitual en que muere una señal en vivo - un vigilante la reancla y la reprepara hasta tres veces, mostrando **Reconectando**. Solo cuando la red permanece muerta recurre al mensaje de canal no disponible.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| "No hay streams disponibles" tras una instalación nueva | Toca **Actualizar catálogo**, o envía un canal desde el teléfono con **Enviar al reloj** |
| "No se pudieron actualizar los streams" | El catálogo es una descarga de varios megabytes. Pon el reloj en Wi-Fi en lugar de un enlace retransmitido por el teléfono, e intenta de nuevo |
| Un canal se abre y luego se detiene | La fuente misma puede estar fuera de línea. El reloj reintenta tres veces antes de rendirse - prueba otro canal para distinguir una transmisión muerta de una red muerta |
| El video se detiene al bajar la muñeca | Es lo esperado: solo el audio continúa una vez que la app abandona la pantalla. Para mantener un canal reproduciéndose con la pantalla oscura, quédate en el reproductor y usa su entrada **Apagar pantalla** |
| El canal que fijaste en el teléfono no está arriba | Los fijados viajan cuando el companion de Wear está activado en la app del teléfono; verifica eso primero |
| El sonido está muy bajo | Gira el bisel o la corona en el reproductor - cambia el volumen multimedia del reloj, no la posición de reproducción |

</div>
