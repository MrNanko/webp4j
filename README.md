# WebP4j

**WebP4j** is a Java library based on JNI (Java Native Interface) that supports WebP image encoding and decoding in Java projects. This project utilizes Google's [libwebp](https://developers.google.com/speed/webp) library (version 1.6.0) and exposes its functionality to Java applications.

## Features

- Supports WebP encoding of RGB and RGBA images with both lossy and lossless compression.
- Supports decoding WebP images to RGB and RGBA formats.
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

## Supported platforms

WebP4j supports the following platforms through automated CI/CD builds:

- **Windows**: x64 (x86-64)
- **macOS**: x64 (Intel) and arm64 (Apple Silicon)
- **Linux**: x64 (x86-64), ARM64 (aarch64), and ARM32 (armv7)

## API

### Maven Dependency

To use WebP4j in your project, add the following dependency to your `pom.xml` file:

```xml
<dependency>
    <groupId>dev.matrixlab</groupId>
    <artifactId>webp4j</artifactId>
    <version>1.3.1</version>
</dependency>
```

### Native methods

```java
public native boolean getInfo(byte[] data, int[] dimensions);
public native int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features);
public native byte[] encodeRGB(byte[] image, int width, int height, int stride, float quality);
public native byte[] encodeRGBA(byte[] image, int width, int height, int stride, float quality);
public native byte[] encodeLosslessRGB(byte[] image, int width, int height, int stride);
public native byte[] encodeLosslessRGBA(byte[] image, int width, int height, int stride);
public native boolean decodeRGBInto(byte[] data, byte[] outputBuffer, int outputStride);
public native boolean decodeRGBAInto(byte[] data, byte[] outputBuffer, int outputStride);
```

### Encoding and Decoding methods

```java
public static byte[] encodeImage(BufferedImage bufferedImage, float quality) throws IOException;
public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless) throws IOException;
public static byte[] encodeLosslessImage(BufferedImage bufferedImage) throws IOException;
public static BufferedImage decodeImage(byte[] webPData) throws IOException;
```

You can use the `encodeImage()` and `decodeImage()` methods of the `WebPCodec` class to convert image formats such as JPG/PNG to WEBP format. The library supports both lossy and lossless compression modes.

#### Compression Mode Guidelines

- **Lossless compression**: Recommended for PNG and other lossless image formats to preserve image quality without any data loss.
- **Lossy compression**: Recommended for JPG and other lossy image formats. Using lossless compression on already-compressed JPG images is not recommended as it may result in larger file sizes without quality benefits.

### Example

```java
// Lossy compression example (recommended for JPG sources)
public void encodeToWebP() throws IOException {
    // Read the source image from disk
    BufferedImage bufferedImage = ImageIO.read(new File("input.jpg"));

    // Set the compression quality (0.0f = worst, 100.0f = best)
    float quality = 75.0f;

    // Encode the BufferedImage into WebP byte array
    byte[] encodedWebP = WebPCodec.encodeImage(bufferedImage, quality);

    // Write the encoded bytes to the output file
    try (FileOutputStream fos = new FileOutputStream("output.webp")) {
        fos.write(encodedWebP);
    }
}

// Lossless compression example (recommended for PNG sources)
public void encodeToLosslessWebP() throws IOException {
    // Read the source image from disk
    BufferedImage bufferedImage = ImageIO.read(new File("input.png"));

    // Encode the BufferedImage into lossless WebP byte array
    byte[] encodedWebP = WebPCodec.encodeLosslessImage(bufferedImage);

    // Write the encoded bytes to the output file
    try (FileOutputStream fos = new FileOutputStream("output_lossless.webp")) {
        fos.write(encodedWebP);
    }
}

// Decoding example
public void decodeFromWebP() throws IOException {
    // Read all bytes from the WebP file into memory
    byte[] webPData = Files.readAllBytes(Paths.get("input.webp"));

    // Decode the WebP byte array into a BufferedImage
    BufferedImage image = WebPCodec.decodeImage(webPData);

    // Write the decoded image as a JPEG file
    ImageIO.write(image, "jpg", new File("decoded_output.jpg"));
}
```

## Future Work

Currently, WebP4j has encapsulated native methods. We will continue to update, develop more efficient APIs, and continuously improve documentation.

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).
