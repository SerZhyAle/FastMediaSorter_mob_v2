---
layout: default
title: "TV-Sender auf Ihrer Smartwatch ansehen - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-tv-de.html
---
<div lang="de" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_stream.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> TV-Sender auf Ihrer Smartwatch ansehen

> **Niveau:** Anfänger &bull; **Zeit:** ~10 Minuten &bull; **Gerät:** Wear OS-Smartwatch

> **Nur Vollversion** - diese Anleitung ist in der über Google Play vertriebenen Version nicht umgesetzt. Sie gilt für die Vollversion, einen direkten APK-Download von [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-tv" dir="/docs/howto/" current="de" %}

FastMedia Wear spielt Live-TV- und Radiosender direkt an Ihrem Handgelenk ab. Die Uhr öffnet den Stream über ihr eigenes Wi-Fi, sodass Sie einen Sender, sobald er in der Liste ist, ansehen können, während das Handy in einem anderen Zimmer, in einer Tasche oder komplett ausgeschaltet ist.

> **Suchen Sie stattdessen gespeicherte Musik?** Siehe [Musik auf der Smartwatch](scenario-watch-music-de.md). Für Ihre eigenen Dateien auf einer NAS- oder PC-Freigabe siehe [Uhr mit Netzwerkfreigaben verbinden](scenario-watch-network-de.md).

---

## Was Sie benötigen

- Eine Smartwatch mit **Wear OS 2.0** oder neuer, auf der FastMedia Wear installiert ist
- Ein Wi-Fi-Netzwerk, dem die Uhr beitreten kann, oder ein gekoppeltes Handy, um die Verbindung weiterzuleiten
- Optional: FastMediaSorter auf Ihrem Android-Handy, wenn Sie eigene Sender an die Uhr senden möchten

---

## Schritt 1 - Streams öffnen

1. Öffnen Sie **FastMedia Wear** auf Ihrer Uhr.
2. Tippen Sie auf dem Startbildschirm auf **Streams**.

![FastMedia Wear Startbildschirm mit dem Bereich Streams](screenshots/screenshot-wear-tv-step1.png)

Der Startbildschirm behält dieselben sechs Bereiche an denselben Stellen, sodass Streams immer in der unteren Reihe steht, unabhängig von der gewählten Rastergröße. Darüber sitzt eine Zeile mit den zuletzt geöffneten Ressourcen - sobald Sie etwas angesehen haben, erscheint der zuletzt verlassene Sender dort für einen einzigen Fingertipp.

---

## Schritt 2 - Die Senderliste füllen

Eine frische Installation hat noch keine Sender, und der Bildschirm sagt das auch.

![Leerer Streams-Bildschirm mit der Schaltfläche Katalog aktualisieren](screenshots/screenshot-wear-tv-step2.png)

Es gibt zwei Wege, sie zu füllen, und sie funktionieren zusammen:

- **Den gemeinsamen Katalog herunterladen.** Tippen Sie auf **Katalog aktualisieren**. Die Uhr lädt die veröffentlichte Senderbank in einem Archiv herunter - viele Tausend TV- und Radiosender mit ihren Themen, Sprachen und Ländern.
- **Sender vom Handy senden.** Ein von Ihnen selbst in FastMediaSorter auf dem Handy hinzugefügter Sender kann mit **An Uhr senden** aus der Streamliste des Handys hinübergeschickt werden. Auf dem Handy gepinnte Sender werden ebenfalls in der Liste der Uhr nach oben gehoben, direkt hinter denen, die Sie auf der Uhr selbst gepinnt haben, sodass die zwei oder drei, die Sie tatsächlich ansehen, ohne Scrollen erreichbar sind. Das Entpinnen auf dem Handy entzieht den Sender wieder dieser Gruppe, und ein Sender, den der eigene Katalog der Uhr nicht führt, wird einfach übersprungen.

Vom Handy gesendete Sender überstehen eine Katalog-Aktualisierung - die Aktualisierung ersetzt die gemeinsame Bank und lässt Ihre eigenen Zeilen unangetastet.

---

## Schritt 3 - Den gewünschten Sender finden

Die drei Schaltflächen oben in der Liste bleiben angeheftet, während die Liste scrollt, sodass sie nie außer Reichweite geraten.

- **Suche** filtert die Liste während der Eingabe.
- **Filter** schränkt nach Thema und nach Sprache ein. Die Namen werden in Ihrer Oberflächensprache angezeigt statt im rohen englischen Katalogtext, am stärksten besetzte zuerst, mit der Senderanzahl in jeder Zeile, und den drei eigenen Sprachen der App ganz oben.
- **Filter** listet außerdem die kuratierten Sammlungen auf, die mit dem Katalog geliefert wurden - „Russisches TV“, „Radio der ehemaligen UdSSR“, „Afrikanisches TV“ und die übrigen, dieselben, die das Handy zeigt. Wählen Sie eine aus, um nur ihre Sender zu sehen, oder wählen Sie **Alle**, um die Einschränkung aufzuheben. Ein Sender kann zu mehreren Sammlungen gehören, sodass derselbe Sender unter mehr als einer erscheint. Führt der heruntergeladene Katalog keine Sammlungen, wird der Eintrag gar nicht angezeigt.
- **Sortieren** bietet Meistgenutzt, Name A-Z, Name Z-A und Nach Medientyp. Meistgenutzt ist die Voreinstellung und steigt mit den Sendern, die Sie tatsächlich auf der Uhr starten, sodass die Liste sich Ihre Gewohnheiten selbst beibringt.

Über der Liste zeigt ein kleiner zweizeiliger Zähler, wie viele Sender die aktuelle Suche und die Filter übrig lassen, über der Größe des gesamten Katalogs.

![Senderliste mit dem Zähler und der angehefteten Symbolleiste](screenshots/screenshot-wear-tv-step3.png)

Im Rastermodus zeigt ein Videosender ein Vorschaubild, bevor Sie ihn je geöffnet haben, entnommen aus einem herunterladbaren Vorschau-Set. Nach dem ersten Ansehen wird die Vorschau durch einen aus dem Sender selbst erfassten Frame ersetzt.

---

## Schritt 4 - Ansehen

1. Tippen Sie auf einen Sender. Der Videoplayer öffnet sich im Vollbild.
2. **Lautstärke:** Drehen Sie die drehbare Lünette oder Krone.
3. **Suchen:** Halten Sie die Zurück- oder Weiter-Schaltfläche lange gedrückt. Beide Schaltflächen bleiben selbst bei einem einzelnen Sender auf dem Bildschirm.
4. **Bild:** Die Bildmodus-Schaltfläche wechselt zwischen dem Einpassen des gesamten Bildes in das runde Glas und dem Zuschneiden zum Ausfüllen des Bildschirms. Die Uhr merkt sich Ihre Wahl - sie übersteht das Verlassen des Players und den Neustart der App, und dieselbe Wahl gilt auch für Ihre eigenen Videodateien.
5. **Bildschirm aus:** Das Player-Menü hat einen Eintrag **Bildschirm aus**. Das Display wird vollständig schwarz - keine Uhr, keine Steuerung -, während der Sender weiterläuft, und die Uhr schläft nicht ein. Ein einzelner Tipp markiert nur die berührte Stelle mit einem kleinen weißen Punkt; ein Doppeltipp, ein langes Drücken oder die eigene Taste der Uhr bringt das Bild und die Steuerung genau so zurück, wie Sie es verlassen haben.
6. **Pinnen:** Die Markierung im Player pinnt den Sender. Gepinnte Sender werden beim nächsten Öffnen von Streams zuerst aufgeführt: die hier auf der Uhr gepinnten führen, gefolgt von den auf dem Handy gepinnten, und alles andere behält die Reihenfolge, die Ihre gewählte Sortierung vorgibt. Der Pin ist an die Senderadresse gebunden, sodass er einen erneuten Katalog-Import übersteht.

> **Video braucht den Bildschirm.** Die Hintergrundwiedergabe hält nur **Audio** am Laufen, nachdem Sie die App verlassen haben - nützlich für Radiosender - aber Video und Diashows stoppen, wenn die App den Bildschirm verlässt. Das ist beabsichtigt: ein Video, das Sie nicht sehen können, kostet nur Akku.

---

## Schritt 5 - Mit einem Fingertipp zurückkehren

- Die **letzte-Ressourcen-Zeile des Startbildschirms** listet den zuletzt abgespielten Sender neben den zuletzt geöffneten Netzwerkressourcen auf, mit dem eigenen Symbol des Senders. Ein Tipp darauf öffnet den Player erneut.
- Eine **Stream-Kachel** kann dem Wear-OS-Kachel-Karussell hinzugefügt und von der Uhr aus auf einen Sender ausgerichtet werden. Von da an ist der Sender einen Wisch vom Ziffernblatt entfernt, ohne die App zuerst öffnen zu müssen.
- Die **Komplikation der letzten Ressource** zeigt den Sender ebenfalls, sodass er auf dem Ziffernblatt sitzen kann.

---

## Schritt 6 - Wenn die Verbindung schwach ist

Live-Streams sind das Anspruchsvollste, was eine Uhr mit ihrem Netzwerk tut, daher ist die App darüber explizit:

- Während ein Stream läuft, fragt die Uhr das System nach einem breitbandigen Netzwerk und gibt es frei, wenn die Wiedergabe endet.
- Kann die aktuelle Verbindung den Stream nicht tragen, sagt die Uhr das, statt stillschweigend zu versagen.
- Friert ein Stream ohne Fehlermeldung ein - die übliche Art, wie ein Live-Feed stirbt -, verankert und bereitet ein Watchdog ihn bis zu dreimal erneut vor und zeigt **Verbindung wird wiederhergestellt**. Erst wenn das Netzwerk dauerhaft tot bleibt, fällt sie auf die Meldung „Sender nicht verfügbar“ zurück.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| „Keine Streams verfügbar“ nach einer frischen Installation | Tippen Sie auf **Katalog aktualisieren**, oder senden Sie einen Sender vom Handy mit **An Uhr senden** |
| „Streams konnten nicht aktualisiert werden“ | Der Katalog ist ein Download von mehreren Megabyte. Bringen Sie die Uhr in Wi-Fi statt in eine handy-weitergeleitete Verbindung und versuchen Sie es erneut |
| Ein Sender öffnet sich und stoppt dann | Die Quelle selbst könnte offline sein. Die Uhr versucht es dreimal, bevor sie aufgibt - probieren Sie einen anderen Sender, um einen toten Stream von einem toten Netzwerk zu unterscheiden |
| Video stoppt, wenn Sie das Handgelenk senken | Erwartet: Nur Audio läuft weiter, sobald die App den Bildschirm verlässt. Um einen Sender bei dunklem Display weiterlaufen zu lassen, bleiben Sie im Player und verwenden Sie dessen Eintrag **Bildschirm aus** |
| Der auf dem Handy gepinnte Sender steht nicht oben | Pins wandern, wenn der Wear-Begleiter in der Handy-App eingeschaltet ist; prüfen Sie das zuerst |
| Der Ton ist zu leise | Drehen Sie die Lünette oder Krone im Player - das ändert die Medienlautstärke der Uhr, nicht die Wiedergabeposition |

</div>
