---
layout: default
title: "Reproductor de Música en el Coche (Unidad Central Android) - FastMediaSorter v2"
permalink: /docs/howto/scenario-car-music-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 🚗 Reproductor de Música en el Coche (Unidad Central Android)

> **Nivel:** Principiante &bull; **Tiempo:** ~10 minutos &bull; **Edición:** Standard, Legacy, VR, noLegal (Lite reproduce audio local pero no tiene reproducción en segundo plano ni Streams; Photos no tiene audio)

{% include lang-switcher.html doc="scenario-car-music" dir="/docs/howto/" current="es" %}

FastMediaSorter funciona muy bien como reproductor de música para coche en unidades centrales Android - acceso instantáneo a toda tu colección de música en tarjeta SD o unidad USB, con soporte integrado para botones del volante.

> **¿Qué es una unidad central Android?** Es un radio de coche con pantalla táctil que ejecuta Android - como tu teléfono, pero instalado en el tablero. Esta guía también funciona en un teléfono o tablet normal montado en el coche.

---

## Lo Que Necesitarás

- Unidad central Android / teléfono / tablet en el coche
- Archivos de música en **tarjeta SD**, **unidad USB** o **almacenamiento interno** (MP3, FLAC, AAC, OGG, y otros)
- (Opcional) Botones de medios en el volante

---

## Paso 1 - Agrega tu Carpeta de Música

1. Abre la app
2. Toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas superior
3. Selecciona **"Carpeta Local"**
4. Navega hasta donde está almacenada tu música:
   - **Tarjeta SD:** busca una carpeta llamada `/storage/` - dentro encontrarás una carpeta con un código como `1234-5678`, y tu música suele estar en `/storage/1234-5678/Music`
   - **Almacenamiento interno:** prueba `/sdcard/Music` o `/sdcard/Download`
   - **Unidad USB:** busca en `/storage/usb0/` o `/storage/usbdisk/`
5. Selecciona la carpeta → toca **Seleccionar**

> **¿No encuentras tu música?** Intenta tocar **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Carpeta Local"** y luego busca una carpeta llamada `Music` en cualquier parte de la lista. En la mayoría de los dispositivos está ahí mismo.


---

## Paso 2 - Configura la Carpeta para Música

Mantén presionada tu carpeta de música en la pantalla principal → toca **Editar (ícono de lápiz)**.

Esto abre la configuración de la carpeta. Establece estas opciones:

| Configuración | Valor | Por qué |
|---------|-------|-----|
| **Perfil** | Biblioteca de Audio | Le dice a la app "esta es una carpeta de música" - configura todo automáticamente para audio |
| **Tipos Compatibles** | Solo audio | Oculta fotos y videos para que solo se muestren pistas de música |
| **Modo de orden** | Título (A→Z) o Artista | Mantiene tus pistas en un orden lógico |
| **Incluir Subcarpetas** | ACTIVADO | Si tu música está organizada en subcarpetas de artista/álbum, esto encuentra todas las pistas |

Toca **Guardar**.

> **¿Qué hace "Perfil"?** Es un preajuste de un toque que configura la carpeta de forma óptima para su propósito. Elegir "Biblioteca de Audio" hace que la app muestre carátulas, ordene correctamente para música y oculte archivos que no sean de audio automáticamente.


---

## Paso 3 - Abre la Carpeta y Comienza a Reproducir

1. Toca tu carpeta de música en la pantalla principal
2. Todas las pistas aparecen en una lista con miniaturas de carátulas
3. Toca **cualquier pista** para comenzar a reproducir

Se abre el **reproductor de audio** a pantalla completa con carátula, barra de progreso y controles de reproducción.

![Reproductor de audio a pantalla completa con carátula (Camel - Dust and Dreams)](screenshots/screenshot-car-step3.png)

---

## Paso 4 - Asegúrate de que la Música Siga Reproduciéndose

Este paso garantiza que la música siga reproduciéndose cuando la pantalla se apaga, cambias de app o recibes una notificación de llamada:

1. Ve a **Configuración → pestaña Medios**
2. Desplázate hasta la sección **Audio**
3. Asegúrate de que **"Soporte de audio"** esté ACTIVADO

Eso es todo. Una vez activado esto, la app se registra como un reproductor de música adecuado - los controles de la pantalla de bloqueo y el reproductor multimedia de la barra de notificaciones aparecen automáticamente.

![Configuración → Medios → sección Audio con opciones de reproducción en segundo plano](screenshots/screenshot-car-step4.png)

---

## Paso 5 - Prueba los Botones del Volante

Presiona **Siguiente** o **Anterior** en tu volante.

**Funcionan automáticamente - no se necesita configuración.** FastMediaSorter responde a todos los botones multimedia estándar de Android.

> **¿Los botones no funcionan?** Algunas unidades centrales más antiguas envían señales no estándar. Prueba ir a **Configuración de Android → Accesibilidad** y busca una opción de "receptor de botones multimedia". Si eso no ayuda, usa las zonas táctiles en pantalla (borde izquierdo/derecho de la pantalla) en su lugar - funcionan perfectamente.


---

## Paso 6 - (Opcional) Usa "Toda la Música" - Un Solo Lugar para Todas tus Pistas

Si tu música está repartida en varias carpetas (por ejemplo, algo en la tarjeta SD, algo en el almacenamiento interno), el recurso virtual **Toda la Música** reúne todo en un solo lugar automáticamente:

1. En la pantalla principal, busca la tarjeta **"Toda la Música"** - normalmente se crea automáticamente si tienes archivos de audio locales
2. Si no está ahí: toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → desplázate hasta **Recursos Virtuales** → toca **"Toda la Música"**

Ahora todas tus pistas de todas las ubicaciones aparecen juntas en una sola lista.

![Pantalla principal de FastMediaSorter - tarjeta del recurso virtual Toda la Música visible](screenshots/screenshot-car-step6.png)

---

## Paso 7 - (Opcional) Acceso Directo en la Pantalla de Inicio para Iniciar con un Toque

Perfecto para cuando solo quieres subir al coche y tocar un botón para iniciar la música:

1. Mantén presionado un espacio vacío en la pantalla de inicio → toca **Widgets**
2. Busca **FastMediaSorter** en la lista → arrastra el widget **"Acceso Directo a Recurso"** a tu pantalla de inicio
3. Cuando se te solicite, selecciona tu recurso de música
4. Listo - toca el widget en cualquier momento y la música comienza de inmediato

---

## Paso 8 - (Opcional) Agrega Estaciones de Radio por Internet

Si tu unidad central tiene una conexión activa de datos móviles o Wi-Fi, puedes agregar estaciones de radio por internet directamente - sin app adicional:

1. Abre el menú principal (hamburguesa o desplegable) → toca **Streams**
2. Toca **Agregar (+)** → pega cualquier URL de radio por internet (http/https, .m3u8, RTSP) y toca Guardar
3. O toca **Importar catálogo** para explorar la lista curada de estaciones integrada y agregar estaciones por género o idioma
4. Toca una fila de estación para iniciar la reproducción de audio en línea - el nombre de la estación y la pista actual aparecen en el mini-control inferior
5. La lista permanece visible para que puedas cambiar de estación sin salir de la pantalla

> **Audio en segundo plano:** para mantener la radio reproduciéndose al cambiar de app, ve a **Configuración → Reproductor → Reproducción de audio en segundo plano** y actívalo.

Nota: Streams requiere una conexión de red, y la pantalla de Streams está ausente en las ediciones Lite y Photos.

---

## ¡Listo! Controles del Reproductor

Mientras la música se reproduce, la pantalla es tu panel de control:

- **20% izquierdo de la pantalla** → Pista anterior
- **20% derecho de la pantalla** → Siguiente pista
- **60% central** → Pausar / Reproducir / Menú de comandos

![Reproductor de audio ejecutándose en segundo plano - panel de comandos visible](screenshots/screenshot-car-done.png)

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| Los botones del volante no funcionan | Verifica si tu unidad central envía eventos de teclas multimedia Android estándar. Algunas unidades necesitan el "receptor de botones multimedia" activado en Configuración de Android → Accesibilidad |
| La música se detiene cuando se bloquea la pantalla | Activa **"Evitar suspensión"** en Configuración → General, o usa los controles de la notificación de audio para reanudar. También verifica que Soporte de audio esté ACTIVADO (Paso 4) |
| No se muestra carátula | Activa **"Obtener carátulas de audio en línea"** en Configuración → Medios → Audio (requiere Wi-Fi). Para carátulas sin conexión, la app lee la carátula incorporada del archivo MP3/FLAC automáticamente |
| No encuentro música en la tarjeta SD | Algunas versiones de Android restringen el acceso a la tarjeta SD. Intenta agregar la ruta de la tarjeta SD usando el botón **"Explorar.."** en el selector de carpetas, que usa el selector de archivos del sistema Android con acceso completo a la tarjeta SD |
| El audio se entrecorta o salta | Cierra otras apps que se ejecutan en segundo plano. Para archivos FLAC, asegúrate de que la unidad central tenga suficiente capacidad de procesamiento |
| La radio por internet se detiene al cambiar de app | Ve a Configuración → Reproductor → Reproducción de audio en segundo plano y asegúrate de que esté activada |

</div>
