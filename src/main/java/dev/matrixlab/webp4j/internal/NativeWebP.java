package dev.matrixlab.webp4j.internal;

import dev.matrixlab.webp4j.model.AnimatedWebPData;
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
     * Encodes packed ARGB pixels (0xAARRGGBB ints, i.e. BGRA byte order on
     * little-endian) to a WebP bitstream.
     * <p>
     * The pixel array is pinned with GetPrimitiveArrayCritical and imported
     * directly by libwebp (WebPPictureImportBGRA/BGRX) — no intermediate copy.
     * The array is never written to, so a BufferedImage's live backing array
     * can be passed safely.
     *
     * @param pixels   Packed ARGB pixels, length must equal width * height
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param hasAlpha False to ignore the alpha byte of each pixel
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encode(int[] pixels, int width, int height,
                                       float quality, boolean lossless, boolean hasAlpha);

    /**
     * Encodes interleaved BGR bytes (a TYPE_3BYTE_BGR backing array, length
     * width * height * 3) to a WebP bitstream via WebPPictureImportBGR.
     * <p>
     * Same zero-copy contract as {@link #encode}: the array is pinned, read
     * directly by libwebp, and never written to.
     *
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @return WebP encoded bytes, or null on failure
     */
    public static native byte[] encodeBgr(byte[] pixels, int width, int height,
                                          float quality, boolean lossless);

    /**
     * Decodes a WebP bitstream directly into a packed ARGB int[] — libwebp
     * writes BGRA bytes straight into the pinned Java array, so the array can
     * be a BufferedImage's live backing store.
     * <p>
     * Both arrays stay pinned for the full duration of the decode, which can
     * delay GC for very large images — the accepted cost of zero-copy.
     *
     * @param data         WebP bitstream
     * @param output       Destination, length must equal width * height
     * @param outputStride Row stride in BYTES (width * 4)
     * @return true on success
     */
    public static native boolean decodeInto(byte[] data, int[] output, int outputStride);

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
     * Encodes an animated WebP from packed ARGB frames using the
     * WebPAnimEncoder API.
     * <p>
     * Each frame array is pinned and imported directly by libwebp
     * (WebPPictureImportBGRA) — frames are never written to, so live
     * BufferedImage backing arrays can be passed safely.
     *
     * @param frames Packed ARGB frames, each of length width * height
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
    public static native byte[] encodeAnimated(
            int[][] frames,
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
     * Decodes an animated WebP image into individual frames.
     * <p>
     * Uses the libwebp WebPAnimDecoder API to extract all frames as packed
     * ARGB pixels along with their cumulative timestamps.
     * <p>
     * The result object will have its fields populated:
     * - canvasWidth, canvasHeight, loopCount, bgcolor, frameCount (metadata)
     * - framePixels (int[][] of packed ARGB frame pixels)
     * - timestamps (int[] of cumulative timestamps in milliseconds)
     *
     * @param webPData The animated WebP image data
     * @param result   AnimatedWebPData object to populate with decoded frames
     * @return true on success, false on failure
     */
    public static native boolean decodeAnimated(byte[] webPData, AnimatedWebPData result);

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
