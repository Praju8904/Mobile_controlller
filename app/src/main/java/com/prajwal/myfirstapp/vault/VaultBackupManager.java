package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a ZIP backup archive of all vault encrypted files.
 *
 * <p>The vault files are already AES-encrypted, so packing them into a ZIP
 * provides a convenient single-file export while retaining the existing
 * layer of encryption ("double encryption").  Standard Java does not support
 * password-protected ZIPs natively; the {@code password} parameter is
 * reserved for future use and is not embedded in the output file.
 */
public class VaultBackupManager {

    private static final String TAG = "VaultBackupManager";

    /** Progress listener for long-running archive operations. */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }

    private VaultBackupManager() {}

    /**
     * Returns the sum of all encrypted file sizes stored in the vault.
     *
     * @param repo The active {@link MediaVaultRepository}.
     * @return Total bytes, or 0 if {@code repo} is null / no files exist.
     */
    public static long estimateBackupSize(MediaVaultRepository repo) {
        if (repo == null) return 0L;
        List<VaultFileItem> files = repo.getAllFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (VaultFileItem f : files) {
            total += f.encryptedSize;
        }
        return total;
    }

    /**
     * Creates a ZIP archive of all vault encrypted files and places it in
     * the app's cache directory.
     *
     * <p>Each ZIP entry uses the vault file's internal name so that the
     * archive can be restored without leaking original file names.
     *
     * @param ctx      Android context.
     * @param repo     Active vault repository.
     * @param password Reserved for future use (not embedded in the output file).
     * @param callback Optional progress listener; may be {@code null}.
     * @return The created {@link File}, or {@code null} on failure.
     */
    public static File createBackupArchive(Context ctx, MediaVaultRepository repo,
                                           String password, ProgressCallback callback) {
        if (ctx == null || repo == null) return null;

        List<VaultFileItem> files = repo.getAllFiles();
        if (files == null) files = java.util.Collections.emptyList();

        String timestamp  = String.valueOf(System.currentTimeMillis());
        String zipName    = "vault_backup_" + timestamp + ".zip";
        File   outputFile = new File(ctx.getCacheDir(), zipName);

        File vaultFilesDir = repo.getFilesDir();

        int total   = files.size();
        int current = 0;

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream  zos = new ZipOutputStream(fos)) {

            // password parameter reserved for future use; not embedded in archive.
            for (VaultFileItem item : files) {
                if (item.vaultFileName == null || item.vaultFileName.isEmpty()) {
                    current++;
                    if (callback != null) callback.onProgress(current, total);
                    continue;
                }

                File encFile = new File(vaultFilesDir, item.vaultFileName);
                if (!encFile.exists()) {
                    current++;
                    if (callback != null) callback.onProgress(current, total);
                    continue;
                }

                ZipEntry entry = new ZipEntry(item.vaultFileName);
                zos.putNextEntry(entry);
                copyFileToStream(encFile, zos);
                zos.closeEntry();

                current++;
                if (callback != null) callback.onProgress(current, total);
            }

            zos.finish();
            return outputFile;

        } catch (IOException e) {
            Log.e(TAG, "Failed to create backup archive", e);
            if (outputFile.exists()) {
                if (!outputFile.delete()) {
                    Log.w(TAG, "Failed to delete incomplete backup file: " + outputFile.getPath());
                }
            }
            return null;
        }
    }

    // ─── Private helper ───────────────────────────────────────────

    private static void copyFileToStream(File src, ZipOutputStream zos) throws IOException {
        byte[] buffer = new byte[8192];
        try (FileInputStream fis = new FileInputStream(src)) {
            int len;
            while ((len = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
        }
    }
}
