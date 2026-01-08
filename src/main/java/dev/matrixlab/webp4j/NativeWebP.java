package dev.matrixlab.webp4j;

public class NativeWebP {

    private static volatile boolean nativeLibraryLoaded = false;

    private NativeWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * int WebPGetInfo(const uint8_t* data, size_t data_size, int* width, int* height);
     */
    public static native boolean getInfo(byte[] data, int[] dimensions);

    /**
     * VP8StatusCode WebPGetFeatures(const uint8_t* data, size_t data_size, WebPBitstreamFeatures* features);
     */
    public static native int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features);

    /**
     * size_t WebPEncodeRGB(const uint8_t* rgb, int width, int height, int stride, float quality_factor, uint8_t** output);
     */
    public static native byte[] encodeRGB(byte[] image, int width, int height, int stride, float quality);

    /**
     * size_t WebPEncodeRGBA(const uint8_t* rgba, int width, int height, int stride, float quality_factor, uint8_t** output);
     */
    public static native byte[] encodeRGBA(byte[] image, int width, int height, int stride, float quality);

    /**
     * size_t WebPEncodeLosslessRGB(const uint8_t* rgb, int width, int height, int stride, uint8_t** output);
     */
    public static native byte[] encodeLosslessRGB(byte[] image, int width, int height, int stride);

    /**
     * size_t WebPEncodeLosslessRGBA(const uint8_t* rgba, int width, int height, int stride, uint8_t** output);
     */
    public static native byte[] encodeLosslessRGBA(byte[] image, int width, int height, int stride);

    /**
     * uint8_t* WebPDecodeRGBInto(const uint8_t* data, size_t data_size, uint8_t* output_buffer, int output_buffer_size, int output_stride);
     */
    public static native boolean decodeRGBInto(byte[] data, byte[] outputBuffer, int outputStride);

    /**
     * uint8_t* WebPDecodeRGBAInto(const uint8_t* data, size_t data_size, uint8_t* output_buffer, int output_buffer_size, int output_stride);
     */
    public static native boolean decodeRGBAInto(byte[] data, byte[] outputBuffer, int outputStride);

    /**
     * Gets information about a GIF file using native giflib.
     *
     * @param gifData GIF image bytes
     * @param info AnimationInfo object to populate (via JNI field access)
     * @return True on success, false on failure
     */
    public static native boolean getGifInfo(byte[] gifData, AnimationInfo info);

    /**
     * Converts GIF data to WebP format using native giflib decoder.
     * This is the primary (fast) path using native GIF decoding.
     *
     * @param gifData GIF image bytes
     * @param quality Quality factor (0-100)
     * @param lossless True for lossless encoding
     * @param compressionMethod Compression method (0-6)
     * @param extractFirstFrameOnly True to extract only first frame
     * @param loopCount Loop count (0=infinite, -1=use GIF's)
     * @param kmin Minimum key-frame distance
     * @param kmax Maximum key-frame distance
     * @param minimizeSize True to minimize output size
     * @param allowMixed True to allow mixed compression
     * @return WebP encoded byte array, or null on failure
     */
    public static native byte[] encodeGifToWebP(
            byte[] gifData,
            float quality,
            boolean lossless,
            int compressionMethod,
            boolean extractFirstFrameOnly,
            int loopCount,
            int kmin,
            int kmax,
            boolean minimizeSize,
            boolean allowMixed
    );

    /**
     * Encodes animated WebP from Java-decoded GIF frames.
     * This is used when GIF is decoded by Java ImageIO (fallback path).
     *
     * Uses WebPAnimEncoder API to create animated WebP.
     *
     * @param frames Array of RGBA frame data (each frame is width * height * 4 bytes)
     * @param delays Array of frame delays in milliseconds
     * @param width Canvas width
     * @param height Canvas height
     * @param quality Quality factor (0-100)
     * @param lossless True for lossless encoding
     * @param compressionMethod Compression method (0-6)
     * @param loopCount Loop count (0=infinite)
     * @param kmin Minimum key-frame distance
     * @param kmax Maximum key-frame distance
     * @param minimizeSize True to minimize output size
     * @param allowMixed True to allow mixed compression
     * @return WebP encoded byte array, or null on failure
     */
    public static native byte[] encodeAnimatedWebP(
            byte[][] frames,
            int[] delays,
            int width,
            int height,
            float quality,
            boolean lossless,
            int compressionMethod,
            int loopCount,
            int kmin,
            int kmax,
            boolean minimizeSize,
            boolean allowMixed
    );

    // Use the NativeLibraryLoaderUtils to load the native library
    static void loadNativeLibrary() {
        if (!nativeLibraryLoaded) {
            synchronized (NativeWebP.class) {
                if (!nativeLibraryLoaded) {
                    try {
                        NativeLibraryLoaderUtils.loadLibrary();
                        nativeLibraryLoaded = true;
                    } catch (Exception e) {
                        throw new NativeLibraryNotFoundException("Failed to load native library", e);
                    }
                }
            }
        }
    }

    // When the class is loaded, try to load the native library
    static {
        try {
            loadNativeLibrary();
        } catch (Exception e) {
            throw new NativeLibraryNotFoundException("Failed to load native library", e);
        }
    }

}
