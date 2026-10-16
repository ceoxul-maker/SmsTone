package cn.local.smstone;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public final class SoundFiles {
    private SoundFiles() {}
    public static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return "自定义音频";
    }
    public static Uri importSound(Context context, Uri source) throws IOException {
        File dir = new File(context.getFilesDir(), "sounds");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建声音目录");
        String filename = UUID.randomUUID() + ".audio";
        File destination = new File(dir, filename);
        boolean valid = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source); FileOutputStream output = new FileOutputStream(destination)) {
                if (input == null) throw new IOException("无法读取音频");
                byte[] buffer = new byte[32768];
                int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 20 * 1024 * 1024) throw new IOException("请选择小于 20 MB 的短音频");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("音频文件为空");
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(destination.getAbsolutePath());
                String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (duration == null || Long.parseLong(duration) <= 0) throw new IOException("无法识别音频，请选择 MP3、M4A 或 WAV");
            } catch (RuntimeException error) { throw new IOException("无法识别音频，请选择 MP3、M4A 或 WAV", error); }
            finally { retriever.release(); }
            valid = true;
            return new Uri.Builder().scheme("content").authority(SoundProvider.AUTHORITY).appendPath(filename).build();
        } finally { if (!valid) destination.delete(); }
    }
    public static void deleteIfUnused(Context context, String uriText) {
        if (uriText == null) return;
        Uri uri = Uri.parse(uriText);
        if (!SoundProvider.AUTHORITY.equals(uri.getAuthority())) return;
        for (Rule rule : new RuleStore(context).all()) if (uriText.equals(rule.soundUri)) return;
        String name = uri.getLastPathSegment();
        if (name != null && name.matches("[a-f0-9\\-]{36}\\.audio")) new File(new File(context.getFilesDir(), "sounds"), name).delete();
    }
}
