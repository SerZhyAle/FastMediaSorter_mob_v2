---
layout: default
title: "Marco de Fotos Digital en Tablet - FastMediaSorter v2"
permalink: /docs/howto/scenario-photo-frame-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 🖼️ Marco de Fotos Digital en Tablet

> **Nivel:** Principiante &bull; **Tiempo:** ~15 minutos &bull; **Edición:** Standard, Photos, Legacy, VR, noLegal (para fotos en NAS/nube) o cualquier edición (para fotos locales)

{% include lang-switcher.html doc="scenario-photo-frame" dir="/docs/howto/" current="es" %}

Convierte cualquier tablet Android en un hermoso marco de fotos digital siempre encendido - transmitiendo tus recuerdos desde un NAS de casa o la nube, con música de fondo opcional. Cero almacenamiento local usado.

> **La idea en una sola frase:** apoya una tablet vieja, enchúfala, inicia una presentación - muestra tus fotos automáticamente, para siempre, cambiando cada pocos segundos. Como un verdadero marco de fotos digital de tienda, pero impulsado por tu propia colección de fotos desde cualquier origen.

---

## Lo Que Necesitarás

- Una tablet Android (de cualquier tamaño - ¡una vieja funciona genial!)
- Un soporte o montaje para mantener la tablet en posición vertical
- Un **cargador USB** para mantenerla enchufada - la tablet funcionará todo el día, así que la batería no es suficiente
- Tus fotos en uno de: **almacenamiento local**, **PC/NAS de casa vía SMB**, o **Google Drive / Dropbox**
- (Opcional) Una fuente de música para audio de fondo

---

## Paso 1 - Agrega tu Fuente de Fotos

Elige dónde viven tus fotos:

**Opción A - Fotos locales (en la propia tablet):**
1. Toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Carpeta Local** → navega a tu carpeta de fotos → **Seleccionar**

**Opción B - NAS de casa / PC con Windows (SMB):**
1. Toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Carpeta de red (SMB)**
2. Toca **"Escanear Red"** → selecciona tu PC/NAS de la lista
3. Completa nombre de recurso compartido + usuario + contraseña
4. Toca **Probar Conexión** → **Guardar**

> Configuración SMB completa: [Conectar a NAS (SMB)](scenario-smb-setup-es.md). Esto toma ~5 minutos configurarlo una vez, luego funciona para siempre.

**Opción C - Google Drive / Dropbox:**
1. Toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Almacenamiento en la Nube** → elige el proveedor
2. Toca **Iniciar sesión** → completa la autenticación en el navegador
3. Selecciona la carpeta con tus fotos → **Listo**

![Pantalla principal de FastMediaSorter - tarjetas de recurso de fotos visibles después de agregar una carpeta de fotos](screenshots/screenshot-pf-step1.png)

---

## Paso 2 - Configura la Carpeta para la Presentación

Mantén presionada tu carpeta de fotos en la pantalla principal → toca **Editar (ícono de lápiz)**.

Establece estas opciones:

| Configuración | Valor recomendado | Por qué |
|---------|------------------|-----|
| **Intervalo de Presentación** | 5-10 segundos | 5 s = sensación de álbum familiar animado; 10 s = tranquilo, bueno para fotos de arte o grupos grandes donde quieres tiempo para reconocer a todos |
| **Incluir Subcarpetas** | ACTIVADO | Muestra fotos de todas las subcarpetas - genial si organizas por año/álbum |
| **Modo de orden** | Fecha de Captura (más recientes primero) o Aleatorio | Aleatorio = más variedad diaria; Fecha = las fotos más recientes aparecen primero |
| **Tipos Compatibles** | Solo imágenes | Elimina Video y Audio - de lo contrario los archivos de video también se reproducirán, interrumpiendo el flujo de la presentación |

Toca **Guardar**.

![Editar Recurso - configuración de Intervalo de Presentación e Incluir Subcarpetas](screenshots/screenshot-pf-step2.png)

---

## Paso 3 - (Opcional) Agrega Música de Fondo

¿Quieres música suave mientras ves las fotos? Así se hace (necesita una edición con audio - la edición Photos no tiene ninguno):

1. Primero, agrega una fuente de música: toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → Carpeta Local → navega a tu carpeta de música
2. Ve a **Configuración → pestaña Medios → Reproducción de audio, carátulas y visuales**
3. Activa **"Mostrar fotos aleatorias durante la reproducción de audio"**

Luego ve a **Configuración → pestaña Medios → Imágenes, GIFs y presentación**:
4. Activa **"Reproducir música durante la presentación"**
5. Toca **"Seleccionar Fuente de Música"** → elige tu recurso de música

> **Consejo:** Si la música se entrecorta cuando las fotos vienen de un NAS, usa una carpeta de música local para el audio y deja que solo las fotos se transmitan desde la red - puedes mezclar fuentes libremente de esta manera.


---

## Paso 4 - Inicia la Presentación

1. Toca tu **carpeta de fotos** en la pantalla principal para abrirla
2. Toca **cualquier foto** para abrir el visor a pantalla completa
3. Toca **"Presentación" <img src="../icons/doc/ic_slideshow.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas superior

Eso es todo - la presentación se ejecuta. Las fotos avanzan automáticamente al intervalo que configuraste.

> **Inicio rápido alternativo:** Toca la **zona inferior derecha** de la pantalla de foto (la pantalla está dividida en una cuadrícula 3×3 de zonas táctiles invisibles; inferior derecha = zona 9 = REPRODUCIR).


---

## Paso 5 - Mantén la Pantalla Encendida

**Este paso es crítico.** Android ahorra batería apagando la pantalla después de unos minutos - lo cual arruinaría el marco de fotos. Necesitas desactivar esto.

**Opción A - Configuración dentro de la app (recomendado):**
Ve a **Configuración → Gestión → Evitar suspensión** y actívalo.

Esto le dice a Android que mantenga la pantalla encendida mientras la app se ejecute en primer plano. En el momento en que cambies de app o la presentación se detenga, vuelve el tiempo de espera normal de la pantalla.

![Configuración, pestaña Gestión - alternador Evitar suspensión activado](screenshots/screenshot-pf-step5.png)

**Opción B - Configuración del sistema Android:**
Configuración de Android → Pantalla → Tiempo de espera de pantalla → establece en **"Nunca"** (o el máximo).

> **Además:** Mantén la tablet **enchufada a la corriente USB** en todo momento. Una tablet ejecutando una presentación todo el día agotará su batería para la noche. Solo usa el cargador original y déjalo conectado.

---

## Paso 6 - (Opcional) Agrega un Widget de Pantalla de Inicio

Este paso es por conveniencia: ¿quieres iniciar el marco de fotos instantáneamente cuando tomas la tablet - sin abrir la app y navegar?

1. Mantén presionada tu pantalla de inicio → toca **Widgets**
2. Busca **FastMediaSorter** en la lista de widgets
3. Arrastra el widget **"Acceso Directo a Recurso"** a tu pantalla de inicio
4. Cuando se te solicite, selecciona tu recurso de fotos
5. Toca el widget en cualquier momento → la presentación se inicia al instante

![Pantalla de inicio de Android con widgets de acceso directo a recursos de FastMediaSorter colocados](screenshots/screenshot-pf-step6.png)

---

## ¡Listo! Tu Marco de Fotos Está Funcionando

**Controles mientras la presentación se reproduce:**
- **Tocar la pantalla** → pausar / mostrar controles
- **Deslizar a la izquierda / derecha** → saltar a la foto siguiente / anterior manualmente
- **Tocar la zona inferior derecha** → detener la presentación y volver a la lista de archivos

---

## Consejos

> **¿Las fotos del NAS no se actualizan después de agregar nuevas?** La app guarda en caché la lista de archivos por velocidad. Para actualizar: vuelve a la carpeta → toca el botón **Actualizar <img src="../icons/doc/ic_refresh.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas. Las fotos nuevas aparecen de inmediato.

> **¿Las fotos se ven ampliadas o recortadas?** Abre Configuración → Medios → Imágenes → **"Recortar imágenes para llenar la pantalla"** y prueba ambas posiciones: DESACTIVADO mantiene la foto completa visible, ACTIVADO llena la pantalla de borde a borde (ligero recorte en los lados).

> **¿Teléfono en vertical usado como marco?** Activa "Recortar imágenes para llenar la pantalla" para evitar barras negras en fotos horizontales.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| La pantalla se oscurece después de unos minutos | Activa "Evitar suspensión" en Configuración → Gestión (Paso 5) **y** enchufa el cargador USB |
| No se muestran las fotos | Abre la configuración de la carpeta (Paso 2) y asegúrate de que **Imágenes** esté marcado en **Tipos Compatibles** |
| La música no se reproduce | Verifica que la carpeta de música contenga al menos un archivo de audio; revisa que **Reproducir música durante la presentación** esté activado en Configuración → Medios → Imágenes, GIFs y presentación |
| La presentación se pausa en archivos de video | Es lo esperado - los videos se reproducen, luego la presentación se reanuda. Establece "Tipos Compatibles → Solo imágenes" en la configuración de la carpeta (Paso 2) para evitar esto |
| Las fotos por SMB cargan lentamente | Edita la carpeta → desactiva "Cargar miniaturas" para reducir la carga de red. O reduce el intervalo de presentación para dar más tiempo de carga |
| Las fotos se repiten demasiado rápido | Aumenta el intervalo de presentación en la configuración de la carpeta (Paso 2) |

</div>
