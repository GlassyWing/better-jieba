package org.manlier.analysis.jieba.dao;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;

public interface DictSource {

    void loadDict(Charset charset, Consumer<String[]> consumer) throws IOException;

    void loadDict(Consumer<String[]> consumer) throws IOException;
}
