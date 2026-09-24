---
layout: default
title: "❓ Domande frequenti (FAQ)"
permalink: /docs/FAQ-it.html
---
<div lang="it" dir="ltr" markdown="1">

# ❓ Domande frequenti (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="it" %}

---

## Domande generali

### Cos'è FastMediaSorter?
FastMediaSorter v2 è un guscio completo per un dispositivo Android: prende il controllo della schermata Home, riproduce i tuoi contenuti multimediali, apre stream in diretta, avvia le tue app, comunica con il tuo smartwatch, tiene d'occhio il dispositivo e gestisce ogni file che possiedi - in cartelle locali, su unità di rete (SMB/SFTP/FTP) e in archivi cloud (Google Drive, OneDrive, Dropbox).

### È gratuito?
Sì! FastMediaSorter v2 è completamente gratuito e open source.

### Quale versione di Android mi serve?
Standard, Lite e Photos richiedono Android 8.0 (API 26) o più recente. La versione **Legacy** supporta Android 6.0 (API 23) o più recente. **XR / noLegal** richiede inoltre un hardware per visori supportato e l'attuale percorso runtime per il sideload.

### Serve Internet?
**No** per i file locali. **Sì** per le unità di rete e l'archiviazione cloud.

### L'app ha dei widget?
Sì! FastMediaSorter v2 include una varietà di widget per la schermata Home - trovali tenendo premuto sulla schermata Home → Widget → FastMediaSorter. Includono scorciatoie alle risorse, avviatori di presentazioni e altro.

### L'app può sostituire la mia schermata Home?
Sì, nelle build **Standard** e **noLegal**. Attiva **Rendi questa app la schermata Home** in **Impostazioni → Generali** e scegli FastMediaSorter quando Android chiede quale schermata Home usare. Ottieni un desktop con scorciatoie alle tue cartelle, gadget come orologio e meteo, una griglia di app e una taskbar. Disattiva l'impostazione, oppure scegli **Esci dalla modalità launcher**, e Android ripristina la tua schermata Home precedente - il layout del tuo desktop viene mantenuto per la prossima volta. Vedi [HOW_TO-it](HOW_TO-it.md#how-to-use-the-app-as-your-home-screen) per la procedura completa.

### Come faccio a smettere di usare l'app come schermata Home?
Tre modi, a seconda di quale raggiungi prima:

- Apri il menu Start sul desktop, scegli **Esci dalla modalità launcher** e conferma.
- Disattiva di nuovo **Rendi questa app la schermata Home** in **Impostazioni → Generali**.
- Apri l'elenco delle app Home di Android in **Impostazioni → Generali → Impostazioni del launcher di sistema → Sistema → Cambia schermata Home** e scegli il launcher che vuoi.

Il layout del tuo desktop viene mantenuto in ogni caso, quindi riattivare la modalità lo riporta come lo avevi lasciato.

### Perché il mio tablet è tornato alla vecchia schermata Home dopo un riavvio?
Perché è il firmware di quel dispositivo a riportarlo indietro, non perché l'app abbia perso l'impostazione. Alcune autoradio economiche e box Android integrati ripristinano l'app Home su quella di fabbrica a ogni avvio, qualunque cosa tu abbia scelto - nessuna app può forzare diversamente. Scegli di nuovo FastMediaSorter come app Home dopo il riavvio, e se il tuo dispositivo offre **Sempre** invece di **Solo una volta**, scegli **Sempre**. Se continua a non restare impostato, quel dispositivo semplicemente non consente una schermata Home sostitutiva.

### Posso mettere le mie cartelle e playlist sul desktop?
Sì - è proprio a questo che serve il desktop. Tieni premuto su un quadrato vuoto e scegli **Aggiungi un elemento..**, poi scegli cosa vuoi: una delle tue cartelle, uno stream radio, un'app, una persona o un gadget come l'orologio o il meteo. La nuova cella finisce sul quadrato che hai premuto, e per una cartella scegli anche se si apre in modalità sfoglia, presentazione o riproduzione. Per spostare le cose in seguito, scegli **Modifica il desktop** dallo stesso menu a pressione prolungata. Vedi [HOW_TO-it](HOW_TO-it.md#how-to-use-the-app-as-your-home-screen) per la procedura completa.

---

## Operazioni sui file

### Dove vanno i file eliminati?
I file eliminati si spostano in una cartella `.trash/` nella stessa posizione (eliminazione reversibile). Non vengono eliminati definitivamente finché non:
- Tocchi **"Svuota cestino"** in Impostazioni → Gestione → Eliminazione dei file e cestino, OPPURE
- Elimini manualmente la cartella `.trash/`

### Posso annullare un'eliminazione/spostamento?
**Sì!** Tocca il pulsante **"Annulla"** (o la zona touch in basso a destra) entro pochi secondi dall'operazione.

> ⚠️ **Nota:** l'annullamento non è disponibile per le eliminazioni di file di rete (vengono eliminati definitivamente all'istante).

### Qual è la differenza tra Copia e Sposta?
- **Copia:** crea un duplicato, l'originale resta al suo posto
- **Sposta:** ricolloca il file, lo rimuove dalla posizione originale

### Cos'è la modalità Tutti i file?
La **modalità Tutti i file** ti permette di usare l'app come un file browser completo su tutte le directory. In questa modalità, l'app ignora i filtri multimediali standard e mostra tutti i file (inclusi ZIP, RAR, APK, EXE, PDF, ecc.). Puoi eseguire le normali operazioni sui file come copia, spostamento, rinomina, condivisione ed eliminazione. Per i file binari non supportati, si apre automaticamente una finestra a comparsa dal basso che permette di gestire il file o aprirlo con app esterne.

### Come trovo ed elimino i file duplicati?
Apri una cartella, tocca il menu overflow e scegli **Trova duplicati** per rivedere tu stesso le corrispondenze, oppure **Trova ed elimina duplicati** per rimuoverli subito. C'è anche **Elimina per dimensione..** per una rapida pulizia basata solo sulla dimensione dei file. L'opzione automatica salta la conferma, quindi usa prima **Trova duplicati** se vuoi ricontrollare prima che qualcosa venga eliminato. La corrispondenza è basata sul contenuto - dimensione, poi un hash rapido, poi un controllo SHA-256 completo - quindi anche le copie rinominate vengono trovate.

---

## Rete e cloud

### Come mi collego al mio NAS domestico (unità di rete)?
1. Tocca **"+"** → **Rete** → **SMB / Unità di rete**
2. **Opzione A - Automatica:** tocca **"Scansiona rete"** per scoprire automaticamente i dispositivi disponibili sulla tua rete
3. **Opzione B - Manuale:** inserisci l'indirizzo del server: `\\192.168.1.100\share` oppure `smb://192.168.1.100/share`
4. Inserisci nome utente e password
5. Tocca "Connetti"

**Problemi comuni e soluzioni:**

| Problema | Cosa provare |
|---------|------------|
| "Connessione rifiutata" | Apri il Firewall di Windows → consenti la **porta TCP 445** in entrata. Oppure disattiva temporaneamente il firewall per testare |
| "Password errata" | Prova a lasciare vuoto il nome utente (accesso guest). Se usi un account Microsoft, inserisci la tua **email completa** come nome utente |
| "Host non trovato" | Assicurati che telefono e PC siano sullo **stesso router Wi-Fi**. L'isolamento AP (un'impostazione di sicurezza del router) può bloccare il traffico da dispositivo a dispositivo - disattivalo nelle impostazioni del router |
| La scansione non trova nulla | Disattiva la VPN sul telefono. Attiva la **Rilevazione rete** in Windows (Pannello di controllo → Centro connessioni di rete e condivisione → Impostazioni di condivisione avanzate). Poi prova a inserire l'IP manualmente |
| Navigazione molto lenta | Modifica la risorsa → esegui **Test di velocità**. Se è sotto i 5 Mbps, passa il telefono alla banda Wi-Fi a 5 GHz. Disattiva le miniature video per le connessioni lente |
| Funziona su Wi-Fi ma non sui dati mobili | Previsto - SMB è un protocollo solo per rete locale, non può funzionare sui dati mobili |

→ Procedura completa: [Guida alla configurazione SMB](howto/scenario-smb-setup-it.md)

### Come mi collego a Google Drive?
1. Tocca **"+"** → **Cloud** → **Google Drive**
2. Tocca "Accedi con Google"
3. Concedi i permessi quando richiesto
4. Le tue cartelle di Drive appariranno

**Nota:** i file NON vengono scaricati automaticamente - vengono trasmessi in streaming su richiesta.

### Come mi collego a OneDrive?
1. Tocca **"+"** → **Cloud** → **OneDrive**
2. Tocca "Accedi con Microsoft"
3. Concedi i permessi quando richiesto
4. Le tue cartelle OneDrive appariranno

### Come mi collego a Dropbox?
1. Tocca **"+"** → **Cloud** → **Dropbox**
2. Tocca "Accedi con Dropbox"
3. Concedi i permessi quando richiesto
4. Le tue cartelle Dropbox appariranno

### Posso usare SFTP o FTP?
**Sì!** Seleziona **SFTP** o **FTP** quando aggiungi una cartella:
- **SFTP:** sicuro, richiede un server SSH (porta 22)
- **FTP:** meno sicuro, protocollo più vecchio (porta 21)

### Posso condividere le cartelle del PC con l'app?
**Sì** - Fast Media Sorter for Windows pubblica le cartelle del PC scelte tramite SFTP e mostra un codice QR / una configurazione `.fmscfg`. Sul telefono, usa **Importa da companion** oppure **Scansiona codice QR** nella schermata Aggiungi risorsa. Vedi la guida lato PC: [Come pubblicare le cartelle del PC su Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html). Disponibile in Standard, Photos, Legacy, XR/noLegal.

### Perché le miniature non si caricano per i file di rete?
Le miniature di rete si generano **su richiesta** per risparmiare banda. Scorri lentamente o attendi qualche secondo perché appaiano.

Se le miniature non si caricano affatto:
- Controlla che la connessione sia attiva: tocca la risorsa → se la cartella si apre, la connessione va bene
- Modifica la risorsa → assicurati che **"Carica miniature"** sia attivato
- Per connessioni molto lente: disattiva del tutto le miniature per evitare timeout (Modifica risorsa → disattiva miniature)

### La connessione continua a cadere / i file non si aprono a metà riproduzione
- Controlla che il Wi-Fi del telefono sia stabile (non stia passando tra banda 2,4 e 5 GHz)
- Alcuni router disconnettono le sessioni SMB inattive - modifica la risorsa → attiva **"Riconnetti in caso di errore"** se disponibile
- Per la riproduzione video via SMB: esegui il Test di velocità (Modifica risorsa → Test di velocità). Servono almeno 10 Mbps per video 1080p

---

## Ordinamento rapido e destinazioni

### Cosa sono le cartelle "Ordinamento rapido"?
Le cartelle di Ordinamento rapido sono cartelle di destinazione preconfigurate per un ordinamento rapido dei file. Puoi assegnare fino a 30 cartelle con pulsanti numerati.

### Come configuro l'Ordinamento rapido?
**Metodo 1:** Impostazioni → Gestione → Destinazioni di ordinamento rapido, poi tocca **"Aggiungi a Ordinamento rapido"**  
**Metodo 2:** modifica qualsiasi cartella → attiva "Contrassegna per Ordinamento rapido"

### Come uso l'Ordinamento rapido mentre visualizzo i file?
1. Apri una foto/video a schermo intero
2. Tocca un **pulsante numerato** (0-9) sul pannello dei comandi, OPPURE
3. Tocca l'**angolo in basso a sinistra** (zona COPIA) o il **centro in basso** (zona SPOSTA)

### Posso usare i tasti numerici invece di toccare?
Sì - collega una tastiera hardware, un gamepad o un telecomando TV e i tuoi pulsanti di Ordinamento rapido vengono numerati (0-9) automaticamente. Premi la cifra corrispondente per copiare o spostare il file in quella destinazione all'istante, esattamente come toccando il pulsante.

### I pulsanti di Ordinamento rapido non compaiono
Assicurati di aver aggiunto prima almeno una cartella di destinazione: Impostazioni → Gestione → Destinazioni di ordinamento rapido, poi **"Aggiungi a Ordinamento rapido"**. I pulsanti appaiono solo quando è configurata almeno una destinazione.

### Ho inviato accidentalmente un file nella cartella sbagliata
Tocca subito **Annulla** (in basso a destra sul pannello dei comandi) - disponibile per qualche secondo dopo ogni operazione. Se hai perso la finestra di tempo, vai nella cartella di destinazione e sposta il file indietro manualmente.

---

## Zone touch

### Cosa sono le "Zone touch"?
Le Zone touch sono aree invisibili sullo schermo che attivano azioni quando vengono toccate. Lo schermo è diviso in una griglia 3x3:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
└─────────┴─────────┴─────────┘
```

### Come vedo le Zone touch?
Impostazioni → Player → **"Mostra sempre la sovrapposizione delle zone touch"**

### Posso disattivare le Zone touch?
Sì, usa semplicemente i **pulsanti del pannello comandi**. Le Zone touch sono opzionali.

---

## Cattura schermo e voce

### Cos'è la striscia di gesti sul bordo sinistro?
È un menu di acquisizione rapida che apri con uno scorrimento diagonale dal bordo sinistro dello schermo. Attivalo in **Impostazioni → Gestione → Gesti sul bordo dello schermo → Overlay dei gesti**. Dal menu puoi fare uno screenshot, scattare una foto, ritagliare e condividere l'immagine corrente, aprire una scorciatoia a un'app o a un pannello, oppure avviare una registrazione schermo, video o voce - tutto senza uscire da ciò che stai guardando. Disponibile in Standard e XR/noLegal.

### Come registro rapidamente una nota vocale?
Tre modi: la voce **Registrazione vocale** nel menu overflow, il widget della schermata Home **Registratore rapido**, oppure l'azione **Avvia registrazione audio** del gesto sul bordo. Comunque tu la avvii, un controllo flottante **Stop** resta sullo schermo - anche sopra un'altra app - finché non lo tocchi per salvare.

---

## Input e controlli

### Supporta tastiere fisiche e gamepad?
**Sì!** Input completo da tastiera, mouse e gamepad è disponibile su tutte le schermate. Premi **F1** su qualsiasi schermata per vedere le associazioni tasti attive per quella superficie.

### Come rimappo i controlli / cambio le associazioni tasti?
Impostazioni → **Gestione** → **Controlli e associazioni tasti** - riassegna qualsiasi azione a un tasto, pulsante o input del gamepad diverso. L'app include 70 preset integrati; tocca **Ripristina** per ripristinarli. I conflitti vengono evidenziati automaticamente.

### Come scarico un file multimediale da un URL?
Condividi qualsiasi link `http(s)` con FastMediaSorter tramite il **foglio di condivisione** Android (da un browser, un'app di messaggistica o qualsiasi app). FastMediaSorter scaricherà il file e offrirà di salvarlo in una qualsiasi delle tue risorse configurate.

---

## Prestazioni e archiviazione

### Come trovo un file specifico per nome?
Usa il pannello **Filtro** in Sfoglia: tocca l'icona del filtro nella barra degli strumenti, digita qualsiasi parte del nome del file nel campo nome - l'elenco si aggiorna all'istante. Non serve una barra di ricerca separata; il filtro copre completamente questo scenario.

### Perché l'app è lenta con 5000+ file?
L'app usa la **paginazione** per caricare i file a lotti. Per raccolte molto grandi:
- Attiva "Disattiva miniature" per quella cartella
- Usa i filtri per restringere i risultati
- Ordina per Data (più recenti prima) - questo carica prima i file recenti ed evita di scansionare l'intera cartella in anticipo

### L'app si arresta in modo anomalo o si blocca
1. Forza la chiusura e riapri l'app
2. Se si arresta su una cartella specifica: quella cartella potrebbe contenere un file danneggiato - prova ad aprire i file uno per uno per identificarlo
3. Cancella la cache: Impostazioni → Generali → **"Cancella cache"** - questo risolve la maggior parte dei problemi di stabilità dopo gli aggiornamenti
4. Se gli arresti anomali persistono: segnala tramite GitHub Issues (link in fondo a questa pagina) - allega una descrizione di cosa stavi facendo quando si è verificato l'arresto anomalo

### Quanto spazio usa la cache delle miniature?
**Predefinito:** 2 GB (configurabile nelle Impostazioni)

### Come cancello la cache?
Impostazioni → Generali → **"Cancella cache"**

---

## Preferiti

### Come contrassegno i file come preferiti?
Tocca l'**icona a stella** mentre visualizzi un file.

### Dove posso vedere tutti i miei preferiti?
Menu principale → scheda **"Preferiti"**

---

## Sicurezza e privacy

### Posso proteggere le cartelle con una password?
**Sì!** Modifica cartella → imposta un **codice PIN** (4-6 cifre)

### I miei dati vengono raccolti?
**No.** FastMediaSorter NON raccoglie né invia alcun dato personale.

### Le scorciatoie ai contatti sul desktop del launcher richiedono l'accesso ai miei contatti?
**No.** Fissare una persona sul desktop del launcher non richiede alcun permesso sui contatti. Scegli la persona nel selettore di contatti nativo di Android, l'app legge quel singolo record una volta e lo conserva come istantanea nella cella - non può mai sfogliare la tua rubrica. Le chiamate usano il numero che hai scelto nel selettore, quindi la cella compone esattamente quel numero.

Esiste un gruppo di permessi **Contatti** opzionale, richiedibile su richiesta, in **Impostazioni → Generali → Permessi e accesso**. Negarlo non cambia nulla nel comportamento descritto sopra - le scorciatoie continuano a funzionare allo stesso modo, senza alcun permesso.

### L'app salva la posizione GPS nelle mie foto?
Solo se lo attivi tu. In **Impostazioni → Gestione → Fotografia**, attiva la cattura foto, poi attiva sotto **Geotag delle foto** - l'app chiede subito il permesso di localizzazione, non al momento dello scatto. La schermata Info file di una foto geotaggata mostra la data di scatto e il punto GPS dai dati EXIF della foto come link toccabile che apre la tua app di mappe o il browser.

### Posso vedere come uso l'app?
Sì - è opzionale e disattivato per impostazione predefinita: attiva **Raccolta statistiche** in **Impostazioni → Generali** per aprire una dashboard locale di utilizzo: file ordinati, spazio liberato, tempo di riproduzione e altro, suddivisi per tipo di media. Nulla viene inviato automaticamente; **Invia all'autore** o **Esporta** condividono un riepilogo solo se lo scegli tu.

---

## Traduzione automatica

### Come funziona la traduzione?
Due passaggi, entrambi sul dispositivo:
- **Tesseract** legge il testo dall'immagine, in ogni lingua supportata (inglese, russo, ucraino, bulgaro, bielorusso).
- **Google ML Kit** poi traduce ciò che è stato letto.

### Cosa fa la lingua di origine "Auto"?
"Auto" legge il testo con il modello inglese e poi determina la lingua di ciò che è stato letto ai fini della traduzione. Per il testo cirillico, scegli esplicitamente la lingua di origine (ad esempio **Russo** o **Ucraino**) - altrimenti le lettere vengono lette come i loro equivalenti visivi latini.

### Funziona offline?
**Sì.** Ti serve Internet solo una volta per scaricare il modello di testo per la tua lingua di origine e il modello di traduzione per la tua coppia di lingue.

### Perché la traduzione a volte è più lenta?
Il primo uso di una lingua carica il suo modello di testo, e le immagini grandi o dettagliate richiedono più tempo per essere lette. Le esecuzioni successive nella stessa lingua partono più velocemente.

### Cos'è la modalità di traduzione in stile lente?
La **modalità in stile lente** mostra le traduzioni come sovrapposizione sopra l'immagine originale, in modo simile a Google Lens. Questo ti permette di vedere il testo tradotto nel suo contesto e nella sua posizione originale. Puoi attivarla in **Impostazioni → Media → Altro** (interruttore "Overlay in stile lente").

La **modalità standard** mostra le traduzioni in una vista di testo separata sotto l'immagine.

---

## Musica di sottofondo per la presentazione

### Come aggiungo musica di sottofondo alle presentazioni?
1. Aggiungi una cartella contenente file audio come risorsa
2. Vai su **Impostazioni → Media → Immagini**
3. Attiva **"Riproduci musica durante la presentazione"**
4. Seleziona la tua risorsa musicale dal menu a tendina
5. Avvia qualsiasi presentazione - la musica partirà automaticamente!

### Posso usare musica da unità di rete o archiviazione cloud?
**Sì!** L'app gestisce automaticamente i file di rete scaricandoli nella cache prima della riproduzione. Funziona con SMB, SFTP, FTP, Google Drive, OneDrive e Dropbox.

### Come salto le tracce durante la presentazione?
Tocca il **nome della traccia** mostrato durante la presentazione per passare a un'altra traccia casuale dalla tua risorsa musicale.

### Funziona con tutte le versioni?
**Quasi.** La musica per la presentazione richiede il supporto audio:
- **Standard**, **Legacy**, **XR / noLegal** - supporto audio completo, inclusa la riproduzione che continua in background
- **Lite** - riproduce file audio locali e testi, ma non ha un servizio di riproduzione in background, quindi il suono si interrompe quando l'app lascia il primo piano
- **Photos** - nessun supporto audio, quindi non c'è musica per la presentazione

---

## Stream Internet

### FastMediaSorter riproduce la radio Internet?
Sì. La schermata **Streams** riproduce stream audio http/https (mp3/aac), radio Icecast/Shoutcast con metadati ICY del brano in riproduzione, VOD HLS (.m3u8) e DASH, e sorgenti RTSP. Disponibile in Standard, Legacy e XR / noLegal. Lite e Photos non hanno affatto la schermata Streams - la funzionalità è assente lì, non semplicemente limitata ad alcuni protocolli.

### Come apro la schermata Streams?
Tocca **Streams** nel menu a tendina della finestra principale (visibile quando Streams è attivo). Puoi raggiungerla anche tramite **Impostazioni > Media > Streams**, dove si trova l'interruttore principale.

### Come aggiungo una stazione radio?
Nella schermata Streams, tocca **⋮** in fondo alla barra degli strumenti, scegli **Aggiungi stream** e incolla l'URL della stazione. Tocca Salva. La stazione appare subito nell'elenco.

### Posso importare una playlist?
Sì - tocca **⋮ > Importa da URL** e inserisci un indirizzo `.m3u` remoto. Lo stesso menu contiene **Aggiorna catalogo FastMediaSorter** per l'elenco curato (con chip per argomento e lingua), disponibile anche da **Impostazioni > Estensioni** o dalla schermata di onboarding di Benvenuto.

### Uno stream non si riproduce - cosa faccio?
Se uno stream fallisce, appare una finestra con le opzioni **Riprova**, **Rimuovi** e **Annulla**. I reindirizzamenti 301 tra protocolli diversi vengono gestiti automaticamente. Se l'host è morto o molto lento, l'importazione del catalogo va in timeout rapidamente invece di bloccarsi.

### La radio continua a riprodursi quando lascio la schermata Streams?
Dipende da **Impostazioni > Player > Riproduzione audio in background**. Con l'audio in background ATTIVO, la riproduzione continua. Con esso DISATTIVATO, lasciare la schermata ferma lo stream e offre una scelta Stop / Continua a riprodurre - lo stesso comportamento del player audio locale.

### Posso vedere miniature live per gli stream?
Passa l'interruttore della barra degli strumenti Streams alla vista **Griglia** - ogni canale appare come un riquadro con l'ultimo fotogramma catturato, così puoi capire al volo cosa sta andando in onda. Il riquadro resta visibile anche dopo aver chiuso e riaperto l'app, e si aggiorna con una nuova cattura quando lo stream torna in diretta.

### Posso trasmettere uno stream sulla mia TV?
Sì, per gli stream video - tocca **Cast** nel player e scegli un Chromecast sulla stessa rete Wi-Fi. Gli stream RTSP non possono essere trasmessi; il pulsante appare solo per i formati che il ricevitore Chromecast supporta.

---

## Wear OS

### FastMediaSorter funziona su smartwatch Wear OS?
**Sì!** FastMediaSorter v2 include un'app companion per Wear OS, ed è cresciuta da un semplice visualizzatore di file locali a un vero secondo schermo per i tuoi contenuti multimediali.

### Cosa posso fare sullo smartwatch?
- **Sfoglia e riproduci** - le cartelle e i preferiti del tuo telefono associato, oppure l'archivio locale dello smartwatch, come una griglia di miniature con ricerca, filtro e ordinamento. Audio e video si riproducono con riproduzione casuale, volume tramite la ghiera e una modalità a schermo spento che mantiene l'audio attivo.
- **Sposta i file in entrambe le direzioni** - invia una foto, un video o una traccia dal telefono direttamente allo smartwatch (proprio dal foglio di condivisione), oppure copia un file dallo smartwatch a una cartella del telefono che scegli tu.
- **Registra una nota vocale al polso** - resta sullo smartwatch finché non la invii al telefono, così nulla va perso durante la registrazione.
- **Riproduci stream in diretta** - radio e video dal tuo catalogo Streams si riproducono direttamente dall'elenco canali dello smartwatch, con i tuoi preferiti fissati in cima.
- **Uno sguardo veloce senza aprire l'app** - aggiungi un tile di FastMediaSorter al pannello a scorrimento del tuo quadrante, oppure la sua complicazione a un quadrante compatibile.
- **Piccoli strumenti integrati** - una calcolatrice, un monitor di rete e il mini-gioco hanno ciascuno la propria schermata sullo smartwatch.

Le impostazioni che cambi sul telefono si sincronizzano con lo smartwatch e viceversa, così le configuri una sola volta.

**Nota:** lo smartwatch non apre mai risorse cloud da solo - non ha un proprio client cloud, e il telefono non gli trasmette le sue cartelle cloud; un file cloud raggiunge lo smartwatch solo quando lo apri sul telefono e scegli il tuo smartwatch in "Invia a..". Nella versione completa dell'app per lo smartwatch (APK diretto) lo smartwatch si connette invece da solo a condivisioni SMB, FTP e SFTP via Wi-Fi - le risorse di rete che gli invii dal telefono. La versione Google Play dell'app per lo smartwatch è una piccola prima release (calcolatrice, cronometro, mini-gioco e impostazioni) e non sfoglia ancora i contenuti multimediali.

---

## E-book EPUB

### Come attivo il supporto EPUB?
Impostazioni → Media → **Documenti** → **"Supporto e-book EPUB"**

**Nota:** riavvia l'app dopo l'attivazione perché le modifiche abbiano effetto.

### Come leggo un libro EPUB?
1. Aggiungi come risorsa una cartella contenente file .epub
2. Apri la cartella - vedrai i file EPUB con il badge "E"
3. Tocca qualsiasi file EPUB per aprirlo nel lettore

### Posso navigare tra i capitoli?
**Sì!** Usa:
- I **pulsanti Precedente/Successivo** in basso
- **Scorri a sinistra/destra** per cambiare capitolo
- Il **pulsante Indice** (icona 📋) per aprire l'indice

### Posso regolare la dimensione del carattere?
**Sì!** Durante la lettura, usa i **pulsanti -A/+A** in basso per diminuire/aumentare la dimensione del carattere (intervallo 6-144px). Le impostazioni sono salvate per ogni libro.

### Posso cercare del testo nell'EPUB?
**Sì!** Tocca il **pulsante Cerca** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> per aprire il pannello di ricerca. Digita la tua query e naviga tra i risultati con i pulsanti Prec/Succ.

### Funziona con i file di rete/cloud?
**Sì!** I file EPUB vengono scaricati automaticamente nella cache quando aperti da archiviazione SMB/SFTP/FTP/Cloud.

### Ricorda la mia posizione di lettura?
**Sì!** L'app salva l'ultimo capitolo che stavi leggendo. Quando riapri il libro, continua da dove avevi lasciato.

### E il tema chiaro/scuro?
Il lettore EPUB si adatta automaticamente al tema della tua app (Impostazioni → Generali → Tema colore).

---

## Operazioni pianificate

### Cosa sono le Operazioni pianificate?
Regole di automazione basate sul tempo che eseguono operazioni di Copia, Sposta o Elimina tra qualsiasi delle tue risorse (cartelle locali, NAS, cloud) secondo una pianificazione ricorrente - anche quando l'app è chiusa.

### Dove configuro le Operazioni pianificate?
Impostazioni → **Gestione** → **Operazioni pianificate per programma**. Tocca **"+"** per aggiungere una nuova regola.

### Verrà eseguita se la mia app è chiusa?
**Sì.** Le operazioni sono pianificate tramite **WorkManager** di Android, che le esegue in background indipendentemente dal fatto che l'app sia aperta.

### Perché un'operazione pianificata non è partita all'orario esatto?
Android può posticipare le attività di WorkManager di alcuni minuti per ottimizzare la batteria. Per una tempistica più affidabile, concedi all'app l'esenzione **Ottimizzazione batteria** (Impostazioni → Generali → Ottimizzazione batteria). L'intervallo minimo è di 15 minuti.

### L'operazione pianificata è stata eseguita ma ha copiato 0 file
Di solito è corretto - significa che tutti i file erano già presenti nella destinazione (l'operazione usa "salta esistenti" per impostazione predefinita). Per verificare: controlla il log dell'operazione e guarda il conteggio "saltati" rispetto a quello "copiati".

Se ti aspettavi che venissero copiati nuovi file ma non è successo:
- Assicurati che la **Sorgente** sia impostata sulla risorsa giusta (ad es. la risorsa virtuale "Foto fotocamera" - non un percorso manuale che potrebbe essere sbagliato)
- Controlla che la risorsa di destinazione (SMB / cloud) fosse raggiungibile all'orario pianificato - se il Wi-Fi era spento, l'esecuzione viene saltata e ritentata la volta successiva

### Posso vedere cosa è stato elaborato?
**Sì.** Tocca **"Visualizza log"** nella sezione Operazioni pianificate per vedere una cronologia con timestamp di ogni esecuzione, inclusi i risultati per singolo file.

---

## Blocco meteo

### Da dove arriva il meteo?
Il blocco meteo del desktop usa **Open-Meteo.com** - un servizio meteo gratuito e senza chiave API. Dati meteo forniti da Open-Meteo.com (CC-BY 4.0).

### L'app traccia la mia posizione?
**No.** Il luogo è quello che digiti tu stesso, e non viene richiesto alcun permesso di localizzazione. Il blocco si aggiorna circa ogni 20 minuti e mostra l'ultima lettura con una nota "Ultimo dato noto" quando non c'è connessione. Toccandolo si apre l'app meteo del dispositivo.

---
## Hai ancora domande?

Non hai trovato una risposta sopra, o qualcosa non funziona come descritto? **Contattaci** - ogni messaggio viene letto e la maggior parte dei problemi viene risolta.

- � **Guide How-To** (attività passo passo): [HOW_TO-it.md](HOW_TO-it.md)
- 🚀 **Guida rapida:** [QUICK_START-it.md](QUICK_START-it.md)
- 🔧 **Risoluzione dei problemi:** [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.md)
- �📧 **Email:** [sza@ukr.net](mailto:sza@ukr.net) - per qualsiasi cosa: aiuto per la configurazione, descrizioni di bug, richieste di funzionalità
- 🌐 **Pagina dell'autore:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Segnalazione bug:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferito per bug riproducibili; includi la versione di Android e cosa stavi facendo
- 📖 **Documentazione completa:** [Portale della documentazione](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vuoi una funzionalità che ancora non c'è?** Scrivici - molte funzionalità dell'app sono state aggiunte perché qualcuno le ha chieste. Se ha senso per il caso d'uso, viene realizzata.

</div>
