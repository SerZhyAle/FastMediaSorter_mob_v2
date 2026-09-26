---
layout: default
title: "Metti FastMedia sul tuo orologio - FastMediaSorter v2"
permalink: /docs/howto/wear-install-it.html
---
<div lang="it" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Metti FastMedia sul tuo orologio

> **Livello:** Principiante &bull; **Durata:** ~5 minuti &bull; **Dispositivo:** Smartwatch Wear OS abbinato a un telefono Android

> **Due versioni.** La versione da Google Play è una piccola prima release: Calcolatrice, Cronometro, Mini-gioco, Impostazioni e il riquadro Programmi. Musica, foto, condivisioni di rete e funzioni del telefono sono presenti solo nella versione completa, un download diretto dell'APK da [Download](../DOWNLOADS.md).

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="it" %}

FastMedia Wear è la metà per orologio di FastMediaSorter. Una volta al polso puoi riprodurre musica e vedere foto direttamente dall'orologio, raggiungere le cartelle condivise dal tuo telefono abbinato, e aprire condivisioni di rete a cui l'orologio si collega da solo. Questa pagina ti guida nell'installazione e nell'abbinamento.

---

## Cosa ti serve

- Uno smartwatch con **Wear OS 3.0** o successivo
- Un telefono Android con FastMediaSorter installato e l'orologio già abbinato ad esso nelle impostazioni di sistema
- Una connessione Wi-Fi o dati mobili sull'orologio, o sul telefono a cui è abbinato, per il download

---

## Passo 1 - Installa FastMedia Wear sull'orologio

1. Sull'orologio, apri l'elenco delle app e tocca **Play Store**.
2. Cerca **FastMedia Wear**.
3. Tocca **Installa** e attendi che il download finisca. L'orologio mostrerà l'app nel suo elenco quando avrà finito.

> Gli orologi variano in quanto ti permettono di digitare. Se cercare sul polso è scomodo, apri il Play Store sul tuo telefono, trova FastMedia Wear, e scegli il tuo orologio come destinazione di installazione - l'orologio lo scarica da solo.

### Nessun Play Store? Installa un APK tramite ADB

Usa questo percorso quando il tuo orologio non ha accesso al Play Store. Ti serve un computer con
l'Android SDK Platform-Tools (`adb`) e una rete Wi-Fi locale condivisa dal computer e dall'orologio. Non
funziona solo tramite internet.

1. Scarica un APK dalla pagina [Direct APK Release](../DOWNLOADS.md):
   - `FastMediaSorter_wear_debug.apk` è la build di debug per i test. Si installa come
     `com.sza.fastmediasorter.debug`.
   - `FastMediaSorter_wear_release.apk` è la build firmata non di debug. Si installa come
     `com.sza.fastmediasorter`.
   - Le due build hanno nomi di pacchetto diversi, quindi possono restare installate fianco a fianco. Non
     provare a installare un file `.aab` del Play Store con ADB.
2. Sull'orologio, attiva la modalità sviluppatore: **Impostazioni** → **Info sull'orologio** → tocca **Numero build** sette
   volte. In **Opzioni sviluppatore**, attiva **Debug ADB** e **Debug wireless**.
3. In **Debug wireless**, scegli **Abbina nuovo dispositivo**. Sul computer, inserisci l'indirizzo di abbinamento
   e il codice mostrati dall'orologio, poi connettiti con la porta di connessione separata dalla schermata principale
   di Debug wireless:

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Accetta la richiesta di debug sull'orologio. Le porte di abbinamento e di connessione sono diverse.
4. Installa o aggiorna l'APK. Usa il comando corrispondente al file che hai scaricato:

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` aggiorna lo stesso pacchetto mantenendo i suoi dati. Non converte una build di debug in
   una build di release, perché sono app separate.
5. Apri **FastMedia Wear** dall'elenco delle app dell'orologio. Se necessario, avvialo da ADB:

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> Questo metodo richiede un orologio Wear OS. Galaxy Watch 3, Galaxy Watch Active e Active 2 eseguono Tizen
> e non possono installare APK Wear OS. Al termine, disattiva il Debug wireless a meno che non ti serva per
> un altro aggiornamento.

---

## Passo 2 - Attiva il companion Wear sul telefono

Il lato telefono è disattivato finché non dichiari di possedere un orologio.

1. Apri FastMediaSorter sul telefono.
2. Vai su **Impostazioni** e apri la scheda **Gestione**.
3. Trova il gruppo **Wear OS** ed espandilo.
4. Attiva la casella **Companion Wear**.

La casella attiva l'intero companion: il pulsante che apre la sua finestra compare subito sotto, una voce per esso si unisce all'elenco dei programmi, e diventa disponibile come riquadro pannello e scorciatoia launcher.

> Le build senza il ponte per l'orologio non mostrano affatto questo gruppo. Se non riesci a trovarlo, stai usando un'edizione che viene distribuita senza supporto Wear.

---

## Passo 3 - Scegli cosa arriva all'orologio

1. Nello stesso gruppo, tocca **Companion Wear**. Si apre la sua finestra sopra l'app.
2. Scegli le risorse che vuoi che l'orologio veda. Nulla viene inviato finché non scegli - una selezione vuota non invia nulla invece di inviare l'intera libreria.
3. Regola qui anche le preferenze dell'orologio stesso: modalità visualizzazione, comportamento di mantenimento attivo e le sezioni mostrate sulla schermata principale dell'orologio.

---

## Passo 4 - Verifica che le due metà si vedano a vicenda

1. Apri **FastMedia Wear** sull'orologio.
2. La schermata principale elenca le sue sezioni - **Telefono**, **Locale**, **Risorse**, **Stream** e **App**.
3. Tocca **Telefono**. Compaiono le cartelle che hai selezionato al Passo 3.

Se la sezione Telefono è vuota, torna alla finestra companion sul telefono e conferma che almeno una risorsa sia selezionata.

> **Suggerimento:** Puoi tornare indietro da qualsiasi schermata sul tuo orologio usando il pulsante universale di ritorno visibile sul bordo sinistro, scorrendo dal bordo sinistro, o premendo il pulsante fisico indietro dell'orologio. Sulla schermata principale, toccando il pulsante di ritorno compare un'icona di uscita (una freccia che lascia un riquadro) per lasciare l'app oppure un doppio chevron («) per ridurre a icona la riproduzione in background. Su ogni schermata che mostra quel pulsante, un pulsante schermo nero (un telefono con schermo scuro) gli sta di fronte sul bordo destro e oscura lo schermo dell'orologio; un doppio tocco, una pressione prolungata o il pulsante fisico lo riportano indietro.

---

## Se qualcosa non funziona

- **L'app per l'orologio non compare nel Play Store.** Conferma che l'orologio esegua Wear OS 3.0 o successivo. Gli orologi più vecchi usano un modello di app diverso e non sono supportati.
- **Il gruppo Wear OS manca dalle impostazioni del telefono.** La build che stai usando non include il ponte per l'orologio.
- **La sezione Telefono sull'orologio è vuota.** Nulla è selezionato nella finestra companion, oppure l'orologio e il telefono hanno perso l'abbinamento - controlla prima l'abbinamento nelle impostazioni di sistema.
- **La riproduzione ha scatti sulla connessione col telefono.** Il Bluetooth tra orologio e telefono è stretto. Per ascolti lunghi, trasferisci i file sull'orologio o collega l'orologio direttamente a una condivisione di rete.

---

## Dove andare dopo

- [Musica sullo smartwatch](scenario-watch-music-it.md) - riproduci la tua raccolta sull'orologio, con copertine, riproduzione casuale e volume dalla ghiera.
- [Collega l'orologio alle condivisioni di rete](scenario-watch-network-it.md) - raggiungi un NAS o una condivisione PC dall'orologio via Wi-Fi, senza il telefono.

</div>
