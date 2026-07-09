package de.gultsch.common;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.R;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class TrustManagers {

    private static final char[] BUNDLED_KEYSTORE_PASSWORD = "letsencrypt".toCharArray();

    private TrustManagers() {
        throw new IllegalStateException("Do not instantiate me");
    }

    public static X509TrustManager createTrustManager(@Nullable final KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        return Iterables.getOnlyElement(
                Iterables.filter(
                        Arrays.asList(trustManagerFactory.getTrustManagers()),
                        X509TrustManager.class));
    }

    public static X509TrustManager createForAndroidVersion(final Context context)
            throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        // The system default trust manager validates a chain using only what the server itself
        // sends plus whatever AIA (Authority Information Access) fetching the platform's TLS
        // stack manages to do live, during the handshake. A server that doesn't send its full
        // chain (a common misconfiguration — pointing the XMPP daemon at the bare leaf cert
        // instead of the "fullchain" file certbot also produces) relies entirely on that live
        // AIA fetch succeeding, which is flaky on mobile networks. Bundling Let's Encrypt's
        // currently-active intermediates lets the chain complete locally as a fallback, without
        // granting trust to anything that isn't a legitimately CA-issued certificate — this does
        // NOT weaken validation for self-signed/unknown certs, which still go through
        // MemorizingTrustManager's normal TOFU-prompt path untouched.
        final var intermediateHelper =
                createWithKeyStore(
                        context.getResources().openRawResource(R.raw.letsencrypt_intermediates));
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            final var rootHelper =
                    createWithKeyStore(context.getResources().openRawResource(R.raw.letsencrypt));
            return CombiningTrustManager.combineWithDefault(intermediateHelper, rootHelper);
        } else {
            return CombiningTrustManager.combineWithDefault(intermediateHelper);
        }
    }

    public static X509TrustManager createDefaultTrustManager()
            throws NoSuchAlgorithmException, KeyStoreException {
        return createTrustManager(null);
    }

    private static X509TrustManager createWithKeyStore(final InputStream inputStream)
            throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(inputStream, BUNDLED_KEYSTORE_PASSWORD);
        return TrustManagers.createTrustManager(keyStore);
    }
}
