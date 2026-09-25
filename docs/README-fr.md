---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

{% include lang-switcher.html doc="README" dir="/docs/" current="fr" %}

**📦 Télécharger :** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

Vous installez l'APK directement ? Android affiche un avertissement pour un paquet qu'il n'a encore jamais vu - [pourquoi cet avertissement apparaît, et sur quoi appuyer](INSTALL_TRUST.md).

**📘 Documentation utilisateur :** [guides pas à pas pour chaque fonctionnalité, avec recherche](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## À propos du projet

**FastMediaSorter v2** est un shell complet pour un appareil Android. Il prend le contrôle de l'écran d'accueil, lit vos médias, ouvre des flux en direct, lance vos applications, communique avec votre montre, surveille l'appareil et gère tous les fichiers que vous possédez - dans des dossiers locaux, sur des lecteurs réseau (SMB, SFTP, FTP) et dans le stockage cloud (Google Drive, OneDrive, Dropbox).

Il repose sur huit piliers : shell d'appareil, lecteur multimédia, flux en direct, lancement d'applications, remplacement des applications système, compagnon sur la montre, surveillance de l'appareil et gestionnaire de fichiers complet. Le tri des fichiers entre toutes ces sources est le point de départ de l'application, et reste le socle sur lequel tout le reste est construit - mais ce n'en est plus la totalité.

Ce manuel suit désormais le même vocabulaire public que l'inventaire canonique des fonctionnalités dans [FEATURES.md](FEATURES.md) et le plan des documents dans [DOCS_MAP.md](DOCS_MAP.md). Utilisez ces deux pages comme source de vérité actuelle pour l'histoire de l'application, les éditions disponibles et le périmètre actuel des fonctionnalités.

## Version Windows 🖥️

Vous cherchez une solution de bureau ? Découvrez **Fast Media Sorter for Windows** (anciennement FastMediaSorter LITE) - une application Windows Forms légère pour trier, visualiser et gérer rapidement des fichiers image et vidéo :

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [Comment publier des dossiers PC vers Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - partagez vos dossiers PC avec l'application via SFTP (import companion / scan QR sur le téléphone).

Fonctionnalités incluses :

- Navigation rapide dans de grands dossiers d'images et de vidéos
- Modes diaporama et lecture aléatoire des fichiers
- Suivi des fichiers et dossiers récents
- Opérations sur les fichiers : déplacer, copier, renommer et supprimer
- Panneau image pour une navigation visuelle rapide
- Raccourcis clavier personnalisables pour un flux de travail efficace
- Prise en charge multilingue (anglais/russe)
- Compatible Windows 7/10/11 avec .NET Framework 4.8

## Table des matières

- [Télécharger](#download-)
- [Éditions](#editions-)
- [Fonctionnalités clés](#key-features)
- [Formats multimédias pris en charge](#supported-media-formats-)
- [Captures d'écran](#screenshots-)
- [Scénarios d'utilisation](#usage-scenarios-)
- [Documentation](#documentation-)
- [Compagnon Wear OS](#wear-os-companion-)
- [Instructions de compilation](#build-instructions)
- [Tests](#testing-)
- [Premiers pas](#first-steps-quick-usage-guide-)
- [Stack technologique](#technology-stack)

## Éditions 🎯 {#editions-}

FastMediaSorter v2 est proposé en **sept éditions** - cinq pour les téléphones et tablettes du quotidien (Standard, Lite, Photos, Legacy, FOSS) plus deux versions pour casque et installation manuelle, VR et noLegal. La grille de capacités canonique est générée depuis le build dans [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) :

| Édition | Description | Remarques |
|--------|-------------|-------|
| **Standard** | Version complète | Le plus large éventail de fonctionnalités pour les médias, documents, OCR, traduction et accès cloud |
| **Lite** | Version légère | Fichiers locaux uniquement - vidéo, audio et images ; pas de sources réseau, de cloud, de documents ni de Flux |
| **Photos** | Version axée photo | Images uniquement, avec SMB/FTP/SFTP et le cloud ; pas de vidéo ni d'audio |
| **Legacy** | Version orientée compatibilité | Mêmes fonctionnalités que Standard, y compris SMB/FTP/SFTP et le cloud (Google Drive, Dropbox, OneDrive) ; conçue pour Android 6/7 (API 23+) |
| **FOSS** | Version pour le catalogue F-Droid | Aucun SDK propriétaire : médias locaux, documents, EPUB et SMB/FTP/SFTP ; pas de cloud, pas de Flux, pas d'OCR, pas de traduction, pas de Cast et pas de compagnon Wear OS |
| **VR** | Version casque sans dépendance store | Ensemble multimédia complet pour casques ; pas de Google Cast ni de compagnon Wear OS |
| **noLegal** | Version à installation manuelle | Tout ce que contient Standard, plus le lecteur immersif OpenXR et des extras réservés à l'installation manuelle |

### Quelle édition télécharger ?

- **Standard** ⭐ **(Recommandée)** : le meilleur choix par défaut pour la plupart des utilisateurs
- **Lite** : à privilégier si vous voulez un paquet plus léger et une configuration plus simple
- **Photos** : à privilégier pour les usages centrés sur la photo
- **Legacy** : à choisir pour les appareils Android 6/7 (API 23+) - inclut le réseau et le cloud
- **FOSS** : à choisir dans le catalogue F-Droid si vous voulez un build sans SDK propriétaire
- **VR** : à choisir pour un casque XR - le build store sans Cast ni prise en charge Wear
- **noLegal** : installation manuelle uniquement - à choisir si vous avez besoin du lecteur immersif OpenXR

Pour la disponibilité exacte des fonctionnalités par édition, utilisez la documentation canonique :

- [Inventaire des fonctionnalités (canonique)](FEATURES.md)
- [How-To (tableau de disponibilité des fonctionnalités)](HOW_TO-fr.md)
- [Guide de démarrage rapide (choix de l'édition)](QUICK_START-fr.md)
- [Limitations du programme](LIMITATIONS.md)

> 🧭 **Premier lancement :** juste sous le sélecteur de langue, l'application vous permet de choisir un **profil d'appareil** (téléphone, tablette, TV, voiture, cadre photo, VR, et plus encore) qui adapte les réglages de départ pour vous - modifiable à tout moment dans les Paramètres. Voir [Premier lancement : choisissez votre profil d'appareil](QUICK_START-fr.md#first-launch-choose-your-device-profile-30-seconds-).

## Télécharger 📥 {#download-}

📲 **[Obtenir sur Google Play](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**Les fichiers APK compilés ne sont PAS stockés dans ce dépôt GitHub.** Tous les builds sont disponibles sur **Google Drive** :

🔗 **[Télécharger tous les builds depuis Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Édition | Nom de fichier | Description |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Fonctionnalités complètes (Cloud, OCR, EPUB, Traduction) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Médias locaux uniquement (vidéos, audio, images ; pas de réseau, de cloud, de documents ni de Flux) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Images uniquement, avec réseau (SMB/FTP/SFTP) et cloud |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Mêmes fonctionnalités que Standard, y compris réseau (SMB/FTP/SFTP) et cloud ; Android 6/7 (API 23+) |

> **Remarque** : tous les builds sont automatiquement téléversés sur Google Drive après une compilation réussie.
>
> 🔐 **Mot de passe du ZIP : `1`** (les fichiers APK sont regroupés dans des archives ZIP protégées par mot de passe pour contourner les restrictions de Google Drive)

## Captures d'écran 📱 {#screenshots-}

| Écran principal | Actions sur les fichiers | Paramètres |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **Vue du lecteur** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

Images en pleine taille :

- [Écran principal](images/Screenshot_20251109_000251.png)
- [Actions sur les fichiers](images/Screenshot_20251109_000314.png)
- [Paramètres](images/Screenshot_20251109_000323.png)
- [Vue du lecteur](images/Screenshot_20251114_184930.png)

## Fonctionnalités clés {#key-features}

- 🗂️ **Interface unifiée :** consultez et gérez les fichiers de toutes les sources dans une seule fenêtre.
- ⚡ **Tri rapide :** copiez ou déplacez des fichiers vers des dossiers de destination préconfigurés en un clic.
- ⭐ **Système de favoris :** marquez les fichiers importants comme favoris et accédez-y rapidement depuis un onglet dédié qui regroupe les favoris de toutes les sources.
- 🔒 **Protection par code PIN :** sécurisez chaque ressource individuellement avec des codes PIN d'accès pour empêcher la navigation et la modification non autorisées.
- ⚙️ **Configuration par ressource :** personnalisez l'intervalle du diaporama, la profondeur d'analyse (sous-dossiers) et la génération de vignettes pour chaque dossier individuellement.
- 🧭 **Configuration du profil d'appareil :** choisissez un profil de premier démarrage pour téléphones, tablettes, TV/boîtiers multimédias, autoradios, lecteurs multimédias, cadres photo, lecteurs audio, liseuses, casques VR ou réglages personnalisés ; l'application applique les valeurs par défaut correspondantes pour la sécurité, l'écran, le contenu et la priorité des commandes.
- 📋 **Ressources intelligentes prédéfinies :** ressources virtuelles intégrées - **Toute la musique**, **Toutes les vidéos**, **Toutes les photos** - qui regroupent les médias de tout votre appareil sans aucune configuration. Accédez instantanément à toute votre bibliothèque multimédia sans ajouter manuellement chaque dossier.
- 🖥️ **Prise en charge réseau et cloud :** travaillez avec les fichiers de vos lecteurs réseau (SMB avec analyse réseau automatique), serveurs SFTP, FTP, et dans le stockage cloud (Google Drive, Dropbox, OneDrive).
- 🖼️ **Affichage flexible :** affichez les fichiers en grille personnalisable ou en liste détaillée, avec pagination pour les grandes collections (1000+ fichiers).
- ▶️ **Lecteur intégré :** lecture des vidéos et audios, visionnage des images et GIF sans quitter l'application. Prend en charge le diaporama et le zoom plein écran.
- 🧩 **Intégration au lecteur par défaut :** des options de lecture activables permettent à FastMediaSorter d'agir comme gestionnaire multimédia système pour les intentions d'ouverture/partage (ACTION_VIEW / ACTION_SEND), et de router les événements de réveil des boutons multimédias matériels vers le service de lecture audio.
- 🗣️ **AppFunctions Assistant (Android 16+) :** l'application déclare des actions utilisables par l'assistant - rechercher un média, ouvrir un fichier, ou ouvrir un dossier d'ordinateur - afin que l'assistant système de votre appareil puisse trouver et ouvrir votre contenu sur simple demande.
- 🎛️ **Prise en charge des boutons matériels :** les commandes au volant, les boutons du casque et les touches multimédias physiques (Lecture/Pause, Suivant, Précédent) sont entièrement prises en charge via le service audio en arrière-plan - sans interaction avec l'écran.
- 📻 **Flux Internet (écran Flux) :** lisez la radio Internet (http/https, Icecast/Shoutcast avec les métadonnées ICY du morceau en cours), les flux HLS/DASH, et les sources RTSP directement depuis un écran Flux dédié. Ajoutez des URL manuellement, importez une playlist `.m3u`, ou téléchargez un catalogue FastMediaSorter sélectionné. Épinglez vos favoris en haut ; filtrez par catégorie et langue. Audio intégré : la radio joue depuis la liste via un mini-contrôle collant en bas d'écran pendant que la liste reste défilable. La vidéo et le RTSP s'ouvrent dans le lecteur plein écran. Disponible dans Standard, Legacy, VR et noLegal ; absent dans Lite et Photos.
- 🎵 **Prise en charge des paroles :** affichez les paroles de la chanson en cours de lecture. Recherche automatique par métadonnées (Artiste/Titre) via `api.lyrics.ovh`, avec repli sur l'analyse du nom de fichier.
- 🎶 **Musique de fond pour diaporama :** jouez de la musique de fond pendant les diaporamas d'images. Choisissez n'importe quelle ressource audio comme source musicale, avec lecture aléatoire des morceaux, contrôle du volume, et affichage du nom du morceau. Touchez le nom du morceau pour passer à un autre morceau aléatoire. Fonctionne parfaitement avec les fichiers réseau et cloud.
- ✏️ **Édition d'image :** pivotez, retournez, appliquez des filtres (niveaux de gris, sépia, négatif), ajustez la luminosité/contraste/saturation - pour les fichiers locaux comme réseau.
- 🗂️ **Prise en charge des fichiers binaires :** consultez et gérez les fichiers binaires (ZIP, RAR, APK, ISO, EXE, DLL, etc.) avec des vignettes générées montrant l'extension du fichier. Menu contextuel avec Partager/Ouvrir avec/Copier/Déplacer/Renommer/Supprimer. Disponible uniquement en « mode Tous les fichiers ».
- ⌨️ **Clavier, souris et manette :** prise en charge complète du clavier, de la souris et de la manette sur tous les écrans - Parcourir, Lecteur, Paramètres, boîtes de dialogue. Entièrement réattribuable via Paramètres → Gestion → Contrôles et raccourcis clavier ; appuyez sur F1 sur n'importe quel écran pour afficher l'aide propre à cette surface. Navigation en liste par croix directionnelle ; menu contextuel au clic droit et effets de survol pour la souris.
- 🔍 **Tri et filtrage :** classez les fichiers par nom, date, taille et durée. Appliquez des filtres pour une recherche rapide. Prise en charge des fichiers cachés (commençant par `.`) avec un interrupteur dédié.
- ↩️ **Annuler et corbeille :** possibilité d'annuler la dernière action (copie, déplacement, suppression) avec suppression douce vers le dossier `.trash/`. Inclut la fonction « Vider la corbeille » pour les ressources.
- 🎨 **Interface moderne :** prise en charge des thèmes clair et sombre, contrôles intuitifs, Material Design 3.
- 💾 **Mise en cache intelligente :** chargement des métadonnées vidéo en deux étapes (1 Mo initial, 5 Mo étendu) et cache de vignettes configurable (2 Go par défaut, jusqu'à 16 Go).
- 📄 **Visionneuse de documents :** visionneuse intégrée pour les fichiers texte (.txt, .md, .log, .json, .xml) et les documents PDF avec zoom, panoramique et navigation gestuelle.
- 📚 **Liseuse EPUB :** liseuse EPUB native avec navigation par chapitre, table des matières, réglage de la taille de police, recherche dans le livre et prise en charge des thèmes clair/sombre. Fonctionne avec les fichiers locaux et réseau.
- 📥 **Téléchargement et ouverture :** téléchargez les fichiers réseau (SMB/SFTP/FTP) vers le stockage local et ouvrez-les dans des applications externes avec suivi de la progression.
- 🌐 **Traduction automatique :** traduisez instantanément le texte des images, PDF et fichiers texte, entièrement sur l'appareil : **Tesseract** lit le texte en écriture latine et cyrillique, et Google ML Kit le traduit. Prend en charge le mode standard et le **mode superposition façon loupe** pour des traductions en place.
- 📱 **Prise en charge des widgets :** plus d'une douzaine de widgets d'écran d'accueil couvrant un large éventail - raccourcis vers les ressources, lecteurs multimédias, capture photo, calculatrices, tâches planifiées, favoris, mini-jeux, et plus encore. Parcourez la sélection complète dans le sélecteur de widgets de votre launcher.
- 🏠 **Mode écran d'accueil :** laissez l'application devenir l'écran d'accueil de votre appareil (builds Standard et noLegal) : son propre bureau avec des raccourcis de ressources qui ouvrent directement le mode parcourir, diaporama ou lecture, des gadgets redimensionnables comme une horloge et la météo, des cellules de contact ne nécessitant aucune permission de contacts, une grille d'applications, et une barre des tâches. Désactivez-le à tout moment et Android restaure votre écran d'accueil précédent.
- ⏰ **Opérations planifiées sur les fichiers :** automatisez les opérations sur les fichiers (Copier/Déplacer/Supprimer) via des règles basées sur le temps, avec des filtres flexibles et une exécution en arrière-plan.
- 👆 **Gestes avancés :** contrôles de zoom intelligents (2x/3x/4x) pour les images et zones tactiles intuitives pour la navigation entre fichiers.
- 📸 **Enregistrer l'image :** capturez l'image vidéo actuelle sous forme d'instantané PNG ou JPG et enregistrez-la dans n'importe quelle ressource configurée - locale ou réseau. Le format de sortie et la ressource de destination se règlent dans les Paramètres vidéo.
- 🖨️ **Impression :** envoyez des documents (PDF, TXT) et des images vers une imprimante directement depuis le lecteur intégré. Les fichiers réseau et cloud sont mis en cache localement avant l'impression.
- ⬇️ **Déchargement de flux :** téléchargez un fichier réseau vers le cache local avec une boîte de dialogue de progression en temps réel, avant ou pendant la lecture. Une invite de nettoyage facultative permet de récupérer l'espace ensuite.
- 🔊 **Audio DTS/DTS-HD :** les pistes audio DTS et DTS-HD sont décodées par logiciel via un build FFmpeg personnalisé - aucun matériel spécial requis.
- 🎨 **Couleur et luminosité vidéo :** ajustez la teinte et la luminosité en temps réel grâce aux effets GPU de Media3. Les réglages persistent d'un fichier vidéo à l'autre pendant la session.
- 📤 **Partager vers FastMediaSorter :** recevez des fichiers depuis n'importe quelle application via le panneau de partage Android standard et copiez-les vers une ressource choisie d'une seule pression.
- 📷 **Capture photo dans Parcourir :** prenez une photo avec l'appareil photo de l'appareil et enregistrez-la directement dans la ressource en cours - locale ou réseau - sans quitter l'application.
- 🔗 **Téléchargement automatique par lien :** partagez n'importe quelle URL http(s) vers l'application via le panneau de partage Android ; le fichier multimédia est téléchargé et enregistré automatiquement dans une ressource choisie.
- 👁️ **Mode 3D mono-œil :** recadrez le contenu stéréo (SBS/OU) sur un seul œil pour un visionnage confortable sur écran plat ; fonctionne pour la vidéo comme pour les images.
- 📲 **Capture et enregistrement d'écran :** bandeau de gestes sur le bord gauche pour les captures d'écran, photos rapides, recadrage-partage, et enregistrement écran/voix/vidéo sans quitter le fichier en cours.
- 📊 **Statistiques d'utilisation (facultatif) :** tableau de bord local des fichiers triés, de l'espace libéré et du temps de lecture - rien ne quitte l'appareil sauf si vous l'exportez.
- 🧹 **Recherche de doublons et nettoyage par taille :** recherche de doublons basée sur le contenu (taille, hachage rapide, SHA-256) avec suppression manuelle ou automatique, plus un nettoyage par suppression selon la taille.

## Formats multimédias pris en charge 🎞️ {#supported-media-formats-}

FastMediaSorter v2 prend en charge un large éventail de formats :

- **Images :** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF
- **Vidéo :** MP4, MKV, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG
- **Audio :** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, DTS, DTS-HD
- **Documents :** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **Fichiers binaires** (mode Tous les fichiers) : ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO, et plus de 60 autres formats

## Scénarios d'utilisation 💡 {#usage-scenarios-}

Voici quelques façons dont FastMediaSorter v2 peut vous être utile :

### 1. 📸 Organiser les photos de l'appareil photo

Connectez votre téléphone ou ouvrez un dossier local de l'appareil photo. Configurez un dossier de destination « Meilleures photos ». Ouvrez la visionneuse, parcourez rapidement des milliers de photos par balayage, et touchez le bouton de destination pour copier instantanément les meilleurs clichés.

### 2. 🏠 Sauvegarde réseau (NAS)

Ajoutez votre NAS domestique via SMB. Parcourez vos fichiers multimédias locaux. Sélectionnez plusieurs fichiers ou une plage, et « Déplacez »-les vers votre NAS pour les conserver en sécurité, libérant de l'espace sur votre appareil.

### 3. ☁️ Gestion du cloud

Connectez votre compte Google Drive, Dropbox ou OneDrive. Parcourez vos fichiers cloud sans tous les télécharger. Supprimez les fichiers indésirables ou organisez-les en dossiers directement dans le cloud.

### 4. 📺 Diaporama et présentation

Ouvrez un dossier contenant des photos de famille ou des diapositives de présentation. Appuyez sur « Lecture » pour démarrer un diaporama. Utilisez les réglages par ressource pour ajuster la durée des diapositives à votre convenance.

### 5. ⭐ Gérer les favoris

Marquez les fichiers importants avec le bouton étoile pendant la navigation. Ensuite, touchez l'onglet « Favoris » dans le menu principal pour accéder instantanément à tous vos fichiers favoris de toutes les sources en un seul endroit - parfait pour créer une collection sélectionnée de vos meilleurs médias.

### 6. 🎶 Diaporama avec musique de fond

Ajoutez votre collection musicale comme ressource. Dans **Paramètres → Médias → Images**, activez **« Jouer de la musique pendant le diaporama »** et sélectionnez votre ressource musicale. Désormais, lorsque vous démarrez un diaporama de vos photos, vos morceaux favoris joueront en fond sonore. Touchez le nom du morceau pour passer à une autre chanson aléatoire, créant l'ambiance parfaite pour vos présentations de photos.

### 7. 🖼️ Cadre photo numérique sur tablette

Transformez n'importe quelle **tablette** Android en un magnifique cadre photo numérique toujours allumé. Posez-la sur un support, connectez-la à votre PC domestique (SMB) ou au stockage cloud - les photos sont diffusées directement sans occuper d'espace de stockage local. Ajustez l'intervalle des diapositives, gardez l'écran toujours allumé, ajoutez de la musique de fond et profitez de vos souvenirs. Même les vieilles tablettes d'entrée de gamme fonctionnent parfaitement pour cet usage - l'application est optimisée pour une lecture continue à faibles ressources.

### 8. 🍿 Cinéma maison et VR

Regardez vos séries préférées stockées sur votre PC ou dans le cloud directement sur votre téléphone ou votre casque VR. Pas besoin d'attendre une copie ni de vous soucier de l'espace libre. Appuyez simplement sur lecture, et l'épisode suivant démarrera automatiquement.

**Cas d'usage pour casque VR** - FastMediaSorter fonctionne nativement sur les casques VR sous Android (Meta Quest, Pico et similaires) sans aucune modification :

- **🎬 Cinéma virtuel géant** : ouvrez une vidéo depuis votre NAS domestique ou le cloud et regardez-la sur un écran virtuel de la taille d'un mur entier. Pas besoin de copier des fichiers de plusieurs gigaoctets sur le casque - l'application diffuse directement via votre réseau domestique. Quand un épisode se termine, le suivant démarre automatiquement.
- **🎵 Lecteur musical immersif** : lancez votre collection musicale dans l'environnement VR. Le service audio en arrière-plan garde la musique active même en changeant d'application ou en ouvrant l'écran d'accueil VR. Les boutons matériels du casque (lecture/pause, morceau suivant) fonctionnent sans toucher la manette.
- **🖼️ Cadre photo VR grandeur murale** : transformez votre casque VR en une expérience photo immersive - démarrez un diaporama et vos photos remplissent un immense mur virtuel autour de vous. Associez-le à de la musique de fond pour une expérience cinématographique et immersive de vos souvenirs. Diffusez les photos directement depuis votre PC domestique ou le cloud pour que le stockage du casque reste libre.

### 9. 🧹 Organisateur de téléchargements

Le dossier des téléchargements est encombré ? Ouvrez-le dans le panneau des sources, configurez des boutons de destination pour « Documents », « Images » et « Installateurs ». Parcourez rapidement les fichiers, prévisualisez-les, et triez-les vers les bons emplacements d'une seule pression. Vous pouvez même trier des fichiers directement sur votre ordinateur en réseau en utilisant votre téléphone comme télécommande.

### 10. 🚗 Musique en voiture avec un autoradio Android

Installez FastMediaSorter sur votre autoradio ou unité centrale Android. Ajoutez des dossiers musicaux d'une clé USB ou d'une carte SD - ou utilisez la ressource virtuelle intégrée **Toute la musique** pour accéder instantanément à toute votre collection sans aucune configuration. Les boutons multimédias matériels (commandes au volant, molettes de volume) fonctionnent parfaitement via le service audio en arrière-plan : lecture/pause, morceau suivant/précédent, tout cela sans toucher l'écran. L'application mémorise la position de lecture et reprend automatiquement au démarrage.

Avec l'écran **Flux** activé, le même autoradio diffuse également des stations de radio Internet directement via les données mobiles ou le Wi-Fi - sans avoir besoin d'une application TuneIn ou RadioDroid séparée. Ajoutez n'importe quelle URL de radio, ou importez un catalogue de stations sélectionné depuis l'écran Extensions. Le mini-contrôle collant affiche le nom du morceau ICY en cours pendant que la liste des stations reste visible.

### 11. 📺 Centre multimédia sur un boîtier Android TV

Installez FastMediaSorter sur n'importe quel boîtier Android TV (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, ou un boîtier Android générique). Connectez-vous à un NAS domestique via SMB, ajoutez Google Drive ou Dropbox, ou branchez une clé USB - le tout depuis une seule application. Contrôlez tout le flux avec une télécommande ou un clavier Bluetooth : la croix directionnelle déplace le focus, **OK** ouvre les éléments, **Retour** revient au niveau précédent, et **Retour arrière** remonte d'un dossier dans le navigateur. Les boutons colorés de la télécommande correspondent aux actions courantes sur les fichiers (**Rouge** = Supprimer, **Vert** = Copier, **Jaune** = Déplacer, **Bleu** = Renommer). Démarrez un diaporama plein écran avec musique de fond sur la TV, ou passez à la lecture audio avec pochette d'album et paroles. Aucun écran tactile n'est requis.

## Documentation 📚 {#documentation-}

**🗺️ Documentation Map / Карта документации :** [View all docs / Все документы](DOCS_MAP.md)

**🌐 Site officiel :** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### Sources canoniques (source unique de vérité)

Les fichiers suivants doivent être considérés comme les sources faisant autorité pour les détails destinés aux utilisateurs :

- [Liste complète des fonctionnalités](FEATURES.md)
- [Plan de la documentation](DOCS_MAP.md)
- [Historique du produit](PRODUCT_HISTORY.md)
- [Téléchargements (EN)](DOWNLOADS.md)
- [Guides How-To](HOW_TO-fr.md)
- [Limitations du programme](LIMITATIONS.md)
- [Guide de démarrage rapide](QUICK_START-fr.md)
- [Conditions d'utilisation](TERMS_OF_SERVICE.md)

Des guides détaillés sont disponibles en plusieurs langues :

**🇺🇸 English:**

- [Product History](PRODUCT_HISTORY.md)
- [How-To Guides](HOW_TO.md)
- [Launcher Web Portal](launcher/index.md)
- [Wear OS Web Portal](wear/index.md)
- [Quick Start](QUICK_START.md)
- [FAQ](FAQ.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Program Limitations](LIMITATIONS.md)
- [Downloads Guide](DOWNLOADS.md)
- [Complete Feature List](FEATURES.md)

**🇷🇺 Русский:**

- [История продукта](PRODUCT_HISTORY-ru.md)
- [Руководства](HOW_TO-ru.md)
- [Быстрый Старт](QUICK_START-ru.md)
- [FAQ](FAQ-ru.md)
- [Устранение неполадок](TROUBLESHOOTING-ru.md)
- [Ограничения программы](LIMITATIONS-ru.md)
- [Скачивание сборок](DOWNLOADS-ru.md)

**🇺🇦 Українська:**

- [Історія продукту](PRODUCT_HISTORY-uk.md)
- [Посібники](HOW_TO-uk.md)
- [Швидкий Старт](QUICK_START-uk.md)
- [FAQ](FAQ-uk.md)
- [Вирішення проблем](TROUBLESHOOTING-uk.md)
- [Обмеження програми](LIMITATIONS-uk.md)
- [Завантаження збірок](DOWNLOADS-uk.md)

**Documentation technique / développeur :**

- [Vue d'ensemble de l'architecture](ARCHITECTURE.md)
- [DevOps et scripts de build](DEV_OPS.md)
- [Stack technologique](TECH_STACK.md)
- [Documentation Wear OS](WEAR_OS_QUICK_START.md)
- [Composants open source](OPEN_SOURCE.md)

## Compagnon Wear OS ⌚ {#wear-os-companion-}

FastMediaSorter comprend une application Wear OS autonome et complète, ainsi qu'un compagnon sur téléphone conçus pour les formats de montre connectée.

- Parcourez et lisez des dossiers et favoris depuis le téléphone associé, le stockage propre de la montre, et les partages SMB/FTP/SFTP que la montre atteint directement via Wi-Fi
- Les ressources cloud restent sur le téléphone - la montre n'a pas son propre client cloud ; un fichier cloud ne l'atteint que lorsque vous l'envoyez depuis le téléphone avec « Envoyer vers.. »
- Déplacez des fichiers entre le téléphone et la montre, diffusez en direct depuis la montre, et utilisez de petits outils intégrés (calculatrice, moniteur réseau, mini-jeu) sans ouvrir l'application sur le téléphone
- Interface et comportement à l'exécution optimisés pour les écrans ronds et compacts
- Portail web dédié, guides de configuration et dépannage pour les usages sur la montre

Les médias, les partages réseau et le transfert de fichiers sont dans la version complète de l'application montre (APK direct). La version Google Play est une petite première version - calculatrice, chronomètre, mini-jeu et paramètres ; le [portail Wear OS](wear/index.md) indique ce que contient chaque version.

Documentation Wear OS :

- 🌟 **[Portail web Wear OS](wear/index.md)** - présentation complète des fonctionnalités, captures d'écran et téléchargements sur les stores
- [Démarrage rapide Wear OS](WEAR_OS_QUICK_START.md) - guide de jumelage et de configuration pas à pas
- [Configuration Wear OS](WEAR_OS_SETUP.md) - architecture du module et configuration du pont companion
- [Section Wear OS dans Fonctionnalités](FEATURES.md#16-settings--navigation)

## Instructions de compilation {#build-instructions}

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou plus récent

- JDK 17+
- Android SDK 35
- Version Android minimale : 8.0 (API 26) pour Standard/Lite/Photos/VR/noLegal ; 6.0 (API 23) pour Legacy

### Compilation

1. Clonez le dépôt :

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Ouvrez le projet dans Android Studio.
3. Attendez la fin de la synchronisation Gradle.
4. Lancez l'application sur un émulateur ou un appareil physique.

### Commandes de compilation préférées (Windows / PowerShell)

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### APK compilés 📦

Après chaque compilation réussie, le fichier APK généré est automatiquement copié dans le dossier `DOWNLOADS` à la racine du projet, avec un horodatage. Vous y trouverez tout l'historique de vos builds.

## Tests 🧪 {#testing-}

FastMediaSorter v2 utilise **Maestro** pour les tests de bout en bout afin d'assurer la qualité et la fiabilité de l'application.

### Exécution rapide des tests

```bash
# Installer Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# Ou Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell en tant qu'administrateur)
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1  # External: Maestro installer
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Lancer les tests de fumée (2-3 minutes)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# Ou utiliser le raccourci
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**Remarque** : N'utilisez PAS `npm install -g maestro-cli` - c'est un paquet différent, sans rapport !

### Suites de tests

- **Tests de fumée** (`maestro/smoke/`) : tests des fonctionnalités essentielles (~2-3 min)
  - Lancement de l'application et permissions
  - Navigation dans les fichiers locaux
  - Lecture multimédia
  - Visionnage d'images

- **Tests des chemins critiques** (`maestro/critical/`) : opérations essentielles (~1-2 min)
  - Opérations sur les fichiers (copier, déplacer, supprimer)
  - Persistance des paramètres

### Documentation

- 📚 [Guide de démarrage rapide](../maestro/INDEX.md)
- 📝 [Écriture de tests](../maestro/WRITING_TESTS.md)
- 🔍 [Exemples de tests](../maestro/EXAMPLES.md)
- 🔧 [Dépannage](../maestro/TROUBLESHOOTING.md)
- 📖 [Documentation complète](../maestro/README.md)

### Intégration CI/CD

Les tests s'exécutent automatiquement à chaque push via GitHub Actions. Voir [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml).

## Premiers pas (guide d'utilisation rapide) 🚀 {#first-steps-quick-usage-guide-}

1. **Ajouter un dossier (ressource) :**
    - Sur l'écran principal, appuyez sur le bouton avec l'icône « Plus » (+) pour ajouter une nouvelle ressource.
    - Sélectionnez le type de ressource (par ex. « Dossier local »).
    - Utilisez l'analyse ou ajoutez le dossier manuellement. Une fois ajouté, il apparaîtra dans la liste de l'écran principal.

2. **Consulter les fichiers :**
    - Double-touchez (ou appuyez longuement) sur la ressource ajoutée dans la liste.
    - L'écran de navigation s'ouvre, où vous verrez tous les fichiers multimédias de ce dossier en liste ou en grille.
    - Utilisez les boutons du panneau supérieur pour trier, filtrer ou changer d'affichage.

3. **Lecture et tri :**
    - Touchez n'importe quel fichier pour l'ouvrir dans le lecteur plein écran.
    - Utilisez les balayages gauche/droite ou les zones tactiles pour naviguer entre les fichiers.
    - Pour les opérations (copier, déplacer), utilisez les zones tactiles correspondantes ou les boutons du panneau de contrôle.

4. **Configurer les dossiers de destination (Destinations) :**
    - Dans les paramètres, sous l'onglet « Destinations », vous pouvez spécifier jusqu'à 30 dossiers qui seront utilisés pour le tri rapide.
    - Vous pouvez aussi activer « Est une destination » dans l'écran d'édition de n'importe quelle ressource pour l'ajouter à la liste de tri rapide.
    - Ensuite, des boutons apparaîtront sur l'écran du lecteur pour copier ou déplacer rapidement les fichiers vers ces dossiers.

## Stack technologique {#technology-stack}

- **Langage** : Kotlin
- **Architecture** : Clean Architecture, MVVM
- **Interface** : Android View System (XML), Material Design 3
- **Asynchronisme** : Kotlin Coroutines & Flow
- **Injection de dépendances** : Hilt (Dagger)
- **Base de données** : Room 2.7.0
- **Navigation** : AndroidX Navigation Component
- **Multimédia** : ExoPlayer (Media3 1.2.1)
- **Chargement d'images** : Glide 5.0.9 avec NetworkFileModelLoader personnalisé
- **Protocoles réseau** :
  - SMB : SMBJ 0.12.1 avec BouncyCastle (transitif)
  - SFTP : JSch 0.2.26 (fork com.github.mwiede, Ed25519 intégré)
  - FTP : Apache Commons Net 3.10.0
- **Cloud** : API Google Drive, OneDrive (MSAL), API Dropbox avec OAuth 2.0
- **OCR et traduction** :
  - Tesseract4Android (Tesseract 5.3.x) - extraction de texte pour les écritures latine et cyrillique
  - Google ML Kit (traduction, identification de la langue) - traduction du texte extrait
- **Recherche et paroles** : api.lyrics.ovh (API JSON)

## Version de compilation

Format de version : `Y.YM.MDDH.Hmm` (par ex. `2.60.1102.207` pour le 10/01/2026 20:07)

Voir [dev/CHANGELOG.md](../dev/CHANGELOG.md) pour les notes de version détaillées.

---

## Contribuer 🤝

Les pull requests sont les bienvenues. Pour des changements majeurs, veuillez d'abord ouvrir un ticket pour discuter de ce que vous souhaitez changer.

## Contact 📧

- **Développeur** : <sza@ukr.net>
- **Site web** : [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues** : [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## Licence 📄

Informations légales du projet :

- [Conditions d'utilisation](TERMS_OF_SERVICE.md)
- [Politique de confidentialité](PRIVACY_POLICY.md)
- [Composants open source](OPEN_SOURCE.md)

</div>
