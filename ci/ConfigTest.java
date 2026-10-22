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
        rotationIntervalsParse();
        rotationCyclesThroughTheChosenOnly();
        rotationIsDueAfterTheInterval();
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

    static void rotationIntervalsParse() {
        assert Config.intervalMillis("30m") == 30L * 60 * 1000;
        assert Config.intervalMillis("1h") == 3600L * 1000;
        assert Config.intervalMillis("24h") == 24L * 3600 * 1000;
        assert Config.intervalMillis("1w") == 7L * 24 * 3600 * 1000;
        assert Config.intervalMillis("10s") == 10L * 1000;
        assert Config.intervalMillis("off") == 0;
        assert Config.intervalMillis(null) == 0;
        assert Config.intervalMillis("x") == 0;
        assert Config.intervalMillis("-5m") == 0;
        assert Config.ROTATE_CHOICES.length == 7 && "off".equals(Config.ROTATE_CHOICES[0]) && "1w".equals(Config.ROTATE_CHOICES[6]);
    }

    static void rotationCyclesThroughTheChosenOnly() {
        java.util.List<String> r = Arrays.asList("image:a.jpg", "Dusk", "image:b.png");
        assert "Dusk".equals(Config.nextInRotation(r, "image:a.jpg"));
        assert "image:a.jpg".equals(Config.nextInRotation(r, "image:b.png"));
        assert "image:a.jpg".equals(Config.nextInRotation(r, "Ocean"));
        assert Config.nextInRotation(new java.util.ArrayList<String>(), "Dusk") == null;
    }

    static void rotationIsDueAfterTheInterval() {
        assert Config.rotationDue(1000, 1000 + 1800000, 1800000);
        assert !Config.rotationDue(1000, 1000 + 1799999, 1800000);
        assert !Config.rotationDue(0, 999999999, 0);
        assert Config.rotationDue(5000, 1000, 1800000);
    }
}
