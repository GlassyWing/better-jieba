package org.manlier.analysis.jieba;

import org.manlier.analysis.jieba.dao.DictSource;
import org.manlier.analysis.jieba.dao.FileDictSource;
import org.manlier.analysis.jieba.dao.InputStreamDictSource;
import org.manlier.analysis.jieba.viterbi.FinalSeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;


public class WordDictionary {
    private static WordDictionary singleton;                 // 全局单列
    private static DictSource MAIN_DICT; //  默认字典采用文件字典
    private static final String CONFIG_NAME = "jieba.defaultDict";
    private Logger log = LoggerFactory.getLogger(getClass().getSimpleName());

    static {
        MAIN_DICT = new InputStreamDictSource(WordDictionary.class
                .getResourceAsStream("/dict.txt"));
    }

    public static String USER_DICT_SUFFIX = ".dict";        //  用户字典后缀

    public final Map<String, Double> freqs = new HashMap<>();   //  记录单词频率
    private Double minFreq = Double.MAX_VALUE;  // 单词所能达到的最大频率
    public Double total = 0.0;                 // 所有单词的频率之和
    private DictSegment _dict = new DictSegment((char) 0);
    ;
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
        singleton.loadUserDict(dictSource);
    }


    /**
     * let user just use their own dict instead of the default dict
     */
    public void resetDict() {
        _dict = new DictSegment((char) 0);
        freqs.clear();
        total = 0d;
        minFreq = Double.MAX_VALUE;
    }

    public boolean isUseDefaultDict() {
        return useDefaultDict;
    }

    /**
     * Load default dict.
     */
    private void loadDict() {

        try {
            long s = System.currentTimeMillis();
            final int[] count = {0};
            MAIN_DICT.loadDict(tokens -> {
                if (tokens.length >= 2) {
                    String word = tokens[0];
                    double freq = Double.valueOf(tokens[1]);
                    if (freq != 0d) {
                        total += freq;
                        word = addWord(word);
                        freqs.put(word, freq);
                        count[0] += 1;
                    } else {
                        FinalSeg.getInstance().addForceSplit(word);
                    }
                }
            });
            // normalize
            normalizeFreqs(freqs);
            log.debug("main dict load finished, total {}, time elapsed {} ms", count[0], System.currentTimeMillis() - s);
        } catch (IOException e) {
            log.error(MAIN_DICT + "load failure!", e);
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
        Map<String, Double> toBeMergefreqs = new HashMap<>();
        synchronized (WordDictionary.class) {
            userDict.loadDict(charset, tokens -> {
                // Ignore empty line
                if (tokens.length >= 1) {
                    String word = tokens[0];
                    // Default frequency
                    double freq = 3;
                    if (tokens.length >= 2)
                        freq = Double.valueOf(tokens[1]);
                    if (freq != 0d) {
                        total += freq;
                        word = addWord(word);
                        toBeMergefreqs.put(word, freq);
                        changeList.add(new Pair<>(word, freq));
                        count[0]++;
                    } else {
                        FinalSeg.getInstance().addForceSplit(word);
                    }
                }
            });
            normalizeFreqs(toBeMergefreqs);
            freqs.putAll(toBeMergefreqs);
            log.debug("user dict {} load finished, tot words:{}, time elapsed:{} ms", userDict, count[0], System.currentTimeMillis() - s);
            return changeList;
        }
    }

    public List<Pair<String>> loadUserDict(DictSource userDict) throws IOException {
        return this.loadUserDict(userDict, StandardCharsets.UTF_8);
    }

    private void normalizeFreqs(Map<String, Double> freqs) {
        freqs.entrySet().parallelStream()
                .forEach(entry -> {
                    double value = Math.log(entry.getValue() / total);
                    entry.setValue(value);
                    minFreq = Math.min(value, minFreq);
                });
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
