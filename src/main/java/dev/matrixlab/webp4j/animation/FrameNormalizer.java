package dev.matrixlab.webp4j.animation;

import dev.matrixlab.webp4j.model.FitMode;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for normalizing animation frames to a common canvas size.
 * <p>
 * When creating animated WebP from images of different sizes, all frames must have
 * identical dimensions. This class provides methods to resize and position frames
 * onto a common canvas with configurable fit strategies.
 *
 * @author MrNanko
 */
public final class FrameNormalizer {

    private FrameNormalizer() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Normalizes a list of frames for animation using default strategy.
     * <ul>
     *   <li>Target size: min width/height among frames (avoids upscaling)</li>
     *   <li>Fit: CONTAIN (preserve aspect, letterbox center)</li>
     *   <li>Background: transparent</li>
     * </ul>
     *
     * @param frames Source frames (must be non-empty)
     * @return New list of TYPE_INT_ARGB frames of identical size
     * @throws IllegalArgumentException if frames is null/empty or contains invalid images
     */
    public static List<BufferedImage> normalize(List<BufferedImage> frames) {
        return normalize(frames, null, null, FitMode.CONTAIN, false, new Color(0, 0, 0, 0));
    }

    /**
     * Normalizes frames to a common canvas with configurable strategy.
     * Returns new TYPE_INT_ARGB frames with identical dimensions.
     *
     * @param frames       Source frames (must be non-empty)
     * @param targetWidth  Target canvas width (nullable: use min width across frames)
     * @param targetHeight Target canvas height (nullable: use min height across frames)
     * @param fitMode      How to fit frames onto canvas (CONTAIN/COVER/STRETCH)
     * @param allowUpscale Whether to allow upscaling smaller images
     * @param background   Background color to fill (use transparent for alpha)
     * @return List of normalized frames (new BufferedImage instances)
     */
    public static List<BufferedImage> normalize(
            List<BufferedImage> frames,
            Integer targetWidth,
            Integer targetHeight,
            FitMode fitMode,
            boolean allowUpscale,
            Color background
    ) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }
        if (fitMode == null) fitMode = FitMode.CONTAIN;
        if (background == null) background = new Color(0, 0, 0, 0);

        // Calculate min dimensions if target size not specified
        int minW = Integer.MAX_VALUE;
        int minH = Integer.MAX_VALUE;
        boolean needMinDimensions = (targetWidth == null || targetWidth <= 0)
                || (targetHeight == null || targetHeight <= 0);

        for (BufferedImage img : frames) {
            if (img == null) throw new IllegalArgumentException("Frame image cannot be null");
            if (needMinDimensions) {
                minW = Math.min(minW, img.getWidth());
                minH = Math.min(minH, img.getHeight());
            }
        }

        int canvasW = (targetWidth != null && targetWidth > 0) ? targetWidth : minW;
        int canvasH = (targetHeight != null && targetHeight > 0) ? targetHeight : minH;
        if (canvasW <= 0 || canvasH <= 0) {
            throw new IllegalArgumentException("Invalid target canvas size");
        }

        ArrayList<BufferedImage> out = new ArrayList<>(frames.size());

        for (BufferedImage src : frames) {
            int sw = src.getWidth();
            int sh = src.getHeight();

            // Fast path: direct copy when no transformation needed
            if (sw == canvasW && sh == canvasH && fitMode != FitMode.COVER) {
                BufferedImage copy = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = copy.createGraphics();
                try {
                    g2d.drawImage(src, 0, 0, null);
                } finally {
                    g2d.dispose();
                }
                out.add(copy);
                continue;
            }

            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = canvas.createGraphics();
            try {
                // Fill background
                g2d.setComposite(AlphaComposite.Src);
                g2d.setColor(background);
                g2d.fillRect(0, 0, canvasW, canvasH);

                // Set high-quality rendering hints
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int dw, dh, dx, dy;
                switch (fitMode) {
                    case COVER: {
                        double scale = Math.max((double) canvasW / sw, (double) canvasH / sh);
                        if (!allowUpscale) scale = Math.min(1.0, scale);
                        dw = Math.max(1, (int) Math.round(sw * scale));
                        dh = Math.max(1, (int) Math.round(sh * scale));
                        dx = (canvasW - dw) / 2;
                        dy = (canvasH - dh) / 2;
                        break;
                    }
                    case STRETCH: {
                        if (allowUpscale) {
                            dw = canvasW;
                            dh = canvasH;
                            dx = 0;
                            dy = 0;
                        } else {
                            dw = Math.min(canvasW, sw);
                            dh = Math.min(canvasH, sh);
                            dx = (canvasW - dw) / 2;
                            dy = (canvasH - dh) / 2;
                        }
                        break;
                    }
                    case CONTAIN:
                    default: {
                        double scale = Math.min((double) canvasW / sw, (double) canvasH / sh);
                        if (!allowUpscale) scale = Math.min(1.0, scale);
                        dw = Math.max(1, (int) Math.round(sw * scale));
                        dh = Math.max(1, (int) Math.round(sh * scale));
                        dx = (canvasW - dw) / 2;
                        dy = (canvasH - dh) / 2;
                        break;
                    }
                }

                g2d.drawImage(src, dx, dy, dw, dh, null);
            } finally {
                g2d.dispose();
            }

            out.add(canvas);
        }

        return out;
    }
}