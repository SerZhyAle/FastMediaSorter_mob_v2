---
layout: default
title: "Se connecter à un NAS / partage Windows (SMB) - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🖥️ Se connecter à un NAS domestique / partage Windows (SMB)

> **Niveau :** Débutant &bull; **Édition :** Standard, Photos, Legacy, VR, noLegal (Lite n'a pas de sources réseau)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="fr" %}

Le SMB (aussi appelé partage de fichiers Windows ou CIFS) vous permet de parcourir les fichiers de votre PC, portable ou appareil NAS à la maison exactement comme s'ils étaient sur votre téléphone - sans câbles, sans USB, juste le Wi-Fi.

> **Explication en langage simple :** imaginez que votre PC ait un tableau d'affichage public sur votre Wi-Fi domestique. N'importe quel appareil de la maison peut lire ce tableau. FastMediaSorter se connecte à ce "tableau" (votre dossier partagé) et vous permet de parcourir vos fichiers comme s'ils étaient stockés directement sur votre téléphone. Rien n'est copié ni téléchargé au préalable - les fichiers s'ouvrent à la demande.

---

## Ce dont vous aurez besoin

- Votre téléphone et votre PC / NAS sur le **même réseau Wi-Fi** (même routeur)
- L'**adresse IP** de votre PC ou NAS (par ex. `192.168.1.100`)
- Le **nom du partage** (le nom du dossier que vous avez partagé, par ex. `Photos`)
- Un **nom d'utilisateur et un mot de passe** pour ce partage (ou un accès invité si activé)

> **Vous n'êtes pas sûr des termes ?** Ne vous inquiétez pas - les étapes 1 et 2 expliquent exactement où trouver ces informations.

---

## Étape 1 - Trouver l'adresse IP de votre PC

L'adresse IP est "l'adresse personnelle" de votre PC sur votre réseau Wi-Fi. Vous en avez besoin pour que votre téléphone sache où chercher.

Sous **Windows :**
1. Appuyez sur `Win + R`, tapez `cmd`, appuyez sur Entrée - une fenêtre de texte noire s'ouvre
2. Tapez `ipconfig` et appuyez sur Entrée
3. Cherchez **Adresse IPv4** sous votre adaptateur Wi-Fi - quelque chose comme `192.168.1.100`

> La ligne dont vous avez besoin est intitulée **"Adresse IPv4"** (pas IPv6, qui ressemble à une longue série de lettres et de chiffres). Elle doit commencer par `192.168.` sur la plupart des réseaux domestiques.

Sur un **NAS** (Synology, QNAP, etc.) :
- Ouvrez le panneau web du NAS → Paramètres réseau - l'IP y est affichée

> Notez l'IP - vous en aurez besoin à l'étape 6.

![Windows PowerShell - ipconfig output, IPv4 Address `192.168.1.100` visible](screenshots/screenshot-smb-step1.png)

---

## Étape 2 - Trouver le nom du partage sur votre PC

Le "nom du partage" est le nom public de votre dossier sur le réseau. Il peut être identique au nom du dossier, ou différent.

Sous **Windows :**
1. Ouvrez l'**Explorateur de fichiers**
2. Clic droit sur le dossier que vous voulez partager → **Propriétés**
3. Allez dans l'onglet **Partage**
4. Regardez le **Chemin réseau** - il ressemble à `\\DESKTOP-ABC\Photos`
5. La partie après le dernier `\` est votre **nom de partage** (ici : `Photos`)

> **Le dossier n'est pas encore partagé ?** Cliquez sur **Partager..** → choisissez **Tout le monde** → **Ajouter** → **Partager**. Windows vous montrera immédiatement le chemin réseau.

> **Important :** assurez-vous que la **découverte réseau** et le **partage de fichiers** sont activés dans Windows. Allez dans Panneau de configuration → Centre Réseau et partage → Modifier les paramètres de partage avancés → activez "Découverte réseau" et "Partage de fichiers et d'imprimantes".

![Windows folder Properties - Sharing tab, Network Path `\\MARK\Common` visible](screenshots/screenshot-smb-step2.png)

---

## Étape 3 - Ouvrir FastMediaSorter et appuyer sur "+"

1. Ouvrez l'application
2. Sur l'**écran principal**, appuyez sur le bouton **"Ajouter" <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils supérieure

![FastMediaSorter main screen - Add button highlighted in top toolbar, SMB tab visible](screenshots/screenshot-smb-step3.png)

---

## Étape 4 - Sélectionner "Dossier réseau (SMB)"

Dans la liste des types de ressource, appuyez sur **"Dossier réseau (SMB)"** (ou l'onglet SMB).

![Select Folder Type dialog - four options: Local Folder, Network Folder (SMB), SFTP/FTP, Cloud Storage](screenshots/screenshot-smb-step4.png)

---

## Étape 5 - Essayer d'abord la découverte automatique

Appuyez sur le bouton **"Analyser le réseau"**. L'application recherchera les appareils avec des partages SMB sur votre Wi-Fi local.

- Attendez ~10 secondes
- Une liste des appareils trouvés apparaît
- Appuyez sur votre PC ou NAS - l'adresse IP se remplit automatiquement

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **Rien trouvé ?** Ce n'est pas grave - passez à l'étape 6 et saisissez l'IP manuellement. Cela arrive quand votre routeur utilise l'isolation AP (un paramètre qui bloque la communication téléphone-PC pour la sécurité). L'IP manuelle fonctionne toujours.

---

## Étape 6 - Remplir les détails de connexion

Remplissez le formulaire :

| Champ | Que saisir | Exemple |
|-------|--------------|---------|
| **Serveur / Chemin** | `\\IP\NomDuPartage` | `\\192.168.1.100\Photos` |
| **Nom d'utilisateur** | Votre nom de connexion Windows | `john` |
| **Mot de passe** | Votre mot de passe Windows | `••••` |
| **Nom d'affichage** | N'importe quel nom de votre choix (optionnel) | `PC maison - Photos` |

> **Vous utilisez un compte Microsoft (e-mail) pour vous connecter à Windows ?** Utilisez votre **adresse e-mail complète** comme nom d'utilisateur (par ex. `john@outlook.com`), pas seulement votre prénom. Votre mot de passe est le même que celui que vous tapez pour déverrouiller votre PC.

> **Vous n'avez pas de mot de passe, ou utilisez Invité ?** Essayez de laisser le nom d'utilisateur et le mot de passe vides et appuyez sur Tester la connexion - certains PC domestiques autorisent un accès ouvert.

![Add Network Folder (SMB) - Server IP `192.168.1.100`, ShareName and credentials filled in](screenshots/screenshot-smb-step6.png)

![Add Network Folder (SMB) - lower section: options, media types, ADD THIS RESOURCE button](screenshots/screenshot-smb-step6b.png)

**Référence des formats d'adresse :**

| Format | Exemple |
|--------|---------|
| Windows standard | `\\192.168.1.100\Photos` |
| Style Linux / macOS | `smb://192.168.1.100/Photos` |
| Sous-dossier | `\\192.168.1.100\Media\Movies` |
| Port personnalisé | `smb://192.168.1.100:445/Photos` |

---

## Étape 7 - Tester la connexion

Appuyez sur **"Tester la connexion"**.

- **Message vert** = succès → passez à l'étape 8 ! Vous avez presque terminé.
- **Message rouge** = quelque chose ne va pas → consultez le tableau de dépannage ci-dessous. Correction la plus courante : revérifiez l'IP et le nom du partage.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## Étape 8 - Enregistrer et ouvrir

Appuyez sur **"Enregistrer"**. Le nouveau dossier apparaît sur l'écran principal avec un badge SMB.

Appuyez dessus pour parcourir son contenu - photos, vidéos et autres fichiers apparaissent sous forme de miniatures comme n'importe quel dossier local.

![FastMediaSorter main screen - new "Common" SMB resource card (smb://192.168.1.100/Common) with Network folder SMB badge highlighted](screenshots/screenshot-smb-step8.png)

---

## Terminé ! Vous pouvez maintenant..

- Parcourir tous les fichiers de votre PC depuis votre téléphone
- Lire des vidéos et de la musique directement - aucun téléchargement requis
- Copier ou déplacer des fichiers entre votre téléphone et votre PC
- Utiliser ce dossier comme source pour le diaporama, le cadre photo, ou la musique embarquée

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| "Connexion refusée" | Ouvrez le pare-feu Windows → autorisez le **port TCP 445** entrant. Ou désactivez temporairement le pare-feu pour tester |
| "Mot de passe incorrect" | Essayez de laisser le **nom d'utilisateur vide** (accès invité). Ou si vous utilisez un compte Microsoft, saisissez votre **e-mail complet** comme nom d'utilisateur |
| "Hôte introuvable" | Assurez-vous que le téléphone et le PC sont sur le **même Wi-Fi** et le même routeur. L'isolation AP (un paramètre de sécurité du routeur) peut bloquer cela - essayez de la désactiver dans les paramètres du routeur |
| L'analyse ne trouve rien | Désactivez le VPN sur le téléphone. Vérifiez aussi que la **découverte réseau** est activée dans Windows (Panneau de configuration → Centre Réseau et partage). Puis essayez de saisir l'IP manuellement |
| Navigation très lente | Appuyez sur **Modifier** sur la ressource → lancez le **test de vitesse** pour voir le débit réel. Désactivez les miniatures vidéo pour les connexions lentes |
| Fonctionne en Wi-Fi mais pas en données mobiles | Attendu - le SMB est un protocole réseau local uniquement. Il ne peut pas fonctionner via les données mobiles |

→ Plus d'aide : [TROUBLESHOOTING.md](../TROUBLESHOOTING-fr.md)

</div>
