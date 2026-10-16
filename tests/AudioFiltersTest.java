package cn.local.smstone;

import java.util.Calendar;
public final class AudioFiltersTest {
    private static int checks;
    public static void main(String[] args) {
        expect(AudioFilters.format("ALERT.MP3", "application/octet-stream").equals("MP3"), "Case insensitive extension");
        expect(AudioFilters.format("no-extension", "audio/mpeg").equals("MP3"), "MIME format fallback");
        expect(AudioFilters.format("voice.m4a", null).equals("M4A/AAC"), "M4A extension");
        expect(AudioFilters.format("voice.aac", null).equals("M4A/AAC"), "AAC extension");
        expect(AudioFilters.format("voice", "audio/x-wav").equals("WAV"), "WAV MIME");
        expect(AudioFilters.format("voice.ogg", "audio/ogg").equals("其他"), "Other audio");
        expect(AudioFilters.matches("a.mp3", null, 1, 1, "MP3", false, 100), "Format without week filter");
        expect(!AudioFilters.matches("a.mp3", null, 1, 1, "MP3", true, 100), "Old file excluded from this week");
        expect(AudioFilters.matches("a.mp3", null, 100, 1, "MP3", true, 100), "Week boundary included by added date");
        expect(AudioFilters.matches("a.mp3", null, 1, 101, "MP3", true, 100), "Recent modification included");
        expect(!AudioFilters.matches("a.wav", null, 101, 101, "MP3", true, 100), "Format and week intersect");
        expect(AudioFilters.matches("a.wav", null, 101, 1, "全部类型", true, 100), "All formats with week");
        Calendar sunday = Calendar.getInstance(); sunday.clear(); sunday.set(2026, Calendar.OCTOBER, 18, 13, 10);
        Calendar monday = Calendar.getInstance(); monday.clear(); monday.set(2026, Calendar.OCTOBER, 12, 0, 0);
        expect(AudioFilters.mondayStartSeconds(sunday.getTimeInMillis()) == monday.getTimeInMillis()/1000, "Sunday belongs to preceding Monday week");
        expect(AudioFilters.mondayStartSeconds(monday.getTimeInMillis()) == monday.getTimeInMillis()/1000, "Monday starts a new week");
        System.out.println("PASS: " + checks + " audio filter checks");
    }
    private static void expect(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
