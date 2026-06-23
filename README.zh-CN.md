# WebP4j

##### 📖 [English Documentation](README.md) | 📖 中文文档

**WebP4j** 是一个基于 JNI（Java Native Interface）的 Java 库，支持 WebP 图像的有损/无损编解码、GIF 转 WebP，以及动态 WebP（动画 WebP）的创建与解码。本项目基于 Google 的 [libwebp](https://developers.google.com/speed/webp)（版本 1.6.0），并将其封装为 Java API。

## 功能特性

- 支持将 RGB 和 RGBA 图像编码为 WebP（有损与无损）。
- 支持将 WebP 图像解码为 RGB 和 RGBA。
- **GIF 转 WebP**：使用原生 giflib 解码器，并提供 Java ImageIO 回退方案。
- **创建动态 WebP**：从 `BufferedImage` 帧列表生成动态 WebP。
- **解码动态 WebP**：从现有动态 WebP 文件中提取帧图像与帧延迟。
- **帧归一化**：可将不同尺寸的图像统一到相同大小，便于生成动画。
- 基于 libwebp 提供高效图像压缩与解压。
- **零拷贝像素管线**：`BufferedImage` 光栅跨越 JNI 边界时不再做中间格式转换（详见[性能](#性能)）。
- 支持多平台（x64 与 ARM64 架构）。
- 使用 **JDK 21** 编译，目标字节码为 **Java 8**，以获得更广泛的兼容性。
- 已发布至 Maven Central 仓库。

## 环境要求

### JDK

- **Java 8 或更高版本**：编译后的库兼容 Java 8、11、17、21 及更高版本。

### 源码构建

如需在本地构建原生库，需要：

- **Java 21**：用于构建（JNI 头文件生成及现代工具链）
- **CMake 3.15+**：用于构建原生库
- **C 编译器**：GCC（Linux/macOS）、MinGW（Windows）或 Clang

**说明：** 该库使用 Java 21 配合 `--release 8` 参数编译，在利用现代构建工具的同时生成兼容 Java 8 的字节码。这意味着库可在任意 Java 8+ 运行时上正常工作。

## 支持平台

WebP4j 通过自动化 CI/CD 构建支持以下平台：

- **Windows**：x64（x86-64）和 ARM64（aarch64）
- **macOS**：x64（Intel）和 arm64（Apple Silicon）
- **Linux**：x64（x86-64）和 ARM64（aarch64）

## Java 22+ 原生访问说明

从 Java 22 开始，JVM 在库通过 JNI 加载原生代码时会发出警告（[JEP 472](https://openjdk.org/jeps/472)）。由于 WebP4j 内部使用了 JNI，你可能会看到以下警告：

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by dev.matrixlab.webp4j.internal.NativeLibraryLoader in an unnamed module
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

**如何消除警告：**

- **模块路径**（推荐）：WebP4j 以 Multi-Release JAR 形式提供命名模块 `dev.matrixlab.webp4j.core`。将 JAR 放到模块路径上并使用：
  ```
  --module-path webp4j-core.jar --add-modules dev.matrixlab.webp4j.core --enable-native-access=dev.matrixlab.webp4j.core
  ```
- **类路径**：如果使用传统类路径，模块名不会被识别，需使用通配标志：
  ```
  --enable-native-access=ALL-UNNAMED
  ```

> 该警告不会影响功能 —— WebP4j 在两种情况下均可正常工作。这只是提示性信息，直到未来某个 Java 版本将原生访问限制设为强制。

## 安装

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

## API 概览

### 平台可用性检查

```java
// 检查当前平台是否受支持
boolean isAvailable();
```

### 静态图像编解码

```java
// 编码
byte[] encodeImage(BufferedImage image, float quality) throws IOException;
byte[] encodeImage(BufferedImage image, float quality, boolean lossless) throws IOException;
byte[] encodeLosslessImage(BufferedImage image) throws IOException;

// 解码
BufferedImage decodeImage(byte[] webPData) throws IOException;

// 信息查询
int[] getWebPInfo(byte[] webPData) throws IOException;  // 返回 [宽度, 高度]
```

### 批量处理

```java
// 并行批量编解码（保序）。相互独立的图像会在共享内部线程池上并发处理，充分利用多核。
List<byte[]>        encodeImages(List<BufferedImage> images, float quality, boolean lossless) throws IOException;
List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException;

// 需要限制或复用线程？用 BatchProcessor 传入你自己的线程池：
List<byte[]>        BatchProcessor.encodeImages(List<BufferedImage>, float, boolean, ExecutorService) throws IOException;
List<BufferedImage> BatchProcessor.decodeImages(List<byte[]>, ExecutorService) throws IOException;
```

### GIF 转 WebP

```java
// GIF 转 WebP
byte[] encodeGifToWebP(byte[] gifData) throws IOException;
byte[] encodeGifToWebP(byte[] gifData, GifToWebPConfig config) throws IOException;
byte[] encodeGifToWebPLossless(byte[] gifData) throws IOException;

// 获取 GIF 信息
AnimationInfo getGifInfo(byte[] gifData) throws IOException;
```

### 创建动态 WebP

```java
// 从帧列表创建动态 WebP
byte[] createAnimatedWebP(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException;
```

### 解码动态 WebP

```java
// 将动态 WebP 解码为单帧图像
AnimatedWebPData decodeAnimatedWebP(byte[] webPData) throws IOException;

// AnimatedWebPData 提供：
List<AnimatedWebPFrame> getFrames();  // 每帧：BufferedImage + 时间戳
int[] getDelays();                    // 每帧延迟（毫秒）
int getCanvasWidth();
int getCanvasHeight();
int getFrameCount();
int getLoopCount();
```

### 帧归一化

```java
// 将帧归一化为统一尺寸（直接使用 FrameNormalizer）
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

## 使用示例

### 静态图像编码

```java
// 有损压缩（推荐用于 JPG 来源）
public void encodeToWebP() throws IOException {
    BufferedImage image = ImageIO.read(new File("input.jpg"));
    byte[] webp = WebPCodec.encodeImage(image, 75.0f);
    Files.write(Paths.get("output.webp"), webp);
}

// 无损压缩（推荐用于 PNG 来源）
public void encodeToLosslessWebP() throws IOException {
    BufferedImage image = ImageIO.read(new File("input.png"));
    byte[] webp = WebPCodec.encodeLosslessImage(image);
    Files.write(Paths.get("output.webp"), webp);
}
```

### 静态图像解码

```java
public void decodeFromWebP() throws IOException {
    byte[] webpData = Files.readAllBytes(Paths.get("input.webp"));
    BufferedImage image = WebPCodec.decodeImage(webpData);
    ImageIO.write(image, "png", new File("output.png"));
}
```

### 批量处理

```java
public void encodeBatch(List<BufferedImage> images) throws IOException {
    // 并行编码所有图像；结果 i 对应输入 i。
    List<byte[]> webps = WebPCodec.encodeImages(images, 75.0f, false);

    // 批量解码同理。
    List<BufferedImage> decoded = WebPCodec.decodeImages(webps);
}

public void encodeBatchWithOwnPool(List<BufferedImage> images, ExecutorService pool) throws IOException {
    // 传入自己的线程池以复用/限制线程（调用不会关闭该线程池）。
    List<byte[]> webps = BatchProcessor.encodeImages(images, 75.0f, false, pool);
}
```

### GIF 转 WebP

```java
// 默认转换（有损，质量 75）
public void convertGifToWebP() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));
    byte[] webp = WebPCodec.encodeGifToWebP(gifData);
    Files.write(Paths.get("output.webp"), webp);
}

// 自定义配置
public void convertGifToWebPCustom() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));

    GifToWebPConfig config = GifToWebPConfig.createLossyConfig(90.0f)
            .setCompressionMethod(6)
            .setMinimizeSize(true);

    byte[] webp = WebPCodec.encodeGifToWebP(gifData, config);
    Files.write(Paths.get("output.webp"), webp);
}

// 无损转换
public void convertGifToWebPLossless() throws IOException {
    byte[] gifData = Files.readAllBytes(Paths.get("input.gif"));
    byte[] webp = WebPCodec.encodeGifToWebPLossless(gifData);
    Files.write(Paths.get("output.webp"), webp);
}
```

### 从图像创建动态 WebP

```java
public void createAnimatedWebP() throws IOException {
    // 加载帧
    List<BufferedImage> frames = Arrays.asList(
        ImageIO.read(new File("frame1.png")),
        ImageIO.read(new File("frame2.png")),
        ImageIO.read(new File("frame3.png"))
    );

    // 将帧归一化为相同尺寸（动画所必需）
    frames = FrameNormalizer.normalize(frames);

    // 设置帧延迟（毫秒/帧）
    int[] delays = {100, 100, 100};

    // 创建动态 WebP
    GifToWebPConfig config = GifToWebPConfig.createLosslessConfig();
    byte[] webp = WebPCodec.createAnimatedWebP(frames, delays, config);

    Files.write(Paths.get("animated.webp"), webp);
}
```

### 将动态 WebP 解码为单帧

```java
public void extractFrames() throws IOException {
    byte[] webpData = Files.readAllBytes(Paths.get("animated.webp"));
    AnimatedWebPData result = WebPCodec.decodeAnimatedWebP(webpData);

    System.out.println("帧数：" + result.getFrameCount());
    System.out.println("画布尺寸：" + result.getCanvasWidth() + "x" + result.getCanvasHeight());
    System.out.println("循环次数：" + result.getLoopCount());

    // 获取每帧延迟
    int[] delays = result.getDelays();

    // 访问单帧图像
    for (int i = 0; i < result.getFrames().size(); i++) {
        AnimatedWebPFrame frame = result.getFrames().get(i);
        BufferedImage image = frame.getImage();
        System.out.println("第 " + i + " 帧：delay=" + delays[i] + "ms");

        // 将每帧保存为 PNG
        ImageIO.write(image, "png", new File("frame_" + i + ".png"));
    }
}
```

### 高级帧归一化

```java
public void normalizeWithCustomSettings() throws IOException {
    List<BufferedImage> frames = loadFrames();

    // 使用自定义适配模式归一化到指定尺寸
    List<BufferedImage> normalized = FrameNormalizer.normalize(
        frames,
        800,                        // 目标宽度
        600,                        // 目标高度
        FitMode.CONTAIN,            // 保持宽高比，添加黑边
        false,                      // 不对小图进行放大
        new Color(0, 0, 0, 0)       // 透明背景
    );

    // FitMode 选项：
    // - CONTAIN：完整显示图像，可能出现黑边
    // - COVER：填满整个画布，可能裁剪边缘
    // - STRETCH：拉伸填满，可能扭曲宽高比
}
```

## 压缩模式说明

- **无损压缩**：推荐用于 PNG 等无损格式，可在不丢失数据的前提下保留图像质量。
- **有损压缩**：推荐用于 JPG 等有损格式。不建议对已压缩的 JPG 图像使用无损压缩，因为这通常会增大文件体积且几乎没有画质提升。

## 性能

2.3.0 将 JNI 边界重写为零拷贝像素管线：`BufferedImage` 光栅直接进入 libwebp，不再经过中间格式转换缓冲；解码则直接写入返回图像的背板数组。**分配量是确定性指标** —— 由代码路径决定，下表的降幅在任何机器上都可复现。耗时与硬件相关，所引数据来自一次参考运行（Apple M5 MacBook Pro、GraalVM JDK 21.0.7、JMH `-prof gc`）。

| 操作 | 分配量 vs 2.2.0 | 消除了什么 |
|---|---|---|
| 有损编码 | **−98%** | 输入侧 JNI 拷贝 + malloc 循环 + Java 像素转换 |
| 无损编码 | **−81%** | 同样的输入侧拷贝 |
| 解码（带透明 / 不透明） | **−50% / −43%** | byte→int 整图转换；峰值 8→4 字节/像素 |
| 动画编码 / 解码 | **−98% / −50%** | 冗余帧缓冲 |

单遍 GIF 解码器还让**首帧提取快约 60%** —— 合成完第 1 帧即停，不再解码全部帧。完整结果与 JMH 套件见 [`benchmark/`](benchmark/)。

## 后续计划

- **多线程编码**：动态 WebP 的并行帧编码
- **JDK 22+ FFM 后端**：在 JNI 之外提供 Foreign Function & Memory API 路径

## 其他工具

https://onlinegiftools.com/analyze-gif

## 许可证

本项目基于 [MIT 许可证](https://opensource.org/licenses/MIT) 开源。
