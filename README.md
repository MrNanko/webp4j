# WebP4j

##### 📖 English Documentation | 📖 [中文文档](README.zh-CN.md)

**WebP4j** is a Java library based on JNI (Java Native Interface) that supports WebP image encoding/decoding with both lossless and lossy compression, GIF to WebP conversion, and animated WebP creation and decoding. This project utilizes Google's [libwebp](https://developers.google.com/speed/webp) library (version 1.6.0) and exposes its functionality to Java applications.

## Features

- Supports WebP encoding of RGB and RGBA images with both lossy and lossless compression.
- Supports decoding WebP images to RGB and RGBA formats.
- **GIF to WebP conversion** with native giflib decoder and Java ImageIO fallback.
- **Animated WebP creation** from BufferedImage frames.
- **Animated WebP decoding** — extract individual frames and delays from existing animated WebP files.
- **Multi-threaded encoding** — libwebp parallelizes each frame's compression (`thread_level`) for a multi-core speedup on animated WebP and GIF→WebP (**~42% faster animated encode** on a 10-core M5, see [Performance](#performance)); output stays bit-identical. Enabled by default, toggle with `GifToWebPConfig.setMultiThreaded(...)`.
- **Frame normalization** for creating animations from images of different sizes.
- Provides efficient image compression and decompression using libwebp.
- **Zero-copy pixel pipeline** — BufferedImage rasters cross the JNI boundary without intermediate format conversion (see [Performance](#performance)).
- Compatible with multiple platforms (supports x86 and ARM).
- Compiled with **JDK 21** but targets **Java 8 bytecode** for maximum compatibility.
- Published WebP4j to Maven Central Repository.

## Prerequisites

### For Library Users

- **Java 8 or higher** - The compiled library is compatible with Java 8, 11, 17, 21, and later versions.

### For Developers (Building from Source)

If you want to build the native libraries locally, you'll need:

- **Java 21** - Required for building (JNI header generation and modern tooling)
- **CMake 3.15+** - For building native libraries
- **C Compiler** - GCC (Linux/macOS), MinGW (Windows), or Clang

**Note:** The library is compiled with Java 21 using `--release 8` flag, which generates Java 8-compatible bytecode while leveraging modern build tools. This ensures the library works on any Java 8+ runtime while maintaining compatibility.

## Supported Platforms

WebP4j supports the following platforms through automated CI/CD builds:

- **Windows**: x64 (x86-64) and ARM64 (aarch64)
- **macOS**: x64 (Intel) and arm64 (Apple Silicon)
- **Linux**: x64 (x86-64) and ARM64 (aarch64)

## Java 22+ Native Access Note

Starting from Java 22, the JVM warns when libraries load native code via JNI ([JEP 472](https://openjdk.org/jeps/472)). Since WebP4j uses JNI internally, you may see the following warning:

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by dev.matrixlab.webp4j.internal.NativeLibraryLoader in an unnamed module
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

**How to suppress the warning:**

- **Module path** (recommended): WebP4j ships as a Multi-Release JAR with a named module `dev.matrixlab.webp4j.core`. Place the JAR on the module path and use:
  ```
  --module-path webp4j-core.jar --add-modules dev.matrixlab.webp4j.core --enable-native-access=dev.matrixlab.webp4j.core
  ```
- **Classpath**: If you use the traditional classpath, the module name is not recognized. Use the blanket flag instead:
  ```
  --enable-native-access=ALL-UNNAMED
  ```

> This warning does not affect functionality — WebP4j works correctly in both cases. It is purely informational until a future Java release makes native access restrictions mandatory.

## Installation

### Maven

[![Maven Central](https://img.shields.io/maven-central/v/dev.matrixlab.webp4j/webp4j-core)](https://central.sonatype.com/artifact/dev.matrixlab.webp4j/webp4j-core)

```xml
<dependency>
    <groupId>dev.matrixlab.webp4j</groupId>
    <artifactId>webp4j-core</artifactId>
    <version>2.5.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.matrixlab.webp4j:webp4j-core:2.5.0'
```

## API Overview

### Platform Availability

```java
// Check if the current platform is supported
boolean isAvailable();
```

### Static Image Encoding/Decoding

```java
// Encoding
byte[] encodeImage(BufferedImage image, float quality) throws IOException;
byte[] encodeImage(BufferedImage image, float quality, boolean lossless) throws IOException;
byte[] encodeLosslessImage(BufferedImage image) throws IOException;

// Decoding
BufferedImage decodeImage(byte[] webPData) throws IOException;

// Info
int[] getWebPInfo(byte[] webPData) throws IOException;  // Returns [width, height]
```

### Batch Processing

```java
// Parallel bulk encode/decode (order-preserving). Independent images are
// processed concurrently across a shared internal pool for multi-core speedup.
List<byte[]>        encodeImages(List<BufferedImage> images, float quality, boolean lossless) throws IOException;
List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException;

// Need to cap or share threads? Use BatchProcessor with your own executor:
List<byte[]>        BatchProcessor.encodeImages(List<BufferedImage>, float, boolean, ExecutorService) throws IOException;
List<BufferedImage> BatchProcessor.decodeImages(List<byte[]>, ExecutorService) throws IOException;
```

### GIF to WebP Conversion

```java
// Convert GIF to WebP
byte[] encodeGifToWebP(byte[] gifData) throws IOException;
byte[] encodeGifToWebP(byte[] gifData, GifToWebPConfig config) throws IOException;
byte[] encodeGifToWebPLossless(byte[] gifData) throws IOException;

// Get GIF information
AnimationInfo getGifInfo(byte[] gifData) throws IOException;
```

### Animated WebP Creation

```java
// Create animated WebP from frames
byte[] createAnimatedWebP(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException;
```

### Animated WebP Decoding

```java
// Decode animated WebP into individual frames
AnimatedWebPData decodeAnimatedWebP(byte[] webPData) throws IOException;

// AnimatedWebPData provides:
List<AnimatedWebPFrame> getFrames();  // Each frame: BufferedImage + timestamp
int[] getDelays();                    // Per-frame delays in milliseconds
int getCanvasWidth();
int getCanvasHeight();
int getFrameCount();
int getLoopCount();
```

### Frame Normalization

```java
// Normalize frames to common size (use FrameNormalizer directly)
List<BufferedImage> normalize(List<BufferedImage> frames);
List<BufferedImage> normalize(
        List<BufferedImage> frames,
        Integer targetWidth,
        Integer targetHeight,
        FitMode fitMode,
        boolean allowUpscale,
        Color background
);
```

## Usage Examples

### Static Image Encoding

```java
// Lossy compression (recommended for JPG sources)
public void encodeToWebP() throws IOException {
    BufferedImage image = ImageIO.read(new File("input.jpg"));
    byte[] webp = WebPCodec.encodeImage(image, 75.0f);
    Files.write(Paths.get("output.webp"), webp);
}

// Lossless compression (recommended for PNG sources)
public void encodeToLosslessWebP() throws IOException {
    BufferedImage image = ImageIO.read(new File("input.png"));
    byte[] webp = WebPCodec.encodeLosslessImage(image);
    Files.write(Paths.get("output.webp"), webp);
}
```

### Static Image Decoding

```java
public void decodeFromWebP() throws IOException {
    byte[] webpData = Files.readAllBytes(Paths.get("input.webp"));
    BufferedImage image = WebPCodec.decodeImage(webpData);
    ImageIO.write(image, "png", new File("output.png"));
}
```

### Batch Processing

```java
public void encodeBatch(List<BufferedImage> images) throws IOException {
    // Encodes all images in parallel; result i corresponds to input i.
    List<byte[]> webps = WebPCodec.encodeImages(images, 75.0f, false);

    // Decode many at once the same way.
    List<BufferedImage> decoded = WebPCodec.decodeImages(webps);
}

public void encodeBatchWithOwnPool(List<BufferedImage> images, ExecutorService pool) throws IOException {
    // Reuse/cap threads by passing your own executor (never shut down by the call).
    List<byte[]> webps = BatchProcessor.encodeImages(images, 75.0f, false, pool);
}
```

### GIF to WebP Conversion

```java
// Default conversion (lossy, quality 75)
public void convertGifToWebP() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));
    byte[] webp = WebPCodec.encodeGifToWebP(gifData);
    Files.write(Paths.get("output.webp"), webp);
}

// Custom configuration
public void convertGifToWebPCustom() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));

    GifToWebPConfig config = GifToWebPConfig.createLossyConfig(90.0f)
            .setCompressionMethod(6)
            .setMinimizeSize(true);

    byte[] webp = WebPCodec.encodeGifToWebP(gifData, config);
    Files.write(Paths.get("output.webp"), webp);
}

// Lossless conversion
public void convertGifToWebPLossless() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));
    byte[] webp = WebPCodec.encodeGifToWebPLossless(gifData);
    Files.write(Paths.get("output.webp"), webp);
}
```

### Creating Animated WebP from Images

```java
public void createAnimatedWebP() throws IOException {
    // Load frames
    List<BufferedImage> frames = Arrays.asList(
        ImageIO.read(new File("frame1.png")),
        ImageIO.read(new File("frame2.png")),
        ImageIO.read(new File("frame3.png"))
    );

    // Normalize frames to same size (required for animation)
    frames = FrameNormalizer.normalize(frames);

    // Set delays (milliseconds per frame)
    int[] delays = {100, 100, 100};

    // Create animated WebP. Each frame's compression is multi-threaded by
    // default (libwebp thread_level); the output is identical to single-threaded,
    // just faster on multi-core machines. Disable with setMultiThreaded(false).
    GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
    byte[] webp = WebPCodec.createAnimatedWebP(frames, delays, config);

    Files.write(Paths.get("animated.webp"), webp);
}
```

### Decoding Animated WebP into Frames

```java
public void extractFrames() throws IOException {
    byte[] webpData = Files.readAllBytes(Paths.get("animated.webp"));
    AnimatedWebPData result = WebPCodec.decodeAnimatedWebP(webpData);

    System.out.println("Frame count: " + result.getFrameCount());
    System.out.println("Canvas size: " + result.getCanvasWidth() + "x" + result.getCanvasHeight());
    System.out.println("Loop count: " + result.getLoopCount());

    // Get per-frame delays
    int[] delays = result.getDelays();

    // Access individual frames
    for (int i = 0; i < result.getFrames().size(); i++) {
        AnimatedWebPFrame frame = result.getFrames().get(i);
        BufferedImage image = frame.getImage();
        System.out.println("Frame " + i + ": delay=" + delays[i] + "ms");

        // Save each frame as PNG
        ImageIO.write(image, "png", new File("frame_" + i + ".png"));
    }
}
```

### Advanced Frame Normalization

```java
public void normalizeWithCustomSettings() throws IOException {
    List<BufferedImage> frames = loadFrames();

    // Normalize to specific size with custom fit mode
    List<BufferedImage> normalized = FrameNormalizer.normalize(
        frames,
        800,                        // target width
        600,                        // target height
        FitMode.CONTAIN,            // preserve aspect ratio, letterbox
        false,                      // don't upscale smaller images
        new Color(0, 0, 0, 0)       // transparent background
    );

    // FitMode options:
    // - CONTAIN: Fit entire image, may have letterboxing
    // - COVER: Fill entire canvas, may crop edges
    // - STRETCH: Stretch to fill, may distort aspect ratio
}
```

## Compression Mode Guidelines

- **Lossless compression**: Recommended for PNG and other lossless image formats to preserve image quality without any data loss.
- **Lossy compression**: Recommended for JPG and other lossy image formats. Using lossless compression on already-compressed JPG images is not recommended as it may result in larger file sizes without quality benefits.

## Performance

Release 2.3.0 rewrote the JNI bridge into a zero-copy pixel pipeline: `BufferedImage` rasters cross into libwebp without an intermediate format-conversion buffer, and decoding writes straight into the returned image's backing array. **Allocation is deterministic** — fixed by the code path, so the reductions below reproduce on any machine. Timing depends on hardware; the figure cited is from one reference run (Apple M5 MacBook Pro, GraalVM JDK 21.0.7, JMH `-prof gc`).

| Operation | Allocation vs 2.2.0 | What was eliminated |
|---|---|---|
| Lossy encode | **−98%** | input-side JNI copy + malloc loop + Java pixel conversion |
| Lossless encode | **−81%** | same input-side copies |
| Decode (alpha / opaque) | **−50% / −43%** | byte→int full-image conversion; peak 8→4 bytes/px |
| Animated encode / decode | **−98% / −50%** | redundant frame buffers |

The single-pass GIF decoder also makes **first-frame extraction ~60% faster** — it stops after composing frame 1 instead of decoding every frame.

### Multi-threaded animated encoding

Where 2.3.0's zero-copy pipeline removed the *memory*, multi-threaded encoding (libwebp `thread_level`, on by default) removes the *time* — the two axes are independent and stack. Enabling it cuts **animated-encode time by ~42%** while the output bitstream and allocation stay **byte-for-byte identical** to single-threaded:

| `encodeAnimated` (20 frames × 256², lossy q=75) | Single-threaded | Multi-threaded | Δ |
|---|---|---|---|
| Time | 172 ms | 99 ms | **−42%** |
| Allocation | 121 KB | 121 KB | unchanged |

> Reference run: 10-core Apple M5, GraalVM JDK 21.0.7, JMH `-prof gc`. The speedup reproduced across all five measured iterations (97–103 ms vs a flat 172 ms).

The win scales with **core count and frame size**: `thread_level` parallelizes *within* each frame's compression (not across frames — `WebPAnimEncoder` adds frames in order), so larger frames and more cores help most, while tiny frames or 1–2 core machines may see little or negative benefit. Don't stack it with `BatchProcessor`, which already saturates cores by encoding one image per thread (it passes `multiThreaded=false` internally to avoid oversubscription).

## Future Work

- **JDK 22+ FFM backend** — A Foreign Function & Memory API path alongside JNI

## Other Utils

https://onlinegiftools.com/analyze-gif

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).
