---
layout: default
title: "FastMedia auf Ihrer Uhr einrichten - FastMediaSorter v2"
permalink: /docs/howto/wear-install-de.html
---
<div lang="de" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> FastMedia auf Ihrer Uhr einrichten

> **Niveau:** Anfänger &bull; **Zeit:** ~5 Minuten &bull; **Gerät:** Wear OS-Smartwatch, gekoppelt mit einem Android-Handy

> **Zwei Versionen.** Die Version aus Google Play ist eine kleine Erstveröffentlichung: Taschenrechner, Stoppuhr, Minispiel, Einstellungen und die Programme-Kachel. Musik, Fotos, Netzwerkfreigaben und Handy-Funktionen gibt es nur in der Vollversion, einem direkten APK-Download von [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="de" %}

FastMedia Wear ist die Uhr-Hälfte von FastMediaSorter. Sobald sie an Ihrem Handgelenk sitzt, können Sie Musik direkt von der Uhr abspielen und Fotos ansehen, auf Ordner zugreifen, die Ihr gekoppeltes Handy freigibt, und Netzwerkfreigaben öffnen, mit denen sich die Uhr selbstständig verbindet. Diese Seite bringt sie zur Installation und Kopplung.

---

## Was Sie benötigen

- Eine Smartwatch mit **Wear OS 3.0** oder neuer
- Ein Android-Handy mit installiertem FastMediaSorter, mit dem die Uhr bereits in den Systemeinstellungen gekoppelt ist
- Eine Wi-Fi- oder Mobilfunkverbindung auf der Uhr oder auf dem gekoppelten Handy für den Download

---

## Schritt 1 - FastMedia Wear auf der Uhr installieren

1. Öffnen Sie auf der Uhr die App-Liste und tippen Sie auf **Play Store**.
2. Suchen Sie nach **FastMedia Wear**.
3. Tippen Sie auf **Installieren** und warten Sie, bis der Download abgeschlossen ist. Die Uhr zeigt die App danach in ihrer App-Liste an.

> Uhren unterscheiden sich darin, wie viel Texteingabe sie erlauben. Ist die Suche am Handgelenk unhandlich, öffnen Sie den Play Store auf Ihrem Handy, suchen Sie FastMedia Wear und wählen Sie Ihre Uhr als Installationsziel - die Uhr lädt es dann von selbst herunter.

### Kein Play Store? Eine APK über ADB installieren

Nutzen Sie diesen Weg, wenn Ihre Uhr keinen Zugriff auf den Play Store hat. Sie benötigen einen Computer mit den Android SDK Platform-Tools (`adb`) und ein lokales Wi-Fi-Netzwerk, das von Computer und Uhr gemeinsam genutzt wird. Es funktioniert nicht allein über das Internet.

1. Laden Sie eine APK von der Seite [Direct APK Release](../DOWNLOADS.md) herunter:
   - `FastMediaSorter_wear_debug.apk` ist der Debug-Build zum Testen. Er wird als
     `com.sza.fastmediasorter.debug` installiert.
   - `FastMediaSorter_wear_release.apk` ist der signierte Nicht-Debug-Build. Er wird als
     `com.sza.fastmediasorter` installiert.
   - Die beiden Builds haben unterschiedliche Paketnamen, sodass sie nebeneinander installiert
     bleiben können. Versuchen Sie nicht, eine `.aab`-Datei aus dem Play Store mit ADB zu installieren.
2. Aktivieren Sie auf der Uhr den Entwicklermodus: **Einstellungen** → **Über die Uhr** → tippen Sie siebenmal
   auf **Build-Nummer**. Aktivieren Sie in den **Entwickleroptionen** **ADB-Debugging** und **Kabelloses Debugging**.
3. Wählen Sie unter **Kabelloses Debugging** die Option **Neues Gerät koppeln**. Geben Sie auf dem Computer die
   von der Uhr angezeigte Kopplungsadresse und den Code ein und verbinden Sie sich dann mit dem separaten
   Verbindungsport vom Hauptbildschirm des kabellosen Debuggings:

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Bestätigen Sie die Debugging-Aufforderung auf der Uhr. Der Kopplungs- und der Verbindungsport sind unterschiedlich.
4. Installieren oder aktualisieren Sie die APK. Verwenden Sie den Befehl, der zur heruntergeladenen Datei passt:

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` aktualisiert dasselbe Paket unter Beibehaltung seiner App-Daten. Es wandelt keinen Debug-Build in
   einen Release-Build um, da dies separate Apps sind.
5. Öffnen Sie **FastMedia Wear** aus der App-Liste der Uhr. Falls nötig, starten Sie es über ADB:

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> Diese Methode erfordert eine Wear-OS-Uhr. Galaxy Watch 3, Galaxy Watch Active und Active 2 laufen mit Tizen
> und können keine Wear-OS-APKs installieren. Schalten Sie das kabellose Debugging nach Abschluss aus, sofern
> Sie es nicht für ein weiteres Update benötigen.

---

## Schritt 2 - Den Wear-Begleiter auf dem Handy einschalten

Die Handy-Seite ist ausgeschaltet, bis Sie angeben, dass Sie eine Uhr besitzen.

1. Öffnen Sie FastMediaSorter auf dem Handy.
2. Gehen Sie zu **Einstellungen** und öffnen Sie den Reiter **Verwaltung**.
3. Suchen Sie die Gruppe **Wear OS** und klappen Sie sie auf.
4. Aktivieren Sie das Kontrollkästchen **Wear-Begleiter**.

Das Kontrollkästchen schaltet den gesamten Begleiter ein: Die Schaltfläche, die sein Fenster öffnet, erscheint direkt darunter, ein Eintrag dafür wird der Programmliste hinzugefügt, und er wird als Panel-Kachel und Launcher-Verknüpfung verfügbar.

> Versionen ohne die Uhr-Brücke zeigen diese Gruppe überhaupt nicht an. Wenn Sie sie nicht finden, verwenden Sie eine Edition, die ohne Wear-Unterstützung ausgeliefert wird.

---

## Schritt 3 - Wählen, was zur Uhr gelangt

1. Tippen Sie in derselben Gruppe auf **Wear-Begleiter**. Sein Fenster öffnet sich über der App.
2. Wählen Sie die Ressourcen aus, die die Uhr sehen soll. Nichts wird gesendet, bis Sie wählen - eine leere Auswahl sendet nichts, statt Ihre gesamte Bibliothek zu übertragen.
3. Passen Sie hier auch die eigenen Einstellungen der Uhr an: Ansichtsmodus, Wachhalte-Verhalten und die auf dem Uhr-Startbildschirm gezeigten Bereiche.

---

## Schritt 4 - Prüfen, dass beide Hälften sich sehen

1. Öffnen Sie **FastMedia Wear** auf der Uhr.
2. Der Startbildschirm listet seine Bereiche auf - **Handy**, **Lokal**, **Ressourcen**, **Streams** und **Apps**.
3. Tippen Sie auf **Handy**. Die in Schritt 3 ausgewählten Ordner erscheinen.

Ist der Bereich Handy leer, kehren Sie zum Begleiter-Fenster auf dem Handy zurück und bestätigen Sie, dass mindestens eine Ressource ausgewählt ist.

> **Tipp:** Sie können von jedem Bildschirm auf Ihrer Uhr zurücknavigieren, indem Sie die sichtbare universelle Zurück-Schaltfläche am linken Rand verwenden, vom linken Rand wischen oder die Hardware-Zurücktaste Ihrer Uhr drücken. Auf dem Haupt-Startbildschirm zeigt ein Antippen der Zurück-Schaltfläche ein Beenden-Symbol (ein Pfeil, der eine Box verlässt), um die App zu verlassen, oder ein Doppel-Chevron («), um die Hintergrundwiedergabe zu minimieren. Auf jedem Bildschirm, der diese Schaltfläche zeigt, befindet sich ihr gegenüber am rechten Rand eine Bildschirm-aus-Schaltfläche (ein Handy mit dunklem Bildschirm), die den Uhr-Bildschirm verdunkelt; ein Doppeltipp, ein langes Drücken oder die Hardware-Taste bringt ihn zurück.

---

## Wenn etwas nicht funktioniert

- **Die Uhr-App erscheint nicht im Play Store.** Bestätigen Sie, dass die Uhr Wear OS 3.0 oder neuer ausführt. Ältere Uhren verwenden ein anderes App-Modell und werden nicht unterstützt.
- **Die Gruppe Wear OS fehlt in den Handy-Einstellungen.** Die von Ihnen verwendete Version enthält die Uhr-Brücke nicht.
- **Der Bereich Handy auf der Uhr ist leer.** Im Begleiter-Fenster ist nichts ausgewählt, oder die Uhr und das Handy haben ihre Kopplung verloren - prüfen Sie zuerst die Kopplung in den Systemeinstellungen.
- **Die Wiedergabe stottert über die Handy-Verbindung.** Bluetooth zwischen Uhr und Handy ist schmalbandig. Übertragen Sie für langes Hören die Dateien auf die Uhr oder verbinden Sie die Uhr direkt mit einer Netzwerkfreigabe.

---

## Wie es weitergeht

- [Musik auf der Smartwatch](scenario-watch-music-de.md) - spielen Sie Ihre Sammlung auf der Uhr ab, mit Cover-Art, Zufallswiedergabe und Lautstärke über die Lünette.
- [Uhr mit Netzwerkfreigaben verbinden](scenario-watch-network-de.md) - erreichen Sie ein NAS oder eine PC-Freigabe von der Uhr aus über Wi-Fi, ohne das Handy.

</div>
