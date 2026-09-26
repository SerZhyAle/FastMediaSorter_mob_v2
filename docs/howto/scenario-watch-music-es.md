---
layout: default
title: "Escucha Música en tu Reloj - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-music-es.html
---
<div lang="es" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_audio.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Escucha Música en tu Reloj

> **Nivel:** Principiante &bull; **Tiempo:** ~5 minutos &bull; **Dispositivo:** Smartwatch Wear OS (emparejado con teléfono Android)

> **Solo versión completa** - esta guía no está implementada en la versión distribuida a través de Google Play. Aplica a la versión completa, una descarga directa de APK desde [Descargas](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-music" dir="/docs/howto/" current="es" %}

FastMediaSorter te permite explorar y reproducir tu colección de música directamente desde tu smartwatch Wear OS. Puedes transmitir pistas compartidas desde tu teléfono emparejado o reproducir archivos de audio locales almacenados en el reloj, con carátulas, aleatorio, control de volumen por bisel giratorio, reproducción en segundo plano que sobrevive al salir de la app, y un modo de pantalla apagada que mantiene la música sonando con la pantalla oscura.

---

## Lo Que Necesitarás

- Un smartwatch con **Wear OS 2.0** o más nuevo con FastMedia Wear instalado
- Un teléfono Android ejecutando FastMediaSorter (si transmites música desde tu teléfono)
- Archivos de música (MP3, FLAC, AAC, OGG) en tu teléfono o transferidos al almacenamiento del reloj
- Audífonos Bluetooth o el altavoz del reloj para la salida de audio

---

## Paso 1 - Abre FastMedia Wear en tu Reloj

1. Abre la lista de apps en tu smartwatch y toca **FastMedia Wear**.
2. La pantalla principal muestra seis secciones, siempre en los mismos lugares:
   - **Recursos**: fuentes de red y opciones de sincronización
   - **Teléfono**: música y medios compartidos desde tu teléfono Android emparejado
   - **Local**: archivos en el propio almacenamiento del reloj, incluyendo notas de voz que grabaste ahí
   - **Streams**: canales de TV y radio ([guía separada](scenario-watch-tv-es.md))
   - **Apps**: calculadora, monitor de red, juego y los otros mini-programas
   - **Favoritos**: todo lo que marcaste

![Pantalla principal de FastMedia Wear en el smartwatch](screenshots/screenshot-wear-music-step1.png)

---

## Paso 2 - Elige tu Fuente de Música

1. Para reproducir música desde tu teléfono: toca **Teléfono** en la pantalla principal, luego toca **Audio**.
2. Para reproducir pistas almacenadas directamente en el reloj: toca **Local** en la pantalla principal, luego toca **Música**.
3. FastMedia Wear se conecta a la fuente seleccionada y carga tu catálogo de música.

> **Consejo:** La pantalla principal mantiene una fila con los recursos que abriste más recientemente encima de las seis secciones - una celda por columna, así que dos en una cuadrícula de dos columnas y tres en una de tres columnas. Una vez que hayas reproducido algo, está ahí con un solo toque, y el último canal de stream que viste está en la misma fila.

---

## Paso 3 - Explora e Inicia la Reproducción

1. Desplázate por tus pistas usando el tacto o el bisel giratorio.
2. Cada elemento muestra el título de la pista, la duración y una miniatura de la carátula.
3. Toca **cualquier pista** para iniciar la reproducción de inmediato.

![Explorar pistas de audio en el reloj](screenshots/screenshot-wear-music-step3.png)

---

## Paso 4 - Controla la Reproducción y el Volumen

Cuando una pista comienza, se abre el **Reproductor de Audio** a pantalla completa:

- **Reproducir / Pausar**: toca el botón resaltado central para pausar o reanudar la reproducción.
- **Saltar pistas**: toca **Anterior** o **Siguiente** para cambiar de pista en tu lista de reproducción.
- **Aleatorio**: toca el botón **Aleatorio** para mezclar el orden de las pistas.
- **Buscar en la pista**: arrastra la barra de progreso horizontalmente para saltar a cualquier posición en la canción.
- **Volumen**: gira la corona o bisel giratorio de tu reloj para ajustar el volumen suavemente. Aparece un indicador de nivel de volumen en pantalla.
- **Favorito**: toca el ícono de estrella para agregar la pista a tus favoritos.

![Reproductor de audio con controles de reproducción y volumen](screenshots/screenshot-wear-music-step4.png)

---

## Paso 5 - Mantén la Música Sonando

Hay dos formas diferentes de seguir escuchando, y responden a dos preguntas diferentes.

**Salir de la app** - activa **Seguir reproduciendo en segundo plano** en la configuración del reloj. El audio continúa entonces después de minimizar la app o volver a la esfera del reloj, con controles en la notificación multimedia. Cuando vuelvas, la pantalla principal muestra una fila con lo que se está reproduciendo: tócala para volver a la pista donde la dejaste, o toca el botón de detener junto a ella para terminar la reproducción sin abrir nada más. El interruptor es opcional, y necesita que las notificaciones estén permitidas - sin ellas el sistema no puede mantener vivo el servicio de reproducción.

**Quedarte en el reproductor con la pantalla oscura** - toca el botón **Apagar pantalla** en la parte inferior de los controles del reproductor. La pantalla se pone completamente negra mientras la música sigue reproduciéndose, lo cual ahorra batería en un reloj OLED. Un solo toque solo marca el punto que tocaste con un pequeño punto blanco, así que una manga rozando el vidrio no cambia nada; un doble toque, una pulsación prolongada, o el propio botón del reloj trae de vuelta los controles. En las esferas de reloj más pequeñas, el botón permanece en el menú del reproductor en lugar de en la fila.

![Botón del modo de pantalla apagada](screenshots/screenshot-wear-music-step5.png)

> Los videos y presentaciones deliberadamente no están cubiertos por ninguno de los dos: se detienen cuando la app abandona la pantalla, porque una imagen que nadie puede ver solo cuesta batería.

---

## ¡Listo! Funciones del Reproductor

- **Carátula y Fondo de Onda**: Muestra carátula a pantalla completa u ondas de sonido dinámicas detrás de los controles.
- **Integración con Bisel Giratorio**: Control de volumen nativo usando el bisel físico o la corona del reloj.
- **Reproducción en Segundo Plano**: El audio sobrevive al salir de la app, con controles en la notificación multimedia y una fila en la pantalla principal indicando qué se está reproduciendo.
- **Escucha con Pantalla Apagada**: Apagado instantáneo de la pantalla dentro del reproductor, preservando la reproducción y la duración de la batería.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| La sección Teléfono dice "Teléfono no conectado" | Asegúrate de que el Bluetooth esté activado en ambos dispositivos y que FastMediaSorter esté instalado en tu teléfono |
| No aparecen archivos de música en Local | Copia archivos MP3 o FLAC al almacenamiento interno de tu reloj o usa la sección Teléfono para reproducir desde tu teléfono |
| El audio se detiene al salir de la app | Activa **Seguir reproduciendo en segundo plano** en la configuración del reloj, y permite las notificaciones - el servicio de reproducción las necesita para mantenerse activo |
| Falta la carátula | Conéctate a Wi-Fi para obtener carátulas en línea, o asegúrate de que tus archivos de audio contengan carátulas ID3 incorporadas |

</div>
