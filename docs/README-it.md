---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-it.html
---
<div lang="it" dir="ltr" markdown="1">

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

{% include lang-switcher.html doc="README" dir="/docs/" current="it" %}

**📦 Download:** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Scaricalo su IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

Installi l'APK direttamente? Android avvisa quando si tratta di un pacchetto che non ha mai visto prima - [perché appare l'avviso e cosa toccare](INSTALL_TRUST.md).

**📘 Documentazione utente:** [guide passo passo per ogni funzionalità, con ricerca](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## Informazioni sul progetto

**FastMediaSorter v2** è un guscio completo per un dispositivo Android. Prende il controllo della schermata Home, riproduce i tuoi contenuti multimediali, apre stream in diretta, avvia le tue app, comunica con il tuo smartwatch, tiene d'occhio il dispositivo e gestisce ogni file che possiedi - in cartelle locali, su unità di rete (SMB, SFTP, FTP) e in archivi cloud (Google Drive, OneDrive, Dropbox).

È costruito su otto pilastri: guscio del dispositivo, player multimediale, stream in diretta, avvio delle app, sostituto delle app di sistema, companion per lo smartwatch, monitoraggio del dispositivo e file manager completo. Ordinare i file da tutte queste fonti è il punto di partenza dell'app, ed è ancora la base su cui si costruisce il resto - ma non ne è più la totalità.

Questo manuale segue ora lo stesso vocabolario pubblico dell'inventario delle funzionalità canonico in [FEATURES.md](FEATURES.md) e della mappa della documentazione in [DOCS_MAP.md](DOCS_MAP.md). Usa queste due pagine come fonte di verità aggiornata per la storia dell'app, le edizioni disponibili e l'attuale superficie di funzionalità.

## Versione Windows 🖥️

Cerchi una soluzione desktop? Dai un'occhiata a **Fast Media Sorter for Windows** (in precedenza FastMediaSorter LITE) - un'applicazione Windows Forms leggera per ordinare, visualizzare e gestire rapidamente file immagine e video:

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [Come pubblicare le cartelle del PC su Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - condividi le cartelle del tuo PC con l'app tramite SFTP (importazione companion / scansione QR sul telefono).

Le funzionalità includono:

- Navigazione rapida tra grandi cartelle di immagini e video
- Modalità presentazione e visualizzazione casuale dei file
- Tracciamento dei file e delle cartelle recenti
- Operazioni sui file: spostamento, copia, rinomina ed eliminazione
- Pannello immagini per una rapida navigazione visiva
- Scorciatoie da tastiera personalizzabili per un flusso di lavoro efficiente
- Supporto multilingua (Inglese/Russo)
- Supporta Windows 7/10/11 con .NET Framework 4.8

## Sommario

- [Download](#download-)
- [Edizioni](#editions-)
- [Funzionalità principali](#key-features)
- [Formati multimediali supportati](#supported-media-formats-)
- [Screenshot](#screenshots-)
- [Scenari d'uso](#usage-scenarios-)
- [Documentazione](#documentation-)
- [Companion Wear OS](#wear-os-companion-)
- [Istruzioni di build](#build-instructions)
- [Test](#testing-)
- [Primi passi](#first-steps-quick-usage-guide-)
- [Stack tecnologico](#technology-stack)

## Edizioni 🎯 {#editions-}

FastMediaSorter v2 viene distribuito in **sette edizioni** - cinque per telefoni e tablet di uso quotidiano (Standard, Lite, Photos, Legacy, FOSS) più due build per visori e sideload, VR e noLegal. La griglia di funzionalità canonica è generata dalla build in [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md):

| Edizione | Descrizione | Note |
|--------|-------------|-------|
| **Standard** | Versione completa | Il set di funzionalità più ampio per media, documenti, OCR, traduzione e accesso cloud |
| **Lite** | Versione leggera | Solo file locali - video, audio e immagini; nessuna fonte di rete, cloud, documenti o Streams |
| **Photos** | Versione incentrata sulle foto | Solo immagini, con SMB/FTP/SFTP e cloud; nessun video e nessun audio |
| **Legacy** | Versione orientata alla compatibilità | Stesso set di funzionalità di Standard, incluso SMB/FTP/SFTP e cloud (Google Drive, Dropbox, OneDrive); realizzata per Android 6/7 (API 23+) |
| **FOSS** | Versione per il catalogo F-Droid | Nessun SDK proprietario: media locali, documenti, EPUB e SMB/FTP/SFTP; nessun cloud, nessun Streams, nessun OCR, nessuna traduzione e nessun Wear OS companion |
| **VR** | Build per visori pulita per gli store | Set multimediale completo per i visori; nessun Google Cast e nessun Wear OS companion |
| **noLegal** | Build per sideload | Tutto quello che c'è in Standard più il player immersivo OpenXR ed extra riservati al sideload |

### Quale edizione dovrei scaricare?

- **Standard** ⭐ **(Consigliata)**: la scelta predefinita migliore per la maggior parte degli utenti
- **Lite**: preferiscila se vuoi un pacchetto più leggero e una configurazione più semplice
- **Photos**: preferiscila per flussi di lavoro incentrati sulle foto
- **Legacy**: scegli questa per dispositivi Android 6/7 (API 23+) - include rete e cloud
- **FOSS**: scegli questa dal catalogo F-Droid quando vuoi una build priva di SDK proprietari
- **VR**: scegli questa per un visore XR - la build da store senza Cast e supporto Wear
- **noLegal**: solo sideload - scegli questa quando ti serve il player immersivo OpenXR

Per la disponibilità esatta delle funzionalità per edizione, usa la documentazione canonica:

- [Inventario delle funzionalità (canonico)](FEATURES.md)
- [How-To (tabella di disponibilità delle funzionalità)](HOW_TO-it.md)
- [Guida rapida (selezione dell'edizione)](QUICK_START-it.md)
- [Limitazioni del programma](LIMITATIONS.md)

> 🧭 **Primo avvio:** subito sotto il selettore della lingua, l'app ti permette di scegliere un **profilo del dispositivo** (telefono, tablet, TV, auto, cornice digitale, VR e altro) che personalizza le impostazioni di partenza per te - modificabile in qualsiasi momento nelle Impostazioni. Vedi [Primo avvio: scegli il profilo del dispositivo](QUICK_START-it.md#first-launch-choose-your-device-profile-30-seconds-).

## Download 📥 {#download-}

📲 **[Scaricalo su Google Play](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**I file APK compilati NON sono conservati in questo repository GitHub.** Tutte le build sono disponibili su **Google Drive**:

🔗 **[Scarica tutte le build da Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Edizione | Nome file | Descrizione |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Funzionalità complete (Cloud, OCR, EPUB, Traduzione) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Solo media locali (Video, Audio, Immagini; nessuna rete, cloud, documenti o Streams) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Solo immagini, con rete (SMB/FTP/SFTP) e cloud |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Stesse funzionalità di Standard, incluso rete (SMB/FTP/SFTP) e cloud; Android 6/7 (API 23+) |

> **Nota**: tutte le build vengono caricate automaticamente su Google Drive dopo una compilazione riuscita.
>
> 🔐 **Password ZIP: `1`** (i file APK sono confezionati in archivi ZIP protetti da password per aggirare le restrizioni di Google Drive)

## Screenshot 📱 {#screenshots-}

| Schermata principale | Azioni sui file | Impostazioni |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **Vista del player** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

Immagini a dimensione intera:

- [Schermata principale](images/Screenshot_20251109_000251.png)
- [Azioni sui file](images/Screenshot_20251109_000314.png)
- [Impostazioni](images/Screenshot_20251109_000323.png)
- [Vista del player](images/Screenshot_20251114_184930.png)

## Funzionalità principali {#key-features}

- 🗂️ **Interfaccia unificata:** visualizza e gestisci i file da tutte le fonti in un'unica finestra.
- ⚡ **Ordinamento rapido:** copia o sposta i file in cartelle di destinazione preconfigurate con un clic.
- ⭐ **Sistema dei preferiti:** contrassegna i file importanti come preferiti e accedi rapidamente da una scheda dedicata che aggrega i preferiti di tutte le fonti.
- 🔒 **Protezione con PIN:** proteggi le singole risorse con codici PIN di accesso per impedire navigazione e modifiche non autorizzate.
- ⚙️ **Configurazione per risorsa:** personalizza l'intervallo della presentazione, la profondità di scansione (sottocartelle) e la generazione delle miniature per ogni cartella singolarmente.
- 🧭 **Configurazione del profilo del dispositivo:** scegli un profilo al primo avvio per telefoni, tablet, TV/media box, autoradio per auto, lettori multimediali, cornici digitali, lettori audio, e-reader, visori VR o impostazioni predefinite personalizzate; l'app applica le impostazioni predefinite corrispondenti per sicurezza, schermo, contenuti e priorità dei comandi.
- 📋 **Risorse intelligenti predefinite:** risorse virtuali integrate - **Tutta la musica**, **Tutti i video**, **Tutte le foto** - che aggregano i contenuti multimediali dell'intero dispositivo senza alcuna configurazione. Accedi istantaneamente all'intera libreria multimediale senza aggiungere manualmente le singole cartelle.
- 🖥️ **Supporto rete e cloud:** lavora con i file sulle tue unità di rete (SMB con scansione automatica della rete), server SFTP, FTP e archivi cloud (Google Drive, Dropbox, OneDrive).
- 🖼️ **Visualizzazione flessibile:** mostra i file come griglia personalizzabile o elenco dettagliato con supporto alla paginazione per grandi raccolte (oltre 1000 file).
- ▶️ **Player integrato:** riproduzione di video e audio, visualizzazione di immagini e GIF senza uscire dall'app. Supporta presentazione e zoom a schermo intero.
- 🧩 **Integrazione come player predefinito:** interruttori di riproduzione opzionali permettono a FastMediaSorter di agire come gestore multimediale di sistema per gli intent di apertura/condivisione (ACTION_VIEW / ACTION_SEND), e instradano gli eventi hardware dei tasti multimediali di riattivazione al servizio di riproduzione audio.
- 🗣️ **AppFunctions per l'assistente (Android 16+):** l'app dichiara azioni richiamabili dall'assistente - cerca i tuoi contenuti multimediali, apri un file o apri una cartella del computer - così l'assistente di sistema del tuo dispositivo può trovare e aprire i tuoi contenuti semplicemente chiedendolo.
- 🎛️ **Supporto ai pulsanti hardware:** i comandi al volante, i pulsanti delle cuffie e i tasti multimediali fisici (Play/Pausa, Successivo, Precedente) sono pienamente supportati tramite il servizio audio in background - nessuna interazione con lo schermo necessaria.
- 📻 **Stream Internet (schermata Streams):** riproduci radio Internet (http/https, Icecast/Shoutcast con now-playing ICY), stream HLS/DASH e sorgenti RTSP direttamente da una schermata Streams dedicata. Aggiungi URL manualmente, importa una playlist `.m3u` o scarica un catalogo curato di FastMediaSorter. Fissa i preferiti in cima; filtra per categoria e lingua. Audio inline: la radio viene riprodotta dall'elenco tramite un mini-controllo fisso in basso mentre l'elenco resta scorrevole. Video e RTSP si aprono nel player a schermo intero. Disponibile in Standard, Legacy, VR e noLegal; assente in Lite e Photos.
- 🎵 **Supporto testi delle canzoni:** visualizza il testo della canzone attualmente in riproduzione. Cerca automaticamente tramite i metadati (Artista/Titolo) usando `api.lyrics.ovh`, con ripiego sull'analisi del nome del file.
- 🎶 **Musica di sottofondo per la presentazione:** riproduci musica di sottofondo durante le presentazioni di immagini. Seleziona qualsiasi risorsa audio come fonte musicale, con riproduzione casuale delle tracce, controllo del volume e visualizzazione del nome della traccia. Tocca il nome della traccia per passare a un'altra traccia casuale. Funziona perfettamente con file di rete e cloud.
- ✏️ **Modifica delle immagini:** ruota, capovolgi, applica filtri (scala di grigi, seppia, negativo), regola luminosità/contrasto/saturazione - sia per file locali che di rete.
- 🗂️ **Supporto ai file binari:** visualizza e gestisci file binari (ZIP, RAR, APK, ISO, EXE, DLL, ecc.) con miniature generate che mostrano le estensioni dei file. Menu contestuale con Condividi/Apri con/Copia/Sposta/Rinomina/Elimina. Disponibile solo in "Modalità Tutti i file".
- ⌨️ **Tastiera, mouse e gamepad:** input completo da tastiera, mouse e gamepad su tutte le schermate - Sfoglia, Player, Impostazioni, finestre di dialogo. Completamente rimappabile tramite Impostazioni → Gestione → Controlli e associazioni tasti; premi F1 su qualsiasi schermata per una sovrapposizione di aiuto specifica per quella superficie. Navigazione dell'elenco con D-pad; menu contestuale con clic destro ed effetti al passaggio del mouse.
- 🔍 **Ordinamento e filtri:** ordina i file per nome, data, dimensione e durata. Applica filtri per una ricerca rapida. Supporto per i file nascosti (che iniziano con `.`) con interruttore dedicato.
- ↩️ **Annulla e cestino:** possibilità di annullare l'ultima azione (copia, spostamento, eliminazione) con eliminazione reversibile nella cartella `.trash/`. Include la funzione "Svuota cestino" per le risorse.
- 🎨 **Interfaccia moderna:** supporto per temi chiaro e scuro, controlli intuitivi, Material Design 3.
- 💾 **Cache intelligente:** caricamento dei metadati video in due fasi (1MB iniziale, 5MB esteso) e cache delle miniature configurabile (2GB predefinito, fino a 16GB).
- 📄 **Visualizzatore di documenti:** visualizzatore integrato per file di testo (.txt, .md, .log, .json, .xml) e documenti PDF con zoom, panoramica e navigazione a gesti.
- 📚 **Lettore di e-book EPUB:** lettore EPUB nativo con navigazione tra i capitoli, indice, controllo della dimensione del carattere, ricerca nel libro e supporto ai temi chiaro/scuro. Funziona con file locali e di rete.
- 📥 **Scarica e apri:** scarica i file di rete (SMB/SFTP/FTP) nell'archivio locale e aprili in app esterne con monitoraggio dell'avanzamento.
- 🌐 **Traduzione automatica:** traduci istantaneamente il testo da immagini, PDF e file di testo interamente sul dispositivo: **Tesseract** legge il testo in caratteri latini e cirillici, e Google ML Kit lo traduce. Supporta sia la modalità standard che la **modalità overlay in stile lente** per traduzioni sul posto.
- 📱 **Supporto ai widget:** oltre una dozzina di widget per la schermata Home che coprono un'ampia gamma - scorciatoie alle risorse, player multimediali, acquisizione dalla fotocamera, calcolatrici, attività pianificate, preferiti, mini-giochi e altro. Sfoglia l'intera selezione nel selettore widget del tuo launcher.
- 🏠 **Modalità schermata Home:** lascia che l'app sia la schermata Home del tuo dispositivo (build Standard e noLegal): un proprio desktop con scorciatoie alle risorse che aprono direttamente in modalità sfoglia, presentazione o riproduzione, gadget ridimensionabili come orologio e meteo, celle di contatto che non richiedono il permesso sui contatti, una griglia di app e una taskbar. Disattivala in qualsiasi momento e Android ripristina la tua schermata Home precedente.
- ⏰ **Operazioni sui file pianificate:** automatizza le operazioni sui file (Copia/Sposta/Elimina) usando regole basate sul tempo con filtri flessibili ed esecuzione in background.
- 👆 **Gesti avanzati:** controlli di zoom intelligenti (2x/3x/4x) per le immagini e zone touch intuitive per la navigazione tra i file.
- 📸 **Salva fotogramma:** cattura il fotogramma video corrente come istantanea PNG o JPG e salvala in qualsiasi risorsa configurata - locale o di rete. Il formato di output e la risorsa di destinazione si impostano nelle Impostazioni video.
- 🖨️ **Stampa:** invia documenti (PDF, TXT) e immagini a una stampante direttamente dal player integrato. I file di rete e cloud vengono memorizzati nella cache localmente prima della stampa.
- ⬇️ **Scaricamento dello stream:** scarica un file di rete nella cache locale con una finestra di avanzamento in tempo reale prima o durante la riproduzione. Un prompt opzionale di pulizia recupera lo spazio di archiviazione in seguito.
- 🔊 **Audio DTS/DTS-HD:** le tracce audio DTS e DTS-HD vengono decodificate via software tramite una build FFmpeg personalizzata - non serve hardware speciale.
- 🎨 **Colore e luminosità video:** regola tonalità e luminosità in tempo reale usando gli effetti GPU di Media3. Le impostazioni persistono tra i file video durante la sessione.
- 📤 **Condividi con FastMediaSorter:** ricevi file da qualsiasi app tramite il normale foglio di condivisione Android e copiali in una risorsa selezionata con un solo tocco.
- 📷 **Acquisizione dalla fotocamera in Sfoglia:** scatta una foto con la fotocamera del dispositivo e salvala direttamente nella risorsa corrente - locale o di rete - senza uscire dall'app.
- 🔗 **Download automatico dai link:** condividi qualsiasi URL http(s) con l'app tramite il foglio di condivisione Android; il file multimediale viene scaricato e salvato automaticamente in una risorsa selezionata.
- 👁️ **Modalità 3D a occhio singolo:** ritaglia i contenuti stereo (SBS/OU) su un occhio solo per una visione comoda su schermi piatti; funziona sia per video che per immagini.
- 📲 **Cattura e registrazione schermo:** striscia di gesti sul bordo sinistro per screenshot, foto rapide, ritaglia-e-condividi e registrazione schermo/voce/video senza uscire dal file corrente.
- 📊 **Statistiche di utilizzo (opzionale):** dashboard locale dei file ordinati, dello spazio liberato e del tempo di riproduzione - nulla lascia il dispositivo a meno che tu non lo esporti.
- 🧹 **Ricerca duplicati e pulizia dimensioni:** scansione dei duplicati basata sul contenuto (dimensione, hash rapido, SHA-256) con eliminazione manuale o automatica, più una pulizia per eliminazione in base alla dimensione.

## Formati multimediali supportati 🎞️ {#supported-media-formats-}

FastMediaSorter v2 supporta un'ampia gamma di formati:

- **Immagini:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF
- **Video:** MP4, MKV, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG
- **Audio:** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, DTS, DTS-HD
- **Documenti:** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **File binari** (Modalità Tutti i file): ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO, e oltre 60 altri formati

## Scenari d'uso 💡 {#usage-scenarios-}

Ecco alcuni modi in cui FastMediaSorter v2 può aiutarti:

### 1. 📸 Organizzare le foto della fotocamera

Collega il tuo telefono o apri una cartella fotocamera locale. Configura una cartella di destinazione "Migliori foto". Apri il visualizzatore, scorri rapidamente tra migliaia di foto e tocca il pulsante di destinazione per copiare istantaneamente gli scatti migliori.

### 2. 🏠 Backup di rete (NAS)

Aggiungi il tuo NAS domestico tramite SMB. Sfoglia i tuoi file multimediali locali. Seleziona più file o un intervallo e "spostali" sul tuo NAS per conservarli in sicurezza, liberando spazio sul dispositivo.

### 3. ☁️ Gestione del cloud

Collega il tuo account Google Drive, Dropbox o OneDrive. Sfoglia i tuoi file cloud senza scaricarli tutti. Elimina i file indesiderati o organizzali in cartelle direttamente nel cloud.

### 4. 📺 Presentazione e slideshow

Apri una cartella con foto di famiglia o diapositive di una presentazione. Premi "Riproduci" per avviare una presentazione. Usa le impostazioni per risorsa per regolare la durata delle diapositive a tuo piacimento.

### 5. ⭐ Gestire i preferiti

Contrassegna i file importanti con il pulsante a stella durante la navigazione. In seguito, tocca la scheda "Preferiti" nel menu principale per accedere istantaneamente a tutti i tuoi file preferiti di tutte le fonti in un unico posto - perfetto per creare una raccolta curata dei tuoi migliori contenuti multimediali.

### 6. 🎶 Presentazione con musica di sottofondo

Aggiungi la tua collezione musicale come risorsa. In **Impostazioni → Media → Immagini**, attiva **"Riproduci musica durante la presentazione"** e seleziona la tua risorsa musicale. Ora, quando avvii una presentazione delle tue foto, le tue tracce preferite verranno riprodotte in sottofondo. Tocca il nome della traccia per passare a un'altra canzone casuale, creando l'atmosfera perfetta per le tue presentazioni fotografiche.

### 7. 🖼️ Cornice digitale su un tablet

Trasforma qualsiasi **tablet** Android in una splendida cornice digitale sempre accesa. Posizionalo su un supporto, collegalo al tuo PC di casa (SMB) o all'archivio cloud - le foto vengono trasmesse in streaming direttamente senza occupare spazio di archiviazione locale. Regola l'intervallo delle diapositive, mantieni lo schermo sempre acceso, aggiungi musica di sottofondo e goditi i tuoi ricordi. Anche i vecchi tablet economici e lenti funzionano perfettamente per questo scopo - l'app è ottimizzata per la riproduzione continua a basse risorse.

### 8. 🍿 Cinema domestico e VR

Guarda la tua serie preferita conservata sul tuo PC o nel cloud direttamente sul telefono o sul visore VR. Nessuna attesa per la copia né preoccupazione per lo spazio libero. Basta premere play, e il prossimo episodio inizierà automaticamente.

**Casi d'uso per i visori VR** - FastMediaSorter funziona nativamente su visori VR basati su Android (Meta Quest, Pico e simili) senza alcuna modifica:

- **🎬 Cinema virtuale gigante**: apri un video dal tuo NAS domestico o dall'archivio cloud e guardalo su uno schermo virtuale grande quanto un'intera parete. Non serve copiare file da giga sul visore - l'app trasmette in streaming direttamente sulla tua rete domestica. Quando un episodio finisce, il successivo parte automaticamente.
- **🎵 Lettore musicale immersivo**: avvia la tua collezione musicale nell'ambiente VR. Il servizio audio in background mantiene la musica in riproduzione anche quando passi tra le app o apri la schermata Home del VR. I pulsanti hardware del visore (play/pausa, traccia successiva) funzionano senza toccare il controller.
- **🖼️ Cornice foto VR a grandezza di parete**: trasforma il tuo visore VR in un'esperienza fotografica immersiva - avvia una presentazione e le tue foto riempiono un'enorme parete virtuale intorno a te. Abbinala alla musica di sottofondo per un'esperienza di ricordi cinematografica e avvolgente. Trasmetti le foto direttamente dal tuo PC di casa o dal cloud in modo che l'archivio del visore resti libero.

### 9. 🧹 Organizzatore dei download

Cartella Download disordinata? Aprila nel pannello sorgente, configura i pulsanti di destinazione per "Documenti", "Immagini" e "Programmi di installazione". Scorri rapidamente i file, visualizzali in anteprima e ordinali nei posti giusti con un solo tocco. Puoi persino ordinare i file direttamente sul tuo computer di rete usando il telefono come telecomando.

### 10. 🚗 Musica in auto con autoradio Android

Installa FastMediaSorter sulla tua autoradio o unità centrale Android. Aggiungi cartelle musicali da chiavetta USB o scheda SD - oppure usa la risorsa virtuale integrata **Tutta la musica** per accedere istantaneamente all'intera collezione senza alcuna configurazione. I pulsanti multimediali hardware (comandi al volante, manopole del volume) funzionano perfettamente tramite il servizio audio in background: play/pausa, traccia successiva/precedente, tutto senza toccare lo schermo. L'app ricorda la posizione di riproduzione e riprende automaticamente all'avvio.

Con la schermata **Streams** attivata, la stessa unità centrale riproduce anche stazioni radio Internet direttamente tramite dati mobili o Wi-Fi - senza bisogno di un'app separata come TuneIn o RadioDroid. Aggiungi qualsiasi URL radio, oppure importa un catalogo di stazioni curato dalla schermata Estensioni. Il mini-controllo fisso mostra il nome della traccia ICY corrente mentre l'elenco delle stazioni resta visibile.

### 11. 📺 Media center su un box Android TV

Installa FastMediaSorter su qualsiasi box Android TV (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV o un box Android generico). Collegati a un NAS domestico tramite SMB, aggiungi Google Drive o Dropbox, oppure collega una chiavetta USB - tutto da un'unica app. Controlla l'intero flusso con un telecomando o una tastiera Bluetooth: il D-pad sposta il focus, **OK** apre gli elementi, **Indietro** torna al livello precedente e **Backspace** sale di una cartella nel browser. I pulsanti colorati del telecomando corrispondono alle azioni comuni sui file (**Rosso** = Elimina, **Verde** = Copia, **Giallo** = Sposta, **Blu** = Rinomina). Avvia una presentazione a schermo intero con musica di sottofondo sulla TV, oppure passa alla riproduzione audio con copertina dell'album e testi. Non è richiesto alcun touchscreen.

## Documentazione 📚 {#documentation-}

**🗺️ Documentation Map / Карта документации:** [View all docs / Все документы](DOCS_MAP.md)

**🌐 Sito ufficiale:** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### Fonti canoniche (Single Source of Truth)

I seguenti file devono essere considerati le fonti autorevoli per i dettagli rivolti all'utente:

- [Elenco completo delle funzionalità](FEATURES.md)
- [Mappa della documentazione](DOCS_MAP.md)
- [Cronologia del prodotto](PRODUCT_HISTORY.md)
- [Download (EN)](DOWNLOADS.md)
- [Guide How-To](HOW_TO.md)
- [Limitazioni del programma](LIMITATIONS.md)
- [Guida rapida](QUICK_START.md)
- [Termini di servizio](TERMS_OF_SERVICE.md)

Guide dettagliate sono disponibili in più lingue:

**🇺🇸 English:**

- [Product History](PRODUCT_HISTORY.md)
- [How-To Guides](HOW_TO.md)
- [Launcher Web Portal](launcher/index.md)
- [Wear OS Web Portal](wear/index.md)
- [Quick Start](QUICK_START.md)
- [FAQ](FAQ.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Program Limitations](LIMITATIONS.md)
- [Downloads Guide](DOWNLOADS.md)
- [Complete Feature List](FEATURES.md)

**🇷🇺 Русский:**

- [История продукта](PRODUCT_HISTORY-ru.md)
- [Руководства](HOW_TO-ru.md)
- [Быстрый Старт](QUICK_START-ru.md)
- [FAQ](FAQ-ru.md)
- [Устранение неполадок](TROUBLESHOOTING-ru.md)
- [Ограничения программы](LIMITATIONS-ru.md)
- [Скачивание сборок](DOWNLOADS-ru.md)

**🇺🇦 Українська:**

- [Історія продукту](PRODUCT_HISTORY-uk.md)
- [Посібники](HOW_TO-uk.md)
- [Швидкий Старт](QUICK_START-uk.md)
- [FAQ](FAQ-uk.md)
- [Вирішення проблем](TROUBLESHOOTING-uk.md)
- [Обмеження програми](LIMITATIONS-uk.md)
- [Завантаження збірок](DOWNLOADS-uk.md)

**Technical / Developer Docs:**

- [Architecture Overview](ARCHITECTURE.md)
- [DevOps & Build Scripts](DEV_OPS.md)
- [Technology Stack](TECH_STACK.md)
- [Wear OS Documentation](WEAR_OS_QUICK_START.md)
- [Open Source Components](OPEN_SOURCE.md)

## Companion Wear OS ⌚ {#wear-os-companion-}

FastMediaSorter include un'app Wear OS standalone completa e un companion per telefono progettati per il fattore di forma degli smartwatch.

- Sfoglia e riproduci cartelle e preferiti dal telefono associato, dall'archivio dello smartwatch stesso e dalle condivisioni SMB/FTP/SFTP che lo smartwatch raggiunge direttamente via Wi-Fi
- Le risorse cloud restano sul telefono - lo smartwatch non ha un proprio client cloud; un file cloud lo raggiunge solo quando lo invii dal telefono con "Invia a.."
- Sposta i file tra il telefono e lo smartwatch, trasmetti in diretta dallo smartwatch e usa piccoli strumenti integrati (calcolatrice, monitor di rete, mini-gioco) senza aprire l'app sul telefono
- Interfaccia e comportamento a runtime ottimizzati per display rotondi e compatti
- Portale web dedicato, guide di configurazione e risoluzione dei problemi per i flussi di lavoro dello smartwatch

Media, condivisioni di rete e trasferimento file sono nella versione completa dell'app per lo smartwatch (APK diretto). La versione Google Play è una piccola prima release - calcolatrice, cronometro, mini-gioco e impostazioni; il [portale Wear OS](wear/index.md) indica cosa include ciascuna versione.

Documentazione Wear OS:

- 🌟 **[Portale Web Wear OS](wear/index.md)** - Vetrina completa delle funzionalità, screenshot e download dagli store
- [Guida rapida Wear OS](WEAR_OS_QUICK_START.md) - Guida passo passo per l'associazione e la configurazione
- [Configurazione Wear OS](WEAR_OS_SETUP.md) - Architettura del modulo e configurazione del bridge companion
- [Sezione Wear OS in Features](FEATURES.md#16-settings--navigation)

## Istruzioni di build {#build-instructions}

### Requisiti

- Android Studio Hedgehog (2023.1.1) o più recente

- JDK 17+
- Android SDK 35
- Versione minima di Android: 8.0 (API 26) per Standard/Lite/Photos/VR/noLegal; 6.0 (API 23) per Legacy

### Compilazione

1. Clona il repository:

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Apri il progetto in Android Studio.
3. Attendi che la sincronizzazione di Gradle sia completata.
4. Esegui l'app su un emulatore o un dispositivo fisico.

### Comandi di build preferiti (Windows / PowerShell)

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### APK compilati 📦

Dopo ogni build riuscita, il file APK generato viene copiato automaticamente nella cartella `DOWNLOADS` nella radice del progetto con un timestamp. Lì puoi trovare tutta la cronologia delle tue build.

## Test 🧪 {#testing-}

FastMediaSorter v2 usa **Maestro** per i test end-to-end per garantire la qualità e l'affidabilità dell'app.

### Esecuzione rapida dei test

```bash
# Install Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# Or Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell as Administrator) - External: install.ps1 is the Maestro installer
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Run smoke tests (2-3 minutes)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# Or use shortcut
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**Nota**: NON usare `npm install -g maestro-cli` - è un pacchetto diverso e non correlato!

### Suite di test

- **Smoke Test** (`maestro/smoke/`): test delle funzionalità principali (~2-3 min)
  - Avvio dell'app e permessi
  - Navigazione tra i file locali
  - Riproduzione multimediale
  - Visualizzazione delle immagini

- **Critical Path Test** (`maestro/critical/`): operazioni essenziali (~1-2 min)
  - Operazioni sui file (copia, spostamento, eliminazione)
  - Persistenza delle impostazioni

### Documentazione

- 📚 [Guida rapida](../maestro/QUICK_START.md)
- 📝 [Scrivere i test](../maestro/WRITING_TESTS.md)
- 🔍 [Esempi di test](../maestro/EXAMPLES.md)
- 🔧 [Risoluzione dei problemi](../maestro/TROUBLESHOOTING.md)
- 📖 [Documentazione completa](../maestro/README.md)

### Integrazione CI/CD

I test vengono eseguiti automaticamente a ogni push tramite GitHub Actions. Vedi [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml).

## Primi passi (Guida rapida all'uso) 🚀 {#first-steps-quick-usage-guide-}

1. **Aggiungere una cartella (risorsa):**
    - Nella schermata principale, premi il pulsante con l'icona "Più" (+) per aggiungere una nuova risorsa.
    - Seleziona il tipo di risorsa (ad es. "Cartella locale").
    - Usa la scansione oppure aggiungi la cartella manualmente. Dopo l'aggiunta, comparirà nell'elenco nella schermata principale.

2. **Visualizzare i file:**
    - Tocca due volte (o tieni premuto) sulla risorsa aggiunta nell'elenco.
    - Si aprirà la schermata di navigazione, dove vedrai tutti i file multimediali di questa cartella come elenco o griglia.
    - Usa i pulsanti sul pannello superiore per ordinare, filtrare o cambiare visualizzazione.

3. **Riproduzione e ordinamento:**
    - Tocca qualsiasi file per aprirlo nel player a schermo intero.
    - Usa gli scorrimenti a sinistra/destra o le zone touch per navigare tra i file.
    - Per le operazioni (copia, spostamento), usa le zone touch corrispondenti o i pulsanti sul pannello di controllo.

4. **Configurare le cartelle di destinazione (Destinazioni):**
    - Nelle impostazioni, nella scheda "Destinazioni", puoi specificare fino a 30 cartelle da usare per l'ordinamento rapido.
    - In alternativa, attiva "È destinazione" nella schermata di modifica di qualsiasi risorsa per aggiungerla all'elenco di ordinamento rapido.
    - Dopo di che, sulla schermata del player compariranno i pulsanti per copiare o spostare rapidamente i file in queste cartelle.

## Stack tecnologico {#technology-stack}

- **Linguaggio**: Kotlin
- **Architettura**: Clean Architecture, MVVM
- **UI**: Android View System (XML), Material Design 3
- **Asincronia**: Kotlin Coroutines & Flow
- **DI**: Hilt (Dagger)
- **Database**: Room 2.7.0
- **Navigazione**: AndroidX Navigation Component
- **Media**: ExoPlayer (Media3 1.2.1)
- **Caricamento immagini**: Glide 5.0.9 con NetworkFileModelLoader personalizzato
- **Protocolli di rete**:
  - SMB: SMBJ 0.12.1 con BouncyCastle (transitiva)
  - SFTP: JSch 0.2.26 (fork com.github.mwiede, Ed25519 integrato)
  - FTP: Apache Commons Net 3.10.0
- **Cloud**: Google Drive API, OneDrive (MSAL), Dropbox API con OAuth 2.0
- **OCR e traduzione**:
  - Tesseract4Android (Tesseract 5.3.x) - estrazione del testo per caratteri latini e cirillici
  - Google ML Kit (Traduzione, identificazione della lingua) - traduzione del testo estratto
- **Ricerca e testi**: api.lyrics.ovh (API JSON)

## Versione della build

Formato della versione: `Y.YM.MDDH.Hmm` (ad es., `2.60.1102.207` per il 10/01/2026 20:07)

Vedi [dev/CHANGELOG.md](../dev/CHANGELOG.md) per le note di rilascio dettagliate.

---

## Contribuire 🤝

Le pull request sono benvenute. Per modifiche importanti, apri prima una issue per discutere cosa vorresti cambiare.

## Contatti 📧

- **Sviluppatore**: <sza@ukr.net>
- **Sito web**: [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues**: [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## Licenza 📄

Informazioni legali del progetto:

- [Termini di servizio](TERMS_OF_SERVICE.md)
- [Informativa sulla privacy](PRIVACY_POLICY.md)
- [Componenti open source](OPEN_SOURCE.md)

</div>
