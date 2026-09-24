---
layout: default
title: "🔧 Guía de solución de problemas"
permalink: /docs/TROUBLESHOOTING-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 🔧 Guía de solución de problemas

Guía de solución de problemas actual para FastMediaSorter v2. Usa la cuadrícula canónica de variantes en [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) cuando el problema dependa de la ruta de compilación elegida (Standard, Lite, Photos, Legacy o XR / noLegal).

{% include lang-switcher.html doc="TROUBLESHOOTING" dir="/docs/" current="es" %}

---

## Problemas de conexión

### ❌ "No se puede conectar al servidor SMB"

**Posibles causas:**
1. **Red equivocada** - el teléfono debe estar en la misma Wi-Fi que el NAS
2. **Formato de dirección incorrecto** - prueba ambos formatos:
   - `\\192.168.1.100\share`
   - `smb://192.168.1.100/share`
3. **Cortafuegos bloqueando** - revisa los ajustes del cortafuegos del NAS
4. **Incompatibilidad de versión SMB** - algunos NAS aún requieren compatibilidad con SMB v2/v3; actualiza el servidor si solo expone ajustes SMB antiguos

**Solución:**
- Prueba primero la conexión desde el PC
- Revisa los registros del NAS en busca de intentos de conexión
- Prueba con la dirección IP en lugar del nombre de host
- Verifica el usuario y la contraseña

---

### ❌ "Tiempo de espera agotado en la conexión SFTP"

**Posibles causas:**
1. Puerto incorrecto (por defecto: 22)
2. El servidor SSH no está en ejecución
3. Cortafuegos bloqueando

**Solución:**
```
1. Prueba primero con un cliente SSH en el PC:
   ssh username@192.168.1.100
2. Comprueba si el servicio SSH está en ejecución
3. Verifica el puerto en Ajustes
```

---

### ❌ "Fallo al iniciar sesión en Google Drive"

**Solución:**
1. Borra los datos de la app: Ajustes → Aplicaciones → FastMediaSorter → Borrar datos
2. Reinstala la app
3. Revisa los ajustes de tu cuenta de Google → Seguridad → Apps de terceros

---

### ❌ "Fallo al iniciar sesión en OneDrive"

**Solución:**
1. Comprueba el estado de tu cuenta de Microsoft
2. Borra los datos de la app: Ajustes → Aplicaciones → FastMediaSorter → Borrar datos
3. Revisa los ajustes de tu cuenta de Microsoft → Privacidad → Apps y servicios

---

### ❌ "Fallo al iniciar sesión en Dropbox"

**Solución:**
1. Comprueba el estado de tu cuenta de Dropbox
2. Borra los datos de la app: Ajustes → Aplicaciones → FastMediaSorter → Borrar datos
3. Revisa los ajustes de tu cuenta de Dropbox → Seguridad → Apps conectadas

---

## Problemas de rendimiento

### ❌ "La app va lenta / con tirones"

**Para carpetas grandes (más de 5000 archivos):**
1. **Editar carpeta** (por recurso) → activa **"Desactivar miniaturas"**
2. Usa **filtros** para reducir los archivos visibles
3. Cierra otras apps para liberar RAM

**Para carpetas de red:**
1. Comprueba la intensidad de la señal Wi-Fi
2. Reduce el tamaño de la caché de miniaturas
3. Activa **"Escanear subcarpetas"** = DESACTIVADO si no lo necesitas

---

### ❌ "Las miniaturas no cargan"

**Archivos locales:**
- Comprueba los permisos de almacenamiento
- Borra la caché de miniaturas
- Reinicia la app

**Archivos de red:**
- Desplázate más despacio (las miniaturas cargan bajo demanda)
- Comprueba la velocidad de la red
- Aumenta el tamaño de la caché en Ajustes

---

## Errores en operaciones de archivo

### ❌ "Copia fallida: permiso denegado"

**Archivos locales:**
- Concede los permisos de almacenamiento: Ajustes → Aplicaciones → Permisos
- Comprueba si la carpeta es de solo lectura
- Prueba a moverlo a otra ubicación

**Archivos de red:**
- Comprueba que el usuario tenga permisos de escritura
- Verifica los ajustes del recurso compartido en el NAS

---

### ❌ "No se puede eliminar el archivo"

**Posibles causas:**
1. El archivo está abierto en otra app
2. No hay permiso de escritura
3. El archivo está protegido por el sistema

**Solución:**
- Cierra otras apps
- Comprueba los permisos de la carpeta
- Para redes: verifica que el usuario tenga permisos de eliminación

---

### ❌ "Fallo en la operación de mover"

**Movimientos entre protocolos** (por ejemplo, Local → SMB):
- En realidad son **copiar + eliminar**
- Requiere espacio libre en el destino
- Puede tardar más con archivos grandes

**Solución:**
- Comprueba el espacio disponible
- Usa Copiar en lugar de Mover por seguridad
- Espera a que la operación termine por completo

---

## Bloqueos de la app

### ❌ "La app se bloquea al abrir el reproductor"

**Causas comunes:**
1. Archivo de vídeo corrupto
2. Códec no compatible
3. Archivo demasiado grande (>4 GB)

**Solución:**
- Prueba a reproducir el archivo en otra app para verificarlo
- Comprueba el formato del archivo (compatibles: MP4, MKV, MOV)
- Borra la caché de la app

---

### ❌ "El archivo multimedia no se reproduce o no hay sonido"

**Problema:** el vídeo carga pero muestra pantalla negra, o se reproduce sin sonido.

**Solución:**
1. Toca el botón **ⓘ (Info)** en la barra de herramientas superior
2. Toca **"Abrir en reproductor externo"**
3. Selecciona un reproductor especializado (por ejemplo, VLC, MX Player)

Esto usa la función *Reproductor secundario* para delegar los códecs no compatibles en otras apps.

---

### ❌ "La app se bloquea al iniciarse"

**Solución:**
1. Borra la caché de la app: Ajustes → Aplicaciones → FastMediaSorter → Borrar caché
2. Si persiste: borra los datos de la app (⚠️ se pierden los ajustes)
3. Reinstala la app como último recurso

---

## Problemas de interfaz / visualización

### ❌ "Las zonas táctiles no funcionan"

**Comprueba si están activadas:**
Ajustes → Reproductor → **"Mostrar aviso de zonas táctiles la primera vez"** = ACTIVADO

**Hacerlas visibles:**
Ajustes → Reproductor → **"Mostrar siempre la superposición de zonas táctiles"** = ACTIVADO

---

### ❌ "Los botones del panel de comandos son demasiado pequeños"

**Solución:**
Ajustes → Reproductor → **"Botones compactos del reproductor"** = DESACTIVADO

Esto duplica el tamaño de todos los botones y sus espacios.

---

### ❌ "El tema oscuro no funciona"

La app sigue el **tema del sistema**:
- Ajustes de Android → Pantalla → Tema oscuro = ACTIVADO

---

## Problemas de datos

### ❌ "Han desaparecido los favoritos"

Los favoritos se guardan **localmente**:
- ¿Borraste los datos de la app? → se pierden los favoritos
- ¿Dispositivo nuevo? → hay que volver a marcarlos

**Prevención:**
- Usa **Ajustes → General → Copias de seguridad, restauración y exportación de ajustes**
- Los favoritos son locales al dispositivo; si cambias de teléfono, vuelve a marcarlos o usa el flujo de copia de seguridad/restauración de la app disponible en tu compilación

---

### ❌ "La carpeta de papelera no deja de crecer"

Los archivos eliminados van a la carpeta `.trash/` y permanecen ahí hasta que se vacían manualmente.

**Solución:**
1. Ajustes → Gestión → **Eliminación de archivos y papelera**
2. O elimina manualmente las carpetas `.trash/`

---

## ¿Sigues teniendo problemas?

### Revisa los registros
1. Ajustes → Gestión → **"Mostrar errores detallados"** = ACTIVADO
2. Reproduce el problema
3. Comprueba la salida de logcat

### Reportar un error
Incluye esta información:
- Versión de Android
- Modelo del dispositivo
- Pasos para reproducirlo
- Mensaje de error (captura de pantalla)

**Enviar:** [Issues de GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

---
    
## Problemas de traducción y EPUB
    
### ❌ "La traducción no funciona o se queda bloqueada"

**Posibles causas:**
1. **Faltan modelos:** la app no pudo descargar los modelos de OCR.
2. **Sin Internet:** la primera ejecución necesita Internet para descargar los modelos.
3. **Almacenamiento lleno:** no hay espacio para los modelos (~50 MB).

**Solución:**
1. Comprueba la conexión a Internet.
2. Ve a **Ajustes** → **Multimedia** → **Otros**
3. Desactiva "Activar traducción" y vuelve a activarlo.
4. Prueba a cambiar el **idioma de origen** a "Auto".

---

### ❌ "El libro EPUB no se abre"

**Posibles causas:**
1. **Protección DRM:** la app solo admite EPUB sin DRM.
2. **Archivo corrupto:** el archivo podría estar incompleto.
3. **Archivo muy grande:** los archivos de más de 100 MB en redes lentas pueden agotar el tiempo de espera.

**Solución:**
1. Verifica que el archivo se abre en otros lectores.
2. Si está en red/nube, prueba a descargarlo manualmente primero.
3. Asegúrate de que la extensión del archivo sea exactamente `.epub`.

---

## Problemas con las emisiones de Internet

### La emisión no arranca / se reproduce un segundo y se detiene

**Posibles causas:**
1. La URL está muerta o redirige a un protocolo distinto.
2. El servidor requiere autenticación (no compatible).
3. El tráfico http:// sin cifrar está bloqueado por una VPN o una red corporativa.

**Solución:**
- Toca **Reintentar** en el diálogo de emisión no disponible para volver a intentarlo.
- Verifica la URL en un navegador.
- Desactiva la VPN temporalmente para probar.
- Si la emisión redirige y sigue fallando, toca **Eliminar** y vuelve a añadir la URL corregida.

### El indicador de importación del catálogo no se detiene / se cuelga

La app aplica un tiempo de espera corto para las descargas del catálogo. Si el indicador se cuelga más de ~15 segundos, es probable que el host no esté accesible. Comprueba tu conexión a Internet e inténtalo de nuevo. El diálogo se cerrará automáticamente al agotarse el tiempo - no se quedará colgado indefinidamente.

### HLS / DASH / RTSP muestra el mensaje "no compatible"

En **Standard**, **Legacy** y **XR / noLegal** los tres protocolos son compatibles, así que este mensaje señala a la emisión o a su códec, no a la compilación. **Lite** y **Photos** no tienen pantalla de Streams en absoluto, así que ahí no se puede añadir ninguna emisión desde el principio.

### La opción Streams no aparece en el menú ni en los ajustes

- En **Standard / Legacy / XR / noLegal**: ve a **Ajustes > Multimedia > Streams** y comprueba que **Activar Streams** esté encendido. El elemento del menú desplegable solo aparece cuando Streams está activado.
- En **Photos**: la función Streams no está integrada en esta variante.
- En **Lite**: la función Streams tampoco está integrada en esta variante - no hay ningún interruptor que activar ni pantalla que abrir.

### No aparecen los metadatos ICY de reproducción actual

Los metadatos ICY requieren una emisión Icecast/Shoutcast que envíe la cabecera `Icy-MetaData: 1`. Las emisiones http mp3 simples sin cabeceras ICY no muestran información de emisora/pista en el mini-control inferior. Es una limitación del lado del servidor.

---

## Problemas de contenido

### ❌ "No se ven los archivos de texto o PDF"

**Solución:**
1. Comprueba **Ajustes** → **Multimedia** → **Documentos**
2. Asegúrate de que **"Admitir archivos de texto"** y **"Admitir archivos PDF"** estén activados.
3. Comprueba los **filtros** de la pantalla principal (icono de embudo) para asegurarte de que están seleccionados.
4. **Vuelve a escanear** la carpeta (deslizar hacia abajo para actualizar).

---

## Limitaciones conocidas

- ⚠️ **Sin compatibilidad con fotos RAW** (CR2, NEF, ARW)
- ⚠️ **Deshacer no disponible en red** (los archivos se eliminan de forma permanente)
- ⚠️ **El almacenamiento en la nube está integrado en todas las variantes excepto Lite**; qué proveedores ofrece una compilación concreta puede depender también de la plataforma del dispositivo, y [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) es la cuadrícula por variante
- ⚠️ **Sin sincronización entre varios dispositivos** (los favoritos son locales)

---

**Última actualización:** 2026-06-05  
**Versión:** Conjunto actual de documentación pública

</div>
