package com.huaban.analysis.jieba;

import com.huaban.analysis.jieba.dao.DictSource;
import com.huaban.analysis.jieba.dao.FileDictSource;
import com.huaban.analysis.jieba.viterbi.FinalSeg;

import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;


public class WordDictionary {
    private static WordDictionary singleton;                 // 全局单列
    private static DictSource MAIN_DICT; //  默认字典采用文件字典
    private static final String CONFIG_NAME = "jieba.defaultDict";

    static {
        try {
            MAIN_DICT = new FileDictSource(Paths.get(WordDictionary.class.getResource("/dict.txt").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static String USER_DICT_SUFFIX = ".dict";        //  用户字典后缀

    public final Map<String, Double> freqs = new HashMap<>();   //  记录单词频率
    private Double minFreq = Double.MAX_VALUE;  // 单词所能达到的最大频率
    public Double total = 0.0;                 // 所有单词的频率之和
    private DictSegment _dict;
    private boolean useDefaultDict = true;      // 是否使用默认字典


    private WordDictionary() {
        loadConfig();
        if (useDefaultDict) {
            this.loadDict();
        }
    }

    private void loadConfig() {
        String config = System.getenv(CONFIG_NAME);
        if (config == null) {
            config = System.getProperty(CONFIG_NAME, "true");
        }
        this.useDefaultDict = Boolean.valueOf(config);
    }


    /**
     * 获得全局单列
     *
     * @return WordDictionary 的单列
     */
    public static WordDictionary getInstance() {
        if (singleton == null) {
            synchronized (WordDictionary.class) {
                if (singleton == null) {
                    singleton = new WordDictionary();
                    return singleton;
                }
            }
        }
        return singleton;
    }


    /**
     * for ES to initialize the user dictionary.
     *
     * @param dictSource 字典源
     */
    public void init(DictSource dictSource) throws IOException {
        synchronized (WordDictionary.class) {
            singleton.loadUserDict(dictSource);
        }
    }


    /**
     * let user just use their own dict instead of the default dict
     */
    public void resetDict() {
        _dict = new DictSegment((char) 0);
        freqs.clear();
    }


    /**
     * Load default dict.
     */
    private void loadDict() {
        _dict = new DictSegment((char) 0);
        try {

            long s = System.currentTimeMillis();
            MAIN_DICT.loadDict(tokens -> {
                if (tokens.length >= 2) {
                    String word = tokens[0];
                    double freq = Double.valueOf(tokens[1]);
                    if (freq != 0d) {
                        total += freq;
                        word = addWord(word);
                        freqs.put(word, freq);
                        for (int i = 0; i < word.length() - 1; i++) {
                            String split = word.substring(0, i + 1).trim();
                            if (!containsWord(split)) {
                                split = addWord(split);
                                freqs.put(split, 0d);
                            }
                        }
                    } else {
                        FinalSeg.getInstance().addForceSplit(word);
                    }
                }
            });

            // normalize
            normalizeFreqs();

            System.out.println(String.format(Locale.getDefault(), "main dict load finished, time elapsed %d ms",
                    System.currentTimeMillis() - s));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(String.format(Locale.getDefault(), "%s load failure!", MAIN_DICT));
        }
    }

    private void normalizeFreqs() {
        for (Entry<String, Double> entry : freqs.entrySet()) {
            entry.setValue((Math.log(entry.getValue() / total)));
            minFreq = Math.min(entry.getValue(), minFreq);
        }
    }


    public String addWord(String word) {
        if (null != word && !"".equals(word.trim())) {
            String key = word.trim().toLowerCase(Locale.getDefault());
            _dict.fillSegment(key.toCharArray());
            return key;
        } else
            return null;
    }

    public String delWord(String word) {
        if (null != word && !"".equals(word.trim())) {
            String key = word.trim().toLowerCase(Locale.getDefault());
            _dict.disableSegment(key.toCharArray());
            return key;
        } else
            return null;
    }

    public List<Pair<String>> loadUserDict(DictSource userDict, Charset charset) throws IOException {
        final int[] count = {0};
        long s = System.currentTimeMillis();
        List<Pair<String>> changeList = new LinkedList<>();
        userDict.loadDict(charset, tokens -> {
            if (tokens.length >= 1) {
                // Ignore empty line
                String word = tokens[0];
                // Default frequency
                double freq = 3.0d;
                if (tokens.length == 2)
                    freq = Double.valueOf(tokens[1]);
                if (freq != 0d) {
                    word = addWord(word);
                    freqs.put(word, Math.log(freq / total));
                    changeList.add(new Pair<>(word, freq));
                    count[0]++;
                    for (int i = 0; i < word.length() - 1; i++) {
                        String split = word.substring(0, i + 1).trim();
                        if (!containsWord(split)) {
                            split = addWord(split);
                            freqs.put(split, 0d);
                            changeList.add(new Pair<>(split, 0d));
                            count[0]++;
                        }
                    }
                } else {
                    FinalSeg.getInstance().addForceSplit(word);
                }
            }
        });
        System.out.println(String.format(Locale.getDefault(), "user dict %s load finished, tot words:%d, time elapsed:%dms", userDict.toString(), count[0], System.currentTimeMillis() - s));
        return changeList;
    }

    public List<Pair<String>> loadUserDict(DictSource userDict) throws IOException {
        return this.loadUserDict(userDict, StandardCharsets.UTF_8);
    }


    /**
     * Get trie
     *
     * @return trie
     */
    public DictSegment getTrie() {
        return this._dict;
    }


    public boolean containsWord(String word) {
        return freqs.containsKey(word);
    }


    public Double getFreq(String key) {
        if (containsWord(key))
            return freqs.get(key);
        else
            return minFreq;
    }
}
