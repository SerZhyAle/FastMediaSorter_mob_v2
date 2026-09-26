---
layout: default
title: "🚀 Guida rapida - FastMediaSorter v2"
permalink: /docs/QUICK_START-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🚀 Guida rapida - FastMediaSorter v2

*Inizia in 5 minuti! Guida semplice per principianti.*

{% include lang-switcher.html doc="QUICK_START" dir="/docs/" current="it" %}

---

## Scegli la tua versione 📱

FastMediaSorter v2 viene distribuito in **cinque versioni per telefoni e tablet di uso quotidiano** - Standard, Lite, Photos, Legacy, FOSS - più **due build per visori e sideload**, VR e noLegal. Sette in totale; la griglia esatta delle funzionalità è generata dalla build in [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md). Scegli quella più adatta alle tue esigenze:

| Versione | Ideale per | Funzionalità principali |
|--------|----------|--------------|
| **Standard** ⭐ | Tutti | Funzionalità complete: video, foto, audio, documenti, archiviazione cloud, traduzione + radio Internet / Streams |
| **Lite** | Download più leggeri | Foto, video e file audio locali; nessun cloud, nessuna cartella di rete, nessuno Stream Internet, nessun documento o traduzione, e l'audio si interrompe quando l'app lascia il primo piano |
| **Photos** | Appassionati di foto | Solo immagini, con archiviazione cloud e cartelle di rete; nessun video, nessun audio, nessuno Streams |
| **Legacy** | Android meno recente (API 23+) | Media completi + SMB/FTP/SFTP e cloud; realizzata per Android meno recente (API 23+) |
| **FOSS** | Utenti F-Droid | Video, foto, audio, documenti ed EPUB dal dispositivo e da SMB/FTP/SFTP; nessun SDK proprietario, quindi nessun cloud, nessuno Streams, nessun OCR e nessuna traduzione |
| **VR / noLegal** | Visore XR / sideload | VR è la build per visori pulita per gli store; noLegal è la build per sideload che aggiunge il player immersivo ed extra riservati al sideload |

**👉 La maggior parte degli utenti dovrebbe scaricare la versione "Standard" per l'esperienza completa.**

---

## Primo avvio: scegli il profilo del dispositivo (30 secondi) 🧭 {#first-launch-choose-your-device-profile-30-seconds-}

Il percorso di benvenuto si apre dichiarando cos'è l'app, come **ruoli** piuttosto che come un campionario di funzionalità: nelle build che includono il launcher, una **schermata Home** che gestisce l'intero dispositivo e un invito a renderla tua; un **file manager**; un **player** di foto, video, musica, GIF, documenti e testo; un **lettore** di fonti sul dispositivo, in rete e nel cloud; **stream** dove la build li include; **ordinamento con un tocco**; e, su una build con il companion per lo smartwatch, l'**app per lo smartwatch** stessa. Ogni riquadro indica i protocolli e i servizi concreti a cui si riferisce - SMB, FTP, SFTP, Google Drive, OneDrive, Dropbox - ed elenca solo ciò che la tua build può effettivamente aprire, così quello che leggi lì è vero per la versione che hai tra le mani.

Al primissimo avvio, subito sotto il selettore della lingua, la schermata di benvenuto ti chiede **come userai questo dispositivo**. Scegli un profilo e l'app parte con impostazioni predefinite sensate per quello stile - layout, miniature, schermo intero, schermo sempre acceso, audio in background, conferme di eliminazione/spostamento, la vista di lettura, i download dai link, i controlli del player, la vista iniziale degli stream e, dove il launcher è disponibile, il desktop stesso. È un preset di partenza una tantum, non un blocco - puoi cambiare qualsiasi cosa in seguito.

- 🎯 **Badge Consigliato:** l'app indovina un profilo probabile per il tuo dispositivo e lo contrassegna come **(Consigliato)**. Se ha indovinato giusto, non devi fare quasi nulla.
- ⏭️ **Salta:** hai fretta? **Salta** applica semplicemente il profilo consigliato. Nessuna risposta sbagliata qui.
- ⚙️ **Cambialo più tardi:** **Impostazioni → Generali → Profilo del dispositivo**. Passare da lì mostra un rapido **avviso** che indica esattamente quante impostazioni il nuovo profilo sovrascriverà, e chiede conferma. Nulla cambia finché non dici sì. Un profilo che non sovrascriverebbe nulla viene applicato senza chiedere.
- 🧩 **Altro / Personalizzato:** mantiene le tue impostazioni attuali esattamente come sono. Nessun preset applicato - utile se ti piace regolare le cose a mano.
- ⬆️ **Aggiorni da una versione precedente?** Il tuo profilo appare come Altro e le tue impostazioni precedenti vengono mantenute intatte - nessun preset è stato applicato automaticamente. Ne vuoi comunque uno? Scegli un profilo nelle Impostazioni.
- 🎚️ **Poi - scegli cosa fa l'app:** dopo il profilo, una schermata rapida ti permette di attivare o disattivare funzionalità (file manager, audio, video, documenti, riconoscimento del testo, traduzione). Le parti opzionali si scaricano lì stesso e si attivano non appena finiscono - tutto modificabile in seguito nelle Impostazioni.

**Gli 11 profili:**

- 📱 **Smartphone personale** - layout touch, sincronizzazione in background, conferme di sicurezza predefinite
- 🖥️ **Tablet e modalità desktop** - tablet, Chromebook, Samsung DeX; layout a griglia, miniature grandi, navigazione multi-finestra
- 📺 **TV / media box** - navigazione con D-pad/telecomando, pulsanti grandi, nessun controllo touch minuscolo
- 🚗 **Autoradio/unità centrale auto** - pulsanti extra-large, schermo sempre acceso, riproduzione prioritaria sulle operazioni sui file
- 🎬 **Media player** - foto, video, musica con presentazione e ripresa della riproduzione
- 🖼️ **Cornice digitale** - si apre direttamente in una presentazione; le operazioni sui file passano in secondo piano
- 🎞️ **Video player** - riproduzione video rapida e miniature
- 🎵 **Audio player** - audio in background e schermata di riproduzione in corso
- 📚 **Lettore di e-book** - lettura di PDF, EPUB e testo
- 🥽 **Visore VR** - layout 3D/360° e controlli immersivi sull'hardware supportato
- 🧩 **Altro / Personalizzato** - mantiene le impostazioni predefinite invariate, nessun preset applicato

---

## Passo 1: aggiungi la tua prima cartella (30 secondi)

1. **Apri l'app** - vedrai la schermata principale
2. **Tocca il pulsante "+"** (angolo in basso a destra)
3. **Seleziona "Cartella locale"** (o Rete/Cloud)
4. **Scegli una cartella**
5. **Fatto!** La cartella appare nel tuo elenco

> 💡 **Suggerimento:** per le unità di rete (SMB), usa il nuovo pulsante **"Scansiona rete"** per trovare i dispositivi automaticamente!

---

## Passo 2: sfoglia i tuoi file (30 secondi)

1. **Tocca la cartella** che hai appena aggiunto (oppure tieni premuto)
2. Vedrai **tutti i tuoi file multimediali** come miniature
3. **Cambia visualizzazione:**
   - Icona griglia = vedi le miniature
   - Icona elenco = vedi i dettagli dei file

> 💡 **Suggerimento:** usa il **pulsante filtro** (icona a imbuto) per mostrare solo foto, video o persino **documenti Testo/PDF**.

---

## Passo 3: visualizza e naviga (1 minuto)

1. **Tocca qualsiasi foto/video** per aprire il visualizzatore a schermo intero
2. **Scorri a sinistra** = file successivo
3. **Scorri a destra** = file precedente
4. **Pizzica per zoomare** sulle foto
5. **Tocca lo schermo** per mostrare/nascondere i controlli

### Zone touch (aree dello schermo)

Lo schermo è diviso in 9 aree per azioni rapide:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legenda:**

1. Indietro | 2. Copia | 3. Rinomina
2. Prec | 5. Sposta | 6. Succ
3. Comandi | 8. Elim | 9. Play

> 💡 **Attiva la sovrapposizione:** Impostazioni → Player → "Mostra sempre la sovrapposizione delle zone touch"

---

## Passo 4: ordina i file rapidamente (2 minuti)

**Configura le cartelle di ordinamento rapido:**

1. Vai su **Impostazioni** → **Gestione** → **Destinazioni di ordinamento rapido**
2. Tocca **"Aggiungi a Ordinamento rapido"**
3. Seleziona le cartelle in cui vuoi ordinare i file (ad es. "Foto migliori", "Vacanze")
4. **Ogni cartella riceve un numero** (0-9) e un colore

**Ora puoi ordinare i file all'istante:**

1. Mentre visualizzi una foto, **tocca l'angolo in basso a sinistra** (zona COPIA)
2. **OPPURE tocca il pulsante "1"** sul pannello dei comandi
3. Il file viene copiato nella tua cartella di Ordinamento rapido numero 1!

---

## Passo 5: funzionalità avanzate (opzionale)

### Unità di rete (SMB / SFTP / FTP)

Tocca **"+"** → **Rete** → **SMB** oppure **SFTP / FTP**

- **SMB:** usa **"Scansiona rete"** per scoprire automaticamente il tuo NAS e i dispositivi di rete. L'app cercherà nella tua rete locale e mostrerà le condivisioni SMB disponibili.
- **SFTP/FTP:** inserisci manualmente IP host, utente e password.

### Archiviazione cloud

Tocca **"+"** → **Archiviazione cloud**

- Supporta: **Google Drive**, **OneDrive**, **Dropbox**
- Tocca "Accedi..", concedi i permessi e scegli le cartelle.

### Sistema dei preferiti

- **Tocca l'icona a stella** mentre visualizzi qualsiasi file
- Accedi a tutti i preferiti: menu principale → scheda **Preferiti**
- Funziona su tutte le cartelle!

### Traduzione automatica

- Attivala in **Impostazioni** → **Media** → **Altro**
- Tocca il pulsante **Traduci** (A→文) mentre visualizzi immagini/PDF/testo
- **Modalità in stile lente:** attiva "Overlay in stile lente" nelle impostazioni per traduzioni sul posto simili a Google Lens

### E-book EPUB

- Attivali in **Impostazioni** → **Media** → **Documenti**
- Aggiungi una cartella con file .epub → i file mostreranno il badge "E"
- **Navigazione tra i capitoli:** scorri a sinistra/destra oppure usa i pulsanti prec/succ
- **Indice:** tocca il pulsante 📋 per l'elenco dei capitoli
- **Dimensione del carattere:** usa i pulsanti -A/+A (intervallo 6-144px)
- **Ricerca:** tocca il pulsante 🔍 per trovare testo nel libro
- **Tema chiaro/scuro:** si adatta automaticamente al tema dell'app

### Musica di sottofondo per la presentazione

- Aggiungi una cartella con file audio come risorsa
- Vai su **Impostazioni** → **Media** → **Immagini** → **"Riproduci musica durante la presentazione"**
- Seleziona la tua risorsa musicale dal menu a tendina
- Avvia qualsiasi presentazione - la musica parte automaticamente!
- **Suggerimento:** tocca il nome della traccia durante la presentazione per passare a un'altra traccia casuale

### Rimappatore delle associazioni tasti

- Impostazioni → **Gestione** → **Controlli e associazioni tasti** - riassegna qualsiasi controllo a un tasto, pulsante o input del gamepad diverso
- 70 preset integrati; tocca **Ripristina** per ripristinarli
- Premi **F1** su qualsiasi schermata per vedere le associazioni tasti attive per quella superficie

### Acquisizione dalla fotocamera

- In Sfoglia, tocca il **pulsante fotocamera** nella barra degli strumenti per aprire la fotocamera integrata e salvare direttamente nella risorsa corrente - locale o di rete
- **Zoom:** tocca un preset (0.5x/1x/2x..) oppure trascina il cursore per un livello preciso
- **Scenario di scatto:** un pulsante offre normale, notte, ritratto, selfie, macro, sport e documento - solo quelli che il tuo dispositivo può effettivamente offrire
- **Tocca per mettere a fuoco:** tocca in qualsiasi punto del mirino per mettere a fuoco e impostare l'esposizione lì
- **Foto o video:** cambia modalità direttamente sulla schermata della fotocamera prima di scattare

### Cattura schermo e gesti sul bordo

- Attiva **Impostazioni → Gestione → Gesti sul bordo dello schermo → Overlay dei gesti**, poi scorri dal bordo sinistro per fare uno screenshot, scattare una foto o avviare una registrazione schermo/voce/video - vedi lo scenario dei gesti sul bordo in [HOW_TO-it.md](HOW_TO-it.md) per i dettagli

### Download automatico dai link

- In qualsiasi browser o app di messaggistica, condividi un link `http(s)` con FastMediaSorter tramite il foglio di condivisione Android
- L'app scarica il file multimediale e lo salva automaticamente nella risorsa selezionata

### Stream Internet (radio e IPTV)

- Apri **Streams** dal menu a tendina della finestra principale (o **Impostazioni > Media > Streams** quando compare per la prima volta).
- Tocca **⋮** in fondo alla barra degli strumenti: **Aggiungi stream** per un URL radio (http/https mp3/aac, HLS .m3u8, RTSP), oppure **Importa da URL** per una playlist `.m3u`.
- I pulsanti **Video**, **Audio** e **Personali** accanto al campo di ricerca dividono l'elenco: Video e Audio mostrano i canali del catalogo e quelli importati di quel tipo, Personali mostra ogni canale che hai aggiunto tramite URL. Tocca di nuovo il pulsante acceso per mostrare tutti i canali.
- Scarica il catalogo curato delle stazioni FastMediaSorter dalla schermata **Estensioni** per una libreria ricercabile e filtrabile con chip per argomento e lingua.
- La radio viene riprodotta inline tramite la barra fissa in basso - l'elenco delle stazioni resta visibile. Gli stream Video/RTSP si aprono nel player a schermo intero; Indietro torna all'elenco.
- **Disponibile in Standard, Legacy, noLegal e VR. La schermata Streams è assente in Lite e Photos - nessun protocollo funziona lì, perché non c'è alcun punto di ingresso.**

### Widget per la schermata Home

FastMediaSorter v2 include una varietà di widget per la schermata Home. Tieni premuto sulla schermata Home → Widget → FastMediaSorter per sfogliarli. I più importanti includono:

- **Scorciatoia a una risorsa** - tocca per aprire istantaneamente una qualsiasi delle tue risorse
- **Continua a leggere** - avvia l'app direttamente in modalità presentazione

---

## Domande comuni

**D: dove vanno i file eliminati?**  
R: nella cartella `.trash/` nella stessa posizione. Usa "Svuota cestino" per eliminarli in modo permanente.

**D: posso annullare un'operazione di spostamento?**  
R: sì! Tocca il pulsante "Annulla" (o la zona in basso a destra) entro 5 secondi.

**D: come modifico le foto?**  
R: apri una foto → tocca il pulsante "Modifica" → ruota, capovolgi, filtri, regola la luminosità.

---

## Serve aiuto?

- 📖 **Documentazione completa:** [README-it.md](README-it.md)
- ❓ **FAQ:** [FAQ-it.md](FAQ-it.md)
- 🔧 **Risoluzione dei problemi:** [TROUBLESHOOTING-it.md](TROUBLESHOOTING-it.md)
- 🐛 **Segnala un problema:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

**Prossimi passi:** esplora le Impostazioni per personalizzare la velocità della presentazione, la dimensione delle miniature e le scorciatoie da tastiera!

</div>
