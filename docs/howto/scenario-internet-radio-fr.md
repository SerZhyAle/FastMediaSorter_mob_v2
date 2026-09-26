---
layout: default
title: "Radio internet et flux - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 📻 Radio internet et flux

> **Niveau :** Débutant - **Durée :** ~10 minutes - **Édition :** Standard, Legacy, VR, noLegal (les flux sont absents dans Lite et Photos)

{% include lang-switcher.html doc="scenario-internet-radio" dir="/docs/howto/" current="fr" %}

FastMediaSorter inclut un écran Flux dédié pour les sources audio et vidéo internet. Ajoutez n'importe quelle URL de radio internet, importez une playlist .m3u, ou parcourez un catalogue de stations sélectionnées - sans application radio séparée. Fonctionne très bien sur les autoradios Android, lecteurs audio, téléphones et tablettes.

> **Remplace :** TuneIn, l'application Shoutcast, Online Radio, RadioDroid, les flux réseau VLC, les lecteurs IPTV.

---

## Ce dont vous aurez besoin

- Un appareil Android avec une connexion réseau (données mobiles ou Wi-Fi)
- FastMediaSorter Standard, Legacy, VR, ou noLegal (l'écran Flux est absent dans Lite et Photos)
- Une URL de flux, un fichier ou une URL de playlist .m3u, ou le catalogue sélectionné intégré

---

## Étape 1 - Ouvrir l'écran Flux

Trois façons d'y accéder :
- Menu déroulant de l'écran principal -> **Flux**
- **Paramètres -> Média -> Flux** -> appuyez sur le bouton de raccourci Flux
- Intégration de bienvenue -> ligne Flux (premier lancement uniquement)

> **Vous ne voyez pas Flux dans le menu ?** Allez dans Paramètres -> Média -> Flux et assurez-vous que "Activer les flux" est ACTIVÉ. C'est activé par défaut sur la plupart des appareils.

---

## Étape 2 - Ajouter une station ou un flux

**Option A - Ajouter une seule URL manuellement :**
1. Appuyez sur **Ajouter (+)** dans la barre d'outils de l'écran Flux
2. Collez l'URL du flux (radio http/https, .m3u8 HLS, rtsp://..)
3. Donnez-lui un nom et appuyez sur **Enregistrer**

**Option B - Importer une playlist .m3u :**
1. Appuyez sur **Importer** -> **Depuis une URL**
2. Collez l'URL de la playlist .m3u et confirmez
3. Toutes les stations de la playlist sont ajoutées à votre liste

**Option C - Parcourir le catalogue sélectionné :**
1. Appuyez sur **Importer le catalogue** (ou téléchargez-le depuis l'écran des extensions)
2. Parcourez ou recherchez par nom, sujet ou langue
3. Appuyez sur les stations pour les ajouter à votre liste

---

## Étape 3 - Lire une station

- **Flux audio (radio) :** appuyez sur la ligne - la lecture démarre en ligne. Un mini-contrôle persistant apparaît en bas affichant le nom de la station et les informations ICY de la piste en cours. La liste reste entièrement interactive.
- **Flux vidéo ou RTSP :** appuyez sur la ligne - s'ouvre dans le lecteur plein écran. Appuyez sur Retour pour revenir à la liste ; la position de défilement et la dernière station sélectionnée sont conservées.

---

## Étape 4 - Garder la radio en lecture en arrière-plan

Pour que l'audio continue de jouer lorsque vous changez d'application ou verrouillez l'écran :

1. Allez dans **Paramètres -> Média -> Lecteur**
2. Trouvez le groupe **Lecture audio en arrière-plan**
3. Activez **Lecture audio en arrière-plan**

> **En quittant l'écran Flux pendant qu'une station joue :** l'application propose le même choix Arrêter / Continuer à jouer que le lecteur principal. Si la lecture en arrière-plan est DÉSACTIVÉE, le flux s'arrête lorsque vous réduisez l'écran.

---

## Étape 5 - Filtrer et organiser

- **Épingler les favoris en haut :** appui long sur une ligne de station -> Épingler. Les stations épinglées apparaissent au-dessus des autres, quel que soit l'ordre de tri.
- **Filtrer par catégorie ou langue :** appuyez sur le bouton Filtre (un point apparaît quand un filtre est actif). Le sélecteur de langue affiche des drapeaux. Utilisez le bouton ET/OU pour correspondre à tous les filtres sélectionnés ou à un seul.
- **Trier :** appuyez sur le bouton de tri pour ordonner par nom, sujet, langue, ou lecture récente.
- **Rechercher :** tapez dans la barre de recherche pour filtrer par nom parmi toutes les stations.

---

## Étape 6 - Que faire si une station est morte

Si un flux est indisponible ou redirigé, une boîte de dialogue apparaît avec trois options :
- **Réessayer** - retente le flux
- **Supprimer** - le supprime de votre liste
- **Annuler** - ferme la boîte de dialogue et conserve l'entrée

---

## Dépannage

| Problème | Que faire |
|---------|-------------|
| Le flux ne se lit pas | Vérifiez que l'URL est correcte et que la station est en ligne. Essayez Réessayer dans la boîte de dialogue d'indisponibilité |
| L'audio s'arrête en changeant d'application | Activez la lecture audio en arrière-plan dans Paramètres -> Média -> Lecteur |
| Pas d'entrée Flux dans le menu | L'écran Flux est absent dans les éditions Lite et Photos. Utilisez Standard, Legacy, VR, ou noLegal |
| L'importation du catalogue reste bloquée | L'hôte du catalogue peut être lent ou hors ligne. L'importation expire automatiquement et affiche une erreur - vérifiez votre connexion et réessayez |
| Aucun drapeau affiché dans le filtre de langue | Les drapeaux s'affichent selon la balise de langue dans le catalogue de stations. Les stations ajoutées manuellement sans balise de langue sont toujours visibles quel que soit le filtre de langue |

</div>
