---
layout: default
title: "Heimkino & VR-Streaming - FastMediaSorter v2"
permalink: /docs/howto/scenario-home-cinema-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 🍿 Heimkino & VR-Streaming

> **Niveau:** Anfänger &bull; **Zeit:** ~15 Minuten &bull; **Edition:** Standard, Legacy, VR, noLegal (Lite hat keine Netzwerkquellen, Photos hat kein Video)

{% include lang-switcher.html doc="scenario-home-cinema" dir="/docs/howto/" current="de" %}

Sehen Sie Ihre Seriensammlung direkt von Ihrem heimischen PC aus - auf Ihrem Handy, Tablet oder einem Android-basierten VR-Headset (Meta Quest, Pico). Kein Kopieren von Dateien. Keine USB-Kabel. Einfach auf Play drücken.

> **Wie funktioniert das?** Ihr Handy und Ihr PC befinden sich im selben Heim-Wi-Fi. Die App verbindet sich mit dem freigegebenen Ordner Ihres PCs und streamt das Video direkt - genau wie Netflix von seinen Servern streamt, nur über Ihr eigenes Heimnetzwerk. Die Videodatei wird nie auf Ihr Handy heruntergeladen; sie spielt live ab.

---

## Was Sie benötigen

- Handy / Tablet / VR-Headset im gleichen **Heim-Wi-Fi-Netzwerk** wie Ihr PC
- Videos auf Ihrem **PC oder NAS** (Ihre Router-Box mit Speicher)
- Installiertes FastMediaSorter

---

## Schritt 1 - Ihren Videoordner auf dem PC freigeben

Machen Sie zunächst den Videoordner über Ihr Heimnetzwerk erreichbar.

Unter **Windows:**
1. Öffnen Sie den **Explorer**, navigieren Sie zu Ihrem Videoordner (z. B. `D:\Series`)
2. **Rechtsklick** auf den Ordner → **Eigenschaften** → Reiter **Freigabe** → klicken Sie auf **Freigeben..**
3. Wählen Sie im Dropdown **Jeder** (oder Ihren Benutzernamen) → klicken Sie auf **Hinzufügen** → klicken Sie auf **Freigeben**
4. Notieren Sie sich die IP-Adresse Ihres PCs - Sie benötigen sie in Schritt 2

> **So finden Sie die IP-Adresse Ihres PCs:** Drücken Sie **Win + R**, geben Sie `cmd` ein, drücken Sie die Eingabetaste. Geben Sie `ipconfig` ein und drücken Sie die Eingabetaste. Suchen Sie die Zeile **IPv4-Adresse** unter Ihrem Wi-Fi-Adapter. Beispiel: `192.168.1.100`.

---

## Schritt 2 - Den Videoordner in FastMediaSorter hinzufügen

1. Öffnen Sie die App → tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **„Netzwerkordner (SMB)“**
2. Tippen Sie auf **„Netzwerk durchsuchen“** - die App durchsucht Ihr Heimnetzwerk nach verfügbaren PCs
3. Wenn Ihr PC in der Liste erscheint, tippen Sie darauf - die Adresse wird automatisch eingetragen
4. Geben Sie den Freigabenamen (den Namen des Videoordners), Benutzernamen und Windows-Passwort ein
5. Tippen Sie auf **Verbindung testen** → **Speichern**

> **PC über die Suche nicht gefunden?** Geben Sie die Adresse manuell ein: `\\192.168.1.100\Series` (ersetzen Sie sie durch Ihre IP und Ihren Ordnernamen). Siehe die vollständige [SMB-Einrichtungsanleitung](scenario-smb-setup-de.md) für alle Verbindungsszenarien.


---

## Schritt 3 - Den Videoordner öffnen

Tippen Sie auf die soeben hinzugefügte Ressource auf dem Hauptbildschirm.

Ihre Serienordner und Videodateien erscheinen als Raster mit Vorschaubildern - genau wie beim lokalen Durchsuchen.

![SMB-Videoordner - Episodendateien (MKV) nach Dateinamen aufgelistet](screenshots/screenshot-hc-step3.png)

---

## Schritt 4 - Automatische nächste Episode einrichten

Damit die nächste Episode automatisch startet, wenn eine zu Ende ist - ohne die nächste manuell auswählen zu müssen:

1. Gehen Sie zurück zum Hauptbildschirm → **halten Sie** Ihre Videoressource **gedrückt** → tippen Sie auf **Bearbeiten**
2. Stellen Sie **Unterstützte Typen** → **Nur Video** ein (blendet Nicht-Video-Dateien aus)
3. Stellen Sie **Sortiermodus** → **Name (A→Z)** ein - dies stellt sicher, dass Episoden in der richtigen Reihenfolge abgespielt werden (Episode 1, 2, 3..)
4. Tippen Sie auf **Speichern**

Starten Sie dann die Diashow im Player (der Befehl **Diashow**) und aktivieren Sie **Einstellungen → Player → Video/Audio in der Diashow bis zum Ende abspielen** - jede Episode spielt dann bis zum Ende, bevor die nächste beginnt.

> **Warum nach Namen sortieren?** Episodendateien sind meist mit `S01E01`, `S01E02` usw. benannt. Die Sortierung nach Namen bringt sie automatisch in die richtige Episodenreihenfolge.

---

## Schritt 5 - Mit dem Ansehen beginnen

1. Öffnen Sie den Ordner, navigieren Sie in den Serien-Unterordner
2. Tippen Sie auf **Episode 1** - der Videoplayer öffnet sich sofort und beginnt zu streamen
3. Das Video läuft über Wi-Fi - kein Warten auf Downloads

![Videoplayer im Vollbild - Episode läuft mit Fortschrittsleiste](screenshots/screenshot-hc-step5.png)

---

## Schritt 6 - Steuerung während des Ansehens

**Touch-Gesten während der Wiedergabe:**
- **Nach links wischen** → zur nächsten Episode springen
- **Nach rechts wischen** → zur vorherigen Episode zurückgehen
- **Bildschirm antippen** → Steuerung ein-/ausblenden
- **Zusammenziehen (Pinch)** → hinein- oder herauszoomen (nützlich für Breitbild-Filme auf einem Handy im Hochformat)
- **Doppeltipp linker/rechter Rand** → 10 Sekunden zurück-/vorspulen

Wenn **„Automatisch weiter“** aktiviert ist, startet die nächste Episode automatisch, wenn die aktuelle endet - genau wie bei Netflix.

---

## Schritt 7 - Für VR-Headsets (Meta Quest, Pico)

> **Dieser Abschnitt ist für Besitzer eines VR-Headsets** (wie Meta Quest 2/3 oder Pico 4). Falls Sie keins haben, überspringen Sie diesen Schritt.

Android-basierte VR-Headsets können FastMediaSorter ausführen. Installieren Sie es per Sideloading:
1. Laden Sie die APK von der [Downloads-Seite](../DOWNLOADS.md) herunter
2. Aktivieren Sie auf Ihrem Headset in den Entwickleroptionen **„Installation aus unbekannten Quellen“**
3. Installieren Sie die APK über SideQuest oder direkt per ADB

Nach der Installation funktioniert der Videoplayer genau gleich:
- Das Video füllt den **virtuellen Flachbildschirm** im Headset
- Verwenden Sie den **Controller-Trigger**, um Schaltflächen anzutippen
- Verwenden Sie den **Thumbstick**, um zwischen Episoden zu wischen (falls Ihr Headset Medientasten zuordnet)
- Für gewöhnliche 2D-Filme und -Serien funktioniert es sofort, ohne zusätzliche Einrichtung

> **Für das VR-Kinoerlebnis:** Sie können eine dedizierte VR-Kino-App als Launcher verwenden und dann „Mit FastMediaSorter öffnen“ wählen, um die Dateiverwaltung zu übernehmen. FastMediaSorter kümmert sich um das Durchsuchen der Dateien; die VR-Kino-App übernimmt die immersive 360°-Anzeige.

---

## Fertig! Was Sie als Nächstes ausprobieren können

- Fügen Sie eine **Google Drive**- oder **Dropbox**-Ressource für in der Cloud gespeicherte Filme hinzu - funktioniert genauso
- Verwenden Sie **Favoriten** (tippen Sie beim Ansehen auf die Sternschaltfläche <img src="../icons/doc/ic_star_filled.png" alt="" width="18" height="18" style="vertical-align:text-bottom">), um Ihre „gerade angesehene“ Serie zu markieren - springen Sie jederzeit dorthin zurück
- **Untertitel:** Wenn Ihr Videoordner passende `.srt`-Untertiteldateien neben den Videodateien enthält, tippen Sie auf die **CC-/Untertitel-Schaltfläche** in der Player-Symbolleiste, um sie zu aktivieren
- **Internetradio oder Live-Streams:** Wenn Sie auch Internetradiosender oder RTSP-/HLS-Quellen hinzufügen möchten, siehe die Anleitung [Internetradio & Streams](scenario-internet-radio-de.md) - kein NAS oder PC erforderlich, nur eine Netzwerkverbindung.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Video stottert oder puffert | Führen Sie einen **Geschwindigkeitstest** aus: halten Sie die Ressource gedrückt → Bearbeiten → Geschwindigkeitstest. Liegt die Geschwindigkeit unter 5 Mbit/s, wechseln Sie Ihr Handy zum **5-GHz-Wi-Fi-Band** (schneller, aber kürzere Reichweite) |
| Video spielt nicht ab (Formatfehler) | Tippen Sie im Player auf **Optionen <img src="../icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → wechseln Sie den **Decoder** von Hardware zu Software (langsamer, aber kompatibler) |
| Episoden spielen in falscher Reihenfolge | Stellen Sie sicher, dass der Sortiermodus in den Ordner-Bearbeitungseinstellungen auf **Name (A→Z)** eingestellt ist |
| Automatisch weiter startet nicht | Stellen Sie sicher, dass die Diashow läuft und Einstellungen → Player → Video/Audio in der Diashow bis zum Ende abspielen aktiviert ist |
| VR-Headset kann die APK nicht installieren | Öffnen Sie die Entwickleroptionen des Headsets und aktivieren Sie „Installationen aus unbekannten Quellen zulassen“ |

</div>
