package com.kaiserdev.geminitrigger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class ScreenshotProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kaiserdev.geminitrigger.provider";

    public static File getScreenshotFile(android.content.Context context) {
        return new File(context.getCacheDir(), "circle_to_search_screen.jpg");
    }

    public static Uri getScreenshotUri() {
        return Uri.parse("content://" + AUTHORITY + "/circle_to_search_screen.jpg");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = getScreenshotFile(getContext());
        String[] cols = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(new Object[]{"screen.jpg", file.exists() ? file.length() : 0L});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getScreenshotFile(getContext());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
