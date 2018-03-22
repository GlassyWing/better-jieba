better-jieba 基于原结巴分词(java版)的功能扩展版 
===============================

首先感谢jieba (java)版分词原作者[huaban](https://github.com/huaban/jieba-analysis)，没有他的辛勤工作，就不会有jieba java版了，更不会有现在的功能扩展版了。


## 如何安装

1. 下载本项目
2. 导航到项目根目录下
3. 执行`mvn clean && mvn install -DskipTests`即可安装到本地仓库
4. 通过在`pom.xml`文件中设置如下依赖即可使用

```xml
<dependency>
  <groupId>org.manlier</groupId>
  <artifactId>better-jieba</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

# 功能扩展

本项目为原java版添加了一些api，和python原版的功能一致，具体如下：

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

为了方便对各种字典源进行导入，将此功能进行了抽象。默认提供了 
`InputStreamDictSource`、`FileDictSource`和`PureDictSource`，使用方式如下所示：

示例 1：`InputStreamDictSource`的使用

```java
wordDict.loadUserDict(new InputStreamDictSource(Files.newInputStream("user.dict")));
```

示例 2：`FileDictSource`的使用

```java
wordDict.loadUserDict(new FileDictSource(Paths.get("conf")));
```

`FileDictSource`接受的参数可为目录或文件路径，若为目录，它会寻找指定目录下后缀名为`.dict`的文件，它**并不支持递归搜索**。

示例 3：`PureDictSource`的使用

```java

List<String> records = new ArrayList<>();

records.add("台北 5");
records.add("台中 3");

wordDict.loadUserDict(new PureDictSource(records));
```

若要导入其他字典源，可继承`DictSource`接口。

## 禁用默认字典

通过`System.setProperty("jieba.defaultDict", "false")`或者设置环境参数`-Djieba.defaultDict=false`可禁用默认字典。

示例：

```java
System.setProperty("jieba.defaultDict", "false");

WordDictionary wordDict = WordDictionary.getInstance();

Assert.assertTrue(!wordDict.isUseDefaultDict());

wordDict.loadUserDict(new FileDictSource(Paths.get("conf")));
```

**注意：** 禁用之后必须载入用户字典，java版本目前不能在没有字典的情况下完美运行

## 可注册订阅者

借用rxjava2，你可以注册订阅者，用于在词典发生变更时发送通知，这对于来自数据库的字典源来说非常重要。

 例如：

```java
JiebaSegmenter segmenter = new JiebaSegmenter();

Disposable disposable = segmenter.subscribe(System.out::println);

segmenter.suggestFreq(true,"中", "将");
```

将收到如下的输出，表示词`中将`的频率发生变化，变为了`494`：

```java
[Candidate [key=中将, freq=494.0]]
```