---
layout: default
title: "Radio por Internet y Transmisiones - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 📻 Radio por Internet y Transmisiones

> **Nivel:** Principiante - **Tiempo:** ~10 minutos - **Edición:** Standard, Legacy, VR, noLegal (Streams está ausente en Lite y Photos)

{% include lang-switcher.html doc="scenario-internet-radio" dir="/docs/howto/" current="es" %}

FastMediaSorter incluye una pantalla dedicada de Streams para fuentes de audio y video por internet. Agrega cualquier URL de radio por internet, importa una lista de reproducción .m3u, o explora un catálogo curado de estaciones - sin necesidad de una app de radio aparte. Funciona muy bien en unidades centrales de coche Android, reproductores de audio, teléfonos y tablets.

> **Reemplaza a:** TuneIn, app de Shoutcast, Online Radio, RadioDroid, transmisiones de red de VLC, reproductores IPTV.

---

## Lo Que Necesitarás

- Dispositivo Android con conexión de red (datos móviles o Wi-Fi)
- FastMediaSorter Standard, Legacy, VR, o noLegal (la pantalla de Streams está ausente en Lite y Photos)
- Una URL de transmisión, un archivo o URL de lista de reproducción .m3u, o el catálogo curado integrado

---

## Paso 1 - Abre la Pantalla de Streams

Tres formas de llegar ahí:
- Menú desplegable de la pantalla principal -> **Streams**
- **Configuración -> Medios -> Streams** -> toca el botón de acceso directo a Streams
- Incorporación de bienvenida -> fila de Streams (solo en el primer inicio)

> **¿No ves Streams en el menú?** Ve a Configuración -> Medios -> Streams y asegúrate de que "Activar Streams" esté ACTIVADO. Está activado por defecto en la mayoría de los dispositivos.

---

## Paso 2 - Agrega una Estación o Transmisión

**Opción A - Agregar una sola URL manualmente:**
1. Toca **Agregar (+)** en la barra de herramientas de la pantalla de Streams
2. Pega la URL de la transmisión (radio http/https, .m3u8 HLS, rtsp://..)
3. Dale un nombre y toca **Guardar**

**Opción B - Importar una lista de reproducción .m3u:**
1. Toca **Importar** -> **Desde URL**
2. Pega la URL de la lista de reproducción .m3u y confirma
3. Todas las estaciones de la lista se agregan a tu lista

**Opción C - Explorar el catálogo curado:**
1. Toca **Importar catálogo** (o descárgalo desde la pantalla de Extensiones)
2. Explora o busca por nombre, tema o idioma
3. Toca las estaciones para agregarlas a tu lista

---

## Paso 3 - Reproduce una Estación

- **Transmisión de audio (radio):** toca la fila - la reproducción comienza en línea. Aparece un mini-control fijo en la parte inferior mostrando el nombre de la estación e información ICY de la pista actual. La lista permanece totalmente interactiva.
- **Transmisión de video o RTSP:** toca la fila - se abre en el reproductor a pantalla completa. Presiona Atrás para volver a la lista; se conservan la posición de desplazamiento y la última estación seleccionada.

---

## Paso 4 - Mantén la Radio Reproduciéndose en Segundo Plano

Para mantener el audio reproduciéndose al cambiar de app o bloquear la pantalla:

1. Ve a **Configuración -> Medios -> Reproductor**
2. Busca el grupo **Reproducción de audio en segundo plano**
3. Activa **Reproducción de audio en segundo plano**

> **Al salir de la pantalla de Streams mientras una estación está reproduciéndose:** la app ofrece la misma opción de Detener / Seguir reproduciendo que el reproductor principal. Si la reproducción en segundo plano está DESACTIVADA, la transmisión se detiene al minimizar la pantalla.

---

## Paso 5 - Filtra y Organiza

- **Fija tus favoritas arriba:** mantén presionada una fila de estación -> Fijar. Las estaciones fijadas aparecen sobre el resto sin importar el orden de clasificación.
- **Filtra por categoría o idioma:** toca el botón Filtro (aparece un punto cuando un filtro está activo). El selector de idioma muestra banderas. Usa el interruptor Y/O para coincidir con todos o cualquiera de los filtros seleccionados.
- **Ordenar:** toca el botón de ordenar para organizar por nombre, tema, idioma o reproducidas recientemente.
- **Buscar:** escribe en la barra de búsqueda para filtrar por nombre en todas las estaciones.

---

## Paso 6 - Qué Hacer Si una Estación Está Caída

Si una transmisión no está disponible o fue redirigida, aparece un cuadro de diálogo con tres opciones:
- **Reintentar** - vuelve a intentar la transmisión
- **Eliminar** - la borra de tu lista
- **Cancelar** - descarta y conserva la entrada

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|-------------|
| La transmisión no se reproduce | Verifica que la URL sea correcta y que la estación esté en línea. Prueba Reintentar en el cuadro de diálogo de no disponibilidad |
| El audio se detiene al cambiar de app | Activa Reproducción de audio en segundo plano en Configuración -> Medios -> Reproductor |
| No hay entrada de Streams en el menú | La pantalla de Streams está ausente en las ediciones Lite y Photos. Usa Standard, Legacy, VR, o noLegal |
| La importación del catálogo se cuelga | El servidor del catálogo puede estar lento o fuera de línea. La importación expira automáticamente y muestra un error - revisa tu conexión y reintenta |
| No se muestran banderas en el filtro de idioma | Las banderas se muestran según la etiqueta de idioma en el catálogo de estaciones. Las estaciones agregadas manualmente sin etiqueta de idioma siempre son visibles bajo cualquier filtro de idioma |

</div>
