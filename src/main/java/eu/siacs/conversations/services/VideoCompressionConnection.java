package eu.siacs.conversations.services;

import eu.siacs.conversations.entities.Transferable;

public class VideoCompressionConnection implements Transferable {

    private volatile int progress = 0;
    private Runnable cancelCallback;

    public void setCancelCallback(final Runnable callback) {
        this.cancelCallback = callback;
    }

    public void setProgress(final int p) {
        this.progress = p;
    }

    @Override
    public boolean start() {
        return false;
    }

    @Override
    public int getStatus() {
        return STATUS_COMPRESSING;
    }

    @Override
    public Long getFileSize() {
        return null;
    }

    @Override
    public int getProgress() {
        return progress;
    }

    @Override
    public void cancel() {
        final Runnable cb = this.cancelCallback;
        if (cb != null) {
            cb.run();
        }
    }
}
