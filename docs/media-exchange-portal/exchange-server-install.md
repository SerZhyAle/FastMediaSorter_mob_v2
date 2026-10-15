---
layout: doc
title: "Installing and checking your exchange server"
description: "Which Windows package carries the exchange server, what Windows says about the installer and what to click, the first run in Share Manager, the address devices dial, and how to check that the server works from another network."
category: "Media exchange"
permalink: /docs/media-exchange-portal/exchange-server-install.html
---
<sub class="doc-stamp">26.10.15 14:25</sub>

# Installing and checking your exchange server

This page is for the person who runs a server: a Windows PC that stays on, an address your devices can reach, and what to do when Windows warns about an installer that is not signed. If you only join a server somebody else runs, you need none of it - go to [Accounts, invitations and passwords](exchange-accounts.html).

## What you need {#what-you-need}

- A 64-bit Windows 10 PC (version 1607 or newer) that can stay on while the others use the server.
- An address the other devices can reach - see [The address](#the-address).
- Nothing paid: no hosting, no certificate and no subscription. Every way on this page is free.

## Which package {#which-package}

The server is a part of [Fast Media Sorter & Sharing for Windows](windows-servers.html), which comes in two models:

- **The server** - for a PC whose job is to stay available: the server runs as a Windows service that starts with Windows. Get it with winget `SerZhyAle.FastMediaSorter.Server`, or as the setup file from the [Server edition page](https://serzhyale.github.io/FastMediaSorter_Lite/server.html).
- **The viewer** - the ordinary package; the exchange server in it is an optional component that stays off until you switch it on in Share Manager. Right for a PC you also work on.
- **Not the Microsoft Store.** The Store carries only the viewer, and a Store app cannot run as a Windows service.

## What the warning is {#the-warning}

Windows shows up to three things when it meets this installer. The quoted words are what Windows shows you:

- **"Windows protected your PC"** (SmartScreen). It appears the first time you run an installer that Windows has not seen often.
- **"Do you want to allow this app to make changes to your device?"** (the administrator prompt). Switching the server on asks for it once, and it does two things:
  - **Registers the server as a Windows service**, so that it starts with Windows and works with nobody signed in.
  - **Adds one firewall rule**, limited to the server program, so that your devices can reach this PC. Without it Windows closes the way silently.
- **An antivirus warning.** Some antivirus programs treat a new unsigned program cautiously, and a program that listens on a network port and registers itself as a service is exactly what they watch.

Which downloads meet it: the setup file and the ZIP from GitHub and the package from winget do; the Microsoft Store version does not, because Microsoft signs it during certification.

## Why it appears {#why-it-appears}

The installer is not signed. A signing certificate costs several hundred dollars a year, the program is free, and not buying one is a choice, not an oversight. The warning does not mean the program is harmful - only that it is new and unsigned.

- **SmartScreen** also counts how many people already ran the file. Every new version starts from zero and builds up on its own.
- **The administrator prompt** is the standard step for any server program: a service and a firewall rule cannot be added without it.
- **The antivirus warning** can stay after the program is signed, because it is about what the program does, not about who signed it.

## What to click {#what-to-click}

1. **"Windows protected your PC":** click **More info**, then **Run anyway**. That is all.
2. **"Do you want to allow this app to make changes to your device?":** read the list above, then click **Yes**. Click **No** and the installer still works - only the service and the firewall rule are not added.
3. **Antivirus:** if it quarantined the file, restore it and add an exception for the one install folder. Never switch SmartScreen off and never disable your antivirus: neither is needed here.
4. **To take it all back:** open Windows Settings, Apps and uninstall the entry of the package. It removes the service and the firewall rule that the installer added.

If you would rather verify than trust: every release has a `.sha256` file beside it, and `Get-FileHash <file> -Algorithm SHA256` in PowerShell must print the same value.

## What the program never does {#never}

- It sends nothing to the developer: no telemetry, no analytics, no account with the developer, no advertising.
- It never sends your files anywhere by itself - they travel only between your own devices, and inside the server they are only passed through.
- It never opens a port and never asks for administrator rights on its own: the server is off until you switch it on, and the window shows what it listens on.
- It never writes a password, a token or an invitation into its log.

The same list is in the [privacy policy](https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.html).

## First run {#first-run}

1. Open **Share Manager** and go to **Media Exchange Server**. On a fresh install a first-run guide walks you through the port, the firewall and service choices, the public address if you have one, the first account and the certificate.
2. **Create the first account.** That is your own account; the PC joins it by itself.
3. **Hand the connection string to your devices.** **Get connection string..** gives a text, a `.fmsexch` file and a QR code of the same string. It carries the server's address and the fingerprint of its certificate, never a login or a password.
4. To let relatives and friends in, make invitations: [Accounts, invitations and passwords](exchange-accounts.html).

## The address {#the-address}

Devices need one address to dial. Take the first that fits:

1. **A public IPv4 address, or a free dynamic DNS name** such as `myhome.example.net`, with one port forwarded in the router to this PC. The router step is the same as for a folder - see [Home network](home-network.html).
2. **IPv6**, when your provider shares one address among many customers (carrier NAT) and no forwarded port can work. No forwarding is needed, but a device that has no IPv6 on its network cannot reach the server.
3. **Neither:** then the server is reachable only on your own network, or through a VPN into it.

**The certificate.** The server presents a certificate it made itself. A device remembers its fingerprint when it first connects: the connection string already carries it, or the device shows the fingerprint and asks you to compare it with the one your server shows. Renewing the certificate keeps the same key, so your devices do not ask again.

## Check that it works {#check}

1. **From the phone, on the same network.** In FastMediaSorter open **My devices**, tap **Add server**, paste the connection string (or **Scan QR**, or **Choose file**), then enter the login and the password. The server's card says **Connected, devices: 1** or more.
2. **From another network.** Switch the phone to mobile data and open **My devices** again. A card that connects is the proof that the server is reachable from outside.
3. **If it does not connect**, the app says at which step it broke and what to do. The usual causes, in the order to check them:
   - the PC with the server is asleep or off;
   - the port is not forwarded in the router, or the provider blocks incoming connections;
   - the network offers only IPv4 or only IPv6 where the address needs the other;
   - the Windows network is set to public, which keeps other devices out - set it to private;
   - a VPN is on, on the device or on the PC;
   - the router's guest network keeps the devices apart;
   - the date and time on a device are wrong.

## Keeping it running {#keeping-it-running}

- **Do not let the PC sleep.** A sleeping PC answers nobody: set sleep to **Never** in the Windows power settings.
- **Keep the clock right.** The certificate and the sign-ins depend on it; leave automatic time on.
- **Update by installing over.** The accounts, the devices and the certificate key survive an update, so your devices do not sign in again.
- **Tell the people you invited** before you switch the PC off for long: the accounts live on it.

Back to [the exchange server](exchange-server.html) or to the [portal start page](index.html).
