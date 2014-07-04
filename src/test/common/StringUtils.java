package test.common;

/**
 * Created by hooxin on 14-7-4.
 */
public class StringUtils {
    public static boolean isNotEmpty(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }
}
