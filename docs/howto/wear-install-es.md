---
layout: default
title: "Instala FastMedia en tu Reloj - FastMediaSorter v2"
permalink: /docs/howto/wear-install-es.html
---
<div lang="es" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Instala FastMedia en tu Reloj

> **Nivel:** Principiante &bull; **Tiempo:** ~5 minutos &bull; **Dispositivo:** Smartwatch Wear OS emparejado con un teléfono Android

> **Dos versiones.** La versión de Google Play es un pequeño primer lanzamiento: Calculadora, Cronómetro, Mini-juego, Configuración y el tile de Programas. Música, fotos, recursos de red y funciones del teléfono están solo en la versión completa, una descarga directa de APK desde [Descargas](../DOWNLOADS.md).

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="es" %}

FastMedia Wear es la mitad para el reloj de FastMediaSorter. Una vez que está en tu muñeca puedes reproducir música y ver fotos directamente desde el reloj, acceder a carpetas compartidas por tu teléfono emparejado, y abrir recursos de red a los que el reloj se conecta por su cuenta. Esta página lo instala y lo empareja.

---

## Lo Que Necesitarás

- Un smartwatch con **Wear OS 3.0** o más nuevo
- Un teléfono Android con FastMediaSorter instalado y el reloj ya emparejado con él en la configuración del sistema
- Una conexión Wi-Fi o de datos móviles en el reloj, o en el teléfono con el que está emparejado, para la descarga

---

## Paso 1 - Instala FastMedia Wear en el Reloj

1. En el reloj, abre la lista de apps y toca **Play Store**.
2. Busca **FastMedia Wear**.
3. Toca **Instalar** y espera a que termine la descarga. El reloj mostrará la app en su lista de apps cuando termine.

> Los relojes varían en cuánto te permiten escribir. Si buscar en la muñeca es incómodo, abre la Play Store en tu teléfono, busca FastMedia Wear, y elige tu reloj como destino de instalación - el reloj lo descarga por sí solo.

### ¿Sin Play Store? Instala un APK a través de ADB

Usa esta ruta cuando tu reloj no tiene acceso a la Play Store. Necesitas una computadora con las
Android SDK Platform-Tools (`adb`) y una red Wi-Fi local compartida por la computadora y el reloj. No
funciona solo por internet.

1. Descarga un APK desde la página de [Descarga Directa de APK](../DOWNLOADS.md):
   - `FastMediaSorter_wear_debug.apk` es la compilación de depuración para pruebas. Se instala como
     `com.sza.fastmediasorter.debug`.
   - `FastMediaSorter_wear_release.apk` es la compilación firmada sin depuración. Se instala como
     `com.sza.fastmediasorter`.
   - Las dos compilaciones tienen nombres de paquete diferentes, así que pueden quedar instaladas una junto a la otra. No
     intentes instalar un archivo `.aab` de la Play Store con ADB.
2. En el reloj, activa el modo desarrollador: **Configuración** → **Acerca del reloj** → toca **Número de compilación** siete
   veces. En **Opciones de desarrollador**, activa **Depuración ADB** y **Depuración inalámbrica**.
3. En **Depuración inalámbrica**, elige **Emparejar nuevo dispositivo**. En la computadora, ingresa la dirección de emparejamiento
   y el código que muestra el reloj, luego conéctate con el puerto de conexión separado de la pantalla principal
   de Depuración inalámbrica:

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Acepta la solicitud de depuración en el reloj. Los puertos de emparejamiento y conexión son diferentes.
4. Instala o actualiza el APK. Usa el comando que corresponda al archivo que descargaste:

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` actualiza el mismo paquete manteniendo sus datos de app. No convierte una compilación de depuración en
   una compilación de lanzamiento, porque son apps separadas.
5. Abre **FastMedia Wear** desde la lista de apps del reloj. Si es necesario, inícialo desde ADB:

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> Este método requiere un reloj Wear OS. Galaxy Watch 3, Galaxy Watch Active y Active 2 ejecutan Tizen
> y no pueden instalar APKs de Wear OS. Cuando termines, desactiva la Depuración inalámbrica a menos que la necesites para
> otra actualización.

---

## Paso 2 - Activa el Companion de Wear en el Teléfono

El lado del teléfono está apagado hasta que indiques que tienes un reloj.

1. Abre FastMediaSorter en el teléfono.
2. Ve a **Configuración** y abre la pestaña **Gestión**.
3. Busca el grupo **Wear OS** y expándelo.
4. Activa la casilla **Companion de Wear**.

La casilla activa todo el companion: el botón que abre su ventana aparece justo debajo, una entrada para él se une a la lista de programas, y queda disponible como tile de panel y acceso directo del launcher.

> Las compilaciones sin el puente hacia el reloj no muestran este grupo en absoluto. Si no lo encuentras, estás ejecutando una edición que se distribuye sin soporte para Wear.

---

## Paso 3 - Elige Qué Viaja al Reloj

1. En el mismo grupo, toca **Companion de Wear**. Su ventana se abre sobre la app.
2. Elige los recursos que quieres que el reloj vea. Nada se envía hasta que elijas - una selección vacía no envía nada en lugar de empujar toda tu biblioteca.
3. Ajusta también las propias preferencias del reloj aquí: modo de vista, comportamiento de mantener despierto y las secciones que se muestran en la pantalla principal del reloj.

---

## Paso 4 - Verifica Que Ambas Mitades Se Vean Entre Sí

1. Abre **FastMedia Wear** en el reloj.
2. La pantalla principal lista sus secciones - **Teléfono**, **Local**, **Recursos**, **Streams** y **Apps**.
3. Toca **Teléfono**. Aparecen las carpetas que seleccionaste en el Paso 3.

Si la sección Teléfono está vacía, vuelve a la ventana del companion en el teléfono y confirma que al menos un recurso esté seleccionado.

> **Consejo:** Puedes navegar hacia atrás desde cualquier pantalla en tu reloj usando el botón visible de retroceso universal en el borde izquierdo, deslizando desde el borde izquierdo, o presionando el botón físico de retroceso de tu reloj. En la pantalla principal, tocar el botón de retroceso muestra un ícono de salida (una flecha saliendo de una caja) para salir de la app o un doble chevron («) para minimizar la reproducción en segundo plano. En cada pantalla que muestra ese botón, un botón de pantalla negra (un teléfono con pantalla oscura) lo enfrenta en el borde derecho y apaga la pantalla del reloj; un doble toque, una pulsación prolongada, o el botón físico lo trae de vuelta.

---

## Si Algo No Funciona

- **La app del reloj no aparece en la Play Store.** Confirma que el reloj ejecuta Wear OS 3.0 o más nuevo. Los relojes más antiguos usan un modelo de app diferente y no son compatibles.
- **Falta el grupo Wear OS en la configuración del teléfono.** La compilación que estás ejecutando no lleva el puente hacia el reloj.
- **La sección Teléfono en el reloj está vacía.** No hay nada seleccionado en la ventana del companion, o el reloj y el teléfono perdieron su emparejamiento - revisa primero el emparejamiento en la configuración del sistema.
- **La reproducción se entrecorta en la conexión por teléfono.** El Bluetooth entre el reloj y el teléfono es limitado. Para escuchar durante mucho tiempo, transfiere los archivos al reloj o conecta el reloj directamente a un recurso de red.

---

## A Dónde Ir Después

- [Música en el Smartwatch](scenario-watch-music-es.md) - reproduce tu colección en el reloj, con carátula, aleatorio y volumen por bisel.
- [Conectar el Reloj a Recursos de Red](scenario-watch-network-es.md) - accede a un NAS o un recurso del PC desde el reloj por Wi-Fi, sin el teléfono.

</div>
