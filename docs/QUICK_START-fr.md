---
layout: default
title: "🚀 Guide de démarrage rapide - FastMediaSorter v2"
permalink: /docs/QUICK_START-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🚀 Guide de démarrage rapide - FastMediaSorter v2

*Démarrez en 5 minutes ! Guide simple pour les débutants.*

{% include lang-switcher.html doc="QUICK_START" dir="/docs/" current="fr" %}

---

## Choisissez votre édition 📱

FastMediaSorter v2 est proposé en **cinq éditions pour les téléphones et tablettes du quotidien** - Standard, Lite, Photos, Legacy, FOSS - plus **deux versions pour casque et installation manuelle**, VR et noLegal. Sept au total ; la grille de capacités exacte est générée depuis le build dans [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md). Choisissez celle qui répond à vos besoins :

| Édition | Idéale pour | Fonctionnalités clés |
|--------|----------|--------------|
| **Standard** ⭐ | Tout le monde | Fonctionnalités complètes : vidéos, photos, audio, documents, stockage cloud, traduction + radio Internet / Flux |
| **Lite** | Téléchargements plus légers | Photos, vidéos et fichiers audio locaux ; pas de cloud, pas de dossiers réseau, pas de Flux Internet, pas de documents ni de traduction, et l'audio s'arrête quand l'application quitte le premier plan |
| **Photos** | Passionnés de photo | Images uniquement, avec stockage cloud et dossiers réseau ; pas de vidéo, pas d'audio, pas de Flux |
| **Legacy** | Android plus ancien (API 23+) | Médias complets + SMB/FTP/SFTP et cloud ; conçu pour les versions Android plus anciennes (API 23+) |
| **FOSS** | Utilisateurs de F-Droid | Vidéos, photos, audio, documents et EPUB depuis l'appareil et via SMB/FTP/SFTP ; aucun SDK propriétaire, donc pas de cloud, pas de Flux, pas d'OCR et pas de traduction |
| **VR / noLegal** | Casque XR / installation manuelle | VR est la version casque sans dépendance store ; noLegal est la version à installation manuelle qui ajoute le lecteur immersif et des extras réservés à l'installation manuelle |

**👉 La plupart des utilisateurs devraient télécharger l'édition « Standard » pour l'expérience complète.**

---

## Premier lancement : choisissez votre profil d'appareil (30 secondes) 🧭 {#first-launch-choose-your-device-profile-30-seconds-}

Le parcours d'accueil commence par présenter ce qu'est l'application, sous forme de **rôles** plutôt qu'un échantillon de fonctionnalités : sur les builds qui embarquent le launcher, un **écran d'accueil** qui pilote tout l'appareil et une invitation à vous l'approprier ; un **gestionnaire de fichiers** ; un **lecteur** de photos, vidéos, musique, GIF, documents et texte ; un **lecteur de sources** sur l'appareil, sur le réseau et dans le cloud ; des **flux** là où le build les embarque ; un **tri en un geste** ; et, sur un build avec le compagnon montre, l'**application montre** elle-même. Chaque tuile nomme les protocoles et services concrets qu'elle recouvre - SMB, FTP, SFTP, Google Drive, OneDrive, Dropbox - et ne liste que ce que votre build peut réellement ouvrir, afin que ce que vous y lisez soit vrai pour l'édition que vous avez entre les mains.

Dès le tout premier lancement, juste sous le sélecteur de langue, l'écran d'accueil vous demande **comment vous allez utiliser cet appareil**. Choisissez un profil, et l'application démarre avec des réglages par défaut adaptés à ce style - disposition, vignettes, plein écran, écran maintenu allumé, audio en arrière-plan, confirmations de suppression/déplacement, la vue de lecture, les téléchargements de liens, les contrôles du lecteur, la vue de démarrage des flux et, là où le launcher est disponible, le bureau lui-même. C'est un préréglage de départ ponctuel, pas un verrou - vous pouvez tout changer ensuite.

- 🎯 **Badge Recommandé :** l'application devine un profil probable pour votre appareil et le marque **(Recommandé)**. Si elle a bien deviné, vous n'avez presque rien à faire.
- ⏭️ **Passer :** pressé ? **Passer** applique simplement le profil recommandé. Il n'y a pas de mauvaise réponse ici.
- ⚙️ **Le changer plus tard :** **Paramètres → Général → Profil d'appareil**. Le changer là affiche un rapide **avertissement** qui indique exactement combien de réglages le nouveau profil va écraser, et demande confirmation. Rien ne change tant que vous ne dites pas oui. Un profil qui n'écraserait rien est appliqué sans demander.
- 🧩 **Autre / Personnalisé :** conserve vos réglages actuels exactement tels quels. Aucun préréglage appliqué - pratique si vous aimez régler les choses manuellement.
- ⬆️ **Vous mettez à jour depuis une version plus ancienne ?** Votre profil s'affiche comme **Autre** et vos réglages précédents sont conservés intacts - aucun préréglage n'a été appliqué automatiquement. Vous en voulez un quand même ? Choisissez un profil dans les Paramètres.
- 🎚️ **Ensuite - choisissez ce que l'application fait :** après le profil, un écran rapide vous permet d'activer ou de désactiver des fonctionnalités (gestionnaire de fichiers, audio, vidéo, documents, reconnaissance de texte, traduction). Les parties facultatives se téléchargent sur place et s'activent dès qu'elles ont fini - tout est modifiable ensuite dans les Paramètres.

**Les 11 profils :**

- 📱 **Smartphone personnel** - disposition tactile, synchronisation en arrière-plan, confirmations de sécurité par défaut
- 🖥️ **Tablette et mode bureau** - tablettes, Chromebook, Samsung DeX ; disposition en grille, grandes vignettes, navigation multi-fenêtres
- 📺 **TV / boîtier multimédia** - navigation par croix directionnelle/télécommande, grands boutons, pas de petits contrôles tactiles
- 🚗 **Autoradio** - boutons extra-larges, écran qui reste allumé, lecture prioritaire sur les opérations de fichiers
- 🎬 **Lecteur multimédia** - photos, vidéos, musique avec diaporama et reprise de lecture
- 🖼️ **Cadre photo** - s'ouvre directement en diaporama ; les opérations de fichiers s'effacent
- 🎞️ **Lecteur vidéo** - lecture vidéo rapide et vignettes
- 🎵 **Lecteur audio** - audio en arrière-plan et écran de lecture en cours
- 📚 **Liseuse** - lecture de PDF, EPUB et texte
- 🥽 **Casque VR** - dispositions 3D/360° et contrôles immersifs sur le matériel compatible
- 🧩 **Autre / Personnalisé** - conserve les réglages par défaut inchangés, aucun préréglage appliqué

---

## Étape 1 : Ajoutez votre premier dossier (30 secondes)

1. **Ouvrez l'application** - vous verrez l'écran principal
2. **Touchez le bouton « + »** (coin inférieur droit)
3. **Sélectionnez « Dossier local »** (ou Réseau/Cloud)
4. **Choisissez un dossier**
5. **C'est fait !** Le dossier apparaît dans votre liste

> 💡 **Astuce :** pour les lecteurs réseau (SMB), utilisez le nouveau bouton **« Analyser le réseau »** pour trouver les appareils automatiquement !

---

## Étape 2 : Parcourez vos fichiers (30 secondes)

1. **Touchez le dossier** que vous venez d'ajouter (ou appuyez longuement)
2. Vous verrez **tous vos fichiers multimédias** sous forme de vignettes
3. **Changez de vue :**
   - Icône grille = voir les vignettes
   - Icône liste = voir les détails des fichiers

> 💡 **Astuce :** utilisez le **bouton de filtre** (icône entonnoir) pour n'afficher que les photos, les vidéos, ou même les **documents texte/PDF**.

---

## Étape 3 : Visionnez et naviguez (1 minute)

1. **Touchez une photo/vidéo** pour ouvrir la visionneuse plein écran
2. **Balayez vers la gauche** = fichier suivant
3. **Balayez vers la droite** = fichier précédent
4. **Pincez pour zoomer** sur les photos
5. **Touchez l'écran** pour afficher/masquer les contrôles

### Zones tactiles (zones de l'écran)

L'écran est divisé en 9 zones pour des actions rapides :

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

1. Retour | 2. Copier | 3. Renommer
2. Précédent | 5. Déplacer | 6. Suivant
3. Commandes | 8. Suppr | 9. Lecture

> 💡 **Activer la superposition :** Paramètres → Lecteur → « Toujours afficher la superposition des zones tactiles »

---

## Étape 4 : Triez les fichiers rapidement (2 minutes)

**Configurer les dossiers de tri rapide :**

1. Allez dans **Paramètres** → **Gestion** → **Destinations de tri rapide**
2. Touchez **« Ajouter au tri rapide »**
3. Sélectionnez les dossiers vers lesquels trier les fichiers (par ex. « Meilleures photos », « Vacances »)
4. **Chaque dossier reçoit un numéro** (0-9) et une couleur

**Vous pouvez maintenant trier les fichiers instantanément :**

1. En visionnant une photo, **touchez le coin inférieur gauche** (zone COPY)
2. **OU touchez le bouton « 1 »** sur le panneau de commandes
3. Le fichier est copié dans votre dossier de tri rapide n°1 !

---

## Étape 5 : Fonctionnalités avancées (facultatif)

### Lecteur réseau (SMB / SFTP / FTP)

Touchez **« + »** → **Réseau** → **SMB** ou **SFTP / FTP**

- **SMB :** utilisez **« Analyser le réseau »** pour découvrir automatiquement votre NAS et vos appareils réseau. L'application analysera votre réseau local et affichera les partages SMB disponibles.
- **SFTP/FTP :** saisissez l'adresse IP de l'hôte, l'utilisateur et le mot de passe manuellement.

### Stockage cloud

Touchez **« + »** → **Stockage cloud**

- Prend en charge : **Google Drive**, **OneDrive**, **Dropbox**
- Touchez « Se connecter.. », accordez les permissions, et choisissez des dossiers.

### Système de favoris

- **Touchez l'icône étoile** en visionnant n'importe quel fichier
- Accédez à tous les favoris : menu principal → onglet **Favoris**
- Fonctionne sur tous les dossiers !

### Traduction automatique

- Activez-la dans **Paramètres** → **Médias** → **Autre**
- Touchez le bouton **Traduire** (A→文) en visionnant des images/PDF/texte
- **Mode façon loupe :** activez « Superposition façon loupe » dans les paramètres pour des traductions en place façon Google Lens

### Livres électroniques EPUB

- Activez-le dans **Paramètres** → **Médias** → **Documents**
- Ajoutez un dossier contenant des fichiers .epub → les fichiers afficheront un badge « E »
- **Navigation par chapitre :** balayez gauche/droite ou utilisez les boutons précédent/suivant
- **Table des matières :** touchez le bouton 📋 pour la liste des chapitres
- **Taille de police :** utilisez les boutons -A/+A (plage de 6 à 144 px)
- **Recherche :** touchez le bouton 🔍 pour trouver du texte dans le livre
- **Thème clair/sombre :** s'adapte automatiquement au thème de l'application

### Musique de fond pour diaporama

- Ajoutez un dossier de fichiers audio comme ressource
- Allez dans **Paramètres** → **Médias** → **Images** → **« Jouer de la musique pendant le diaporama »**
- Sélectionnez votre ressource musicale dans le menu déroulant
- Démarrez n'importe quel diaporama - la musique joue automatiquement !
- **Astuce :** touchez le nom du morceau pendant le diaporama pour passer à un autre morceau aléatoire

### Réattribution des raccourcis clavier

- Paramètres → **Gestion** → **Contrôles et raccourcis clavier** - réattribuez n'importe quel contrôle à une autre touche, bouton ou entrée de manette
- 70 valeurs par défaut intégrées ; touchez **Réinitialiser** pour les restaurer
- Appuyez sur **F1** sur n'importe quel écran pour voir les raccourcis actifs de cette surface

### Capture photo

- Dans Parcourir, touchez le **bouton appareil photo** dans la barre d'outils pour ouvrir l'appareil photo intégré et enregistrer directement dans la ressource en cours - locale ou réseau
- **Zoom :** touchez un préréglage (0.5x/1x/2x..) ou faites glisser le curseur pour un niveau précis
- **Scénario de prise de vue :** un bouton propose normal, nuit, portrait, selfie, macro, sport et document - uniquement ceux que votre appareil peut réellement fournir
- **Toucher pour faire le point :** touchez n'importe où dans le viseur pour faire le point et régler l'exposition à cet endroit
- **Photo ou vidéo :** changez de mode directement sur l'écran de l'appareil photo avant de prendre la vue

### Capture d'écran et gestes de bord

- Activez **Paramètres → Gestion → Gestes de bord d'écran → Superposition de gestes**, puis balayez depuis le bord gauche pour prendre une capture d'écran, une photo, ou démarrer un enregistrement d'écran/voix/vidéo - voir le scénario des gestes de bord dans [HOW_TO-fr.md](HOW_TO-fr.md) pour plus de détails

### Téléchargement automatique par lien

- Dans n'importe quel navigateur ou messagerie, partagez un lien `http(s)` vers FastMediaSorter via le panneau de partage Android
- L'application télécharge le fichier multimédia et l'enregistre automatiquement dans votre ressource choisie

### Flux Internet (radio et IPTV)

- Ouvrez **Flux** depuis le menu déroulant de la fenêtre principale (ou **Paramètres > Médias > Flux** lorsqu'il apparaît pour la première fois).
- Touchez **⋮** à l'extrémité de la barre d'outils : **Ajouter un flux** pour une URL de radio (http/https mp3/aac, HLS .m3u8, RTSP), ou **Importer depuis une URL** pour une playlist `.m3u`.
- Les boutons **Vidéo**, **Audio** et **Personnels** à côté du champ de recherche divisent la liste : Vidéo et Audio affichent les chaînes du catalogue et importées de ce type, Personnels affiche toutes les chaînes que vous avez ajoutées par URL. Touchez à nouveau le bouton actif pour afficher toutes les chaînes.
- Téléchargez le catalogue de stations FastMediaSorter sélectionné depuis l'écran **Extensions** pour une bibliothèque consultable et filtrable avec des puces de thème et de langue.
- La radio joue en ligne via la barre collante en bas d'écran - la liste des stations reste visible. Les flux vidéo/RTSP s'ouvrent dans le lecteur plein écran ; Retour revient à la liste.
- **Disponible dans Standard, Legacy, noLegal et VR. L'écran Flux est absent de Lite et Photos - aucun protocole n'y fonctionne, car il n'y a pas de point d'entrée.**

### Widgets d'écran d'accueil

FastMediaSorter v2 propose une variété de widgets d'écran d'accueil. Appuyez longuement sur votre écran d'accueil → Widgets → FastMediaSorter pour les parcourir. Parmi les principaux :

- **Raccourci de ressource** - touchez pour ouvrir instantanément n'importe laquelle de vos ressources
- **Continuer la lecture** - lance l'application directement en mode diaporama

---

## Questions fréquentes

**Q : Où vont les fichiers supprimés ?**  
R : Dans le dossier `.trash/` au même emplacement. Utilisez « Vider la corbeille » pour une suppression définitive.

**Q : Puis-je annuler une opération de déplacement ?**  
R : Oui ! Touchez le bouton « Annuler » (ou la zone en bas à droite) dans les 5 secondes.

**Q : Comment modifier des photos ?**  
R : Ouvrez la photo → touchez le bouton « Modifier » → pivoter, retourner, filtres, ajuster la luminosité.

---

## Besoin d'aide ?

- 📖 **Documentation complète :** [README-fr.md](README-fr.md)
- ❓ **FAQ :** [FAQ-fr.md](FAQ-fr.md)
- 🔧 **Dépannage :** [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.md)
- 🐛 **Signaler un problème :** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

**Prochaines étapes :** explorez les Paramètres pour personnaliser la vitesse du diaporama, la taille des vignettes et les raccourcis clavier !

</div>
