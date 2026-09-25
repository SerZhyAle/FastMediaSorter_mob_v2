---
layout: default
title: "Installer FastMedia sur votre montre - FastMediaSorter v2"
permalink: /docs/howto/wear-install-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Installer FastMedia sur votre montre

> **Niveau :** Débutant &bull; **Durée :** ~5 minutes &bull; **Appareil :** Montre connectée Wear OS associée à un téléphone Android

> **Deux versions.** La version de Google Play est une petite première version : Calculatrice, Chronomètre, Mini-jeu, Paramètres et la tuile Programmes. La musique, les photos, les partages réseau et les fonctionnalités du téléphone ne sont disponibles que dans la version complète, un téléchargement direct de l'APK depuis [Téléchargements](../DOWNLOADS.md).

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="fr" %}

FastMedia Wear est la moitié montre de FastMediaSorter. Une fois qu'elle est à votre poignet, vous pouvez lire de la musique et voir des photos directement depuis la montre, atteindre les dossiers partagés par votre téléphone associé, et ouvrir des partages réseau auxquels la montre se connecte toute seule. Cette page vous guide pour l'installer et l'associer.

---

## Ce dont vous aurez besoin

- Une montre connectée fonctionnant sous **Wear OS 3.0** ou plus récent
- Un téléphone Android avec FastMediaSorter installé et la montre déjà associée dans les paramètres système
- Une connexion Wi-Fi ou mobile sur la montre, ou sur le téléphone auquel elle est associée, pour le téléchargement

---

## Étape 1 - Installer FastMedia Wear sur la montre

1. Sur la montre, ouvrez la liste des applications et appuyez sur **Play Store**.
2. Recherchez **FastMedia Wear**.
3. Appuyez sur **Installer** et attendez la fin du téléchargement. La montre affichera l'application dans sa liste une fois terminé.

> Les montres varient dans ce qu'elles vous permettent de taper. Si la recherche au poignet est peu pratique, ouvrez le Play Store sur votre téléphone, trouvez FastMedia Wear, et choisissez votre montre comme cible d'installation - la montre le télécharge toute seule.

### Pas de Play Store ? Installez un APK via ADB

Utilisez cette voie lorsque votre montre n'a pas accès au Play Store. Vous avez besoin d'un ordinateur avec le SDK Android Platform-Tools (`adb`) et d'un réseau Wi-Fi local partagé par l'ordinateur et la montre. Cela ne fonctionne pas via internet seul.

1. Téléchargez un APK depuis la page [Direct APK Release](../DOWNLOADS.md) :
   - `FastMediaSorter_wear_debug.apk` est la version de débogage pour les tests. Elle s'installe en tant que
     `com.sza.fastmediasorter.debug`.
   - `FastMediaSorter_wear_release.apk` est la version signée non-débogage. Elle s'installe en tant que
     `com.sza.fastmediasorter`.
   - Les deux versions ont des noms de package différents, donc elles peuvent rester installées côte à côte. N'essayez pas
     d'installer un fichier `.aab` du Play Store avec ADB.
2. Sur la montre, activez le mode développeur : **Paramètres** → **À propos de la montre** → appuyez sur **Numéro de build** sept
   fois. Dans **Options pour les développeurs**, activez **Débogage ADB** et **Débogage sans fil**.
3. Dans **Débogage sans fil**, choisissez **Associer un nouvel appareil**. Sur l'ordinateur, saisissez l'adresse d'association
   et le code affichés par la montre, puis connectez-vous avec le port de connexion séparé depuis l'écran principal de
   Débogage sans fil :

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Acceptez l'invite de débogage sur la montre. Les ports d'association et de connexion sont différents.
4. Installez ou mettez à jour l'APK. Utilisez la commande correspondant au fichier que vous avez téléchargé :

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` met à jour le même package tout en conservant ses données d'application. Cela ne convertit pas une version de
   débogage en version de production, car ce sont des applications séparées.
5. Ouvrez **FastMedia Wear** depuis la liste des applications de la montre. Si besoin, démarrez-la depuis ADB :

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> Cette méthode nécessite une montre Wear OS. Les Galaxy Watch 3, Galaxy Watch Active et Active 2 fonctionnent sous Tizen
> et ne peuvent pas installer d'APK Wear OS. Une fois terminé, désactivez le débogage sans fil sauf si vous en avez besoin pour
> une autre mise à jour.

---

## Étape 2 - Activer le compagnon Wear sur le téléphone

Le côté téléphone est désactivé jusqu'à ce que vous indiquiez posséder une montre.

1. Ouvrez FastMediaSorter sur le téléphone.
2. Allez dans **Paramètres** et ouvrez l'onglet **Gestion**.
3. Trouvez le groupe **Wear OS** et développez-le.
4. Activez la case à cocher **Compagnon Wear**.

La case active tout le compagnon : le bouton qui ouvre sa fenêtre apparaît juste en dessous, une entrée le concernant rejoint la liste des programmes, et il devient disponible comme tuile de panneau et raccourci de launcher.

> Les versions sans le pont vers la montre n'affichent pas du tout ce groupe. Si vous ne le trouvez pas, vous utilisez une édition qui ne prend pas en charge Wear.

---

## Étape 3 - Choisir ce qui va vers la montre

1. Dans le même groupe, appuyez sur **Compagnon Wear**. Sa fenêtre s'ouvre par-dessus l'application.
2. Choisissez les ressources que vous voulez que la montre voie. Rien n'est envoyé tant que vous n'avez pas choisi - une sélection vide n'envoie rien plutôt que de pousser toute votre bibliothèque.
3. Ajustez aussi ici les préférences propres à la montre : mode d'affichage, comportement de maintien éveillé et les sections affichées sur l'écran d'accueil de la montre.

---

## Étape 4 - Vérifier que les deux moitiés se voient

1. Ouvrez **FastMedia Wear** sur la montre.
2. L'écran d'accueil liste ses sections - **Téléphone**, **Local**, **Ressources**, **Flux** et **Applications**.
3. Appuyez sur **Téléphone**. Les dossiers que vous avez sélectionnés à l'étape 3 apparaissent.

Si la section Téléphone est vide, retournez à la fenêtre du compagnon sur le téléphone et confirmez qu'au moins une ressource est sélectionnée.

> **Astuce :** vous pouvez revenir en arrière depuis n'importe quel écran de votre montre en utilisant le bouton universel de retour visible sur le bord gauche, en balayant depuis le bord gauche, ou en appuyant sur le bouton retour matériel de votre montre. Sur l'écran d'accueil principal, appuyer sur le bouton de retour affiche une icône de sortie (une flèche quittant une boîte) pour quitter l'application ou un double chevron («) pour réduire la lecture en arrière-plan. Sur chaque écran qui affiche ce bouton, un bouton d'écran noir (un téléphone avec un écran sombre) lui fait face sur le bord droit et éteint l'écran de la montre ; un double appui, un appui maintenu, ou le bouton matériel le ramène.

---

## Si quelque chose ne fonctionne pas

- **L'application montre n'apparaît pas dans le Play Store.** Confirmez que la montre fonctionne sous Wear OS 3.0 ou plus récent. Les montres plus anciennes utilisent un modèle d'application différent et ne sont pas prises en charge.
- **Le groupe Wear OS est absent des paramètres du téléphone.** La version que vous utilisez ne comporte pas le pont vers la montre.
- **La section Téléphone sur la montre est vide.** Rien n'est sélectionné dans la fenêtre du compagnon, ou la montre et le téléphone ont perdu leur association - vérifiez d'abord l'association dans les paramètres système.
- **La lecture bégaie via la connexion au téléphone.** Le Bluetooth entre la montre et le téléphone est étroit. Pour une écoute longue, transférez les fichiers vers la montre ou connectez la montre directement à un partage réseau.

---

## Où aller ensuite

- [Musique sur la montre connectée](scenario-watch-music-fr.md) - lisez votre collection sur la montre, avec pochettes d'album, lecture aléatoire et volume par la lunette.
- [Connecter la montre aux partages réseau](scenario-watch-network-fr.md) - atteignez un NAS ou un partage PC depuis la montre via le Wi-Fi, sans le téléphone.

</div>
