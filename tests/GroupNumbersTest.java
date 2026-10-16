package cn.local.smstone;
public final class GroupNumbersTest {
    private static int checks;
    public static void main(String[] args) {
        expect(GroupNumbers.MAX_GROUPS == 6, "Six group limit");
        expect(GroupNumbers.parse("95588\n95533, 1069001；13800138000").size() == 4, "Mixed delimiters");
        expect(GroupNumbers.valid("+86 138-0013-8000\n95588"), "Formatted phone inside group");
        expect(GroupNumbers.matches("95533\n95588", "+8695588"), "Any member matches");
        expect(!GroupNumbers.matches("95533\n95588", "10695588"), "No suffix matching");
        expect(!GroupNumbers.valid("95588\n+8695588"), "Reject normalized duplicate in group");
        expect(GroupNumbers.overlaps("95588,95533", "+8695588"), "Cross group duplicates");
        expect(!GroupNumbers.overlaps("95588", "95533"), "Distinct groups");
        expect(!GroupNumbers.valid("95588,foo"), "Reject one invalid member");
        expect(!GroupNumbers.valid(" ,； \n"), "Reject empty group");
        expect(GroupNumbers.join("95588, 95533").equals("95588\n95533"), "Stable normalized delimiters");
        expect(GroupNumbers.parse(null).isEmpty(), "Null input");
        System.out.println("PASS: " + checks + " group checks");
    }
    private static void expect(boolean result, String label) { checks++; if (!result) throw new AssertionError(label); }
}
