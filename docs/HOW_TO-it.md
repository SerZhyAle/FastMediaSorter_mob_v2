---
layout: default
title: "📖 Guide pratiche"
permalink: /docs/HOW_TO-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 📖 Guide pratiche

Istruzioni passo passo per le attività più comuni.

Questa guida è ora organizzata su due livelli:

- **Gruppi di scenari** per flussi di lavoro reali più ricchi e combinazioni di funzioni.
- **Riferimento rapido alle attività** per ricette dirette a singola funzione, più avanti.

{% include lang-switcher.html doc="HOW_TO" dir="/docs/" current="it" %}

---

## Nota: disponibilità delle funzioni per edizione {#note-feature-availability-by-flavor}

Alcune funzioni sono disponibili solo in edizioni specifiche. La tabella seguente deriva da [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), generata dalla build stessa; la superficie XR / noLegal è mantenuta volutamente come un'unica colonna perché dipende dall'hardware del visore e dalle regole di build per il sideload.

| Funzione | Standard | Lite | Photos | Legacy | XR / noLegal | FOSS |
|---------|----------|------|--------|--------|--------------|------|
| Cartelle di rete (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Archiviazione cloud (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ |
| Riproduzione audio e testi | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ |
| Riproduzione audio in background | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Stream Internet (radio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Visualizzatore documenti (PDF, testo) | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Lettore EPUB | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Traduzione e OCR | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Modifica immagini | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Modalità home screen (launcher) | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |

La modalità home screen è l'unica riga in cui l'ultima colonna combinata si divide: è disponibile nella build sideload **noLegal** ma non nella build VR/XR, dove è il visore stesso a fornire il proprio ambiente home.

Due righe per l'audio, perché sono due decisioni di build separate: **Lite riproduce i file audio locali**, testi inclusi, ma si interrompe quando l'app lascia il primo piano - non ha un servizio di riproduzione in background. Lite non ha nemmeno la schermata Stream Internet, quindi radio e HLS/DASH/RTSP non sono semplicemente limitati lì, sono assenti.

Se una funzione è contrassegnata con "✗", scegli la build **Standard** o **XR / noLegal** adatta al tuo hardware e al canale di distribuzione.

---

## Indice {#table-of-contents}

### Gruppi di scenari

#### Media domestici, TV e salotto

1. [Trasformare un NAS in uno scaffale multimediale del soggiorno](#turn-a-nas-into-a-living-room-media-shelf)
2. [Avviare una presentazione con musica di sottofondo per un display in una stanza](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Usare FMS su un box Android TV](#how-to-use-fms-on-android-tv-box)
4. [Cinema immersivo OpenXR VR](#openxr-vr-immersive-cinema)

#### Viaggi, lettura e flussi di lavoro sui documenti

5. [Preparare una cartella per il viaggio senza internet stabile](#prepare-a-folder-for-travel-without-stable-internet)
6. [Leggere documenti ed EPUB dal cloud in movimento](#read-cloud-documents-and-epubs-on-the-go)
7. [Tradurre cartelli, scansioni e screenshot con l'OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Passare file di rete ad app specialistiche](#hand-network-files-off-to-specialist-apps)
9. [Calcoli matematici e testuali rapidi](#quick-math-and-text-calculations)
10. [Note in Markdown e codice su cloud](#cloud-markdown-and-code-notes)

#### Flussi di lavoro per utenti avanzati e media misti

11. [Ordinare un archivio fotografico di famiglia con Quick Sort](#sort-a-family-photo-archive-with-quick-sort)
12. [Catturare lo schermo con i gesti dal bordo](#capture-the-screen-with-edge-gestures)
13. [Creare una presentazione con musica di sottofondo](#how-to-create-slideshow-with-background-music)
14. [Leggere e-book (EPUB)](#how-to-read-e-books-epub)
15. [Traduzione automatica](#auto-translation)
16. [Widget intelligenti per la Home](#home-screen-smart-widgets)

### Riferimento rapido alle attività

17. [Connettersi a un'unità di rete (SMB)](#how-to-connect-to-network-drive-smb)
18. [Connettersi a un server SFTP/FTP](#how-to-connect-to-sftpftp-server)
19. [Importare una condivisione Companion di Windows (scansiona un codice o importa un file)](#how-to-import-a-windows-companion-share)
20. [Connettersi all'archiviazione cloud](#how-to-connect-to-cloud-storage)
21. [Configurare le cartelle di Quick Sort](#how-to-set-up-quick-sort-folders)
22. [Usare le zone di tocco](#how-to-use-touch-zones)
23. [Modificare le foto](#how-to-edit-photos)
24. [Creare una presentazione](#how-to-create-slideshow)
25. [Proteggere una cartella con un PIN](#how-to-protect-folder-with-pin)
26. [Svuotare il cestino](#how-to-empty-trash)
27. [Backup delle impostazioni](#how-to-backup-settings)
28. [Visualizzare file di testo e PDF](#how-to-view-text-and-pdf-files)
29. [Aprire file di rete in app esterne](#how-to-open-network-files-in-external-apps)
30. [Visualizzare il testo dei brani](#how-to-view-song-lyrics)
31. [Registrare lo schermo](#how-to-record-your-screen)
32. [Registrare una nota vocale](#how-to-record-a-voice-note)
33. [Usare la fotocamera integrata](#how-to-use-the-in-app-camera)
34. [Trovare ed eliminare i file duplicati](#how-to-find-and-delete-duplicate-files)
35. [Visualizzare le statistiche di utilizzo](#how-to-view-your-usage-statistics)
36. [Usare una scheda SD o un'unità collegata](#how-to-use-an-sd-card-or-connected-drive)
37. [Riconnettere una cartella aggiunta tramite percorso diretto](#how-to-reconnect-a-folder-added-by-direct-path)
38. [Usare l'app come schermata Home](#how-to-use-the-app-as-your-home-screen)
39. [Scegliere dove salvare catture e download](#how-to-choose-where-captures-and-downloads-are-saved)
40. [Ricevere file condivisi da un'altra app](#how-to-receive-files-shared-from-another-app)
41. [Usare i programmi integrati](#how-to-use-the-built-in-programs)
42. [Chiedere al tuo assistente di trovare e aprire i contenuti multimediali](#how-to-ask-your-assistant-to-find-and-open-media)
43. [Crittografare un file con FileDO](#how-to-encrypt-a-file-with-filedo)

---

## Gruppi di scenari {#scenario-groups}

Queste sezioni sono volutamente più varie dei blocchi di riferimento principali più avanti. Ogni scenario combina un percorso rapido con il contesto, i compromessi e le situazioni in cui FastMediaSorter è particolarmente efficace.

> **⭐ In evidenza: porta le cartelle del tuo PC sul telefono con una sola scansione.** Avvia il companion gratuito [Fast Media Sorter per Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) sul PC, scegli le cartelle con i tuoi video, musica, documenti o foto, e l'app mostra un codice sullo schermo. Sul telefono, tocca **Aggiungi**, scegli **Importa tramite codice a barre**, punta la fotocamera sul codice - le cartelle del PC vengono collegate all'istante, senza digitare indirizzo, porta o password. Guida completa: [Aprire le cartelle del PC scansionando un codice](howto/scenario-companion-share-it.md) &bull; ricetta rapida: [Importare una condivisione Companion di Windows](#how-to-import-a-windows-companion-share).

> **⌚ Smartwatch Wear OS:** usi un orologio Wear OS? Consulta le nostre guide passo passo per [Ascoltare musica sul tuo orologio](howto/scenario-watch-music-it.md) e [Collegare lo smartwatch a NAS e condivisioni PC](howto/scenario-watch-network-it.md).

## Media domestici, TV e salotto {#home-media-tv-and-living-room-flows}

## Trasformare un NAS in uno scaffale multimediale del soggiorno {#turn-a-nas-into-a-living-room-media-shelf}

**Disponibile in:** Standard, Photos, Legacy, XR/noLegal

**Percorso rapido**

1. Aggiungi il tuo NAS come risorsa SMB.
2. Esegui **Scansiona rete** se non vuoi digitare manualmente l'IP.
3. Apri la risorsa da un box TV, un tablet o un telefono.
4. Inizia a sfogliare video, foto o documenti direttamente dal NAS.

**Approfondimento dello scenario**

- Mantieni un'unica risorsa SMB per l'intera libreria di famiglia e separa le sottocartelle per uso: Film, Foto di famiglia, Scansioni, Manuali.
- Esegui **Verifica connessione** una volta durante la configurazione, così la risorsa è stabile prima di affidartici dal divano.
- Se il NAS viene usato da un box TV, abbinalo a una tastiera Bluetooth o al telecomando della TV per una navigazione rapida.
- Se la navigazione sembra lenta, apri le impostazioni della risorsa ed esegui il test di velocità integrato prima di cambiare altro.

**Quando aiuta**

- Vuoi un'unica fonte multimediale centrale invece di copiare gli stessi file su più dispositivi.
- Vuoi che la stessa libreria funzioni per presentazioni, lettura di documenti e riproduzione.

**Da evitare**

- Non iniziare risolvendo problemi legati all'hostname. Usa prima un indirizzo IP, poi ottimizza in seguito.
- Non aspettarti che l'edizione Lite sfogli le condivisioni SMB: quella build non ha alcuna fonte di rete.

## Avviare una presentazione con musica di sottofondo per un display in una stanza {#run-a-slideshow-with-background-music-for-a-room-display}

**Disponibile in:** Standard, Lite, Legacy, XR / noLegal (Photos non ha supporto audio)

**Percorso rapido**

1. Aggiungi una fonte di immagini e una fonte musicale.
2. In **Impostazioni → Contenuti multimediali → Immagini**, attiva **Riproduci musica durante la presentazione**.
3. Scegli la risorsa musicale.
4. Apri una cartella di foto e premi **Play**.

**Approfondimento dello scenario**

- Usa una cartella di immagini locale o una condivisione NAS veloce per le transizioni più fluide.
- Mantieni una risorsa musicale separata per brani di sottofondo tranquilli, così l'audio della presentazione è prevedibile.
- Se la cartella contiene sia immagini che video, ricorda che la musica si mette automaticamente in pausa quando parte un video.

**Quando aiuta**

- Vuoi che un box TV, un tablet o un vecchio telefono funga da cornice digitale per una stanza.
- Vuoi un'unica configurazione in grado di far ruotare foto di famiglia, scatti di eventi o album di viaggio senza costruire manualmente una coda.

**Da evitare**

- Non usare una condivisione di rete molto lenta sia per le immagini che per la musica se la riproduzione fluida è importante.

## Cinema immersivo OpenXR VR {#openxr-vr-immersive-cinema}

**Disponibile in:** Standard, Lite, Legacy, `vr`, noLegal (3D a occhio singolo); `vr` e noLegal (immersione completa con visore - entrambe le build offrono la vista immersiva, che si apre quando l'app rileva un visore OpenXR e l'interruttore principale VR è attivo)

**Percorso rapido - attiva, configura, guarda in 3D**

1. **3D a occhio singolo (ogni edizione, nulla da attivare):** apri qualsiasi file SBS/OU/180°/360° - viene rilevato automaticamente e ritagliato su un occhio solo, così appare corretto su uno schermo piatto normale. Questo è controllato da **Impostazioni > Player > "Mostra contenuti 3D da un occhio solo"** (attivo per impostazione predefinita). Per forzare un formato specifico invece di affidarti al rilevamento automatico, apri la finestra di dialogo Controllo del player su una build `vr`/XR-noLegal e scegli una modalità dalla scheda 3D - **Rilevamento automatico**, **Side-by-Side (SBS)**, **Over-Under (OU)** o **Mono (disattivato)**; la scelta viene ricordata per quel file.
2. **Immersione completa su un Quest (build `vr` o XR/noLegal):** con il visore indossato, tocca il badge VR nel player mentre un file 3D è aperto, scegli **Apri in VR Cinema** dal menu con i puntini di un file in Sfoglia, oppure apri **Impostazioni > Contenuti multimediali** e tocca **Prova immersione** per un esempio. Ognuna delle tre opzioni apre una vista OpenXR per occhio di quel contenuto.
3. **Guardare:** all'interno della vista immersiva, una barra HUD porta i controlli - una barra di posizione che trascini con il raggio del controller per cercare (tempo trascorso e totale accanto), più i selettori che si applicano a questo file: traccia audio solo quando ce n'è più di una, sottotitoli solo se il file li ha, profondità stereo solo per contenuti stereo. **NASCONDI** ed **ESCI** si trovano alle estremità opposte della barra; nasconderla la rimuove completamente e premendo il grilletto ricompare senza attivare ciò che si trova sotto. Il thumbstick cerca a passi di 10 secondi; tieni premuto **grip** mentre lo spingi per passare tra i file - avanti e indietro percorrono l'intera lista della risorsa, non solo il file che hai aperto. Al primo ingresso in modalità immersiva dopo l'installazione, una legenda elenca ogni associazione dei tasti del controller; qualsiasi pressione la chiude, e il pulsante **AIUTO** sulla barra la fa ricomparire in qualsiasi momento.

**Approfondimento dello scenario**

- Il 3D a occhio singolo non richiede alcun visore - è il modo più semplice per rivedere vecchi filmati SBS/OU su un telefono o un tablet.
- L'immersione completa richiede un Quest o un altro visore OpenXR e una build che la offra - la build `vr` o la build sideload XR/noLegal (vedi la [Guida al sideload VR](VR_SIDELOAD.md)).
- Le foto e i video 360°/180° vengono resi come una sfera/emisfero intorno a te una volta dentro la vista immersiva; i file 2D piatti vengono semplicemente riprodotti piatti.

**Quando aiuta**

- Vuoi rivedere filmati archiviati in SBS/OU/360°/180° senza un'app VR dedicata separata.
- Hai un Quest e vuoi provare oggi l'immersione completa sui tuoi file, accettando che per ora la navigazione sia solo avanti/indietro.

**Da evitare**

- Non aspettarti che la build `vr` del Meta Horizon Store / Google Play entri già in modalità immersiva - quella parte è ancora in sviluppo.
- La ricerca, la selezione di traccia e sottotitoli e la profondità stereo si trovano sulla barra HUD nel visore. Le operazioni sui file no - torna al pannello piatto per copiare, spostare o eliminare.

## Ascoltare la radio Internet sull'autoradio o su un lettore audio {#play-internet-radio-on-a-car-head-unit-or-audio-player}

**Disponibile in:** Standard, Legacy, XR / noLegal - la schermata Stream è assente in Lite e Photos

**Percorso rapido**

1. Apri il menu a discesa della finestra principale e tocca **Stream**, oppure vai su **Impostazioni > Contenuti multimediali > Stream** e attiva l'interruttore se è spento.
2. Tocca **⋮** in fondo alla barra degli strumenti, scegli **Aggiungi stream**, e incolla l'URL di una stazione radio qualsiasi (http:// o https://, .m3u8, rtsp://).
3. Tocca la riga della stazione - l'audio parte nel mini-controllo fisso in basso. La lista resta scorrevole.
4. Per un catalogo più ampio, tocca **Importa** e inserisci un URL remoto `.m3u`, oppure scarica il catalogo curato di FastMediaSorter dalla schermata **Estensioni**.

**Approfondimento dello scenario**

- Il catalogo curato arriva con chip per argomento e lingua; filtra per genere o lingua tramite il pulsante filtro (un pallino lo indica quando è attivo). L'interruttore AND/OR permette di trovare stazioni che soddisfano tutti i criteri o anche solo uno.
- Il catalogo arriva anche raggruppato in collezioni nominate - "TV russa", "Radio dell'ex URSS", "TV africana" e altre. Compaiono come una striscia scorrevole di chip appena sotto la barra degli strumenti; toccane una per vedere solo i suoi canali, nell'ordine in cui li ha organizzati il curatore, e tocca **Tutti** per tornare indietro. Uno stesso canale può appartenere a più collezioni, quindi potresti incontrarlo sia sotto un paese sia sotto un continente. Una collezione è una condizione di filtro in più, non una schermata separata: la ricerca, l'ordinamento, i filtri per genere e lingua e i tuoi preferiti continuano a funzionare al suo interno. Se il catalogo scaricato non ha collezioni, la striscia semplicemente non c'è.
- Le due piccole icone a destra del campo di ricerca separano la radio dal video con un tocco: tocca l'icona audio o video per mostrare solo quel tipo, tocca di nuovo quella accesa per mostrare tutto.
- Fissa in alto le tue stazioni preferite con l'icona del perno - l'ordine è indipendente dai Preferiti globali.
- Passa l'interruttore della vista della barra degli strumenti su **Griglia** per vedere i canali come riquadri con l'ultimo fotogramma catturato - comodo per sfogliare a colpo d'occhio gli stream video. La tua scelta tra lista e griglia viene ricordata la volta successiva che apri Stream.
- Se uno stream è compatibile con il cast e il telefono è su Wi-Fi, tocca **Cast** nel player per inviarlo a un Chromecast sulla stessa rete. Gli stream RTSP non possono essere trasmessi via cast.
- I metadati "now playing" ICY (nome della stazione, brano corrente) compaiono nel mini-controllo in basso.
- Una stazione che hai aggiunto tu stesso può essere inviata al tuo orologio Wear OS: apri il menu **⋮** della riga e tocca **Invia all'orologio** (il comando compare quando l'opzione Wear Companion è attiva). La stazione trasferita resta sull'orologio anche dopo gli aggiornamenti del catalogo; se lo stesso indirizzo compare in seguito nel catalogo online, la voce del catalogo ne prende il posto.
- Gli stream video e RTSP si aprono nel player a schermo intero; premendo Indietro si torna alla lista Stream con la posizione di scorrimento conservata.
- Il comportamento della riproduzione audio in background segue **Impostazioni > Player > Riproduzione audio in background**: se disattivata, l'audio si interrompe quando lasci la schermata e l'app offre la scelta Interrompi / Continua a riprodurre.

**Quando aiuta**

- Autoradio Android, lettori audio e box multimediali dove vuoi la radio Internet senza un'app separata (TuneIn, RadioDroid, stream di rete VLC).
- Uso IPTV-lite: gli stream VOD HLS/DASH vengono riprodotti nel player a schermo intero.

**Da evitare**

- Non aspettarti la riproduzione HLS/DASH live offset (live-edge) - in questa versione è supportato solo l'HLS/DASH VOD.
- Non usare l'edizione Lite o Photos per Stream; nessuna delle due build ha una voce Stream, quindi lì nessun protocollo funziona.

## Viaggi, lettura e flussi di lavoro sui documenti {#travel-reading-and-document-workflows}

## Preparare una cartella per il viaggio senza internet stabile {#prepare-a-folder-for-travel-without-stable-internet}

**Disponibile in:** Standard, Lite, Photos, Legacy, XR / noLegal (la lettura di PDF ed EPUB richiede Standard, Legacy o XR / noLegal)

**Percorso rapido**

1. Crea o scegli un'unica cartella locale per il viaggio.
2. Copiaci dentro i contenuti multimediali, i PDF, gli EPUB o le note di cui hai bisogno prima di lasciare il Wi-Fi.
3. Apri quella cartella una volta in FastMediaSorter così le miniature e le ultime posizioni sono pronte.
4. Usa la cartella offline durante il viaggio.

**Approfondimento dello scenario**

- Tieni i contenuti di viaggio in un'unica cartella locale anche se gli originali vivono normalmente su NAS o cloud.
- Mescola i formati di proposito: carte d'imbarco in PDF, EPUB da leggere, screenshot e musica offline possono convivere fianco a fianco.
- Usa il pannello dei filtri se vuoi passare tra sole immagini, soli documenti o solo audio mentre sei offline.

**Quando aiuta**

- Voli, treni, hotel e zone rurali dove lo streaming cloud non è affidabile.
- Situazioni in cui vuoi un unico pacchetto offline invece di cercare tra più app.

**Da evitare**

- Non aspettare l'ultimo momento per verificare che i file si aprano davvero senza internet.

## Leggere documenti ed EPUB dal cloud in movimento {#read-cloud-documents-and-epubs-on-the-go}

**Disponibile in:** Standard, Legacy, XR / noLegal - Lite e Photos non possono leggere documenti o EPUB affatto; l'archiviazione cloud è inoltre assente in Lite

**Percorso rapido**

1. Aggiungi il tuo provider cloud in **Archiviazione cloud**.
2. Apri la cartella che contiene PDF o EPUB.
3. Tocca direttamente il file dalla risorsa cloud.
4. Continua a leggere dall'ultima posizione salvata più tardi.

**Approfondimento dello scenario**

- Usa questa funzione quando i tuoi documenti di lavoro vivono già in Google Drive, OneDrive o Dropbox e non vuoi un flusso di lettura separato.
- I PDF sono ideali per file a layout fisso come biglietti, manuali e contratti scansionati.
- L'EPUB è migliore per la lettura di testi lunghi, dove la dimensione del carattere regolabile e la navigazione tra i capitoli contano più della fedeltà del layout.

**Quando aiuta**

- Passi tra documenti di lavoro e letture personali senza uscire dall'app.
- Tieni i file di viaggio o dei clienti nel cloud ma vuoi comunque un'interfaccia orientata alla lettura.

**Da evitare**

- Non aspettarti la lettura cloud in Lite - quella build non ha né archiviazione cloud né supporto documenti. Photos e Legacy hanno archiviazione cloud, ma solo Legacy può aprire documenti.
- Non considerare i dati mobili lenti un'esperienza di lettura garantita per file molto grandi.

## Tradurre cartelli, scansioni e screenshot con l'OCR {#translate-signs-scans-and-screenshots-with-ocr}

**Disponibile in:** Standard, Legacy, XR / noLegal

**Percorso rapido**

1. Apri un'immagine, un PDF o un file di testo.
2. Mostra il pannello dei comandi.
3. Tocca **Traduci**.
4. Conferma il download del modello al primo utilizzo se richiesto.

**Approfondimento dello scenario**

- L'app legge il testo con Tesseract sul dispositivo e lo traduce con Google ML Kit.
- Per il materiale in cirillico, scegli esplicitamente la lingua di partenza (ad esempio russo o ucraino) - "Automatico" legge con il modello inglese.
- Screenshot, scontrini, menu e pagine scansionate funzionano particolarmente bene quando il testo sorgente è ragionevolmente nitido.

**Quando aiuta**

- Sei in viaggio, leggi manuali stranieri o decifri screenshot da chat e app.
- Ti serve la traduzione sul posto invece di dover prima copiare il testo in uno strumento separato.

**Da evitare**

- Non giudicare la qualità dell'OCR da una foto notturna sfocata o da una scansione ritagliata male.

## Passare file di rete ad app specialistiche {#hand-network-files-off-to-specialist-apps}

**Disponibile in:** Standard, Photos, Legacy, XR/noLegal

**Percorso rapido**

1. Apri un file da SMB, SFTP o FTP.
2. Tocca **ⓘ Info**.
3. Tocca **Scarica e apri**.
4. Scegli l'app specialistica dal selettore Android.

**Approfondimento dello scenario**

- Usa questa funzione quando FastMediaSorter è il miglior browser per l'archiviazione remota, ma un'altra app è il miglior editor o visualizzatore per quel tipo di file.
- I casi tipici di passaggio sono documenti da ufficio, PDF avanzati, video con codec pesanti e formati multimediali di nicchia.
- La copia scaricata resta in `Downloads`, così puoi riaprirla più tardi anche se la fonte remota va offline.

**Quando aiuta**

- Vuoi un unico hub per i file remoti senza rinunciare ai migliori strumenti specialistici della categoria.

**Da evitare**

- Non aspettarti ancora il passaggio dal cloud tramite esattamente questo flusso.

## Calcoli matematici e testuali rapidi {#quick-math-and-text-calculations}

**Disponibile in:** Standard, Legacy, XR / noLegal

**Percorso rapido**

1. Apri qualsiasi documento PDF, e-book EPUB, file di testo, o esegui la traduzione OCR su un'immagine.
2. Tieni premuto per selezionare un blocco di testo contenente numeri o equazioni matematiche.
3. Dal menu azioni testo mobile, tocca il pulsante **Calcolatrice**.
4. La calcolatrice valuta la formula matematica all'istante in un overlay a comparsa.

**Approfondimento dello scenario**

- Seleziona una riga di testo contenente numeri con simboli di operatore (come `(45 + 12) * 3`) in un PDF o in un risultato di traduzione OCR.
- Usa il menu delle funzioni della calcolatrice scientifica integrata per operazioni complesse (trigonometria, radici, potenze, logaritmi).
- La calcolatrice mantiene la cronologia dei calcoli tra le sessioni e supporta gli slot di memoria (M+/M-/MR/MC) per un rapido monitoraggio dei dati.

**Quando aiuta**

- Stai leggendo un manuale, una scansione screenshot o un documento e devi risolvere rapidamente formule o sommare valute/numeri senza passare a un'altra app calcolatrice.

**Da evitare**

- Non incollare stringhe alfabetiche pure; possono essere analizzati solo numeri validi, parentesi e operatori matematici.

## Note in Markdown e codice su cloud {#cloud-markdown-and-code-notes}

**Disponibile in:** Standard, Photos, Legacy, XR / noLegal (locale, di rete e cloud); Lite (solo cartelle locali)

**Percorso rapido**

1. Sfoglia una cartella locale qualsiasi, un NAS domestico (SMB), un server FTP/SFTP o un'unità cloud (Google Drive).
2. Tocca il pulsante **Nuova nota <img src="icons/doc/ic_create_text_file.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti della cartella.
3. Digita il tuo contenuto nell'editor. L'app evidenzia i tag Markdown e la sintassi del codice.
4. Tocca **Salva** (o lascia che si salvi automaticamente) per scrivere le modifiche direttamente sulla fonte remota.

**Approfondimento dello scenario**

- Mantieni un file diario `.md` su Google Drive o sul NAS domestico e modificalo da qualsiasi dispositivo con la modifica sul posto.
- Crea nuove note nelle risorse principali con risoluzione automatica dei conflitti di nome (ad esempio `Nota_1.txt`, `Nota_2.txt`).
- Visualizza i layout Markdown renderizzati in modalità di sola lettura, oppure esporta le note direttamente verso servizi esterni come Google Keep.

**Quando aiuta**

- Vuoi mantenere semplici note, frammenti di codice o liste di cose da fare direttamente sulle tue unità di rete/cloud centrali senza flussi di copia-incolla locali.

**Da evitare**

- Non aspettarti la creazione di note sull'archiviazione cloud in Lite - quella build non ha né archiviazione cloud né fonti di rete.

## Flussi di lavoro per utenti avanzati e media misti {#power-user-and-mixed-media-workflows}

## Ordinare un archivio fotografico di famiglia con Quick Sort {#sort-a-family-photo-archive-with-quick-sort}

**Disponibile in:** Standard, Lite, Photos, Legacy, XR / noLegal

**Percorso rapido**

1. Aggiungi le tue cartelle di destinazione a **Quick Sort**.
2. Apri la cartella sorgente con le foto di famiglia non ordinate.
3. Usa i pulsanti numerati o le zone di tocco mentre riguardi le immagini.
4. Invia subito gli scatti da tenere alle cartelle di destinazione.

**Approfondimento dello scenario**

- Crea le cartelle di destinazione per esito, non solo per data: `Migliori`, `Da stampare`, `Da inviare alla famiglia`, `Archivio`.
- Riguarda a schermo intero così puoi decidere rapidamente e spostare o copiare senza tornare alla lista dei file.
- Se più persone curano lo stesso archivio, mantieni uno schema di denominazione delle destinazioni coerente prima di una grande sessione di ordinamento.

**Quando aiuta**

- Hai un arretrato di compleanni, viaggi, eventi scolastici o importazioni da vecchi telefoni.
- Vuoi un flusso di selezione veloce invece di trascinare manualmente i file in un file manager.

**Da evitare**

- Non iniziare a ordinare prima che le destinazioni siano nominate chiaramente.
- Non usare subito Sposta se non sei ancora sicuro di quali cartelle debbano restare come archivio a lungo termine.

## Catturare lo schermo con i gesti dal bordo {#capture-the-screen-with-edge-gestures}

**Disponibile in:** Standard, XR/noLegal

**Percorso rapido**

1. Vai su **Impostazioni → Gestione → Gesti dal bordo dello schermo → Overlay gesti** e attivalo.
2. Mentre visualizzi un file qualsiasi, scorri dal bordo sinistro per aprire il menu di cattura.
3. Scegli un'azione - la striscia si chiude e l'azione viene eseguita.

**Cosa può fare la striscia**

- Scattare uno **screenshot** della schermata corrente - visualizzalo, modificalo, condividilo, inviarlo a un'altra app o eseguire la traduzione OCR su di esso, più un'opzione di cattura silenziosa.
- **Scattare una foto** con la fotocamera, poi inviarla, modificarla o eseguire la traduzione OCR senza uscire dall'app.
- Avviare una registrazione di **schermo**, **video** o **audio/voce** - vedi [Come registrare lo schermo](#how-to-record-your-screen) e [Come registrare una nota vocale](#how-to-record-a-voice-note).
- **Aprire un'app o un pannello** che usi spesso.
- **Ritagliare e condividere** una regione dell'immagine corrente.

**Cose utili da sapere**

- Mentre la striscia è attiva, uno scorrimento dal bordo sinistro apre il menu di cattura invece di cambiare pagina.
- La striscia è pensata per la cattura con una mano sola durante la navigazione - disattivala se fai affidamento sugli scorrimenti di pagina dal bordo sinistro.
- Android conferma la cattura o la registrazione ogni volta che usi questo gesto, anche per l'opzione di screenshot silenzioso - è una protezione di sistema, non qualcosa che l'app controlla.

**Quando aiuta**

- Vuoi uno screenshot, una foto rapida o una registrazione senza uscire dal file che stai visualizzando.

## Riferimento rapido alle attività {#core-task-reference}

## Come aggiungere o importare uno stream Internet {#how-to-add-or-import-an-internet-stream}

**Disponibile in:** Standard, Legacy, XR / noLegal (tutti i protocolli) - la schermata Stream è assente in Lite e Photos

**Aggiungere un singolo URL:**

1. Apri **Stream** dal menu a discesa della finestra principale.
2. Tocca il pulsante **⋮** in fondo alla barra degli strumenti, poi **Aggiungi stream**.
3. Incolla l'URL dello stream (radio http/https, .m3u8, rtsp://). Tocca **Salva**.
4. Tocca la riga per avviare la riproduzione.

**Importare una playlist .m3u remota:**

1. Nella schermata Stream, tocca **⋮ > Importa da URL**.
2. Inserisci l'indirizzo .m3u remoto. Tocca **Importa**.
3. Tutte le stazioni del file compaiono nella lista.

**Scaricare il catalogo curato di FastMediaSorter:**

1. Apri **Impostazioni > Estensioni** (oppure la riga Stream dell'onboarding di benvenuto).
2. Tocca **Scarica** accanto alla voce del catalogo Stream.
3. Dopo il download, le righe del catalogo compaiono in Stream con chip per argomento/lingua e sono ricercabili e ordinabili.

---

## Come connettersi a un'unità di rete (SMB) {#how-to-connect-to-network-drive-smb}

**Cosa serve:**

- NAS o PC Windows con cartella condivisa
- Entrambi i dispositivi sulla stessa rete Wi-Fi
- Nome utente e password per la condivisione

**Disponibile in:** edizioni Standard, Photos, Legacy, XR / noLegal

**Passaggi:**

1. **Tocca il pulsante "+"** nella schermata principale
2. Seleziona **"Cartella di rete (SMB)"**
3. Compila i dettagli:
   - **Rilevamento automatico (nuovo):**
     1. Tocca il pulsante **"Scansiona rete"**
     2. Attendi che i dispositivi compaiano nella lista
     3. Seleziona il tuo dispositivo dalla lista
     4. L'indirizzo IP verrà compilato automaticamente

   - **Inserimento manuale:**

     ```
     Server/Percorso: \\192.168.1.100\photos
     Nome utente: john
     Password: ****
     Nome visualizzato: Home NAS (opzionale)
     ```

4. Tocca **"Verifica connessione"** per verificare
5. Tocca **"Salva"**

**Formati dell'indirizzo del server:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- Con porta: `smb://192.168.1.100:445/share`

**Suggerimenti:**

- Usa l'indirizzo IP (non l'hostname) per l'affidabilità
- Abilita SMB v2/v3 sul NAS per la sicurezza
- Porta SMB predefinita: 445

**Risoluzione dei problemi:**
→ Vedi [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.md)

---

## Come connettersi a un server SFTP/FTP {#how-to-connect-to-sftpftp-server}

**Cosa serve:**

- Server con SSH (SFTP) o FTP abilitato
- Porta 22 (SFTP) o 21 (FTP) aperta
- Nome utente e password (o chiave per SFTP)

**Passaggi:**

1. **Tocca il pulsante "+"** nella schermata principale
2. Seleziona **"SFTP / FTP"**
3. Scegli il protocollo: **SFTP** o **FTP**
4. Compila i dettagli:

   ```
   Host: 192.168.1.100
   Porta: 22 (SFTP) / 21 (FTP)
   Nome utente: username
   Password: ****
   Percorso remoto: /home/user/photos (opzionale)
   ```

5. Tocca **"Connetti"**

**Avanzate:**

- **Autenticazione con chiave SSH:** attualmente non supportata (solo password)
- **Porta personalizzata:** cambia il numero di porta se il server ne usa una non predefinita

**Risoluzione dei problemi:**
→ Vedi [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.md)

---

## Come importare una condivisione Companion di Windows {#how-to-import-a-windows-companion-share}

**Cos'è:** il companion è una funzione di [Fast Media Sorter per Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (in precedenza FastMediaSorter LITE) - il sorter multimediale gratuito per Windows dello stesso autore. Condivide le cartelle scelte del PC tramite SFTP ed esporta una connessione già pronta - nessuna configurazione manuale del server, nessuna digitazione di host/porta/chiave sul telefono. La porti sul telefono **scansionando un codice QR** sullo schermo del PC, oppure **importando un file `.fmscfg`**.

**Disponibile in:** Standard, Photos, Legacy, XR/noLegal (la scansione del codice a barre richiede una fotocamera; il metodo con file funziona ovunque, VR incluso)

> Preferisci una versione guidata, ricca di screenshot? Vedi la guida allo scenario [Aprire le cartelle del PC scansionando un codice](howto/scenario-companion-share-it.md).

**Ottenere Fast Media Sorter per Windows:**

- Sito web: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Pubblicazione delle cartelle (guida): [Come pubblicare le cartelle del PC su Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [ultima versione](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installer o ZIP portatile)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: cerca "FastMediaSorter LITE" (ancora elencato con il nome precedente)

**Sul PC:**

1. Installa e avvia **Fast Media Sorter per Windows**, apri la scheda **Condividi** nelle impostazioni.
2. Scegli la o le cartelle da condividere - l'app avvia da sola il server SFTP, genera le chiavi e configura l'avvio automatico.
3. Mostra un **codice QR** sullo schermo. Può anche **Salvare .fmscfg** se preferisci un file.

**Sul telefono - Metodo A, scansiona il codice (il più veloce):**

1. **Tocca il pulsante "+"** nella schermata principale.
2. Tocca **"Importa tramite codice a barre"** - si trova accanto alle quattro schede dei tipi di risorsa e nell'intestazione del modulo SFTP.
3. Punta la fotocamera sul QR del PC (tocca **Torcia** in una stanza buia), poi conferma la finestra di dialogo **Importa accesso**.
4. Fatto - compare una risorsa in sola lettura per ogni cartella condivisa, con la chiave del server fissata automaticamente.

**Sul telefono - Metodo B, importa il file:**

1. Sul PC, usa **Salva .fmscfg** e trasferisci il file sul telefono (email, Telegram o una posizione condivisa).
2. **Tocca "+"** -> **"SFTP / FTP"** -> **"Importa da file"** e scegli il file `.fmscfg`. Se è arrivato come allegato Telegram/email, tocca semplicemente l'allegato.
3. Conferma la finestra di dialogo **Importa accesso** - compaiono le risorse in sola lettura.

**Nota:** sia il codice QR sia il file di configurazione incorporano la password di accesso - trattali come una chiave, non pubblicare lo screenshot né il file. La voce **Importa tramite codice a barre** è nascosta sui dispositivi senza fotocamera e sui visori VR; usa il Metodo B in quel caso.

---

## Come connettersi all'archiviazione cloud {#how-to-connect-to-cloud-storage}

**Provider supportati:**

- Google Drive
- OneDrive
- Dropbox

**Passaggi:**

1. **Tocca il pulsante "+"** nella schermata principale
2. Seleziona **"Archiviazione cloud"**
3. Seleziona il provider: **Google Drive**, **OneDrive** o **Dropbox**
4. Tocca il pulsante **"Accedi.."**
5. Segui il flusso di autenticazione del browser/app
6. Concedi le autorizzazioni richieste
7. **Seleziona le cartelle** da sincronizzare
8. Tocca **"Fatto"**

**Note:**

- I file vengono **trasmessi in streaming**, non scaricati
- Richiede connessione internet
- Le modifiche si sincronizzano automaticamente
- Puoi disconnetterti in qualsiasi momento: Modifica cartella → Rimuovi

**Privacy:**

- Nessuna password memorizzata (usa token OAuth)
- I token possono essere revocati nelle impostazioni di sicurezza del tuo provider cloud

---

## Verificare la velocità della rete {#check-network-speed}

**Supportato per:** SMB, SFTP, FTP, Cloud (Google Drive)

**Verifica automatica:**
Quando aggiungi una nuova risorsa di rete, l'app esegue automaticamente un test di velocità in background. I risultati (velocità di lettura/scrittura) vengono salvati nelle impostazioni della risorsa.

**Verifica manuale:**

1. Vai su **Gestisci risorse**
2. Modifica una risorsa di rete (icona matita)
3. Scorri fino in fondo
4. Tocca il pulsante **"Velocità"**
5. Attendi circa 15 secondi per "Analisi velocità in corso.."
6. Guarda i risultati:
   - **Velocità di lettura (Mbps)**
   - **Velocità di scrittura (Mbps)**
   - **Thread consigliati** (per prestazioni ottimali)

---

## Come configurare le cartelle di Quick Sort {#how-to-set-up-quick-sort-folders}

**Metodo 1: dalle Impostazioni**

1. **Impostazioni** → scheda **Gestione** → **Destinazioni Quick Sort**
2. Tocca **"Aggiungi a Quick Sort"**
3. Seleziona una cartella esistente dalla lista
4. Alla cartella viene assegnato un numero (0-9) e un colore
5. Ripeti per un massimo di 30 cartelle

**Metodo 2: dalle Impostazioni della cartella**

1. Schermata principale → **Tieni premuta la cartella**
2. Tocca **"Modifica"** (icona matita)
3. Attiva **"Contrassegna per Quick Sort"**
4. Tocca **"Salva"**

**Usare Quick Sort:**

Durante la visualizzazione dei file:

- Tocca il **pulsante numerato** (0-9) sul pannello dei comandi
- OPPURE tocca l'**angolo in basso a sinistra** (zona COPIA)
- OPPURE tocca l'**angolo in basso al centro** (zona SPOSTA)

Il file viene copiato/spostato istantaneamente in quella cartella!

**Con una tastiera o il telecomando TV:** collegane uno e i pulsanti di destinazione ricevono un badge numerico - premi il tasto numerico corrispondente per lanciare all'istante quella destinazione, senza dover toccare.

---

## Come usare le zone di tocco {#how-to-use-touch-zones}

**Cosa sono le zone di tocco?**

Lo schermo è diviso in 9 aree invisibili per azioni rapide:

```
┌─────────┬─────────┬─────────┐
│ INDIETRO│  COPIA  │RINOMINA │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREC.  │ SPOSTA  │  SUCC.  │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMANDI │ ELIMINA │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legenda:**

1. **INDIETRO** - Torna alla lista dei file
2. **COPIA** - Copia il file nella destinazione
3. **RINOMINA** - Rinomina il file corrente
4. **PREC.** - Vai al file precedente
5. **SPOSTA** - Sposta il file nella destinazione
6. **SUCC.** - Vai al file successivo
7. **COMANDI** - Apri il menu comandi
8. **ELIMINA** - Elimina il file corrente
9. **PLAY** - Avvia/Ferma la presentazione

**Attivare l'overlay (consigliato per i principianti):**

1. Impostazioni → Player
2. Attiva **"Mostra sempre l'overlay delle zone di tocco"**
3. Ora vedrai una griglia semitrasparente

**Prova:**

1. Apri una foto qualsiasi
2. **Tocca l'angolo in alto a destra** → File successivo
3. **Tocca l'angolo in alto a sinistra** → File precedente
4. **Tocca l'angolo centrale a destra** → Elimina file
5. **Tocca l'angolo centrale a sinistra** → Copia file

**Disattivare se non necessario:**
Impostazioni → Player → "Mostra sempre l'overlay delle zone di tocco" = OFF

Poi usa i **pulsanti del pannello comandi** al loro posto.

---

## Come modificare le foto {#how-to-edit-photos}

**Operazioni supportate:**

- Ruota (90°, 180°, 270°)
- Capovolgi (orizzontale, verticale)
- Filtri (Scala di grigi, Seppia, Negativo)
- Regola (Luminosità, Contrasto, Saturazione)

**Passaggi:**

1. **Apri una foto** nel visualizzatore a schermo intero
2. Tocca il pulsante **"Modifica"** (o la zona di tocco centrale a sinistra)
3. **Scegli l'operazione:**
   - Ruota: tocca l'icona di rotazione
   - Capovolgi: tocca l'icona di capovolgimento
   - Filtro: seleziona dalla lista
   - Regola: usa i cursori
4. Tocca **"Salva"**

**Note:**

- Il file originale viene **sovrascritto** (nessun annulla!)
- Funziona per **file locali e di rete**
- Supporta: JPG, PNG, WEBP

---

## Come creare una presentazione {#how-to-create-slideshow}

**Passaggi:**

1. **Apri una cartella qualsiasi** con foto
2. Tocca la **prima foto** per aprire il visualizzatore
3. Tocca il pulsante **"Play"** (o la zona di tocco in basso a destra)
4. La presentazione parte automaticamente

**Personalizzare la velocità:**

1. **Modifica le impostazioni della cartella:**
   - Schermata principale → Tieni premuta la cartella → Modifica
2. Cambia **"Intervallo presentazione":**
   - Veloce: 2 secondi
   - Normale: 5 secondi
   - Lento: 10 secondi
3. Tocca **"Salva"**

**Controlli durante la presentazione:**

- **Tocca lo schermo** → Pausa/Riprendi
- **Scorri a sinistra/destra** → Salta i file
- **Tocca "Stop"** → Esci dalla presentazione

---

## Come creare una presentazione con musica di sottofondo {#how-to-create-slideshow-with-background-music}

**Requisiti:**

- Almeno una cartella/risorsa con file audio (MP3, FLAC, ecc.)
- **Disponibile in:** Standard, Lite, Legacy, XR / noLegal (Photos non ha supporto audio)

**Configurazione:**

1. **Impostazioni** → scheda **Contenuti multimediali** → **Immagini**
2. Attiva **"Riproduci musica durante la presentazione"**
3. Tocca il pulsante **"Seleziona fonte musicale"**
4. Scegli una risorsa che contiene i tuoi file musicali
5. Tocca **"Salva"** o chiudi le impostazioni

**Riprodurre la presentazione con musica:**

1. **Apri una cartella qualsiasi** con foto/immagini
2. Tocca la **prima foto** per aprire il visualizzatore
3. Tocca il pulsante **"Play"** (o la zona di tocco in basso a destra)
4. La presentazione parte con la musica di sottofondo in riproduzione

**Come funziona:**

- La musica viene riprodotta casualmente dalla risorsa musicale selezionata
- Quando un brano termina, parte automaticamente il successivo brano casuale
- La musica continua durante le transizioni tra le immagini
- La musica si interrompe quando esci dalla presentazione o metti in pausa

**Note:**

- La musica viene riprodotta solo per **immagini e GIF** (non per video/audio)
- Quando la presentazione mostra un video, la musica si mette automaticamente in pausa
- La musica riprende quando torni alle immagini
- Funziona con fonti musicali locali e di rete (SMB, SFTP, FTP)

**Personalizzare la selezione musicale:**

- Aggiungi più file musicali alla cartella della tua risorsa musicale
- L'app mescolerà casualmente tutti i file audio
- Organizza la musica in sottocartelle se la risorsa musicale ha "Includi sottocartelle" attivo

**Risoluzione dei problemi:**

- Se non parte alcuna musica: verifica che la risorsa musicale contenga almeno un file audio
- Se la musica si blocca sulla rete: usa una cartella locale o una connessione di rete più veloce
- Per la musica via SMB: assicurati che la risorsa SMB usi il protocollo `file://` (vedi TROUBLESHOOTING.md)

---

## Come proteggere una cartella con un PIN {#how-to-protect-folder-with-pin}

**Passaggi:**

1. Schermata principale → **Tieni premuta la cartella**
2. Tocca **"Modifica"** (icona matita)
3. Scorri fino al campo **"Codice PIN"**
4. Inserisci un **PIN di 4-6 cifre** (ad es. 1234)
5. Tocca **"Salva"**

**Ora:**

- Aprire questa cartella richiede il PIN
- Impedisce l'accesso non autorizzato
- Si applica alla navigazione e alla modifica

**Rimuovere il PIN:**

- Modifica cartella → Svuota il campo PIN → Salva

**PIN dimenticato?**

- Nessuna opzione di recupero (per progetto, per motivi di sicurezza)
- Dovrai rimuovere e riaggiungere la cartella

---

## Come crittografare un file con FileDO {#how-to-encrypt-a-file-with-filedo}

Un contenitore FileDO è un unico file con estensione `.fd-sec` che racchiude un altro file protetto da una password. Il formato è lo stesso usato dall'app desktop FileDO, quindi un contenitore creato qui si apre in FileDO e viceversa.

**Attivare i comandi:** **Impostazioni** → scheda **Gestione** → **Operazioni di crittografia FileDO**. Aprire un contenitore funziona sia che questo interruttore sia attivo sia che sia disattivo.

**Crittografare un file:**

1. In Sfoglia, apri il menu **⋮** del file.
2. Tocca **Crittografa con FileDO**.
3. Inserisci la password due volte e conferma.
4. Il contenitore compare accanto al file come `<nome>.fd-sec`. Il file originale resta intatto - eliminalo tu stesso se non ti serve più.

**Decrittografare un file:** apri il menu **⋮** del file `.fd-sec`, tocca **Decrittografa con FileDO** e inserisci la password. Il file ripristinato compare accanto al contenitore.

**Aprire un contenitore senza ripristinarlo:** tocca il file `.fd-sec` in qualsiasi cartella che mostra tutti i tipi di file. L'app chiede solo la password e apre il file al suo interno nel visualizzatore. La copia decrittografata resta nell'archiviazione privata dell'app e viene eliminata quando torni alla lista. Seleziona **Ricorda la password e provala su ogni file .fd-sec** per saltare la richiesta la prossima volta.

**Dove funziona:** cartelle del dispositivo, cartelle scelte tramite il selettore di sistema, e condivisioni SMB, SFTP e FTP. Su una cartella scelta col selettore o su una condivisione di rete il file viene elaborato come copia privata, il risultato viene scritto con un nome temporaneo, riletto e verificato, e solo allora rinominato al suo posto - un file esistente non viene mai sovrascritto.

**Se non si apre:** il messaggio indica tre possibili cause - una password sbagliata, un file che non è mai stato un contenitore, o un contenitore che è stato modificato. Non è possibile distinguerle. Un contenitore che racchiude un programma o uno script non viene aperto.

**Password dimenticata?** Non c'è modo di recuperarla. Una password vuota nasconde il file solo a uno sguardo superficiale.

---

## Come lavorare con le cartelle (seleziona, copia, sposta) {#how-to-work-with-folders-select-copy-move}

Quando le sottocartelle vengono mostrate come elementi separati nella lista, una riga di cartella si comporta come una riga di file.

**Attivare le righe delle cartelle:** **Impostazioni** → **Generali** → **Mostra sottocartelle separatamente**. Lo stesso interruttore esiste per singola risorsa nell'editor della risorsa.

**Passaggi:**

1. Tocca la casella di controllo su una riga di cartella, oppure tienila premuta, per selezionare una singola cartella. Un tocco breve apre comunque la cartella.
2. Usa il menu **⋮** della riga, o la barra delle azioni di selezione, per scegliere **Copia**, **Sposta**, **Rinomina** o **Elimina**.
3. Scegli la destinazione. I file e le cartelle nella stessa selezione viaggiano insieme in un'unica operazione.
4. La destinazione riceve l'intera struttura - ogni sottocartella e file all'interno della cartella sorgente.

**Tra tipi di risorsa diversi:** una cartella può essere copiata o spostata tra il dispositivo, SMB, SFTP, FTP e le risorse cloud - la struttura viene ricreata sul lato ricevente.

**Cosa viene rifiutato, e perché:** una destinazione all'interno della cartella stessa, o la posizione attuale della cartella, viene rifiutata prima che qualcosa venga copiato; una destinazione scelta tramite il selettore di sistema che non ha un percorso file reale non può ricevere cartelle. Il messaggio indica il motivo così puoi scegliere un'altra destinazione.

**Annullamento:** un trasferimento di cartelle mostra l'avanzamento e può essere interrotto. Ciò che è già stato scritto resta a destinazione - controlla la cartella prima di ricominciare. Uno spostamento elimina ogni elemento sorgente solo dopo che la sua copia è riuscita, quindi nulla va perso nel frattempo.

**Mandarlo in background:** non devi restare a guardare la finestra di avanzamento. Chiudila e il trasferimento continua a funzionare, restando visibile in Sfoglia come una striscia lungo il fondo che mostra l'operazione, la percentuale e il file su cui si trova in questo momento. Tocca quella striscia per far tornare la finestra di avanzamento completa, con la possibilità di annullare inclusa.

---

## Come svuotare il cestino {#how-to-empty-trash}

I file eliminati vanno nelle cartelle `.trash/` e vi restano finché non vengono svuotate manualmente.

**Metodo 1: svuota tutto il cestino**

1. **Impostazioni** → scheda **Gestione** → **Eliminazione file e cestino**
2. Tocca **"Svuota cestino"**
3. Conferma l'eliminazione
4. Tutte le cartelle `.trash/` su tutte le risorse vengono svuotate

**Metodo 2: per singola cartella**

1. Usa un'app di gestione file
2. Naviga fino alla cartella (ad es. `/storage/emulated/0/DCIM/Camera`)
3. Trova la sottocartella `.trash/`
4. Elimina manualmente

**Attenzione:** questa è un'**eliminazione permanente**! I file non possono essere recuperati.

---

## Backup delle impostazioni {#how-to-backup-settings}

**Esportare le impostazioni:**

1. **Impostazioni** → scheda **Generali** → **Backup, ripristino ed esportazione impostazioni**
2. Tocca **"Esporta tutte le impostazioni su file"**
4. Scegli la posizione (ad es. Downloads)
5. Il file viene salvato come `fastmediasorter_backup.xml`

**Ripristinare le impostazioni:**

1. **Impostazioni** → scheda **Generali** → **Backup, ripristino ed esportazione impostazioni**
2. Tocca **"Importa impostazioni da file"**
4. Seleziona il file di backup
5. Tocca **"Ripristina"**
6. L'app si riavvia con le impostazioni ripristinate

**Cosa è incluso:**
✅ Cartelle di Quick Sort
✅ Preferenze di visualizzazione
✅ Intervalli presentazione
✅ Credenziali di rete (crittografate)
✅ Preferiti
✅ Impostazioni Modalità sicura

**NON incluso:**
❌ Cache delle miniature
❌ Contenuto del cestino

---

## Come visualizzare file di testo e PDF {#how-to-view-text-and-pdf-files}

**1. Attivare il supporto:**

1. **Impostazioni** → scheda **Contenuti multimediali** → **Documenti**
2. Attiva **"Supporta file di testo (.txt, .md, .log, .json, .xml)"** e **"Supporta documenti PDF"**
3. **Riesegui la scansione** delle tue cartelle per trovare i nuovi file.

**2. Filtrare per tipo di contenuto:**

1. Tocca l'**icona filtro** (imbuto) nella schermata principale (in alto a destra).
2. Usa le caselle di controllo per selezionare i tipi di contenuto:
   - Immagini
   - Video
   - Audio
   - GIF
   - **Testo** (nuovo)
   - **PDF** (nuovo)
3. Tocca **"Applica"** per vedere solo i file selezionati.

**3. Visualizzatore di testo:**

- Tocca un file **.txt, .md, .log, .json, .xml** qualsiasi.
- **Scorri** per leggere.
- **Copiare il testo:** tieni premuto per selezionare e copiare.

**4. Visualizzatore PDF (nuove funzioni):**

- Tocca un file **.pdf** qualsiasi.
- **Barra di controllo di navigazione (in basso):**
  - **Precedente/Successivo:** pulsanti grandi ai bordi.
  - **Zoom avanti (+):** ingrandisce la pagina.
  - **Zoom indietro (-):** rimpicciolisce la pagina.
- **Gesti:**
  - **Scorri verso l'ALTO:** vai alla pagina successiva.
  - **Scorri verso il BASSO:** vai alla pagina precedente.
  - **Pizzica:** zoom avanti/indietro in modo naturale.
  - **Doppio tocco:** ripristina lo zoom.
  - **Lo zoom viene mantenuto:** la pagina successiva si apre con lo zoom e la posizione con cui stavi leggendo; il doppio tocco riporta la pagina intera.
- **Panoramica:** trascina per spostarti quando sei ingrandito.
- **Selezionare il testo tenendo premuto (Android 15+):** premi e tieni premuta una parola per selezionarla direttamente dal livello di testo della pagina - nessun passaggio OCR, nessuna attesa. Se la stessa parola compare più volte nella pagina, viene selezionata quella sotto il dito, non la prima. Trascina le maniglie per estendere la selezione, poi copia o traduci.

---

## Come leggere e-book (EPUB) {#how-to-read-e-books-epub}

**Requisiti:**

- **Impostazioni** → scheda **Contenuti multimediali** → **Documenti** → **Supporta e-book EPUB** deve essere attivo (attivo per impostazione predefinita)
- Formato supportato: `.epub` (senza DRM)

**Funzioni:**

- **Navigazione tra capitoli:** scorri a sinistra/destra o usa i pulsanti del pannello comandi
- **Indice:** tocca l'icona elenco <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> per saltare a un capitolo specifico
- **Dimensione carattere:** regolabile (6px - 144px, predefinita 18px)
- **Ricerca:** trova testo all'interno del libro corrente
- **Temi:** si adatta automaticamente alla modalità Chiara/Scura

**Controlli:**

1. **Apri un file EPUB** dalla lista dei file
2. **Tocca lo schermo** per mostrare/nascondere il pannello comandi
3. **Usa i controlli in basso:**
   - `Precedente` / `successivo`: naviga tra i capitoli
   - `- A` / `+ A`: diminuisce/aumenta la dimensione del carattere
   - `Ricerca` <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: cerca testo
   - `Indice` <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: apre l'indice
4. **Gesto di scorrimento:** cambia capitolo in modo naturale

**Nota:** funziona perfettamente con file locali e stream di rete (SMB/SFTP/Cloud). I libri di grandi dimensioni (>50MB) su reti lente potrebbero richiedere qualche secondo per il caricamento iniziale.

---

## Come aprire file di rete in app esterne {#how-to-open-network-files-in-external-apps}

**Disponibile per:** file SMB, SFTP, FTP

**Caso d'uso:** vuoi aprire un documento, una foto o un video dalla tua unità di rete in un'app esterna specializzata (ad es. MS Office, Adobe Acrobat, VLC Player).

**Passaggi:**

1. **Sfoglia fino al file** sulla tua risorsa di rete
2. **Tocca il file** per aprirlo nel player/visualizzatore
3. **Tocca il pulsante ⓘ (Info)** nella barra degli strumenti superiore
4. **Tocca il pulsante "Scarica e apri"**
5. **Attendi il download** - una finestra di avanzamento mostra la percentuale
6. **Scegli l'app** dal selettore Android

**Cosa succede:**

- Il file viene scaricato nella tua cartella `Downloads`
- L'avanzamento viene mostrato in una finestra di dialogo (0-100%)
- Al termine del download, Android mostra il selettore delle app
- Puoi aprire il file in qualsiasi app compatibile

**Protocolli supportati:**

- ✅ Condivisioni di rete SMB/CIFS
- ✅ Server SFTP
- ✅ Server FTP
- ❌ Archiviazione cloud (non ancora implementato)

**Suggerimenti:**

- I file scaricati restano nella cartella `Downloads`
- Puoi eliminarli manualmente in seguito tramite un file manager
- Funziona con tutti i tipi di file (immagini, video, documenti, ecc.)
- Per i file di grandi dimensioni, il download può richiedere diversi minuti

**Esempi di utilizzo:**

- Modificare un documento di rete in MS Word
- Riprodurre un video di rete in VLC Player
- Visualizzare un PDF di rete in Adobe Acrobat
- Condividere una foto di rete tramite app di messaggistica

---

## Come visualizzare il testo dei brani {#how-to-view-song-lyrics}

**Requisiti:**

- File audio (MP3, FLAC, ecc.) con metadati Artista e Titolo.
- È richiesta una **connessione internet** (usa api.lyrics.ovh).

**Passaggi:**

1. **Riproduci un file audio** nel player a schermo intero.
2. Tocca il pulsante **"Testo"** nel pannello comandi superiore (o nel menu comandi).
   - *Nota: il pulsante è visibile solo per i file audio.*
3. Attendi il completamento della ricerca.
4. Il testo verrà mostrato in una finestra di dialogo scorrevole.

**Logica di ricerca:**

1. L'app cerca in base al tag **Artista + Titolo**.
2. Se i tag mancano, prova ad analizzare il **nome del file**.

---

## Traduzione automatica {#auto-translation}

Traduce automaticamente il testo da immagini, PDF e file di testo: **Tesseract** legge il testo, Google ML Kit lo traduce.

**Funzioni principali:**

- **Un solo motore di lettura:** **Tesseract** legge testo latino e cirillico (inglese, russo, ucraino, bulgaro, bielorusso); Google ML Kit traduce il risultato e ne identifica la lingua.
- **Offline:** funziona interamente sul dispositivo (dopo il download iniziale del modello).
- **Overlay intelligente:** il testo tradotto si sovrappone al testo originale in paragrafi leggibili.

**Configurazione:**

1. **Impostazioni** → scheda **Contenuti multimediali** → **Altro**
2. Attiva **"Abilita traduzione"**
3. Seleziona la **lingua di origine**:
   - **"Automatico":** legge il testo con il modello inglese, poi rileva la lingua di quanto letto ai fini della traduzione.
   - **Lingua specifica:** legge con il modello di quella lingua - scegliela per il testo cirillico (ad es. "Russo").
4. Seleziona la **lingua di destinazione** (ad es. inglese).

**Come usarla:**

1. Apri un file **Immagine**, **PDF** o **Testo**.
2. Tocca lo schermo per mostrare il **pannello comandi**.
3. Tocca il pulsante **"Traduci"** (icona A→文).
4. **Primo utilizzo:**
   - Conferma il download del modello di testo per la lingua di origine.
   - Conferma il download del modello di traduzione per la coppia di lingue.
5. Il testo tradotto comparirà in un overlay.

**Nota:** il primo utilizzo di una lingua carica il suo modello di testo, il che aggiunge un breve ritardo.

## Widget intelligenti per la Home {#home-screen-smart-widgets}

**Disponibile in:** tutte le edizioni - il set di widget è incluso in ogni build; ogni widget segue la propria capacità, quindi il widget del registratore vocale richiede una build con supporto microfono (non Lite o Photos) mentre i widget cornice fotografica e risorse funzionano ovunque

**Percorso rapido**

1. Vai alla schermata Home di Android, tieni premuto e seleziona **Widget**.
2. Trascina un widget **FastMediaSorter** (come il registratore vocale rapido 1×1 o Camera OCR) sullo schermo.
3. Configura la cartella di destinazione e le impostazioni di cattura, poi tocca **Salva**.
4. Usa il widget per eseguire attività con un tocco direttamente dalla tua schermata Home.

**Approfondimento dello scenario**

- Usa i widget 1×1 come icone di lancio dedicate per avviare azioni in background all'istante (ad es. tocca una volta per iniziare a registrare la voce, tocca di nuovo per salvarla sul tuo NAS).
- Configura un **widget Operazioni pianificate** per monitorare i trasferimenti di file in background o avviare un'operazione "Esegui tutto".
- Posiziona un **widget cornice fotografica casuale** per mostrare una presentazione rotante di foto di famiglia recuperate direttamente da una condivisione SMB.

**Quando aiuta**

- Vuoi scorciatoie rapide sulla schermata Home per le catture quotidiane (ricevute, memo vocali) senza aprire l'interfaccia principale dell'app.
- Ti servono widget chiari per controllare i contenuti multimediali o avviare operazioni pianificate all'istante.

**Da evitare**

- Non provare ad aggiungere widget se il tuo launcher Android limita la creazione di widget personalizzati.

---

## Come usare l'app come schermata Home {#how-to-use-the-app-as-your-home-screen}

FastMediaSorter può sostituire la schermata Home del tuo dispositivo e mostrare il proprio desktop al suo posto - le tue cartelle, un orologio, il meteo, le tue app e una barra delle applicazioni lungo un bordo. Se hai mai usato un desktop Windows ti sembrerà familiare: le cose restano dove le metti, e un pulsante Start apre il menu. Questa si chiama modalità launcher, ed è disponibile solo nelle build **Standard** e **noLegal**.

**Attivarla:**

1. Apri **Impostazioni → Generali** e attiva **Rendi questa app la schermata Home**.
2. Android chiede conferma. Su Android 10 e versioni successive è una sola domanda - "Consentire a FastMediaSorter di essere la tua app Home?" - quindi basta consentirlo. Sulle versioni più vecchie compare la classica scelta la prossima volta che premi Home: scegli FastMediaSorter e tocca **Sempre**, oppure **Solo una volta** se vuoi solo provarla per ora.
3. Premi Home. Il desktop compare, già riempito con una dozzina di elementi utili - un orologio, il meteo, le tue cartelle, una casella di ricerca - così il primo giorno non è una griglia vuota.

Su un'installazione nuova c'è una scorciatoia: spunta **Usa come schermata Home** nella prima pagina di benvenuto. Questo non interrompe la configurazione con una finestra di dialogo di sistema - la conferma di Android arriva la prima volta che apri **Impostazioni → Generali** in seguito.

**Cosa vive sul desktop:**

| Tipo di cella | Cosa fa |
|-----------|--------------|
| Scorciatoia risorsa | Apre una cartella che hai aggiunto - e scegli tu se si apre in modalità sfoglia, presentazione o riproduzione |
| Gadget | Un orologio con i secondi (tocca per le sveglie), il meteo dove vivi, ciò che sta suonando in questo momento, un traduttore, e altre due dozzine |
| Scorciatoia app | Avvia qualsiasi app installata; tieni premuto per elencare le azioni rapide di quell'app |
| Cella contatto | Apre la scheda di una persona, la chiama, invia un SMS, oppure apre la sua conversazione di messaggistica |
| Widget dell'app | Gli stessi widget che l'app offre per la schermata Home di Android, posizionati qui invece |

**La barra delle applicazioni e il menu Start:**

- La barra delle applicazioni si trova lungo il bordo inferiore e contiene il pulsante Start, le app usate di recente, quelle che hai fissato, e un piccolo vassoio con orologio, batteria, rete e segnale SIM.
- Preferisci averla in alto? **Impostazioni → Generali → Impostazioni launcher di sistema → Barra applicazioni → Posizione barra applicazioni** passa tra **Basso** e **Alto**. Il menu Start segue la barra e scende dall'alto quando la barra si trova lì.
- Il pulsante Start apre il menu: apri FastMediaSorter, le tue risorse, aggiungi una risorsa, impostazioni Android, impostazioni dell'app, impostazioni del launcher, modifica i contenuti del desktop, e infine riavvia, spegni ed **Esci dalla modalità launcher**. Riavvio e spegnimento funzionano solo se il tuo dispositivo consente a un'app comune di farlo - sulla maggior parte dei telefoni non faranno semplicemente nulla.

**Le tue app:** la griglia delle app raggruppa le app in sezioni, ciascuna con una piccola intestazione. Tocca un'intestazione per comprimere una sezione che apri raramente; le intestazioni compresse si affiancano tra loro, così il desktop diventa più corto invece di lasciare spazi vuoti. Un desktop nuovo divide in due le app che semina: una sezione **Google** per le app Google che hai già installato, e una sezione **App** per le tue - messaggistica, giochi e qualsiasi altra cosa tu metta sul dispositivo. Nessuna app finisce in entrambe. Tieni premuta qualsiasi app nella lista per **Metti sul desktop** e **Fissa alla barra applicazioni**.

**Riorganizzarlo:**

- Tieni premuto un riquadro vuoto del desktop. Compaiono quattro scelte: **Aggiungi un elemento..**, **Modifica il desktop**, **Sfondo**, **Impostazioni launcher**. La nuova cella finisce esattamente sul riquadro che hai premuto.
- **Aggiungi un elemento..** apre un selettore: un'app, una funzione, una delle tue cartelle, uno stream radio, una persona, un'azione di sistema, un'operazione pianificata, un gadget o un'azione. Tra i gadget ci sono la scheda "in riproduzione ora" - mostra ciò che sta suonando sul dispositivo e ti porta a quel player con un tocco - e la cella del traduttore.
- **Modifica il desktop** attiva la modalità modifica, la stessa di **Modifica contenuti del desktop** nel menu Start. Durante la modifica: trascina una cella per spostarla, trascina la maniglia d'angolo di un gadget per ridimensionarlo, tocca **+** per aggiungere qualcosa, e scegli **Rimuovi dal desktop** su una cella per toglierla. Tocca **Fatto** quando hai finito.
- Condividi il dispositivo con qualcuno? Attiva **Blocca desktop** nelle impostazioni del launcher - la pressione prolungata allora non fa nulla, così il layout non può essere spostato per errore.
- Altre app possono mettere qui le proprie scorciatoie, esattamente come farebbero su qualsiasi altra schermata Home.

**Verticale e orizzontale sono due desktop separati.** Ciò che sistemi in verticale non è ciò che ottieni quando ruoti il dispositivo di lato - ogni orientamento mantiene il proprio layout e le proprie sezioni compresse. L'app lo segnala una volta sola, la prima volta che ruoti un desktop che hai organizzato. Le impostazioni stesse - posizione della barra applicazioni, densità, sfondo - sono condivise da entrambi.

**Adattare più elementi, o meno, allo schermo:** **Impostazioni → Generali → Impostazioni launcher di sistema → Desktop → Densità griglia** offre **Sparsa**, **Standard**, **Densa** e **Molto densa** - celle più ampie, oppure più scorciatoie per schermata.

**Tornare alla tua vecchia schermata Home** - una qualsiasi di queste tre:

- Apri il menu Start, scegli **Esci dalla modalità launcher**, e conferma.
- Disattiva **Rendi questa app la schermata Home** in **Impostazioni → Generali**.
- Vai direttamente alla lista delle app Home di Android: **Impostazioni → Generali → Impostazioni launcher di sistema → Sistema → Cambia schermata Home**.

Il layout del tuo desktop viene conservato in ogni caso, quindi riattivare la modalità lo riporta esattamente come lo avevi lasciato.

**Un avvertimento onesto.** Alcuni dispositivi si rifiutano di ricordare la scelta. Alcune autoradio Android economiche di terze parti e altri box Android integrati riportano indietro la loro schermata Home di fabbrica a ogni avvio, qualunque cosa tu abbia scelto. Questo è il firmware del dispositivo stesso che ti scavalca, non un difetto dell'app, e nessuna app può aggirarlo. Se il tuo si comporta così, scegli di nuovo FastMediaSorter come app Home dopo un riavvio - e se ancora non si fissa, quel dispositivo semplicemente non lo consente.

---

## Come usare FMS su un box Android TV {#how-to-use-fms-on-android-tv-box}

FastMediaSorter funziona su qualsiasi box Android TV o set-top box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, box Android generici). Non serve un touchscreen - l'app è completamente utilizzabile tramite telecomando TV o tastiera Bluetooth.

**Cosa serve:**

- Un box Android TV con Android 8.0+ (Standard/Lite/Photos) o Android 6.0+ (edizione Legacy)
- Un telecomando TV con D-pad, o una tastiera Bluetooth
- Facoltativo: un NAS domestico (SMB), un'unità USB o una scheda SD con contenuti multimediali

**Navigazione con un telecomando TV:**

| Pulsante | Azione |
|--------|--------|
| D-pad Su/Giù/Sinistra/Destra | Sposta il focus tra gli elementi |
| OK / Invio | Apre l'elemento o conferma |
| Indietro | Torna alla schermata precedente |
| Backspace | Sale di una cartella in Sfoglia |
| Rosso | Elimina i file selezionati |
| Verde | Copia i file selezionati |
| Giallo | Sposta i file selezionati |
| Blu | Rinomina il file selezionato |
| Canale Su / Canale Giù | File precedente / successivo nel player |

**Passaggi:**

1. Installa l'app da Google Play oppure esegui il sideload di un APK. L'edizione Standard è consigliata.
2. Nella schermata principale, premi **OK** sul pulsante (+) per aggiungere una risorsa.
3. Scegli **Cartella locale** per l'archiviazione USB/SD, oppure **Cartella di rete** per connetterti a un NAS via SMB.
4. Dopo aver aggiunto la risorsa, entraci con D-pad + OK per sfogliare i file.
5. Apri un video, un'immagine o un file audio qualsiasi - il player funziona completamente tramite telecomando.
6. Per avviare una presentazione, apri una cartella di immagini e vai al pulsante **Presentazione** nella barra dei comandi.
7. Per aggiungere musica di sottofondo alla presentazione, vai su **Impostazioni → Contenuti multimediali → Immagini**, attiva **Riproduci musica durante la presentazione**, e seleziona la tua risorsa musicale.

**Suggerimenti:**

- Tieni premuto D-pad Su/Giù per accelerare lo scorrimento in liste di file lunghe.
- Premi **F1** su una tastiera Bluetooth per aprire un riferimento delle scorciatoie specifico per la schermata corrente.
- I tasti colorati del telecomando TV possono essere riassegnati in **Impostazioni → Gestione → Comandi e associazioni tasti**.

---

## Come registrare lo schermo {#how-to-record-your-screen}

**Disponibile in:** Standard, XR/noLegal

**Passaggi:**

1. Avviala dal menu con i puntini della schermata principale (**Registrazione video dello schermo**), dal pannello Avvio rapido, oppure dall'azione **Avvia registrazione schermo** del gesto dal bordo.
2. Conferma la richiesta di Android per condividere lo schermo o solo questa app - compare ogni volta che avvii una registrazione e non può essere saltata.
3. Una piccola pillola nell'angolo mostra **Registrazione schermo in corso**, con controlli di pausa/ripresa e stop. Anche una notifica offre **Stop**.
4. Tocca **Stop** quando hai finito.

**Cosa succede:**

- La registrazione cattura tutto ciò che è sullo schermo, incluse le altre app a cui passi, insieme all'audio.
- Il video finito viene salvato nella cartella Film del dispositivo.

**Nota:** il passaggio di conferma di Android è una protezione di sistema per qualsiasi cosa registri lo schermo - non è qualcosa che l'app può disattivare.

---

## Come registrare una nota vocale {#how-to-record-a-voice-note}

**Disponibile in:** Standard, Legacy, XR / noLegal

**Passaggi:**

1. Avvia una registrazione dalla voce **Registrazione vocale** nel menu con i puntini, dal widget home screen **Registratore rapido**, oppure dall'azione **Avvia registrazione audio** del gesto dal bordo.
2. Parla - un indicatore **Registrazione in corso..** (o una pillola fluttuante sopra qualsiasi app in primo piano) mostra che è attivo.
3. Tocca **Interrompi e salva** (o tocca di nuovo il widget/gesto) per terminare.

**Cosa succede:**

- La registrazione viene salvata nella destinazione del microfono che hai scelto nelle Impostazioni, oppure nella cartella Registrazioni del dispositivo se non ne è stata impostata una.
- Avviare una nota vocale dal widget o dal gesto dal bordo funziona anche mentre usi un'altra app - un piccolo controllo fluttuante resta in primo piano così puoi fermarla senza tornare all'app.

**Dove impostare la cartella di salvataggio:** Impostazioni → Gestione → Registratore vocale.

---

## Come usare la fotocamera integrata {#how-to-use-the-in-app-camera}

**Disponibile in:** Standard, Lite, Photos (solo foto), Legacy, XR/noLegal

**Passaggi:**

1. In Sfoglia, apri la barra degli strumenti o il menu con i puntini e tocca **Cattura con fotocamera** (foto) oppure **Registra video**.
2. Passa tra **Foto** e **Video** direttamente nella schermata della fotocamera se cambi idea.
3. Imposta lo zoom con un chip preimpostato (0.5x/1x/2x..) o con il cursore sottostante - restano sincronizzati.
4. Tocca il pulsante dell'aspetto per modellare l'inquadratura - **4:3**, **16:9** o **Schermo intero**. Il mirino stesso cambia, così quello che vedi è ciò che sarà la foto salvata, e la scelta viene ricordata la prossima volta che apri la fotocamera (16:9 finché non la cambi).
5. Tocca il pulsante dello scenario di scatto per scegliere come viene effettuato lo scatto - normale, notturno, ritratto, selfie, macro, sport o documento. Macro passa all'obiettivo dedicato a fuoco ravvicinato, selfie passa alla fotocamera anteriore, sport mantiene l'esposizione breve così il movimento si blocca, e documento è ottimizzato per fotografare pagine piatte e schermi. Vengono elencati solo gli scenari che il tuo dispositivo può effettivamente offrire, quello attivo è indicato sul pulsante, e cambiare l'obiettivo manualmente riporta la fotocamera a normale.
6. Tocca l'otturatore (o il pulsante di registrazione) per catturare. Il risultato viene salvato direttamente nella risorsa - locale o di rete - che stavi sfogliando.

**Suggerimenti:**

- Tocca in qualsiasi punto del mirino per mettere a fuoco e impostare l'esposizione su quel punto - un piccolo anello segna dove.
- L'azione **Avvia registrazione video** del gesto dal bordo apre la fotocamera già in modalità Video e inizia a registrare non appena l'anteprima è pronta - rapido, ma quella particolare scorciatoia salva nella cartella Film del dispositivo invece che nella risorsa sfogliata.
- Attiva **Geolocalizza foto** accanto alle impostazioni della fotocamera per incorporare la posizione GPS in ogni JPEG catturato - è disattivato finché non lo attivi. **Informazioni file** mostra poi la data di scatto e il punto GPS EXIF della foto come un link toccabile che si apre nella tua app di mappe o nel browser.

**Dove trovare le impostazioni della fotocamera:** Impostazioni → Gestione → Fotografia.

---

## Come trovare ed eliminare i file duplicati {#how-to-find-and-delete-duplicate-files}

**Passaggi:**

1. Apri una cartella in Sfoglia, poi apri il **menu con i puntini** <img src="icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> nella barra degli strumenti.
2. Tocca **Trova duplicati** per rivedere tu stesso le corrispondenze, oppure **Trova ed elimina duplicati** per rimuoverli subito.
3. Per **Trova duplicati**, l'app preseleziona ogni copia tranne la più vecchia di ogni gruppo - regola la selezione, poi tocca **Elimina selezionati** e conferma.
4. **Trova ed elimina duplicati** rimuove le stesse copie preselezionate subito dopo la scansione, senza passaggio di conferma - usa prima **Trova duplicati** se vuoi ricontrollare prima che qualcosa venga eliminato.

**Ripulire per dimensione invece:**

1. Dallo stesso menu con i puntini, tocca **Elimina per dimensione..**
2. Scegli **Più piccolo di** o **Più grande di**, imposta una dimensione, e tocca **Analizza**.
3. Rivedi il conteggio e lo spazio che libererebbe, poi tocca **Elimina file** per confermare.

**Note:**

- La scansione confronta i file per contenuto in tre passaggi - dimensione, poi un hash rapido, poi un controllo SHA-256 completo - così anche i duplicati rinominati vengono individuati.
- L'eliminazione per dimensione mostra quanto spazio libererai prima che venga rimosso qualcosa; le fonti di rete e cloud saltano il cestino, quindi quell'eliminazione è immediata e permanente.

---

## Come visualizzare le statistiche di utilizzo {#how-to-view-your-usage-statistics}

**Passaggi:**

1. Vai su **Impostazioni → Generali → Raccolta statistiche** e attivala.
2. Tocca **Statistiche** (compare subito sotto l'interruttore) per aprire la dashboard.

**Cosa vedrai:**

- Schede riassuntive per file ordinati, spazio liberato e tempo speso a riprodurre contenuti multimediali.
- Una ripartizione per tipo (immagini, video, audio, documenti..).
- Sezioni comprimibili con più dettagli: operazioni, cattura, visualizzazione, modifica, fonti e utilizzo generale.

**Condividere un report:**

- **Invia all'autore** apre la tua app email con un riepilogo allegato, indirizzato allo sviluppatore.
- **Esporta** condivide lo stesso riepilogo tramite il normale menu di condivisione Android, così puoi salvarlo o inviarlo ovunque.

**Nota:** tutto resta sul tuo dispositivo finché non scegli di inviarlo o esportarlo - vedi la FAQ per i dettagli sulla privacy.

---

## Come usare una scheda SD o un'unità collegata {#how-to-use-an-sd-card-or-connected-drive}

Una scheda di memoria o un'unità USB che il telefono ha montato contiene risorse esattamente come l'archiviazione integrata.

**Passaggi:**

1. Apri **Aggiungi risorsa** e inizia ad aggiungere una cartella locale. La sezione **Supporti rimovibili** compare solo mentre qualcosa è collegato, ed elenca ogni volume con il suo nome e lo spazio libero.
2. Tocca il volume. Se l'app non riesce a raggiungerlo per percorso, spiega perché e apre il selettore di cartelle di sistema - scegli lì lo stesso volume e concedi l'accesso alla cartella che vuoi.
3. La risorsa si unisce alla lista con un'icona di supporto rimovibile, così una risorsa su scheda è riconoscibile a colpo d'occhio.

**Spostamento e copia:** intere cartelle viaggiano verso una scheda e ritorno con l'intera struttura di sottocartelle, allo stesso modo in cui lo fanno tra il dispositivo e una risorsa di rete.

**Non c'è abbastanza spazio:** una copia o uno spostamento che non ci sta viene rifiutato prima di iniziare, e il messaggio indica il supporto e quanto spazio manca - liberane lì oppure scegli un'altra destinazione.

**Quando il supporto viene espulso:** le sue risorse vengono contrassegnate come non disponibili invece di essere rimosse. Ricollega la scheda e funzioneranno senza doverle configurare una seconda volta.

**Su Android 6:** il sistema non segnala i volumi montati alle app, quindi la sezione dei supporti rimovibili resta vuota su quei dispositivi.

---

## Come riconnettere una cartella aggiunta tramite percorso diretto {#how-to-reconnect-a-folder-added-by-direct-path}

Una cartella che hai aggiunto digitando o sfogliando il suo percorso può mostrare le tue foto, video e musica ma nessuno dei tuoi documenti. Non si tratta di una scansione che li ha persi: una build da store legge file di testo, PDF ed e-book solo attraverso una cartella che hai connesso con il selettore di sistema. Riconnettere punta la stessa risorsa alla stessa cartella tramite quel selettore, e i documenti compaiono.

**Passaggi:**

1. Tocca il menu a tre puntini sulla scheda della cartella nella lista principale.
2. Scegli **Riconnetti risorsa**. Si apre il selettore di cartelle di sistema, già dentro quella cartella dove il telefono lo consente.
3. Scegli la stessa cartella e conferma.
4. Se scegli una cartella diversa, l'app indica entrambe le cartelle e chiede conferma prima di cambiare qualcosa.

**Cosa resta:** il nome, la posizione nella tua lista, il PIN, l'icona, il ruolo Quick Sort, i tuoi preferiti e le tue pianificazioni sopravvivono tutti - la risorsa viene reindirizzata, non ricreata.

**Dove non la vedrai:** sulle build che leggono ancora le cartelle direttamente per percorso, e su Android 10 e versioni precedenti, la voce è assente perché lì non manca nulla.

---

## Come scegliere dove salvare catture e download {#how-to-choose-where-captures-and-downloads-are-saved}

Le foto dalla fotocamera integrata, gli screenshot, gli snapshot e i file scaricati automaticamente scrivono ciascuno in una cartella che scegli tu, e quella cartella non deve necessariamente essere una delle tue risorse.

**Passaggi:**

1. Apri l'impostazione per ciò che stai salvando - cattura, screenshot, snapshot o download automatico.
2. Scegli la cartella di destinazione. Si apre il browser di cartelle di sistema, così puoi puntare a qualsiasi cartella locale, anche una che non hai mai aggiunto all'app.
3. Quella cartella diventa la destinazione di scrittura solo per quell'impostazione. Resta fuori dalla tua lista generale delle risorse, così scegliere una cartella di appoggio per gli screenshot non affolla la schermata principale.

**Suggerimenti:**

- Ognuna delle quattro impostazioni ha la propria destinazione - gli screenshot e le foto della fotocamera possono finire in posti completamente diversi.
- Puntarne diverse verso un'unica cartella va bene se preferisci avere tutto in un solo posto.

### Nomi dei file di cattura {#capture-file-names}

Le nuove catture usano lo schema `prefisso_yyMMdd_HHmmss`. I prefissi stabili sono `photo`, `screenshot`, `audio`, `video`, `screen_video` e `video_frame`, così il nome del file identifica la sua origine. Se un nome esiste già nella cartella di destinazione, l'app aggiunge il suffisso ` (2)` prima dell'estensione. Un nome file inserito manualmente nella fotocamera resta una sostituzione manuale e non viene modificato.

---

## Come ricevere file condivisi da un'altra app {#how-to-receive-files-shared-from-another-app}

Il menu di condivisione di qualsiasi app può inviare file a FastMediaSorter, che poi li copia dove vuoi.

**Passaggi:**

1. Nell'altra app, condividi il file o i file e scegli **FastMediaSorter**.
2. Scegli la cartella di destinazione nella schermata di ricezione.
3. Avvia la copia.

**Non devi aspettare che finisca.** La copia continua a funzionare dopo che la schermata di ricezione si chiude, con una notifica che mostra l'avanzamento mentre lavora e una notifica di risultato quando finisce. Esci dall'app, blocca il dispositivo, continua pure - il trasferimento non è legato al fatto che quella schermata resti aperta.

Disponibile nelle build Standard, Lite, Photos e Legacy.

---

## Come usare i programmi integrati {#how-to-use-the-built-in-programs}

**Disponibile in:** tutte le edizioni - il menu programmi e il pannello sono inclusi in ogni build, ma ogni programma segue la propria capacità: Network Monitor richiede Standard o noLegal, il companion Wear richiede Standard o noLegal, il mini-gioco è assente da XR e noLegal, e Specchio richiede una fotocamera anteriore con la cattura fotocamera attivata nelle Impostazioni. La calcolatrice, la torcia frontale e Informazioni di sistema sono presenti in ogni build.

Oltre a sfogliare e riprodurre file, l'app porta con sé un insieme di piccoli programmi integrati - una calcolatrice, una lampada schermo, un monitor di rete, un registratore vocale e altro. Sono disattivati per impostazione predefinita: ognuno viene attivato dalla propria impostazione, e la maggior parte degli interruttori dedicati si trova insieme in **Impostazioni → Gestione**.

**Percorso rapido**

1. Vai su **Impostazioni → Gestione** e attiva ciò che vuoi - ad esempio **Calcolatrice**, **Torcia frontale**, **Network Monitor**, **Mini-gioco** o **Informazioni di sistema**.
2. Apri il menu a discesa della finestra principale. I programmi che hai attivato sono elencati lì.
3. Toccane uno per eseguirlo.

**Dove compare un programma**

Un programma può essere offerto su un massimo di quattro superfici, e ogni superficie prende i propri contenuti e il proprio ordine dalla stessa lista unica, così non si scostano mai tra loro:

- **Menu programmi** - il menu a discesa della finestra principale, e il pannello programmi che lo ripete.
- **Pannello di avvio app** - l'overlay ad accesso rapido.
- **Widget home screen** - solo per i programmi che ne hanno uno; fissalo dal selettore widget dell'app stessa.
- **Desktop del launcher** - quando usi l'app come schermata Home, attivare un programma ne aggiunge automaticamente la cella.

**Cosa c'è nel set**

Nell'ordine in cui compaiono:

- **Cattura rapida** - scatta una foto direttamente nell'app.
- **Registrazione vocale** - registra una nota vocale.
- **Calcolatrice** - una calcolatrice scientifica con cronologia e slot di memoria.
- **Network Monitor** - letture in tempo reale per il collegamento attivo, Wi-Fi, dati mobili, Bluetooth e posizione, più un traceroute che percorre la rotta verso un host passo dopo passo e continua a contare quando un passaggio non risponde.
- **Traduzione OCR foto** - fotografa un testo e traducilo.
- **Registrazione video dello schermo** - registra lo schermo.
- **Scarica da link** - recupera un file da un link incollato.
- **Mini-gioco** - il piccolo gioco integrato nell'app.
- **Informazioni di sistema** - un report del dispositivo raggiungibile senza aprire le Impostazioni.
- **Companion Wear** - la schermata dell'orologio, nelle build che includono il ponte verso il Wear.
- **Torcia frontale** - trasforma lo schermo stesso in una lampada: si apre bianca alla massima luminosità della finestra, uno scorrimento verticale cambia la luminosità, un piccolo pulsante in alto a sinistra sceglie e ricorda un altro colore, e un singolo tocco la chiude. Viene toccata solo la luminosità della finestra, così l'impostazione del tuo dispositivo resta invariata in seguito.
- **Torcia per l'acqua** - la stessa luce per le mani bagnate. Accende insieme il flash della fotocamera e lo schermo, poi blocca lo schermo: l'ora e un breve promemoria sono tutto ciò che vedi, e toccare il vetro non fa nulla - un tasto del volume la chiude. Spariscono anche le barre di sistema, così una mano bagnata non incontra un pulsante di navigazione; uno scorrimento deliberato può comunque farle tornare. Pensata per la pioggia e per la doccia - i due luoghi dove il vetro reagisce all'acqua invece che a te. Uscire tramite un gesto di sistema spegne anche la luce, così non resta mai accesa in tasca. Sull'orologio non c'è il flash, quindi solo il display fa da luce. Non sostituisce la modalità blocco acqua integrata in un orologio o in un telefono; nessuna app può attivare quella.
- **Specchio** - trasforma il telefono in uno specchio illuminato: la fotocamera anteriore riempie lo schermo dentro un campo bianco brillante che illumina il tuo volto, e l'immagine viene capovolta nel modo in cui la mostrerebbe uno specchio reale, con un pulsante d'angolo per spegnere la luce senza uscire dalla schermata. I preset di zoom - x1, x2, x3, x5 - si trovano in basso a sinistra e si aprono su x3. Un pulsante foto e un pulsante video salvano direttamente nella stessa cartella usata da Cattura, il video con audio. Lo zoom, il capovolgimento e lo stato della retroilluminazione vengono tutti ricordati tra un utilizzo e l'altro. Viene aumentata solo la luminosità della finestra, mai quella del tuo dispositivo, così il telefono torna normale nel momento in cui esci. Attivo per impostazione predefinita su qualsiasi dispositivo con fotocamera anteriore, purché la cattura fotocamera stessa non sia disattivata nelle Impostazioni.

Il pannello e il launcher includono inoltre scorciatoie dirette per la fotocamera - scatta una foto e inviala, scatta una foto e modificala, scatta una foto e traducila, avvia una registrazione video, e apri la cartella della fotocamera.

**Quando aiuta**

- Vuoi una calcolatrice o una torcia senza uscire dall'app, o senza cercare un'app separata su un telefono affollato.
- Usi l'app come schermata Home e vuoi una cella con un tocco per uno strumento che usi spesso.

**Da evitare**

- Non aspettarti che la torcia per l'acqua sopravviva a uno scorrimento verso la home - un gesto di navigazione di sistema la chiude comunque, e la luce si spegne con essa.
- Non aspettarti ogni programma in ogni build - l'elenco sopra è il set completo, e una build senza la capacità sottostante semplicemente non mostra quella voce.

---

## Come chiedere al tuo assistente di trovare e aprire i contenuti multimediali {#how-to-ask-your-assistant-to-find-and-open-media}

**Disponibile in:** ogni build, su Android 16 e versioni successive. Le versioni Android precedenti semplicemente non offrono la funzione, e non c'è nulla da attivare nell'app.

Su Android 16+ l'app registra presso il sistema un insieme di azioni per l'assistente - AppFunctions, nel linguaggio stesso di Android. L'assistente del tuo dispositivo può quindi chiamarle per nome, così puoi chiedere a voce una foto, un video o una cartella di computer invece di aprire l'app e cercarla tu stesso sfogliando.

**Cosa puoi chiedere**

- **Cerca i tuoi contenuti multimediali** - l'assistente passa le tue parole alla ricerca dell'app e mostra ciò che ha trovato.
- **Apri un file multimediale** - una foto, un video o un brano si apre direttamente nel visualizzatore o nel player dell'app.
- **Apri una cartella di computer** - una delle tue cartelle di rete o cloud si apre nella schermata di navigazione.

**Percorso rapido**

1. Assicurati che il dispositivo esegua Android 16 o versioni successive e abbia un assistente di sistema configurato.
2. Chiedi all'assistente il contenuto multimediale che vuoi, nominando FastMediaSorter se il dispositivo ospita più app multimediali.
3. L'app si apre sul risultato - la lista di ricerca, il file o la cartella che hai chiesto.

**Quando aiuta**

- Hai le mani occupate - cucini, guidi, tieni in braccio un bambino - e sfogliare cartelle non è un'opzione.
- Ricordi come si chiama un file ma non dove lo hai archiviato.

**Da evitare**

- Non aspettartelo sotto Android 16: le azioni dell'assistente fanno parte del sistema più recente, quindi su un telefono più vecchio l'assistente non le vedrà.
- Non aspettarti che l'assistente raggiunga una cartella protetta da PIN - il blocco si applica comunque, e la cartella chiede il suo PIN come al solito.

---

## Serve altro aiuto? {#need-more-help}

- 📖 **Guida rapida:** [QUICK_START-it.md](QUICK_START-it.md)
- ❓ **FAQ:** [FAQ-it.md](FAQ-it.md)
- 🔧 **Risoluzione dei problemi:** [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.md)
- 🐛 **Segnala un problema:** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

</div>
