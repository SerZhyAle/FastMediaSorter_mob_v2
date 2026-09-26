---
layout: default
title: "Sauvegarde photo programmée vers le PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-camera-backup-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 📷 Sauvegarde photo programmée vers le PC

> **Niveau :** Débutant &bull; **Durée :** ~15 minutes de configuration &bull; **Édition :** Standard, Photos, Legacy, VR, noLegal (nécessite des sources réseau - Lite n'en a pas)

{% include lang-switcher.html doc="scenario-camera-backup" dir="/docs/howto/" current="fr" %}

Copiez automatiquement les nouvelles photos de l'appareil photo de votre téléphone vers votre ordinateur **chaque nuit, via le Wi-Fi**. Configurez-le une fois - il fonctionne pour toujours sans aucune action manuelle.

**Ce que cela vous apporte :** chaque matin vous vous réveillez et les photos de la nuit précédente sont déjà sur votre PC. Pas de câbles. Pas d'abonnement cloud. Pas d'oubli. Entièrement automatique.

---

## Ce dont vous aurez besoin

- Le téléphone et le PC connectés au **même réseau Wi-Fi domestique** (même routeur)
- Un dossier sur votre PC où les photos seront enregistrées (par ex. `C:\PhoneBackup`)
- FastMediaSorter installé dans une édition avec sources réseau (**Standard**, Photos, Legacy, VR, noLegal)

> **Vous ne savez pas quelle édition vous avez ?** Ouvrez **Paramètres** <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> → **Informations système**. La ligne **Édition** indique l'édition (Standard / Lite / etc.).

---

## Étape 1 - Créer un dossier de sauvegarde sur votre PC

D'abord, créez un dossier sur votre PC. Puis partagez-le pour que le téléphone puisse y accéder.

Sous **Windows :**
1. Créez un nouveau dossier n'importe où - par exemple `C:\PhoneBackup`
2. **Clic droit** sur le dossier → **Propriétés** → onglet **Partage** → cliquez sur **Partager..**
3. Dans le menu déroulant, sélectionnez votre nom d'utilisateur ou tapez **Tout le monde** → cliquez sur **Ajouter** → cliquez sur **Partager**
4. Windows affiche le chemin réseau - notez-le. Il ressemble à : `\\MYPC\PhoneBackup`

> **Notez aussi l'adresse IP de votre PC** - vous en aurez besoin à l'étape 2. Le moyen le plus rapide : appuyez sur **Win + R**, tapez `cmd`, appuyez sur Entrée. Dans la fenêtre noire, tapez `ipconfig` et appuyez sur Entrée. Trouvez la ligne **Adresse IPv4** sous votre adaptateur Wi-Fi. Exemple : `192.168.1.100`. Notez ce numéro.

---

## Étape 2 - Connecter l'application à votre dossier PC

Maintenant, indiquez à FastMediaSorter où envoyer les photos.

> **Qu'est-ce que le SMB ?** C'est simplement la façon dont Windows partage des dossiers sur le Wi-Fi domestique. Vous n'avez pas besoin de comprendre les détails - suivez simplement les étapes.

1. Ouvrez l'application → appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils supérieure → sélectionnez **"Dossier réseau (SMB)"**
2. Dans le champ **Serveur / Chemin**, saisissez : `\\192.168.1.100\PhoneBackup`
   - Remplacez `192.168.1.100` par l'IP réelle de votre PC obtenue à l'étape 1
   - Remplacez `PhoneBackup` par le nom réel de votre dossier
3. Saisissez votre **nom d'utilisateur** et votre **mot de passe** Windows (les mêmes que ceux utilisés pour vous connecter à votre PC)
4. Appuyez sur **Tester la connexion** - attendez quelques secondes - vous devriez voir un message de succès en vert
5. Appuyez sur **Enregistrer**

> **Impossible de se connecter ?** Consultez le [Guide de configuration SMB](scenario-smb-setup-fr.md) - il couvre tous les problèmes de connexion courants avec des solutions étape par étape.


---

## Étape 3 - Ouvrir les paramètres des opérations programmées

1. Appuyez sur **Paramètres** (l'icône d'engrenage <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> dans la barre d'outils)
2. Allez dans l'onglet **Gestion**
3. Faites défiler jusqu'à **"Opérations programmées par planification"** et appuyez sur **"Opérations de fichiers programmées"** - l'écran des opérations programmées s'ouvre
4. Activez **"Utiliser les opérations programmées"** en haut de cet écran

![Settings → Management - Scheduled section with ADD button](screenshots/screenshot-cb-step3.png)

---

## Étape 4 - Créer une nouvelle planification

Appuyez sur le bouton **+** (**"Ajouter"**) sur l'écran des opérations programmées.

Une boîte de dialogue de nouvelle planification s'ouvre.

![Add Schedule dialog - Conditions section: interval and overwrite options](screenshots/screenshot-cb-step4.png)

---

## Étape 5 - Remplir la planification de sauvegarde

Remplissez chaque champ :

| Champ | Que définir | Exemple |
|-------|------------|---------|
| **Nom** | Un libellé quelconque pour reconnaître cette planification | `Sauvegarde nocturne caméra` |
| **Source** | Où se trouvent les photos de votre appareil photo | Sélectionnez **"Photos de l'appareil photo"** - trouve automatiquement toutes les photos prises |
| **Destination** | Votre dossier de sauvegarde PC | Sélectionnez la ressource SMB que vous venez d'ajouter (`PhoneBackup (SMB)`) |
| **Opération** | Que faire des fichiers | **"Copier (ignorer les existants)"** - copie uniquement les nouvelles photos, jamais de doublons |
| **Planification** | Quand exécuter | `Chaque jour à 02:00` - s'exécute pendant que vous dormez |
| **Exécuter en Wi-Fi uniquement** | Activez ceci | Empêche la sauvegarde d'utiliser accidentellement vos données mobiles |

> **Qu'est-ce que "Photos de l'appareil photo" ?** C'est un dossier virtuel spécial que FastMediaSorter crée automatiquement. Il affiche toujours toutes les photos prises par votre appareil photo - même si elles sont stockées dans différents dossiers sur votre téléphone. Préférez toujours cette option à un chemin manuel.

![Add Schedule - Source: Camera Photos, Operation: Copy, Destination: SMB](screenshots/screenshot-cb-step5.png)

---

## Étape 6 - Enregistrer et autoriser l'accès en arrière-plan

Appuyez sur **Enregistrer**.

La planification apparaît dans la liste - elle est maintenant active.

**Vous pourriez voir une boîte de dialogue d'autorisation.** L'application demande à être exclue de l'économie de batterie. Appuyez sur **"Désactiver l'optimisation"** (ou **"Autoriser"**).

> **Pourquoi cette étape est-elle importante ?** Android essaie d'économiser la batterie en arrêtant automatiquement les applications qui s'exécutent en arrière-plan. Sans cette autorisation, Android pourrait arrêter la sauvegarde en pleine nuit. Accorder ceci permet simplement à l'application de se réveiller à l'heure programmée - cela ne draine pas votre batterie de manière perceptible.

![Saved schedule entry: Camera Photos → SMB at scheduled time](screenshots/screenshot-cb-step6.png)

---

## Étape 7 - Tester tout de suite

N'attendez pas 2 heures du matin - testez la sauvegarde immédiatement pour vous assurer que tout fonctionne :

1. Allez dans **Paramètres → Gestion → Opérations de fichiers programmées**
2. Trouvez votre planification → appuyez sur **"Exécuter maintenant"**
3. Une notification apparaît en haut de votre écran montrant la progression du transfert
4. Une fois terminé : appuyez sur la ressource SMB (`PhoneBackup`) → vos photos de l'appareil photo devraient y être visibles

> **Rien n'a été copié ?** Si toutes vos photos sont déjà dans le dossier de sauvegarde (ou si le dossier de l'appareil photo du téléphone est vide), l'application copie correctement zéro fichier. Essayez de prendre une nouvelle photo de test et relancez.


---

## Terminé ! Voici ce qui se passe chaque nuit

1. À 02:00 l'application se réveille silencieusement
2. Elle examine votre dossier de l'appareil photo et le compare avec le dossier de sauvegarde du PC
3. Copie uniquement les photos qui ne sont pas encore sur le PC - prend de quelques secondes à quelques minutes
4. Affiche une notification : "12 fichiers sauvegardés" (ou le nombre de nouveaux fichiers)
5. Se rendort

Votre PC reçoit de nouvelles photos chaque matin. Vous n'avez jamais à y penser.

---

## Astuces

> **Vous voulez libérer de l'espace de stockage sur le téléphone après la sauvegarde ?** Changez l'opération en **"Déplacer"** au lieu de "Copier". Les photos sont supprimées du téléphone juste après avoir été copiées en toute sécurité sur le PC. Utilisez ceci avec précaution - une fois déplacées, les photos ne sont plus sur le téléphone.

> **Plusieurs téléphones dans la famille ?** Créez une planification par téléphone. Utilisez des sous-dossiers différents comme destinations - par exemple `PhoneBackup\Maman` et `PhoneBackup\Papa` - afin que tous les appareils sauvegardent vers le même PC sans mélanger les fichiers.

> **Vous préférez sauvegarder vers Google Drive ?** Ajoutez une ressource Google Drive comme destination à la place de SMB - le reste des étapes est identique.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| La planification ne s'exécute pas la nuit | Allez dans **Paramètres Android → Applications → FastMediaSorter → Batterie** → réglez sur **Illimité** |
| Erreur "Destination inaccessible" | Votre téléphone doit être sur le Wi-Fi au moment de la sauvegarde. Si le Wi-Fi était désactivé à 2h du matin, la sauvegarde est ignorée et réessayée automatiquement la nuit suivante |
| Certaines photos n'ont pas été sauvegardées | Utilisez la ressource virtuelle **"Photos de l'appareil photo"** comme source - elle capture les photos de tous les dossiers de l'appareil photo sur votre téléphone |
| Des fichiers en double apparaissent sur le PC | Assurez-vous que l'opération est réglée sur **"Copier (ignorer les existants)"**, pas "Copier (écraser)" |

</div>
