package eu.siacs.conversations.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.emoji2.text.DefaultEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;

import eu.siacs.conversations.Config;

public class EmojiInitializationService {

    public static void execute(final Context context) {
        final EmojiCompat.Config config = DefaultEmojiCompatConfig.create(context);
        if (config == null) {
            return;
        }
        EmojiCompat.init(config.setReplaceAll(true))
                .registerInitCallback(
                        new EmojiCompat.InitCallback() {
                            @Override
                            public void onInitialized() {
                                Log.d(Config.LOGTAG, "initialized EmojiCompat");
                                super.onInitialized();
                            }

                            @Override
                            public void onFailed(@Nullable Throwable throwable) {
                                Log.e(Config.LOGTAG, "failed to initialize EmojiCompat", throwable);
                                super.onFailed(throwable);
                            }
                        });
    }
}
