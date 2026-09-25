---
layout: default
title: "Musikplayer fürs Auto (Android-Autoradio) - FastMediaSorter v2"
permalink: /docs/howto/scenario-car-music-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 🚗 Musikplayer fürs Auto (Android-Autoradio)

> **Niveau:** Anfänger &bull; **Zeit:** ~10 Minuten &bull; **Edition:** Standard, Legacy, VR, noLegal (Lite spielt lokale Audiodateien ab, hat aber keine Hintergrundwiedergabe und keine Streams; Photos hat kein Audio)

{% include lang-switcher.html doc="scenario-car-music" dir="/docs/howto/" current="de" %}

FastMediaSorter eignet sich hervorragend als Musikplayer fürs Auto auf Android-Autoradios - sofortiger Zugriff auf Ihre gesamte Musiksammlung auf SD-Karte oder USB-Stick, mit integrierter Unterstützung für Lenkradtasten.

> **Was ist ein Android-Autoradio?** Es ist ein Autoradio mit Touchscreen, auf dem Android läuft - wie Ihr Handy, nur im Armaturenbrett eingebaut. Diese Anleitung funktioniert auch mit einem gewöhnlichen Handy oder Tablet, das im Auto montiert ist.

---

## Was Sie benötigen

- Android-Autoradio / Handy / Tablet im Auto
- Musikdateien auf **SD-Karte**, **USB-Stick** oder **internem Speicher** (MP3, FLAC, AAC, OGG und andere)
- (Optional) Medientasten am Lenkrad

---

## Schritt 1 - Ihren Musikordner hinzufügen

1. Öffnen Sie die App
2. Tippen Sie in der oberen Symbolleiste auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">**
3. Wählen Sie **„Lokaler Ordner“**
4. Navigieren Sie dorthin, wo Ihre Musik gespeichert ist:
   - **SD-Karte:** Suchen Sie nach einem Ordner namens `/storage/` - darin finden Sie einen Ordner mit einem Code wie `1234-5678`, und Ihre Musik liegt meist in `/storage/1234-5678/Music`
   - **Interner Speicher:** Versuchen Sie `/sdcard/Music` oder `/sdcard/Download`
   - **USB-Stick:** Schauen Sie in `/storage/usb0/` oder `/storage/usbdisk/`
5. Wählen Sie den Ordner aus → tippen Sie auf **Auswählen**

> **Finden Sie Ihre Musik nicht?** Versuchen Sie, auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **„Lokaler Ordner“** zu tippen und dann irgendwo in der Liste nach einem Ordner namens `Music` zu suchen. Auf den meisten Geräten ist er direkt sichtbar.


---

## Schritt 2 - Den Ordner für Musik konfigurieren

Halten Sie Ihren Musikordner auf dem Hauptbildschirm gedrückt → tippen Sie auf **Bearbeiten (Stiftsymbol)**.

Dies öffnet die Ordnereinstellungen. Setzen Sie diese Optionen:

| Einstellung | Wert | Warum |
|---------|-------|-----|
| **Profil** | Audiobibliothek | Teilt der App mit „das ist ein Musikordner“ - konfiguriert automatisch alles für Audio |
| **Unterstützte Typen** | Nur Audio | Blendet Fotos und Videos aus, sodass nur Musiktitel angezeigt werden |
| **Sortiermodus** | Titel (A→Z) oder Interpret | Hält Ihre Titel in einer logischen Reihenfolge |
| **Unterordner einbeziehen** | EIN | Wenn Ihre Musik in Interpret-/Album-Unterordnern organisiert ist, findet dies alle Titel |

Tippen Sie auf **Speichern**.

> **Was macht „Profil“?** Es ist eine Ein-Tipp-Voreinstellung, die den Ordner optimal für seinen Zweck einrichtet. Die Wahl von „Audiobibliothek“ bedeutet, dass die App Album-Cover anzeigt, korrekt nach Musik sortiert und Nicht-Audio-Dateien automatisch ausblendet.


---

## Schritt 3 - Den Ordner öffnen und die Wiedergabe starten

1. Tippen Sie auf Ihren Musikordner auf dem Hauptbildschirm
2. Alle Titel erscheinen in einer Liste mit Album-Cover-Vorschaubildern
3. Tippen Sie auf **einen beliebigen Titel**, um die Wiedergabe zu starten

Der Vollbild-**Audioplayer** öffnet sich mit Album-Cover, Fortschrittsleiste und Wiedergabesteuerung.

![Audioplayer im Vollbild mit Album-Cover (Camel - Dust and Dreams)](screenshots/screenshot-car-step3.png)

---

## Schritt 4 - Sicherstellen, dass die Musik weiterläuft

Dieser Schritt sorgt dafür, dass die Musik weiterspielt, wenn der Bildschirm sich ausschaltet, Sie die App wechseln oder eine Anrufbenachrichtigung erhalten:

1. Gehen Sie zu **Einstellungen → Reiter Medien**
2. Scrollen Sie zum Abschnitt **Audio**
3. Stellen Sie sicher, dass **„Audio-Unterstützung“** EINGESCHALTET ist

Das ist alles. Sobald dies aktiviert ist, registriert sich die App als vollwertiger Musikplayer - Sperrbildschirm-Steuerung und der Medienplayer in der Benachrichtigungsleiste erscheinen automatisch.

![Einstellungen → Medien → Abschnitt Audio mit Optionen für Hintergrundwiedergabe](screenshots/screenshot-car-step4.png)

---

## Schritt 5 - Lenkradtasten testen

Drücken Sie **Weiter** oder **Zurück** an Ihrem Lenkrad.

**Sie funktionieren automatisch - keine Einrichtung nötig.** FastMediaSorter reagiert auf alle standardmäßigen Android-Medientasten.

> **Tasten funktionieren nicht?** Einige ältere Autoradios senden nicht standardmäßige Signale. Versuchen Sie, in den Android-**Einstellungen → Barrierefreiheit** nach einer Option „Medientasten-Empfänger“ zu suchen. Hilft das nicht, verwenden Sie stattdessen die Touch-Zonen auf dem Bildschirm (linker/rechter Bildschirmrand) - sie funktionieren einwandfrei.


---

## Schritt 6 - (Optional) „Alle Titel“ verwenden - ein Ort für alle Ihre Titel

Wenn Ihre Musik über mehrere Ordner verteilt ist (z. B. teilweise auf der SD-Karte, teilweise im internen Speicher), sammelt die virtuelle Ressource **Alle Titel** automatisch alles an einem Ort:

1. Suchen Sie auf dem Hauptbildschirm nach der Karte **„Alle Titel“** - sie wird meist automatisch erstellt, wenn Sie lokale Audiodateien haben
2. Falls sie nicht da ist: Tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → scrollen Sie zu **Virtuelle Ressourcen** → tippen Sie auf **„Alle Titel“**

Jetzt erscheinen alle Ihre Titel aus allen Orten gemeinsam in einer Liste.

![FastMediaSorter Hauptbildschirm - Karte der virtuellen Ressource „Alle Titel“ sichtbar](screenshots/screenshot-car-step6.png)

---

## Schritt 7 - (Optional) Startbildschirm-Verknüpfung für den Ein-Tipp-Start

Perfekt, wenn Sie einfach ins Auto steigen und mit einem Fingertipp die Musik starten möchten:

1. Halten Sie eine leere Stelle auf dem Startbildschirm gedrückt → tippen Sie auf **Widgets**
2. Suchen Sie **FastMediaSorter** in der Liste → ziehen Sie das Widget **„Ressourcen-Verknüpfung“** auf Ihren Startbildschirm
3. Wählen Sie bei Aufforderung Ihre Musikressource aus
4. Fertig - tippen Sie jederzeit auf das Widget, und die Musik startet sofort

---

## Schritt 8 - (Optional) Internetradiosender hinzufügen

Wenn Ihr Autoradio über eine aktive Mobilfunk- oder Wi-Fi-Verbindung verfügt, können Sie Internetradiosender direkt hinzufügen - keine zusätzliche App nötig:

1. Öffnen Sie das Hauptmenü (Hamburger- oder Dropdown-Symbol) → tippen Sie auf **Streams**
2. Tippen Sie auf **Hinzufügen (+)** → fügen Sie eine beliebige Internetradio-URL ein (http/https, .m3u8, RTSP) und tippen Sie auf Speichern
3. Oder tippen Sie auf **Katalog importieren**, um die integrierte kuratierte Senderliste zu durchsuchen und Sender nach Genre oder Sprache hinzuzufügen
4. Tippen Sie auf eine Senderzeile, um die Audiowiedergabe direkt in der Liste zu starten - der Sendername und der aktuelle Titel erscheinen in der unteren Mini-Steuerung
5. Die Liste bleibt sichtbar, sodass Sie zwischen Sendern wechseln können, ohne den Bildschirm zu verlassen

> **Hintergrund-Audio:** Damit das Radio beim App-Wechsel weiterläuft, gehen Sie zu **Einstellungen → Player → Hintergrund-Audiowiedergabe** und aktivieren Sie es.

Hinweis: Streams benötigt eine Netzwerkverbindung, und der Streams-Bildschirm fehlt in den Editionen Lite und Photos.

---

## Fertig! Player-Steuerung

Während Musik läuft, ist der Bildschirm Ihr Bedienfeld:

- **Linke 20% des Bildschirms** → Vorheriger Titel
- **Rechte 20% des Bildschirms** → Nächster Titel
- **Mittlere 60%** → Pause / Wiedergabe / Befehlsmenü

![Audioplayer läuft im Hintergrund - Befehlsleiste sichtbar](screenshots/screenshot-car-done.png)

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Lenkradtasten funktionieren nicht | Prüfen Sie, ob Ihr Autoradio standardmäßige Android-Medientastenereignisse sendet. Bei manchen Geräten muss „Medientasten-Empfänger“ in Android-**Einstellungen → Barrierefreiheit** aktiviert werden |
| Musik stoppt, wenn der Bildschirm gesperrt wird | Aktivieren Sie **„Ruhezustand verhindern“** in Einstellungen → Allgemein, oder verwenden Sie die Audio-Benachrichtigungssteuerung zum Fortsetzen. Prüfen Sie außerdem, dass Audio-Unterstützung EIN ist (Schritt 4) |
| Kein Album-Cover angezeigt | Aktivieren Sie **„Audio-Cover online abrufen“** in Einstellungen → Medien → Audio (benötigt Wi-Fi). Für Offline-Cover liest die App eingebettete Cover-Art aus der MP3-/FLAC-Datei automatisch aus |
| Musik auf der SD-Karte nicht auffindbar | Manche Android-Versionen schränken den Zugriff auf die SD-Karte ein. Versuchen Sie, den SD-Kartenpfad über die Schaltfläche **„Durchsuchen..“** im Ordnerauswahldialog hinzuzufügen, die den systemeigenen Android-Dateiauswahldialog mit vollem SD-Kartenzugriff verwendet |
| Audio stottert oder springt | Schließen Sie andere im Hintergrund laufende Apps. Stellen Sie bei FLAC-Dateien sicher, dass das Autoradio genügend Rechenleistung hat |
| Internetradio stoppt beim App-Wechsel | Gehen Sie zu Einstellungen → Player → Hintergrund-Audiowiedergabe und stellen Sie sicher, dass es aktiviert ist |

</div>
