---
layout: default
title: "🔧 Guida alla risoluzione dei problemi"
permalink: /docs/TROUBLESHOOTING-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🔧 Guida alla risoluzione dei problemi

Guida attuale alla risoluzione dei problemi per FastMediaSorter v2. Usa la matrice canonica delle versioni in [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) quando il problema dipende dal percorso di build selezionato (Standard, Lite, Photos, Legacy o XR / noLegal).

{% include lang-switcher.html doc="TROUBLESHOOTING" dir="/docs/" current="it" %}

---

## Problemi di connessione

### ❌ "Impossibile connettersi al server SMB"

**Possibili cause:**
1. **Rete sbagliata** - il telefono deve essere sulla stessa rete Wi-Fi del NAS
2. **Formato indirizzo sbagliato** - prova entrambi i formati:
   - `\\192.168.1.100\share`
   - `smb://192.168.1.100/share`
3. **Firewall che blocca** - controlla le impostazioni del firewall del NAS
4. **Versione SMB non compatibile** - alcuni NAS richiedono ancora la compatibilità SMB v2/v3; aggiorna il server se espone solo impostazioni SMB legacy

**Soluzione:**
- Testa prima la connessione dal PC
- Controlla i log del NAS per i tentativi di connessione
- Prova l'indirizzo IP invece del nome host
- Verifica nome utente/password

---

### ❌ "Timeout di connessione SFTP"

**Possibili cause:**
1. Porta sbagliata (predefinita: 22)
2. Server SSH non in esecuzione
3. Firewall che blocca

**Soluzione:**
```
1. Test with SSH client on PC first:
   ssh username@192.168.1.100
2. Check if SSH service is running
3. Verify port in Settings
```

---

### ❌ "Accesso a Google Drive non riuscito"

**Soluzione:**
1. Cancella i dati dell'app: Impostazioni → App → FastMediaSorter → Cancella dati
2. Reinstalla l'app
3. Controlla le impostazioni dell'account Google → Sicurezza → App di terze parti

---

### ❌ "Accesso a OneDrive non riuscito"

**Soluzione:**
1. Controlla lo stato dell'account Microsoft
2. Cancella i dati dell'app: Impostazioni → App → FastMediaSorter → Cancella dati
3. Controlla le impostazioni dell'account Microsoft → Privacy → App e servizi

---

### ❌ "Accesso a Dropbox non riuscito"

**Soluzione:**
1. Controlla lo stato dell'account Dropbox
2. Cancella i dati dell'app: Impostazioni → App → FastMediaSorter → Cancella dati
3. Controlla le impostazioni dell'account Dropbox → Sicurezza → App collegate

---

## Problemi di prestazioni

### ❌ "L'app è lenta / a scatti"

**Per cartelle grandi (5000+ file):**
1. **Modifica cartella** (per risorsa) → attiva **"Disattiva miniature"**
2. Usa i **filtri** per ridurre i file visibili
3. Chiudi altre app per liberare RAM

**Per cartelle di rete:**
1. Controlla la potenza del segnale Wi-Fi
2. Riduci la dimensione della cache delle miniature
3. Attiva **"Scansiona sottocartelle"** = OFF se non necessario

---

### ❌ "Le miniature non si caricano"

**File locali:**
- Controlla i permessi di archiviazione
- Cancella la cache delle miniature
- Riavvia l'app

**File di rete:**
- Scorri più lentamente (le miniature si caricano su richiesta)
- Controlla la velocità della rete
- Aumenta la dimensione della cache nelle Impostazioni

---

## Errori nelle operazioni sui file

### ❌ "Copia non riuscita: permesso negato"

**File locali:**
- Concedi i permessi di archiviazione: Impostazioni → App → Permessi
- Controlla se la cartella è di sola lettura
- Prova a spostarti in un'altra posizione

**File di rete:**
- Controlla che l'utente abbia i permessi di scrittura
- Verifica le impostazioni della condivisione sul NAS

---

### ❌ "Impossibile eliminare il file"

**Possibili cause:**
1. Il file è aperto in un'altra app
2. Nessun permesso di scrittura
3. Il file è protetto dal sistema

**Soluzione:**
- Chiudi le altre app
- Controlla i permessi della cartella
- Per la rete: verifica che l'utente abbia i diritti di eliminazione

---

### ❌ "Operazione di spostamento non riuscita"

**Spostamenti tra protocolli diversi** (ad es. Locale → SMB):
- In realtà sono **copia + eliminazione**
- Richiedono spazio libero sulla destinazione
- Possono richiedere più tempo per i file grandi

**Soluzione:**
- Controlla lo spazio disponibile
- Usa Copia invece di Sposta per sicurezza
- Attendi il completamento dell'intera operazione

---

## Arresti anomali dell'app

### ❌ "L'app si arresta in modo anomalo all'apertura del player"

**Cause comuni:**
1. File video danneggiato
2. Codec non supportato
3. File troppo grande (>4GB)

**Soluzione:**
- Prova a riprodurre il file in un'altra app per verificarlo
- Controlla il formato del file (supportati: MP4, MKV, MOV)
- Cancella la cache dell'app

---

### ❌ "Il file multimediale non si riproduce o non c'è audio"

**Problema:** il video si carica ma mostra uno schermo nero, oppure viene riprodotto senza audio.

**Soluzione:**
1. Tocca il pulsante **ⓘ (Info)** nella barra degli strumenti superiore
2. Tocca **"Apri in player esterno"**
3. Seleziona un player specializzato (ad es. VLC, MX Player)

Questo usa la funzione *Player secondario* per passare i codec non supportati ad altre app.

---

### ❌ "L'app si arresta in modo anomalo all'avvio"

**Soluzione:**
1. Cancella la cache dell'app: Impostazioni → App → FastMediaSorter → Cancella cache
2. Se persiste: cancella i dati dell'app (⚠️ perdi le impostazioni)
3. Reinstalla l'app come ultima risorsa

---

## Problemi di interfaccia / visualizzazione

### ❌ "Le zone touch non funzionano"

**Controlla se sono attive:**
Impostazioni → Player → **"Mostra il suggerimento delle zone touch al primo avvio"** = ON

**Rendile visibili:**
Impostazioni → Player → **"Mostra sempre la sovrapposizione delle zone touch"** = ON

---

### ❌ "I pulsanti del pannello comandi sono troppo piccoli"

**Soluzione:**
Impostazioni → Player → **"Pulsanti del player compatti"** = OFF

Questo raddoppia la dimensione di tutti i pulsanti e la spaziatura.

---

### ❌ "Il tema scuro non funziona"

L'app segue il **tema di sistema**:
- Impostazioni Android → Schermo → Tema scuro = ON

---

## Problemi con i dati

### ❌ "I preferiti sono scomparsi"

I preferiti sono memorizzati **localmente**:
- Hai cancellato i dati dell'app? → i preferiti sono persi
- Nuovo dispositivo? → devi ricontrassegnarli

**Prevenzione:**
- Usa **Impostazioni → Generali → Backup, ripristino ed esportazione delle impostazioni**
- I preferiti sono locali al dispositivo; se passi a un telefono nuovo, ricontrassegnali oppure usa il flusso di backup/ripristino dell'app disponibile nella tua build

---

### ❌ "La cartella cestino continua a crescere"

I file eliminati vanno nella cartella `.trash/` e vi restano finché non vengono svuotati manualmente.

**Soluzione:**
1. Impostazioni → Gestione → **Eliminazione dei file e cestino**
2. Oppure elimina manualmente le cartelle `.trash/`

---

## Hai ancora problemi?

### Controlla i log
1. Impostazioni → Gestione → **"Mostra errori dettagliati"** = ON
2. Riproduci il problema
3. Controlla l'output del logcat

### Segnala un bug
Includi queste informazioni:
- Versione di Android
- Modello del dispositivo
- Passaggi per riprodurre il problema
- Messaggio di errore (screenshot)

**Invia:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

---
    
## Problemi di traduzione ed EPUB
    
### ❌ "La traduzione non funziona o resta bloccata"

**Possibili cause:**
1. **Modelli mancanti:** l'app non è riuscita a scaricare i modelli OCR.
2. **Nessuna connessione Internet:** il primo avvio richiede Internet per scaricare i modelli.
3. **Spazio pieno:** nessuno spazio per i modelli (~50 MB).

**Soluzione:**
1. Controlla la connessione Internet.
2. Vai su **Impostazioni** → **Media** → **Altro**
3. Disattiva "Attiva traduzione" e riattivala.
4. Prova a impostare la **Lingua di origine** su "Auto".

---

### ❌ "Il libro EPUB non si apre"

**Possibili cause:**
1. **Protezione DRM:** l'app supporta solo EPUB senza DRM.
2. **File danneggiato:** il file potrebbe essere incompleto.
3. **File molto grande:** i file >100MB su rete lenta potrebbero andare in timeout.

**Soluzione:**
1. Verifica che il file si apra in altri lettori.
2. Se è su rete/cloud, prova prima a scaricarlo manualmente.
3. Assicurati che l'estensione del file sia esattamente `.epub`.

---

## Problemi con gli stream Internet

### Lo stream non parte / si riproduce per un secondo e poi si ferma

**Possibili cause:**
1. L'URL è morto o reindirizza a un protocollo diverso.
2. Il server richiede l'autenticazione (non supportata).
3. Il testo in chiaro http:// è bloccato da una VPN o da una rete aziendale.

**Soluzione:**
- Tocca **Riprova** nella finestra di stream non disponibile per riprovare.
- Verifica l'URL in un browser.
- Disattiva temporaneamente la VPN per testare.
- Se lo stream reindirizza e continua a fallire, tocca **Rimuovi** e riaggiungi l'URL corretto.

### La rotellina di importazione del catalogo non si ferma / resta bloccata

L'app applica un timeout rapido per i download del catalogo. Se la rotellina resta bloccata per più di ~15 secondi, l'host è probabilmente irraggiungibile. Controlla la tua connessione Internet e riprova. La finestra si chiuderà automaticamente al timeout - non resterà bloccata all'infinito.

### HLS / DASH / RTSP mostra il messaggio "non supportato"

In **Standard**, **Legacy** e **XR / noLegal** tutti e tre i protocolli sono supportati, quindi questo messaggio indica un problema con lo stream o il suo codec, non con la build. **Lite** e **Photos** non hanno affatto la schermata Streams, quindi nessuno stream può essere aggiunto lì in primo luogo.

### L'opzione Streams non è visibile nel menu o nelle impostazioni

- In **Standard / Legacy / XR / noLegal**: vai su **Impostazioni > Media > Streams** e assicurati che **Attiva Streams** sia attivato. La voce nel menu a tendina appare solo quando Streams è attivo.
- In **Photos**: la funzionalità Streams non è integrata in questa versione.
- In **Lite**: la funzionalità Streams non è integrata nemmeno in questa versione - non c'è alcun interruttore da attivare né alcuna schermata da aprire.

### I metadati ICY del brano in riproduzione non vengono mostrati

I metadati ICY richiedono uno stream Icecast/Shoutcast che invii l'header `Icy-MetaData: 1`. Gli stream mp3 http semplici senza header ICY non mostrano informazioni su stazione/traccia nel mini-controllo in basso. Si tratta di una limitazione lato server.

---

## Problemi con i contenuti

### ❌ "Non riesco a vedere i file di testo o PDF"

**Soluzione:**
1. Controlla **Impostazioni** → **Media** → **Documenti**
2. Assicurati che **"Supporto file di testo"** e **"Supporto file PDF"** siano attivati.
3. Controlla i **Filtri** nella schermata principale (icona a imbuto) per assicurarti che siano selezionati.
4. **Riscansiona** la cartella (trascina verso il basso per aggiornare).

---

## Limitazioni note

- ⚠️ **Nessun supporto per foto RAW** (CR2, NEF, ARW)
- ⚠️ **Annullamento non disponibile in rete** (i file vengono eliminati definitivamente)
- ⚠️ **L'archiviazione cloud è integrata in tutte le versioni tranne Lite**; quali provider offre una determinata build può comunque dipendere dalla piattaforma del dispositivo, e [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) è la griglia per versione
- ⚠️ **Nessuna sincronizzazione multi-dispositivo** (i preferiti sono locali)

---

**Ultimo aggiornamento:** 2026-06-05  
**Versione:** set attuale della documentazione pubblica

</div>
