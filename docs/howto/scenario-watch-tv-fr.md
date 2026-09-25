---
layout: default
title: "Regarder des chaînes de télévision sur votre montre connectée - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-tv-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_stream.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Regarder des chaînes de télévision sur votre montre connectée

> **Niveau :** Débutant &bull; **Durée :** ~10 minutes &bull; **Appareil :** Montre connectée Wear OS

> **Version complète uniquement** - ce guide n'est pas implémenté dans la version distribuée via Google Play. Il s'applique à la version complète, un téléchargement direct de l'APK depuis [Téléchargements](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-tv" dir="/docs/howto/" current="fr" %}

FastMedia Wear lit les chaînes de télévision et de radio en direct directement au poignet. La montre ouvre le flux via son propre Wi-Fi, donc une fois qu'une chaîne est dans la liste, vous pouvez la regarder avec le téléphone dans une autre pièce, dans un sac, ou entièrement éteint.

> **Vous cherchez plutôt de la musique stockée ?** Voir [Musique sur la montre connectée](scenario-watch-music-fr.md). Pour vos propres fichiers sur un partage NAS ou PC, voir [Connecter la montre aux partages réseau](scenario-watch-network-fr.md).

---

## Ce dont vous aurez besoin

- Une montre connectée fonctionnant sous **Wear OS 2.0** ou plus récent avec FastMedia Wear installé
- Un réseau Wi-Fi que la montre peut rejoindre, ou un téléphone associé pour relayer la connexion
- Optionnel : FastMediaSorter sur votre téléphone Android, si vous voulez envoyer vos propres chaînes à la montre

---

## Étape 1 - Ouvrir Flux

1. Ouvrez **FastMedia Wear** sur votre montre.
2. Sur l'écran d'accueil, appuyez sur **Flux**.

![FastMedia Wear home screen with the Streams section](screenshots/screenshot-wear-tv-step1.png)

L'écran d'accueil garde les six mêmes sections aux mêmes endroits, donc Flux est toujours dans la rangée inférieure quelle que soit la taille de grille que vous avez choisie. Au-dessus se trouve une rangée des ressources que vous avez ouvertes le plus récemment - une fois que vous avez regardé quelque chose, la chaîne que vous avez quittée apparaît là en un geste.

---

## Étape 2 - Remplir la liste des chaînes

Une installation neuve n'a pas encore de chaînes, et l'écran l'indique.

![Empty Streams screen with the Refresh catalog button](screenshots/screenshot-wear-tv-step2.png)

Il existe deux façons de la remplir, et elles fonctionnent ensemble :

- **Télécharger le catalogue partagé.** Appuyez sur **Actualiser le catalogue**. La montre récupère la banque de chaînes publiée en une seule archive - plusieurs milliers de chaînes TV et radio avec leurs sujets, langues et pays.
- **Envoyer des chaînes depuis votre téléphone.** Une chaîne que vous avez ajoutée vous-même dans FastMediaSorter sur le téléphone peut être transmise avec **Envoyer à la montre** depuis la liste de flux du téléphone. Les chaînes que vous épinglez sur le téléphone sont aussi remontées vers le haut de la liste de la montre, juste derrière celles que vous avez épinglées sur la montre elle-même, afin que les deux ou trois que vous regardez réellement soient accessibles sans défiler. Désépingler sur le téléphone retire à nouveau la chaîne de ce groupe, et une chaîne que le propre catalogue de la montre ne contient pas est simplement ignorée.

Les chaînes envoyées depuis le téléphone survivent à une actualisation du catalogue - l'actualisation remplace la banque partagée et laisse vos propres lignes intactes.

---

## Étape 3 - Trouver la chaîne que vous voulez

Les trois boutons en haut de la liste restent épinglés pendant que la liste défile, afin qu'ils ne défilent jamais hors de portée.

- **Rechercher** filtre la liste au fur et à mesure que vous tapez.
- **Filtrer** restreint par sujet et par langue. Les noms sont affichés dans votre langue d'interface plutôt qu'en anglais brut du catalogue, les plus peuplés en premier, avec le nombre de chaînes sur chaque ligne, et les trois langues propres de l'application en haut.
- **Filtrer** liste aussi les collections sélectionnées fournies avec le catalogue - "TV russe", "Radio de l'ex-URSS", "TV africaine" et le reste, les mêmes que celles affichées sur le téléphone. Choisissez-en une pour ne voir que ses chaînes, ou choisissez **Toutes** pour lever la restriction. Une chaîne peut appartenir à plusieurs collections, donc la même station apparaît sous plusieurs d'entre elles. Si le catalogue téléchargé ne contient aucune collection, l'entrée n'est pas affichée du tout.
- **Trier** propose Les plus utilisées, Nom A-Z, Nom Z-A et Par type de média. Les plus utilisées est le tri par défaut et remonte avec les chaînes que vous démarrez réellement sur la montre, donc la liste apprend d'elle-même vos habitudes.

Au-dessus de la liste, un petit compteur sur deux lignes indique combien de chaînes la recherche et les filtres actuels laissent, sur la taille du catalogue entier.

![Channel list with the counter and the pinned toolbar](screenshots/screenshot-wear-tv-step3.png)

En mode grille, une chaîne vidéo affiche une image d'aperçu avant même que vous ne l'ayez ouverte, tirée d'un ensemble d'aperçus téléchargeable. Après votre premier visionnage, l'aperçu est remplacé par une image capturée depuis la chaîne elle-même.

---

## Étape 4 - Regarder

1. Appuyez sur une chaîne. Le lecteur vidéo s'ouvre en plein écran.
2. **Volume :** tournez la lunette rotative ou la couronne.
3. **Avance/retour :** appui long sur le bouton précédent ou suivant. Les deux boutons restent à l'écran même pour une seule chaîne.
4. **Cadrage :** le bouton de mode de cadrage bascule entre adapter l'image entière à l'intérieur du verre rond et la recadrer pour remplir l'écran. La montre se souvient de votre choix - il survit à la fermeture du lecteur et au redémarrage de l'application, et le même choix couvre vos propres fichiers vidéo.
5. **Écran éteint :** le menu du lecteur a une entrée **Écran éteint**. L'affichage devient entièrement noir - pas d'horloge, pas de commandes - pendant que la chaîne continue de jouer, et la montre ne se mettra pas en veille. Un simple appui marque seulement l'endroit que vous avez touché avec un petit point blanc ; un double appui, un appui maintenu, ou le propre bouton de la montre ramène l'image et les commandes exactement comme vous les avez laissées.
6. **Épingler :** la marque sur le lecteur épingle la chaîne. Les chaînes épinglées sont listées en premier la prochaine fois que vous ouvrez Flux : celles que vous avez épinglées ici sur la montre viennent en tête, celles épinglées sur le téléphone les suivent, et tout le reste garde l'ordre que donne votre tri choisi. L'épinglage est lié à l'adresse de la chaîne, donc il survit à une réimportation du catalogue.

> **La vidéo a besoin de l'écran.** La lecture en arrière-plan garde l'**audio** en marche après que vous quittez l'application - utile pour les chaînes radio - mais la vidéo et les diaporamas s'arrêtent quand l'application quitte l'écran. C'est voulu : une vidéo que vous ne pouvez pas voir ne fait que vider la batterie.

---

## Étape 5 - Revenir en un geste

- La **rangée récente de l'écran d'accueil** liste la dernière chaîne que vous avez regardée à côté des ressources réseau que vous avez ouvertes récemment, avec l'icône propre de la chaîne. Appuyer dessus rouvre le lecteur.
- Une **tuile Flux** peut être ajoutée au carrousel de tuiles Wear OS et pointée vers une chaîne depuis la montre elle-même. À partir de là, la chaîne est à un balayage du cadran de la montre, sans avoir besoin d'ouvrir d'abord l'application.
- La **complication de dernière ressource** affiche aussi la chaîne, afin qu'elle puisse figurer sur le cadran de la montre.

---

## Étape 6 - Quand la liaison est faible

Les flux en direct sont ce qu'il y a de plus exigeant pour le réseau d'une montre, donc l'application est explicite à ce sujet :

- Pendant qu'un flux joue, la montre demande au système un réseau à large bande et le libère quand la lecture se termine.
- Si la liaison actuelle ne peut pas supporter le flux, la montre le signale plutôt que d'échouer silencieusement.
- Si un flux se fige sans erreur - la façon habituelle dont un flux en direct meurt - un système de surveillance le réancre et le repréparer jusqu'à trois fois, affichant **Reconnexion**. Ce n'est que lorsque le réseau reste mort qu'il retombe sur le message de chaîne indisponible.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| "Aucun flux disponible" après une installation neuve | Appuyez sur **Actualiser le catalogue**, ou envoyez une chaîne depuis le téléphone avec **Envoyer à la montre** |
| "Impossible de mettre à jour les flux" | Le catalogue est un téléchargement de plusieurs mégaoctets. Mettez la montre sur le Wi-Fi plutôt que sur une liaison relayée par le téléphone, et réessayez |
| Une chaîne s'ouvre puis s'arrête | La source elle-même peut être hors ligne. La montre réessaie trois fois avant d'abandonner - essayez une autre chaîne pour distinguer un flux mort d'un réseau mort |
| La vidéo s'arrête quand vous baissez le poignet | Attendu : seul l'audio continue une fois que l'application quitte l'écran. Pour garder une chaîne en lecture avec l'affichage éteint, restez dans le lecteur et utilisez son entrée **Écran éteint** |
| La chaîne que vous avez épinglée sur le téléphone n'est pas en haut | Les épingles voyagent quand le compagnon Wear est activé dans l'application téléphone ; vérifiez cela en premier |
| Le son est trop faible | Tournez la lunette ou la couronne dans le lecteur - cela change le volume multimédia de la montre, pas la position de lecture |

</div>
