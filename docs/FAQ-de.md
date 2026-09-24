---
layout: default
title: "❓ Frequently Asked Questions (FAQ)"
permalink: /docs/FAQ-de.html
---
<div lang="de" dir="ltr" markdown="1">

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="de" %}

# ❓ Häufig gestellte Fragen (FAQ)

---

## Allgemeine Fragen

### Was ist FastMediaSorter?
FastMediaSorter v2 ist eine komplette Shell für ein Android-Gerät: Sie übernimmt den Startbildschirm, spielt deine Medien ab, öffnet Live-Streams, startet deine Apps, spricht mit deiner Uhr, behält das Gerät im Blick und verwaltet jede Datei, die dir gehört - in lokalen Ordnern, auf Netzlaufwerken (SMB/SFTP/FTP) und in Cloud-Speichern (Google Drive, OneDrive, Dropbox).

### Ist sie kostenlos?
Ja! FastMediaSorter v2 ist vollständig kostenlos und quelloffen.

### Welche Android-Version brauche ich?
Standard, Lite und Photos benötigen Android 8.0 (API 26) oder neuer. Die Variante **Legacy** unterstützt Android 6.0 (API 23) oder neuer. **XR / noLegal** benötigt zusätzlich unterstützte Headset-Hardware und den aktuellen Sideload-Laufzeitpfad.

### Braucht sie Internet?
**Nein** für lokale Dateien. **Ja** für Netzlaufwerke und Cloud-Speicher.

### Hat die App Widgets?
Ja! FastMediaSorter v2 bringt eine Vielzahl von Startbildschirm-Widgets mit - finde sie über langes Drücken auf dem Startbildschirm → Widgets → FastMediaSorter. Dazu gehören Ressourcen-Verknüpfungen, Diashow-Starter und mehr.

### Kann die App meinen Startbildschirm ersetzen?
Ja, in den Builds **Standard** und **noLegal**. Aktiviere **Diese App als Startbildschirm festlegen** in **Einstellungen → Allgemein** und wähle FastMediaSorter, wenn Android fragt, welcher Startbildschirm verwendet werden soll. Du erhältst einen Desktop mit Verknüpfungen zu deinen Ordnern, Gadgets wie Uhr und Wetter, ein App-Raster und eine Taskleiste. Schalte die Einstellung aus oder wähle **Launcher-Modus verlassen**, und Android stellt deinen vorherigen Startbildschirm wieder her - dein Desktop-Layout bleibt für das nächste Mal erhalten. Siehe [HOW_TO](HOW_TO-de.md#how-to-use-the-app-as-your-home-screen) für die vollständige Anleitung.

### Wie beende ich den Startbildschirm-Modus der App?
Drei Wege, je nachdem, welchen du zuerst findest:

- Öffne das Startmenü auf dem Desktop, wähle **Launcher-Modus verlassen** und bestätige.
- Schalte **Diese App als Startbildschirm festlegen** in **Einstellungen → Allgemein** wieder aus.
- Öffne Androids eigene Liste der Startbildschirm-Apps unter **Einstellungen → Allgemein → Systemeinstellungen für den Launcher → System → Startbildschirm ändern** und wähle den gewünschten Launcher.

Dein Desktop-Layout bleibt in jedem Fall erhalten, sodass es beim erneuten Aktivieren des Modus wieder so erscheint, wie du es verlassen hast.

### Warum ist mein Tablet nach einem Neustart zum alten Startbildschirm zurückgekehrt?
Weil die Firmware dieses Geräts es zurückgesetzt hat, nicht weil die App die Einstellung verworfen hat. Manche billigen Autoradios und eingebauten Android-Boxen setzen die Startbildschirm-App bei jedem Neustart auf ihre Werkseinstellung zurück, egal was du gewählt hast - keine App kann das überschreiben. Wähle FastMediaSorter nach dem Neustart erneut als Startbildschirm-App, und wenn dein Gerät **Immer** statt **Nur einmal** anbietet, wähle **Immer**. Bleibt es trotzdem nicht dabei, erlaubt dieses Gerät schlicht keinen alternativen Startbildschirm.

### Kann ich meine eigenen Ordner und Playlists auf dem Desktop platzieren?
Ja - dafür ist der Desktop da. Halte ein leeres Feld gedrückt und wähle **Element hinzufügen..**, dann wähle, was du möchtest: einen deiner Ordner, einen Radiostream, eine App, eine Person oder ein Gadget wie die Uhr oder das Wetter. Die neue Zelle landet auf dem Feld, das du gedrückt hast, und bei einem Ordner wählst du auch, ob er im Browse-, Diashow- oder Wiedergabemodus öffnet. Um Dinge später zu verschieben, wähle **Desktop bearbeiten** aus demselben Langdruck-Menü. Siehe [HOW_TO](HOW_TO-de.md#how-to-use-the-app-as-your-home-screen) für die vollständige Anleitung.

---

## Dateioperationen

### Wohin gehen gelöschte Dateien?
Gelöschte Dateien werden am selben Ort in einen Ordner `.trash/` verschoben (Soft-Delete). Sie werden erst endgültig gelöscht, wenn du:
- in **Einstellungen → Verwaltung → Dateilöschung und Papierkorb** auf **"Papierkorb leeren"** tippst, ODER
- den Ordner `.trash/` manuell löschst

### Kann ich ein Löschen/Verschieben rückgängig machen?
**Ja!** Tippe innerhalb weniger Sekunden nach der Operation auf die Schaltfläche **"Rückgängig"** (oder die untere rechte Berührungszone).

> ⚠️ **Hinweis:** Rückgängig ist für das Löschen von Netzwerkdateien nicht verfügbar (sie werden sofort endgültig gelöscht).

### Was ist der Unterschied zwischen Kopieren und Verschieben?
- **Kopieren:** Erstellt ein Duplikat, das Original bleibt an seinem Platz
- **Verschieben:** Verlagert die Datei, entfernt sie vom ursprünglichen Ort

### Was ist der Modus "Alle Dateien"?
Der **Modus "Alle Dateien"** erlaubt es, die App als vollwertigen Dateibrowser über alle Verzeichnisse hinweg zu nutzen. In diesem Modus umgeht die App die Standard-Medienfilter und zeigt alle Dateien an (einschließlich ZIP, RAR, APK, EXE, PDF usw.). Du kannst Standard-Dateioperationen wie Kopieren, Verschieben, Umbenennen, Teilen und Löschen durchführen. Für nicht unterstützte Binärdateien öffnet sich automatisch ein Bottom Sheet, mit dem du die Datei verwalten oder mit externen Anwendungen öffnen kannst.

### Wie finde und entferne ich doppelte Dateien?
Öffne einen Ordner, tippe auf das Überlaufmenü und wähle **Duplikate finden**, um Übereinstimmungen selbst zu überprüfen, oder **Duplikate finden und löschen**, um sie sofort zu entfernen. Es gibt außerdem **Nach Größe löschen..** für eine schnelle Bereinigung allein anhand der Dateigröße. Die automatische Option überspringt die Bestätigung, nutze also zuerst **Duplikate finden**, wenn du vor dem Löschen noch einmal prüfen möchtest. Der Abgleich erfolgt inhaltsbasiert - Größe, dann ein Schnell-Hash, dann eine vollständige SHA-256-Prüfung -, sodass auch umbenannte Kopien gefunden werden.

---

## Netzwerk & Cloud

### Wie verbinde ich mich mit meinem Heim-NAS (Netzlaufwerk)?
1. Tippe auf **"+"** → **Netzwerk** → **SMB / Netzlaufwerk**
2. **Option A - Automatisch:** Tippe auf **"Netzwerk scannen"**, um verfügbare Geräte in deinem Netzwerk automatisch zu finden
3. **Option B - Manuell:** Gib die Serveradresse ein: `\\192.168.1.100\share` oder `smb://192.168.1.100/share`
4. Benutzername und Passwort eingeben
5. Auf "Verbinden" tippen

**Häufige Probleme und Lösungen:**

| Problem | Was zu versuchen ist |
|---------|------------|
| "Verbindung abgelehnt" | Windows-Firewall öffnen → **TCP-Port 445** eingehend zulassen. Oder die Firewall zum Testen vorübergehend deaktivieren |
| "Falsches Passwort" | Versuche, den Benutzernamen leer zu lassen (Gastzugriff). Wenn du ein Microsoft-Konto nutzt, gib deine **vollständige E-Mail-Adresse** als Benutzernamen ein |
| "Host nicht gefunden" | Stelle sicher, dass Handy und PC im **selben WLAN-Router** sind. AP-Isolation (eine Router-Sicherheitseinstellung) kann Geräte-zu-Geräte-Verkehr blockieren - deaktiviere sie in den Router-Einstellungen |
| Scan findet nichts | VPN auf dem Handy deaktivieren. **Netzwerkerkennung** in Windows aktivieren (Systemsteuerung → Netzwerk- und Freigabecenter → Erweiterte Freigabeeinstellungen). Dann versuche, die IP manuell einzugeben |
| Sehr langsames Durchsuchen | Ressource bearbeiten → **Geschwindigkeitstest** ausführen. Bei unter 5 Mbit/s auf das 5-GHz-WLAN-Band des Handys wechseln. Video-Vorschaubilder bei langsamen Verbindungen deaktivieren |
| Funktioniert über WLAN, aber nicht über mobile Daten | Zu erwarten - SMB ist ein reines Lokalnetzwerk-Protokoll, es kann nicht über mobile Daten funktionieren |

→ Vollständige Anleitung: [SMB-Einrichtungsanleitung](howto/scenario-smb-setup-de.md)

### Wie verbinde ich mich mit Google Drive?
1. Tippe auf **"+"** → **Cloud** → **Google Drive**
2. Tippe auf "Mit Google anmelden"
3. Berechtigungen erteilen, wenn danach gefragt wird
4. Deine Drive-Ordner erscheinen

**Hinweis:** Dateien werden NICHT automatisch heruntergeladen - sie werden bei Bedarf gestreamt.

### Wie verbinde ich mich mit OneDrive?
1. Tippe auf **"+"** → **Cloud** → **OneDrive**
2. Tippe auf "Mit Microsoft anmelden"
3. Berechtigungen erteilen, wenn danach gefragt wird
4. Deine OneDrive-Ordner erscheinen

### Wie verbinde ich mich mit Dropbox?
1. Tippe auf **"+"** → **Cloud** → **Dropbox**
2. Tippe auf "Mit Dropbox anmelden"
3. Berechtigungen erteilen, wenn danach gefragt wird
4. Deine Dropbox-Ordner erscheinen

### Kann ich SFTP oder FTP verwenden?
**Ja!** Wähle **SFTP** oder **FTP**, wenn du einen Ordner hinzufügst:
- **SFTP:** Sicher, erfordert einen SSH-Server (Port 22)
- **FTP:** Weniger sicher, älteres Protokoll (Port 21)

### Kann ich PC-Ordner mit der App teilen?
**Ja** - Fast Media Sorter for Windows veröffentlicht ausgewählte PC-Ordner über SFTP und zeigt einen QR-Code / eine `.fmscfg`-Konfiguration an. Nutze auf dem Handy **Vom Companion importieren** oder **QR-Code scannen** auf dem Bildschirm zum Hinzufügen einer Ressource. Siehe die PC-seitige Anleitung: [So veröffentlichst du PC-Ordner für Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html). Verfügbar in Standard, Photos, Legacy, XR/noLegal.

### Warum laden Vorschaubilder für Netzwerkdateien nicht?
Netzwerk-Vorschaubilder werden **bei Bedarf** erzeugt, um Bandbreite zu sparen. Scrolle langsam oder warte ein paar Sekunden, bis sie erscheinen.

Wenn Vorschaubilder überhaupt nie laden:
- Prüfe, ob die Verbindung aktiv ist: tippe auf die Ressource → wenn sich der Ordner öffnet, ist die Verbindung in Ordnung
- Ressource bearbeiten → stelle sicher, dass **"Vorschaubilder laden"** aktiviert ist
- Bei sehr langsamen Verbindungen: Vorschaubilder komplett deaktivieren, um Timeouts zu vermeiden (Ressource bearbeiten → Vorschaubilder deaktivieren)

### Die Verbindung bricht immer wieder ab / Dateien lassen sich während der Wiedergabe nicht öffnen
- Prüfe, ob das WLAN deines Handys stabil ist (kein Wechsel zwischen 2,4- und 5-GHz-Bändern)
- Manche Router trennen inaktive SMB-Sitzungen - Ressource bearbeiten → **"Bei Fehler erneut verbinden"** aktivieren, falls verfügbar
- Für Videowiedergabe über SMB: Geschwindigkeitstest ausführen (Ressource bearbeiten → Geschwindigkeitstest). Du brauchst mindestens 10 Mbit/s für 1080p-Video

---

## Quick Sort & Zielordner

### Was sind "Quick Sort"-Ordner?
Quick-Sort-Ordner sind vorkonfigurierte Zielordner für schnelles Dateisortieren. Du kannst bis zu 30 Ordner mit nummerierten Schaltflächen zuweisen.

### Wie richte ich Quick Sort ein?
**Methode 1:** Einstellungen → Verwaltung → Quick-Sort-Ziele, dann auf **"Zu Quick Sort hinzufügen"** tippen  
**Methode 2:** Beliebigen Ordner bearbeiten → "Für Quick Sort markieren" aktivieren

### Wie nutze ich Quick Sort beim Ansehen von Dateien?
1. Öffne ein Foto/Video im Vollbild
2. Tippe auf eine **nummerierte Schaltfläche** (0-9) im Befehlsfeld, ODER
3. Tippe auf die **untere linke Ecke** (COPY-Zone) oder die **untere Mitte** (MOVE-Zone)

### Kann ich Zifferntasten statt Tippen verwenden?
Ja - schließe eine Hardware-Tastatur, ein Gamepad oder eine TV-Fernbedienung an, und deine Quick-Sort-Schaltflächen werden automatisch nummeriert (0-9). Drücke die passende Ziffer, um die Datei sofort in dieses Ziel zu kopieren oder zu verschieben, genau wie beim Tippen auf die Schaltfläche.

### Quick-Sort-Schaltflächen werden nicht angezeigt
Stelle sicher, dass du zuerst mindestens einen Zielordner hinzugefügt hast: Einstellungen → Verwaltung → Quick-Sort-Ziele, dann **"Zu Quick Sort hinzufügen"**. Schaltflächen erscheinen erst, wenn mindestens ein Ziel konfiguriert ist.

### Ich habe versehentlich eine Datei in den falschen Ordner gesendet
Tippe sofort auf **Rückgängig** (unten rechts im Befehlsfeld) - verfügbar für ein paar Sekunden nach jeder Operation. Wenn du das Zeitfenster verpasst hast, gehe zum Zielordner und verschiebe die Datei manuell zurück.

---

## Berührungszonen

### Was sind "Berührungszonen"?
Berührungszonen sind unsichtbare Bereiche auf dem Bildschirm, die bei Berührung Aktionen auslösen. Der Bildschirm ist in ein 3x3-Raster unterteilt:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
└─────────┴─────────┴─────────┘
```

### Wie sehe ich die Berührungszonen?
Einstellungen → Player → **"Berührungszonen-Overlay immer anzeigen"**

### Kann ich Berührungszonen deaktivieren?
Ja, nutze stattdessen einfach die **Schaltflächen im Befehlsfeld**. Berührungszonen sind optional.

---

## Bildschirm- & Sprachaufnahme

### Was ist der Randstreifen auf der linken Seite?
Es ist ein Schnellaufnahme-Menü, das du mit einer diagonalen Wischgeste vom linken Bildschirmrand öffnest. Aktiviere es in **Einstellungen → Verwaltung → Randbildschirmgesten → Gesten-Overlay**. Aus dem Menü kannst du einen Screenshot machen, ein Foto schießen, das aktuelle Bild zuschneiden und teilen, eine App- oder Panel-Verknüpfung öffnen oder eine Bildschirm-, Video- oder Sprachaufzeichnung starten - alles ohne das zu verlassen, was du gerade ansiehst. Verfügbar in Standard und XR/noLegal.

### Wie nehme ich eine schnelle Sprachnotiz auf?
Drei Wege: der Eintrag **Sprachaufnahme** im Überlaufmenü, das Startbildschirm-Widget **Schnellrekorder**, oder die Randgesten-Aktion **Audioaufnahme starten**. Wie auch immer du sie startest, ein schwebendes **Stopp**-Element bleibt auf dem Bildschirm - selbst über einer anderen App -, bis du darauf tippst, um zu speichern.

---

## Eingabe & Steuerung

### Unterstützt sie physische Tastaturen und Gamepads?
**Ja!** Vollständige Tastatur-, Maus- und Gamepad-Eingabe ist auf allen Bildschirmen verfügbar. Drücke **F1** auf jedem Bildschirm, um die aktiven Tastenbelegungen für diesen Bereich zu sehen.

### Wie belege ich Steuerungen neu / ändere Tastenbelegungen?
Einstellungen → **Verwaltung** → **Steuerung & Tastenbelegung** - jede Aktion einer anderen Taste, Schaltfläche oder Gamepad-Eingabe neu zuweisen. Die App bringt 70 eingebaute Standardbelegungen mit; tippe auf **Zurücksetzen**, um sie wiederherzustellen. Konflikte werden automatisch hervorgehoben.

### Wie lade ich eine Mediendatei von einer URL herunter?
Teile einen beliebigen `http(s)`-Link über das Android-**Teilen-Menü** (aus einem Browser, Messenger oder einer beliebigen App) mit FastMediaSorter. FastMediaSorter lädt die Datei herunter und bietet an, sie in einer deiner konfigurierten Ressourcen zu speichern.

---

## Leistung & Speicher

### Wie finde ich eine bestimmte Datei nach Namen?
Nutze das **Filter**-Panel in Browse: Tippe in der Symbolleiste auf das Filter-Symbol und gib einen beliebigen Teil des Dateinamens in das Namensfeld ein - die Liste aktualisiert sich sofort. Eine separate Suchleiste ist nicht nötig; der Filter deckt dieses Szenario vollständig ab.

### Warum ist die App bei 5000+ Dateien langsam?
Die App lädt Dateien in Stapeln mittels **Paginierung**. Bei sehr großen Sammlungen:
- "Vorschaubilder deaktivieren" für diesen Ordner aktivieren
- Filter nutzen, um Ergebnisse einzugrenzen
- Nach Datum sortieren (neueste zuerst) - das lädt zuerst die neuesten Dateien und vermeidet, im Voraus den gesamten Ordner zu scannen

### Die App stürzt ab oder friert ein
1. App erzwungen schließen und neu öffnen
2. Wenn sie bei einem bestimmten Ordner abstürzt: Dieser Ordner könnte eine beschädigte Datei enthalten - versuche, Dateien einzeln zu öffnen, um sie zu identifizieren
3. Cache leeren: Einstellungen → Allgemein → **"Cache leeren"** - das behebt die meisten Stabilitätsprobleme nach Updates
4. Falls Abstürze weiterhin auftreten: über GitHub Issues melden (Link am Ende dieser Seite) - füge eine Beschreibung bei, was du gerade getan hast, als es abgestürzt ist

### Wie viel Speicher nutzt der Vorschaubild-Cache?
**Standard:** 2 GB (konfigurierbar in den Einstellungen)

### Wie leere ich den Cache?
Einstellungen → Allgemein → **"Cache leeren"**

---

## Favoriten

### Wie markiere ich Dateien als Favoriten?
Tippe beim Ansehen einer Datei auf das **Stern-Symbol**.

### Wo sehe ich alle meine Favoriten?
Hauptmenü → Tab **"Favoriten"**

---

## Sicherheit & Datenschutz

### Kann ich Ordner mit einem Passwort schützen?
**Ja!** Ordner bearbeiten → **PIN-Code** festlegen (4-6 Ziffern)

### Werden meine Daten gesammelt?
**Nein.** FastMediaSorter sammelt oder sendet KEINE persönlichen Daten.

### Benötigen Kontakt-Verknüpfungen auf dem Launcher-Desktop Zugriff auf meine Kontakte?
**Nein.** Das Anheften einer Person an den Launcher-Desktop erfordert überhaupt keine Kontakte-Berechtigung. Du wählst die Person in Androids eigener Kontaktauswahl, die App liest diesen einen Eintrag einmal und behält ihn als Momentaufnahme auf der Zelle - sie kann dein Adressbuch nie durchsuchen. Beim Anrufen wird die Nummer verwendet, die du in der Auswahl gewählt hast, sodass die Zelle genau diese Nummer wählt.

Eine optionale **Kontakte**-Berechtigungsgruppe existiert zwar und kann bei Bedarf angefragt werden, unter **Einstellungen → Allgemein → Berechtigungen & Zugriff**. Sie zu verweigern ändert nichts am oben beschriebenen Verhalten - Verknüpfungen funktionieren weiterhin genauso ohne Berechtigung.

### Speichert die App GPS-Standorte in meinen Fotos?
Nur wenn du es aktivierst. Aktiviere in **Einstellungen → Verwaltung → Fotografie** die Fotoaufnahme und schalte darunter **Fotos geotaggen** ein - die App fragt sofort nach der Standortberechtigung, nicht erst beim Auslösen. Der Bildinfo-Bildschirm eines geotagten Fotos zeigt das Aufnahmedatum und den GPS-Standort aus den EXIF-Daten des Fotos als antippbaren Link, der deine Karten-App oder deinen Browser öffnet.

### Kann ich sehen, wie ich die App nutze?
Ja - das ist opt-in und standardmäßig aus: Aktiviere **Statistikerfassung** in **Einstellungen → Allgemein**, um ein lokales Nutzungs-Dashboard zu öffnen: sortierte Dateien, freigegebener Speicherplatz, Wiedergabezeit und mehr, aufgeschlüsselt nach Medientyp. Nichts wird automatisch gesendet; **An den Autor senden** oder **Exportieren** teilt nur dann eine Zusammenfassung, wenn du es so wählst.

---

## Automatische Übersetzung

### Wie funktioniert die Übersetzung?
Zwei Schritte, beide auf deinem Gerät:
- **Tesseract** liest den Text aus dem Bild, in jeder unterstützten Sprache (Englisch, Russisch, Ukrainisch, Bulgarisch, Weißrussisch).
- **Google ML Kit** übersetzt anschließend das Gelesene.

### Was macht die Quellsprache "Auto"?
"Auto" liest den Text mit dem englischen Modell und ermittelt dann die Sprache des Gelesenen für die Übersetzung. Bei kyrillischem Text wähle die Quellsprache explizit aus (zum Beispiel **Russisch** oder **Ukrainisch**) - sonst werden Buchstaben als ihre lateinischen Doppelgänger gelesen.

### Funktioniert sie offline?
**Ja.** Du brauchst nur einmal Internet, um das Textmodell für deine Quellsprache und das Übersetzungsmodell für dein Sprachpaar herunterzuladen.

### Warum ist die Übersetzung manchmal langsamer?
Die erste Nutzung einer Sprache lädt ihr Textmodell, und große oder detaillierte Bilder brauchen länger zum Lesen. Spätere Durchläufe mit derselben Sprache starten schneller.

### Was ist der lupenartige Übersetzungsmodus?
Der **lupenartige Modus** zeigt Übersetzungen als Overlay über dem Originalbild an, ähnlich wie bei Google Lens. So siehst du den übersetzten Text in seinem ursprünglichen Kontext und an seiner ursprünglichen Position. Du kannst ihn in **Einstellungen → Medien → Sonstiges** aktivieren (der Schalter "Lupenartiges Overlay").

Der **Standardmodus** zeigt Übersetzungen in einer separaten Textansicht unterhalb des Bildes.

---

## Hintergrundmusik für die Diashow

### Wie füge ich Diashows Hintergrundmusik hinzu?
1. Füge einen Ordner mit Audiodateien als Ressource hinzu
2. Gehe zu **Einstellungen → Medien → Bilder**
3. Aktiviere **"Musik während Diashow abspielen"**
4. Wähle deine Musikressource aus dem Dropdown
5. Starte eine beliebige Diashow - die Musik spielt automatisch!

### Kann ich Musik von Netzlaufwerken oder Cloud-Speichern nutzen?
**Ja!** Die App handhabt Netzwerkdateien automatisch, indem sie sie vor der Wiedergabe in den Cache herunterlädt. Das funktioniert mit SMB, SFTP, FTP, Google Drive, OneDrive und Dropbox.

### Wie überspringe ich Titel während der Diashow?
Tippe auf den während der Diashow angezeigten **Titelnamen**, um zu einem anderen zufälligen Titel aus deiner Musikressource zu springen.

### Funktioniert sie mit allen Varianten?
**Fast.** Diashow-Musik benötigt Audio-Unterstützung:
- **Standard**, **Legacy**, **XR / noLegal** - volle Audio-Unterstützung, einschließlich Wiedergabe, die im Hintergrund weiterläuft
- **Lite** - spielt lokale Audiodateien und Songtexte ab, hat aber keinen Hintergrund-Wiedergabedienst, sodass der Ton stoppt, wenn die App den Vordergrund verlässt
- **Photos** - überhaupt keine Audio-Unterstützung, daher gibt es keine Diashow-Musik

---

## Internet-Streams

### Spielt FastMediaSorter Internetradio ab?
Ja. Der **Streams**-Bildschirm spielt http/https-Audiostreams (mp3/aac), Icecast-/Shoutcast-Radio mit ICY-Jetzt-läuft-Metadaten, HLS (.m3u8) und DASH-VOD sowie RTSP-Quellen. Verfügbar in Standard, Legacy und XR / noLegal. Lite und Photos haben überhaupt keinen Streams-Bildschirm - die Funktion fehlt dort komplett, sie ist nicht nur auf einige Protokolle beschränkt.

### Wie öffne ich den Streams-Bildschirm?
Tippe auf **Streams** im Dropdown des Hauptfensters (sichtbar, wenn Streams aktiviert ist). Du erreichst ihn auch über **Einstellungen > Medien > Streams**, wo der Hauptschalter liegt.

### Wie füge ich einen Radiosender hinzu?
Tippe im Streams-Bildschirm auf **⋮** am Ende der Symbolleiste, wähle **Stream hinzufügen** und füge die Sender-URL ein. Tippe auf Speichern. Der Sender erscheint sofort in der Liste.

### Kann ich eine Playlist importieren?
Ja - tippe auf **⋮ > Von URL importieren** und gib eine entfernte `.m3u`-Adresse ein. Dasselbe Menü enthält **FastMediaSorter-Katalog aktualisieren** für die kuratierte Liste (mit Themen- und Sprach-Chips), die auch über **Einstellungen > Extensions** oder den Willkommens-Onboarding-Bildschirm verfügbar ist.

### Ein Stream spielt nicht - was tue ich?
Falls ein Stream fehlschlägt, erscheint ein Dialog mit den Optionen **Erneut versuchen**, **Entfernen** und **Abbrechen**. Protokollübergreifende 301-Weiterleitungen werden automatisch behandelt. Ist der Host tot oder sehr langsam, läuft der Katalog-Import schnell in ein Timeout, statt zu hängen.

### Läuft das Radio weiter, wenn ich den Streams-Bildschirm verlasse?
Hängt von **Einstellungen > Player > Hintergrund-Audiowiedergabe** ab. Ist Hintergrund-Audio AN, läuft die Wiedergabe weiter. Ist es AUS, stoppt das Verlassen des Bildschirms den Stream und bietet die Wahl Stopp / Weiterspielen an - dasselbe Verhalten wie beim lokalen Audioplayer.

### Kann ich Live-Vorschaubilder für Streams sehen?
Schalte den Symbolleisten-Umschalter der Streams auf die **Raster**-Ansicht - jeder Kanal wird dann als Kachel mit seinem zuletzt erfassten Bild angezeigt, sodass du auf einen Blick siehst, was gerade läuft. Die Kachel bleibt auch sichtbar, nachdem du die App geschlossen und wieder geöffnet hast, und aktualisiert sich mit einer neuen Aufnahme, sobald der Stream wieder live ist.

### Kann ich einen Stream auf meinen Fernseher casten?
Ja, für Video-Streams - tippe im Player auf **Cast** und wähle ein Chromecast-Gerät im selben WLAN. RTSP-Streams können nicht gecastet werden; die Schaltfläche erscheint nur für Formate, die der Chromecast-Empfänger unterstützt.

---

## Wear OS

### Funktioniert FastMediaSorter auf Wear OS Smartwatches?
**Ja!** FastMediaSorter v2 enthält eine Wear OS Begleiter-App und ist von einem einfachen lokalen Dateibetrachter zu einem echten zweiten Bildschirm für deine Medien herangewachsen.

### Was kann ich auf der Uhr tun?
- **Durchsuchen und wiedergeben** - die Ordner und Favoriten deines gekoppelten Handys oder den eigenen lokalen Speicher der Uhr, als Vorschaubild-Raster mit Suche, Filter und Sortierung. Audio und Video spielen mit Zufallswiedergabe, Lünetten-Lautstärke und einem Bildschirm-aus-Modus, der den Ton weiterlaufen lässt.
- **Dateien in beide Richtungen verschieben** - sende ein Foto, Video oder einen Titel vom Handy direkt an die Uhr (direkt aus dem Teilen-Menü), oder kopiere eine Datei von der Uhr zurück in einen Handy-Ordner deiner Wahl.
- **Eine Sprachnotiz am Handgelenk aufnehmen** - sie wartet auf der Uhr, bis du sie ans Handy sendest, sodass während der Aufnahme nichts verloren geht.
- **Live-Streams abspielen** - Radio und Video aus deinem Streams-Katalog spielen direkt aus der eigenen Kanalliste der Uhr, mit deinen angehefteten Favoriten oben.
- **Auf einen Blick sehen, ohne die App zu öffnen** - füge eine FastMediaSorter-Kachel zum Wischbereich deines Zifferblatts hinzu, oder ihre Komplikation zu einem kompatiblen Zifferblatt.
- **Kleine eingebaute Werkzeuge** - ein Taschenrechner, ein Netzwerkmonitor und das Minispiel erhalten jeweils einen eigenen Uhr-Bildschirm.

Einstellungen, die du auf dem Handy änderst, werden mit der Uhr synchronisiert und zurück, sodass du alles nur einmal einrichten musst.

**Hinweis:** Die Uhr öffnet nie von sich aus Cloud-Ressourcen - sie hat keinen eigenen Cloud-Client, und das Handy gibt seine Cloud-Ordner nicht an sie weiter; eine Cloud-Datei erreicht die Uhr nur, wenn du sie auf dem Handy öffnest und deine Uhr unter "Senden an.." auswählst. In der Vollversion der Uhr-App (direkte APK) verbindet sich die Uhr tatsächlich von sich aus mit SMB-, FTP- und SFTP-Freigaben über WLAN - den Netzwerkressourcen, die du ihr vom Handy aus sendest. Die Google-Play-Version der Uhr-App ist eine kleine Erstveröffentlichung (Taschenrechner, Stoppuhr, Minispiel und Einstellungen) und durchsucht noch keine Medien.

---

## EPUB-E-Books

### Wie aktiviere ich EPUB-Unterstützung?
Einstellungen → Medien → **Dokumente** → **"EPUB-E-Books unterstützen"**

**Hinweis:** Starte die App nach der Aktivierung neu, damit die Änderungen wirksam werden.

### Wie lese ich ein EPUB-Buch?
1. Füge einen Ordner mit .epub-Dateien als Ressource hinzu
2. Öffne den Ordner - du siehst EPUB-Dateien mit dem "E"-Abzeichen
3. Tippe auf eine beliebige EPUB-Datei, um sie im Reader zu öffnen

### Kann ich zwischen Kapiteln navigieren?
**Ja!** Nutze:
- **Zurück-/Weiter-Schaltflächen** unten
- **Nach links/rechts wischen**, um das Kapitel zu wechseln
- **TOC-Schaltfläche** (📋-Symbol), um das Inhaltsverzeichnis zu öffnen

### Kann ich die Schriftgröße anpassen?
**Ja!** Nutze beim Lesen die Schaltflächen **-A/+A** unten, um die Schriftgröße zu verringern/erhöhen (Bereich 6-144 px). Einstellungen werden pro Buch gespeichert.

### Kann ich Text in EPUB suchen?
**Ja!** Tippe auf die **Such-Schaltfläche** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom">, um das Suchfeld zu öffnen. Gib deine Anfrage ein und navigiere mit den Zurück-/Weiter-Schaltflächen durch die Treffer.

### Funktioniert es mit Netzwerk-/Cloud-Dateien?
**Ja!** EPUB-Dateien werden beim Öffnen von SMB/SFTP/FTP/Cloud-Speicher automatisch in den Cache heruntergeladen.

### Merkt sie sich meine Leseposition?
**Ja!** Die App speichert das letzte Kapitel, das du gelesen hast. Wenn du das Buch erneut öffnest, macht sie dort weiter, wo du aufgehört hast.

### Was ist mit dunklem/hellem Theme?
Der EPUB-Betrachter passt sich automatisch deinem App-Theme an (Einstellungen → Allgemein → Farbthema).

---

## Geplante Operationen

### Was sind geplante Operationen?
Zeitbasierte Automatisierungsregeln, die Kopier-, Verschiebe- oder Löschoperationen zwischen beliebigen deiner Ressourcen (lokale Ordner, NAS, Cloud) nach einem wiederkehrenden Zeitplan ausführen - selbst wenn die App geschlossen ist.

### Wo richte ich geplante Operationen ein?
Einstellungen → **Verwaltung** → **Geplante Operationen nach Zeitplan**. Tippe auf **"+"**, um eine neue Regel hinzuzufügen.

### Läuft sie, wenn meine App geschlossen ist?
**Ja.** Operationen werden über Androids **WorkManager** geplant, der sie im Hintergrund ausführt, unabhängig davon, ob die App geöffnet ist.

### Warum lief eine geplante Operation nicht zur genauen Zeit?
Android kann WorkManager-Aufgaben um einige Minuten verschieben, um den Akku zu schonen. Für zuverlässigere Zeitplanung erteile der App die Ausnahme von der **Akku-Optimierung** (Einstellungen → Allgemein → Akku-Optimierung). Das Mindestintervall beträgt 15 Minuten.

### Geplante Operation lief, hat aber 0 Dateien kopiert
Das ist meist korrekt - es bedeutet, dass alle Dateien bereits am Ziel vorhanden waren (die Operation nutzt standardmäßig "vorhandene überspringen"). Zur Überprüfung: Sieh dir das Operationsprotokoll an und vergleiche die Anzahl "übersprungen" mit "kopiert".

Falls du erwartet hast, dass neue Dateien kopiert werden, es aber nicht geschah:
- Stelle sicher, dass die **Quelle** auf die richtige Ressource eingestellt ist (z. B. die virtuelle Ressource "Kamerafotos" - kein manueller Pfad, der falsch sein könnte)
- Prüfe, ob die Zielressource (SMB / Cloud) zur geplanten Zeit erreichbar war - war WLAN aus, wird der Lauf übersprungen und beim nächsten Mal wiederholt

### Kann ich sehen, was verarbeitet wurde?
**Ja.** Tippe im Bereich der geplanten Operationen auf **"Protokoll anzeigen"**, um eine zeitgestempelte Historie jedes Laufs samt Ergebnissen pro Datei zu sehen.

---

## Wetter-Baustein

### Woher kommt das Wetter?
Der Desktop-Wetter-Baustein nutzt **Open-Meteo.com** - einen kostenlosen, schlüssellosen Wetterdienst. Wetterdaten von Open-Meteo.com (CC-BY 4.0).

### Verfolgt die App meinen Standort?
**Nein.** Der Ort ist derjenige, den du selbst eingibst, und es wird keine Standortberechtigung angefragt. Der Baustein aktualisiert sich etwa alle 20 Minuten und zeigt bei fehlender Verbindung den letzten Stand mit dem Hinweis "Zuletzt bekannt" an. Ein Tipp darauf öffnet die Wetter-App des Geräts.

---
## Noch Fragen?

Keine Antwort oben gefunden, oder funktioniert etwas nicht wie beschrieben? **Bitte melde dich** - jede Nachricht wird gelesen, und die meisten Probleme werden behoben.

- � **How-To-Anleitungen** (Schritt-für-Schritt-Aufgaben): [HOW_TO-de.md](HOW_TO-de.md)
- 🚀 **Schnellstart:** [QUICK_START-de.md](QUICK_START-de.md)
- 🔧 **Problembehebung:** [TROUBLESHOOTING-de.md](TROUBLESHOOTING-de.md)
- �📧 **E-Mail:** [sza@ukr.net](mailto:sza@ukr.net) - für alles: Einrichtungshilfe, Fehlerbeschreibungen, Funktionswünsche
- 🌐 **Seite des Autors:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Fehlerbericht:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - bevorzugt für reproduzierbare Fehler; gib die Android-Version an und was du getan hast
- 📖 **Vollständige Dokumentation:** [Dokumentationsportal](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vermisst du eine Funktion?** Schreib uns - viele Funktionen der App wurden hinzugefügt, weil jemand danach gefragt hat. Wenn es für den Anwendungsfall sinnvoll ist, wird es gebaut.

</div>
