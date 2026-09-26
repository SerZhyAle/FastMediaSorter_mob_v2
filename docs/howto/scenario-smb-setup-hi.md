---
layout: default
title: "NAS / Windows शेयर (SMB) से जुड़ें - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-hi.html
---
<div lang="hi" dir="ltr" markdown="1">

# 🖥️ होम NAS / Windows शेयर (SMB) से जुड़ें

> **लेवल:** शुरुआती &bull; **एडिशन:** Standard, Photos, Legacy, VR, noLegal (Lite में नेटवर्क सोर्स नहीं हैं)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="hi" %}

SMB (जिसे Windows File Sharing या CIFS भी कहते हैं) आपको अपने होम पीसी, लैपटॉप, या NAS डिवाइस पर मौजूद फ़ाइलें बिल्कुल वैसे ब्राउज़ करने देता है जैसे वे आपके फोन पर हों - कोई केबल नहीं, कोई USB नहीं, बस Wi-Fi.

> **आसान भाषा में समझें:** मान लीजिए आपके पीसी में आपके घरेलू Wi-Fi पर एक पब्लिक नोटिस बोर्ड है. घर का कोई भी डिवाइस उस बोर्ड को पढ़ सकता है. FastMediaSorter उस "बोर्ड" (आपके शेयर किए गए फ़ोल्डर) से जुड़ता है और आपको अपनी फ़ाइलें ऐसे ब्राउज़ करने देता है जैसे वे सीधे आपके फोन में सेव हों. कुछ भी पहले से कॉपी या डाउनलोड नहीं होता - फ़ाइलें ज़रूरत पड़ने पर खुलती हैं.

---

## आपको क्या चाहिए होगा

- आपका फोन और पीसी / NAS **एक ही Wi-Fi नेटवर्क** (एक ही राउटर) पर
- आपके पीसी या NAS का **IP एड्रेस** (जैसे `192.168.1.100`)
- **शेयर का नाम** (आपने जो फ़ोल्डर शेयर किया है उसका नाम, जैसे `Photos`)
- उस शेयर के लिए एक **यूज़रनेम और पासवर्ड** (या गेस्ट एक्सेस अगर ऑन है)

> **इन शब्दों के बारे में पक्का नहीं हैं?** चिंता न करें - चरण 1 और 2 बताते हैं कि ये कहां मिलेंगे.

---

## चरण 1 - अपने पीसी का IP एड्रेस ढूंढें

IP एड्रेस आपके Wi-Fi नेटवर्क पर आपके पीसी का "घर का पता" होता है. इसकी ज़रूरत है ताकि आपके फोन को पता चले कि कहां देखना है.

**Windows** पर:
1. `Win + R` दबाएं, `cmd` टाइप करें, Enter दबाएं - एक काली टेक्स्ट विंडो खुलती है
2. `ipconfig` टाइप करें और Enter दबाएं
3. अपने Wi-Fi एडाप्टर के नीचे **IPv4 Address** ढूंढें - कुछ ऐसा `192.168.1.100`

> जिस लाइन की आपको ज़रूरत है उसे **"IPv4 Address"** कहा जाता है (IPv6 नहीं, जो अक्षरों और नंबरों की एक लंबी सीरीज़ जैसी दिखती है). ज़्यादातर होम नेटवर्क पर यह `192.168.` से शुरू होनी चाहिए.

**NAS** (Synology, QNAP, आदि) पर:
- NAS वेब पैनल → Network settings खोलें - IP वहीं दिखता है

> IP लिख लें - इसकी ज़रूरत चरण 6 में होगी.

![Windows PowerShell - ipconfig output, IPv4 Address `192.168.1.100` visible](screenshots/screenshot-smb-step1.png)

---

## चरण 2 - अपने पीसी पर शेयर का नाम ढूंढें

"शेयर का नाम" नेटवर्क पर आपके फ़ोल्डर का सार्वजनिक नाम है. यह फ़ोल्डर के नाम जैसा ही हो सकता है, या अलग.

**Windows** पर:
1. **File Explorer** खोलें
2. जिस फ़ोल्डर को शेयर करना है उस पर राइट-क्लिक करें → **Properties**
3. **Sharing** टैब पर जाएं
4. **Network Path** देखें - यह `\\DESKTOP-ABC\Photos` जैसा दिखता है
5. आखिरी `\` के बाद का हिस्सा आपका **शेयर का नाम** है (यहां: `Photos`)

> **फ़ोल्डर अभी शेयर नहीं हुआ?** **Share..** पर क्लिक करें → **Everyone** चुनें → **Add** → **Share**. Windows तुरंत नेटवर्क पथ दिखा देगा.

> **ज़रूरी:** पक्का करें कि Windows में **Network Discovery** और **File Sharing** ऑन हैं. Control Panel → Network and Sharing Center → Change advanced sharing settings पर जाएं → "Network discovery" और "File and printer sharing" ऑन करें.

![Windows folder Properties - Sharing tab, Network Path `\\MARK\Common` visible](screenshots/screenshot-smb-step2.png)

---

## चरण 3 - FastMediaSorter खोलें और "+" टैप करें

1. ऐप खोलें
2. **मुख्य स्क्रीन** पर, टॉप टूलबार में **"Add" <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** बटन टैप करें

![FastMediaSorter main screen - Add button highlighted in top toolbar, SMB tab visible](screenshots/screenshot-smb-step3.png)

---

## चरण 4 - "Network folder (SMB)" चुनें

रिसोर्स टाइप लिस्ट में, **"Network folder (SMB)"** (या SMB टैब) टैप करें.

![Select Folder Type dialog - four options: Local Folder, Network Folder (SMB), SFTP/FTP, Cloud Storage](screenshots/screenshot-smb-step4.png)

---

## चरण 5 - पहले Auto-Discovery आज़माएं

**"Scan Network"** बटन टैप करें. ऐप SMB शेयर वाले डिवाइस के लिए आपका लोकल Wi-Fi स्कैन करेगा.

- ~10 सेकंड रुकें
- मिले हुए डिवाइस की एक लिस्ट दिखती है
- अपना पीसी या NAS टैप करें - IP एड्रेस अपने आप भर जाता है

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **कुछ नहीं मिला?** कोई बात नहीं - चरण 6 पर जाएं और IP मैनुअली टाइप करें. ऐसा तब होता है जब आपका राउटर AP Isolation इस्तेमाल करता है (सुरक्षा के लिए फोन-से-पीसी संचार रोकने वाली एक सेटिंग). मैनुअल IP हमेशा काम करता है.

---

## चरण 6 - कनेक्शन की जानकारी भरें

फ़ॉर्म भरें:

| फ़ील्ड | क्या डालें | उदाहरण |
|-------|--------------|---------|
| **Server / Path** | `\\IP\ShareName` | `\\192.168.1.100\Photos` |
| **Username** | आपका Windows लॉगिन नाम | `john` |
| **Password** | आपका Windows पासवर्ड | `••••` |
| **Display Name** | आपको पसंद कोई भी नाम (वैकल्पिक) | `Home PC - Photos` |

> **Windows में लॉगिन के लिए Microsoft अकाउंट (ईमेल) इस्तेमाल करते हैं?** यूज़रनेम के तौर पर अपना **पूरा ईमेल एड्रेस** इस्तेमाल करें (जैसे `john@outlook.com`), सिर्फ़ पहला नाम नहीं. आपका पासवर्ड वही है जो आप अपना पीसी अनलॉक करने के लिए टाइप करते हैं.

> **पासवर्ड नहीं है, या Guest इस्तेमाल कर रहे हैं?** Username और Password खाली छोड़कर Test Connection टैप करें - कुछ होम पीसी ओपन एक्सेस की अनुमति देते हैं.

![Add Network Folder (SMB) - Server IP `192.168.1.100`, ShareName and credentials filled in](screenshots/screenshot-smb-step6.png)

![Add Network Folder (SMB) - lower section: options, media types, ADD THIS RESOURCE button](screenshots/screenshot-smb-step6b.png)

**पता फ़ॉर्मेट संदर्भ:**

| फ़ॉर्मेट | उदाहरण |
|--------|---------|
| मानक Windows | `\\192.168.1.100\Photos` |
| Linux / macOS शैली | `smb://192.168.1.100/Photos` |
| सबफ़ोल्डर | `\\192.168.1.100\Media\Movies` |
| कस्टम पोर्ट | `smb://192.168.1.100:445/Photos` |

---

## चरण 7 - कनेक्शन टेस्ट करें

**"Test Connection"** टैप करें.

- **हरा संदेश** = सफलता → चरण 8 पर जाएं! आप लगभग पहुंच गए हैं.
- **लाल संदेश** = कुछ गलत है → नीचे दी गई समस्या निवारण टेबल देखें. सबसे आम समाधान: IP और शेयर के नाम को दोबारा जांचें.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## चरण 8 - सेव करें और खोलें

**"Save"** टैप करें. नया फ़ोल्डर एक SMB बैज के साथ मुख्य स्क्रीन पर दिखता है.

इसे ब्राउज़ करने के लिए टैप करें - फ़ोटो, वीडियो, और अन्य फ़ाइलें किसी भी लोकल फ़ोल्डर की तरह थंबनेल के रूप में दिखती हैं.

![FastMediaSorter main screen - new "Common" SMB resource card (smb://192.168.1.100/Common) with Network folder SMB badge highlighted](screenshots/screenshot-smb-step8.png)

---

## हो गया! अब आप यह कर सकते हैं..

- अपने फोन से अपने पीसी की सभी फ़ाइलें ब्राउज़ करें
- वीडियो और म्यूज़िक सीधे चलाएं - डाउनलोड की ज़रूरत नहीं
- अपने फोन और पीसी के बीच फ़ाइलें कॉपी या मूव करें
- इस फ़ोल्डर को स्लाइडशो, फोटो फ्रेम, या कार म्यूज़िक के सोर्स के रूप में इस्तेमाल करें

---

## समस्या निवारण

| समस्या | क्या करें |
|---------|------------|
| "Connection refused" | Windows Firewall खोलें → इनबाउंड **TCP पोर्ट 445** की अनुमति दें. या टेस्ट के लिए फ़ायरवॉल को अस्थायी रूप से बंद करें |
| "Wrong password" | **Username खाली** छोड़ने की कोशिश करें (गेस्ट एक्सेस). या अगर आप Microsoft अकाउंट इस्तेमाल करते हैं, तो यूज़रनेम के तौर पर अपना **पूरा ईमेल** डालें |
| "Host not found" | पक्का करें कि फोन और पीसी **एक ही Wi-Fi** और एक ही राउटर पर हैं. AP Isolation (राउटर की एक सुरक्षा सेटिंग) इसे रोक सकती है - राउटर सेटिंग्स में इसे बंद करने की कोशिश करें |
| स्कैन में कुछ नहीं मिलता | फोन पर VPN बंद करें. यह भी जांचें कि Windows में **Network Discovery** ऑन है (Control Panel → Network and Sharing Center). फिर IP मैनुअली डालने की कोशिश करें |
| ब्राउज़िंग बहुत धीमी है | रिसोर्स पर **Edit** टैप करें → असली स्पीड देखने के लिए **Speed Test** चलाएं. धीमे कनेक्शन के लिए वीडियो थंबनेल बंद करें |
| Wi-Fi पर काम करता है पर मोबाइल डेटा पर नहीं | यह सामान्य है - SMB सिर्फ़ एक लोकल नेटवर्क प्रोटोकॉल है. यह मोबाइल डेटा पर काम नहीं कर सकता |

→ और मदद: [TROUBLESHOOTING.md](../TROUBLESHOOTING-hi.md)

</div>
