package dev.matrixlab.webp4j.model;

public enum VP8StatusCode {
    VP8_STATUS_OK,
    VP8_STATUS_OUT_OF_MEMORY,
    VP8_STATUS_INVALID_PARAM,
    VP8_STATUS_BITSTREAM_ERROR,
    VP8_STATUS_UNSUPPORTED_FEATURE,
    VP8_STATUS_SUSPENDED,
    VP8_STATUS_USER_ABORT,
    VP8_STATUS_NOT_ENOUGH_DATA;

    private static final VP8StatusCode[] VALUES = values();

    public static VP8StatusCode getStatusCode(int aValue) {
        if (aValue >= 0 && aValue < VALUES.length) {
            return VALUES[aValue];
        } else {
            return null;
        }
    }
}
