#ifndef WEBP4J_GIF_DECODER_H_
#define WEBP4J_GIF_DECODER_H_

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Represents a single frame in a GIF animation.
 */
typedef struct {
    uint8_t* rgba_data;  // RGBA pixel data (width * height * 4 bytes)
    int width;           // Frame width
    int height;          // Frame height
    int duration_ms;     // Frame duration in milliseconds
    int x_offset;        // Frame position X offset
    int y_offset;        // Frame position Y offset
} GifFrame;

/**
 * Holds all decoded GIF data including frames and metadata.
 */
typedef struct {
    GifFrame* frames;       // Array of frames
    int frame_count;        // Number of frames
    int loop_count;         // Loop count (0=infinite, N=loop N times)
    int canvas_width;       // Canvas width (logical screen width)
    int canvas_height;      // Canvas height (logical screen height)
    uint32_t bgcolor;       // Background color in ARGB format
    int has_transparency;   // 1 if any frame has transparency, 0 otherwise
} GifDecodeResult;

/**
 * Decodes entire GIF animation from memory.
 *
 * This function decodes all frames from a GIF image stored in memory.
 * It handles palette conversion, transparency, disposal methods, and blending.
 *
 * @param data Pointer to GIF image data in memory
 * @param data_size Size of GIF data in bytes
 * @return GifDecodeResult* on success, NULL on failure
 *         Caller must free result using FreeGifDecodeResult()
 */
GifDecodeResult* DecodeGifFromMemory(const uint8_t* data, size_t data_size);

/**
 * Decodes only the first frame of a GIF (for static GIF conversion).
 *
 * This is an optimized path for converting static GIFs or extracting
 * only the first frame of an animated GIF.
 *
 * @param data Pointer to GIF image data in memory
 * @param data_size Size of GIF data in bytes
 * @param canvas_width Output parameter for canvas width
 * @param canvas_height Output parameter for canvas height
 * @return GifFrame* on success, NULL on failure
 *         Caller must free result using FreeGifFrame()
 */
GifFrame* DecodeGifFirstFrame(const uint8_t* data, size_t data_size,
                               int* canvas_width, int* canvas_height);

/**
 * Gets GIF metadata without full decode.
 *
 * This is a lightweight operation for querying GIF properties without
 * decoding pixel data.
 *
 * @param data Pointer to GIF image data in memory
 * @param data_size Size of GIF data in bytes
 * @param width Output parameter for image width
 * @param height Output parameter for image height
 * @param frame_count Output parameter for number of frames
 * @param loop_count Output parameter for loop count (0=infinite)
 * @param has_transparency Output parameter (1=has transparency, 0=none)
 * @return 1 on success, 0 on failure
 */
int GetGifInfo(const uint8_t* data, size_t data_size,
               int* width, int* height, int* frame_count,
               int* loop_count, int* has_transparency);

/**
 * Frees memory allocated by DecodeGifFromMemory().
 *
 * @param result Pointer to GifDecodeResult to free
 */
void FreeGifDecodeResult(GifDecodeResult* result);

/**
 * Frees memory allocated by DecodeGifFirstFrame().
 *
 * @param frame Pointer to GifFrame to free
 */
void FreeGifFrame(GifFrame* frame);

#ifdef __cplusplus
}
#endif

#endif  // WEBP4J_GIF_DECODER_H_