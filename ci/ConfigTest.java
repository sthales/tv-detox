package com.ansub.tvhome;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

public class ConfigTest {
    public static void main(String[] args) {
        parseKeysReadsCodeToPackage();
        isImageAcceptsPictureFilesOnly();
        setsRoundTripWithoutDuplicates();
        parseKeysRefusesNavigationAndSystemKeys();
        controllersAreNotRemapped();
        bannerFileMatchesThePackageOnly();
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

    static void parseKeysRefusesNavigationAndSystemKeys() {
        Map<Integer, String> k = Config.parseKeys("23:x,4:x,3:x,19:x,20:x,21:x,22:x,24:x,25:x,26:x,66:x,82:x,85:x,90:x,160:x,164:x,4020:s");
        assert k.size() == 1 && "s".equals(k.get(4020)) : k;
        assert Config.parseKeys("84:a,190:b,4062:c").size() == 3;
    }

    static void controllersAreNotRemapped() {
        assert !Config.fromController(0x301);      // the TCL remote: keyboard | dpad
        assert Config.fromController(0x401);       // SOURCE_GAMEPAD
        assert Config.fromController(0x1000010);   // SOURCE_JOYSTICK
        assert Config.fromController(0x501);       // keyboard | gamepad, how many pads report
    }

    static void bannerFileMatchesThePackageOnly() {
        String[] files = {"com.netflix.ninja.png", "com.stremio.one.JPG", "com.stremio.one.ninja.png", "notes.txt"};
        assert "com.netflix.ninja.png".equals(Config.bannerFile("com.netflix.ninja", files));
        assert "com.stremio.one.JPG".equals(Config.bannerFile("com.stremio.one", files));
        assert Config.bannerFile("com.spotify.tv.android", files) == null;
        assert Config.bannerFile("com.netflix", files) == null;
        assert Config.bannerFile("com.netflix.ninja", null) == null;
    }
}
