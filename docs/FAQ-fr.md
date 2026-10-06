---
layout: default
title: "❓ Foire aux questions (FAQ)"
permalink: /docs/FAQ-fr.html
lang: fr
---

<div lang="fr" markdown="1">

<div lang="fr" dir="ltr" markdown="1">

# ❓ Foire aux questions (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="fr" %}

---

## Questions générales

### Qu'est-ce que FastMediaSorter ?
FastMediaSorter réunit lecture multimédia et gestion de fichiers locaux, réseau et cloud. Lanceur, flux et liaison montre dépendent de l'édition. L'accès exige vos autorisations ; remplacer l'accueil nécessite votre choix explicite dans Android.

### Est-ce gratuit ?
Oui ! FastMediaSorter v2 est totalement gratuit et open source.

### De quelle version d'Android ai-je besoin ?
Les minima du code actuel figurent ci-dessous. L'immersion VR/XR nécessite aussi un casque/runtime compatible ; **noLegal n'est pas réservée aux casques**. Une variante du code ne garantit pas un APK publié.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK (EN)](TECHNICAL_REQUIREMENTS.html)

### Faut-il une connexion Internet ?
Les fichiers locaux n'ont pas besoin d'internet. SMB/SFTP/FTP sur LAN demandent un réseau accessible, pas internet public. Cloud et flux internet exigent internet ; disponibilité selon l'édition.

### L'application a-t-elle des widgets ?
Oui ! FastMediaSorter v2 propose une variété de widgets d'écran d'accueil - trouvez-les via un appui long sur l'écran d'accueil → Widgets → FastMediaSorter. Ils incluent des raccourcis de ressources, des lanceurs de diaporama, et plus encore.

### L'application peut-elle remplacer mon écran d'accueil ?
Dans **Standard/noLegal** : **Paramètres → Général → Fenêtre de démarrage principale → Écran d'accueil de l'appareil**, puis choisir FastMediaSorter comme accueil Android. Le **Bureau comme fenêtre principale** ne remplace pas le lanceur système.

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Comment arrêter que l'application soit mon écran d'accueil ?
Choisissez **Quitter le mode lanceur** dans Démarrer, une autre fenêtre initiale ou une autre application d'accueil par défaut dans Android. La disposition est conservée ; le chemin système varie selon l'appareil.

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Pourquoi ma tablette est-elle revenue à son ancien écran d'accueil après un redémarrage ?
Vérifiez l'accueil Android par défaut et la fenêtre initiale. Choisissez **Toujours** si proposé. Firmware, mise à jour ou réinitialisation peuvent changer ce choix ; un redémarrage ne prouve pas la cause. Indiquez modèle et version Android.

### Puis-je mettre mes propres dossiers et playlists sur le bureau ?
Oui - c'est à ça que sert le bureau. Appuyez longuement sur une case vide et choisissez **Ajouter un élément..**, puis choisissez ce que vous voulez : l'un de vos dossiers, un flux radio, une application, une personne, ou un gadget comme l'horloge ou la météo. La nouvelle cellule se place sur la case sur laquelle vous avez appuyé, et pour un dossier, vous choisissez aussi s'il s'ouvre en mode parcourir, diaporama ou lecture. Pour réorganiser les choses ensuite, choisissez **Modifier le bureau** depuis le même menu d'appui long. Voir [HOW_TO](HOW_TO-fr.html#how-to-use-the-app-as-your-home-screen) pour le guide complet.

---

## Opérations sur les fichiers

### Où vont les fichiers supprimés ?
Avec la corbeille active, les chemins locaux ordinaires compatibles utilisent `.trash/`. **Suppression définitive**, `content://`, SMB/SFTP/FTP/cloud et chemins protégés `/Android/media/` n'utilisent pas cette politique. Vider la corbeille est irréversible ; toutes les suppressions ne sont pas récupérables.

### Puis-je annuler une suppression/un déplacement ?
Utilisez immédiatement **Annuler**, seulement si l'action est proposée. Cela dépend de l'opération, de l'écran et des chemins. Suppression définitive ou réseau/document-tree n'est pas récupérable via la corbeille locale ; les transferts réseau/cloud ne sont pas toujours réversibles. Annuler ne remplace pas une sauvegarde.

### Quelle est la différence entre Copier et Déplacer ?
- **Copier :** crée un doublon, l'original reste en place
- **Déplacer :** déplace le fichier, le supprime de l'emplacement d'origine

### Qu'est-ce que le mode Tous les fichiers ?
Tous les fichiers retire les filtres multimédias **dans les ressources accessibles**, sans contourner les permissions Android. Les formats non pris en charge se gèrent ou s'ouvrent ailleurs ; afficher APK/EXE/archives ne signifie pas exécution ou extraction.

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

Utilisez un compte NAS/Windows autorisé et un partage SMB accessible. Autorisez TCP **445** uniquement sur le réseau privé fiable et le sous-réseau nécessaire ; **ne désactivez pas le pare-feu et n'exposez pas SMB à internet**. Vérifiez droits, adresse, routes VPN et isolation des invités. Un VPN privé permet l'accès distant, même via données mobiles.

[Guide de configuration SMB](howto/scenario-smb-setup-fr.html)

### Comment me connecter à Google Drive ?
1. Touchez **« + »** → **Cloud** → **Google Drive**
2. Touchez « Se connecter avec Google »
3. Accordez les permissions lorsqu'on vous le demande
4. Vos dossiers Drive apparaîtront

Les fichiers s'ouvrent à la demande, mais affichage, miniatures ou lecture peuvent télécharger dans le cache. Ce n'est pas une synchronisation automatique de tout Drive.

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
**Oui** - Fast Media Sorter for Windows publie les dossiers PC choisis via SFTP et affiche un code QR / une configuration `.fmscfg`. Sur le téléphone, utilisez **Importer depuis companion** ou **Scanner le code QR** sur l'écran Ajouter une ressource. Voir le guide côté PC : [Comment publier des dossiers PC vers Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Pourquoi les vignettes ne se chargent-elles pas pour les fichiers réseau ?
Les vignettes réseau se génèrent **à la demande** pour économiser la bande passante. Faites défiler lentement ou attendez quelques secondes qu'elles apparaissent.

Si les vignettes ne se chargent jamais du tout :
- Vérifiez que la connexion est active : touchez la ressource → si le dossier s'ouvre, la connexion fonctionne
- Modifiez la ressource → assurez-vous que **« Charger les vignettes »** est activé
- Pour les connexions très lentes : désactivez complètement les vignettes pour éviter les délais d'expiration (Modifier la ressource → désactiver les vignettes)

### La connexion se coupe sans cesse / les fichiers ne s'ouvrent pas en cours de lecture
Vérifiez Wi-Fi, serveur, identifiants et routes VPN. Testez le débit de la ressource et réduisez les miniatures. Le débit dépend du bitrate/codec, pas seulement de la résolution ; **10 Mbit/s n'est pas un minimum universel pour 1080p**.

---

## Tri rapide et destinations

### Que sont les dossiers « Tri rapide » ?
Les dossiers de tri rapide sont des dossiers cibles préconfigurés pour un tri rapide des fichiers. Vous pouvez assigner jusqu'à 10 dossiers avec des boutons numérotés.

### Comment configurer le tri rapide ?
**Méthode 1 :** Paramètres → Gestion → Destinations de tri rapide, puis touchez **« Ajouter au tri rapide »**  
**Méthode 2 :** Modifiez n'importe quel dossier → activez « Marquer pour le tri rapide »

### Comment utiliser le tri rapide en visionnant des fichiers ?
Ouvrez un fichier et choisissez la destination dans le panneau. Vérifiez **Copier** ou **Déplacer** avant confirmation. Les zones varient selon média/mode ; le coin inférieur gauche ne copie pas toujours.

### Puis-je utiliser les touches numériques au lieu de toucher l'écran ?
Oui - connectez un clavier physique, une manette, ou une télécommande TV, et vos boutons de tri rapide seront automatiquement numérotés (0-9). Appuyez sur le chiffre correspondant pour copier ou déplacer instantanément le fichier vers cette destination, exactement comme en touchant le bouton.

### Les boutons de tri rapide ne s'affichent pas
Assurez-vous d'avoir d'abord ajouté au moins un dossier de destination : Paramètres → Gestion → Destinations de tri rapide, puis **« Ajouter au tri rapide »**. Les boutons n'apparaissent que lorsqu'au moins une destination est configurée.

### J'ai envoyé un fichier par erreur dans le mauvais dossier
Utilisez **Annuler** si proposé. Sinon vérifiez source/destination et remettez le fichier manuellement. Copier conserve l'original ; ne supprimez aucune copie avant vérification.

---

## Zones tactiles

### Que sont les « zones tactiles » ?
La carte dépend du média/mode : images en 3×3 possible, audio/vidéo réservent les commandes et utilisent pause/reprise au centre. Le panneau emploie trois colonnes ; les documents utilisent les balayages sans zones de toucher. L'overlay montre la carte active.

### Comment voir les zones tactiles ?
Paramètres → Lecteur → **« Toujours afficher la superposition des zones tactiles »**

### Puis-je désactiver les zones tactiles ?
Désactivez la grille de neuf zones et utilisez le panneau. Cela **ne désactive pas tous les gestes** : navigation et commandes restent dans trois colonnes ; les documents gardent leurs balayages.

---

## Capture d'écran et vocale

### Qu'est-ce que le bandeau de gestes sur le bord gauche ?
C'est un menu de capture rapide que vous ouvrez avec un balayage diagonal depuis le bord gauche de l'écran. Activez-le dans **Paramètres → Gestion → Gestes de bord d'écran → Superposition de gestes**. Depuis le menu, vous pouvez prendre une capture d'écran, une photo, recadrer et partager l'image actuelle, ouvrir un raccourci d'application ou de panneau, ou démarrer un enregistrement écran, vidéo ou voix - le tout sans quitter ce que vous regardez. Disponible dans Standard et XR/noLegal.

### Comment enregistrer une note vocale rapide ?
Trois façons : l'élément **Enregistrement vocal** dans le menu débordant, le widget d'écran d'accueil **Enregistreur rapide**, ou l'action **Démarrer l'enregistrement audio** du geste de bord. Quelle que soit la façon dont vous le démarrez, un contrôle flottant **Arrêter** reste à l'écran - même par-dessus une autre application - jusqu'à ce que vous le touchiez pour enregistrer.

---

## Saisie et contrôles

### Prend-il en charge les claviers physiques et les manettes ?
Clavier, souris et manette dépendent de l'écran/appareil. **F1** montre les raccourcis sur les écrans compatibles, sans garantir chaque touche dans chaque dialogue.

### Comment réattribuer les contrôles / changer les raccourcis clavier ?
**Paramètres → Gestion → Commandes et raccourcis** permet de changer les actions compatibles. **Reset** restaure les valeurs ; conflits signalés. Consultez la liste actuelle plutôt qu'un total fixe de 70.

### Comment télécharger un fichier multimédia depuis une URL ?
Partagez une URL `http(s)` compatible dans Android. Un fichier direct n'est pas une page web, une vidéo protégée ou un flux DRM. Téléchargement et destinations modifiables dépendent de l'édition, de l'URL et des autorisations.

---

## Performance et stockage

### Comment trouver un fichier spécifique par son nom ?
Utilisez le panneau **Filtre** dans Parcourir : touchez l'icône de filtre dans la barre d'outils, saisissez n'importe quelle partie du nom de fichier dans le champ nom - la liste se met à jour instantanément. Aucune barre de recherche séparée n'est nécessaire ; le filtre couvre entièrement ce scénario.

### Pourquoi l'application est-elle lente avec 5000+ fichiers ?
Les grands dossiers demandent liste, métadonnées et miniatures. Limitez la ressource, filtrez et réduisez les miniatures. Le tri par date **ne garantit pas** l'absence de scan complet ; source et formats déterminent la vitesse.

### L'application plante ou se fige
Rouvrez l'app et vérifiez espace, autorisations et connexion. Vider le cache peut corriger des miniatures périmées, pas tous les plantages. Signalez version/édition, Android, ressource et étapes ; masquez identifiants/chemins privés des journaux.

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
Le **PIN de ressource** limite l'accès dans l'app, mais **ne chiffre pas les fichiers** et ne bloque pas d'autres apps/utilisateurs autorisés du serveur. Utilisez le chiffrement du stockage pour protéger hors de l'app.

### Mes données sont-elles collectées ?
L'app n'envoie pas automatiquement de statistiques à l'auteur. Elles restent locales jusqu'à export/envoi. Cloud, flux et météo contactent les fournisseurs choisis avec les requêtes nécessaires. Consultez la politique de confidentialité.

[Politique de confidentialité (EN)](PRIVACY_POLICY.html)

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

Selon édition/appareil. Traduction ML Kit uniquement sur téléphones, tablettes, Chromebook et ordinateurs, pas TV, automobile, montres ou XR. OCR distinct : API 26+, au moins 3 Go RAM et appareil non low-RAM.

### Que fait la langue source « Auto » ?
« Auto » lit le texte avec le modèle anglais puis détermine la langue de ce qui a été lu pour la traduction. Pour le texte cyrillique, choisissez explicitement la langue source (par exemple **russe** ou **ukrainien**) - sinon les lettres sont lues comme leurs équivalents visuels latins.

### Cela fonctionne-t-il hors ligne ?
**Oui.** Vous n'avez besoin d'Internet qu'une seule fois pour télécharger le modèle de texte de votre langue source et le modèle de traduction pour votre paire de langues.

### Pourquoi la traduction est-elle parfois plus lente ?
La première utilisation d'une langue charge son modèle de texte, et les images volumineuses ou détaillées prennent plus de temps à lire. Les exécutions suivantes dans la même langue démarrent plus vite.

### Qu'est-ce que le mode de traduction façon loupe ?
Le mode superposé affiche des blocs traduits sur l’image ; le mode standard montre un texte séparé. Sur appareils compatibles : **Paramètres → Médias → Traduction, numérisation (OCR) → Résultat de la traduction en blocs**.

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
Standard, noLegal, Legacy, VR, XR et FOSS prennent en charge l'audio persistant en arrière-plan. Lite : audio local sans service persistant ; Photos : aucun audio. Réseau/cloud dépendent aussi de l'édition.

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Flux Internet

### FastMediaSorter lit-il la radio Internet ?
**Flux** prend en charge radio HTTP(S)/ICY, HLS/DASH et RTSP selon source/codec. Disponible dans **Standard, noLegal, Legacy, VR, XR**, pas **Lite, Photos, FOSS**. Une URL ne contourne pas incompatibilités ou DRM.

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Comment ouvrir l'écran Flux ?
Touchez **Flux** dans le menu déroulant de la fenêtre principale (visible lorsque les Flux sont activés). Vous pouvez aussi y accéder via **Paramètres > Médias > Flux**, où se trouve l'interrupteur principal.

### Comment ajouter une station de radio ?
Sur l'écran Flux, touchez **⋮** à l'extrémité de la barre d'outils, choisissez **Ajouter un flux** et collez l'URL de la station. Touchez Enregistrer. La station apparaît immédiatement dans la liste.

### Puis-je importer une playlist ?
Oui - touchez **⋮ > Importer depuis une URL** et saisissez une adresse `.m3u` distante. Le même menu contient **Mettre à jour le catalogue FastMediaSorter** pour la liste sélectionnée (avec des puces de thème et de langue), également disponible depuis **Paramètres > Extensions** ou l'écran d'accueil de bienvenue.

### Un flux ne joue pas - que faire ?
Si un flux échoue, une boîte de dialogue apparaît avec les options **Réessayer**, **Supprimer** et **Annuler**. Les redirections 301 inter-protocoles sont gérées automatiquement. Si l'hôte est mort ou très lent, l'import du catalogue expire rapidement plutôt que de rester bloqué.

### La radio continue-t-elle de jouer quand je quitte l'écran Flux ?
Avec audio persistant compatible et activé, la sortie suit Arrêter / Continuer / Demander. Sans ce mode, le son cesse quand l’écran quitte le premier plan. Vérifiez Paramètres → Lecteur.

### Puis-je voir des vignettes en direct pour les flux ?
Basculez l'interrupteur de la barre d'outils Flux sur la vue **Grille** - chaque chaîne s'affiche sous forme de tuile avec sa dernière image capturée, afin que vous puissiez voir en un coup d'œil ce qui est diffusé. La tuile reste visible même après avoir fermé et rouvert l'application, puis se rafraîchit avec une nouvelle capture une fois le flux à nouveau actif.

### Puis-je diffuser un flux sur ma TV ?
Oui, pour les flux vidéo - touchez **Cast** dans le lecteur et choisissez un Chromecast sur le même réseau Wi-Fi. Les flux RTSP ne peuvent pas être diffusés ; le bouton n'apparaît que pour les formats pris en charge par le récepteur Chromecast.

---

## Wear OS

### FastMediaSorter fonctionne-t-il sur les montres connectées Wear OS ?
L'**app Wear OS** séparée exige au moins API 28 ; APK sur la montre. Liaison téléphone : **Standard/noLegal**, identifiants/signatures compatibles. Le **cadran WFF v4** séparé nécessite Wear OS 6 / API 36.

[Matrice des fonctions (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Que puis-je faire sur la montre ?
Le code Wear actuel propose médias locaux/réseau, transferts téléphone, flux, enregistrement et outils. Les paramètres/ressources compatibles se synchronisent, pas tous les réglages du téléphone. Pas de client cloud indépendant. **Les versions publiées peuvent proposer moins que le code** ; consultez la description de téléchargement/version.

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
Les règles exécutent Copier, Déplacer ou Supprimer compatibles sur les ressources accessibles. Permissions, identifiants et disponibilité restent nécessaires. Testez d'abord avec **Copier** ; suppression planifiée pas automatiquement réversible.

### Où configurer les opérations planifiées ?
Paramètres → **Gestion** → **Opérations planifiées par horaire**. Touchez **« + »** pour ajouter une nouvelle règle.

### Cela fonctionnera-t-il si mon application est fermée ?
WorkManager peut agir après fermeture, sans garantie après **Arrêt forcé** Android, appareil éteint ou conditions/permissions manquantes. Rouvrez ensuite et vérifiez règle/journal.

### Pourquoi une opération planifiée ne s'est-elle pas exécutée à l'heure exacte ?
WorkManager n'est pas une alarme exacte. Batterie, réseau et appareil peuvent retarder plus de quelques minutes. Intervalle minimal : **15 minutes** ; l'exemption batterie ne garantit pas l'heure précise.

### L'opération planifiée s'est exécutée mais a copié 0 fichier
Vérifiez le journal : zéro copie peut être fichiers existants ignorés, aucun résultat, ressource indisponible ou refus de permission. Vérifiez source, filtres, destination, identifiants ; zéro n'est pas toujours un succès.

### Puis-je voir ce qui a été traité ?
**Oui.** Touchez **« Voir le journal »** dans la section Opérations planifiées pour voir un historique horodaté de chaque exécution, y compris les résultats par fichier.

---

## Bloc météo

### D'où vient la météo ?
Le bloc météo du bureau utilise **Open-Meteo.com** - un service météo gratuit et sans clé. Données météo par Open-Meteo.com (CC-BY 4.0).

### L'application suit-elle ma position ?
Le **bloc météo** utilise le lieu saisi, sans suivi GPS, et l'envoie au service météo. Le géomarquage photo optionnel est distinct et demande une permission de localisation.

---
## Encore des questions ?

Vous n'avez pas trouvé de réponse ci-dessus, ou quelque chose ne fonctionne pas comme décrit ? **N'hésitez pas à nous contacter** - chaque message est lu et la plupart des problèmes sont résolus.

- 📖 **Guides How-To** (tâches pas à pas) : [HOW_TO-fr.md](HOW_TO-fr.html)
- 🚀 **Démarrage rapide :** [QUICK_START-fr.md](QUICK_START-fr.html)
- 🔧 **Dépannage :** [TROUBLESHOOTING-fr.md](TROUBLESHOOTING-fr.html)
- 📧 **E-mail :** [sza@ukr.net](mailto:sza@ukr.net) - pour tout : aide à la configuration, description de bugs, souhaits de fonctionnalités
- 🌐 **Page de l'auteur :** [sza.od.ua](https://sza.od.ua)
- 🐛 **Signaler un bug :** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - préféré pour les bugs reproductibles ; indiquez la version d'Android et ce que vous faisiez
- 📖 **Documentation complète :** [Portail de documentation](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vous voulez une fonctionnalité qui n'existe pas encore ?** Écrivez - de nombreuses fonctionnalités de l'application ont été ajoutées parce que quelqu'un les a demandées. Si cela a du sens pour le cas d'usage, elle finit par être développée.

</div>

</div>
