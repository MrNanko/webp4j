#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <webp/encode.h>
#include <webp/decode.h>
#include <webp/mux.h>
#include <webp/demux.h>
#include "dev_matrixlab_webp4j_internal_NativeWebP.h"

#ifdef HAVE_GIFLIB
#include "gif_decoder.h"
#endif

/*
 * Pixels cross the JNI boundary as Java ARGB ints (0xAARRGGBB). This binding
 * requires a little-endian target so that those ints are BGRA in byte order,
 * matching libwebp's WebPPictureImportBGRA/BGRX, WebPDecodeBGRAInto and
 * MODE_BGRA entry points with no conversion pass. All release platforms
 * (win/mac/linux, x64/aarch64) are little-endian; there is no big-endian path.
 */
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__)
#error "webp4j requires a little-endian target (Java ARGB int == BGRA bytes)"
#endif

/*
 * Critical-section rule: between GetPrimitiveArrayCritical and the matching
 * ReleasePrimitiveArrayCritical there must be NO other JNI calls and no
 * unbounded blocking, and every early-return path must release in reverse
 * acquisition order. Long-running work (WebPEncode, WebPAnimEncoderAdd, the
 * giflib scan) is kept outside critical sections; the one exception is
 * decodeInto, where decoding directly into the pinned Java array IS the
 * zero-copy.
 */

/* ---------------------------------------------------------------------------
 * Cached classes and field IDs (initialized once in JNI_OnLoad).
 * The global class refs keep the jfieldIDs valid for the library's lifetime.
 * ------------------------------------------------------------------------- */

static jclass g_features_class;        /* dev.matrixlab.webp4j.model.WebPBitstreamFeatures */
static jfieldID g_features_width;
static jfieldID g_features_height;
static jfieldID g_features_has_alpha;
static jfieldID g_features_has_animation;
static jfieldID g_features_format;

static jclass g_anim_info_class;       /* dev.matrixlab.webp4j.model.AnimationInfo */
static jfieldID g_anim_info_width;
static jfieldID g_anim_info_height;
static jfieldID g_anim_info_frame_count;
static jfieldID g_anim_info_loop_count;
static jfieldID g_anim_info_has_transparency;

static jclass g_anim_data_class;       /* dev.matrixlab.webp4j.model.AnimatedWebPData */
static jfieldID g_anim_data_canvas_width;
static jfieldID g_anim_data_canvas_height;
static jfieldID g_anim_data_loop_count;
static jfieldID g_anim_data_bgcolor;
static jfieldID g_anim_data_frame_count;
static jfieldID g_anim_data_frame_pixels;
static jfieldID g_anim_data_timestamps;

static jclass g_int_array_class;       /* int[] */

static jclass CacheClass(JNIEnv* env, const char* name) {
    jclass local = (*env)->FindClass(env, name);
    if (local == NULL) {
        return NULL;
    }
    jclass global = (jclass)(*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return global;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    g_features_class = CacheClass(env, "dev/matrixlab/webp4j/model/WebPBitstreamFeatures");
    g_anim_info_class = CacheClass(env, "dev/matrixlab/webp4j/model/AnimationInfo");
    g_anim_data_class = CacheClass(env, "dev/matrixlab/webp4j/model/AnimatedWebPData");
    g_int_array_class = CacheClass(env, "[I");
    if (g_features_class == NULL || g_anim_info_class == NULL ||
        g_anim_data_class == NULL || g_int_array_class == NULL) {
        return JNI_ERR;
    }

    g_features_width         = (*env)->GetFieldID(env, g_features_class, "width", "I");
    g_features_height        = (*env)->GetFieldID(env, g_features_class, "height", "I");
    g_features_has_alpha     = (*env)->GetFieldID(env, g_features_class, "hasAlpha", "Z");
    g_features_has_animation = (*env)->GetFieldID(env, g_features_class, "hasAnimation", "Z");
    g_features_format        = (*env)->GetFieldID(env, g_features_class, "format", "I");

    g_anim_info_width            = (*env)->GetFieldID(env, g_anim_info_class, "width", "I");
    g_anim_info_height           = (*env)->GetFieldID(env, g_anim_info_class, "height", "I");
    g_anim_info_frame_count      = (*env)->GetFieldID(env, g_anim_info_class, "frameCount", "I");
    g_anim_info_loop_count       = (*env)->GetFieldID(env, g_anim_info_class, "loopCount", "I");
    g_anim_info_has_transparency = (*env)->GetFieldID(env, g_anim_info_class, "hasTransparency", "Z");

    g_anim_data_canvas_width  = (*env)->GetFieldID(env, g_anim_data_class, "canvasWidth", "I");
    g_anim_data_canvas_height = (*env)->GetFieldID(env, g_anim_data_class, "canvasHeight", "I");
    g_anim_data_loop_count    = (*env)->GetFieldID(env, g_anim_data_class, "loopCount", "I");
    g_anim_data_bgcolor       = (*env)->GetFieldID(env, g_anim_data_class, "bgcolor", "I");
    g_anim_data_frame_count   = (*env)->GetFieldID(env, g_anim_data_class, "frameCount", "I");
    g_anim_data_frame_pixels  = (*env)->GetFieldID(env, g_anim_data_class, "framePixels", "[[I");
    g_anim_data_timestamps    = (*env)->GetFieldID(env, g_anim_data_class, "timestamps", "[I");

    if (g_features_width == NULL || g_features_height == NULL ||
        g_features_has_alpha == NULL || g_features_has_animation == NULL ||
        g_features_format == NULL ||
        g_anim_info_width == NULL || g_anim_info_height == NULL ||
        g_anim_info_frame_count == NULL || g_anim_info_loop_count == NULL ||
        g_anim_info_has_transparency == NULL ||
        g_anim_data_canvas_width == NULL || g_anim_data_canvas_height == NULL ||
        g_anim_data_loop_count == NULL || g_anim_data_bgcolor == NULL ||
        g_anim_data_frame_count == NULL || g_anim_data_frame_pixels == NULL ||
        g_anim_data_timestamps == NULL) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        return;
    }
    if (g_features_class != NULL)  (*env)->DeleteGlobalRef(env, g_features_class);
    if (g_anim_info_class != NULL) (*env)->DeleteGlobalRef(env, g_anim_info_class);
    if (g_anim_data_class != NULL) (*env)->DeleteGlobalRef(env, g_anim_data_class);
    if (g_int_array_class != NULL) (*env)->DeleteGlobalRef(env, g_int_array_class);
}

/* ---------------------------------------------------------------------------
 * Probes
 * ------------------------------------------------------------------------- */

/*
 * Smoke test: verifies the library loaded, the JNI bindings link, and libwebp
 * is callable. Invoked from NativeWebP's static initializer.
 */
JNIEXPORT jint JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_getLibWebPVersion
  (JNIEnv *env, jclass clazz) {
    return (jint) WebPGetEncoderVersion();
}

/*
 * Retrieves width/height of a WebP image into dimensions[0..1].
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_getInfo
  (JNIEnv *env, jclass clazz, jbyteArray data, jintArray dimensions) {
    jsize data_size = (*env)->GetArrayLength(env, data);
    if ((*env)->GetArrayLength(env, dimensions) < 2) {
        return JNI_FALSE;
    }

    WebPBitstreamFeatures features;
    jbyte* webp_data = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (webp_data == NULL) {
        return JNI_FALSE;
    }
    VP8StatusCode status = WebPGetFeatures((const uint8_t*)webp_data, (size_t)data_size, &features);
    (*env)->ReleasePrimitiveArrayCritical(env, data, webp_data, JNI_ABORT);

    if (status != VP8_STATUS_OK) {
        return JNI_FALSE;
    }

    jint dims[2] = { (jint)features.width, (jint)features.height };
    (*env)->SetIntArrayRegion(env, dimensions, 0, 2, dims);
    return JNI_TRUE;
}

/*
 * Extracts WebP bitstream features into a WebPBitstreamFeatures object.
 * Returns the VP8StatusCode from WebPGetFeatures (0 = OK).
 */
JNIEXPORT jint JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_getFeatures
  (JNIEnv *env, jclass clazz, jbyteArray data, jint dataSize, jobject featuresObj) {
    WebPBitstreamFeatures features;
    jbyte* webp_data = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (webp_data == NULL) {
        return -1;
    }
    int status = WebPGetFeatures((const uint8_t*)webp_data, (size_t)dataSize, &features);
    (*env)->ReleasePrimitiveArrayCritical(env, data, webp_data, JNI_ABORT);

    if (status != VP8_STATUS_OK) {
        return status;
    }

    (*env)->SetIntField(env, featuresObj, g_features_width, features.width);
    (*env)->SetIntField(env, featuresObj, g_features_height, features.height);
    (*env)->SetBooleanField(env, featuresObj, g_features_has_alpha,
                            features.has_alpha ? JNI_TRUE : JNI_FALSE);
    (*env)->SetBooleanField(env, featuresObj, g_features_has_animation,
                            features.has_animation ? JNI_TRUE : JNI_FALSE);
    (*env)->SetIntField(env, featuresObj, g_features_format, features.format);
    return status;
}

/* ---------------------------------------------------------------------------
 * Static image encode/decode
 * ------------------------------------------------------------------------- */

/*
 * Encodes packed ARGB pixels (BGRA bytes on LE) to a WebP bitstream.
 *
 * Mirrors the flow of libwebp's simple-API Encode() (picture_enc.c) so the
 * output is identical to WebPEncodeBGRA/WebPEncodeLosslessBGRA: preset
 * DEFAULT, lossless preset quality 70, pic.use_argb = lossless.
 *
 * The only work inside the critical section is the single linear import pass;
 * the expensive WebPEncode runs after the array is released, so GC is never
 * blocked during compression.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_encode
  (JNIEnv *env, jclass clazz, jintArray pixels, jint width, jint height,
   jfloat quality, jboolean lossless, jboolean hasAlpha) {
    if (width <= 0 || height <= 0 ||
        (jlong)(*env)->GetArrayLength(env, pixels) != (jlong)width * height) {
        return NULL;
    }

    WebPConfig config;
    if (!WebPConfigPreset(&config, WEBP_PRESET_DEFAULT, lossless ? 70.f : quality)) {
        return NULL;
    }
    config.lossless = lossless ? 1 : 0;

    WebPPicture pic;
    if (!WebPPictureInit(&pic)) {
        return NULL;
    }
    pic.use_argb = lossless ? 1 : 0;
    pic.width = width;
    pic.height = height;

    WebPMemoryWriter wrt;
    WebPMemoryWriterInit(&wrt);
    pic.writer = WebPMemoryWrite;
    pic.custom_ptr = &wrt;

    jint* p = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    int ok = 0;
    if (p != NULL) {
        ok = hasAlpha
            ? WebPPictureImportBGRA(&pic, (const uint8_t*)p, width * 4)
            : WebPPictureImportBGRX(&pic, (const uint8_t*)p, width * 4);
        (*env)->ReleasePrimitiveArrayCritical(env, pixels, p, JNI_ABORT);
    }

    ok = ok && WebPEncode(&config, &pic);
    WebPPictureFree(&pic);

    jbyteArray result = NULL;
    if (ok && wrt.size > 0) {
        result = (*env)->NewByteArray(env, (jsize)wrt.size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, (jsize)wrt.size, (const jbyte*)wrt.mem);
        }
    }
    WebPMemoryWriterClear(&wrt);
    return result;
}

/*
 * Decodes a WebP bitstream directly into a packed ARGB int[].
 *
 * Zero-copy: libwebp writes BGRA bytes straight into the pinned Java array,
 * which is typically the backing store of the BufferedImage being returned.
 * Both arrays stay pinned across the whole decode — the accepted cost; for
 * extreme image sizes this can delay GC for the duration of the decode.
 * Nested criticals are legal here because no other JNI call happens between
 * acquisition and release.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_decodeInto
  (JNIEnv *env, jclass clazz, jbyteArray data, jintArray output, jint outputStride) {
    jsize data_size = (*env)->GetArrayLength(env, data);
    size_t output_size = (size_t)(*env)->GetArrayLength(env, output) * 4;

    jbyte* in = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (in == NULL) {
        return JNI_FALSE;
    }
    jint* out = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
    if (out == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, data, in, JNI_ABORT);
        return JNI_FALSE;
    }

    uint8_t* decoded = WebPDecodeBGRAInto((const uint8_t*)in, (size_t)data_size,
                                          (uint8_t*)out, output_size, (int)outputStride);

    (*env)->ReleasePrimitiveArrayCritical(env, output, out, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, data, in, JNI_ABORT);

    return decoded != NULL ? JNI_TRUE : JNI_FALSE;
}

/* ---------------------------------------------------------------------------
 * GIF
 * ------------------------------------------------------------------------- */

/*
 * Gets GIF metadata via giflib and populates an AnimationInfo object.
 * Returns JNI_FALSE when giflib is unavailable, triggering the Java fallback.
 *
 * Uses GetByteArrayElements (not a critical section): the giflib scan walks
 * the whole file and its duration is unbounded.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_getGifInfo
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jobject info) {
#ifdef HAVE_GIFLIB
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return JNI_FALSE;
    }

    int width, height, frame_count, loop_count, has_transparency;
    int success = GetGifInfo((const uint8_t*)gif_bytes, (size_t)data_size,
                             &width, &height, &frame_count,
                             &loop_count, &has_transparency);
    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    if (!success) {
        return JNI_FALSE;
    }

    (*env)->SetIntField(env, info, g_anim_info_width, width);
    (*env)->SetIntField(env, info, g_anim_info_height, height);
    (*env)->SetIntField(env, info, g_anim_info_frame_count, frame_count);
    (*env)->SetIntField(env, info, g_anim_info_loop_count, loop_count);
    (*env)->SetBooleanField(env, info, g_anim_info_has_transparency,
                            has_transparency ? JNI_TRUE : JNI_FALSE);
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

#ifdef HAVE_GIFLIB
/* Encodes a single canvas-sized RGBA frame as a static WebP byte array. */
static jbyteArray EncodeStaticRGBA(JNIEnv* env, const uint8_t* rgba,
                                   int width, int height,
                                   jfloat quality, jboolean lossless) {
    uint8_t* output = NULL;
    size_t output_size = lossless
        ? WebPEncodeLosslessRGBA(rgba, width, height, width * 4, &output)
        : WebPEncodeRGBA(rgba, width, height, width * 4, quality, &output);

    if (output_size == 0 || output == NULL) {
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, (jsize)output_size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)output_size, (const jbyte*)output);
    }
    WebPFree(output);
    return result;
}
#endif

/*
 * Converts GIF data to WebP using the native giflib decoder (primary path).
 * Returns NULL on any failure, triggering the Java ImageIO fallback.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_encodeGifToWebP
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jfloat quality,
   jboolean lossless, jint compressionMethod, jboolean extractFirstFrameOnly,
   jint loopCount, jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed) {
#ifdef HAVE_GIFLIB
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return NULL;
    }

    if (extractFirstFrameOnly) {
        int canvas_width, canvas_height;
        GifFrame* frame = DecodeGifFirstFrame((const uint8_t*)gif_bytes, (size_t)data_size,
                                              &canvas_width, &canvas_height);
        (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

        if (frame == NULL) {
            return NULL;
        }
        jbyteArray result = EncodeStaticRGBA(env, frame->rgba_data,
                                             canvas_width, canvas_height, quality, lossless);
        FreeGifFrame(frame);
        return result;
    }

    GifDecodeResult* gif_result = DecodeGifFromMemory((const uint8_t*)gif_bytes, (size_t)data_size);
    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    if (gif_result == NULL) {
        return NULL;
    }

    if (gif_result->frame_count == 1) {
        jbyteArray result = EncodeStaticRGBA(env, gif_result->frames[0].rgba_data,
                                             gif_result->canvas_width, gif_result->canvas_height,
                                             quality, lossless);
        FreeGifDecodeResult(gif_result);
        return result;
    }

    /* Multi-frame GIF -> animated WebP. Single cleanup tail; enc/webp_data
     * are tracked so every failure path is provably leak-free. */
    jbyteArray result = NULL;
    WebPAnimEncoder* enc = NULL;
    WebPData webp_data;
    WebPDataInit(&webp_data);

    WebPAnimEncoderOptions enc_options;
    if (!WebPAnimEncoderOptionsInit(&enc_options)) {
        goto cleanup;
    }
    enc_options.anim_params.loop_count = (loopCount == -1) ? gif_result->loop_count : loopCount;
    /* Force alpha=0 to preserve transparency in WebP output. */
    enc_options.anim_params.bgcolor = gif_result->bgcolor & 0x00FFFFFF;
    enc_options.kmin = kmin;
    enc_options.kmax = kmax;
    enc_options.minimize_size = minimizeSize ? 1 : 0;
    enc_options.allow_mixed = allowMixed ? 1 : 0;

    enc = WebPAnimEncoderNew(gif_result->canvas_width, gif_result->canvas_height, &enc_options);
    if (enc == NULL) {
        goto cleanup;
    }

    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        goto cleanup;
    }
    config.lossless = lossless ? 1 : 0;
    config.quality = quality;
    config.method = compressionMethod;
    if (!WebPValidateConfig(&config)) {
        goto cleanup;
    }

    int timestamp_ms = 0;
    for (int i = 0; i < gif_result->frame_count; i++) {
        GifFrame* frame = &gif_result->frames[i];

        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            goto cleanup;
        }
        picture.width = gif_result->canvas_width;
        picture.height = gif_result->canvas_height;
        picture.use_argb = 1;

        /* WebPPictureImportRGBA allocates the picture buffer itself. */
        if (!WebPPictureImportRGBA(&picture, frame->rgba_data, gif_result->canvas_width * 4)) {
            WebPPictureFree(&picture);
            goto cleanup;
        }

        int added = WebPAnimEncoderAdd(enc, &picture, timestamp_ms, &config);
        WebPPictureFree(&picture);
        if (!added) {
            goto cleanup;
        }
        timestamp_ms += frame->duration_ms;
    }

    /* Finalize (flush last frame) and assemble. */
    if (!WebPAnimEncoderAdd(enc, NULL, timestamp_ms, NULL) ||
        !WebPAnimEncoderAssemble(enc, &webp_data)) {
        goto cleanup;
    }

    result = (*env)->NewByteArray(env, (jsize)webp_data.size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)webp_data.size, (const jbyte*)webp_data.bytes);
    }

cleanup:
    WebPDataClear(&webp_data);
    if (enc != NULL) {
        WebPAnimEncoderDelete(enc);
    }
    FreeGifDecodeResult(gif_result);
    return result;
#else
    return NULL;
#endif
}

/* ---------------------------------------------------------------------------
 * Animated WebP
 * ------------------------------------------------------------------------- */

/*
 * Encodes an animated WebP from packed ARGB frames (int[][]).
 *
 * Per frame, only WebPPictureImportBGRA's single linear pass runs inside the
 * critical section; the heavy WebPAnimEncoderAdd runs after the frame array
 * is released. Frame arrays are read-only (JNI_ABORT) — they may be live
 * BufferedImage backing stores.
 */
JNIEXPORT jbyteArray JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_encodeAnimated
  (JNIEnv *env, jclass clazz, jobjectArray frames, jintArray delays, jint width, jint height,
   jfloat quality, jboolean lossless, jint compressionMethod, jint loopCount,
   jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed) {
    jsize frame_count = (*env)->GetArrayLength(env, frames);
    if (frame_count == 0 || width <= 0 || height <= 0 ||
        (*env)->GetArrayLength(env, delays) < frame_count) {
        return NULL;
    }
    jlong expected_pixels = (jlong)width * height;

    jbyteArray result = NULL;
    WebPAnimEncoder* enc = NULL;
    WebPData webp_data;
    WebPDataInit(&webp_data);

    jint* delay_array = (*env)->GetIntArrayElements(env, delays, NULL);
    if (delay_array == NULL) {
        return NULL;
    }

    WebPAnimEncoderOptions enc_options;
    if (!WebPAnimEncoderOptionsInit(&enc_options)) {
        goto cleanup;
    }
    enc_options.anim_params.loop_count = loopCount;
    enc_options.anim_params.bgcolor = 0x00000000;  /* Transparent background */
    enc_options.kmin = kmin;
    enc_options.kmax = kmax;
    enc_options.minimize_size = minimizeSize ? 1 : 0;
    enc_options.allow_mixed = allowMixed ? 1 : 0;

    enc = WebPAnimEncoderNew(width, height, &enc_options);
    if (enc == NULL) {
        goto cleanup;
    }

    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        goto cleanup;
    }
    config.lossless = lossless ? 1 : 0;
    config.quality = quality;
    config.method = compressionMethod;
    if (!WebPValidateConfig(&config)) {
        goto cleanup;
    }

    int timestamp_ms = 0;
    for (jsize i = 0; i < frame_count; i++) {
        jintArray frame = (jintArray)(*env)->GetObjectArrayElement(env, frames, i);
        if (frame == NULL ||
            (jlong)(*env)->GetArrayLength(env, frame) != expected_pixels) {
            if (frame != NULL) (*env)->DeleteLocalRef(env, frame);
            goto cleanup;
        }

        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            (*env)->DeleteLocalRef(env, frame);
            goto cleanup;
        }
        picture.width = width;
        picture.height = height;
        picture.use_argb = 1;

        /* WebPPictureImportBGRA allocates the picture buffer itself. */
        jint* frame_pixels = (*env)->GetPrimitiveArrayCritical(env, frame, NULL);
        int imported = 0;
        if (frame_pixels != NULL) {
            imported = WebPPictureImportBGRA(&picture, (const uint8_t*)frame_pixels, width * 4);
            (*env)->ReleasePrimitiveArrayCritical(env, frame, frame_pixels, JNI_ABORT);
        }
        (*env)->DeleteLocalRef(env, frame);

        int added = imported && WebPAnimEncoderAdd(enc, &picture, timestamp_ms, &config);
        WebPPictureFree(&picture);
        if (!added) {
            goto cleanup;
        }
        timestamp_ms += delay_array[i];
    }

    /* Finalize (flush last frame) and assemble. */
    if (!WebPAnimEncoderAdd(enc, NULL, timestamp_ms, NULL) ||
        !WebPAnimEncoderAssemble(enc, &webp_data)) {
        goto cleanup;
    }

    result = (*env)->NewByteArray(env, (jsize)webp_data.size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)webp_data.size, (const jbyte*)webp_data.bytes);
    }

cleanup:
    WebPDataClear(&webp_data);
    if (enc != NULL) {
        WebPAnimEncoderDelete(enc);
    }
    (*env)->ReleaseIntArrayElements(env, delays, delay_array, JNI_ABORT);
    return result;
}

/*
 * Decodes an animated WebP into packed ARGB frames and populates an
 * AnimatedWebPData object (framePixels, timestamps, metadata).
 *
 * The input uses GetByteArrayElements, NOT a critical section: the
 * WebPAnimDecoder retains a pointer into the buffer for its whole lifetime,
 * and the frame loop must make JNI calls (NewIntArray, SetIntArrayRegion,
 * SetObjectArrayElement) while that pointer stays valid — both are illegal
 * inside a critical section.
 *
 * The per-frame SetIntArrayRegion copy is unavoidable: the decoder owns and
 * reuses its internal frame buffer.
 */
JNIEXPORT jboolean JNICALL Java_dev_matrixlab_webp4j_internal_NativeWebP_decodeAnimated
  (JNIEnv *env, jclass clazz, jbyteArray webPData, jobject result) {
    jboolean ok = JNI_FALSE;
    WebPAnimDecoder* dec = NULL;

    jsize data_size = (*env)->GetArrayLength(env, webPData);
    jbyte* webp_bytes = (*env)->GetByteArrayElements(env, webPData, NULL);
    if (webp_bytes == NULL) {
        return JNI_FALSE;
    }

    WebPData webp_data;
    webp_data.bytes = (const uint8_t*)webp_bytes;
    webp_data.size = (size_t)data_size;

    WebPAnimDecoderOptions dec_options;
    if (!WebPAnimDecoderOptionsInit(&dec_options)) {
        goto cleanup;
    }
    /* BGRA frame buffers == Java ARGB ints on LE: frames go into int[]
     * arrays that the Java side wraps directly as TYPE_INT_ARGB images. */
    dec_options.color_mode = MODE_BGRA;

    dec = WebPAnimDecoderNew(&webp_data, &dec_options);
    if (dec == NULL) {
        goto cleanup;
    }

    WebPAnimInfo anim_info;
    if (!WebPAnimDecoderGetInfo(dec, &anim_info)) {
        goto cleanup;
    }

    uint32_t canvas_width = anim_info.canvas_width;
    uint32_t canvas_height = anim_info.canvas_height;
    uint32_t frame_count = anim_info.frame_count;
    jsize frame_pixels = (jsize)(canvas_width * canvas_height);

    jobjectArray framePixelsArray =
        (*env)->NewObjectArray(env, (jsize)frame_count, g_int_array_class, NULL);
    if (framePixelsArray == NULL) {
        goto cleanup;
    }
    jintArray timestampsArray = (*env)->NewIntArray(env, (jsize)frame_count);
    if (timestampsArray == NULL) {
        goto cleanup;
    }

    uint32_t frame_index = 0;
    while (WebPAnimDecoderHasMoreFrames(dec) && frame_index < frame_count) {
        uint8_t* buf;
        int timestamp;
        if (!WebPAnimDecoderGetNext(dec, &buf, &timestamp)) {
            goto cleanup;
        }

        jintArray frameArray = (*env)->NewIntArray(env, frame_pixels);
        if (frameArray == NULL) {
            goto cleanup;
        }
        (*env)->SetIntArrayRegion(env, frameArray, 0, frame_pixels, (const jint*)buf);
        (*env)->SetObjectArrayElement(env, framePixelsArray, (jsize)frame_index, frameArray);
        (*env)->DeleteLocalRef(env, frameArray);

        jint ts = (jint)timestamp;
        (*env)->SetIntArrayRegion(env, timestampsArray, (jsize)frame_index, 1, &ts);
        frame_index++;
    }

    (*env)->SetIntField(env, result, g_anim_data_canvas_width, (jint)canvas_width);
    (*env)->SetIntField(env, result, g_anim_data_canvas_height, (jint)canvas_height);
    (*env)->SetIntField(env, result, g_anim_data_loop_count, (jint)anim_info.loop_count);
    (*env)->SetIntField(env, result, g_anim_data_bgcolor, (jint)anim_info.bgcolor);
    (*env)->SetIntField(env, result, g_anim_data_frame_count, (jint)frame_index);
    (*env)->SetObjectField(env, result, g_anim_data_frame_pixels, framePixelsArray);
    (*env)->SetObjectField(env, result, g_anim_data_timestamps, timestampsArray);
    ok = JNI_TRUE;

cleanup:
    if (dec != NULL) {
        WebPAnimDecoderDelete(dec);
    }
    (*env)->ReleaseByteArrayElements(env, webPData, webp_bytes, JNI_ABORT);
    return ok;
}