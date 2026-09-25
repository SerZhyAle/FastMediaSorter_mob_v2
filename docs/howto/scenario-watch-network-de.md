---
layout: default
title: "Smartwatch mit NAS & PC-Freigaben verbinden - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-de.html
---
<div lang="de" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Smartwatch mit NAS & PC-Freigaben verbinden

> **Niveau:** Fortgeschritten &bull; **Zeit:** ~10 Minuten &bull; **Gerät:** Wear OS-Smartwatch

> **Nur Vollversion** - diese Anleitung ist in der über Google Play vertriebenen Version nicht umgesetzt. Sie gilt für die Vollversion, einen direkten APK-Download von [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="de" %}

FastMediaSorter unter Wear OS verbindet sich direkt über Wi-Fi mit Ihrem Heimnetzwerkspeicher (NAS, PC-Freigabeordner, FTP- oder SFTP-Server). Sie können auf entfernte Dateien zugreifen, Musik an Bluetooth-Kopfhörer streamen und Ihre bevorzugten Ordner synchronisieren, ohne Ihr Handy zu benötigen.

> **Neu bei Netzwerkfreigaben?** Wenn Sie noch keinen freigegebenen Ordner auf Ihrem PC oder NAS eingerichtet haben, beginnen Sie zuerst mit unserer Anleitung [Mit NAS / Windows-Freigabe verbinden (SMB)](scenario-smb-setup-de.md).

---

## Was Sie benötigen

- Eine Smartwatch mit **Wear OS 2.0** oder neuer, verbunden mit Ihrem Heim-Wi-Fi-Netzwerk
- Einen freigegebenen Netzwerkordner (SMB-/Windows-Freigabe, FTP-Server oder SFTP-Server)
- Netzwerkzugangsdaten: IP-Adresse oder Hostname, Freigabename, Benutzername und Passwort
- FastMedia Wear auf Ihrer Uhr installiert

---

## Schritt 1 - Ressourcen auf Ihrer Uhr öffnen

1. Öffnen Sie **FastMedia Wear** auf Ihrer Smartwatch.
2. Tippen Sie auf dem Hauptbildschirm auf **Ressourcen** (Wi-Fi-Symbol).
3. Der Ressourcen-Bildschirm zeigt Ihre konfigurierten Netzwerkverbindungen an.

![Ressourcen-Bildschirm unter Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Verknüpfung „Vom Handy synchronisieren“:** Wenn Sie Ihre SMB- oder SFTP-Freigaben bereits in FastMediaSorter auf Ihrem Android-Handy hinzugefügt haben, tippen Sie auf **Vom Handy synchronisieren**, um alle Verbindungseinstellungen mit einem Fingertipp auf Ihre Uhr zu importieren.

---

## Schritt 2 - Eine Netzwerkquelle hinzufügen

1. Tippen Sie auf dem Ressourcen-Bildschirm auf **Ressource hinzufügen**.
2. Wählen Sie Ihr Netzwerkprotokoll:
   - **SMB**: Standard-Windows-Freigaben, Synology, QNAP oder TrueNAS
   - **FTP**: Standard-FTP-Dateiserver
   - **SFTP**: sichere SSH-Dateiübertragungsserver (unterstützt Passwort oder privaten SSH-Schlüssel)
3. Tippen Sie auf jedes Feld, um die Verbindungsdetails über die Bildschirmtastatur der Uhr einzugeben:
   - **Name**: optionale Bezeichnung (z. B. „Heim-NAS“ oder „Musikfreigabe“)
   - **Serveradresse**: die IP Ihres Computers oder NAS (z. B. `192.168.1.50`)
   - **Port**: Netzwerkport (Standard: 445 für SMB, 21 für FTP, 22 für SFTP)
   - **Freigabename** (nur SMB): der Name des freigegebenen Ordners auf Ihrem NAS/PC
   - **Benutzername** und **Passwort**: Ihre Anmeldedaten

![Bildschirm „Netzwerkquelle hinzufügen“ auf der Uhr](screenshots/screenshot-wear-network-step2.png)

---

## Schritt 3 - Die Verbindung testen und speichern

1. Scrollen Sie zum unteren Rand des Formulars und tippen Sie auf **Testen**.
2. FastMedia Wear überprüft den Netzwerkweg und die Zugangsdaten:
   - Bei Erfolg zeigt der Bildschirm **Verbindung erfolgreich!** an.
   - Bei einem Problem zeigt eine freundliche Statusmeldung an, was anzupassen ist (z. B. Serveradresse oder Passwort).
3. Tippen Sie auf **Speichern**, um die Netzwerkquelle auf Ihrer Uhr zu speichern.

![Netzwerkverbindung testen und Quelle speichern](screenshots/screenshot-wear-network-step3.png)

---

## Schritt 4 - Netzwerkmedien durchsuchen und abspielen

1. Tippen Sie auf dem Ressourcen-Bildschirm auf Ihre neu gespeicherte Netzwerkfreigabe.
2. FastMedia Wear verbindet sich mit der entfernten Freigabe und listet ihren Inhalt auf.
3. Durchsuchen Sie Ordner und Dateien in der Listen- oder Rasteransicht.
4. Tippen Sie auf einen beliebigen Audiotitel, um die Wiedergabe im Vollbildplayer zu starten. Für ausführliche Player-Funktionen und Akku-Einsparung siehe [Musik auf Ihrer Uhr hören](scenario-watch-music-de.md).

![Dateien und Ordner auf einer Netzwerkfreigabe durchsuchen](screenshots/screenshot-wear-network-step4.png)

---

## Fertig! Netzwerkfunktionen unter Wear OS

- **Unabhängiges Wi-Fi-Streaming**: Streamt direkt von Ihrem NAS oder PC über Wi-Fi, ohne Handy-Relais.
- **Unterstützung mehrerer Protokolle**: Volle Unterstützung für SMB, FTP und SFTP mit Passwort- oder SSH-Schlüssel-Authentifizierung.
- **Bidirektionale Synchronisation**: Synchronisieren Sie Verbindungen von Ihrem Handy-Begleiter oder exportieren Sie Uhr-Quellen zurück zum Handy.

---

## Problembehandlung

| Problem | Was zu versuchen ist |
|---------|------------|
| Der Verbindungstest meldet „Verbindung fehlgeschlagen“ | Prüfen Sie, ob Ihre Uhr mit demselben Wi-Fi-Netzwerk wie der Server verbunden ist, und überprüfen Sie die IP-Adresse |
| Fehler beim Freigabenamen bei SMB | Geben Sie nur den Freigabenamen ein (z. B. `Music`), nicht den vollständigen Pfad mit Schrägstrichen |
| Authentifizierung fehlgeschlagen | Prüfen Sie Ihren Benutzernamen und Ihr Passwort. Stellen Sie bei Windows-Freigaben sicher, dass die Netzwerkfreigabeberechtigungen Ihr Benutzerkonto zulassen |
| Langsames Laden über Wi-Fi | Stellen Sie sicher, dass das Wi-Fi-Signal der Uhr stark ist und die 5-GHz-/2,4-GHz-Netzwerkweiterleitung zum lokalen Server nicht blockiert ist |

</div>
