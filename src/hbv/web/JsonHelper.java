package hbv.web;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    private JsonHelper() {}

    public static String escape(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public static String getString(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + fieldName + "\" : \"";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return "";
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    public static long getLong(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + fieldName + "\" : ";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return 0;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean getBoolean(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + fieldName + "\" : ";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return false;
        int start = idx + pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        return json.startsWith("true", start);
    }

    public static List<String> getObjectsInArray(String json, String arrayName) {
        List<String> objects = new ArrayList<>();
        String pattern = "\"" + arrayName + "\":[";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + arrayName + "\" : [";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return objects;
        int start = idx + pattern.length();
        int end = json.indexOf("]", start);
        if (end == -1) return objects;
        String arrayContent = json.substring(start, end);

        int pos = 0;
        while (pos < arrayContent.length()) {
            int objStart = arrayContent.indexOf("{", pos);
            if (objStart == -1) break;
            int objEnd = arrayContent.indexOf("}", objStart);
            if (objEnd == -1) break;
            objects.add(arrayContent.substring(objStart, objEnd + 1));
            pos = objEnd + 1;
        }
        return objects;
    }
}
