---
layout: default
title: "Collegati a NAS / condivisione Windows (SMB) - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 🖥️ Collegati a NAS di casa / condivisione Windows (SMB)

> **Livello:** Principiante &bull; **Edizione:** Standard, Photos, Legacy, VR, noLegal (Lite non ha fonti di rete)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="it" %}

SMB (chiamato anche Condivisione file di Windows o CIFS) ti permette di sfogliare i file sul tuo PC, laptop o dispositivo NAS di casa esattamente come se fossero sul tuo telefono - senza cavi, senza USB, solo Wi-Fi.

> **Spiegazione in parole semplici:** Immagina che il tuo PC abbia una bacheca pubblica sulla tua Wi-Fi di casa. Qualsiasi dispositivo in casa può leggere da quella bacheca. FastMediaSorter si collega a quella "bacheca" (la tua cartella condivisa) e ti permette di sfogliare i tuoi file come se fossero archiviati proprio sul telefono. Niente viene copiato o scaricato in anticipo - i file si aprono su richiesta.

---

## Cosa ti serve

- Il tuo telefono e il tuo PC / NAS sulla **stessa rete Wi-Fi** (stesso router)
- L'**indirizzo IP** del tuo PC o NAS (ad es. `192.168.1.100`)
- Il **nome della condivisione** (il nome della cartella che hai condiviso, ad es. `Foto`)
- Un **nome utente e una password** per quella condivisione (o l'accesso ospite se abilitato)

> **Non sei sicuro dei termini?** Non preoccuparti - i Passi 1 e 2 spiegano esattamente dove trovarli.

---

## Passo 1 - Trova l'indirizzo IP del tuo PC

L'indirizzo IP è l'"indirizzo di casa" del tuo PC sulla tua rete Wi-Fi. Ti serve perché il telefono sappia dove cercare.

Su **Windows:**
1. Premi `Win + R`, digita `cmd`, premi Invio - si apre una finestra di testo nera
2. Digita `ipconfig` e premi Invio
3. Cerca **Indirizzo IPv4** sotto il tuo adattatore Wi-Fi - qualcosa come `192.168.1.100`

> La riga che ti serve è etichettata **"Indirizzo IPv4"** (non IPv6, che assomiglia a una lunga serie di lettere e numeri). Sulla maggior parte delle reti domestiche dovrebbe iniziare con `192.168.`

Su un **NAS** (Synology, QNAP, ecc.):
- Apri il pannello web del NAS → Impostazioni di rete - l'IP è mostrato lì

> Annota l'IP - ti servirà al Passo 6.

![Windows PowerShell - output di ipconfig, indirizzo IPv4 `192.168.1.100` visibile](screenshots/screenshot-smb-step1.png)

---

## Passo 2 - Trova il nome della condivisione sul tuo PC

Il "nome della condivisione" è il nome pubblico della tua cartella sulla rete. Può essere uguale al nome della cartella, oppure diverso.

Su **Windows:**
1. Apri **Esplora file**
2. Tasto destro sulla cartella che vuoi condividere → **Proprietà**
3. Vai alla scheda **Condivisione**
4. Guarda il **Percorso di rete** - assomiglia a `\\DESKTOP-ABC\Foto`
5. La parte dopo l'ultimo `\` è il tuo **nome della condivisione** (qui: `Foto`)

> **Cartella non ancora condivisa?** Clic su **Condividi..** → scegli **Everyone** → **Aggiungi** → **Condividi**. Windows ti mostrerà subito il percorso di rete.

> **Importante:** assicurati che **Individuazione rete** e **Condivisione file** siano attivate in Windows. Vai su Pannello di controllo → Centro connessioni di rete e condivisione → Cambia impostazioni di condivisione avanzate → attiva "Individuazione rete" e "Condivisione file e stampanti".

![Proprietà cartella Windows - scheda Condivisione, percorso di rete `\\MARK\Common` visibile](screenshots/screenshot-smb-step2.png)

---

## Passo 3 - Apri FastMediaSorter e tocca "+"

1. Apri l'app
2. Sulla **schermata principale**, tocca il pulsante **"Aggiungi" <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti in alto

![Schermata principale di FastMediaSorter - pulsante Aggiungi evidenziato nella barra degli strumenti, scheda SMB visibile](screenshots/screenshot-smb-step3.png)

---

## Passo 4 - Seleziona "Cartella di rete (SMB)"

Nell'elenco dei tipi di risorsa, tocca **"Cartella di rete (SMB)"** (o la scheda SMB).

![Finestra Seleziona tipo di cartella - quattro opzioni: Cartella locale, Cartella di rete (SMB), SFTP/FTP, Archiviazione cloud](screenshots/screenshot-smb-step4.png)

---

## Passo 5 - Prova prima l'individuazione automatica

Tocca il pulsante **"Scansiona rete"**. L'app scansionerà la tua Wi-Fi locale alla ricerca di dispositivi con condivisioni SMB.

- Attendi ~10 secondi
- Compare un elenco di dispositivi trovati
- Tocca il tuo PC o NAS - l'indirizzo IP si compila automaticamente

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **Non hai trovato niente?** Va bene - passa al Passo 6 e digita l'IP manualmente. Questo succede quando il tuo router usa l'AP Isolation (un'impostazione che blocca la comunicazione telefono-PC per sicurezza). L'IP manuale funziona sempre.

---

## Passo 6 - Compila i dettagli di connessione

Compila il modulo:

| Campo | Cosa inserire | Esempio |
|-------|--------------|---------|
| **Server / Percorso** | `\\IP\NomeCondivisione` | `\\192.168.1.100\Foto` |
| **Nome utente** | Il tuo nome di accesso Windows | `mario` |
| **Password** | La tua password Windows | `••••` |
| **Nome visualizzato** | Un nome qualsiasi a tua scelta (facoltativo) | `PC di casa - Foto` |

> **Usi un account Microsoft (email) per accedere a Windows?** Usa il tuo **indirizzo email completo** come nome utente (ad es. `mario@outlook.com`), non solo il tuo nome. La password è la stessa che digiti per sbloccare il PC.

> **Non hai una password, o usi l'accesso Ospite?** Prova a lasciare vuoti Nome utente e Password e tocca Connessione di prova - alcuni PC di casa consentono l'accesso libero.

![Aggiungi cartella di rete (SMB) - IP server `192.168.1.100`, nome condivisione e credenziali compilati](screenshots/screenshot-smb-step6.png)

![Aggiungi cartella di rete (SMB) - sezione inferiore: opzioni, tipi di media, pulsante AGGIUNGI QUESTA RISORSA](screenshots/screenshot-smb-step6b.png)

**Riferimento per il formato dell'indirizzo:**

| Formato | Esempio |
|--------|---------|
| Windows standard | `\\192.168.1.100\Foto` |
| Stile Linux / macOS | `smb://192.168.1.100/Foto` |
| Sottocartella | `\\192.168.1.100\Media\Film` |
| Porta personalizzata | `smb://192.168.1.100:445/Foto` |

---

## Passo 7 - Prova la connessione

Tocca **"Connessione di prova"**.

- **Messaggio verde** = successo → vai al Passo 8! Sei quasi al traguardo.
- **Messaggio rosso** = qualcosa non va → controlla la tabella Risoluzione dei problemi qui sotto. Soluzione più comune: ricontrolla l'IP e il nome della condivisione.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## Passo 8 - Salva e apri

Tocca **"Salva"**. La nuova cartella compare sulla schermata principale con un distintivo SMB.

Toccala per sfogliarne il contenuto - foto, video e altri file compaiono come miniature, proprio come qualsiasi cartella locale.

![Schermata principale di FastMediaSorter - nuova card risorsa SMB "Common" (smb://192.168.1.100/Common) con distintivo Cartella di rete SMB evidenziato](screenshots/screenshot-smb-step8.png)

---

## Fatto! Ora puoi..

- Sfogliare tutti i file sul tuo PC dal tuo telefono
- Riprodurre video e musica direttamente - senza bisogno di scaricare
- Copiare o spostare file tra il telefono e il PC
- Usare questa cartella come fonte per presentazioni, cornice fotografica o musica in auto

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| "Connessione rifiutata" | Apri il Firewall di Windows → consenti la **porta TCP 445** in entrata. Oppure disattiva temporaneamente il firewall per fare un test |
| "Password errata" | Prova a lasciare **Nome utente vuoto** (accesso ospite). Oppure, se usi un account Microsoft, inserisci la tua **email completa** come nome utente |
| "Host non trovato" | Assicurati che telefono e PC siano sulla **stessa Wi-Fi** e sullo stesso router. L'AP Isolation (un'impostazione di sicurezza del router) può bloccare questo - prova a disattivarla nelle impostazioni del router |
| La scansione non trova nulla | Disattiva la VPN sul telefono. Controlla anche che **Individuazione rete** sia attiva in Windows (Pannello di controllo → Centro connessioni di rete e condivisione). Poi prova a inserire l'IP manualmente |
| Navigazione molto lenta | Tocca **Modifica** sulla risorsa → esegui **Speed Test** per vedere la velocità effettiva. Disattiva le miniature video per le connessioni lente |
| Funziona su Wi-Fi ma non con i dati mobili | Previsto - SMB è un protocollo solo per rete locale. Non può funzionare con i dati mobili |

→ Altro aiuto: [TROUBLESHOOTING.md](../TROUBLESHOOTING-it.md)

</div>
