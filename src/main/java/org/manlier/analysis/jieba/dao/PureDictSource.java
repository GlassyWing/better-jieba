package org.manlier.analysis.jieba.dao;


import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Consumer;

public class PureDictSource implements DictSource {

    private List<String> records;

    public PureDictSource(List<String> records) {
        this.records = records;
    }

    @Override
    public void loadDict(Charset charset, Consumer<String[]> consumer) throws IOException {

    }

    @Override
    public void loadDict(Consumer<String[]> consumer) throws IOException {
        records.forEach(record -> consumer.accept(record.split("[\t ]+")));
    }
}
