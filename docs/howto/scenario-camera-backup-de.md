---
layout: default
title: "Geplantes Kamera-Backup auf den PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-camera-backup-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 📷 Geplantes Kamera-Backup auf den PC

> **Niveau:** Anfänger &bull; **Zeit:** ~15 Minuten Einrichtung &bull; **Edition:** Standard, Photos, Legacy, VR, noLegal (benötigt Netzwerkquellen - Lite hat keine)

{% include lang-switcher.html doc="scenario-camera-backup" dir="/docs/howto/" current="de" %}

Kopieren Sie neue Fotos von der Kamera Ihres Handys automatisch **jede Nacht über Wi-Fi** auf Ihren Heimcomputer. Einmal eingerichtet, läuft es für immer ohne manuelles Zutun.

**Das bringt es Ihnen:** Jeden Morgen wachen Sie auf, und die Fotos der letzten Nacht sind bereits auf Ihrem PC. Keine Kabel. Keine Cloud-Abos. Kein Vergessen. Vollautomatisch.

---

## Was Sie benötigen

- Handy und PC im **gleichen Heim-Wi-Fi** (gleicher Router)
- Ein Ordner auf Ihrem PC, in dem die Fotos gespeichert werden (z. B. `C:\PhoneBackup`)
- FastMediaSorter installiert in einer Edition mit Netzwerkquellen (**Standard**, Photos, Legacy, VR, noLegal)

> **Nicht sicher, welche Edition Sie haben?** Öffnen Sie **Einstellungen** <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> → **Systeminformationen**. Die Zeile **Edition** nennt die Edition (Standard / Lite / usw.).

---

## Schritt 1 - Backup-Ordner auf Ihrem PC erstellen

Erstellen Sie zunächst einen Ordner auf Ihrem PC. Geben Sie ihn dann frei, damit das Handy ihn erreichen kann.

Unter **Windows:**
1. Erstellen Sie irgendwo einen neuen Ordner - zum Beispiel `C:\PhoneBackup`
2. **Rechtsklick** auf den Ordner → **Eigenschaften** → Reiter **Freigabe** → klicken Sie auf **Freigeben..**
3. Wählen Sie im Dropdown Ihren Benutzernamen aus oder geben Sie **Jeder** ein → klicken Sie auf **Hinzufügen** → klicken Sie auf **Freigeben**
4. Windows zeigt den Netzwerkpfad an - notieren Sie ihn sich. Er sieht etwa so aus: `\\MYPC\PhoneBackup`

> **Notieren Sie sich außerdem die IP-Adresse Ihres PCs** - Sie benötigen sie in Schritt 2. Der schnellste Weg: Drücken Sie **Win + R**, geben Sie `cmd` ein und drücken Sie die Eingabetaste. Geben Sie im schwarzen Fenster `ipconfig` ein und drücken Sie die Eingabetaste. Suchen Sie die Zeile **IPv4-Adresse** unter Ihrem Wi-Fi-Adapter. Beispiel: `192.168.1.100`. Notieren Sie sich diese Zahl.

---

## Schritt 2 - Die App mit Ihrem PC-Ordner verbinden

Sagen Sie FastMediaSorter nun, wohin die Fotos gesendet werden sollen.

> **Was ist SMB?** Das ist einfach die Art, wie Windows Ordner über das Heim-Wi-Fi freigibt. Sie müssen die Details nicht verstehen - folgen Sie einfach den Schritten.

1. Öffnen Sie die App → tippen Sie in der oberen Symbolleiste auf **Hinzufügen <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → wählen Sie **„Netzwerkordner (SMB)“**
2. Geben Sie im Feld **Server / Pfad** ein: `\\192.168.1.100\PhoneBackup`
   - Ersetzen Sie `192.168.1.100` durch die tatsächliche IP-Adresse Ihres PCs aus Schritt 1
   - Ersetzen Sie `PhoneBackup` durch den tatsächlichen Namen Ihres Ordners
3. Geben Sie Ihren Windows-**Benutzernamen** und Ihr **Passwort** ein (dieselben, mit denen Sie sich an Ihrem PC anmelden)
4. Tippen Sie auf **Verbindung testen** - warten Sie ein paar Sekunden - Sie sollten eine grüne Erfolgsmeldung sehen
5. Tippen Sie auf **Speichern**

> **Keine Verbindung möglich?** Sehen Sie sich die [SMB-Einrichtungsanleitung](scenario-smb-setup-de.md) an - sie behandelt jedes gängige Verbindungsproblem mit schrittweisen Lösungen.


---

## Schritt 3 - Einstellungen für geplante Vorgänge öffnen

1. Tippen Sie auf **Einstellungen** (das Zahnradsymbol <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> in der Symbolleiste)
2. Wechseln Sie zum Reiter **Verwaltung**
3. Scrollen Sie zu **„Geplante Vorgänge nach Zeitplan“** und tippen Sie auf **„Geplante Dateivorgänge“** - der Bildschirm für geplante Vorgänge öffnet sich
4. Schalten Sie oben auf diesem Bildschirm **„Geplante Vorgänge verwenden“** ein

![Einstellungen → Verwaltung - Bereich „Geplant“ mit Schaltfläche HINZUFÜGEN](screenshots/screenshot-cb-step3.png)

---

## Schritt 4 - Einen neuen Zeitplan erstellen

Tippen Sie auf dem Bildschirm für geplante Vorgänge auf die Schaltfläche **+** (**„Hinzufügen“**).

Ein Dialog für einen neuen Zeitplan öffnet sich.

![Dialog „Zeitplan hinzufügen“ - Bereich Bedingungen: Intervall- und Überschreiboptionen](screenshots/screenshot-cb-step4.png)

---

## Schritt 5 - Den Backup-Zeitplan ausfüllen

Füllen Sie jedes Feld aus:

| Feld | Was einzustellen ist | Beispiel |
|-------|------------|---------|
| **Name** | Eine beliebige Bezeichnung zum Wiedererkennen dieses Zeitplans | `Nightly Camera Backup` |
| **Quelle** | Wo sich Ihre Kamerafotos befinden | Wählen Sie **„Kamerafotos“** - findet automatisch alle Kameraaufnahmen |
| **Ziel** | Ihr PC-Backup-Ordner | Wählen Sie die soeben hinzugefügte SMB-Ressource (`PhoneBackup (SMB)`) |
| **Vorgang** | Was mit den Dateien geschehen soll | **„Kopieren (vorhandene überspringen)“** - kopiert nur neue Fotos, nie Duplikate |
| **Zeitplan** | Wann ausgeführt werden soll | `Daily at 02:00` - läuft, während Sie schlafen |
| **Nur über Wi-Fi ausführen** | Schalten Sie dies EIN | Verhindert, dass das Backup versehentlich Ihre mobilen Daten nutzt |

> **Was ist „Kamerafotos“?** Das ist ein spezieller virtueller Ordner, den FastMediaSorter automatisch erstellt. Er zeigt immer alle von Ihrer Kamera aufgenommenen Fotos - auch wenn sie in verschiedenen Ordnern auf Ihrem Handy gespeichert sind. Bevorzugen Sie ihn stets gegenüber der manuellen Pfadauswahl.

![Zeitplan hinzufügen - Quelle: Kamerafotos, Vorgang: Kopieren, Ziel: SMB](screenshots/screenshot-cb-step5.png)

---

## Schritt 6 - Speichern und Hintergrundzugriff erlauben

Tippen Sie auf **Speichern**.

Der Zeitplan erscheint in der Liste - er ist jetzt aktiv.

**Möglicherweise erscheint ein Berechtigungsdialog.** Die App bittet darum, von der Akku-Optimierung ausgenommen zu werden. Tippen Sie auf **„Optimierung deaktivieren“** (oder **„Zulassen“**).

> **Warum ist dieser Schritt wichtig?** Android versucht, Akku zu sparen, indem es im Hintergrund laufende Apps automatisch stoppt. Ohne diese Berechtigung könnte Android das Backup mitten in der Nacht beenden. Die Erteilung erlaubt der App lediglich, zur geplanten Zeit aufzuwachen - sie belastet den Akku dadurch nicht spürbar.

![Gespeicherter Zeitplaneintrag: Kamerafotos → SMB zur geplanten Zeit](screenshots/screenshot-cb-step6.png)

---

## Schritt 7 - Jetzt gleich testen

Warten Sie nicht bis 2 Uhr nachts - testen Sie das Backup sofort, um sicherzustellen, dass alles funktioniert:

1. Gehen Sie zu **Einstellungen → Verwaltung → Geplante Dateivorgänge**
2. Suchen Sie Ihren Zeitplan → tippen Sie auf **„Jetzt ausführen“**
3. Oben auf Ihrem Bildschirm erscheint eine Benachrichtigung mit dem Übertragungsfortschritt
4. Wenn es fertig ist: Tippen Sie auf die SMB-Ressource (`PhoneBackup`) → dort sollten Ihre Kamerafotos sichtbar sein

> **Es wurde nichts kopiert?** Wenn sich bereits alle Ihre Fotos im Backup-Ordner befinden (oder der Kameraordner des Handys leer ist), kopiert die App korrekterweise null Dateien. Machen Sie ein neues Testfoto und führen Sie es erneut aus.


---

## Fertig! So läuft es jede Nacht ab

1. Um 02:00 Uhr wacht die App leise auf
2. Sie sieht sich Ihren Kameraordner an und vergleicht ihn mit dem PC-Backup-Ordner
3. Kopiert nur die Fotos, die noch nicht auf dem PC sind - das dauert von ein paar Sekunden bis zu ein paar Minuten
4. Zeigt eine Benachrichtigung: „12 Dateien gesichert“ (oder wie viele neue es sind)
5. Schläft wieder ein

Ihr PC erhält jeden Morgen neue Fotos. Sie müssen nie mehr daran denken.

---

## Tipps

> **Möchten Sie nach dem Backup Speicherplatz auf dem Handy freigeben?** Ändern Sie den Vorgang statt auf „Kopieren“ auf **„Verschieben“**. Fotos werden vom Handy gelöscht, sobald sie sicher auf den PC kopiert wurden. Verwenden Sie dies mit Vorsicht - einmal verschoben, befinden sich die Fotos nicht mehr auf dem Handy.

> **Mehrere Handys in der Familie?** Erstellen Sie einen Zeitplan pro Handy. Verwenden Sie unterschiedliche Unterordner als Ziele - zum Beispiel `PhoneBackup\Mom` und `PhoneBackup\Dad` - damit alle Geräte auf denselben PC sichern, ohne die Dateien zu vermischen.

> **Lieber auf Google Drive sichern?** Fügen Sie statt SMB eine Google-Drive-Ressource als Ziel hinzu - der Rest der Schritte ist identisch.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Zeitplan läuft nachts nicht | Gehen Sie zu **Android-Einstellungen → Apps → FastMediaSorter → Akku** → stellen Sie auf **Uneingeschränkt** |
| Fehler „Ziel nicht erreichbar“ | Ihr Handy muss zur Backup-Zeit mit Wi-Fi verbunden sein. War Wi-Fi um 2 Uhr nachts ausgeschaltet, wird das Backup übersprungen und in der nächsten Nacht automatisch wiederholt |
| Einige Fotos wurden nicht gesichert | Verwenden Sie die virtuelle Ressource **„Kamerafotos“** als Quelle - sie erfasst Fotos aus allen Kameraordnern Ihres Handys |
| Doppelte Dateien erscheinen auf dem PC | Stellen Sie sicher, dass der Vorgang auf **„Kopieren (vorhandene überspringen)“** eingestellt ist, nicht auf „Kopieren (überschreiben)“ |

</div>
