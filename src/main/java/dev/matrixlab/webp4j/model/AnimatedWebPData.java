package dev.matrixlab.webp4j.model;

import java.util.List;

/**
 * Contains all decoded frames and metadata from an animated WebP image.
 * <p>
 * This class holds the complete result of decoding an animated WebP file,
 * including individual frames with their timestamps, canvas dimensions,
 * loop count, and background color.
 * <p>
 * The raw frame data (byte arrays and timestamps) are populated by the
 * native layer, and then converted to {@link AnimatedWebPFrame} objects
 * by the decoder.
 */
public class AnimatedWebPData {

    private List<AnimatedWebPFrame> frames;
    private int canvasWidth;
    private int canvasHeight;
    private int loopCount;
    private int bgcolor;
    private int frameCount;

    // Raw data populated by JNI (before conversion to AnimatedWebPFrame list)
    private byte[][] rawFrameData;
    private int[] timestamps;

    /**
     * Public constructor.
     */
    public AnimatedWebPData() {
    }

    /**
     * Gets the list of decoded frames.
     *
     * @return List of frames, each containing a BufferedImage and timestamp
     */
    public List<AnimatedWebPFrame> getFrames() {
        return frames;
    }

    /**
     * Sets the list of decoded frames.
     *
     * @param frames List of decoded frames
     */
    public void setFrames(List<AnimatedWebPFrame> frames) {
        this.frames = frames;
    }

    /**
     * Gets the canvas width in pixels.
     *
     * @return Canvas width
     */
    public int getCanvasWidth() {
        return canvasWidth;
    }

    /**
     * Sets the canvas width.
     *
     * @param canvasWidth Canvas width in pixels
     */
    public void setCanvasWidth(int canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    /**
     * Gets the canvas height in pixels.
     *
     * @return Canvas height
     */
    public int getCanvasHeight() {
        return canvasHeight;
    }

    /**
     * Sets the canvas height.
     *
     * @param canvasHeight Canvas height in pixels
     */
    public void setCanvasHeight(int canvasHeight) {
        this.canvasHeight = canvasHeight;
    }

    /**
     * Gets the loop count for the animation.
     *
     * @return Loop count (0 = infinite loop)
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * Sets the loop count.
     *
     * @param loopCount Loop count (0 = infinite loop)
     */
    public void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

    /**
     * Gets the background color of the animation canvas.
     *
     * @return Background color as ARGB integer
     */
    public int getBgcolor() {
        return bgcolor;
    }

    /**
     * Sets the background color.
     *
     * @param bgcolor Background color as ARGB integer
     */
    public void setBgcolor(int bgcolor) {
        this.bgcolor = bgcolor;
    }

    /**
     * Gets the total number of frames in the animation.
     *
     * @return Frame count
     */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Sets the frame count.
     *
     * @param frameCount Total number of frames
     */
    public void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    /**
     * Gets the raw RGBA frame data populated by the native layer.
     * <p>
     * Each element is an RGBA byte array of size canvasWidth * canvasHeight * 4.
     * This data is used internally by the decoder to create BufferedImage objects.
     *
     * @return Array of RGBA frame byte arrays, or null if not yet populated
     */
    public byte[][] getRawFrameData() {
        return rawFrameData;
    }

    /**
     * Sets the raw frame data. Called by native code.
     *
     * @param rawFrameData Array of RGBA frame byte arrays
     */
    public void setRawFrameData(byte[][] rawFrameData) {
        this.rawFrameData = rawFrameData;
    }

    /**
     * Gets the cumulative timestamps for each frame in milliseconds.
     * <p>
     * These are populated by the native layer and represent the time
     * at which each frame should be displayed from the start of the animation.
     *
     * @return Array of timestamps in milliseconds, or null if not yet populated
     */
    public int[] getTimestamps() {
        return timestamps;
    }

    /**
     * Sets the timestamps array. Called by native code.
     *
     * @param timestamps Array of cumulative timestamps in milliseconds
     */
    public void setTimestamps(int[] timestamps) {
        this.timestamps = timestamps;
    }

    /**
     * Computes per-frame delays from the cumulative timestamps.
     * <p>
     * The delay for each frame is calculated as the difference between
     * consecutive timestamps. The first frame's delay is its timestamp value.
     *
     * @return Array of per-frame delays in milliseconds
     * @throws IllegalStateException If frames have not been decoded yet
     */
    public int[] getDelays() {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalStateException("No frames have been decoded yet");
        }

        int[] delays = new int[frames.size()];
        int prevTimestamp = 0;
        for (int i = 0; i < frames.size(); i++) {
            int currentTimestamp = frames.get(i).getTimestamp();
            delays[i] = currentTimestamp - prevTimestamp;
            prevTimestamp = currentTimestamp;
        }
        return delays;
    }

    @Override
    public String toString() {
        return "AnimatedWebPData{" +
                "frameCount=" + frameCount +
                ", canvasWidth=" + canvasWidth +
                ", canvasHeight=" + canvasHeight +
                ", loopCount=" + loopCount +
                ", bgcolor=0x" + Integer.toHexString(bgcolor) +
                '}';
    }
}