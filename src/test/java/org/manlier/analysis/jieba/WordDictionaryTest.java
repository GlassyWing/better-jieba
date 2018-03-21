package org.manlier.analysis.jieba;

import org.manlier.analysis.jieba.dao.FileDictSource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;

@RunWith(JUnit4.class)
public class WordDictionaryTest {

    @Test
    public void testLoadDefaultDict() throws URISyntaxException, IOException {
        WordDictionary wordDict = WordDictionary.getInstance();
        double d = wordDict.getFreq("司机");
        System.out.println(d);
        URL url = ClassLoader.getSystemResource("test_user.dict");
        wordDict.loadUserDict(new FileDictSource(Paths.get(url.toURI())));
        d = wordDict.getFreq("司机");
        System.out.println(d);
    }

    @Test
    public void testUseDefaultDict() {
        WordDictionary wordDict = WordDictionary.getInstance();
        Assert.assertTrue(wordDict.isUseDefaultDict());
    }

    @Test
    public void testUseDefaultDict02() throws IOException {
        System.setProperty("jieba.defaultDict", "false");
        WordDictionary wordDict = WordDictionary.getInstance();
        Assert.assertTrue(!wordDict.isUseDefaultDict());
        wordDict.loadUserDict(new FileDictSource(Paths.get("conf")));
        double d = wordDict.getFreq("司机");
        System.out.println(new DecimalFormat("#,##0.000000000000000000000000").format(d));
    }

    @Test
    public void testUseDefaultDict03() throws IOException {
        WordDictionary wordDict = WordDictionary.getInstance();
        wordDict.resetDict();
        wordDict.loadUserDict(new FileDictSource(Paths.get("conf")));
        double d = wordDict.getFreq("司机");
        System.out.println(new DecimalFormat("#,##0.000000000000000000000000").format(d));
    }

    @Test
    public void testUserDict() throws IOException, URISyntaxException {
        System.setProperty("jieba.defaultDict", "false");
        JiebaSegmenter segmenter = new JiebaSegmenter();
        segmenter.loadUserDict(new FileDictSource(Paths.get("conf")));
        cut(segmenter);
    }

    private void cut(JiebaSegmenter segmenter) {
        List<String> strings = segmenter.sentenceProcess("大连美容美发学校中君意是你值得信赖的选择");
        System.out.println(String.join("/", strings));
        double freq = segmenter.suggestFreq(true, "君意");
        freq = segmenter.suggestFreq(true, "美容");
        System.out.println(freq);
        strings = segmenter.sentenceProcess("大连美容美发学校中君意是你值得信赖的选择");
        System.out.println(String.join("/", strings));
    }
}
