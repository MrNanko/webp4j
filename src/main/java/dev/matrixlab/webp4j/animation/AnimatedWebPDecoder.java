package dev.matrixlab.webp4j.animation;

import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.internal.PixelConverter;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.AnimatedWebPFrame;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for extracting individual frames from animated WebP images.
 * <p>
 * This class uses the libwebp WebPAnimDecoder API to decode animated WebP
 * images into individual frames as BufferedImage objects with their timestamps.
 * <p>
 * Example usage:
 * <pre>{@code
 * byte[] webPData = Files.readAllBytes(Paths.get("animation.webp"));
 * AnimatedWebPData result = AnimatedWebPDecoder.decode(webPData);
 * for (AnimatedWebPFrame frame : result.getFrames()) {
 *     BufferedImage image = frame.getImage();
 *     int timestamp = frame.getTimestamp();
 * }
 * int[] delays = result.getDelays(); // per-frame delays in ms
 * }</pre>
 *
 * @author MrNanko
 */
public final class AnimatedWebPDecoder {

    private AnimatedWebPDecoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Decodes an animated WebP image into individual frames.
     * <p>
     * Each frame is a fully composited canvas-sized RGBA BufferedImage.
     * The returned {@link AnimatedWebPData} contains all frames with their
     * timestamps, as well as animation metadata (canvas size, loop count, etc.).
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimatedWebPData containing all decoded frames and metadata
     * @throws IOException              If decoding fails
     * @throws IllegalArgumentException If webPData is null or empty
     */
    public static AnimatedWebPData decode(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        // Create result container for native layer to populate
        AnimatedWebPData result = new AnimatedWebPData();

        // Decode using native WebPAnimDecoder
        boolean success = NativeWebP.decodeAnimated(webPData, result);
        if (!success) {
            throw new IOException("Failed to decode animated WebP image.");
        }

        int[][] framePixels = result.getFramePixels();
        int[] timestamps = result.getTimestamps();

        if (framePixels == null || timestamps == null) {
            throw new IOException("Native decoder returned incomplete data.");
        }

        int canvasWidth = result.getCanvasWidth();
        int canvasHeight = result.getCanvasHeight();

        // Each BufferedImage wraps its frame's pixel array directly — no
        // second per-frame buffer ever exists.
        List<AnimatedWebPFrame> frames = new ArrayList<>(framePixels.length);
        for (int i = 0; i < framePixels.length; i++) {
            BufferedImage image = PixelConverter.wrapPixels(framePixels[i], canvasWidth, canvasHeight, true);
            frames.add(new AnimatedWebPFrame(image, timestamps[i]));
        }

        // Drop the redundant references; the images now own the pixel arrays.
        result.setFramePixels(null);
        result.setTimestamps(null);

        result.setFrames(frames);
        return result;
    }
}