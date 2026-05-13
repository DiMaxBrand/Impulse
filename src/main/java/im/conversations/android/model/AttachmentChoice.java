package im.conversations.android.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public record AttachmentChoice(@DrawableRes int icon, @StringRes int name, Type type) {

    public enum Type {
        FILE,
        PICTURE,
        CAMERA,
        VIDEO,
        LOCATION,
        RECORDING
    }
}
