package dev.matrixlab.webp4j;

/**
 * Holds animation metadata extracted from GIF images.
 * <p>
 * This class provides information about a GIF image without performing
 * the full conversion. It's useful for determining whether a GIF is
 * animated, its dimensions, and other properties before conversion.
 * <p>
 * Instances of this class are immutable after being populated by
 * the native layer or GIF decoder.
 */
public class AnimationInfo {

    private int frameCount;
    private int width;
    private int height;
    private int loopCount;
    private boolean hasTransparency;

    /**
     * Package-private constructor.
     * Instances are created by WebPCodec or native code.
     */
    AnimationInfo() {
    }

    /**
     * Gets the number of frames in the GIF animation.
     *
     * @return Frame count (1 for static GIF, >1 for animated GIF)
     */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Package-private setter for frame count.
     * Called by WebPCodec or native code.
     */
    void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    /**
     * Gets the width of the GIF image in pixels.
     *
     * @return Image width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Package-private setter for width.
     * Called by WebPCodec or native code.
     */
    void setWidth(int width) {
        this.width = width;
    }

    /**
     * Gets the height of the GIF image in pixels.
     *
     * @return Image height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Package-private setter for height.
     * Called by WebPCodec or native code.
     */
    void setHeight(int height) {
        this.height = height;
    }

    /**
     * Gets the loop count for the animation.
     *
     * @return Loop count (0=infinite loop, N=loop N times)
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * Package-private setter for loop count.
     * Called by WebPCodec or native code.
     */
    void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

    /**
     * Checks if the GIF has transparency (transparent color index).
     *
     * @return true if the GIF has at least one transparent pixel
     */
    public boolean hasTransparency() {
        return hasTransparency;
    }

    /**
     * Package-private setter for transparency flag.
     * Called by WebPCodec or native code.
     */
    void setHasTransparency(boolean hasTransparency) {
        this.hasTransparency = hasTransparency;
    }

    /**
     * Checks if the GIF is animated (has multiple frames).
     *
     * @return true if frame count > 1
     */
    public boolean isAnimated() {
        return frameCount > 1;
    }

    @Override
    public String toString() {
        return "AnimationInfo{" +
                "frameCount=" + frameCount +
                ", width=" + width +
                ", height=" + height +
                ", loopCount=" + loopCount +
                ", hasTransparency=" + hasTransparency +
                ", isAnimated=" + isAnimated() +
                '}';
    }
}