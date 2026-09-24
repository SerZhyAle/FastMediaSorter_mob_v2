---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-de.html
---
<div lang="de" dir="ltr" markdown="1">

{% include lang-switcher.html doc="README" dir="/docs/" current="de" %}

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![Lizenz](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

**📦 Download:** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Bei IzzyOnDroid erhältlich" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

Installierst du die APK direkt? Android warnt vor einem Paket, das es noch nicht kennt - [warum die Warnung erscheint und worauf du tippen musst](INSTALL_TRUST.md).

**📘 Benutzerdokumentation:** [Schritt-für-Schritt-Anleitungen für jede Funktion, mit Suche](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## Über das Projekt

**FastMediaSorter v2** ist eine komplette Shell für ein Android-Gerät. Sie übernimmt den Startbildschirm, spielt deine Medien ab, öffnet Live-Streams, startet deine Apps, spricht mit deiner Uhr, behält das Gerät im Blick und verwaltet jede Datei, die dir gehört - in lokalen Ordnern, auf Netzlaufwerken (SMB, SFTP, FTP) und in Cloud-Speichern (Google Drive, OneDrive, Dropbox).

Sie basiert auf acht Säulen: Geräte-Shell, Medienplayer, Live-Streams, App-Start, Ersatz für Standard-Apps, Begleiter auf der Uhr, Geräteüberwachung und ein vollwertiger Dateimanager. Das Sortieren von Dateien über all diese Quellen hinweg war der Ausgangspunkt der App und ist immer noch das Fundament, auf dem alles andere aufbaut - aber längst nicht mehr alles.

Dieses Handbuch folgt jetzt demselben öffentlichen Wortschatz wie das maßgebliche Funktionsverzeichnis in [FEATURES.md](FEATURES.md) und die Dokumentenübersicht in [DOCS_MAP.md](DOCS_MAP.md). Nutze diese beiden Seiten als aktuelle verlässliche Quelle für die App-Story, die verfügbaren Editionen und den aktuellen Funktionsumfang.

## Windows-Version 🖥️

Suchst du eine Desktop-Lösung? Wirf einen Blick auf **Fast Media Sorter for Windows** (früher FastMediaSorter LITE) - eine schlanke Windows-Forms-Anwendung zum schnellen Sortieren, Anzeigen und Verwalten von Bild- und Videodateien:

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [So veröffentlichst du PC-Ordner für Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - teile deine PC-Ordner mit der App über SFTP (Companion-Import / QR-Scan auf dem Handy).

Zu den Funktionen gehören:

- Schnelle Navigation durch große Ordner mit Bildern und Videos
- Diashow- und Zufallswiedergabe-Modi
- Verfolgung zuletzt verwendeter Dateien und Ordner
- Dateioperationen: Verschieben, Kopieren, Umbenennen und Löschen
- Bildpanel für schnelle visuelle Navigation
- Anpassbare Tastenkombinationen für einen effizienten Arbeitsablauf
- Mehrsprachige Unterstützung (Englisch/Russisch)
- Unterstützt Windows 7/10/11 mit .NET Framework 4.8

## Inhaltsverzeichnis

- [Download](#download-)
- [Editionen](#editions-)
- [Hauptfunktionen](#key-features)
- [Unterstützte Medienformate](#supported-media-formats-)
- [Screenshots](#screenshots-)
- [Anwendungsszenarien](#usage-scenarios-)
- [Dokumentation](#documentation-)
- [Wear OS Begleiter](#wear-os-companion-)
- [Build-Anleitung](#build-instructions)
- [Testen](#testing-)
- [Erste Schritte](#first-steps-quick-usage-guide-)
- [Technologie-Stack](#technology-stack)

## Editionen 🎯 {#editions-}

FastMediaSorter v2 erscheint in **sieben Editionen** - fünf für Alltags-Smartphones und -Tablets (Standard, Lite, Photos, Legacy, FOSS) sowie zwei Headset- und Sideload-Builds, VR und noLegal. Das maßgebliche Funktionsraster wird direkt aus dem Build generiert in [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md):

| Edition | Beschreibung | Hinweise |
|--------|-------------|-------|
| **Standard** | Vollständige Version | Größter Funktionsumfang für Medien, Dokumente, OCR, Übersetzung und Cloud-Zugriff |
| **Lite** | Schlanke Version | Nur lokale Dateien - Video, Audio und Bilder; keine Netzwerkquellen, Cloud, Dokumente oder Streams |
| **Photos** | Foto-fokussierte Version | Nur Bilder, mit SMB/FTP/SFTP und Cloud; kein Video und kein Audio |
| **Legacy** | Kompatibilitätsorientierte Version | Gleicher Funktionsumfang wie Standard, einschließlich SMB/FTP/SFTP und Cloud (Google Drive, Dropbox, OneDrive); gebaut für Android 6/7 (API 23+) |
| **FOSS** | F-Droid-Katalogversion | Keine proprietären SDKs: lokale Medien, Dokumente, EPUB und SMB/FTP/SFTP; keine Cloud, keine Streams, kein OCR, keine Übersetzung und kein Wear OS Begleiter |
| **VR** | Store-konformer Headset-Build | Vollständiges Medienangebot für Headsets; ohne Google Cast und ohne Wear OS Begleiter |
| **noLegal** | Sideload-Build | Alles aus Standard plus der immersive OpenXR-Player und Sideload-exklusive Extras |

### Welche Edition sollte ich herunterladen?

- **Standard** ⭐ **(Empfohlen)**: Beste Standardwahl für die meisten Nutzer
- **Lite**: Bevorzugt, wenn du ein leichteres Paket und eine einfachere Einrichtung möchtest
- **Photos**: Bevorzugt für foto-fokussierte Arbeitsabläufe
- **Legacy**: Wähle diese für Android 6/7-Geräte (API 23+) - inklusive Netzwerk und Cloud
- **FOSS**: Wähle diese aus dem F-Droid-Katalog, wenn du einen Build ohne proprietäre SDKs möchtest
- **VR**: Wähle diese für ein XR-Headset - der Store-Build ohne Cast- und Wear-Unterstützung
- **noLegal**: Nur Sideload - wähle diese Edition, wenn du den immersiven OpenXR-Player brauchst

Für die genaue Funktionsverfügbarkeit pro Edition nutze die maßgebliche Dokumentation:

- [Vollständige Funktionsliste (maßgeblich)](FEATURES.md)
- [How-To (Tabelle der Funktionsverfügbarkeit)](HOW_TO-de.md)
- [Schnellstart (Editionswahl)](QUICK_START-de.md)
- [Programmeinschränkungen](LIMITATIONS.md)

> 🧭 **Erster Start:** direkt unter der Sprachauswahl lässt dich die App ein **Geräteprofil** wählen (Smartphone, Tablet, TV, Auto, Bilderrahmen, VR und mehr), das die Startvorgaben für dich anpasst - jederzeit änderbar in den Einstellungen. Siehe [Erster Start: Wähle dein Geräteprofil](QUICK_START-de.md#first-launch-choose-your-device-profile-30-seconds-).

## Download 📥 {#download-}

📲 **[Bei Google Play holen](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**Kompilierte APK-Dateien werden NICHT in diesem GitHub-Repository gespeichert.** Alle Builds sind auf **Google Drive** verfügbar:

🔗 **[Alle Builds von Google Drive herunterladen](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Edition | Dateiname | Beschreibung |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Voller Funktionsumfang (Cloud, OCR, EPUB, Übersetzung) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Nur lokale Medien (Videos, Audio, Bilder; kein Netzwerk, keine Cloud, keine Dokumente oder Streams) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Nur Bilder, mit Netzwerk (SMB/FTP/SFTP) und Cloud |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Gleiche Funktionen wie Standard, einschließlich Netzwerk (SMB/FTP/SFTP) und Cloud; Android 6/7 (API 23+) |

> **Hinweis**: Alle Builds werden nach erfolgreicher Kompilierung automatisch zu Google Drive hochgeladen.
>
> 🔐 **ZIP-Passwort: `1`** (APK-Dateien werden als passwortgeschützte ZIP-Archive verpackt, um Google-Drive-Beschränkungen zu umgehen)

## Screenshots 📱 {#screenshots-}

| Hauptbildschirm | Dateiaktionen | Einstellungen |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **Player-Ansicht** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

Bilder in voller Größe:

- [Hauptbildschirm](images/Screenshot_20251109_000251.png)
- [Dateiaktionen](images/Screenshot_20251109_000314.png)
- [Einstellungen](images/Screenshot_20251109_000323.png)
- [Player-Ansicht](images/Screenshot_20251114_184930.png)

## Hauptfunktionen {#key-features}

- 🗂️ **Einheitliche Oberfläche:** Dateien aus allen Quellen in einem einzigen Fenster anzeigen und verwalten.
- ⚡ **Schnelles Sortieren:** Dateien mit einem Klick in vorkonfigurierte Zielordner kopieren oder verschieben.
- ⭐ **Favoritensystem:** Wichtige Dateien als Favoriten markieren und schnell über einen eigenen Tab abrufen, der Favoriten aus allen Quellen zusammenfasst.
- 🔒 **PIN-Schutz:** Einzelne Ressourcen mit Zugriffs-PINs sichern, um unbefugtes Durchsuchen und Bearbeiten zu verhindern.
- ⚙️ **Konfiguration pro Ressource:** Diashow-Intervall, Scan-Tiefe (Unterordner) und Vorschaubild-Erzeugung für jeden Ordner einzeln anpassen.
- 🧭 **Geräteprofil-Einrichtung:** Ein Erststart-Profil für Smartphones, Tablets, TV/Media-Boxen, Auto-Kopfeinheiten, Medienplayer, Bilderrahmen, Audioplayer, E-Book-Reader, VR-Headsets oder benutzerdefinierte Vorgaben wählen; die App wendet passende Sicherheits-, Bildschirm-, Inhalts- und Befehlspriorität-Vorgaben an.
- 📋 **Vordefinierte intelligente Ressourcen:** Integrierte virtuelle Ressourcen - **Alle Musik**, **Alle Videos**, **Alle Fotos** - die Medien aus deinem gesamten Gerät ohne jede Konfiguration zusammenfassen. Sofortiger Zugriff auf deine gesamte Mediathek, ohne einzelne Ordner manuell hinzuzufügen.
- 🖥️ **Netzwerk- und Cloud-Unterstützung:** Arbeite mit Dateien auf deinen Netzlaufwerken (SMB mit automatischem Netzwerk-Scan), SFTP-Servern, FTP und in Cloud-Speichern (Google Drive, Dropbox, OneDrive).
- 🖼️ **Flexible Ansicht:** Dateien als anpassbares Raster oder detaillierte Liste mit Seitennummerierung für große Sammlungen (1000+ Dateien) anzeigen.
- ▶️ **Integrierter Player:** Wiedergabe von Video und Audio, Ansicht von Bildern und GIFs, ohne die App zu verlassen. Unterstützt Diashow und Vollbild-Zoom.
- 🧩 **Standard-Player-Integration:** Optionale Wiedergabe-Schalter lassen FastMediaSorter als System-Medienhandler für Öffnen-/Teilen-Intents (ACTION_VIEW / ACTION_SEND) auftreten und leiten Hardware-Medientasten-Weckereignisse an den Audio-Wiedergabedienst weiter.
- 🗣️ **Assistant AppFunctions (Android 16+):** Die App meldet für den Assistenten aufrufbare Aktionen - Medien durchsuchen, eine Datei öffnen oder einen Computerordner öffnen - sodass der Systemassistent deines Geräts deine Inhalte einfach per Sprachbefehl finden und öffnen kann.
- 🎛️ **Hardware-Tasten-Unterstützung:** Lenkradsteuerung, Headset-Tasten und physische Medientasten (Play/Pause, Weiter, Zurück) werden vollständig über den Hintergrund-Audiodienst unterstützt - keine Bildschirminteraktion nötig.
- 📻 **Internet-Streams (Streams-Bildschirm):** Internetradio (http/https, Icecast/Shoutcast mit ICY-Titelanzeige), HLS/DASH-Streams und RTSP-Quellen direkt über einen eigenen Streams-Bildschirm abspielen. URLs manuell hinzufügen, eine `.m3u`-Playlist importieren oder einen kuratierten FastMediaSorter-Katalog herunterladen. Favoriten oben anheften; nach Kategorie und Sprache filtern. Inline-Audio: Radio spielt aus der Liste über eine angeheftete untere Mini-Steuerung, während die Liste scrollbar bleibt. Video und RTSP öffnen im Vollbild-Player. Verfügbar in Standard, Legacy, VR und noLegal; nicht vorhanden in Lite und Photos.
- 🎵 **Songtext-Unterstützung:** Songtexte für den aktuell laufenden Titel anzeigen. Sucht automatisch anhand von Metadaten (Interpret/Titel) über `api.lyrics.ovh`, mit Rückfall auf die Analyse des Dateinamens.
- 🎶 **Hintergrundmusik für die Diashow:** Musik während Bild-Diashows abspielen. Jede Audio-Ressource als Musikquelle wählen, mit zufälliger Titelwiedergabe, Lautstärkeregelung und Titelnamenanzeige. Auf den Titelnamen tippen, um zu einem anderen zufälligen Titel zu springen. Funktioniert nahtlos mit Netzwerk- und Cloud-Dateien.
- ✏️ **Bildbearbeitung:** Drehen, Spiegeln, Filter anwenden (Graustufen, Sepia, Negativ), Helligkeit/Kontrast/Sättigung anpassen - für lokale und Netzwerkdateien.
- 🗂️ **Unterstützung für Binärdateien:** Binärdateien (ZIP, RAR, APK, ISO, EXE, DLL usw.) mit erzeugten Vorschaubildern anzeigen und verwalten, die die Dateiendung zeigen. Kontextmenü mit Teilen/Öffnen mit/Kopieren/Verschieben/Umbenennen/Löschen. Nur im Modus "Alle Dateien" verfügbar.
- ⌨️ **Tastatur, Maus & Gamepad:** Vollständige Tastatur-, Maus- und Gamepad-Eingabe auf allen Bildschirmen - Browse, Player, Einstellungen, Dialoge. Vollständig neu belegbar über Einstellungen → Verwaltung → Steuerung & Tastenbelegung; F1 auf jedem Bildschirm zeigt eine bildschirmspezifische Hilfeübersicht. D-Pad-Listennavigation; Rechtsklick-Kontextmenü und Hover-Effekte für die Maus.
- 🔍 **Sortieren und Filtern:** Dateien nach Name, Datum, Größe und Dauer ordnen. Filter für die Schnellsuche anwenden. Unterstützung für versteckte Dateien (beginnend mit `.`) mit eigenem Schalter.
- ↩️ **Rückgängig & Papierkorb:** Die letzte Aktion (Kopieren, Verschieben, Löschen) rückgängig machen, mit Soft-Delete in den `.trash/`-Ordner. Enthält eine "Papierkorb leeren"-Funktion für Ressourcen.
- 🎨 **Moderne Oberfläche:** Unterstützung für helle und dunkle Themes, intuitive Bedienung, Material Design 3.
- 💾 **Intelligentes Caching:** Zweistufiges Laden von Video-Metadaten (1 MB initial, 5 MB erweitert) und konfigurierbarer Vorschaubild-Cache (2 GB Standard, bis zu 16 GB).
- 📄 **Dokumentenbetrachter:** Integrierter Betrachter für Textdateien (.txt, .md, .log, .json, .xml) und PDF-Dokumente mit Zoom, Verschieben und Gestennavigation.
- 📚 **EPUB-E-Book-Reader:** Nativer EPUB-Reader mit Kapitelnavigation, Inhaltsverzeichnis, Schriftgrößenregelung, Suche im Buch und Unterstützung für dunkles/helles Theme. Funktioniert mit lokalen und Netzwerkdateien.
- 📥 **Herunterladen & Öffnen:** Netzwerkdateien (SMB/SFTP/FTP) auf den lokalen Speicher herunterladen und mit Fortschrittsanzeige in externen Apps öffnen.
- 🌐 **Automatische Übersetzung:** Text aus Bildern, PDFs und Textdateien vollständig auf dem Gerät sofort übersetzen: **Tesseract** liest den Text in lateinischer und kyrillischer Schrift, und Google ML Kit übersetzt ihn. Unterstützt sowohl den Standardmodus als auch den **lupenartigen Überlagerungsmodus** für Übersetzungen direkt an Ort und Stelle.
- 📱 **Widget-Unterstützung:** Über ein Dutzend Startbildschirm-Widgets mit breiter Abdeckung - Ressourcen-Verknüpfungen, Medienplayer, Kamera-Aufnahme, Rechner, geplante Aufgaben, Favoriten, Minispiele und mehr. Die vollständige Auswahl findest du in der Widget-Auswahl deines Launchers.
- 🏠 **Startbildschirm-Modus:** Lass die App den Startbildschirm deines Geräts sein (Standard- und noLegal-Builds): ein eigener Desktop mit Ressourcen-Verknüpfungen, die direkt in Browse, Diashow oder Wiedergabe öffnen, größenveränderbare Gadgets wie Uhr und Wetter, Kontaktkacheln, die keine Kontakte-Berechtigung benötigen, ein App-Raster und eine Taskleiste. Jederzeit abschaltbar - Android stellt danach deinen vorherigen Startbildschirm wieder her.
- ⏰ **Geplante Dateioperationen:** Dateioperationen (Kopieren/Verschieben/Löschen) mit zeitbasierten Regeln, flexiblen Filtern und Hintergrundausführung automatisieren.
- 👆 **Erweiterte Gesten:** Intelligente Zoom-Steuerung (2x/3x/4x) für Bilder und intuitive Berührungszonen für die Dateinavigation.
- 📸 **Bild speichern:** Das aktuelle Videobild als PNG- oder JPG-Schnappschuss aufnehmen und in jeder konfigurierten Ressource speichern - lokal oder im Netzwerk. Ausgabeformat und Zielressource werden in den Videoeinstellungen festgelegt.
- 🖨️ **Drucken:** Dokumente (PDF, TXT) und Bilder direkt aus dem integrierten Player an einen Drucker senden. Netzwerk- und Cloud-Dateien werden vor dem Drucken lokal zwischengespeichert.
- ⬇️ **Stream-Offload:** Eine Netzwerkdatei vor oder während der Wiedergabe mit Echtzeit-Fortschrittsanzeige in den lokalen Cache herunterladen. Eine optionale Aufräum-Aufforderung gibt danach Speicherplatz frei.
- 🔊 **DTS/DTS-HD-Audio:** DTS- und DTS-HD-Audiospuren werden per Software über einen angepassten FFmpeg-Build dekodiert - keine spezielle Hardware nötig.
- 🎨 **Video-Farbe & Helligkeit:** Farbton und Helligkeit in Echtzeit mit Media3-GPU-Effekten anpassen. Die Einstellungen bleiben während der Sitzung über Videodateien hinweg erhalten.
- 📤 **An FastMediaSorter teilen:** Dateien aus jeder App über das Standard-Android-Teilen-Menü empfangen und mit einem Tipp in eine ausgewählte Ressource kopieren.
- 📷 **Kamera-Aufnahme in Browse:** Mit der Gerätekamera ein Foto aufnehmen und direkt in der aktuellen Ressource speichern - lokal oder im Netzwerk -, ohne die App zu verlassen.
- 🔗 **Automatischer Link-Download:** Eine beliebige http(s)-URL über das Android-Teilen-Menü an die App senden; die Mediendatei wird heruntergeladen und automatisch in einer ausgewählten Ressource gespeichert.
- 👁️ **Ein-Augen-3D-Modus:** Stereo-Inhalte (SBS/OU) für die bequeme Ansicht auf flachen Bildschirmen auf ein Auge zuschneiden; funktioniert für Video und Bilder.
- 📲 **Bildschirmaufnahme & Aufzeichnung:** Randstreifen-Geste links für Screenshots, Schnellfotos, Zuschneiden und Teilen sowie Bildschirm-/Sprach-/Videoaufzeichnung, ohne die aktuelle Datei zu verlassen.
- 📊 **Nutzungsstatistik (opt-in):** Lokales Dashboard mit sortierten Dateien, freigegebenem Speicherplatz und Wiedergabezeit - nichts verlässt das Gerät, außer du exportierst es.
- 🧹 **Duplikatsuche & Größenbereinigung:** Inhaltsbasierte Duplikatsuche (Größe, Schnell-Hash, SHA-256) mit manuellem oder automatischem Löschen, plus eine Löschung nach Größe.

## Unterstützte Medienformate 🎞️ {#supported-media-formats-}

FastMediaSorter v2 unterstützt eine breite Palette an Formaten:

- **Bilder:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF
- **Video:** MP4, MKV, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG
- **Audio:** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, DTS, DTS-HD
- **Dokumente:** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **Binärdateien** (Modus "Alle Dateien"): ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO und über 60 weitere Formate

## Anwendungsszenarien 💡 {#usage-scenarios-}

Hier sind einige Möglichkeiten, wie FastMediaSorter v2 dir helfen kann:

### 1. 📸 Kamerafotos organisieren

Verbinde dein Handy oder öffne einen lokalen Kameraordner. Richte einen Zielordner "Beste Fotos" ein. Öffne den Betrachter, wische schnell durch tausende Fotos und tippe auf die Zielschaltfläche, um die besten Aufnahmen sofort zu kopieren.

### 2. 🏠 Netzwerk-Backup (NAS)

Füge dein Heim-NAS über SMB hinzu. Durchsuche deine lokalen Mediendateien. Wähle mehrere Dateien oder einen Bereich aus und "Verschiebe" sie zur sicheren Aufbewahrung auf dein NAS, um Speicherplatz auf deinem Gerät freizugeben.

### 3. ☁️ Cloud-Verwaltung

Verbinde dein Google-Drive-, Dropbox- oder OneDrive-Konto. Durchsuche deine Cloud-Dateien, ohne sie alle herunterzuladen. Lösche unerwünschte Dateien oder organisiere sie direkt in der Cloud in Ordner.

### 4. 📺 Diashow & Präsentation

Öffne einen Ordner mit Familienfotos oder Präsentationsfolien. Tippe auf "Wiedergabe", um eine Diashow zu starten. Nutze die Einstellungen pro Ressource, um die Foliendauer nach Wunsch anzupassen.

### 5. ⭐ Favoriten verwalten

Markiere wichtige Dateien beim Durchsuchen mit der Sternschaltfläche. Tippe später im Hauptmenü auf den Tab "Favoriten", um sofort auf alle deine Favoriten aus allen Quellen an einem Ort zuzugreifen - perfekt, um eine kuratierte Sammlung deiner besten Medien zu erstellen.

### 6. 🎶 Diashow mit Hintergrundmusik

Füge deine Musiksammlung als Ressource hinzu. Aktiviere in **Einstellungen → Medien → Bilder** die Option **"Musik während Diashow abspielen"** und wähle deine Musikressource. Wenn du nun eine Diashow deiner Fotos startest, spielen deine Lieblingstitel im Hintergrund. Tippe auf den Titelnamen, um zu einem anderen zufälligen Titel zu springen und die perfekte Stimmung für deine Fotopräsentationen zu schaffen.

### 7. 🖼️ Digitaler Bilderrahmen auf einem Tablet

Verwandle jedes Android-**Tablet** in einen wunderschönen, dauerhaft aktiven digitalen Bilderrahmen. Stelle es auf einen Ständer, verbinde es mit deinem Heim-PC (SMB) oder Cloud-Speicher - Fotos werden direkt gestreamt, ohne lokalen Speicherplatz zu belegen. Passe das Diashow-Intervall an, halte den Bildschirm dauerhaft an, füge Hintergrundmusik hinzu und genieße deine Erinnerungen. Selbst alte, langsame Budget-Tablets funktionieren hierfür einwandfrei - die App ist für ressourcenschonende Dauerwiedergabe optimiert.

### 8. 🍿 Heimkino & VR

Sieh deine Lieblingsserien von deinem PC oder deiner Cloud direkt auf deinem Handy oder VR-Headset. Kein Warten aufs Kopieren, keine Sorge um freien Speicher. Einfach auf Wiedergabe drücken, und die nächste Folge startet automatisch.

**Anwendungsfälle für VR-Headsets** - FastMediaSorter läuft nativ auf Android-basierten VR-Headsets (Meta Quest, Pico und ähnlichen) ohne jede Änderung:

- **🎬 Riesiges virtuelles Kino**: Öffne ein Video von deinem Heim-NAS oder deiner Cloud und sieh es auf einer virtuellen Leinwand in Wandgröße. Keine Notwendigkeit, gigabytegroße Dateien auf das Headset zu kopieren - die App streamt direkt über dein Heimnetzwerk. Wenn eine Folge endet, startet die nächste automatisch.
- **🎵 Immersiver Musikplayer**: Starte deine Musiksammlung in der VR-Umgebung. Der Hintergrund-Audiodienst hält die Musik am Laufen, auch wenn du zwischen Apps wechselst oder den VR-Startbildschirm öffnest. Hardware-Headset-Tasten (Play/Pause, nächster Titel) funktionieren, ohne den Controller zu berühren.
- **🖼️ Wandgroßer VR-Bilderrahmen**: Verwandle dein VR-Headset in ein immersives Foto-Erlebnis - starte eine Diashow, und deine Fotos füllen eine riesige virtuelle Wand um dich herum. Kombiniere sie mit Hintergrundmusik für ein filmreifes, raumfüllendes Erinnerungserlebnis. Streame Fotos direkt von deinem Heim-PC oder deiner Cloud, damit der Headset-Speicher frei bleibt.

### 9. 🧹 Download-Organizer

Ist dein Downloads-Ordner unübersichtlich? Öffne ihn im Quellenpanel, richte Zielschaltflächen für "Dokumente", "Bilder" und "Installationsdateien" ein. Scanne schnell durch die Dateien, sieh sie dir an und sortiere sie mit einem Tipp an den richtigen Ort. Du kannst Dateien sogar direkt auf deinem Netzwerkcomputer sortieren, indem du dein Handy als Fernbedienung verwendest.

### 10. 🚗 Automusik mit Android-Kopfeinheit

Installiere FastMediaSorter auf deiner Android-basierten Autostereoanlage oder Kopfeinheit. Füge USB-Stick- oder SD-Karten-Musikordner hinzu - oder nutze die eingebaute virtuelle Ressource **Alle Musik**, um sofort ohne Einrichtung auf deine gesamte Sammlung zuzugreifen. Hardware-Medientasten (Lenkradsteuerung, Lautstärkeregler) funktionieren nahtlos über den Hintergrund-Audiodienst: Play/Pause, nächster/vorheriger Titel, alles ohne den Bildschirm zu berühren. Die App merkt sich die Wiedergabeposition und setzt beim Start automatisch fort.

Mit aktiviertem **Streams**-Bildschirm spielt dieselbe Kopfeinheit auch Internetradiosender direkt über mobile Daten oder Wi-Fi ab - ohne separate TuneIn- oder RadioDroid-App. Füge eine beliebige Radio-URL hinzu oder importiere einen kuratierten Senderkatalog vom Extensions-Bildschirm. Die angeheftete Mini-Steuerung zeigt den aktuellen ICY-Titelnamen, während die Senderliste sichtbar bleibt.

### 11. 📺 Medienzentrale auf einer Android-TV-Box

Installiere FastMediaSorter auf jeder Android-TV-Box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV oder eine generische Android-Box). Verbinde dich über SMB mit einem Heim-NAS, füge Google Drive oder Dropbox hinzu, oder schließe einen USB-Stick an - alles aus einer App. Steuere den gesamten Ablauf mit einer Fernbedienung oder Bluetooth-Tastatur: das D-Pad bewegt den Fokus, **OK** öffnet Elemente, **Zurück** kehrt zur vorherigen Ebene zurück, und **Rücktaste** geht im Browser einen Ordner nach oben. Die farbigen Tasten der Fernbedienung sind gängigen Dateiaktionen zugeordnet (**Rot** = Löschen, **Grün** = Kopieren, **Gelb** = Verschieben, **Blau** = Umbenennen). Starte eine Vollbild-Diashow mit Hintergrundmusik auf dem Fernseher oder wechsle zur Audiowiedergabe mit Albumcover und Songtext. Kein Touchscreen erforderlich.

## Dokumentation 📚 {#documentation-}

**🗺️ Documentation Map / Карта документации:** [Alle Dokumente ansehen / Все документы](DOCS_MAP.md)

**🌐 Offizielle Website:** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### Maßgebliche Quellen (Single Source of Truth)

Die folgenden Dateien gelten als maßgebliche Quellen für nutzerrelevante Details:

- [Vollständige Funktionsliste](FEATURES.md)
- [Dokumentenübersicht](DOCS_MAP.md)
- [Produktgeschichte](PRODUCT_HISTORY.md)
- [Downloads (EN)](DOWNLOADS.md)
- [How-To-Anleitungen](HOW_TO-de.md)
- [Programmeinschränkungen](LIMITATIONS.md)
- [Schnellstart-Anleitung](QUICK_START-de.md)
- [Nutzungsbedingungen](TERMS_OF_SERVICE.md)

Ausführliche Anleitungen sind in mehreren Sprachen verfügbar:

**🇺🇸 English:**

- [Product History](PRODUCT_HISTORY.md)
- [How-To Guides](HOW_TO.md)
- [Launcher Web Portal](launcher/index.md)
- [Wear OS Web Portal](wear/index.md)
- [Quick Start](QUICK_START.md)
- [FAQ](FAQ.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Program Limitations](LIMITATIONS.md)
- [Downloads Guide](DOWNLOADS.md)
- [Complete Feature List](FEATURES.md)

**🇷🇺 Русский:**

- [История продукта](PRODUCT_HISTORY-ru.md)
- [Руководства](HOW_TO-ru.md)
- [Быстрый Старт](QUICK_START-ru.md)
- [FAQ](FAQ-ru.md)
- [Устранение неполадок](TROUBLESHOOTING-ru.md)
- [Ограничения программы](LIMITATIONS-ru.md)
- [Скачивание сборок](DOWNLOADS-ru.md)

**🇺🇦 Українська:**

- [Історія продукту](PRODUCT_HISTORY-uk.md)
- [Посібники](HOW_TO-uk.md)
- [Швидкий Старт](QUICK_START-uk.md)
- [FAQ](FAQ-uk.md)
- [Вирішення проблем](TROUBLESHOOTING-uk.md)
- [Обмеження програми](LIMITATIONS-uk.md)
- [Завантаження збірок](DOWNLOADS-uk.md)

**Technische Dokumentation / Entwicklerdokumentation:**

- [Architekturüberblick](ARCHITECTURE.md)
- [DevOps & Build-Skripte](DEV_OPS.md)
- [Technologie-Stack](TECH_STACK.md)
- [Wear OS Dokumentation](WEAR_OS_QUICK_START.md)
- [Open-Source-Komponenten](OPEN_SOURCE.md)

## Wear OS Begleiter ⌚ {#wear-os-companion-}

FastMediaSorter enthält eine vollwertige eigenständige Wear OS App und einen Handy-Begleiter, die für Smartwatch-Formfaktoren entwickelt wurden.

- Durchsuche und spiele Ordner und Favoriten vom gekoppelten Handy, dem eigenen Speicher der Uhr und SMB-/FTP-/SFTP-Freigaben, die die Uhr direkt über Wi-Fi erreicht
- Cloud-Ressourcen bleiben auf dem Handy - die Uhr hat keinen eigenen Cloud-Client; eine Cloud-Datei erreicht sie nur, wenn du sie vom Handy aus mit "Senden an.." schickst
- Verschiebe Dateien zwischen Handy und Uhr, sende live von der Uhr, und nutze kleine eingebaute Tools (Rechner, Netzwerkmonitor, Minispiel), ohne die Handy-App zu öffnen
- UI und Laufzeitverhalten optimiert für runde und kompakte Displays
- Eigenes Webportal, Einrichtungsanleitungen und Problembehebung für Uhr-Arbeitsabläufe

Medien, Netzwerkfreigaben und Dateiübertragung sind in der Vollversion der Uhr-App (direkte APK) enthalten. Die Google-Play-Version ist eine kleine Erstveröffentlichung - Rechner, Stoppuhr, Minispiel und Einstellungen; das [Wear OS Portal](wear/index.md) kennzeichnet, was jede Version bietet.

Wear OS Dokumentation:

- 🌟 **[Wear OS Webportal](wear/index.md)** - Vollständige Funktionsübersicht, Screenshots und App-Store-Downloads
- [Wear OS Schnellstart](WEAR_OS_QUICK_START.md) - Schritt-für-Schritt-Anleitung zum Koppeln und Einrichten
- [Wear OS Einrichtung](WEAR_OS_SETUP.md) - Modularchitektur und Konfiguration der Begleiter-Brücke
- [Wear OS Abschnitt in Features](FEATURES.md#16-settings--navigation)

## Build-Anleitung {#build-instructions}

### Anforderungen

- Android Studio Hedgehog (2023.1.1) oder neuer

- JDK 17+
- Android SDK 35
- Mindest-Android-Version: 8.0 (API 26) für Standard/Lite/Photos/VR/noLegal; 6.0 (API 23) für Legacy

### Build

1. Klone das Repository:

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Öffne das Projekt in Android Studio.
3. Warte, bis die Gradle-Synchronisierung abgeschlossen ist.
4. Starte die App auf einem Emulator oder einem physischen Gerät.

### Bevorzugte Build-Befehle (Windows / PowerShell)

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### Erzeugte APKs 📦

Nach jedem erfolgreichen Build wird die erzeugte APK-Datei automatisch mit Zeitstempel in den `DOWNLOADS`-Ordner im Projektstamm kopiert. Dort findest du deinen gesamten Build-Verlauf.

## Testen 🧪 {#testing-}

FastMediaSorter v2 nutzt **Maestro** für End-to-End-Tests, um Qualität und Zuverlässigkeit der App sicherzustellen.

### Schneller Testlauf

```bash
# Install Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# Or Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell as Administrator) - External: install.ps1 is the Maestro installer
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Run smoke tests (2-3 minutes)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# Or use shortcut
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**Hinweis**: Verwende NICHT `npm install -g maestro-cli` - das ist ein anderes, unabhängiges Paket!

### Testsuiten

- **Smoke-Tests** (`maestro/smoke/`): Tests der Kernfunktionalität (~2-3 Min.)
  - App-Start und Berechtigungen
  - Lokales Durchsuchen von Dateien
  - Medienwiedergabe
  - Bildansicht

- **Critical-Path-Tests** (`maestro/critical/`): Grundlegende Vorgänge (~1-2 Min.)
  - Dateioperationen (Kopieren, Verschieben, Löschen)
  - Persistenz der Einstellungen

### Dokumentation

- 📚 [Schnellstart-Anleitung](../maestro/QUICK_START.md)
- 📝 [Tests schreiben](../maestro/WRITING_TESTS.md)
- 🔍 [Testbeispiele](../maestro/EXAMPLES.md)
- 🔧 [Problembehebung](../maestro/TROUBLESHOOTING.md)
- 📖 [Vollständige Dokumentation](../maestro/README.md)

### CI/CD-Integration

Tests laufen bei jedem Push automatisch über GitHub Actions. Siehe [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml).

## Erste Schritte (Kurzanleitung) 🚀 {#first-steps-quick-usage-guide-}

1. **Einen Ordner (Ressource) hinzufügen:**
    - Tippe auf dem Hauptbildschirm auf die Schaltfläche mit dem Plus-Symbol (+), um eine neue Ressource hinzuzufügen.
    - Wähle den Ressourcentyp (z. B. "Lokaler Ordner").
    - Nutze das Scannen oder füge den Ordner manuell hinzu. Nach dem Hinzufügen erscheint er in der Liste auf dem Hauptbildschirm.

2. **Dateien ansehen:**
    - Doppeltippe (oder halte gedrückt) auf die hinzugefügte Ressource in der Liste.
    - Der Browse-Bildschirm öffnet sich, wo du alle Mediendateien dieses Ordners als Liste oder Raster siehst.
    - Nutze die Schaltflächen im oberen Bereich zum Sortieren, Filtern oder Wechseln der Ansicht.

3. **Wiedergabe und Sortierung:**
    - Tippe auf eine Datei, um sie im Vollbild-Player zu öffnen.
    - Nutze Wischen nach links/rechts oder Berührungszonen zur Navigation zwischen Dateien.
    - Für Operationen (Kopieren, Verschieben) nutze die entsprechenden Berührungszonen oder Schaltflächen im Bedienfeld.

4. **Zielordner konfigurieren (Destinations):**
    - Lege in den Einstellungen im Tab "Destinations" bis zu 30 Ordner fest, die für das schnelle Sortieren verwendet werden.
    - Alternativ aktiviere "Is Destination" im Bearbeitungsbildschirm einer beliebigen Ressource, um sie zur Schnellsortierliste hinzuzufügen.
    - Danach erscheinen auf dem Player-Bildschirm Schaltflächen zum schnellen Kopieren oder Verschieben von Dateien in diese Ordner.

## Technologie-Stack {#technology-stack}

- **Sprache**: Kotlin
- **Architektur**: Clean Architecture, MVVM
- **UI**: Android View System (XML), Material Design 3
- **Asynchronität**: Kotlin Coroutines & Flow
- **DI**: Hilt (Dagger)
- **Datenbank**: Room 2.7.0
- **Navigation**: AndroidX Navigation Component
- **Medien**: ExoPlayer (Media3 1.2.1)
- **Bildladen**: Glide 5.0.9 mit angepasstem NetworkFileModelLoader
- **Netzwerkprotokolle**:
  - SMB: SMBJ 0.12.1 mit BouncyCastle (transitiv)
  - SFTP: JSch 0.2.26 (com.github.mwiede-Fork, Ed25519 integriert)
  - FTP: Apache Commons Net 3.10.0
- **Cloud**: Google Drive API, OneDrive (MSAL), Dropbox API mit OAuth 2.0
- **OCR & Übersetzung**:
  - Tesseract4Android (Tesseract 5.3.x) - Texterkennung für lateinische und kyrillische Schrift
  - Google ML Kit (Übersetzung, Spracherkennung) - Übersetzung des erkannten Textes
- **Suche & Songtexte**: api.lyrics.ovh (JSON-API)

## Build-Version

Versionsformat: `Y.YM.MDDH.Hmm` (z. B. `2.60.1102.207` für 10.01.2026, 20:07 Uhr)

Siehe [dev/CHANGELOG.md](../dev/CHANGELOG.md) für ausführliche Release-Hinweise.

---

## Mitwirken 🤝

Pull Requests sind willkommen. Für größere Änderungen eröffne bitte zuerst ein Issue, um zu besprechen, was du ändern möchtest.

## Kontakt 📧

- **Entwickler**: <sza@ukr.net>
- **Website**: [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues**: [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## Lizenz 📄

Rechtliche Informationen zum Projekt:

- [Nutzungsbedingungen](TERMS_OF_SERVICE.md)
- [Datenschutzerklärung](PRIVACY_POLICY.md)
- [Open-Source-Komponenten](OPEN_SOURCE.md)

</div>
