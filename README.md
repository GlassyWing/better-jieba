结巴分词(java版) jieba-analysis
===============================

首先感谢jieba (java)版分词原作者[huaban](https://github.com/huaban/jieba-analysis)，没有他的抛砖引玉，就不会有jieba java版了。
本项目为java版添加了一些api，和python原版的功能一致，具体如下：

# 功能扩展

## 添加/修改的api

- 获得建议的频率
  1. long suggestFreq(boolean tune, String segment)
  1. long suggestFreq(boolean tune, String... segments)

- 添加词语到字典，若字典已存在该词语，则修改它的频率
  1. void addWord(String word, long actualFreq)
  2. void addWord(String word)
- 从字典中删除词语
  - void delWord(String word)
- 导入用户字典
  - void loadUserDict(DictSource dictSource) throws IOException
- 分词可控制是否启用HMM新词发现，默认开启
  1. List&lt;SegToken> process(String paragraph, SegMode mode)
  2. List&lt;SegToken> process(String paragraph, SegMode mode, boolean HMM)
  3. Listt&lt;String> sentenceProcess(String sentence)
  4. Listt&lt;String> sentenceProcess(String sentence, boolean HMM)

以上api的用法均可在[jieba](https://github.com/fxsjy/jieba) 的READEME.md文件中找到

## 将字典加载功能抽象到DictSource类中

为了方便对各种字典源进行导入，将此功能进行了抽象。默认提供了`FileDictSource`和`PureDictSource`，针对文件字典源和原始的java List&lt;String>列表。若要导入其他字典源，可继承`DictSource`接口。

## 禁用默认字典

通过`ystem.setProperty("jieba.defaultDict", "false")`或者设置环境参数`-Djieba.defaultDict=false`可禁用默认字典。

**注意：** 禁用之后必须载入用户字典，java版本目前不能在没有字典的情况下完美运行