package dev.matrixlab.webp4j;

/**
 * Represents WebP bitstream features.
 * This class is used by JNI code to return image metadata.
 * Fields are public to allow direct access from native code.
 */
public class WebPBitstreamFeatures {

    /**
     * Image width in pixels.
     */
    private int width;

    /**
     * Image height in pixels.
     */
    private int height;

    /**
     * True if the bitstream contains an alpha channel.
     */
    private boolean hasAlpha;

    /**
     * True if the bitstream is an animation.
     */
    private boolean hasAnimation;

    /**
     * Image format: 0 = undefined/mixed, 1 = lossy, 2 = lossless.
     */
    private int format;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isHasAlpha() {
        return hasAlpha;
    }

    public void setHasAlpha(boolean hasAlpha) {
        this.hasAlpha = hasAlpha;
    }

    public boolean isHasAnimation() {
        return hasAnimation;
    }

    public void setHasAnimation(boolean hasAnimation) {
        this.hasAnimation = hasAnimation;
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return "WebPBitstreamFeatures{" +
                "width=" + width +
                ", height=" + height +
                ", hasAlpha=" + hasAlpha +
                ", hasAnimation=" + hasAnimation +
                ", format=" + format +
                '}';
    }
}
