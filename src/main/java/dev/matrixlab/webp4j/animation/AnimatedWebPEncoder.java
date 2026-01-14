package dev.matrixlab.webp4j.animation;

import dev.matrixlab.webp4j.gif.GifToWebPConfig;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.internal.PixelConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for creating animated WebP images from BufferedImage frames.
 * <p>
 * This class provides methods to create animated WebP images directly from
 * Java BufferedImage objects without requiring an intermediate GIF file.
 *
 * @author MrNanko
 */
public final class AnimatedWebPEncoder {

    private AnimatedWebPEncoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Creates an animated WebP from a list of BufferedImage frames.
     * <p>
     * Example usage:
     * <pre>{@code
     * List<BufferedImage> frames = Arrays.asList(frame1, frame2, frame3);
     * int[] delays = {100, 100, 100};  // milliseconds per frame
     * GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
     * byte[] webp = AnimatedWebPEncoder.encode(frames, delays, config);
     * }</pre>
     *
     * @param frames List of BufferedImage frames (must not be empty)
     * @param delays Array of frame delays in milliseconds (must match frame count)
     * @param config Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty or delays length doesn't match
     */
    public static byte[] encode(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (delays == null || delays.length != frames.size()) {
            throw new IllegalArgumentException("Delays array length must match frame count");
        }
        if (config == null) {
            config = new GifToWebPConfig();
        }

        // Get canvas dimensions from first frame
        BufferedImage firstFrame = frames.get(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();

        // Convert all BufferedImage frames to RGBA byte arrays
        byte[][] frameData = new byte[frames.size()][];

        try {
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);

                // Validate frame dimensions match canvas
                if (frame.getWidth() != width || frame.getHeight() != height) {
                    throw new IllegalArgumentException(
                            String.format("Frame %d dimensions (%dx%d) don't match canvas (%dx%d)",
                                    i, frame.getWidth(), frame.getHeight(), width, height));
                }

                frameData[i] = PixelConverter.toBytes(frame);
            }

            // Encode using native WebPAnimEncoder
            byte[] result = NativeWebP.encodeAnimatedWebP(
                    frameData,
                    delays,
                    width,
                    height,
                    config.getQuality(),
                    config.isLossless(),
                    config.getCompressionMethod(),
                    config.getLoopCount() == -1 ? 0 : config.getLoopCount(),  // Default to infinite loop
                    config.getKmin(),
                    config.getKmax(),
                    config.isMinimizeSize(),
                    config.isAllowMixed()
            );

            if (result == null || result.length == 0) {
                throw new IOException("Animated WebP encoding failed");
            }

            return result;

        } finally {
            // Clear frame data arrays to free memory
            for (byte[] frame : frameData) {
                if (frame != null) {
                    Arrays.fill(frame, (byte) 0);
                }
            }
        }
    }

    /**
     * Creates an animated WebP with uniform delay for all frames.
     * <p>
     * This is a convenience method when all frames have the same delay.
     *
     * @param frames  List of BufferedImage frames (must not be empty)
     * @param delay   Delay in milliseconds for each frame
     * @param config  Configuration for encoding (quality, compression, etc.)
     * @return Byte array containing the animated WebP data
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If frames is empty
     */
    public static byte[] encode(List<BufferedImage> frames, int delay, GifToWebPConfig config) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }

        int[] delays = new int[frames.size()];
        Arrays.fill(delays, delay);

        return encode(frames, delays, config);
    }
}
