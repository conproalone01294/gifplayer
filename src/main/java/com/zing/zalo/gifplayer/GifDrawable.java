package com.zing.zalo.gifplayer;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.StrictMode;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.zing.zalo.gifplayer.InvalidationHandler.MSG_TYPE_ANIMATION_COMPLETED;
import static com.zing.zalo.gifplayer.InvalidationHandler.MSG_TYPE_INVALIDATION;

/**
 * A {@link Drawable} which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can also be examined.
 *
 * @author koral--
 */
@TargetApi(Build.VERSION_CODES.DONUT)
public class GifDrawable extends Drawable implements Animatable, IAnimationDecoder {
    static final String TAG = GifDrawable.class.getSimpleName();
    public static boolean GIF_ENABLED = true;
    public static boolean GIF_AUTO_REPEAT = true;
    public static int GIF_MAX_PREVIEW_SIZE = 960 * 960 * 4;
    public static int GIF_MAX_SIZE_CHAT = 720 * 720 * 4;
    public static int GIF_MAX_SIZE_FEED = 720 * 720 * 4;
    public static boolean LOAD_SO_SUCCESSFUL = true;
    public static final int MODE_CHAT = 0;
    public static final int MODE_FEED = 1;
    public static final int MODE_INPUTSTREAM = 2;
    ScheduledThreadPoolExecutor mExecutor;

    volatile boolean mIsRunning = false;
    long mNextFrameRenderTime = -1;
    public String gifPath;
    final Rect mDstRect = new Rect();
    /**
     * Paint used to draw on a Canvas
     */
    protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    Paint mBorderPaint;
    float mBorderWidth = 0;
    int mColorBorder = Color.TRANSPARENT;
    /**
     * Frame buffer, holds current frame.
     */
    Bitmap mBuffer;
    public Bitmap mRenderingBitmap;
    GifInfoHandle mNativeInfoHandle;
    public AnimationListener mListener;
    ColorStateList mTint = ColorStateList.valueOf(mColorBorder);
    PorterDuffColorFilter mTintFilter;
    PorterDuff.Mode mTintMode;
    InvalidationHandler mInvalidationHandler;

    Rect mSrcRect;
    ScheduledFuture<?> mSchedule;
    int mScaledWidth, mScaledHeight;
    float mCornerRadius = 0.f;
    final RectF mDstRectF = new RectF();
    public volatile WeakReference<View> parentView = null;
    boolean mLoop;
    volatile boolean mIsAnimationCompleted;
    volatile boolean mIsDecoded;
    int mMaxGifSize = GIF_MAX_SIZE_CHAT;

    int mCurrentMode = MODE_CHAT;

    // [ topLeft, topRight, bottomLeft, bottomRight ]
    boolean[] mCornersRounded = new boolean[] { false, false, false, false };
    final RectF mSquareCornersRect = new RectF();


    /**
     * Constructs drawable from given file path.<br>
     * Only metadata is read, no graphic data is decoded here.
     * In practice can be called from main thread. However it will violate
     * {@link StrictMode} policy if disk reads detection is enabled.<br>
     *
     * @param filePath path to the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if filePath is null
     */
    public GifDrawable(String filePath, int maxSize) {
        mMaxGifSize = maxSize;
        gifPath = filePath;
        mExecutor = GifRenderingExecutor.getInstance();
        mInvalidationHandler = new InvalidationHandler(this);
    }

    /**
     * Equivalent to {@code} GifDrawable(file.getPath())}
     *
     * @param file the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if file is null
     */
    public GifDrawable(File file, int maxSize) {
        mMaxGifSize = maxSize;
        gifPath = file.getPath();
        mExecutor = GifRenderingExecutor.getInstance();
        mInvalidationHandler = new InvalidationHandler(this);
    }

    public GifDrawable(){
        mExecutor = GifRenderingExecutor.getInstance();
        mInvalidationHandler = new InvalidationHandler(this);
    }

    void init() {
        try {
            mNativeInfoHandle = new GifInfoHandle(gifPath, false, mMaxGifSize);
            mIsDecoded = true;
            mBuffer = Bitmap.createBitmap(mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight(), Bitmap.Config.ARGB_8888);
            mSrcRect = new Rect(0, 0, mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight());
            mScaledWidth = mNativeInfoHandle.getWidth();
            mScaledHeight = mNativeInfoHandle.getHeight();
            setLoopCount(0);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void read(InputStream is){
        try{
            mNativeInfoHandle = new GifInfoHandle(new BufferedInputStream(is), false, mMaxGifSize);
            mIsDecoded = true;
            mBuffer = Bitmap.createBitmap(mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight(), Bitmap.Config.ARGB_8888);
            mSrcRect = new Rect(0, 0, mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight());
            mScaledWidth = mNativeInfoHandle.getWidth();
            mScaledHeight = mNativeInfoHandle.getHeight();
            setLoopCount(0);
        }catch (Throwable ex){
            ex.printStackTrace();
        }
    }

    public void setParentView(View view) {
        parentView = new WeakReference<View>(view);
    }

    /**
     * Frees any memory allocated native way.
     * Operation is irreversible. After this call, nothing will be drawn.
     * This method is idempotent, subsequent calls have no effect.
     * Like {@link Bitmap#recycle()} this is an advanced call and
     * is invoked implicitly by finalizer.
     */
    public void recycle() {
        try {
            shutdown();
            if (mBuffer != null)
                mBuffer.recycle();
            mBuffer = null;
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    void shutdown() {
        try {
            mIsDecoded = false;
            mIsRunning = false;
            mInvalidationHandler.removeMessages(MSG_TYPE_INVALIDATION);
            if (mNativeInfoHandle != null)
                mNativeInfoHandle.recycle();
            mNativeInfoHandle = null;
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * @return true if drawable is recycled
     */
    public boolean isRecycled() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.isRecycled();
        return false;
    }

    @Override
    public int getIntrinsicHeight() {
        return mScaledHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mScaledWidth;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    /**
     * See {@link Drawable#getOpacity()}
     *
     * @return always {@link PixelFormat#TRANSPARENT}
     */
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /**
     * Starts the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void start() {
        synchronized (this) {
            if (mIsRunning) {
                return;
            }
            mIsRunning = true;
        }
        mIsAnimationCompleted = false;
        startAnimation();
    }

    void startAnimation() {
        waitForPendingRenderTask();
        mSchedule = mExecutor.schedule(loadFrameRunnable, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Causes the animation to start over.
     * If rewinding input source fails then state is not affected.
     * This method is thread-safe.
     */
    public void reset() {
        try {
            mIsRunning = false;
            mExecutor.execute(resetRunnable);
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * Stops the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void stop() {
        synchronized (this) {
            if (!mIsRunning) {
                return;
            }
            mIsRunning = false;
        }

        waitForPendingRenderTask();
        mRenderingBitmap = null;
    }

    void waitForPendingRenderTask() {
        try {
            mInvalidationHandler.removeMessages(MSG_TYPE_INVALIDATION);
            if (mSchedule != null) {
                mSchedule.cancel(false);
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }


    @Override
    public int getDelay(int n) {
        int delay = -1;
        if ((n >= 0) && (n < getFrameCount())) {
            delay = getFrameDuration(n);
        }
        return delay;
    }

    /**
     * @return number of frames in GIF, at least one
     */
    @Override
    public int getFrameCount() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.getNumberOfFrames();
        return 0;
    }

    @Override
    public Bitmap getBitmap() {
        return getFrame(0);
    }

    /**
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 1 if there is no such extension.
     *
     * @return loop count, 0 means that animation is infinite
     */
    public int getLoopCount() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.getLoopCount();
        return 0;
    }

    /**
     * Sets loop count of the animation. Loop count must be in range &lt;0 ,65535&gt;
     *
     * @param loopCount loop count, 0 means infinity
     */
    public void setLoopCount(final int loopCount) {
        if (mNativeInfoHandle != null)
            mNativeInfoHandle.setLoopCount(loopCount);
    }

    /**
     * Gets the image contents of frame n.
     *
     * @return BufferedBitmap representation of frame, or null if n is invalid.
     */
    @Override
    public Bitmap getFrame(int n){
        Bitmap bitmap = null;
        try {
            if (getFrameCount() <= 0)
                return bitmap;
            n = n % getFrameCount();
            if (mNativeInfoHandle != null) {
                mNativeInfoHandle.seekToFrame(n, mBuffer);
                bitmap = getCurrentFrame();
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return bitmap;
    }

    /**
     * Gets the image contents of frame n.
     * GifDecoder doesn't care isReuse
     *
     * @return BufferedBitmap representation of frame, or null if n is invalid.
     */
    @Override
    public Bitmap getFrame(int n, boolean isReuse) {
        return getFrame(n);
    }

    @Override
    public void clearData() {
        recycle();
    }

    @Override
    public int getType() {
        return 0;
    }

    /**
     * Retrieves last error which is also the indicator of current GIF status.
     *
     * @return current error or {@link GifError#NO_ERROR} if there was no error or drawable is recycled
     */
    public GifError getError() {
        if (mNativeInfoHandle != null)
            return GifError.fromCode(mNativeInfoHandle.getNativeErrorCode());
        return null;
    }

    /**
     * Returns the minimum number of bytes that can be used to store pixels of the single frame.
     * Returned value is the same for all the frames since it is based on the size of GIF screen.
     * <p>This method should not be used to calculate the memory usage of the bitmap.
     * Instead see {@link #getAllocationByteCount()}.
     *
     * @return the minimum number of bytes that can be used to store pixels of the single frame
     */
    public int getFrameByteCount() {
        if (mBuffer != null)
            return mBuffer.getRowBytes() * mBuffer.getHeight();
        return 0;
    }

    /**
     * Returns size of the allocated memory used to store pixels of this object.
     * It counts length of all frame buffers. Returned value does not change during runtime.
     *
     * @return size of the allocated memory used to store pixels of this object
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public long getAllocationByteCount() {
        if (mNativeInfoHandle != null) {
            long byteCount = mNativeInfoHandle.getAllocationByteCount();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                byteCount += mBuffer.getAllocationByteCount();
            } else {
                byteCount += mBuffer.getRowBytes() * mBuffer.getHeight();
            }
            return byteCount;
        }
        return 0;
    }

    /**
     * Returns length of the input source obtained at the opening time or -1 if
     * length cannot be determined. Returned value does not change during runtime.
     * If GifDrawable is constructed from {@link InputStream} -1 is always returned.
     * In case of byte array and {@link ByteBuffer} length is always known.
     * In other cases length -1 can be returned if length cannot be determined.
     *
     * @return number of bytes backed by input source or -1 if it is unknown
     */
    public long getInputSourceByteCount() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.getSourceLength();
        return 0;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mDstRect.set(bounds);
        mDstRectF.set(mDstRect);

        final Shader shader = mPaint.getShader();
        if (shader != null) {
            final Matrix shaderMatrix = new Matrix();
            shaderMatrix.setTranslate(mDstRectF.left, mDstRectF.top);
            shaderMatrix.preScale(
                    mDstRectF.width() / mBuffer.getWidth(),
                    mDstRectF.height() / mBuffer.getHeight());
            shader.setLocalMatrix(shaderMatrix);
            mPaint.setShader(shader);
        }
    }

    /**
     * Reads and renders new frame if needed then draws last rendered frame.
     *
     * @param canvas canvas to draw into
     */

    @Override
    public void draw(Canvas canvas) {
        if (mRenderingBitmap != null) {
            final boolean clearColorFilter;
            if (mIsRunning && mTintFilter != null && mPaint.getColorFilter() == null) {
                mPaint.setColorFilter(mTintFilter);
                clearColorFilter = true;
            } else {
                clearColorFilter = false;
            }
            if (mPaint.getShader() == null) {
                canvas.drawBitmap(mBuffer, null, mDstRect, mPaint);
            } else {
                canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, mPaint);
            }

            if(mBorderWidth >0) {
                canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, mBorderPaint);
            }

            redrawBitmapForSquareCorners(canvas);
            redrawBorderForSquareCorners(canvas);

            if (clearColorFilter) {
                mPaint.setColorFilter(null);
            }
        }
        if (mIsRunning && !mIsAnimationCompleted && mNextFrameRenderTime != Integer.MIN_VALUE) {
            long renderDelay = Math.max(mNextFrameRenderTime - System.currentTimeMillis(), 0);

            mNextFrameRenderTime = Integer.MIN_VALUE;
            mExecutor.remove(loadFrameRunnable);
            mSchedule = mExecutor.schedule(loadFrameRunnable, renderDelay, TimeUnit.MILLISECONDS);
        }
    }

    void redrawBitmapForSquareCorners(Canvas canvas) {
        if (all(mCornersRounded)) {
            // no square corners
            return;
        }

        if (mCornerRadius == 0) {
            return; // no round corners
        }

        float left = mDstRectF.left;
        float top = mDstRectF.top;
        float right = left + mDstRectF.width();
        float bottom = top + mDstRectF.height();
        float radius = mCornerRadius;

        //topLeft
        if (!mCornersRounded[0]) {
            mSquareCornersRect.set(left, top, left + radius, top + radius);
            canvas.drawRect(mSquareCornersRect, mPaint);
        }

        //topRight
        if (!mCornersRounded[1]) {
            mSquareCornersRect.set(right - radius, top, right, radius);
            canvas.drawRect(mSquareCornersRect, mPaint);
        }

        //bottomRight
        if (!mCornersRounded[2]) {
            mSquareCornersRect.set(right - radius, bottom - radius, right, bottom);
            canvas.drawRect(mSquareCornersRect, mPaint);
        }

        //bottomLeft
        if (!mCornersRounded[3]) {
            mSquareCornersRect.set(left, bottom - radius, left + radius, bottom);
            canvas.drawRect(mSquareCornersRect, mPaint);
        }
    }

    void redrawBorderForSquareCorners(Canvas canvas) {
        if(mBorderWidth <=0 || mBorderPaint == null) return;

        if (all(mCornersRounded)) {
            // no square corners
            return;
        }

        if (mCornerRadius == 0) {
            return; // no round corners
        }

        float left = mDstRectF.left;
        float top = mDstRectF.top;
        float right = left + mDstRectF.width();
        float bottom = top + mDstRectF.height();
        float radius = mCornerRadius;
        float offset = mBorderWidth / 2;

        //topLeft
        if (!mCornersRounded[0]) {
            canvas.drawLine(left - offset, top, left + radius, top, mBorderPaint);
            canvas.drawLine(left, top - offset, left, top + radius, mBorderPaint);
        }

        //topRight
        if (!mCornersRounded[1]) {
            canvas.drawLine(right - radius - offset, top, right, top, mBorderPaint);
            canvas.drawLine(right, top - offset, right, top + radius, mBorderPaint);
        }

        //bottomRight
        if (!mCornersRounded[2]) {
            canvas.drawLine(right - radius - offset, bottom, right + offset, bottom, mBorderPaint);
            canvas.drawLine(right, bottom - radius, right, bottom, mBorderPaint);
        }

        //bottomLeft
        if (!mCornersRounded[3]) {
            canvas.drawLine(left - offset, bottom, left + radius, bottom, mBorderPaint);
            canvas.drawLine(left, bottom - radius, left, bottom, mBorderPaint);
        }
    }

    /**
     * @return the paint used to render this drawable
     */
    public final Paint getPaint() {
        return mPaint;
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
        invalidateSelf();
    }

    @SuppressWarnings("deprecation")
    @Override
    @Deprecated
    public void setDither(boolean dither) {
        mPaint.setDither(dither);
        invalidateSelf();
    }

    /**
     * Adds a new animation listener
     *
     * @param listener animation listener to be added, not null
     * @throws NullPointerException if listener is null
     */
    public void setAnimationListener(AnimationListener listener) {
        mListener = listener;
    }

    public void removeAnimationListener() {
        mListener = null;
    }

    @Override
    public ColorFilter getColorFilter() {
        return mPaint.getColorFilter();
    }

    /**
     * Retrieves a copy of currently buffered frame.
     *
     * @return current frame
     */
    public Bitmap getCurrentFrame() {
        if (mBuffer != null)
            return mBuffer.copy(mBuffer.getConfig(), mBuffer.isMutable());
        return null;
    }

    PorterDuffColorFilter updateTintFilter(ColorStateList tint, PorterDuff.Mode tintMode) {
        if (tint == null || tintMode == null) {
            return null;
        }

        final int color = tint.getColorForState(getState(), Color.TRANSPARENT);
        return new PorterDuffColorFilter(color, tintMode);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mTint = tint;
        mTintFilter = updateTintFilter(tint, mTintMode);
        invalidateSelf();
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mTintMode = tintMode;
        mTintFilter = updateTintFilter(mTint, tintMode);
        invalidateSelf();
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        if (mTint != null && mTintMode != null) {
            mTintFilter = updateTintFilter(mTint, mTintMode);
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() {
        return super.isStateful() || (mTint != null && mTint.isStateful());
    }

    /**
     * Returns zero-based index of recently rendered frame in given loop or -1 when drawable is recycled.
     *
     * @return index of recently rendered frame or -1 when drawable is recycled
     */
    public int getCurrentFrameIndex() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.getCurrentFrameIndex();
        return -1;
    }

    /**
     * Returns zero-based index of currently played animation loop. If animation is infinite or
     * drawable is recycled 0 is returned.
     *
     * @return index of currently played animation loop
     */
    public int getCurrentLoop() {
        if (mNativeInfoHandle != null) {
            final int currentLoop = mNativeInfoHandle.getCurrentLoop();
            if (currentLoop == 0 || currentLoop < mNativeInfoHandle.getLoopCount()) {
                return currentLoop;
            } else {
                return currentLoop - 1;
            }
        }
        return -1;
    }

    /**
     * Returns whether all animation loops has ended. If drawable is recycled false is returned.
     *
     * @return true if all animation loops has ended
     */
    public boolean isAnimationCompleted() {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.isAnimationCompleted();
        return false;
    }

    /**
     * Returns duration of the given frame (in milliseconds). If there is no data (no Graphics
     * Control Extension blocks or drawable is recycled) 0 is returned.
     *
     * @param index index of the frame
     * @return duration of the given frame in milliseconds
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= number of frames
     */
    public int getFrameDuration(final int index) {
        if (mNativeInfoHandle != null)
            return mNativeInfoHandle.getFrameDuration(index);
        return 0;
    }

    /**
     * Sets the corner radius to be applied when drawing the bitmap.
     * Note that changing corner radius will cause replacing current {@link Paint} shader by {@link BitmapShader}.
     *
     * @param cornerRadius corner radius or 0 to remove rounding
     */
    public void setCornerRadius(final float cornerRadius) {
        mCornerRadius = cornerRadius;
        final Shader bitmapShader;
        if (cornerRadius > 0) {
            bitmapShader = new BitmapShader(mBuffer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            bitmapShader = null;
        }
        mPaint.setShader(bitmapShader);
    }

    public void setCornerRadius(float cornerRadius, boolean[] cornersRounded) {
        mCornersRounded = cornersRounded;
        setCornerRadius(cornerRadius);
    }

    public void setCornerRadius(float cornerRadius, boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
        mCornersRounded[0] = topLeft;
        mCornersRounded[1] = topRight;
        mCornersRounded[2] = bottomLeft;
        mCornersRounded[3] = bottomRight;
        setCornerRadius(cornerRadius);
    }

    public void setBorder(final int borderWidth, int color) {
        mBorderWidth = borderWidth;
        mColorBorder = color;
        if(mBorderPaint == null) {
            mBorderPaint = new Paint();
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setAntiAlias(true);
        }
        mBorderPaint.setColor(color/*mTint.getColorForState(getState(), color)*/);
        mBorderPaint.setStrokeWidth(mBorderWidth);
    }

    /**
     * @return The corner radius applied when drawing this drawable. 0 when drawable is not rounded.
     */
    public
    float getCornerRadius() {
        return mCornerRadius;
    }

    public boolean hasBitmap() {
        return mRenderingBitmap != null;
    }

    public void setLoop(boolean loop) {
        this.mLoop = loop;
    }

    public boolean isLoop() {
        return mLoop;
    }

    Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            if (mNativeInfoHandle != null) {
                if (mNativeInfoHandle.reset()) {
                    start();
                }
            }
        }
    };

    Runnable loadFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled()) {
                loadFrame();
            }
        }
    };

    private final int MAX_RENDER_DELAY_VALUE = 2000;
    private final int DEFAULT_RENDER_DELAY_VALUE = 200;
    void loadFrame() {
        if (mIsRunning) {
            if (!mIsDecoded) {
                if(mCurrentMode != MODE_INPUTSTREAM) {
                    init();
                }
            }

            if (mNativeInfoHandle != null && mBuffer != null) {
                try {
                    long invalidationDelay = Math.min(mNativeInfoHandle.renderFrame(mBuffer), MAX_RENDER_DELAY_VALUE);
                    // If delay value is invalid, just delay 200ms
                    if (invalidationDelay < 0)
                        invalidationDelay = DEFAULT_RENDER_DELAY_VALUE;

                    if (invalidationDelay >= 0) {
                        mRenderingBitmap = mBuffer;
                        mNextFrameRenderTime = System.currentTimeMillis() + invalidationDelay;
                        // The first frame is 1, the last frame is 0. WTF
                        if (mListener != null && getCurrentFrameIndex() == 0 && !GIF_AUTO_REPEAT) {
                            mIsAnimationCompleted = true;
                            mInvalidationHandler.sendEmptyMessage(MSG_TYPE_ANIMATION_COMPLETED);
                        }
                    } else {
                        mNextFrameRenderTime = Integer.MIN_VALUE;
                        mIsRunning = false;
                    }
                } catch (OutOfMemoryError e) {
                    e.printStackTrace();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            if (mIsRunning){
                mInvalidationHandler.sendEmptyMessage(MSG_TYPE_INVALIDATION);
            }
        }
    }

    public void resetState() {
        mIsRunning = false;
    }

    public static void setGifMaxSizeFeed(int size){
        GIF_MAX_SIZE_FEED = size;
    }

    public static void setGifMaxSizeChat(int size) {
        GIF_MAX_SIZE_CHAT = size;
    }

    public static void setGifMaxPreviewSize(int size) {
        GIF_MAX_PREVIEW_SIZE = size;
    }

    public static void setGifEnabled(boolean enabled) {
        GIF_ENABLED = enabled;
    }

    public static boolean isGifEnabled() {
        return GIF_ENABLED;
    }

    public static void setGifAutoRepeat(boolean auto) {
        GIF_AUTO_REPEAT = auto;
    }

    public static boolean isGifAutoRepeat() {
        return GIF_AUTO_REPEAT;
    }

    public static boolean all(boolean[] booleans) {
        for (boolean b : booleans) {
            if (b) { return false; }
        }
        return true;
    }
}