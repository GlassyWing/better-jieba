package com.huaban.analysis.jieba;

import org.junit.Test;

public class ArithmeticTest {

    @Test
    public void test01() {
        String word = "你们好呀";
        for (int i = 0; i < word.length() - 1; i++) {
            String split = word.substring(0, i + 1).trim();
            System.out.println(split);
        }
    }

    @Test
    public void test02() {
        String word = "你们好呀";
        for (char ch : word.toCharArray()) {
            System.out.println(ch);
        }
    }

    @Test
    public void test03() {
        System.out.println(Math.log(3));
        System.out.println(Math.pow(Math.E, Math.log(0)));
    }

    @Test
    public void test04() {
        System.out.println(Math.pow(Math.E, -0.26268660809250016));
    }
}
