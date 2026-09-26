---
layout: default
title: "Abre las Carpetas de tu PC Escaneando un Código - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-es.html
---
<div lang="es" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Abre las Carpetas de tu PC Escaneando un Código

> **Nivel:** Principiante &bull; **Edición:** Standard, Photos, Legacy, VR, noLegal (Lite no tiene fuentes de red; escanear necesita una cámara, el método de archivo funciona en todas partes)

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="es" %}

Ejecutas un pequeño programa auxiliar en tu PC con Windows, eliges las carpetas con tus videos, música, documentos o fotos, y muestra un código en pantalla. En el teléfono tocas **Agregar**, apuntas la cámara a ese código, y las carpetas del PC se conectan al instante - sin escribir una dirección, sin puerto, sin contraseña, sin cables.

> **Explicación en lenguaje simple:** El auxiliar de Windows convierte tus carpetas elegidas en un recurso compartido privado y de solo lectura en tu Wi-Fi de casa e imprime un código que ya contiene todo lo que el teléfono necesita para acceder a ellas. Escanear ese código es lo mismo que llenar un formulario de conexión largo a mano - el teléfono simplemente lo lee de un vistazo. Los archivos luego se abren bajo demanda, transmitidos por Wi-Fi; nada se copia al teléfono hasta que lo pides.

---

## El Programa Auxiliar

El "companion" es una función integrada de **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (antes FastMediaSorter LITE) - el clasificador de medios gratuito para Windows del mismo autor. Cuando compartes carpetas con él, hace lo siguiente:

- Inicia un servidor SFTP privado solo para esas carpetas en tu PC.
- Genera sus propias claves y configura el inicio automático, para que el recurso compartido esté ahí la próxima vez también.
- Muestra un **código QR** en pantalla y también puede guardar un pequeño archivo de configuración `.fmscfg`.

**Dónde conseguirlo:**

- Sitio web: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publicar carpetas (paso a paso): [Cómo publicar carpetas del PC en Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [última versión](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (instalador o ZIP portátil)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: busca "FastMediaSorter LITE" (todavía aparece bajo el nombre anterior)

---

## Lo Que Necesitarás

- Una PC con Windows con **Fast Media Sorter for Windows** instalado
- Tu teléfono y PC en la **misma red Wi-Fi** (mismo router)
- Para la ruta más rápida: una **cámara** en el teléfono para escanear el código (hay una ruta basada en archivo disponible si no la tienes)

---

## Paso 1 - Comparte las Carpetas en el PC

1. Instala y ejecuta **Fast Media Sorter for Windows**, luego abre la pestaña **Compartir** en la configuración.
2. Elige la(s) carpeta(s) que quieres en el teléfono - Películas, Música, Documentos, Fotos, lo que sea.
3. La app inicia el servidor SFTP, genera las claves y configura el inicio automático por su cuenta. Nada más que configurar.
4. Ahora muestra un **código QR** en la pantalla del PC. Deja esa ventana abierta para el Paso 2.

> ¿Prefieres un archivo en lugar de un código? Usa **Guardar .fmscfg** en la misma ventana y envía ese archivo al teléfono (correo, Telegram, o cualquier carpeta compartida). Consulta [Paso 2, Método B](#step-2-method-b---import-the-file).

---

## Paso 2, Método A - Escanear el Código (más rápido)

1. Abre FastMediaSorter y toca el botón **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la pantalla principal.
2. Toca **"Importar por código de barras"** - está junto a las cuatro tarjetas de tipo de recurso (Local, SMB, SFTP/FTP, Nube) y en el encabezado del formulario SFTP.
3. Se abre la cámara con la indicación *"Apunta la cámara al código QR del companion"*. Sostén el teléfono frente al QR de tu PC. En una habitación oscura, toca **Linterna**.
4. Aparece una confirmación - *"Importar acceso - ¿Agregar el recurso SFTP .. con N carpeta(s)?"*. Toca **Importar**.
5. Listo. Aparece un recurso de solo lectura por cada carpeta compartida en la pantalla principal, con la clave del servidor fijada automáticamente.

> La entrada **Importar por código de barras** está oculta en dispositivos sin cámara y en visores VR - usa el Método B en esos casos.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Paso 2, Método B - Importar el Archivo {#step-2-method-b---import-the-file}

Usa esto cuando el teléfono no tiene cámara, o cuando la PC y el teléfono no están uno al lado del otro.

1. En la PC, usa **Guardar .fmscfg** y lleva el archivo al teléfono (correo, Telegram, nube, o una carpeta compartida).
2. **Si el archivo ya está en el teléfono:** toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **"SFTP / FTP"** -> **"Importar desde archivo"**, luego elige el archivo `.fmscfg`.
3. **Si lo recibiste como adjunto** (Telegram o correo): simplemente toca el adjunto `.fmscfg` - la app abre un cuadro de diálogo de confirmación directamente.
4. Confirma el mismo cuadro de diálogo *"Importar acceso"* y toca **Importar**. Los recursos de solo lectura aparecen.

> **Trata el código y el archivo como una llave.** Ambos incorporan la contraseña de acceso para que el teléfono pueda conectarse sin escribir nada. No publiques la captura del código QR ni el archivo `.fmscfg` públicamente.

---

## ¡Listo! Ahora Puedes..

Las carpetas compartidas se comportan como cualquier otro recurso en la app. Por ejemplo:

- **Ver películas y series** desde el PC en tu teléfono, tablet o dispositivo Android TV - transmitido, sin copiar nada. Consulta [Cine en Casa y Streaming VR](scenario-home-cinema-es.md).
- **Reproducir tu biblioteca de música** sobre la marcha o en una unidad central de coche.
- **Leer PDFs y EPUBs** almacenados en el PC, conservando tu última posición.
- **Explorar un archivo de fotos** y clasificarlo con Clasificación Rápida, o mostrarlo como un [marco de fotos digital](scenario-photo-frame-es.md).
- **Entregar un archivo a una app especializada** - abre la hoja de Información de un archivo de red y toca Descargar y Abrir.
- **Copiar o mover archivos** entre el PC y el teléfono en cualquier dirección.

---

## Cómo Funciona (por dentro)

- El auxiliar de Windows ejecuta un **servidor SFTP** ligero vinculado a las carpetas que elegiste, solo en tu red local.
- El código QR (o el archivo `.fmscfg`) codifica la conexión: host, puerto, credencial, las rutas de las carpetas compartidas y la huella digital de la clave del servidor. Los recursos compartidos densos se envían comprimidos, así que incluso muchas carpetas caben en un solo código.
- El teléfono lee esa información, la verifica y crea un **recurso SFTP de solo lectura por carpeta**. El código también lleva la huella digital de la clave del servidor de tu PC, y el teléfono la verifica en cada conexión - al explorar, copiar, generar miniaturas y reproducir. Si otra computadora alguna vez responde en lugar de tu PC, el teléfono no carga nada y te dice que el servidor se ve diferente.
- Como es tu Wi-Fi local y es de solo lectura, el teléfono explora y transmite los archivos sin cambiar nada en el PC.
- **En el mismo Wi-Fi, el teléfono encuentra el PC por sí solo.** El companion anuncia el recurso compartido en la red local, y el teléfono lo identifica por la clave fijada - así que incluso si la dirección del PC en la red cambia, el recurso sigue funcionando sin volver a escanear.
- **Una sola importación puede funcionar en casa y fuera de casa.** El código puede llevar más de una dirección - la local, una IPv6, y un reenvío de puerto a internet. El teléfono las prueba y usa la que esté disponible en ese momento: la dirección local en casa, la de internet con datos móviles. El mismo recurso sigue funcionando mientras te mueves entre redes, siempre que el PC sea realmente accesible desde donde estás.
- **Si no puede conectarse, la app explica qué hacer** - conectarte a la misma Wi-Fi, o configurar el acceso en el PC - en lugar de un error genérico. Cuando el companion incluye una nota sobre el acceso, el teléfono la muestra.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| No hay entrada de "Importar por código de barras" | El dispositivo no tiene cámara, o es una compilación VR. Usa el [Método B - Importar el Archivo](#step-2-method-b---import-the-file) |
| La cámara dice que se necesita acceso | Otorga el permiso de cámara cuando se solicite - se usa solo para el escaneo |
| "Este archivo no es una configuración de companion válida" | El código o archivo no proviene del companion de Windows. Vuelve a exportarlo desde la pestaña **Compartir** |
| "Creado por una versión más nueva del companion" | Actualiza FastMediaSorter en el teléfono, o vuelve a exportar desde una versión de companion compatible |
| Recurso agregado pero las carpetas están vacías | Asegúrate de que el auxiliar del PC siga ejecutándose. En la **misma Wi-Fi** la app encuentra el PC por sí sola; si aun así falla, la app muestra qué revisar |
| Funciona en Wi-Fi pero no con datos móviles | Para acceder al PC desde otra red, debe ser accesible desde internet - configura el reenvío de puertos o IPv6 en la configuración **Compartir** del companion. Sin eso, el recurso compartido funciona solo en la misma Wi-Fi |

→ Más ayuda: [TROUBLESHOOTING-es.md](../TROUBLESHOOTING-es.md) &bull; Fundamentos: [Conectar a NAS / Recurso Compartido de Windows (SMB)](scenario-smb-setup-es.md)

</div>
