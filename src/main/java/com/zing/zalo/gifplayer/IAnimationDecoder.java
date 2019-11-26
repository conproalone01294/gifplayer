/**
 *
 */
package com.zing.zalo.gifplayer;

import android.graphics.Bitmap;

/**
 * @author khanhtm
 */
public interface IAnimationDecoder {
    /**
     * Gets display duration for specified frame.
     *
     * @param n int index of frame
     * @return delay in milliseconds
     */
    public int getDelay(int n);

    /**
     * Gets the number of frames read from file.
     *
     * @return frame count
     */
    public int getFrameCount();

    /**
     * Gets the first (or only) image read.
     *
     * @return BufferedBitmap containing first frame, or null if none.
     */
    public Bitmap getBitmap();

    /**
     * Gets the "Netscape" iteration count, if any. A count of 0 means repeat indefinitiely.
     *
     * @return iteration count if one was specified, else 1.
     */
    public int getLoopCount();

    /**
     * Gets the image contents of frame n.
     *
     * @return BufferedBitmap representation of frame, or null if n is invalid.
     */
    public Bitmap getFrame(int n);

    /**
     * Gets the image contents of frame n.
     * isReuse: whether to use ashMem and BitmapPool or not
     *
     * @return BufferedBitmap representation of frame, or null if n is invalid.
     */
    public Bitmap getFrame(int n, boolean isReuse);


//    public void setImageViewFromFrame(int n, ImageView iv);

    public void clearData();

    public int getType();
}
