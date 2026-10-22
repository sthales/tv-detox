package com.ansub.tvhome;

import java.util.Map;

public class ConfigTest {
    public static void main(String[] args) {
        parseKeysReadsCodeToPackage();
        isImageAcceptsPictureFilesOnly();
        System.out.println("OK");
    }

    static void parseKeysReadsCodeToPackage() {
        Map<Integer, String> one = Config.parseKeys("4020:com.stremio.one");
        assert "com.stremio.one".equals(one.get(4020)) : one;
        assert Config.parseKeys("4020:a, 84:b").size() == 2;
        assert Config.parseKeys("x:a,4020:,:b,4020").isEmpty();
        assert Config.parseKeys(null).isEmpty();
        assert Config.parseKeys("").isEmpty();
    }

    static void isImageAcceptsPictureFilesOnly() {
        assert Config.isImage("a.JPG");
        assert Config.isImage("b.webp");
        assert Config.isImage("c.jpeg") && Config.isImage("d.png");
        assert !Config.isImage("c.gif");
        assert !Config.isImage(".jpg.txt");
        assert !Config.isImage(null);
    }
}
