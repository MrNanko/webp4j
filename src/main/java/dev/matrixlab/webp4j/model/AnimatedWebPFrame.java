package dev.matrixlab.webp4j.model;

import java.awt.image.BufferedImage;

/**
 * Represents a single frame extracted from an animated WebP image.
 * <p>
 * Each frame contains the fully composited image data and its
 * cumulative timestamp in milliseconds from the start of the animation.
 */
public class AnimatedWebPFrame {

    private BufferedImage image;
    private int timestamp;

    /**
     * Public constructor.
     */
    public AnimatedWebPFrame() {
    }

    /**
     * Creates a frame with the given image and timestamp.
     *
     * @param image     The fully composited frame image
     * @param timestamp Cumulative timestamp in milliseconds from animation start
     */
    public AnimatedWebPFrame(BufferedImage image, int timestamp) {
        this.image = image;
        this.timestamp = timestamp;
    }

    /**
     * Gets the frame image.
     * <p>
     * This is a fully reconstructed canvas-sized RGBA image,
     * not just the frame sub-rectangle.
     *
     * @return The frame as a BufferedImage
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Sets the frame image.
     *
     * @param image The frame image
     */
    public void setImage(BufferedImage image) {
        this.image = image;
    }

    /**
     * Gets the cumulative timestamp of this frame in milliseconds.
     * <p>
     * The timestamp represents the time at which this frame should be
     * displayed, measured from the start of the animation.
     *
     * @return Timestamp in milliseconds
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the cumulative timestamp of this frame.
     *
     * @param timestamp Timestamp in milliseconds
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AnimatedWebPFrame{" +
                "image=" + (image != null ? image.getWidth() + "x" + image.getHeight() : "null") +
                ", timestamp=" + timestamp +
                '}';
    }
}