---
layout: default
title: "Mit NAS / Windows-Freigabe verbinden (SMB) - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-de.html
---
<div lang="de" dir="ltr" markdown="1">

# 🖥️ Mit Heim-NAS / Windows-Freigabe verbinden (SMB)

> **Niveau:** Anfänger &bull; **Edition:** Standard, Photos, Legacy, VR, noLegal (Lite hat keine Netzwerkquellen)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="de" %}

SMB (auch Windows-Dateifreigabe oder CIFS genannt) lässt Sie Dateien auf Ihrem Heim-PC, Laptop oder NAS-Gerät genau so durchsuchen, als wären sie auf Ihrem Handy - keine Kabel, kein USB, nur Wi-Fi.

> **Einfach erklärt:** Stellen Sie sich vor, Ihr PC hat ein öffentliches Schwarzes Brett in Ihrem Heim-Wi-Fi. Jedes Gerät im Haus kann von diesem Brett lesen. FastMediaSorter verbindet sich mit diesem „Brett“ (Ihrem freigegebenen Ordner) und lässt Sie Ihre Dateien durchsuchen, als wären sie direkt auf Ihrem Handy gespeichert. Nichts wird im Voraus kopiert oder heruntergeladen - Dateien öffnen sich bei Bedarf.

---

## Was Sie benötigen

- Ihr Handy und Ihr PC / NAS im **gleichen Wi-Fi-Netzwerk** (gleicher Router)
- Die **IP-Adresse** Ihres PCs oder NAS (z. B. `192.168.1.100`)
- Den **Freigabenamen** (den Namen des von Ihnen freigegebenen Ordners, z. B. `Photos`)
- Einen **Benutzernamen und ein Passwort** für diese Freigabe (oder Gastzugriff, falls aktiviert)

> **Nicht sicher bei den Begriffen?** Keine Sorge - die Schritte 1 und 2 erklären genau, wo Sie diese finden.

---

## Schritt 1 - Die IP-Adresse Ihres PCs finden

Die IP-Adresse ist die „Heimatadresse“ Ihres PCs in Ihrem Wi-Fi-Netzwerk. Sie benötigen sie, damit Ihr Handy weiß, wo es suchen soll.

Unter **Windows:**
1. Drücken Sie `Win + R`, geben Sie `cmd` ein, drücken Sie die Eingabetaste - ein schwarzes Textfenster öffnet sich
2. Geben Sie `ipconfig` ein und drücken Sie die Eingabetaste
3. Suchen Sie **IPv4-Adresse** unter Ihrem Wi-Fi-Adapter - etwas wie `192.168.1.100`

> Die benötigte Zeile heißt **„IPv4-Adresse“** (nicht IPv6, das wie eine lange Folge von Buchstaben und Zahlen aussieht). Sie sollte in den meisten Heimnetzwerken mit `192.168.` beginnen.

Auf einem **NAS** (Synology, QNAP usw.):
- Öffnen Sie das NAS-Webpanel → Netzwerkeinstellungen - die IP wird dort angezeigt

> Notieren Sie sich die IP - Sie benötigen sie in Schritt 6.

![Windows PowerShell - ipconfig-Ausgabe, IPv4-Adresse `192.168.1.100` sichtbar](screenshots/screenshot-smb-step1.png)

---

## Schritt 2 - Den Freigabenamen auf Ihrem PC finden

Der „Freigabename“ ist der öffentliche Name Ihres Ordners im Netzwerk. Er kann mit dem Ordnernamen übereinstimmen oder abweichen.

Unter **Windows:**
1. Öffnen Sie den **Explorer**
2. Rechtsklick auf den Ordner, den Sie freigeben möchten → **Eigenschaften**
3. Gehen Sie zum Reiter **Freigabe**
4. Schauen Sie sich den **Netzwerkpfad** an - er sieht aus wie `\\DESKTOP-ABC\Photos`
5. Der Teil nach dem letzten `\` ist Ihr **Freigabename** (hier: `Photos`)

> **Ordner noch nicht freigegeben?** Klicken Sie auf **Freigeben..** → wählen Sie **Jeder** → **Hinzufügen** → **Freigeben**. Windows zeigt Ihnen sofort den Netzwerkpfad an.

> **Wichtig:** Stellen Sie sicher, dass **Netzwerkerkennung** und **Dateifreigabe** in Windows aktiviert sind. Gehen Sie zu Systemsteuerung → Netzwerk- und Freigabecenter → Erweiterte Freigabeeinstellungen ändern → schalten Sie „Netzwerkerkennung“ und „Datei- und Druckerfreigabe“ ein.

![Windows-Ordnereigenschaften - Reiter Freigabe, Netzwerkpfad `\\MARK\Common` sichtbar](screenshots/screenshot-smb-step2.png)

---

## Schritt 3 - FastMediaSorter öffnen und auf „+“ tippen

1. Öffnen Sie die App
2. Tippen Sie auf dem **Hauptbildschirm** auf die Schaltfläche **„Hinzufügen“ <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** in der oberen Symbolleiste

![FastMediaSorter Hauptbildschirm - Schaltfläche Hinzufügen in der oberen Symbolleiste hervorgehoben, SMB-Reiter sichtbar](screenshots/screenshot-smb-step3.png)

---

## Schritt 4 - „Netzwerkordner (SMB)“ auswählen

Tippen Sie in der Liste der Ressourcentypen auf **„Netzwerkordner (SMB)“** (oder den SMB-Reiter).

![Dialog „Ordnertyp auswählen“ - vier Optionen: Lokaler Ordner, Netzwerkordner (SMB), SFTP/FTP, Cloud-Speicher](screenshots/screenshot-smb-step4.png)

---

## Schritt 5 - Zuerst die automatische Erkennung versuchen

Tippen Sie auf die Schaltfläche **„Netzwerk durchsuchen“**. Die App durchsucht Ihr lokales Wi-Fi nach Geräten mit SMB-Freigaben.

- Warten Sie ~10 Sekunden
- Eine Liste gefundener Geräte erscheint
- Tippen Sie auf Ihren PC oder NAS - die IP-Adresse wird automatisch eingetragen

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **Nichts gefunden?** Kein Problem - fahren Sie mit Schritt 6 fort und geben Sie die IP manuell ein. Dies passiert, wenn Ihr Router AP-Isolation verwendet (eine Einstellung, die aus Sicherheitsgründen die Kommunikation zwischen Handy und PC blockiert). Manuelle IP-Eingabe funktioniert immer.

---

## Schritt 6 - Die Verbindungsdetails ausfüllen

Füllen Sie das Formular aus:

| Feld | Was einzugeben ist | Beispiel |
|-------|--------------|---------|
| **Server / Pfad** | `\\IP\Freigabename` | `\\192.168.1.100\Photos` |
| **Benutzername** | Ihr Windows-Anmeldename | `john` |
| **Passwort** | Ihr Windows-Passwort | `••••` |
| **Anzeigename** | Ein beliebiger Name (optional) | `Home PC - Photos` |

> **Verwenden Sie ein Microsoft-Konto (E-Mail) zur Anmeldung bei Windows?** Verwenden Sie Ihre **vollständige E-Mail-Adresse** als Benutzernamen (z. B. `john@outlook.com`), nicht nur Ihren Vornamen. Ihr Passwort ist dasselbe, mit dem Sie Ihren PC entsperren.

> **Kein Passwort, oder verwenden Sie Gast?** Versuchen Sie, Benutzername und Passwort leer zu lassen und auf Verbindung testen zu tippen - manche Heim-PCs erlauben offenen Zugriff.

![Netzwerkordner (SMB) hinzufügen - Server-IP `192.168.1.100`, Freigabename und Zugangsdaten ausgefüllt](screenshots/screenshot-smb-step6.png)

![Netzwerkordner (SMB) hinzufügen - unterer Bereich: Optionen, Medientypen, Schaltfläche DIESE RESSOURCE HINZUFÜGEN](screenshots/screenshot-smb-step6b.png)

**Referenz für Adressformate:**

| Format | Beispiel |
|--------|---------|
| Windows-Standard | `\\192.168.1.100\Photos` |
| Linux-/macOS-Stil | `smb://192.168.1.100/Photos` |
| Unterordner | `\\192.168.1.100\Media\Movies` |
| Benutzerdefinierter Port | `smb://192.168.1.100:445/Photos` |

---

## Schritt 7 - Die Verbindung testen

Tippen Sie auf **„Verbindung testen“**.

- **Grüne Meldung** = Erfolg → weiter zu Schritt 8! Sie sind fast fertig.
- **Rote Meldung** = etwas stimmt nicht → prüfen Sie die Problembehandlungs-Tabelle unten. Häufigste Lösung: IP und Freigabename doppelt prüfen.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## Schritt 8 - Speichern und öffnen

Tippen Sie auf **„Speichern“**. Der neue Ordner erscheint mit einem SMB-Abzeichen auf dem Hauptbildschirm.

Tippen Sie darauf, um seinen Inhalt zu durchsuchen - Fotos, Videos und andere Dateien erscheinen als Vorschaubilder wie jeder lokale Ordner.

![FastMediaSorter Hauptbildschirm - neue SMB-Ressourcenkarte „Common“ (smb://192.168.1.100/Common) mit hervorgehobenem Netzwerkordner-SMB-Abzeichen](screenshots/screenshot-smb-step8.png)

---

## Fertig! Das können Sie jetzt tun..

- Alle Dateien auf Ihrem PC von Ihrem Handy aus durchsuchen
- Videos und Musik direkt abspielen - kein Herunterladen nötig
- Dateien zwischen Ihrem Handy und dem PC kopieren oder verschieben
- Diesen Ordner als Quelle für Diashow, Bilderrahmen oder Automusik verwenden

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| „Verbindung abgelehnt“ | Öffnen Sie die Windows-Firewall → erlauben Sie eingehenden **TCP-Port 445**. Oder deaktivieren Sie die Firewall vorübergehend zum Testen |
| „Falsches Passwort“ | Versuchen Sie, **Benutzername leer** zu lassen (Gastzugriff). Oder geben Sie bei einem Microsoft-Konto Ihre **vollständige E-Mail** als Benutzername ein |
| „Host nicht gefunden“ | Stellen Sie sicher, dass Handy und PC im **gleichen Wi-Fi** und am gleichen Router sind. AP-Isolation (eine Router-Sicherheitseinstellung) kann dies blockieren - versuchen Sie, sie in den Router-Einstellungen zu deaktivieren |
| Die Suche findet nichts | Deaktivieren Sie VPN auf dem Handy. Prüfen Sie außerdem, dass **Netzwerkerkennung** in Windows aktiviert ist (Systemsteuerung → Netzwerk- und Freigabecenter). Versuchen Sie dann, die IP manuell einzugeben |
| Sehr langsames Durchsuchen | Tippen Sie auf **Bearbeiten** bei der Ressource → führen Sie einen **Geschwindigkeitstest** aus, um den tatsächlichen Durchsatz zu sehen. Deaktivieren Sie Video-Vorschaubilder bei langsamen Verbindungen |
| Funktioniert über Wi-Fi, aber nicht mit mobilen Daten | Erwartet - SMB ist ausschließlich ein lokales Netzwerkprotokoll. Es kann nicht über mobile Daten funktionieren |

→ Weitere Hilfe: [TROUBLESHOOTING.md](../TROUBLESHOOTING-de.md)

</div>
