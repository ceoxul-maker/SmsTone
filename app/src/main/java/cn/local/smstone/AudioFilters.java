package cn.local.smstone;

import java.util.Calendar;
import java.util.Locale;

/** File format and time filters are independent and applied as an intersection. */
public final class AudioFilters {
    private AudioFilters() {}
    public static String format(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3") || type.equals("audio/mpeg") || type.equals("audio/mp3")) return "MP3";
        if (lower.endsWith(".m4a") || lower.endsWith(".aac") || type.equals("audio/mp4") || type.equals("audio/aac") || type.equals("audio/x-m4a")) return "M4A/AAC";
        if (lower.endsWith(".wav") || lower.endsWith(".wave") || type.contains("wav")) return "WAV";
        return "其他";
    }
    public static long mondayStartSeconds(long nowMillis) {
        Calendar week = Calendar.getInstance(); week.setTimeInMillis(nowMillis);
        int offset = (week.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        week.add(Calendar.DAY_OF_MONTH, -offset);
        week.set(Calendar.HOUR_OF_DAY, 0); week.set(Calendar.MINUTE, 0); week.set(Calendar.SECOND, 0); week.set(Calendar.MILLISECOND, 0);
        return week.getTimeInMillis() / 1000;
    }
    public static boolean matches(String name, String mime, long added, long modified, String format, boolean thisWeek, long weekStart) {
        return ("全部类型".equals(format) || format(name, mime).equals(format))
            && (!thisWeek || added >= weekStart || modified >= weekStart);
    }
}
