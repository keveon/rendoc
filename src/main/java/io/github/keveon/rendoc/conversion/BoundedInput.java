package io.github.keveon.rendoc.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class BoundedInput {

    private static final int BUFFER_SIZE = 16 * 1024;

    private BoundedInput() {
    }

    static long copy(InputStream input, Path target, long limit) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new ConversionException(ConversionException.Reason.PAYLOAD_TOO_LARGE);
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }
}
