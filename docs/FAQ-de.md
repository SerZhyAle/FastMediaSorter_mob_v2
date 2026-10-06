---
layout: default
title: "❓ Frequently Asked Questions (FAQ)"
permalink: /docs/FAQ-de.html
lang: de
---

<sub class="doc-stamp">26.10.06 14:51</sub>

<div lang="de" markdown="1">

<div lang="de" dir="ltr" markdown="1">

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="de" %}

# ❓ Häufig gestellte Fragen (FAQ)

---

## Allgemeine Fragen

### Was ist FastMediaSorter?
FastMediaSorter verbindet Medienwiedergabe mit lokaler, Netzwerk- und Cloud-Dateiverwaltung. Launcher, Streams und Uhrenanbindung hängen von der Edition ab. Dateizugriff benötigt Berechtigungen; den Android-Startbildschirm ersetzen Sie nur durch Ihre ausdrückliche Auswahl.

### Ist sie kostenlos?
Ja! FastMediaSorter v2 ist vollständig kostenlos und quelloffen.

### Welche Android-Version brauche ich?
Die Mindestversionen der aktuellen Quellkonfiguration stehen unten. Immersives VR/XR benötigt zusätzlich ein kompatibles Headset/Runtime; **noLegal ist nicht auf Headsets beschränkt**. Eine Quellvariante garantiert kein veröffentlichtes APK.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK (EN)](TECHNICAL_REQUIREMENTS.html)

### Braucht sie Internet?
Lokale Dateien brauchen kein Internet. SMB/SFTP/FTP im LAN benötigen eine erreichbare Netzwerkverbindung, kein öffentliches Internet. Cloud und Internetstreams benötigen Internet; die Funktionen hängen von der Edition ab.

### Hat die App Widgets?
Ja! FastMediaSorter v2 bringt eine Vielzahl von Startbildschirm-Widgets mit - finde sie über langes Drücken auf dem Startbildschirm → Widgets → FastMediaSorter. Dazu gehören Ressourcen-Verknüpfungen, Diashow-Starter und mehr.

### Kann die App meinen Startbildschirm ersetzen?
In **Standard/noLegal**: **Einstellungen → Allgemein → Startfenster → Startbildschirm des Geräts**, dann FastMediaSorter als Android-Home-App wählen. Der **Desktop als Hauptfenster** ersetzt den System-Launcher nicht.

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Wie beende ich den Startbildschirm-Modus der App?
Im Startmenü **Launcher-Modus beenden**, ein anderes Startfenster oder in Android eine andere Home-App wählen. Das Desktop-Layout bleibt gespeichert; der Android-Einstellungspfad ist geräteabhängig.

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Warum ist mein Tablet nach einem Neustart zum alten Startbildschirm zurückgekehrt?
Prüfen Sie Androids Standard-Home-App und das Startfenster der App. Falls angeboten, wählen Sie **Immer**. Firmware, Updates oder zurückgesetzte Standards können die Auswahl ändern; ein Neustart beweist keine Ursache. Beim Melden Modell und Android-Version angeben.

### Kann ich meine eigenen Ordner und Playlists auf dem Desktop platzieren?
Ja - dafür ist der Desktop da. Halte ein leeres Feld gedrückt und wähle **Element hinzufügen..**, dann wähle, was du möchtest: einen deiner Ordner, einen Radiostream, eine App, eine Person oder ein Gadget wie die Uhr oder das Wetter. Die neue Zelle landet auf dem Feld, das du gedrückt hast, und bei einem Ordner wählst du auch, ob er im Browse-, Diashow- oder Wiedergabemodus öffnet. Um Dinge später zu verschieben, wähle **Desktop bearbeiten** aus demselben Langdruck-Menü. Siehe [HOW_TO](HOW_TO-de.html#how-to-use-the-app-as-your-home-screen) für die vollständige Anleitung.

---

## Dateioperationen

### Wohin gehen gelöschte Dateien?
Bei aktiviertem Papierkorb nutzen unterstützte gewöhnliche lokale Pfade `.trash/`. **Endgültiges Löschen**, `content://`, SMB/SFTP/FTP/Cloud und geschützte `/Android/media/`-Pfade nutzen diese Soft-Delete-Regel nicht. Das Leeren des Papierkorbs ist unwiderruflich; nicht jede Löschung lässt sich wiederherstellen.

### Kann ich ein Löschen/Verschieben rückgängig machen?
Sofort die sichtbare Aktion **Rückgängig** nutzen, sofern angeboten. Verfügbarkeit hängt von Vorgang, Bildschirm und Pfaden ab. Endgültiges sowie Netzwerk-/document-tree-Löschen lässt sich nicht über den lokalen Papierkorb rückgängig machen; Netzwerk-/Cloud-Dateitransfers sind nicht immer umkehrbar. Rückgängig ersetzt kein Backup.

### Was ist der Unterschied zwischen Kopieren und Verschieben?
- **Kopieren:** Erstellt ein Duplikat, das Original bleibt an seinem Platz
- **Verschieben:** Verlagert die Datei, entfernt sie vom ursprünglichen Ort

### Was ist der Modus "Alle Dateien"?
Alle Dateien entfernt Medienfilter **in zugänglichen Ressourcen**, umgeht aber keine Android-Berechtigungen. Fremdformate können verwaltet oder extern geöffnet werden; angezeigte APK/EXE/Archive sind nicht automatisch ausführbar oder entpackbar.

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

Ein autorisiertes NAS/Windows-Konto und eine erreichbare SMB-Freigabe verwenden. TCP **445** nur im vertrauenswürdigen privaten Netz und benötigten Subnetz freigeben; **Firewall nicht ausschalten und SMB nicht ins Internet exponieren**. Freigaberechte, Adresse, VPN-Routen und Gastnetz-Isolation prüfen. Ein privates VPN ermöglicht auch Zugriff über Mobilfunk.

[SMB-Einrichtungsanleitung](howto/scenario-smb-setup-de.html)

### Wie verbinde ich mich mit Google Drive?
1. Tippe auf **"+"** → **Cloud** → **Google Drive**
2. Tippe auf "Mit Google anmelden"
3. Berechtigungen erteilen, wenn danach gefragt wird
4. Deine Drive-Ordner erscheinen

Dateien werden bei Bedarf geöffnet, können aber für Ansicht, Vorschaubilder oder Wiedergabe im App-Cache landen. Das ist keine automatische Synchronisierung des gesamten Drive.

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
**Ja** - Fast Media Sorter for Windows veröffentlicht ausgewählte PC-Ordner über SFTP und zeigt einen QR-Code / eine `.fmscfg`-Konfiguration an. Nutze auf dem Handy **Vom Companion importieren** oder **QR-Code scannen** auf dem Bildschirm zum Hinzufügen einer Ressource. Siehe die PC-seitige Anleitung: [So veröffentlichst du PC-Ordner für Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Warum laden Vorschaubilder für Netzwerkdateien nicht?
Netzwerk-Vorschaubilder werden **bei Bedarf** erzeugt, um Bandbreite zu sparen. Scrolle langsam oder warte ein paar Sekunden, bis sie erscheinen.

Wenn Vorschaubilder überhaupt nie laden:
- Prüfe, ob die Verbindung aktiv ist: tippe auf die Ressource → wenn sich der Ordner öffnet, ist die Verbindung in Ordnung
- Ressource bearbeiten → stelle sicher, dass **"Vorschaubilder laden"** aktiviert ist
- Bei sehr langsamen Verbindungen: Vorschaubilder komplett deaktivieren, um Timeouts zu vermeiden (Ressource bearbeiten → Vorschaubilder deaktivieren)

### Die Verbindung bricht immer wieder ab / Dateien lassen sich während der Wiedergabe nicht öffnen
WLAN, Server, Zugangsdaten und VPN-Routen prüfen. Ressourcengeschwindigkeit testen und Vorschaubilder reduzieren. Bandbreite hängt von Bitrate/Codec ab, nicht nur Auflösung; **10 Mbit/s ist keine allgemeine 1080p-Mindestanforderung**.

---

## Quick Sort & Zielordner

### Was sind "Quick Sort"-Ordner?
Quick-Sort-Ordner sind vorkonfigurierte Zielordner für schnelles Dateisortieren. Du kannst bis zu 10 Ordner mit nummerierten Schaltflächen zuweisen.

### Wie richte ich Quick Sort ein?
**Methode 1:** Einstellungen → Verwaltung → Quick-Sort-Ziele, dann auf **"Zu Quick Sort hinzufügen"** tippen  
**Methode 2:** Beliebigen Ordner bearbeiten → "Für Quick Sort markieren" aktivieren

### Wie nutze ich Quick Sort beim Ansehen von Dateien?
Datei öffnen und Ziel auf der Befehlsleiste wählen. Vor Bestätigung **Kopieren** oder **Verschieben** prüfen. Touchzonen hängen von Medientyp und Modus ab; unten links bedeutet nicht immer Kopieren.

### Kann ich Zifferntasten statt Tippen verwenden?
Ja - schließe eine Hardware-Tastatur, ein Gamepad oder eine TV-Fernbedienung an, und deine Quick-Sort-Schaltflächen werden automatisch nummeriert (0-9). Drücke die passende Ziffer, um die Datei sofort in dieses Ziel zu kopieren oder zu verschieben, genau wie beim Tippen auf die Schaltfläche.

### Quick-Sort-Schaltflächen werden nicht angezeigt
Stelle sicher, dass du zuerst mindestens einen Zielordner hinzugefügt hast: Einstellungen → Verwaltung → Quick-Sort-Ziele, dann **"Zu Quick Sort hinzufügen"**. Schaltflächen erscheinen erst, wenn mindestens ein Ziel konfiguriert ist.

### Ich habe versehentlich eine Datei in den falschen Ordner gesendet
**Rückgängig** sofort nutzen, wenn angeboten. Sonst Quelle/Ziel prüfen und manuell zurückverschieben. Beim Kopieren bleibt das Original; keine Kopie vor Prüfung löschen.

---

## Berührungszonen

### Was sind "Berührungszonen"?
Die Karte hängt von Medien und Modus ab: Bilder können 3×3 nutzen, Video/Audio reservieren Platz für Wiedergabe und pausieren in der Mitte. Die Befehlsleiste nutzt drei Spalten; Dokumente nutzen Wischgesten ohne Tippzonen. Das Overlay zeigt die aktive Karte.

### Wie sehe ich die Berührungszonen?
Einstellungen → Player → **"Berührungszonen-Overlay immer anzeigen"**

### Kann ich Berührungszonen deaktivieren?
Das Neun-Zonen-Raster lässt sich ausschalten; verwenden Sie die Befehlsleiste. **Nicht alle Gesten werden deaktiviert**: die Drei-Spalten-Alternative behält Navigation/Mediensteuerung; Dokumente haben eigene Wischgesten.

---

## Bildschirm- & Sprachaufnahme

### Was ist der Randstreifen auf der linken Seite?
Es ist ein Schnellaufnahme-Menü, das du mit einer diagonalen Wischgeste vom linken Bildschirmrand öffnest. Aktiviere es in **Einstellungen → Verwaltung → Randbildschirmgesten → Gesten-Overlay**. Aus dem Menü kannst du einen Screenshot machen, ein Foto schießen, das aktuelle Bild zuschneiden und teilen, eine App- oder Panel-Verknüpfung öffnen oder eine Bildschirm-, Video- oder Sprachaufzeichnung starten - alles ohne das zu verlassen, was du gerade ansiehst. Verfügbar in Standard und XR/noLegal.

### Wie nehme ich eine schnelle Sprachnotiz auf?
Drei Wege: der Eintrag **Sprachaufnahme** im Überlaufmenü, das Startbildschirm-Widget **Schnellrekorder**, oder die Randgesten-Aktion **Audioaufnahme starten**. Wie auch immer du sie startest, ein schwebendes **Stopp**-Element bleibt auf dem Bildschirm - selbst über einer anderen App -, bis du darauf tippst, um zu speichern.

---

## Eingabe & Steuerung

### Unterstützt sie physische Tastaturen und Gamepads?
Tastatur, Maus und Gamepad hängen von Bildschirm und Gerät ab. **F1** zeigt auf unterstützten Oberflächen verfügbare Belegungen; nicht jede Taste funktioniert in jedem Dialog.

### Wie belege ich Steuerungen neu / ändere Tastenbelegungen?
**Einstellungen → Verwaltung → Steuerung & Tastenbelegung**: unterstützte Aktionen ändern. **Reset** stellt Standards wieder her, Konflikte werden markiert. Die aktuelle Liste gilt, nicht eine feste Zahl von 70.

### Wie lade ich eine Mediendatei von einer URL herunter?
Eine unterstützte `http(s)`-URL über Android teilen. Ein direktes Downloadfile ist keine Webseite, kein geschütztes Video und kein DRM-Stream. Downloads und beschreibbare Ziele hängen von Edition, URL und Berechtigungen ab.

---

## Leistung & Speicher

### Wie finde ich eine bestimmte Datei nach Namen?
Nutze das **Filter**-Panel in Browse: Tippe in der Symbolleiste auf das Filter-Symbol und gib einen beliebigen Teil des Dateinamens in das Namensfeld ein - die Liste aktualisiert sich sofort. Eine separate Suchleiste ist nicht nötig; der Filter deckt dieses Szenario vollständig ab.

### Warum ist die App bei 5000+ Dateien langsam?
Große Ordner brauchen Dateiliste, Metadaten und Vorschaubilder. Ressourcen eingrenzen, filtern und Vorschaubilder reduzieren. Datumsortierung **garantiert nicht**, dass der gesamte Ordner ungescannt bleibt; Quelle und Formate bestimmen die Leistung.

### Die App stürzt ab oder friert ein
App neu öffnen, Speicherplatz, Berechtigungen und Verbindung prüfen. Cacheleeren hilft bei alten Vorschaubildern, behebt aber nicht jeden Absturz. App-Version/Edition, Android, Ressource und Schritte melden; Zugangsdaten/private Pfade aus Logs entfernen.

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
Die Ressourcen-**PIN** schützt den App-Zugriff, **verschlüsselt keine Dateien** und sperrt andere Apps/autorisierte Servernutzer nicht aus. Für Schutz außerhalb der App Geräte-/Speicherverschlüsselung nutzen.

### Werden meine Daten gesammelt?
Nutzungsstatistiken werden nicht automatisch an den Autor gesendet. Optionale Statistik bleibt lokal bis Export/Versand. Cloud, Streams und Wetter kontaktieren die gewählten Anbieter und übertragen nötige Anfragen. Details stehen in der Datenschutzerklärung.

[Datenschutzerklärung (EN)](PRIVACY_POLICY.html)

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

Verfügbarkeit hängt von Edition/Geräteklasse ab. ML-Kit-Übersetzung ist hier nur für Telefone, Tablets, Chromebooks und Desktopgeräte erlaubt, nicht TV, Auto, Uhren oder XR. Separate OCR benötigt API 26+, mindestens 3 GB RAM und kein low-RAM-Gerät.

### Was macht die Quellsprache "Auto"?
"Auto" liest den Text mit dem englischen Modell und ermittelt dann die Sprache des Gelesenen für die Übersetzung. Bei kyrillischem Text wähle die Quellsprache explizit aus (zum Beispiel **Russisch** oder **Ukrainisch**) - sonst werden Buchstaben als ihre lateinischen Doppelgänger gelesen.

### Funktioniert sie offline?
**Ja.** Du brauchst nur einmal Internet, um das Textmodell für deine Quellsprache und das Übersetzungsmodell für dein Sprachpaar herunterzuladen.

### Warum ist die Übersetzung manchmal langsamer?
Die erste Nutzung einer Sprache lädt ihr Textmodell, und große oder detaillierte Bilder brauchen länger zum Lesen. Spätere Durchläufe mit derselben Sprache starten schneller.

### Was ist der lupenartige Übersetzungsmodus?
Das Overlay legt Übersetzungsblöcke über das Bild; Standardmodus zeigt separaten Text. Auf unterstützten Geräten: **Einstellungen → Medien → Übersetzung, Texterkennung (OCR) → Übersetzungsergebnis in Blöcken**.

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
Standard, noLegal, Legacy, VR, XR und FOSS unterstützen Audio mit dauerhafter Hintergrundwiedergabe. Lite unterstützt lokales Audio ohne Hintergrunddienst; Photos kein Audio. Netzwerk-/Cloud-Quellen hängen zusätzlich von der Edition ab.

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Internet-Streams

### Spielt FastMediaSorter Internetradio ab?
**Streams** unterstützt HTTP(S)-Radio/ICY, HLS/DASH und RTSP je nach Quelle/Codec. Verfügbar in **Standard, noLegal, Legacy, VR, XR**, nicht **Lite, Photos, FOSS**. Eine URL umgeht keine fehlende Unterstützung oder DRM.

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Wie öffne ich den Streams-Bildschirm?
Tippe auf **Streams** im Dropdown des Hauptfensters (sichtbar, wenn Streams aktiviert ist). Du erreichst ihn auch über **Einstellungen > Medien > Streams**, wo der Hauptschalter liegt.

### Wie füge ich einen Radiosender hinzu?
Tippe im Streams-Bildschirm auf **⋮** am Ende der Symbolleiste, wähle **Stream hinzufügen** und füge die Sender-URL ein. Tippe auf Speichern. Der Sender erscheint sofort in der Liste.

### Kann ich eine Playlist importieren?
Ja - tippe auf **⋮ > Von URL importieren** und gib eine entfernte `.m3u`-Adresse ein. Dasselbe Menü enthält **FastMediaSorter-Katalog aktualisieren** für die kuratierte Liste (mit Themen- und Sprach-Chips), die auch über **Einstellungen > Extensions** oder den Willkommens-Onboarding-Bildschirm verfügbar ist.

### Ein Stream spielt nicht - was tue ich?
Falls ein Stream fehlschlägt, erscheint ein Dialog mit den Optionen **Erneut versuchen**, **Entfernen** und **Abbrechen**. Protokollübergreifende 301-Weiterleitungen werden automatisch behandelt. Ist der Host tot oder sehr langsam, läuft der Katalog-Import schnell in ein Timeout, statt zu hängen.

### Läuft das Radio weiter, wenn ich den Streams-Bildschirm verlasse?
Bei unterstütztem, aktiviertem Hintergrundaudio gilt beim Verlassen die Einstellung Stoppen / Weiter / Fragen. Ohne Hintergrundaudio stoppt die Wiedergabe, sobald der Bildschirm den Vordergrund verlässt. Einstellungen → Player prüfen.

### Kann ich Live-Vorschaubilder für Streams sehen?
Schalte den Symbolleisten-Umschalter der Streams auf die **Raster**-Ansicht - jeder Kanal wird dann als Kachel mit seinem zuletzt erfassten Bild angezeigt, sodass du auf einen Blick siehst, was gerade läuft. Die Kachel bleibt auch sichtbar, nachdem du die App geschlossen und wieder geöffnet hast, und aktualisiert sich mit einer neuen Aufnahme, sobald der Stream wieder live ist.

### Kann ich einen Stream auf meinen Fernseher casten?
Ja, für Video-Streams - tippe im Player auf **Cast** und wähle ein Chromecast-Gerät im selben WLAN. RTSP-Streams können nicht gecastet werden; die Schaltfläche erscheint nur für Formate, die der Chromecast-Empfänger unterstützt.

---

## Wear OS

### Funktioniert FastMediaSorter auf Wear OS Smartwatches?
Die separate **Wear-OS-App** benötigt mindestens API 28; APK auf der Uhr installieren. Telefonanbindung: **Standard/noLegal** mit kompatiblen Paket-IDs/Signaturen. Das separate **WFF-v4-Zifferblatt** braucht Wear OS 6 / API 36.

[Funktionsmatrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Was kann ich auf der Uhr tun?
Der aktuelle Wear-Code enthält lokale/Netzwerkmedien, Telefontransfers, Streams, Aufnahme und Werkzeuge. Unterstützte Einstellungen/Ressourcen können synchronisieren, nicht alle Telefoneinstellungen. Kein eigener Cloud-Client. **Veröffentlichte Builds können weniger als der Quellcode bieten**; Download-/Releasebeschreibung prüfen.

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
Zeitpläne führen unterstütztes Kopieren/Verschieben/Löschen in zugänglichen Ressourcen aus. Berechtigungen, Zugangsdaten und Erreichbarkeit bleiben nötig. Zuerst mit **Kopieren** testen; geplantes Löschen ist nicht automatisch umkehrbar.

### Wo richte ich geplante Operationen ein?
Einstellungen → **Verwaltung** → **Geplante Operationen nach Zeitplan**. Tippe auf **"+"**, um eine neue Regel hinzuzufügen.

### Läuft sie, wenn meine App geschlossen ist?
WorkManager kann nach Verlassen der App laufen, garantiert aber nichts nach **Androids erzwungenem Stopp**, bei ausgeschaltetem Gerät oder fehlenden Bedingungen/Berechtigungen. App danach öffnen und Regel/Log prüfen.

### Warum lief eine geplante Operation nicht zur genauen Zeit?
WorkManager ist kein exakter Alarm. Akku-, Netzwerk- und Gerätebedingungen können länger verzögern. Mindestintervall: **15 Minuten**; eine Akku-Ausnahme garantiert keinen genauen Start.

### Geplante Operation lief, hat aber 0 Dateien kopiert
Log prüfen: null Kopien kann übersprungene vorhandene Dateien, keine Treffer, unerreichbare Ressourcen oder Berechtigungsfehler bedeuten. Quelle, Filter, Ziel und Zugangsdaten prüfen; null Dateien bedeutet nicht immer Erfolg.

### Kann ich sehen, was verarbeitet wurde?
**Ja.** Tippe im Bereich der geplanten Operationen auf **"Protokoll anzeigen"**, um eine zeitgestempelte Historie jedes Laufs samt Ergebnissen pro Datei zu sehen.

---

## Wetter-Baustein

### Woher kommt das Wetter?
Der Desktop-Wetter-Baustein nutzt **Open-Meteo.com** - einen kostenlosen, schlüssellosen Wetterdienst. Wetterdaten von Open-Meteo.com (CC-BY 4.0).

### Verfolgt die App meinen Standort?
Der **Wetterblock** nutzt den eingegebenen Ort statt GPS-Tracking und sendet diesen Ort an den Wetterdienst. Optionales Foto-Geotagging ist getrennt und benötigt Standortberechtigung.

---
## Noch Fragen?

Keine Antwort oben gefunden, oder funktioniert etwas nicht wie beschrieben? **Bitte melde dich** - jede Nachricht wird gelesen, und die meisten Probleme werden behoben.

- 📖 **How-To-Anleitungen** (Schritt-für-Schritt-Aufgaben): [HOW_TO-de.md](HOW_TO-de.html)
- 🚀 **Schnellstart:** [QUICK_START-de.md](QUICK_START-de.html)
- 🔧 **Problembehebung:** [TROUBLESHOOTING-de.md](TROUBLESHOOTING-de.html)
- 📧 **E-Mail:** [sza@ukr.net](mailto:sza@ukr.net) - für alles: Einrichtungshilfe, Fehlerbeschreibungen, Funktionswünsche
- 🌐 **Seite des Autors:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Fehlerbericht:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - bevorzugt für reproduzierbare Fehler; gib die Android-Version an und was du getan hast
- 📖 **Vollständige Dokumentation:** [Dokumentationsportal](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vermisst du eine Funktion?** Schreib uns - viele Funktionen der App wurden hinzugefügt, weil jemand danach gefragt hat. Wenn es für den Anwendungsfall sinnvoll ist, wird es gebaut.

</div>

</div>
