package dev.matrixlab.webp4j.model;

/**
 * Defines how frames should be fitted onto a target canvas when normalizing frames for animation.
 * <p>
 * These modes control how source frames of different dimensions are scaled and positioned
 * onto a uniform canvas size, similar to CSS object-fit behavior.
 *
 * @author MrNanko
 * @since 2026/1/13 19:45
 */
public enum FitMode {
    /**
     * Contain mode: Scale the frame to fit entirely within the canvas while preserving aspect ratio.
     * <p>
     * The entire source frame will be visible on the canvas. If the aspect ratios don't match,
     * letterboxing (black bars or transparent areas) will appear on the sides or top/bottom.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Scales down to fit if source is larger than canvas</li>
     *   <li>Does not scale up by default unless allowUpscale is true</li>
     *   <li>Centers the frame on the canvas</li>
     *   <li>Preserves aspect ratio - no distortion</li>
     * </ul>
     * <p>
     * Use case: When you want to ensure the entire frame is visible without cropping.
     * <p>
     * Similar to CSS: {@code object-fit: contain}
     */
    CONTAIN,

    /**
     * Cover mode: Scale the frame to completely fill the canvas while preserving aspect ratio.
     * <p>
     * The canvas will be entirely covered by the frame. If the aspect ratios don't match,
     * parts of the source frame will be cropped off.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Scales to cover the entire canvas</li>
     *   <li>May crop parts of the frame that extend beyond the canvas</li>
     *   <li>Centers the frame on the canvas</li>
     *   <li>Preserves aspect ratio - no distortion</li>
     * </ul>
     * <p>
     * Use case: When you want to fill the entire canvas without empty space, accepting potential cropping.
     * <p>
     * Similar to CSS: {@code object-fit: cover}
     */
    COVER,

    /**
     * Stretch mode: Stretch the frame to exactly match the canvas dimensions.
     * <p>
     * The frame will be stretched or compressed to fill the entire canvas,
     * potentially distorting the aspect ratio.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Stretches to fill canvas width and height exactly</li>
     *   <li>If allowUpscale is false, limits to source size and centers</li>
     *   <li>Does NOT preserve aspect ratio - may cause distortion</li>
     *   <li>No cropping, no letterboxing</li>
     * </ul>
     * <p>
     * Use case: When exact canvas coverage is more important than aspect ratio preservation.
     * <p>
     * Similar to CSS: {@code object-fit: fill}
     */
    STRETCH
}
