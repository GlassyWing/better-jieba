package com.huaban.analysis.jieba;

import java.io.IOException;
import java.util.*;

import com.huaban.analysis.jieba.dao.DictSource;
import com.huaban.analysis.jieba.viterbi.FinalSeg;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.PublishProcessor;


public class JiebaSegmenter {
    private static WordDictionary wordDict = WordDictionary.getInstance();
    private static FinalSeg finalSeg = FinalSeg.getInstance();
    private PublishProcessor<List<Pair<String>>> processor;

    public enum SegMode {
        INDEX,
        SEARCH
    }

    public JiebaSegmenter() {
        this.processor = PublishProcessor.create();
    }

    /**
     * 注册一个监听器，用于在词典发生变更时发送通知
     *
     * @param consumer 消费者
     * @return Disposable对象，可用于取消监听
     */
    public Disposable registerListener(Consumer<List<Pair<String>>> consumer) {
        return processor.subscribe(consumer);
    }

    /**
     * 若要将某几个分割开的词语分为一整个词，可通过该方法获得建议的词频
     *
     * @param segment 词语
     * @param tune    是否调整内部词典
     * @return 建议的该词应分配的词频
     */
    public long suggestFreq(boolean tune, String segment) {
        double freq = 1.0d;
        for (String seg : sentenceProcess(segment)) {
            freq *= Math.pow(Math.E, wordDict.freqs.getOrDefault(seg, Math.log(1.0d / wordDict.total)));
        }
        freq = Math.max(freq + 1.0d / wordDict.total
                , Math.pow(Math.E, wordDict.freqs.getOrDefault(segment, Math.log(1.0d / wordDict.total))));
        long actualFreq = (long) (freq * wordDict.total);

        if (tune) {
            addWord(segment, actualFreq, Math.log(Math.ceil(freq)));
        }
        return actualFreq;
    }

    /**
     * 若要将某个词分为几个词，可通过该方法获得针对该词所建议的词频，如：
     * double suggestedFreq = suggestFreq(false, '台', '中')
     * print('建议`台中`的频率应为：' + suggestedFreq)
     *
     * @param segments 词语
     * @param tune     是否将变更同步到词典中
     * @return 所建议的词频
     */
    public double suggestFreq(boolean tune, String... segments) {
        double freq = 1.0d;
        String words = String.join("", segments);
        for (String seg : segments) {
            freq *= Math.pow(Math.E, wordDict.freqs.getOrDefault(seg, Math.log(1 / wordDict.total)));
        }
        freq = Math.min(freq, Math.pow(Math.E, wordDict.freqs.getOrDefault(words, Math.log(0d))));
        long actualFreq = (long) (freq * wordDict.total);

        if (tune) {
            addWord(words, actualFreq, Math.log(Math.floor(freq)));
        }
        return actualFreq;
    }

    /**
     * 添加一个词到词典中，若该词已经存在于词典中，则更新它的频率
     *
     * @param word          词
     * @param actualFreq    真实频率
     * @param normalizeFreq 规格化后的频率
     */
    private void addWord(String word, long actualFreq, double normalizeFreq) {
        wordDict.total += actualFreq;
        if (wordDict.freqs.containsKey(word)) {
            wordDict.freqs.replace(word, normalizeFreq);
        } else {
            wordDict.freqs.put(word, normalizeFreq);
            wordDict.addWord(word);
        }

        List<Pair<String>> changeList = new ArrayList<>();
        changeList.add(new Pair<>(word, actualFreq));

        // 将词语分割后全部加入到字典中
        for (int i = 0; i < word.length() - 1; i++) {
            String split = word.substring(0, i + 1).trim();
            if (!wordDict.containsWord(split)) {
                split = wordDict.addWord(split);
                wordDict.freqs.put(split, 0d);
                changeList.add(new Pair<>(split, 0d));
            }
        }

        if (actualFreq == 0d) {
            finalSeg.addForceSplit(word);
            wordDict.freqs.remove(word);
            wordDict.delWord(word);
        }

        if (changeList.size() != 0) {
            this.processor.onNext(changeList);
        }

    }

    /**
     * 添加词语
     *
     * @param word       词语
     * @param actualFreq 真实频率
     */
    public void addWord(String word, long actualFreq) {
        this.addWord(word, actualFreq, Math.log(actualFreq / wordDict.total));
    }


    /**
     * 将一个词添加到词典中，使用建议的频率
     *
     * @param word 词
     */
    public void addWord(String word) {
        finalSeg.delForceSplit(word);
        suggestFreq(true, word);
    }

    /**
     * 删除一个词
     *
     * @param word 词
     */
    public void delWord(String word) {
        addWord(word, 0);
    }

    public void loadUserDict(DictSource dictSource) throws IOException {
        List<Pair<String>> changeList = wordDict.loadUserDict(dictSource);
        if (changeList.size() != 0) {
            this.processor.onNext(changeList);
        }
    }


    private Map<Integer, List<Integer>> createDAG(String sentence) {
        Map<Integer, List<Integer>> dag = new HashMap<>();
        DictSegment trie = wordDict.getTrie();
        char[] chars = sentence.toCharArray();
        int N = chars.length;
        int i = 0, j = 0;
        while (i < N) {
            Hit hit = trie.match(chars, i, j - i + 1);
            if (hit.isPrefix() || hit.isMatch()) {
                if (hit.isMatch()) {
                    if (!dag.containsKey(i)) {
                        List<Integer> value = new ArrayList<Integer>();
                        dag.put(i, value);
                        value.add(j);
                    } else
                        dag.get(i).add(j);
                }
                j += 1;
                if (j >= N) {
                    i += 1;
                    j = i;
                }
            } else {
                i += 1;
                j = i;
            }
        }
        for (i = 0; i < N; ++i) {
            if (!dag.containsKey(i)) {
                List<Integer> value = new ArrayList<Integer>();
                value.add(i);
                dag.put(i, value);
            }
        }
        return dag;
    }


    private Map<Integer, Pair<Integer>> calc(String sentence, Map<Integer, List<Integer>> dag) {
        int N = sentence.length();
        HashMap<Integer, Pair<Integer>> route = new HashMap<Integer, Pair<Integer>>();
        route.put(N, new Pair<Integer>(0, 0.0));
        for (int i = N - 1; i > -1; i--) {
            Pair<Integer> candidate = null;
            for (Integer x : dag.get(i)) {
                double freq = wordDict.getFreq(sentence.substring(i, x + 1)) + route.get(x + 1).freq;
                if (null == candidate) {
                    candidate = new Pair<Integer>(x, freq);
                } else if (candidate.freq < freq) {
                    candidate.freq = freq;
                    candidate.key = x;
                }
            }
            route.put(i, candidate);
        }
        return route;
    }


    public List<SegToken> process(String paragraph, SegMode mode) {
        List<SegToken> tokens = new ArrayList<SegToken>();
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (int i = 0; i < paragraph.length(); ++i) {
            char ch = CharacterUtil.regularize(paragraph.charAt(i));
            if (CharacterUtil.ccFind(ch))
                sb.append(ch);
            else {
                if (sb.length() > 0) {
                    // process
                    if (mode == SegMode.SEARCH) {
                        for (String word : sentenceProcess(sb.toString())) {
                            tokens.add(new SegToken(word, offset, offset += word.length()));
                        }
                    } else {
                        for (String token : sentenceProcess(sb.toString())) {
                            if (token.length() > 2) {
                                String gram2;
                                int j = 0;
                                for (; j < token.length() - 1; ++j) {
                                    gram2 = token.substring(j, j + 2);
                                    if (wordDict.containsWord(gram2))
                                        tokens.add(new SegToken(gram2, offset + j, offset + j + 2));
                                }
                            }
                            if (token.length() > 3) {
                                String gram3;
                                int j = 0;
                                for (; j < token.length() - 2; ++j) {
                                    gram3 = token.substring(j, j + 3);
                                    if (wordDict.containsWord(gram3))
                                        tokens.add(new SegToken(gram3, offset + j, offset + j + 3));
                                }
                            }
                            tokens.add(new SegToken(token, offset, offset += token.length()));
                        }
                    }
                    sb = new StringBuilder();
                    offset = i;
                }
                if (wordDict.containsWord(paragraph.substring(i, i + 1)))
                    tokens.add(new SegToken(paragraph.substring(i, i + 1), offset, ++offset));
                else
                    tokens.add(new SegToken(paragraph.substring(i, i + 1), offset, ++offset));
            }
        }
        if (sb.length() > 0)
            if (mode == SegMode.SEARCH) {
                for (String token : sentenceProcess(sb.toString())) {
                    tokens.add(new SegToken(token, offset, offset += token.length()));
                }
            } else {
                for (String token : sentenceProcess(sb.toString())) {
                    if (token.length() > 2) {
                        String gram2;
                        int j = 0;
                        for (; j < token.length() - 1; ++j) {
                            gram2 = token.substring(j, j + 2);
                            if (wordDict.containsWord(gram2))
                                tokens.add(new SegToken(gram2, offset + j, offset + j + 2));
                        }
                    }
                    if (token.length() > 3) {
                        String gram3;
                        int j = 0;
                        for (; j < token.length() - 2; ++j) {
                            gram3 = token.substring(j, j + 3);
                            if (wordDict.containsWord(gram3))
                                tokens.add(new SegToken(gram3, offset + j, offset + j + 3));
                        }
                    }
                    tokens.add(new SegToken(token, offset, offset += token.length()));
                }
            }

        return tokens;
    }


    public List<String> sentenceProcess(String sentence) {
        List<String> tokens = new ArrayList<String>();
        int N = sentence.length();
        Map<Integer, List<Integer>> dag = createDAG(sentence);
        Map<Integer, Pair<Integer>> route = calc(sentence, dag);

        int x = 0;
        int y = 0;
        String buf;
        StringBuilder sb = new StringBuilder();
        while (x < N) {
            y = route.get(x).key + 1;
            String lWord = sentence.substring(x, y);
            if (y - x == 1)
                sb.append(lWord);
            else {
                if (sb.length() > 0) {
                    buf = sb.toString();
                    sb = new StringBuilder();
                    if (buf.length() == 1) {
                        tokens.add(buf);
                    } else {
                        if (wordDict.containsWord(buf)) {
                            tokens.add(buf);
                        } else {
                            finalSeg.cut(buf, tokens);
                        }
                    }
                }
                tokens.add(lWord);
            }
            x = y;
        }
        buf = sb.toString();
        if (buf.length() > 0) {
            if (buf.length() == 1) {
                tokens.add(buf);
            } else {
                if (wordDict.containsWord(buf)) {
                    tokens.add(buf);
                } else {
                    finalSeg.cut(buf, tokens);
                }
            }

        }
        return tokens;
    }
}
