---
layout: default
title: "Radio internet e stream - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 📻 Radio internet e stream

> **Livello:** Principiante - **Durata:** ~10 minuti - **Edizione:** Standard, Legacy, VR, noLegal (Stream è assente in Lite e Photos)

{% include lang-switcher.html doc="scenario-internet-radio" dir="/docs/howto/" current="it" %}

FastMediaSorter include una schermata Stream dedicata per fonti audio e video internet. Aggiungi qualsiasi URL di radio internet, importa una playlist .m3u, o sfoglia un catalogo di stazioni curato - senza bisogno di un'app radio separata. Funziona benissimo su autoradio Android, lettori audio, telefoni e tablet.

> **Sostituisce:** TuneIn, l'app Shoutcast, Online Radio, RadioDroid, gli stream di rete di VLC, i lettori IPTV.

---

## Cosa ti serve

- Un dispositivo Android con una connessione di rete (dati mobili o Wi-Fi)
- FastMediaSorter Standard, Legacy, VR o noLegal (la schermata Stream è assente in Lite e Photos)
- Un URL di stream, un file o URL di playlist .m3u, oppure il catalogo curato integrato

---

## Passo 1 - Apri la schermata Stream

Tre modi per arrivarci:
- Menu a tendina della schermata principale -> **Stream**
- **Impostazioni -> Multimediali -> Stream** -> tocca il pulsante di scorciatoia Stream
- Configurazione di benvenuto -> riga Stream (solo al primo avvio)

> **Non vedi Stream nel menu?** Vai su Impostazioni -> Multimediali -> Stream e assicurati che "Abilita Stream" sia ATTIVO. È attivo per impostazione predefinita sulla maggior parte dei dispositivi.

---

## Passo 2 - Aggiungi una stazione o uno stream

**Opzione A - Aggiungi manualmente un singolo URL:**
1. Tocca **Aggiungi (+)** nella barra degli strumenti della schermata Stream
2. Incolla l'URL dello stream (radio http/https, HLS .m3u8, rtsp://..)
3. Dagli un nome e tocca **Salva**

**Opzione B - Importa una playlist .m3u:**
1. Tocca **Importa** -> **Da URL**
2. Incolla l'URL della playlist .m3u e conferma
3. Tutte le stazioni della playlist vengono aggiunte al tuo elenco

**Opzione C - Sfoglia il catalogo curato:**
1. Tocca **Importa catalogo** (o scaricalo dalla schermata Estensioni)
2. Sfoglia o cerca per nome, argomento o lingua
3. Tocca le stazioni per aggiungerle al tuo elenco

---

## Passo 3 - Riproduci una stazione

- **Stream audio (radio):** tocca la riga - la riproduzione inizia inline. In basso compare un mini-controllo fisso che mostra il nome della stazione e le informazioni ICY sul brano attualmente in riproduzione. L'elenco resta completamente interattivo.
- **Stream video o RTSP:** tocca la riga - si apre nel player a schermo intero. Premi Indietro per tornare all'elenco; la posizione di scorrimento e l'ultima stazione selezionata vengono conservate.

---

## Passo 4 - Mantieni la radio in riproduzione in background

Per mantenere l'audio in riproduzione quando cambi app o blocchi lo schermo:

1. Vai su **Impostazioni -> Multimediali -> Player**
2. Trova il gruppo **Riproduzione audio in background**
3. Attiva **Riproduzione audio in background**

> **Lasciare la schermata Stream mentre una stazione è in riproduzione:** l'app offre la stessa scelta Ferma / Continua a riprodurre del player principale. Se la riproduzione in background è DISATTIVATA, lo stream si ferma quando riduci a icona la schermata.

---

## Passo 5 - Filtra e organizza

- **Fissa i preferiti in alto:** tieni premuta una riga di stazione -> Fissa. Le stazioni fissate compaiono sopra le altre, indipendentemente dall'ordinamento.
- **Filtra per categoria o lingua:** tocca il pulsante Filtro (compare un punto quando un filtro è attivo). Il selettore lingua mostra le bandiere. Usa l'interruttore E/O per far corrispondere tutti o uno qualsiasi dei filtri selezionati.
- **Ordina:** tocca il pulsante di ordinamento per ordinare per nome, argomento, lingua o riproduzione recente.
- **Cerca:** digita nella barra di ricerca per filtrare per nome tra tutte le stazioni.

---

## Passo 6 - Cosa fare se una stazione non funziona

Se uno stream non è disponibile o è stato reindirizzato, appare una finestra con tre opzioni:
- **Riprova** - riprova lo stream
- **Rimuovi** - lo elimina dal tuo elenco
- **Annulla** - chiude la finestra e mantiene la voce

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|-------------|
| Lo stream non si riproduce | Controlla che l'URL sia corretto e che la stazione sia online. Prova Riprova nella finestra di non disponibilità |
| L'audio si ferma quando cambio app | Attiva Riproduzione audio in background in Impostazioni -> Multimediali -> Player |
| Nessuna voce Stream nel menu | La schermata Stream è assente nelle edizioni Lite e Photos. Usa Standard, Legacy, VR o noLegal |
| L'importazione del catalogo si blocca | L'host del catalogo potrebbe essere lento o offline. L'importazione va in timeout automaticamente e mostra un errore - controlla la connessione e riprova |
| Nessuna bandiera mostrata nel filtro lingua | Le bandiere vengono mostrate in base al tag della lingua nel catalogo delle stazioni. Le stazioni aggiunte manualmente senza tag della lingua sono sempre visibili con qualsiasi filtro lingua |

</div>
