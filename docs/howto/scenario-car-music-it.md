---
layout: default
title: "Lettore musicale per auto (autoradio Android) - FastMediaSorter v2"
permalink: /docs/howto/scenario-car-music-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🚗 Lettore musicale per auto (autoradio Android)

> **Livello:** Principiante &bull; **Durata:** ~10 minuti &bull; **Edizione:** Standard, Legacy, VR, noLegal (Lite riproduce audio locale ma non ha riproduzione in background né Stream; Photos non ha audio)

{% include lang-switcher.html doc="scenario-car-music" dir="/docs/howto/" current="it" %}

FastMediaSorter funziona benissimo come lettore musicale per auto su autoradio Android - accesso immediato all'intera raccolta musicale su scheda SD o chiavetta USB, con supporto integrato ai pulsanti del volante.

> **Cos'è un'autoradio Android?** È uno stereo per auto con schermo touch che esegue Android - come il tuo telefono, ma installato nel cruscotto. Questa guida funziona anche su un normale telefono o tablet montato in auto.

---

## Cosa ti serve

- Autoradio Android / telefono / tablet in auto
- File musicali su **scheda SD**, **chiavetta USB** o **memoria interna** (MP3, FLAC, AAC, OGG e altri)
- (Facoltativo) Pulsanti multimediali sul volante

---

## Passo 1 - Aggiungi la tua cartella musicale

1. Apri l'app
2. Tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti in alto
3. Seleziona **"Cartella locale"**
4. Vai dove è archiviata la tua musica:
   - **Scheda SD:** cerca una cartella chiamata `/storage/` - al suo interno troverai una cartella con un codice tipo `1234-5678`, e la musica di solito si trova in `/storage/1234-5678/Music`
   - **Memoria interna:** prova `/sdcard/Music` o `/sdcard/Download`
   - **Chiavetta USB:** cerca in `/storage/usb0/` o `/storage/usbdisk/`
5. Seleziona la cartella → tocca **Seleziona**

> **Non trovi la tua musica?** Prova a toccare **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Cartella locale"** e poi cerca una cartella chiamata `Music` ovunque nell'elenco. Sulla maggior parte dei dispositivi è proprio lì.


---

## Passo 2 - Configura la cartella per la musica

Tieni premuta la cartella musicale sulla schermata principale → tocca **Modifica (icona matita)**.

Si aprono le impostazioni della cartella. Imposta queste opzioni:

| Impostazione | Valore | Perché |
|---------|-------|-----|
| **Profilo** | Libreria audio | Dice all'app "questa è una cartella musicale" - configura automaticamente tutto per l'audio |
| **Tipi supportati** | Solo audio | Nasconde foto e video, così vengono mostrati solo i brani musicali |
| **Modalità ordinamento** | Titolo (A→Z) o Artista | Mantiene i brani in un ordine logico |
| **Includi sottocartelle** | ATTIVO | Se la tua musica è organizzata in sottocartelle per artista/album, questa opzione trova tutti i brani |

Tocca **Salva**.

> **Cosa fa il "Profilo"?** È una preimpostazione a un tocco che configura la cartella in modo ottimale per il suo scopo. Scegliendo "Libreria audio" l'app mostra le copertine degli album, ordina correttamente per la musica e nasconde automaticamente i file non audio.


---

## Passo 3 - Apri la cartella e inizia la riproduzione

1. Tocca la tua cartella musicale sulla schermata principale
2. Tutti i brani appaiono in un elenco con miniature delle copertine
3. Tocca **un brano qualsiasi** per iniziare la riproduzione

Si apre il **player audio** a schermo intero con copertina, barra di avanzamento e controlli di riproduzione.

![Player audio a schermo intero con copertina (Camel - Dust and Dreams)](screenshots/screenshot-car-step3.png)

---

## Passo 4 - Assicurati che la musica continui a suonare

Questo passaggio garantisce che la musica continui a suonare quando lo schermo si spegne, cambi app o ricevi la notifica di una chiamata:

1. Vai su **Impostazioni → scheda Multimediali**
2. Scorri fino alla sezione **Audio**
3. Assicurati che **"Supporto audio"** sia ATTIVO

Tutto qui. Una volta attivato, l'app si registra come un vero e proprio lettore musicale - i controlli sulla schermata di blocco e il player nella barra delle notifiche compaiono automaticamente.

![Impostazioni → Multimediali → sezione Audio con le opzioni di riproduzione in background](screenshots/screenshot-car-step4.png)

---

## Passo 5 - Prova i pulsanti del volante

Premi **Avanti** o **Indietro** sul volante.

**Funzionano automaticamente - non serve alcuna configurazione.** FastMediaSorter risponde a tutti i pulsanti multimediali Android standard.

> **I pulsanti non funzionano?** Alcune autoradio più vecchie inviano segnali non standard. Prova ad andare su **Impostazioni Android → Accessibilità** e cerca un'opzione "ricevitore pulsanti multimediali". Se non aiuta, usa invece le zone touch sullo schermo (bordo sinistro/destro dello schermo) - funzionano perfettamente.


---

## Passo 6 - (Facoltativo) Usa "Tutta la musica" - un solo posto per tutti i tuoi brani

Se la tua musica è distribuita su più cartelle (ad es. alcune sulla scheda SD, altre nella memoria interna), la risorsa virtuale **Tutta la musica** raccoglie automaticamente tutto in un unico posto:

1. Sulla schermata principale, cerca la card **"Tutta la musica"** - di solito viene creata automaticamente se hai file audio locali
2. Se non c'è: tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → scorri fino a **Risorse virtuali** → tocca **"Tutta la musica"**

Ora tutti i tuoi brani da tutte le posizioni compaiono insieme in un unico elenco.

![Schermata principale di FastMediaSorter - card della risorsa virtuale Tutta la musica visibile](screenshots/screenshot-car-step6.png)

---

## Passo 7 - (Facoltativo) Scorciatoia sulla schermata Home per l'avvio a un tocco

Perfetto per quando vuoi solo salire in auto e toccare un pulsante per avviare la musica:

1. Tieni premuto uno spazio vuoto sulla schermata Home → tocca **Widget**
2. Trova **FastMediaSorter** nell'elenco → trascina il widget **"Scorciatoia risorsa"** sulla schermata Home
3. Quando richiesto, seleziona la tua risorsa musicale
4. Fatto - tocca il widget in qualsiasi momento e la musica parte immediatamente

---

## Passo 8 - (Facoltativo) Aggiungi stazioni radio internet

Se la tua autoradio ha una connessione dati mobile o Wi-Fi attiva, puoi aggiungere stazioni radio internet direttamente - senza bisogno di app aggiuntive:

1. Apri il menu principale (hamburger o menu a tendina) → tocca **Stream**
2. Tocca **Aggiungi (+)** → incolla un qualsiasi URL di radio internet (http/https, .m3u8, RTSP) e tocca Salva
3. Oppure tocca **Importa catalogo** per sfogliare l'elenco integrato di stazioni curate e aggiungerle per genere o lingua
4. Tocca una riga di stazione per avviare la riproduzione audio inline - il nome della stazione e il brano attualmente in riproduzione compaiono nel mini-controllo in basso
5. L'elenco resta visibile, così puoi cambiare stazione senza lasciare la schermata

> **Audio in background:** per mantenere la radio in riproduzione quando cambi app, vai su **Impostazioni → Player → Riproduzione audio in background** e attivala.

Nota: Stream richiede una connessione di rete, e la schermata Stream è assente nelle edizioni Lite e Photos.

---

## Fatto! Controlli del player

Mentre la musica è in riproduzione, lo schermo è il tuo pannello di controllo:

- **20% sinistro dello schermo** → Brano precedente
- **20% destro dello schermo** → Brano successivo
- **60% centrale** → Pausa / Riproduci / Menu comandi

![Player audio in esecuzione in background - pannello comandi visibile](screenshots/screenshot-car-done.png)

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| I pulsanti del volante non funzionano | Verifica che la tua autoradio invii eventi standard dei tasti multimediali Android. Alcune unità richiedono di abilitare "ricevitore pulsanti multimediali" in Impostazioni Android → Accessibilità |
| La musica si ferma quando lo schermo si blocca | Attiva **"Impedisci lo spegnimento"** in Impostazioni → Generale, oppure usa i controlli della notifica audio per riprendere. Verifica anche che il Supporto audio sia ATTIVO (Passo 4) |
| Nessuna copertina mostrata | Attiva **"Scarica copertine audio online"** in Impostazioni → Multimediali → Audio (richiede Wi-Fi). Per le copertine offline, l'app legge automaticamente la copertina incorporata nel file MP3/FLAC |
| Non trovo la musica sulla scheda SD | Alcune versioni di Android limitano l'accesso alla scheda SD. Prova ad aggiungere il percorso della scheda SD usando il pulsante **"Sfoglia.."** nel selettore di cartelle, che usa il selettore file di sistema di Android con accesso completo alla scheda SD |
| L'audio ha scatti o salti | Chiudi le altre app in esecuzione in background. Per i file FLAC, assicurati che l'autoradio abbia potenza di elaborazione sufficiente |
| La radio internet si ferma quando cambio app | Vai su Impostazioni → Player → Riproduzione audio in background e assicurati che sia attivata |

</div>
