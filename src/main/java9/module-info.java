/**
 * WebP4j core module.
 *
 * <p>This module descriptor is compiled into a Multi-Release JAR under
 * {@code META-INF/versions/9/}, so the artifact remains a plain classpath JAR
 * for Java 8 consumers while being recognized as a named module
 * ({@code dev.matrixlab.webp4j.core}) on Java 9+.
 *
 * <p>The {@code dev.matrixlab.webp4j.internal} package is intentionally not
 * exported: it contains implementation details such as the native library
 * loader and is not part of the public API.
 */
module dev.matrixlab.webp4j.core {
    exports dev.matrixlab.webp4j;
    exports dev.matrixlab.webp4j.animation;
    exports dev.matrixlab.webp4j.batch;
    exports dev.matrixlab.webp4j.exception;
    exports dev.matrixlab.webp4j.gif;
    exports dev.matrixlab.webp4j.model;
}