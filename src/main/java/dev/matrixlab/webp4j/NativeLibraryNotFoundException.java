package dev.matrixlab.webp4j;


public class NativeLibraryNotFoundException extends RuntimeException {

    public NativeLibraryNotFoundException(String message) {
        super(message);
    }

    public NativeLibraryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}