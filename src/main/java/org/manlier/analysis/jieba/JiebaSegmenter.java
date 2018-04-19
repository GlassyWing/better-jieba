package org.manlier.analysis.jieba;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import org.manlier.analysis.jieba.dao.DictSource;
import org.manlier.analysis.jieba.viterbi.FinalSeg;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.PublishProcessor;
import org.reactivestreams.Subscriber;


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
     * 注册一个订阅者，用于在词典发生变更时发送通知
     *
     * @param consumer 消费者
     * @return Disposable对象，可用于取消监听
     */
    public Disposable subscribe(Consumer<List<Pair<String>>> consumer) {
        return processor.subscribe(consumer);
    }

    /**
     * 注册一个订阅者，用于在词典发生变更时发送通知
     *
     * @param subscriber 订阅者
     */
    public void subscribe(Subscriber<List<Pair<String>>> subscriber) {
        processor.subscribe(subscriber);
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
        List<String> segs = sentenceProcessWithNoHMM(segment);
        for (String seg : segs) {
            freq *= Math.pow(Math.E, wordDict.freqs.getOrDefault(seg, Math.log(1.0d / wordDict.total)));
        }

        // 原先segment在字典中所占的比例
        double segmentPercent = Math.pow(Math.E, wordDict.freqs.getOrDefault(segment, Math.log(0d)));

        // 要将segment分出来，要满足 P(segment) = max{P(segment}, p(seg1)*p(seg2), p(seg1)*p(seg2)*p(seg3)}
        freq = Math.max(freq + 1.0d / wordDict.total
                , segmentPercent);

        // 得到将segment分出来的频率
        long actualFreq = (long) (freq * wordDict.total);


        if (tune) {
            addWord(segment, actualFreq, Math.log(freq));
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
    public long suggestFreq(boolean tune, String... segments) {
        double percent = 1.0d;
        String words = String.join("", segments);
        for (String seg : segments) {
            percent *= Math.pow(Math.E, wordDict.freqs.getOrDefault(seg, Math.log(1 / wordDict.total)));
        }

        // words 在词典中所占的比例
        double wordsPercent = Math.pow(Math.E, wordDict.freqs.getOrDefault(words, Math.log(0d)));

        // 要将words进行分割，应满足 P(words) = min{ P(words), P(seg1)*P(seg2), P(seg1)*P(seg2)*P(seg3),...}
        percent = Math.min(percent, wordsPercent);
        // 得到实际频率
        long actualFreq = (long) (percent * wordDict.total);


        if (tune) {
            addWord(words, actualFreq, Math.log(percent));
        }
        return actualFreq;
    }

    /**
     * 添加一个词到词典中，若该词已经存在于词典中，则更新它的频率
     * 注意：若频率为0，将触发删除操作
     *
     * @param word          词
     * @param actualFreq    频率
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
     * @param actualFreq 频率
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

    /**
     * 根据Trie词典来构建有向无环图
     *
     * @param sentence 句子
     * @return 有向无环图
     */
    private Map<Integer, List<Integer>> createDAG(String sentence) {
        Map<Integer, List<Integer>> dag = new HashMap<>();
        // 获得Trie
        DictSegment trie = wordDict.getTrie();
        char[] chars = sentence.toCharArray();
        int N = chars.length;
        int i = 0, j = 0;
        while (i < N) {
            // 尝试从尾部开始，词长度不断增长地匹配字典中的词
            Hit hit = trie.match(chars, i, j - i + 1);
            if (hit.isPrefix() || hit.isMatch()) {
                if (hit.isMatch()) {
                    if (!dag.containsKey(i)) {
                        List<Integer> value = new ArrayList<Integer>();
                        // 在有向无环图中加入一个点，相当于记下线段首部
                        dag.put(i, value);
                        // 将线段的尾部记下来
                        value.add(j);
                    } else
                        dag.get(i).add(j);
                }
                // 如果只是前缀而没有完全匹配，则词长度向后加一
                // 如果匹配到，则词长度向后加一，碰到结尾则重新初始化
                j += 1;
                if (j >= N) {
                    i += 1;
                    j = i;
                }
            }
            // 没有匹配成功，则为无法组词的单字，放到while循环外统一处理
            else {
                i += 1;
                j = i;
            }
        }
        // 把未被匹配的单字加入有向无环图
        for (i = 0; i < N; ++i) {
            if (!dag.containsKey(i)) {
                List<Integer> value = new ArrayList<Integer>();
                value.add(i);
                dag.put(i, value);
            }
        }
        return dag;
    }

    /**
     * 计算最大可能路径
     *
     * @param sentence 句子
     * @param dag      DAG图
     * @return 路由表
     */
    private Map<Integer, Pair<Integer>> calc(String sentence, Map<Integer, List<Integer>> dag) {
        int N = sentence.length();
        HashMap<Integer, Pair<Integer>> route = new HashMap<Integer, Pair<Integer>>();
        route.put(N, new Pair<>(0, 0.0));
        for (int i = N - 1; i > -1; i--) {
            Pair<Integer> candidate = null;
            for (Integer x : dag.get(i)) {
                double freq = wordDict.getFreq(sentence.substring(i, x + 1)) + route.get(x + 1).freq;
                if (null == candidate) {
                    candidate = new Pair<>(x, freq);
                } else if (candidate.freq < freq) {
                    candidate.freq = freq;
                    candidate.key = x;
                }
            }
            route.put(i, candidate);
        }
        return route;
    }

    private List<SegToken> _process(List<String> tokenList, SegMode mode, int offset) {
        List<SegToken> tokens = new ArrayList<>();

        // SEARCH模式下，只处理一次句子，不对长的词句再次分解
        if (mode == SegMode.SEARCH) {
            for (String token : tokenList) {
                tokens.add(new SegToken(token, offset, offset += token.length()));
            }
        } else {
            // INDEX模式下，对长的词句不仅将其自身加入token，并且将其中的长度为2和3的词也加入token中
            for (String token : tokenList) {
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

    /**
     * 分词，默认开启HMM新词发现
     *
     * @param paragraph 句子
     * @param mode      分词模式
     * @return 词元集合
     */
    public List<SegToken> process(String paragraph, SegMode mode) {
        return process(paragraph, mode, true);
    }

    /**
     * 分词
     *
     * @param paragraph 句子
     * @param mode      分词模式
     * @param HMM       是否开启HMM新词发现
     * @return 词元集合
     */
    public List<SegToken> process(String paragraph, SegMode mode, boolean HMM) {
        if (!HMM) {
            return processWithNoHMM(paragraph, mode);
        }
        List<String> tokenList;
        List<SegToken> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (int i = 0; i < paragraph.length(); ++i) {
            char ch = CharacterUtil.regularize(paragraph.charAt(i));
            // 如果找到的是中文字符，加入处理语块中
            if (CharacterUtil.ccFind(ch))
                sb.append(ch);
                // 遇到标点符号或尾部，开始处理语块
            else {
                if (sb.length() > 0) {
                    tokenList = sentenceProcess(sb.toString());
                    tokens.addAll(_process(tokenList, mode, offset));

                    sb = new StringBuilder();
                    offset = i;
                }
                // 将标点符号也加入token中
                tokens.add(new SegToken(paragraph.substring(i, i + 1), offset, ++offset));
            }
        }
        // 若有剩余未处理的中文字符
        if (sb.length() > 0) {
            tokenList = sentenceProcess(sb.toString());
            tokens.addAll(_process(tokenList, mode, offset));
        }

        return tokens;
    }

    /**
     * 分词，默认不开启HMM新词发现
     *
     * @param paragraph 句子
     * @param mode      分词模式
     * @return 词元集合
     */
    private List<SegToken> processWithNoHMM(String paragraph, SegMode mode) {
        List<String> tokenList;
        List<SegToken> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (int i = 0; i < paragraph.length(); ++i) {
            char ch = CharacterUtil.regularize(paragraph.charAt(i));
            if (CharacterUtil.ccFind(ch))
                sb.append(ch);
            else {
                if (sb.length() > 0) {
                    tokenList = sentenceProcessWithNoHMM(sb.toString());
                    tokens.addAll(_process(tokenList, mode, offset));
                    sb = new StringBuilder();
                    offset = i;
                }
                if (wordDict.containsWord(paragraph.substring(i, i + 1)))
                    tokens.add(new SegToken(paragraph.substring(i, i + 1), offset, ++offset));
                else
                    tokens.add(new SegToken(paragraph.substring(i, i + 1), offset, ++offset));
            }
        }
        if (sb.length() > 0) {
            tokenList = sentenceProcessWithNoHMM(sb.toString());
            tokens.addAll(_process(tokenList, mode, offset));
        }

        return tokens;
    }

    /**
     * 分词，默认开启HMM新词发现
     *
     * @param sentence 句子
     * @return 分好的词序列
     */
    public List<String> sentenceProcess(String sentence) {
        return sentenceProcess(sentence, true);
    }

    /**
     * 分词
     *
     * @param sentence 句子
     * @param HMM      是否开启HMM新词发现
     * @return 分好的词序列
     */
    public List<String> sentenceProcess(String sentence, boolean HMM) {
        if (!HMM) {
            return sentenceProcessWithNoHMM(sentence);
        }
        List<String> tokens = new ArrayList<>();
        int N = sentence.length();
        Map<Integer, List<Integer>> dag = createDAG(sentence);
        Map<Integer, Pair<Integer>> route = calc(sentence, dag);

        int x = 0;
        int y;
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
                    processBuf(tokens, buf);
                }
                tokens.add(lWord);
            }
            x = y;
        }
        buf = sb.toString();
        if (buf.length() > 0) {
            processBuf(tokens, buf);
        }
        return tokens;
    }

    private void processBuf(List<String> tokens, String buf) {
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

    /**
     * 分词，默认不开启HMM新词发现
     *
     * @param sentence 句子
     * @return 分好的词序列
     */
    private List<String> sentenceProcessWithNoHMM(String sentence) {
        List<String> tokens = new ArrayList<>();
        int N = sentence.length();
        Map<Integer, List<Integer>> dag = createDAG(sentence);
        Map<Integer, Pair<Integer>> route = calc(sentence, dag);
        int x = 0;
        int y;
        String buf;
        StringBuilder sb = new StringBuilder();
        while (x < N) {
            y = route.get(x).key + 1;
            String lWord = sentence.substring(x, y);

            if (Pattern.compile("[a-zA-Z0-9]").matcher(lWord).find() && lWord.length() == 1) {
                sb.append(lWord);
                x = y;
            } else {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
                tokens.add(lWord);
                x = y;
            }

        }

        buf = sb.toString();
        if (buf.length() > 0) {
            tokens.add(sb.toString());
            sb.setLength(0);
        }
        return tokens;
    }
}
