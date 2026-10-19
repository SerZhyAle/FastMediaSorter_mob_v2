# Pointer - `EXCHANGE-MARK`

| | |
| --- | --- |
| **Id** | `EXCHANGE-MARK` |
| **Version** | 0.3, active - 2026-10-19 (S4395: mark adopted across phone, watch, portal and documentation; proper name "Media Exchange Server"); 0.2 - 2026-10-19 (small 24dp vector and mono glyph, high-contrast, binary assets); 0.1 - 2026-10-19 (first version) |
| **Home** | `exchange-mark/README.md` in the shared contracts catalog |
| **Role here** | consumer; publisher of the portal "Media exchange between your devices" (`docs/media-exchange-portal/`); vendor of artwork (`android/ic_exchange_mark.xml`, `android/ic_exchange_mark_small.xml`, `android/ic_exchange_mono.xml`, PNG set) |

## What this repository owes it once implementations exist

- Show the mark of `EXCHANGE-MARK` at every surface that names or shows the exchange server (Rule 8):
  - screen or dialog where an account is added, signed in to or managed (My devices, Admin, sign-in dialog, management dialogs);
  - each account entry card in the list;
  - connection status and failure panel about the server;
  - channel or source kind row in Settings > SFTP (one-tone glyph `ic_exchange_mono`);
  - watch settings list entry (`ic_exchange_mono`) and watch servers screen header (`ic_exchange_mark_small`, untinted).
- Copy artwork files byte for byte from `artwork/` without redrawing, re-exporting, scaling PNG up or altering bytes (Rules 1, 5, 6).
- Use the flat drawing (`mark-16..32`, `ic_exchange_mark_small.xml`) up to 32 px, and faceted drawing (`ic_exchange_mark.xml`) from 33 px (Rule 5).
- Use one-tone glyph `ic_exchange_mono` where the platform or theme allows one colour only (settings rows, watch settings list) and in high-contrast themes (Rule 3).
- Display full form (`full-256.png`) or banner at the head of portal pages whose subject is the exchange server, once, with alt "Media Exchange Server" (Rule 10, Rule 14).
- Display inline mark beside the title of in-app help pages about surfaces that show the mark (Rule 11).
- Accessible name: the full name "Media Exchange Server" for identity head images; the localized term of `DEVICE-EXCHANGE` item CW ("Exchange server" / "Сервер обмена" / "Сервер обміну") for markers (Rule 14).
- Never replace or badge the product's own launcher icon (Rule 12).
- Record the vendored version (0.3) in the catalog's adoption registry (Rule 16).

## Where it lives here

- Phone vector drawables: `app_v2/src/main/res/drawable/ic_exchange_mark.xml`, `ic_exchange_mark_small.xml`, `ic_exchange_mono.xml`.
- Watch vector drawables: `wear/src/main/res/drawable/ic_exchange_mark_small.xml`, `ic_exchange_mono.xml`.
- Portal PNG images: `docs/media-exchange-portal/exchange-mark/`.
- Help images: `app_v2/src/main/assets/help/img/`.

## Ticket

- S4395 - exchange server mark adoption across phone, watch, portal and documentation.
