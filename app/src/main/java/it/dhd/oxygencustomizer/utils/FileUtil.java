package it.dhd.oxygencustomizer.utils;

import static it.dhd.oxygencustomizer.OxygenCustomizer.getAppContext;
import static it.dhd.oxygencustomizer.utils.ModuleConstants.TEMP_MODULE_DIR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {

    public static final String DATA_DIR = getAppContext().getFilesDir().toString();

    public static void copyAssets(String assetFolder) throws IOException {
        cleanDir(assetFolder);
        createDir(assetFolder);
        copyFileOrDirectory(getAppContext(), assetFolder, DATA_DIR + "/" + assetFolder);
    }

    public static void cleanDir(String dirName) {
        Shell.cmd("rm -rf " + DATA_DIR + "/" + dirName).exec();
    }

    private static void createDir(String dirName) {
        File new_folder = new File(DATA_DIR + "/" + dirName + "/");
        new_folder.mkdirs();
    }

    public static void extractBanner() {
        AssetManager assetManager = getAppContext().getAssets();
        try (InputStream in = assetManager.open("banner.png");
             OutputStream out = new FileOutputStream(TEMP_MODULE_DIR + "/banner.png")) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (IOException e) {
            Log.e("OxygenCustomizer", "Failed to extract banner.png", e);
        }
    }

    private static void copyFileOrDirectory(Context context, String dirName, String outPath) throws IOException {
        String[] srcFiles = context.getAssets().list(dirName);
        if (srcFiles == null) return;

        for (String srcFileName : srcFiles) {
            String outFileName = outPath + File.separator + srcFileName;
            String inFileName = dirName + File.separator + srcFileName;
            if (dirName.equals("")) {
                inFileName = srcFileName;
            }
            try {
                InputStream inputStream = context.getAssets().open(inFileName);
                copyAndClose(inputStream, Files.newOutputStream(Paths.get(outFileName)));
            } catch (IOException e) {
                new File(outFileName).mkdir();
                copyFileOrDirectory(context, inFileName, outFileName);
            }
        }
    }

    public static void closeQuietly(AutoCloseable autoCloseable) {
        try {
            if (autoCloseable != null) {
                autoCloseable.close();
            }
        } catch (Exception ignored) {
        }
    }

    public static void copyAndClose(InputStream input, OutputStream output) throws IOException {
        copy(input, output);
        closeQuietly(input);
        closeQuietly(output);
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
    }

    public static String getRealPath(Object obj) {
        if (obj instanceof Intent) {
            return getRealPathFromURI(((Intent) obj).getData());
        }
        if (obj instanceof Uri) {
            return getRealPathFromURI((Uri) obj);
        }
        return null;
    }

    private static String getRealPathFromURI(Uri uri) {
        if (uri == null) return null;
        File temp = null;
        try {
            // DISPLAY_NAME belongs to the document provider: never turn it into a file path.
            String suffix = ".bin";
            try (Cursor cursor = getAppContext().getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) {
                        String displayName = cursor.getString(column);
                        if (displayName != null) {
                            int dot = displayName.lastIndexOf('.');
                            if (dot >= 0 && dot < displayName.length() - 1) {
                                String extension = displayName.substring(dot);
                                if (extension.matches("\\.[A-Za-z0-9]{1,8}")) suffix = extension;
                            }
                        }
                    }
                }
            }

            temp = File.createTempFile("oc-import-", suffix, getAppContext().getFilesDir());
            try (InputStream input = getAppContext().getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("Unable to open imported document");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
            return temp.getAbsolutePath();
        } catch (Exception error) {
            Log.e("FileUtil", "Unable to import document", error);
            if (temp != null && !temp.delete()) {
                Log.w("FileUtil", "Unable to delete failed document import");
            }
            return null;
        }
    }

    public static boolean moveToOCHiddenDir(String source, String destination) {
        if (source == null || destination == null || source.isBlank() || destination.isBlank()
                || source.indexOf('\u0000') >= 0 || destination.indexOf('\u0000') >= 0) {
            return false;
        }
        // Replace only after a successful move: avoid deleting the existing image first.
        return Shell.cmd("mkdir -p " + shellQuote(Constants.XPOSED_RESOURCE_TEMP_DIR)
                + " && mv -f -- " + shellQuote(source) + " " + shellQuote(destination))
                .exec().isSuccess();
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    public static void launchFilePicker(ActivityResultLauncher<Intent> launcher, String type) {
        launchFilePicker(launcher, type, false);
    }

    public static void launchFilePicker(ActivityResultLauncher<Intent> launcher, String type, boolean allowMultiple) {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType(type);
        chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
        launcher.launch(chooseFile);
    }
}
