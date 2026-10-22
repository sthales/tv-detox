package com.ansub.tvhome;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

public class ConfigTest {
    public static void main(String[] args) {
        parseKeysReadsCodeToPackage();
        isImageAcceptsPictureFilesOnly();
        setsRoundTripWithoutDuplicates();
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

    static void setsRoundTripWithoutDuplicates() {
        assert Config.splitSet("a,b,a").equals(new LinkedHashSet<>(Arrays.asList("a", "b")));
        assert Config.joinSet(Config.splitSet("x,y")).equals("x,y");
        assert Config.joinSet(Config.splitSet(" x , ,y ")).equals("x,y");
        assert Config.splitSet("").isEmpty();
        assert Config.splitSet(null).isEmpty();
    }
}
