package dev.matrixlab.webp4j.internal;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;

/**
 * Internal utility class for converting between BufferedImage and raw pixel byte arrays.
 * <p>
 * This class serves as a bridge between Java's BufferedImage objects and the raw byte arrays
 * required by the native WebP encoding/decoding functions.
 *
 * @author MrNanko
 */
public final class PixelConverter {

    private PixelConverter() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Creates a BufferedImage from a byte array containing pixel data.
     * <p>
     * This method supports both RGB (3 bytes per pixel) and RGBA (4 bytes per pixel) formats.
     * It determines the format based on the length of the input byte array and the image dimensions.
     *
     * @param width  The width of the image.
     * @param height The height of the image.
     * @param data   A byte array containing the pixel data.
     *               - For RGB format: Each pixel is represented by 3 consecutive bytes (R, G, B).
     *               - For RGBA format: Each pixel is represented by 4 consecutive bytes (R, G, B, A).
     * @return A BufferedImage object representing the image with the specified width, height, and pixel data.
     *         - If the input buffer is RGB, the image will be of type BufferedImage.TYPE_INT_RGB.
     *         - If the input buffer is RGBA, the image will be of type BufferedImage.TYPE_INT_ARGB.
     */
    public static BufferedImage toBufferedImage(int width, int height, byte[] data) {
        // Determine if the input buffer is RGB (3 bytes per pixel) or ARGB (4 bytes per pixel)
        boolean hasAlpha = data.length == width * height * 4;
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        // Use DataBufferInt backend to set pixels directly, avoiding setRGB calls for each pixel
        BufferedImage image = new BufferedImage(width, height, imageType);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        int index = 0;
        int pixelIndex = 0;

        // Process entire rows at once to improve cache utilization
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = data[index++] & 0xFF;
                int g = data[index++] & 0xFF;
                int b = data[index++] & 0xFF;
                int a = hasAlpha ? (data[index++] & 0xFF) : 255;

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
     * @return A byte array containing the pixel data in RGB or RGBA format.
     */
    public static byte[] toBytes(BufferedImage image) {
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
                case BufferedImage.TYPE_INT_RGB:
                case BufferedImage.TYPE_INT_ARGB:
                case BufferedImage.TYPE_INT_ARGB_PRE: {
                    // Get direct reference without creating a copy
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferInt) {
                        DataBufferInt dataBufferInt = (DataBufferInt) dataBuffer;
                        int[] intPixels = dataBufferInt.getData();
                        int index = 0;
                        boolean isPremultiplied = (imageType == BufferedImage.TYPE_INT_ARGB_PRE);

                        // Use direct array access for maximum speed
                        if (hasAlpha) {
                            for (int pixel : intPixels) {
                                int a = (pixel >> 24) & 0xFF;
                                int r = (pixel >> 16) & 0xFF;
                                int g = (pixel >> 8) & 0xFF;
                                int b = pixel & 0xFF;

                                // Unpremultiply alpha if needed to avoid dark/black edges
                                if (isPremultiplied && a > 0 && a < 255) {
                                    r = (r * 255 + (a >> 1)) / a;
                                    g = (g * 255 + (a >> 1)) / a;
                                    b = (b * 255 + (a >> 1)) / a;
                                    // Clamp values to [0, 255]
                                    r = Math.min(255, r);
                                    g = Math.min(255, g);
                                    b = Math.min(255, b);
                                }

                                output[index++] = (byte) r; // Red
                                output[index++] = (byte) g; // Green
                                output[index++] = (byte) b; // Blue
                                output[index++] = (byte) a; // Alpha
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
                    break;
                }

                // INT-based BGR type with direct buffer access
                case BufferedImage.TYPE_INT_BGR: {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferInt) {
                        DataBufferInt dataBufferInt = (DataBufferInt) dataBuffer;
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
                    break;
                }

                // BYTE-based types with direct buffer access
                case BufferedImage.TYPE_3BYTE_BGR: {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferByte) {
                        DataBufferByte dataBufferByte = (DataBufferByte) dataBuffer;
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
                    break;
                }

                case BufferedImage.TYPE_4BYTE_ABGR:
                case BufferedImage.TYPE_4BYTE_ABGR_PRE: {
                    DataBuffer dataBuffer = image.getRaster().getDataBuffer();
                    if (dataBuffer instanceof DataBufferByte) {
                        DataBufferByte dataBufferByte = (DataBufferByte) dataBuffer;
                        byte[] abgrBytes = dataBufferByte.getData();
                        int index = 0;
                        boolean isPremultiplied = (imageType == BufferedImage.TYPE_4BYTE_ABGR_PRE);

                        if (hasAlpha) {
                            // Process all pixels with unpremultiply support
                            for (int i = 0; i < abgrBytes.length; i += 4) {
                                int a = abgrBytes[i] & 0xFF;      // Alpha
                                int b = abgrBytes[i + 1] & 0xFF;  // Blue
                                int g = abgrBytes[i + 2] & 0xFF;  // Green
                                int r = abgrBytes[i + 3] & 0xFF;  // Red

                                // Unpremultiply alpha if needed to avoid dark/black edges
                                if (isPremultiplied && a > 0 && a < 255) {
                                    r = (r * 255 + (a >> 1)) / a;
                                    g = (g * 255 + (a >> 1)) / a;
                                    b = (b * 255 + (a >> 1)) / a;
                                    // Clamp values to [0, 255]
                                    r = Math.min(255, r);
                                    g = Math.min(255, g);
                                    b = Math.min(255, b);
                                }

                                output[index++] = (byte) r;  // Red
                                output[index++] = (byte) g;  // Green
                                output[index++] = (byte) b;  // Blue
                                output[index++] = (byte) a;  // Alpha
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
                    break;
                }

                // Default case for all other types
                default:
                    processImageByRows(image, output, width, height, hasAlpha);
                    break;
            }
        } catch (Exception e) {
            // Fallback if any error occurs during optimized processing
            processImageByRows(image, output, width, height, hasAlpha);
        }

        return output;
    }

    /**
     * Fallback method for processing images row by row using getRGB.
     */
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
