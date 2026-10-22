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
        inputIdsBecomeFileNames();
        rotationIntervalsParse();
        rotationCyclesThroughTheChosenOnly();
        rotationIsDueAfterTheInterval();
        driftStaysInsideThePicture();
        bingTitleDropsTheCredit();
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
        assert Config.splitSet("none").isEmpty();
        assert Config.joinSet(Config.splitSet("none,a")).equals("a");
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

    static void inputIdsBecomeFileNames() {
        assert "com.tcl.tvinput_.TvPassThroughService_HW15".equals(Config.fileKey("com.tcl.tvinput/.TvPassThroughService/HW15"));
        assert "com.netflix.ninja".equals(Config.fileKey("com.netflix.ninja"));
        String[] files = {"com.tcl.tvinput_.TvPassThroughService_HW15.png"};
        assert files[0].equals(Config.bannerFile(Config.fileKey("com.tcl.tvinput/.TvPassThroughService/HW15"), files));
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
        assert java.util.Arrays.asList(Config.ROTATE_CHOICES).equals(
                java.util.Arrays.asList("off", "5m", "10m", "30m", "1h", "4h", "12h", "24h", "1w")) : java.util.Arrays.toString(Config.ROTATE_CHOICES);
        assert Config.intervalMillis("10m") == 10L * 60 * 1000;
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

    // {startScale, endScale, startX, startY, endX, endY}: x/y are offsets as a fraction of the view.
    // A picture scaled by s covers the view as long as |offset| <= (s - 1) / 2.
    static void driftStaysInsideThePicture() {
        for (int seed = 0; seed < 50; seed++) {
            float[] d = Config.drift(seed);
            assert d[0] >= 1f && d[1] >= 1f && d[0] <= 1.15f && d[1] <= 1.15f : java.util.Arrays.toString(d);
            assert Math.abs(d[1] - d[0]) >= 0.05f : "too little zoom " + java.util.Arrays.toString(d);
            assert Math.abs(d[2]) <= (d[0] - 1) / 2 + 1e-6 && Math.abs(d[3]) <= (d[0] - 1) / 2 + 1e-6 : java.util.Arrays.toString(d);
            assert Math.abs(d[4]) <= (d[1] - 1) / 2 + 1e-6 && Math.abs(d[5]) <= (d[1] - 1) / 2 + 1e-6 : java.util.Arrays.toString(d);
        }
        assert !java.util.Arrays.equals(Config.drift(1), Config.drift(2));
    }

    static void bingTitleDropsTheCredit() {
        assert "Outono no Parque Nacional Naejangsan, Coreia do Sul".equals(
                Config.bingTitle("Outono no Parque Nacional Naejangsan, Coreia do Sul (© Twenty47studio/Getty Images)"));
        assert "Plain".equals(Config.bingTitle("Plain"));
        assert "".equals(Config.bingTitle(null));
    }
}
