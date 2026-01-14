package dev.matrixlab.webp4j.internal;

import dev.matrixlab.webp4j.model.AnimationInfo;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;

public class NativeWebP {

    /**
     * Holds the cause of native library unavailability.
     * If null, the library loaded successfully and all native methods are available.
     * If not null, contains the exception that prevented loading.
     */
    private static final Throwable UNAVAILABILITY_CAUSE;

    private NativeWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Simple native method for smoke testing.
     * Returns the libwebp version number to verify JNI bindings work correctly.
     *
     * @return WebP library version number (e.g., 0x010300 for version 1.3.0)
     */
    private static native int getLibWebPVersion();

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

    /**
     * Checks if the native library has been successfully loaded.
     * <p>
     * This method can be used to determine if WebP operations are available
     * on the current platform without actually attempting to encode/decode.
     *
     * @return true if the native library is loaded and available, false otherwise
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Returns the cause of unavailability if the native library failed to load.
     * <p>
     * This is useful for debugging when WebP support is not available.
     * The returned Throwable can indicate issues like:
     * <ul>
     *   <li>Unsupported platform (no native library for this OS/architecture)</li>
     *   <li>Missing dependencies</li>
     *   <li>JNI signature mismatch</li>
     * </ul>
     *
     * @return The Throwable that caused loading to fail, or null if library loaded successfully
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    // Static initializer: attempt to load the native library when class is loaded
    static {
        Throwable cause = null;
        try {
            // Load the native library
            NativeLibraryLoader.loadLibrary();

            // Perform smoke test - verify JNI bindings work correctly
            // This ensures not just that the library loaded, but that native methods are callable
            int version = getLibWebPVersion();
            if (version <= 0) {
                throw new IllegalStateException("Invalid libwebp version: " + version);
            }

            // Native library loaded successfully
            // Users can check availability with isAvailable() or get version from unavailabilityCause()
        } catch (LinkageError | Exception e) {
            // Capture any errors during library loading:
            // - LinkageError: UnsatisfiedLinkError (native method not found) and other linking errors
            // - Exception: IO errors, security exceptions, etc.
            cause = e;
        }
        UNAVAILABILITY_CAUSE = cause;
    }

}
