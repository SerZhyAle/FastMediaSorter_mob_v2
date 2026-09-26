---
layout: default
title: "Öffnen Sie die Ordner Ihres PCs durch Scannen eines einzigen Codes - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-de.html
---
<div lang="de" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Öffnen Sie die Ordner Ihres PCs durch Scannen eines einzigen Codes

> **Niveau:** Anfänger &bull; **Edition:** Standard, Photos, Legacy, VR, noLegal (Lite hat keine Netzwerkquellen; das Scannen benötigt eine Kamera, die Dateimethode funktioniert überall)

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="de" %}

Sie starten ein kleines Hilfsprogramm auf Ihrem Windows-PC, wählen die Ordner mit Ihren Videos, Ihrer Musik, Dokumenten oder Fotos aus, und es zeigt einen Code auf dem Bildschirm an. Auf dem Handy tippen Sie auf **Hinzufügen**, halten die Kamera vor diesen Code, und die PC-Ordner sind sofort verbunden - keine Adresseingabe, kein Port, kein Passwort, keine Kabel.

> **Einfach erklärt:** Der Windows-Helfer verwandelt Ihre ausgewählten Ordner in eine private, schreibgeschützte Freigabe in Ihrem Heim-Wi-Fi und druckt einen Code, der bereits alles enthält, was das Handy braucht, um sie zu erreichen. Diesen Code zu scannen ist dasselbe, wie ein langes Verbindungsformular von Hand auszufüllen - das Handy liest es nur auf einen Blick. Dateien öffnen sich dann bei Bedarf, gestreamt über Wi-Fi; nichts wird auf das Handy kopiert, bis Sie es verlangen.

---

## Das Hilfsprogramm

Der „Begleiter“ ist eine integrierte Funktion von **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (früher FastMediaSorter LITE) - dem kostenlosen Windows-Media-Sorter desselben Autors. Wenn Sie damit Ordner freigeben, tut er Folgendes:

- Startet auf Ihrem PC einen privaten SFTP-Server nur für diese Ordner.
- Generiert eigene Schlüssel und richtet den Autostart ein, sodass die Freigabe auch beim nächsten Mal vorhanden ist.
- Zeigt einen **QR-Code** auf dem Bildschirm an und kann auch eine kleine Konfigurationsdatei `.fmscfg` speichern.

**Wo Sie es bekommen:**

- Website: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Ordner veröffentlichen (Schritt für Schritt): [Anleitung: PC-Ordner für Android veröffentlichen](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [neueste Version](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (Installer oder portables ZIP)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: Suchen Sie nach „FastMediaSorter LITE“ (noch unter dem früheren Namen gelistet)

---

## Was Sie benötigen

- Ein Windows-PC mit installiertem **Fast Media Sorter for Windows**
- Ihr Handy und PC im **gleichen Wi-Fi-Netzwerk** (gleicher Router)
- Für den schnellsten Weg: eine Handy-**Kamera** zum Scannen des Codes (ein dateibasierter Weg steht zur Verfügung, falls keine Kamera vorhanden ist)

---

## Schritt 1 - Die Ordner auf dem PC freigeben

1. Installieren und starten Sie **Fast Media Sorter for Windows**, öffnen Sie dann den Reiter **Freigabe** in den Einstellungen.
2. Wählen Sie die Ordner aus, die Sie auf dem Handy haben möchten - Filme, Musik, Dokumente, Fotos, alles Mögliche.
3. Die App startet den SFTP-Server, generiert die Schlüssel und richtet den Autostart von selbst ein. Es ist nichts weiter zu konfigurieren.
4. Sie zeigt nun einen **QR-Code** auf dem PC-Bildschirm an. Lassen Sie dieses Fenster für Schritt 2 geöffnet.

> Bevorzugen Sie eine Datei statt eines Codes? Verwenden Sie **.fmscfg speichern** im selben Fenster und senden Sie diese Datei an das Handy (E-Mail, Telegram oder einen beliebigen freigegebenen Ordner). Siehe [Schritt 2, Methode B](#step-2-method-b---import-the-file).

---

## Schritt 2, Methode A - Den Code scannen (am schnellsten)

1. Öffnen Sie FastMediaSorter und tippen Sie auf dem Hauptbildschirm auf die Schaltfläche **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">**.
2. Tippen Sie auf **„Per Barcode importieren“** - sie befindet sich neben den vier Ressourcentyp-Karten (Lokal, SMB, SFTP/FTP, Cloud) und in der Kopfzeile des SFTP-Formulars.
3. Die Kamera öffnet sich mit dem Hinweis *„Richten Sie die Kamera auf den QR-Code des Begleiters“*. Halten Sie das Handy vor den QR-Code auf Ihrem PC. In einem dunklen Raum tippen Sie auf **Taschenlampe**.
4. Eine Bestätigung erscheint - *„Zugriff importieren - Die SFTP-Ressource .. mit N Ordner(n) hinzufügen?“*. Tippen Sie auf **Importieren**.
5. Fertig. Für jeden freigegebenen Ordner erscheint eine schreibgeschützte Ressource auf dem Hauptbildschirm, wobei der Schlüssel des Servers automatisch gepinnt wird.

> Der Eintrag **Per Barcode importieren** ist auf Geräten ohne Kamera und auf VR-Headsets ausgeblendet - verwenden Sie dort Methode B.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Schritt 2, Methode B - Die Datei importieren {#step-2-method-b---import-the-file}

Verwenden Sie dies, wenn das Handy keine Kamera hat oder wenn PC und Handy nicht nebeneinander stehen.

1. Verwenden Sie auf dem PC **.fmscfg speichern** und bringen Sie die Datei auf das Handy (E-Mail, Telegram, Cloud oder ein freigegebener Ordner).
2. **Wenn die Datei bereits auf dem Handy ist:** tippen Sie auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **„SFTP / FTP“** -> **„Aus Datei importieren“** und wählen Sie dann die `.fmscfg`-Datei aus.
3. **Wenn Sie sie als Anhang erhalten haben** (Telegram oder E-Mail): tippen Sie einfach auf den `.fmscfg`-Anhang - die App öffnet direkt einen Bestätigungsdialog.
4. Bestätigen Sie denselben Dialog *„Zugriff importieren“* und tippen Sie auf **Importieren**. Die schreibgeschützten Ressourcen erscheinen.

> **Behandeln Sie den Code und die Datei wie einen Schlüssel.** Beide betten das Zugriffspasswort ein, damit sich das Handy ohne jegliche Eingabe verbinden kann. Veröffentlichen Sie den QR-Screenshot oder die `.fmscfg`-Datei nicht öffentlich.

---

## Fertig! Das können Sie jetzt tun..

Die freigegebenen Ordner verhalten sich wie jede andere Ressource in der App. Zum Beispiel:

- **Filme und Serien ansehen** vom PC auf Ihrem Handy, Tablet oder Ihrer Android-TV-Box - gestreamt, nichts kopiert. Siehe [Heimkino & VR-Streaming](scenario-home-cinema-de.md).
- **Ihre Musikbibliothek abspielen** unterwegs oder auf einem Autoradio.
- **PDFs und EPUBs lesen**, die auf dem PC gespeichert sind, wobei Ihre letzte Position erhalten bleibt.
- **Ein Fotoarchiv durchsuchen** und es mit Quick Sort sortieren, oder es als [digitalen Bilderrahmen](scenario-photo-frame-de.md) anzeigen.
- **Eine Datei an eine spezialisierte App übergeben** - öffnen Sie das Info-Blatt einer Netzwerkdatei und tippen Sie auf Herunterladen und öffnen.
- **Dateien kopieren oder verschieben** zwischen PC und Handy in beide Richtungen.

---

## So funktioniert es (im Hintergrund)

- Der Windows-Helfer betreibt einen schlanken **SFTP-Server**, der an die von Ihnen ausgewählten Ordner gebunden ist, nur in Ihrem lokalen Netzwerk.
- Der QR-Code (oder die `.fmscfg`-Datei) kodiert die Verbindung: Host, Port, Zugangsdaten, die Pfade der freigegebenen Ordner und den Host-Key-Fingerabdruck des Servers. Umfangreiche Freigaben werden komprimiert übertragen, sodass selbst viele Ordner in einen Code passen.
- Das Handy liest diese Nutzdaten aus, überprüft sie und erstellt pro Ordner eine **schreibgeschützte SFTP-Ressource**. Der Code enthält auch den Fingerabdruck des PC-Serverschlüssels, und das Handy prüft ihn bei jeder Verbindung - beim Durchsuchen, Kopieren, bei Vorschaubildern und bei der Wiedergabe. Falls jemals ein anderer Computer an Stelle Ihres PCs antwortet, lädt das Handy nichts und teilt Ihnen mit, dass der Server anders aussieht.
- Da es sich um Ihr lokales Wi-Fi handelt und der Zugriff schreibgeschützt ist, durchsucht und streamt das Handy die Dateien, ohne etwas auf dem PC zu verändern.
- **Im gleichen Wi-Fi findet das Handy den PC von selbst.** Der Begleiter meldet die Freigabe im lokalen Netzwerk, und das Handy gleicht sie anhand des gepinnten Schlüssels ab - selbst wenn sich die Netzwerkadresse des PCs ändert, funktioniert die Freigabe weiter, ohne dass erneut gescannt werden muss.
- **Ein einziger Import kann zu Hause und unterwegs funktionieren.** Der Code kann mehr als eine Adresse enthalten - die lokale, eine IPv6-Adresse und eine Portweiterleitung ins Internet. Das Handy probiert sie aus und verwendet die gerade erreichbare: die lokale Adresse zu Hause, die Internetadresse bei mobilen Daten. Dieselbe Ressource funktioniert weiter, während Sie zwischen Netzwerken wechseln, solange der PC von dort aus tatsächlich erreichbar ist.
- **Kann keine Verbindung hergestellt werden, erklärt die App, was zu tun ist** - sich mit demselben Wi-Fi verbinden oder den Zugriff auf dem PC einrichten - statt einer nackten Fehlermeldung. Enthält der Begleiter einen Hinweis zum Zugriff, zeigt das Handy ihn an.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Kein Eintrag „Per Barcode importieren“ | Das Gerät hat keine Kamera, oder es handelt sich um eine VR-Version. Verwenden Sie [Methode B - Die Datei importieren](#step-2-method-b---import-the-file) |
| Die Kamera meldet, dass Zugriff benötigt wird | Erteilen Sie bei Aufforderung die Kameraberechtigung - sie wird nur für den Scan verwendet |
| „Diese Datei ist keine gültige Begleiter-Konfiguration“ | Der Code oder die Datei stammt nicht vom Windows-Begleiter. Exportieren Sie sie erneut aus dem Reiter **Freigabe** |
| „Von einer neueren Begleiter-Version erstellt“ | Aktualisieren Sie FastMediaSorter auf dem Handy, oder exportieren Sie erneut mit einer passenden Begleiter-Version |
| Ressource hinzugefügt, aber Ordner sind leer | Stellen Sie sicher, dass der PC-Helfer noch läuft. Im **gleichen Wi-Fi** findet die App den PC von selbst; schlägt es weiterhin fehl, zeigt die App, was zu prüfen ist |
| Funktioniert über Wi-Fi, aber nicht mit mobilen Daten | Um den PC aus einem anderen Netzwerk zu erreichen, muss er aus dem Internet erreichbar sein - richten Sie Portweiterleitung oder IPv6 in den **Freigabe**-Einstellungen des Begleiters ein. Ohne das funktioniert die Freigabe nur im gleichen Wi-Fi |

→ Weitere Hilfe: [TROUBLESHOOTING.md](../TROUBLESHOOTING-de.md) &bull; Grundlagen: [Mit NAS / Windows-Freigabe verbinden (SMB)](scenario-smb-setup-de.md)

</div>
