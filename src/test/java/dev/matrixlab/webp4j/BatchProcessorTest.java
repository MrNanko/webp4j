package dev.matrixlab.webp4j;

import dev.matrixlab.webp4j.batch.BatchProcessor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BatchProcessor} parallel bulk encoding/decoding, including
 * the {@link WebPCodec} facade delegations.
 */
class BatchProcessorTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 80;

    private static final Color[] COLORS = {
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK
    };

    /**
     * Builds a list of distinct solid-color images so that each encoded result
     * is byte-distinct from the others — lets us assert ordering is preserved.
     */
    private static List<BufferedImage> makeImages(int count) {
        List<BufferedImage> images = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(COLORS[i % COLORS.length]);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.dispose();
            images.add(img);
        }
        return images;
    }

    @Test
    void testBatchEncodeMatchesSequentialAndPreservesOrder() throws IOException {
        List<BufferedImage> images = makeImages(8);

        List<byte[]> batch = BatchProcessor.encodeImages(images, 80, false);
        assertEquals(images.size(), batch.size(), "Batch result count must match input count");

        // Each batch result must byte-for-byte equal the sequential single-image
        // encode at the same index — proves both correctness and ordering.
        for (int i = 0; i < images.size(); i++) {
            byte[] expected = WebPCodec.encodeImage(images.get(i), 80, false);
            assertNotNull(batch.get(i), "Encoded entry " + i + " should not be null");
            assertArrayEquals(expected, batch.get(i),
                    "Batch entry " + i + " must equal the sequential encode at the same index");
        }
    }

    @Test
    void testRoundTripThroughBatch() throws IOException {
        List<BufferedImage> images = makeImages(5);

        List<byte[]> encoded = BatchProcessor.encodeImages(images, 90, true); // lossless
        List<BufferedImage> decoded = BatchProcessor.decodeImages(encoded);

        assertEquals(images.size(), decoded.size(), "Decoded count must match");
        for (int i = 0; i < images.size(); i++) {
            BufferedImage out = decoded.get(i);
            assertEquals(WIDTH, out.getWidth(), "Width must survive round-trip at index " + i);
            assertEquals(HEIGHT, out.getHeight(), "Height must survive round-trip at index " + i);
            // Lossless: the dominant color must come back at a sample point.
            int expectedRgb = COLORS[i % COLORS.length].getRGB() & 0x00FFFFFF;
            int actualRgb = out.getRGB(WIDTH / 2, HEIGHT / 2) & 0x00FFFFFF;
            assertEquals(expectedRgb, actualRgb, "Center pixel color must survive lossless round-trip at index " + i);
        }
    }

    @Test
    void testWebPCodecFacadeDelegates() throws IOException {
        List<BufferedImage> images = makeImages(4);

        List<byte[]> viaFacade = WebPCodec.encodeImages(images, 75, false);
        List<BufferedImage> back = WebPCodec.decodeImages(viaFacade);

        assertEquals(images.size(), viaFacade.size());
        assertEquals(images.size(), back.size());
        for (int i = 0; i < images.size(); i++) {
            assertEquals(WIDTH, back.get(i).getWidth());
            assertEquals(HEIGHT, back.get(i).getHeight());
        }
    }

    @Test
    void testNullAndEmptyInputs() throws IOException {
        assertTrue(BatchProcessor.encodeImages(null, 75, false).isEmpty(), "null images -> empty list");
        assertTrue(BatchProcessor.encodeImages(Collections.emptyList(), 75, false).isEmpty(), "empty images -> empty list");
        assertTrue(BatchProcessor.decodeImages(null).isEmpty(), "null data -> empty list");
        assertTrue(BatchProcessor.decodeImages(Collections.emptyList()).isEmpty(), "empty data -> empty list");

        List<BufferedImage> withNull = new ArrayList<>(makeImages(2));
        withNull.add(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BatchProcessor.encodeImages(withNull, 75, false));
        assertTrue(ex.getMessage().contains("index 2"), "Message should name the null index: " + ex.getMessage());
    }

    @Test
    void testCustomExecutorIsUsedAndNotShutDown() throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<BufferedImage> images = makeImages(6);
            List<byte[]> encoded = BatchProcessor.encodeImages(images, 80, false, pool);
            assertEquals(images.size(), encoded.size());
            assertFalse(pool.isShutdown(), "Caller-supplied executor must not be shut down by BatchProcessor");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void testFailFastNamesFailingIndex() throws IOException {
        List<BufferedImage> images = makeImages(1);
        byte[] valid = WebPCodec.encodeImage(images.get(0), 80, false);

        // Index 0 is decodable; index 1 is garbage that WebPCodec.decodeImage rejects.
        List<byte[]> data = Arrays.asList(valid, new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

        IOException ex = assertThrows(IOException.class, () -> BatchProcessor.decodeImages(data));
        assertTrue(ex.getMessage().contains("index 1"),
                "Fail-fast message should name the failing index: " + ex.getMessage());
    }
}