---
layout: default
title: "Écouter de la musique sur votre montre - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-music-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_audio.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Écouter de la musique sur votre montre

> **Niveau :** Débutant &bull; **Durée :** ~5 minutes &bull; **Appareil :** Montre connectée Wear OS (associée à un téléphone Android)

> **Version complète uniquement** - ce guide n'est pas implémenté dans la version distribuée via Google Play. Il s'applique à la version complète, un téléchargement direct de l'APK depuis [Téléchargements](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-music" dir="/docs/howto/" current="fr" %}

FastMediaSorter vous permet de parcourir et de lire votre collection musicale directement depuis votre montre connectée Wear OS. Vous pouvez diffuser des pistes partagées depuis votre téléphone associé ou lire des fichiers audio locaux stockés sur la montre, avec pochettes d'album, lecture aléatoire, contrôle du volume par la lunette rotative, lecture en arrière-plan qui survit à la fermeture de l'application, et un mode écran éteint qui garde la musique en marche avec l'affichage sombre.

---

## Ce dont vous aurez besoin

- Une montre connectée fonctionnant sous **Wear OS 2.0** ou plus récent avec FastMedia Wear installé
- Un téléphone Android exécutant FastMediaSorter (si vous diffusez de la musique depuis votre téléphone)
- Des fichiers musicaux (MP3, FLAC, AAC, OGG) sur votre téléphone ou transférés vers le stockage de votre montre
- Des écouteurs Bluetooth ou le haut-parleur de la montre pour la sortie audio

---

## Étape 1 - Ouvrir FastMedia Wear sur votre montre

1. Ouvrez la liste des applications sur votre montre connectée et appuyez sur **FastMedia Wear**.
2. L'écran d'accueil affiche six sections, toujours aux mêmes endroits :
   - **Ressources** : sources réseau et options de synchronisation
   - **Téléphone** : musique et médias partagés depuis votre téléphone Android associé
   - **Local** : fichiers dans le stockage propre de la montre, y compris les notes vocales que vous y avez enregistrées
   - **Flux** : chaînes de télévision et de radio ([guide séparé](scenario-watch-tv-fr.md))
   - **Applications** : calculatrice, moniteur réseau, jeu et les autres mini-programmes
   - **Favoris** : tout ce que vous avez marqué

![FastMedia Wear main screen on smartwatch](screenshots/screenshot-wear-music-step1.png)

---

## Étape 2 - Choisir votre source de musique

1. Pour lire de la musique depuis votre téléphone : appuyez sur **Téléphone** sur l'écran principal, puis appuyez sur **Audio**.
2. Pour lire des pistes stockées directement sur la montre : appuyez sur **Local** sur l'écran principal, puis appuyez sur **Musique**.
3. FastMedia Wear se connecte à la source sélectionnée et charge votre catalogue musical.

> **Astuce :** l'écran d'accueil garde une rangée des ressources que vous avez ouvertes le plus récemment au-dessus des six sections - une cellule par colonne, donc deux dans une grille à deux colonnes et trois dans une grille à trois colonnes. Une fois que vous avez lu quelque chose, il est là en un geste, et la dernière chaîne de flux que vous avez regardée se trouve dans la même rangée.

---

## Étape 3 - Parcourir et démarrer la lecture

1. Faites défiler vos pistes à l'aide du toucher ou de la lunette rotative.
2. Chaque élément affiche le titre de la piste, la durée et une miniature de la pochette d'album.
3. Appuyez sur **n'importe quelle piste** pour démarrer la lecture immédiatement.

![Browse audio tracks on watch](screenshots/screenshot-wear-music-step3.png)

---

## Étape 4 - Contrôler la lecture et le volume

Lorsqu'une piste démarre, le **lecteur audio** plein écran s'ouvre :

- **Lecture / Pause** : appuyez sur le bouton central mis en évidence pour mettre en pause ou reprendre la lecture.
- **Changer de piste** : appuyez sur **Précédent** ou **Suivant** pour changer de piste dans votre liste de lecture.
- **Lecture aléatoire** : appuyez sur le bouton **Aléatoire** pour mélanger l'ordre des pistes.
- **Se déplacer dans la piste** : faites glisser la barre de progression horizontalement pour sauter à n'importe quelle position dans la chanson.
- **Volume** : tournez la couronne rotative ou la lunette de votre montre pour ajuster le volume en douceur. Un indicateur de niveau de volume apparaît à l'écran.
- **Favori** : appuyez sur l'icône étoile pour ajouter la piste à vos favoris.

![Audio player with playback controls and volume](screenshots/screenshot-wear-music-step4.png)

---

## Étape 5 - Continuer d'écouter la musique

Il existe deux façons différentes de continuer à écouter, et elles répondent à deux questions différentes.

**Quitter l'application** - activez **Continuer la lecture en arrière-plan** dans les paramètres de la montre. L'audio continue alors après avoir réduit l'application ou être revenu au cadran de la montre, avec des commandes dans la notification multimédia. Quand vous revenez, l'écran d'accueil affiche une rangée nommant ce qui joue : appuyez dessus pour revenir à la piste là où elle s'est arrêtée, ou appuyez sur le bouton d'arrêt à côté pour terminer la lecture sans rien ouvrir d'autre. Le bouton est optionnel, et il nécessite que les notifications soient autorisées - sans elles, le système ne peut pas garder le service de lecture en vie.

**Rester dans le lecteur avec l'écran sombre** - appuyez sur le bouton **Écran éteint** en bas des commandes du lecteur. L'affichage devient entièrement noir pendant que la musique continue de jouer, ce qui économise la batterie sur une montre OLED. Un simple appui marque seulement l'endroit que vous avez touché avec un petit point blanc, donc une manche frôlant le verre ne change rien ; un double appui, un appui maintenu, ou le propre bouton de la montre ramène les commandes. Sur les cadrans de montre les plus petits, le bouton reste dans le menu du lecteur plutôt que dans la rangée.

![Screen-off mode button](screenshots/screenshot-wear-music-step5.png)

> La vidéo et les diaporamas ne sont volontairement couverts par aucun des deux : ils s'arrêtent quand l'application quitte l'écran, car une image que personne ne peut voir ne fait que coûter de la batterie.

---

## Terminé ! Fonctionnalités du lecteur

- **Pochette d'album et fond d'onde** : affiche la pochette en plein cadre ou des ondes sonores dynamiques derrière les commandes.
- **Intégration de la lunette rotative** : contrôle natif du volume à l'aide de la lunette physique ou de la couronne de la montre.
- **Lecture en arrière-plan** : l'audio survit à la fermeture de l'application, avec des commandes dans la notification multimédia et une rangée sur l'écran d'accueil nommant ce qui joue.
- **Écoute écran éteint** : extinction instantanée de l'affichage à l'intérieur du lecteur, préservant la lecture et l'autonomie de la batterie.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| La section Téléphone indique "Téléphone non connecté" | Assurez-vous que le Bluetooth est activé sur les deux appareils et que FastMediaSorter est installé sur votre téléphone |
| Aucun fichier musical n'apparaît sous Local | Copiez des fichiers MP3 ou FLAC vers le stockage interne de votre montre ou utilisez la section Téléphone pour lire depuis votre téléphone |
| L'audio s'arrête quand vous quittez l'application | Activez **Continuer la lecture en arrière-plan** dans les paramètres de la montre, et autorisez les notifications - le service de lecture en a besoin pour rester actif |
| La pochette est manquante | Connectez-vous au Wi-Fi pour récupérer les pochettes en ligne, ou assurez-vous que vos fichiers audio contiennent une pochette ID3 intégrée |

</div>
