---
layout: default
title: "Conecta tu Smartwatch a Recursos Compartidos de NAS y PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-es.html
---
<div lang="es" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Conecta tu Smartwatch a Recursos Compartidos de NAS y PC

> **Nivel:** Intermedio &bull; **Tiempo:** ~10 minutos &bull; **Dispositivo:** Smartwatch Wear OS

> **Solo versión completa** - esta guía no está implementada en la versión distribuida a través de Google Play. Aplica a la versión completa, una descarga directa de APK desde [Descargas](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="es" %}

FastMediaSorter en Wear OS se conecta directamente al almacenamiento de tu red de casa (NAS, carpetas compartidas del PC, servidores FTP o SFTP) por Wi-Fi. Puedes explorar archivos remotos, transmitir música a audífonos Bluetooth, y sincronizar tus carpetas favoritas sin necesitar tu teléfono.

> **¿Nuevo en recursos compartidos de red?** Si aún no has configurado una carpeta compartida en tu PC o NAS, comienza primero con nuestra guía [Conectar a NAS / Recurso Compartido de Windows (SMB)](scenario-smb-setup-es.md).

---

## Lo Que Necesitarás

- Un smartwatch con **Wear OS 2.0** o más nuevo conectado a tu red Wi-Fi de casa
- Una carpeta de red compartida (recurso SMB / Windows, servidor FTP, o servidor SFTP)
- Credenciales de red: dirección IP o nombre de host, nombre del recurso compartido, nombre de usuario y contraseña
- FastMedia Wear instalado en tu reloj

---

## Paso 1 - Abre Recursos en tu Reloj

1. Abre **FastMedia Wear** en tu smartwatch.
2. En la pantalla principal, toca **Recursos** (ícono de Wi-Fi).
3. La pantalla de Recursos muestra tus conexiones de red configuradas.

![Pantalla de Recursos en Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Acceso directo Sincronizar desde el Teléfono:** Si ya agregaste tus recursos SMB o SFTP en FastMediaSorter en tu teléfono Android, toca **Sincronizar desde el Teléfono** para importar toda la configuración de conexión a tu reloj con un solo toque.

---

## Paso 2 - Agrega una Fuente de Red

1. En la pantalla de Recursos, toca **Agregar recurso**.
2. Selecciona tu protocolo de red:
   - **SMB**: recursos compartidos de Windows estándar, Synology, QNAP, o TrueNAS
   - **FTP**: servidores de archivos FTP estándar
   - **SFTP**: servidores de transferencia de archivos SSH seguros (admite contraseña o clave SSH privada)
3. Toca cada campo para ingresar los detalles de conexión usando el teclado en pantalla del reloj:
   - **Nombre**: etiqueta opcional (por ejemplo, "NAS de Casa" o "Recurso de Música")
   - **Dirección del Servidor**: la IP de tu computadora o NAS (por ejemplo, `192.168.1.50`)
   - **Puerto**: puerto de red (por defecto: 445 para SMB, 21 para FTP, 22 para SFTP)
   - **Nombre del Recurso Compartido** (solo SMB): el nombre de la carpeta compartida en tu NAS/PC
   - **Nombre de usuario** y **Contraseña**: tus credenciales de acceso

![Pantalla Agregar Fuente de Red en el reloj](screenshots/screenshot-wear-network-step2.png)

---

## Paso 3 - Prueba y Guarda la Conexión

1. Desplázate hasta la parte inferior del formulario y toca **Probar**.
2. FastMedia Wear verifica la ruta de red y las credenciales:
   - En caso de éxito, la pantalla muestra **¡Conexión exitosa!**.
   - Si hay un problema, un mensaje de estado amigable indica qué ajustar (por ejemplo, la dirección del servidor o la contraseña).
3. Toca **Guardar** para almacenar la fuente de red en tu reloj.

![Probar conexión de red y guardar fuente](screenshots/screenshot-wear-network-step3.png)

---

## Paso 4 - Explora y Reproduce Medios de Red

1. En la pantalla de Recursos, toca tu recurso de red recién guardado.
2. FastMedia Wear se conecta al recurso remoto y lista su contenido.
3. Explora carpetas y archivos en vista de lista o de cuadrícula.
4. Toca cualquier pista de audio para iniciar la reproducción en el reproductor a pantalla completa. Para funciones detalladas del reproductor y ahorro de batería, consulta [Escucha Música en tu Reloj](scenario-watch-music-es.md).

![Explorar archivos y carpetas en un recurso de red](screenshots/screenshot-wear-network-step4.png)

---

## ¡Listo! Funciones de Red en Wear OS

- **Transmisión Wi-Fi Independiente**: Transmite directamente desde tu NAS o PC por Wi-Fi sin retransmisión por el teléfono.
- **Soporte Multi-Protocolo**: Soporte completo para SMB, FTP y SFTP con autenticación por contraseña o clave SSH privada.
- **Sincronización Bidireccional**: Sincroniza conexiones desde tu companion del teléfono o exporta las fuentes del reloj de vuelta al teléfono.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| La prueba de conexión reporta "Conexión fallida" | Verifica que tu reloj esté conectado a la misma red Wi-Fi que el servidor, y revisa la dirección IP |
| Error de nombre de recurso compartido en SMB | Asegúrate de ingresar solo el nombre del recurso compartido (por ejemplo, `Music`), no la ruta completa con barras |
| Autenticación fallida | Revisa tu nombre de usuario y contraseña. En recursos compartidos de Windows, asegúrate de que los permisos de uso compartido de red permitan tu cuenta de usuario |
| Carga lenta por Wi-Fi | Asegúrate de que la señal Wi-Fi del reloj sea fuerte y que el enrutamiento de red de 5 GHz / 2.4 GHz hacia el servidor local no esté bloqueado |

</div>
