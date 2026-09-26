---
layout: default
title: "Home Cinema e streaming VR - FastMediaSorter v2"
permalink: /docs/howto/scenario-home-cinema-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🍿 Home Cinema e streaming VR

> **Livello:** Principiante &bull; **Durata:** ~15 minuti &bull; **Edizione:** Standard, Legacy, VR, noLegal (Lite non ha fonti di rete, Photos non ha video)

{% include lang-switcher.html doc="scenario-home-cinema" dir="/docs/howto/" current="it" %}

Guarda la tua raccolta di serie direttamente dal PC di casa - sul telefono, sul tablet o su un visore VR basato su Android (Meta Quest, Pico). Senza copiare file. Senza cavi USB. Basta premere play.

> **Come funziona?** Il telefono e il PC sono sulla stessa Wi-Fi di casa. L'app si collega alla cartella condivisa del PC e trasmette il video direttamente - proprio come Netflix trasmette dai propri server, ma usando la tua rete di casa. Il file video non viene mai scaricato sul telefono; viene riprodotto al volo.

---

## Cosa ti serve

- Telefono / tablet / visore VR sulla stessa **rete Wi-Fi di casa** del tuo PC
- Video sul tuo **PC o NAS** (il box del router con archiviazione)
- FastMediaSorter installato

---

## Passo 1 - Condividi la tua cartella video sul PC

Per prima cosa, rendi la cartella video raggiungibile sulla tua rete di casa.

Su **Windows:**
1. Apri **Esplora file**, vai alla tua cartella video (ad es. `D:\Serie`)
2. **Tasto destro** sulla cartella → **Proprietà** → scheda **Condivisione** → clic su **Condividi..**
3. Nel menu a tendina scegli **Everyone** (o il tuo nome utente) → clic su **Aggiungi** → clic su **Condividi**
4. Annota l'indirizzo IP del tuo PC - ti servirà al Passo 2

> **Come trovare l'IP del tuo PC:** premi **Win + R**, digita `cmd`, premi Invio. Digita `ipconfig` e premi Invio. Trova la riga **Indirizzo IPv4** sotto il tuo adattatore Wi-Fi. Esempio: `192.168.1.100`.

---

## Passo 2 - Aggiungi la cartella video in FastMediaSorter

1. Apri l'app → tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Cartella di rete (SMB)"**
2. Tocca **"Scansiona rete"** - l'app scansiona la tua rete di casa alla ricerca di PC disponibili
3. Quando il tuo PC compare nell'elenco, toccalo - l'indirizzo si compila automaticamente
4. Inserisci il nome della condivisione (il nome della cartella video), il nome utente e la password di Windows
5. Tocca **Connessione di prova** → **Salva**

> **Non hai trovato il tuo PC con la scansione?** Inserisci l'indirizzo manualmente: `\\192.168.1.100\Serie` (sostituisci con il tuo IP e il nome della cartella). Vedi la [Guida completa alla configurazione SMB](scenario-smb-setup-it.md) per tutti gli scenari di connessione.


---

## Passo 3 - Apri la cartella video

Tocca la risorsa appena aggiunta sulla schermata principale.

Le cartelle delle tue serie e i file video compaiono come una griglia con miniature - proprio come sfogliare in locale.

![Cartella video SMB - file degli episodi (MKV) elencati per nome file](screenshots/screenshot-hc-step3.png)

---

## Passo 4 - Configura l'episodio successivo automatico

Perché l'episodio successivo parta automaticamente quando uno finisce - senza dover scegliere manualmente quello dopo:

1. Torna alla schermata principale → **tieni premuta** la tua risorsa video → tocca **Modifica**
2. Imposta **Tipi supportati** → **Solo video** (nasconde i file non video)
3. Imposta **Modalità ordinamento** → **Nome (A→Z)** - questo assicura che gli episodi vengano riprodotti in ordine (Episodio 1, 2, 3..)
4. Tocca **Salva**

Poi avvia la presentazione nel player (il comando **Presentazione**) e attiva **Impostazioni → Player → Riproduci video/audio nella presentazione fino alla fine** - ogni episodio viene poi riprodotto fino alla fine prima che inizi il successivo.

> **Perché ordinare per nome?** I file degli episodi sono di solito chiamati `S01E01`, `S01E02`, ecc. Ordinare per nome li mette automaticamente nell'ordine corretto degli episodi.

---

## Passo 5 - Inizia a guardare

1. Apri la cartella, entra nella sottocartella della serie
2. Tocca **Episodio 1** - il player video si apre immediatamente e inizia lo streaming
3. Il video viene riprodotto via Wi-Fi - senza attese per il download

![Player video a schermo intero - episodio in riproduzione con barra di avanzamento](screenshots/screenshot-hc-step5.png)

---

## Passo 6 - Controlli durante la visione

**Gesti touch durante la riproduzione:**
- **Scorri a sinistra** → salta all'episodio successivo
- **Scorri a destra** → torna all'episodio precedente
- **Tocca lo schermo** → mostra / nascondi i controlli
- **Pizzica** → zoom avanti o indietro (utile per film widescreen su un telefono in verticale)
- **Doppio tocco sul bordo sinistro / destro** → riavvolgi / avanza veloce di 10 secondi

Quando **"Passaggio automatico"** è attivo, l'episodio successivo parte automaticamente quando quello attuale finisce - proprio come Netflix.

---

## Passo 7 - Per visori VR (Meta Quest, Pico)

> **Questa sezione è per chi ha un visore VR (come Meta Quest 2/3 o Pico 4).** Se non ne hai uno, salta questo passaggio.

I visori VR basati su Android possono eseguire FastMediaSorter. Installalo tramite sideload:
1. Scarica l'APK dalla [pagina Download](../DOWNLOADS.md)
2. Sul tuo visore, attiva **"Installa da fonti sconosciute"** nelle impostazioni Sviluppatore
3. Installa l'APK usando SideQuest o direttamente via ADB

Una volta installato, il player video funziona esattamente allo stesso modo:
- Il video riempie lo **schermo piatto virtuale** dentro il visore
- Usa il **grilletto del controller** per toccare i pulsanti
- Usa la **levetta analogica** per scorrere tra gli episodi (se il tuo visore mappa i pulsanti multimediali)
- Per i normali film e serie 2D - funziona subito, senza configurazione extra

> **Per l'esperienza cinema VR:** puoi usare un'app dedicata al cinema VR come launcher, e poi scegliere "Apri con FastMediaSorter" per la gestione dei file. FastMediaSorter gestisce la navigazione dei file; l'app cinema VR gestisce la visualizzazione immersiva a 360°.

---

## Fatto! Cosa provare dopo

- Aggiungi una risorsa **Google Drive** o **Dropbox** per i film archiviati nel cloud - funziona allo stesso modo
- Usa i **Preferiti** (tocca il pulsante stella <img src="../icons/doc/ic_star_filled.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> mentre guardi) per segnare la serie che stai "guardando in questo momento" - torna indietro in qualsiasi momento
- **Sottotitoli:** se la tua cartella video ha file sottotitoli `.srt` corrispondenti accanto ai file video, tocca il **pulsante CC / sottotitoli** nella barra degli strumenti del player per attivarli
- **Radio internet o stream live:** se vuoi anche aggiungere stazioni radio internet o fonti RTSP/HLS, vedi la guida [Radio internet e stream](scenario-internet-radio-it.md) - non serve NAS o PC, solo una connessione di rete.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| Il video ha scatti o si blocca in buffering | Esegui uno **Speed Test**: tieni premuta la risorsa → Modifica → Speed Test. Se la velocità è sotto i 5 Mbps, prova a passare la banda Wi-Fi del telefono ai **5 GHz** (più veloce, ma portata minore) |
| Il video non si riproduce (errore di formato) | Nel player, tocca **Opzioni <img src="../icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → passa il **Decoder** da Hardware a Software (più lento ma più compatibile) |
| Gli episodi vengono riprodotti nell'ordine sbagliato | Assicurati che la Modalità ordinamento sia impostata su **Nome (A→Z)** nelle impostazioni Modifica della cartella |
| Il passaggio automatico non parte | Assicurati che la presentazione sia in esecuzione e che Impostazioni → Player → Riproduci video/audio nella presentazione fino alla fine sia attivo |
| Il visore VR non riesce a installare l'APK | Apri le impostazioni Sviluppatore del visore e attiva "Consenti installazioni da fonti sconosciute" |

</div>
