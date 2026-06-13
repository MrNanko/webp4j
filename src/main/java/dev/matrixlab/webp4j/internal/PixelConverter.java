package dev.matrixlab.webp4j.internal;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

/**
 * Internal utility class bridging BufferedImage and the packed ARGB int[] arrays
 * consumed by the native layer.
 * <p>
 * Pixels are exchanged as 0xAARRGGBB ints — on little-endian platforms (all
 * supported targets) this is BGRA byte order, which the native layer feeds
 * directly to libwebp's BGRA/BGRX entry points. For {@code TYPE_INT_ARGB} and
 * {@code TYPE_INT_RGB} images with a simple raster layout this means the
 * image's backing array crosses the JNI boundary without any conversion copy.
 *
 * @author MrNanko
 */
public final class PixelConverter {

    private PixelConverter() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Returns the image's pixels as packed ARGB ints (0xAARRGGBB).
     * <p>
     * When the image is {@code TYPE_INT_ARGB} (hasAlpha) or {@code TYPE_INT_RGB}
     * (!hasAlpha) with a contiguous, untranslated raster, the image's live backing
     * array is returned directly — zero-copy. Callers must treat the returned
     * array as read-only; mutating it would corrupt the source image.
     * <p>
     * All other layouts (BGR variants, byte-interleaved, premultiplied, subimages,
     * custom types) are normalized with a single Java2D blit.
     *
     * @param image    The source image.
     * @param hasAlpha Whether the alpha channel must be preserved. When false,
     *                 the alpha byte of the returned ints is undefined.
     * @return Packed ARGB pixels, length {@code width * height}.
     */
    public static int[] toArgbPixels(BufferedImage image, boolean hasAlpha) {
        int expectedType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (image.getType() == expectedType) {
            int[] backing = backingArrayOrNull(image);
            if (backing != null) {
                return backing;
            }
        }
        return normalize(image, hasAlpha);
    }

    /**
     * Wraps a packed ARGB int[] as a BufferedImage without copying.
     * <p>
     * The returned image shares {@code pixels} as its live backing store and
     * reports {@code TYPE_INT_ARGB} (hasAlpha) or {@code TYPE_INT_RGB} (!hasAlpha).
     *
     * @param pixels Packed ARGB pixels, length {@code width * height}.
     * @param width  Image width.
     * @param height Image height.
     * @param hasAlpha Whether the image should expose an alpha channel.
     * @return A BufferedImage backed directly by {@code pixels}.
     */
    public static BufferedImage wrapPixels(int[] pixels, int width, int height, boolean hasAlpha) {
        if (pixels.length != width * height) {
            throw new IllegalArgumentException(
                    "Pixel array length " + pixels.length + " does not match " + width + "x" + height);
        }

        DirectColorModel colorModel = hasAlpha
                ? new DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000)
                : new DirectColorModel(24, 0x00ff0000, 0x0000ff00, 0x000000ff);
        DataBufferInt dataBuffer = new DataBufferInt(pixels, pixels.length);
        WritableRaster raster = Raster.createPackedRaster(
                dataBuffer, width, height, width, colorModel.getMasks(), null);
        return new BufferedImage(colorModel, raster, false, null);
    }

    /**
     * Returns the image's live BGR byte backing when it is a plain
     * {@code TYPE_3BYTE_BGR} raster — ImageIO's most common output for JPEG
     * and opaque PNG — so it can be fed to libwebp's BGR import without any
     * conversion; otherwise null.
     * <p>
     * Same read-only contract as {@link #toArgbPixels}: the returned array is
     * the image's live storage and must never be modified.
     */
    public static byte[] bgrPixelsOrNull(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            return null;
        }
        WritableRaster raster = image.getRaster();
        if (raster.getSampleModelTranslateX() != 0 || raster.getSampleModelTranslateY() != 0) {
            return null;
        }
        if (!(raster.getSampleModel() instanceof PixelInterleavedSampleModel)) {
            return null;
        }
        PixelInterleavedSampleModel sampleModel = (PixelInterleavedSampleModel) raster.getSampleModel();
        int width = image.getWidth();
        int height = image.getHeight();
        if (sampleModel.getPixelStride() != 3 || sampleModel.getScanlineStride() != width * 3
                || sampleModel.getWidth() != width || sampleModel.getHeight() != height) {
            return null;
        }
        // Band order must be memory order B,G,R (red sample at byte offset 2)
        int[] bandOffsets = sampleModel.getBandOffsets();
        if (bandOffsets.length != 3 || bandOffsets[0] != 2 || bandOffsets[1] != 1 || bandOffsets[2] != 0) {
            return null;
        }
        DataBuffer dataBuffer = raster.getDataBuffer();
        if (!(dataBuffer instanceof DataBufferByte)) {
            return null;
        }
        DataBufferByte byteBuffer = (DataBufferByte) dataBuffer;
        if (byteBuffer.getNumBanks() != 1 || byteBuffer.getOffset() != 0
                || byteBuffer.getSize() != width * height * 3) {
            return null;
        }
        return byteBuffer.getData();
    }

    /**
     * Returns the image's backing int[] when the raster is a plain, contiguous,
     * untranslated view of a single-bank DataBufferInt; otherwise null.
     * <p>
     * Every guard here is load-bearing: {@code BufferedImage.getSubimage()} shares
     * the parent's DataBuffer with a translated raster and a non-zero offset, and
     * reading its raw array as if it were the full image would silently encode
     * the wrong pixels.
     */
    private static int[] backingArrayOrNull(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        if (raster.getSampleModelTranslateX() != 0 || raster.getSampleModelTranslateY() != 0) {
            return null;
        }
        if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel)) {
            return null;
        }
        SinglePixelPackedSampleModel sampleModel = (SinglePixelPackedSampleModel) raster.getSampleModel();
        int width = image.getWidth();
        int height = image.getHeight();
        if (sampleModel.getScanlineStride() != width
                || sampleModel.getWidth() != width || sampleModel.getHeight() != height) {
            return null;
        }
        DataBuffer dataBuffer = raster.getDataBuffer();
        if (!(dataBuffer instanceof DataBufferInt)) {
            return null;
        }
        DataBufferInt intBuffer = (DataBufferInt) dataBuffer;
        if (intBuffer.getNumBanks() != 1 || intBuffer.getOffset() != 0
                || intBuffer.getSize() != width * height) {
            return null;
        }
        return intBuffer.getData();
    }

    /**
     * Normalizes any image to packed ARGB ints with a single Java2D blit.
     * Handles every source layout (BGR orders, byte rasters, premultiplied
     * alpha, subimages, custom color models) in one optimized native pass.
     */
    private static int[] normalize(BufferedImage image, boolean hasAlpha) {
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage normalized = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D g2d = normalized.createGraphics();
        try {
            g2d.setComposite(AlphaComposite.Src);
            g2d.drawImage(image, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return ((DataBufferInt) normalized.getRaster().getDataBuffer()).getData();
    }
}
