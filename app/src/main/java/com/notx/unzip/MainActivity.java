package com.notx.unzip;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.Gravity;
import android.database.Cursor;
import android.provider.OpenableColumns;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_NPK = 101;
    private static final int CREATE_ZIP = 102;
    private static final int PICK_TEXTURE_ZIP = 103;
    private static final int CREATE_PNG_ZIP = 104;
    private Uri selectedUri;
    private Uri selectedTextureZipUri;
    private File convertedZipFile;
    private File unpackDir;
    private File zipFile;
    private TextView status;
    private Button chooseButton, extractButton, saveButton;
    private Button chooseTextureZipButton, convertTextureZipButton, savePngZipButton;
    private Button renameZipKtxButton;
    private CheckBox jpgCheck;
    private ProgressBar progress;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private File pathDict;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
        pathDict = new File(getFilesDir(), "pathdict.txt.gz");
        // Copy the bundled path dictionary into app-private storage. Any failure is
        // checked again before extraction and shown to the user instead of hidden.
        try { ensurePathDict(); } catch (Exception ignored) { }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(247,248,250));

        TextView title = new TextView(this);
        title.setText("NOTX UnZip");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(28,35,48));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("蛋仔派对 NPK 贴图解包 · Android");
        sub.setTextSize(14); sub.setTextColor(Color.rgb(90,100,115));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = dp(24); root.addView(sub, subLp);

        chooseButton = new Button(this); chooseButton.setText("① 选择 .npk 文件");
        root.addView(chooseButton, matchWrap());
        jpgCheck = new CheckBox(this); jpgCheck.setText("导出为 JPG（透明背景会变成白色）");
        root.addView(jpgCheck, matchWrap());

        extractButton = new Button(this); extractButton.setText("② 开始提取贴图");
        LinearLayout.LayoutParams eLp = matchWrap(); eLp.topMargin = dp(8);
        root.addView(extractButton, eLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLp = matchWrap(); pLp.topMargin = dp(12);
        root.addView(progress, pLp);

        status = new TextView(this);
        status.setText("请选择一个蛋仔派对 PC 版 NPK 文件。建议先用较小的资源包测试。");
        status.setTextSize(14); status.setTextColor(Color.rgb(50,60,75));
        status.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams sLp = matchWrap(); sLp.topMargin = dp(16);
        root.addView(status, sLp);

        saveButton = new Button(this); saveButton.setText("③ 保存 ZIP 到手机");
        saveButton.setEnabled(false);
        LinearLayout.LayoutParams zLp = matchWrap(); zLp.topMargin = dp(12);
        root.addView(saveButton, zLp);

        TextView convertTitle = new TextView(this);
        convertTitle.setText("KTX / ASTC 转 PNG（离线批量）");
        convertTitle.setTextSize(18);
        convertTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        convertTitle.setTextColor(Color.rgb(28,35,48));
        LinearLayout.LayoutParams ctLp = matchWrap(); ctLp.topMargin = dp(28);
        root.addView(convertTitle, ctLp);

        chooseTextureZipButton = new Button(this);
        chooseTextureZipButton.setText("④ 选择贴图 ZIP 压缩包");
        LinearLayout.LayoutParams czLp = matchWrap(); czLp.topMargin = dp(8);
        root.addView(chooseTextureZipButton, czLp);

        convertTextureZipButton = new Button(this);
        convertTextureZipButton.setText("⑤ 离线批量转换为 PNG");
        convertTextureZipButton.setEnabled(false);
        LinearLayout.LayoutParams cvLp = matchWrap(); cvLp.topMargin = dp(6);
        root.addView(convertTextureZipButton, cvLp);

        savePngZipButton = new Button(this);
        savePngZipButton.setText("⑥ 保存转换结果 ZIP");
        savePngZipButton.setEnabled(false);
        LinearLayout.LayoutParams spLp = matchWrap(); spLp.topMargin = dp(6);
        root.addView(savePngZipButton, spLp);

        TextView renameTitle = new TextView(this);
        renameTitle.setText("ZIP 文件后缀批量修改");
        renameTitle.setTextSize(18);
        renameTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        renameTitle.setTextColor(Color.rgb(28,35,48));
        LinearLayout.LayoutParams rtLp = matchWrap(); rtLp.topMargin = dp(28);
        root.addView(renameTitle, rtLp);

        renameZipKtxButton = new Button(this);
        renameZipKtxButton.setText("⑦ 将所选 ZIP 内所有文件改为 .ktx（覆盖原包）");
        renameZipKtxButton.setEnabled(false);
        LinearLayout.LayoutParams rkLp = matchWrap(); rkLp.topMargin = dp(8);
        root.addView(renameZipKtxButton, rkLp);

        TextView renameNote = new TextView(this);
        renameNote.setText("先用上方“选择贴图 ZIP 压缩包”选中压缩包，再点此按钮。软件会在内部临时处理后覆盖原 ZIP，不会另外生成一个供你手动保存的 ZIP。文件内容不转换，只修改压缩包内的文件名；目录结构保持不变。建议先备份重要文件。部分云盘/只读位置可能不支持覆盖。 ");
        renameNote.setTextSize(12); renameNote.setTextColor(Color.rgb(110,115,125));
        LinearLayout.LayoutParams rnLp = matchWrap(); rnLp.topMargin = dp(6);
        root.addView(renameNote, rnLp);

        TextView note = new TextView(this);
        note.setText("说明：只提取可识别的贴图。特殊 DDS/KTX 可能保留原格式；游戏更新后索引密钥或格式变化可能导致解包失败。请确保有足够的存储空间。");
        note.setTextSize(12); note.setTextColor(Color.rgb(110,115,125));
        LinearLayout.LayoutParams nLp = matchWrap(); nLp.topMargin = dp(24);
        root.addView(note, nLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        chooseButton.setOnClickListener(v -> chooseNpk());
        extractButton.setOnClickListener(v -> extract());
        saveButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "NOTX-UnZip-textures.zip");
            startActivityForResult(i, CREATE_ZIP);
        });
        chooseTextureZipButton.setOnClickListener(v -> chooseTextureZip());
        convertTextureZipButton.setOnClickListener(v -> convertTextureZip());
        savePngZipButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "NOTX-textures-PNG.zip");
            startActivityForResult(i, CREATE_PNG_ZIP);
        });
        renameZipKtxButton.setOnClickListener(v -> renameSelectedZipEntriesToKtx());
    }

    private synchronized void ensurePathDict() throws Exception {
        if (pathDict == null) pathDict = new File(getFilesDir(), "pathdict.txt.gz");
        if (pathDict.exists() && pathDict.length() > 0) return;
        if (pathDict.exists() && !pathDict.delete()) {
            throw new java.io.IOException("无法删除损坏的路径索引文件：" + pathDict.getAbsolutePath());
        }
        // Android's asset packaging strips the .gz suffix and stores this asset as
        // assets/pathdict.txt. Re-compress it in app-private storage because
        // PathResolver.from_file() expects a gzip-compressed dictionary.
        try (InputStream in = getAssets().open("pathdict.txt");
             OutputStream out = new java.util.zip.GZIPOutputStream(new FileOutputStream(pathDict))) {
            byte[] b = new byte[32768]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        } catch (Exception e) {
            if (pathDict.exists()) pathDict.delete();
            throw new java.io.IOException("APK 内缺少 assets/pathdict.txt。请确认 app/src/main/assets/pathdict.txt.gz 已提交并重新构建 APK。", e);
        }
        if (!pathDict.exists() || pathDict.length() == 0) {
            throw new java.io.IOException("路径索引文件复制后为空，请重新安装 APK。");
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void chooseNpk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream"});
        startActivityForResult(i, PICK_NPK);
    }

    private void chooseTextureZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(i, PICK_TEXTURE_ZIP);
    }

    private File ensureAstcenc() throws Exception {
        // The ASTC CLI is packaged as an executable in jniLibs so Android extracts
        // it into nativeLibraryDir, rather than trying to execute a file under files/.
        String abi = null;
        for (String supported : android.os.Build.SUPPORTED_ABIS) {
            if (supported.equals("arm64-v8a")) { abi = supported; break; }
        }
        if (abi == null) throw new java.io.IOException("当前设备不是 arm64-v8a，暂不支持 ASTC 解码。");

        File exe = new File(getApplicationInfo().nativeLibraryDir, "libastcenc.so");
        if (!exe.isFile() || exe.length() == 0) {
            throw new java.io.IOException("APK 未正确打包 ASTC 解码器：" + exe.getAbsolutePath()
                    + "。请确认 Actions 将解码器放入 app/src/main/jniLibs/arm64-v8a/libastcenc.so 并重新构建。");
        }
        if (!exe.canExecute()) {
            // Native libraries should be extracted with executable permissions.
            // If not, fail with a useful message rather than silently attempting conversion.
            throw new java.io.IOException("ASTC 解码器没有执行权限：" + exe.getAbsolutePath());
        }
        return exe;
    }

    private void convertTextureZip() {
        if (selectedTextureZipUri == null) { status.setText("请先选择贴图 ZIP 压缩包。"); return; }
        chooseTextureZipButton.setEnabled(false); convertTextureZipButton.setEnabled(false);
        savePngZipButton.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setText("正在离线转换 KTX / ASTC 纹理，请保持应用打开……大压缩包可能需要较长时间。");
        worker.execute(() -> {
            try {
                File input = new File(getCacheDir(), "textures_input.zip");
                try (InputStream in = getContentResolver().openInputStream(selectedTextureZipUri);
                     OutputStream out = new FileOutputStream(input)) {
                    if (in == null) throw new java.io.IOException("无法读取所选 ZIP 文件。");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                File astcenc = ensureAstcenc();
                convertedZipFile = new File(getCacheDir(), "NOTX-textures-PNG.zip");
                if (convertedZipFile.exists()) convertedZipFile.delete();
                PyObject module = Python.getInstance().getModule("batch_convert");
                String summary = module.callAttr("convert_zip", input.getAbsolutePath(),
                        convertedZipFile.getAbsolutePath(), astcenc.getAbsolutePath()).toString();
                runOnUiThread(() -> {
                    status.setText(summary + "\n\n点击“保存转换结果 ZIP”导出。");
                    savePngZipButton.setEnabled(convertedZipFile.exists() && convertedZipFile.length() > 0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("转换失败：\n" + e.getMessage()
                        + "\n\n如果提示找不到 ASTC 解码器，请确认 GitHub Actions 已成功构建并打包 astcenc。"));
            } finally {
                runOnUiThread(() -> {
                    chooseTextureZipButton.setEnabled(true); convertTextureZipButton.setEnabled(selectedTextureZipUri != null);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void renameSelectedZipEntriesToKtx() {
        if (selectedTextureZipUri == null) {
            status.setText("请先选择要修改的 ZIP 压缩包。");
            return;
        }
        renameZipKtxButton.setEnabled(false);
        chooseTextureZipButton.setEnabled(false);
        convertTextureZipButton.setEnabled(false);
        savePngZipButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("正在修改 ZIP 内部文件名并覆盖原压缩包，请勿关闭应用……");
        final Uri sourceUri = selectedTextureZipUri;
        worker.execute(() -> {
            File source = new File(getCacheDir(), "rename_source.zip");
            File rewritten = new File(getCacheDir(), "rename_rewritten.zip");
            try {
                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = new FileOutputStream(source)) {
                    if (in == null) throw new java.io.IOException("无法读取所选 ZIP 文件。");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) != -1) out.write(b, 0, n);
                }
                int[] counts = rewriteZipExtensions(source, rewritten);
                // ZIP archives must be structurally rewritten to change entry names.
                // The temporary file stays private to the app; the selected document is overwritten.
                try (InputStream in = new FileInputStream(rewritten);
                     OutputStream out = getContentResolver().openOutputStream(sourceUri, "wt")) {
                    if (out == null) throw new java.io.IOException("当前文件位置不支持覆盖写入。请把 ZIP 移到手机本地存储后重试。");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) != -1) out.write(b, 0, n);
                    out.flush();
                }
                runOnUiThread(() -> {
                    status.setText("已直接覆盖原 ZIP！\n改名文件：" + counts[0]
                            + " 个\n目录：" + counts[1] + " 个\n所有文件后缀已改为 .ktx，文件内容未转换。");
                    // Force the user to reselect before another operation, so the displayed selection is refreshed.
                    renameZipKtxButton.setEnabled(false);
                    convertTextureZipButton.setEnabled(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("修改 ZIP 失败：" + e.getMessage()
                        + "\n如果压缩包位于云盘或只读目录，请先复制到手机本地存储再操作。"));
            } finally {
                if (source.exists()) source.delete();
                if (rewritten.exists()) rewritten.delete();
                runOnUiThread(() -> {
                    chooseTextureZipButton.setEnabled(true);
                    renameZipKtxButton.setEnabled(selectedTextureZipUri != null);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private int[] rewriteZipExtensions(File source, File target) throws Exception {
        int renamed = 0;
        int directories = 0;
        java.util.HashSet<String> names = new java.util.HashSet<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(source);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(target))) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[65536];
            while (entries.hasMoreElements()) {
                ZipEntry original = entries.nextElement();
                String oldName = original.getName();
                boolean isDirectory = original.isDirectory() || oldName.endsWith("/");
                String newName = oldName;
                if (isDirectory) {
                    directories++;
                } else {
                    int slash = oldName.lastIndexOf('/');
                    String parent = slash >= 0 ? oldName.substring(0, slash + 1) : "";
                    String leaf = slash >= 0 ? oldName.substring(slash + 1) : oldName;
                    int dot = leaf.lastIndexOf('.');
                    String stem = (dot > 0) ? leaf.substring(0, dot) : leaf;
                    newName = parent + stem + ".ktx";
                    String candidate = newName;
                    int duplicate = 2;
                    while (names.contains(candidate)) {
                        candidate = parent + stem + "_" + duplicate++ + ".ktx";
                    }
                    newName = candidate;
                    renamed++;
                }
                if (!names.add(newName)) {
                    throw new java.io.IOException("ZIP 内存在无法区分的重复路径：" + newName);
                }
                ZipEntry replacement = new ZipEntry(newName);
                if (original.getTime() >= 0) replacement.setTime(original.getTime());
                if (!isDirectory && original.getComment() != null) replacement.setComment(original.getComment());
                out.putNextEntry(replacement);
                if (!isDirectory) {
                    try (InputStream in = zip.getInputStream(original)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
                out.closeEntry();
            }
        }
        return new int[]{renamed, directories};
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_NPK) {
            selectedUri = uri;
            status.setText("已选择：" + displayName(uri) + "\n点击“开始提取贴图”。");
            saveButton.setEnabled(false);
        } else if (requestCode == PICK_TEXTURE_ZIP) {
            selectedTextureZipUri = uri;
            status.setText("已选择贴图包：" + displayName(uri) + "\n点击“离线批量转换为 PNG”。");
            convertTextureZipButton.setEnabled(true);
            renameZipKtxButton.setEnabled(true);
            savePngZipButton.setEnabled(false);
        } else if (requestCode == CREATE_PNG_ZIP) {
            Uri target = uri;
            worker.execute(() -> {
                try (InputStream in = new FileInputStream(convertedZipFile);
                     OutputStream out = getContentResolver().openOutputStream(target)) {
                    if (out == null) throw new java.io.IOException("无法写入所选位置。");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    runOnUiThread(() -> status.setText("转换结果 ZIP 已保存到你选择的位置。"));
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("保存转换 ZIP 失败：" + e.getMessage()));
                }
            });
        } else if (requestCode == CREATE_ZIP) {
            Uri target = uri;
            worker.execute(() -> {
                try (InputStream in = new FileInputStream(zipFile);
                     OutputStream out = getContentResolver().openOutputStream(target)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    runOnUiThread(() -> status.setText("ZIP 已保存到你选择的位置。"));
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("保存失败：" + e.getMessage()));
                }
            });
        }
    }

    private String displayName(Uri uri) {
        String name = "selected.npk";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void extract() {
        if (selectedUri == null) { status.setText("请先选择 NPK 文件。"); return; }
        chooseButton.setEnabled(false); extractButton.setEnabled(false);
        saveButton.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setText("正在复制文件并解析索引，请保持应用打开……");
        worker.execute(() -> {
            try {
                File input = new File(getCacheDir(), "selected.npk");
                try (InputStream in = getContentResolver().openInputStream(selectedUri);
                     OutputStream out = new FileOutputStream(input)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                ensurePathDict();
                unpackDir = new File(getCacheDir(), "textures_out");
                deleteTree(unpackDir); unpackDir.mkdirs();
                Python py = Python.getInstance();
                PyObject module = py.getModule("mobile_unpack");
                PyObject result = module.callAttr("extract_images", input.getAbsolutePath(),
                        unpackDir.getAbsolutePath(), pathDict.getAbsolutePath(), jpgCheck.isChecked());
                String summary = result.toString();
                zipFile = new File(getCacheDir(), "NOTX-UnZip-textures.zip");
                if (zipFile.exists()) zipFile.delete();
                zipDirectory(unpackDir, zipFile);
                runOnUiThread(() -> {
                    status.setText("处理完成：\n" + summary + "\n\n点击“保存 ZIP 到手机”导出结果。");
                    saveButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("解包失败：\n" + e.getMessage()
                        + "\n\n如果提示缺少 zstandard/Pillow，或索引格式不匹配，请检查构建日志和游戏版本。"));
            } finally {
                runOnUiThread(() -> {
                    chooseButton.setEnabled(true); extractButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void zipDirectory(File root, File zip) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            addToZip(root, root, zos);
        }
    }
    private void addToZip(File root, File file, ZipOutputStream zos) throws Exception {
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) addToZip(root, child, zos);
            else {
                String name = root.toURI().relativize(child.toURI()).getPath();
                zos.putNextEntry(new ZipEntry(name));
                try (FileInputStream in = new FileInputStream(child)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) zos.write(b, 0, n);
                }
                zos.closeEntry();
            }
        }
    }
    private void deleteTree(File f) {
        if (f.isDirectory()) { File[] children = f.listFiles(); if (children != null) for (File c : children) deleteTree(c); }
        if (f.exists()) f.delete();
    }
}
