---
layout: default
title: "❓ Foire aux questions (FAQ)"
permalink: /docs/FAQ-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# ❓ Foire aux questions (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="fr" %}

---

## Questions générales

### Qu'est-ce que FastMediaSorter ?
FastMediaSorter v2 est un shell complet pour un appareil Android : il prend le contrôle de l'écran d'accueil, lit vos médias, ouvre des flux en direct, lance vos applications, communique avec votre montre, surveille l'appareil et gère tous les fichiers que vous possédez - dans des dossiers locaux, sur des lecteurs réseau (SMB/SFTP/FTP) et dans le stockage cloud (Google Drive, OneDrive, Dropbox).

### Est-ce gratuit ?
Oui ! FastMediaSorter v2 est totalement gratuit et open source.

### De quelle version d'Android ai-je besoin ?
Standard, Lite et Photos nécessitent Android 8.0 (API 26) ou plus récent. L'édition **Legacy** prend en charge Android 6.0 (API 23) ou plus récent. **XR / noLegal** nécessite en plus un matériel de casque compatible et le chemin d'exécution actuel pour l'installation manuelle.

### Faut-il une connexion Internet ?
**Non** pour les fichiers locaux. **Oui** pour les lecteurs réseau et le stockage cloud.

### L'application a-t-elle des widgets ?
Oui ! FastMediaSorter v2 propose une variété de widgets d'écran d'accueil - trouvez-les via un appui long sur l'écran d'accueil → Widgets → FastMediaSorter. Ils incluent des raccourcis de ressources, des lanceurs de diaporama, et plus encore.

### L'application peut-elle remplacer mon écran d'accueil ?
Oui, dans les builds **Standard** et **noLegal**. Activez **Faire de cette application l'écran d'accueil** dans **Paramètres → Général** et choisissez FastMediaSorter quand Android demande quel écran d'accueil utiliser. Vous obtenez un bureau avec des raccourcis vers vos dossiers, des gadgets comme une horloge et la météo, une grille d'applications et une barre des tâches. Désactivez le réglage, ou choisissez **Quitter le mode launcher**, et Android restaure votre écran d'accueil précédent - la disposition de votre bureau est conservée pour la prochaine fois. Voir [HOW_TO](HOW_TO-fr.md#how-to-use-the-app-as-your-home-screen) pour le guide complet.

### Comment arrêter que l'application soit mon écran d'accueil ?
Trois façons, selon celle que vous trouvez en premier :

- Ouvrez le menu Démarrer sur le bureau, choisissez **Quitter le mode launcher**, et confirmez.
- Désactivez à nouveau **Faire de cette application l'écran d'accueil** dans **Paramètres → Général**.
- Ouvrez la propre liste d'applications d'accueil d'Android dans **Paramètres → Général → Paramètres du launcher système → Système → Changer d'écran d'accueil** et choisissez le launcher que vous voulez.

La disposition de votre bureau est conservée dans tous les cas, donc réactiver le mode la fait revenir telle que vous l'avez laissée.

### Pourquoi ma tablette est-elle revenue à son ancien écran d'accueil après un redémarrage ?
Parce que le firmware de cet appareil l'a remis en place, pas parce que l'application a perdu le réglage. Certains autoradios bon marché et boîtiers Android intégrés réinitialisent l'application d'accueil vers leur réglage d'usine à chaque démarrage, quel que soit votre choix - aucune application ne peut passer outre. Choisissez à nouveau FastMediaSorter comme application d'accueil après le redémarrage, et si votre appareil propose **Toujours** plutôt que **Une seule fois**, choisissez **Toujours**. Si cela refuse toujours de tenir, cet appareil ne permet tout simplement pas de remplacer l'écran d'accueil.

### Puis-je mettre mes propres dossiers et playlists sur le bureau ?
Oui - c'est à ça que sert le bureau. Appuyez longuement sur une case vide et choisissez **Ajouter un élément..**, puis choisissez ce que vous voulez : l'un de vos dossiers, un flux radio, une application, une personne, ou un gadget comme l'horloge ou la météo. La nouvelle cellule se place sur la case sur laquelle vous avez appuyé, et pour un dossier, vous choisissez aussi s'il s'ouvre en mode parcourir, diaporama ou lecture. Pour réorganiser les choses ensuite, choisissez **Modifier le bureau** depuis le même menu d'appui long. Voir [HOW_TO](HOW_TO-fr.md#how-to-use-the-app-as-your-home-screen) pour le guide complet.

---

## Opérations sur les fichiers

### Où vont les fichiers supprimés ?
Les fichiers supprimés vont dans un dossier `.trash/` au même emplacement (suppression douce). Ils ne sont pas définitivement supprimés tant que vous n'avez pas :
- touché **« Vider la corbeille »** dans Paramètres → Gestion → Suppression de fichiers et corbeille, OU
- supprimé manuellement le dossier `.trash/`

### Puis-je annuler une suppression/un déplacement ?
**Oui !** Touchez le bouton **« Annuler »** (ou la zone tactile en bas à droite) dans les quelques secondes qui suivent l'opération.

> ⚠️ **Remarque :** l'annulation n'est pas disponible pour les suppressions de fichiers réseau (ils sont supprimés définitivement et immédiatement).

### Quelle est la différence entre Copier et Déplacer ?
- **Copier :** crée un doublon, l'original reste en place
- **Déplacer :** déplace le fichier, le supprime de l'emplacement d'origine

### Qu'est-ce que le mode Tous les fichiers ?
Le **mode Tous les fichiers** vous permet d'utiliser l'application comme un navigateur de fichiers complet dans tous les répertoires. Dans ce mode, l'application contourne les filtres multimédias standard et affiche tous les fichiers (y compris ZIP, RAR, APK, EXE, PDF, etc.). Vous pouvez effectuer les opérations de fichiers standard comme copier, déplacer, renommer, partager et supprimer. Pour les fichiers binaires non pris en charge, une feuille inférieure s'ouvre automatiquement, vous permettant de gérer le fichier ou de l'ouvrir avec des applications externes.

### Comment trouver et supprimer les fichiers en double ?
Ouvrez un dossier, touchez le menu débordant, et choisissez **Trouver les doublons** pour examiner vous-même les correspondances, ou **Trouver et supprimer les doublons** pour les supprimer immédiatement. Il existe aussi **Supprimer par taille..** pour un nettoyage rapide basé uniquement sur la taille des fichiers. L'option automatique saute la confirmation, donc utilisez d'abord **Trouver les doublons** si vous voulez vérifier avant toute suppression. La correspondance est basée sur le contenu - taille, puis un hachage rapide, puis une vérification SHA-256 complète - donc les copies renommées sont quand même trouvées.

---

## Réseau et Cloud

### Comment me connecter à mon NAS domestique (lecteur réseau) ?
1. Touchez **« + »** → **Réseau** → **SMB / Lecteur réseau**
2. **Option A - Automatique :** touchez **« Analyser le réseau »** pour découvrir automatiquement les appareils disponibles sur votre réseau
3. **Option B - Manuelle :** saisissez l'adresse du serveur : `\\192.168.1.100\share` ou `smb://192.168.1.100/share`
4. Saisissez le nom d'utilisateur et le mot de passe
5. Touchez « Se connecter »

**Problèmes courants et solutions :**

| Problème | Que faire |
|---------|------------|
| « Connexion refusée » | Ouvrez le pare-feu Windows → autorisez le **port TCP 445** en entrée. Ou désactivez temporairement le pare-feu pour tester |
| « Mot de passe incorrect » | Essayez de laisser le nom d'utilisateur vide (accès invité). Si vous utilisez un compte Microsoft, saisissez votre **adresse e-mail complète** comme nom d'utilisateur |
| « Hôte introuvable » | Assurez-vous que le téléphone et le PC sont sur le **même routeur Wi-Fi**. L'isolation AP (un réglage de sécurité du routeur) peut bloquer le trafic entre appareils - désactivez-la dans les réglages du routeur |
| L'analyse ne trouve rien | Désactivez le VPN sur le téléphone. Activez la **découverte réseau** dans Windows (Panneau de configuration → Centre Réseau et partage → Paramètres de partage avancés). Puis essayez de saisir l'IP manuellement |
| Navigation très lente | Modifiez la ressource → lancez le **Test de vitesse**. Si en dessous de 5 Mbps, passez le téléphone sur la bande Wi-Fi 5 GHz. Désactivez les vignettes vidéo pour les connexions lentes |
| Fonctionne en Wi-Fi mais pas en données mobiles | Normal - SMB est un protocole réseau local uniquement, il ne peut pas fonctionner sur les données mobiles |

→ Guide complet : [Guide de configuration SMB](howto/scenario-smb-setup-fr.md)

### Comment me connecter à Google Drive ?
1. Touchez **« + »** → **Cloud** → **Google Drive**
2. Touchez « Se connecter avec Google »
3. Accordez les permissions lorsqu'on vous le demande
4. Vos dossiers Drive apparaîtront

**Remarque :** les fichiers ne sont PAS téléchargés automatiquement - ils sont diffusés à la demande.

### Comment me connecter à OneDrive ?
1. Touchez **« + »** → **Cloud** → **OneDrive**
2. Touchez « Se connecter avec Microsoft »
3. Accordez les permissions lorsqu'on vous le demande
4. Vos dossiers OneDrive apparaîtront

### Comment me connecter à Dropbox ?
1. Touchez **« + »** → **Cloud** → **Dropbox**
2. Touchez « Se connecter avec Dropbox »
3. Accordez les permissions lorsqu'on vous le demande
4. Vos dossiers Dropbox apparaîtront

### Puis-je utiliser SFTP ou FTP ?
**Oui !** Sélectionnez **SFTP** ou **FTP** en ajoutant un dossier :
- **SFTP :** sécurisé, nécessite un serveur SSH (port 22)
- **FTP :** moins sécurisé, protocole plus ancien (port 21)

### Puis-je partager des dossiers PC avec l'application ?
**Oui** - Fast Media Sorter for Windows publie les dossiers PC choisis via SFTP et affiche un code QR / une configuration `.fmscfg`. Sur le téléphone, utilisez **Importer depuis companion** ou **Scanner le code QR** sur l'écran Ajouter une ressource. Voir le guide côté PC : [Comment publier des dossiers PC vers Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html). Disponible dans Standard, Photos, Legacy, XR/noLegal.

### Pourquoi les vignettes ne se chargent-elles pas pour les fichiers réseau ?
Les vignettes réseau se génèrent **à la demande** pour économiser la bande passante. Faites défiler lentement ou attendez quelques secondes qu'elles apparaissent.

Si les vignettes ne se chargent jamais du tout :
- Vérifiez que la connexion est active : touchez la ressource → si le dossier s'ouvre, la connexion fonctionne
- Modifiez la ressource → assurez-vous que **« Charger les vignettes »** est activé
- Pour les connexions très lentes : désactivez complètement les vignettes pour éviter les délais d'expiration (Modifier la ressource → désactiver les vignettes)

### La connexion se coupe sans cesse / les fichiers ne s'ouvrent pas en cours de lecture
- Vérifiez que le Wi-Fi de votre téléphone est stable (ne bascule pas entre les bandes 2,4 et 5 GHz)
- Certains routeurs déconnectent les sessions SMB inactives - modifiez la ressource → activez **« Reconnexion en cas d'erreur »** si disponible
- Pour la lecture vidéo via SMB : lancez le Test de vitesse (Modifier la ressource → Test de vitesse). Il vous faut au moins 10 Mbps pour de la vidéo 1080p

---

## Tri rapide et destinations

### Que sont les dossiers « Tri rapide » ?
Les dossiers de tri rapide sont des dossiers cibles préconfigurés pour un tri rapide des fichiers. Vous pouvez assigner jusqu'à 30 dossiers avec des boutons numérotés.

### Comment configurer le tri rapide ?
**Méthode 1 :** Paramètres → Gestion → Destinations de tri rapide, puis touchez **« Ajouter au tri rapide »**  
**Méthode 2 :** Modifiez n'importe quel dossier → activez « Marquer pour le tri rapide »

### Comment utiliser le tri rapide en visionnant des fichiers ?
1. Ouvrez une photo/vidéo en plein écran
2. Touchez un **bouton numéroté** (0-9) sur le panneau de commandes, OU
3. Touchez le **coin inférieur gauche** (zone COPY) ou le **centre inférieur** (zone MOVE)

### Puis-je utiliser les touches numériques au lieu de toucher l'écran ?
Oui - connectez un clavier physique, une manette, ou une télécommande TV, et vos boutons de tri rapide seront automatiquement numérotés (0-9). Appuyez sur le chiffre correspondant pour copier ou déplacer instantanément le fichier vers cette destination, exactement comme en touchant le bouton.

### Les boutons de tri rapide ne s'affichent pas
Assurez-vous d'avoir d'abord ajouté au moins un dossier de destination : Paramètres → Gestion → Destinations de tri rapide, puis **« Ajouter au tri rapide »**. Les boutons n'apparaissent que lorsqu'au moins une destination est configurée.

### J'ai envoyé un fichier par erreur dans le mauvais dossier
Touchez **Annuler** immédiatement (en bas à droite du panneau de commandes) - disponible pendant quelques secondes après chaque opération. Si vous avez manqué la fenêtre, allez dans le dossier de destination et déplacez le fichier manuellement.

---

## Zones tactiles

### Que sont les « zones tactiles » ?
Les zones tactiles sont des zones invisibles de l'écran qui déclenchent des actions lorsqu'on les touche. L'écran est divisé en une grille 3x3 :

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
└─────────┴─────────┴─────────┘
```

### Comment voir les zones tactiles ?
Paramètres → Lecteur → **« Toujours afficher la superposition des zones tactiles »**

### Puis-je désactiver les zones tactiles ?
Oui, utilisez simplement les **boutons du panneau de commandes** à la place. Les zones tactiles sont facultatives.

---

## Capture d'écran et vocale

### Qu'est-ce que le bandeau de gestes sur le bord gauche ?
C'est un menu de capture rapide que vous ouvrez avec un balayage diagonal depuis le bord gauche de l'écran. Activez-le dans **Paramètres → Gestion → Gestes de bord d'écran → Superposition de gestes**. Depuis le menu, vous pouvez prendre une capture d'écran, une photo, recadrer et partager l'image actuelle, ouvrir un raccourci d'application ou de panneau, ou démarrer un enregistrement écran, vidéo ou voix - le tout sans quitter ce que vous regardez. Disponible dans Standard et XR/noLegal.

### Comment enregistrer une note vocale rapide ?
Trois façons : l'élément **Enregistrement vocal** dans le menu débordant, le widget d'écran d'accueil **Enregistreur rapide**, ou l'action **Démarrer l'enregistrement audio** du geste de bord. Quelle que soit la façon dont vous le démarrez, un contrôle flottant **Arrêter** reste à l'écran - même par-dessus une autre application - jusqu'à ce que vous le touchiez pour enregistrer.

---

## Saisie et contrôles

### Prend-il en charge les claviers physiques et les manettes ?
**Oui !** La prise en charge complète du clavier, de la souris et de la manette est disponible sur tous les écrans. Appuyez sur **F1** sur n'importe quel écran pour voir les raccourcis actifs de cette surface.

### Comment réattribuer les contrôles / changer les raccourcis clavier ?
Paramètres → **Gestion** → **Contrôles et raccourcis clavier** - réattribuez n'importe quelle action à une autre touche, bouton ou entrée de manette. L'application est livrée avec 70 valeurs par défaut intégrées ; touchez **Réinitialiser** pour les restaurer. Les conflits sont mis en évidence automatiquement.

### Comment télécharger un fichier multimédia depuis une URL ?
Partagez n'importe quel lien `http(s)` vers FastMediaSorter via le **panneau de partage** Android (depuis un navigateur, une messagerie, ou n'importe quelle application). FastMediaSorter téléchargera le fichier et proposera de l'enregistrer dans n'importe laquelle de vos ressources configurées.

---

## Performance et stockage

### Comment trouver un fichier spécifique par son nom ?
Utilisez le panneau **Filtre** dans Parcourir : touchez l'icône de filtre dans la barre d'outils, saisissez n'importe quelle partie du nom de fichier dans le champ nom - la liste se met à jour instantanément. Aucune barre de recherche séparée n'est nécessaire ; le filtre couvre entièrement ce scénario.

### Pourquoi l'application est-elle lente avec 5000+ fichiers ?
L'application utilise la **pagination** pour charger les fichiers par lots. Pour les très grandes collections :
- Activez « Désactiver les vignettes » pour ce dossier
- Utilisez des filtres pour réduire les résultats
- Triez par Date (plus récent en premier) - cela charge d'abord les fichiers récents et évite d'analyser tout le dossier dès le départ

### L'application plante ou se fige
1. Forcez l'arrêt et rouvrez l'application
2. Si elle plante sur un dossier spécifique : ce dossier peut contenir un fichier corrompu - essayez d'ouvrir les fichiers un par un pour l'identifier
3. Videz le cache : Paramètres → Général → **« Vider le cache »** - cela résout la plupart des problèmes de stabilité après les mises à jour
4. Si les plantages persistent : signalez-le via GitHub Issues (lien en bas de cette page) - joignez une description de ce que vous faisiez lors du plantage

### Combien de stockage le cache de vignettes utilise-t-il ?
**Par défaut :** 2 Go (configurable dans les Paramètres)

### Comment vider le cache ?
Paramètres → Général → **« Vider le cache »**

---

## Favoris

### Comment marquer des fichiers comme favoris ?
Touchez l'**icône étoile** en visionnant un fichier.

### Où puis-je voir tous mes favoris ?
Menu principal → onglet **« Favoris »**

---

## Sécurité et confidentialité

### Puis-je protéger des dossiers par mot de passe ?
**Oui !** Modifiez le dossier → définissez un **code PIN** (4 à 6 chiffres)

### Mes données sont-elles collectées ?
**Non.** FastMediaSorter ne collecte NI n'envoie aucune donnée personnelle.

### Les raccourcis de contact sur le bureau du launcher ont-ils besoin d'accéder à mes contacts ?
**Non.** Épingler une personne sur le bureau du launcher ne demande aucune permission de contacts. Vous choisissez la personne dans le propre sélecteur de contacts d'Android, l'application lit cette seule fiche une fois, et la conserve en tant qu'instantané sur la cellule - elle ne parcourt jamais votre répertoire. L'appel utilise le numéro que vous avez choisi dans le sélecteur, donc la cellule compose exactement ce numéro.

Un groupe de permission **Contacts** facultatif existe bien, disponible à la demande, sous **Paramètres → Général → Permissions et accès**. Le refuser ne change rien au comportement ci-dessus - les raccourcis continuent de fonctionner de la même façon, sans permission.

### L'application enregistre-t-elle la position GPS dans mes photos ?
Seulement si vous l'activez. Dans **Paramètres → Gestion → Photographie**, activez la capture photo, puis activez **Géolocaliser les photos** juste en dessous - l'application demande immédiatement la permission de localisation, pas au moment du déclenchement. L'écran d'informations d'une photo géolocalisée affiche la date de capture et l'emplacement GPS issu des données EXIF de la photo, sous forme de lien touchable qui ouvre votre application de cartes ou votre navigateur.

### Puis-je voir comment j'utilise l'application ?
Oui - c'est facultatif et désactivé par défaut : activez **Collecte de statistiques** dans **Paramètres → Général** pour ouvrir un tableau de bord d'utilisation local : fichiers triés, espace libéré, temps de lecture, et plus, réparti par type de média. Rien n'est envoyé automatiquement ; **Envoyer à l'auteur** ou **Exporter** ne partage un résumé que si vous le choisissez.

---

## Traduction automatique

### Comment fonctionne la traduction ?
Deux étapes, toutes deux sur votre appareil :
- **Tesseract** lit le texte de l'image, dans chaque langue prise en charge (anglais, russe, ukrainien, bulgare, biélorusse).
- **Google ML Kit** traduit ensuite ce qui a été lu.

### Que fait la langue source « Auto » ?
« Auto » lit le texte avec le modèle anglais puis détermine la langue de ce qui a été lu pour la traduction. Pour le texte cyrillique, choisissez explicitement la langue source (par exemple **russe** ou **ukrainien**) - sinon les lettres sont lues comme leurs équivalents visuels latins.

### Cela fonctionne-t-il hors ligne ?
**Oui.** Vous n'avez besoin d'Internet qu'une seule fois pour télécharger le modèle de texte de votre langue source et le modèle de traduction pour votre paire de langues.

### Pourquoi la traduction est-elle parfois plus lente ?
La première utilisation d'une langue charge son modèle de texte, et les images volumineuses ou détaillées prennent plus de temps à lire. Les exécutions suivantes dans la même langue démarrent plus vite.

### Qu'est-ce que le mode de traduction façon loupe ?
Le **mode façon loupe** affiche les traductions en superposition par-dessus l'image originale, à la manière de Google Lens. Cela vous permet de voir le texte traduit dans son contexte et sa position d'origine. Vous pouvez l'activer dans **Paramètres → Médias → Autre** (l'interrupteur « Superposition façon loupe »).

Le **mode standard** affiche les traductions dans une vue texte séparée sous l'image.

---

## Musique de fond pour diaporama

### Comment ajouter de la musique de fond aux diaporamas ?
1. Ajoutez un dossier contenant des fichiers audio comme ressource
2. Allez dans **Paramètres → Médias → Images**
3. Activez **« Jouer de la musique pendant le diaporama »**
4. Sélectionnez votre ressource musicale dans le menu déroulant
5. Démarrez n'importe quel diaporama - la musique jouera automatiquement !

### Puis-je utiliser de la musique depuis des lecteurs réseau ou le stockage cloud ?
**Oui !** L'application gère automatiquement les fichiers réseau en les téléchargeant vers le cache avant la lecture. Cela fonctionne avec SMB, SFTP, FTP, Google Drive, OneDrive et Dropbox.

### Comment passer d'un morceau à l'autre pendant le diaporama ?
Touchez le **nom du morceau** affiché pendant le diaporama pour passer à un autre morceau aléatoire de votre ressource musicale.

### Cela fonctionne-t-il avec toutes les éditions ?
**Presque.** La musique de diaporama nécessite la prise en charge audio :
- **Standard**, **Legacy**, **XR / noLegal** - prise en charge audio complète, y compris la lecture qui continue en arrière-plan
- **Lite** - lit les fichiers audio locaux et les paroles, mais n'a pas de service de lecture en arrière-plan, donc le son s'arrête quand l'application quitte le premier plan
- **Photos** - aucune prise en charge audio du tout, donc pas de musique de diaporama

---

## Flux Internet

### FastMediaSorter lit-il la radio Internet ?
Oui. L'écran **Flux** lit les flux audio http/https (mp3/aac), la radio Icecast/Shoutcast avec les métadonnées ICY du morceau en cours, le HLS (.m3u8) et le DASH VOD, ainsi que les sources RTSP. Disponible dans Standard, Legacy et XR / noLegal. Lite et Photos n'ont pas d'écran Flux du tout - la fonctionnalité y est absente, pas simplement limitée à certains protocoles.

### Comment ouvrir l'écran Flux ?
Touchez **Flux** dans le menu déroulant de la fenêtre principale (visible lorsque les Flux sont activés). Vous pouvez aussi y accéder via **Paramètres > Médias > Flux**, où se trouve l'interrupteur principal.

### Comment ajouter une station de radio ?
Sur l'écran Flux, touchez **⋮** à l'extrémité de la barre d'outils, choisissez **Ajouter un flux** et collez l'URL de la station. Touchez Enregistrer. La station apparaît immédiatement dans la liste.

### Puis-je importer une playlist ?
Oui - touchez **⋮ > Importer depuis une URL** et saisissez une adresse `.m3u` distante. Le même menu contient **Mettre à jour le catalogue FastMediaSorter** pour la liste sélectionnée (avec des puces de thème et de langue), également disponible depuis **Paramètres > Extensions** ou l'écran d'accueil de bienvenue.

### Un flux ne joue pas - que faire ?
Si un flux échoue, une boîte de dialogue apparaît avec les options **Réessayer**, **Supprimer** et **Annuler**. Les redirections 301 inter-protocoles sont gérées automatiquement. Si l'hôte est mort ou très lent, l'import du catalogue expire rapidement plutôt que de rester bloqué.

### La radio continue-t-elle de jouer quand je quitte l'écran Flux ?
Cela dépend de **Paramètres > Lecteur > Lecture audio en arrière-plan**. Avec l'audio en arrière-plan ACTIVÉ, la lecture continue. Avec DÉSACTIVÉ, quitter l'écran arrête le flux et propose un choix Arrêter / Garder la lecture - le même comportement que le lecteur audio local.

### Puis-je voir des vignettes en direct pour les flux ?
Basculez l'interrupteur de la barre d'outils Flux sur la vue **Grille** - chaque chaîne s'affiche sous forme de tuile avec sa dernière image capturée, afin que vous puissiez voir en un coup d'œil ce qui est diffusé. La tuile reste visible même après avoir fermé et rouvert l'application, puis se rafraîchit avec une nouvelle capture une fois le flux à nouveau actif.

### Puis-je diffuser un flux sur ma TV ?
Oui, pour les flux vidéo - touchez **Cast** dans le lecteur et choisissez un Chromecast sur le même réseau Wi-Fi. Les flux RTSP ne peuvent pas être diffusés ; le bouton n'apparaît que pour les formats pris en charge par le récepteur Chromecast.

---

## Wear OS

### FastMediaSorter fonctionne-t-il sur les montres connectées Wear OS ?
**Oui !** FastMediaSorter v2 inclut une application compagnon Wear OS, et elle est passée d'une simple visionneuse de fichiers locaux à un véritable second écran pour vos médias.

### Que puis-je faire sur la montre ?
- **Parcourir et lire** - les dossiers et favoris de votre téléphone associé, ou le stockage local propre de la montre, sous forme de grille de vignettes avec recherche, filtre et tri. L'audio et la vidéo se lisent avec lecture aléatoire, volume à la lunette, et un mode écran éteint qui garde le son actif.
- **Déplacer des fichiers dans les deux sens** - envoyez une photo, une vidéo ou un morceau du téléphone directement à la montre (depuis le panneau de partage), ou copiez un fichier de la montre vers un dossier du téléphone que vous choisissez.
- **Enregistrer une note vocale sur votre poignet** - elle attend sur la montre jusqu'à ce que vous l'envoyiez au téléphone, donc rien n'est perdu en cours d'enregistrement.
- **Lire des flux en direct** - la radio et la vidéo de votre catalogue Flux se lisent directement depuis la propre liste de chaînes de la montre, avec vos favoris épinglés en haut.
- **Un coup d'œil sans ouvrir l'application** - ajoutez une tuile FastMediaSorter au panneau de balayage du cadran de votre montre pour un aperçu rapide, ou sa complication à un cadran compatible.
- **Petits outils intégrés** - une calculatrice, un moniteur réseau et le mini-jeu ont chacun leur propre écran sur la montre.

Les réglages que vous modifiez sur le téléphone se synchronisent avec la montre et inversement, donc vous ne les configurez qu'une seule fois.

**Remarque :** la montre n'ouvre jamais de ressources cloud d'elle-même - elle n'a pas de client cloud, et le téléphone ne lui transmet pas ses dossiers cloud ; un fichier cloud n'atteint la montre que lorsque vous l'ouvrez sur le téléphone et choisissez votre montre dans « Envoyer vers.. ». Dans la version complète de l'application montre (APK direct), la montre se connecte bien d'elle-même aux partages SMB, FTP et SFTP via Wi-Fi - les ressources réseau que vous lui envoyez depuis le téléphone. La version Google Play de l'application montre est une petite première version (calculatrice, chronomètre, mini-jeu et paramètres) et ne parcourt pas encore les médias.

---

## Livres électroniques EPUB

### Comment activer la prise en charge des EPUB ?
Paramètres → Médias → **Documents** → **« Prise en charge des livres électroniques EPUB »**

**Remarque :** redémarrez l'application après l'activation pour que les changements prennent effet.

### Comment lire un livre EPUB ?
1. Ajoutez un dossier contenant des fichiers .epub comme ressource
2. Ouvrez le dossier - vous verrez les fichiers EPUB avec un badge « E »
3. Touchez n'importe quel fichier EPUB pour l'ouvrir dans la liseuse

### Puis-je naviguer entre les chapitres ?
**Oui !** Utilisez :
- Les **boutons Précédent/Suivant** en bas
- **Balayez gauche/droite** pour changer de chapitre
- Le **bouton TOC** (icône 📋) pour ouvrir la table des matières

### Puis-je ajuster la taille de la police ?
**Oui !** Pendant la lecture, utilisez les **boutons -A/+A** en bas pour diminuer/augmenter la taille de la police (plage de 6 à 144 px). Les réglages sont enregistrés par livre.

### Puis-je rechercher du texte dans un EPUB ?
**Oui !** Touchez le **bouton Rechercher** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> pour ouvrir le panneau de recherche. Tapez votre requête et naviguez entre les correspondances avec les boutons Précédent/Suivant.

### Cela fonctionne-t-il avec les fichiers réseau/cloud ?
**Oui !** Les fichiers EPUB sont automatiquement téléchargés vers le cache lorsqu'ils sont ouverts depuis SMB/SFTP/FTP/stockage cloud.

### Mémorise-t-il ma position de lecture ?
**Oui !** L'application enregistre le dernier chapitre que vous lisiez. Quand vous rouvrez le livre, elle reprend là où vous vous étiez arrêté.

### Qu'en est-il du thème clair/sombre ?
La liseuse EPUB s'adapte automatiquement au thème de votre application (Paramètres → Général → Thème de couleur).

---

## Opérations planifiées

### Que sont les opérations planifiées ?
Des règles d'automatisation basées sur le temps qui exécutent des opérations de Copie, Déplacement ou Suppression entre n'importe lesquelles de vos ressources (dossiers locaux, NAS, cloud) selon un horaire répétitif - même quand l'application est fermée.

### Où configurer les opérations planifiées ?
Paramètres → **Gestion** → **Opérations planifiées par horaire**. Touchez **« + »** pour ajouter une nouvelle règle.

### Cela fonctionnera-t-il si mon application est fermée ?
**Oui.** Les opérations sont planifiées via **WorkManager** d'Android, qui les exécute en arrière-plan que l'application soit ouverte ou non.

### Pourquoi une opération planifiée ne s'est-elle pas exécutée à l'heure exacte ?
Android peut retarder les tâches WorkManager de quelques minutes pour optimiser la batterie. Pour un timing plus fiable, accordez à l'application l'exemption **Optimisation de la batterie** (Paramètres → Général → Optimisation de la batterie). L'intervalle minimum est de 15 minutes.

### L'opération planifiée s'est exécutée mais a copié 0 fichier
C'est généralement normal - cela signifie que tous les fichiers étaient déjà présents dans la destination (l'opération utilise « ignorer les existants » par défaut). Pour vérifier : consultez le journal de l'opération et regardez le nombre « ignorés » par rapport au nombre « copiés ».

Si vous vous attendiez à ce que de nouveaux fichiers soient copiés mais qu'ils ne l'ont pas été :
- Assurez-vous que la **Source** est réglée sur la bonne ressource (par ex. la ressource virtuelle « Photos de l'appareil photo » - pas un chemin manuel qui pourrait être incorrect)
- Vérifiez que la ressource de destination (SMB / cloud) était accessible au moment planifié - si le Wi-Fi était désactivé, l'exécution est ignorée et retentée la fois suivante

### Puis-je voir ce qui a été traité ?
**Oui.** Touchez **« Voir le journal »** dans la section Opérations planifiées pour voir un historique horodaté de chaque exécution, y compris les résultats par fichier.

---

## Bloc météo

### D'où vient la météo ?
Le bloc météo du bureau utilise **Open-Meteo.com** - un service météo gratuit et sans clé. Données météo par Open-Meteo.com (CC-BY 4.0).

### L'application suit-elle ma position ?
**Non.** Le lieu est celui que vous saisissez vous-même, et aucune permission de localisation n'est demandée. Le bloc se rafraîchit environ toutes les 20 minutes et affiche la dernière lecture avec une mention « Dernière connue » lorsqu'il n'y a pas de connexion. Le toucher ouvre l'application météo de l'appareil.

---
## Encore des questions ?

Vous n'avez pas trouvé de réponse ci-dessus, ou quelque chose ne fonctionne pas comme décrit ? **N'hésitez pas à nous contacter** - chaque message est lu et la plupart des problèmes sont résolus.

- � **Guides How-To** (tâches pas à pas) : [HOW_TO-fr.md](HOW_TO-fr.md)
- 🚀 **Démarrage rapide :** [QUICK_START-fr.md](QUICK_START-fr.md)
- 🔧 **Dépannage :** [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.md)
- �📧 **E-mail :** [sza@ukr.net](mailto:sza@ukr.net) - pour tout : aide à la configuration, description de bugs, souhaits de fonctionnalités
- 🌐 **Page de l'auteur :** [sza.od.ua](https://sza.od.ua)
- 🐛 **Signaler un bug :** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - préféré pour les bugs reproductibles ; indiquez la version d'Android et ce que vous faisiez
- 📖 **Documentation complète :** [Portail de documentation](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vous voulez une fonctionnalité qui n'existe pas encore ?** Écrivez - de nombreuses fonctionnalités de l'application ont été ajoutées parce que quelqu'un les a demandées. Si cela a du sens pour le cas d'usage, elle finit par être développée.

</div>
