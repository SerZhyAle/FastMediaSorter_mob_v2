---
layout: default
title: "Guarda canali TV sul tuo smartwatch - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-tv-it.html
---
<div lang="it" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_stream.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Guarda canali TV sul tuo smartwatch

> **Livello:** Principiante &bull; **Durata:** ~10 minuti &bull; **Dispositivo:** Smartwatch Wear OS

> **Solo versione completa** - questa guida non è implementata nella versione distribuita tramite Google Play. Si applica alla versione completa, un download diretto dell'APK da [Download](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-tv" dir="/docs/howto/" current="it" %}

FastMedia Wear riproduce canali TV e radio in diretta direttamente al polso. L'orologio apre lo stream sulla propria Wi-Fi, quindi una volta che un canale è nell'elenco puoi guardarlo con il telefono in un'altra stanza, in una borsa, o completamente spento.

> **Cerchi invece la musica archiviata?** Vedi [Musica sullo smartwatch](scenario-watch-music-it.md). Per i tuoi file su una condivisione NAS o PC, vedi [Collega l'orologio alle condivisioni di rete](scenario-watch-network-it.md).

---

## Cosa ti serve

- Uno smartwatch con **Wear OS 2.0** o successivo con FastMedia Wear installato
- Una rete Wi-Fi a cui l'orologio può connettersi, oppure un telefono abbinato per inoltrare la connessione
- Facoltativo: FastMediaSorter sul tuo telefono Android, se vuoi inviare i tuoi canali all'orologio

---

## Passo 1 - Apri Stream

1. Apri **FastMedia Wear** sul tuo orologio.
2. Sulla schermata principale, tocca **Stream**.

![Schermata principale di FastMedia Wear con la sezione Stream](screenshots/screenshot-wear-tv-step1.png)

La schermata principale mantiene sempre le stesse sei sezioni nelle stesse posizioni, quindi Stream è sempre nella riga inferiore qualunque sia la dimensione della griglia scelta. Sopra di esse c'è una riga con le risorse aperte più di recente - una volta che hai guardato qualcosa, il canale che hai lasciato compare lì con un tocco.

---

## Passo 2 - Riempi l'elenco dei canali

Un'installazione appena fatta non ha ancora canali, e la schermata lo segnala.

![Schermata Stream vuota con il pulsante Aggiorna catalogo](screenshots/screenshot-wear-tv-step2.png)

Ci sono due modi per riempirlo, e funzionano insieme:

- **Scarica il catalogo condiviso.** Tocca **Aggiorna catalogo**. L'orologio scarica in un unico archivio la raccolta di canali pubblicata - molte migliaia di canali TV e radio con i loro argomenti, lingue e paesi.
- **Invia canali dal telefono.** Un canale che hai aggiunto tu stesso in FastMediaSorter sul telefono può essere trasferito con **Invia all'orologio** dall'elenco stream del telefono. Anche i canali che fissi sul telefono vengono portati verso l'alto nell'elenco dell'orologio, subito dietro quelli che hai fissato sull'orologio stesso, così i due o tre che guardi davvero sono raggiungibili senza scorrere. Rimuovere il fissaggio sul telefono ritira di nuovo il canale da quel gruppo, e un canale che il catalogo dell'orologio stesso non contiene viene semplicemente saltato.

I canali inviati dal telefono sopravvivono a un aggiornamento del catalogo - l'aggiornamento sostituisce la raccolta condivisa e lascia intatte le tue righe personali.

---

## Passo 3 - Trova il canale che vuoi

I tre pulsanti in cima all'elenco restano fissi mentre l'elenco scorre, così non escono mai fuori portata.

- **Cerca** filtra l'elenco mentre digiti.
- **Filtro** restringe per argomento e per lingua. I nomi sono mostrati nella tua lingua dell'interfaccia invece che nell'inglese grezzo del catalogo, i più popolati per primi, con il conteggio dei canali su ogni riga, e le tre lingue dell'app stessa in cima.
- **Filtro** elenca anche le raccolte curate incluse nel catalogo - "TV russa", "Radio dell'ex URSS", "TV africana" e le altre, le stesse che mostra il telefono. Scegline una per vedere solo i suoi canali, oppure scegli **Tutti** per rimuovere la restrizione. Un canale può appartenere a più raccolte, quindi la stessa stazione compare sotto più di una. Se il catalogo scaricato non contiene raccolte, la voce non viene mostrata affatto.
- **Ordina** offre Più usati, Nome A-Z, Nome Z-A e Per tipo di media. Più usati è l'opzione predefinita e cresce con i canali che avvii davvero sull'orologio, così l'elenco impara da solo le tue abitudini.

Sopra l'elenco, un piccolo contatore su due righe mostra quanti canali lasciano la ricerca e i filtri attuali, sul totale dell'intero catalogo.

![Elenco canali con il contatore e la barra degli strumenti fissa](screenshots/screenshot-wear-tv-step3.png)

In modalità griglia, un canale video mostra un'immagine di anteprima prima ancora che tu l'abbia mai aperto, presa da un set di anteprime scaricabile. Dopo la prima visione, l'anteprima viene sostituita da un fotogramma catturato dal canale stesso.

---

## Passo 4 - Guarda

1. Tocca un canale. Il player video si apre a schermo intero.
2. **Volume:** gira la ghiera rotante o la corona.
3. **Ricerca:** tieni premuto a lungo il pulsante precedente o successivo. Entrambi i pulsanti restano sullo schermo anche con un solo canale.
4. **Inquadratura:** il pulsante modalità inquadratura passa tra adattare l'intera immagine dentro il vetro rotondo e ritagliarla per riempire lo schermo. L'orologio ricorda la tua scelta - sopravvive all'uscita dal player e al riavvio dell'app, e la stessa scelta vale anche per i tuoi file video.
5. **Schermo spento:** il menu del player ha una voce **Schermo spento**. Il display diventa completamente nero - niente orologio, niente controlli - mentre il canale continua a essere riprodotto, e l'orologio non si addormenterà. Un singolo tocco segna solo il punto che hai toccato con un piccolo puntino bianco; un doppio tocco, una pressione prolungata o il pulsante fisico dell'orologio riportano indietro l'immagine e i controlli esattamente come li avevi lasciati.
6. **Fissa:** il segno sul player fissa il canale. I canali fissati vengono elencati per primi la volta successiva che apri Stream: quelli che hai fissato qui sull'orologio guidano, quelli fissati sul telefono li seguono, e tutto il resto mantiene l'ordine dato dall'ordinamento scelto. Il fissaggio è legato all'indirizzo del canale, quindi sopravvive a una nuova importazione del catalogo.

> **Il video ha bisogno dello schermo.** La riproduzione in background mantiene attivo solo l'**audio** dopo che lasci l'app - utile per i canali radio - ma video e presentazioni si fermano quando l'app lascia lo schermo. È deliberato: un video che non puoi vedere consuma solo batteria.

---

## Passo 5 - Torna indietro con un tocco

- La **riga recente della schermata principale** elenca l'ultimo canale che hai riprodotto accanto alle risorse di rete aperte di recente, con l'icona propria del canale. Toccandola riapri il player.
- Un **riquadro Stream** può essere aggiunto alla carosello dei riquadri di Wear OS e puntato su un canale direttamente dall'orologio. Da quel momento il canale è a uno swipe di distanza dal quadrante, senza bisogno di aprire prima l'app.
- La **complicazione ultima risorsa** mostra anche il canale, così può stare sul quadrante.

---

## Passo 6 - Quando il collegamento è debole

Gli stream in diretta sono la cosa più impegnativa che un orologio fa con la sua rete, quindi l'app è esplicita a riguardo:

- Mentre uno stream è in riproduzione, l'orologio chiede al sistema una rete a banda larga e la rilascia quando la riproduzione termina.
- Se il collegamento attuale non riesce a sostenere lo stream, l'orologio lo dice invece di fallire silenziosamente.
- Se uno stream si blocca senza errori - il modo tipico in cui muore un feed live - un watchdog lo riancora e lo ripreparara fino a tre volte, mostrando **Riconnessione**. Solo quando la rete resta morta passa al messaggio di canale non disponibile.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| "Nessuno stream disponibile" dopo un'installazione pulita | Tocca **Aggiorna catalogo**, oppure invia un canale dal telefono con **Invia all'orologio** |
| "Impossibile aggiornare gli stream" | Il catalogo è un download di diversi megabyte. Metti l'orologio sulla Wi-Fi invece che su un collegamento inoltrato dal telefono, e riprova |
| Un canale si apre e poi si ferma | La fonte stessa potrebbe essere offline. L'orologio riprova tre volte prima di arrendersi - prova un altro canale per distinguere uno stream morto da una rete morta |
| Il video si ferma quando abbassi il polso | Previsto: solo l'audio continua una volta che l'app lascia lo schermo. Per mantenere un canale in riproduzione con il display spento, resta nel player e usa la sua voce **Schermo spento** |
| Il canale che hai fissato sul telefono non è in cima | I fissaggi viaggiano quando il companion Wear è attivo nell'app sul telefono; controlla prima quello |
| Il suono è troppo basso | Gira la ghiera o la corona nel player - cambia il volume multimediale dell'orologio, non la posizione di riproduzione |

</div>
