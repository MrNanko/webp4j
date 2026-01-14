# WebP4j

**WebP4j** is a Java library based on JNI (Java Native Interface) that supports WebP image encoding and decoding in Java projects. This project utilizes Google's [libwebp](https://developers.google.com/speed/webp) library (version 1.6.0) and exposes its functionality to Java applications.

## Features

- Supports WebP encoding of RGB and RGBA images with both lossy and lossless compression.
- Supports decoding WebP images to RGB and RGBA formats.
- **GIF to WebP conversion** with native giflib decoder and Java ImageIO fallback.
- **Animated WebP creation** from BufferedImage frames.
- **Frame normalization** for creating animations from images of different sizes.
- Provides efficient image compression and decompression using libwebp.
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

- **Windows**: x64 (x86-64)
- **macOS**: x64 (Intel) and arm64 (Apple Silicon)
- **Linux**: x64 (x86-64), ARM64 (aarch64), and ARM32 (armv7)

## Installation

### Maven

```xml
<dependency>
    <groupId>dev.matrixlab</groupId>
    <artifactId>webp4j</artifactId>
    <version>1.4.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'dev.matrixlab:webp4j:1.4.0'
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

### Frame Normalization

```java
// Normalize frames to common size (use FrameNormalizer directly)
List<BufferedImage> FrameNormalizer.normalize(List<BufferedImage> frames);
List<BufferedImage> FrameNormalizer.normalize(
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

    // Create animated WebP
    GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
    byte[] webp = WebPCodec.createAnimatedWebP(frames, delays, config);

    Files.write(Paths.get("animated.webp"), webp);
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

## Future Work

- **Multi-threaded encoding** - Parallel frame encoding for animated WebP
- **Batch processing** - Optimize bulk image conversion performance
- **Memory optimization** - Reduce memory allocation and GC pressure
- **Zero-copy optimization** - Minimize data copying between Java and native layers

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).