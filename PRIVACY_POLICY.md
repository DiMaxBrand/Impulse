# Impulse — Privacy Policy

Effective date: **[DATE]**

Developer: **[NAME]** ("Developer"). Contact: **[E-MAIL]**

## 1. In short

- Impulse is a messenger client for the open XMPP protocol. The Developer **operates the XMPP server `on-chat.ru`**: accounts you create in the app are registered on it, and those accounts' data is stored on that server.
- The server runs Prosody on a rented virtual server: **[HOSTING PROVIDER, COUNTRY]**.
- **OMEMO end-to-end encryption is on by default**: the Developer cannot read the content of such messages. Encryption can be turned off; then message content is stored on the server in readable form and is technically accessible to the server administrator (see section 3).
- No ads, no analytics, no trackers in the app.
- The source code is open: https://github.com/DiMaxBrand/Impulse — what is described below about the app's behavior can be verified.

## 2. The on-chat.ru server: what is stored on it

If you created your account in the app (that is, on `on-chat.ru`), the server stores:

- your username (an address like `name@on-chat.ru`) and login credentials;
- your contact list and subscriptions;
- your profile name and avatar, if set;
- messages: the queue of undelivered messages and the server-side message archive — **[MESSAGE ARCHIVE RETENTION]**;
- files you uploaded (photos, video, documents, voice messages) — **[UPLOADED FILE RETENTION]**;
- group chats and channels you created or joined;
- technical server logs (IP address, connection time): **[WHETHER LOGS ARE KEPT AND FOR HOW LONG]**.

This data is used only to run the messenger. The Developer does not use it for advertising, does not sell it, and does not pass it to third parties.

If you connect an account on **another** server, that server's operator is a third party, not the Developer, and your data there is handled under that operator's rules.

## 3. Encryption, and who can read messages

- **OMEMO is on by default.** Message content is encrypted on your device and decrypted only on your correspondents' devices. The server, including its administrator, only receives and stores encrypted data and cannot read the content.
- **Encryption can be turned off**, for all chats in the app's settings or for a single chat. For unencrypted messages: they travel over a TLS-protected connection but are **stored on the server in readable form**, so the server administrator has a technical ability to read them. We value users' privacy: the Developer does not read conversations, does not use them, and does not pass them to third parties.
- Metadata (who writes to whom, and when; when you are online) is visible to the server regardless of content encryption.
- If the privacy of your conversations matters to you, do not turn encryption off.

## 4. What is stored on your device

In the app's private storage: your account details (XMPP address and login password), messages and attachments, the contact list, encryption keys (OMEMO, OpenPGP) and app settings. It is removed when you uninstall the app or clear its data. A backup is created only when you ask for one, and is saved where you choose.

## 5. What leaves your device, and to whom

**5.1 XMPP server.** The app connects to your account's server (by default `on-chat.ru`). It receives your login, messages, presence, typing state, uploaded files and technical connection metadata. The connection is protected by TLS. Messages addressed to users on other servers are received by the recipient's server, which the Developer does not own.

**5.2 News channel.** On an account's first connection the app automatically joins the official news channel `news@conference.on-chat.ru` on the Developer's server. You can leave it at any time; it will not be re-joined.

**5.3 Calls.** Audio and video calls use WebRTC, with STUN/TURN servers provided by your account's server. With a direct connection the other party can see your IP address.

**5.4 Direct file transfer.** Sending a file device-to-device reveals your IP address to the other party.

**5.5 UnifiedPush notifications.** If you set up UnifiedPush, a service signal about new messages passes through the push server `up.conversations.im`, which belongs to a third party. It receives your device's delivery address and the fact of the signal. **[CONFIRM: on-chat.ru does not include message text in push notifications]**

**5.6 Update checks and downloads.** The app contacts GitHub (`api.github.com`, `github.com`) to learn about and download new versions; GitHub sees your IP address and the app version. Automatic checking can be turned off: Settings → Updates → "Auto-check for updates".

**5.7 Maps.** When sending or viewing a location, the app loads OpenStreetMap tiles; the tile server sees your IP address and the area requested.

**5.8 Channel search.** If you use public channel search, the query is sent to `search.jabber.network`, a third-party service.

**5.9 Crash reports.** After a crash the app asks whether to send a report. Only if you tap "Send now" is the report (app version, device manufacturer and model, time, technical error log) sent as a plain, unencrypted message from your account to the support address `support@on-chat.ru` on the Developer's server; your account address is visible to the recipient. Turn off in Settings → Privacy → "Send crash reports".

**5.10 Links.** Links in messages or help open in an external app or browser, which then handle your data.

## 6. Android permissions and why

Internet / network state (connect to the server); Camera (photos, video, QR codes, video calls); Microphone (voice messages, calls); Location (only when you send your location); Notifications; foreground services, boot completed, battery-optimization exemption and wake lock (stay connected in the background); full-screen notifications, draw-over-apps and call management (show incoming calls); Bluetooth (headsets during calls); file access on older Android (send and save attachments); install packages (install updates you downloaded); vibration and audio settings (alerts and call audio). The app does **not** request access to your phone book.

## 7. What the app does not do

No ads, no advertising or analytics SDKs, no tracking of your activity, no commercial sharing of data with third parties, no user profiling.

## 8. Retention and deletion

- On the device: remove the account in the app or clear the app's data.
- On `on-chat.ru`: to delete your account and its data, **[HOW TO DELETE AN ACCOUNT ON THE SERVER, E.G.: EMAIL THE ADDRESS IN SECTION 10]**. Processing time: **[TIME]**.
- On other operators' servers: contact them.

## 9. Children

The app is not aimed at children, and the Developer does not collect users' ages.

## 10. Changes and contact

A revised policy is published at the same address with a new effective date.

Privacy and data-deletion questions: **[E-MAIL]**.
