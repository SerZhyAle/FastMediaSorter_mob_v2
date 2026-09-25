---
layout: default
title: "Backup fotografico pianificato sul PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-camera-backup-it.html
---
<div lang="it" dir="ltr" markdown="1">

# 📷 Backup fotografico pianificato sul PC

> **Livello:** Principiante &bull; **Durata:** ~15 minuti di configurazione &bull; **Edizione:** Standard, Photos, Legacy, VR, noLegal (servono fonti di rete - Lite non ne ha)

{% include lang-switcher.html doc="scenario-camera-backup" dir="/docs/howto/" current="it" %}

Copia automaticamente le nuove foto della fotocamera del telefono sul tuo computer di casa **ogni notte, via Wi-Fi**. Configuralo una volta sola: funzionerà per sempre senza alcuna azione manuale.

**Cosa ottieni:** ogni mattina ti svegli e le foto dell'ultima notte sono già sul tuo PC. Niente cavi. Nessun abbonamento cloud. Niente dimenticanze. Completamente automatico.

---

## Cosa ti serve

- Telefono e PC collegati alla **stessa rete Wi-Fi di casa** (stesso router)
- Una cartella sul PC dove salvare le foto (ad es. `C:\PhoneBackup`)
- FastMediaSorter installato in un'edizione con fonti di rete (**Standard**, Photos, Legacy, VR, noLegal)

> **Non sei sicuro di quale edizione hai?** Apri **Impostazioni** <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> → **Informazioni di sistema**. La riga **Edizione** indica la variante (Standard / Lite / ecc.).

---

## Passo 1 - Crea una cartella di backup sul tuo PC

Per prima cosa crea una cartella sul tuo PC. Poi condividila in modo che il telefono possa raggiungerla.

Su **Windows:**
1. Crea una nuova cartella ovunque - ad esempio `C:\PhoneBackup`
2. **Tasto destro** sulla cartella → **Proprietà** → scheda **Condivisione** → clic su **Condividi..**
3. Nel menu a tendina, seleziona il tuo nome utente o digita **Everyone** → clic su **Aggiungi** → clic su **Condividi**
4. Windows mostra il percorso di rete - annotalo. Ha una forma simile a: `\\MYPC\PhoneBackup`

> **Annota anche l'indirizzo IP del tuo PC** - ti servirà al Passo 2. Il modo più rapido: premi **Win + R**, digita `cmd`, premi Invio. Nella finestra nera digita `ipconfig` e premi Invio. Trova la riga **Indirizzo IPv4** sotto il tuo adattatore Wi-Fi. Esempio: `192.168.1.100`. Annota quel numero.

---

## Passo 2 - Collega l'app alla cartella del tuo PC

Ora indica a FastMediaSorter dove inviare le foto.

> **Cos'è SMB?** È semplicemente il modo in cui Windows condivide le cartelle sulla Wi-Fi di casa. Non serve capirne i dettagli - basta seguire i passaggi.

1. Apri l'app → tocca **Aggiungi <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** nella barra degli strumenti in alto → seleziona **"Cartella di rete (SMB)"**
2. Nel campo **Server / Percorso** inserisci: `\\192.168.1.100\PhoneBackup`
   - Sostituisci `192.168.1.100` con l'IP effettivo del tuo PC dal Passo 1
   - Sostituisci `PhoneBackup` con il nome effettivo della tua cartella
3. Inserisci il tuo **nome utente** e la **password** di Windows (gli stessi che usi per accedere al PC)
4. Tocca **Connessione di prova** - attendi qualche secondo - dovresti vedere un messaggio verde di successo
5. Tocca **Salva**

> **Non riesci a connetterti?** Consulta la [Guida alla configurazione SMB](scenario-smb-setup-it.md) - copre ogni problema di connessione comune con soluzioni passo dopo passo.


---

## Passo 3 - Apri le impostazioni delle operazioni pianificate

1. Tocca **Impostazioni** (l'icona a ingranaggio <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> nella barra degli strumenti)
2. Vai alla scheda **Gestione**
3. Scorri fino a **"Operazioni pianificate"** e tocca **"Operazioni file pianificate"** - si apre la schermata delle operazioni pianificate
4. Attiva **"Usa operazioni pianificate"** in cima a quella schermata

![Impostazioni → Gestione - sezione Pianificazione con il pulsante AGGIUNGI](screenshots/screenshot-cb-step3.png)

---

## Passo 4 - Crea una nuova pianificazione

Tocca il pulsante **+** (**"Aggiungi"**) nella schermata delle operazioni pianificate.

Si apre la finestra di dialogo per una nuova pianificazione.

![Finestra Aggiungi pianificazione - sezione Condizioni: intervallo e opzioni di sovrascrittura](screenshots/screenshot-cb-step4.png)

---

## Passo 5 - Compila la pianificazione del backup

Compila ogni campo:

| Campo | Cosa impostare | Esempio |
|-------|------------|---------|
| **Nome** | Un'etichetta qualsiasi per riconoscere questa pianificazione | `Backup notturno fotocamera` |
| **Origine** | Dove si trovano le foto della tua fotocamera | Seleziona **"Foto fotocamera"** - trova automaticamente tutti gli scatti della fotocamera |
| **Destinazione** | La tua cartella di backup sul PC | Seleziona la risorsa SMB che hai appena aggiunto (`PhoneBackup (SMB)`) |
| **Operazione** | Cosa fare con i file | **"Copia (salta esistenti)"** - copia solo le foto nuove, senza mai duplicare |
| **Pianificazione** | Quando eseguire | `Ogni giorno alle 02:00` - viene eseguita mentre dormi |
| **Esegui solo su Wi-Fi** | Attiva questa opzione | Impedisce che il backup usi accidentalmente i dati mobili |

> **Cosa sono le "Foto fotocamera"?** È una cartella virtuale speciale che FastMediaSorter crea automaticamente. Mostra sempre tutte le foto scattate dalla tua fotocamera, anche se sono archiviate in cartelle diverse sul telefono. Preferisci sempre questa opzione a un percorso manuale.

![Aggiungi pianificazione - Origine: Foto fotocamera, Operazione: Copia, Destinazione: SMB](screenshots/screenshot-cb-step5.png)

---

## Passo 6 - Salva e consenti l'accesso in background

Tocca **Salva**.

La pianificazione compare nell'elenco: ora è attiva.

**Potresti vedere una finestra di autorizzazione.** L'app chiede di essere esclusa dal risparmio energetico. Tocca **"Disattiva ottimizzazione"** (o **"Consenti"**).

> **Perché questo passaggio è importante?** Android cerca di risparmiare batteria fermando automaticamente le app che girano in background. Senza questo permesso, Android potrebbe interrompere il backup nel cuore della notte. Concedere questo permesso permette solo all'app di svegliarsi all'orario pianificato - non consuma batteria in modo percepibile.

![Voce di pianificazione salvata: Foto fotocamera → SMB all'orario pianificato](screenshots/screenshot-cb-step6.png)

---

## Passo 7 - Provalo subito

Non aspettare le 2 del mattino: testa subito il backup per assicurarti che tutto funzioni:

1. Vai su **Impostazioni → Gestione → Operazioni file pianificate**
2. Trova la tua pianificazione → tocca **"Esegui ora"**
3. In alto sullo schermo appare una notifica che mostra l'avanzamento del trasferimento
4. Quando finisce: tocca la risorsa SMB (`PhoneBackup`) → le tue foto della fotocamera dovrebbero essere visibili lì

> **Non è stato copiato nulla?** Se tutte le tue foto sono già nella cartella di backup (o la cartella della fotocamera del telefono è vuota), l'app copia correttamente zero file. Prova a scattare una nuova foto di prova e riesegui.


---

## Fatto! Ecco cosa succede ogni notte

1. Alle 02:00 l'app si risveglia silenziosamente
2. Controlla la cartella della fotocamera e la confronta con la cartella di backup sul PC
3. Copia solo le foto che non sono ancora sul PC - richiede da pochi secondi a qualche minuto
4. Mostra una notifica: "Backup di 12 file" (o quanti ne sono di nuovi)
5. Torna a dormire

Il tuo PC riceve nuove foto ogni mattina. Non devi mai pensarci.

---

## Suggerimenti

> **Vuoi liberare spazio sul telefono dopo il backup?** Cambia l'Operazione in **"Sposta"** invece di "Copia". Le foto vengono eliminate dal telefono subito dopo essere state copiate in sicurezza sul PC. Usa questa opzione con attenzione: una volta spostate, le foto non sono più sul telefono.

> **Hai più telefoni in famiglia?** Crea una pianificazione per ogni telefono. Usa sottocartelle diverse come destinazioni - ad esempio `PhoneBackup\Mamma` e `PhoneBackup\Papà` - così tutti i dispositivi fanno il backup sullo stesso PC senza mescolare i file.

> **Preferisci fare il backup su Google Drive?** Aggiungi una risorsa Google Drive come destinazione invece di SMB - il resto dei passaggi è identico.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| La pianificazione non viene eseguita di notte | Vai su **Impostazioni Android → App → FastMediaSorter → Batteria** → imposta su **Nessuna restrizione** |
| Errore "Destinazione non raggiungibile" | Il telefono deve essere connesso al Wi-Fi all'ora del backup. Se il Wi-Fi era spento alle 2 del mattino, il backup viene saltato e riprovato automaticamente la notte successiva |
| Alcune foto non sono state salvate nel backup | Usa la risorsa virtuale **"Foto fotocamera"** come origine - cattura le foto da tutte le cartelle della fotocamera sul telefono |
| Compaiono file duplicati sul PC | Assicurati che l'Operazione sia impostata su **"Copia (salta esistenti)"**, non su "Copia (sovrascrivi)" |

</div>
