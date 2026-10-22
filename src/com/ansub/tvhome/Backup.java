package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class Backup {
    private Backup() {}

    static File file(Context c) {
        return new File(c.getExternalFilesDir(null), "settings.json");
    }

    static void export(Context c) throws Exception {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> e : new TreeMap<>(c.getSharedPreferences(MainActivity.PREFS, 0).getAll()).entrySet()) {
            Object v = e.getValue();
            String type = v instanceof String ? "string" : v instanceof Boolean ? "bool" : v instanceof Long ? "long"
                    : v instanceof Integer ? "int" : v instanceof Float ? "float" : "set";
            if (!out.has(type)) out.put(type, new JSONObject());
            out.getJSONObject(type).put(e.getKey(), v instanceof Set ? new JSONArray(new TreeSet<>((Set<?>) v)) : v);
        }
        try (FileOutputStream f = new FileOutputStream(file(c))) {
            f.write(out.toString(2).getBytes("UTF-8"));
        }
    }

    static void load(Context c) throws Exception {
        byte[] buf;
        try (FileInputStream f = new FileInputStream(file(c))) {
            buf = new byte[(int) file(c).length()];
            for (int n = 0; n < buf.length; ) n += f.read(buf, n, buf.length - n);
        }
        JSONObject in = new JSONObject(new String(buf, "UTF-8"));
        SharedPreferences.Editor e = c.getSharedPreferences(MainActivity.PREFS, 0).edit().clear();
        for (Iterator<String> types = in.keys(); types.hasNext(); ) {
            String type = types.next();
            JSONObject group = in.getJSONObject(type);
            for (Iterator<String> keys = group.keys(); keys.hasNext(); ) {
                String k = keys.next();
                switch (type) {
                    case "string": e.putString(k, group.getString(k)); break;
                    case "bool": e.putBoolean(k, group.getBoolean(k)); break;
                    case "long": e.putLong(k, group.getLong(k)); break;
                    case "int": e.putInt(k, group.getInt(k)); break;
                    case "float": e.putFloat(k, (float) group.getDouble(k)); break;
                    case "set":
                        Set<String> s = new HashSet<>();
                        JSONArray a = group.getJSONArray(k);
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        e.putStringSet(k, s);
                        break;
                }
            }
        }
        e.commit();
    }
}
