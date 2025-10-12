package dev.matrixlab.webp4j;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class WebP4jTest {

    private static final String TEST_RESOURCES_DIR = "src/test/resources/";

    private static final String FORMAT_PNG = "png";
    
    // Source images for testing
    private static final String SOURCE_RGB_PNG = TEST_RESOURCES_DIR + "test_rgb.png";
    private static final String SOURCE_RGBA_PNG = TEST_RESOURCES_DIR + "test_rgba.png";
    private static final String SOURCE_RGB_WEBP = TEST_RESOURCES_DIR + "test_rgb.webp";
    
    // Generated output files
    private static final String OUTPUT_RGB_WEBP = TEST_RESOURCES_DIR + "encode_rgb.webp";
    private static final String OUTPUT_RGBA_WEBP = TEST_RESOURCES_DIR + "encode_rgba.webp";
    private static final String OUTPUT_LOSSLESS_RGB_WEBP = TEST_RESOURCES_DIR + "encode_lossless_rgb.webp";
    private static final String OUTPUT_LOSSLESS_RGBA_WEBP = TEST_RESOURCES_DIR + "encode_lossless_rgba.webp";
    
    // Decoded output files
    private static final String OUTPUT_DECODED_RGB_PNG = TEST_RESOURCES_DIR + "decode_rgb.png";
    private static final String OUTPUT_DECODED_RGBA_PNG = TEST_RESOURCES_DIR + "decoded_rgba.png";
    private static final String OUTPUT_DECODED_LOSSLESS_RGB_JPG = TEST_RESOURCES_DIR + "decode_lossless_rgb.jpg";
    private static final String OUTPUT_DECODED_LOSSLESS_RGBA_PNG = TEST_RESOURCES_DIR + "decode_lossless_rgba.png";

    @Test
    void testGetWebPInfo() {
        try {
            // Load WebP image file
            byte[] webPData = Files.readAllBytes(Paths.get(SOURCE_RGB_WEBP));

            // Retrieve WebP image information
            int[] dimensions = WebPCodec.getWebPInfo(webPData);

            // Validate the retrieved image dimensions
            assertEquals(2248, dimensions[0], "Width does not match expected value.");
            assertEquals(1442, dimensions[1], "Height does not match expected value.");

        } catch (IOException e) {
            fail("Exception thrown during WebP info retrieval: " + e.getMessage());
        }
    }

    @Test
    void testEncodeRGB() {
        try {
            // Load RGB image file
            BufferedImage bufferedImage = ImageIO.read(new File(SOURCE_RGB_PNG));
            assertNotNull(bufferedImage, "Failed to load test image.");

            // Encode image to WebP
            float quality = 75.0f;
            byte[] encodedWebP = WebPCodec.encodeImage(bufferedImage, quality);

            // Validate the encoded result
            assertNotNull(encodedWebP, "WebP encoding failed for RGB.");
            assertTrue(encodedWebP.length > 0, "Encoded WebP file is empty.");

            // Save the encoded file for manual inspection
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_RGB_WEBP)) {
                fos.write(encodedWebP);
            }

        } catch (IOException e) {
            fail("Exception thrown during WebP encoding: " + e.getMessage());
        }
    }

    @Test
    void testEncodeRGBA() {
        try {
            // Load RGBA image file
            BufferedImage bufferedImage = ImageIO.read(new File(SOURCE_RGBA_PNG));
            assertNotNull(bufferedImage, "Failed to load test image.");

            // Encode image to WebP
            float quality = 75.0f;
            byte[] encodedWebP = WebPCodec.encodeImage(bufferedImage, quality);

            // Validate the encoded result
            assertNotNull(encodedWebP, "WebP encoding failed for RGBA.");
            assertTrue(encodedWebP.length > 0, "Encoded WebP file is empty.");

            // Save the encoded file for manual inspection
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_RGBA_WEBP)) {
                fos.write(encodedWebP);
            }

        } catch (IOException e) {
            fail("Exception thrown during WebP encoding: " + e.getMessage());
        }
    }

    /**
     * Test method for decoding an RGB WebP image.
     */
    @Test
    void testDecodeRGBInto() throws IOException {
        // Load WebP image file to decode
        byte[] webPData = Files.readAllBytes(Paths.get(OUTPUT_RGB_WEBP));
        assertNotNull(webPData, "WebP data should not be null.");
        assertTrue(webPData.length > 0, "WebP data should not be empty.");

        // Create BufferedImage from decoded RGB data
        BufferedImage image = WebPCodec.decodeImage(webPData);
        assertNotNull(image, "Decoded RGB image should not be null.");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "Decoded RGB image dimensions must be positive.");
        assertFalse(image.getColorModel().hasAlpha(), "Decoded RGB image should not have alpha.");

        // Save decoded image to a file
        ImageIO.write(image, FORMAT_PNG, new File(OUTPUT_DECODED_RGB_PNG));
    }

    /**
     * Test method for decoding an RGBA WebP image.
     */
    @Test
    void testDecodeRGBAInto() throws IOException {
        // Load WebP image file to decode
        byte[] webPData = Files.readAllBytes(Paths.get(OUTPUT_RGBA_WEBP));
        assertNotNull(webPData, "WebP data should not be null.");
        assertTrue(webPData.length > 0, "WebP data should not be empty.");

        // Create BufferedImage from decoded RGBA data
        BufferedImage image = WebPCodec.decodeImage(webPData);
        assertNotNull(image, "Decoded RGBA image should not be null.");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "Decoded RGBA image dimensions must be positive.");
        assertTrue(image.getColorModel().hasAlpha(), "Decoded RGBA image should have alpha.");

        // Save decoded image to a file
        ImageIO.write(image, FORMAT_PNG, new File(OUTPUT_DECODED_RGBA_PNG));
    }

    @Test
    void testEncodeLosslessRGB() {
        try {
            // Load RGB image file
            BufferedImage bufferedImage = ImageIO.read(new File(SOURCE_RGB_PNG));
            assertNotNull(bufferedImage, "Failed to load test image.");

            // Encode image to lossless WebP
            byte[] encodedWebP = WebPCodec.encodeLosslessImage(bufferedImage);

            // Validate the encoded result
            assertNotNull(encodedWebP, "Lossless WebP encoding failed for RGB.");
            assertTrue(encodedWebP.length > 0, "Encoded lossless WebP file is empty.");

            // Save the encoded file for manual inspection
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_LOSSLESS_RGB_WEBP)) {
                fos.write(encodedWebP);
            }

        } catch (IOException e) {
            fail("Exception thrown during lossless WebP encoding: " + e.getMessage());
        }
    }

    @Test
    void testEncodeLosslessRGBA() {
        try {
            // Load RGBA image file
            BufferedImage bufferedImage = ImageIO.read(new File(SOURCE_RGBA_PNG));
            assertNotNull(bufferedImage, "Failed to load test image.");

            // Encode image to lossless WebP
            byte[] encodedWebP = WebPCodec.encodeLosslessImage(bufferedImage);

            // Validate the encoded result
            assertNotNull(encodedWebP, "Lossless WebP encoding failed for RGBA.");
            assertTrue(encodedWebP.length > 0, "Encoded lossless WebP file is empty.");

            // Save the encoded file for manual inspection
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_LOSSLESS_RGBA_WEBP)) {
                fos.write(encodedWebP);
            }

        } catch (IOException e) {
            fail("Exception thrown during lossless WebP encoding: " + e.getMessage());
        }
    }

    @Test
    void testDecodeLosslessRGB() throws IOException {
        // Load lossless WebP image file to decode
        byte[] webPData = Files.readAllBytes(Paths.get(OUTPUT_LOSSLESS_RGB_WEBP));
        assertNotNull(webPData, "Lossless WebP data should not be null.");
        assertTrue(webPData.length > 0, "Lossless WebP data should not be empty.");

        // Create BufferedImage from decoded RGB data
        BufferedImage image = WebPCodec.decodeImage(webPData);
        assertNotNull(image, "Decoded lossless RGB image should not be null.");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "Decoded lossless RGB image dimensions must be positive.");
        assertFalse(image.getColorModel().hasAlpha(), "Decoded lossless RGB image should not have alpha.");

        // Save decoded image to a file
        ImageIO.write(image, FORMAT_PNG, new File(OUTPUT_DECODED_LOSSLESS_RGB_JPG));
    }

    @Test
    void testDecodeLosslessRGBA() throws IOException {
        // Load lossless WebP image file to decode
        byte[] webPData = Files.readAllBytes(Paths.get(OUTPUT_LOSSLESS_RGBA_WEBP));
        assertNotNull(webPData, "Lossless WebP data should not be null.");
        assertTrue(webPData.length > 0, "Lossless WebP data should not be empty.");

        // Create BufferedImage from decoded RGBA data
        BufferedImage image = WebPCodec.decodeImage(webPData);
        assertNotNull(image, "Decoded lossless RGBA image should not be null.");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "Decoded lossless RGBA image dimensions must be positive.");
        assertTrue(image.getColorModel().hasAlpha(), "Decoded lossless RGBA image should have alpha.");

        // Save decoded image to a file
        ImageIO.write(image, FORMAT_PNG, new File(OUTPUT_DECODED_LOSSLESS_RGBA_PNG));
    }

    @Test
    void testWebPBitstreamFeatures() throws IOException {
        // Load WebP file for feature analysis
        byte[] webPData = Files.readAllBytes(Paths.get(OUTPUT_LOSSLESS_RGB_WEBP));
        assertNotNull(webPData, "WebP data should not be null");
        assertTrue(webPData.length > 0, "WebP data should not be empty");

        // Create features container
        WebPBitstreamFeatures features = new WebPBitstreamFeatures();

        // Extract bitstream features
        int status = NativeWebP.getFeatures(webPData, webPData.length, features);
        VP8StatusCode statusCode = VP8StatusCode.getStatusCode(status);
        
        // Validate feature extraction succeeded
        assertEquals(VP8StatusCode.VP8_STATUS_OK, statusCode, "Failed to get WebP bitstream features");
        
        // Validate image dimensions
        assertTrue(features.getWidth() > 0, "Image width should be positive, actual: " + features.getWidth());
        assertTrue(features.getHeight() > 0, "Image height should be positive, actual: " + features.getHeight());
        
        // Validate compression format
        assertTrue(features.getFormat() >= 0 && features.getFormat() <= 2,
                  "Format should be 0 (undefined/mixed), 1 (lossy), or 2 (lossless), actual: " + features.getFormat());
        
        // Validate specific properties for lossless RGB image
        // Format should be 0 (undefined/mixed), 1 (lossy), or 2 (lossless)
        assertEquals(2, features.getFormat(), "Expected lossless compression format");
        assertFalse(features.isHasAlpha(), "RGB image should not have alpha channel");
        assertFalse(features.isHasAnimation(), "Static image should not have animation");
    }
}