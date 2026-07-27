package io.github.keveon.rendoc.conversion;

import java.nio.file.Path;

@FunctionalInterface
public interface DocumentEngine {

    void convert(Path input, Path output, SourceFormat source) throws Exception;
}
