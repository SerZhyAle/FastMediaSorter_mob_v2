---
layout: default
title: "Copia de Seguridad Programada de la Cámara al PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-camera-backup-es.html
---
<div lang="es" dir="ltr" markdown="1">

# 📷 Copia de Seguridad Programada de la Cámara al PC

> **Nivel:** Principiante &bull; **Tiempo:** ~15 minutos de configuración &bull; **Edición:** Standard, Photos, Legacy, VR, noLegal (necesita fuentes de red - Lite no tiene ninguna)

{% include lang-switcher.html doc="scenario-camera-backup" dir="/docs/howto/" current="es" %}

Copia automáticamente las fotos nuevas de la cámara de tu teléfono a tu computadora de casa **cada noche, por Wi-Fi**. Configúralo una vez - funciona para siempre sin ninguna acción manual.

**Lo que esto te da:** cada mañana te despiertas y las fotos de la noche anterior ya están en tu PC. Sin cables. Sin suscripciones en la nube. Sin olvidos. Totalmente automático.

---

## Lo Que Necesitarás

- Teléfono y PC conectados al **mismo Wi-Fi de casa** (mismo router)
- Una carpeta en tu PC donde se guardarán las fotos (por ejemplo `C:\PhoneBackup`)
- FastMediaSorter instalado en una edición con fuentes de red (**Standard**, Photos, Legacy, VR, noLegal)

> **¿No sabes qué edición tienes?** Abre **Configuración** <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> → **Información del sistema**. La fila **Edición** indica la variante (Standard / Lite / etc.).

---

## Paso 1 - Crea una Carpeta de Respaldo en tu PC

Primero, crea una carpeta en tu PC. Luego compártela para que el teléfono pueda acceder a ella.

En **Windows:**
1. Crea una carpeta nueva donde quieras - por ejemplo `C:\PhoneBackup`
2. **Clic derecho** en la carpeta → **Propiedades** → pestaña **Compartir** → clic en **Compartir..**
3. En el menú desplegable, selecciona tu nombre de usuario o escribe **Todos** → clic en **Agregar** → clic en **Compartir**
4. Windows muestra la ruta de red - anótala. Se ve así: `\\MYPC\PhoneBackup`

> **También anota la dirección IP de tu PC** - la necesitarás en el Paso 2. La forma más rápida: presiona **Win + R**, escribe `cmd`, presiona Enter. En la ventana negra escribe `ipconfig` y presiona Enter. Busca la línea **Dirección IPv4** bajo tu adaptador Wi-Fi. Ejemplo: `192.168.1.100`. Anota ese número.

---

## Paso 2 - Conecta la App a la Carpeta de tu PC

Ahora dile a FastMediaSorter a dónde enviar las fotos.

> **¿Qué es SMB?** Es simplemente la forma en que Windows comparte carpetas por el Wi-Fi de casa. No necesitas entender los detalles - solo sigue los pasos.

1. Abre la app → toca **Agregar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** en la barra de herramientas superior → selecciona **"Carpeta de red (SMB)"**
2. En el campo **Servidor / Ruta** escribe: `\\192.168.1.100\PhoneBackup`
   - Reemplaza `192.168.1.100` con la IP real de tu PC del Paso 1
   - Reemplaza `PhoneBackup` con el nombre real de tu carpeta
3. Ingresa tu **nombre de usuario** y **contraseña** de Windows (los mismos que usas para iniciar sesión en tu PC)
4. Toca **Probar Conexión** - espera unos segundos - deberías ver un mensaje verde de éxito
5. Toca **Guardar**

> **¿No puedes conectarte?** Consulta la [Guía de Configuración de SMB](scenario-smb-setup-es.md) - cubre todos los problemas de conexión comunes con soluciones paso a paso.


---

## Paso 3 - Abre la Configuración de Operaciones Programadas

1. Toca **Configuración** (el ícono del engranaje <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> en la barra de herramientas)
2. Ve a la pestaña **Gestión**
3. Desplázate hasta **"Operaciones programadas por horario"** y toca **"Operaciones de archivos programadas"** - se abre la pantalla de operaciones programadas
4. Activa **"Usar operaciones programadas"** en la parte superior de esa pantalla

![Configuración → Gestión - sección Programado con el botón AGREGAR](screenshots/screenshot-cb-step3.png)

---

## Paso 4 - Crea un Nuevo Horario

Toca el botón **+** (**"Agregar"**) en la pantalla de operaciones programadas.

Se abre un cuadro de diálogo de nuevo horario.

![Cuadro de diálogo Agregar Horario - sección Condiciones: opciones de intervalo y sobrescritura](screenshots/screenshot-cb-step4.png)

---

## Paso 5 - Completa el Horario de Respaldo

Completa cada campo:

| Campo | Qué establecer | Ejemplo |
|-------|------------|---------|
| **Nombre** | Cualquier etiqueta para reconocer este horario | `Respaldo Nocturno de la Cámara` |
| **Origen** | Dónde están las fotos de tu cámara | Selecciona **"Fotos de la Cámara"** - encuentra automáticamente todas las tomas de la cámara |
| **Destino** | Tu carpeta de respaldo en el PC | Selecciona el recurso SMB que acabas de agregar (`PhoneBackup (SMB)`) |
| **Operación** | Qué hacer con los archivos | **"Copiar (omitir existentes)"** - copia solo fotos nuevas, nunca duplica |
| **Horario** | Cuándo ejecutarse | `Diario a las 02:00` - se ejecuta mientras duermes |
| **Ejecutar solo con Wi-Fi** | Actívalo | Evita que el respaldo use tus datos móviles por accidente |

> **¿Qué es "Fotos de la Cámara"?** Es una carpeta virtual especial que FastMediaSorter crea automáticamente. Siempre muestra todas las fotos tomadas por tu cámara - incluso si están almacenadas en diferentes carpetas del teléfono. Prefiere siempre esta opción en lugar de elegir una ruta manual.

![Agregar Horario - Origen: Fotos de la Cámara, Operación: Copiar, Destino: SMB](screenshots/screenshot-cb-step5.png)

---

## Paso 6 - Guarda y Permite el Acceso en Segundo Plano

Toca **Guardar**.

El horario aparece en la lista - ahora está activo.

**Puede que veas un cuadro de diálogo de permisos.** La app pide ser excluida del ahorro de batería. Toca **"Desactivar Optimización"** (o **"Permitir"**).

> **¿Por qué es importante este paso?** Android intenta ahorrar batería deteniendo automáticamente las apps que se ejecutan en segundo plano. Sin este permiso, Android podría detener el respaldo a mitad de la noche. Otorgar esto solo permite que la app despierte a la hora programada - no drena tu batería de forma notable.

![Entrada de horario guardado: Fotos de la Cámara → SMB a la hora programada](screenshots/screenshot-cb-step6.png)

---

## Paso 7 - Pruébalo Ahora Mismo

No esperes hasta las 2 AM - prueba el respaldo de inmediato para asegurarte de que todo funcione:

1. Ve a **Configuración → Gestión → Operaciones de archivos programadas**
2. Busca tu horario → toca **"Ejecutar ahora"**
3. Aparece una notificación en la parte superior de tu pantalla mostrando el progreso de la transferencia
4. Cuando termine: toca el recurso SMB (`PhoneBackup`) → tus fotos de la cámara deberían ser visibles ahí

> **¿No se copió nada?** Si todas tus fotos ya están en la carpeta de respaldo (o la carpeta de la cámara del teléfono está vacía), la app copia correctamente cero archivos. Intenta tomar una nueva foto de prueba y ejecuta de nuevo.


---

## ¡Listo! Esto Es lo Que Sucede Cada Noche

1. A las 02:00 la app despierta silenciosamente
2. Observa tu carpeta de cámara y la compara con la carpeta de respaldo del PC
3. Copia solo las fotos que aún no están en el PC - toma desde unos segundos hasta unos minutos
4. Muestra una notificación: "Se respaldaron 12 archivos" (o cuantos sean nuevos)
5. Vuelve a dormir

Tu PC recibe fotos nuevas cada mañana. Nunca tienes que pensar en ello.

---

## Consejos

> **¿Quieres liberar espacio en el teléfono después del respaldo?** Cambia la Operación a **"Mover"** en lugar de "Copiar". Las fotos se eliminan del teléfono justo después de copiarse de forma segura al PC. Usa esto con cuidado - una vez movidas, las fotos ya no están en el teléfono.

> **¿Tienes varios teléfonos en la familia?** Crea un horario por teléfono. Usa subcarpetas diferentes como destinos - por ejemplo `PhoneBackup\Mamá` y `PhoneBackup\Papá` - para que todos los dispositivos respalden en el mismo PC sin mezclar archivos.

> **¿Prefieres respaldar en Google Drive?** Agrega un recurso de Google Drive como destino en lugar de SMB - el resto de los pasos son idénticos.

---

## Solución de Problemas

| Problema | Qué intentar |
|---------|------------|
| El horario no se ejecuta de noche | Ve a **Configuración de Android → Apps → FastMediaSorter → Batería** → establece en **Sin restricciones** |
| Error "Destino no accesible" | Tu teléfono debe estar en Wi-Fi a la hora del respaldo. Si el Wi-Fi estaba apagado a las 2 AM, el respaldo se omite y se reintenta automáticamente la noche siguiente |
| Algunas fotos no se respaldaron | Usa el recurso virtual **"Fotos de la Cámara"** como origen - captura fotos de todas las carpetas de cámara en tu teléfono |
| Aparecen archivos duplicados en el PC | Asegúrate de que la Operación esté configurada como **"Copiar (omitir existentes)"**, no "Copiar (sobrescribir)" |

</div>
