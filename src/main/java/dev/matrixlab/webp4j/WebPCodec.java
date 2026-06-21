package dev.matrixlab.webp4j;

import dev.matrixlab.webp4j.animation.AnimatedWebPDecoder;
import dev.matrixlab.webp4j.animation.AnimatedWebPEncoder;
import dev.matrixlab.webp4j.batch.BatchProcessor;
import dev.matrixlab.webp4j.gif.GifToWebPConfig;
import dev.matrixlab.webp4j.gif.GifToWebPConverter;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.internal.PixelConverter;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.AnimationInfo;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Main facade class for WebP encoding and decoding operations.
 * <p>
 * This class provides a unified API for all WebP-related operations including:
 * <ul>
 *   <li>Static image encoding/decoding</li>
 *   <li>GIF to WebP conversion</li>
 *   <li>Animated WebP creation</li>
 * </ul>
 *
 * @author MrNanko
 * @since 1.4.0
 */
public final class WebPCodec {

    private WebPCodec() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    // ============================================
    // Platform Availability
    // ============================================

    /**
     * Checks if WebP support is available on the current platform.
     * <p>
     * This method verifies that the native WebP library has been successfully
     * loaded for the current operating system and architecture. It provides
     * a lightweight way to check platform support without attempting to
     * encode or decode an image.
     * <p>
     * Example usage:
     * <pre>
     * if (WebPCodec.isAvailable()) {
     *     // Show WebP export option in UI
     *     byte[] webpData = WebPCodec.encodeImage(image, 75);
     * } else {
     *     // Hide WebP option or show unsupported message
     * }
     * </pre>
     *
     * @return true if WebP operations are supported on this platform, false otherwise
     */
    public static boolean isAvailable() {
        return NativeWebP.isAvailable();
    }

    // ============================================
    // Static Image Encoding/Decoding
    // ============================================

    /**
     * Retrieves information about a WebP image.
     *
     * @param webPData Byte array containing WebP image data
     * @return int array containing width and height of the image [width, height]
     * @throws IOException If there is an error processing the image
     */
    public static int[] getWebPInfo(byte[] webPData) throws IOException {
        int[] dimensions = new int[2];
        boolean success = NativeWebP.getInfo(webPData, dimensions);

        if (!success) {
            throw new IOException("Failed to retrieve WebP image information.");
        }

        return dimensions;
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a WebP encoded byte array.
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @param quality       The WebP quality parameter (0-100). Ignored when lossless is true.
     * @param lossless      True for lossless encoding, false for lossy encoding.
     * @return A byte array containing the WebP encoded data.
     * @throws IOException              If an error occurs during image conversion or encoding.
     * @throws IllegalArgumentException If bufferedImage is null.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless) throws IOException {
        if (bufferedImage == null) {
            throw new IllegalArgumentException("The input BufferedImage cannot be null.");
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        boolean hasAlpha = bufferedImage.getColorModel().hasAlpha();

        // Zero-copy paths: the arrays below may be the image's live backing
        // store, so they must never be modified. TYPE_3BYTE_BGR (ImageIO's
        // usual output for JPEG and opaque PNG) feeds libwebp's BGR import
        // directly; TYPE_INT_ARGB/TYPE_INT_RGB feed the BGRA/BGRX import.
        byte[] encodedWebP;
        byte[] bgrPixels = hasAlpha ? null : PixelConverter.bgrPixelsOrNull(bufferedImage);
        if (bgrPixels != null) {
            encodedWebP = NativeWebP.encodeBgr(bgrPixels, width, height, quality, lossless);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(bufferedImage, hasAlpha);
            encodedWebP = NativeWebP.encode(pixels, width, height, quality, lossless, hasAlpha);
        }
        if (encodedWebP == null || encodedWebP.length == 0) {
            String encodingType = lossless ? "Lossless" : "Lossy";
            throw new IOException(encodingType + " WebP encoding failed.");
        }

        return encodedWebP;
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a lossy WebP encoded byte array.
     * This is a convenience method that calls encodeImage(bufferedImage, quality, false).
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @param quality       The WebP quality parameter (0-100).
     * @return A byte array containing the lossy WebP encoded data.
     * @throws IOException              If an error occurs during image conversion or encoding.
     * @throws IllegalArgumentException If bufferedImage is null.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality) throws IOException {
        return encodeImage(bufferedImage, quality, false);
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a lossless WebP encoded byte array.
     * This is a convenience method that calls encodeImage(bufferedImage, 0, true).
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @return A byte array containing the lossless WebP encoded data.
     * @throws IOException              If an error occurs during image conversion or encoding.
     * @throws IllegalArgumentException If bufferedImage is null.
     */
    public static byte[] encodeLosslessImage(BufferedImage bufferedImage) throws IOException {
        return encodeImage(bufferedImage, 0, true);
    }

    /**
     * Decodes a WebP image (stored as a byte array) into an RGB/RGBA BufferedImage.
     *
     * @param webPData The byte array containing the WebP encoded image.
     * @return A BufferedImage representing the decoded RGB/RGBA image.
     * @throws IOException              If an error occurs during retrieval of image info or decoding.
     * @throws IllegalArgumentException If webPData is null or empty.
     */
    public static BufferedImage decodeImage(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        WebPBitstreamFeatures features = new WebPBitstreamFeatures();
        int status = NativeWebP.getFeatures(webPData, webPData.length, features);
        VP8StatusCode code = VP8StatusCode.getStatusCode(status);
        if (code != VP8StatusCode.VP8_STATUS_OK) {
            throw new IOException("Failed to get WebP bitstream features, error code: " + code);
        }

        int width = features.getWidth();
        int height = features.getHeight();
        boolean hasAlpha = features.isHasAlpha();

        // Decode straight into the array that will back the returned image.
        int[] pixels = new int[width * height];
        if (!NativeWebP.decodeInto(webPData, pixels, width * 4)) {
            throw new IOException("Failed to decode WebP data.");
        }

        return PixelConverter.wrapPixels(pixels, width, height, hasAlpha);
    }

    // ============================================
    // Batch Processing (Delegate to BatchProcessor)
    // ============================================

    /**
     * Encodes a list of images to WebP in parallel.
     * <p>
     * Independent images are encoded concurrently across a shared internal thread
     * pool for multi-core speedup. Results are order-preserving: result {@code i}
     * corresponds to input image {@code i}.
     * <p>
     * For control over the thread pool (e.g. to cap or share threads in a server),
     * call {@link BatchProcessor#encodeImages(List, float, boolean, java.util.concurrent.ExecutorService)}
     * directly.
     * <p>
     * Example usage:
     * <pre>{@code
     * List<BufferedImage> images = Arrays.asList(img1, img2, img3);
     * List<byte[]> webps = WebPCodec.encodeImages(images, 75, false);
     * }</pre>
     *
     * @param images   List of images to encode (null or empty yields an empty list)
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding, false for lossy
     * @return An immutable list of encoded WebP byte arrays, one per input image, in order
     * @throws IOException              If encoding any image fails (names the failing index)
     * @throws IllegalArgumentException If any image element is null
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless) throws IOException {
        return BatchProcessor.encodeImages(images, quality, lossless);
    }

    /**
     * Decodes a list of WebP byte arrays into images in parallel.
     * <p>
     * Independent images are decoded concurrently across a shared internal thread
     * pool. Results are order-preserving: result {@code i} corresponds to input
     * array {@code i}.
     * <p>
     * For control over the thread pool, call
     * {@link BatchProcessor#decodeImages(List, java.util.concurrent.ExecutorService)} directly.
     *
     * @param encodedImages List of WebP-encoded byte arrays (null or empty yields an empty list)
     * @return An immutable list of decoded images, one per input array, in order
     * @throws IOException              If decoding any array fails (names the failing index)
     * @throws IllegalArgumentException If any element is null
     */
    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException {
        return BatchProcessor.decodeImages(encodedImages);
    }

    // ============================================
    // GIF to WebP Conversion (Delegate to GifToWebPConverter)
    // ============================================

    /**
     * Gets information about a GIF image without performing full conversion.
     * <p>
     * This is useful for determining whether a GIF is animated, its dimensions,
     * and other properties before deciding how to convert it.
     *
     * @param gifData Byte array containing the GIF image data
     * @return AnimationInfo containing frame count, dimensions, loop count, and transparency info
     * @throws IOException              If reading GIF information fails
     * @throws IllegalArgumentException If gifData is null or empty
     */
    public static AnimationInfo getGifInfo(byte[] gifData) throws IOException {
        return GifToWebPConverter.getInfo(gifData);
    }

    /**
     * Converts a GIF image to WebP format with default lossy settings.
     * <p>
     * This is a convenience method that uses quality factor 75 and lossy compression.
     * For more control over the conversion, use {@link #encodeGifToWebP(byte[], GifToWebPConfig)}.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the WebP encoded image
     * @throws IOException              If conversion fails
     * @throws IllegalArgumentException If gifData is null or empty
     */
    public static byte[] encodeGifToWebP(byte[] gifData) throws IOException {
        return GifToWebPConverter.convert(gifData);
    }

    /**
     * Converts a GIF image to WebP format.
     * <p>
     * This method automatically detects whether the GIF is static or animated,
     * and handles both cases appropriately. It uses a dual-path approach:
     * <ul>
     *   <li>Primary: Native giflib decoder (fast, complete GIF support)</li>
     *   <li>Fallback: Java ImageIO decoder (slower, but works when native library unavailable)</li>
     * </ul>
     *
     * @param gifData Byte array containing the GIF image data
     * @param config  Configuration for conversion (quality, lossless, compression, etc.)
     * @return Byte array containing the WebP encoded image
     * @throws IOException              If conversion fails
     * @throws IllegalArgumentException If gifData or config is null
     */
    public static byte[] encodeGifToWebP(byte[] gifData, GifToWebPConfig config) throws IOException {
        return GifToWebPConverter.convert(gifData, config);
    }

    /**
     * Converts a GIF image to lossless WebP format.
     * <p>
     * This is a convenience method that uses lossless compression to preserve
     * the original GIF quality without any loss.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the lossless WebP encoded image
     * @throws IOException              If conversion fails
     * @throws IllegalArgumentException If gifData is null or empty
     */
    public static byte[] encodeGifToWebPLossless(byte[] gifData) throws IOException {
        return GifToWebPConverter.convertLossless(gifData);
    }

    // ============================================
    // Animated WebP Creation (Delegate to AnimatedWebPEncoder)
    // ============================================

    /**
     * Creates an animated WebP from a list of BufferedImage frames.
     * <p>
     * This method provides direct conversion from Java BufferedImage objects
     * to animated WebP without requiring an intermediate GIF file.
     * <p>
     * Example usage:
     * <pre>{@code
     * List<BufferedImage> frames = Arrays.asList(frame1, frame2, frame3);
     * int[] delays = {100, 100, 100};  // milliseconds per frame
     * GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
     * byte[] webp = WebPCodec.createAnimatedWebP(frames, delays, config);
     * }</pre>
     *
     * @param frames List of BufferedImage frames (must not be empty)
     * @param delays Array of frame delays in milliseconds (must match frame count)
     * @param config Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty or delays length doesn't match
     */
    public static byte[] createAnimatedWebP(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        return AnimatedWebPEncoder.encode(frames, delays, config);
    }

    // ============================================
    // Animated WebP Decoding (Delegate to AnimatedWebPDecoder)
    // ============================================

    /**
     * Decodes an animated WebP image into individual frames.
     * <p>
     * This method extracts all frames from an animated WebP as BufferedImage
     * objects along with their timestamps and animation metadata.
     * <p>
     * Each frame is a fully composited canvas-sized RGBA image.
     * <p>
     * Example usage:
     * <pre>{@code
     * byte[] webPData = Files.readAllBytes(Paths.get("animation.webp"));
     * AnimatedWebPData result = WebPCodec.decodeAnimatedWebP(webPData);
     *
     * // Access individual frames
     * for (AnimatedWebPFrame frame : result.getFrames()) {
     *     BufferedImage image = frame.getImage();
     *     int timestamp = frame.getTimestamp();
     * }
     *
     * // Get per-frame delays
     * int[] delays = result.getDelays();
     * }</pre>
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimatedWebPData containing all decoded frames and animation metadata
     * @throws IOException              If decoding fails
     * @throws IllegalArgumentException If webPData is null or empty
     */
    public static AnimatedWebPData decodeAnimatedWebP(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.decode(webPData);
    }

}