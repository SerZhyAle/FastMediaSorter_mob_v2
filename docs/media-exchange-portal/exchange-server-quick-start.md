---
layout: doc
title: "Deploy your own exchange server: step by step"
description: "The short list of steps to run your own exchange server on a Windows PC: install the package, answer the two Windows prompts, set the address, create the first account, add the server on the phone, check it from mobile data and invite people."
category: "Media exchange"
permalink: /docs/media-exchange-portal/exchange-server-quick-start.html
---
<sub class="doc-stamp">26.10.22 01:25</sub>

# Deploy your own exchange server

<img src="exchange-mark/full-256.png" alt="Media Exchange Server" width="128">

This is the short list. The reasons and the details are on [Installing and checking your exchange server](exchange-server-install.html). What the server is for: [What the exchange server is for](exchange-server-purpose.html).

## Before you start {#before-you-start}

- A 64-bit Windows 10 PC (version 1607 or newer) that can stay on.
- An address your devices can reach - step 5.
- Nothing paid: no hosting, no certificate, no subscription.

## Steps {#steps}

1. **Get the package.** Install Fast Media Sorter & Sharing for Windows with the exchange server ticked: the Server edition (winget `SerZhyAle.FastMediaSorter.Server`, or the setup file from the [Server edition page](https://serzhyale.github.io/FastMediaSorter_Lite/server.html)) for a PC that only serves, or the full package for a PC you also work on - see [Windows servers](windows-servers.html#availability). Not from the Microsoft Store: a Store app cannot run as a Windows service.
2. **Answer Windows.** On **"Windows protected your PC"** click **More info**, then **Run anyway**. On **"Do you want to allow this app to make changes to your device?"** click **Yes**: it adds the Windows service and one firewall rule for the server program. The installer is not signed, so Windows warns.
3. **Open Share Manager** and go to **Media Exchange Server**. On a fresh install the first-run guide walks you through the port, the firewall and service choices, the address on the internet, the first account and the security certificate.
4. **Create the first account.** It is your own account, and the PC joins it by itself.
5. **Give the server an address.** Take the first that fits:
   - a fixed address on the internet from your provider, or a free name such as `myhome.example.net` that follows a changing address (a "dynamic DNS" service), with one port forwarded in the router to this PC - see [Home network](home-network.html);
   - IPv6 (a newer kind of internet address), when your provider shares one address among many customers - no forwarding is needed, but a device with no IPv6 cannot reach the server;
   - neither: the server then works on your own network or through a VPN into it.
6. **Get the connection string.** In Share Manager choose **Get connection string..**: it gives a text, a `.fmsexch` file and a QR code of the same string. It holds the server's address and the fingerprint of its certificate (a short code that identifies the server), never a login or a password.
7. **Add the server on the phone.** In FastMediaSorter open **Settings**, the **General** tab, **My devices**, then tap **Add server**. Paste the string, or **Scan QR**, or **Choose file**, then enter the login and the password. The server's card says **Connected, devices: 1** or more. More: [My devices](my-devices.html).
8. **Check it from another network.** Switch the phone to mobile data and open **My devices** again. A card that connects proves the server is reachable from outside.
9. **If it does not connect**, the app says at which step it broke. Check in this order:
   - the PC with the server is asleep or off;
   - the port is not forwarded, or the provider blocks incoming connections;
   - the network offers only IPv4 or only IPv6 where the address needs the other;
   - the Windows network is public - set it to private;
   - a VPN is on, on the device or on the PC;
   - the router's guest network keeps the devices apart;
   - the date and time on a device are wrong.
10. **Invite people** if you want to: make an invitation in Share Manager for each relative or friend. See [Accounts, invitations and passwords](exchange-accounts.html).

## Keep it running {#keep-it-running}

- Set sleep to **Never** in the Windows power settings: a sleeping PC answers nobody.
- Leave automatic time on: the certificate and the sign-ins depend on it.
- Update by installing over: the accounts, the devices and the certificate key survive.
- Tell the people you invited before you switch the PC off for long.

## Take it back {#take-it-back}

Open Windows Settings, Apps, and uninstall the entry of the package. It removes the service and the firewall rule the installer added.

Back to the [exchange server](exchange-server.html) or to the [portal start page](index.html).
