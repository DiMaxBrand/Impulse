# Impulse

Impulse is a free, open-source messenger for Android, built on [Conversations](https://codeberg.org/iNPUTmice/Conversations) and the federated XMPP protocol. It brings an expressive interface and its own features to private conversations while staying compatible with XMPP.

Chat one-to-one or in groups, share photos, videos, files and voice messages, and make audio and video calls. Impulse supports OMEMO encryption, reactions, pinned messages, multiple accounts, and a choice of Material 3 Expressive or Liquid Glass appearance. Some features depend on your server and the other person's client.

## Download

Download the Android APK from [GitHub Releases](https://github.com/DiMaxBrand/Impulse/releases). Stable and testing releases are available; alpha, beta, and release-candidate builds may contain bugs. The app can check for updates, and you can choose your update channel in its settings.

After setting up an account, the in-app welcome tour introduces some of Impulse's features. You can also sign in with an existing XMPP account.

## Building

Impulse uses Gradle and JDK 21. To compile the free Conversations variant locally:

```sh
./gradlew compileConversationsFreeDebugKotlin
```

## About the project

Impulse is a fork of Conversations. Thank you to the Conversations contributors for the original app and its XMPP foundations. For upstream documentation and its full FAQ, see the [Conversations README](https://codeberg.org/iNPUTmice/Conversations/src/branch/master/README.md). Upstream documentation can describe behavior that differs from Impulse.

Impulse is licensed under the [GNU General Public License, version 3](LICENSE).
