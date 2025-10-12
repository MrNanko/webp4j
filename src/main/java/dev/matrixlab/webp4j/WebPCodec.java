package dev.matrixlab.webp4j;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Arrays;

public final class WebPCodec {

    private WebPCodec() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

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
     * @throws IOException If an error occurs during image conversion or encoding.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless) throws IOException {
        if (bufferedImage == null) {
            throw new IllegalArgumentException("The input BufferedImage cannot be null.");
        }

        // Get the image width and height.
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // Convert the BufferedImage to an RGB/RGBA byte array.
        byte[] imageBytes = WebPCodec.convertBufferedImageToBytes(bufferedImage);
        if (imageBytes.length == 0) {
            throw new IOException("Failed to convert BufferedImage to a byte array.");
        }

        // Release image resources as soon as they are no longer needed.
        bufferedImage.flush();

        boolean hasAlpha = bufferedImage.getColorModel().hasAlpha();

        // Calculate the stride (number of bytes per row), each pixel is represented by 3 bytes (RGB) / 4 bytes (RGBA).
        int stride = width * (hasAlpha ? 4 : 3);

        // Encode the RGB/RGBA data to WebP format using nativeWebP.
        try {
            byte[] encodedWebP = encodeWithNativeLibrary(imageBytes, width, height, stride, quality, lossless, hasAlpha);

            if (encodedWebP == null || encodedWebP.length == 0) {
                String encodingType = lossless ? "Lossless" : "Lossy";
                throw new IOException(encodingType + " WebP encoding failed.");
            }

            return encodedWebP;
        } finally {
            // Clear the contents of the imageBytes and remove its reference to allow garbage collection.
            Arrays.fill(imageBytes, (byte) 0);
        }
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a lossy WebP encoded byte array.
     * This is a convenience method that calls encodeImage(bufferedImage, quality, false).
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @param quality       The WebP quality parameter (0-100).
     * @return A byte array containing the lossy WebP encoded data.
     * @throws IOException If an error occurs during image conversion or encoding.
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
     * @throws IOException If an error occurs during image conversion or encoding.
     */
    public static byte[] encodeLosslessImage(BufferedImage bufferedImage) throws IOException {
        return encodeImage(bufferedImage, 0, true);
    }

    /**
     * Decodes a WebP image (stored as a byte array) into an RGB/RGBA BufferedImage.
     *
     * @param webPData The byte array containing the WebP encoded image.
     * @return A BufferedImage representing the decoded RGB/RGBA image.
     * @throws IOException If an error occurs during retrieval of image info or decoding.
     */
    public static BufferedImage decodeImage(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        // Retrieve image dimensions from the WebP data.
        int[] dimensions = WebPCodec.getWebPInfo(webPData);

        int width = dimensions[0];
        int height = dimensions[1];

        WebPBitstreamFeatures features = new WebPBitstreamFeatures();

        int status = NativeWebP.getFeatures(webPData, webPData.length, features);
        VP8StatusCode code = VP8StatusCode.getStatusCode(status);
        if (code != VP8StatusCode.VP8_STATUS_OK) {
            throw new IOException("Failed to get WebP bitstream features, error code: " + code);
        }

        boolean hasAlpha = features.isHasAlpha();

        // Calculate the stride for RGB (3 bytes per pixel) / RGBA (4 bytes per pixel).
        int outputStride = width * (hasAlpha ? 4 : 3);

        // Allocate a buffer for the decoded RGB/RGBA image data.
        byte[] outputBuffer = new byte[height * outputStride];

        try {
            // Decode the WebP data into the provided RGB/RGBA buffer.
            boolean success = hasAlpha
                    ? NativeWebP.decodeRGBAInto(webPData, outputBuffer, outputStride)
                    : NativeWebP.decodeRGBInto(webPData, outputBuffer, outputStride);
            if (!success) {
                throw new IOException("Failed to decode WebP data into RGB buffer.");
            }

            // Convert the decoded RGB/RGBA byte array into a BufferedImage.
            return WebPCodec.convertBytesToBufferedImage(width, height, outputBuffer);
        } finally {
            // Clear the contents of the outputBuffer to remove sensitive data.
            Arrays.fill(outputBuffer, (byte) 0);
        }
    }

    /**
     * Handles the native library encoding calls based on encoding type and alpha channel.
     *
     * @param imageBytes The image byte data
     * @param width      Image width
     * @param height     Image height
     * @param stride     Bytes per row
     * @param quality    Quality parameter (ignored for lossless)
     * @param lossless   True for lossless, false for lossy
     * @param hasAlpha   True if image has an alpha channel
     * @return Encoded WebP byte array
     */
    private static byte[] encodeWithNativeLibrary(byte[] imageBytes, int width, int height, int stride,
                                                  float quality, boolean lossless, boolean hasAlpha) {
        if (lossless) {
            return hasAlpha
                    ? NativeWebP.encodeLosslessRGBA(imageBytes, width, height, stride)
                    : NativeWebP.encodeLosslessRGB(imageBytes, width, height, stride);
        } else {
            return hasAlpha
                    ? NativeWebP.encodeRGBA(imageBytes, width, height, stride, quality)
                    : NativeWebP.encodeRGB(imageBytes, width, height, stride, quality);
        }
    }

    /**
     * Creates a BufferedImage from a byte array containing pixel data.
     * <p>
     * This method supports both RGB (3 bytes per pixel) and ARGB (4 bytes per pixel) formats.
     * It determines the format based on the length of the input byte array and the image dimensions.
     *
     * @param width        The width of the image.
     * @param height       The height of the image.
     * @param outputBuffer A byte array containing the pixel data.
     *                     - For RGB format: Each pixel is represented by 3 consecutive bytes (R, G, B).
     *                     - For ARGB format: Each pixel is represented by 4 consecutive bytes (R, G, B, A).
     * @return A BufferedImage object representing the image with the specified width, height, and pixel data.
     * - If the input buffer is RGB, the image will be of type BufferedImage.TYPE_INT_RGB.
     * - If the input buffer is ARGB, the image will be of type BufferedImage.TYPE_INT_ARGB.
     * @throws IllegalArgumentException if the length of the outputBuffer does not match the expected size
     *                                  for the given width, height, and pixel format.
     */
    private static BufferedImage convertBytesToBufferedImage(int width, int height, byte[] outputBuffer) {
        // Determine if the input buffer is RGB (3 bytes per pixel) or ARGB (4 bytes per pixel)
        boolean hasAlpha = outputBuffer.length == width * height * 4;
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        // Use DataBufferInt backend to set pixels directly, avoiding setRGB calls for each pixel
        BufferedImage image = new BufferedImage(width, height, imageType);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        int index = 0;
        int pixelIndex = 0;

        // Process entire rows at once to improve cache utilization
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = outputBuffer[index++] & 0xFF;
                int g = outputBuffer[index++] & 0xFF;
                int b = outputBuffer[index++] & 0xFF;
                int a = hasAlpha ? (outputBuffer[index++] & 0xFF) : 255;

                // Set values directly in the pixel array
                pixels[pixelIndex++] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        return image;
    }

    /**
     * Extracts pixel data from a BufferedImage into a byte array.
     * <p>
     * This method automatically detects the image's color type, channel order, and alpha presence,
     * then extracts pixel data accordingly.
     *
     * @param image The BufferedImage to extract pixel data from.
     * @return A byte array containing the pixel data in the appropriate color format.
     */
    private static byte[] convertBufferedImageToBytes(BufferedImage image) {
        // Check if the image has an Alpha channel
        boolean hasAlpha = image.getColorModel().hasAlpha();

        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerPixel = hasAlpha ? 4 : 3;

        // Allocate only the necessary output buffer
        byte[] output = new byte[width * height * bytesPerPixel];
        int imageType = image.getType();

        try {
            // Handle different types of BufferedImage
            switch (imageType) {
                // INT-based types with direct buffer access
                case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE -> {
                    // Get direct reference without creating a copy
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferInt dataBufferInt) {
                        int[] intPixels = dataBufferInt.getData();
                        int index = 0;

                        // Use direct array access for maximum speed
                        if (hasAlpha) {
                            for (int pixel : intPixels) {
                                output[index++] = (byte) ((pixel >> 16) & 0xFF); // Red
                                output[index++] = (byte) ((pixel >> 8) & 0xFF);  // Green
                                output[index++] = (byte) (pixel & 0xFF);         // Blue
                                output[index++] = (byte) ((pixel >> 24) & 0xFF); // Alpha
                            }
                        } else {
                            for (int pixel : intPixels) {
                                output[index++] = (byte) ((pixel >> 16) & 0xFF); // Red
                                output[index++] = (byte) ((pixel >> 8) & 0xFF);  // Green
                                output[index++] = (byte) (pixel & 0xFF);         // Blue
                            }
                        }
                    } else {
                        processImageByRows(image, output, width, height, hasAlpha);
                    }
                }

                // INT-based BGR type with direct buffer access
                case BufferedImage.TYPE_INT_BGR -> {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferInt dataBufferInt) {
                        int[] bgrIntPixels = dataBufferInt.getData();
                        int index = 0;
                        for (int pixel : bgrIntPixels) {
                            output[index++] = (byte) ((pixel) & 0xFF);       // Red (BGR order)
                            output[index++] = (byte) ((pixel >> 8) & 0xFF);  // Green
                            output[index++] = (byte) ((pixel >> 16) & 0xFF); // Blue (BGR order)
                            if (hasAlpha) {
                                output[index++] = (byte) ((pixel >> 24) & 0xFF); // Alpha
                            }
                        }
                    } else {
                        processImageByRows(image, output, width, height, hasAlpha);
                    }
                }

                // BYTE-based types with direct buffer access
                case BufferedImage.TYPE_3BYTE_BGR -> {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferByte dataBufferByte) {
                        byte[] bgrBytes = dataBufferByte.getData();
                        int index = 0;
                        // Unroll the loop for better performance
                        int maxIndex = bgrBytes.length - 2;  // Safe limit for unrolled loop
                        int i = 0;

                        // Process 3 pixels (9 bytes) at a time
                        for (; i < maxIndex - 8; i += 9) {
                            // Pixel 1
                            output[index++] = bgrBytes[i + 2];
                            output[index++] = bgrBytes[i + 1];
                            output[index++] = bgrBytes[i];

                            // Pixel 2
                            output[index++] = bgrBytes[i + 5];
                            output[index++] = bgrBytes[i + 4];
                            output[index++] = bgrBytes[i + 3];

                            // Pixel 3
                            output[index++] = bgrBytes[i + 8];
                            output[index++] = bgrBytes[i + 7];
                            output[index++] = bgrBytes[i + 6];
                        }

                        // Handle remaining pixels
                        for (; i < bgrBytes.length; i += 3) {
                            output[index++] = bgrBytes[i + 2];  // Red (BGR → RGB)
                            output[index++] = bgrBytes[i + 1];  // Green
                            output[index++] = bgrBytes[i];      // Blue (BGR → RGB)
                        }
                    } else {
                        processImageByRows(image, output, width, height, hasAlpha);
                    }
                }

                case BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_4BYTE_ABGR_PRE -> {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferByte dataBufferByte) {
                        byte[] abgrBytes = dataBufferByte.getData();
                        int index = 0;
                        if (hasAlpha) {
                            // Similar loop unrolling for 4-byte pixels
                            int maxIndex = abgrBytes.length - 7;
                            int i = 0;

                            // Process 2 pixels (8 bytes) at a time
                            for (; i < maxIndex; i += 8) {
                                // Pixel 1
                                output[index++] = abgrBytes[i + 3];  // Red
                                output[index++] = abgrBytes[i + 2];  // Green
                                output[index++] = abgrBytes[i + 1];  // Blue
                                output[index++] = abgrBytes[i];      // Alpha

                                // Pixel 2
                                output[index++] = abgrBytes[i + 7];  // Red
                                output[index++] = abgrBytes[i + 6];  // Green
                                output[index++] = abgrBytes[i + 5];  // Blue
                                output[index++] = abgrBytes[i + 4];  // Alpha
                            }

                            // Handle remaining pixels
                            for (; i < abgrBytes.length; i += 4) {
                                output[index++] = abgrBytes[i + 3];  // Red
                                output[index++] = abgrBytes[i + 2];  // Green
                                output[index++] = abgrBytes[i + 1];  // Blue
                                output[index++] = abgrBytes[i];      // Alpha
                            }
                        } else {
                            // When hasAlpha is false but image has 4 bytes per pixel
                            for (int i = 0; i < abgrBytes.length; i += 4) {
                                output[index++] = abgrBytes[i + 3];  // Red
                                output[index++] = abgrBytes[i + 2];  // Green
                                output[index++] = abgrBytes[i + 1];  // Blue
                            }
                        }
                    } else {
                        processImageByRows(image, output, width, height, hasAlpha);
                    }
                }

                // Default case for all other types
                default -> processImageByRows(image, output, width, height, hasAlpha);
            }
        } catch (Exception e) {
            // Fallback if any error occurs during optimized processing
            processImageByRows(image, output, width, height, hasAlpha);
        }

        return output;
    }

    private static void processImageByRows(BufferedImage image, byte[] output, int width, int height, boolean hasAlpha) {
        // More efficient row-by-row processing
        int[] rowBuffer = new int[width];
        int index = 0;

        for (int y = 0; y < height; y++) {
            // Get the entire row at once
            image.getRGB(0, y, width, 1, rowBuffer, 0, width);

            if (hasAlpha) {
                for (int x = 0; x < width; x++) {
                    int argb = rowBuffer[x];
                    output[index++] = (byte) ((argb >> 16) & 0xFF); // Red
                    output[index++] = (byte) ((argb >> 8) & 0xFF);  // Green
                    output[index++] = (byte) (argb & 0xFF);         // Blue
                    output[index++] = (byte) ((argb >> 24) & 0xFF); // Alpha
                }
            } else {
                for (int x = 0; x < width; x++) {
                    int argb = rowBuffer[x];
                    output[index++] = (byte) ((argb >> 16) & 0xFF); // Red
                    output[index++] = (byte) ((argb >> 8) & 0xFF);  // Green
                    output[index++] = (byte) (argb & 0xFF);         // Blue
                }
            }
        }
    }

}
