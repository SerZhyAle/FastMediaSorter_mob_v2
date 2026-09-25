---
layout: default
title: "Connecter la montre connectée aux partages NAS et PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Connecter la montre connectée aux partages NAS et PC

> **Niveau :** Intermédiaire &bull; **Durée :** ~10 minutes &bull; **Appareil :** Montre connectée Wear OS

> **Version complète uniquement** - ce guide n'est pas implémenté dans la version distribuée via Google Play. Il s'applique à la version complète, un téléchargement direct de l'APK depuis [Téléchargements](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="fr" %}

FastMediaSorter sur Wear OS se connecte directement au stockage de votre réseau domestique (NAS, dossiers partagés PC, serveurs FTP ou SFTP) via le Wi-Fi. Vous pouvez parcourir des fichiers distants, diffuser de la musique vers des écouteurs Bluetooth, et synchroniser vos dossiers favoris sans avoir besoin de votre téléphone.

> **Nouveau dans les partages réseau ?** Si vous n'avez pas encore configuré de dossier partagé sur votre PC ou NAS, commencez d'abord par notre guide [Se connecter à un NAS / partage Windows (SMB)](scenario-smb-setup-fr.md).

---

## Ce dont vous aurez besoin

- Une montre connectée fonctionnant sous **Wear OS 2.0** ou plus récent, connectée à votre réseau Wi-Fi domestique
- Un dossier réseau partagé (partage SMB / Windows, serveur FTP, ou serveur SFTP)
- Des identifiants réseau : adresse IP ou nom d'hôte, nom du partage, nom d'utilisateur et mot de passe
- FastMedia Wear installé sur votre montre

---

## Étape 1 - Ouvrir les ressources sur votre montre

1. Ouvrez **FastMedia Wear** sur votre montre connectée.
2. Sur l'écran principal, appuyez sur **Ressources** (icône Wi-Fi).
3. L'écran Ressources affiche vos connexions réseau configurées.

![Resources screen on Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Raccourci Synchroniser depuis le téléphone :** si vous avez déjà ajouté vos partages SMB ou SFTP dans FastMediaSorter sur votre téléphone Android, appuyez sur **Synchroniser depuis le téléphone** pour importer tous les paramètres de connexion vers votre montre en un geste.

---

## Étape 2 - Ajouter une source réseau

1. Sur l'écran Ressources, appuyez sur **Ajouter une ressource**.
2. Sélectionnez votre protocole réseau :
   - **SMB** : partages Windows standard, Synology, QNAP, ou TrueNAS
   - **FTP** : serveurs de fichiers FTP standard
   - **SFTP** : serveurs de transfert de fichiers SSH sécurisés (prend en charge le mot de passe ou une clé SSH privée)
3. Appuyez sur chaque champ pour saisir les détails de connexion à l'aide du clavier à l'écran de la montre :
   - **Nom** : libellé optionnel (par ex. "NAS maison" ou "Partage musique")
   - **Adresse du serveur** : l'IP de votre ordinateur ou NAS (par ex. `192.168.1.50`)
   - **Port** : port réseau (par défaut : 445 pour SMB, 21 pour FTP, 22 pour SFTP)
   - **Nom du partage** (SMB uniquement) : le nom du dossier partagé sur votre NAS/PC
   - **Nom d'utilisateur** et **Mot de passe** : vos identifiants de connexion

![Add Network Source screen on watch](screenshots/screenshot-wear-network-step2.png)

---

## Étape 3 - Tester et enregistrer la connexion

1. Faites défiler jusqu'en bas du formulaire et appuyez sur **Tester**.
2. FastMedia Wear vérifie la route réseau et les identifiants :
   - En cas de succès, l'écran affiche **Connexion réussie !**.
   - S'il y a un problème, un message de statut convivial indique quoi ajuster (par ex. adresse du serveur ou mot de passe).
3. Appuyez sur **Enregistrer** pour stocker la source réseau sur votre montre.

![Test network connection and save source](screenshots/screenshot-wear-network-step3.png)

---

## Étape 4 - Parcourir et lire les médias réseau

1. Sur l'écran Ressources, appuyez sur votre partage réseau nouvellement enregistré.
2. FastMedia Wear se connecte au partage distant et liste son contenu.
3. Parcourez les dossiers et fichiers en vue liste ou grille.
4. Appuyez sur n'importe quelle piste audio pour démarrer la lecture dans le lecteur plein écran. Pour les fonctionnalités détaillées du lecteur et l'économie de batterie, voir [Écouter de la musique sur votre montre](scenario-watch-music-fr.md).

![Browse files and folders on network share](screenshots/screenshot-wear-network-step4.png)

---

## Terminé ! Fonctionnalités réseau sur Wear OS

- **Streaming Wi-Fi indépendant** : diffuse directement depuis votre NAS ou PC via le Wi-Fi sans relais du téléphone.
- **Prise en charge multi-protocole** : prise en charge complète de SMB, FTP et SFTP avec authentification par mot de passe ou clé SSH privée.
- **Synchronisation bidirectionnelle** : synchronisez les connexions depuis votre compagnon téléphone ou exportez les sources de la montre vers le téléphone.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| Le test de connexion indique "Échec de la connexion" | Vérifiez que votre montre est connectée au même réseau Wi-Fi que le serveur, et vérifiez l'adresse IP |
| Erreur de nom de partage sur SMB | Assurez-vous de ne saisir que le nom du partage (par ex. `Music`), pas le chemin complet avec des barres obliques |
| Échec de l'authentification | Vérifiez votre nom d'utilisateur et votre mot de passe. Sur les partages Windows, assurez-vous que les autorisations de partage réseau permettent votre compte utilisateur |
| Chargement lent via Wi-Fi | Assurez-vous que le signal Wi-Fi de la montre est fort et que le routage réseau 5 GHz / 2,4 GHz vers le serveur local n'est pas bloqué |

</div>
