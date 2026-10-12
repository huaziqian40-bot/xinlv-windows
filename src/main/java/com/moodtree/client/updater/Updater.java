/* 心履 Windows 应用内更新（jpackage 便携版/安装版通用）—— **卡片确认制**。
 *
 * 启动时后台**只检查** https://phix.ing/api/v1/update/check?product=xinlv&platform=win，
 * 有新版本就弹卡片（版本号 + 本次更新内容 + 取消 / 跳过本版本 / 更新）。
 * **用户点「更新」之后**才走下面这套下载替换：
 *   1. 下载便携版 zip 到数据目录 .updates/
 *   2. SHA256 校验（与清单比对，不匹配丢弃）
 *   3. 解压到程序目录旁的 .update-staging/
 *   4. 写 updater.bat：等本进程退出 → 把新目录移到 XinLv/ 位置 → 重启 → 删自身
 *
 * 「取消」不记任何东西（下次启动还会提示）；「跳过本版本」把版本号写进 Config，
 * 该版本之后静默，但更高的版本仍会提示。
 *
 * 便携目录定位：jpackage 便携版运行时 user.dir 即安装目录（含 XinLv.exe）；
 * 安装版（%LOCALAPPDATA%\\Programs\\XinLv）同理。数据在 user.home/.moodtree，
 * 替换程序目录不影响任何用户数据。
 *
 * 仅 Windows；macOS 见 UpdaterMac。
 */
package com.moodtree.client.updater;

import com.moodtree.client.Config;

import javafx.application.Platform;

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
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {

    /** 与 pom.xml <version> 保持同步（发布时一起改） */
    public static final String APP_VERSION = "1.1.10";

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

            // 简易 JSON 解析：只取 latest_version / url / sha256 / size / release_notes
            String body = resp.body();
            String latest = jsonString(body, "latest_version");
            String url = jsonString(body, "url");
            String sha = jsonString(body, "sha256");
            if (latest.isEmpty() || url.isEmpty() || sha.isEmpty()) return false;
            if (latest.equals(APP_VERSION)) return false;
            // 用户曾「跳过本版本」：该版本不再提示（更高的新版本仍会提示）
            if (latest.equals(new Config().skippedUpdateVersion())) return false;

            // 记录待更新信息供卡片与 applyUpdate 使用
            pending(latest, url, sha, jsonString(body, "release_notes"));
            return true;
        } catch (Exception e) {
            return false; // 断网/服务器不可用：静默跳过，下次启动再试
        }
    }

    // ---------- 「关于软件」手动检查更新 ----------
    //
    // 启动检查把"没有新版本"和"检查失败"都压成 false，静默处理没问题；
    // 但手动检查必须把这两种**相反**的状态分开，否则断网会误显示成
    // 「已是最新版本」（用户最讨厌的经典 bug）。
    // 所以手动检查返回三态：已是最新 / 发现新版本 / 检查失败。

    /** 手动检查更新的三种结果。 */
    public enum UpdateCheckResult { UP_TO_DATE, NEW_VERSION, FAILED }

    // 防止「启动检查」和「手动检查」同时弹卡（叠出两个对话框）
    private static final AtomicBoolean CARD_IN_FLIGHT = new AtomicBoolean(false);

    /**
     * 纯分类逻辑：根据已拉取的清单字段判断检查结果。
     * 不碰网络（测试也走这里，用桩数据驱动）：
     *   - 拿不到版本号、或缺更新包必备字段 → FAILED（坏响应，绝不当"已是最新"）；
     *   - 版本相同、或用户已「跳过本版本」→ UP_TO_DATE；
     *   - 其余 → NEW_VERSION。
     */
    public static UpdateCheckResult classify(String latest, String url, String sha) {
        return classify(latest, url, sha, new Config());
    }

    /** 可注入 Config 的纯分类（带 Config 版本给测试用，避免读真实用户配置）。 */
    public static UpdateCheckResult classify(String latest, String url, String sha, Config cfg) {
        if (latest == null || latest.isEmpty()) return UpdateCheckResult.FAILED;      // 坏响应：没有版本号
        if (url == null || url.isEmpty() || sha == null || sha.isEmpty()) return UpdateCheckResult.FAILED; // 拿不到更新包
        if (latest.equals(APP_VERSION)) return UpdateCheckResult.UP_TO_DATE;
        if (latest.equals(cfg.skippedUpdateVersion())) return UpdateCheckResult.UP_TO_DATE; // 用户已跳过
        return UpdateCheckResult.NEW_VERSION;
    }

    /** 手动「检查更新」：任何一步不对（断网/超时/非 200/坏响应）都判 FAILED，绝不吞成"已是最新"。 */
    public static UpdateCheckResult manualCheck() {
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
            if (resp.statusCode() != 200) return UpdateCheckResult.FAILED;
            String body = resp.body();
            String latest = jsonString(body, "latest_version");
            String url = jsonString(body, "url");
            String sha = jsonString(body, "sha256");
            UpdateCheckResult r = classify(latest, url, sha);
            // 有新版本时暂存下载信息，供下方既有「确认卡片」流程使用
            if (r == UpdateCheckResult.NEW_VERSION) {
                pending(latest, url, sha, jsonString(body, "release_notes"));
            }
            return r;
        } catch (Exception e) {
            return UpdateCheckResult.FAILED; // 断网/超时：明确报「检查失败」，不是"已是最新"
        }
    }

    /** 手动检查暂存的新版本号（用于拼「发现新版本 vX」；无则空串）。 */
    public static String newVersion() {
        return pendingVersion == null ? "" : pendingVersion;
    }

    /** 检查结果 → 界面提示文案（纯映射，测试直接测）。 */
    public static String messageFor(UpdateCheckResult r, String newVersion) {
        return switch (r) {
            case UP_TO_DATE -> "已是最新版本";
            case NEW_VERSION -> "发现新版本 v" + (newVersion == null ? "" : newVersion);
            case FAILED -> "检查失败，请稍后再试";
        };
    }

    /**
     * 手动检查发现新版本后，走既有「确认卡片」流程（不重复拉取，直接弹卡）。
     * 在后台线程（Bg）调用；与启动卡片用 CARD_IN_FLIGHT 互斥，绝不叠两个对话框。
     */
    public static void runManualUpdateFlow() {
        if (!CARD_IN_FLIGHT.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                if (pendingUrl.isEmpty() || pendingSha.isEmpty()) return; // 没有待更新信息
                UpdateCard.Choice c = showCard();
                if (c == UpdateCard.Choice.SKIP) {
                    Config cfg = new Config();
                    cfg.setSkippedUpdateVersion(pendingVersion);
                    cfg.save();
                } else if (c == UpdateCard.Choice.UPDATE && applyUpdate()) {
                    // 给 updater.bat 一点时间接管，然后退出当前实例
                    Thread.sleep(1500);
                    System.exit(0);
                }
            } catch (Throwable ignored) { }
            finally { CARD_IN_FLIGHT.set(false); }
        }, "xinlv-updater-manual").start();
    }

    // 简单 JSON 字段提取（项目未引入 JSON 库时用最小实现；值必须是字符串）。
    // 正确处理反斜杠转义（引号、反斜杠、换行、Unicode 十六进制序列），
    // 避免被「更新内容里的引号」提前截断。
    private static String jsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        i = json.indexOf(':', i + needle.length());
        if (i < 0) return "";
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return "";
        i++; // 跳过开头的引号
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
                if (i >= json.length()) break;
                char e = json.charAt(i);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u': {
                        if (i + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (Exception ignored) { /* 非法转义序列保持原样 */ }
                        }
                        break;
                    }
                    default: sb.append(e); break;
                }
                i++;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // 待更新的下载信息（进程内暂存）
    private static volatile String pendingUrl = "";
    private static volatile String pendingSha = "";
    private static volatile String pendingVersion = "";
    private static volatile String pendingNotes = "";
    private static void pending(String v, String url, String sha, String notes) {
        pendingVersion = v;
        pendingUrl = url;
        pendingSha = sha;
        pendingNotes = notes;
    }

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

    /**
     * 后台线程检查更新；有新版则弹「确认卡片」，不自动下载。
     * 用户点「更新」才真正下载/替换；「取消」不动；「跳过本版本」记住该版本不再提示。
     */
    public static void checkAsyncAndExit() {
        if (!CARD_IN_FLIGHT.compareAndSet(false, true)) return; // 已有卡片在弹（如手动检查），别再叠一个
        Thread t = new Thread(() -> {
            try {
                if (!hasUpdate()) return;   // 只检查，绝不自动下载
                UpdateCard.Choice c = showCard();
                if (c == UpdateCard.Choice.SKIP) {
                    Config cfg = new Config();
                    cfg.setSkippedUpdateVersion(pendingVersion);
                    cfg.save();
                    return;
                }
                if (c == UpdateCard.Choice.UPDATE && applyUpdate()) {
                    // 给 updater.bat 一点时间接管，然后退出当前实例
                    Thread.sleep(1500);
                    System.exit(0);
                }
            } catch (Throwable ignored) { }
            finally { CARD_IN_FLIGHT.set(false); }
        }, "xinlv-updater-check");
        t.setDaemon(true);
        t.start();
    }

    /** 跳到 FX 线程弹卡片（阻塞当前后台线程直到用户作出选择）。 */
    private static UpdateCard.Choice showCard() throws Exception {
        FutureTask<UpdateCard.Choice> task = new FutureTask<>(() ->
                UpdateCard.showAndWait(pendingVersion, APP_VERSION, pendingNotes));
        Platform.runLater(task);
        return task.get();
    }
}
