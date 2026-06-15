package dev.matrixlab.webp4j.batch;

import dev.matrixlab.webp4j.WebPCodec;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel bulk encoding/decoding of independent WebP images.
 * <p>
 * Each image in a batch is encoded or decoded independently of the others, so
 * the work fans out across a thread pool for real multi-core speedup. The heavy
 * native compression in {@link WebPCodec#encodeImage} runs after the JNI array
 * critical section is released and native methods hold no JVM-wide lock, so
 * multiple Java threads call into libwebp concurrently without contention.
 * <p>
 * Results are <strong>order-preserving</strong>: result element {@code i} always
 * corresponds to input element {@code i}, regardless of completion order.
 * <p>
 * <strong>Thread-safety contract:</strong> every input must be a distinct object
 * that is not mutated by another thread during the call. The single-image
 * methods only read each image's backing array (zero-copy), so concurrent reads
 * of separate images are safe; sharing one mutable {@link BufferedImage} across
 * the batch while it is being modified externally is not.
 * <p>
 * Methods come in two flavours: a convenience form that uses a shared internal
 * pool sized to the available processors, and a form that accepts a caller-owned
 * {@link ExecutorService} for environments that need to cap or share threads.
 * A caller-supplied executor is never shut down by this class.
 *
 * @author MrNanko
 * @since 2.4.0
 */
public final class BatchProcessor {

    private BatchProcessor() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Lazily-initialized shared pool, created on first batch call (holder idiom).
     * Threads are daemons so the pool never blocks JVM shutdown.
     */
    private static final class DefaultPool {
        static final ExecutorService INSTANCE = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "webp4j-batch-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
    }

    // ============================================
    // Encoding
    // ============================================

    /**
     * Encodes a list of images to WebP in parallel using the shared internal pool.
     *
     * @param images   Images to encode (null or empty yields an empty list)
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @return An immutable list of encoded WebP byte arrays, one per input image, in order
     * @throws IOException              If encoding any image fails (names the failing index)
     * @throws IllegalArgumentException If any image element is null
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless)
            throws IOException {
        return encodeImages(images, quality, lossless, DefaultPool.INSTANCE);
    }

    /**
     * Encodes a list of images to WebP in parallel using a caller-supplied executor.
     * <p>
     * The executor is not shut down by this method.
     *
     * @param images   Images to encode (null or empty yields an empty list)
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param executor Executor to run the encode tasks on
     * @return An immutable list of encoded WebP byte arrays, one per input image, in order
     * @throws IOException              If encoding any image fails (names the failing index)
     * @throws IllegalArgumentException If executor is null or any image element is null
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless,
                                            ExecutorService executor) throws IOException {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        requireExecutor(executor);
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i) == null) {
                throw new IllegalArgumentException("Image at index " + i + " is null");
            }
        }
        return runBatch(executor, images.size(), index -> WebPCodec.encodeImage(images.get(index), quality, lossless));
    }

    // ============================================
    // Decoding
    // ============================================

    /**
     * Decodes a list of WebP byte arrays into images in parallel using the shared internal pool.
     *
     * @param encodedImages WebP-encoded byte arrays (null or empty yields an empty list)
     * @return An immutable list of decoded images, one per input array, in order
     * @throws IOException              If decoding any array fails (names the failing index)
     * @throws IllegalArgumentException If any element is null
     */
    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException {
        return decodeImages(encodedImages, DefaultPool.INSTANCE);
    }

    /**
     * Decodes a list of WebP byte arrays into images in parallel using a caller-supplied executor.
     * <p>
     * The executor is not shut down by this method.
     *
     * @param encodedImages WebP-encoded byte arrays (null or empty yields an empty list)
     * @param executor     Executor to run the decode tasks on
     * @return An immutable list of decoded images, one per input array, in order
     * @throws IOException              If decoding any array fails (names the failing index)
     * @throws IllegalArgumentException If executor is null or any element is null
     */
    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages, ExecutorService executor)
            throws IOException {
        if (encodedImages == null || encodedImages.isEmpty()) {
            return Collections.emptyList();
        }
        requireExecutor(executor);
        for (int i = 0; i < encodedImages.size(); i++) {
            if (encodedImages.get(i) == null) {
                throw new IllegalArgumentException("Encoded image at index " + i + " is null");
            }
        }
        return runBatch(executor, encodedImages.size(), index -> WebPCodec.decodeImage(encodedImages.get(index)));
    }

    // ============================================
    // Internal
    // ============================================

    /**
     * An indexed unit of batch work, allowed to throw the checked exceptions the
     * single-image codec methods declare.
     */
    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws IOException;
    }

    /**
     * Submits {@code size} indexed tasks, then collects their results in submission
     * order so the output mirrors the input order. Fails fast: the first task to
     * throw cancels the rest and surfaces as an {@link IOException} naming the index.
     */
    private static <T> List<T> runBatch(ExecutorService executor, int size, IndexedTask<T> task) throws IOException {
        List<Future<T>> futures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int index = i;
            futures.add(executor.submit(() -> task.run(index)));
        }

        List<T> results = new ArrayList<>(size);
        int i = 0;
        try {
            for (; i < size; i++) {
                results.add(futures.get(i).get());
            }
        } catch (ExecutionException e) {
            cancelAll(futures);
            Throwable cause = e.getCause();
            throw new IOException("Batch processing failed at index " + i
                    + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""), cause);
        } catch (InterruptedException e) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw new IOException("Batch processing was interrupted at index " + i, e);
        }
        return Collections.unmodifiableList(results);
    }

    private static void cancelAll(List<? extends Future<?>> futures) {
        for (Future<?> f : futures) {
            f.cancel(true);
        }
    }

    private static void requireExecutor(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
    }
}