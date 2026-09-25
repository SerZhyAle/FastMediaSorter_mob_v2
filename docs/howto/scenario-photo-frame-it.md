---
layout: default
title: "Cornice fotografica digitale su tablet - FastMediaSorter v2"
permalink: /docs/howto/scenario-photo-frame-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🖼️ Cornice fotografica digitale su tablet

> **Livello:** Principiante &bull; **Durata:** ~15 minuti &bull; **Edizione:** Standard, Photos, Legacy, VR, noLegal (per foto da NAS/cloud) oppure qualsiasi edizione (per foto locali)

{% include lang-switcher.html doc="scenario-photo-frame" dir="/docs/howto/" current="it" %}

Trasforma qualsiasi tablet Android in una bellissima cornice fotografica digitale sempre accesa - trasmettendo i tuoi ricordi da un NAS di casa o dal cloud, con musica di sottofondo opzionale. Zero spazio di archiviazione locale usato.

> **L'idea in una frase:** appoggia un vecchio tablet, collegalo alla corrente, avvia una presentazione - mostra le tue foto automaticamente, per sempre, cambiando ogni pochi secondi. Come una vera cornice fotografica digitale da negozio, ma alimentata dalla tua raccolta fotografica, da qualsiasi fonte.

---

## Cosa ti serve

- Un tablet Android (qualsiasi dimensione - anche uno vecchio va benissimo!)
- Un supporto o una base per tenere il tablet in verticale
- Un **alimentatore USB** per tenerlo sempre collegato - il tablet resterà acceso tutto il giorno, quindi la batteria non basta
- Le tue foto su una di queste fonti: **archiviazione locale**, **PC/NAS di casa via SMB**, o **Google Drive / Dropbox**
- (Facoltativo) Una fonte musicale per l'audio di sottofondo

---

## Passo 1 - Aggiungi la tua fonte fotografica

Scegli dove si trovano le tue foto:

**Opzione A - Foto locali (sul tablet stesso):**
1. Tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Cartella locale** → vai alla cartella delle tue foto → **Seleziona**

**Opzione B - NAS di casa / PC Windows (SMB):**
1. Tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Cartella di rete (SMB)**
2. Tocca **"Scansiona rete"** → seleziona il tuo PC/NAS dall'elenco
3. Compila nome condivisione + nome utente + password
4. Tocca **Connessione di prova** → **Salva**

> Configurazione SMB completa: [Collegati a NAS (SMB)](scenario-smb-setup-it.md). Richiede circa 5 minuti da configurare una volta, poi funziona per sempre.

**Opzione C - Google Drive / Dropbox:**
1. Tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Archiviazione cloud** → scegli il provider
2. Tocca **Accedi** → completa l'autenticazione nel browser
3. Seleziona la cartella con le tue foto → **Fatto**

![Schermata principale di FastMediaSorter - card della risorsa fotografica visibili dopo l'aggiunta di una cartella foto](screenshots/screenshot-pf-step1.png)

---

## Passo 2 - Configura la cartella per la presentazione

Tieni premuta la tua cartella foto sulla schermata principale → tocca **Modifica (icona matita)**.

Imposta queste opzioni:

| Impostazione | Valore consigliato | Perché |
|---------|------------------|-----|
| **Intervallo presentazione** | 5-10 secondi | 5 s = sensazione vivace da album di famiglia; 10 s = calmo, ottimo per foto artistiche o grandi gruppi dove vuoi il tempo di riconoscere tutti |
| **Includi sottocartelle** | ATTIVO | Mostra le foto da tutte le sottocartelle - ottimo se organizzi per anno/album |
| **Modalità ordinamento** | Data scatto (più recenti prima) o Casuale | Casuale = più varietà ogni giorno; Data = le foto più recenti appaiono per prime |
| **Tipi supportati** | Solo immagini | Rimuovi Video e Audio - altrimenti verranno riprodotti anche i file video, interrompendo il flusso della presentazione |

Tocca **Salva**.

![Modifica risorsa - impostazioni Intervallo presentazione e Includi sottocartelle](screenshots/screenshot-pf-step2.png)

---

## Passo 3 - (Facoltativo) Aggiungi musica di sottofondo

Vuoi musica soft mentre guardi le foto? Ecco come fare (serve un'edizione con audio - l'edizione Photos non ne ha):

1. Per prima cosa, aggiungi una fonte musicale: tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → Cartella locale → vai alla tua cartella musicale
2. Vai su **Impostazioni → scheda Multimediali → Riproduzione audio, copertine e visualizzazioni**
3. Attiva **"Mostra foto casuali durante la riproduzione audio"**

Poi vai su **Impostazioni → scheda Multimediali → Immagini, GIF e presentazione**:
4. Attiva **"Riproduci musica durante la presentazione"**
5. Tocca **"Seleziona fonte musicale"** → scegli la tua risorsa musicale

> **Suggerimento:** Se la musica ha degli scatti quando le foto provengono da un NAS, usa una cartella musicale locale per l'audio e lascia che solo le foto vengano trasmesse dalla rete - puoi mescolare liberamente le fonti in questo modo.


---

## Passo 4 - Avvia la presentazione

1. Tocca la tua **cartella foto** sulla schermata principale per aprirla
2. Tocca **una foto qualsiasi** per aprire il visualizzatore a schermo intero
3. Tocca **"Presentazione" <img src="../icons/doc/ic_slideshow.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti in alto

Tutto qui - la presentazione parte. Le foto avanzano automaticamente all'intervallo che hai impostato.

> **Avvio rapido alternativo:** Tocca la **zona in basso a destra** della schermata foto (lo schermo è diviso in una griglia 3×3 di zone touch invisibili; in basso a destra = zona 9 = RIPRODUCI).


---

## Passo 5 - Mantieni lo schermo acceso

**Questo passaggio è fondamentale.** Android risparmia batteria spegnendo lo schermo dopo pochi minuti - il che rovinerebbe la cornice fotografica. Devi disattivare questa opzione.

**Opzione A - Impostazione nell'app (consigliata):**
Vai su **Impostazioni → Gestione → Impedisci lo spegnimento** e attivala.

Questo dice ad Android di tenere lo schermo acceso finché l'app è in esecuzione in primo piano. Nel momento in cui cambi app o la presentazione si ferma, torna il normale timeout dello schermo.

![Impostazioni, scheda Gestione - interruttore Impedisci lo spegnimento attivato](screenshots/screenshot-pf-step5.png)

**Opzione B - Impostazione di sistema Android:**
Impostazioni Android → Schermo → Timeout schermo → imposta su **"Mai"** (o al massimo).

> **Inoltre:** Tieni il tablet sempre **collegato all'alimentazione USB**. Un tablet che esegue una presentazione tutta la giornata scaricherà la batteria entro sera. Usa semplicemente il caricabatterie originale e lascialo collegato.

---

## Passo 6 - (Facoltativo) Aggiungi un widget alla schermata Home

Questo passaggio è per comodità: vuoi avviare istantaneamente la cornice fotografica quando prendi in mano il tablet - senza aprire l'app e navigare?

1. Tieni premuta la schermata Home → tocca **Widget**
2. Trova **FastMediaSorter** nell'elenco dei widget
3. Trascina il widget **"Scorciatoia risorsa"** sulla schermata Home
4. Quando richiesto, seleziona la tua risorsa fotografica
5. Tocca il widget in qualsiasi momento → la presentazione si avvia istantaneamente

![Schermata Home Android con widget di scorciatoia risorsa di FastMediaSorter posizionati](screenshots/screenshot-pf-step6.png)

---

## Fatto! La tua cornice fotografica è in funzione

**Controlli durante la riproduzione della presentazione:**
- **Tocca lo schermo** → metti in pausa / mostra i controlli
- **Scorri a sinistra / destra** → salta manualmente alla foto successiva / precedente
- **Tocca la zona in basso a destra** → ferma la presentazione e torna all'elenco file

---

## Suggerimenti

> **Le foto dal NAS non si aggiornano dopo averne aggiunte di nuove?** L'app memorizza nella cache l'elenco dei file per velocità. Per aggiornare: torna alla cartella → tocca il pulsante **Aggiorna <img src="../icons/doc/ic_refresh.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti. Le nuove foto compaiono immediatamente.

> **Le foto sembrano ingrandite o tagliate?** Apri Impostazioni → Multimediali → Immagini → **"Ritaglia le immagini per riempire lo schermo"** e prova entrambe le posizioni: DISATTIVATO mantiene visibile la foto intera, ATTIVATO riempie lo schermo bordo a bordo (leggero ritaglio ai lati).

> **Usi un telefono in verticale come cornice?** Attiva "Ritaglia le immagini per riempire lo schermo" per evitare bande nere sulle foto orizzontali.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| Lo schermo si spegne dopo pochi minuti | Attiva "Impedisci lo spegnimento" in Impostazioni → Gestione (Passo 5) **e** collega l'alimentatore USB |
| Le foto non vengono mostrate | Apri le impostazioni della cartella (Passo 2) e assicurati che **Immagini** sia selezionato in **Tipi supportati** |
| La musica non si riproduce | Verifica che la cartella musicale contenga almeno un file audio; controlla che **Riproduci musica durante la presentazione** sia attivo in Impostazioni → Multimediali → Immagini, GIF e presentazione |
| La presentazione si mette in pausa sui file video | Previsto - i video vengono riprodotti, poi la presentazione riprende. Imposta "Tipi supportati → Solo immagini" nelle impostazioni della cartella (Passo 2) per evitarlo |
| Le foto SMB si caricano lentamente | Modifica cartella → disattiva "Carica miniature" per ridurre il carico di rete. Oppure riduci l'intervallo della presentazione per avere più tempo di caricamento |
| Le foto si ripetono troppo rapidamente | Aumenta l'intervallo della presentazione nelle impostazioni della cartella (Passo 2) |

</div>
