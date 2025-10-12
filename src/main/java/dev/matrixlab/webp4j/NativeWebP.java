package dev.matrixlab.webp4j;

public class NativeWebP {

    private static volatile boolean nativeLibraryLoaded = false;

    private NativeWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * int WebPGetInfo(const uint8_t* data, size_t data_size, int* width, int* height);
     */
    public static native boolean getInfo(byte[] data, int[] dimensions);

    /**
     * VP8StatusCode WebPGetFeatures(const uint8_t* data, size_t data_size, WebPBitstreamFeatures* features);
     */
    public static native int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features);

    /**
     * size_t WebPEncodeRGB(const uint8_t* rgb, int width, int height, int stride, float quality_factor, uint8_t** output);
     */
    public static native byte[] encodeRGB(byte[] image, int width, int height, int stride, float quality);

    /**
     * size_t WebPEncodeRGBA(const uint8_t* rgba, int width, int height, int stride, float quality_factor, uint8_t** output);
     */
    public static native byte[] encodeRGBA(byte[] image, int width, int height, int stride, float quality);

    /**
     * size_t WebPEncodeLosslessRGB(const uint8_t* rgb, int width, int height, int stride, uint8_t** output);
     */
    public static native byte[] encodeLosslessRGB(byte[] image, int width, int height, int stride);

    /**
     * size_t WebPEncodeLosslessRGBA(const uint8_t* rgba, int width, int height, int stride, uint8_t** output);
     */
    public static native byte[] encodeLosslessRGBA(byte[] image, int width, int height, int stride);

    /**
     * uint8_t* WebPDecodeRGBInto(const uint8_t* data, size_t data_size, uint8_t* output_buffer, int output_buffer_size, int output_stride);
     */
    public static native boolean decodeRGBInto(byte[] data, byte[] outputBuffer, int outputStride);

    /**
     * uint8_t* WebPDecodeRGBAInto(const uint8_t* data, size_t data_size, uint8_t* output_buffer, int output_buffer_size, int output_stride);
     */
    public static native boolean decodeRGBAInto(byte[] data, byte[] outputBuffer, int outputStride);

    // Use the NativeLibraryLoaderUtils to load the native library
    static void loadNativeLibrary() {
        if (!nativeLibraryLoaded) {
            synchronized (NativeWebP.class) {
                if (!nativeLibraryLoaded) {
                    try {
                        NativeLibraryLoaderUtils.loadLibrary();
                        nativeLibraryLoaded = true;
                    } catch (Exception e) {
                        throw new NativeLibraryNotFoundException("Failed to load native library", e);
                    }
                }
            }
        }
    }

    // When the class is loaded, try to load the native library
    static {
        try {
            loadNativeLibrary();
        } catch (Exception e) {
            throw new NativeLibraryNotFoundException("Failed to load native library", e);
        }
    }

}
