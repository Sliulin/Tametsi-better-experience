package tametsimod;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Runs before the original launcher main, for both enabled and disabled sessions. */
final class NativeStartup {
    private static GameGuard lock;
    private NativeStartup() {}
    static void start(Instrumentation instrumentation) {
        Path run = null;
        try {
            Path root = NativeIntegration.runtimeJar().getParent().getParent().getParent().toRealPath();
            if (!java.nio.file.Paths.get("").toRealPath().equals(root))
                throw new Exception("游戏工作目录不正确，请从安装目录启动 tametsi.exe。");
            run = root.resolve("mods/runs/" + UUID.randomUUID()); Files.createDirectories(run);
            java.io.PrintStream log = new java.io.PrintStream(Files.newOutputStream(run.resolve("game.log")), true, "UTF-8");
            System.setOut(log); System.setErr(log);
            Settings settings = new Settings(root); RuntimeOptions.configure(settings);
            lock = GameGuard.acquire(root, "game");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { if (lock != null) lock.close(); } catch (Exception ex) { ex.printStackTrace(); }
            }));
            GameGuard.ensureStopped(root, true);
            // Install transforms before reflection preflight loads Game and MouseHandler.
            if (settings.enabled()) { ModAgent.activate(instrumentation); Bootstrap.verifyGame(root); }
            Path backup = null;
            if (RuntimeOptions.enabled(Feature.BACKUP)) backup = AutomaticBackups.create(root);
            if (settings.enabled()) {
                FlagIcons.preflight();
                ModRuntime.install();
            }
            Properties ready = new Properties();
            ready.setProperty("mode", settings.enabled() ? "mod" : "vanilla");
            ready.setProperty("pid", Long.toString(GameGuard.pid()));
            ready.setProperty("native.exe", "true");
            ready.setProperty("input.installed", Boolean.toString(ModRuntime.installed()));
            for (Feature feature : Feature.values()) ready.setProperty(feature.key, Boolean.toString(RuntimeOptions.enabled(feature)));
            if (backup != null) ready.setProperty("backup", root.relativize(backup).toString());
            FilesSupport.write(run.resolve("ready.properties"), ready);
            System.out.println("Tametsi Mod v4: original exe startup, mode=" + ready.getProperty("mode"));
            // Isolation tests invoke the real executable, then stop before opening its UI.
            if (Boolean.getBoolean("tametsimod.startupProbe")) {
                FilesSupport.write(root.resolve("mods/native-probe.properties"), ready);
                System.exit(0);
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            try {
                if (run != null) Files.write(run.resolve("error.txt"), ex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (!Boolean.getBoolean("tametsimod.startupProbe")) javax.swing.JOptionPane.showMessageDialog(null,
                        "Mod 启动未完成：" + ex.getMessage() + "\n请打开 Mod 管理器检查设置。", "Tametsi Mod", javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {}
            System.exit(1);
        }
    }
}
