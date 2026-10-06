---
layout: default
title: "❓ Domande frequenti (FAQ)"
permalink: /docs/FAQ-it.html
lang: it
---

<sub class="doc-stamp">26.10.06 14:51</sub>

<div lang="it" markdown="1">

<div lang="it" dir="ltr" markdown="1">

# ❓ Domande frequenti (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="it" %}

---

## Domande generali

### Cos'è FastMediaSorter?
FastMediaSorter combina riproduzione e gestione di file locali, di rete e cloud. Launcher, flussi e orologio dipendono dall'edizione. Servono autorizzazioni per i file e una scelta esplicita in Android per sostituire la schermata iniziale.

### È gratuito?
Sì! FastMediaSorter v2 è completamente gratuito e open source.

### Quale versione di Android mi serve?
Di seguito i minimi del codice attuale. L'uso immersivo VR/XR richiede anche visore/runtime compatibile; **noLegal non è limitata ai visori**. Una variante nel codice non garantisce un APK pubblicato.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK (EN)](TECHNICAL_REQUIREMENTS.html)

### Serve Internet?
I file locali non richiedono internet. SMB/SFTP/FTP nella LAN richiedono rete raggiungibile, non internet pubblico. Cloud e flussi internet richiedono internet; dipende dall'edizione.

### L'app ha dei widget?
Sì! FastMediaSorter v2 include una varietà di widget per la schermata Home - trovali tenendo premuto sulla schermata Home → Widget → FastMediaSorter. Includono scorciatoie alle risorse, avviatori di presentazioni e altro.

### L'app può sostituire la mia schermata Home?
In **Standard/noLegal**: **Impostazioni → Generali → Finestra di avvio principale → Schermata Home del dispositivo**, poi scegliere FastMediaSorter come Home Android. Il **Scrivania come finestra principale** non sostituisce il launcher di sistema.

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Come faccio a smettere di usare l'app come schermata Home?
Scegliere **Esci dalla modalità launcher**, un'altra finestra iniziale o un'altra app Home predefinita in Android. Il layout resta salvato; il percorso Android varia per dispositivo.

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Perché il mio tablet è tornato alla vecchia schermata Home dopo un riavvio?
Controllare Home predefinita e finestra iniziale. Scegliere **Sempre** se offerto. Firmware, aggiornamenti o ripristini possono cambiare la scelta; il solo riavvio non prova la causa. Segnalare modello e Android.

### Posso mettere le mie cartelle e playlist sul desktop?
Sì - è proprio a questo che serve il desktop. Tieni premuto su un quadrato vuoto e scegli **Aggiungi un elemento..**, poi scegli cosa vuoi: una delle tue cartelle, uno stream radio, un'app, una persona o un gadget come l'orologio o il meteo. La nuova cella finisce sul quadrato che hai premuto, e per una cartella scegli anche se si apre in modalità sfoglia, presentazione o riproduzione. Per spostare le cose in seguito, scegli **Modifica il desktop** dallo stesso menu a pressione prolungata. Vedi [HOW_TO-it](HOW_TO-it.html#how-to-use-the-app-as-your-home-screen) per la procedura completa.

---

## Operazioni sui file

### Dove vanno i file eliminati?
Con cestino attivo, i normali percorsi locali supportati usano `.trash/`. **Eliminazione permanente**, `content://`, SMB/SFTP/FTP/cloud e percorsi protetti `/Android/media/` non usano questa politica. Svuotare il cestino è irreversibile; non ogni cancellazione è recuperabile.

### Posso annullare un'eliminazione/spostamento?
Usare subito **Annulla**, solo se proposto. Dipende da operazione, schermata e percorsi. Eliminazioni permanenti o di rete/document-tree non si recuperano tramite cestino locale; trasferimenti di rete/cloud non sono sempre reversibili. Non sostituisce un backup.

### Qual è la differenza tra Copia e Sposta?
- **Copia:** crea un duplicato, l'originale resta al suo posto
- **Sposta:** ricolloca il file, lo rimuove dalla posizione originale

### Cos'è la modalità Tutti i file?
Tutti i file rimuove filtri multimediali **nelle risorse accessibili**, senza aggirare permessi Android. Formati non supportati si gestiscono o aprono esternamente; visualizzare APK/EXE/archivi non significa eseguirli o estrarli.

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

Usare un account NAS/Windows autorizzato e una condivisione SMB raggiungibile. Consentire TCP **445** solo nella rete privata fidata e sottorete necessaria; **non disattivare il firewall né esporre SMB a internet**. Controllare permessi, indirizzo, percorsi VPN e isolamento ospiti. Una VPN privata consente accesso remoto anche tramite rete mobile.

[Guida alla configurazione SMB](howto/scenario-smb-setup-it.html)

### Come mi collego a Google Drive?
1. Tocca **"+"** → **Cloud** → **Google Drive**
2. Tocca "Accedi con Google"
3. Concedi i permessi quando richiesto
4. Le tue cartelle di Drive appariranno

I file si aprono su richiesta, ma visualizzazione, miniature o riproduzione possono scaricare dati nella cache. Non è sincronizzazione automatica dell'intero Drive.

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
**Sì** - Fast Media Sorter for Windows pubblica le cartelle del PC scelte tramite SFTP e mostra un codice QR / una configurazione `.fmscfg`. Sul telefono, usa **Importa da companion** oppure **Scansiona codice QR** nella schermata Aggiungi risorsa. Vedi la guida lato PC: [Come pubblicare le cartelle del PC su Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Perché le miniature non si caricano per i file di rete?
Le miniature di rete si generano **su richiesta** per risparmiare banda. Scorri lentamente o attendi qualche secondo perché appaiano.

Se le miniature non si caricano affatto:
- Controlla che la connessione sia attiva: tocca la risorsa → se la cartella si apre, la connessione va bene
- Modifica la risorsa → assicurati che **"Carica miniature"** sia attivato
- Per connessioni molto lente: disattiva del tutto le miniature per evitare timeout (Modifica risorsa → disattiva miniature)

### La connessione continua a cadere / i file non si aprono a metà riproduzione
Controllare Wi-Fi, server, credenziali e percorsi VPN. Testare la velocità e ridurre miniature. La banda dipende da bitrate/codec, non solo risoluzione; **10 Mbps non è un requisito universale per 1080p**.

---

## Ordinamento rapido e destinazioni

### Cosa sono le cartelle "Ordinamento rapido"?
Le cartelle di Ordinamento rapido sono cartelle di destinazione preconfigurate per un ordinamento rapido dei file. Puoi assegnare fino a 10 cartelle con pulsanti numerati.

### Come configuro l'Ordinamento rapido?
**Metodo 1:** Impostazioni → Gestione → Destinazioni di ordinamento rapido, poi tocca **"Aggiungi a Ordinamento rapido"**  
**Metodo 2:** modifica qualsiasi cartella → attiva "Contrassegna per Ordinamento rapido"

### Come uso l'Ordinamento rapido mentre visualizzo i file?
Aprire il file e scegliere destinazione nel pannello comandi. Verificare **Copia** o **Sposta** prima di confermare. Le zone variano per media/modalità; l'angolo inferiore sinistro non copia sempre.

### Posso usare i tasti numerici invece di toccare?
Sì - collega una tastiera hardware, un gamepad o un telecomando TV e i tuoi pulsanti di Ordinamento rapido vengono numerati (0-9) automaticamente. Premi la cifra corrispondente per copiare o spostare il file in quella destinazione all'istante, esattamente come toccando il pulsante.

### I pulsanti di Ordinamento rapido non compaiono
Assicurati di aver aggiunto prima almeno una cartella di destinazione: Impostazioni → Gestione → Destinazioni di ordinamento rapido, poi **"Aggiungi a Ordinamento rapido"**. I pulsanti appaiono solo quando è configurata almeno una destinazione.

### Ho inviato accidentalmente un file nella cartella sbagliata
Usare **Annulla** se disponibile. Altrimenti verificare origine/destinazione e riportare il file manualmente. La copia conserva l'originale; non cancellare copie prima della verifica.

---

## Zone touch

### Cosa sono le "Zone touch"?
La mappa dipende da media/modalità: immagini possono usare 3×3, audio/video riservano controlli e pausa/ripresa al centro. Il pannello usa tre colonne; documenti usano scorrimenti senza zone di tocco. L'overlay mostra la mappa attiva.

### Come vedo le Zone touch?
Impostazioni → Player → **"Mostra sempre la sovrapposizione delle zone touch"**

### Posso disattivare le Zone touch?
Disattivare la griglia a nove zone e usare il pannello. **Non disattiva tutti i gesti**: restano navigazione/controlli in tre colonne e scorrimenti per documenti.

---

## Cattura schermo e voce

### Cos'è la striscia di gesti sul bordo sinistro?
È un menu di acquisizione rapida che apri con uno scorrimento diagonale dal bordo sinistro dello schermo. Attivalo in **Impostazioni → Gestione → Gesti sul bordo dello schermo → Overlay dei gesti**. Dal menu puoi fare uno screenshot, scattare una foto, ritagliare e condividere l'immagine corrente, aprire una scorciatoia a un'app o a un pannello, oppure avviare una registrazione schermo, video o voce - tutto senza uscire da ciò che stai guardando. Disponibile in Standard e XR/noLegal.

### Come registro rapidamente una nota vocale?
Tre modi: la voce **Registrazione vocale** nel menu overflow, il widget della schermata Home **Registratore rapido**, oppure l'azione **Avvia registrazione audio** del gesto sul bordo. Comunque tu la avvii, un controllo flottante **Stop** resta sullo schermo - anche sopra un'altra app - finché non lo tocchi per salvare.

---

## Input e controlli

### Supporta tastiere fisiche e gamepad?
Tastiera, mouse e gamepad dipendono da schermata/dispositivo. **F1** mostra associazioni su schermate supportate, senza garantire ogni tasto in ogni dialogo.

### Come rimappo i controlli / cambio le associazioni tasti?
**Impostazioni → Gestione → Controlli e tasti**: modificare azioni supportate. **Reset** ripristina i predefiniti, conflitti evidenziati. Consultare l'elenco attuale, non un numero fisso di 70.

### Come scarico un file multimediale da un URL?
Condividere un URL `http(s)` supportato in Android. Un file diretto non è una pagina, un video protetto o DRM. Download e destinazioni scrivibili dipendono da edizione, URL e permessi.

---

## Prestazioni e archiviazione

### Come trovo un file specifico per nome?
Usa il pannello **Filtro** in Sfoglia: tocca l'icona del filtro nella barra degli strumenti, digita qualsiasi parte del nome del file nel campo nome - l'elenco si aggiorna all'istante. Non serve una barra di ricerca separata; il filtro copre completamente questo scenario.

### Perché l'app è lenta con 5000+ file?
Cartelle grandi richiedono elenco, metadati e miniature. Limitare risorse, filtrare e ridurre miniature. Ordinare per data **non garantisce** di evitare una scansione completa; prestazioni dipendono da fonte/formati.

### L'app si arresta in modo anomalo o si blocca
Riaprire l'app e controllare spazio, permessi e connessione. Svuotare cache aiuta con miniature obsolete, non ogni crash. Segnalare versione/edizione, Android, risorsa e passi; oscurare credenziali/percorsi privati nei log.

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
Il **PIN della risorsa** limita l'accesso nell'app, ma **non cifra i file** né blocca altre app o utenti autorizzati del server. Per protezione esterna usare cifratura del dispositivo/archivio.

### I miei dati vengono raccolti?
L'app non invia statistiche all'autore automaticamente. Sono locali fino a esportazione/invio. Cloud, flussi e meteo contattano fornitori scelti con richieste necessarie. Consultare l'informativa privacy.

[Informativa privacy (EN)](PRIVACY_POLICY.html)

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

Dipende da edizione/dispositivo. Traduzione ML Kit solo su telefoni, tablet, Chromebook e desktop, non TV, auto, orologi o XR. OCR separato: API 26+, almeno 3 GB RAM e dispositivo non low-RAM.

### Cosa fa la lingua di origine "Auto"?
"Auto" legge il testo con il modello inglese e poi determina la lingua di ciò che è stato letto ai fini della traduzione. Per il testo cirillico, scegli esplicitamente la lingua di origine (ad esempio **Russo** o **Ucraino**) - altrimenti le lettere vengono lette come i loro equivalenti visivi latini.

### Funziona offline?
**Sì.** Ti serve Internet solo una volta per scaricare il modello di testo per la tua lingua di origine e il modello di traduzione per la tua coppia di lingue.

### Perché la traduzione a volte è più lenta?
Il primo uso di una lingua carica il suo modello di testo, e le immagini grandi o dettagliate richiedono più tempo per essere lette. Le esecuzioni successive nella stessa lingua partono più velocemente.

### Cos'è la modalità di traduzione in stile lente?
La sovrapposizione mostra blocchi tradotti sull’immagine; modalità standard con testo separato. Su dispositivi supportati: **Impostazioni → Media → Traduzione, digitalizzazione (OCR) → Risultato della traduzione in blocchi**.

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
Standard, noLegal, Legacy, VR, XR e FOSS supportano audio persistente in background. Lite: audio locale senza servizio persistente; Photos: niente audio. Rete/cloud dipendono anche dall'edizione.

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Stream Internet

### FastMediaSorter riproduce la radio Internet?
**Flussi** supporta radio HTTP(S)/ICY, HLS/DASH e RTSP secondo fonte/codec. Disponibile in **Standard, noLegal, Legacy, VR, XR**, non **Lite, Photos, FOSS**. Un URL non supera incompatibilità o DRM.

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Come apro la schermata Streams?
Tocca **Streams** nel menu a tendina della finestra principale (visibile quando Streams è attivo). Puoi raggiungerla anche tramite **Impostazioni > Media > Streams**, dove si trova l'interruttore principale.

### Come aggiungo una stazione radio?
Nella schermata Streams, tocca **⋮** in fondo alla barra degli strumenti, scegli **Aggiungi stream** e incolla l'URL della stazione. Tocca Salva. La stazione appare subito nell'elenco.

### Posso importare una playlist?
Sì - tocca **⋮ > Importa da URL** e inserisci un indirizzo `.m3u` remoto. Lo stesso menu contiene **Aggiorna catalogo FastMediaSorter** per l'elenco curato (con chip per argomento e lingua), disponibile anche da **Impostazioni > Estensioni** o dalla schermata di onboarding di Benvenuto.

### Uno stream non si riproduce - cosa faccio?
Se uno stream fallisce, appare una finestra con le opzioni **Riprova**, **Rimuovi** e **Annulla**. I reindirizzamenti 301 tra protocolli diversi vengono gestiti automaticamente. Se l'host è morto o molto lento, l'importazione del catalogo va in timeout rapidamente invece di bloccarsi.

### La radio continua a riprodursi quando lascio la schermata Streams?
Con audio persistente supportato e attivo, l’uscita segue Ferma / Continua / Chiedi. Senza, l’audio si ferma quando la schermata lascia il primo piano. Controllare Impostazioni → Lettore.

### Posso vedere miniature live per gli stream?
Passa l'interruttore della barra degli strumenti Streams alla vista **Griglia** - ogni canale appare come un riquadro con l'ultimo fotogramma catturato, così puoi capire al volo cosa sta andando in onda. Il riquadro resta visibile anche dopo aver chiuso e riaperto l'app, e si aggiorna con una nuova cattura quando lo stream torna in diretta.

### Posso trasmettere uno stream sulla mia TV?
Sì, per gli stream video - tocca **Cast** nel player e scegli un Chromecast sulla stessa rete Wi-Fi. Gli stream RTSP non possono essere trasmessi; il pulsante appare solo per i formati che il ricevitore Chromecast supporta.

---

## Wear OS

### FastMediaSorter funziona su smartwatch Wear OS?
L'**app Wear OS** separata richiede almeno API 28; APK sull'orologio. Collegamento telefono: **Standard/noLegal**, identità/firme compatibili. Il **quadrante WFF v4** separato richiede Wear OS 6 / API 36.

[Matrice delle funzioni (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Cosa posso fare sullo smartwatch?
Il codice Wear attuale include media locali/rete, trasferimenti telefono, flussi, registrazione e strumenti. Sincronizza impostazioni/risorse compatibili, non tutte quelle del telefono. Nessun client cloud autonomo. **Le versioni pubblicate possono avere meno funzioni del codice**; verificare descrizione download/release.

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
Le regole eseguono Copia, Sposta o Elimina supportati su risorse accessibili. Servono permessi, credenziali e disponibilità. Provare prima con **Copia**; eliminazione pianificata non automaticamente reversibile.

### Dove configuro le Operazioni pianificate?
Impostazioni → **Gestione** → **Operazioni pianificate per programma**. Tocca **"+"** per aggiungere una nuova regola.

### Verrà eseguita se la mia app è chiusa?
WorkManager può operare dopo l'uscita, senza garanzia dopo **Arresto forzato** Android, dispositivo spento o condizioni/permessi mancanti. Riaprire e verificare regola/log.

### Perché un'operazione pianificata non è partita all'orario esatto?
WorkManager non è una sveglia esatta. Batteria, rete e dispositivo possono ritardare oltre pochi minuti. Intervallo minimo: **15 minuti**; esenzione batteria non garantisce l'ora precisa.

### L'operazione pianificata è stata eseguita ma ha copiato 0 file
Verificare log: zero copie può significare esistenti saltati, nessun risultato, risorsa irraggiungibile o permessi negati. Controllare origine, filtri, destinazione, credenziali; zero non sempre significa successo.

### Posso vedere cosa è stato elaborato?
**Sì.** Tocca **"Visualizza log"** nella sezione Operazioni pianificate per vedere una cronologia con timestamp di ogni esecuzione, inclusi i risultati per singolo file.

---

## Blocco meteo

### Da dove arriva il meteo?
Il blocco meteo del desktop usa **Open-Meteo.com** - un servizio meteo gratuito e senza chiave API. Dati meteo forniti da Open-Meteo.com (CC-BY 4.0).

### L'app traccia la mia posizione?
Il **blocco meteo** usa il luogo inserito senza tracciamento GPS e lo invia al servizio meteo. La geolocalizzazione foto opzionale è distinta e richiede permesso posizione.

---
## Hai ancora domande?

Non hai trovato una risposta sopra, o qualcosa non funziona come descritto? **Contattaci** - ogni messaggio viene letto e la maggior parte dei problemi viene risolta.

- 📖 **Guide How-To** (attività passo passo): [HOW_TO-it.md](HOW_TO-it.html)
- 🚀 **Guida rapida:** [QUICK_START-it.md](QUICK_START-it.html)
- 🔧 **Risoluzione dei problemi:** [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.html)
- 📧 **Email:** [sza@ukr.net](mailto:sza@ukr.net) - per qualsiasi cosa: aiuto per la configurazione, descrizioni di bug, richieste di funzionalità
- 🌐 **Pagina dell'autore:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Segnalazione bug:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferito per bug riproducibili; includi la versione di Android e cosa stavi facendo
- 📖 **Documentazione completa:** [Portale della documentazione](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Vuoi una funzionalità che ancora non c'è?** Scrivici - molte funzionalità dell'app sono state aggiunte perché qualcuno le ha chieste. Se ha senso per il caso d'uso, viene realizzata.

</div>

</div>
