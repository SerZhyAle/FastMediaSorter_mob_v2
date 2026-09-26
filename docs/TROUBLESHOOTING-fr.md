---
layout: default
title: "🔧 Guide de dépannage"
permalink: /docs/TROUBLESHOOTING-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# 🔧 Guide de dépannage

Guide de dépannage actuel pour FastMediaSorter v2. Utilisez la grille de flavors canonique dans [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) lorsque le problème dépend du chemin de build sélectionné (Standard, Lite, Photos, Legacy, ou XR / noLegal).

{% include lang-switcher.html doc="TROUBLESHOOTING" dir="/docs/" current="fr" %}

---

## Problèmes de connexion

### ❌ « Impossible de se connecter au serveur SMB »

**Causes possibles :**
1. **Mauvais réseau** - le téléphone doit être sur le même Wi-Fi que le NAS
2. **Mauvais format d'adresse** - essayez les deux formats :
   - `\\192.168.1.100\share`
   - `smb://192.168.1.100/share`
3. **Pare-feu bloquant** - vérifiez les paramètres du pare-feu du NAS
4. **Incompatibilité de version SMB** - certains NAS exigent encore la compatibilité SMB v2/v3 ; mettez à jour le serveur s'il n'expose que des réglages SMB obsolètes

**Solution :**
- Testez d'abord la connexion depuis un PC
- Vérifiez les journaux du NAS pour les tentatives de connexion
- Essayez l'adresse IP plutôt que le nom d'hôte
- Vérifiez le nom d'utilisateur/mot de passe

---

### ❌ « Délai de connexion SFTP dépassé »

**Causes possibles :**
1. Mauvais port (par défaut : 22)
2. Le serveur SSH n'est pas en cours d'exécution
3. Pare-feu bloquant

**Solution :**
```
1. Testez d'abord avec un client SSH sur PC :
   ssh username@192.168.1.100
2. Vérifiez que le service SSH est en cours d'exécution
3. Vérifiez le port dans les Paramètres
```

---

### ❌ « Échec de connexion Google Drive »

**Solution :**
1. Effacez les données de l'application : Paramètres → Applications → FastMediaSorter → Effacer les données
2. Réinstallez l'application
3. Vérifiez les paramètres du compte Google → Sécurité → Applications tierces

---

### ❌ « Échec de connexion OneDrive »

**Solution :**
1. Vérifiez l'état du compte Microsoft
2. Effacez les données de l'application : Paramètres → Applications → FastMediaSorter → Effacer les données
3. Vérifiez les paramètres du compte Microsoft → Confidentialité → Applications et services

---

### ❌ « Échec de connexion Dropbox »

**Solution :**
1. Vérifiez l'état du compte Dropbox
2. Effacez les données de l'application : Paramètres → Applications → FastMediaSorter → Effacer les données
3. Vérifiez les paramètres du compte Dropbox → Sécurité → Applications connectées

---

## Problèmes de performance

### ❌ « L'application est lente / saccadée »

**Pour les grands dossiers (5000+ fichiers) :**
1. **Modifier le dossier** (par ressource) → activez **« Désactiver les vignettes »**
2. Utilisez des **filtres** pour réduire les fichiers visibles
3. Fermez les autres applications pour libérer de la RAM

**Pour les dossiers réseau :**
1. Vérifiez la force du signal Wi-Fi
2. Réduisez la taille du cache de vignettes
3. Activez **« Analyser les sous-dossiers »** = DÉSACTIVÉ si non nécessaire

---

### ❌ « Les vignettes ne se chargent pas »

**Fichiers locaux :**
- Vérifiez les permissions de stockage
- Videz le cache de vignettes
- Redémarrez l'application

**Fichiers réseau :**
- Faites défiler plus lentement (les vignettes se chargent à la demande)
- Vérifiez la vitesse du réseau
- Augmentez la taille du cache dans les Paramètres

---

## Erreurs d'opérations sur les fichiers

### ❌ « Échec de copie : permission refusée »

**Fichiers locaux :**
- Accordez les permissions de stockage : Paramètres → Applications → Permissions
- Vérifiez si le dossier est en lecture seule
- Essayez de déplacer vers un autre emplacement

**Fichiers réseau :**
- Vérifiez que l'utilisateur a les permissions d'écriture
- Vérifiez les paramètres de partage sur le NAS

---

### ❌ « Impossible de supprimer le fichier »

**Causes possibles :**
1. Le fichier est ouvert dans une autre application
2. Pas de permission d'écriture
3. Le fichier est protégé par le système

**Solution :**
- Fermez les autres applications
- Vérifiez les permissions du dossier
- Pour le réseau : vérifiez que l'utilisateur a les droits de suppression

---

### ❌ « Échec de l'opération de déplacement »

**Déplacements inter-protocoles** (par ex., Local → SMB) :
- Il s'agit en réalité de **copie + suppression**
- Nécessite de l'espace libre sur la cible
- Peut prendre plus de temps pour les gros fichiers

**Solution :**
- Vérifiez l'espace disponible
- Utilisez Copier plutôt que Déplacer par sécurité
- Attendez que l'opération se termine complètement

---

## Plantages de l'application

### ❌ « L'application plante à l'ouverture du lecteur »

**Causes courantes :**
1. Fichier vidéo corrompu
2. Codec non pris en charge
3. Fichier trop volumineux (>4 Go)

**Solution :**
- Essayez de lire le fichier dans une autre application pour vérifier
- Vérifiez le format du fichier (pris en charge : MP4, MKV, MOV)
- Videz le cache de l'application

---

### ❌ « Le fichier multimédia ne se lit pas ou pas de son »

**Problème :** la vidéo se charge mais affiche un écran noir, ou joue sans son.

**Solution :**
1. Touchez le bouton **ⓘ (Info)** dans la barre d'outils supérieure
2. Touchez **« Ouvrir dans un lecteur externe »**
3. Sélectionnez un lecteur spécialisé (par ex., VLC, MX Player)

Cela utilise la fonctionnalité *Lecteur secondaire* pour confier les codecs non pris en charge à d'autres applications.

---

### ❌ « L'application plante au démarrage »

**Solution :**
1. Videz le cache de l'application : Paramètres → Applications → FastMediaSorter → Vider le cache
2. Si le problème persiste : effacez les données de l'application (⚠️ perte des réglages)
3. Réinstallez l'application en dernier recours

---

## Problèmes d'interface / d'affichage

### ❌ « Les zones tactiles ne fonctionnent pas »

**Vérifiez si activé :**
Paramètres → Lecteur → **« Afficher l'astuce des zones tactiles au premier lancement »** = ACTIVÉ

**Rendre visible :**
Paramètres → Lecteur → **« Toujours afficher la superposition des zones tactiles »** = ACTIVÉ

---

### ❌ « Les boutons du panneau de commandes sont trop petits »

**Solution :**
Paramètres → Lecteur → **« Boutons du lecteur compacts »** = DÉSACTIVÉ

Cela double la taille de tous les boutons et de l'espacement.

---

### ❌ « Le thème sombre ne fonctionne pas »

L'application suit le **thème système** :
- Paramètres Android → Affichage → Thème sombre = ACTIVÉ

---

## Problèmes de données

### ❌ « Les favoris ont disparu »

Les favoris sont stockés **localement** :
- Données de l'application effacées ? → favoris perdus
- Nouvel appareil ? → il faut les remarquer

**Prévention :**
- Utilisez **Paramètres → Général → Sauvegardes, restauration et export des réglages**
- Les favoris sont locaux à l'appareil ; si vous changez de téléphone, remarquez-les ou utilisez le flux de sauvegarde/restauration de l'application disponible dans votre build

---

### ❌ « Le dossier corbeille ne cesse de grossir »

Les fichiers supprimés vont dans le dossier `.trash/` et y restent jusqu'à ce qu'ils soient vidés manuellement.

**Solution :**
1. Paramètres → Gestion → **Suppression de fichiers et corbeille**
2. Ou supprimez manuellement les dossiers `.trash/`

---

## Toujours des problèmes ?

### Vérifier les journaux
1. Paramètres → Gestion → **« Afficher les erreurs détaillées »** = ACTIVÉ
2. Reproduisez le problème
3. Vérifiez la sortie logcat

### Signaler un bug
Incluez ces informations :
- Version d'Android
- Modèle de l'appareil
- Étapes pour reproduire
- Message d'erreur (capture d'écran)

**Soumettre :** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

---
    
## Problèmes de traduction et d'EPUB
    
### ❌ « La traduction ne fonctionne pas ou reste bloquée »

**Causes possibles :**
1. **Modèles manquants :** l'application n'a pas réussi à télécharger les modèles OCR.
2. **Pas d'Internet :** le premier lancement nécessite Internet pour télécharger les modèles.
3. **Stockage plein :** pas d'espace pour les modèles (~50 Mo).

**Solution :**
1. Vérifiez la connexion Internet.
2. Allez dans **Paramètres** → **Médias** → **Autre**
3. Désactivez « Activer la traduction » puis réactivez-la.
4. Essayez de passer la **langue source** sur « Auto ».

---

### ❌ « Le livre EPUB ne s'ouvre pas »

**Causes possibles :**
1. **Protection DRM :** l'application ne prend en charge que les EPUB sans DRM.
2. **Fichier corrompu :** le fichier pourrait être incomplet.
3. **Fichier très volumineux :** les fichiers >100 Mo sur un réseau lent peuvent expirer.

**Solution :**
1. Vérifiez que le fichier s'ouvre dans d'autres liseuses.
2. S'il est sur le réseau/cloud, essayez d'abord de le télécharger manuellement.
3. Assurez-vous que l'extension du fichier est exactement `.epub`.

---

## Problèmes de flux Internet

### Le flux ne démarre pas / joue une seconde puis s'arrête

**Causes possibles :**
1. L'URL est morte ou redirige vers un protocole différent.
2. Le serveur exige une authentification (non prise en charge).
3. Le http:// non chiffré est bloqué par un VPN ou un réseau d'entreprise.

**Solution :**
- Touchez **Réessayer** dans la boîte de dialogue de flux indisponible pour réessayer.
- Vérifiez l'URL dans un navigateur.
- Désactivez temporairement le VPN pour tester.
- Si le flux redirige et échoue toujours, touchez **Supprimer** et rajoutez l'URL corrigée.

### L'indicateur d'import du catalogue ne s'arrête pas / reste bloqué

L'application applique un délai d'expiration rapide pour les téléchargements de catalogue. Si l'indicateur reste bloqué plus de ~15 secondes, l'hôte est probablement inaccessible. Vérifiez votre connexion Internet et réessayez. La boîte de dialogue se ferme automatiquement au délai d'expiration - elle ne restera pas bloquée indéfiniment.

### HLS / DASH / RTSP affiche le message « non pris en charge »

Dans **Standard**, **Legacy** et **XR / noLegal**, les trois protocoles sont pris en charge, donc ce message pointe vers le flux ou son codec, pas vers le build. **Lite** et **Photos** n'ont pas d'écran Flux du tout, donc aucun flux ne peut y être ajouté au départ.

### L'option Flux n'est pas visible dans le menu ou les paramètres

- Dans **Standard / Legacy / XR / noLegal** : allez dans **Paramètres > Médias > Flux** et assurez-vous que **Activer les Flux** est activé. L'élément du menu déroulant n'apparaît que lorsque les Flux sont activés.
- Dans **Photos** : la fonctionnalité Flux n'est pas intégrée à cette édition.
- Dans **Lite** : la fonctionnalité Flux n'est pas intégrée à cette édition non plus - il n'y a ni interrupteur pour l'activer ni écran à ouvrir.

### Les métadonnées ICY du morceau en cours ne s'affichent pas

Les métadonnées ICY nécessitent un flux Icecast/Shoutcast qui envoie l'en-tête `Icy-MetaData: 1`. Les flux mp3 http simples sans en-têtes ICY n'affichent aucune information de station/morceau dans le mini-contrôle en bas d'écran. Il s'agit d'une limitation côté serveur.

---

## Problèmes de contenu

### ❌ « Impossible de voir les fichiers texte ou PDF »

**Solution :**
1. Vérifiez **Paramètres** → **Médias** → **Documents**
2. Assurez-vous que **« Prise en charge des fichiers texte »** et **« Prise en charge des fichiers PDF »** sont activées.
3. Vérifiez les **Filtres** sur l'écran principal (icône entonnoir) pour vous assurer qu'ils sont sélectionnés.
4. **Réanalysez** le dossier (tirer pour actualiser).

---

## Limitations connues

- ⚠️ **Pas de prise en charge des photos RAW** (CR2, NEF, ARW)
- ⚠️ **Annulation réseau indisponible** (les fichiers sont supprimés définitivement)
- ⚠️ **Le stockage cloud est intégré à toutes les éditions sauf Lite** ; les fournisseurs proposés par un build donné peuvent encore dépendre de la plateforme de l'appareil, et [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) est la grille par édition
- ⚠️ **Pas de synchronisation multi-appareil** (les favoris sont locaux)

---

**Dernière mise à jour :** 2026-06-05  
**Version :** Ensemble actuel de la documentation publique

</div>
