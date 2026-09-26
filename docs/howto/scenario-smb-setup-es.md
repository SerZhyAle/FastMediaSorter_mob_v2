---
layout: default
title: "Conectar a NAS / Recurso Compartido de Windows (SMB) - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 🖥️ Conectar a NAS de Casa / Recurso Compartido de Windows (SMB)

> **Nivel:** Principiante &bull; **Edición:** Standard, Photos, Legacy, VR, noLegal (Lite no tiene fuentes de red)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="es" %}

SMB (también llamado Uso Compartido de Archivos de Windows o CIFS) te permite explorar archivos en tu PC, laptop o dispositivo NAS de casa exactamente como si estuvieran en tu teléfono - sin cables, sin USB, solo Wi-Fi.

> **Explicación en lenguaje simple:** Imagina que tu PC tiene un tablón de anuncios público en tu Wi-Fi de casa. Cualquier dispositivo de la casa puede leer de ese tablón. FastMediaSorter se conecta a ese "tablón" (tu carpeta compartida) y te permite explorar tus archivos como si estuvieran guardados directamente en tu teléfono. Nada se copia ni se descarga por adelantado - los archivos se abren bajo demanda.

---

## Lo Que Necesitarás

- Tu teléfono y tu PC / NAS en la **misma red Wi-Fi** (mismo router)
- La **dirección IP** de tu PC o NAS (por ejemplo, `192.168.1.100`)
- El **nombre del recurso compartido** (el nombre de la carpeta que compartiste, por ejemplo, `Photos`)
- Un **nombre de usuario y contraseña** para ese recurso compartido (o acceso de invitado si está habilitado)

> **¿No estás seguro de los términos?** No te preocupes - los Pasos 1 y 2 explican exactamente dónde encontrarlos.

---

## Paso 1 - Encuentra la Dirección IP de tu PC

La dirección IP es la "dirección de casa" de tu PC en tu red Wi-Fi. La necesitas para que tu teléfono sepa dónde buscar.

En **Windows:**
1. Presiona `Win + R`, escribe `cmd`, presiona Enter - se abre una ventana negra de texto
2. Escribe `ipconfig` y presiona Enter
3. Busca **Dirección IPv4** bajo tu adaptador Wi-Fi - algo como `192.168.1.100`

> La línea que necesitas está etiquetada como **"Dirección IPv4"** (no IPv6, que se ve como una larga serie de letras y números). Debería comenzar con `192.168.` en la mayoría de las redes de casa.

En un **NAS** (Synology, QNAP, etc.):
- Abre el panel web del NAS → Configuración de red - la IP se muestra ahí

> Anota la IP - la necesitarás en el Paso 6.

![Windows PowerShell - salida de ipconfig, Dirección IPv4 `192.168.1.100` visible](screenshots/screenshot-smb-step1.png)

---

## Paso 2 - Encuentra el Nombre del Recurso Compartido en tu PC

El "nombre del recurso compartido" es el nombre público de tu carpeta en la red. Puede ser el mismo que el nombre de la carpeta, o diferente.

En **Windows:**
1. Abre el **Explorador de Archivos**
2. Clic derecho en la carpeta que quieres compartir → **Propiedades**
3. Ve a la pestaña **Compartir**
4. Mira la **Ruta de Red** - se ve como `\\DESKTOP-ABC\Photos`
5. La parte después de la última `\` es tu **nombre del recurso compartido** (aquí: `Photos`)

> **¿La carpeta aún no está compartida?** Haz clic en **Compartir..** → elige **Todos** → **Agregar** → **Compartir**. Windows te mostrará la ruta de red de inmediato.

> **Importante:** asegúrate de que **Detección de Red** y **Uso Compartido de Archivos** estén activados en Windows. Ve a Panel de Control → Centro de Redes y Recursos Compartidos → Cambiar configuración de uso compartido avanzado → activa "Detección de redes" y "Uso compartido de archivos e impresoras".

![Propiedades de carpeta de Windows - pestaña Compartir, Ruta de Red `\\MARK\Common` visible](screenshots/screenshot-smb-step2.png)

---

## Paso 3 - Abre FastMediaSorter y Toca "+"

1. Abre la app
2. En la **pantalla principal**, toca el botón **"Agregar" <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas superior

![Pantalla principal de FastMediaSorter - botón Agregar resaltado en la barra de herramientas superior, pestaña SMB visible](screenshots/screenshot-smb-step3.png)

---

## Paso 4 - Selecciona "Carpeta de red (SMB)"

En la lista de tipos de recurso, toca **"Carpeta de red (SMB)"** (o la pestaña SMB).

![Cuadro de diálogo Seleccionar Tipo de Carpeta - cuatro opciones: Carpeta Local, Carpeta de Red (SMB), SFTP/FTP, Almacenamiento en la Nube](screenshots/screenshot-smb-step4.png)

---

## Paso 5 - Prueba Primero la Detección Automática

Toca el botón **"Escanear Red"**. La app escaneará tu Wi-Fi local en busca de dispositivos con recursos SMB compartidos.

- Espera ~10 segundos
- Aparece una lista de dispositivos encontrados
- Toca tu PC o NAS - la dirección IP se completa automáticamente

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **¿No se encontró nada?** Está bien - salta al Paso 6 y escribe la IP manualmente. Esto sucede cuando tu router usa Aislamiento de AP (una configuración que bloquea la comunicación teléfono-PC por seguridad). La IP manual siempre funciona.

---

## Paso 6 - Completa los Detalles de Conexión

Completa el formulario:

| Campo | Qué ingresar | Ejemplo |
|-------|--------------|---------|
| **Servidor / Ruta** | `\\IP\NombreCompartido` | `\\192.168.1.100\Photos` |
| **Nombre de usuario** | Tu nombre de inicio de sesión de Windows | `john` |
| **Contraseña** | Tu contraseña de Windows | `••••` |
| **Nombre para Mostrar** | Cualquier nombre que quieras (opcional) | `PC de Casa - Fotos` |

> **¿Usas una cuenta de Microsoft (correo) para iniciar sesión en Windows?** Usa tu **dirección de correo completa** como nombre de usuario (por ejemplo, `john@outlook.com`), no solo tu primer nombre. Tu contraseña es la misma que escribes para desbloquear tu PC.

> **¿No tienes contraseña, o usas Invitado?** Intenta dejar el Nombre de usuario y la Contraseña en blanco y toca Probar Conexión - algunas PCs de casa permiten acceso abierto.

![Agregar Carpeta de Red (SMB) - IP del Servidor `192.168.1.100`, NombreCompartido y credenciales completados](screenshots/screenshot-smb-step6.png)

![Agregar Carpeta de Red (SMB) - sección inferior: opciones, tipos de medios, botón AGREGAR ESTE RECURSO](screenshots/screenshot-smb-step6b.png)

**Referencia de formato de dirección:**

| Formato | Ejemplo |
|--------|---------|
| Windows estándar | `\\192.168.1.100\Photos` |
| Estilo Linux / macOS | `smb://192.168.1.100/Photos` |
| Subcarpeta | `\\192.168.1.100\Media\Movies` |
| Puerto personalizado | `smb://192.168.1.100:445/Photos` |

---

## Paso 7 - Prueba la Conexión

Toca **"Probar Conexión"**.

- **Mensaje verde** = éxito → ¡ve al Paso 8! Ya casi terminas.
- **Mensaje rojo** = algo está mal → revisa la tabla de Solución de Problemas abajo. Solución más común: verifica dos veces la IP y el nombre del recurso compartido.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## Paso 8 - Guarda y Abre

Toca **"Guardar"**. La nueva carpeta aparece en la pantalla principal con una insignia SMB.

Tócala para explorar su contenido - fotos, videos y otros archivos aparecen como miniaturas igual que cualquier carpeta local.

![Pantalla principal de FastMediaSorter - nueva tarjeta de recurso SMB "Common" (smb://192.168.1.100/Common) con insignia Carpeta de red SMB resaltada](screenshots/screenshot-smb-step8.png)

---

## ¡Listo! Ahora Puedes..

- Explorar todos los archivos de tu PC desde tu teléfono
- Reproducir videos y música directamente - sin necesidad de descargar
- Copiar o mover archivos entre tu teléfono y tu PC
- Usar esta carpeta como fuente para presentación, marco de fotos o música del coche

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| "Conexión rechazada" | Abre el Firewall de Windows → permite el **puerto TCP 445** de entrada. O desactiva temporalmente el firewall para probar |
| "Contraseña incorrecta" | Intenta dejar el **Nombre de usuario en blanco** (acceso de invitado). O si usas una cuenta de Microsoft, ingresa tu **correo completo** como nombre de usuario |
| "Host no encontrado" | Asegúrate de que el teléfono y el PC estén en la **misma Wi-Fi** y el mismo router. El Aislamiento de AP (una configuración de seguridad del router) puede bloquear esto - intenta desactivarlo en la configuración del router |
| El escaneo no encuentra nada | Desactiva la VPN en el teléfono. También verifica que **Detección de Red** esté activada en Windows (Panel de Control → Centro de Redes y Recursos Compartidos). Luego intenta ingresar la IP manualmente |
| Exploración muy lenta | Toca **Editar** en el recurso → ejecuta **Prueba de Velocidad** para ver el rendimiento real. Desactiva las miniaturas de video para conexiones lentas |
| Funciona en Wi-Fi pero no con datos móviles | Es lo esperado - SMB es un protocolo de red local únicamente. No puede funcionar con datos móviles |

→ Más ayuda: [TROUBLESHOOTING-es.md](../TROUBLESHOOTING-es.md)

</div>
