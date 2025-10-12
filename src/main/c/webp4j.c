#include <jni.h>
#include <stdlib.h>
#include <webp/encode.h>
#include <webp/decode.h>
#include "dev_matrixlab_webp4j_NativeWebP.h"

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

