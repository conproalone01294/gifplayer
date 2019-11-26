package com.zing.zalo.gifplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

class InvalidationHandler extends Handler {

    static final int MSG_TYPE_INVALIDATION = 1;
    static final int MSG_TYPE_ANIMATION_COMPLETED = 2;

    private final WeakReference<GifDrawable> mDrawableRef;

    public InvalidationHandler(final GifDrawable gifDrawable) {
        super(Looper.getMainLooper());
        mDrawableRef = new WeakReference<>(gifDrawable);
    }

    @Override
    public void handleMessage(final Message msg) {
        final GifDrawable gifDrawable = mDrawableRef.get();
        if (gifDrawable == null) {
            return;
        }
        if (msg.what == MSG_TYPE_INVALIDATION) {
            if (gifDrawable.parentView != null && gifDrawable.parentView.get() != null)
                gifDrawable.parentView.get().invalidate();
        } else if (msg.what == MSG_TYPE_ANIMATION_COMPLETED){
            if(gifDrawable.mListener != null)
                gifDrawable.mListener.onAnimationCompleted();
        }
    }
}
