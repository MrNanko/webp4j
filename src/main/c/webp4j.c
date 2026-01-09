#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <webp/encode.h>
#include <webp/decode.h>
#include <webp/mux.h>
#include "dev_matrixlab_webp4j_NativeWebP.h"

#ifdef HAVE_GIFLIB
#include "gif_decoder.h"
#endif

/*
 * Utility function to convert a Java byte array to a native uint8_t array.
 */
uint8_t* jByteArrayToUint8(JNIEnv *env, jbyteArray array) {
    jsize len = (*env)->GetArrayLength(env, array);
    jbyte* data = (*env)->GetByteArrayElements(env, array, 0);
    if (data == NULL) {
        return NULL;  // Failed to get byte array
    }

    uint8_t* result = (uint8_t*) malloc(len * sizeof(uint8_t));
    if (result == NULL) {
        (*env)->ReleaseByteArrayElements(env, array, data, JNI_ABORT);
        return NULL;  // Memory allocation failed
    }

    for (int i = 0; i < len; i++) {
        result[i] = (uint8_t) data[i];
    }

    (*env)->ReleaseByteArrayElements(env, array, data, JNI_ABORT);
    return result;
}

/*
 * Free the native uint8_t array.
 */
void freeUint8(uint8_t* ptr) {
    if (ptr) {
        free(ptr);
    }
}

/*
 * Class:     NativeWebP
 * Method:    getInfo
 * Signature: ([B[I)Z
 *
 * This JNI function retrieves the width and height of a WebP image.
 * It uses the libwebp function WebPGetFeatures to extract the image dimensions
 * from the input byte array and stores them in a Java integer array.
 *
 * Parameters:
 * - data: A Java byte array containing the WebP image data.
 * - dimensions: A Java integer array to store the width and height of the image.
 *
 * The function performs the following steps:
 * 1. Converts the input Java byte array to a native uint8_t array.
 * 2. Calls the WebPGetFeatures function to extract the image dimensions.
 * 3. Stores the width and height in the provided Java integer array.
 * 4. Releases the native resources and returns success or failure.
 *
 * Returns:
 * - true (JNI_TRUE) if the operation is successful.
 * - false (JNI_FALSE) if the operation fails.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_NativeWebP_getInfo
  (JNIEnv *env, jclass clazz, jbyteArray data, jintArray dimensions) {

    // Convert Java byte array to native uint8_t array
    jbyte* webp_data = (*env)->GetByteArrayElements(env, data, NULL);
    if (webp_data == NULL) {
        return JNI_FALSE;  // Failed to convert byte array
    }

    // Retrieve the length of the WebP data
    jsize data_size = (*env)->GetArrayLength(env, data);

    // Declare width and height variables
    int width = 0;
    int height = 0;

    // WebP feature structure
    WebPBitstreamFeatures features;

    // Use WebPGetFeatures to retrieve the width and height of the WebP image
    VP8StatusCode status = WebPGetFeatures((const uint8_t*)webp_data, (size_t)data_size, &features);

    if (status != VP8_STATUS_OK) {
        (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);
        return JNI_FALSE;  // Failed to get WebP features
    }

    // Set width and height
    width = features.width;
    height = features.height;

    // Get the dimensions array from Java
    jint* dims = (*env)->GetIntArrayElements(env, dimensions, NULL);
    if (dims == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);
        return JNI_FALSE;  // Failed to get int array
    }

    // Store the width and height in the dimensions array
    dims[0] = width;
    dims[1] = height;

    // Release the dimensions array and the WebP data
    (*env)->ReleaseIntArrayElements(env, dimensions, dims, 0);
    (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);

    return JNI_TRUE;  // Success
}

/*
 * Class:     NativeWebP
 * Method:    getFeatures
 * Signature: ([BI LWebPBitstreamFeatures;)I
 *
 * This JNI function wraps the libwebp function WebPGetFeatures.
 * It extracts WebP bitstream features from the input byte array and populates
 * a Java WebPBitstreamFeatures object with the extracted values.
 *
 * Parameters:
 * - data: A Java byte array containing the WebP image data.
 * - dataSize: The size of the WebP image data in bytes.
 * - featuresObj: A Java object of type WebPBitstreamFeatures to store the extracted features.
 *
 * The function performs the following steps:
 * 1. Retrieves the WebP image data from the input Java byte array.
 * 2. Calls the WebPGetFeatures function to extract the bitstream features.
 * 3. Maps the extracted features to the fields of the Java WebPBitstreamFeatures object.
 * 4. Releases the input byte array.
 *
 * Returns:
 * - VP8_STATUS_OK (0) if the operation is successful.
 * - A non-zero error code if the operation fails.
 */
JNIEXPORT jint JNICALL Java_dev_matrixlab_webp4j_NativeWebP_getFeatures
  (JNIEnv *env, jclass clazz, jbyteArray data, jint dataSize, jobject featuresObj) {

    // Retrieve the pointer to the input byte array.
    jbyte* webpData = (*env)->GetByteArrayElements(env, data, NULL);
    if (webpData == NULL) {
        // Failed to get byte array elements; return an error code.
        return -1;
    }

    // Initialize the C structure to hold bitstream features.
    WebPBitstreamFeatures cFeatures;
    int status = WebPGetFeatures((const uint8_t*)webpData, (size_t)dataSize, &cFeatures);

    // Release the input array; use JNI_ABORT as we do not need to copy back modifications.
    (*env)->ReleaseByteArrayElements(env, data, webpData, JNI_ABORT);

    // If WebPGetFeatures did not succeed, return the error status.
    if (status != VP8_STATUS_OK) {
        return status;
    }

    // Obtain the Java class of the features object.
    jclass featuresClass = (*env)->GetObjectClass(env, featuresObj);
    if (featuresClass == NULL) {
        return status;
    }

    // Get the field IDs for the expected fields in the Java WebPBitstreamFeatures class.
    // Assuming the Java class defines the fields: int width, int height, boolean hasAlpha,
    // boolean hasAnimation, and int format.
    jfieldID fidWidth       = (*env)->GetFieldID(env, featuresClass, "width", "I");
    jfieldID fidHeight      = (*env)->GetFieldID(env, featuresClass, "height", "I");
    jfieldID fidHasAlpha    = (*env)->GetFieldID(env, featuresClass, "hasAlpha", "Z");
    jfieldID fidHasAnimation= (*env)->GetFieldID(env, featuresClass, "hasAnimation", "Z");
    jfieldID fidFormat      = (*env)->GetFieldID(env, featuresClass, "format", "I");

    // Check that all field IDs are successfully obtained.
    if (fidWidth == NULL || fidHeight == NULL || fidHasAlpha == NULL || fidHasAnimation == NULL || fidFormat == NULL) {
        // Optionally, we can throw an exception here.
        return status;
    }

    // Write the values from the C structure into the Java object's fields.
    (*env)->SetIntField(env, featuresObj, fidWidth, cFeatures.width);
    (*env)->SetIntField(env, featuresObj, fidHeight, cFeatures.height);
    (*env)->SetBooleanField(env, featuresObj, fidHasAlpha, cFeatures.has_alpha ? JNI_TRUE : JNI_FALSE);
    (*env)->SetBooleanField(env, featuresObj, fidHasAnimation, cFeatures.has_animation ? JNI_TRUE : JNI_FALSE);
    (*env)->SetIntField(env, featuresObj, fidFormat, cFeatures.format);

    // Return the status code from WebPGetFeatures.
    return status;
}

/*
 * Class:     NativeWebP
 * Method:    encodeRGB
 * Signature: ([BIII F)[B
 *
 * This JNI function wraps the libwebp function WebPEncodeRGB.
 * It encodes an RGB image provided as a byte array into the WebP format.
 *
 * Parameters:
 * - image: A Java byte array containing the RGB image data.
 * - width: The width of the image in pixels.
 * - height: The height of the image in pixels.
 * - stride: The number of bytes per row in the image.
 * - quality: A float value representing the quality factor for encoding (0 to 100).
 *
 * The function performs the following steps:
 * 1. Converts the Java byte array to a native uint8_t array.
 * 2. Calls the WebPEncodeRGB function to encode the image into WebP format.
 * 3. Creates a new Java byte array to store the encoded WebP data.
 * 4. Copies the encoded data into the Java byte array and returns it.
 *
 * Returns:
 * - A Java byte array containing the encoded WebP image, or NULL if encoding fails.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeRGB
  (JNIEnv *env, jclass clazz, jbyteArray image, jint width, jint height, jint stride, jfloat quality) {

    // Convert Java byte array to native uint8_t array
    uint8_t* rgb = jByteArrayToUint8(env, image);
    if (rgb == NULL) {
        return NULL;  // Failed to convert byte array
    }

    // Output buffer
    uint8_t* output = NULL;

    // Call WebPEncodeRGB function
    size_t output_size = WebPEncodeRGB(rgb, width, height, stride, quality, &output);

    // Free the input RGB array
    freeUint8(rgb);

    // Check if encoding was successful
    if (output_size == 0 || output == NULL) {
        return NULL;  // Encoding failed
    }

    // Create a new Java byte array for the output
    jbyteArray result = (*env)->NewByteArray(env, output_size);
    if (result == NULL) {
        WebPFree(output);  // Ensure the output buffer is freed
        return NULL;  // Memory allocation failed
    }

    // Copy the encoded output to the Java byte array
    (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*) output);

    // Free the WebP output
    WebPFree(output);

    // Return the result
    return result;
}

/*
 * Class:     NativeWebP
 * Method:    encodeRGBA
 * Signature: ([BIII F)[B
 *
 * This JNI function wraps the libwebp function WebPEncodeRGBA.
 * It encodes an RGBA image provided as a Java byte array into the WebP format.
 * The method takes the following parameters:
 * - image: A Java byte array containing the RGBA image data.
 * - width: The width of the image in pixels.
 * - height: The height of the image in pixels.
 * - stride: The number of bytes per row in the image.
 * - quality: A float value representing the quality factor for encoding (0 to 100).
 *
 * The function performs the following steps:
 * 1. Converts the Java byte array to a native uint8_t array.
 * 2. Calls the WebPEncodeRGBA function to encode the image into WebP format.
 * 3. Creates a new Java byte array to store the encoded WebP data.
 * 4. Copies the encoded data into the Java byte array and returns it.
 *
 * Returns:
 * - A Java byte array containing the encoded WebP image, or NULL if encoding fails.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeRGBA
  (JNIEnv *env, jclass clazz, jbyteArray image, jint width, jint height, jint stride, jfloat quality) {

    // Convert Java byte array to native uint8_t array
    uint8_t* rgba = jByteArrayToUint8(env, image);
    if (rgba == NULL) {
        return NULL;  // Failed to convert byte array
    }

    // Output buffer
    uint8_t* output = NULL;

    // Call WebPEncodeRGBA function
    size_t output_size = WebPEncodeRGBA(rgba, width, height, stride, quality, &output);

    // Free the input RGBA array
    freeUint8(rgba);

    // Check if encoding was successful
    if (output_size == 0 || output == NULL) {
        return NULL;  // Encoding failed
    }

    // Create a new Java byte array for the output
    jbyteArray result = (*env)->NewByteArray(env, output_size);
    if (result == NULL) {
        WebPFree(output);  // Ensure the output buffer is freed
        return NULL;  // Memory allocation failed
    }

    // Copy the encoded output to the Java byte array
    (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*) output);

    // Free the WebP output
    WebPFree(output);

    // Return the result
    return result;
}

/*
 * Class:     NativeWebP
 * Method:    encodeLosslessRGB
 * Signature: ([BIII)[B
 *
 * This JNI function wraps the libwebp function WebPEncodeLosslessRGB.
 * It encodes an RGB image provided as a byte array into the lossless WebP format.
 *
 * Parameters:
 * - image: A Java byte array containing the RGB image data.
 * - width: The width of the image in pixels.
 * - height: The height of the image in pixels.
 * - stride: The number of bytes per row in the image.
 *
 * The function performs the following steps:
 * 1. Converts the Java byte array to a native uint8_t array.
 * 2. Calls the WebPEncodeLosslessRGB function to encode the image into lossless WebP format.
 * 3. Creates a new Java byte array to store the encoded WebP data.
 * 4. Copies the encoded data into the Java byte array and returns it.
 *
 * Returns:
 * - A Java byte array containing the encoded lossless WebP image, or NULL if encoding fails.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeLosslessRGB
  (JNIEnv *env, jclass clazz, jbyteArray image, jint width, jint height, jint stride) {

    // Convert Java byte array to native uint8_t array
    uint8_t* rgb = jByteArrayToUint8(env, image);
    if (rgb == NULL) {
        return NULL;  // Failed to convert byte array
    }

    // Output buffer
    uint8_t* output = NULL;

    // Call WebPEncodeLosslessRGB function
    size_t output_size = WebPEncodeLosslessRGB(rgb, width, height, stride, &output);

    // Free the input RGB array
    freeUint8(rgb);

    // Check if encoding was successful
    if (output_size == 0 || output == NULL) {
        return NULL;  // Encoding failed
    }

    // Create a new Java byte array for the output
    jbyteArray result = (*env)->NewByteArray(env, output_size);
    if (result == NULL) {
        WebPFree(output);  // Ensure the output buffer is freed
        return NULL;  // Memory allocation failed
    }

    // Copy the encoded output to the Java byte array
    (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*) output);

    // Free the WebP output
    WebPFree(output);

    // Return the result
    return result;
}

/*
 * Class:     NativeWebP
 * Method:    encodeLosslessRGBA
 * Signature: ([BIII)[B
 *
 * This JNI function wraps the libwebp function WebPEncodeLosslessRGBA.
 * It encodes an RGBA image provided as a byte array into the lossless WebP format.
 *
 * Parameters:
 * - image: A Java byte array containing the RGBA image data.
 * - width: The width of the image in pixels.
 * - height: The height of the image in pixels.
 * - stride: The number of bytes per row in the image.
 *
 * The function performs the following steps:
 * 1. Converts the Java byte array to a native uint8_t array.
 * 2. Calls the WebPEncodeLosslessRGBA function to encode the image into lossless WebP format.
 * 3. Creates a new Java byte array to store the encoded WebP data.
 * 4. Copies the encoded data into the Java byte array and returns it.
 *
 * Returns:
 * - A Java byte array containing the encoded lossless WebP image, or NULL if encoding fails.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeLosslessRGBA
  (JNIEnv *env, jclass clazz, jbyteArray image, jint width, jint height, jint stride) {

    // Convert Java byte array to native uint8_t array
    uint8_t* rgba = jByteArrayToUint8(env, image);
    if (rgba == NULL) {
        return NULL;  // Failed to convert byte array
    }

    // Output buffer
    uint8_t* output = NULL;

    // Call WebPEncodeLosslessRGBA function
    size_t output_size = WebPEncodeLosslessRGBA(rgba, width, height, stride, &output);

    // Free the input RGBA array
    freeUint8(rgba);

    // Check if encoding was successful
    if (output_size == 0 || output == NULL) {
        return NULL;  // Encoding failed
    }

    // Create a new Java byte array for the output
    jbyteArray result = (*env)->NewByteArray(env, output_size);
    if (result == NULL) {
        WebPFree(output);  // Ensure the output buffer is freed
        return NULL;  // Memory allocation failed
    }

    // Copy the encoded output to the Java byte array
    (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*) output);

    // Free the WebP output
    WebPFree(output);

    // Return the result
    return result;
}

/*
 * Class:     NativeWebP
 * Method:    decodeRGBInto
 * Signature: ([B[BI)Z
 *
 * This JNI function wraps the libwebp function WebPDecodeRGBInto.
 * It decodes a WebP image from a Java byte array into an RGB format and stores
 * the result in a provided output buffer.
 *
 * Parameters:
 * - data: A Java byte array containing the WebP image data.
 * - outputBuffer: A Java byte array to store the decoded RGB image.
 * - outputStride: The number of bytes per row in the output buffer.
 *
 * The function performs the following steps:
 * 1. Retrieves the WebP image data from the input Java byte array.
 * 2. Retrieves the output buffer to store the decoded RGB image.
 * 3. Calls the WebPDecodeRGBInto function to decode the WebP image into the output buffer.
 * 4. Releases the input and output buffers.
 *
 * Returns:
 * - true (JNI_TRUE) if decoding is successful.
 * - false (JNI_FALSE) if decoding fails.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_NativeWebP_decodeRGBInto
  (JNIEnv *env, jclass clazz, jbyteArray data, jbyteArray outputBuffer, jint outputStride) {

    // Get data size
    jsize data_size = (*env)->GetArrayLength(env, data);

    // Get webp data
    jbyte* webp_data = (*env)->GetByteArrayElements(env, data, NULL);
    if (webp_data == NULL) {
        return JNI_FALSE;  // Failed to get data
    }

    // Get output buffer size
    jsize output_buffer_size = (*env)->GetArrayLength(env, outputBuffer);

    // Get output buffer
    jbyte* output_buffer = (*env)->GetByteArrayElements(env, outputBuffer, NULL);
    if (output_buffer == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);
        return JNI_FALSE;
    }

    // Call WebPDecodeRGBInto
    uint8_t* result = WebPDecodeRGBInto(
        (const uint8_t*)webp_data,
        (size_t)data_size,
        (uint8_t*)output_buffer,
        (int)output_buffer_size,
        (int)outputStride
    );

    // Release the input data
    (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);

    // Release the output buffer and commit changes
    (*env)->ReleaseByteArrayElements(env, outputBuffer, output_buffer, 0);

    // Check if decoding was successful
    if (result == NULL) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Class:     NativeWebP
 * Method:    decodeRGBAInto
 * Signature: ([B[BI)Z
 *
 * This JNI function wraps the libwebp function WebPDecodeRGBAInto.
 * It decodes a WebP image from a Java byte array into an RGBA format and stores
 * the result in a provided output buffer.
 *
 * Parameters:
 * - data: A Java byte array containing the WebP image data.
 * - outputBuffer: A Java byte array to store the decoded RGBA image.
 * - outputStride: The number of bytes per row in the output buffer.
 *
 * The function performs the following steps:
 * 1. Retrieves the WebP image data from the input Java byte array.
 * 2. Retrieves the output buffer to store the decoded RGBA image.
 * 3. Calls the WebPDecodeRGBAInto function to decode the WebP image into the output buffer.
 * 4. Releases the input and output buffers.
 *
 * Returns:
 * - true (JNI_TRUE) if decoding is successful.
 * - false (JNI_FALSE) if decoding fails.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_NativeWebP_decodeRGBAInto
  (JNIEnv *env, jclass clazz, jbyteArray data, jbyteArray outputBuffer, jint outputStride) {

    // Get data size
    jsize data_size = (*env)->GetArrayLength(env, data);

    // Get webp data
    jbyte* webp_data = (*env)->GetByteArrayElements(env, data, NULL);
    if (webp_data == NULL) {
        return JNI_FALSE;  // Failed to get data
    }

    // Get output buffer size
    jsize output_buffer_size = (*env)->GetArrayLength(env, outputBuffer);

    // Get output buffer
    jbyte* output_buffer = (*env)->GetByteArrayElements(env, outputBuffer, NULL);
    if (output_buffer == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);
        return JNI_FALSE;
    }

    // Call WebPDecodeRGBAInto
    uint8_t* result = WebPDecodeRGBAInto(
        (const uint8_t*)webp_data,
        (size_t)data_size,
        (uint8_t*)output_buffer,
        (int)output_buffer_size,
        (int)outputStride
    );

    // Release the input data
    (*env)->ReleaseByteArrayElements(env, data, webp_data, JNI_ABORT);

    // Release the output buffer and commit changes
    (*env)->ReleaseByteArrayElements(env, outputBuffer, output_buffer, 0);

    // Check if decoding was successful
    if (result == NULL) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Class:     NativeWebP
 * Method:    getGifInfo
 * Signature: ([BLdev/matrixlab/webp4j/AnimationInfo;)Z
 *
 * This JNI function gets information about a GIF file using native giflib.
 *
 * NOTE: This is currently a stub implementation that returns JNI_FALSE.
 *       The Java code will automatically fall back to Java ImageIO.
 *       Full giflib integration will be implemented in Phase 3-4.
 *
 * Parameters:
 * - gifData: GIF image bytes
 * - info: AnimationInfo object to populate (via JNI field access)
 *
 * Returns:
 * - True on success, false on failure (triggers Java fallback)
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_NativeWebP_getGifInfo
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jobject info) {

#ifdef HAVE_GIFLIB
    // Get GIF data
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return JNI_FALSE;
    }

    // Get GIF info
    int width, height, frame_count, loop_count, has_transparency;
    int success = GetGifInfo((const uint8_t*)gif_bytes, data_size,
                            &width, &height, &frame_count,
                            &loop_count, &has_transparency);

    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    if (!success) {
        return JNI_FALSE;  // Fall back to Java
    }

    // Populate AnimationInfo fields
    jclass infoClass = (*env)->GetObjectClass(env, info);
    if (infoClass == NULL) {
        return JNI_FALSE;
    }

    jfieldID fidWidth = (*env)->GetFieldID(env, infoClass, "width", "I");
    jfieldID fidHeight = (*env)->GetFieldID(env, infoClass, "height", "I");
    jfieldID fidFrameCount = (*env)->GetFieldID(env, infoClass, "frameCount", "I");
    jfieldID fidLoopCount = (*env)->GetFieldID(env, infoClass, "loopCount", "I");
    jfieldID fidHasTransparency = (*env)->GetFieldID(env, infoClass, "hasTransparency", "Z");

    if (fidWidth == NULL || fidHeight == NULL || fidFrameCount == NULL ||
        fidLoopCount == NULL || fidHasTransparency == NULL) {
        return JNI_FALSE;
    }

    (*env)->SetIntField(env, info, fidWidth, width);
    (*env)->SetIntField(env, info, fidHeight, height);
    (*env)->SetIntField(env, info, fidFrameCount, frame_count);
    (*env)->SetIntField(env, info, fidLoopCount, loop_count);
    (*env)->SetBooleanField(env, info, fidHasTransparency, has_transparency ? JNI_TRUE : JNI_FALSE);

    return JNI_TRUE;
#else
    // giflib not available, return JNI_FALSE to trigger Java ImageIO fallback
    return JNI_FALSE;
#endif
}

/*
 * Class:     NativeWebP
 * Method:    encodeGifToWebP
 * Signature: ([BFZIZIIIZZ)[B
 *
 * This JNI function converts GIF data to WebP format using native giflib decoder.
 * This is the primary (fast) path using native GIF decoding.
 *
 * Parameters:
 * - gifData: GIF image bytes
 * - quality: Quality factor (0-100)
 * - lossless: True for lossless encoding
 * - compressionMethod: Compression method (0-6)
 * - extractFirstFrameOnly: True to extract only first frame
 * - loopCount: Loop count (0=infinite, -1=use GIF's)
 * - kmin: Minimum key-frame distance
 * - kmax: Maximum key-frame distance
 * - minimizeSize: True to minimize output size
 * - allowMixed: True to allow mixed compression
 *
 * Returns:
 * - WebP encoded byte array, or NULL on failure (triggers Java fallback)
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeGifToWebP
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jfloat quality,
   jboolean lossless, jint compressionMethod, jboolean extractFirstFrameOnly,
   jint loopCount, jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed) {

#ifdef HAVE_GIFLIB
    // Get GIF data
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return NULL;
    }

    // Decode GIF based on mode
    if (extractFirstFrameOnly) {
        // Decode only first frame
        int canvas_width, canvas_height;
        GifFrame* frame = DecodeGifFirstFrame((const uint8_t*)gif_bytes, data_size,
                                              &canvas_width, &canvas_height);
        (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

        if (frame == NULL) {
            return NULL;  // Fall back to Java
        }

        // Encode as static WebP
        uint8_t* output = NULL;
        size_t output_size;

        if (lossless) {
            output_size = WebPEncodeLosslessRGBA(frame->rgba_data,
                frame->width, frame->height, frame->width * 4, &output);
        } else {
            output_size = WebPEncodeRGBA(frame->rgba_data,
                frame->width, frame->height, frame->width * 4, quality, &output);
        }

        FreeGifFrame(frame);

        if (output_size == 0 || output == NULL) {
            return NULL;
        }

        jbyteArray result = (*env)->NewByteArray(env, output_size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*)output);
        }
        WebPFree(output);
        return result;
    }

    // Decode all frames for animation
    GifDecodeResult* gif_result = DecodeGifFromMemory((const uint8_t*)gif_bytes, data_size);
    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    if (gif_result == NULL || gif_result->frame_count == 0) {
        if (gif_result) FreeGifDecodeResult(gif_result);
        return NULL;  // Fall back to Java
    }

    // Single frame GIF -> encode as static WebP
    if (gif_result->frame_count == 1) {
        uint8_t* output = NULL;
        size_t output_size;

        if (lossless) {
            output_size = WebPEncodeLosslessRGBA(gif_result->frames[0].rgba_data,
                gif_result->canvas_width, gif_result->canvas_height,
                gif_result->canvas_width * 4, &output);
        } else {
            output_size = WebPEncodeRGBA(gif_result->frames[0].rgba_data,
                gif_result->canvas_width, gif_result->canvas_height,
                gif_result->canvas_width * 4, quality, &output);
        }

        FreeGifDecodeResult(gif_result);

        if (output_size == 0 || output == NULL) {
            return NULL;
        }

        jbyteArray result = (*env)->NewByteArray(env, output_size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, output_size, (jbyte*)output);
        }
        WebPFree(output);
        return result;
    }

    // Multi-frame GIF -> encode as animated WebP
    WebPAnimEncoderOptions enc_options;
    if (!WebPAnimEncoderOptionsInit(&enc_options)) {
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    enc_options.anim_params.loop_count = (loopCount == -1) ?
        gif_result->loop_count : loopCount;
    // Force alpha=0 to preserve transparency in WebP output
    enc_options.anim_params.bgcolor = gif_result->bgcolor & 0x00FFFFFF;
    enc_options.kmin = kmin;
    enc_options.kmax = kmax;
    enc_options.minimize_size = minimizeSize ? 1 : 0;
    enc_options.allow_mixed = allowMixed ? 1 : 0;

    WebPAnimEncoder* enc = WebPAnimEncoderNew(gif_result->canvas_width,
                                              gif_result->canvas_height,
                                              &enc_options);
    if (enc == NULL) {
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    // Configure WebP encoding
    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        WebPAnimEncoderDelete(enc);
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    config.lossless = lossless ? 1 : 0;
    config.quality = quality;
    config.method = compressionMethod;

    if (!WebPValidateConfig(&config)) {
        WebPAnimEncoderDelete(enc);
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    // Add frames
    int timestamp_ms = 0;
    for (int i = 0; i < gif_result->frame_count; i++) {
        GifFrame* frame = &gif_result->frames[i];

        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            WebPAnimEncoderDelete(enc);
            FreeGifDecodeResult(gif_result);
            return NULL;
        }

        picture.width = gif_result->canvas_width;
        picture.height = gif_result->canvas_height;
        picture.use_argb = 1;

        if (!WebPPictureAlloc(&picture)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(enc);
            FreeGifDecodeResult(gif_result);
            return NULL;
        }

        if (!WebPPictureImportRGBA(&picture, frame->rgba_data,
                                  frame->width * 4)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(enc);
            FreeGifDecodeResult(gif_result);
            return NULL;
        }

        if (!WebPAnimEncoderAdd(enc, &picture, timestamp_ms, &config)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(enc);
            FreeGifDecodeResult(gif_result);
            return NULL;
        }

        WebPPictureFree(&picture);
        timestamp_ms += frame->duration_ms;
    }

    // Finalize
    if (!WebPAnimEncoderAdd(enc, NULL, timestamp_ms, NULL)) {
        WebPAnimEncoderDelete(enc);
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    WebPData webp_data;
    WebPDataInit(&webp_data);

    if (!WebPAnimEncoderAssemble(enc, &webp_data)) {
        WebPAnimEncoderDelete(enc);
        FreeGifDecodeResult(gif_result);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, webp_data.size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, webp_data.size, (jbyte*)webp_data.bytes);
    }

    WebPDataClear(&webp_data);
    WebPAnimEncoderDelete(enc);
    FreeGifDecodeResult(gif_result);

    return result;
#else
    // giflib not available, return NULL to trigger Java ImageIO fallback
    return NULL;
#endif
}

/*
 * Class:     NativeWebP
 * Method:    encodeAnimatedWebP
 * Signature: ([[B[IIIFZIIIZZZ)[B
 *
 * This JNI function encodes animated WebP from Java-decoded GIF frames.
 * This is used when GIF is decoded by Java ImageIO (fallback path).
 *
 * Parameters:
 * - frames: Array of RGBA frame data (each frame is width * height * 4 bytes)
 * - delays: Array of frame delays in milliseconds
 * - width: Canvas width
 * - height: Canvas height
 * - quality: Quality factor (0-100)
 * - lossless: True for lossless encoding
 * - compressionMethod: Compression method (0-6)
 * - loopCount: Loop count (0=infinite)
 * - kmin: Minimum key-frame distance
 * - kmax: Maximum key-frame distance
 * - minimizeSize: True to minimize output size
 * - allowMixed: True to allow mixed compression
 *
 * Returns:
 * - A Java byte array containing the encoded animated WebP, or NULL if encoding fails.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_NativeWebP_encodeAnimatedWebP
  (JNIEnv *env, jclass clazz, jobjectArray frames, jintArray delays, jint width, jint height,
   jfloat quality, jboolean lossless, jint compressionMethod, jint loopCount,
   jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed) {

    // 1. Get frame count
    jsize frame_count = (*env)->GetArrayLength(env, frames);
    if (frame_count == 0) {
        return NULL;  // No frames to encode
    }

    // 2. Get delays array
    jint* delay_array = (*env)->GetIntArrayElements(env, delays, NULL);
    if (delay_array == NULL) {
        return NULL;  // Failed to get delays
    }

    // 3. Initialize WebP animation encoder
    WebPAnimEncoderOptions enc_options;
    if (!WebPAnimEncoderOptionsInit(&enc_options)) {
        (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);
        return NULL;
    }

    enc_options.anim_params.loop_count = loopCount;
    enc_options.anim_params.bgcolor = 0x00000000;  // Transparent background
    enc_options.kmin = kmin;
    enc_options.kmax = kmax;
    enc_options.minimize_size = minimizeSize ? 1 : 0;
    enc_options.allow_mixed = allowMixed ? 1 : 0;

    WebPAnimEncoder* enc = WebPAnimEncoderNew(width, height, &enc_options);
    if (enc == NULL) {
        (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);
        return NULL;
    }

    // 4. Initialize WebP config
    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);
        WebPAnimEncoderDelete(enc);
        return NULL;
    }

    config.lossless = lossless ? 1 : 0;
    config.quality = quality;
    config.method = compressionMethod;

    if (!WebPValidateConfig(&config)) {
        (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);
        WebPAnimEncoderDelete(enc);
        return NULL;
    }

    // 5. Add frames to encoder
    int timestamp_ms = 0;
    jboolean encoding_failed = JNI_FALSE;

    for (jsize i = 0; i < frame_count; i++) {
        // Get frame byte array
        jbyteArray frame_data = (jbyteArray)(*env)->GetObjectArrayElement(env, frames, i);
        if (frame_data == NULL) {
            encoding_failed = JNI_TRUE;
            break;
        }

        jsize frame_size = (*env)->GetArrayLength(env, frame_data);
        jbyte* frame_bytes = (*env)->GetByteArrayElements(env, frame_data, NULL);
        if (frame_bytes == NULL) {
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        // Verify frame size
        if (frame_size != width * height * 4) {
            (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        // Create WebPPicture
        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        picture.width = width;
        picture.height = height;
        picture.use_argb = 1;

        if (!WebPPictureAlloc(&picture)) {
            WebPPictureFree(&picture);
            (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        // Import RGBA data
        if (!WebPPictureImportRGBA(&picture, (uint8_t*)frame_bytes, width * 4)) {
            WebPPictureFree(&picture);
            (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        // Add frame to encoder
        if (!WebPAnimEncoderAdd(enc, &picture, timestamp_ms, &config)) {
            WebPPictureFree(&picture);
            (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
            (*env)->DeleteLocalRef(env, frame_data);
            encoding_failed = JNI_TRUE;
            break;
        }

        // Cleanup
        WebPPictureFree(&picture);
        (*env)->ReleaseByteArrayElements(env, frame_data, frame_bytes, JNI_ABORT);
        (*env)->DeleteLocalRef(env, frame_data);

        // Update timestamp for next frame
        timestamp_ms += delay_array[i];
    }

    // Release delays array
    (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);

    // Check if encoding failed
    if (encoding_failed) {
        WebPAnimEncoderDelete(enc);
        return NULL;
    }

    // 6. Finalize animation (add NULL frame)
    if (!WebPAnimEncoderAdd(enc, NULL, timestamp_ms, NULL)) {
        WebPAnimEncoderDelete(enc);
        return NULL;
    }

    // 7. Assemble WebP data
    WebPData webp_data;
    WebPDataInit(&webp_data);

    if (!WebPAnimEncoderAssemble(enc, &webp_data)) {
        WebPAnimEncoderDelete(enc);
        return NULL;
    }

    // 8. Create Java byte array
    jbyteArray result = (*env)->NewByteArray(env, webp_data.size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, webp_data.size, (jbyte*)webp_data.bytes);
    }

    // 9. Cleanup
    WebPDataClear(&webp_data);
    WebPAnimEncoderDelete(enc);

    return result;
}
