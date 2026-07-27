# Screenshot Shooting Guide - How-To Scenario Guides

All screenshots go to: `docs/howto/screenshots/`

---

## Master Table - All Screenshots

| File name | Scenario | Step | What must be visible on screen | Source |
|-----------|----------|------|-------------------------------|--------|
| `screenshot-smb-step1.png` | SMB Setup | 1 | FastMediaSorter main screen; **Add (⊕)** button in the top toolbar must be clearly visible | 📸 Phone/tablet |
| `screenshot-smb-step2.png` | SMB Setup | 2 | "Add Resource" type selection screen with options list (Local, SMB, SFTP, Cloud..) | 📸 Phone/tablet |
| `screenshot-smb-step3.png` | SMB Setup | 3 | SMB form with **IP address entered manually** in the Server field (e.g. `192.168.1.100`) and Share name filled (e.g. `Photos`); no scan used | 📸 Phone/tablet |
| `screenshot-smb-step4.png` | SMB Setup | 4 | Green **"Connection successful"** toast or banner after Test Connection | 📸 Phone/tablet (needs real SMB) |
| `screenshot-smb-step5.png` | SMB Setup | 5 | Main screen with the new SMB resource card visible (SMB badge on card) | ♻️ Rename `Screenshot_20260415_012148.png` |
| `screenshot-dl-step1.png` | Download Organizer | 1 | Main screen with a **Downloads** resource card just added; Local badge visible | 📸 Phone/tablet |
| `screenshot-dl-step3.png` | Download Organizer | 3 | **Settings → Operations** tab, Quick Sort section visible | ♻️ Rename `Screenshot_20260415_012410.png` |
| `screenshot-dl-step4.png` | Download Organizer | 4 | Quick Sort configuration list with 3-4 folders, each with a numbered color badge (1, 2, 3..) | 📸 Phone/tablet |
| `screenshot-dl-step5.png` | Download Organizer | 5 | Browse screen showing the Downloads folder file list | ♻️ Rename `Screenshot_20260415_012249.png` |
| `screenshot-dl-step6.png` | Download Organizer | 6 | Full-screen file viewer with **command panel visible at bottom**; numbered color buttons (1, 2, 3) clearly shown | 📸 Phone/tablet |
| `screenshot-dl-step7.png` | Download Organizer | 7 | **Settings → Playback → Touch Zones** section visible | ♻️ Rename `Screenshot_20260415_012354.png` |
| `screenshot-pf-step1.png` | Photo Frame | 1 | Main screen with a **photo resource card** just added (photo thumbnail or folder icon) | 📸 Phone/tablet |
| `screenshot-pf-step2.png` | Photo Frame | 2 | Folder **Edit** screen showing **Slideshow Interval** field (e.g. "5") and **Include Subfolders** toggle ON | 📸 Phone/tablet |
| `screenshot-pf-step3.png` | Photo Frame | 3 | Settings screen with **Slideshow Background Music** toggle ON and "Select Music Source" button visible | 📸 Phone/tablet |

| `screenshot-pf-step4.png` | Photo Frame | 4 | A photo displayed **full-screen** with no UI chrome; or slideshow visibly running (▶ in toolbar) | 📸 Phone/tablet |
| `screenshot-pf-step5.png` | Photo Frame | 5 | **Settings → General → System** tab; **"Prevent Sleep"** toggle is **ON** | ♻️ Rename `Screenshot_20260415_012128.png` |

| `screenshot-pf-step6.png` | Photo Frame | 6 | Android **widget picker** showing FastMediaSorter widgets listed; or a "Resource Shortcut" widget placed on home screen | 📸 Phone/tablet |
| `screenshot-cb-step2.png` | Camera Backup | 2 | SMB add-resource form filled in with a backup folder path, e.g. `\\192.168.1.100\PhoneBackup` | 📸 Phone/tablet |
| `screenshot-cb-step3.png` | Camera Backup | 3 | **Settings → Operations** tab with the **Schedule** section visible (label "Schedule" must be readable) | 📸 Phone/tablet |

| `screenshot-cb-step4.png` | Camera Backup | 4 | New schedule configuration dialog/screen - empty or just opened | 📸 Phone/tablet |
| `screenshot-cb-step5.png` | Camera Backup | 5 | Schedule form fully filled in: source = **Camera Photos**, destination = SMB resource, time = **02:00** | 📸 Phone/tablet |
| `screenshot-cb-step6.png` | Camera Backup | 6 | Completed schedule entry in the Schedule **list** showing source → destination arrow and scheduled time | 📸 Phone/tablet (after save) |

| `screenshot-cb-step7.png` | Camera Backup | 7 | SMB Browse screen showing the PhoneBackup folder with actual photos inside (proof backup ran) | 📸 Phone/tablet (needs real backup run) |
| `screenshot-car-step1.png` | Car Music | 1 | Local folder picker showing internal storage or SD card with a **Music** folder visible and selectable | 📸 Phone/tablet |
| `screenshot-car-step2.png` | Car Music | 2 | Folder **Edit** screen with **Profile** dropdown showing **"Audio Library"** selected | 📸 Phone/tablet |
| `screenshot-car-step3.png` | Car Music | 3 | Audio player **full-screen** with album art and playback controls visible | ♻️ Rename `Screenshot_20260415_012436.png` |
| `screenshot-car-step4.png` | Car Music | 4 | **Settings → Media → Audio** section visible | ♻️ Rename `Screenshot_20260415_012338.png` |
| `screenshot-car-step5.png` | Car Music | 5 | **Settings → Playback** or similar screen showing **steering wheel button mapping** or "Commands" section | 📸 Phone/tablet |
| `screenshot-car-step6.png` | Car Music | 6 | Main screen showing the **"All Music"** virtual resource card | ♻️ Reuse `Screenshot_20260415_012148.png` (same shot) |
| `screenshot-car-done.png` | Car Music | Done | Audio player with the **command panel overlay** visible (floating mini-player strip) | ♻️ Rename `Screenshot_20260415_012423.png` |

| `screenshot-hc-step2.png` | Home Cinema | 2 | SMB connection form with a **video share path**, e.g. `\\192.168.1.100\Series` | 📸 Phone/tablet |
| `screenshot-hc-step3.png` | Home Cinema | 3 | Browse screen showing a video folder with **episode thumbnails** or file names with video icons | 📸 Phone/tablet (needs SMB with video files) |
| `screenshot-hc-step5.png` | Home Cinema | 5 | Video **player full-screen** with a video actively playing, progress bar visible | ♻️ Rename `Screenshot_20260415_012507.png` |

---

## Summary

| | Count |
|-|-------|
| ♻️ Already have - rename only | **9** |
| 📸 Session A - in app, no network needed (~20 min) | **16** |
| 📸 Session B - needs live SMB connection (~20 min) | **5** |
| **Total** | **30** |

### Session A - no network needed
`smb-step1`, `smb-step2`, `smb-step3` (real IP in form, no scan) · `dl-step1`, `dl-step4`, `dl-step6` · `pf-step1`, `pf-step2`, `pf-step3`, `pf-step4`, `pf-step6` · `car-step1`, `car-step2`, `car-step5` · `cb-step3`, `cb-step4`, `cb-step5`

### Session B - needs live SMB
`smb-step4` (connection success toast) · `hc-step2`, `hc-step3` · `cb-step2`, `cb-step6`, `cb-step7`

### Renames (copy from `store_assets/screenshots/`)
```
Screenshot_20260415_012128.png  →  screenshot-pf-step5.png
Screenshot_20260415_012148.png  →  screenshot-smb-step5.png  (also screenshot-car-step6.png)
Screenshot_20260415_012249.png  →  screenshot-dl-step5.png
Screenshot_20260415_012338.png  →  screenshot-car-step4.png
Screenshot_20260415_012354.png  →  screenshot-dl-step7.png
Screenshot_20260415_012410.png  →  screenshot-dl-step3.png
Screenshot_20260415_012423.png  →  screenshot-car-done.png
Screenshot_20260415_012436.png  →  screenshot-car-step3.png
Screenshot_20260415_012507.png  →  screenshot-hc-step5.png
Screenshot_20260415_012039.png  →  screenshot-cb-battery.png
```

---

*After placing a file, replace `[SCREENSHOT: filename.png]` in the corresponding `.md` with `![description](screenshots/filename.png)`*
