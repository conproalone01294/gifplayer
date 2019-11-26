package com.zing.zalo.gifplayer;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Surface;

import com.zing.zalo.utils.NativeLibrary;
import com.zing.zalo.utils.NativeLoader;
import com.zing.zalocore.CoreUtility;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Native library wrapper
 */
final class GifInfoHandle {

    /**
     * Pointer to native structure. Access must be synchronized, heap corruption may occur otherwise
     * when {@link #recycle()} is called during another operation.
     */
    static final NativeLibrary BASE_LIBRARY_NAME = NativeLibrary.GIF_PLAYER;

    private volatile long gifInfoPtr;

    static final GifInfoHandle NULL_INFO = new GifInfoHandle();

    static {
        try{
            NativeLoader.loadNewestLibrary(CoreUtility.getAppContext(), BASE_LIBRARY_NAME);
        } catch (Throwable e){
            GifDrawable.LOAD_SO_SUCCESSFUL = false;
        }
    }

    private GifInfoHandle() {
    }

    GifInfoHandle(FileDescriptor fd, boolean justDecodeMetaData, int maxSize) throws GifIOException {
        gifInfoPtr = openFd(fd, 0, justDecodeMetaData, maxSize);
    }

    GifInfoHandle(byte[] bytes, boolean justDecodeMetaData, int maxSize) throws GifIOException {
        gifInfoPtr = openByteArray(bytes, justDecodeMetaData, maxSize);
    }

    GifInfoHandle(ByteBuffer buffer, boolean justDecodeMetaData, int maxSize) throws GifIOException {
        gifInfoPtr = openDirectByteBuffer(buffer, justDecodeMetaData, maxSize);
    }

    GifInfoHandle(String filePath, boolean justDecodeMetaData, int maxSize) throws GifIOException {
        gifInfoPtr = openFile(filePath, justDecodeMetaData, maxSize);
    }

    GifInfoHandle(InputStream stream, boolean justDecodeMetaData, int maxSize) throws GifIOException {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("InputStream does not support marking");
        }
        gifInfoPtr = openStream(stream, justDecodeMetaData, maxSize);
    }

    GifInfoHandle(AssetFileDescriptor afd, boolean justDecodeMetaData, int maxSize) throws IOException {
        try {
            gifInfoPtr = openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData, maxSize);
        } finally {
            afd.close();
        }
    }

    static GifInfoHandle openUri(ContentResolver resolver, Uri uri, boolean justDecodeMetaData, int maxSize) throws IOException {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) { //workaround for #128
            return new GifInfoHandle(uri.getPath(), justDecodeMetaData, maxSize);
        }
        return new GifInfoHandle(resolver.openAssetFileDescriptor(uri, "r"), justDecodeMetaData, maxSize);
    }

    static native long openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData, int maxSize) throws GifIOException;

    static native long openByteArray(byte[] bytes, boolean justDecodeMetaData, int maxSize) throws GifIOException;

    static native long openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData, int maxSize) throws GifIOException;

    static native long openStream(InputStream stream, boolean justDecodeMetaData, int maxSize) throws GifIOException;

    static native long openFile(String filePath, boolean justDecodeMetaData, int maxSize) throws GifIOException;

    private static native long renderFrame(long gifFileInPtr, Bitmap frameBuffer);

    private static native void bindSurface(long gifInfoPtr, Surface surface, long[] savedState);

    private static native void free(long gifFileInPtr);

    private static native boolean reset(long gifFileInPtr);

    private static native void setSpeedFactor(long gifFileInPtr, float factor);

    private static native String getComment(long gifFileInPtr);

    private static native int getLoopCount(long gifFileInPtr);

    private static native void setLoopCount(long gifFileInPtr, int loopCount);

    private static native long getSourceLength(long gifFileInPtr);

    private static native int getDuration(long gifFileInPtr);

    private static native int getCurrentPosition(long gifFileInPtr);

    private static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

    private static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

    private static native void saveRemainder(long gifFileInPtr);

    private static native long restoreRemainder(long gifFileInPtr);

    private static native long getAllocationByteCount(long gifFileInPtr);

    private static native int getNativeErrorCode(long gifFileInPtr);

    private static native int getCurrentFrameIndex(long gifFileInPtr);

    private static native int getCurrentLoop(long gifFileInPtr);

    private static native void postUnbindSurface(long gifFileInPtr);

    private static native boolean isAnimationCompleted(long gifInfoPtr);

    private static native long[] getSavedState(long gifInfoPtr);

    private static native int restoreSavedState(long gifInfoPtr, long[] savedState, Bitmap mBuffer);

    private static native int getFrameDuration(long gifInfoPtr, int index);

    private static native void setOptions(long gifInfoPtr, int sampleSize, boolean isOpaque);

    private static native int getWidth(long gifFileInPtr);

    private static native int getHeight(long gifFileInPtr);

    private static native int getNumberOfFrames(long gifInfoPtr);

    private static native boolean isOpaque(long gifInfoPtr);

    private static native void startDecoderThread(long gifInfoPtr);

    private static native void stopDecoderThread(long gifInfoPtr);

    private static native void glTexImage2D(long gifInfoPtr, int target, int level);

    private static native void glTexSubImage2D(long gifInfoPtr, int target, int level);

    private static native void seekToFrameGL(long gifInfoPtr, int index);

    private static native void initTexImageDescriptor(long gifInfoPtr);

    synchronized long renderFrame(Bitmap frameBuffer) {
        return renderFrame(gifInfoPtr, frameBuffer);
    }

    void bindSurface(Surface surface, long[] savedState) {
        bindSurface(gifInfoPtr, surface, savedState);
    }

    synchronized void recycle() {
        free(gifInfoPtr);
        gifInfoPtr = 0L;
    }

    synchronized long restoreRemainder() {
        return restoreRemainder(gifInfoPtr);
    }

    synchronized boolean reset() {
        return reset(gifInfoPtr);
    }

    synchronized void saveRemainder() {
        saveRemainder(gifInfoPtr);
    }

    synchronized String getComment() {
        return getComment(gifInfoPtr);
    }

    synchronized int getLoopCount() {
        return getLoopCount(gifInfoPtr);
    }

    void setLoopCount(final int loopCount) {
        if (loopCount < 0 || loopCount > 0xFFFF) {
            throw new IllegalArgumentException("Loop count of range <0, 65535>");
        }
        synchronized (this) {
            setLoopCount(gifInfoPtr, loopCount);
        }
    }

    synchronized long getSourceLength() {
        return getSourceLength(gifInfoPtr);
    }

    synchronized int getNativeErrorCode() {
        return getNativeErrorCode(gifInfoPtr);
    }

    void setSpeedFactor(float factor) {
        if (factor <= 0f || Float.isNaN(factor)) {
            throw new IllegalArgumentException("Speed factor is not positive");
        }
        if (factor < 1f / Integer.MAX_VALUE) {
            factor = 1f / Integer.MAX_VALUE;
        }
        synchronized (this) {
            setSpeedFactor(gifInfoPtr, factor);
        }
    }

    synchronized int getDuration() {
        return getDuration(gifInfoPtr);
    }

    synchronized int getCurrentPosition() {
        return getCurrentPosition(gifInfoPtr);
    }

    synchronized int getCurrentFrameIndex() {
        return getCurrentFrameIndex(gifInfoPtr);
    }

    synchronized int getCurrentLoop() {
        return getCurrentLoop(gifInfoPtr);
    }

    synchronized void seekToTime(final int position, final Bitmap buffer) {
        seekToTime(gifInfoPtr, position, buffer);
    }

    synchronized void seekToFrame(final int frameIndex, final Bitmap buffer) {
        seekToFrame(gifInfoPtr, frameIndex, buffer);
    }

    synchronized long getAllocationByteCount() {
        return getAllocationByteCount(gifInfoPtr);
    }

    synchronized boolean isRecycled() {
        return gifInfoPtr == 0L;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    synchronized void postUnbindSurface() {
        postUnbindSurface(gifInfoPtr);
    }

    synchronized boolean isAnimationCompleted() {
        return isAnimationCompleted(gifInfoPtr);
    }

    synchronized long[] getSavedState() {
        return getSavedState(gifInfoPtr);
    }

    synchronized int restoreSavedState(long[] savedState, Bitmap mBuffer) {
        return restoreSavedState(gifInfoPtr, savedState, mBuffer);
    }

    int getFrameDuration(final int index) {
        synchronized (this) {
            if (index < 0 || index >= getNumberOfFrames(gifInfoPtr)) {
                throw new IndexOutOfBoundsException("Frame index is out of bounds");
            }
            return getFrameDuration(gifInfoPtr, index);
        }
    }

    void setOptions(int sampleSize, boolean isOpaque) {
        setOptions(gifInfoPtr, sampleSize, isOpaque);
    }

    synchronized int getWidth() {
        return getWidth(gifInfoPtr);
    }

    synchronized int getHeight() {
        return getHeight(gifInfoPtr);
    }

    synchronized int getNumberOfFrames() {
        return getNumberOfFrames(gifInfoPtr);
    }

    synchronized boolean isOpaque() {
        return isOpaque(gifInfoPtr);
    }

    void glTexImage2D(int target, int level) {
        glTexImage2D(gifInfoPtr, target, level);
    }

    void glTexSubImage2D(int target, int level) {
        glTexSubImage2D(gifInfoPtr, target, level);
    }

    void startDecoderThread() {
        startDecoderThread(gifInfoPtr);
    }

    void stopDecoderThread() {
        stopDecoderThread(gifInfoPtr);
    }

    void initTexImageDescriptor() {
        initTexImageDescriptor(gifInfoPtr);
    }

    void seekToFrameGL(final int index) {
        if (index < 0 || index >= getNumberOfFrames(gifInfoPtr)) {
            throw new IndexOutOfBoundsException("Frame index is out of bounds");
        }
        seekToFrameGL(gifInfoPtr, index);
    }
}