---
layout: doc
title: "Accounts, invitations and passwords"
description: "How an account on an exchange server is created from an invitation, what you type at the first connection, how to give a folder to a guest or by a link with a password, and how to change a password, sign out or deal with a lost device."
category: "Media exchange"
permalink: /docs/media-exchange-portal/exchange-accounts.html
---
<sub class="doc-stamp">26.10.15 14:25</sub>

# Accounts, invitations and passwords

<img src="exchange-mark/full-256.png" alt="Media Exchange Server" width="128">

An account on an exchange server is a login and a password. It holds the list of your devices and what they publish, and it exists on one server only: the one you run or the one a person you know runs for you. The developer of the app keeps no accounts and has no server.

## Getting an account {#invitation}

Nobody signs up on a server by themselves: its owner gives out invitations. There are two kinds of invitation, and the owner picks the kind when making one:

- **Own device or family** - the account of a person and their household. Its devices publish folders, broadcast, cast and use the clipboard, as on any account of yours.
- **Guest** - the account of a person who is given things and offers nothing. A guest sees only what was given to it; it cannot publish, broadcast, cast or use the clipboard.

How the owner hands the invitation out:

- **A one-time invitation.** It comes as a connection string with a QR code, and it works for one account. It does not expire: it lives until somebody registers with it or the owner withdraws it. The owner can make as many as needed, and may write into it the login the person is expected to take.
- **The shared invitation password.** It is off by default. The owner may switch it on for a group that registers without a code each, choose which kind of account it creates, and change or switch it off at any time; the accounts already created are not touched. It never rides in a string, a QR code or a file - the owner tells it to people.

Only the owner makes invitations. A device that is signed in to an account cannot.

## What you type {#what-you-type}

The first time, and only the first time, you give the program two things: **the server** and **who you are on it**.

- **The server** is a connection string. Paste it, scan its QR code where the device has a camera, or choose its `.fmsexch` file. It holds the server's address and the fingerprint of its certificate, and never a login or a password.
- **Who you are** is a login and a password. With an invitation the program says "This is an invitation. Choose a login and a password to create your account." and you choose them. With the shared invitation password you tap **Create a new account with the server's invitation password**, type that password and choose your login and password.

After that the program remembers the server and the sign-in until the owner of the server changes the terms. IP addresses, ports, firewalls and routers stay out of sight for as long as they can.

In short, by case:

- **A new person with an invitation:** the string (or QR code or file) and a login and password of your choice.
- **A new device of an account you already have:** the string, then your login and password.
- **A watch:** nothing to type on the Standard edition of the watch; on the phone choose **Connect watch** in the menu of the server's card and the watch joins by a code the phone hands over. The noLegal edition of the watch lets you type a password too.
- **Any device where typing a strong password is hard** (a TV box, for instance): on a device that is signed in, choose **Issue a pairing code** and enter the code instead of the password. It is 8 characters, works once and is valid for 10 minutes.
- **A protected folder:** its PIN, which is set where the folder is shared.
- **Google Drive:** one Google sign-in, nothing from the server.

## A login and a password {#login-and-password}

- **A login** is 3 to 64 Latin letters, digits, dots, hyphens or underscores; capital letters count as small ones.
- **A password** is at least 10 characters, with capital and small Latin letters and digits. The server stores only a salted hash of it, so nobody can read it back, and the owner cannot see it either.
- **Guessing is slowed down.** The server checks one password per second for a login and for an address, with no lockout: a wrong try costs a second, and the right one works right after it.

## People outside your account {#outside}

A folder or a broadcast of yours can go to someone who has no account of yours. There are two ways, and both are withdrawn on their own:

- **A guest.** The person registers a guest account on your server with an invitation of the kind **Guest**, and sees only what you gave to that account - one folder or one broadcast. In FastMediaSorter: open **Access from outside** for the folder and choose **Invite a guest**.
- **A link with a password.** For a person with no account at all: open **Access from outside** for the folder and choose **Link with a password**. Send the link, and tell the password another way - by voice or in another messenger. The password is checked only by the device that shares the folder, and the server never holds it. A link never expires; **Withdraw** ends it and the sessions it opened.

The same two ways exist for a broadcast.

## Changing a password {#change-password}

- **You know the password.** Open **My devices**, the menu of the server's card, **Change password**, then type the current password and the new one. This device stays signed in; every other device of the account signs out and asks for the new password, and its place in the list stays.
- **The owner resets it.** The owner of the server can set a new password for any account. The effect is the same, with no device spared: all of them ask for the new password.
- **You forgot it.** It cannot be recovered: there is no e-mail in the account to send a link to. Ask the owner of the server to reset it, or create a new account with a new invitation.

## A lost or sold device {#lost-device}

1. **Change the password** of the account from another device. Every other device signs out at once, the lost one included.
2. **Revoke the device** in **My devices**, on its row, **Revoke**. It loses access to the account and can come back only by signing in again.

Revoking a device changes no password and no folder PIN by itself, which is why the first step is the password. A folder's PIN is changed where the folder is shared.

## Leaving an account {#leaving}

- **Sign out on this device** (in the menu of the server's card) removes this device from the account's list and makes it forget the sign-in. The account and its other devices stay. If the server cannot be reached, the device forgets locally and stays in the list until another device revokes it.
- **Delete my account** erases the account and everything it holds on the server: [How to delete an account](account-deletion.html).

## What the owner of the server sets {#limits}

- **The number of accounts**, 10 by default. Accounts the owner makes count too.
- **Limits of an account**, which the owner may set per account and as the default for new ones: devices, folders, live broadcasts and the size of the clipboard. By default only the clipboard is limited, to 5 GiB.

A request that would cross a limit is refused and changes nothing; a limit lowered below what an account already has refuses only the next request and never removes what exists.

Back to [the exchange server](exchange-server.html) or to the [portal start page](index.html).
