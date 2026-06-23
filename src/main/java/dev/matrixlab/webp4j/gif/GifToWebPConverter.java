package dev.matrixlab.webp4j.gif;

import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.internal.PixelConverter;
import dev.matrixlab.webp4j.model.AnimationInfo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Converter for transforming GIF images to WebP format.
 * <p>
 * This class provides methods to convert both static and animated GIF images to WebP format.
 * It supports a dual-path approach:
 * <ul>
 *   <li>Primary: Native giflib decoder (fast, complete GIF support)</li>
 *   <li>Fallback: Java ImageIO decoder (slower, but works when native library unavailable)</li>
 * </ul>
 *
 * @author MrNanko
 */
public final class GifToWebPConverter {

    private GifToWebPConverter() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

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
    public static AnimationInfo getInfo(byte[] gifData) throws IOException {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }

        AnimationInfo info = new AnimationInfo();

        // Try native path first
        try {
            boolean success = NativeWebP.getGifInfo(gifData, info);
            if (success) {
                return info;
            }
        } catch (UnsatisfiedLinkError e) {
            // Native library not available, fall back to Java ImageIO
        }

        // If native path returned false or library unavailable, use Java ImageIO fallback
        boolean success = GifDecoderJava.getGifInfo(gifData, info);
        if (!success) {
            throw new IOException("Failed to read GIF information");
        }

        return info;
    }

    /**
     * Converts a GIF image to WebP format with default lossy settings.
     * <p>
     * This is a convenience method that uses quality factor 75 and lossy compression.
     * For more control over the conversion, use {@link #convert(byte[], GifToWebPConfig)}.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convert(byte[] gifData) throws IOException {
        return convert(gifData, new GifToWebPConfig());
    }

    /**
     * Converts a GIF image to WebP format.
     * <p>
     * This method automatically detects whether the GIF is static or animated,
     * and handles both cases appropriately.
     *
     * @param gifData Byte array containing the GIF image data
     * @param config  Configuration for conversion (quality, lossless, compression, etc.)
     * @return Byte array containing the WebP encoded image
     * @throws IOException              If conversion fails
     * @throws IllegalArgumentException If gifData or config is null
     */
    public static byte[] convert(byte[] gifData, GifToWebPConfig config) throws IOException {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        // Try native path first (giflib + JNI) for best performance
        byte[] result = null;
        try {
            result = NativeWebP.encodeGifToWebP(
                    gifData,
                    config.getQuality(),
                    config.isLossless(),
                    config.getCompressionMethod(),
                    config.isExtractFirstFrameOnly(),
                    config.getLoopCount() == -1 ? ((Supplier<Integer>) () -> {
                        try {
                            return getInfo(gifData).getLoopCount();
                        } catch (Exception e) {
                            return 0; // Default to infinite loop if unable to read GIF info
                        }
                    }).get() : config.getLoopCount(),
                    config.getKmin(),
                    config.getKmax(),
                    config.isMinimizeSize(),
                    config.isAllowMixed(),
                    config.isMultiThreaded()
            );

            if (result != null && result.length > 0) {
                return result;  // Success via native path
            }
        } catch (UnsatisfiedLinkError e) {
            // Native library not available, fall back to Java ImageIO
        }

        // If native path returned null or failed to load, try Java ImageIO fallback
        if (result == null) {
            return convertUsingJavaImageIO(gifData, config);
        }

        throw new IOException("Native GIF encoding returned empty result");
    }

    /**
     * Converts a GIF image to lossless WebP format.
     * <p>
     * This is a convenience method that uses lossless compression to preserve
     * the original GIF quality without any loss.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the lossless WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convertLossless(byte[] gifData) throws IOException {
        return convert(gifData, GifToWebPConfig.createLosslessConfig());
    }

    /**
     * Fallback implementation: Encodes GIF to WebP using Java ImageIO for GIF decoding.
     * <p>
     * This method is used when native giflib is unavailable. It decodes the GIF
     * using Java's ImageIO, then encodes to WebP using native WebPAnimEncoder.
     * <p>
     * Package-private for testing purposes.
     *
     * @param gifData Byte array containing the GIF image data
     * @param config  Configuration for conversion
     * @return Byte array containing the WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convertUsingJavaImageIO(byte[] gifData, GifToWebPConfig config) throws IOException {
        // Decode GIF using Java ImageIO
        GifDecoderJava.GifData gif = GifDecoderJava.decodeGif(gifData);

        if (config.isExtractFirstFrameOnly() || gif.frames.size() == 1) {
            // Static GIF or extract first frame only: use existing single-frame encoding
            BufferedImage firstFrame = gif.frames.get(0).image;
            return encodeSingleFrame(firstFrame, config.getQuality(), config.isLossless(),
                    config.isMultiThreaded());
        }

        // Animated GIF: encode all frames using native WebPAnimEncoder
        int[][] framePixels = new int[gif.frames.size()][];
        int[] delays = new int[gif.frames.size()];

        // Maintain a canvas for frame composition (GIF frames may be incremental)
        BufferedImage canvas = new BufferedImage(gif.width, gif.height, BufferedImage.TYPE_INT_ARGB);
        int[] canvasPixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        BufferedImage previousFrame = null;

        for (int i = 0; i < gif.frames.size(); i++) {
            previousFrame = composeFrame(gif, i, canvas, previousFrame);
            // The canvas is reused for the next frame, so its pixels must be cloned
            // here — one copy per frame, with no format conversion.
            framePixels[i] = canvasPixels.clone();
            delays[i] = gif.frames.get(i).delayMs;
        }

        // Use native WebPAnimEncoder for animated output
        byte[] result = NativeWebP.encodeAnimated(
                framePixels,
                delays,
                gif.width,
                gif.height,
                config.getQuality(),
                config.isLossless(),
                config.getCompressionMethod(),
                config.getLoopCount() == -1 ? gif.loopCount : config.getLoopCount(),
                config.getKmin(),
                config.getKmax(),
                config.isMinimizeSize(),
                config.isAllowMixed(),
                config.isMultiThreaded()
        );

        if (result == null || result.length == 0) {
            throw new IOException("Animated WebP encoding failed");
        }

        return result;
    }

    /**
     * Encodes a single BufferedImage frame to WebP.
     */
    private static byte[] encodeSingleFrame(BufferedImage image, float quality, boolean lossless,
                                            boolean multiThreaded) throws IOException {
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int[] pixels = PixelConverter.toArgbPixels(image, hasAlpha);

        byte[] result = NativeWebP.encode(pixels, image.getWidth(), image.getHeight(), quality, lossless,
                hasAlpha, multiThreaded);
        if (result == null || result.length == 0) {
            throw new IOException("WebP encoding failed.");
        }

        return result;
    }

    /**
     * Composes a single GIF frame onto a canvas, applying disposal methods and frame composition.
     * <p>
     * This method handles the GIF animation frame composition logic:
     * <ul>
     *   <li>Applies the previous frame's disposal method (clear or restore)</li>
     *   <li>Saves the canvas state if needed for future restoration (disposal method 3)</li>
     *   <li>Draws the current frame onto the canvas at its offset position</li>
     * </ul>
     *
     * @param gif           GIF data containing all frames
     * @param frameIndex    Index of the current frame to compose
     * @param canvas        The canvas to draw onto (will be modified)
     * @param previousFrame The saved previous frame state (for disposal method 3), may be null
     * @return The updated previousFrame (may be newly created or remain the same)
     */
    private static BufferedImage composeFrame(GifDecoderJava.GifData gif, int frameIndex,
                                              BufferedImage canvas, BufferedImage previousFrame) {
        GifDecoderJava.GifFrame frame = gif.frames.get(frameIndex);

        // Apply disposal method from the PREVIOUS frame (before drawing current frame)
        if (frameIndex > 0 && gif.frames.get(frameIndex - 1).disposeMethod == 2) {
            // Disposal method 2: Restore to background (clear to transparent)
            Graphics2D g2d = canvas.createGraphics();
            try {
                g2d.setComposite(AlphaComposite.Clear);
                GifDecoderJava.GifFrame prevFrame = gif.frames.get(frameIndex - 1);
                g2d.fillRect(
                        prevFrame.leftOffset,
                        prevFrame.topOffset,
                        prevFrame.image.getWidth(),
                        prevFrame.image.getHeight()
                );
            } finally {
                g2d.dispose();
            }
        } else if (frameIndex > 0 && gif.frames.get(frameIndex - 1).disposeMethod == 3 && previousFrame != null) {
            // Disposal method 3: Restore to previous frame
            Graphics2D g2d = canvas.createGraphics();
            try {
                g2d.setComposite(AlphaComposite.Src);
                g2d.drawImage(previousFrame, 0, 0, null);
            } finally {
                g2d.dispose();
            }
        }

        // Save current canvas state if next frame needs it (disposal method 3)
        BufferedImage updatedPreviousFrame = previousFrame;
        if (frameIndex < gif.frames.size() - 1 && frame.disposeMethod == 3) {
            updatedPreviousFrame = new BufferedImage(gif.width, gif.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = updatedPreviousFrame.createGraphics();
            try {
                g2d.drawImage(canvas, 0, 0, null);
            } finally {
                g2d.dispose();
            }
        }

        // Draw current frame onto canvas at its offset position
        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.drawImage(frame.image, frame.leftOffset, frame.topOffset, null);
        } finally {
            g2d.dispose();
        }

        return updatedPreviousFrame;
    }
}
