package cn.local.smstone;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Delimit on lines/commas/semicolons, not spaces inside a formatted phone number. */
public final class GroupNumbers {
    public static final int MAX_GROUPS = 6;
    private GroupNumbers() {}
    public static List<String> parse(String text) {
        List<String> numbers = new ArrayList<>();
        if (text == null) return numbers;
        for (String part : text.split("[\\r\\n,;，；]+")) if (!part.trim().isEmpty()) numbers.add(part.trim());
        return numbers;
    }
    public static boolean valid(String text) {
        List<String> numbers = parse(text);
        if (numbers.isEmpty()) return false;
        Set<String> unique = new LinkedHashSet<>();
        for (String number : numbers) if (!NumberMatcher.isValid(number) || !unique.add(NumberMatcher.normalize(number))) return false;
        return true;
    }
    public static boolean matches(String group, String sender) {
        for (String number : parse(group)) if (NumberMatcher.matches(number, sender)) return true;
        return false;
    }
    public static boolean overlaps(String first, String second) {
        for (String number : parse(first)) if (matches(second, number)) return true;
        return false;
    }
    public static String join(String text) {
        StringBuilder result = new StringBuilder();
        for (String number : parse(text)) { if (result.length() > 0) result.append('\n'); result.append(number); }
        return result.toString();
    }
}
