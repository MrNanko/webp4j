package dev.matrixlab.webp4j.gif;

/**
 * Configuration options for GIF to WebP conversion.
 * <p>
 * This class encapsulates the parameters used when converting GIF images
 * to WebP format. It provides fine-grained control over encoding quality,
 * compression mode, and animation properties.
 * <p>
 * Based on Google's gif2webp tool options: https://developers.google.com/speed/webp/docs/gif2webp
 */
public class GifToWebPConfig {

    private float quality = 75.0f;
    private boolean lossless = false;
    private int compressionMethod = 4;
    private boolean extractFirstFrameOnly = false;
    private int loopCount = -1;  // -1 = use GIF's loop count
    private int kmin = 9;
    private int kmax = 17;
    private boolean minimizeSize = false;
    private boolean allowMixed = false;
    private boolean multiThreaded = true;

    /**
     * Gets the quality factor for lossy compression.
     *
     * @return Quality value between 0 (worst) and 100 (best). Default is 75.
     */
    public float getQuality() {
        return quality;
    }

    /**
     * Sets the quality factor for lossy compression.
     *
     * @param quality Quality value between 0 (worst) and 100 (best)
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setQuality(float quality) {
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("Quality must be between 0 and 100");
        }
        this.quality = quality;
        return this;
    }

    /**
     * Checks if lossy compression is enabled.
     *
     * @return true if using lossy compression, false for lossless
     */
    public boolean isLossless() {
        return lossless;
    }

    /**
     * Sets whether to use lossless compression.
     * When true, lossless compression is used (recommended for GIF).
     * When false, lossy compression is used.
     *
     * @param lossless true for lossless compression, false for lossy
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setLossless(boolean lossless) {
        this.lossless = lossless;
        return this;
    }

    /**
     * Gets the compression method.
     *
     * @return Compression method value between 0 (fastest) and 6 (slowest). Default is 4.
     */
    public int getCompressionMethod() {
        return compressionMethod;
    }

    /**
     * Sets the compression method.
     *
     * @param compressionMethod Value between 0 (fastest) and 6 (slowest, best quality)
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setCompressionMethod(int compressionMethod) {
        if (compressionMethod < 0 || compressionMethod > 6) {
            throw new IllegalArgumentException("Compression method must be between 0 and 6");
        }
        this.compressionMethod = compressionMethod;
        return this;
    }

    /**
     * Checks if only the first frame should be extracted.
     *
     * @return true if extracting first frame only, false to process all frames
     */
    public boolean isExtractFirstFrameOnly() {
        return extractFirstFrameOnly;
    }

    /**
     * Sets whether to extract only the first frame of an animated GIF.
     * When true, the output will be a static WebP image.
     *
     * @param extractFirstFrameOnly true to extract first frame only, false to process all frames
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setExtractFirstFrameOnly(boolean extractFirstFrameOnly) {
        this.extractFirstFrameOnly = extractFirstFrameOnly;
        return this;
    }

    /**
     * Gets the loop count for animation.
     *
     * @return Loop count (0=infinite, -1=use GIF's loop count). Default is -1.
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * Sets the loop count for animation.
     *
     * @param loopCount Number of loops (0=infinite, -1=use GIF's loop count)
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setLoopCount(int loopCount) {
        if (loopCount < -1) {
            throw new IllegalArgumentException("Loop count must be >= -1");
        }
        this.loopCount = loopCount;
        return this;
    }

    /**
     * Gets the minimum distance between key frames.
     *
     * @return Minimum key frame distance. Default is 9.
     */
    public int getKmin() {
        return kmin;
    }

    /**
     * Sets the minimum distance between key frames.
     *
     * @param kmin Minimum distance (recommended: 9 for lossless, 3 for lossy)
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setKmin(int kmin) {
        if (kmin < 0) {
            throw new IllegalArgumentException("Kmin must be non-negative");
        }
        this.kmin = kmin;
        return this;
    }

    /**
     * Gets the maximum distance between key frames.
     *
     * @return Maximum key frame distance. Default is 17.
     */
    public int getKmax() {
        return kmax;
    }

    /**
     * Sets the maximum distance between key frames.
     *
     * @param kmax Maximum distance (recommended: 17 for lossless, 5 for lossy)
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setKmax(int kmax) {
        if (kmax < 0) {
            throw new IllegalArgumentException("Kmax must be non-negative");
        }
        this.kmax = kmax;
        return this;
    }

    /**
     * Checks if size minimization is enabled.
     *
     * @return true if minimizing output size
     */
    public boolean isMinimizeSize() {
        return minimizeSize;
    }

    /**
     * Sets whether to minimize output size.
     * When true, the encoder will try to produce the smallest possible file,
     * at the cost of slower encoding.
     *
     * @param minimizeSize true to minimize output size
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setMinimizeSize(boolean minimizeSize) {
        this.minimizeSize = minimizeSize;
        return this;
    }

    /**
     * Checks if mixed compression mode is enabled.
     *
     * @return true if allowing mixed compression
     */
    public boolean isAllowMixed() {
        return allowMixed;
    }

    /**
     * Sets whether to allow mixed compression mode.
     * In mixed mode, the encoder automatically chooses lossy or lossless
     * compression for each frame based on heuristics.
     *
     * @param allowMixed true to enable mixed mode
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setAllowMixed(boolean allowMixed) {
        this.allowMixed = allowMixed;
        return this;
    }

    /**
     * Checks if multi-threaded encoding is enabled.
     *
     * @return true if libwebp multi-threads each frame's encode
     */
    public boolean isMultiThreaded() {
        return multiThreaded;
    }

    /**
     * Sets whether libwebp may use multiple threads to encode each frame
     * (libwebp's {@code thread_level}, the same knob as {@code gif2webp -mt}).
     * <p>
     * This is a pure speed knob: the output bitstream is bit-identical to
     * single-threaded encoding, only faster on multi-core machines. Frames are
     * still assembled in order — the parallelism is <em>within</em> each frame's
     * VP8 compression, so frame-to-frame dependencies are preserved. Enabled by
     * default.
     *
     * @param multiThreaded true to enable multi-threaded frame encoding
     * @return This config instance for method chaining
     */
    public GifToWebPConfig setMultiThreaded(boolean multiThreaded) {
        this.multiThreaded = multiThreaded;
        return this;
    }

    /**
     * Creates a default configuration optimized for lossless GIF conversion.
     * Recommended for preserving GIF quality without any loss.
     *
     * @return A new GifToWebPConfig with lossless settings
     */
    public static GifToWebPConfig createLosslessConfig() {
        return new GifToWebPConfig()
                .setLossless(true)
                .setKmin(9)
                .setKmax(17);
    }

    /**
     * Creates a default configuration optimized for lossy GIF conversion.
     * Recommended for reducing file size at the cost of some quality loss.
     *
     * @param quality Quality factor (0-100)
     * @return A new GifToWebPConfig with lossy settings
     */
    public static GifToWebPConfig createLossyConfig(float quality) {
        return new GifToWebPConfig()
                .setLossless(false)
                .setQuality(quality)
                .setKmin(3)
                .setKmax(5);
    }
}