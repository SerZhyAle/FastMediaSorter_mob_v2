---
layout: default
title: "📖 Anleitungen"
permalink: /docs/HOW_TO-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 📖 Anleitungen

Schritt-für-Schritt-Anleitungen für häufige Aufgaben.

Dieser Leitfaden hat jetzt zwei Ebenen:

- **Szenario-Gruppen** für umfassendere reale Arbeitsabläufe und Kombinationen von Funktionen.
- **Kernaufgaben-Referenz** für direkte Rezepte zu einzelnen Funktionen weiter unten.

{% include lang-switcher.html doc="HOW_TO" dir="/docs/" current="de" %}

---

## Hinweis: Funktionsverfügbarkeit nach Edition

Manche Funktionen sind nur in bestimmten Editionen verfügbar. Die folgende Tabelle stammt aus [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), die direkt aus dem Build erzeugt wird; die Oberfläche XR / noLegal wird bewusst als eine einzige Spalte geführt, weil sie von der Headset-Hardware und den Sideload-Build-Regeln abhängt.

| Funktion | Standard | Lite | Photos | Legacy | XR / noLegal | FOSS |
|---------|----------|------|--------|--------|--------------|------|
| Netzwerkordner (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Cloud-Speicher (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ |
| Audiowiedergabe & Songtexte | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ |
| Audiowiedergabe im Hintergrund | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Internet-Streams (Radio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Dokumentenanzeige (PDF, Text) | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| EPUB-Reader | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Übersetzung & OCR | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Bildbearbeitung | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Startbildschirm-Modus (Launcher) | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |

Beim Startbildschirm-Modus teilt sich die kombinierte letzte Spalte als einzige Zeile auf: Er ist im **noLegal**-Sideload-Build enthalten, aber nicht im VR/XR-Build, wo das Headset seine eigene Startumgebung bereitstellt.

Zwei Zeilen für Audio, weil dies zwei getrennte Build-Entscheidungen sind: **Lite spielt lokale Audiodateien ab**, einschließlich Songtexte, stoppt aber, sobald die App den Vordergrund verlässt - es gibt keinen Hintergrundwiedergabedienst. Lite hat überhaupt keinen Internet-Streams-Bildschirm, daher sind Radio und HLS/DASH/RTSP dort nicht nur eingeschränkt, sondern schlicht nicht vorhanden.

Wenn eine Funktion mit „✗" markiert ist, wählen Sie den **Standard**- oder **XR / noLegal**-Build, der zu Ihrer Hardware und Ihrem Vertriebsweg passt.

---

## Inhaltsverzeichnis

### Szenario-Gruppen

#### Heimmedien, TV und Wohnzimmer-Workflows

1. [Ein NAS in ein Medienregal fürs Wohnzimmer verwandeln](#turn-a-nas-into-a-living-room-media-shelf)
2. [Eine Diashow mit Hintergrundmusik für eine Raumanzeige starten](#run-a-slideshow-with-background-music-for-a-room-display)
3. [FMS auf einer Android-TV-Box nutzen](#how-to-use-fms-on-android-tv-box)
4. [OpenXR-VR-Immersiv-Kino](#openxr-vr-immersive-cinema)

#### Reisen, Lesen und Dokumenten-Workflows

5. [Einen Ordner für Reisen ohne stabiles Internet vorbereiten](#prepare-a-folder-for-travel-without-stable-internet)
6. [Cloud-Dokumente und EPUBs unterwegs lesen](#read-cloud-documents-and-epubs-on-the-go)
7. [Schilder, Scans und Screenshots mit OCR übersetzen](#translate-signs-scans-and-screenshots-with-ocr)
8. [Netzwerkdateien an spezialisierte Apps übergeben](#hand-network-files-off-to-specialist-apps)
9. [Schnelle Mathe- & Textberechnungen](#quick-math-and-text-calculations)
10. [Cloud-Markdown- & Code-Notizen](#cloud-markdown-and-code-notes)

#### Workflows für Power-User und gemischte Medien

11. [Ein Familienfotoarchiv mit Quick Sort sortieren](#sort-a-family-photo-archive-with-quick-sort)
12. [Den Bildschirm mit Rand-Gesten erfassen](#capture-the-screen-with-edge-gestures)
13. [Diashow mit Hintergrundmusik erstellen](#how-to-create-slideshow-with-background-music)
14. [E-Books lesen (EPUB)](#how-to-read-e-books-epub)
15. [Automatische Übersetzung](#auto-translation)
16. [Intelligente Startbildschirm-Widgets](#home-screen-smart-widgets)

### Kernaufgaben-Referenz

17. [Mit einem Netzwerklaufwerk verbinden (SMB)](#how-to-connect-to-network-drive-smb)
18. [Mit einem SFTP/FTP-Server verbinden](#how-to-connect-to-sftpftp-server)
19. [Eine Windows-Companion-Freigabe importieren (Code scannen oder Datei importieren)](#how-to-import-a-windows-companion-share)
20. [Mit Cloud-Speicher verbinden](#how-to-connect-to-cloud-storage)
21. [Quick-Sort-Ordner einrichten](#how-to-set-up-quick-sort-folders)
22. [Touch-Zonen verwenden](#how-to-use-touch-zones)
23. [Fotos bearbeiten](#how-to-edit-photos)
24. [Diashow erstellen](#how-to-create-slideshow)
25. [Ordner mit PIN schützen](#how-to-protect-folder-with-pin)
26. [Papierkorb leeren](#how-to-empty-trash)
27. [Einstellungen sichern](#how-to-backup-settings)
28. [Text- und PDF-Dateien anzeigen](#how-to-view-text-and-pdf-files)
29. [Netzwerkdateien in externen Apps öffnen](#how-to-open-network-files-in-external-apps)
30. [Songtexte anzeigen](#how-to-view-song-lyrics)
31. [Bildschirm aufnehmen](#how-to-record-your-screen)
32. [Eine Sprachnotiz aufnehmen](#how-to-record-a-voice-note)
33. [Die App-interne Kamera verwenden](#how-to-use-the-in-app-camera)
34. [Doppelte Dateien finden und löschen](#how-to-find-and-delete-duplicate-files)
35. [Nutzungsstatistiken anzeigen](#how-to-view-your-usage-statistics)
36. [Eine SD-Karte oder ein angeschlossenes Laufwerk verwenden](#how-to-use-an-sd-card-or-connected-drive)
37. [Einen per Direktpfad hinzugefügten Ordner neu verbinden](#how-to-reconnect-a-folder-added-by-direct-path)
38. [Die App als Startbildschirm verwenden](#how-to-use-the-app-as-your-home-screen)
39. [Speicherort für Aufnahmen und Downloads festlegen](#how-to-choose-where-captures-and-downloads-are-saved)
40. [Von einer anderen App geteilte Dateien empfangen](#how-to-receive-files-shared-from-another-app)
41. [Die integrierten Programme verwenden](#how-to-use-the-built-in-programs)
42. [Ihren Assistenten bitten, Medien zu finden und zu öffnen](#how-to-ask-your-assistant-to-find-and-open-media)
43. [Eine Datei mit FileDO verschlüsseln](#how-to-encrypt-a-file-with-filedo)

---

## Szenario-Gruppen

Diese Abschnitte sind bewusst vielfältiger gestaltet als die Kernreferenz-Blöcke weiter unten. Jedes Szenario verbindet einen schnellen Weg mit Kontext, Kompromissen und den Situationen, in denen FastMediaSorter besonders stark ist.

> **⭐ Empfehlung: Holen Sie die Ordner Ihres PCs mit einem Scan aufs Handy.** Starten Sie den kostenlosen Begleiter [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) auf Ihrem PC, wählen Sie die Ordner mit Ihren Videos, Musik, Dokumenten oder Fotos aus, und er zeigt einen Code auf dem Bildschirm an. Tippen Sie auf dem Handy auf **Hinzufügen**, wählen Sie **Per Barcode importieren**, richten Sie die Kamera auf den Code - die PC-Ordner werden sofort verbunden, ohne Adresse, Port oder Passwort einzutippen. Vollständige Anleitung: [PC-Ordner durch Scannen eines Codes öffnen](howto/scenario-companion-share-de.md) &bull; Kurzrezept: [Eine Windows-Companion-Freigabe importieren](#how-to-import-a-windows-companion-share).

> **⌚ Wear-OS-Smartwatches:** Nutzen Sie eine Wear-OS-Uhr? Sehen Sie sich unsere Schritt-für-Schritt-Anleitungen an: [Musik auf Ihrer Uhr hören](howto/scenario-watch-music-de.md) und [Smartwatch mit NAS- & PC-Freigaben verbinden](howto/scenario-watch-network-de.md).

## Heimmedien, TV und Wohnzimmer-Workflows

## Ein NAS in ein Medienregal fürs Wohnzimmer verwandeln {#turn-a-nas-into-a-living-room-media-shelf}

**Verfügbar in:** Standard, Photos, Legacy, XR/noLegal

**Schnellstart**

1. Fügen Sie Ihr NAS als SMB-Ressource hinzu.
2. Führen Sie **Netzwerk scannen** aus, wenn Sie die IP nicht manuell eingeben möchten.
3. Öffnen Sie die Ressource von einer TV-Box, einem Tablet oder Handy aus.
4. Durchsuchen Sie Videos, Fotos oder Dokumente direkt vom NAS aus.

**Szenario-Ablauf**

- Behalten Sie eine SMB-Ressource für die gesamte Familienbibliothek und trennen Sie Unterordner nach Verwendungszweck: Filme, Familienfotos, Scans, Handbücher.
- Führen Sie bei der Einrichtung einmal **Verbindung testen** aus, damit die Ressource stabil ist, bevor Sie sich vom Sofa aus darauf verlassen.
- Wenn das NAS von einer TV-Box aus genutzt wird, koppeln Sie es mit einer Bluetooth-Tastatur oder Fernbedienung für eine schnelle Navigation.
- Wenn das Durchsuchen langsam wirkt, öffnen Sie die Ressourceneinstellungen und führen Sie zunächst die integrierte Geschwindigkeitsprüfung aus, bevor Sie etwas anderes ändern.

**Wann es hilft**

- Sie möchten eine zentrale Medienquelle, statt dieselben Dateien auf mehrere Geräte zu kopieren.
- Sie möchten, dass dieselbe Bibliothek für Diashow, Dokumentenlektüre und Wiedergabe funktioniert.

**Das sollten Sie vermeiden**

- Beginnen Sie nicht mit der Fehlersuche beim Hostnamen. Verwenden Sie zuerst eine IP-Adresse und optimieren Sie später.
- Erwarten Sie nicht, dass die Lite-Edition SMB-Freigaben durchsuchen kann - dieser Build hat überhaupt keine Netzwerkquellen.

## Eine Diashow mit Hintergrundmusik für eine Raumanzeige starten {#run-a-slideshow-with-background-music-for-a-room-display}

**Verfügbar in:** Standard, Lite, Legacy, XR / noLegal (Photos unterstützt kein Audio)

**Schnellstart**

1. Fügen Sie eine Bildquelle und eine Musikquelle hinzu.
2. Aktivieren Sie unter **Einstellungen → Medien → Bilder** die Option **Musik während der Diashow abspielen**.
3. Wählen Sie die Musikressource aus.
4. Öffnen Sie einen Fotoordner und drücken Sie **Wiedergabe**.

**Szenario-Ablauf**

- Verwenden Sie für die reibungslosesten Übergänge einen lokalen Bildordner oder eine schnelle NAS-Freigabe.
- Behalten Sie eine separate Musikressource für ruhige Hintergrundtitel, damit der Diashow-Ton vorhersehbar bleibt.
- Wenn der Ordner sowohl Bilder als auch Videos enthält, denken Sie daran, dass die Musik automatisch pausiert, sobald ein Video startet.

**Wann es hilft**

- Sie möchten, dass eine TV-Box, ein Tablet oder ein altes Handy als digitaler Bilderrahmen für einen Raum dient.
- Sie möchten ein Setup, das Familienfotos, Veranstaltungsbilder oder Reisealben rotieren lässt, ohne die Warteschlange manuell zu erstellen.

**Das sollten Sie vermeiden**

- Verwenden Sie keine sehr langsame Netzwerkfreigabe für Bilder und Musik zugleich, wenn eine flüssige Wiedergabe wichtig ist.

## OpenXR-VR-Immersiv-Kino {#openxr-vr-immersive-cinema}

**Verfügbar in:** Standard, Lite, Legacy, `vr`, noLegal (Einzelaugen-3D); `vr` und noLegal (volle Headset-Immersion - beide Builds enthalten die Immersiv-Ansicht, die sich öffnet, wenn die App ein OpenXR-Headset erkennt und der VR-Hauptschalter aktiviert ist)

**Schnellstart - aktivieren, konfigurieren, 3D ansehen**

1. **Einzelaugen-3D (jede Edition, nichts zu aktivieren):** Öffnen Sie eine beliebige SBS/OU/180°/360°-Datei - sie wird automatisch erkannt und auf ein Auge zugeschnitten, damit sie auf einem normalen Flachbildschirm richtig aussieht. Das wird über **Einstellungen > Player > „3D-Inhalt von einem Auge anzeigen"** gesteuert (Standard: EIN). Um statt der automatischen Erkennung ein bestimmtes Format zu erzwingen, öffnen Sie in einem `vr`/XR-noLegal-Build den Steuerungsdialog des Players und wählen Sie im 3D-Tab einen Modus - **Automatische Erkennung**, **Nebeneinander (SBS)**, **Übereinander (OU)** oder **Mono (deaktiviert)**; die Wahl wird für diese Datei gespeichert.
2. **Volle Immersion auf einer Quest (`vr`- oder XR/noLegal-Build):** Tippen Sie bei aufgesetztem Headset auf das VR-Symbol im Player, während eine 3D-Datei geöffnet ist, wählen Sie **In VR-Kino öffnen** aus dem Overflow-Menü einer Datei in Durchsuchen, oder öffnen Sie **Einstellungen > Medien** und tippen Sie auf **Immersiv testen**, um ein Beispiel auszuprobieren. Jede der drei Optionen öffnet eine Pro-Auge-OpenXR-Ansicht dieses Inhalts.
3. **Ansehen:** In der Immersiv-Ansicht enthält eine HUD-Leiste die Bedienelemente - eine Positionsleiste, die Sie mit dem Controller-Strahl zum Suchen ziehen (verstrichene und Gesamtzeit daneben), sowie die Auswahlfelder, die für diese Datei gelten: Audiospur nur, wenn es mehr als eine gibt, Untertitel nur, wenn die Datei welche hat, Stereotiefe nur bei stereoskopischem Inhalt. **AUSBLENDEN** und **BEENDEN** stehen an den gegenüberliegenden Enden der Leiste; wird sie ausgeblendet, verschwindet sie vollständig, und ein Trigger-Zug holt sie zurück, ohne das darunterliegende Element zu aktivieren. Der Thumbstick sucht in Schritten von 10 Sekunden; halten Sie **Grip** gedrückt, während Sie ihn bewegen, um stattdessen zwischen Dateien zu wechseln - Weiter und Zurück durchlaufen die gesamte Ressourcenliste, nicht nur die geöffnete Datei. Beim ersten Immersiv-Aufruf nach der Installation zeigt eine Legende jede Controller-Belegung; jeder Tastendruck schließt sie, und die Schaltfläche **HILFE** auf der Leiste holt sie jederzeit wieder zurück.

**Szenario-Ablauf**

- Einzelaugen-3D benötigt überhaupt kein Headset - es ist der einfachste Weg, alte SBS/OU-Aufnahmen auf einem Handy oder Tablet erneut anzusehen.
- Volle Immersion benötigt eine Quest oder ein anderes OpenXR-Headset sowie einen Build, der sie mitbringt - den `vr`-Build oder den XR/noLegal-Sideload-Build (siehe die [VR-Sideloading-Anleitung](VR_SIDELOAD.md)).
- 360°/180°-Fotos und -Videos werden in der Immersiv-Ansicht als Kugel/Halbkugel um Sie herum dargestellt; flache 2D-Dateien werden einfach flach wiedergegeben.

**Wann es hilft**

- Sie möchten archivierte SBS/OU/360°/180°-Aufnahmen ansehen, ohne eine separate VR-Medien-App zu benötigen.
- Sie besitzen eine Quest und möchten schon heute die volle Immersion mit Ihren eigenen Dateien ausprobieren - und akzeptieren, dass die Navigation vorerst nur Weiter/Zurück bietet.

**Das sollten Sie vermeiden**

- Erwarten Sie noch nicht, dass der `vr`-Build aus dem Meta Horizon Store / Google Play in den Immersiv-Modus wechselt - dieser Teil befindet sich noch in der Entwicklung.
- Suchen, die Auswahl von Spur und Untertiteln sowie die Stereotiefe befinden sich auf der HUD-Leiste im Headset. Dateioperationen dagegen nicht - wechseln Sie zum flachen Bedienfeld zurück, um zu kopieren, zu verschieben oder zu löschen.

## Internetradio auf einem Autoradio oder Audioplayer abspielen

**Verfügbar in:** Standard, Legacy, XR / noLegal - der Streams-Bildschirm fehlt in Lite und Photos

**Schnellstart**

1. Öffnen Sie das Dropdown-Menü des Hauptfensters und tippen Sie auf **Streams**, oder gehen Sie zu **Einstellungen > Medien > Streams** und aktivieren Sie den Schalter, falls er aus ist.
2. Tippen Sie am Ende der Symbolleiste auf **⋮**, wählen Sie **Stream hinzufügen** und fügen Sie eine beliebige Sender-URL ein (http:// oder https://, .m3u8, rtsp://).
3. Tippen Sie auf die Senderzeile - der Ton startet in der angehefteten Mini-Steuerung am unteren Rand. Die Liste bleibt scrollbar.
4. Für einen größeren Katalog tippen Sie auf **Importieren** und geben eine entfernte `.m3u`-URL ein, oder laden Sie den kuratierten FastMediaSorter-Katalog vom Bildschirm **Erweiterungen** herunter.

**Szenario-Ablauf**

- Der kuratierte Katalog kommt mit Themen- und Sprach-Chips; filtern Sie über die Filterschaltfläche nach Genre oder Sprache (Punktanzeige, wenn aktiv). Der UND/ODER-Schalter lässt Sie Sender finden, die alle Kriterien oder nur eines davon erfüllen.
- Der Katalog kommt außerdem in benannte Sammlungen gruppiert - „Russisches TV", „Radio der ehemaligen UdSSR", „Afrikanisches TV" und mehr. Sie erscheinen als scrollbarer Chip-Streifen direkt unter der Symbolleiste; tippen Sie auf einen, um nur dessen Sender zu sehen, in der vom Kurator festgelegten Reihenfolge, und tippen Sie auf **Alle**, um zurückzukehren. Ein und derselbe Sender kann zu mehreren Sammlungen gehören, sodass Sie ihm sowohl unter einem Land als auch unter einem Kontinent begegnen können. Eine Sammlung ist eine weitere Filterbedingung, kein eigener Bildschirm: Suche, Sortierung, die Genre- und Sprachfilter sowie Ihre Pins funktionieren weiterhin darin. Enthält der heruntergeladene Katalog keine Sammlungen, fehlt der Streifen einfach.
- Die beiden kleinen Symbole rechts vom Suchfeld trennen Radio von Video mit einem Fingertipp: Tippen Sie auf das Audio- oder das Video-Symbol, um nur diese Art zu behalten, tippen Sie erneut auf das aktive Symbol, um wieder alles anzuzeigen.
- Heften Sie Ihre Lieblingssender mit dem Pin-Symbol oben an - die Reihenfolge ist unabhängig von den globalen Favoriten.
- Stellen Sie den Ansichtsschalter in der Symbolleiste auf **Raster**, um Sender als Kacheln mit ihrem zuletzt erfassten Bild zu sehen - praktisch, um Video-Streams auf einen Blick zu durchsuchen. Ihre Wahl von Liste oder Raster wird beim nächsten Öffnen von Streams gespeichert.
- Wenn ein Stream Cast-fähig ist und Ihr Handy per WLAN verbunden ist, tippen Sie im Player auf **Cast**, um ihn an einen Chromecast im selben Netzwerk zu senden. RTSP-Streams können nicht gecastet werden.
- ICY-„Jetzt läuft"-Metadaten (Sendername, aktueller Titel) werden in der unteren Mini-Steuerung angezeigt.
- Einen selbst hinzugefügten Sender können Sie an Ihre Wear-OS-Uhr senden: Öffnen Sie das **⋮**-Menü der Zeile und tippen Sie auf **An Uhr senden** (der Befehl erscheint, wenn die Wear-Companion-Option aktiviert ist). Der übertragene Sender bleibt bei Katalog-Aktualisierungen auf der Uhr erhalten; erscheint dieselbe Adresse später im Online-Katalog, übernimmt der Katalogeintrag.
- Video- und RTSP-Streams öffnen sich im Vollbild-Player; ein Druck auf Zurück kehrt zur Streams-Liste zurück, wobei die Scroll-Position erhalten bleibt.
- Das Verhalten der Hintergrundwiedergabe richtet sich nach **Einstellungen > Player > Audiowiedergabe im Hintergrund**: Ist die Option deaktiviert, stoppt der Ton, sobald Sie den Bildschirm verlassen, und die App bietet die Wahl zwischen Stopp / Weiterspielen.

**Wann es hilft**

- Android-Autoradios, Audioplayer und Media-Boxen, bei denen Sie Internetradio ohne separate App möchten (TuneIn, RadioDroid, VLC-Netzwerkstreams).
- IPTV-Lite-Nutzung: HLS/DASH-VOD-Streams spielen im Vollbild-Player.

**Das sollten Sie vermeiden**

- Erwarten Sie keine Live-HLS/DASH-Wiedergabe mit Live-Rand (live-edge) - in dieser Version wird nur VOD-HLS/DASH unterstützt.
- Verwenden Sie für Streams nicht die Lite- oder Photos-Edition; keiner der beiden Builds hat einen Streams-Eintrag, sodass dort kein Protokoll funktioniert.

## Reisen, Lesen und Dokumenten-Workflows

## Einen Ordner für Reisen ohne stabiles Internet vorbereiten {#prepare-a-folder-for-travel-without-stable-internet}

**Verfügbar in:** Standard, Lite, Photos, Legacy, XR / noLegal (PDF- und EPUB-Lesen erfordert Standard, Legacy oder XR / noLegal)

**Schnellstart**

1. Erstellen oder wählen Sie einen lokalen Ordner für die Reise.
2. Kopieren Sie die benötigten Medien, PDFs, EPUBs oder Notizen hinein, bevor Sie das WLAN verlassen.
3. Öffnen Sie diesen Ordner einmal in FastMediaSorter, damit Miniaturansichten und letzte Positionen bereit sind.
4. Verwenden Sie den Ordner während der Reise offline.

**Szenario-Ablauf**

- Bewahren Sie Reisemedien in einem lokalen Ordner auf, auch wenn die Originale normalerweise auf dem NAS oder in der Cloud liegen.
- Mischen Sie Formate absichtlich: Bordkarten als PDF, EPUB-Lektüre, Screenshots und Offline-Musik können nebeneinander bestehen.
- Verwenden Sie das Filterpanel, wenn Sie offline zwischen nur Bildern, nur Dokumenten oder nur Audio wechseln möchten.

**Wann es hilft**

- Flüge, Zugfahrten, Hotels und ländliche Gebiete, in denen Cloud-Streaming unzuverlässig ist.
- Situationen, in denen Sie ein einziges Offline-Paket möchten, statt mehrere Apps zu durchsuchen.

**Das sollten Sie vermeiden**

- Warten Sie nicht bis zur letzten Minute, um zu testen, ob die Dateien wirklich ohne Internet geöffnet werden können.

## Cloud-Dokumente und EPUBs unterwegs lesen {#read-cloud-documents-and-epubs-on-the-go}

**Verfügbar in:** Standard, Legacy, XR / noLegal - Lite und Photos können überhaupt keine Dokumente oder EPUBs lesen; bei Lite fehlt zusätzlich der Cloud-Speicher

**Schnellstart**

1. Fügen Sie Ihren Cloud-Anbieter unter Cloud-Speicher hinzu.
2. Öffnen Sie den Ordner, der PDFs oder EPUBs enthält.
3. Tippen Sie die Datei direkt aus der Cloud-Ressource an.
4. Setzen Sie das Lesen später an Ihrer zuletzt gespeicherten Position fort.

**Szenario-Ablauf**

- Nutzen Sie dies, wenn Ihre Arbeitsdokumente bereits in Google Drive, OneDrive oder Dropbox liegen und Sie keinen separaten Lese-Workflow möchten.
- PDFs eignen sich am besten für Dateien mit festem Layout wie Tickets, Handbücher und gescannte Verträge.
- EPUB ist besser für längere Lektüre geeignet, bei der eine anpassbare Schriftgröße und die Kapitelnavigation wichtiger sind als Layouttreue.

**Wann es hilft**

- Sie wechseln zwischen Arbeitsdokumenten und privater Lektüre, ohne die App zu verlassen.
- Sie bewahren Reise- oder Kundendateien in der Cloud auf, möchten aber dennoch eine leseorientierte Oberfläche.

**Das sollten Sie vermeiden**

- Erwarten Sie kein Cloud-Lesen in Lite - dieser Build hat weder Cloud-Speicher noch Dokumentenunterstützung. Photos und Legacy haben zwar Cloud-Speicher, aber nur Legacy kann Dokumente öffnen.
- Verlassen Sie sich bei sehr großen Dateien nicht auf langsame mobile Daten für ein garantiertes Leseerlebnis.

## Schilder, Scans und Screenshots mit OCR übersetzen {#translate-signs-scans-and-screenshots-with-ocr}

**Verfügbar in:** Standard, Legacy, XR / noLegal

**Schnellstart**

1. Öffnen Sie ein Bild, ein PDF oder eine Textdatei.
2. Blenden Sie das Befehlspanel ein.
3. Tippen Sie auf **Übersetzen**.
4. Bestätigen Sie bei Bedarf beim ersten Gebrauch den Modell-Download.

**Szenario-Ablauf**

- Die App liest den Text mit Tesseract auf dem Gerät und übersetzt ihn mit Google ML Kit.
- Wählen Sie bei kyrillischem Material die Ausgangssprache ausdrücklich aus (zum Beispiel Russisch oder Ukrainisch) - „Automatisch" liest mit dem englischen Modell.
- Screenshots, Quittungen, Menüs und gescannte Seiten funktionieren besonders gut, wenn der Ausgangstext einigermaßen scharf ist.

**Wann es hilft**

- Sie sind auf Reisen, lesen fremdsprachige Handbücher oder entziffern Screenshots aus Chats und Apps.
- Sie benötigen die Übersetzung direkt vor Ort, statt den Text zuerst in ein separates Tool zu kopieren.

**Das sollten Sie vermeiden**

- Beurteilen Sie die OCR-Qualität nicht anhand eines verwackelten Nachtfotos oder eines schlecht zugeschnittenen Scans.

## Netzwerkdateien an spezialisierte Apps übergeben {#hand-network-files-off-to-specialist-apps}

**Verfügbar in:** Standard, Photos, Legacy, XR/noLegal

**Schnellstart**

1. Öffnen Sie eine Datei von SMB, SFTP oder FTP.
2. Tippen Sie auf **ⓘ Info**.
3. Tippen Sie auf **Herunterladen und öffnen**.
4. Wählen Sie die spezialisierte App aus der Android-Auswahl.

**Szenario-Ablauf**

- Nutzen Sie dies, wenn FastMediaSorter der beste Browser für entfernten Speicher ist, eine andere App aber der bessere Editor oder Viewer für einen bestimmten Dateityp ist.
- Typische Übergabefälle sind Office-Dokumente, aufwendige PDFs, codec-lastige Videos und Nischen-Medienformate.
- Die heruntergeladene Kopie bleibt in Downloads erhalten, sodass Sie sie später erneut öffnen können, selbst wenn die entfernte Quelle offline geht.

**Wann es hilft**

- Sie möchten einen zentralen Hub für entfernte Dateien, ohne auf erstklassige Spezial-Tools zu verzichten.

**Das sollten Sie vermeiden**

- Erwarten Sie über genau diesen Ablauf noch keine Cloud-Übergabe.

## Schnelle Mathe- & Textberechnungen {#quick-math-and-text-calculations}

**Verfügbar in:** Standard, Legacy, XR / noLegal

**Schnellstart**

1. Öffnen Sie ein beliebiges PDF-Dokument, EPUB-E-Book, eine Textdatei, oder führen Sie eine OCR-Übersetzung auf einem Bild aus.
2. Halten Sie gedrückt, um einen beliebigen Textblock mit Zahlen oder mathematischen Gleichungen auszuwählen.
3. Tippen Sie im schwebenden Textaktionsmenü auf die Schaltfläche **Taschenrechner**.
4. Der Taschenrechner wertet die mathematische Formel sofort in einem Popup-Overlay aus.

**Szenario-Ablauf**

- Wählen Sie eine Textzeile mit Zahlen und Operatorsymbolen aus (wie `(45 + 12) * 3`) in einem PDF oder einem OCR-Übersetzungsergebnis.
- Nutzen Sie das Funktionsmenü des integrierten wissenschaftlichen Taschenrechners für komplexe Operationen (Trigonometrie, Wurzeln, Potenzen, Logarithmen).
- Der Taschenrechner behält den Berechnungsverlauf über Sitzungen hinweg und unterstützt Speicherplätze (M+/M-/MR/MC) zur schnellen Datenverfolgung.

**Wann es hilft**

- Sie lesen ein Handbuch, einen Screenshot-Scan oder ein Dokument und müssen schnell Formeln lösen oder Beträge/Zahlen summieren, ohne zu einer anderen Taschenrechner-App zu wechseln.

**Das sollten Sie vermeiden**

- Fügen Sie keine reinen Buchstabenfolgen ein; es können nur gültige Zahlen, Klammern und mathematische Operatoren verarbeitet werden.

## Cloud-Markdown- & Code-Notizen {#cloud-markdown-and-code-notes}

**Verfügbar in:** Standard, Photos, Legacy, XR / noLegal (lokal, Netzwerk und Cloud); Lite (nur lokale Ordner)

**Schnellstart**

1. Navigieren Sie zu einem beliebigen lokalen Ordner, Heim-NAS (SMB), FTP/SFTP-Server oder Cloud-Laufwerk (Google Drive).
2. Tippen Sie in der Ordner-Symbolleiste auf die Schaltfläche **Neue Notiz <img src="icons/doc/ic_create_text_file.png" alt="" width="18" height="18" style="vertical-align:text-bottom">**.
3. Geben Sie Ihren Inhalt im Editor ein. Die App hebt Markdown-Tags und Code-Syntax hervor.
4. Tippen Sie auf **Speichern** (oder lassen Sie automatisch speichern), um die Änderungen direkt in die entfernte Quelle zu schreiben.

**Szenario-Ablauf**

- Führen Sie eine `.md`-Journaldatei in Ihrem Google Drive oder Heim-NAS und bearbeiten Sie sie mit In-Place-Bearbeitung von jedem Gerät aus.
- Erstellen Sie neue Notizen in wichtigen Ressourcen mit automatischer Auflösung von Namenskonflikten (z. B. `Note_1.txt`, `Note_2.txt`).
- Zeigen Sie gerenderte Markdown-Layouts im Nur-Lese-Modus an, oder exportieren Sie Notizen direkt zu externen Diensten wie Google Keep.

**Wann es hilft**

- Sie möchten einfache Notizen, Code-Schnipsel oder Aufgabenlisten direkt auf Ihren zentralen Netzwerk-/Cloud-Laufwerken pflegen, ohne lokale Kopier-Einfüge-Workflows.

**Das sollten Sie vermeiden**

- Erwarten Sie auf Lite keine Notizerstellung im Cloud-Speicher - dieser Build hat weder Cloud-Speicher noch Netzwerkquellen.

## Workflows für Power-User und gemischte Medien

## Ein Familienfotoarchiv mit Quick Sort sortieren {#sort-a-family-photo-archive-with-quick-sort}

**Verfügbar in:** Standard, Lite, Photos, Legacy, XR / noLegal

**Schnellstart**

1. Fügen Sie Ihre Zielordner zu Quick Sort hinzu.
2. Öffnen Sie den Quellordner mit unsortierten Familienfotos.
3. Verwenden Sie beim Durchsehen der Bilder nummerierte Schaltflächen oder Touch-Zonen.
4. Senden Sie Behaltenswertes sofort an die Zielordner.

**Szenario-Ablauf**

- Erstellen Sie Zielordner nach Ergebnis, nicht nur nach Datum: Beste, Drucken, An Familie senden, Archiv.
- Sehen Sie im Vollbild durch, damit Sie schnell entscheiden und verschieben oder kopieren können, ohne zur Dateiliste zurückzukehren.
- Wenn mehrere Personen dasselbe Archiv pflegen, legen Sie vor einer großen Sortiersitzung ein einheitliches Namensschema für die Ziele fest.

**Wann es hilft**

- Sie haben einen Rückstand von Geburtstagen, Reisen, Schulveranstaltungen oder alten Handy-Importen.
- Sie möchten einen schnellen Sichtungsablauf, statt Dateien manuell in einem Dateimanager zu ziehen.

**Das sollten Sie vermeiden**

- Beginnen Sie nicht mit dem Sortieren, bevor die Ziele klar benannt sind.
- Verwenden Sie nicht sofort Verschieben, wenn noch unklar ist, welche Ordner als langfristiges Archiv bestehen bleiben sollen.

## Den Bildschirm mit Rand-Gesten erfassen {#capture-the-screen-with-edge-gestures}

**Verfügbar in:** Standard, XR/noLegal

**Schnellstart**

1. Gehen Sie zu **Einstellungen → Verwaltung → Rand-Bildschirmgesten → Gesten-Overlay** und schalten Sie es ein.
2. Wischen Sie beim Betrachten einer beliebigen Datei vom linken Rand herein, um das Erfassungsmenü zu öffnen.
3. Wählen Sie eine Aktion - der Streifen schließt sich und die Aktion wird ausgeführt.

**Was der Streifen kann**

- Einen **Screenshot** des aktuellen Bildschirms aufnehmen - ansehen, bearbeiten, teilen, an eine andere App senden oder eine OCR-Übersetzung darauf ausführen, plus eine Option für stille Aufnahme.
- Mit der Kamera **ein Foto aufnehmen**, es dann senden, bearbeiten oder per OCR übersetzen, ohne die App zu verlassen.
- Eine **Bildschirm-, Video- oder Audio-/Sprach**-Aufnahme starten - siehe [Bildschirm aufnehmen](#how-to-record-your-screen) und [Eine Sprachnotiz aufnehmen](#how-to-record-a-voice-note).
- **Eine App oder ein Panel öffnen**, das Sie häufig nutzen.
- Einen Bereich des aktuellen Bildes **zuschneiden und teilen**.

**Gut zu wissen**

- Solange der Streifen aktiv ist, öffnet ein Wischen vom linken Rand das Erfassungsmenü, statt die Seite umzublättern.
- Der Streifen ist für einhändiges Erfassen beim Durchsuchen gedacht - lassen Sie ihn aus, wenn Sie auf Seitenwischen vom linken Rand angewiesen sind.
- Android bestätigt die Aufnahme oder Aufzeichnung jedes Mal, wenn Sie diese Geste verwenden, sogar bei der Option für stille Screenshots - das ist eine Systemsicherung, die die App nicht steuert.

**Wann es hilft**

- Sie möchten einen Screenshot, ein schnelles Foto oder eine Aufnahme, ohne die gerade betrachtete Datei zu verlassen.

## Kernaufgaben-Referenz

## Einen Internet-Stream hinzufügen oder importieren

**Verfügbar in:** Standard, Legacy, XR / noLegal (alle Protokolle) - der Streams-Bildschirm fehlt in Lite und Photos

**Eine einzelne URL hinzufügen:**

1. Öffnen Sie **Streams** aus dem Dropdown-Menü des Hauptfensters.
2. Tippen Sie auf die Schaltfläche **⋮** am Ende der Symbolleiste, dann auf **Stream hinzufügen**.
3. Fügen Sie die Stream-URL ein (http/https-Radio, .m3u8, rtsp://). Tippen Sie auf **Speichern**.
4. Tippen Sie auf die Zeile, um die Wiedergabe zu starten.

**Eine entfernte .m3u-Playlist importieren:**

1. Tippen Sie im Streams-Bildschirm auf **⋮ > Aus URL importieren**.
2. Geben Sie die entfernte .m3u-Adresse ein. Tippen Sie auf **Importieren**.
3. Alle Sender aus der Datei erscheinen in der Liste.

**Den kuratierten FastMediaSorter-Katalog herunterladen:**

1. Öffnen Sie **Einstellungen > Erweiterungen** (oder die Streams-Zeile im Willkommens-Onboarding).
2. Tippen Sie neben dem Streams-Katalog-Eintrag auf **Herunterladen**.
3. Nach dem Download erscheinen die Katalogzeilen in Streams mit Themen-/Sprach-Chips und sind durchsuchbar und sortierbar.

---

## Mit einem Netzwerklaufwerk verbinden (SMB) {#how-to-connect-to-network-drive-smb}

**Was Sie benötigen:**

- NAS oder Windows-PC mit freigegebenem Ordner
- Beide Geräte im selben WLAN
- Benutzername und Passwort für die Freigabe

**Verfügbar in:** den Editionen Standard, Photos, Legacy, XR / noLegal

**Schritte:**

1. **Tippen Sie auf „+"** auf dem Hauptbildschirm
2. Wählen Sie **„Netzwerkordner (SMB)"**
3. Geben Sie die Details ein:
   - **Automatische Erkennung (Neu):**
     1. Tippen Sie auf die Schaltfläche **„Netzwerk scannen"**
     2. Warten Sie, bis Geräte in der Liste erscheinen
     3. Wählen Sie Ihr Gerät aus der Liste
     4. Die IP-Adresse wird automatisch eingetragen

   - **Manuelle Eingabe:**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Tippen Sie auf **„Verbindung testen"**, um sie zu überprüfen
5. Tippen Sie auf **„Speichern"**

**Serveradressformate:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- Mit Port: `smb://192.168.1.100:445/share`

**Tipps:**

- Verwenden Sie die IP-Adresse (nicht den Hostnamen) für mehr Zuverlässigkeit
- Aktivieren Sie SMB v2/v3 auf dem NAS für mehr Sicherheit
- Standard-SMB-Port: 445

**Fehlerbehebung:**
→ Siehe [TROUBLESHOOTING.md](TROUBLESHOOTING-de.md)

---

## Mit einem SFTP/FTP-Server verbinden {#how-to-connect-to-sftpftp-server}

**Was Sie benötigen:**

- Server mit aktiviertem SSH (SFTP) oder FTP
- Offener Port 22 (SFTP) oder 21 (FTP)
- Benutzername und Passwort (oder Schlüssel für SFTP)

**Schritte:**

1. **Tippen Sie auf „+"** auf dem Hauptbildschirm
2. Wählen Sie **„SFTP / FTP"**
3. Wählen Sie das Protokoll: **SFTP** oder **FTP**
4. Geben Sie die Details ein:

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Tippen Sie auf **„Verbinden"**

**Erweitert:**

- **SSH-Schlüssel-Authentifizierung:** derzeit nicht unterstützt (nur Passwort)
- **Benutzerdefinierter Port:** Ändern Sie die Portnummer, wenn der Server nicht den Standard verwendet

**Fehlerbehebung:**
→ Siehe [TROUBLESHOOTING.md](TROUBLESHOOTING-de.md)

---

## Eine Windows-Companion-Freigabe importieren {#how-to-import-a-windows-companion-share}

**Was es ist:** Der Companion ist eine Funktion von [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (früher FastMediaSorter LITE) - dem kostenlosen Windows-Medien-Sortierer desselben Autors. Er gibt ausgewählte PC-Ordner über SFTP frei und exportiert eine fertige Verbindung - keine manuelle Servereinrichtung, keine Eingabe von Host/Port/Schlüssel auf dem Handy. Sie bringen sie aufs Handy, indem Sie einen **QR-Code** auf dem PC-Bildschirm scannen oder eine `.fmscfg`-Datei importieren.

**Verfügbar in:** Standard, Photos, Legacy, XR/noLegal (Barcode-Scan benötigt eine Kamera; die Dateimethode funktioniert überall, auch in VR)

> Bevorzugen Sie eine geführte, screenshot-freundliche Version? Siehe die Szenario-Anleitung [PC-Ordner durch Scannen eines Codes öffnen](howto/scenario-companion-share-de.md).

**Fast Media Sorter for Windows holen:**

- Website: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Ordner veröffentlichen (Anleitung): [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [latest release](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (Installationsprogramm oder portables ZIP)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: Suchen Sie nach „FastMediaSorter LITE" (dort noch unter dem früheren Namen gelistet)

**Auf dem PC:**

1. Installieren und starten Sie **Fast Media Sorter for Windows**, öffnen Sie den Tab **Freigabe** in den Einstellungen.
2. Wählen Sie die freizugebenden Ordner - die App startet den SFTP-Server, erzeugt Schlüssel und richtet den Autostart von selbst ein.
3. Sie zeigt einen **QR-Code** auf dem Bildschirm an. Sie kann auch **.fmscfg speichern**, wenn Sie eine Datei bevorzugen.

**Auf dem Handy - Methode A, den Code scannen (am schnellsten):**

1. **Tippen Sie auf die Schaltfläche „+"** auf dem Hauptbildschirm.
2. Tippen Sie auf **„Per Barcode importieren"** - sie befindet sich neben den vier Ressourcentyp-Karten und im Kopf des SFTP-Formulars.
3. Richten Sie die Kamera auf den QR-Code auf dem PC (tippen Sie in einem dunklen Raum auf **Taschenlampe**), und bestätigen Sie dann den Dialog **Zugriff importieren**.
4. Fertig - für jeden freigegebenen Ordner erscheint eine schreibgeschützte Ressource, wobei der Server-Schlüssel automatisch angeheftet wird.

**Auf dem Handy - Methode B, die Datei importieren:**

1. Verwenden Sie auf dem PC **.fmscfg speichern** und übertragen Sie die Datei aufs Handy (E-Mail, Telegram oder einen freigegebenen Ort).
2. Tippen Sie auf **„+"** -> **„SFTP / FTP"** -> **„Aus Datei importieren"** und wählen Sie die `.fmscfg`-Datei. Kam sie als Telegram-/E-Mail-Anhang an, tippen Sie einfach auf den Anhang.
3. Bestätigen Sie den Dialog **Zugriff importieren** - die schreibgeschützten Ressourcen erscheinen.

**Hinweis:** Der QR-Code und die Konfigurationsdatei enthalten beide das Zugriffspasswort - behandeln Sie sie wie einen Schlüssel und veröffentlichen Sie weder den Screenshot noch die Datei. Der Eintrag „Per Barcode importieren" ist auf Geräten ohne Kamera und auf VR-Headsets ausgeblendet; verwenden Sie dort Methode B.

---

## Mit Cloud-Speicher verbinden {#how-to-connect-to-cloud-storage}

**Unterstützte Anbieter:**

- Google Drive
- OneDrive
- Dropbox

**Schritte:**

1. **Tippen Sie auf „+"** auf dem Hauptbildschirm
2. Wählen Sie **„Cloud-Speicher"**
3. Wählen Sie den Anbieter: **Google Drive**, **OneDrive** oder **Dropbox**
4. Tippen Sie auf die Schaltfläche **„Anmelden.."**
5. Folgen Sie dem Authentifizierungsablauf im Browser/in der App
6. Erteilen Sie die erforderlichen Berechtigungen
7. **Wählen Sie die Ordner** aus, die synchronisiert werden sollen
8. Tippen Sie auf **„Fertig"**

**Hinweise:**

- Dateien werden **gestreamt**, nicht heruntergeladen
- Erfordert eine Internetverbindung
- Änderungen werden automatisch synchronisiert
- Sie können die Verbindung jederzeit trennen: Ordner bearbeiten → Entfernen

**Datenschutz:**

- Kein Passwort gespeichert (verwendet OAuth-Tokens)
- Tokens können in den Sicherheitseinstellungen Ihres Cloud-Anbieters widerrufen werden

---

## Netzwerkgeschwindigkeit prüfen

**Unterstützt für:** SMB, SFTP, FTP, Cloud (Google Drive)

**Automatische Prüfung:**
Wenn Sie eine neue Netzwerkressource hinzufügen, führt die App automatisch im Hintergrund einen Geschwindigkeitstest durch. Die Ergebnisse (Lese-/Schreibgeschwindigkeit) werden in den Ressourceneinstellungen gespeichert.

**Manuelle Prüfung:**

1. Gehen Sie zu **Ressourcen verwalten**
2. Bearbeiten Sie eine Netzwerkressource (Stiftsymbol)
3. Scrollen Sie ganz nach unten
4. Tippen Sie auf die Schaltfläche **„Geschwindigkeit"**
5. Warten Sie ca. 15 Sekunden auf „Geschwindigkeit wird analysiert.."
6. Sehen Sie die Ergebnisse:
   - **Lesegeschwindigkeit (Mbps)**
   - **Schreibgeschwindigkeit (Mbps)**
   - **Empfohlene Threads** (für optimale Leistung)

---

## Quick-Sort-Ordner einrichten {#how-to-set-up-quick-sort-folders}

**Methode 1: Über die Einstellungen**

1. **Einstellungen** → Tab **Verwaltung** → **Quick-Sort-Ziele**
2. Tippen Sie auf **„Zu Quick Sort hinzufügen"**
3. Wählen Sie einen vorhandenen Ordner aus der Liste
4. Dem Ordner werden eine Nummer (0-9) und eine Farbe zugewiesen
5. Wiederholen Sie dies für bis zu 30 Ordner

**Methode 2: Über die Ordnereinstellungen**

1. Hauptbildschirm → **Ordner lange gedrückt halten**
2. Tippen Sie auf **„Bearbeiten"** (Stiftsymbol)
3. Aktivieren Sie **„Für Quick Sort markieren"**
4. Tippen Sie auf **„Speichern"**

**Quick Sort verwenden:**

Beim Betrachten von Dateien:

- Tippen Sie auf die **nummerierte Schaltfläche** (0-9) im Befehlspanel
- ODER tippen Sie auf die **untere linke Ecke** (KOPIEREN-Zone)
- ODER tippen Sie auf die **untere mittlere Ecke** (VERSCHIEBEN-Zone)

Die Datei wird sofort in diesen Ordner kopiert/verschoben!

**Mit einer Tastatur oder Fernbedienung:** Schließen Sie eine an, und die Zielschaltflächen erhalten ein Ziffern-Abzeichen - drücken Sie die passende Zifferntaste, um dieses Ziel sofort auszulösen, ohne zu tippen.

---

## Touch-Zonen verwenden {#how-to-use-touch-zones}

**Was sind Touch-Zonen?**

Der Bildschirm ist in 9 unsichtbare Bereiche für schnelle Aktionen unterteilt:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legende:**

1. **BACK** - Zurück zur Dateiliste
2. **COPY** - Datei an Ziel kopieren
3. **RENAME** - Aktuelle Datei umbenennen
4. **PREV** - Zur vorherigen Datei
5. **MOVE** - Datei an Ziel verschieben
6. **NEXT** - Zur nächsten Datei
7. **COMMAND** - Befehlsmenü öffnen
8. **DELETE** - Aktuelle Datei löschen
9. **PLAY** - Diashow starten/stoppen

**Overlay aktivieren (für Einsteiger empfohlen):**

1. Einstellungen → Player
2. Aktivieren Sie **„Touch-Zonen-Overlay immer anzeigen"**
3. Jetzt sehen Sie ein halbtransparentes Raster

**Probieren Sie es aus:**

1. Öffnen Sie ein beliebiges Foto
2. **Obere rechte Ecke antippen** → Nächste Datei
3. **Obere linke Ecke antippen** → Vorherige Datei
4. **Mittlere rechte Ecke antippen** → Datei löschen
5. **Mittlere linke Ecke antippen** → Datei kopieren

**Deaktivieren, wenn nicht benötigt:**
Einstellungen → Player → „Touch-Zonen-Overlay immer anzeigen" = AUS

Verwenden Sie stattdessen die Schaltflächen im Befehlspanel.

---

## Fotos bearbeiten {#how-to-edit-photos}

**Unterstützte Operationen:**

- Drehen (90°, 180°, 270°)
- Spiegeln (horizontal, vertikal)
- Filter (Graustufen, Sepia, Negativ)
- Anpassen (Helligkeit, Kontrast, Sättigung)

**Schritte:**

1. **Öffnen Sie ein Foto** im Vollbild-Viewer
2. Tippen Sie auf die Schaltfläche **„Bearbeiten"** (oder die mittlere linke Touch-Zone)
3. **Wählen Sie eine Operation:**
   - Drehen: Tippen Sie auf das Dreh-Symbol
   - Spiegeln: Tippen Sie auf das Spiegel-Symbol
   - Filter: Aus der Liste auswählen
   - Anpassen: Schieberegler verwenden
4. Tippen Sie auf **„Speichern"**

**Hinweise:**

- Die Originaldatei wird **überschrieben** (kein Rückgängig!)
- Funktioniert für **lokale und Netzwerkdateien**
- Unterstützt: JPG, PNG, WEBP

---

## Diashow erstellen {#how-to-create-slideshow}

**Schritte:**

1. **Öffnen Sie einen beliebigen Ordner** mit Fotos
2. Tippen Sie auf das **erste Foto**, um den Viewer zu öffnen
3. Tippen Sie auf die Schaltfläche **„Wiedergabe"** (oder die untere rechte Touch-Zone)
4. Die Diashow startet automatisch

**Geschwindigkeit anpassen:**

1. **Ordnereinstellungen bearbeiten:**
   - Hauptbildschirm → Ordner lange gedrückt halten → Bearbeiten
2. Ändern Sie das **„Diashow-Intervall":**
   - Schnell: 2 Sekunden
   - Normal: 5 Sekunden
   - Langsam: 10 Sekunden
3. Tippen Sie auf **„Speichern"**

**Steuerung während der Diashow:**

- **Bildschirm antippen** → Pause/Fortsetzen
- **Nach links/rechts wischen** → Dateien überspringen
- **Auf „Stopp" tippen** → Diashow beenden

---

## Diashow mit Hintergrundmusik erstellen {#how-to-create-slideshow-with-background-music}

**Voraussetzungen:**

- Mindestens ein Ordner/eine Ressource mit Audiodateien (MP3, FLAC usw.)
- **Verfügbar in:** Standard, Lite, Legacy, XR / noLegal (Photos unterstützt kein Audio)

**Einrichtung:**

1. **Einstellungen** → Tab **Medien** → **Bilder**
2. Aktivieren Sie **„Musik während der Diashow abspielen"**
3. Tippen Sie auf die Schaltfläche **„Musikquelle auswählen"**
4. Wählen Sie eine Ressource, die Ihre Musikdateien enthält
5. Tippen Sie auf **„Speichern"** oder schließen Sie die Einstellungen

**Diashow mit Musik abspielen:**

1. **Öffnen Sie einen beliebigen Ordner** mit Fotos/Bildern
2. Tippen Sie auf das **erste Foto**, um den Viewer zu öffnen
3. Tippen Sie auf die Schaltfläche **„Wiedergabe"** (oder die untere rechte Touch-Zone)
4. Die Diashow startet mit laufender Hintergrundmusik

**So funktioniert es:**

- Musik wird zufällig aus der ausgewählten Musikressource abgespielt
- Wenn ein Titel endet, startet automatisch der nächste zufällige Titel
- Die Musik spielt während der Bildübergänge weiter
- Die Musik stoppt, wenn Sie die Diashow beenden oder pausieren

**Hinweise:**

- Musik spielt nur bei **Bildern und GIFs** (nicht bei Videos/Audio)
- Wenn die Diashow ein Video zeigt, pausiert die Musik automatisch
- Die Musik setzt fort, sobald wieder Bilder gezeigt werden
- Funktioniert mit lokalen und Netzwerk-Musikquellen (SMB, SFTP, FTP)

**Musikauswahl anpassen:**

- Fügen Sie mehrere Musikdateien zu Ihrem Musikressourcen-Ordner hinzu
- Die App mischt alle Audiodateien zufällig
- Organisieren Sie Musik in Unterordnern, wenn bei der Musikressource „Unterordner einbeziehen" aktiviert ist

**Fehlerbehebung:**

- Wenn keine Musik spielt: Prüfen Sie, ob die Musikressource mindestens eine Audiodatei enthält
- Wenn die Musik im Netzwerk stottert: Verwenden Sie einen lokalen Ordner oder eine schnellere Netzwerkverbindung
- Für SMB-Musik: Stellen Sie sicher, dass die SMB-Ressource das Protokoll `file://` verwendet (siehe TROUBLESHOOTING.md)

---

## Ordner mit PIN schützen {#how-to-protect-folder-with-pin}

**Schritte:**

1. Hauptbildschirm → **Ordner lange gedrückt halten**
2. Tippen Sie auf **„Bearbeiten"** (Stiftsymbol)
3. Scrollen Sie zum Feld **„PIN-Code"**
4. Geben Sie eine **4-6-stellige PIN** ein (z. B. 1234)
5. Tippen Sie auf **„Speichern"**

**Jetzt gilt:**

- Das Öffnen dieses Ordners erfordert die PIN
- Verhindert unbefugten Zugriff
- Gilt für Durchsuchen und Bearbeiten

**PIN entfernen:**

- Ordner bearbeiten → PIN-Feld leeren → Speichern

**PIN vergessen?**

- Keine Wiederherstellungsoption (aus Sicherheitsgründen so gestaltet)
- Sie müssen den Ordner entfernen und erneut hinzufügen

---

## Eine Datei mit FileDO verschlüsseln {#how-to-encrypt-a-file-with-filedo}

Ein FileDO-Container ist eine einzelne Datei mit der Erweiterung `.fd-sec`, die eine andere, mit einem Passwort gesperrte Datei enthält. Das Format ist dasselbe, das die FileDO-Desktop-App verwendet, sodass ein hier erstellter Container in FileDO geöffnet werden kann und umgekehrt.

**Die Befehle aktivieren:** **Einstellungen** → Tab **Verwaltung** → **FileDO-Verschlüsselungsoperationen**. Das Öffnen eines Containers funktioniert unabhängig davon, ob dieser Schalter an oder aus ist.

**Eine Datei verschlüsseln:**

1. Öffnen Sie in Durchsuchen das Menü **⋮** der Datei.
2. Tippen Sie auf **Mit FileDO verschlüsseln**.
3. Geben Sie das Passwort zweimal ein und bestätigen Sie.
4. Der Container erscheint neben der Datei als `<name>.fd-sec`. Die Originaldatei bleibt unangetastet - löschen Sie sie selbst, wenn Sie sie nicht mehr benötigen.

**Eine Datei entschlüsseln:** Öffnen Sie das **⋮**-Menü der `.fd-sec`-Datei, tippen Sie auf **Mit FileDO entschlüsseln** und geben Sie das Passwort ein. Die wiederhergestellte Datei erscheint neben dem Container.

**Einen Container öffnen, ohne ihn wiederherzustellen:** Tippen Sie in einem Ordner, der alle Dateitypen anzeigt, auf die `.fd-sec`-Datei. Die App fragt nur nach dem Passwort und öffnet die enthaltene Datei im Viewer. Die entschlüsselte Kopie verbleibt im privaten Speicher der App und wird gelöscht, sobald Sie zur Liste zurückkehren. Aktivieren Sie **Passwort merken und bei jeder .fd-sec-Datei versuchen**, um die Abfrage beim nächsten Mal zu überspringen.

**Wo es funktioniert:** Geräteordner, über die Systemordnerauswahl ausgewählte Ordner sowie SMB-, SFTP- und FTP-Freigaben. Bei einem über die Auswahl gewählten Ordner oder einer Netzwerkfreigabe wird die Datei als private Kopie verarbeitet, das Ergebnis unter einem temporären Namen zurückgeschrieben, erneut gelesen und geprüft und erst dann an Ort und Stelle umbenannt - eine vorhandene Datei wird niemals überschrieben.

**Wenn sie sich nicht öffnen lässt:** Die Meldung nennt drei mögliche Ursachen - ein falsches Passwort, eine Datei, die nie ein Container war, oder ein Container, der verändert wurde. Sie lassen sich nicht voneinander unterscheiden. Ein Container, der ein Programm oder ein Skript enthält, wird nicht geöffnet.

**Passwort vergessen?** Es gibt keine Möglichkeit, es wiederherzustellen. Ein leeres Passwort verbirgt die Datei nur vor einem flüchtigen Blick.

---

## Mit Ordnern arbeiten (auswählen, kopieren, verschieben)

Wenn Unterordner als separate Einträge in der Liste angezeigt werden, verhält sich eine Ordnerzeile wie eine Dateizeile.

**Ordnerzeilen aktivieren:** **Einstellungen** → **Allgemein** → **Unterordner separat anzeigen**. Denselben Schalter gibt es pro Ressource auch im Ressourcen-Editor.

**Schritte:**

1. Tippen Sie auf das Kontrollkästchen einer Ordnerzeile oder halten Sie die Zeile lange gedrückt, um einen einzelnen Ordner auszuwählen. Ein kurzer Tipp öffnet den Ordner weiterhin.
2. Verwenden Sie das **⋮**-Menü der Zeile oder die Auswahl-Aktionsleiste, um **Kopieren**, **Verschieben**, **Umbenennen** oder **Löschen** zu wählen.
3. Wählen Sie das Ziel aus. Dateien und Ordner in derselben Auswahl werden gemeinsam in einer Operation übertragen.
4. Das Ziel erhält die gesamte Struktur - jeden Unterordner und jede Datei innerhalb des Quellordners.

**Über Ressourcentypen hinweg:** Ein Ordner kann zwischen Gerät, SMB-, SFTP-, FTP- und Cloud-Ressourcen kopiert oder verschoben werden - die Struktur wird auf der empfangenden Seite neu erstellt.

**Was abgelehnt wird, und warum:** Ein Ziel innerhalb des Ordners selbst oder der aktuelle Speicherort des Ordners wird abgelehnt, bevor irgendetwas kopiert wird; ein über die Systemordnerauswahl gewähltes Ziel ohne echten Dateipfad kann keine Ordner empfangen. Die Meldung nennt den Grund, damit Sie ein anderes Ziel wählen können.

**Abbrechen:** Eine Ordnerübertragung zeigt den Fortschritt an und kann gestoppt werden. Was bereits geschrieben wurde, bleibt am Ziel erhalten - prüfen Sie den Ordner, bevor Sie erneut beginnen. Ein Verschiebevorgang löscht jedes Quellelement erst, nachdem dessen Kopie erfolgreich war, sodass zwischendurch nichts verloren geht.

**In den Hintergrund verschieben:** Sie müssen nicht dabeisitzen und den Fortschrittsdialog beobachten. Schließen Sie ihn, und die Übertragung läuft weiter, sichtbar in Durchsuchen als Streifen am unteren Rand, der die Operation, den Prozentsatz und die gerade bearbeitete Datei zeigt. Tippen Sie auf diesen Streifen, um den vollständigen Fortschrittsdialog zurückzuholen, Abbrechen eingeschlossen.

---

## Papierkorb leeren {#how-to-empty-trash}

Gelöschte Dateien wandern in `.trash/`-Ordner und bleiben dort, bis sie manuell geleert werden.

**Methode 1: Gesamten Papierkorb leeren**

1. **Einstellungen** → Tab **Verwaltung** → **Dateilöschung und Papierkorb**
2. Tippen Sie auf **„Papierkorb leeren"**
3. Bestätigen Sie das Löschen
4. Alle `.trash/`-Ordner in allen Ressourcen werden geleert

**Methode 2: Pro Ordner**

1. Verwenden Sie eine Dateimanager-App
2. Navigieren Sie zum Ordner (z. B. `/storage/emulated/0/DCIM/Camera`)
3. Suchen Sie den Unterordner `.trash/`
4. Löschen Sie ihn manuell

**Warnung:** Dies ist eine **endgültige Löschung**! Dateien können nicht wiederhergestellt werden.

---

## Einstellungen sichern {#how-to-backup-settings}

**Einstellungen exportieren:**

1. **Einstellungen** → Tab **Allgemein** → **Sicherungen, Wiederherstellung und Einstellungsexport**
2. Tippen Sie auf **„Alle Einstellungen in Datei exportieren"**
4. Wählen Sie einen Speicherort (z. B. Downloads)
5. Die Datei wird als `fastmediasorter_backup.xml` gespeichert

**Einstellungen wiederherstellen:**

1. **Einstellungen** → Tab **Allgemein** → **Sicherungen, Wiederherstellung und Einstellungsexport**
2. Tippen Sie auf **„Einstellungen aus Datei importieren"**
4. Wählen Sie die Sicherungsdatei aus
5. Tippen Sie auf **„Wiederherstellen"**
6. Die App startet mit den wiederhergestellten Einstellungen neu

**Was enthalten ist:**
✅ Quick-Sort-Ordner
✅ Anzeigeeinstellungen
✅ Diashow-Intervalle
✅ Netzwerkanmeldedaten (verschlüsselt)
✅ Favoriten
✅ Safe-Mode-Einstellungen

**NICHT enthalten:**
❌ Miniaturbild-Cache
❌ Papierkorb-Inhalt  

---

## Text- und PDF-Dateien anzeigen {#how-to-view-text-and-pdf-files}

**1. Unterstützung aktivieren:**

1. **Einstellungen** → Tab **Medien** → **Dokumente**
2. Aktivieren Sie **„Textdateien unterstützen (.txt, .md, .log, .json, .xml)"** und **„PDF-Dokumente unterstützen"**
3. **Durchsuchen Sie** Ihre Ordner erneut, um die neuen Dateien zu finden.

**2. Nach Medientyp filtern:**

1. Tippen Sie auf das **Filtersymbol** (Trichter) auf dem Hauptbildschirm (oben rechts).
2. Verwenden Sie Kontrollkästchen, um Medientypen auszuwählen:
   - Bilder
   - Videos
   - Audio
   - GIFs
   - **Text** (Neu)
   - **PDF** (Neu)
3. Tippen Sie auf **„Anwenden"**, um nur die ausgewählten Dateien zu sehen.

**3. Text-Viewer:**

- Tippen Sie auf eine beliebige **.txt-, .md-, .log-, .json-, .xml**-Datei.
- **Scrollen** Sie, um zu lesen.
- **Text kopieren:** Lange gedrückt halten, um auszuwählen und zu kopieren.

**4. PDF-Viewer (neue Funktionen):**

- Tippen Sie auf eine beliebige **.pdf**-Datei.
- **Navigationsleiste (unten):**
  - **Zurück/Weiter:** Große Schaltflächen an den Rändern.
  - **Vergrößern (+):** Seite vergrößern.
  - **Verkleinern (-):** Seite verkleinern.
- **Gesten:**
  - **Nach OBEN wischen:** Zur nächsten Seite.
  - **Nach UNTEN wischen:** Zur vorherigen Seite.
  - **Kneifgeste:** Natürliches Vergrößern/Verkleinern.
  - **Doppeltipp:** Zoom zurücksetzen.
  - **Zoom bleibt erhalten:** Die nächste Seite öffnet sich mit dem Zoom und an der Position, an der Sie zuletzt gelesen haben; ein Doppeltipp holt die ganze Seite zurück.
- **Verschieben:** Ziehen Sie, um sich im gezoomten Zustand zu bewegen.
- **Text durch langes Gedrückthalten auswählen (Android 15+):** Halten Sie ein Wort gedrückt, um es direkt aus der eigenen Textebene der Seite auszuwählen - kein OCR-Durchlauf, kein Warten. Kommt dasselbe Wort mehrmals auf der Seite vor, wird das unter Ihrem Finger ausgewählt, nicht das erste. Ziehen Sie die Anfasser, um die Auswahl zu erweitern, und kopieren oder übersetzen Sie sie dann.

---

## E-Books lesen (EPUB) {#how-to-read-e-books-epub}

**Voraussetzungen:**

- **Einstellungen** → Tab **Medien** → **Dokumente** → **EPUB-E-Books unterstützen** muss aktiviert sein (standardmäßig eingeschaltet)
- Unterstütztes Format: `.epub` (DRM-frei)

**Funktionen:**

- **Kapitelnavigation:** Nach links/rechts wischen oder Schaltflächen im Befehlspanel verwenden
- **Inhaltsverzeichnis:** Tippen Sie auf das Listensymbol <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom">, um zu einem bestimmten Kapitel zu springen
- **Schriftgröße:** anpassbar (6px - 144px, Standard 18px)
- **Suche:** Text im aktuellen Buch finden
- **Designs:** Passt sich automatisch an den hellen/dunklen Modus an

**Steuerung:**

1. **Öffnen Sie eine EPUB-Datei** aus der Dateiliste
2. **Tippen Sie auf den Bildschirm**, um das Befehlspanel ein-/auszublenden
3. **Verwenden Sie die unteren Steuerelemente:**
   - `Zurück` / `Weiter`: zwischen Kapiteln navigieren
   - `- A` / `+ A`: Schriftgröße verkleinern/vergrößern
   - `Suche` <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: Text suchen
   - `Inhalt` <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: Inhaltsverzeichnis öffnen
4. **Wischgeste:** natürlich zwischen Kapiteln wechseln

**Hinweis:** Funktioniert nahtlos mit lokalen Dateien und Netzwerkquellen (SMB/SFTP/Cloud). Große Bücher (>50 MB) können bei langsamen Netzwerken beim ersten Laden ein paar Sekunden brauchen.

---

## Netzwerkdateien in externen Apps öffnen {#how-to-open-network-files-in-external-apps}

**Verfügbar für:** SMB-, SFTP-, FTP-Dateien

**Anwendungsfall:** Sie möchten ein Dokument, Foto oder Video von Ihrem Netzwerklaufwerk in einer spezialisierten externen App öffnen (z. B. MS Office, Adobe Acrobat, VLC Player).

**Schritte:**

1. **Navigieren Sie zur Datei** auf Ihrer Netzwerkressource
2. **Tippen Sie auf die Datei**, um sie im Player/Viewer zu öffnen
3. **Tippen Sie auf die Schaltfläche ⓘ (Info)** in der oberen Symbolleiste
4. **Tippen Sie auf „Herunterladen und öffnen"**
5. **Warten Sie auf den Download** - der Fortschrittsdialog zeigt den Prozentsatz
6. **Wählen Sie eine App** aus der Android-App-Auswahl

**Was passiert:**

- Die Datei wird in Ihren `Downloads`-Ordner heruntergeladen
- Der Fortschritt wird in einem Dialog angezeigt (0-100 %)
- Nach Abschluss des Downloads zeigt Android die App-Auswahl
- Sie können die Datei in jeder kompatiblen App öffnen

**Unterstützte Protokolle:**

- ✅ SMB/CIFS-Netzwerkfreigaben
- ✅ SFTP-Server
- ✅ FTP-Server
- ❌ Cloud-Speicher (noch nicht implementiert)

**Tipps:**

- Heruntergeladene Dateien bleiben im `Downloads`-Ordner
- Sie können sie später manuell über einen Dateimanager löschen
- Funktioniert mit allen Dateitypen (Bilder, Videos, Dokumente usw.)
- Bei großen Dateien kann der Download mehrere Minuten dauern

**Beispiel-Anwendungsfälle:**

- Ein Netzwerkdokument in MS Word bearbeiten
- Ein Netzwerkvideo im VLC Player abspielen
- Ein Netzwerk-PDF in Adobe Acrobat anzeigen
- Ein Netzwerkfoto über Messaging-Apps teilen

---

## Songtexte anzeigen {#how-to-view-song-lyrics}

**Voraussetzungen:**

- Audiodatei (MP3, FLAC usw.) mit Interpret- und Titel-Metadaten.
- **Eine Internetverbindung ist erforderlich** (nutzt api.lyrics.ovh).

**Schritte:**

1. **Spielen Sie eine Audiodatei** im Vollbild-Player ab.
2. Tippen Sie auf die Schaltfläche **„Songtext"** im oberen Befehlspanel (oder im Befehlsmenü).
   - *Hinweis: Die Schaltfläche ist nur bei Audiodateien sichtbar.*
3. Warten Sie, bis die Suche abgeschlossen ist.
4. Der Songtext wird in einem scrollbaren Dialog angezeigt.

**Suchlogik:**

1. Die App sucht anhand der Tags **Interpret + Titel**.
2. Fehlen die Tags, versucht sie, den **Dateinamen** zu interpretieren.

---

## Automatische Übersetzung {#auto-translation}

Text aus Bildern, PDFs und Textdateien automatisch übersetzen: **Tesseract** liest den Text, Google ML Kit übersetzt ihn.

**Hauptfunktionen:**

- **Eine Leseengine:** **Tesseract** liest lateinischen und kyrillischen Text (Englisch, Russisch, Ukrainisch, Bulgarisch, Belarussisch); Google ML Kit übersetzt das Ergebnis und erkennt dessen Sprache.
- **Offline:** Funktioniert vollständig auf dem Gerät (nach dem anfänglichen Modell-Download).
- **Intelligentes Overlay:** Der übersetzte Text überlagert den Originaltext in lesbaren Absätzen.

**Einrichtung:**

1. **Einstellungen** → Tab **Medien** → **Sonstiges**
2. Aktivieren Sie **„Übersetzung aktivieren"**
3. Wählen Sie die **Ausgangssprache**:
   - **„Automatisch":** Liest den Text mit dem englischen Modell und erkennt anschließend die Sprache des Gelesenen für die Übersetzung.
   - **Bestimmte Sprache:** Liest mit dem Modell dieser Sprache - wählen Sie sie für kyrillischen Text (z. B. „Russisch").
4. Wählen Sie die **Zielsprache** (z. B. Englisch).

**So verwenden Sie es:**

1. Öffnen Sie ein **Bild**, **PDF** oder eine **Textdatei**.
2. Tippen Sie auf den Bildschirm, um das **Befehlspanel** anzuzeigen.
3. Tippen Sie auf die Schaltfläche **„Übersetzen"** (Symbol A→文).
4. **Beim ersten Start:**
   - Bestätigen Sie den Download des Textmodells für die Ausgangssprache.
   - Bestätigen Sie den Download des Übersetzungsmodells für das Sprachpaar.
5. Der übersetzte Text erscheint in einem Overlay.

**Hinweis:** Die erste Verwendung einer Sprache lädt deren Textmodell, was eine kurze Verzögerung verursacht.

## Intelligente Startbildschirm-Widgets {#home-screen-smart-widgets}

**Verfügbar in:** allen Editionen - der Widget-Satz ist in jedem Build enthalten; jedes Widget folgt seiner eigenen Fähigkeit, sodass das Sprachrekorder-Widget einen Build mit Mikrofonunterstützung benötigt (nicht Lite oder Photos), während die Bilderrahmen- und Ressourcen-Widgets überall funktionieren

**Schnellstart**

1. Gehen Sie zu Ihrem Android-Startbildschirm, halten Sie lange gedrückt und wählen Sie **Widgets**.
2. Ziehen Sie ein FastMediaSorter-Widget (wie 1×1 Schnellsprachrekorder oder Kamera-OCR) auf Ihren Bildschirm.
3. Konfigurieren Sie den Zielordner und die Aufnahmeeinstellungen, und tippen Sie dann auf **Speichern**.
4. Verwenden Sie das Widget, um Aufgaben mit einem Fingertipp direkt von Ihrem Startbildschirm aus auszuführen.

**Szenario-Ablauf**

- Verwenden Sie 1×1-Widgets als eigene Launcher-Symbole, um Hintergrundaktionen sofort zu starten (z. B. einmal tippen, um eine Sprachaufnahme zu starten, erneut tippen, um sie auf Ihrem NAS zu speichern).
- Richten Sie ein **Widget für geplante Vorgänge** ein, um Hintergrund-Dateiübertragungen zu überwachen oder eine „Alle ausführen"-Operation auszulösen.
- Platzieren Sie ein **Zufälliger-Bilderrahmen-Widget**, um eine rotierende Diashow von Familienfotos anzuzeigen, die direkt von einer SMB-Freigabe abgerufen werden.

**Wann es hilft**

- Sie möchten schnelle Verknüpfungen auf Ihrem Startbildschirm für tägliche Aufnahmen (Quittungen, Sprachnotizen), ohne die Hauptoberfläche der App zu öffnen.
- Sie benötigen übersichtliche Widgets, um Medien zu steuern oder geplante Vorgänge sofort auszulösen.

**Das sollten Sie vermeiden**

- Versuchen Sie nicht, Widgets hinzuzufügen, wenn Ihr Android-Launcher die Erstellung benutzerdefinierter Widgets einschränkt.

---

## Die App als Startbildschirm verwenden {#how-to-use-the-app-as-your-home-screen}

FastMediaSorter kann den Startbildschirm Ihres Geräts übernehmen und stattdessen seinen eigenen Desktop anzeigen - Ihre Ordner, eine Uhr, das Wetter, Ihre Apps und eine Taskleiste an einem Rand. Wenn Sie schon einmal einen Windows-Desktop verwendet haben, wird es Ihnen vertraut vorkommen: Dinge bleiben dort, wo Sie sie hinlegen, und eine Start-Schaltfläche öffnet das Menü. Das nennt sich Launcher-Modus und ist nur in den Builds **Standard** und **noLegal** enthalten.

**Aktivieren:**

1. Öffnen Sie **Einstellungen → Allgemein** und schalten Sie **Diese App als Startbildschirm festlegen** ein.
2. Android bittet Sie um Bestätigung. Ab Android 10 ist es eine einzige Frage - „FastMediaSorter als Startbildschirm-App zulassen?" - bestätigen Sie sie einfach. Bei älteren Versionen erscheint die klassische Auswahl beim nächsten Drücken der Home-Taste: Wählen Sie FastMediaSorter und tippen Sie auf **Immer**, oder auf **Nur diesmal**, wenn Sie es erst einmal ausprobieren möchten.
3. Drücken Sie Home. Der Desktop erscheint, bereits mit etwa einem Dutzend nützlicher Dinge gefüllt - eine Uhr, das Wetter, Ihre Ordner, ein Suchfeld -, sodass der erste Tag kein leeres Raster ist.

Bei einer brandneuen Installation gibt es eine Abkürzung: Aktivieren Sie **Als Startbildschirm verwenden** auf der ersten Willkommensseite. Das unterbricht die Einrichtung nicht mit einem Systemdialog - Androids Bestätigung erscheint erst, wenn Sie danach zum ersten Mal **Einstellungen → Allgemein** öffnen.

**Was auf dem Desktop lebt:**

| Zellentyp | Was sie tut |
|-----------|--------------|
| Ressourcen-Verknüpfung | Öffnet einen von Ihnen hinzugefügten Ordner - und Sie wählen, ob er im Durchsuchen-, Diashow- oder Wiedergabemodus öffnet |
| Gadget | Eine Uhr mit Sekunden (Antippen für Alarme), das Wetter an Ihrem Standort, was gerade läuft, ein Übersetzer und zwei Dutzend weitere |
| App-Verknüpfung | Startet jede installierte App; langes Gedrückthalten listet die eigenen Schnellaktionen dieser App auf |
| Kontaktzelle | Öffnet die Karte einer Person, ruft sie an, sendet eine SMS oder öffnet die Messenger-Unterhaltung |
| App-Widget | Dieselben Widgets, die die App für den Android-Startbildschirm anbietet, hier stattdessen platziert |

**Die Taskleiste und das Start-Menü:**

- Die Taskleiste liegt am unteren Rand und enthält die Start-Schaltfläche, die zuletzt verwendeten Apps, die angehefteten Apps und einen kleinen Infobereich mit Uhr, Akku, Netzwerk- und SIM-Signal.
- Möchten Sie sie lieber oben haben? **Einstellungen → Allgemein → Systemlauncher-Einstellungen → Taskleiste → Taskleisten-Position** wechselt zwischen **Unten** und **Oben**. Das Start-Menü folgt der Leiste und klappt von oben herunter, wenn die Leiste dort oben liegt.
- Die Start-Schaltfläche öffnet das Menü: FastMediaSorter öffnen, Ihre Ressourcen, eine Ressource hinzufügen, Android-Einstellungen, App-Einstellungen, Launcher-Einstellungen, Desktop-Inhalte bearbeiten und am Ende Neustart, Ausschalten und **Launcher-Modus verlassen**. Neustart und Ausschalten funktionieren nur, wenn Ihr Gerät das einer gewöhnlichen App erlaubt - auf den meisten Handys tun sie schlicht nichts.

**Ihre Apps:** Das App-Raster gruppiert Apps in Abschnitte, jeder mit einer kleinen Überschrift. Tippen Sie auf eine Überschrift, um einen selten geöffneten Abschnitt einzuklappen; eingeklappte Überschriften rücken nebeneinander, sodass der Desktop kürzer wird, statt Lücken zu hinterlassen. Ein frischer Desktop teilt die vorbelegten Apps in zwei Teile: einen **Google**-Abschnitt für die bereits installierten Google-Apps und einen **Apps**-Abschnitt für Ihre eigenen - Messenger, Spiele und was immer sonst auf dem Gerät ist. Keine App landet in beiden. Halten Sie eine beliebige App in der Liste lange gedrückt für **Auf Desktop legen** und **An Taskleiste anheften**.

**Neu anordnen:**

- Halten Sie ein leeres Feld des Desktops lange gedrückt. Vier Optionen erscheinen: **Element hinzufügen..**, **Desktop bearbeiten**, **Hintergrundbild**, **Launcher-Einstellungen**. Die neue Zelle landet genau auf dem Feld, das Sie gedrückt haben.
- **Element hinzufügen..** öffnet eine Auswahl: eine App, eine Funktion, einen Ihrer Ordner, einen Radiostream, eine Person, eine Systemaktion, einen geplanten Vorgang, ein Gadget oder eine Aktion. Zu den Gadgets gehören die Jetzt-läuft-Karte - sie zeigt, was gerade auf dem Gerät läuft, und bringt Sie mit einem Fingertipp zu diesem Player - sowie die Übersetzer-Zelle.
- **Desktop bearbeiten** schaltet den Bearbeitungsmodus ein, ebenso wie **Desktop-Inhalte bearbeiten** im Start-Menü. Während der Bearbeitung: Ziehen Sie eine Zelle, um sie zu verschieben, ziehen Sie den Eckgriff eines Gadgets, um es in der Größe zu ändern, tippen Sie auf **+**, um etwas hinzuzufügen, und wählen Sie **Vom Desktop entfernen** bei einer Zelle, um sie zu entfernen. Tippen Sie auf **Fertig**, wenn Sie fertig sind.
- Teilen Sie das Gerät mit jemandem? Aktivieren Sie **Desktop sperren** in den Launcher-Einstellungen - das lange Gedrückthalten bewirkt dann nichts mehr, sodass das Layout nicht versehentlich verschoben werden kann.
- Andere Apps können hier ihre eigenen Verknüpfungen ablegen, genau wie auf jedem anderen Startbildschirm.

**Hoch- und Querformat sind zwei getrennte Desktops.** Was Sie im Hochformat anordnen, ist nicht das, was Sie sehen, wenn Sie das Gerät seitlich drehen - jede Ausrichtung behält ihr eigenes Layout und ihre eigenen eingeklappten Abschnitte. Die App weist einmalig darauf hin, beim ersten Drehen eines von Ihnen angeordneten Desktops. Die Einstellungen selbst - Taskleisten-Position, Dichte, Hintergrundbild - werden von beiden geteilt.

**Mehr oder weniger auf den Bildschirm bringen:** **Einstellungen → Allgemein → Systemlauncher-Einstellungen → Desktop → Rasterdichte** bietet **Locker**, **Standard**, **Dicht** und **Sehr dicht** - geräumigere Zellen oder mehr Verknüpfungen pro Bildschirm.

**Zu Ihrem alten Startbildschirm zurückkehren** - eine dieser drei Möglichkeiten:

- Öffnen Sie das Start-Menü, wählen Sie **Launcher-Modus verlassen** und bestätigen Sie.
- Schalten Sie **Diese App als Startbildschirm festlegen** in **Einstellungen → Allgemein** aus.
- Gehen Sie direkt zu Androids eigener Liste der Startbildschirm-Apps: **Einstellungen → Allgemein → Systemlauncher-Einstellungen → System → Startbildschirm ändern**.

Ihr Desktop-Layout bleibt in jedem Fall erhalten, sodass das erneute Einschalten des Modus es genau so zurückbringt, wie Sie es verlassen haben.

**Eine ehrliche Warnung.** Ein paar Geräte weigern sich, die Wahl zu speichern. Manche billigen Nachrüst-Autoradios und andere fest verbaute Android-Boxen erzwingen bei jedem Start ihren werkseitigen Startbildschirm zurück, egal was Sie gewählt haben. Das ist die eigene Firmware des Geräts, die sich über Sie hinwegsetzt, kein Fehler der App, und keine App kann das umgehen. Wenn sich Ihres so verhält, wählen Sie FastMediaSorter nach einem Neustart erneut als Startbildschirm-App - und wenn es immer noch nicht bleibt, lässt dieses Gerät es schlicht nicht zu.

---

## FMS auf einer Android-TV-Box nutzen {#how-to-use-fms-on-android-tv-box}

FastMediaSorter läuft auf jeder Android-TV-Box oder Set-Top-Box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, generische Android-Boxen). Kein Touchscreen erforderlich - die App ist vollständig über die TV-Fernbedienung oder eine Bluetooth-Tastatur bedienbar.

**Was Sie benötigen:**

- Android-TV-Box mit Android 8.0+ (Standard/Lite/Photos) oder Android 6.0+ (Legacy-Edition)
- TV-Fernbedienung mit Steuerkreuz oder eine Bluetooth-Tastatur
- Optional: Heim-NAS (SMB), USB-Laufwerk oder SD-Karte mit Medien

**Navigation mit einer TV-Fernbedienung:**

| Taste | Aktion |
|--------|--------|
| Steuerkreuz hoch/runter/links/rechts | Fokus zwischen Elementen bewegen |
| OK / Enter | Element öffnen oder bestätigen |
| Zurück | Zum vorherigen Bildschirm gehen |
| Rücktaste | In Durchsuchen einen Ordner nach oben navigieren |
| Rot | Ausgewählte Datei(en) löschen |
| Grün | Ausgewählte Datei(en) kopieren |
| Gelb | Ausgewählte Datei(en) verschieben |
| Blau | Ausgewählte Datei umbenennen |
| Kanal hoch / Kanal runter | Vorherige/nächste Datei im Player |

**Schritte:**

1. Installieren Sie die App über Google Play oder laden Sie eine APK per Sideload. Die Standard-Edition wird empfohlen.
2. Drücken Sie auf dem Hauptbildschirm **OK** auf der Schaltfläche (+), um eine Ressource hinzuzufügen.
3. Wählen Sie **Lokaler Ordner** für USB-/SD-Speicher oder **Netzwerkordner**, um sich per SMB mit einem NAS zu verbinden.
4. Navigieren Sie nach dem Hinzufügen der Ressource mit Steuerkreuz + OK hinein, um Dateien zu durchsuchen.
5. Öffnen Sie ein beliebiges Video, Bild oder eine Audiodatei - der Player funktioniert vollständig über die Fernbedienung.
6. Um eine Diashow zu starten, öffnen Sie einen Bildordner und navigieren Sie zur Schaltfläche **Diashow** in der Befehlsleiste.
7. Um der Diashow Hintergrundmusik hinzuzufügen, gehen Sie zu **Einstellungen → Medien → Bilder**, aktivieren Sie **Musik während der Diashow abspielen** und wählen Sie Ihre Musikressource aus.

**Tipps:**

- Halten Sie Steuerkreuz hoch/runter gedrückt, um in langen Dateilisten schneller zu scrollen.
- Drücken Sie **F1** auf einer Bluetooth-Tastatur, um auf jedem Bildschirm eine bildschirmspezifische Tastenkürzel-Referenz zu öffnen.
- Die Farbtasten der TV-Fernbedienung können in **Einstellungen → Verwaltung → Steuerung & Tastenbelegung** neu zugewiesen werden.

---

## Bildschirm aufnehmen {#how-to-record-your-screen}

**Verfügbar in:** Standard, XR/noLegal

**Schritte:**

1. Starten Sie sie über das Overflow-Menü des Hauptbildschirms (**Bildschirmvideoaufnahme**), das Schnellstart-Panel oder die Rand-Geste **Bildschirmaufnahme starten**.
2. Bestätigen Sie Androids Abfrage, den Bildschirm oder nur diese App freizugeben - sie erscheint bei jedem Aufnahmestart und kann nicht übersprungen werden.
3. Eine kleine Pille in der Ecke zeigt **Bildschirm wird aufgenommen** mit Pause-/Fortsetzen- und Stopp-Steuerung. Auch eine Benachrichtigung bietet **Stopp**.
4. Tippen Sie auf **Stopp**, wenn Sie fertig sind.

**Was passiert:**

- Die Aufnahme erfasst alles auf dem Bildschirm, einschließlich anderer Apps, zu denen Sie wechseln, zusammen mit Audio.
- Das fertige Video wird im Ordner `Movies` Ihres Geräts gespeichert.

**Hinweis:** Der Android-Bestätigungsschritt ist eine Systemsicherung für alles, was Ihren Bildschirm aufnimmt - die App kann das nicht abschalten.

---

## Eine Sprachnotiz aufnehmen {#how-to-record-a-voice-note}

**Verfügbar in:** Standard, Legacy, XR / noLegal

**Schritte:**

1. Starten Sie eine Aufnahme über den Eintrag **Sprachaufnahme** im Overflow-Menü, das Startbildschirm-Widget **Schnellrekorder** oder die Rand-Geste **Audioaufnahme starten**.
2. Sprechen Sie - eine Anzeige **Aufnahme läuft..** (oder eine schwebende Pille über der gerade aktiven App) zeigt, dass sie läuft.
3. Tippen Sie auf **Stoppen und speichern** (oder tippen Sie erneut auf das Widget/die Geste), um zu beenden.

**Was passiert:**

- Die Aufnahme wird an dem in den Einstellungen gewählten Mikrofon-Zielort gespeichert oder im Ordner `Recordings` Ihres Geräts, falls keiner festgelegt ist.
- Das Starten einer Sprachnotiz über das Widget oder die Rand-Geste funktioniert auch, während Sie eine andere App nutzen - ein kleines schwebendes Bedienelement bleibt oben, sodass Sie stoppen können, ohne zurückzuwechseln.

**Wo Sie den Speicherordner festlegen:** Einstellungen → Verwaltung → Sprachrekorder.

---

## Die App-interne Kamera verwenden {#how-to-use-the-in-app-camera}

**Verfügbar in:** Standard, Lite, Photos (nur Foto), Legacy, XR/noLegal

**Schritte:**

1. Öffnen Sie in Durchsuchen die Symbolleiste oder das Overflow-Menü und tippen Sie auf **Mit Kamera aufnehmen** (Foto) oder **Video aufnehmen**.
2. Wechseln Sie direkt auf dem Kamerabildschirm zwischen **Foto** und **Video**, wenn Sie es sich anders überlegen.
3. Stellen Sie den Zoom mit einem voreingestellten Chip (0,5x/1x/2x..) oder dem Schieberegler darunter ein - beide bleiben synchron.
4. Tippen Sie auf die Seitenverhältnis-Schaltfläche, um den Rahmen zu formen - **4:3**, **16:9** oder **Vollbild**. Der Sucher selbst ändert sich, sodass das, was Sie sehen, dem gespeicherten Foto entspricht, und die Wahl wird beim nächsten Öffnen der Kamera gespeichert (16:9, bis Sie es ändern).
5. Tippen Sie auf die Aufnahmeszenario-Schaltfläche, um zu wählen, wie das Bild aufgenommen wird - normal, Nacht, Porträt, Selfie, Makro, Sport oder Dokument. Makro wechselt zum dedizierten Nahfokus-Objektiv, Selfie wechselt zur Frontkamera, Sport hält die Belichtung kurz, damit Bewegung eingefroren wird, und Dokument ist auf das Fotografieren flacher Seiten und Bildschirme abgestimmt. Es werden nur die Szenarien angezeigt, die Ihr Gerät tatsächlich liefern kann, das aktive wird auf der Schaltfläche genannt, und ein manueller Objektivwechsel bringt die Kamera zurück auf normal.
6. Tippen Sie auf den Auslöser (oder die Aufnahmetaste), um aufzunehmen. Das Ergebnis wird direkt in der Ressource gespeichert - lokal oder im Netzwerk -, die Sie gerade durchsucht haben.

**Tipps:**

- Tippen Sie an eine beliebige Stelle im Sucher, um dort zu fokussieren und die Belichtung festzulegen - ein kleiner Ring markiert die Stelle.
- Die Rand-Geste **Videoaufnahme starten** öffnet die Kamera bereits im Videomodus und beginnt die Aufnahme, sobald die Vorschau bereit ist - schnell, aber genau diese Verknüpfung speichert im `Movies`-Ordner Ihres Geräts statt in der durchsuchten Ressource.
- Aktivieren Sie **Fotos geotaggen** neben den Kameraeinstellungen, um den GPS-Standort in jedes aufgenommene JPEG einzubetten - standardmäßig ausgeschaltet, bis Sie es aktivieren. **Dateiinfo** zeigt dann das Aufnahmedatum und den EXIF-GPS-Punkt des Fotos als antippbaren Link, der in Ihrer Karten-App oder im Browser öffnet.

**Wo Sie die Kameraeinstellungen finden:** Einstellungen → Verwaltung → Fotografie.

---

## Doppelte Dateien finden und löschen {#how-to-find-and-delete-duplicate-files}

**Schritte:**

1. Öffnen Sie einen Ordner in Durchsuchen und dann das **Overflow-Menü** <img src="icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> in der Symbolleiste.
2. Tippen Sie auf **Duplikate finden**, um Treffer selbst zu überprüfen, oder auf **Duplikate finden und löschen**, um sie sofort zu entfernen.
3. Bei **Duplikate finden** wählt die App bereits jede Kopie außer der ältesten in jeder Gruppe vor - passen Sie die Auswahl an, tippen Sie dann auf **Auswahl löschen** und bestätigen Sie.
4. **Duplikate finden und löschen** entfernt dieselben vorausgewählten Kopien direkt nach dem Scan, ohne Bestätigungsschritt - verwenden Sie zuerst **Duplikate finden**, wenn Sie vor dem Löschen noch einmal prüfen möchten.

**Stattdessen nach Größe bereinigen:**

1. Tippen Sie im selben Overflow-Menü auf **Nach Größe löschen..**
2. Wählen Sie **Kleiner als** oder **Größer als**, legen Sie eine Größe fest und tippen Sie auf **Analysieren**.
3. Prüfen Sie die Anzahl und den freizugebenden Speicherplatz, und tippen Sie dann auf **Dateien löschen**, um zu bestätigen.

**Hinweise:**

- Der Scan gleicht Dateien anhand des Inhalts in drei Durchgängen ab - Größe, dann ein schneller Hash, dann eine vollständige SHA-256-Prüfung -, sodass umbenannte Duplikate trotzdem erkannt werden.
- Das Löschen nach Größe zeigt, wie viel Speicherplatz Sie freigeben werden, bevor etwas entfernt wird; Netzwerk- und Cloud-Quellen überspringen den Papierkorb, sodass diese Löschung sofort und endgültig ist.

---

## Nutzungsstatistiken anzeigen {#how-to-view-your-usage-statistics}

**Schritte:**

1. Gehen Sie zu **Einstellungen → Allgemein → Statistikerfassung** und schalten Sie sie ein.
2. Tippen Sie auf **Statistik** (erscheint direkt unter dem Schalter), um das Dashboard zu öffnen.

**Was Sie sehen werden:**

- Übersichtskarten für sortierte Dateien, freigegebenen Speicherplatz und mit Medienwiedergabe verbrachte Zeit.
- Eine Aufschlüsselung nach Typ (Bilder, Videos, Audio, Dokumente..).
- Einklappbare Abschnitte mit weiteren Details: Operationen, Aufnahme, Ansehen, Bearbeitung, Quellen und allgemeine Nutzung.

**Einen Bericht teilen:**

- **An den Autor senden** öffnet Ihre E-Mail-App mit einer angehängten Zusammenfassung, adressiert an den Entwickler.
- **Exportieren** teilt dieselbe Zusammenfassung über das normale Android-Freigabeblatt, sodass Sie sie überall speichern oder senden können.

**Hinweis:** Alles bleibt auf Ihrem Gerät, bis Sie es senden oder exportieren - die Datenschutzdetails finden Sie in den FAQ.

---

## Eine SD-Karte oder ein angeschlossenes Laufwerk verwenden {#how-to-use-an-sd-card-or-connected-drive}

Eine Speicherkarte oder ein vom Handy eingebundenes USB-Laufwerk enthält Ressourcen genau wie interner Speicher.

**Schritte:**

1. Öffnen Sie **Ressource hinzufügen** und beginnen Sie mit dem Hinzufügen eines lokalen Ordners. Der Abschnitt **Wechseldatenträger** erscheint nur, während etwas angeschlossen ist, und listet jedes Volume mit Namen und freiem Speicherplatz auf.
2. Tippen Sie auf das Volume. Kann die App es nicht per Pfad erreichen, erklärt sie warum und öffnet die Systemordnerauswahl - wählen Sie dort dasselbe Volume und gewähren Sie Zugriff auf den gewünschten Ordner.
3. Die Ressource erscheint in der Liste mit einem Wechseldatenträger-Symbol, sodass eine Kartenressource auf einen Blick erkennbar ist.

**Verschieben und Kopieren:** Ganze Ordner wandern mit ihrer vollständigen Unterordnerstruktur auf eine Karte und zurück, genauso wie zwischen dem Gerät und einer Netzwerkressource.

**Nicht genug Platz:** Ein Kopier- oder Verschiebevorgang, der nicht passt, wird abgelehnt, bevor er beginnt, und die Meldung nennt den Datenträger und wie viel Speicherplatz fehlt - schaffen Sie dort Platz oder wählen Sie ein anderes Ziel.

**Wenn der Datenträger ausgeworfen wird:** Seine Ressourcen werden als nicht verfügbar markiert, statt entfernt zu werden. Schließen Sie die Karte erneut an, und sie funktionieren, ohne ein zweites Mal eingerichtet werden zu müssen.

**Unter Android 6:** Das System meldet Apps keine eingebundenen Volumes, sodass der Abschnitt für Wechseldatenträger auf diesen Geräten leer bleibt.

---

## Einen per Direktpfad hinzugefügten Ordner neu verbinden {#how-to-reconnect-a-folder-added-by-direct-path}

Ein Ordner, den Sie durch Eingabe oder Durchsuchen seines Pfads hinzugefügt haben, kann Ihre Fotos, Videos und Musik anzeigen, aber keines Ihrer Dokumente. Das liegt nicht an einem Scan, der sie übersehen hat: Ein Store-Build liest Textdateien, PDFs und E-Books nur über einen Ordner, den Sie mit der Systemauswahl verbunden haben. Ein erneutes Verbinden richtet dieselbe Ressource über diese Auswahl auf denselben Ordner aus, und die Dokumente erscheinen.

**Schritte:**

1. Tippen Sie auf das Dreipunkt-Menü der Ordnerkarte in der Hauptliste.
2. Wählen Sie **Ressource neu verbinden**. Die Systemordnerauswahl öffnet sich, bereits in diesem Ordner, sofern das Handy dies zulässt.
3. Wählen Sie denselben Ordner aus und bestätigen Sie.
4. Wählen Sie einen anderen Ordner, nennt die App beide Ordner und fragt nach, bevor etwas geändert wird.

**Was erhalten bleibt:** Der Name, die Position in Ihrer Liste, die PIN, das Symbol, die Quick-Sort-Rolle, Ihre Favoriten und Ihre Zeitpläne bleiben alle bestehen - die Ressource wird neu adressiert, nicht neu erstellt.

**Wo Sie es nicht sehen werden:** In Builds, die Ordner weiterhin direkt per Pfad lesen, und unter Android 10 und älter fehlt der Eintrag, weil dort nichts fehlt.

---

## Speicherort für Aufnahmen und Downloads festlegen {#how-to-choose-where-captures-and-downloads-are-saved}

Fotos von der App-internen Kamera, Screenshots, Schnappschüsse und automatisch heruntergeladene Dateien schreiben jeweils in einen von Ihnen gewählten Ordner, und dieser Ordner muss keine Ihrer Ressourcen sein.

**Schritte:**

1. Öffnen Sie die Einstellung für das, was Sie speichern - Aufnahme, Screenshot, Schnappschuss oder Auto-Download.
2. Wählen Sie den Zielordner aus. Der Systemordner-Browser öffnet sich, sodass Sie auf jeden lokalen Ordner zeigen können, auch auf einen, den Sie der App noch nie hinzugefügt haben.
3. Dieser Ordner wird zum Schreibziel nur für diese Einstellung. Er bleibt aus Ihrer allgemeinen Ressourcenliste heraus, sodass die Wahl eines Ablageordners für Screenshots den Hauptbildschirm nicht überfüllt.

**Tipps:**

- Jede der vier Einstellungen hat ihr eigenes Ziel - Screenshots und Kamerafotos können an völlig unterschiedlichen Orten landen.
- Es ist völlig in Ordnung, mehrere davon auf einen Ordner zu richten, wenn Sie lieber alles an einem Ort haben möchten.

### Namen von Aufnahmedateien

Neue Aufnahmen verwenden das Muster `prefix_yyMMdd_HHmmss`. Die festen Präfixe sind `photo`, `screenshot`, `audio`, `video`, `screen_video` und `video_frame`, sodass der Dateiname seine Quelle erkennen lässt. Existiert im Zielordner bereits ein Name, fügt die App das Suffix ` (2)` vor der Erweiterung hinzu. Ein manuell in der Kamera eingegebener Dateiname bleibt eine Überschreibung und wird nicht verändert.

---

## Von einer anderen App geteilte Dateien empfangen {#how-to-receive-files-shared-from-another-app}

Das Freigabeblatt jeder App kann Dateien an FastMediaSorter senden, die sie dann dorthin kopiert, wo Sie möchten.

**Schritte:**

1. Teilen Sie in der anderen App die Datei oder Dateien und wählen Sie **FastMediaSorter**.
2. Wählen Sie den Zielordner auf dem Empfangsbildschirm.
3. Starten Sie den Kopiervorgang.

**Sie müssen nicht darauf warten.** Der Kopiervorgang läuft weiter, nachdem der Empfangsbildschirm geschlossen wurde, mit einer Benachrichtigung, die den Fortschritt während der Arbeit zeigt, und einer Ergebnisbenachrichtigung, wenn er fertig ist. Verlassen Sie die App, sperren Sie das Gerät, machen Sie weiter - die Übertragung ist nicht daran gebunden, dass dieser Bildschirm geöffnet bleibt.

Verfügbar in den Builds Standard, Lite, Photos und Legacy.

---

## Die integrierten Programme verwenden {#how-to-use-the-built-in-programs}

**Verfügbar in:** allen Editionen - das Programme-Menü und das Panel sind in jedem Build enthalten, aber jedes Programm folgt seiner eigenen Fähigkeit: Der Netzwerk-Monitor benötigt Standard oder noLegal, der Wear-Companion benötigt Standard oder noLegal, das Minispiel fehlt in XR und noLegal, und Spiegel benötigt eine Frontkamera mit in den Einstellungen aktivierter Kameraaufnahme. Der Taschenrechner, die Front-Taschenlampe und Systeminformationen sind in jedem Build enthalten.

Neben dem Durchsuchen und Abspielen von Dateien bringt die App eine Reihe kleiner integrierter Programme mit - einen Taschenrechner, eine Bildschirmlampe, einen Netzwerk-Monitor, einen Sprachrekorder und mehr. Sie sind standardmäßig ausgeschaltet: Jedes wird über eine eigene Einstellung eingeschaltet, und die meisten der zugehörigen Schalter befinden sich zusammen unter **Einstellungen → Verwaltung**.

**Schnellstart**

1. Gehen Sie zu **Einstellungen → Verwaltung** und schalten Sie ein, was Sie möchten - zum Beispiel **Taschenrechner**, **Front-Taschenlampe**, **Netzwerk-Monitor**, **Minispiel** oder **Systeminformationen**.
2. Öffnen Sie das Dropdown-Menü des Hauptfensters. Die von Ihnen aktivierten Programme werden dort aufgelistet.
3. Tippen Sie auf eines, um es auszuführen.

**Wo ein Programm erscheint**

Ein Programm kann auf bis zu vier Oberflächen angeboten werden, und jede Oberfläche übernimmt ihren Inhalt und ihre Reihenfolge aus derselben einzigen Liste, sodass sie nie auseinanderdriften:

- **Programme-Menü** - das Dropdown des Hauptfensters und das Programme-Panel, das es wiederholt.
- **App-Start-Panel** - das Schnellzugriffs-Overlay.
- **Startbildschirm-Widget** - nur für die Programme, die eines haben; heften Sie es aus der App-eigenen Widget-Auswahl an.
- **Launcher-Desktop** - wenn Sie die App als Startbildschirm verwenden, fügt das Einschalten eines Programms dessen Zelle automatisch hinzu.

**Was im Angebot ist**

In der Reihenfolge, in der sie erscheinen:

- **Schnellaufnahme** - ein Foto direkt in die App aufnehmen.
- **Sprachaufnahme** - eine Sprachnotiz aufnehmen.
- **Taschenrechner** - ein wissenschaftlicher Taschenrechner mit Verlauf und Speicherplätzen.
- **Netzwerk-Monitor** - Live-Messwerte für die aktive Verbindung, WLAN, Mobilfunk, Bluetooth und Standort, plus ein Traceroute, das die Route zu einem Host Hop für Hop durchläuft und weiterzählt, wenn ein Hop nicht antwortet.
- **Foto-OCR-Übersetzung** - Text fotografieren und übersetzen.
- **Bildschirmvideoaufnahme** - den Bildschirm aufnehmen.
- **Download per Link** - eine Datei über einen eingefügten Link abrufen.
- **Minispiel** - das kleine, in die App eingebaute Spiel.
- **Systeminformationen** - ein Gerätebericht, der ohne Öffnen der Einstellungen erreichbar ist.
- **Wear-Companion** - der Uhr-Bildschirm, in Builds mit Uhr-Brücke.
- **Front-Taschenlampe** - verwandelt den Bildschirm selbst in eine Lampe: Sie öffnet weiß bei voller Fensterhelligkeit, eine vertikale Wischgeste ändert die Helligkeit, eine kleine Schaltfläche oben links wählt und merkt sich eine andere Farbe, und ein einzelner Tipp schließt sie. Es wird nur die Fensterhelligkeit berührt, sodass Ihre Geräteeinstellung danach unverändert bleibt.
- **Wasser-Taschenlampe** - dasselbe Licht für nasse Hände. Sie schaltet Kamerablitz und Bildschirm gemeinsam ein und sperrt dann den Bildschirm: Sie sehen nur die Uhrzeit und einen kurzen Hinweis, und das Berühren des Glases bewirkt gar nichts - eine Lautstärketaste schließt sie. Auch die Systemleisten verschwinden, damit eine nasse Hand nicht auf eine Navigationsschaltfläche trifft; eine bewusste Wischgeste kann sie dennoch zurückholen. Gemacht für Regen und für die Dusche - die zwei Orte, an denen das Glas eher auf Wasser als auf Sie reagiert. Das Verlassen über eine Systemgeste schaltet auch das Licht aus, sodass es niemals in einer Tasche brennend bleibt. Auf der Uhr gibt es keinen Blitz, sodass allein das Display das Licht ist. Sie ersetzt nicht den in einer Uhr oder einem Handy eingebauten Wassersperrmodus; keine App kann diesen einschalten.
- **Spiegel** - verwandelt das Handy in einen beleuchteten Spiegel: Die Frontkamera füllt den Bildschirm innerhalb eines hellen weißen Felds, das Ihr Gesicht beleuchtet, und das Bild wird so gespiegelt, wie es ein echter Spiegel zeigt, mit einer Eckschaltfläche, um das Licht auszuschalten, ohne den Bildschirm zu verlassen. Zoom-Voreinstellungen - x1, x2, x3, x5 - befinden sich unten links und öffnen bei x3. Eine Foto- und eine Video-Schaltfläche speichern direkt in denselben Ordner, den auch die Aufnahme verwendet, Video mit Ton. Zoom, Spiegelung und Hintergrundbeleuchtungsstatus werden zwischen den Aufrufen gespeichert. Nur die Fensterhelligkeit steigt, nie die Geräteeinstellung selbst, sodass das Handy sofort wieder normal ist, sobald Sie es verlassen. Standardmäßig eingeschaltet auf jedem Gerät mit Frontkamera, solange die Kameraaufnahme selbst nicht in den Einstellungen deaktiviert ist.

Das Panel und der Launcher enthalten zusätzlich direkte Kamera-Verknüpfungen - ein Foto aufnehmen und weiterleiten, ein Foto aufnehmen und bearbeiten, ein Foto aufnehmen und übersetzen, eine Videoaufnahme starten und den Kameraordner öffnen.

**Wann es hilft**

- Sie möchten einen Taschenrechner oder eine Taschenlampe, ohne die App zu verlassen oder auf einem überfüllten Handy nach einer separaten App zu suchen.
- Sie nutzen die App als Startbildschirm und möchten eine Ein-Tipp-Zelle für ein Werkzeug, das Sie häufig verwenden.

**Das sollten Sie vermeiden**

- Erwarten Sie nicht, dass die Wasser-Taschenlampe eine Wischgeste zum Startbildschirm übersteht - eine System-Navigationsgeste verlässt sie trotzdem, und das Licht geht damit aus.
- Erwarten Sie nicht jedes Programm in jedem Build - die obige Liste ist der vollständige Satz, und ein Build ohne die zugrunde liegende Fähigkeit zeigt diesen Eintrag einfach nicht.

---

## Ihren Assistenten bitten, Medien zu finden und zu öffnen {#how-to-ask-your-assistant-to-find-and-open-media}

**Verfügbar in:** jedem Build, ab Android 16. Ältere Android-Versionen bieten die Funktion schlicht nicht an, und in der App muss dafür nichts eingeschaltet werden.

Ab Android 16 registriert die App eine Reihe von Assistenten-Aktionen - AppFunctions, in Androids eigener Bezeichnung - beim System. Der Assistent Ihres Geräts kann sie dann namentlich aufrufen, sodass Sie laut nach einem Foto, einem Video oder einem Computerordner fragen können, statt die App selbst zu öffnen und danach zu suchen.

**Wonach Sie fragen können**

- **Ihre Medien durchsuchen** - der Assistent übergibt Ihre Worte an die Suche der App und zeigt die Treffer.
- **Eine Mediendatei öffnen** - ein Foto, ein Video oder ein Titel öffnet sich direkt im Viewer oder Player der App.
- **Einen Computerordner öffnen** - einer Ihrer Netzwerk- oder Cloud-Ordner öffnet sich im Browser-Bildschirm.

**Schnellstart**

1. Stellen Sie sicher, dass das Gerät mit Android 16 oder neuer läuft und ein Systemassistent eingerichtet ist.
2. Bitten Sie den Assistenten um die gewünschten Medien und nennen Sie FastMediaSorter, wenn das Gerät mehrere Medien-Apps hostet.
3. Die App öffnet sich mit dem Ergebnis - der Suchliste, der Datei oder dem Ordner, nach dem Sie gefragt haben.

**Wann es hilft**

- Ihre Hände sind beschäftigt - beim Kochen, Fahren, ein Kind halten - und sich durch Ordner zu tippen ist keine Option.
- Sie erinnern sich, wie eine Datei heißt, aber nicht, wo Sie sie abgelegt haben.

**Das sollten Sie vermeiden**

- Erwarten Sie es nicht unterhalb von Android 16: Die Assistenten-Aktionen sind Teil des neueren Systems, sodass der Assistent sie auf einem älteren Handy nicht sieht.
- Erwarten Sie nicht, dass der Assistent einen PIN-geschützten Ordner erreicht - die Sperre gilt weiterhin, und der Ordner fragt wie gewohnt nach seiner PIN.

---

## Brauchen Sie weitere Hilfe?

- 📖 **Schnellstart:** [QUICK_START.md](QUICK_START-de.md)
- ❓ **FAQ:** [FAQ.md](FAQ-de.md)
- 🔧 **Fehlerbehebung:** [TROUBLESHOOTING.md](TROUBLESHOOTING-de.md)
- 🐛 **Problem melden:** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

</div>
