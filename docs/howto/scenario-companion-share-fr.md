---
layout: default
title: "Ouvrir les dossiers de votre PC en scannant un seul code - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-fr.html
---
<div lang="fr" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Ouvrir les dossiers de votre PC en scannant un seul code

> **Niveau :** Débutant &bull; **Édition :** Standard, Photos, Legacy, VR, noLegal (Lite n'a pas de sources réseau ; le scan nécessite une caméra, la méthode par fichier fonctionne partout)

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="fr" %}

Vous exécutez un petit programme d'assistance sur votre PC Windows, vous choisissez les dossiers contenant vos vidéos, votre musique, vos documents ou vos photos, et il affiche un code à l'écran. Sur le téléphone, vous appuyez sur **Ajouter**, vous placez la caméra devant ce code, et les dossiers du PC sont instantanément connectés - sans saisir d'adresse, sans port, sans mot de passe, sans câbles.

> **Explication en langage simple :** L'assistant Windows transforme les dossiers que vous avez choisis en un partage privé, en lecture seule, sur votre Wi-Fi domestique, et imprime un code qui contient déjà tout ce dont le téléphone a besoin pour les atteindre. Scanner ce code revient à remplir un long formulaire de connexion à la main - le téléphone le lit simplement en un coup d'œil. Les fichiers s'ouvrent ensuite à la demande, diffusés via le Wi-Fi ; rien n'est copié sur le téléphone tant que vous ne le demandez pas.

---

## Le programme d'assistance

Le "compagnon" est une fonctionnalité intégrée de **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (anciennement FastMediaSorter LITE) - le trieur de médias Windows gratuit du même auteur. Lorsque vous partagez des dossiers avec lui, il :

- Démarre un serveur SFTP privé pour ces seuls dossiers sur votre PC.
- Génère ses propres clés et configure le démarrage automatique, afin que le partage soit là la prochaine fois aussi.
- Affiche un **code QR** à l'écran et peut aussi enregistrer un petit fichier de configuration `.fmscfg`.

**Où l'obtenir :**

- Site web : [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publication de dossiers (étape par étape) : [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub : [dernière version](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installateur ou ZIP portable)
- winget : `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store : cherchez "FastMediaSorter LITE" (encore répertorié sous l'ancien nom)

---

## Ce dont vous aurez besoin

- Un PC Windows avec **Fast Media Sorter for Windows** installé
- Votre téléphone et votre PC sur le **même réseau Wi-Fi** (même routeur)
- Pour le chemin le plus rapide : une **caméra** sur le téléphone pour scanner le code (un chemin par fichier est disponible si la caméra n'est pas disponible)

---

## Étape 1 - Partager les dossiers sur le PC

1. Installez et lancez **Fast Media Sorter for Windows**, puis ouvrez l'onglet **Partage** dans les paramètres.
2. Choisissez le(s) dossier(s) que vous voulez sur le téléphone - Films, Musique, Documents, Photos, n'importe quoi.
3. L'application démarre le serveur SFTP, génère les clés et configure le démarrage automatique toute seule. Rien d'autre à configurer.
4. Elle affiche maintenant un **code QR** sur l'écran du PC. Laissez cette fenêtre ouverte pour l'étape 2.

> Vous préférez un fichier plutôt qu'un code ? Utilisez **Enregistrer .fmscfg** dans la même fenêtre et envoyez ce fichier au téléphone (e-mail, Telegram, ou tout dossier partagé). Voir [Étape 2, méthode B](#step-2-method-b---import-the-file).

---

## Étape 2, méthode A - Scanner le code (le plus rapide)

1. Ouvrez FastMediaSorter et appuyez sur le bouton **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** sur l'écran principal.
2. Appuyez sur **"Importer par code-barres"** - il se trouve à côté des quatre cartes de type de ressource (Local, SMB, SFTP/FTP, Cloud) et dans l'en-tête du formulaire SFTP.
3. La caméra s'ouvre avec l'indication *"Pointez la caméra vers le code QR du compagnon"*. Placez le téléphone devant le QR sur votre PC. Dans une pièce sombre, appuyez sur **Torche**.
4. Une confirmation apparaît - *"Importer l'accès - Ajouter la ressource SFTP .. avec N dossier(s) ?"*. Appuyez sur **Importer**.
5. Terminé. Une ressource en lecture seule par dossier partagé apparaît sur l'écran principal, avec la clé du serveur épinglée automatiquement.

> L'entrée **Importer par code-barres** est masquée sur les appareils sans caméra et sur les casques VR - utilisez la méthode B dans ce cas.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Étape 2, méthode B - Importer le fichier {#step-2-method-b---import-the-file}

Utilisez ceci lorsque le téléphone n'a pas de caméra, ou lorsque le PC et le téléphone ne sont pas côte à côte.

1. Sur le PC, utilisez **Enregistrer .fmscfg** et faites parvenir le fichier au téléphone (e-mail, Telegram, cloud, ou un dossier partagé).
2. **Si le fichier est déjà sur le téléphone :** appuyez sur **Ajouter <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **"SFTP / FTP"** -> **"Importer depuis un fichier"**, puis choisissez le fichier `.fmscfg`.
3. **Si vous l'avez reçu en pièce jointe** (Telegram ou e-mail) : appuyez simplement sur la pièce jointe `.fmscfg` - l'application ouvre directement une boîte de dialogue de confirmation.
4. Confirmez la même boîte de dialogue *"Importer l'accès"* et appuyez sur **Importer**. Les ressources en lecture seule apparaissent.

> **Traitez le code et le fichier comme une clé.** Les deux intègrent le mot de passe d'accès pour que le téléphone puisse se connecter sans aucune saisie. Ne publiez pas la capture d'écran du QR ni le fichier `.fmscfg` publiquement.

---

## Terminé ! Vous pouvez maintenant..

Les dossiers partagés se comportent comme n'importe quelle autre ressource dans l'application. Par exemple :

- **Regarder des films et des séries** depuis le PC sur votre téléphone, tablette ou boîtier Android TV - diffusés en streaming, rien n'est copié. Voir [Cinéma maison et streaming VR](scenario-home-cinema-fr.md).
- **Lire votre bibliothèque musicale** en déplacement ou sur un autoradio.
- **Lire des PDF et des EPUB** stockés sur le PC, en conservant votre dernière position.
- **Parcourir une archive photo** et la trier avec le tri rapide, ou l'afficher comme [cadre photo numérique](scenario-photo-frame-fr.md).
- **Confier un fichier à une application spécialisée** - ouvrez la fiche d'information d'un fichier réseau et appuyez sur Télécharger et ouvrir.
- **Copier ou déplacer des fichiers** entre le PC et le téléphone dans les deux sens.

---

## Comment ça marche (sous le capot)

- L'assistant Windows exécute un **serveur SFTP** léger lié aux dossiers que vous avez choisis, sur votre réseau local uniquement.
- Le code QR (ou le fichier `.fmscfg`) encode la connexion : hôte, port, identifiants, les chemins des dossiers partagés, et l'empreinte de la clé hôte du serveur. Les partages denses sont envoyés compressés, afin que même de nombreux dossiers tiennent dans un seul code.
- Le téléphone lit cette charge utile, la vérifie, et crée une **ressource SFTP en lecture seule par dossier**. Le code porte aussi l'empreinte de la clé du serveur du PC, et le téléphone la vérifie à chaque connexion - navigation, copie, miniatures et lecture. Si un autre ordinateur répond un jour à la place de votre PC, le téléphone ne charge rien et vous indique que le serveur semble différent.
- Comme il s'agit de votre Wi-Fi local et d'un accès en lecture seule, le téléphone parcourt et diffuse les fichiers sans rien modifier sur le PC.
- **Sur le même Wi-Fi, le téléphone trouve le PC tout seul.** Le compagnon annonce le partage sur le réseau local, et le téléphone le reconnaît grâce à la clé épinglée - ainsi, même si l'adresse du PC sur le réseau change, le partage continue de fonctionner sans re-scanner.
- **Un seul import peut fonctionner à la maison et à l'extérieur.** Le code peut porter plusieurs adresses - l'adresse locale, une adresse IPv6, et une redirection de port internet. Le téléphone les essaie et utilise celle qui est joignable à l'instant : l'adresse locale à la maison, celle d'internet sur les données mobiles. La même ressource continue de fonctionner lorsque vous changez de réseau, tant que le PC est réellement joignable depuis l'endroit où vous êtes.
- **S'il ne peut pas se connecter, l'application explique quoi faire** - se mettre sur le même Wi-Fi, ou configurer l'accès sur le PC - plutôt qu'une simple erreur. Lorsque le compagnon inclut une note sur l'accès, le téléphone l'affiche.

---

## Dépannage

| Problème | Que faire |
|---------|------------|
| Pas d'entrée "Importer par code-barres" | L'appareil n'a pas de caméra, ou c'est une version VR. Utilisez la [méthode B - Importer le fichier](#step-2-method-b---import-the-file) |
| La caméra dit qu'un accès est nécessaire | Accordez l'autorisation de caméra lorsque demandé - elle n'est utilisée que pour le scan |
| "Ce fichier n'est pas une configuration de compagnon valide" | Le code ou le fichier ne provient pas du compagnon Windows. Réexportez-le depuis l'onglet **Partage** |
| "Créé par une version de compagnon plus récente" | Mettez à jour FastMediaSorter sur le téléphone, ou réexportez depuis une version de compagnon correspondante |
| Ressource ajoutée mais dossiers vides | Assurez-vous que l'assistant PC est toujours en cours d'exécution. Sur le **même Wi-Fi**, l'application trouve le PC toute seule ; si cela échoue encore, l'application indique quoi vérifier |
| Fonctionne en Wi-Fi mais pas en données mobiles | Pour atteindre le PC depuis un autre réseau, il doit être joignable depuis internet - configurez la redirection de port ou l'IPv6 dans les paramètres **Partage** du compagnon. Sans cela, le partage fonctionne uniquement sur le même Wi-Fi |

→ Plus d'aide : [TROUBLESHOOTING.md](../TROUBLESHOOTING-fr.md) &bull; Fondations : [Se connecter à un NAS / partage Windows (SMB)](scenario-smb-setup-fr.md)

</div>
