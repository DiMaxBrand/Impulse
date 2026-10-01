# Impulse — Privacy Policy

Effective date: **[DATE]**

Developer: **[NAME]** ("Developer"). Contact: **[E-MAIL]**

## 1. In short

- Impulse is a messenger client for the open XMPP protocol. The Developer runs **no servers** that hold your accounts, messages, files or contacts.
- The Developer **does not collect, receive or store** your personal data, shows no ads, and uses no analytics or trackers.
- Your messages go to the XMPP server **you choose**, and are stored on your device.
- The source code is open: https://github.com/DiMaxBrand/Impulse — everything below can be verified.

## 2. What is stored on your device

In the app's private storage: your account details (XMPP address and login password), messages and attachments, the contact list received from the server, encryption keys (OMEMO, OpenPGP) and app settings. It stays on the device and is removed when you uninstall the app or clear its data. A backup is created only when you ask for one, and is saved where you choose.

## 3. What leaves your device, and to whom

**3.1 XMPP server.** The app connects to the XMPP server you enter when adding an account. It receives your login, messages, presence, typing state, uploaded files and technical connection metadata. A recipient's server receives messages addressed to its users. Server operators are third parties, not the Developer; their own rules govern what happens there. The connection is protected by TLS.

**3.2 End-to-end encryption.** With OMEMO or OpenPGP on, message content is not readable by the server. Metadata (who writes to whom, and when) remains visible to it.

**3.3 Calls.** Audio and video calls use WebRTC, with STUN/TURN servers (usually your XMPP server's) to connect. With a direct connection the other party can see your IP address.

**3.4 Direct file transfer.** Sending a file device-to-device (Jingle) reveals your IP address to the other party.

**3.5 Update checks and downloads.** The app contacts GitHub (api.github.com, github.com) to learn about and download new versions; GitHub sees your IP address and the app version. Automatic checking can be turned off: Settings → Updates → "Auto-check for updates". "Check now" runs only when you tap it.

**3.6 Maps.** When sending or viewing a location, the app loads OpenStreetMap tiles; the tile server sees your IP address and the area requested.

**3.7 Crash reports.** After a crash the app asks whether to send a report. Only if you tap "Send now" is the report (app version, device manufacturer and model, time, technical error log) sent as a plain, unencrypted XMPP message from your account to the support address **support@on-chat.ru**, which therefore also sees your account address. Turn off in Settings → Privacy → "Send crash reports".

**3.8 Links.** Links in messages open in an external app or browser, which then handle your data.

## 4. Android permissions and why

Internet / network state (connect to the server); Camera (photos, video, QR codes, video calls); Microphone (voice messages, calls); Location (only when you send your location); Notifications; foreground services, boot completed, battery-optimization exemption and wake lock (stay connected in the background); full-screen notifications, draw-over-apps and call management (show incoming calls); Bluetooth (headsets during calls); file access on older Android (send and save attachments); install packages (install updates you downloaded); vibration and audio settings (alerts and call audio). The app does **not** request access to your phone book.

## 5. What the app does not do

No ads, no advertising or analytics SDKs, no tracking of your activity, no commercial sharing of data with third parties, no user profiling.

## 6. Retention and deletion

The Developer stores no user data, so there is nothing to delete on the Developer's side. To delete data on the device, remove the account in the app or clear the app's data. To delete data on an XMPP server, contact its operator or use the account-deletion tools it offers.

## 7. Children

The app is not aimed at children, and the Developer does not collect users' ages.

## 8. Requests from authorities

The Developer has no access to, and does not store, message content, contact lists or account data, and so holds nothing to provide. Information about users may exist with XMPP server operators; such requests should be directed to them.

## 9. Changes

A revised policy is published at the same address with a new effective date.

## 10. Contact

Privacy questions: **[E-MAIL]**.
