---
layout: default
title: "Internetradio & Streams - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 📻 Internetradio & Streams

> **Niveau:** Anfänger - **Zeit:** ~10 Minuten - **Edition:** Standard, Legacy, VR, noLegal (Streams fehlen in Lite und Photos)

{% include lang-switcher.html doc="scenario-internet-radio" dir="/docs/howto/" current="de" %}

FastMediaSorter enthält einen eigenen Streams-Bildschirm für Internet-Audio- und Videoquellen. Fügen Sie eine beliebige Internetradio-URL hinzu, importieren Sie eine .m3u-Playlist oder durchsuchen Sie einen kuratierten Senderkatalog - keine separate Radio-App nötig. Funktioniert hervorragend auf Android-Autoradios, Audioplayern, Handys und Tablets.

> **Ersetzt:** TuneIn, Shoutcast-App, Online Radio, RadioDroid, VLC-Netzwerk-Streams, IPTV-Player.

---

## Was Sie benötigen

- Ein Android-Gerät mit Netzwerkverbindung (mobile Daten oder Wi-Fi)
- FastMediaSorter Standard, Legacy, VR oder noLegal (der Streams-Bildschirm fehlt in Lite und Photos)
- Eine Stream-URL, eine .m3u-Playlist-Datei oder -URL, oder den integrierten kuratierten Katalog

---

## Schritt 1 - Den Streams-Bildschirm öffnen

Drei Wege führen dorthin:
- Dropdown-Menü des Hauptbildschirms -> **Streams**
- **Einstellungen -> Medien -> Streams** -> tippen Sie auf die Streams-Schnellzugriffsschaltfläche
- Willkommens-Einführung -> Zeile Streams (nur beim ersten Start)

> **Sehen Sie Streams nicht im Menü?** Gehen Sie zu Einstellungen -> Medien -> Streams und stellen Sie sicher, dass „Streams aktivieren“ EINGESCHALTET ist. Es ist auf den meisten Geräten standardmäßig EIN.

---

## Schritt 2 - Einen Sender oder Stream hinzufügen

**Option A - Eine einzelne URL manuell hinzufügen:**
1. Tippen Sie auf **Hinzufügen (+)** in der Symbolleiste des Streams-Bildschirms
2. Fügen Sie die Stream-URL ein (http/https-Radio, .m3u8 HLS, rtsp://..)
3. Geben Sie ihr einen Namen und tippen Sie auf **Speichern**

**Option B - Eine .m3u-Playlist importieren:**
1. Tippen Sie auf **Importieren** -> **Von URL**
2. Fügen Sie die .m3u-Playlist-URL ein und bestätigen Sie
3. Alle Sender aus der Playlist werden Ihrer Liste hinzugefügt

**Option C - Den kuratierten Katalog durchsuchen:**
1. Tippen Sie auf **Katalog importieren** (oder laden Sie ihn vom Erweiterungen-Bildschirm herunter)
2. Durchsuchen Sie ihn oder suchen Sie nach Name, Thema oder Sprache
3. Tippen Sie auf Sender, um sie Ihrer Liste hinzuzufügen

---

## Schritt 3 - Einen Sender abspielen

- **Audio-Stream (Radio):** Tippen Sie auf die Zeile - die Wiedergabe startet direkt in der Liste. Eine feste Mini-Steuerung erscheint unten und zeigt den Sendernamen und ICY-Now-Playing-Titelinformationen. Die Liste bleibt vollständig interaktiv.
- **Video- oder RTSP-Stream:** Tippen Sie auf die Zeile - öffnet sich im Vollbildplayer. Drücken Sie Zurück, um zur Liste zurückzukehren; Scrollposition und zuletzt ausgewählter Sender bleiben erhalten.

---

## Schritt 4 - Radio im Hintergrund weiterspielen lassen

Damit Audio weiterspielt, wenn Sie Apps wechseln oder den Bildschirm sperren:

1. Gehen Sie zu **Einstellungen -> Medien -> Player**
2. Suchen Sie die Gruppe **Hintergrund-Audiowiedergabe**
3. Aktivieren Sie **Hintergrund-Audiowiedergabe**

> **Den Streams-Bildschirm verlassen, während ein Sender läuft:** Die App bietet dieselbe Stopp-/Weiterspielen-Auswahl wie der Hauptplayer. Ist die Hintergrundwiedergabe AUS, stoppt der Stream, wenn Sie den Bildschirm minimieren.

---

## Schritt 5 - Filtern und Organisieren

- **Favoriten nach oben pinnen:** Halten Sie eine Senderzeile gedrückt -> Pinnen. Gepinnte Sender erscheinen unabhängig von der Sortierreihenfolge über den übrigen.
- **Nach Kategorie oder Sprache filtern:** Tippen Sie auf die Filter-Schaltfläche (ein Punkt erscheint, wenn ein Filter aktiv ist). Die Sprachauswahl zeigt Flaggen. Verwenden Sie den UND/ODER-Schalter, um alle oder eine der ausgewählten Filterbedingungen zu erfüllen.
- **Sortieren:** Tippen Sie auf die Sortier-Schaltfläche, um nach Name, Thema, Sprache oder zuletzt gespielt zu ordnen.
- **Suchen:** Geben Sie in der Suchleiste Text ein, um über alle Sender hinweg nach Namen zu filtern.

---

## Schritt 6 - Was tun, wenn ein Sender tot ist

Ist ein Stream nicht verfügbar oder umgeleitet, erscheint ein Dialog mit drei Optionen:
- **Erneut versuchen** - versucht den Stream erneut
- **Entfernen** - löscht ihn aus Ihrer Liste
- **Abbrechen** - schließt den Dialog und behält den Eintrag

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|-------------|
| Stream spielt nicht ab | Prüfen Sie, ob die URL korrekt ist und der Sender online ist. Versuchen Sie „Erneut versuchen“ im Nichtverfügbarkeits-Dialog |
| Audio stoppt beim App-Wechsel | Aktivieren Sie die Hintergrund-Audiowiedergabe in Einstellungen -> Medien -> Player |
| Kein Streams-Eintrag im Menü | Der Streams-Bildschirm fehlt in den Editionen Lite und Photos. Verwenden Sie Standard, Legacy, VR oder noLegal |
| Katalog-Import hängt | Der Katalog-Host ist möglicherweise langsam oder offline. Der Import läuft automatisch ab und zeigt einen Fehler an - prüfen Sie Ihre Verbindung und versuchen Sie es erneut |
| Keine Flaggen im Sprachfilter angezeigt | Flaggen werden basierend auf dem Sprach-Tag im Senderkatalog angezeigt. Manuell hinzugefügte Sender ohne Sprach-Tag sind immer unter jedem Sprachfilter sichtbar |

</div>
