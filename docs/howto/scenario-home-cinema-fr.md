---
layout: default
title: "Cinéma maison et streaming VR - FastMediaSorter v2"
permalink: /docs/howto/scenario-home-cinema-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🍿 Cinéma maison et streaming VR

> **Niveau :** Débutant &bull; **Durée :** ~15 minutes &bull; **Édition :** Standard, Legacy, VR, noLegal (Lite n'a pas de sources réseau, Photos n'a pas de vidéo)

{% include lang-switcher.html doc="scenario-home-cinema" dir="/docs/howto/" current="fr" %}

Regardez votre collection de séries directement depuis votre PC à la maison - sur votre téléphone, tablette, ou casque VR sous Android (Meta Quest, Pico). Sans copier de fichiers. Sans câbles USB. Il suffit d'appuyer sur lecture.

> **Comment ça marche ?** Votre téléphone et votre PC sont sur le même Wi-Fi domestique. L'application se connecte au dossier partagé de votre PC et diffuse la vidéo directement - tout comme Netflix diffuse depuis ses serveurs, mais en utilisant votre propre réseau domestique. Le fichier vidéo n'est jamais téléchargé sur votre téléphone ; il se lit à la volée.

---

## Ce dont vous aurez besoin

- Téléphone / tablette / casque VR sur le même **réseau Wi-Fi domestique** que votre PC
- Des vidéos sur votre **PC ou NAS** (votre boîtier avec stockage)
- FastMediaSorter installé

---

## Étape 1 - Partager votre dossier vidéo sur le PC

D'abord, rendez le dossier vidéo accessible sur votre réseau domestique.

Sous **Windows :**
1. Ouvrez l'**Explorateur de fichiers**, naviguez jusqu'à votre dossier vidéo (par ex. `D:\Series`)
2. **Clic droit** sur le dossier → **Propriétés** → onglet **Partage** → cliquez sur **Partager..**
3. Dans le menu déroulant, choisissez **Tout le monde** (ou votre nom d'utilisateur) → cliquez sur **Ajouter** → cliquez sur **Partager**
4. Notez l'adresse IP de votre PC - vous en aurez besoin à l'étape 2

> **Comment trouver l'IP de votre PC :** appuyez sur **Win + R**, tapez `cmd`, appuyez sur Entrée. Tapez `ipconfig` et appuyez sur Entrée. Trouvez la ligne **Adresse IPv4** sous votre adaptateur Wi-Fi. Exemple : `192.168.1.100`.

---

## Étape 2 - Ajouter le dossier vidéo dans FastMediaSorter

1. Ouvrez l'application → appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Dossier réseau (SMB)"**
2. Appuyez sur **"Analyser le réseau"** - l'application recherche les PC disponibles sur votre réseau domestique
3. Lorsque votre PC apparaît dans la liste, appuyez dessus - l'adresse se remplit automatiquement
4. Saisissez le nom du partage (le nom du dossier vidéo), le nom d'utilisateur et le mot de passe Windows
5. Appuyez sur **Tester la connexion** → **Enregistrer**

> **Vous n'avez pas trouvé votre PC via l'analyse ?** Saisissez l'adresse manuellement : `\\192.168.1.100\Series` (remplacez par votre IP et le nom de votre dossier). Consultez le [Guide de configuration SMB](scenario-smb-setup-fr.md) complet pour tous les scénarios de connexion.


---

## Étape 3 - Ouvrir le dossier vidéo

Appuyez sur votre ressource nouvellement ajoutée sur l'écran principal.

Vos dossiers de séries et fichiers vidéo apparaissent sous forme de grille avec des vignettes - exactement comme en navigation locale.

![SMB video folder - episode files (MKV) listed by filename](screenshots/screenshot-hc-step3.png)

---

## Étape 4 - Configurer le passage automatique à l'épisode suivant

Pour que l'épisode suivant démarre automatiquement quand un épisode se termine - sans avoir à choisir le suivant manuellement :

1. Retournez à l'écran principal → **appui long** sur votre ressource vidéo → appuyez sur **Modifier**
2. Réglez **Types pris en charge** → **Vidéo uniquement** (masque les fichiers non vidéo)
3. Réglez **Mode de tri** → **Nom (A→Z)** - ceci garantit que les épisodes se lisent dans l'ordre (Épisode 1, 2, 3..)
4. Appuyez sur **Enregistrer**

Puis démarrez le diaporama dans le lecteur (la commande **Diaporama**) et activez **Paramètres → Lecteur → Lire la vidéo/audio en diaporama jusqu'à la fin** - chaque épisode se lit alors jusqu'à sa fin avant que le suivant ne démarre.

> **Pourquoi trier par nom ?** Les fichiers d'épisodes sont généralement nommés `S01E01`, `S01E02`, etc. Le tri par nom les place automatiquement dans le bon ordre d'épisode.

---

## Étape 5 - Commencer à regarder

1. Ouvrez le dossier, naviguez dans le sous-dossier de la série
2. Appuyez sur **Épisode 1** - le lecteur vidéo s'ouvre immédiatement et démarre le streaming
3. La vidéo se lit via le Wi-Fi - pas d'attente de téléchargement

![Video player full-screen - episode playing with progress bar](screenshots/screenshot-hc-step5.png)

---

## Étape 6 - Commandes pendant le visionnage

**Gestes tactiles pendant la lecture :**
- **Balayer vers la gauche** → passer à l'épisode suivant
- **Balayer vers la droite** → revenir à l'épisode précédent
- **Appuyer sur l'écran** → afficher / masquer les commandes
- **Pincer** → zoomer en avant ou en arrière (utile pour les films en écran large sur un téléphone en portrait)
- **Double-appui sur le bord gauche / droit** → reculer / avancer de 10 secondes

Lorsque **"Passage automatique"** est activé, l'épisode suivant démarre automatiquement quand l'actuel se termine - tout comme Netflix.

---

## Étape 7 - Pour les casques VR (Meta Quest, Pico)

> **Cette section concerne les personnes possédant un casque VR (comme le Meta Quest 2/3 ou le Pico 4).** Si vous n'en avez pas, passez cette étape.

Les casques VR sous Android peuvent exécuter FastMediaSorter. Installez-le par chargement latéral :
1. Téléchargez l'APK depuis la [page Téléchargements](../DOWNLOADS.md)
2. Sur votre casque, activez **"Installer depuis des sources inconnues"** dans les paramètres développeur
3. Installez l'APK avec SideQuest ou directement via ADB

Une fois installé, le lecteur vidéo fonctionne exactement de la même manière :
- La vidéo remplit l'**écran plat virtuel** à l'intérieur du casque
- Utilisez la **gâchette de la manette** pour appuyer sur les boutons
- Utilisez le **joystick** pour balayer entre les épisodes (si votre casque associe les boutons multimédia)
- Pour les films et séries 2D classiques - fonctionne immédiatement, sans configuration supplémentaire

> **Pour une expérience de cinéma VR :** vous pouvez utiliser une application de cinéma VR dédiée comme lanceur, puis choisir "Ouvrir avec FastMediaSorter" pour la gestion des fichiers. FastMediaSorter gère la navigation dans les fichiers ; l'application de cinéma VR gère l'affichage immersif 360°.

---

## Terminé ! Quoi essayer ensuite

- Ajoutez une ressource **Google Drive** ou **Dropbox** pour les films stockés dans le cloud - fonctionne de la même manière
- Utilisez les **Favoris** (appuyez sur le bouton étoile <img src="../icons/doc/ic_star_filled.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> pendant le visionnage) pour marquer votre série "en cours de visionnage" - revenez-y à tout moment
- **Sous-titres :** si votre dossier vidéo contient des fichiers de sous-titres `.srt` correspondants à côté des fichiers vidéo, appuyez sur le **bouton CC / sous-titres** dans la barre d'outils du lecteur pour les activer
- **Radio internet ou flux en direct :** si vous voulez aussi ajouter des stations de radio internet ou des sources RTSP/HLS, consultez le guide [Radio internet et flux](scenario-internet-radio-fr.md) - ni NAS ni PC nécessaire, juste une connexion réseau.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| La vidéo bégaie ou met en mémoire tampon | Lancez un **test de vitesse** : appui long sur la ressource → Modifier → Test de vitesse. Si la vitesse est inférieure à 5 Mbps, essayez de basculer votre téléphone sur la **bande Wi-Fi 5 GHz** (plus rapide, mais portée plus courte) |
| La vidéo ne se lance pas (erreur de format) | Dans le lecteur, appuyez sur **Options <img src="../icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → passez le **décodeur** de matériel à logiciel (plus lent mais plus compatible) |
| Les épisodes se lisent dans le mauvais ordre | Assurez-vous que le mode de tri est réglé sur **Nom (A→Z)** dans les paramètres de modification du dossier |
| Le passage automatique ne démarre pas | Assurez-vous que le diaporama est en cours et que Paramètres → Lecteur → Lire la vidéo/audio en diaporama jusqu'à la fin est activé |
| Le casque VR ne peut pas installer l'APK | Ouvrez les paramètres développeur du casque et activez "Autoriser les installations depuis des sources inconnues" |

</div>
