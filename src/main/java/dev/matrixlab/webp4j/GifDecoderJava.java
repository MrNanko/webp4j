package dev.matrixlab.webp4j;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Java-based GIF decoder using ImageIO.
 * This is used as a fallback when native giflib is unavailable,
 * or for testing purposes.
 */
class GifDecoderJava {

    private static final String GRAPHIC_CONTROL_EXTENSION = "GraphicControlExtension";

    private GifDecoderJava() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Represents a single frame in a GIF animation.
     */
    static class GifFrame {
        BufferedImage image;
        int delayMs;
        int disposeMethod;  // 0=NONE, 1=NONE, 2=BACKGROUND, 3=RESTORE_PREVIOUS
        int leftOffset;
        int topOffset;
    }

    /**
     * Holds all decoded GIF data including frames and metadata.
     */
    static class GifData {
        List<GifFrame> frames;
        int loopCount;
        int width;
        int height;
        boolean hasTransparency;
    }

    /**
     * Decodes a GIF from a byte array using Java ImageIO.
     * Extracts all frames, delays, disposal methods, and loop count.
     *
     * @param gifBytes Byte array containing the GIF image data
     * @return GifData containing all frames and metadata
     * @throws IOException If decoding fails
     */
    static GifData decodeGif(byte[] gifBytes) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF image reader found");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);

                GifData data = new GifData();
                data.frames = new ArrayList<>();

                int numImages = reader.getNumImages(true);

                // Extract loop count from stream metadata
                IIOMetadata streamMetadata = reader.getStreamMetadata();
                data.loopCount = extractLoopCount(streamMetadata);

                // Read each frame
                for (int i = 0; i < numImages; i++) {
                    BufferedImage image = reader.read(i);
                    IIOMetadata frameMetadata = reader.getImageMetadata(i);

                    GifFrame frame = new GifFrame();
                    frame.image = image;
                    frame.delayMs = extractFrameDelay(frameMetadata);
                    frame.disposeMethod = extractDisposeMethod(frameMetadata);

                    // Extract frame position
                    int[] offsets = extractFramePosition(frameMetadata);
                    frame.leftOffset = offsets[0];
                    frame.topOffset = offsets[1];

                    // Check for transparency
                    if (extractTransparencyIndex(frameMetadata) >= 0) {
                        data.hasTransparency = true;
                    }

                    data.frames.add(frame);

                    if (i == 0) {
                        data.width = image.getWidth();
                        data.height = image.getHeight();
                    }
                }

                return data;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Extracts loop count from GIF stream metadata.
     * Parses the ApplicationExtensions node for NETSCAPE2.0 loop extension.
     *
     * @param metadata Stream metadata
     * @return Loop count (0=infinite, N=loop N times)
     */
    private static int extractLoopCount(IIOMetadata metadata) {
        if (metadata == null) {
            return 0;  // Default: infinite loop
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList applicationExtensions = ((IIOMetadataNode) root).getElementsByTagName("ApplicationExtensions");
        if (applicationExtensions.getLength() == 0) {
            return 0;  // No loop extension, default to infinite
        }

        IIOMetadataNode appExtNode = (IIOMetadataNode) applicationExtensions.item(0);
        NodeList appExtChildren = appExtNode.getChildNodes();

        for (int i = 0; i < appExtChildren.getLength(); i++) {
            Node appExt = appExtChildren.item(i);
            int loopCount = extractLoopCountFromExtension(appExt);
            if (loopCount >= 0) {
                return loopCount;
            }
        }

        return 0;  // Default: infinite loop
    }

    /**
     * Extracts loop count from a single ApplicationExtension node.
     *
     * @param appExt ApplicationExtension node
     * @return Loop count if found, or -1 if not a valid NETSCAPE2.0 extension
     */
    private static int extractLoopCountFromExtension(Node appExt) {
        if (!"ApplicationExtension".equals(appExt.getNodeName())) {
            return -1;
        }

        NamedNodeMap attrs = appExt.getAttributes();
        Node appIdNode = attrs.getNamedItem("applicationID");
        Node authCodeNode = attrs.getNamedItem("authenticationCode");

        if (appIdNode == null || authCodeNode == null) {
            return -1;
        }

        String appId = appIdNode.getNodeValue();
        String authCode = authCodeNode.getNodeValue();

        // NETSCAPE2.0 extension contains loop count
        if (!"NETSCAPE".equals(appId) || !"2.0".equals(authCode)) {
            return -1;
        }

        if (!(appExt instanceof IIOMetadataNode)) {
            return -1;
        }

        byte[] appData = (byte[]) ((IIOMetadataNode) appExt).getUserObject();
        if (appData == null || appData.length < 3 || appData[0] != 1) {
            return -1;
        }

        // Loop count is stored in bytes 1-2 (little-endian)
        return (appData[1] & 0xFF) | ((appData[2] & 0xFF) << 8);
    }

    /**
     * Extracts frame delay from Graphics Control Extension.
     *
     * @param metadata Frame metadata
     * @return Frame delay in milliseconds (default 100ms if not specified)
     */
    private static int extractFrameDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;  // Default delay
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList graphicControlExtensions = ((IIOMetadataNode) root).getElementsByTagName(GRAPHIC_CONTROL_EXTENSION);
        if (graphicControlExtensions.getLength() > 0) {
            IIOMetadataNode gceNode = (IIOMetadataNode) graphicControlExtensions.item(0);
            String delayTimeStr = gceNode.getAttribute("delayTime");
            if (!delayTimeStr.isEmpty()) {
                try {
                    // delayTime is in 1/100 seconds, convert to milliseconds
                    int delayTime = Integer.parseInt(delayTimeStr);
                    return delayTime * 10;
                } catch (NumberFormatException e) {
                    // Ignore and use default
                }
            }
        }

        return 100;  // Default delay
    }

    /**
     * Extracts disposal method from Graphics Control Extension.
     *
     * @param metadata Frame metadata
     * @return Disposal method (0=NONE, 1=NONE, 2=BACKGROUND, 3=RESTORE_PREVIOUS)
     */
    private static int extractDisposeMethod(IIOMetadata metadata) {
        if (metadata == null) {
            return 0;  // NONE
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList graphicControlExtensions = ((IIOMetadataNode) root).getElementsByTagName(GRAPHIC_CONTROL_EXTENSION);
        if (graphicControlExtensions.getLength() > 0) {
            IIOMetadataNode gceNode = (IIOMetadataNode) graphicControlExtensions.item(0);
            String disposalMethodStr = gceNode.getAttribute("disposalMethod");
            if (!disposalMethodStr.isEmpty()) {
                try {
                    // disposalMethod: none, doNotDispose, restoreToBackgroundColor, restoreToPrevious
                    if ("restoreToBackgroundColor".equals(disposalMethodStr)) {
                        return 2;
                    } else if ("restoreToPrevious".equals(disposalMethodStr)) {
                        return 3;
                    }
                } catch (Exception e) {
                    // Ignore and use default
                }
            }
        }

        return 0;  // Default: NONE
    }

    /**
     * Extracts transparent color index from Graphics Control Extension.
     *
     * @param metadata Frame metadata
     * @return Transparent color index, or -1 if no transparency
     */
    private static int extractTransparencyIndex(IIOMetadata metadata) {
        if (metadata == null) {
            return -1;
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList graphicControlExtensions = ((IIOMetadataNode) root).getElementsByTagName(GRAPHIC_CONTROL_EXTENSION);
        if (graphicControlExtensions.getLength() > 0) {
            IIOMetadataNode gceNode = (IIOMetadataNode) graphicControlExtensions.item(0);
            String transparentColorFlagStr = gceNode.getAttribute("transparentColorFlag");
            String transparentColorIndexStr = gceNode.getAttribute("transparentColorIndex");

            if ("true".equals(transparentColorFlagStr) && !transparentColorIndexStr.isEmpty()) {
                try {
                    return Integer.parseInt(transparentColorIndexStr);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        return -1;  // No transparency
    }

    /**
     * Extracts frame position (left and top offsets) from Image Descriptor.
     *
     * @param metadata Frame metadata
     * @return int array [leftOffset, topOffset]
     */
    private static int[] extractFramePosition(IIOMetadata metadata) {
        int[] offsets = new int[]{0, 0};

        if (metadata == null) {
            return offsets;
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList imageDescriptors = ((IIOMetadataNode) root).getElementsByTagName("ImageDescriptor");
        if (imageDescriptors.getLength() > 0) {
            IIOMetadataNode idNode = (IIOMetadataNode) imageDescriptors.item(0);
            String imageLeftPositionStr = idNode.getAttribute("imageLeftPosition");
            String imageTopPositionStr = idNode.getAttribute("imageTopPosition");

            try {
                if (!imageLeftPositionStr.isEmpty()) {
                    offsets[0] = Integer.parseInt(imageLeftPositionStr);
                }
                if (!imageTopPositionStr.isEmpty()) {
                    offsets[1] = Integer.parseInt(imageTopPositionStr);
                }
            } catch (NumberFormatException e) {
                // Ignore and use default (0, 0)
            }
        }

        return offsets;
    }

    /**
     * Gets GIF information without full decoding.
     * This is a lightweight operation for querying GIF properties.
     *
     * @param gifBytes Byte array containing the GIF image data
     * @param info AnimationInfo object to populate
     * @return true on success, false on failure
     */
    static boolean getGifInfo(byte[] gifBytes, AnimationInfo info) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return false;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);

                int numImages = reader.getNumImages(true);
                info.setFrameCount(numImages);

                BufferedImage firstImage = reader.read(0);
                info.setWidth(firstImage.getWidth());
                info.setHeight(firstImage.getHeight());

                IIOMetadata streamMetadata = reader.getStreamMetadata();
                info.setLoopCount(extractLoopCount(streamMetadata));

                // Check for transparency in any frame
                boolean hasTransparency = false;
                for (int i = 0; i < numImages && !hasTransparency; i++) {
                    IIOMetadata frameMetadata = reader.getImageMetadata(i);
                    if (extractTransparencyIndex(frameMetadata) >= 0) {
                        hasTransparency = true;
                    }
                }
                info.setHasTransparency(hasTransparency);

                return true;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }
}