package cn.local.smstone;

/** Exact matching after presentation formatting and an explicit Chinese country code. */
public final class NumberMatcher {
    private NumberMatcher() {}

    public static String normalize(String value) {
        if (value == null) return "";
        String text = value.trim();
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') cleaned.append(ch);
            else if (ch >= '\uff10' && ch <= '\uff19') cleaned.append((char) ('0' + ch - '\uff10'));
            else if (ch == '+' && cleaned.length() == 0) cleaned.append(ch);
            else if (Character.isWhitespace(ch) || ch == '-' || ch == '(' || ch == ')') continue;
            else return ""; // Reject letters/wildcards rather than silently matching another number.
        }
        String result = cleaned.toString();
        if (result.startsWith("+86")) result = result.substring(3);
        else if (result.startsWith("0086")) result = result.substring(4);
        return result;
    }

    public static boolean isValid(String value) {
        String number = normalize(value);
        String digits = number.startsWith("+") ? number.substring(1) : number;
        return digits.length() >= 3 && digits.length() <= 30;
    }

    public static boolean matches(String configured, String sender) {
        return isValid(configured) && isValid(sender) && normalize(configured).equals(normalize(sender));
    }
}
