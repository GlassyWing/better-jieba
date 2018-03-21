package com.huaban.analysis.jieba.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class FileDictSourceTest {

    DictSource dictSource;

    @Before
    public void setUp() throws URISyntaxException {

        dictSource = new FileDictSource(Paths.get(this.getClass().getResource("/dict.txt").toURI()).normalize());
    }

    @Test
    public void loadDict() throws IOException {
        dictSource.loadDict(System.out::println);
    }
}
