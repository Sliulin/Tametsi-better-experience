package tametsimod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.swing.JOptionPane;

/** One fresh process per game session; changing settings never mutates a running game. */
public final class Bootstrap {
    static final String SUPPORTED_SHA256 = "b8f4e5453f8f12b25c28c2796d6b2ec0a066fa9ae64cff98bf72dccff0eca9da";
    private static GameGuard gameLock;

    static void verifyGame(Path root) throws Exception {
        if (!SUPPORTED_SHA256.equals(FilesSupport.sha256(root.resolve("tametsi.exe")))) {
            throw new Exception("当前游戏文件与本 Mod 支持的版本不一致。可关闭 Mod 启动原版。");
        }
        ModRuntime.preflight();
    }

    public static void main(String[] args) {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Path run = null;
        try {
            if (args.length != 2 || !("--mod".equals(args[0]) || "--vanilla".equals(args[0]))) {
                throw new Exception("请通过 Tametsi Mod 启动器启动游戏。");
            }
            run = Paths.get(args[1]).toAbsolutePath().normalize();
            if (!run.startsWith(root.resolve("mods/runs"))) throw new Exception("启动状态路径无效。");
            gameLock = GameGuard.acquire(root, "game");
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() {
                    try { if (gameLock != null) gameLock.close(); }
                    catch (Exception ex) { ex.printStackTrace(); }
                }
            }));
            GameGuard.ensureStopped(root);
            boolean enabled = "--mod".equals(args[0]);
            Settings settings = new Settings(root);
            RuntimeOptions.configure(settings);
            if (enabled) {
                verifyGame(root);
                if (RuntimeOptions.enabled(Feature.BACKUP)) AutomaticBackups.create(root);
                ModRuntime.install();
            }
            launcher.Launcher.main(new String[0]);
            Properties ready = new Properties();
            ready.setProperty("mode", enabled ? "mod" : "vanilla");
            ready.setProperty("pid", Long.toString(GameGuard.pid()));
            FilesSupport.write(run.resolve("ready.properties"), ready);
        } catch (Throwable ex) {
            ex.printStackTrace();
            try {
                if (run != null) {
                    Files.createDirectories(run);
                    Files.write(run.resolve("error.txt"), ex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                JOptionPane.showMessageDialog(null, "游戏启动失败：\n" + ex.getMessage()
                        + "\n可以关闭 Mod 后重试原版模式。", "Tametsi Mod", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) { }
            System.exit(1);
        }
    }
}
