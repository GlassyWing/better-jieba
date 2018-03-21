package org.manlier.analysis.jieba.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static org.manlier.analysis.jieba.WordDictionary.USER_DICT_SUFFIX;

/**
 * 文件字典源
 */
public class FileDictSource implements DictSource {

    private final Set<Path> loadedPath = new HashSet<>();

    private Path dictPath;

    public FileDictSource(Path dictPath) {
        this.dictPath = dictPath.toAbsolutePath();
    }

    @Override
    public void loadDict(Charset charset, Consumer<String[]> consumer) throws IOException {
        if (loadedPath.contains(dictPath.toAbsolutePath())) {
            return;
        }

        if (Files.isDirectory(dictPath)) {
            DirectoryStream<Path> stream = Files.newDirectoryStream(dictPath
                    , String.format(Locale.getDefault(), "*%s", USER_DICT_SUFFIX));
            for (Path path : stream) {
                readFile(consumer, Files.newBufferedReader(path, charset));
            }
        } else {
            readFile(consumer, Files.newBufferedReader(dictPath, charset));
        }

        loadedPath.add(dictPath);
    }

    private void readFile(Consumer<String[]> consumer, BufferedReader bufferedReader) throws IOException {
        try (BufferedReader reader = bufferedReader) {
            while (reader.ready()) {
                String line = reader.readLine();
                String[] tokens = line.split("[\t ]+");
                consumer.accept(tokens);
            }
        }
    }


    @Override
    public void loadDict(Consumer<String[]> consumer) throws IOException {
        this.loadDict(StandardCharsets.UTF_8, consumer);
    }

    @Override
    public String toString() {
        return dictPath.toString();
    }
}
