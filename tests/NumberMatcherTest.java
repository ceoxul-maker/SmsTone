package cn.local.smstone;

public final class NumberMatcherTest {
    private static int checks = 0;
    public static void main(String[] args) {
        expect(NumberMatcher.matches("13800138000", "+86 138-0013-8000"), "Chinese mobile country code");
        expect(NumberMatcher.matches("95588", "+8695588"), "Explicit service country code");
        expect(NumberMatcher.matches("95588", "008695588"), "0086 service country code");
        expect(NumberMatcher.matches("１０６９００１２３", "106900123"), "Full width digits");
        expect(!NumberMatcher.matches("95588", "10695588"), "No suffix matching");
        expect(!NumberMatcher.matches("106900123", "1069001234"), "Exact long service number");
        expect(!NumberMatcher.matches("13800138000", "+1 13800138000"), "Different country");
        expect(!NumberMatcher.matches("", ""), "No empty matches");
        expect(!NumberMatcher.matches(null, null), "No null matches");
        expect(!NumberMatcher.isValid("95588*"), "Reject wildcard");
        expect(!NumberMatcher.isValid("bank95588"), "Reject sender labels");
        expect(!NumberMatcher.isValid("++95588"), "Reject double plus");
        expect(!NumberMatcher.isValid("12"), "Reject too short");
        expect(NumberMatcher.matches("+44 7700 900123", "+447700900123"), "Other international code remains intact");
        System.out.println("PASS: " + checks + " number matching checks");
    }
    private static void expect(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
