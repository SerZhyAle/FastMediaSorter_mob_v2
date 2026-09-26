---
layout: default
title: "Lecteur de musique embarqué (autoradio Android) - FastMediaSorter v2"
permalink: /docs/howto/scenario-car-music-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🚗 Lecteur de musique embarqué (autoradio Android)

> **Niveau :** Débutant &bull; **Durée :** ~10 minutes &bull; **Édition :** Standard, Legacy, VR, noLegal (Lite lit l'audio local mais n'a pas de lecture en arrière-plan ni de flux ; Photos n'a pas d'audio)

{% include lang-switcher.html doc="scenario-car-music" dir="/docs/howto/" current="fr" %}

FastMediaSorter fonctionne très bien comme lecteur de musique embarqué sur les autoradios Android - accès instantané à toute votre collection musicale sur carte SD ou clé USB, avec la prise en charge des boutons du volant intégrée.

> **Qu'est-ce qu'un autoradio Android ?** C'est un autoradio avec un écran tactile qui fonctionne sous Android - comme votre téléphone, mais installé dans le tableau de bord. Ce guide fonctionne aussi sur un téléphone ou une tablette ordinaire monté dans la voiture.

---

## Ce dont vous aurez besoin

- Un autoradio Android / téléphone / tablette dans la voiture
- Des fichiers musicaux sur **carte SD**, **clé USB**, ou **stockage interne** (MP3, FLAC, AAC, OGG, et autres)
- (Optionnel) Boutons multimédia sur le volant

---

## Étape 1 - Ajouter votre dossier de musique

1. Ouvrez l'application
2. Appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils supérieure
3. Sélectionnez **"Dossier local"**
4. Naviguez jusqu'à l'endroit où votre musique est stockée :
   - **Carte SD :** cherchez un dossier nommé `/storage/` - à l'intérieur vous trouverez un dossier avec un code comme `1234-5678`, et votre musique se trouve généralement dans `/storage/1234-5678/Music`
   - **Stockage interne :** essayez `/sdcard/Music` ou `/sdcard/Download`
   - **Clé USB :** cherchez dans `/storage/usb0/` ou `/storage/usbdisk/`
5. Sélectionnez le dossier → appuyez sur **Sélectionner**

> **Vous ne trouvez pas votre musique ?** Essayez d'appuyer sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Dossier local"** puis cherchez un dossier appelé `Music` n'importe où dans la liste. Sur la plupart des appareils, il est juste là.


---

## Étape 2 - Configurer le dossier pour la musique

Appuyez longuement sur votre dossier de musique sur l'écran principal → appuyez sur **Modifier (icône crayon)**.

Cela ouvre les paramètres du dossier. Définissez ces options :

| Paramètre | Valeur | Pourquoi |
|---------|-------|-----|
| **Profil** | Bibliothèque audio | Indique à l'application "ceci est un dossier de musique" - configure automatiquement tout pour l'audio |
| **Types pris en charge** | Audio uniquement | Masque les photos et vidéos pour n'afficher que les pistes musicales |
| **Mode de tri** | Titre (A→Z) ou Artiste | Garde vos pistes dans un ordre logique |
| **Inclure les sous-dossiers** | ACTIVÉ | Si votre musique est organisée en sous-dossiers artiste/album, ceci trouve toutes les pistes |

Appuyez sur **Enregistrer**.

> **Que fait "Profil" ?** C'est un préréglage en un geste qui configure le dossier de manière optimale pour son usage. Choisir "Bibliothèque audio" signifie que l'application affiche les pochettes d'album, trie correctement pour la musique et masque automatiquement les fichiers non audio.


---

## Étape 3 - Ouvrir le dossier et lancer la lecture

1. Appuyez sur votre dossier de musique sur l'écran principal
2. Toutes les pistes apparaissent dans une liste avec des miniatures de pochettes d'album
3. Appuyez sur **une piste quelconque** pour démarrer la lecture

Le **lecteur audio** plein écran s'ouvre avec la pochette d'album, la barre de progression et les commandes de lecture.

![Audio player full-screen with album art (Camel - Dust and Dreams)](screenshots/screenshot-car-step3.png)

---

## Étape 4 - S'assurer que la musique continue de jouer

Cette étape garantit que la musique continue de jouer lorsque l'écran s'éteint, que vous changez d'application, ou en cas de notification d'appel téléphonique :

1. Allez dans **Paramètres → onglet Média**
2. Faites défiler jusqu'à la section **Audio**
3. Assurez-vous que **"Prise en charge audio"** est ACTIVÉE

C'est tout. Une fois cette option activée, l'application s'enregistre comme un véritable lecteur de musique - les commandes de l'écran de verrouillage et le lecteur multimédia de la barre de notification apparaissent automatiquement.

![Settings → Media → Audio section with background playback options](screenshots/screenshot-car-step4.png)

---

## Étape 5 - Tester les boutons du volant

Appuyez sur **Suivant** ou **Précédent** sur votre volant.

**Ils fonctionnent automatiquement - aucune configuration nécessaire.** FastMediaSorter répond à tous les boutons multimédia Android standard.

> **Les boutons ne fonctionnent pas ?** Certains autoradios plus anciens envoient des signaux non standard. Essayez d'aller dans **Paramètres Android → Accessibilité** et cherchez une option "récepteur de bouton multimédia". Si cela ne fonctionne pas, utilisez plutôt les zones tactiles à l'écran (bord gauche/droit de l'écran) - elles fonctionnent parfaitement.


---

## Étape 6 - (Optionnel) Utiliser "Toute la musique" - un seul endroit pour toutes vos pistes

Si votre musique est répartie sur plusieurs dossiers (par ex. certaines sur carte SD, d'autres en stockage interne), la ressource virtuelle **Toute la musique** rassemble tout automatiquement en un seul endroit :

1. Sur l'écran principal, cherchez la carte **"Toute la musique"** - elle est généralement créée automatiquement si vous avez des fichiers audio locaux
2. Si elle n'est pas là : appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → faites défiler jusqu'à **Ressources virtuelles** → appuyez sur **"Toute la musique"**

Maintenant, toutes vos pistes de tous les emplacements apparaissent ensemble dans une seule liste.

![FastMediaSorter main screen - All Music virtual resource card visible](screenshots/screenshot-car-step6.png)

---

## Étape 7 - (Optionnel) Raccourci sur l'écran d'accueil pour un lancement en un geste

Parfait pour quand vous voulez simplement monter dans la voiture et appuyer sur un bouton pour démarrer la musique :

1. Appuyez longuement sur un espace vide de l'écran d'accueil → appuyez sur **Widgets**
2. Trouvez **FastMediaSorter** dans la liste → faites glisser le widget **"Raccourci de ressource"** sur votre écran d'accueil
3. Lorsque demandé, sélectionnez votre ressource musicale
4. Terminé - appuyez sur le widget à tout moment et la musique démarre immédiatement

---

## Étape 8 - (Optionnel) Ajouter des stations de radio internet

Si votre autoradio dispose d'une connexion de données mobiles ou Wi-Fi active, vous pouvez ajouter directement des stations de radio internet - sans application supplémentaire :

1. Ouvrez le menu principal (hamburger ou menu déroulant) → appuyez sur **Flux**
2. Appuyez sur **Ajouter (+)** → collez n'importe quelle URL de radio internet (http/https, .m3u8, RTSP) et appuyez sur Enregistrer
3. Ou appuyez sur **Importer le catalogue** pour parcourir la liste de stations sélectionnées intégrée et ajouter des stations par genre ou par langue
4. Appuyez sur une ligne de station pour démarrer la lecture audio en ligne - le nom de la station et la piste en cours apparaissent dans le mini-contrôle en bas
5. La liste reste visible pour que vous puissiez changer de station sans quitter l'écran

> **Audio en arrière-plan :** pour que la radio continue de jouer lorsque vous changez d'application, allez dans **Paramètres → Lecteur → Lecture audio en arrière-plan** et activez-la.

Remarque : les flux nécessitent une connexion réseau, et l'écran Flux est absent dans les éditions Lite et Photos.

---

## Terminé ! Commandes du lecteur

Pendant la lecture de la musique, l'écran est votre panneau de commande :

- **20% gauche de l'écran** → Piste précédente
- **20% droit de l'écran** → Piste suivante
- **60% central** → Pause / Lecture / Menu de commandes

![Audio player running in the background - command panel visible](screenshots/screenshot-car-done.png)

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| Les boutons du volant ne fonctionnent pas | Vérifiez si votre autoradio envoie des événements de touches multimédia Android standard. Certains appareils nécessitent l'activation du "récepteur de bouton multimédia" dans Paramètres Android → Accessibilité |
| La musique s'arrête quand l'écran se verrouille | Activez **"Empêcher la mise en veille"** dans Paramètres → Général, ou utilisez les commandes de notification audio pour reprendre. Vérifiez aussi que la prise en charge audio est ACTIVÉE (étape 4) |
| Aucune pochette d'album affichée | Activez **"Récupérer les pochettes audio en ligne"** dans Paramètres → Média → Audio (nécessite le Wi-Fi). Pour les pochettes hors ligne, l'application lit automatiquement la pochette intégrée du fichier MP3/FLAC |
| Impossible de trouver la musique sur la carte SD | Certaines versions d'Android restreignent l'accès à la carte SD. Essayez d'ajouter le chemin de la carte SD en utilisant le bouton **"Parcourir.."** dans le sélecteur de dossier, qui utilise le sélecteur de fichiers système Android avec un accès complet à la carte SD |
| L'audio bégaie ou saute | Fermez les autres applications s'exécutant en arrière-plan. Pour les fichiers FLAC, assurez-vous que l'autoradio dispose de suffisamment de puissance de traitement |
| La radio internet s'arrête quand je change d'application | Allez dans Paramètres → Lecteur → Lecture audio en arrière-plan et assurez-vous qu'elle est activée |

</div>
