/* 心履 Windows 应用内自动更新（jpackage 便携版/安装版通用）。
 *
 * 启动时后台检查 https://phix.ing/api/v1/update/check?product=xinlv&platform=win，
 * 有新版本则：
 *   1. 下载便携版 zip 到数据目录 .updates/
 *   2. SHA256 校验（与清单比对，不匹配丢弃）
 *   3. 解压到程序目录旁的 .update-staging/
 *   4. 写 updater.bat：等本进程退出 → 把新目录移到 XinLv/ 位置 → 重启 → 删自身
 *
 * 便携目录定位：jpackage 便携版运行时 user.dir 即安装目录（含 XinLv.exe）；
 * 安装版（%LOCALAPPDATA%\\Programs\\XinLv）同理。数据在 user.home/.moodtree，
 * 替换程序目录不影响任何用户数据。
 *
 * 仅 Windows：macOS 未签名不走自动替换（见 _macos 提示方案）。
 */
package com.moodtree.client.updater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {

    /** 与 pom.xml <version> 保持同步（发布时一起改） */
    public static final String APP_VERSION = "1.1.5";

    private static final String CHECK_URL =
            "https://phix.ing/api/v1/update/check?product=xinlv&platform=win";
    private static final String USER_AGENT = "XinLv-Windows-" + APP_VERSION;

    private Updater() { }

    /** 返回"是否需要更新"（网络失败/版本相同返回 false）。 */
    public static boolean hasUpdate() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(CHECK_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;

            // 简易 JSON 解析：只取 latest_version / url / sha256 / size
            String body = resp.body();
            String latest = jsonString(body, "latest_version");
            String url = jsonString(body, "url");
            String sha = jsonString(body, "sha256");
            if (latest.isEmpty() || url.isEmpty() || sha.isEmpty()) return false;
            if (latest.equals(APP_VERSION)) return false;

            // 记录待更新信息供 applyUpdate 使用
            pending(url, sha);
            return true;
        } catch (Exception e) {
            return false; // 断网/服务器不可用：静默跳过，下次启动再试
        }
    }

    // 简单 JSON 字段提取（项目未引入 JSON 库时用最小实现；值必须是字符串）
    private static String jsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        i = json.indexOf(':', i + key.length() + 2);
        if (i < 0) return "";
        i = json.indexOf('"', i);
        if (i < 0) return "";
        int j = json.indexOf('"', i + 1);
        if (j < 0) return "";
        return json.substring(i + 1, j);
    }

    // 待更新的下载信息（进程内暂存）
    private static volatile String pendingUrl = "";
    private static volatile String pendingSha = "";
    private static void pending(String url, String sha) { pendingUrl = url; pendingSha = sha; }

    /** 下载 → 校验 → 解压 → 写 updater.bat。成功则返回 true（调用方应立即退出当前进程）。 */
    public static boolean applyUpdate() {
        if (pendingUrl.isEmpty() || pendingSha.isEmpty()) return false;
        try {
            // 程序所在目录（jpackage：exe 旁）
            Path appDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            if (!Files.exists(appDir.resolve("XinLv.exe"))) return false; // 非标准布局不自动更新

            Path staging = appDir.resolve(".update-staging");
            Path zipPath = appDir.resolve(".update-staging.zip");
            Files.createDirectories(staging.getParent());

            // 1) 下载
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(pendingUrl))
                    .timeout(Duration.ofSeconds(600))
                    .header("User-Agent", USER_AGENT)
                    .GET().build();
            http.send(req, HttpResponse.BodyHandlers.ofFile(zipPath));

            // 2) SHA256 校验
            if (!sha256(zipPath).equalsIgnoreCase(pendingSha)) {
                Files.deleteIfExists(zipPath);
                return false;
            }

            // 3) 解压到 staging（zip 内顶层就是 XinLv.exe / runtime/ 等）
            Files.createDirectories(staging);
            unzip(zipPath, staging);
            if (!Files.exists(staging.resolve("XinLv.exe"))) {
                Files.deleteIfExists(zipPath);
                return false; // 解压结构不对
            }
            Files.deleteIfExists(zipPath);

            // 4) 写 updater.bat（等本进程退出 → 换目录（保留旧版备份）→ 重启 → 删自身）
            Path bat = staging.resolve("xinlv_updater.bat");
            String exeName = "XinLv.exe";
            // bat 里路径用**单反斜杠**（不需要转义；cmd 认单反斜杠路径）
            String app = appDir.toString();
            String stg = staging.toString();
            String oldBak = appDir.resolveSibling("XinLv.old").toString();
            Files.writeString(bat,
                    "@echo off\r\n"
                    + "rem 心履自动更新器（生成）\r\n"
                    + "cd /d \"%~dp0\"\r\n"
                    + ":wait\r\n"
                    + "tasklist /FI \"IMAGENAME eq " + exeName + "\" 2>nul | find /I \"" + exeName + "\" >nul\r\n"
                    + "if not errorlevel 1 (timeout /t 1 /nobreak >nul & goto wait)\r\n"
                    // 旧目录改名留作回滚备份（不是删除）；新目录移到原位
                    + "if exist \"" + oldBak + "\" rmdir /s /q \"" + oldBak + "\" >nul 2>nul\r\n"
                    + "if exist \"" + app + "\" ren \"" + app + "\" XinLv.old\r\n"
                    + "xcopy /s /e /q /y \"" + stg + "\" \"" + app + "\" >nul\r\n"
                    // 启动前做一次最小校验：exe 必须在
                    + "if not exist \"" + app + "\\" + exeName + "\" (\r\n"
                    + "  if exist \"" + oldBak + "\" ren \"" + oldBak + "\" XinLv\r\n"
                    + "  start \"\" \"" + app + "\\" + exeName + "\"\r\n"
                    + "  exit /b 1\r\n"
                    + ")\r\n"
                    + "start \"\" \"" + app + "\\" + exeName + "\"\r\n"
                    + "rmdir /s /q \"" + stg + "\" >nul 2>nul\r\n"
                    + "del /q \"%~f0\" >nul 2>nul\r\n",
                    java.nio.charset.StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", staging.resolve("xinlv_updater.bat").toString());
            pb.redirectErrorStream(true);
            pb.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void unzip(Path zip, Path outDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path target = outDir.resolve(e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream os = Files.newOutputStream(target)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /** 后台线程检查更新；返回 true 表示已接管（调用方应尽快退出）。 */
    public static void checkAsyncAndExit() {
        Thread t = new Thread(() -> {
            try {
                if (hasUpdate() && applyUpdate()) {
                    // 给 updater.bat 一点时间接管，然后退出当前实例
                    Thread.sleep(1500);
                    System.exit(0);
                }
            } catch (Exception ignored) { }
        }, "xinlv-auto-updater");
        t.setDaemon(true);
        t.start();
    }
}
