---
layout: default
title: "Cadre photo numérique sur tablette - FastMediaSorter v2"
permalink: /docs/howto/scenario-photo-frame-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🖼️ Cadre photo numérique sur tablette

> **Niveau :** Débutant &bull; **Durée :** ~15 minutes &bull; **Édition :** Standard, Photos, Legacy, VR, noLegal (pour les photos NAS/cloud) ou n'importe quelle édition (pour les photos locales)

{% include lang-switcher.html doc="scenario-photo-frame" dir="/docs/howto/" current="fr" %}

Transformez n'importe quelle tablette Android en un magnifique cadre photo numérique toujours allumé - diffusant vos souvenirs depuis un NAS domestique ou le cloud, avec musique de fond en option. Aucun stockage local utilisé.

> **L'idée en une phrase :** installez une ancienne tablette, branchez-la, lancez un diaporama - elle affiche automatiquement vos photos, pour toujours, en changeant toutes les quelques secondes. Comme un vrai cadre photo numérique acheté en magasin, mais alimenté par votre propre collection de photos depuis n'importe quelle source.

---

## Ce dont vous aurez besoin

- Une tablette Android (n'importe quelle taille - une ancienne fonctionne très bien !)
- Un support ou une fixation pour maintenir la tablette debout
- Un **chargeur USB** pour la garder branchée - la tablette fonctionnera toute la journée, donc la batterie ne suffit pas
- Vos photos sur l'un de : **stockage local**, **PC/NAS domestique via SMB**, ou **Google Drive / Dropbox**
- (Optionnel) Une source de musique pour l'audio de fond

---

## Étape 1 - Ajouter votre source de photos

Choisissez où se trouvent vos photos :

**Option A - Photos locales (sur la tablette elle-même) :**
1. Appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Dossier local** → naviguez jusqu'à votre dossier de photos → **Sélectionner**

**Option B - NAS domestique / PC Windows (SMB) :**
1. Appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Dossier réseau (SMB)**
2. Appuyez sur **"Analyser le réseau"** → sélectionnez votre PC/NAS dans la liste
3. Remplissez le nom du partage + nom d'utilisateur + mot de passe
4. Appuyez sur **Tester la connexion** → **Enregistrer**

> Configuration SMB complète : [Se connecter à un NAS (SMB)](scenario-smb-setup-fr.md). Cela prend environ 5 minutes à configurer une fois, puis fonctionne pour toujours.

**Option C - Google Drive / Dropbox :**
1. Appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Stockage cloud** → choisissez le fournisseur
2. Appuyez sur **Se connecter** → terminez l'authentification dans le navigateur
3. Sélectionnez le dossier contenant vos photos → **Terminé**

![FastMediaSorter main screen - photo resource cards visible after adding a photo folder](screenshots/screenshot-pf-step1.png)

---

## Étape 2 - Configurer le dossier pour le diaporama

Appuyez longuement sur votre dossier de photos sur l'écran principal → appuyez sur **Modifier (icône crayon)**.

Réglez ces options :

| Paramètre | Valeur recommandée | Pourquoi |
|---------|------------------|-----|
| **Intervalle du diaporama** | 5-10 secondes | 5 s = ambiance animée d'album de famille ; 10 s = calme, adapté aux photos artistiques ou aux grands groupes où vous voulez le temps de reconnaître chacun |
| **Inclure les sous-dossiers** | ACTIVÉ | Affiche les photos de tous les sous-dossiers - idéal si vous organisez par année/album |
| **Mode de tri** | Date de prise (plus récent d'abord) ou Aléatoire | Aléatoire = plus de variété au quotidien ; Date = les photos les plus récentes apparaissent en premier |
| **Types pris en charge** | Images uniquement | Retirez Vidéo et Audio - sinon les fichiers vidéo se liront aussi, interrompant le déroulement du diaporama |

Appuyez sur **Enregistrer**.

![Edit Resource - Slideshow Interval and Include Subfolders settings](screenshots/screenshot-pf-step2.png)

---

## Étape 3 - (Optionnel) Ajouter de la musique de fond

Vous voulez une musique douce pendant que vous regardez les photos ? Voici comment (nécessite une édition avec audio - l'édition Photos n'en a pas) :

1. D'abord, ajoutez une source de musique : appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → Dossier local → naviguez jusqu'à votre dossier de musique
2. Allez dans **Paramètres → onglet Média → Lecture audio, pochettes et visuels**
3. Activez **"Afficher des photos aléatoires pendant la lecture audio"**

Puis allez dans **Paramètres → onglet Média → Images, GIF et diaporama** :
4. Activez **"Jouer de la musique pendant le diaporama"**
5. Appuyez sur **"Sélectionner la source musicale"** → choisissez votre ressource musicale

> **Astuce :** Si la musique bégaie quand les photos proviennent d'un NAS, utilisez un dossier de musique local pour l'audio et laissez uniquement les photos être diffusées depuis le réseau - vous pouvez librement mélanger les sources de cette façon.


---

## Étape 4 - Démarrer le diaporama

1. Appuyez sur votre **dossier de photos** sur l'écran principal pour l'ouvrir
2. Appuyez sur **n'importe quelle photo** pour ouvrir la visionneuse plein écran
3. Appuyez sur **"Diaporama" <img src="../icons/doc/ic_slideshow.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils supérieure

C'est tout - le diaporama fonctionne. Les photos avancent automatiquement à l'intervalle que vous avez défini.

> **Démarrage rapide alternatif :** appuyez sur la **zone en bas à droite** de l'écran photo (l'écran est divisé en une grille invisible de 3×3 zones tactiles ; en bas à droite = zone 9 = LECTURE).


---

## Étape 5 - Garder l'écran allumé

**Cette étape est essentielle.** Android économise la batterie en éteignant l'écran après quelques minutes - ce qui ruinerait le cadre photo. Vous devez désactiver cela.

**Option A - Paramètre dans l'application (recommandé) :**
Allez dans **Paramètres → Gestion → Empêcher la mise en veille** et activez-le.

Cela indique à Android de garder l'écran allumé tant que l'application s'exécute au premier plan. Dès que vous changez d'application ou que le diaporama s'arrête, le délai d'expiration d'écran normal revient.

![Settings, Management tab - Prevent sleep toggle enabled](screenshots/screenshot-pf-step5.png)

**Option B - Paramètre système Android :**
Paramètres Android → Affichage → Délai d'expiration de l'écran → réglez sur **"Jamais"** (ou au maximum).

> **Aussi :** Gardez la tablette **branchée sur l'alimentation USB** en permanence. Une tablette faisant tourner un diaporama toute la journée videra sa batterie d'ici le soir. Utilisez simplement le chargeur d'origine et laissez-le branché.

---

## Étape 6 - (Optionnel) Ajouter un widget sur l'écran d'accueil

Cette étape est pour la commodité : vous voulez lancer le cadre photo instantanément quand vous prenez la tablette - sans ouvrir l'application ni naviguer ?

1. Appuyez longuement sur votre écran d'accueil → appuyez sur **Widgets**
2. Trouvez **FastMediaSorter** dans la liste des widgets
3. Faites glisser le widget **"Raccourci de ressource"** sur votre écran d'accueil
4. Lorsque demandé, sélectionnez votre ressource photo
5. Appuyez sur le widget à tout moment → le diaporama se lance instantanément

![Android home screen with FastMediaSorter resource shortcut widgets placed](screenshots/screenshot-pf-step6.png)

---

## Terminé ! Votre cadre photo fonctionne

**Commandes pendant la lecture du diaporama :**
- **Appuyer sur l'écran** → pause / afficher les commandes
- **Balayer à gauche / droite** → passer manuellement à la photo suivante / précédente
- **Appuyer sur la zone en bas à droite** → arrêter le diaporama et revenir à la liste de fichiers

---

## Astuces

> **Les photos du NAS ne se mettent pas à jour après en avoir ajouté de nouvelles ?** L'application met en cache la liste des fichiers pour la rapidité. Pour rafraîchir : retournez au dossier → appuyez sur le bouton **Rafraîchir <img src="../icons/doc/ic_refresh.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** dans la barre d'outils. Les nouvelles photos apparaissent immédiatement.

> **Les photos semblent zoomées ou coupées ?** Ouvrez Paramètres → Média → Images → **"Recadrer les images pour remplir l'écran"** et essayez les deux positions : DÉSACTIVÉ garde la photo entière visible, ACTIVÉ remplit l'écran de bord à bord (léger recadrage sur les côtés).

> **Un téléphone en position verticale utilisé comme cadre ?** Activez "Recadrer les images pour remplir l'écran" pour éviter les bandes noires sur les photos en format paysage.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| L'écran s'éteint après quelques minutes | Activez "Empêcher la mise en veille" dans Paramètres → Gestion (étape 5) **et** branchez le chargeur USB |
| Les photos ne s'affichent pas | Ouvrez les paramètres du dossier (étape 2) et assurez-vous que **Images** est coché sous **Types pris en charge** |
| La musique ne joue pas | Vérifiez que le dossier de musique contient au moins un fichier audio ; vérifiez que **Jouer de la musique pendant le diaporama** est activé dans Paramètres → Média → Images, GIF et diaporama |
| Le diaporama se met en pause sur les fichiers vidéo | Attendu - les vidéos se lisent, puis le diaporama reprend. Réglez "Types pris en charge → Images uniquement" dans les paramètres du dossier (étape 2) pour éviter cela |
| Les photos SMB se chargent lentement | Modifiez le dossier → désactivez "Charger les miniatures" pour réduire la charge réseau. Ou réduisez l'intervalle du diaporama pour laisser plus de temps de chargement |
| Les photos se répètent trop vite | Augmentez l'intervalle du diaporama dans les paramètres du dossier (étape 2) |

</div>
