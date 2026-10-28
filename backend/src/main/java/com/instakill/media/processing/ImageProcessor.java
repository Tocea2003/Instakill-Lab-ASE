package com.instakill.media.processing;

/**
 * Port for image post-processing. Bytes in, bytes out, so an implementation can later run in a
 * separate worker (queue consumer, microservice) without changing {@code MediaStorageService}.
 */
public interface ImageProcessor {

    /** @param format image format name understood by ImageIO, e.g. "png" or "jpg"
     *  @throws IllegalArgumentException if the bytes are not a readable image */
    byte[] process(byte[] input, String format);
}
