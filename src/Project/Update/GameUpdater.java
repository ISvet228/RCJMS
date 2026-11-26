package Project.Update;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.*;
import java.util.jar.Manifest;
import java.util.zip.*;

import Project.RCJMS;

public final class GameUpdater {
    //region Variables
    static final String WINDOWS_SCRIPT = "rcjms-update.ps1";
    static final String WINDOWS_PLAN = "rcjms-update.json";
    static final String UNIX_SCRIPT = "rcjms-update.sh";
    static final String DEFAULT_JAR_URL = "https://github.com/ISvet228/RCJMS/releases/download/Complete-Ver/RCJMS.jar";
    private static final String PENDING_NAME = "RCJMS.jar.new";
    private static final long CHECK_MILLIS = 30L * 60 * 1000;
    private static final long RACE_WAIT_MILLIS = 5000;
    private static final int TIMEOUT_MILLIS = 15000;
    private static final long MAX_DOWNLOAD_BYTES = 1L << 30;   // 1 GiB is far above any real build
    private static volatile BooleanSupplier restartBlocker = () -> false;
    private static volatile String declinedTag = "";
    //endregion

    //region Constructors
    private GameUpdater() {}
    //endregion

    //region Startup
    public static void setRestartBlocker(BooleanSupplier blocker) { restartBlocker = blocker; }
    public static void start() {
        if (Boolean.getBoolean("rcjms.noUpdate")) return;
        Path jar = runningJar();
        if (jar == null) return;
        Thread thread = new Thread(() -> loop(jar), "rcjms-update");
        thread.setDaemon(true);
        thread.start();
    }

    static String jarUrl() { return System.getProperty("rcjms.update.url", DEFAULT_JAR_URL); }

    static Path runningJar() {
        try {
            Path path = Paths.get(GameUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return null;
            if (!Files.isWritable(path.getParent())) return null;
            return path;
        } catch (Exception exception) { return null; }
    }

    private static void loop(Path jar) {
        while (true) {
            try {
                if (prepare(jar, GameUpdater::askToDownload)) {
                    while (restartBlocker.getAsBoolean()) if (!sleep(RACE_WAIT_MILLIS)) return;
                    restart(jar, jar.resolveSibling(PENDING_NAME));
                }
            } catch (Exception exception) { System.err.println("RCJMS update: " + exception.getMessage()); }
            if (!sleep(CHECK_MILLIS)) return;
        }
    }
    static boolean askToDownload(String tag) {
        while (RCJMS.instance == null) if (!sleep(1000)) return false;
        return UpdatePrompt.ask();
    }
    private static boolean sleep(long millis) {
        try { Thread.sleep(millis); return true; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
    }
    //endregion

    //region Checking
    static boolean prepare(Path jar) throws IOException { return prepare(jar, tag -> true); }
    static boolean prepare(Path jar, Predicate<String> asker) throws IOException {
        Path pending = jar.resolveSibling(PENDING_NAME);
        if (Files.exists(pending)) return true;
        Properties state = loadState();
        String tag = remoteTag(jarUrl());
        String local = contentHash(jar);
        if (tag.equals(state.getProperty("tag")) && local.equals(state.getProperty("remoteHash"))) return false;
        if (!tag.isEmpty() && tag.equals(declinedTag)) return false;
        if (!asker.test(tag)) {
            declinedTag = tag;
            return false;
        }
        download(jarUrl(), pending);
        String remote = isJar(pending) ? contentHash(pending) : "";
        state.setProperty("tag", tag);
        state.setProperty("remoteHash", remote);
        saveState(state);
        if (!remote.isEmpty() && !remote.equals(local)) return true;
        Files.deleteIfExists(pending);
        return false;
    }
    static String remoteTag(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            connection.setRequestMethod("HEAD");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + connection.getResponseCode());
            String etag = connection.getHeaderField("ETag");
            if (etag != null) return etag;
            return connection.getContentLengthLong() + "/" + connection.getHeaderField("Last-Modified");
        } finally { connection.disconnect(); }
    }

    static void download(String url, Path target) throws IOException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = open(url);
        try (InputStream in = new LimitedInputStream(connection.getInputStream(), MAX_DOWNLOAD_BYTES)) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(part);   // nothing is left behind after a failed or oversized download
        }
    }
    private static final class LimitedInputStream extends FilterInputStream {
        private long left;
        LimitedInputStream(InputStream in, long limit) { super(in); left = limit; }
        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && --left < 0) throw new IOException("update is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
            return b;
        }
        @Override public int read(byte[] buffer, int off, int len) throws IOException {
            int n = super.read(buffer, off, len);
            if (n > 0 && (left -= n) < 0) throw new IOException("update is larger than " + MAX_DOWNLOAD_BYTES + " bytes");
            return n;
        }
    }
    private static HttpURLConnection open(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !Boolean.getBoolean("rcjms.update.allowInsecure"))
            throw new IOException("update URL must use https: " + url);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        return connection;
    }
    static boolean isJar(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
            if (entry == null) return false;
            String mainClass;
            try (InputStream in = zip.getInputStream(entry)) { mainClass = new Manifest(in).getMainAttributes().getValue("Main-Class"); }
            return mainClass != null && zip.getEntry(mainClass.replace('.', '/') + ".class") != null;
        } catch (IOException exception) { return false; }
    }
    static String contentHash(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> names = zip.stream().filter(entry -> !entry.isDirectory() && !entry.getName().startsWith("META-INF/")).map(ZipEntry::getName).sorted().toList();
            for (String name : names) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
                }
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IOException(exception); }
    }
    //endregion

    //region State
    private static Path statePath() { return Paths.get(System.getProperty("user.home"), ".rcjms", "update.properties"); }
    private static Properties loadState() {
        Properties state = new Properties();
        try (InputStream in = Files.newInputStream(statePath())) { state.load(in); }
        catch (IOException ignored) { }
        return state;
    }
    private static void saveState(Properties state) throws IOException {
        Files.createDirectories(statePath().getParent());
        try (OutputStream out = Files.newOutputStream(statePath())) { state.store(out, "RCJMS update state"); }
    }
    //endregion

    //region Restart
    static void restart(Path jar, Path pending) throws IOException {
        restartCommand(jar, pending, isWindows(), ProcessHandle.current().pid(), launchCommand(jar)).start();
        System.exit(0);
    }
    static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    static ProcessBuilder restartCommand(Path jar, Path pending, boolean windows, long pid, List<String> launch) throws IOException {
        Path folder = jar.getParent();
        if (windows) {
            Files.writeString(folder.resolve(WINDOWS_PLAN), planJson(jar, pending, pid, launch), StandardCharsets.UTF_8);
            Files.writeString(folder.resolve(WINDOWS_SCRIPT), WINDOWS_SCRIPT_TEXT, StandardCharsets.UTF_8);
            return new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", WINDOWS_SCRIPT).directory(folder.toFile());
        }
        Files.writeString(folder.resolve(UNIX_SCRIPT), unixScript(pid, pending, jar, launch), StandardCharsets.UTF_8);
        return new ProcessBuilder("sh", UNIX_SCRIPT).directory(folder.toFile());
    }
    static List<String> launchCommand(Path jar) {
        Path appFolder = jar.getParent();
        Path launcher = appFolder.getParent() == null ? null : appFolder.getParent().resolve("RCJMS.exe");
        if (appFolder.getFileName().toString().equalsIgnoreCase("app") && launcher != null && Files.exists(launcher)) { return List.of(launcher.toString()); }
        String java = ProcessHandle.current().info().command().orElse("java");
        return List.of(java, "-jar", jar.toString());
    }
    static String planJson(Path jar, Path pending, long pid, List<String> launch) {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"pid\": ").append(pid).append(",\n");
        json.append("  \"pending\": ").append(jsonString(pending.toString())).append(",\n");
        json.append("  \"jar\": ").append(jsonString(jar.toString())).append(",\n");
        json.append("  \"file\": ").append(jsonString(launch.getFirst())).append(",\n");
        json.append("  \"args\": [");
        for (int i = 1; i < launch.size(); i++) {
            if (i > 1) json.append(", ");
            json.append(jsonString(launch.get(i)));
        }
        return json.append("]\n}\n").toString();
    }

    private static String jsonString(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
    private static final String WINDOWS_SCRIPT_TEXT = """
            $ErrorActionPreference = 'SilentlyContinue'
            $folder = $PSScriptRoot
            $planPath = Join-Path $folder 'rcjms-update.json'
            $plan = Get-Content -LiteralPath $planPath -Raw -Encoding UTF8 | ConvertFrom-Json
            try { Wait-Process -Id $plan.pid -ErrorAction Stop } catch { }
            for ($i = 0; $i -lt 60; $i++) {
                try {
                    Move-Item -LiteralPath $plan.pending -Destination $plan.jar -Force -ErrorAction Stop
                    break
                } catch { Start-Sleep -Seconds 1 }
            }
            $argList = @($plan.args | ForEach-Object { '"' + $_ + '"' })
            if ($argList.Count -gt 0) { Start-Process -FilePath $plan.file -ArgumentList $argList }
            else { Start-Process -FilePath $plan.file }
            Remove-Item -LiteralPath $planPath -Force
            Remove-Item -LiteralPath $PSCommandPath -Force
            """;
    private static String unixScript(long pid, Path pending, Path jar, List<String> launch) {
        StringBuilder command = new StringBuilder();
        for (String part : launch) command.append(shellQuote(part)).append(' ');
        return """
                #!/bin/sh
                while kill -0 %d 2>/dev/null; do sleep 1; done
                tries=0
                until mv -f %s %s 2>/dev/null; do
                    tries=$((tries + 1))
                    [ "$tries" -ge 60 ] && break
                    sleep 1
                done
                %s>/dev/null 2>&1 &
                rm -f "$0"
                """.formatted(pid, shellQuote(pending.toString()), shellQuote(jar.toString()), command);
    }
    private static String shellQuote(String text) { return "'" + text.replace("'", "'\\''") + "'"; }
    //endregion
}