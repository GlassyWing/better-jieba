package org.manlier.analysis.jieba;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class JiebaSegmeterTest02 {

    JiebaSegmenter segmenter = new JiebaSegmenter();

    @Before
    public void setUp() {

//        WordDictionary.getInstance().init(new FileDictSource(Paths.get("conf")));
    }

    @Test
    public void testForceSplit() {
        List<String> strings = segmenter.sentenceProcess("台中", false);
        System.out.println(String.join("/", strings));
        strings = segmenter.sentenceProcess("「台中」正确应该不会被切开", false);
        System.out.println(String.join("/", strings));

        double freq = segmenter.suggestFreq(true, "台中");
        System.out.println(freq);
        strings = segmenter.sentenceProcess("「台中」正确应该不会被切开", false);
        System.out.println(String.join("/", strings));

        freq = segmenter.suggestFreq(true, "台", "中");
        System.out.println(freq);
        strings = segmenter.sentenceProcess("「台中」正确应该不会被切开", false);
        System.out.println(String.join("/", strings));
    }

    @Test
    public void testSuggestFreq() {
        List<String> strings = segmenter.sentenceProcess("大连美容美发学校中君意是你值得信赖的选择");
        System.out.println(String.join("/", strings));
        double freq = segmenter.suggestFreq(true, "君意");
        System.out.println(freq);
        strings = segmenter.sentenceProcess("大连美容美发学校中君意是你值得信赖的选择");
        System.out.println(String.join("/", strings));
    }

}
