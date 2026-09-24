---
layout: default
title: "📖 Guides pratiques"
permalink: /docs/HOW_TO-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 📖 Guides pratiques

Instructions étape par étape pour les tâches courantes.

Ce guide comporte désormais deux niveaux :

- **Groupes de scénarios** pour des flux de travail réels plus riches et des combinaisons de fonctionnalités.
- **Référence des tâches de base**, plus bas, pour des recettes directes portant sur une seule fonctionnalité.

{% include lang-switcher.html doc="HOW_TO" dir="/docs/" current="fr" %}

---

## Remarque : disponibilité des fonctionnalités selon la version

Certaines fonctionnalités ne sont disponibles que dans des versions (flavors) spécifiques. Le tableau ci-dessous est dérivé de [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), qui est généré à partir du build lui-même ; la surface XR / noLegal est volontairement regroupée dans une seule colonne car elle dépend du matériel du casque et des règles de build en sideload.

| Fonctionnalité | Standard | Lite | Photos | Legacy | XR / noLegal | FOSS |
|---------|----------|------|--------|--------|--------------|------|
| Dossiers réseau (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Stockage cloud (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ |
| Lecture audio et paroles | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ |
| Lecture audio en arrière-plan | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Flux Internet (radio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Visionneuse de documents (PDF, Texte) | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Lecteur EPUB | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Traduction et OCR | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Édition d'images | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Mode écran d'accueil (lanceur) | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |

Le mode écran d'accueil est la seule ligne où la dernière colonne combinée se scinde : il est présent dans le build sideload **noLegal**, mais pas dans le build VR/XR, où le casque fournit son propre environnement d'accueil.

Deux lignes pour l'audio, car ce sont deux décisions de build distinctes : **Lite lit les fichiers audio locaux**, paroles comprises, mais s'arrête dès que l'application quitte le premier plan - elle n'a pas de service de lecture en arrière-plan. Lite n'a aucun écran Flux Internet, donc la radio et le HLS/DASH/RTSP n'y sont pas seulement limités, ils sont absents.

Si une fonctionnalité est marquée « ✗ », choisissez le build **Standard** ou **XR / noLegal** correspondant à votre matériel et à votre canal de distribution.

---

## Table des matières

### Groupes de scénarios

#### Multimédia domestique, TV et salon

1. [Transformer un NAS en médiathèque de salon](#turn-a-nas-into-a-living-room-media-shelf)
2. [Lancer un diaporama avec musique de fond pour un écran de pièce](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Utiliser FMS sur un boîtier Android TV](#how-to-use-fms-on-android-tv-box)
4. [Cinéma immersif OpenXR VR](#openxr-vr-immersive-cinema)

#### Voyage, lecture et documents

5. [Préparer un dossier pour un voyage sans internet stable](#prepare-a-folder-for-travel-without-stable-internet)
6. [Lire des documents cloud et des EPUB en déplacement](#read-cloud-documents-and-epubs-on-the-go)
7. [Traduire des panneaux, scans et captures d'écran avec l'OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Transmettre des fichiers réseau à des applications spécialisées](#hand-network-files-off-to-specialist-apps)
9. [Calculs rapides sur du texte](#quick-math-and-text-calculations)
10. [Notes cloud en Markdown et code](#cloud-markdown-and-code-notes)

#### Utilisateurs avancés et flux multimédias mixtes

11. [Trier une archive photo familiale avec le tri rapide](#sort-a-family-photo-archive-with-quick-sort)
12. [Capturer l'écran avec les gestes de bord](#capture-the-screen-with-edge-gestures)
13. [Créer un diaporama avec musique de fond](#how-to-create-slideshow-with-background-music)
14. [Lire des livres électroniques (EPUB)](#how-to-read-e-books-epub)
15. [Traduction automatique](#auto-translation)
16. [Widgets intelligents pour l'écran d'accueil](#home-screen-smart-widgets)

### Référence des tâches de base

17. [Se connecter à un lecteur réseau (SMB)](#how-to-connect-to-network-drive-smb)
18. [Se connecter à un serveur SFTP/FTP](#how-to-connect-to-sftpftp-server)
19. [Importer un partage Companion Windows (scanner un code ou importer un fichier)](#how-to-import-a-windows-companion-share)
20. [Se connecter à un espace de stockage cloud](#how-to-connect-to-cloud-storage)
21. [Configurer les dossiers de tri rapide](#how-to-set-up-quick-sort-folders)
22. [Utiliser les zones tactiles](#how-to-use-touch-zones)
23. [Modifier des photos](#how-to-edit-photos)
24. [Créer un diaporama](#how-to-create-slideshow)
25. [Protéger un dossier par code PIN](#how-to-protect-folder-with-pin)
26. [Vider la corbeille](#how-to-empty-trash)
27. [Sauvegarder les paramètres](#how-to-backup-settings)
28. [Afficher les fichiers texte et PDF](#how-to-view-text-and-pdf-files)
29. [Ouvrir des fichiers réseau dans des applications externes](#how-to-open-network-files-in-external-apps)
30. [Afficher les paroles d'une chanson](#how-to-view-song-lyrics)
31. [Enregistrer votre écran](#how-to-record-your-screen)
32. [Enregistrer une note vocale](#how-to-record-a-voice-note)
33. [Utiliser l'appareil photo intégré](#how-to-use-the-in-app-camera)
34. [Trouver et supprimer les fichiers en double](#how-to-find-and-delete-duplicate-files)
35. [Consulter vos statistiques d'utilisation](#how-to-view-your-usage-statistics)
36. [Utiliser une carte SD ou un lecteur connecté](#how-to-use-an-sd-card-or-connected-drive)
37. [Reconnecter un dossier ajouté par chemin direct](#how-to-reconnect-a-folder-added-by-direct-path)
38. [Utiliser l'application comme écran d'accueil](#how-to-use-the-app-as-your-home-screen)
39. [Choisir où enregistrer les captures et les téléchargements](#how-to-choose-where-captures-and-downloads-are-saved)
40. [Recevoir des fichiers partagés depuis une autre application](#how-to-receive-files-shared-from-another-app)
41. [Utiliser les programmes intégrés](#how-to-use-the-built-in-programs)
42. [Demander à votre assistant de trouver et d'ouvrir des médias](#how-to-ask-your-assistant-to-find-and-open-media)
43. [Chiffrer un fichier avec FileDO](#how-to-encrypt-a-file-with-filedo)

---

## Groupes de scénarios

Ces sections sont volontairement plus variées que les blocs de référence de base ci-dessous. Chaque scénario combine un chemin rapide avec du contexte, des compromis, et les situations où FastMediaSorter est particulièrement efficace.

> **⭐ À la une : transférez les dossiers de votre PC vers votre téléphone en un seul scan.** Lancez le compagnon gratuit [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) sur votre PC, choisissez les dossiers contenant vos vidéos, votre musique, vos documents ou vos photos, et il affiche un code à l'écran. Sur le téléphone, appuyez sur **Ajouter**, choisissez **Importer par code-barres**, pointez la caméra vers le code - les dossiers du PC sont connectés instantanément, sans avoir à saisir d'adresse, de port ni de mot de passe. Procédure complète : [Ouvrir les dossiers du PC en scannant un code](howto/scenario-companion-share-fr.md) &bull; recette rapide : [Importer un partage Companion Windows](#how-to-import-a-windows-companion-share).

> **⌚ Montres Wear OS :** Vous utilisez une montre Wear OS ? Consultez nos guides pas à pas [Écouter de la musique sur votre montre](howto/scenario-watch-music-fr.md) et [Connecter la montre au NAS et aux partages PC](howto/scenario-watch-network-fr.md).

## Multimédia domestique, TV et salon

## Transformer un NAS en médiathèque de salon {#turn-a-nas-into-a-living-room-media-shelf}

**Disponible dans :** Standard, Photos, Legacy, XR/noLegal

**Chemin rapide**

1. Ajoutez votre NAS comme ressource SMB.
2. Lancez **Analyser le réseau** si vous ne voulez pas saisir l'IP manuellement.
3. Ouvrez la ressource depuis un boîtier TV, une tablette ou un téléphone.
4. Commencez à parcourir vidéos, photos ou documents directement depuis le NAS.

**Parcours du scénario**

- Gardez une seule ressource SMB pour toute la bibliothèque familiale et séparez les sous-dossiers par usage : Films, Photos de famille, Scans, Manuels.
- Lancez **Tester la connexion** une fois pendant la configuration, pour que la ressource soit stable avant d'en dépendre depuis le canapé.
- Si le NAS est utilisé depuis un boîtier TV, associez-le à un clavier Bluetooth ou à une télécommande TV pour une navigation rapide.
- Si la navigation semble lente, ouvrez les paramètres de la ressource et lancez le test de vitesse intégré avant de changer autre chose.

**Quand c'est utile**

- Vous voulez une seule source multimédia centrale au lieu de copier les mêmes fichiers sur plusieurs appareils.
- Vous voulez que la même bibliothèque fonctionne pour le diaporama, la lecture de documents et la lecture multimédia.

**À éviter**

- Ne commencez pas par le dépannage du nom d'hôte. Utilisez d'abord une adresse IP, puis optimisez ensuite.
- Ne vous attendez pas à ce que la version Lite puisse parcourir les partages SMB - ce build n'a aucune source réseau.

## Lancer un diaporama avec musique de fond pour un écran de pièce {#run-a-slideshow-with-background-music-for-a-room-display}

**Disponible dans :** Standard, Lite, Legacy, XR / noLegal (Photos n'a pas de prise en charge audio)

**Chemin rapide**

1. Ajoutez une source d'images et une source de musique.
2. Dans **Paramètres → Média → Images**, activez **Lire de la musique pendant le diaporama**.
3. Choisissez la ressource musicale.
4. Ouvrez un dossier de photos et appuyez sur **Lecture**.

**Parcours du scénario**

- Utilisez un dossier d'images local ou un partage NAS rapide pour des transitions les plus fluides possible.
- Gardez une ressource musicale distincte pour des morceaux de fond calmes, afin que l'audio du diaporama reste prévisible.
- Si le dossier contient à la fois des images et des vidéos, rappelez-vous que la musique se met en pause automatiquement quand une vidéo démarre.

**Quand c'est utile**

- Vous voulez qu'un boîtier TV, une tablette ou un vieux téléphone serve de cadre photo numérique pour une pièce.
- Vous voulez une seule configuration capable de faire défiler photos de famille, clichés d'événements ou albums de voyage sans construire de file d'attente à la main.

**À éviter**

- N'utilisez pas un partage réseau très lent à la fois pour les images et pour la musique si la fluidité de la lecture compte.

## Cinéma immersif OpenXR VR {#openxr-vr-immersive-cinema}

**Disponible dans :** Standard, Lite, Legacy, `vr`, noLegal (3D à œil unique) ; `vr` et noLegal (immersion complète au casque - les deux builds embarquent la vue immersive, qui s'ouvre quand l'application détecte un casque OpenXR et que l'interrupteur principal VR est activé)

**Chemin rapide - activer, configurer, regarder en 3D**

1. **3D à œil unique (toutes les versions, rien à activer) :** ouvrez n'importe quel fichier SBS/OU/180°/360° - il est détecté automatiquement et recadré sur un seul œil pour bien s'afficher sur un écran plat classique. Ceci est contrôlé par **Paramètres > Lecteur > « Afficher le contenu 3D à partir d'un seul œil »** (activé par défaut). Pour forcer un format précis plutôt que de vous fier à la détection automatique, ouvrez la boîte de dialogue Contrôle du lecteur sur un build `vr`/XR-noLegal et choisissez un mode dans l'onglet 3D - **Détection automatique**, **Côte à côte (SBS)**, **Superposé (OU)** ou **Mono (désactivé)** ; le choix est mémorisé pour ce fichier.
2. **Immersion complète sur un Quest (build `vr` ou XR/noLegal) :** casque sur la tête, appuyez sur le badge VR dans le lecteur pendant qu'un fichier 3D est ouvert, choisissez **Ouvrir en cinéma VR** depuis le menu contextuel d'un fichier dans Parcourir, ou ouvrez **Paramètres > Média** et appuyez sur **Tester l'immersion** pour essayer un exemple. Chacune de ces trois options ouvre une vue OpenXR par œil de ce contenu.
3. **Regarder :** à l'intérieur de la vue immersive, une bande HUD porte les commandes - une barre de position que vous faites glisser avec le rayon de la manette pour avancer dans la lecture (temps écoulé et durée totale à côté), ainsi que les sélecteurs applicables à ce fichier : piste audio seulement s'il y en a plusieurs, sous-titres seulement si le fichier en contient, profondeur stéréo seulement pour le contenu stéréo. **MASQUER** et **QUITTER** se trouvent aux deux extrémités de la bande ; la masquer la fait disparaître complètement, et une pression sur la gâchette la fait réapparaître sans activer ce qui se trouve dessous. Le joystick avance de 10 secondes par cran ; maintenez **grip** en le poussant pour passer d'un fichier à l'autre à la place - suivant et précédent parcourent toute la liste de la ressource, pas seulement le fichier ouvert. À la première entrée en immersion après l'installation, une légende répertorie toutes les combinaisons de la manette ; n'importe quelle pression la ferme, et le bouton **AIDE** sur la bande la ramène à tout moment.

**Parcours du scénario**

- La 3D à œil unique ne nécessite aucun casque - c'est le moyen le plus simple de revoir d'anciennes images SBS/OU sur un téléphone ou une tablette.
- L'immersion complète nécessite un Quest ou un autre casque OpenXR et un build qui l'embarque - le build `vr` ou le build sideload XR/noLegal (voir le [guide de sideload VR](VR_SIDELOAD.md)).
- Les photos et vidéos 360°/180° s'affichent en sphère/hémisphère autour de vous une fois dans la vue immersive ; les fichiers 2D plats se lisent simplement à plat.

**Quand c'est utile**

- Vous voulez revoir des images SBS/OU/360°/180° archivées sans une application VR dédiée séparée.
- Vous avez un Quest et voulez essayer l'immersion complète sur vos propres fichiers dès aujourd'hui, en acceptant que la navigation soit pour l'instant limitée au suivant/précédent.

**À éviter**

- Ne vous attendez pas encore à ce que le build `vr` du Meta Horizon Store / Google Play entre en mode immersif - cette partie est encore en développement.
- La recherche, la sélection de piste et de sous-titres et la profondeur stéréo se trouvent sur la bande HUD dans le casque. Les opérations sur les fichiers, non - revenez au panneau plat pour copier, déplacer ou supprimer.

## Écouter la radio Internet sur un autoradio ou un lecteur audio

**Disponible dans :** Standard, Legacy, XR / noLegal - l'écran Flux est absent dans Lite et Photos

**Chemin rapide**

1. Ouvrez le menu déroulant de la fenêtre principale et appuyez sur **Flux**, ou allez dans **Paramètres > Média > Flux** et activez l'interrupteur s'il est désactivé.
2. Appuyez sur **⋮** à l'extrémité de la barre d'outils, choisissez **Ajouter un flux**, et collez n'importe quelle URL de station radio (http:// ou https://, .m3u8, rtsp://).
3. Appuyez sur la ligne de la station - l'audio démarre dans le mini-contrôle collé en bas. La liste reste défilable.
4. Pour un catalogue plus vaste, appuyez sur **Importer** et saisissez une URL `.m3u` distante, ou téléchargez le catalogue FastMediaSorter organisé depuis l'écran **Extensions**.

**Parcours du scénario**

- Le catalogue organisé arrive avec des puces de thème et de langue ; filtrez par genre ou langue via le bouton de filtre (point indicateur quand actif). Le bouton ET/OU vous permet de faire correspondre les stations à tous les critères ou à un seul.
- Le catalogue arrive aussi regroupé en collections nommées - « Russian TV », « Radio of the former USSR », « African TV » et d'autres. Elles apparaissent sous forme de bande de puces défilante juste sous la barre d'outils ; appuyez sur l'une d'elles pour ne voir que ses chaînes, dans l'ordre choisi par le curateur, et appuyez sur **Tout** pour revenir en arrière. Une même chaîne peut appartenir à plusieurs collections, vous pouvez donc la retrouver à la fois sous un pays et sous un continent. Une collection est une condition de filtre supplémentaire, pas un écran séparé : la recherche, le tri, les filtres de genre et de langue et vos épingles continuent tous de fonctionner à l'intérieur. Si le catalogue téléchargé ne contient aucune collection, la bande est simplement absente.
- Les deux petites icônes à droite du champ de recherche séparent la radio de la vidéo en un seul geste : appuyez sur l'icône audio ou vidéo pour ne garder que ce type, appuyez de nouveau sur celle allumée pour tout réafficher.
- Épinglez vos stations favorites en haut avec l'icône d'épingle - l'ordre est indépendant des Favoris globaux.
- Basculez l'affichage de la barre d'outils sur **Grille** pour voir les chaînes sous forme de vignettes avec leur dernière image capturée - pratique pour parcourir les flux vidéo d'un coup d'œil. Votre choix de liste ou de grille est mémorisé la prochaine fois que vous ouvrez Flux.
- Si un flux est compatible avec le cast et que votre téléphone est en Wi-Fi, appuyez sur **Cast** dans le lecteur pour l'envoyer vers un Chromecast sur le même réseau. Les flux RTSP ne peuvent pas être diffusés en cast.
- Les métadonnées ICY en cours de lecture (nom de la station, morceau en cours) s'affichent dans le mini-contrôle en bas.
- Une station que vous avez ajoutée vous-même peut être envoyée vers votre montre Wear OS : ouvrez le menu **⋮** de la ligne et appuyez sur **Envoyer vers la montre** (la commande apparaît quand l'option Compagnon Wear est activée). La station transférée reste sur la montre à travers les actualisations du catalogue ; si la même adresse apparaît ensuite dans le catalogue en ligne, l'entrée du catalogue prend le relais.
- Les flux vidéo et RTSP s'ouvrent dans le lecteur plein écran ; un appui sur Retour ramène à la liste Flux avec la position de défilement conservée.
- Le comportement de la lecture audio en arrière-plan suit **Paramètres > Lecteur > Lecture audio en arrière-plan** : si elle est désactivée, l'audio s'arrête quand vous quittez l'écran et l'application propose un choix Arrêter / Continuer la lecture.

**Quand c'est utile**

- Autoradios Android, lecteurs audio et boîtiers multimédias où vous voulez la radio Internet sans application séparée (TuneIn, RadioDroid, flux réseau VLC).
- Usage IPTV léger : les flux VOD HLS/DASH se lisent dans le lecteur plein écran.

**À éviter**

- Ne vous attendez pas à la lecture HLS/DASH en direct (décalage sur le direct) - seul le VOD HLS/DASH est pris en charge dans cette version.
- N'utilisez pas la version Lite ou Photos pour Flux ; aucun de ces builds n'a d'entrée Flux, donc aucun protocole n'y fonctionne.

## Voyage, lecture et documents

## Préparer un dossier pour un voyage sans internet stable {#prepare-a-folder-for-travel-without-stable-internet}

**Disponible dans :** Standard, Lite, Photos, Legacy, XR / noLegal (la lecture PDF et EPUB nécessite Standard, Legacy ou XR / noLegal)

**Chemin rapide**

1. Créez ou choisissez un dossier local pour le voyage.
2. Copiez-y les médias, PDF, EPUB ou notes dont vous avez besoin avant de quitter le Wi-Fi.
3. Ouvrez ce dossier une fois dans FastMediaSorter pour que les vignettes et les dernières positions soient prêtes.
4. Utilisez le dossier hors ligne pendant le voyage.

**Parcours du scénario**

- Gardez les médias de voyage dans un seul dossier local, même si les originaux vivent habituellement sur un NAS ou dans le cloud.
- Mélangez les formats volontairement : PDF d'embarquement, EPUB de lecture, captures d'écran et musique hors ligne peuvent cohabiter.
- Utilisez le panneau de filtre si vous voulez basculer entre uniquement les images, uniquement les documents, ou uniquement l'audio hors ligne.

**Quand c'est utile**

- Vols, trains, hôtels et zones rurales où la diffusion cloud n'est pas fiable.
- Situations où vous voulez un seul pack hors ligne plutôt que de chercher dans plusieurs applications.

**À éviter**

- N'attendez pas la dernière minute pour vérifier que les fichiers s'ouvrent vraiment sans internet.

## Lire des documents cloud et des EPUB en déplacement {#read-cloud-documents-and-epubs-on-the-go}

**Disponible dans :** Standard, Legacy, XR / noLegal - Lite et Photos ne peuvent lire ni documents ni EPUB du tout ; le stockage cloud est en outre absent de Lite

**Chemin rapide**

1. Ajoutez votre fournisseur cloud dans **Stockage cloud**.
2. Ouvrez le dossier qui contient les PDF ou EPUB.
3. Appuyez directement sur le fichier depuis la ressource cloud.
4. Continuez la lecture depuis votre dernière position enregistrée plus tard.

**Parcours du scénario**

- Utilisez ceci quand vos documents de travail vivent déjà dans Google Drive, OneDrive ou Dropbox et que vous ne voulez pas d'un flux de lecture séparé.
- Le PDF convient mieux aux fichiers à mise en page fixe comme les billets, les manuels et les contrats scannés.
- L'EPUB convient mieux à la lecture longue où la taille de police ajustable et la navigation par chapitre comptent plus que la fidélité de la mise en page.

**Quand c'est utile**

- Vous passez des documents de travail à la lecture personnelle sans quitter l'application.
- Vous gardez vos fichiers de voyage ou clients dans le cloud, mais voulez tout de même une interface pensée pour la lecture.

**À éviter**

- Ne vous attendez pas à la lecture cloud dans Lite - ce build n'a ni stockage cloud ni prise en charge des documents. Photos et Legacy ont bien le stockage cloud, mais seul Legacy peut ouvrir des documents.
- Ne considérez pas les données mobiles lentes comme garantissant une bonne expérience de lecture pour de très gros fichiers.

## Traduire des panneaux, scans et captures d'écran avec l'OCR {#translate-signs-scans-and-screenshots-with-ocr}

**Disponible dans :** Standard, Legacy, XR / noLegal

**Chemin rapide**

1. Ouvrez une image, un PDF ou un fichier texte.
2. Affichez le panneau de commandes.
3. Appuyez sur **Traduire**.
4. Confirmez le téléchargement du modèle à la première utilisation si nécessaire.

**Parcours du scénario**

- L'application lit le texte avec Tesseract sur l'appareil et le traduit avec Google ML Kit.
- Pour du texte cyrillique, choisissez explicitement la langue source (par exemple russe ou ukrainien) - « Auto » lit avec le modèle anglais.
- Les captures d'écran, reçus, menus et pages scannées fonctionnent particulièrement bien quand le texte source est raisonnablement net.

**Quand c'est utile**

- Vous voyagez, lisez des manuels étrangers ou décodez des captures d'écran provenant de discussions et d'applications.
- Vous avez besoin d'une traduction sur place plutôt que de copier d'abord le texte dans un outil séparé.

**À éviter**

- Ne jugez pas la qualité de l'OCR à partir d'une photo de nuit floue ou d'un scan mal cadré.

## Transmettre des fichiers réseau à des applications spécialisées {#hand-network-files-off-to-specialist-apps}

**Disponible dans :** Standard, Photos, Legacy, XR/noLegal

**Chemin rapide**

1. Ouvrez un fichier depuis SMB, SFTP ou FTP.
2. Appuyez sur **ⓘ Infos**.
3. Appuyez sur **Télécharger et ouvrir**.
4. Choisissez l'application spécialisée dans le sélecteur Android.

**Parcours du scénario**

- Utilisez ceci quand FastMediaSorter est le meilleur navigateur pour le stockage distant, mais qu'une autre application est le meilleur éditeur ou visualiseur pour un type de fichier donné.
- Les cas de transfert typiques sont les documents bureautiques, les PDF avancés, les vidéos gourmandes en codecs et les formats multimédias de niche.
- La copie téléchargée reste dans `Downloads`, vous pouvez donc la rouvrir plus tard même si la source distante devient inaccessible.

**Quand c'est utile**

- Vous voulez un seul hub de fichiers distants sans renoncer aux meilleurs outils spécialisés du marché.

**À éviter**

- Ne vous attendez pas encore au transfert cloud via exactement ce flux.

## Calculs rapides sur du texte {#quick-math-and-text-calculations}

**Disponible dans :** Standard, Legacy, XR / noLegal

**Chemin rapide**

1. Ouvrez n'importe quel document PDF, livre électronique EPUB, fichier texte, ou lancez la traduction OCR sur une image.
2. Faites un appui long pour sélectionner un bloc de texte contenant des nombres ou des équations mathématiques.
3. Dans le menu d'action de texte flottant, appuyez sur le bouton **Calculatrice**.
4. La calculatrice évalue instantanément la formule mathématique dans une fenêtre superposée.

**Parcours du scénario**

- Sélectionnez une ligne de texte contenant des nombres avec des symboles d'opérateurs (comme `(45 + 12) * 3`) dans un PDF ou un résultat de traduction OCR.
- Utilisez le menu de fonctions de la calculatrice scientifique intégrée pour les opérations complexes (trigonométrie, racines, puissances, logarithmes).
- La calculatrice conserve l'historique des calculs entre les sessions et prend en charge des emplacements mémoire (M+/M-/MR/MC) pour un suivi rapide des données.

**Quand c'est utile**

- Vous lisez un manuel, un scan de capture d'écran ou un document et devez rapidement résoudre des formules ou additionner des devises/nombres sans passer à une autre application de calculatrice.

**À éviter**

- Ne collez pas de chaînes alphabétiques brutes ; seuls les nombres valides, les parenthèses et les opérateurs mathématiques peuvent être analysés.

## Notes cloud en Markdown et code {#cloud-markdown-and-code-notes}

**Disponible dans :** Standard, Photos, Legacy, XR / noLegal (local, réseau et cloud) ; Lite (dossiers locaux uniquement)

**Chemin rapide**

1. Parcourez n'importe quel dossier local, NAS domestique (SMB), serveur FTP/SFTP ou lecteur cloud (Google Drive).
2. Appuyez sur le bouton **Nouvelle note <img src="icons/doc/ic_create_text_file.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils du dossier.
3. Saisissez votre contenu dans l'éditeur. L'application met en évidence les balises Markdown et la syntaxe du code.
4. Appuyez sur **Enregistrer** (ou laissez l'enregistrement automatique faire son travail) pour écrire les modifications directement sur la source distante.

**Parcours du scénario**

- Gardez un fichier journal `.md` sur votre Google Drive ou votre NAS domestique et modifiez-le depuis n'importe quel appareil grâce à l'édition sur place.
- Créez de nouvelles notes dans les ressources clés avec résolution automatique des conflits de nom (par ex. `Note_1.txt`, `Note_2.txt`).
- Affichez les mises en page Markdown rendues en mode lecture seule, ou exportez les notes directement vers des services externes comme Google Keep.

**Quand c'est utile**

- Vous voulez tenir des notes simples, des extraits de code ou des listes de tâches directement sur vos lecteurs réseau/cloud centraux, sans flux de copier-coller local.

**À éviter**

- Ne vous attendez pas à la création de notes sur le stockage cloud dans Lite - ce build n'a ni stockage cloud ni source réseau.

## Utilisateurs avancés et flux multimédias mixtes

## Trier une archive photo familiale avec le tri rapide {#sort-a-family-photo-archive-with-quick-sort}

**Disponible dans :** Standard, Lite, Photos, Legacy, XR / noLegal

**Chemin rapide**

1. Ajoutez vos dossiers de destination au **tri rapide**.
2. Ouvrez le dossier source contenant les photos de famille non triées.
3. Utilisez les boutons numérotés ou les zones tactiles pendant la revue des images.
4. Envoyez les photos à conserver vers les dossiers de destination immédiatement.

**Parcours du scénario**

- Créez les dossiers de destination par résultat, pas seulement par date : `À garder`, `À imprimer`, `À envoyer à la famille`, `Archive`.
- Passez en revue en plein écran pour décider rapidement et déplacer ou copier sans revenir à la liste de fichiers.
- Si plusieurs personnes gèrent la même archive, adoptez un schéma de nommage de destination cohérent avant une grande session de tri.

**Quand c'est utile**

- Vous avez un arriéré d'anniversaires, de voyages, d'événements scolaires ou d'imports d'anciens téléphones.
- Vous voulez un flux de tri rapide plutôt que de glisser les fichiers manuellement dans un gestionnaire de fichiers.

**À éviter**

- Ne commencez pas à trier avant que les destinations ne soient clairement nommées.
- N'utilisez pas Déplacer immédiatement si vous n'êtes pas encore sûr des dossiers qui doivent rester comme archive à long terme.

## Capturer l'écran avec les gestes de bord {#capture-the-screen-with-edge-gestures}

**Disponible dans :** Standard, XR/noLegal

**Chemin rapide**

1. Allez dans **Paramètres → Gestion → Gestes de bord d'écran → Overlay de gestes** et activez-le.
2. En consultant n'importe quel fichier, glissez depuis le bord gauche pour ouvrir le menu de capture.
3. Choisissez une action - la bande se ferme et l'action s'exécute.

**Ce que la bande peut faire**

- Prendre une **capture d'écran** de l'écran actuel - la consulter, la modifier, la partager, l'envoyer vers une autre application ou lancer une traduction OCR dessus, avec en plus une option de capture silencieuse.
- **Prendre une photo** avec l'appareil photo, puis l'envoyer, la modifier, ou lancer l'OCR-traduction dessus sans quitter l'application.
- Démarrer un enregistrement d'**écran**, de **vidéo** ou **audio/vocal** - voir [Comment enregistrer votre écran](#how-to-record-your-screen) et [Comment enregistrer une note vocale](#how-to-record-a-voice-note).
- **Ouvrir une application ou un panneau** que vous utilisez souvent.
- **Recadrer et partager** une zone de l'image actuelle.

**Bon à savoir**

- Tant que la bande est active, un glissement depuis le bord gauche ouvre le menu de capture au lieu de tourner la page.
- La bande est conçue pour la capture à une main pendant la navigation - désactivez-la si vous comptez sur les glissements de page depuis le bord gauche.
- Android confirme la capture ou l'enregistrement à chaque utilisation de ce geste, même pour l'option de capture silencieuse - c'est une protection du système, pas quelque chose que l'application contrôle.

**Quand c'est utile**

- Vous voulez une capture d'écran, une photo rapide ou un enregistrement sans quitter le fichier que vous consultez.

## Référence des tâches de base

## Comment ajouter ou importer un flux Internet

**Disponible dans :** Standard, Legacy, XR / noLegal (tous les protocoles) - l'écran Flux est absent dans Lite et Photos

**Ajouter une seule URL :**

1. Ouvrez **Flux** depuis le menu déroulant de la fenêtre principale.
2. Appuyez sur le bouton **⋮** à l'extrémité de la barre d'outils, puis **Ajouter un flux**.
3. Collez l'URL du flux (radio http/https, .m3u8, rtsp://). Appuyez sur **Enregistrer**.
4. Appuyez sur la ligne pour démarrer la lecture.

**Importer une playlist .m3u distante :**

1. Dans l'écran Flux, appuyez sur **⋮ > Importer depuis une URL**.
2. Saisissez l'adresse .m3u distante. Appuyez sur **Importer**.
3. Toutes les stations du fichier apparaissent dans la liste.

**Télécharger le catalogue FastMediaSorter organisé :**

1. Ouvrez **Paramètres > Extensions** (ou la ligne Flux de l'accueil de bienvenue).
2. Appuyez sur **Télécharger** à côté de l'entrée du catalogue Flux.
3. Après le téléchargement, les lignes du catalogue apparaissent dans Flux avec des puces de thème/langue et sont consultables et triables.

---

## Comment se connecter à un lecteur réseau (SMB) {#how-to-connect-to-network-drive-smb}

**Ce dont vous avez besoin :**

- Un NAS ou un PC Windows avec un dossier partagé
- Les deux appareils sur le même réseau Wi-Fi
- Nom d'utilisateur et mot de passe pour le partage

**Disponible dans :** Standard, Photos, Legacy, XR / noLegal

**Étapes :**

1. **Appuyez sur le bouton « + »** sur l'écran principal
2. Sélectionnez **« Dossier réseau (SMB) »**
3. Renseignez les détails :
   - **Découverte automatique (nouveau) :**
     1. Appuyez sur le bouton **« Analyser le réseau »**
     2. Attendez que les appareils apparaissent dans la liste
     3. Sélectionnez votre appareil dans la liste
     4. L'adresse IP sera renseignée automatiquement

   - **Saisie manuelle :**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Appuyez sur **« Tester la connexion »** pour vérifier
5. Appuyez sur **« Enregistrer »**

**Formats d'adresse serveur :**

- Windows : `\\192.168.1.100\share`
- Linux/Mac : `smb://192.168.1.100/share`
- Avec port : `smb://192.168.1.100:445/share`

**Astuces :**

- Utilisez l'adresse IP (pas le nom d'hôte) pour plus de fiabilité
- Activez SMB v2/v3 sur le NAS pour la sécurité
- Port SMB par défaut : 445

**Dépannage :**
→ Voir [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.md)

---

## Comment se connecter à un serveur SFTP/FTP {#how-to-connect-to-sftpftp-server}

**Ce dont vous avez besoin :**

- Un serveur avec SSH (SFTP) ou FTP activé
- Le port 22 (SFTP) ou 21 (FTP) ouvert
- Nom d'utilisateur et mot de passe (ou clé pour SFTP)

**Étapes :**

1. **Appuyez sur le bouton « + »** sur l'écran principal
2. Sélectionnez **« SFTP / FTP »**
3. Choisissez le protocole : **SFTP** ou **FTP**
4. Renseignez les détails :

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Appuyez sur **« Connecter »**

**Avancé :**

- **Authentification par clé SSH :** actuellement non prise en charge (mot de passe uniquement)
- **Port personnalisé :** changez le numéro de port si le serveur utilise un port non standard

**Dépannage :**
→ Voir [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.md)

---

## Comment importer un partage Companion Windows {#how-to-import-a-windows-companion-share}

**Ce que c'est :** le compagnon est une fonctionnalité de [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (anciennement FastMediaSorter LITE) - le trieur de médias Windows gratuit du même auteur. Il partage les dossiers PC choisis via SFTP et exporte une connexion prête à l'emploi - aucune configuration manuelle de serveur, aucune saisie d'hôte/port/clé sur le téléphone. Vous l'apportez au téléphone en **scannant un code QR** sur l'écran du PC, ou en **important un fichier `.fmscfg`**.

**Disponible dans :** Standard, Photos, Legacy, XR/noLegal (le scan de code-barres nécessite un appareil photo ; la méthode par fichier fonctionne partout, y compris en VR)

> Vous préférez une version guidée avec captures d'écran ? Consultez le guide de scénario [Ouvrir les dossiers du PC en scannant un code](howto/scenario-companion-share-fr.md).

**Obtenir Fast Media Sorter for Windows :**

- Site web : [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publier des dossiers (guide) : [Comment publier des dossiers PC vers Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub : [dernière version](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installeur ou ZIP portable)
- winget : `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store : recherchez « FastMediaSorter LITE » (toujours répertorié sous l'ancien nom)

**Sur le PC :**

1. Installez et lancez **Fast Media Sorter for Windows**, ouvrez l'onglet **Partage** dans les paramètres.
2. Choisissez le(s) dossier(s) à partager - l'application démarre le serveur SFTP, génère les clés et configure le démarrage automatique toute seule.
3. Elle affiche un **code QR** à l'écran. Elle peut aussi **enregistrer un .fmscfg** si vous préférez un fichier.

**Sur le téléphone - Méthode A, scanner le code (la plus rapide) :**

1. **Appuyez sur le bouton « + »** sur l'écran principal.
2. Appuyez sur **« Importer par code-barres »** - il se trouve à côté des quatre cartes de type de ressource et dans l'en-tête du formulaire SFTP.
3. Pointez l'appareil photo vers le QR sur le PC (appuyez sur **Torche** dans une pièce sombre), puis confirmez la boîte de dialogue **Autoriser l'import**.
4. Terminé - une ressource en lecture seule apparaît par dossier partagé, avec la clé du serveur épinglée automatiquement.

**Sur le téléphone - Méthode B, importer le fichier :**

1. Sur le PC, utilisez **Enregistrer un .fmscfg** et transférez le fichier vers le téléphone (e-mail, Telegram ou un emplacement partagé).
2. **Appuyez sur « + »** -> **« SFTP / FTP »** -> **« Importer depuis un fichier »** et choisissez le fichier `.fmscfg`. S'il est arrivé en pièce jointe Telegram/e-mail, appuyez simplement sur la pièce jointe.
3. Confirmez la boîte de dialogue **Autoriser l'import** - les ressources en lecture seule apparaissent.

**Remarque :** le code QR et le fichier de configuration intègrent tous deux le mot de passe d'accès - traitez-les comme une clé, ne publiez pas la capture d'écran ni le fichier. L'entrée **Importer par code-barres** est masquée sur les appareils sans appareil photo et sur les casques VR ; utilisez la méthode B dans ce cas.

---

## Comment se connecter à un espace de stockage cloud {#how-to-connect-to-cloud-storage}

**Fournisseurs pris en charge :**

- Google Drive
- OneDrive
- Dropbox

**Étapes :**

1. **Appuyez sur le bouton « + »** sur l'écran principal
2. Sélectionnez **« Stockage cloud »**
3. Sélectionnez le fournisseur : **Google Drive**, **OneDrive** ou **Dropbox**
4. Appuyez sur le bouton **« Se connecter.. »**
5. Suivez le flux d'authentification du navigateur/de l'application
6. Accordez les autorisations requises
7. **Sélectionnez les dossiers** à synchroniser
8. Appuyez sur **« Terminé »**

**Remarques :**

- Les fichiers sont **diffusés en continu**, pas téléchargés
- Nécessite une connexion internet
- Les modifications se synchronisent automatiquement
- Vous pouvez vous déconnecter à tout moment : Modifier le dossier → Supprimer

**Confidentialité :**

- Aucun mot de passe stocké (utilise des jetons OAuth)
- Les jetons peuvent être révoqués dans les paramètres de sécurité de votre fournisseur cloud

---

## Vérifier la vitesse réseau

**Pris en charge pour :** SMB, SFTP, FTP, Cloud (Google Drive)

**Vérification automatique :**
Quand vous ajoutez une nouvelle ressource réseau, l'application lance automatiquement un test de vitesse en arrière-plan. Les résultats (vitesse de lecture/écriture) sont enregistrés dans les paramètres de la ressource.

**Vérification manuelle :**

1. Allez dans **Gérer les ressources**
2. Modifiez une ressource réseau (icône crayon)
3. Faites défiler jusqu'en bas
4. Appuyez sur le bouton **« Vitesse »**
5. Attendez environ 15 secondes pendant « Analyse de la vitesse.. »
6. Consultez les résultats :
   - **Vitesse de lecture (Mbps)**
   - **Vitesse d'écriture (Mbps)**
   - **Threads recommandés** (pour des performances optimales)

---

## Comment configurer les dossiers de tri rapide {#how-to-set-up-quick-sort-folders}

**Méthode 1 : depuis les Paramètres**

1. **Paramètres** → onglet **Gestion** → **Destinations de tri rapide**
2. Appuyez sur **« Ajouter au tri rapide »**
3. Sélectionnez un dossier existant dans la liste
4. Le dossier reçoit un numéro (0-9) et une couleur
5. Répétez pour jusqu'à 30 dossiers

**Méthode 2 : depuis les paramètres du dossier**

1. Écran principal → **Appui long sur un dossier**
2. Appuyez sur **« Modifier »** (icône crayon)
3. Activez **« Marquer pour le tri rapide »**
4. Appuyez sur **« Enregistrer »**

**Utiliser le tri rapide :**

En consultant des fichiers :

- Appuyez sur le **bouton numéroté** (0-9) sur le panneau de commandes
- OU appuyez sur le **coin inférieur gauche** (zone COPIER)
- OU appuyez sur le **coin inférieur centre** (zone DÉPLACER)

Le fichier est instantanément copié/déplacé vers ce dossier !

**Avec un clavier ou une télécommande TV :** connectez-en un et les boutons de destination reçoivent un badge chiffré - appuyez sur la touche numérique correspondante pour déclencher cette destination instantanément, sans avoir à toucher l'écran.

---

## Comment utiliser les zones tactiles {#how-to-use-touch-zones}

**Que sont les zones tactiles ?**

L'écran est divisé en 9 zones invisibles pour des actions rapides :

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Légende :**

1. **RETOUR** - Revenir à la liste de fichiers
2. **COPIER** - Copier le fichier vers une destination
3. **RENOMMER** - Renommer le fichier actuel
4. **PRÉCÉDENT** - Aller au fichier précédent
5. **DÉPLACER** - Déplacer le fichier vers une destination
6. **SUIVANT** - Aller au fichier suivant
7. **COMMANDE** - Ouvrir le menu de commandes
8. **SUPPRIMER** - Supprimer le fichier actuel
9. **LECTURE** - Démarrer/arrêter le diaporama

**Activer l'overlay (recommandé pour les débutants) :**

1. Paramètres → Lecteur
2. Activez **« Toujours afficher l'overlay des zones tactiles »**
3. Vous verrez désormais une grille semi-transparente

**Essayez :**

1. Ouvrez n'importe quelle photo
2. **Appuyez sur le coin supérieur droit** → Fichier suivant
3. **Appuyez sur le coin supérieur gauche** → Fichier précédent
4. **Appuyez sur le coin médian droit** → Supprimer le fichier
5. **Appuyez sur le coin médian gauche** → Copier le fichier

**Désactiver si ce n'est pas nécessaire :**
Paramètres → Lecteur → « Toujours afficher l'overlay des zones tactiles » = DÉSACTIVÉ

Utilisez ensuite les **boutons du panneau de commandes** à la place.

---

## Comment modifier des photos {#how-to-edit-photos}

**Opérations prises en charge :**

- Rotation (90°, 180°, 270°)
- Retournement (horizontal, vertical)
- Filtres (Niveaux de gris, Sépia, Négatif)
- Ajustement (Luminosité, Contraste, Saturation)

**Étapes :**

1. **Ouvrez une photo** dans la visionneuse plein écran
2. Appuyez sur le bouton **« Modifier »** (ou la zone tactile médiane gauche)
3. **Choisissez une opération :**
   - Rotation : appuyez sur l'icône de rotation
   - Retournement : appuyez sur l'icône de retournement
   - Filtre : sélectionnez dans la liste
   - Ajustement : utilisez les curseurs
4. Appuyez sur **« Enregistrer »**

**Remarques :**

- Le fichier original est **écrasé** (pas d'annulation possible !)
- Fonctionne pour les **fichiers locaux et réseau**
- Prend en charge : JPG, PNG, WEBP

---

## Comment créer un diaporama {#how-to-create-slideshow}

**Étapes :**

1. **Ouvrez n'importe quel dossier** de photos
2. Appuyez sur la **première photo** pour ouvrir la visionneuse
3. Appuyez sur le bouton **« Lecture »** (ou la zone tactile inférieure droite)
4. Le diaporama démarre automatiquement

**Personnaliser la vitesse :**

1. **Modifiez les paramètres du dossier :**
   - Écran principal → Appui long sur le dossier → Modifier
2. Changez l'**« Intervalle du diaporama »** :
   - Rapide : 2 secondes
   - Normal : 5 secondes
   - Lent : 10 secondes
3. Appuyez sur **« Enregistrer »**

**Commandes pendant le diaporama :**

- **Appuyez sur l'écran** → Pause/Reprendre
- **Glissez à gauche/droite** → Passer les fichiers
- **Appuyez sur « Arrêter »** → Quitter le diaporama

---

## Comment créer un diaporama avec musique de fond {#how-to-create-slideshow-with-background-music}

**Prérequis :**

- Au moins un dossier/une ressource avec des fichiers audio (MP3, FLAC, etc.)
- **Disponible dans :** Standard, Lite, Legacy, XR / noLegal (Photos n'a pas de prise en charge audio)

**Configuration :**

1. **Paramètres** → onglet **Média** → **Images**
2. Activez **« Lire de la musique pendant le diaporama »**
3. Appuyez sur le bouton **« Sélectionner une source musicale »**
4. Choisissez une ressource contenant vos fichiers musicaux
5. Appuyez sur **« Enregistrer »** ou fermez les paramètres

**Lecture du diaporama avec musique :**

1. **Ouvrez n'importe quel dossier** de photos/images
2. Appuyez sur la **première photo** pour ouvrir la visionneuse
3. Appuyez sur le bouton **« Lecture »** (ou la zone tactile inférieure droite)
4. Le diaporama démarre avec la musique de fond

**Comment ça marche :**

- La musique est jouée aléatoirement depuis la ressource musicale sélectionnée
- Quand un morceau se termine, le morceau aléatoire suivant démarre automatiquement
- La musique continue pendant les transitions d'images
- La musique s'arrête quand vous quittez le diaporama ou le mettez en pause

**Remarques :**

- La musique ne joue que pour les **images et les GIF** (pas pour les vidéos/l'audio)
- Quand le diaporama affiche une vidéo, la musique se met automatiquement en pause
- La musique reprend au retour aux images
- Fonctionne avec des sources musicales locales et réseau (SMB, SFTP, FTP)

**Personnaliser la sélection musicale :**

- Ajoutez plusieurs fichiers musicaux à votre dossier de ressource musicale
- L'application mélangera aléatoirement tous les fichiers audio
- Organisez la musique en sous-dossiers si la ressource musicale a l'option « Inclure les sous-dossiers » activée

**Dépannage :**

- Si aucune musique ne joue : vérifiez que la ressource musicale contient au moins un fichier audio
- Si la musique bégaie sur le réseau : utilisez un dossier local ou une connexion réseau plus rapide
- Pour la musique via SMB : assurez-vous que la ressource SMB utilise le protocole `file://` (voir TROUBLESHOOTING-fr.md)

---

## Comment protéger un dossier par code PIN {#how-to-protect-folder-with-pin}

**Étapes :**

1. Écran principal → **Appui long sur un dossier**
2. Appuyez sur **« Modifier »** (icône crayon)
3. Faites défiler jusqu'au champ **« Code PIN »**
4. Saisissez un **code PIN à 4-6 chiffres** (par ex. 1234)
5. Appuyez sur **« Enregistrer »**

**Maintenant :**

- L'ouverture de ce dossier requiert le code PIN
- Empêche l'accès non autorisé
- S'applique à la navigation et à la modification

**Retirer le code PIN :**

- Modifier le dossier → Effacer le champ PIN → Enregistrer

**Code PIN oublié ?**

- Aucune option de récupération (par conception, pour la sécurité)
- Vous devrez supprimer le dossier puis l'ajouter de nouveau

---

## Comment chiffrer un fichier avec FileDO {#how-to-encrypt-a-file-with-filedo}

Un conteneur FileDO est un fichier unique avec l'extension `.fd-sec` qui contient un autre fichier verrouillé par un mot de passe. C'est le format utilisé par l'application de bureau FileDO, si bien qu'un conteneur créé ici s'ouvre dans FileDO, et inversement.

**Activer les commandes :** **Paramètres** → onglet **Gestion** → **Opérations de chiffrement FileDO**. Ouvrir un conteneur fonctionne que cet interrupteur soit activé ou non.

**Chiffrer un fichier :**

1. Dans Parcourir, ouvrez le menu **⋮** du fichier.
2. Appuyez sur **Chiffrer avec FileDO**.
3. Saisissez le mot de passe deux fois et confirmez.
4. Le conteneur apparaît à côté du fichier sous le nom `<nom>.fd-sec`. Le fichier original reste intact - supprimez-le vous-même si vous n'en avez plus besoin.

**Déchiffrer un fichier :** ouvrez le menu **⋮** du fichier `.fd-sec`, appuyez sur **Déchiffrer avec FileDO** et saisissez le mot de passe. Le fichier restauré apparaît à côté du conteneur.

**Ouvrir un conteneur sans le restaurer :** appuyez sur le fichier `.fd-sec` dans n'importe quel dossier affichant tous les types de fichiers. L'application demande uniquement le mot de passe et ouvre le fichier qu'il contient dans la visionneuse. La copie déchiffrée reste dans le stockage privé de l'application et est supprimée quand vous revenez à la liste. Cochez **Se souvenir du mot de passe et l'essayer sur chaque fichier .fd-sec** pour éviter l'invite la prochaine fois.

**Là où ça fonctionne :** dossiers de l'appareil, dossiers choisis via le sélecteur de dossier système, et partages SMB, SFTP et FTP. Sur un dossier issu du sélecteur ou un partage réseau, le fichier est traité comme une copie privée, le résultat est écrit sous un nom temporaire, relu et vérifié, puis seulement renommé à sa place définitive - un fichier existant n'est jamais écrasé.

**Si ça ne s'ouvre pas :** le message indique trois causes possibles - un mot de passe erroné, un fichier qui n'a jamais été un conteneur, ou un conteneur qui a été modifié. Elles ne peuvent pas être distinguées. Un conteneur contenant un programme ou un script ne s'ouvre pas.

**Mot de passe oublié ?** Il n'existe aucun moyen de le récupérer. Un mot de passe vide ne fait que masquer le fichier à un simple coup d'œil.

---

## Comment gérer les dossiers (sélectionner, copier, déplacer)

Quand les sous-dossiers sont affichés comme des éléments séparés dans la liste, une ligne de dossier se comporte comme une ligne de fichier.

**Activer les lignes de dossier :** **Paramètres** → **Général** → **Afficher les sous-dossiers séparément**. Le même interrupteur existe par ressource dans l'éditeur de ressource.

**Étapes :**

1. Appuyez sur la case à cocher d'une ligne de dossier, ou faites un appui long sur la ligne, pour sélectionner un seul dossier. Un appui court ouvre toujours le dossier.
2. Utilisez le menu **⋮** de la ligne, ou la barre d'action de sélection, pour choisir **Copier**, **Déplacer**, **Renommer** ou **Supprimer**.
3. Choisissez la destination. Les fichiers et dossiers d'une même sélection voyagent ensemble en une seule opération.
4. La destination reçoit toute la structure - chaque sous-dossier et fichier à l'intérieur du dossier source.

**Entre types de ressources :** un dossier peut être copié ou déplacé entre l'appareil, SMB, SFTP, FTP et les ressources cloud - la structure est recréée du côté récepteur.

**Ce qui est refusé, et pourquoi :** une destination à l'intérieur du dossier lui-même, ou l'emplacement actuel du dossier, est rejetée avant que quoi que ce soit ne soit copié ; une destination choisie via le sélecteur de dossier système qui n'a pas de chemin de fichier réel ne peut pas recevoir de dossiers. Le message indique la raison, pour que vous puissiez choisir une autre destination.

**Annulation :** un transfert de dossier affiche une progression et peut être arrêté. Tout ce qui a déjà été écrit reste à destination - vérifiez le dossier avant de recommencer. Un déplacement ne supprime chaque élément source qu'après le succès de sa copie, si bien que rien n'est perdu entre-temps.

**L'envoyer en arrière-plan :** vous n'êtes pas obligé de rester à regarder la boîte de dialogue de progression. Fermez-la et le transfert continue de s'exécuter, restant visible dans Parcourir sous forme d'une bande en bas de l'écran montrant l'opération, le pourcentage et le fichier en cours. Appuyez sur cette bande pour rouvrir la boîte de dialogue complète de progression, annulation comprise.

---

## Comment vider la corbeille {#how-to-empty-trash}

Les fichiers supprimés vont dans des dossiers `.trash/` et y restent jusqu'à ce qu'ils soient vidés manuellement.

**Méthode 1 : vider toute la corbeille**

1. **Paramètres** → onglet **Gestion** → **Suppression de fichiers et corbeille**
2. Appuyez sur **« Vider la corbeille »**
3. Confirmez la suppression
4. Tous les dossiers `.trash/` de toutes les ressources sont vidés

**Méthode 2 : par dossier**

1. Utilisez une application de gestionnaire de fichiers
2. Accédez au dossier (par ex. `/storage/emulated/0/DCIM/Camera`)
3. Trouvez le sous-dossier `.trash/`
4. Supprimez manuellement

**Attention :** Il s'agit d'une **suppression définitive** ! Les fichiers ne peuvent pas être récupérés.

---

## Comment sauvegarder les paramètres {#how-to-backup-settings}

**Exporter les paramètres :**

1. **Paramètres** → onglet **Général** → **Sauvegardes, restauration et export des paramètres**
2. Appuyez sur **« Exporter tous les paramètres vers un fichier »**
4. Choisissez un emplacement (par ex. Téléchargements)
5. Le fichier est enregistré sous le nom `fastmediasorter_backup.xml`

**Restaurer les paramètres :**

1. **Paramètres** → onglet **Général** → **Sauvegardes, restauration et export des paramètres**
2. Appuyez sur **« Importer les paramètres depuis un fichier »**
4. Sélectionnez le fichier de sauvegarde
5. Appuyez sur **« Restaurer »**
6. L'application redémarre avec les paramètres restaurés

**Ce qui est inclus :**
✅ Dossiers de tri rapide
✅ Préférences d'affichage
✅ Intervalles de diaporama
✅ Identifiants réseau (chiffrés)
✅ Favoris
✅ Paramètres du mode sécurisé

**NON inclus :**
❌ Cache des vignettes
❌ Contenu de la corbeille  

---

## Comment afficher les fichiers texte et PDF {#how-to-view-text-and-pdf-files}

**1. Activer la prise en charge :**

1. **Paramètres** → onglet **Média** → **Documents**
2. Activez **« Prise en charge des fichiers texte (.txt, .md, .log, .json, .xml) »** et **« Prise en charge des documents PDF »**
3. **Relancez l'analyse** de vos dossiers pour trouver les nouveaux fichiers.

**2. Filtrer par type de média :**

1. Appuyez sur l'**icône de filtre** (entonnoir) sur l'écran principal (en haut à droite).
2. Utilisez les cases à cocher pour sélectionner les types de médias :
   - Images
   - Vidéos
   - Audio
   - GIF
   - **Texte** (Nouveau)
   - **PDF** (Nouveau)
3. Appuyez sur **« Appliquer »** pour ne voir que les fichiers sélectionnés.

**3. Visionneuse de texte :**

- Appuyez sur n'importe quel fichier **.txt, .md, .log, .json, .xml**.
- **Faites défiler** pour lire.
- **Copier le texte :** appui long pour sélectionner et copier.

**4. Visionneuse PDF (nouvelles fonctionnalités) :**

- Appuyez sur n'importe quel fichier **.pdf**.
- **Barre de contrôle de navigation (en bas) :**
  - **Précédent/Suivant :** grands boutons sur les bords.
  - **Zoom avant (+) :** agrandir la page.
  - **Zoom arrière (-) :** réduire la page.
- **Gestes :**
  - **Glisser vers le HAUT :** aller à la page suivante.
  - **Glisser vers le BAS :** aller à la page précédente.
  - **Pincer :** zoomer/dézoomer naturellement.
  - **Double-appui :** réinitialiser le zoom.
  - **Le zoom est conservé :** la page suivante s'ouvre au zoom et à la position où vous en étiez ; un double-appui ramène la page entière.
- **Déplacement :** faites glisser pour vous déplacer une fois zoomé.
- **Sélectionner du texte par appui long (Android 15+) :** appuyez et maintenez un mot pour le sélectionner directement depuis la couche de texte propre à la page - pas de passage OCR, pas d'attente. Si le même mot apparaît plusieurs fois sur la page, c'est celui sous votre doigt qui est sélectionné, pas le premier. Faites glisser les poignées pour étendre la sélection, puis copiez ou traduisez-la.

---

## Comment lire des livres électroniques (EPUB) {#how-to-read-e-books-epub}

**Prérequis :**

- **Paramètres** → onglet **Média** → **Documents** → **Prise en charge des livres électroniques EPUB** doit être activée (activée par défaut)
- Format pris en charge : `.epub` (sans DRM)

**Fonctionnalités :**

- **Navigation par chapitre :** glissez à gauche/droite ou utilisez les boutons du panneau de commandes
- **Table des matières :** appuyez sur l'icône de liste <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> pour aller directement à un chapitre
- **Taille de police :** ajustable (6px - 144px, par défaut 18px)
- **Recherche :** trouver du texte dans le livre en cours
- **Thèmes :** s'adapte automatiquement au mode clair/sombre

**Commandes :**

1. **Ouvrez un fichier EPUB** depuis la liste de fichiers
2. **Appuyez sur l'écran** pour basculer le panneau de commandes
3. **Utilisez les commandes en bas :**
   - `Précédent` / `suivant` : naviguer entre les chapitres
   - `- A` / `+ A` : diminuer/augmenter la taille de police
   - `Recherche` <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> : rechercher du texte
   - `TDM` <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> : ouvrir la table des matières
4. **Geste de glissement :** changer de chapitre naturellement

**Remarque :** Fonctionne parfaitement avec les fichiers locaux et les flux réseau (SMB/SFTP/Cloud). Les gros livres (>50 Mo) sur des réseaux lents peuvent prendre quelques secondes à charger au départ.

---

## Comment ouvrir des fichiers réseau dans des applications externes {#how-to-open-network-files-in-external-apps}

**Disponible pour :** fichiers SMB, SFTP, FTP

**Cas d'usage :** vous voulez ouvrir un document, une photo ou une vidéo depuis votre lecteur réseau dans une application externe spécialisée (par ex. MS Office, Adobe Acrobat, VLC Player).

**Étapes :**

1. **Parcourez jusqu'au fichier** sur votre ressource réseau
2. **Appuyez sur le fichier** pour l'ouvrir dans le lecteur/la visionneuse
3. **Appuyez sur le bouton ⓘ (Infos)** dans la barre d'outils supérieure
4. **Appuyez sur le bouton « Télécharger et ouvrir »**
5. **Attendez le téléchargement** - une boîte de dialogue de progression affiche le pourcentage
6. **Choisissez une application** dans le sélecteur Android

**Ce qui se passe :**

- Le fichier est téléchargé vers votre dossier `Downloads`
- La progression est affichée dans une boîte de dialogue (0-100 %)
- Une fois le téléchargement terminé, Android affiche le sélecteur d'application
- Vous pouvez ouvrir le fichier dans n'importe quelle application compatible

**Protocoles pris en charge :**

- ✅ Partages réseau SMB/CIFS
- ✅ Serveurs SFTP
- ✅ Serveurs FTP
- ❌ Stockage cloud (pas encore implémenté)

**Astuces :**

- Les fichiers téléchargés restent dans le dossier `Downloads`
- Vous pouvez les supprimer manuellement plus tard via un gestionnaire de fichiers
- Fonctionne avec tous les types de fichiers (images, vidéos, documents, etc.)
- Pour les gros fichiers, le téléchargement peut prendre plusieurs minutes

**Exemples d'usage :**

- Modifier un document réseau dans MS Word
- Lire une vidéo réseau dans VLC Player
- Consulter un PDF réseau dans Adobe Acrobat
- Partager une photo réseau via une application de messagerie

---

## Comment afficher les paroles d'une chanson {#how-to-view-song-lyrics}

**Prérequis :**

- Un fichier audio (MP3, FLAC, etc.) avec les métadonnées Artiste et Titre.
- Une **connexion internet** est nécessaire (utilise api.lyrics.ovh).

**Étapes :**

1. **Lisez un fichier audio** dans le lecteur plein écran.
2. Appuyez sur le bouton **« Paroles »** dans le panneau de commandes supérieur (ou le menu de commandes).
   - *Remarque : le bouton n'est visible que pour les fichiers audio.*
3. Attendez la fin de la recherche.
4. Les paroles s'affichent dans une boîte de dialogue défilante.

**Logique de recherche :**

1. L'application recherche par les tags **Artiste + Titre**.
2. Si les tags sont manquants, elle essaie d'analyser le **nom de fichier**.

---

## Traduction automatique {#auto-translation}

Traduisez automatiquement le texte des images, PDF et fichiers texte : **Tesseract** lit le texte, Google ML Kit le traduit.

**Fonctionnalités clés :**

- **Un seul moteur de lecture :** **Tesseract** lit le texte latin et cyrillique (anglais, russe, ukrainien, bulgare, biélorusse) ; Google ML Kit traduit le résultat et identifie sa langue.
- **Hors ligne :** fonctionne entièrement sur l'appareil (après le téléchargement initial du modèle).
- **Overlay intelligent :** le texte traduit se superpose au texte original en paragraphes lisibles.

**Configuration :**

1. **Paramètres** → onglet **Média** → **Autre**
2. Activez **« Activer la traduction »**
3. Sélectionnez la **langue source** :
   - **« Auto » :** lit le texte avec le modèle anglais, puis détecte la langue de ce qui a été lu pour la traduction.
   - **Langue spécifique :** lit avec le modèle de cette langue - choisissez-la pour du texte cyrillique (par ex. « russe »).
4. Sélectionnez la **langue cible** (par ex. anglais).

**Comment l'utiliser :**

1. Ouvrez une **image**, un **PDF** ou un fichier **texte**.
2. Appuyez sur l'écran pour afficher le **panneau de commandes**.
3. Appuyez sur le bouton **« Traduire »** (icône A→文).
4. **Première utilisation :**
   - Confirmez le téléchargement du modèle de texte pour la langue source.
   - Confirmez le téléchargement du modèle de traduction pour la paire de langues.
5. Le texte traduit apparaît dans un overlay.

**Remarque :** La première utilisation d'une langue charge son modèle de texte, ce qui ajoute un court délai.

## Widgets intelligents pour l'écran d'accueil {#home-screen-smart-widgets}

**Disponible dans :** toutes les versions - l'ensemble de widgets est présent dans tous les builds ; chaque widget suit sa propre capacité, si bien que le widget d'enregistreur vocal nécessite un build avec prise en charge du microphone (pas Lite ni Photos), tandis que le cadre photo et les widgets de ressource fonctionnent partout

**Chemin rapide**

1. Allez sur votre écran d'accueil Android, faites un appui long, et sélectionnez **Widgets**.
2. Faites glisser un widget **FastMediaSorter** (comme l'enregistreur vocal rapide 1×1 ou l'OCR caméra) vers votre écran.
3. Configurez le dossier de destination et les paramètres de capture, puis appuyez sur **Enregistrer**.
4. Utilisez le widget pour exécuter des tâches en un appui directement depuis votre écran d'accueil.

**Parcours du scénario**

- Utilisez des widgets 1×1 comme icônes de lancement dédiées pour démarrer instantanément des actions en arrière-plan (par ex. appuyez une fois pour démarrer l'enregistrement vocal, appuyez de nouveau pour l'enregistrer sur votre NAS).
- Configurez un **widget Opérations planifiées** pour surveiller les transferts de fichiers en arrière-plan ou déclencher une opération « Tout exécuter ».
- Placez un **widget Cadre photo aléatoire** pour afficher un diaporama tournant de photos de famille récupérées directement depuis un partage SMB.

**Quand c'est utile**

- Vous voulez des raccourcis rapides sur votre écran d'accueil pour les captures quotidiennes (reçus, mémos vocaux) sans ouvrir l'interface principale de l'application.
- Vous avez besoin de widgets clairs pour contrôler les médias ou déclencher des opérations planifiées instantanément.

**À éviter**

- N'essayez pas d'ajouter des widgets si votre lanceur Android restreint la création de widgets personnalisés.

---

## Comment utiliser l'application comme écran d'accueil {#how-to-use-the-app-as-your-home-screen}

FastMediaSorter peut prendre le contrôle de l'écran d'accueil de votre appareil et afficher son propre bureau à la place - vos dossiers, une horloge, la météo, vos applications et une barre des tâches sur un bord. Si vous avez déjà utilisé un bureau Windows, cela vous semblera familier : les choses restent où vous les mettez, et un bouton Démarrer ouvre le menu. C'est ce qu'on appelle le mode lanceur, et il n'est présent que dans les builds **Standard** et **noLegal**.

**L'activer :**

1. Ouvrez **Paramètres → Général** et activez **Faire de cette application l'écran d'accueil**.
2. Android vous demande de confirmer. Sur Android 10 et versions ultérieures, c'est une seule question - « Autoriser FastMediaSorter à être votre application d'accueil ? » - il suffit donc d'accepter. Sur les versions plus anciennes, le choix classique apparaît la prochaine fois que vous appuyez sur Accueil : choisissez FastMediaSorter et appuyez sur **Toujours**, ou **Une seule fois** si vous voulez seulement essayer pour l'instant.
3. Appuyez sur Accueil. Le bureau apparaît, déjà rempli d'environ une douzaine d'éléments utiles - une horloge, la météo, vos dossiers, une zone de recherche - de sorte que le premier jour n'est pas une grille vide.

Sur une installation toute neuve, il existe un raccourci : cochez **Utiliser comme écran d'accueil** sur la première page d'accueil. Cela n'interrompt pas la configuration par une boîte de dialogue système - la confirmation d'Android apparaît la première fois que vous ouvrez ensuite **Paramètres → Général**.

**Ce qui se trouve sur le bureau :**

| Type de cellule | Ce qu'elle fait |
|-----------|--------------|
| Raccourci de ressource | Ouvre un dossier que vous avez ajouté - et vous choisissez s'il s'ouvre en navigation, diaporama ou lecture |
| Gadget | Une horloge avec les secondes (appuyez pour les alarmes), la météo de votre lieu, ce qui est en cours de lecture, un traducteur, et deux douzaines d'autres |
| Raccourci d'application | Lance n'importe quelle application installée ; l'appui long affiche les actions rapides propres à cette application |
| Cellule de contact | Ouvre la fiche d'une personne, l'appelle, lui envoie un SMS, ou ouvre sa conversation de messagerie |
| Widget d'application | Les mêmes widgets que l'application propose pour l'écran d'accueil Android, placés ici à la place |

**La barre des tâches et le menu Démarrer :**

- La barre des tâches se trouve le long du bord inférieur et contient le bouton Démarrer, les applications récemment utilisées, celles que vous avez épinglées, et une petite zone avec l'horloge, la batterie, le réseau et le signal SIM.
- Vous la préférez en haut ? **Paramètres → Général → Paramètres du lanceur système → Barre des tâches → Position de la barre des tâches** bascule entre **Bas** et **Haut**. Le menu Démarrer suit la barre et se déploie vers le bas quand la barre est en haut.
- Le bouton Démarrer ouvre le menu : ouvrir FastMediaSorter, vos ressources, ajouter une ressource, les paramètres Android, les paramètres de l'application, les paramètres du lanceur, modifier le contenu du bureau, et à la fin redémarrer, éteindre et **Quitter le mode lanceur**. Redémarrer et éteindre ne fonctionnent que si votre appareil permet à une application ordinaire de le faire - sur la plupart des téléphones, ils ne feront simplement rien.

**Vos applications :** la grille d'applications regroupe les applications en sections, chacune avec un petit en-tête. Appuyez sur un en-tête pour réduire une section que vous ouvrez rarement ; les en-têtes réduits se rangent les uns à côté des autres, de sorte que le bureau devient plus court au lieu de laisser des vides. Un bureau tout neuf divise en deux les applications qu'il propose au départ : une section **Google** pour les applications Google que vous avez déjà installées, et une section **Applications** pour les vôtres - messageries, jeux et tout ce que vous avez mis d'autre sur l'appareil. Aucune application ne se retrouve dans les deux. Faites un appui long sur n'importe quelle application de la liste pour **Placer sur le bureau** et **Épingler à la barre des tâches**.

**La réorganiser :**

- Faites un appui long sur une case vide du bureau. Quatre choix apparaissent : **Ajouter un élément..**, **Modifier le bureau**, **Fond d'écran**, **Paramètres du lanceur**. La nouvelle cellule atterrit exactement sur la case sur laquelle vous avez appuyé.
- **Ajouter un élément..** ouvre un sélecteur : une application, une fonctionnalité, un de vos dossiers, un flux radio, une personne, une action système, une opération planifiée, un gadget, ou une action. Parmi les gadgets figurent la carte « en cours de lecture » - elle affiche ce qui est en cours de lecture sur l'appareil et vous mène à ce lecteur en un appui - et la cellule traducteur.
- **Modifier le bureau** active le mode édition, identique à **Modifier le contenu du bureau** dans le menu Démarrer. Pendant l'édition : faites glisser une cellule pour la déplacer, faites glisser la poignée d'angle d'un gadget pour le redimensionner, appuyez sur **+** pour ajouter quelque chose, et choisissez **Retirer du bureau** sur une cellule pour l'enlever. Appuyez sur **Terminé** quand vous avez fini.
- Vous partagez l'appareil avec quelqu'un ? Activez **Verrouiller le bureau** dans les paramètres du lanceur - l'appui long ne fait alors plus rien, si bien que la disposition ne peut pas être perturbée par accident.
- D'autres applications peuvent placer leurs propres raccourcis ici, exactement comme elles le feraient sur n'importe quel autre écran d'accueil.

**Le portrait et le paysage sont deux bureaux distincts.** Ce que vous organisez à la verticale n'est pas ce que vous obtenez quand vous tournez l'appareil sur le côté - chaque orientation garde sa propre disposition et ses propres sections réduites. L'application le mentionne une fois, la première fois que vous faites pivoter un bureau que vous avez organisé. Les paramètres eux-mêmes - position de la barre des tâches, densité, fond d'écran - sont partagés entre les deux.

**Faire tenir plus, ou moins, à l'écran :** **Paramètres → Général → Paramètres du lanceur système → Bureau → Densité de la grille** propose **Aéré**, **Standard**, **Dense** et **Très dense** - des cellules plus spacieuses, ou plus de raccourcis par écran.

**Revenir à votre ancien écran d'accueil** - l'une de ces trois méthodes :

- Ouvrez le menu Démarrer, choisissez **Quitter le mode lanceur**, et confirmez.
- Désactivez **Faire de cette application l'écran d'accueil** dans **Paramètres → Général**.
- Allez directement à la propre liste d'applications d'accueil d'Android : **Paramètres → Général → Paramètres du lanceur système → Système → Changer l'écran d'accueil**.

Votre disposition de bureau est conservée dans tous les cas, donc réactiver le mode la restaure exactement telle que vous l'avez laissée.

**Un avertissement honnête.** Quelques appareils refusent de mémoriser le choix. Certains autoradios Android bon marché et d'autres boîtiers Android intégrés réimposent leur écran d'accueil d'usine à chaque démarrage, quel que soit votre choix. C'est le firmware de l'appareil lui-même qui vous contredit, pas un défaut de l'application, et aucune application ne peut contourner cela. Si le vôtre se comporte ainsi, choisissez de nouveau FastMediaSorter comme application d'accueil après un redémarrage - et si cela ne tient toujours pas, cet appareil ne le permet tout simplement pas.

---

## Comment utiliser FMS sur un boîtier Android TV {#how-to-use-fms-on-android-tv-box}

FastMediaSorter fonctionne sur n'importe quel boîtier Android TV ou set-top box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, boîtiers Android génériques). Aucun écran tactile requis - l'application est entièrement utilisable via une télécommande TV ou un clavier Bluetooth.

**Ce dont vous avez besoin :**

- Un boîtier Android TV avec Android 8.0+ (Standard/Lite/Photos) ou Android 6.0+ (version Legacy)
- Une télécommande TV avec pavé directionnel, ou un clavier Bluetooth
- Optionnel : un NAS domestique (SMB), une clé USB, ou une carte SD avec des médias

**Navigation avec une télécommande TV :**

| Bouton | Action |
|--------|--------|
| Pavé directionnel Haut/Bas/Gauche/Droite | Déplacer le focus entre les éléments |
| OK / Entrée | Ouvrir l'élément ou confirmer |
| Retour | Revenir à l'écran précédent |
| Retour arrière | Remonter d'un dossier dans Parcourir |
| Rouge | Supprimer le(s) fichier(s) sélectionné(s) |
| Vert | Copier le(s) fichier(s) sélectionné(s) |
| Jaune | Déplacer le(s) fichier(s) sélectionné(s) |
| Bleu | Renommer le fichier sélectionné |
| Chaîne +/- | Fichier précédent/suivant dans le lecteur |

**Étapes :**

1. Installez l'application depuis Google Play ou installez un APK en sideload. Le build Standard est recommandé.
2. Sur l'écran principal, appuyez sur **OK** sur le bouton (+) pour ajouter une ressource.
3. Choisissez **Dossier local** pour le stockage USB/SD, ou **Dossier réseau** pour vous connecter à un NAS via SMB.
4. Après avoir ajouté la ressource, naviguez dedans avec le pavé directionnel + OK pour parcourir les fichiers.
5. Ouvrez n'importe quel fichier vidéo, image ou audio - le lecteur fonctionne entièrement à la télécommande.
6. Pour démarrer un diaporama, ouvrez un dossier d'images et allez sur le bouton **Diaporama** dans la barre de commandes.
7. Pour ajouter de la musique de fond au diaporama, allez dans **Paramètres → Média → Images**, activez **Lire de la musique pendant le diaporama**, et sélectionnez votre ressource musicale.

**Astuces :**

- Maintenez le pavé directionnel Haut/Bas pour accélérer le défilement dans les longues listes de fichiers.
- Appuyez sur **F1** sur un clavier Bluetooth pour ouvrir une référence de raccourcis propre à l'écran en cours.
- Les touches de couleur de la télécommande TV peuvent être réattribuées dans **Paramètres → Gestion → Commandes et raccourcis clavier**.

---

## Comment enregistrer votre écran {#how-to-record-your-screen}

**Disponible dans :** Standard, XR/noLegal

**Étapes :**

1. Démarrez-le depuis le menu contextuel de l'écran principal (**Enregistrement vidéo de l'écran**), le panneau de lancement rapide, ou l'action **Démarrer l'enregistrement de l'écran** du geste de bord.
2. Confirmez l'invite Android pour partager votre écran ou uniquement cette application - elle apparaît à chaque démarrage d'un enregistrement et ne peut pas être ignorée.
3. Une petite pastille dans le coin affiche **Enregistrement de l'écran**, avec des commandes pause/reprendre et arrêter. Une notification propose aussi **Arrêter**.
4. Appuyez sur **Arrêter** quand vous avez terminé.

**Ce qui se passe :**

- L'enregistrement capture tout ce qui se trouve à l'écran, y compris les autres applications vers lesquelles vous basculez, avec le son.
- La vidéo finale est enregistrée dans le dossier Films de votre appareil.

**Remarque :** L'étape de confirmation Android est une protection système pour tout ce qui enregistre votre écran - ce n'est pas quelque chose que l'application peut désactiver.

---

## Comment enregistrer une note vocale {#how-to-record-a-voice-note}

**Disponible dans :** Standard, Legacy, XR / noLegal

**Étapes :**

1. Démarrez un enregistrement depuis l'élément **Enregistrement vocal** du menu contextuel, le widget d'écran d'accueil **Enregistreur rapide**, ou l'action **Démarrer l'enregistrement audio** du geste de bord.
2. Parlez - un indicateur **Enregistrement..** (ou une pastille flottante par-dessus l'application au premier plan) montre qu'il est en cours.
3. Appuyez sur **Arrêter et enregistrer** (ou appuyez de nouveau sur le widget/geste) pour terminer.

**Ce qui se passe :**

- L'enregistrement est sauvegardé vers la destination microphone que vous avez choisie dans les Paramètres, ou vers le dossier Enregistrements de votre appareil si aucune n'est définie.
- Démarrer une note vocale depuis le widget ou le geste de bord fonctionne même pendant l'utilisation d'une autre application - une petite commande flottante reste au premier plan pour que vous puissiez l'arrêter sans revenir à l'application.

**Où définir le dossier d'enregistrement :** Paramètres → Gestion → Enregistreur vocal.

---

## Comment utiliser l'appareil photo intégré {#how-to-use-the-in-app-camera}

**Disponible dans :** Standard, Lite, Photos (photo uniquement), Legacy, XR/noLegal

**Étapes :**

1. Dans Parcourir, ouvrez la barre d'outils ou le menu contextuel et appuyez sur **Capturer avec l'appareil photo** (photo) ou **Enregistrer une vidéo**.
2. Basculez entre **Photo** et **Vidéo** directement sur l'écran de l'appareil photo si vous changez d'avis.
3. Réglez votre zoom avec une puce prédéfinie (0.5x/1x/2x..) ou le curseur en dessous - les deux restent synchronisés.
4. Appuyez sur le bouton de format pour cadrer l'image - **4:3**, **16:9** ou **Plein écran**. Le viseur lui-même change, donc ce que vous voyez est ce que sera la photo enregistrée, et le choix est mémorisé la prochaine fois que vous ouvrez l'appareil photo (16:9 jusqu'à ce que vous le changiez).
5. Appuyez sur le bouton de scénario de prise de vue pour choisir comment la photo est prise - normal, nuit, portrait, selfie, macro, sport ou document. Macro passe à l'objectif dédié de mise au point rapprochée, selfie bascule vers la caméra avant, sport garde un temps d'exposition court pour figer le mouvement, et document est réglé pour photographier des pages et des écrans plats. Seuls les scénarios que votre appareil peut réellement produire sont listés, celui actif est nommé sur le bouton, et changer d'objectif manuellement ramène l'appareil photo en mode normal.
6. Appuyez sur le déclencheur (ou le bouton d'enregistrement) pour capturer. Le résultat s'enregistre directement dans la ressource - locale ou réseau - que vous parcouriez.

**Astuces :**

- Appuyez n'importe où dans le viseur pour faire la mise au point et régler l'exposition à cet endroit - un petit cercle indique où.
- L'action **Démarrer l'enregistrement vidéo** du geste de bord ouvre l'appareil photo déjà en mode Vidéo et démarre l'enregistrement dès que l'aperçu est prêt - rapide, mais ce raccourci précis enregistre dans le dossier Films de votre appareil plutôt que dans la ressource parcourue.
- Activez **Géolocaliser les photos** à côté des paramètres de l'appareil photo pour intégrer la position GPS dans chaque JPEG capturé - c'est désactivé jusqu'à ce que vous l'activiez. **Infos du fichier** affiche ensuite la date de capture et la position GPS EXIF de la photo sous forme de lien cliquable qui s'ouvre dans votre application de cartes ou votre navigateur.

**Où trouver les paramètres de l'appareil photo :** Paramètres → Gestion → Photographie.

---

## Comment trouver et supprimer les fichiers en double {#how-to-find-and-delete-duplicate-files}

**Étapes :**

1. Ouvrez un dossier dans Parcourir, puis ouvrez le **menu contextuel** <img src="icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> dans la barre d'outils.
2. Appuyez sur **Trouver les doublons** pour vérifier vous-même les correspondances, ou sur **Trouver et supprimer les doublons** pour les supprimer immédiatement.
3. Pour **Trouver les doublons**, l'application présélectionne chaque copie sauf la plus ancienne de chaque groupe - ajustez la sélection, puis appuyez sur **Supprimer la sélection** et confirmez.
4. **Trouver et supprimer les doublons** supprime les mêmes copies présélectionnées immédiatement après l'analyse, sans étape de confirmation - utilisez d'abord **Trouver les doublons** si vous voulez vérifier avant que quoi que ce soit ne soit supprimé.

**Nettoyer par taille à la place :**

1. Depuis le même menu contextuel, appuyez sur **Supprimer par taille..**
2. Choisissez **Plus petit que** ou **Plus grand que**, définissez une taille, et appuyez sur **Analyser**.
3. Vérifiez le nombre et l'espace que cela libérerait, puis appuyez sur **Supprimer les fichiers** pour confirmer.

**Remarques :**

- L'analyse fait correspondre les fichiers par contenu en trois passes - taille, puis un hachage rapide, puis une vérification SHA-256 complète - de sorte que les doublons renommés sont tout de même repérés.
- La suppression par taille montre l'espace qui sera libéré avant que quoi que ce soit ne soit supprimé ; les sources réseau et cloud sautent la corbeille, donc cette suppression est immédiate et définitive.

---

## Comment consulter vos statistiques d'utilisation {#how-to-view-your-usage-statistics}

**Étapes :**

1. Allez dans **Paramètres → Général → Collecte de statistiques** et activez-la.
2. Appuyez sur **Statistiques** (elle apparaît juste sous l'interrupteur) pour ouvrir le tableau de bord.

**Ce que vous verrez :**

- Des cartes résumées pour les fichiers triés, l'espace libéré et le temps passé à lire des médias.
- Une répartition par type (images, vidéos, audio, documents..).
- Des sections repliables avec plus de détails : opérations, capture, visionnage, édition, sources et usage général.

**Partager un rapport :**

- **Envoyer à l'auteur** ouvre votre application e-mail avec un résumé en pièce jointe, adressé au développeur.
- **Exporter** partage le même résumé via la feuille de partage Android habituelle, pour que vous puissiez l'enregistrer ou l'envoyer où vous voulez.

**Remarque :** Tout reste sur votre appareil jusqu'à ce que vous choisissiez de l'envoyer ou de l'exporter - voir la FAQ pour les détails sur la confidentialité.

---

## Comment utiliser une carte SD ou un lecteur connecté {#how-to-use-an-sd-card-or-connected-drive}

Une carte mémoire ou une clé USB montée par le téléphone héberge des ressources exactement comme le stockage interne.

**Étapes :**

1. Ouvrez **Ajouter une ressource** et commencez à ajouter un dossier local. La section **Support amovible** n'apparaît que lorsque quelque chose est connecté, et liste chaque volume avec son nom et son espace libre.
2. Appuyez sur le volume. Si l'application ne peut pas y accéder par chemin, elle explique pourquoi et ouvre le sélecteur de dossier système - choisissez le même volume là-bas et accordez l'accès au dossier voulu.
3. La ressource rejoint la liste avec une icône de support amovible, de sorte qu'une ressource de carte est reconnaissable d'un coup d'œil.

**Déplacement et copie :** des dossiers entiers voyagent vers une carte et inversement avec toute leur structure de sous-dossiers, de la même manière qu'entre l'appareil et une ressource réseau.

**Pas assez de place :** une copie ou un déplacement qui ne rentre pas est refusé avant même de commencer, et le message indique le support et l'espace manquant - libérez de l'espace ou choisissez une autre destination.

**Quand le support est éjecté :** ses ressources sont marquées indisponibles plutôt que supprimées. Reconnectez la carte et elles fonctionnent de nouveau sans avoir à être reconfigurées.

**Sur Android 6 :** le système ne signale pas les volumes montés aux applications, donc la section support amovible reste vide sur ces appareils.

---

## Comment reconnecter un dossier ajouté par chemin direct {#how-to-reconnect-a-folder-added-by-direct-path}

Un dossier que vous avez ajouté en saisissant ou en parcourant son chemin peut afficher vos photos, vidéos et musique, mais aucun de vos documents. Ce n'est pas une analyse qui les aurait ratés : un build store ne lit les fichiers texte, PDF et livres électroniques que via un dossier connecté avec le sélecteur système. Reconnecter fait pointer la même ressource vers le même dossier via ce sélecteur, et les documents apparaissent.

**Étapes :**

1. Appuyez sur le menu à trois points de la carte du dossier dans la liste principale.
2. Choisissez **Reconnecter la ressource**. Le sélecteur de dossier système s'ouvre, déjà dans ce dossier là où le téléphone le permet.
3. Choisissez le même dossier et confirmez.
4. Si vous choisissez un dossier différent, l'application nomme les deux dossiers et demande confirmation avant de changer quoi que ce soit.

**Ce qui reste :** le nom, la position dans votre liste, le code PIN, l'icône, le rôle de tri rapide, vos favoris et vos planifications survivent tous - la ressource est ré-adressée, pas recréée.

**Où vous ne le verrez pas :** sur les builds qui lisent encore les dossiers par chemin direct, et sur Android 10 et versions antérieures, l'entrée est absente car rien n'y manque.

---

## Comment choisir où enregistrer les captures et les téléchargements {#how-to-choose-where-captures-and-downloads-are-saved}

Les photos de l'appareil photo intégré, les captures d'écran, les instantanés et les fichiers téléchargés automatiquement s'écrivent chacun dans un dossier que vous choisissez, et ce dossier n'a pas besoin d'être l'une de vos ressources.

**Étapes :**

1. Ouvrez le paramètre pour ce que vous enregistrez - capture, capture d'écran, instantané ou téléchargement automatique.
2. Choisissez le dossier de destination. Le navigateur de dossier système s'ouvre, vous pouvez donc viser n'importe quel dossier local, y compris un que vous n'avez jamais ajouté à l'application.
3. Ce dossier devient la destination d'écriture pour ce paramètre uniquement. Il reste en dehors de votre liste de ressources générale, donc choisir un dossier temporaire pour les captures d'écran n'encombre pas l'écran principal.

**Astuces :**

- Chacun des quatre paramètres a sa propre destination - les captures d'écran et les photos de l'appareil photo peuvent atterrir dans des endroits complètement différents.
- Les diriger tous vers un seul dossier convient très bien si vous préférez tout avoir au même endroit.

### Noms des fichiers de capture

Les nouvelles captures utilisent le modèle `prefix_yyMMdd_HHmmss`. Les préfixes stables sont `photo`, `screenshot`, `audio`, `video`, `screen_video` et `video_frame`, de sorte que le nom du fichier identifie sa source. Si un nom existe déjà dans le dossier de destination, l'application ajoute le suffixe ` (2)` avant l'extension. Un nom de fichier saisi manuellement dans l'appareil photo reste une exception et n'est pas modifié.

---

## Comment recevoir des fichiers partagés depuis une autre application {#how-to-receive-files-shared-from-another-app}

La feuille de partage de n'importe quelle application peut envoyer des fichiers vers FastMediaSorter, qui les copie ensuite où vous voulez.

**Étapes :**

1. Dans l'autre application, partagez le ou les fichiers et choisissez **FastMediaSorter**.
2. Choisissez le dossier de destination sur l'écran de réception.
3. Démarrez la copie.

**Vous n'avez pas besoin d'attendre.** La copie continue de s'exécuter après la fermeture de l'écran de réception, avec une notification affichant la progression pendant le travail et une notification de résultat à la fin. Quittez l'application, verrouillez l'appareil, continuez votre journée - le transfert n'est pas lié au fait que cet écran reste ouvert.

Disponible dans les builds Standard, Lite, Photos et Legacy.

---

## Comment utiliser les programmes intégrés {#how-to-use-the-built-in-programs}

**Disponible dans :** toutes les versions - le menu des programmes et le panneau sont présents dans tous les builds, mais chaque programme suit sa propre capacité : le Moniteur réseau nécessite Standard ou noLegal, le compagnon Wear nécessite Standard ou noLegal, le mini-jeu est absent de XR et de noLegal, et Miroir nécessite une caméra frontale avec la capture caméra activée dans les Paramètres. La calculatrice, la torche frontale et les informations système sont présentes dans tous les builds.

En plus de parcourir et de lire des fichiers, l'application embarque un ensemble de petits programmes intégrés - une calculatrice, une lampe d'écran, un moniteur réseau, un enregistreur vocal et plus encore. Ils sont désactivés par défaut : chacun est activé par son propre paramètre, et la plupart des interrupteurs dédiés se trouvent regroupés dans **Paramètres → Gestion**.

**Chemin rapide**

1. Allez dans **Paramètres → Gestion** et activez ce que vous voulez - par exemple **Calculatrice**, **Torche frontale**, **Moniteur réseau**, **Mini-jeu** ou **Informations système**.
2. Ouvrez le menu déroulant de la fenêtre principale. Les programmes que vous avez activés y sont listés.
3. Appuyez sur l'un d'eux pour le lancer.

**Où un programme apparaît**

Un programme peut être proposé sur jusqu'à quatre surfaces, et chaque surface tire son contenu et son ordre de la même liste unique, de sorte qu'elles ne divergent jamais :

- **Menu des programmes** - le menu déroulant de la fenêtre principale, et le panneau des programmes qui le répète.
- **Panneau de lancement d'application** - l'overlay d'accès rapide.
- **Widget d'écran d'accueil** - uniquement pour les programmes qui en ont un ; épinglez-le depuis le propre sélecteur de widgets de l'application.
- **Bureau du lanceur** - quand vous utilisez l'application comme écran d'accueil, activer un programme ajoute automatiquement sa cellule.

**Ce que contient l'ensemble**

Dans l'ordre où ils apparaissent :

- **Capture rapide** - prendre une photo directement dans l'application.
- **Enregistrement vocal** - enregistrer une note vocale.
- **Calculatrice** - une calculatrice scientifique avec historique et emplacements mémoire.
- **Moniteur réseau** - relevés en direct pour la connexion active, le Wi-Fi, les données mobiles, le Bluetooth et la localisation, plus un traceroute qui parcourt la route vers un hôte saut par saut et continue de compter quand un saut ne répond rien.
- **Traduction OCR photo** - photographier du texte et le traduire.
- **Enregistrement vidéo de l'écran** - enregistrer l'écran.
- **Télécharger depuis un lien** - récupérer un fichier depuis un lien collé.
- **Mini-jeu** - le petit jeu intégré à l'application.
- **Informations système** - un rapport sur l'appareil accessible sans ouvrir les Paramètres.
- **Compagnon Wear** - l'écran de la montre, dans les builds qui embarquent le pont vers la montre.
- **Torche frontale** - transforme l'écran lui-même en lampe : elle s'ouvre en blanc à pleine luminosité de fenêtre, un glissement vertical change la luminosité, un petit bouton en haut à gauche choisit et mémorise une autre couleur, et un simple appui la ferme. Seule la luminosité de la fenêtre est modifiée, donc le paramètre de votre appareil reste inchangé ensuite.
- **Torche eau** - la même lumière pour les mains mouillées. Elle allume le flash de l'appareil photo et l'écran ensemble, puis verrouille l'écran : l'heure et un court rappel sont tout ce que vous voyez, et toucher la vitre ne fait rien du tout - un bouton de volume la ferme. Les barres système disparaissent aussi, pour qu'une main mouillée ne rencontre pas un bouton de navigation ; un geste système délibéré peut tout de même les faire revenir. Conçue pour la pluie et la douche - les deux endroits où la vitre réagit à l'eau plutôt qu'à vous. Sortir par un geste système éteint aussi la lumière, pour qu'elle ne reste jamais allumée dans une poche. Sur la montre, il n'y a pas de flash, donc l'écran seul est la lumière. Elle ne remplace pas le mode verrouillage à l'eau intégré à une montre ou à un téléphone ; aucune application ne peut activer celui-là.
- **Miroir** - transforme le téléphone en miroir éclairé : la caméra frontale remplit l'écran à l'intérieur d'un champ blanc lumineux qui éclaire votre visage, et l'image est retournée comme le montrerait un vrai miroir, avec un bouton dans un coin pour éteindre la lumière sans quitter l'écran. Des préréglages de zoom - x1, x2, x3, x5 - se trouvent en bas à gauche et s'ouvrent sur x3. Un bouton photo et un bouton vidéo enregistrent directement dans le même dossier que Capture utilise, la vidéo avec le son. Le zoom, le retournement et l'état du rétroéclairage sont tous mémorisés d'une utilisation à l'autre. Seule la luminosité de la fenêtre augmente, jamais le paramètre propre de votre appareil, donc le téléphone revient à la normale dès que vous quittez l'écran. Activé par défaut sur tout appareil avec une caméra frontale, tant que la capture caméra elle-même n'est pas désactivée dans les Paramètres.

Le panneau et le lanceur proposent en plus des raccourcis directs vers l'appareil photo - prendre une photo et l'envoyer, prendre une photo et la modifier, prendre une photo et la traduire, démarrer un enregistrement vidéo, et ouvrir le dossier de l'appareil photo.

**Quand c'est utile**

- Vous voulez une calculatrice ou une torche sans quitter l'application, ou sans chercher une application séparée sur un téléphone encombré.
- Vous utilisez l'application comme écran d'accueil et voulez une cellule en un appui pour un outil que vous utilisez souvent.

**À éviter**

- Ne vous attendez pas à ce que la torche eau survive à un retour à l'accueil - un geste de navigation système la quitte quand même, et la lumière s'éteint avec elle.
- Ne vous attendez pas à trouver chaque programme dans chaque build - la liste ci-dessus est l'ensemble complet, et un build sans la capacité sous-jacente n'affiche simplement pas cette entrée.

---

## Comment demander à votre assistant de trouver et d'ouvrir des médias {#how-to-ask-your-assistant-to-find-and-open-media}

**Disponible dans :** tous les builds, sur Android 16 et versions ultérieures. Les versions Android plus anciennes n'offrent tout simplement pas la fonctionnalité, et rien dans l'application n'a besoin d'être activé pour cela.

Sur Android 16+, l'application enregistre auprès du système un ensemble d'actions d'assistant - des AppFunctions, dans les propres termes d'Android. L'assistant de votre appareil peut alors les appeler par leur nom, ce qui vous permet de demander à voix haute une photo, une vidéo ou un dossier d'ordinateur au lieu d'ouvrir l'application et de le chercher vous-même.

**Ce que vous pouvez demander**

- **Rechercher vos médias** - l'assistant transmet vos mots à la recherche de l'application et montre ce qui correspond.
- **Ouvrir un fichier média** - une photo, une vidéo ou un morceau s'ouvre directement dans la visionneuse ou le lecteur de l'application.
- **Ouvrir un dossier d'ordinateur** - un de vos dossiers réseau ou cloud s'ouvre dans l'écran de navigation.

**Chemin rapide**

1. Assurez-vous que l'appareil tourne sous Android 16 ou une version ultérieure et dispose d'un assistant système configuré.
2. Demandez à l'assistant le média que vous voulez, en nommant FastMediaSorter si l'appareil héberge plusieurs applications multimédias.
3. L'application s'ouvre sur le résultat - la liste de recherche, le fichier, ou le dossier que vous avez demandé.

**Quand c'est utile**

- Vous avez les mains occupées - en cuisinant, en conduisant, en tenant un enfant - et naviguer dans les dossiers n'est pas une option.
- Vous vous souvenez du nom d'un fichier mais pas de l'endroit où vous l'avez classé.

**À éviter**

- Ne vous y attendez pas en dessous d'Android 16 : les actions d'assistant font partie du système plus récent, donc sur un téléphone plus ancien, l'assistant ne les verra pas.
- Ne vous attendez pas à ce que l'assistant atteigne un dossier protégé par code PIN - le verrou s'applique toujours, et le dossier demande son code PIN comme d'habitude.

---

## Besoin d'aide supplémentaire ?

- 📖 **Démarrage rapide :** [QUICK_START-fr.md](QUICK_START-fr.md)
- ❓ **FAQ :** [FAQ-fr.md](FAQ-fr.md)
- 🔧 **Dépannage :** [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.md)
- 🐛 **Signaler un problème :** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

</div>
