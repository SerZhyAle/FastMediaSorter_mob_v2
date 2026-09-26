---
layout: default
title: "Collega lo smartwatch a NAS e condivisioni PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-it.html
---
<div lang="it" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Collega lo smartwatch a NAS e condivisioni PC

> **Livello:** Intermedio &bull; **Durata:** ~10 minuti &bull; **Dispositivo:** Smartwatch Wear OS

> **Solo versione completa** - questa guida non è implementata nella versione distribuita tramite Google Play. Si applica alla versione completa, un download diretto dell'APK da [Download](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="it" %}

FastMediaSorter su Wear OS si collega direttamente all'archiviazione di rete di casa tua (NAS, cartelle condivise del PC, server FTP o SFTP) via Wi-Fi. Puoi sfogliare file remoti, trasmettere musica a cuffie Bluetooth e sincronizzare le tue cartelle preferite senza bisogno del telefono.

> **Nuovo alle condivisioni di rete?** Se non hai ancora configurato una cartella condivisa sul tuo PC o NAS, inizia prima con la nostra guida [Collegati a NAS / condivisione Windows (SMB)](scenario-smb-setup-it.md).

---

## Cosa ti serve

- Uno smartwatch con **Wear OS 2.0** o successivo connesso alla tua Wi-Fi di casa
- Una cartella di rete condivisa (condivisione SMB / Windows, server FTP o server SFTP)
- Credenziali di rete: indirizzo IP o nome host, nome della condivisione, nome utente e password
- FastMedia Wear installato sul tuo orologio

---

## Passo 1 - Apri Risorse sul tuo orologio

1. Apri **FastMedia Wear** sul tuo smartwatch.
2. Sulla schermata principale, tocca **Risorse** (icona Wi-Fi).
3. La schermata Risorse mostra le tue connessioni di rete configurate.

![Schermata Risorse su Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Scorciatoia Sincronizza dal telefono:** Se hai già aggiunto le tue condivisioni SMB o SFTP in FastMediaSorter sul telefono Android, tocca **Sincronizza dal telefono** per importare tutte le impostazioni di connessione sull'orologio con un tocco.

---

## Passo 2 - Aggiungi una fonte di rete

1. Sulla schermata Risorse, tocca **Aggiungi risorsa**.
2. Seleziona il tuo protocollo di rete:
   - **SMB**: condivisioni Windows standard, Synology, QNAP o TrueNAS
   - **FTP**: server file FTP standard
   - **SFTP**: server di trasferimento file SSH sicuri (supporta password o chiave privata SSH)
3. Tocca ogni campo per inserire i dettagli di connessione usando la tastiera a schermo dell'orologio:
   - **Nome**: etichetta facoltativa (ad es. "NAS di casa" o "Condivisione musica")
   - **Indirizzo server**: l'IP del tuo computer o NAS (ad es. `192.168.1.50`)
   - **Porta**: porta di rete (predefinita: 445 per SMB, 21 per FTP, 22 per SFTP)
   - **Nome condivisione** (solo SMB): il nome della cartella condivisa sul tuo NAS/PC
   - **Nome utente** e **Password**: le tue credenziali di accesso

![Schermata Aggiungi fonte di rete sull'orologio](screenshots/screenshot-wear-network-step2.png)

---

## Passo 3 - Testa e salva la connessione

1. Scorri fino in fondo al modulo e tocca **Test**.
2. FastMedia Wear verifica il percorso di rete e le credenziali:
   - In caso di successo, la schermata mostra **Connessione riuscita!**.
   - Se c'è un problema, un messaggio di stato amichevole indica cosa correggere (ad es. indirizzo server o password).
3. Tocca **Salva** per memorizzare la fonte di rete sull'orologio.

![Test della connessione di rete e salvataggio della fonte](screenshots/screenshot-wear-network-step3.png)

---

## Passo 4 - Sfoglia e riproduci contenuti multimediali di rete

1. Sulla schermata Risorse, tocca la tua condivisione di rete appena salvata.
2. FastMedia Wear si collega alla condivisione remota e ne elenca il contenuto.
3. Sfoglia cartelle e file in vista elenco o griglia.
4. Tocca un brano audio qualsiasi per avviare la riproduzione nel player a schermo intero. Per le funzioni dettagliate del player e il risparmio batteria, vedi [Ascolta musica sul tuo orologio](scenario-watch-music-it.md).

![Sfoglia file e cartelle sulla condivisione di rete](screenshots/screenshot-wear-network-step4.png)

---

## Fatto! Funzioni di rete su Wear OS

- **Streaming Wi-Fi indipendente**: Trasmette direttamente dal tuo NAS o PC via Wi-Fi senza passare dal telefono.
- **Supporto multi-protocollo**: Supporto completo per SMB, FTP e SFTP con autenticazione tramite password o chiave privata SSH.
- **Sincronizzazione bidirezionale**: Sincronizza le connessioni dal companion sul telefono oppure esporta le fonti dell'orologio verso il telefono.

---

## Risoluzione dei problemi

| Problema | Cosa provare |
|---------|------------|
| Il test di connessione segnala "Connessione fallita" | Verifica che l'orologio sia connesso alla stessa rete Wi-Fi del server, e controlla l'indirizzo IP |
| Errore nome condivisione su SMB | Assicurati di inserire solo il nome della condivisione (ad es. `Musica`), non il percorso completo con le barre |
| Autenticazione fallita | Controlla il tuo nome utente e la password. Sulle condivisioni Windows, assicurati che i permessi di condivisione di rete consentano il tuo account utente |
| Caricamento lento via Wi-Fi | Assicurati che il segnale Wi-Fi dell'orologio sia forte e che il routing di rete 5 GHz / 2,4 GHz verso il server locale non sia bloccato |

</div>
