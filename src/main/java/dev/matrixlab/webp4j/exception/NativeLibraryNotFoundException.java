package dev.matrixlab.webp4j.exception;


public class NativeLibraryNotFoundException extends RuntimeException {

    public NativeLibraryNotFoundException(String message) {
        super(message);
    }

    public NativeLibraryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}