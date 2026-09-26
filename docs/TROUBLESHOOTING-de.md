---
layout: default
title: "🔧 Troubleshooting Guide"
permalink: /docs/TROUBLESHOOTING-de.html
---
<div lang="de" dir="ltr" markdown="1">

{% include lang-switcher.html doc="TROUBLESHOOTING" dir="/docs/" current="de" %}

# 🔧 Leitfaden zur Problembehebung

Aktueller Leitfaden zur Problembehebung für FastMediaSorter v2. Nutze das maßgebliche Varianten-Raster in [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), wenn das Problem vom gewählten Build-Pfad abhängt (Standard, Lite, Photos, Legacy oder XR / noLegal).

---

## Verbindungsprobleme

### ❌ "Keine Verbindung zum SMB-Server möglich"

**Mögliche Ursachen:**
1. **Falsches Netzwerk** - Das Handy muss im selben WLAN wie das NAS sein
2. **Falsches Adressformat** - Probiere beide Formate:
   - `\\192.168.1.100\share`
   - `smb://192.168.1.100/share`
3. **Firewall blockiert** - Prüfe die Firewall-Einstellungen des NAS
4. **SMB-Versionskonflikt** - Manche NAS-Geräte benötigen weiterhin SMB v2/v3-Kompatibilität; aktualisiere den Server, wenn er nur veraltete SMB-Einstellungen anbietet

**Lösung:**
- Verbindung zuerst vom PC aus testen
- NAS-Protokolle auf Verbindungsversuche prüfen
- IP-Adresse statt Hostname probieren
- Benutzername/Passwort überprüfen

---

### ❌ "SFTP-Verbindungs-Timeout"

**Mögliche Ursachen:**
1. Falscher Port (Standard: 22)
2. SSH-Server läuft nicht
3. Firewall blockiert

**Lösung:**
```
1. Test with SSH client on PC first:
   ssh username@192.168.1.100
2. Check if SSH service is running
3. Verify port in Settings
```

---

### ❌ "Google Drive-Anmeldung fehlgeschlagen"

**Lösung:**
1. App-Daten löschen: Einstellungen → Apps → FastMediaSorter → Daten löschen
2. App neu installieren
3. Google-Kontoeinstellungen → Sicherheit → Drittanbieter-Apps prüfen

---

### ❌ "OneDrive-Anmeldung fehlgeschlagen"

**Lösung:**
1. Microsoft-Kontostatus prüfen
2. App-Daten löschen: Einstellungen → Apps → FastMediaSorter → Daten löschen
3. Microsoft-Kontoeinstellungen → Datenschutz → Apps und Dienste prüfen

---

### ❌ "Dropbox-Anmeldung fehlgeschlagen"

**Lösung:**
1. Dropbox-Kontostatus prüfen
2. App-Daten löschen: Einstellungen → Apps → FastMediaSorter → Daten löschen
3. Dropbox-Kontoeinstellungen → Sicherheit → Verbundene Apps prüfen

---

## Leistungsprobleme

### ❌ "App ist langsam / ruckelt"

**Bei großen Ordnern (5000+ Dateien):**
1. **Ordner bearbeiten** (pro Ressource) → **"Vorschaubilder deaktivieren"** aktivieren
2. **Filter** nutzen, um sichtbare Dateien zu reduzieren
3. Andere Apps schließen, um RAM freizugeben

**Bei Netzwerkordnern:**
1. WLAN-Signalstärke prüfen
2. Vorschaubild-Cache-Größe verringern
3. **"Unterordner scannen"** = AUS aktivieren, falls nicht benötigt

---

### ❌ "Vorschaubilder laden nicht"

**Lokale Dateien:**
- Speicherberechtigungen prüfen
- Vorschaubild-Cache leeren
- App neu starten

**Netzwerkdateien:**
- Langsamer scrollen (Vorschaubilder laden bei Bedarf)
- Netzwerkgeschwindigkeit prüfen
- Cache-Größe in den Einstellungen erhöhen

---

## Fehler bei Dateioperationen

### ❌ "Kopieren fehlgeschlagen: Zugriff verweigert"

**Lokale Dateien:**
- Speicherberechtigungen erteilen: Einstellungen → Apps → Berechtigungen
- Prüfen, ob der Ordner schreibgeschützt ist
- Versuchen, an einen anderen Ort zu verschieben

**Netzwerkdateien:**
- Prüfen, ob der Benutzername Schreibrechte hat
- Freigabeeinstellungen auf dem NAS überprüfen

---

### ❌ "Datei kann nicht gelöscht werden"

**Mögliche Ursachen:**
1. Die Datei ist in einer anderen App geöffnet
2. Keine Schreibberechtigung
3. Die Datei ist systemgeschützt

**Lösung:**
- Andere Apps schließen
- Ordnerberechtigungen prüfen
- Bei Netzwerk: prüfen, ob der Benutzer Löschrechte hat

---

### ❌ "Verschiebe-Operation fehlgeschlagen"

**Protokollübergreifendes Verschieben** (z. B. Lokal → SMB):
- Das ist eigentlich **Kopieren + Löschen**
- Erfordert freien Speicherplatz am Ziel
- Kann bei großen Dateien länger dauern

**Lösung:**
- Verfügbaren Speicherplatz prüfen
- Aus Sicherheitsgründen Kopieren statt Verschieben nutzen
- Auf den vollständigen Abschluss der Operation warten

---

## App-Abstürze

### ❌ "App stürzt beim Öffnen des Players ab"

**Häufige Ursachen:**
1. Beschädigte Videodatei
2. Nicht unterstützter Codec
3. Datei zu groß (>4 GB)

**Lösung:**
- Datei zur Überprüfung in einer anderen App abspielen
- Dateiformat prüfen (unterstützt: MP4, MKV, MOV)
- App-Cache leeren

---

### ❌ "Mediendatei spielt nicht oder ohne Ton"

**Problem:** Das Video lädt, zeigt aber einen schwarzen Bildschirm oder spielt ohne Ton.

**Lösung:**
1. Tippe auf die Schaltfläche **ⓘ (Info)** in der oberen Symbolleiste
2. Tippe auf **"In externem Player öffnen"**
3. Wähle einen spezialisierten Player (z. B. VLC, MX Player)

Dies nutzt die Funktion *Sekundärer Player*, um nicht unterstützte Codecs an andere Apps zu übergeben.

---

### ❌ "App stürzt beim Start ab"

**Lösung:**
1. App-Cache leeren: Einstellungen → Apps → FastMediaSorter → Cache leeren
2. Falls das Problem bestehen bleibt: App-Daten löschen (⚠️ Einstellungen gehen verloren)
3. App als letzten Ausweg neu installieren

---

## Probleme mit UI / Anzeige

### ❌ "Berührungszonen funktionieren nicht"

**Prüfen, ob aktiviert:**
Einstellungen → Player → **"Hinweis zu Berührungszonen beim ersten Start anzeigen"** = AN

**Sichtbar machen:**
Einstellungen → Player → **"Berührungszonen-Overlay immer anzeigen"** = AN

---

### ❌ "Schaltflächen im Befehlsfeld zu klein"

**Lösung:**
Einstellungen → Player → **"Kompakte Player-Schaltflächen"** = AUS

Das verdoppelt die Größe aller Schaltflächen und Abstände.

---

### ❌ "Dunkles Theme funktioniert nicht"

Die App folgt dem **Systemthema**:
- Android-Einstellungen → Anzeige → Dunkles Theme = AN

---

## Datenprobleme

### ❌ "Favoriten sind verschwunden"

Favoriten werden **lokal** gespeichert:
- App-Daten gelöscht? → Favoriten verloren
- Neues Gerät? → Muss erneut markiert werden

**Vorbeugung:**
- Nutze **Einstellungen → Allgemein → Sicherungen, Wiederherstellung und Einstellungsexport**
- Favoriten sind lokal auf dem Gerät; wenn du zu einem neuen Handy wechselst, markiere sie erneut oder nutze den in deinem Build verfügbaren App-Sicherungs-/Wiederherstellungsablauf

---

### ❌ "Der Papierkorb-Ordner wächst immer weiter"

Gelöschte Dateien landen im Ordner `.trash/` und bleiben dort, bis sie manuell geleert werden.

**Lösung:**
1. Einstellungen → Verwaltung → **Dateilöschung und Papierkorb**
2. Oder `.trash/`-Ordner manuell löschen

---

## Immer noch Probleme?

### Protokolle prüfen
1. Einstellungen → Verwaltung → **"Detaillierte Fehler anzeigen"** = AN
2. Das Problem reproduzieren
3. Logcat-Ausgabe prüfen

### Einen Fehler melden
Füge diese Informationen bei:
- Android-Version
- Gerätemodell
- Schritte zur Reproduktion
- Fehlermeldung (Screenshot)

**Einreichen:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

---
    
## Probleme mit Übersetzung & EPUB
    
### ❌ "Übersetzung funktioniert nicht oder hängt"

**Mögliche Ursachen:**
1. **Fehlende Modelle:** Die App konnte die OCR-Modelle nicht herunterladen.
2. **Kein Internet:** Beim ersten Start wird Internet benötigt, um Modelle herunterzuladen.
3. **Speicher voll:** Kein Platz für Modelle (~50 MB).

**Lösung:**
1. Internetverbindung prüfen.
2. Zu **Einstellungen** → **Medien** → **Sonstiges** gehen
3. "Übersetzung aktivieren" AUS- und wieder ANschalten.
4. Versuche, die **Quellsprache** auf "Auto" umzustellen.

---

### ❌ "EPUB-Buch öffnet nicht"

**Mögliche Ursachen:**
1. **DRM-Schutz:** Die App unterstützt nur DRM-freie EPUBs.
2. **Beschädigte Datei:** Die Datei könnte unvollständig sein.
3. **Sehr große Datei:** Dateien >100 MB können bei langsamem Netzwerk in ein Timeout laufen.

**Lösung:**
1. Prüfen, ob die Datei in anderen Readern öffnet.
2. Bei Netzwerk/Cloud: zuerst manuell herunterladen.
3. Sicherstellen, dass die Dateiendung genau `.epub` lautet.

---

## Probleme mit Internet-Streams

### Stream startet nicht / spielt eine Sekunde und stoppt dann

**Mögliche Ursachen:**
1. Die URL ist tot oder leitet auf ein anderes Protokoll um.
2. Der Server erfordert eine Authentifizierung (nicht unterstützt).
3. Klartext-http:// wird von einem VPN oder Firmennetzwerk blockiert.

**Lösung:**
- Tippe im Dialog "Stream nicht verfügbar" auf **Erneut versuchen**.
- Die URL in einem Browser prüfen.
- VPN vorübergehend deaktivieren, um zu testen.
- Wenn der Stream weiterleitet und weiterhin fehlschlägt, tippe auf **Entfernen** und füge die korrigierte URL erneut hinzu.

### Der Ladekreis beim Katalog-Import stoppt nicht / hängt

Die App wendet für Katalog-Downloads einen kurzen Timeout an. Wenn der Ladekreis länger als ~15 Sekunden hängt, ist der Host wahrscheinlich nicht erreichbar. Prüfe deine Internetverbindung und versuche es erneut. Der Dialog schließt sich bei Timeout automatisch - er hängt nicht unbegrenzt.

### HLS / DASH / RTSP zeigt die Meldung "nicht unterstützt"

In **Standard**, **Legacy** und **XR / noLegal** werden alle drei Protokolle unterstützt, sodass diese Meldung auf den Stream oder seinen Codec hinweist, nicht auf den Build. **Lite** und **Photos** haben überhaupt keinen Streams-Bildschirm, sodass dort von vornherein kein Stream hinzugefügt werden kann.

### Die Streams-Option ist im Menü oder in den Einstellungen nicht sichtbar

- In **Standard / Legacy / XR / noLegal**: gehe zu **Einstellungen > Medien > Streams** und stelle sicher, dass **Streams aktivieren** eingeschaltet ist. Der Dropdown-Eintrag erscheint nur, wenn Streams aktiviert ist.
- In **Photos**: Die Streams-Funktion ist in dieser Variante nicht eingebaut.
- In **Lite**: Die Streams-Funktion ist ebenfalls nicht eingebaut - es gibt weder einen Schalter noch einen Bildschirm zum Öffnen.

### ICY-Jetzt-läuft-Metadaten werden nicht angezeigt

ICY-Metadaten erfordern einen Icecast-/Shoutcast-Stream, der den Header `Icy-MetaData: 1` sendet. Reine http-mp3-Streams ohne ICY-Header zeigen keine Sender-/Titelinformationen in der unteren Mini-Steuerung an. Dies ist eine serverseitige Einschränkung.

---

## Inhaltsprobleme

### ❌ "Text- oder PDF-Dateien nicht sichtbar"

**Lösung:**
1. **Einstellungen** → **Medien** → **Dokumente** prüfen
2. Sicherstellen, dass **"Textdateien unterstützen"** und **"PDF-Dateien unterstützen"** aktiviert sind.
3. **Filter** auf dem Hauptbildschirm (Trichter-Symbol) prüfen, um sicherzustellen, dass sie ausgewählt sind.
4. Ordner **neu scannen** (zum Aktualisieren nach unten ziehen).

---

## Bekannte Einschränkungen

- ⚠️ **Keine RAW-Fotounterstützung** (CR2, NEF, ARW)
- ⚠️ **Netzwerk-Rückgängig nicht verfügbar** (Dateien werden endgültig gelöscht)
- ⚠️ **Cloud-Speicher ist in jeder Variante außer Lite eingebaut**; welche Anbieter ein bestimmter Build bietet, kann trotzdem von der Geräteplattform abhängen, und [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) ist das Raster pro Variante
- ⚠️ **Keine Mehrgeräte-Synchronisierung** (Favoriten sind lokal)

---

**Zuletzt aktualisiert:** 2026-06-05  
**Version:** Aktueller öffentlicher Dokumentationsstand

</div>
