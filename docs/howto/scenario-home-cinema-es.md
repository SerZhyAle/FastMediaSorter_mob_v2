---
layout: default
title: "Cine en Casa y Streaming VR - FastMediaSorter v2"
permalink: /docs/howto/scenario-home-cinema-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 🍿 Cine en Casa y Streaming VR

> **Nivel:** Principiante &bull; **Tiempo:** ~15 minutos &bull; **Edición:** Standard, Legacy, VR, noLegal (Lite no tiene fuentes de red, Photos no tiene video)

{% include lang-switcher.html doc="scenario-home-cinema" dir="/docs/howto/" current="es" %}

Mira tu colección de series directamente desde tu PC de casa - en tu teléfono, tablet, o visor VR basado en Android (Meta Quest, Pico). Sin copiar archivos. Sin cables USB. Solo presiona reproducir.

> **¿Cómo funciona esto?** Tu teléfono y tu PC están en el mismo Wi-Fi de casa. La app se conecta a la carpeta compartida de tu PC y transmite el video directamente - igual que Netflix transmite desde sus servidores, pero usando tu propia red de casa. El archivo de video nunca se descarga a tu teléfono; se reproduce sobre la marcha.

---

## Lo Que Necesitarás

- Teléfono / tablet / visor VR en la misma **red Wi-Fi de casa** que tu PC
- Videos en tu **PC o NAS** (tu router con almacenamiento)
- FastMediaSorter instalado

---

## Paso 1 - Comparte tu Carpeta de Video en el PC

Primero, haz que la carpeta de video sea accesible en tu red de casa.

En **Windows:**
1. Abre el **Explorador de Archivos**, navega a tu carpeta de video (por ejemplo, `D:\Series`)
2. **Clic derecho** en la carpeta → **Propiedades** → pestaña **Compartir** → clic en **Compartir..**
3. En el menú desplegable elige **Todos** (o tu nombre de usuario) → clic en **Agregar** → clic en **Compartir**
4. Anota la dirección IP de tu PC - la necesitarás en el Paso 2

> **Cómo encontrar la IP de tu PC:** presiona **Win + R**, escribe `cmd`, presiona Enter. Escribe `ipconfig` y presiona Enter. Busca la línea **Dirección IPv4** bajo tu adaptador Wi-Fi. Ejemplo: `192.168.1.100`.

---

## Paso 2 - Agrega la Carpeta de Video en FastMediaSorter

1. Abre la app → toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Carpeta de red (SMB)"**
2. Toca **"Escanear Red"** - la app escanea tu red de casa en busca de PCs disponibles
3. Cuando tu PC aparezca en la lista, tócalo - la dirección se completa automáticamente
4. Ingresa el nombre del recurso compartido (el nombre de la carpeta de video), nombre de usuario y contraseña de Windows
5. Toca **Probar Conexión** → **Guardar**

> **¿No encontraste tu PC al escanear?** Ingresa la dirección manualmente: `\\192.168.1.100\Series` (reemplaza con tu IP y nombre de carpeta). Consulta la [Guía de Configuración de SMB](scenario-smb-setup-es.md) completa para todos los escenarios de conexión.


---

## Paso 3 - Abre la Carpeta de Video

Toca tu recurso recién agregado en la pantalla principal.

Tus carpetas de series y archivos de video aparecen como una cuadrícula con miniaturas - igual que explorar localmente.

![Carpeta de video SMB - archivos de episodios (MKV) listados por nombre de archivo](screenshots/screenshot-hc-step3.png)

---

## Paso 4 - Configura el Siguiente Episodio Automático

Para que el siguiente episodio comience automáticamente cuando uno termina - sin necesidad de elegir el siguiente manualmente:

1. Regresa a la pantalla principal → **mantén presionado** tu recurso de video → toca **Editar**
2. Establece **Tipos Compatibles** → **Solo video** (oculta archivos que no sean de video)
3. Establece **Modo de orden** → **Nombre (A→Z)** - esto asegura que los episodios se reproduzcan en orden (Episodio 1, 2, 3..)
4. Toca **Guardar**

Luego inicia la presentación en el reproductor (el comando **Presentación**) y activa **Configuración → Reproductor → Reproducir video/audio en presentación hasta el final** - cada episodio se reproducirá entonces hasta el final antes de que comience el siguiente.

> **¿Por qué ordenar por nombre?** Los archivos de episodios suelen nombrarse `S01E01`, `S01E02`, etc. Ordenar por nombre los coloca automáticamente en el orden correcto de episodios.

---

## Paso 5 - Comienza a Ver

1. Abre la carpeta, navega dentro de la subcarpeta de la serie
2. Toca **Episodio 1** - el reproductor de video se abre inmediatamente y comienza a transmitir
3. El video se reproduce por Wi-Fi - sin esperar descargas

![Reproductor de video a pantalla completa - episodio reproduciéndose con barra de progreso](screenshots/screenshot-hc-step5.png)

---

## Paso 6 - Controles Mientras Ves

**Gestos táctiles durante la reproducción:**
- **Deslizar a la izquierda** → saltar al siguiente episodio
- **Deslizar a la derecha** → volver al episodio anterior
- **Tocar la pantalla** → mostrar / ocultar controles
- **Pellizcar** → acercar o alejar (útil para películas panorámicas en un teléfono en vertical)
- **Doble toque en el borde izquierdo / derecho** → retroceder / adelantar 10 segundos

Cuando **"Siguiente automático"** está activado, el siguiente episodio comienza automáticamente cuando termina el actual - igual que Netflix.

---

## Paso 7 - Para Visores VR (Meta Quest, Pico)

> **Esta sección es para personas con un visor VR (como Meta Quest 2/3 o Pico 4).** Si no tienes uno, omite este paso.

Los visores VR basados en Android pueden ejecutar FastMediaSorter. Instálalo mediante sideloading:
1. Descarga el APK desde la [página de Descargas](../DOWNLOADS.md)
2. En tu visor, activa **"Instalar desde orígenes desconocidos"** en la configuración de Desarrollador
3. Instala el APK usando SideQuest o directamente vía ADB

Una vez instalado, el reproductor de video funciona exactamente igual:
- El video llena la **pantalla plana virtual** dentro del visor
- Usa el **gatillo del control** para tocar botones
- Usa el **joystick** para deslizarte entre episodios (si tu visor mapea los botones multimedia)
- Para películas y series 2D normales - funciona de inmediato, sin configuración adicional

> **Para experiencia de cine VR:** puedes usar una app dedicada de cine VR como launcher, y luego elegir "Abrir con FastMediaSorter" para la gestión de archivos. FastMediaSorter maneja la exploración de archivos; la app de cine VR maneja la visualización inmersiva en 360°.

---

## ¡Listo! Qué Probar Después

- Agrega un recurso de **Google Drive** o **Dropbox** para películas almacenadas en la nube - funciona de la misma manera
- Usa **Favoritos** (toca el botón de estrella <img src="../icons/doc/ic_star_filled.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> mientras ves) para marcar tu serie "en curso" - vuelve a ella en cualquier momento
- **Subtítulos:** si tu carpeta de video tiene archivos de subtítulos `.srt` coincidentes junto a los archivos de video, toca el **botón CC / subtítulos** en la barra de herramientas del reproductor para activarlos
- **Radio por internet o transmisiones en vivo:** si también quieres agregar estaciones de radio por internet o fuentes RTSP/HLS, consulta la guía de [Radio por Internet y Transmisiones](scenario-internet-radio-es.md) - no se necesita NAS ni PC, solo una conexión de red.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| El video se entrecorta o se almacena en búfer | Ejecuta una **Prueba de Velocidad**: mantén presionado el recurso → Editar → Prueba de Velocidad. Si la velocidad está por debajo de 5 Mbps, prueba cambiar tu teléfono a la **banda Wi-Fi de 5 GHz** (más rápida, pero de menor alcance) |
| El video no se reproduce (error de formato) | En el reproductor, toca **Opciones <img src="../icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → cambia el **Decodificador** de Hardware a Software (más lento pero más compatible) |
| Los episodios se reproducen en orden incorrecto | Asegúrate de que el Modo de orden esté establecido en **Nombre (A→Z)** en la configuración de Editar carpeta |
| El siguiente automático no comienza | Asegúrate de que la presentación esté en marcha y que Configuración → Reproductor → Reproducir video/audio en presentación hasta el final esté activado |
| El visor VR no puede instalar el APK | Abre la configuración de Desarrollador del visor y activa "Permitir instalaciones desde orígenes desconocidos" |

</div>
