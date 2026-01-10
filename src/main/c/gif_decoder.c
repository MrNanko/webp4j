#include "gif_decoder.h"
#include <gif_lib.h>
#include <stdlib.h>
#include <string.h>

// Memory-based GIF reader state
typedef struct {
    const uint8_t* data;
    size_t size;
    size_t offset;
} GifMemoryReader;

// Read callback for giflib
static int ReadFromMemory(GifFileType* gif, GifByteType* buf, int size) {
    GifMemoryReader* reader = (GifMemoryReader*)gif->UserData;
    if (reader->offset + size > reader->size) {
        size = (int)(reader->size - reader->offset);
    }
    if (size > 0) {
        memcpy(buf, reader->data + reader->offset, size);
        reader->offset += size;
    }
    return size;
}

// Get background color from GIF global color map
static uint32_t GetBackgroundColor(GifFileType* gif) {
    if (gif->SColorMap == NULL) {
        return 0x00000000;  // Transparent black
    }

    int bg_index = gif->SBackGroundColor;
    if (bg_index >= gif->SColorMap->ColorCount) {
        return 0x00000000;
    }

    GifColorType* color = &gif->SColorMap->Colors[bg_index];
    // Return RGB color with alpha=0 to preserve transparency
    return (color->Red << 16) | (color->Green << 8) | color->Blue;
}

// Convert palette index to RGBA color
static void GetRGBA(GifFileType* gif, ColorMapObject* cmap, int index,
                    int transparent_index, uint8_t* rgba) {
    if (index == transparent_index) {
        // Transparent pixel
        rgba[0] = 0;
        rgba[1] = 0;
        rgba[2] = 0;
        rgba[3] = 0;
    } else if (cmap != NULL && index < cmap->ColorCount) {
        GifColorType* color = &cmap->Colors[index];
        rgba[0] = color->Red;
        rgba[1] = color->Green;
        rgba[2] = color->Blue;
        rgba[3] = 255;  // Opaque
    } else {
        // Invalid index, use black
        rgba[0] = 0;
        rgba[1] = 0;
        rgba[2] = 0;
        rgba[3] = 255;
    }
}

// Fill canvas with background color
static void ClearCanvas(uint8_t* canvas, int width, int height, uint32_t bgcolor) {
    uint8_t r = (bgcolor >> 16) & 0xFF;
    uint8_t g = (bgcolor >> 8) & 0xFF;
    uint8_t b = bgcolor & 0xFF;
    uint8_t a = (bgcolor >> 24) & 0xFF;

    for (int i = 0; i < width * height; i++) {
        canvas[i * 4 + 0] = r;
        canvas[i * 4 + 1] = g;
        canvas[i * 4 + 2] = b;
        canvas[i * 4 + 3] = a;
    }
}

// Copy canvas region
static void CopyCanvas(uint8_t* dst, const uint8_t* src, int width, int height) {
    memcpy(dst, src, width * height * 4);
}

// Render GIF frame onto canvas
static void RenderFrame(GifFileType* gif, GifImageDesc* image_desc,
                        uint8_t* raster, int transparent_index,
                        uint8_t* canvas, int canvas_width, int canvas_height) {
    ColorMapObject* cmap = image_desc->ColorMap ? image_desc->ColorMap : gif->SColorMap;
    int left = image_desc->Left;
    int top = image_desc->Top;
    int width = image_desc->Width;
    int height = image_desc->Height;

    // Clip to canvas bounds
    if (left < 0 || top < 0 || left + width > canvas_width || top + height > canvas_height) {
        if (left < 0) { width += left; left = 0; }
        if (top < 0) { height += top; top = 0; }
        if (left + width > canvas_width) width = canvas_width - left;
        if (top + height > canvas_height) height = canvas_height - top;
    }

    if (width <= 0 || height <= 0) return;

    // Render pixels
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int raster_index = y * image_desc->Width + x;
            int canvas_index = ((top + y) * canvas_width + (left + x)) * 4;

            uint8_t rgba[4];
            GetRGBA(gif, cmap, raster[raster_index], transparent_index, rgba);

            // Only draw if not transparent
            if (rgba[3] != 0) {
                canvas[canvas_index + 0] = rgba[0];
                canvas[canvas_index + 1] = rgba[1];
                canvas[canvas_index + 2] = rgba[2];
                canvas[canvas_index + 3] = rgba[3];
            }
        }
    }
}

GifDecodeResult* DecodeGifFromMemory(const uint8_t* data, size_t data_size) {
    if (data == NULL || data_size == 0) {
        return NULL;
    }

    // Open GIF from memory
    GifMemoryReader reader = {data, data_size, 0};
    int error_code;
    GifFileType* gif = DGifOpen(&reader, ReadFromMemory, &error_code);
    if (gif == NULL) {
        return NULL;
    }

    // Allocate result structure
    GifDecodeResult* result = (GifDecodeResult*)calloc(1, sizeof(GifDecodeResult));
    if (result == NULL) {
        DGifCloseFile(gif, NULL);
        return NULL;
    }

    result->canvas_width = gif->SWidth;
    result->canvas_height = gif->SHeight;
    result->bgcolor = GetBackgroundColor(gif);
    result->loop_count = 0;  // Default: infinite
    result->has_transparency = 0;

    // Allocate canvas buffers
    int canvas_size = result->canvas_width * result->canvas_height * 4;
    uint8_t* canvas = (uint8_t*)malloc(canvas_size);
    uint8_t* prev_canvas = (uint8_t*)malloc(canvas_size);

    if (canvas == NULL || prev_canvas == NULL) {
        free(canvas);
        free(prev_canvas);
        free(result);
        DGifCloseFile(gif, NULL);
        return NULL;
    }

    ClearCanvas(canvas, result->canvas_width, result->canvas_height, result->bgcolor);

    // Count frames first
    int estimated_frame_count = 0;
    GifRecordType record_type;
    do {
        if (DGifGetRecordType(gif, &record_type) == GIF_ERROR) {
            break;
        }

        if (record_type == IMAGE_DESC_RECORD_TYPE) {
            estimated_frame_count++;
            GifImageDesc image_desc;
            if (DGifGetImageDesc(gif) == GIF_ERROR) {
                break;
            }
            // Skip raster data
            uint8_t* raster = (uint8_t*)malloc(gif->Image.Width * gif->Image.Height);
            if (raster) {
                DGifGetLine(gif, raster, gif->Image.Width * gif->Image.Height);
                free(raster);
            }
        } else if (record_type == EXTENSION_RECORD_TYPE) {
            int ext_code;
            GifByteType* extension;
            if (DGifGetExtension(gif, &ext_code, &extension) != GIF_ERROR) {
                while (extension != NULL) {
                    DGifGetExtensionNext(gif, &extension);
                }
            }
        }
    } while (record_type != TERMINATE_RECORD_TYPE);

    // Reopen GIF
    DGifCloseFile(gif, NULL);
    reader.offset = 0;
    gif = DGifOpen(&reader, ReadFromMemory, &error_code);
    if (gif == NULL) {
        free(canvas);
        free(prev_canvas);
        free(result);
        return NULL;
    }

    // Allocate frame array
    result->frames = (GifFrame*)calloc(estimated_frame_count, sizeof(GifFrame));
    if (result->frames == NULL) {
        free(canvas);
        free(prev_canvas);
        free(result);
        DGifCloseFile(gif, NULL);
        return NULL;
    }

    // Decode frames
    int transparent_index = -1;
    int disposal_method = 0;
    int delay_ms = 100;  // Default 100ms

    do {
        if (DGifGetRecordType(gif, &record_type) == GIF_ERROR) {
            break;
        }

        if (record_type == IMAGE_DESC_RECORD_TYPE) {
            // Save previous canvas for RESTORE_PREVIOUS disposal
            CopyCanvas(prev_canvas, canvas, result->canvas_width, result->canvas_height);

            // Get image descriptor
            if (DGifGetImageDesc(gif) == GIF_ERROR) {
                break;
            }

            // Allocate raster buffer
            int raster_size = gif->Image.Width * gif->Image.Height;
            uint8_t* raster = (uint8_t*)malloc(raster_size);
            if (raster == NULL) {
                break;
            }

            // Read raster data
            if (DGifGetLine(gif, raster, raster_size) == GIF_ERROR) {
                free(raster);
                break;
            }

            // Render frame onto canvas
            RenderFrame(gif, &gif->Image, raster, transparent_index,
                       canvas, result->canvas_width, result->canvas_height);
            free(raster);

            // Create frame
            GifFrame* frame = &result->frames[result->frame_count];
            frame->width = result->canvas_width;
            frame->height = result->canvas_height;
            frame->duration_ms = delay_ms;
            frame->x_offset = 0;
            frame->y_offset = 0;
            frame->rgba_data = (uint8_t*)malloc(canvas_size);

            if (frame->rgba_data == NULL) {
                break;
            }

            CopyCanvas(frame->rgba_data, canvas, result->canvas_width, result->canvas_height);
            result->frame_count++;

            // Apply disposal method for next frame
            if (disposal_method == 2) {
                // DISPOSE_BACKGROUND: clear to background
                ClearCanvas(canvas, result->canvas_width, result->canvas_height, result->bgcolor);
            } else if (disposal_method == 3) {
                // DISPOSE_PREVIOUS: restore previous canvas
                CopyCanvas(canvas, prev_canvas, result->canvas_width, result->canvas_height);
            }
            // DISPOSE_NONE (0,1): keep current canvas

            // Reset for next frame
            transparent_index = -1;
            disposal_method = 0;
            delay_ms = 100;

        } else if (record_type == EXTENSION_RECORD_TYPE) {
            int ext_code;
            GifByteType* extension;

            if (DGifGetExtension(gif, &ext_code, &extension) == GIF_ERROR) {
                break;
            }

            // Graphic Control Extension
            if (ext_code == GRAPHICS_EXT_FUNC_CODE && extension[0] >= 4) {
                int flags = extension[1];
                delay_ms = (extension[3] << 8) | extension[2];
                delay_ms *= 10;  // Convert from 1/100s to ms
                if (delay_ms == 0) delay_ms = 100;  // Default

                disposal_method = (flags >> 2) & 0x07;

                if (flags & 0x01) {  // Transparent color flag
                    transparent_index = extension[4];
                    result->has_transparency = 1;
                }
            }

            // Application Extension (NETSCAPE2.0 for loop count)
            if (ext_code == APPLICATION_EXT_FUNC_CODE && extension[0] >= 11) {
                if (memcmp(extension + 1, "NETSCAPE2.0", 11) == 0) {
                    if (DGifGetExtensionNext(gif, &extension) != GIF_ERROR && extension && extension[0] >= 3) {
                        // extension[1] is sub-block ID (should be 1)
                        // extension[2] is loop count low byte
                        // extension[3] is loop count high byte
                        // Reference: https://chromium.googlesource.com/webm/libwebp/%2B/0.3.0/examples/gif2webp.c#398
                        result->loop_count = extension[2] | (extension[3] << 8);
                    }
                }
            }

            // Skip remaining extension blocks
            while (extension != NULL) {
                if (DGifGetExtensionNext(gif, &extension) == GIF_ERROR) {
                    break;
                }
            }
        }
    } while (record_type != TERMINATE_RECORD_TYPE);

    free(canvas);
    free(prev_canvas);
    DGifCloseFile(gif, NULL);

    if (result->frame_count == 0) {
        free(result->frames);
        free(result);
        return NULL;
    }

    return result;
}

GifFrame* DecodeGifFirstFrame(const uint8_t* data, size_t data_size,
                               int* canvas_width, int* canvas_height) {
    GifDecodeResult* full_result = DecodeGifFromMemory(data, data_size);
    if (full_result == NULL || full_result->frame_count == 0) {
        if (full_result) FreeGifDecodeResult(full_result);
        return NULL;
    }

    // Extract first frame
    GifFrame* first_frame = (GifFrame*)malloc(sizeof(GifFrame));
    if (first_frame == NULL) {
        FreeGifDecodeResult(full_result);
        return NULL;
    }

    *first_frame = full_result->frames[0];
    *canvas_width = full_result->canvas_width;
    *canvas_height = full_result->canvas_height;

    // Prevent double-free by nulling out the rgba_data pointer
    full_result->frames[0].rgba_data = NULL;
    FreeGifDecodeResult(full_result);

    return first_frame;
}

int GetGifInfo(const uint8_t* data, size_t data_size,
               int* width, int* height, int* frame_count,
               int* loop_count, int* has_transparency) {
    if (data == NULL || data_size == 0) {
        return 0;
    }

    // Open GIF from memory
    GifMemoryReader reader = {data, data_size, 0};
    int error_code;
    GifFileType* gif = DGifOpen(&reader, ReadFromMemory, &error_code);
    if (gif == NULL) {
        return 0;
    }

    *width = gif->SWidth;
    *height = gif->SHeight;
    *frame_count = 0;
    *loop_count = 0;
    *has_transparency = 0;

    // Scan through GIF records
    GifRecordType record_type;
    do {
        if (DGifGetRecordType(gif, &record_type) == GIF_ERROR) {
            break;
        }

        if (record_type == IMAGE_DESC_RECORD_TYPE) {
            (*frame_count)++;

            if (DGifGetImageDesc(gif) == GIF_ERROR) {
                break;
            }

            // Skip raster data
            uint8_t* raster = (uint8_t*)malloc(gif->Image.Width * gif->Image.Height);
            if (raster) {
                DGifGetLine(gif, raster, gif->Image.Width * gif->Image.Height);
                free(raster);
            }

        } else if (record_type == EXTENSION_RECORD_TYPE) {
            int ext_code;
            GifByteType* extension;

            if (DGifGetExtension(gif, &ext_code, &extension) == GIF_ERROR) {
                break;
            }

            // Check for transparency
            if (ext_code == GRAPHICS_EXT_FUNC_CODE && extension[0] >= 4) {
                int flags = extension[1];
                if (flags & 0x01) {
                    *has_transparency = 1;
                }
            }

            // Check for loop count
            if (ext_code == APPLICATION_EXT_FUNC_CODE && extension[0] >= 11) {
                if (memcmp(extension + 1, "NETSCAPE2.0", 11) == 0) {
                    if (DGifGetExtensionNext(gif, &extension) != GIF_ERROR && extension && extension[0] >= 3) {
                        // extension[1] is sub-block ID (should be 1)
                        // extension[2] is loop count low byte
                        // extension[3] is loop count high byte
                        // Reference: https://chromium.googlesource.com/webm/libwebp/%2B/0.3.0/examples/gif2webp.c#398
                        *loop_count = extension[2] | (extension[3] << 8);
                    }
                }
            }

            while (extension != NULL) {
                if (DGifGetExtensionNext(gif, &extension) == GIF_ERROR) {
                    break;
                }
            }
        }
    } while (record_type != TERMINATE_RECORD_TYPE);

    DGifCloseFile(gif, NULL);
    return 1;
}

void FreeGifDecodeResult(GifDecodeResult* result) {
    if (result == NULL) {
        return;
    }

    if (result->frames) {
        for (int i = 0; i < result->frame_count; i++) {
            free(result->frames[i].rgba_data);
        }
        free(result->frames);
    }

    free(result);
}

void FreeGifFrame(GifFrame* frame) {
    if (frame == NULL) {
        return;
    }

    free(frame->rgba_data);
    free(frame);
}