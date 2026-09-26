---
layout: default
title: "Ascolta musica sul tuo orologio - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-music-it.html
---
<div lang="it" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_audio.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Ascolta musica sul tuo orologio

> **Livello:** Principiante &bull; **Durata:** ~5 minuti &bull; **Dispositivo:** Smartwatch Wear OS (abbinato a un telefono Android)

> **Solo versione completa** - questa guida non è implementata nella versione distribuita tramite Google Play. Si applica alla versione completa, un download diretto dell'APK da [Download](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-music" dir="/docs/howto/" current="it" %}

FastMediaSorter ti permette di sfogliare e riprodurre la tua raccolta musicale direttamente dal tuo smartwatch Wear OS. Puoi trasmettere brani condivisi dal telefono abbinato oppure riprodurre file audio locali archiviati sull'orologio, con copertine, riproduzione casuale, controllo del volume dalla ghiera rotante, riproduzione in background che sopravvive all'uscita dall'app, e una modalità a schermo spento che mantiene la musica in corso con il display scuro.

---

## Cosa ti serve

- Uno smartwatch con **Wear OS 2.0** o successivo con FastMedia Wear installato
- Un telefono Android con FastMediaSorter in esecuzione (se trasmetti musica dal telefono)
- File musicali (MP3, FLAC, AAC, OGG) sul telefono o trasferiti sulla memoria dell'orologio
- Cuffie Bluetooth o l'altoparlante dell'orologio per l'uscita audio

---

## Passo 1 - Apri FastMedia Wear sul tuo orologio

1. Apri l'elenco delle app sul tuo smartwatch e tocca **FastMedia Wear**.
2. La schermata Home mostra sei sezioni, sempre nelle stesse posizioni:
   - **Risorse**: fonti di rete e opzioni di sincronizzazione
   - **Telefono**: musica e contenuti multimediali condivisi dal tuo telefono Android abbinato
   - **Locale**: file nella memoria dell'orologio stesso, incluse le note vocali registrate lì
   - **Stream**: canali TV e radio ([guida separata](scenario-watch-tv-it.md))
   - **App**: calcolatrice, monitor di rete, gioco e gli altri mini-programmi
   - **Preferiti**: tutto ciò che hai contrassegnato

![Schermata principale di FastMedia Wear sullo smartwatch](screenshots/screenshot-wear-music-step1.png)

---

## Passo 2 - Scegli la tua fonte musicale

1. Per riprodurre musica dal telefono: tocca **Telefono** sulla schermata principale, poi tocca **Audio**.
2. Per riprodurre brani archiviati direttamente sull'orologio: tocca **Locale** sulla schermata principale, poi tocca **Musica**.
3. FastMedia Wear si collega alla fonte selezionata e carica il tuo catalogo musicale.

> **Suggerimento:** La schermata principale mantiene una riga con le risorse aperte più di recente sopra le sei sezioni - una cella per colonna, quindi due in una griglia a due colonne e tre in una a tre colonne. Una volta che hai riprodotto qualcosa, è lì pronta con un tocco, e l'ultimo canale stream che hai guardato si trova nella stessa riga.

---

## Passo 3 - Sfoglia e avvia la riproduzione

1. Scorri tra i tuoi brani usando il tocco o la ghiera rotante.
2. Ogni elemento mostra il titolo del brano, la durata e la miniatura della copertina.
3. Tocca **un brano qualsiasi** per avviare subito la riproduzione.

![Sfoglia i brani audio sull'orologio](screenshots/screenshot-wear-music-step3.png)

---

## Passo 4 - Controlla la riproduzione e il volume

Quando un brano parte, si apre il **Player audio** a schermo intero:

- **Riproduci / Pausa**: tocca il pulsante evidenziato al centro per mettere in pausa o riprendere la riproduzione.
- **Salta brani**: tocca **Precedente** o **Successivo** per cambiare brano nella tua playlist.
- **Casuale**: tocca il pulsante **Casuale** per mescolare l'ordine dei brani.
- **Cerca nel brano**: trascina orizzontalmente la barra di avanzamento per saltare a qualsiasi punto della canzone.
- **Volume**: gira la corona o la ghiera rotante dell'orologio per regolare il volume in modo fluido. Sullo schermo compare un indicatore del livello del volume.
- **Preferito**: tocca l'icona a stella per aggiungere il brano ai tuoi preferiti.

![Player audio con controlli di riproduzione e volume](screenshots/screenshot-wear-music-step4.png)

---

## Passo 5 - Mantieni la musica in corso

Ci sono due modi diversi per continuare ad ascoltare, e rispondono a due domande diverse.

**Lasciare l'app** - attiva **Continua a riprodurre in background** nelle impostazioni dell'orologio. L'audio continua poi dopo che riduci a icona l'app o torni al quadrante, con i controlli nella notifica multimediale. Quando torni, la schermata principale mostra una riga con il nome di ciò che è in riproduzione: toccala per tornare al brano dal punto in cui era rimasto, oppure tocca il pulsante di stop accanto per terminare la riproduzione senza aprire nient'altro. L'interruttore è opzionale, e richiede che le notifiche siano consentite - senza di esse il sistema non può mantenere attivo il servizio di riproduzione.

**Restare nel player con lo schermo spento** - tocca il pulsante **Schermo spento** in fondo ai controlli del player. Il display diventa completamente nero mentre la musica continua a suonare, il che risparmia batteria su un orologio OLED. Un singolo tocco segna solo il punto che hai toccato con un piccolo puntino bianco, così una manica che sfiora il vetro non cambia nulla; un doppio tocco, una pressione prolungata o il pulsante fisico dell'orologio riportano indietro i controlli. Sui quadranti più piccoli il pulsante resta nel menu del player invece che nella riga.

![Pulsante modalità schermo spento](screenshots/screenshot-wear-music-step5.png)

> Video e presentazioni non sono deliberatamente coperti da nessuna delle due opzioni: si fermano quando l'app lascia lo schermo, perché un'immagine che nessuno può vedere costa solo batteria.

---

## Fatto! Funzioni del player

- **Copertina e sfondo con onde**: Mostra la copertina a tutto schermo o onde sonore dinamiche dietro i controlli.
- **Integrazione ghiera rotante**: Controllo del volume nativo usando la ghiera fisica o la corona dell'orologio.
- **Riproduzione in background**: L'audio sopravvive all'uscita dall'app, con i controlli nella notifica multimediale e una riga sulla schermata principale che indica cosa è in riproduzione.
- **Ascolto a schermo spento**: Oscuramento istantaneo del display dentro il player, preservando riproduzione e durata della batteria.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| La sezione Telefono dice "Telefono non connesso" | Assicurati che il Bluetooth sia attivo su entrambi i dispositivi e che FastMediaSorter sia installato sul tuo telefono |
| Nessun file musicale compare in Locale | Copia file MP3 o FLAC nella memoria interna dell'orologio, oppure usa la sezione Telefono per riprodurre dal telefono |
| L'audio si ferma quando lasci l'app | Attiva **Continua a riprodurre in background** nelle impostazioni dell'orologio, e consenti le notifiche - il servizio di riproduzione ne ha bisogno per restare attivo |
| Manca la copertina | Connettiti al Wi-Fi per scaricare la copertina online, oppure assicurati che i tuoi file audio contengano una copertina ID3 incorporata |

</div>
