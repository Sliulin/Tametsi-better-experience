package tametsimod;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

final class GameGuard implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private GameGuard(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }

    static GameGuard acquire(Path root, String name) throws Exception {
        Files.createDirectories(root.resolve("mods"));
        FileChannel channel = FileChannel.open(root.resolve("mods/" + name + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try { lock = channel.tryLock(); }
        catch (OverlappingFileLockException ex) { channel.close(); throw new Exception("游戏或相关操作正在运行。", ex); }
        if (lock == null) { channel.close(); throw new Exception("游戏或相关操作正在运行，请正常退出后重试。"); }
        return new GameGuard(channel, lock);
    }

    static long pid() {
        return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
    }

    static void ensureStopped(Path root) throws Exception {
        ensureStopped(root, false);
    }
    static void ensureStopped(Path root, boolean allowWrapperParent) throws Exception {
        Path powershell = java.nio.file.Paths.get(System.getenv("SystemRoot"),
                "System32/WindowsPowerShell/v1.0/powershell.exe");
        java.util.List<String> command = new java.util.ArrayList<String>(java.util.Arrays.asList(powershell.toString(), "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File",
                root.resolve("mods/check-game.ps1").toString(), "-GameRoot", root.toString(),
                "-ExcludePid", Long.toString(pid())));
        if (allowWrapperParent) command.add("-AllowWrapperParent");
        Process scan = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!scan.waitFor(12, TimeUnit.SECONDS)) {
            scan.destroy();
            throw new Exception("检查游戏进程超时，请稍后重试。");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = scan.getInputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
        }
        // Token detection is ASCII, independent of the Windows console encoding.
        String output = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        if (scan.exitValue() != 0 || !output.contains("CHECK_OK")) {
            throw new Exception("无法确认游戏是否已退出。检查程序返回：\n" + output);
        }
        if (output.contains("RUNNING|")) throw new Exception("检测到 Tametsi 正在运行，请正常退出游戏后重试。");
    }

    @Override public void close() throws Exception { try { lock.release(); } finally { channel.close(); } }
}
