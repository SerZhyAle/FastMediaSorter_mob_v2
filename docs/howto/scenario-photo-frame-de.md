---
layout: default
title: "Digitaler Bilderrahmen auf dem Tablet - FastMediaSorter v2"
permalink: /docs/howto/scenario-photo-frame-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 🖼️ Digitaler Bilderrahmen auf dem Tablet

> **Niveau:** Anfänger &bull; **Zeit:** ~15 Minuten &bull; **Edition:** Standard, Photos, Legacy, VR, noLegal (für NAS-/Cloud-Fotos) oder beliebige Edition (für lokale Fotos)

{% include lang-switcher.html doc="scenario-photo-frame" dir="/docs/howto/" current="de" %}

Verwandeln Sie jedes Android-Tablet in einen wunderschönen, dauerhaft aktiven digitalen Bilderrahmen - der Ihre Erinnerungen von einem Heim-NAS oder der Cloud streamt, mit optionaler Hintergrundmusik. Kein lokaler Speicherplatz wird verbraucht.

> **Die Idee in einem Satz:** Stellen Sie ein altes Tablet auf, schließen Sie es an, starten Sie eine Diashow - sie zeigt automatisch, für immer, alle paar Sekunden wechselnd, Ihre Fotos. Wie ein echter digitaler Bilderrahmen aus dem Laden, nur angetrieben von Ihrer eigenen Fotosammlung aus jeder Quelle.

---

## Was Sie benötigen

- Ein Android-Tablet (beliebige Größe - ein altes funktioniert hervorragend!)
- Einen Ständer oder eine Halterung, um das Tablet aufrecht zu halten
- Ein **USB-Ladegerät**, um es dauerhaft angeschlossen zu halten - das Tablet läuft den ganzen Tag, der Akku reicht dafür nicht aus
- Ihre Fotos an einem der folgenden Orte: **lokaler Speicher**, **Heim-PC/NAS über SMB** oder **Google Drive / Dropbox**
- (Optional) Eine Musikquelle für Hintergrundaudio

---

## Schritt 1 - Ihre Fotoquelle hinzufügen

Wählen Sie, wo sich Ihre Fotos befinden:

**Option A - Lokale Fotos (auf dem Tablet selbst):**
1. Tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Lokaler Ordner** → navigieren Sie zu Ihrem Fotoordner → **Auswählen**

**Option B - Heim-NAS / Windows-PC (SMB):**
1. Tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Netzwerkordner (SMB)**
2. Tippen Sie auf **„Netzwerk durchsuchen“** → wählen Sie Ihren PC/NAS aus der Liste
3. Füllen Sie Freigabename + Benutzername + Passwort aus
4. Tippen Sie auf **Verbindung testen** → **Speichern**

> Vollständige SMB-Einrichtung: [Mit NAS verbinden (SMB)](scenario-smb-setup-de.md). Das dauert einmalig etwa 5 Minuten und funktioniert dann für immer.

**Option C - Google Drive / Dropbox:**
1. Tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Cloud-Speicher** → wählen Sie den Anbieter
2. Tippen Sie auf **Anmelden** → schließen Sie die Authentifizierung im Browser ab
3. Wählen Sie den Ordner mit Ihren Fotos aus → **Fertig**

![FastMediaSorter Hauptbildschirm - Fotoressourcenkarten nach dem Hinzufügen eines Fotoordners sichtbar](screenshots/screenshot-pf-step1.png)

---

## Schritt 2 - Den Ordner für die Diashow konfigurieren

Halten Sie Ihren Fotoordner auf dem Hauptbildschirm gedrückt → tippen Sie auf **Bearbeiten (Stiftsymbol)**.

Setzen Sie diese Optionen:

| Einstellung | Empfohlener Wert | Warum |
|---------|------------------|-----|
| **Diashow-Intervall** | 5-10 Sekunden | 5 s = lebhaftes Familienalbum-Gefühl; 10 s = ruhig, gut für Kunstfotos oder große Gruppen, bei denen Sie Zeit brauchen, um alle zu erkennen |
| **Unterordner einbeziehen** | EIN | Zeigt Fotos aus allen Unterordnern - großartig, wenn Sie nach Jahr/Album organisieren |
| **Sortiermodus** | Aufnahmedatum (neueste zuerst) oder Zufällig | Zufällig = mehr tägliche Abwechslung; Datum = neueste Fotos erscheinen zuerst |
| **Unterstützte Typen** | Nur Bilder | Entfernen Sie Video und Audio - sonst spielen auch Videodateien ab und unterbrechen den Diashow-Fluss |

Tippen Sie auf **Speichern**.

![Ressource bearbeiten - Einstellungen für Diashow-Intervall und Unterordner einbeziehen](screenshots/screenshot-pf-step2.png)

---

## Schritt 3 - (Optional) Hintergrundmusik hinzufügen

Möchten Sie sanfte Musik hören, während Sie Fotos ansehen? So geht's (benötigt eine Edition mit Audio - die Photos-Edition hat keins):

1. Fügen Sie zuerst eine Musikquelle hinzu: Tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → Lokaler Ordner → navigieren Sie zu Ihrem Musikordner
2. Gehen Sie zu **Einstellungen → Reiter Medien → Audiowiedergabe, Cover und Visualisierungen**
3. Aktivieren Sie **„Zufällige Fotos während der Audiowiedergabe anzeigen“**

Gehen Sie dann zu **Einstellungen → Reiter Medien → Bilder, GIFs und Diashow**:
4. Aktivieren Sie **„Musik während der Diashow abspielen“**
5. Tippen Sie auf **„Musikquelle auswählen“** → wählen Sie Ihre Musikressource

> **Tipp:** Wenn die Musik stottert, während die Fotos von einem NAS kommen, verwenden Sie einen lokalen Musikordner für Audio und lassen Sie nur die Fotos vom Netzwerk streamen - so können Sie Quellen frei mischen.


---

## Schritt 4 - Die Diashow starten

1. Tippen Sie auf Ihren **Fotoordner** auf dem Hauptbildschirm, um ihn zu öffnen
2. Tippen Sie auf **ein beliebiges Foto**, um den Vollbildbetrachter zu öffnen
3. Tippen Sie in der oberen Symbolleiste auf **„Diashow“ <img src="../icons/doc/ic_slideshow.png" alt="" width="18" height="18" style="vertical-align:text-bottom">**

Das war's - die Diashow läuft. Die Fotos wechseln automatisch im von Ihnen eingestellten Intervall.

> **Alternativer Schnellstart:** Tippen Sie auf die **untere rechte Zone** des Fotobildschirms (der Bildschirm ist in ein 3×3-Raster unsichtbarer Tippzonen unterteilt; unten rechts = Zone 9 = WIEDERGABE).


---

## Schritt 5 - Den Bildschirm eingeschaltet halten

**Dieser Schritt ist entscheidend.** Android spart Akku, indem es den Bildschirm nach ein paar Minuten ausschaltet - das würde den Bilderrahmen ruinieren. Sie müssen dies deaktivieren.

**Option A - Einstellung in der App (empfohlen):**
Gehen Sie zu **Einstellungen → Verwaltung → Ruhezustand verhindern** und schalten Sie es ein.

Dies weist Android an, den Bildschirm eingeschaltet zu lassen, solange die App im Vordergrund läuft. Sobald Sie die App wechseln oder die Diashow stoppt, gilt wieder das normale Bildschirm-Zeitlimit.

![Einstellungen, Reiter Verwaltung - Schalter „Ruhezustand verhindern“ aktiviert](screenshots/screenshot-pf-step5.png)

**Option B - Android-Systemeinstellung:**
Android-Einstellungen → Display → Bildschirm-Zeitlimit → auf **„Nie“** (oder Maximum) einstellen.

> **Außerdem:** Halten Sie das Tablet stets **an USB-Strom angeschlossen**. Ein Tablet, das den ganzen Tag eine Diashow abspielt, entlädt seinen Akku bis zum Abend. Verwenden Sie einfach das Originalladegerät und lassen Sie es angeschlossen.

---

## Schritt 6 - (Optional) Ein Startbildschirm-Widget hinzufügen

Dieser Schritt dient der Bequemlichkeit: Möchten Sie den Bilderrahmen sofort starten, wenn Sie das Tablet in die Hand nehmen - ohne die App zu öffnen und zu navigieren?

1. Halten Sie Ihren Startbildschirm gedrückt → tippen Sie auf **Widgets**
2. Suchen Sie **FastMediaSorter** in der Widget-Liste
3. Ziehen Sie das Widget **„Ressourcen-Verknüpfung“** auf Ihren Startbildschirm
4. Wählen Sie bei Aufforderung Ihre Fotoressource aus
5. Tippen Sie jederzeit auf das Widget → die Diashow startet sofort

![Android-Startbildschirm mit platzierten FastMediaSorter-Ressourcen-Verknüpfungs-Widgets](screenshots/screenshot-pf-step6.png)

---

## Fertig! Ihr Bilderrahmen läuft

**Steuerung während die Diashow läuft:**
- **Bildschirm antippen** → pausieren / Steuerung anzeigen
- **Nach links/rechts wischen** → manuell zum nächsten/vorherigen Foto springen
- **Untere rechte Zone antippen** → Diashow stoppen und zur Dateiliste zurückkehren

---

## Tipps

> **NAS-Fotos werden nach dem Hinzufügen neuer nicht aktualisiert?** Die App speichert die Dateiliste zwischen für mehr Geschwindigkeit. Zum Aktualisieren: Gehen Sie zurück zum Ordner → tippen Sie auf die Schaltfläche **Aktualisieren <img src="../icons/doc/ic_refresh.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** in der Symbolleiste. Neue Fotos erscheinen sofort.

> **Fotos sehen vergrößert oder abgeschnitten aus?** Öffnen Sie Einstellungen → Medien → Bilder → **„Bilder zum Füllen des Bildschirms zuschneiden“** und probieren Sie beide Positionen aus: AUS zeigt das vollständige Foto, EIN füllt den Bildschirm randlos aus (leichtes Zuschneiden an den Seiten).

> **Ein Handy im Hochformat als Rahmen verwendet?** Aktivieren Sie „Bilder zum Füllen des Bildschirms zuschneiden“, um schwarze Balken bei Querformat-Fotos zu vermeiden.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Bildschirm wird nach ein paar Minuten dunkel | Aktivieren Sie „Ruhezustand verhindern“ in Einstellungen → Verwaltung (Schritt 5) **und** schließen Sie das USB-Ladegerät an |
| Fotos werden nicht angezeigt | Öffnen Sie die Ordnereinstellungen (Schritt 2) und stellen Sie sicher, dass **Bilder** unter **Unterstützte Typen** angehakt ist |
| Musik spielt nicht ab | Prüfen Sie, ob der Musikordner mindestens eine Audiodatei enthält; stellen Sie sicher, dass **Musik während der Diashow abspielen** in Einstellungen → Medien → Bilder, GIFs und Diashow aktiviert ist |
| Diashow pausiert bei Videodateien | Erwartet - Videos spielen ab, dann setzt die Diashow fort. Stellen Sie „Unterstützte Typen → Nur Bilder“ in den Ordnereinstellungen ein (Schritt 2), um dies zu verhindern |
| SMB-Fotos laden langsam | Bearbeiten Sie den Ordner → deaktivieren Sie „Vorschaubilder laden“, um die Netzwerklast zu reduzieren. Oder verringern Sie das Diashow-Intervall, um mehr Ladezeit zu geben |
| Fotos wiederholen sich zu schnell | Erhöhen Sie das Diashow-Intervall in den Ordnereinstellungen (Schritt 2) |

</div>
