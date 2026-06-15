package dev.matrixlab.webp4j;

import dev.matrixlab.webp4j.gif.GifToWebPConfig;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.AnimatedWebPFrame;
import org.junit.jupiter.api.Test;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the zero-copy pixel pipeline: every BufferedImage layout must
 * survive a lossless encode/decode roundtrip, raster fast-path guards must
 * reject shared/translated buffers (subimages), and encoding must never
 * mutate the caller's image.
 */
class PixelPathTest {

    private static final int W = 64;
    private static final int H = 48;

    /** Fills an image with a deterministic gradient, plus alpha variation when supported. */
    private static BufferedImage createTestImage(int type, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, type);
        boolean hasAlpha = image.getColorModel().hasAlpha();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / (width - 1);
                int g = (y * 255) / (height - 1);
                int b = ((x + y) * 255) / (width + height - 2);
                int a = hasAlpha ? 255 - ((x * 200) / (width - 1)) : 255;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /** Normalizes any image to TYPE_INT_ARGB/TYPE_INT_RGB with a Src blit (the reference result). */
    private static BufferedImage normalize(BufferedImage src, boolean hasAlpha) {
        BufferedImage normalized = new BufferedImage(src.getWidth(), src.getHeight(),
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = normalized.createGraphics();
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return normalized;
    }

    private static void assertPixelsEqual(BufferedImage expected, BufferedImage actual, String context) {
        assertEquals(expected.getWidth(), actual.getWidth(), context + ": width");
        assertEquals(expected.getHeight(), actual.getHeight(), context + ": height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int e = expected.getRGB(x, y);
                int a = actual.getRGB(x, y);
                if (e != a) {
                    fail(String.format("%s: pixel (%d,%d) expected 0x%08X but was 0x%08X",
                            context, x, y, e, a));
                }
            }
        }
    }

    private static void assertLosslessRoundtrip(BufferedImage src, String context) throws IOException {
        boolean hasAlpha = src.getColorModel().hasAlpha();
        byte[] webp = WebPCodec.encodeLosslessImage(src);
        assertNotNull(webp, context + ": encoded data should not be null");
        assertTrue(webp.length > 0, context + ": encoded data should not be empty");

        BufferedImage decoded = WebPCodec.decodeImage(webp);
        assertEquals(hasAlpha, decoded.getColorModel().hasAlpha(), context + ": alpha presence");
        assertPixelsEqual(normalize(src, hasAlpha), decoded, context);
    }

    @Test
    void testLosslessRoundtripIntArgb() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_INT_ARGB, W, H), "TYPE_INT_ARGB");
    }

    @Test
    void testLosslessRoundtripIntRgb() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_INT_RGB, W, H), "TYPE_INT_RGB");
    }

    @Test
    void testLosslessRoundtripIntArgbPre() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_INT_ARGB_PRE, W, H), "TYPE_INT_ARGB_PRE");
    }

    @Test
    void testLosslessRoundtripIntBgr() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_INT_BGR, W, H), "TYPE_INT_BGR");
    }

    @Test
    void testLosslessRoundtrip3ByteBgr() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_3BYTE_BGR, W, H), "TYPE_3BYTE_BGR");
    }

    @Test
    void testLosslessRoundtrip4ByteAbgr() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_4BYTE_ABGR, W, H), "TYPE_4BYTE_ABGR");
    }

    @Test
    void testLosslessRoundtripByteGray() throws IOException {
        assertLosslessRoundtrip(createTestImage(BufferedImage.TYPE_BYTE_GRAY, W, H), "TYPE_BYTE_GRAY");
    }

    /**
     * getSubimage() shares the parent's DataBuffer with a translated raster.
     * The raster fast-path guards must reject it and fall back to a normalize
     * copy — encoding the subimage must yield the subimage's pixels, not a
     * window into the parent's array.
     */
    @Test
    void testSubimageEncodesItsOwnPixels() throws IOException {
        BufferedImage parent = createTestImage(BufferedImage.TYPE_INT_ARGB, 128, 96);
        BufferedImage sub = parent.getSubimage(32, 16, W, H);

        byte[] webp = WebPCodec.encodeLosslessImage(sub);
        BufferedImage decoded = WebPCodec.decodeImage(webp);

        assertPixelsEqual(normalize(sub, true), decoded, "subimage");
    }

    /**
     * Zero-copy encoding pins the image's live backing array — it must be
     * treated as strictly read-only. A regression that writes to or clears
     * the array would corrupt the caller's image.
     */
    @Test
    void testEncodeDoesNotMutateSourceImage() throws IOException {
        BufferedImage image = createTestImage(BufferedImage.TYPE_INT_ARGB, W, H);
        int[] backing = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int[] snapshot = backing.clone();

        WebPCodec.encodeImage(image, 75.0f);
        WebPCodec.encodeLosslessImage(image);

        assertArrayEquals(snapshot, backing, "Encoding must not modify the source image's pixels");
    }

    /** Same read-only guarantee for the TYPE_3BYTE_BGR direct-import path. */
    @Test
    void testEncodeDoesNotMutateBgrSourceImage() throws IOException {
        BufferedImage image = createTestImage(BufferedImage.TYPE_3BYTE_BGR, W, H);
        byte[] backing = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        byte[] snapshot = backing.clone();

        WebPCodec.encodeImage(image, 75.0f);
        WebPCodec.encodeLosslessImage(image);

        assertArrayEquals(snapshot, backing, "Encoding must not modify the source image's pixels");
    }

    /**
     * Decoded images wrap the decode buffer directly; they must still report
     * the standard types and be mutable like any ordinary BufferedImage.
     */
    @Test
    void testDecodedImageTypeAndMutability() throws IOException {
        byte[] argbWebP = WebPCodec.encodeLosslessImage(createTestImage(BufferedImage.TYPE_INT_ARGB, W, H));
        BufferedImage argb = WebPCodec.decodeImage(argbWebP);
        assertEquals(BufferedImage.TYPE_INT_ARGB, argb.getType(), "Alpha image should decode as TYPE_INT_ARGB");

        byte[] rgbWebP = WebPCodec.encodeLosslessImage(createTestImage(BufferedImage.TYPE_INT_RGB, W, H));
        BufferedImage rgb = WebPCodec.decodeImage(rgbWebP);
        assertEquals(BufferedImage.TYPE_INT_RGB, rgb.getType(), "Opaque image should decode as TYPE_INT_RGB");

        argb.setRGB(0, 0, 0x12345678);
        assertEquals(0x12345678, argb.getRGB(0, 0), "Decoded image must be mutable");
        rgb.setRGB(0, 0, 0xFF345678);
        assertEquals(0xFF345678, rgb.getRGB(0, 0), "Decoded image must be mutable");
    }

    /**
     * Lossless animated roundtrip with per-pixel verification. Includes one
     * frame without an alpha channel: the encoder must normalize it to ARGB
     * instead of rejecting the whole animation (regression test — the old
     * byte[]-based pipeline produced 3-byte pixels for opaque frames, which
     * the native frame-size check refused).
     */
    @Test
    void testAnimatedRoundtripWithOpaqueFrame() throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        frames.add(createTestImage(BufferedImage.TYPE_INT_ARGB, W, H));
        frames.add(createTestImage(BufferedImage.TYPE_INT_RGB, W, H));  // opaque frame
        BufferedImage solid = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = solid.createGraphics();
        g2d.setColor(Color.ORANGE);
        g2d.fillRect(0, 0, W, H);
        g2d.dispose();
        frames.add(solid);
        int[] delays = {100, 100, 100};

        byte[] webp = WebPCodec.createAnimatedWebP(frames, delays, GifToWebPConfig.createLosslessConfig());
        assertNotNull(webp, "Animated WebP data should not be null");
        assertTrue(webp.length > 0, "Animated WebP data should not be empty");

        AnimatedWebPData decoded = WebPCodec.decodeAnimatedWebP(webp);
        List<AnimatedWebPFrame> decodedFrames = decoded.getFrames();
        assertEquals(frames.size(), decodedFrames.size(), "Frame count should match");

        for (int i = 0; i < frames.size(); i++) {
            assertPixelsEqual(normalize(frames.get(i), true), decodedFrames.get(i).getImage(), "frame " + i);
        }
        assertArrayEquals(delays, decoded.getDelays(), "Delays should roundtrip");
    }

    /**
     * Large-image smoke test: exercises the critical-section paths under
     * allocation pressure (encode import + full-decode pin).
     */
    @Test
    void testLargeImageRoundtrip() throws IOException {
        int size = 4096;
        BufferedImage large = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) large.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i * 31;
        }

        byte[] webp = WebPCodec.encodeImage(large, 75.0f);
        assertNotNull(webp, "Large image encoding should succeed");
        assertTrue(webp.length > 0, "Encoded data should not be empty");

        BufferedImage decoded = WebPCodec.decodeImage(webp);
        assertEquals(size, decoded.getWidth(), "Decoded width");
        assertEquals(size, decoded.getHeight(), "Decoded height");
    }
}