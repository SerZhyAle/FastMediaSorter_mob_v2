---
layout: default
title: "Apri le cartelle del tuo PC scansionando un codice - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-it.html
---
<div lang="it" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Apri le cartelle del tuo PC scansionando un codice

> **Livello:** Principiante &bull; **Edizione:** Standard, Photos, Legacy, VR, noLegal (Lite non ha fonti di rete; la scansione richiede una fotocamera, il metodo via file funziona ovunque)

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="it" %}

Esegui un piccolo programma di supporto sul tuo PC Windows, scegli le cartelle con i tuoi video, musica, documenti o foto, e lui mostra un codice sullo schermo. Sul telefono tocchi **Aggiungi**, punti la fotocamera verso quel codice, e le cartelle del PC vengono collegate all'istante - senza digitare un indirizzo, senza porta, senza password, senza cavi.

> **Spiegazione in parole semplici:** L'helper di Windows trasforma le cartelle che hai scelto in una condivisione privata e di sola lettura sulla tua Wi-Fi di casa, e stampa un codice che già contiene tutto ciò di cui il telefono ha bisogno per raggiungerle. Scansionare quel codice equivale a compilare a mano un lungo modulo di connessione - il telefono lo legge semplicemente con un'occhiata. I file si aprono poi su richiesta, trasmessi via Wi-Fi; niente viene copiato sul telefono finché non lo chiedi.

---

## Il programma di supporto (companion)

Il "companion" è una funzione integrata di **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (in precedenza FastMediaSorter LITE) - il media sorter gratuito per Windows dello stesso autore. Quando condividi le cartelle con esso:

- Avvia un server SFTP privato solo per quelle cartelle sul tuo PC.
- Genera le proprie chiavi e configura l'avvio automatico, così la condivisione sarà disponibile anche la prossima volta.
- Mostra un **codice QR** sullo schermo e può anche salvare un piccolo file di configurazione `.fmscfg`.

**Dove trovarlo:**

- Sito web: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Pubblicare cartelle (passo dopo passo): [Come pubblicare le cartelle del PC su Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [ultima versione](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installer o ZIP portatile)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: cerca "FastMediaSorter LITE" (ancora elencato con il vecchio nome)

---

## Cosa ti serve

- Un PC Windows con **Fast Media Sorter for Windows** installato
- Il tuo telefono e il PC sulla **stessa rete Wi-Fi** (stesso router)
- Per il percorso più rapido: una **fotocamera** sul telefono per scansionare il codice (è disponibile un percorso basato su file se non c'è la fotocamera)

---

## Passo 1 - Condividi le cartelle sul PC

1. Installa ed esegui **Fast Media Sorter for Windows**, poi apri la scheda **Condividi** nelle impostazioni.
2. Scegli la cartella o le cartelle che vuoi sul telefono - Film, Musica, Documenti, Foto, qualsiasi cosa.
3. L'app avvia il server SFTP, genera le chiavi e configura l'avvio automatico da sola. Niente altro da configurare.
4. Ora mostra un **codice QR** sullo schermo del PC. Lascia quella finestra aperta per il Passo 2.

> Preferisci un file invece di un codice? Usa **Salva .fmscfg** nella stessa finestra e invia quel file al telefono (email, Telegram o una cartella condivisa). Vedi [Passo 2, Metodo B](#step-2-method-b---import-the-file).

---

## Passo 2, Metodo A - Scansiona il codice (il più veloce)

1. Apri FastMediaSorter e tocca il pulsante **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** sulla schermata principale.
2. Tocca **"Importa da codice a barre"** - si trova accanto alle quattro card dei tipi di risorsa (Locale, SMB, SFTP/FTP, Cloud) e nell'intestazione del modulo SFTP.
3. Si apre la fotocamera con il suggerimento *"Punta la fotocamera verso il codice QR del companion"*. Avvicina il telefono al codice QR sul tuo PC. In una stanza buia, tocca **Torcia**.
4. Compare una conferma - *"Importa accesso - Aggiungere la risorsa SFTP .. con N cartelle?"*. Tocca **Importa**.
5. Fatto. Sulla schermata principale compare una risorsa di sola lettura per ogni cartella condivisa, con la chiave del server fissata automaticamente.

> La voce **Importa da codice a barre** è nascosta sui dispositivi senza fotocamera e sui visori VR - usa il Metodo B in quei casi.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Passo 2, Metodo B - Importa il file {#step-2-method-b---import-the-file}

Usa questo metodo quando il telefono non ha fotocamera, o quando il PC e il telefono non sono uno accanto all'altro.

1. Sul PC, usa **Salva .fmscfg** e trasferisci il file sul telefono (email, Telegram, cloud o una cartella condivisa).
2. **Se il file è già sul telefono:** tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **"SFTP / FTP"** -> **"Importa da file"**, poi scegli il file `.fmscfg`.
3. **Se lo hai ricevuto come allegato** (Telegram o email): basta toccare l'allegato `.fmscfg` - l'app apre direttamente una finestra di conferma.
4. Conferma la stessa finestra *"Importa accesso"* e tocca **Importa**. Compaiono le risorse di sola lettura.

> **Tratta il codice e il file come una chiave.** Entrambi incorporano la password di accesso, così il telefono può connettersi senza digitare nulla. Non pubblicare pubblicamente lo screenshot del QR o il file `.fmscfg`.

---

## Fatto! Ora puoi..

Le cartelle condivise si comportano come qualsiasi altra risorsa nell'app. Ad esempio:

- **Guardare film e serie** dal PC sul telefono, tablet o box Android TV - in streaming, senza copiare nulla. Vedi [Home Cinema e streaming VR](scenario-home-cinema-it.md).
- **Riprodurre la tua libreria musicale** in movimento o su un'autoradio.
- **Leggere PDF ed EPUB** archiviati sul PC, mantenendo l'ultima posizione di lettura.
- **Sfogliare un archivio fotografico** e ordinarlo con l'ordinamento rapido, o mostrarlo come [cornice fotografica digitale](scenario-photo-frame-it.md).
- **Consegnare un file a un'app specializzata** - apri il foglio Info di un file di rete e tocca Scarica e apri.
- **Copiare o spostare file** tra il PC e il telefono in entrambe le direzioni.

---

## Come funziona (sotto il cofano)

- L'helper di Windows esegue un leggero **server SFTP** collegato alle cartelle che hai scelto, solo sulla tua rete locale.
- Il codice QR (o il file `.fmscfg`) codifica la connessione: host, porta, credenziale, i percorsi delle cartelle condivise e l'impronta della chiave del server. Le condivisioni dense vengono inviate compresse, così anche molte cartelle entrano in un unico codice.
- Il telefono legge quel contenuto, lo verifica e crea una **risorsa SFTP di sola lettura per ogni cartella**. Il codice porta anche l'impronta della chiave del server del PC, e il telefono la controlla a ogni connessione - durante la navigazione, la copia, le miniature e la riproduzione. Se un altro computer dovesse mai rispondere al posto del tuo PC, il telefono non carica nulla e ti avvisa che il server sembra diverso.
- Essendo sulla tua Wi-Fi locale e di sola lettura, il telefono sfoglia e trasmette i file senza modificare nulla sul PC.
- **Sulla stessa Wi-Fi, il telefono trova il PC da solo.** Il companion annuncia la condivisione sulla rete locale, e il telefono la riconosce tramite la chiave fissata - quindi anche se l'indirizzo del PC sulla rete cambia, la condivisione continua a funzionare senza dover riscansionare.
- **Un'unica importazione può funzionare sia a casa sia fuori.** Il codice può contenere più di un indirizzo - quello locale, uno IPv6 e un port-forward su internet. Il telefono li prova e usa quello raggiungibile in quel momento: l'indirizzo locale a casa, quello internet con i dati mobili. La stessa risorsa continua a funzionare mentre ti sposti tra le reti, finché il PC è effettivamente raggiungibile da dove ti trovi.
- **Se non riesce a connettersi, l'app spiega cosa fare** - collegarsi alla stessa Wi-Fi, o configurare l'accesso sul PC - invece di un semplice errore. Quando il companion include una nota sull'accesso, il telefono la mostra.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| Nessuna voce "Importa da codice a barre" | Il dispositivo non ha fotocamera, oppure è una build VR. Usa il [Metodo B - Importa il file](#step-2-method-b---import-the-file) |
| La fotocamera dice che serve un permesso | Concedi il permesso della fotocamera quando richiesto - viene usata solo per la scansione |
| "Questo file non è una configurazione companion valida" | Il codice o il file non provengono dal companion Windows. Riesportalo dalla scheda **Condividi** |
| "Creato da una versione più recente del companion" | Aggiorna FastMediaSorter sul telefono, oppure riesporta da una versione compatibile del companion |
| Risorsa aggiunta ma le cartelle sono vuote | Assicurati che l'helper del PC sia ancora in esecuzione. Sulla **stessa Wi-Fi** l'app trova il PC da sola; se continua a fallire, l'app mostra cosa controllare |
| Funziona su Wi-Fi ma non con i dati mobili | Per raggiungere il PC da un'altra rete, deve essere raggiungibile da internet - configura il port forwarding o l'IPv6 nelle impostazioni **Condividi** del companion. Senza questo, la condivisione funziona solo sulla stessa Wi-Fi |

→ Altro aiuto: [TROUBLESHOOTING.md](../TROUBLESHOOTING-it.md) &bull; Fondamenta: [Collegati a NAS / condivisione Windows (SMB)](scenario-smb-setup-it.md)

</div>
