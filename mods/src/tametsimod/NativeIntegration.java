package tametsimod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/** Launch4j's supported sidecar JVM options; the original executable is unchanged. */
final class NativeIntegration {
    private static final String BEGIN = "# TAMETSI_MOD_BEGIN", END = "# TAMETSI_MOD_END";
    private NativeIntegration() {}
    static Path runtimeJar() throws Exception {
        return Paths.get(NativeIntegration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
    }
    static void apply(Path root, Settings settings, boolean enabled, Map<Feature, Boolean> features) throws Exception {
        Bootstrap.verifyGame(root);
        Path runtime = runtimeJar();
        if (!runtime.startsWith(root.resolve("mods/versions")) || !Files.isRegularFile(runtime))
            throw new Exception("管理器必须位于游戏 mods/versions 目录中。");
        Path ini = root.resolve("tametsi.l4j.ini");
        byte[] previous = Files.exists(ini) ? Files.readAllBytes(ini) : new byte[0];
        Path installation = root.resolve("mods/installation"); Files.createDirectories(installation);
        Path original = installation.resolve("tametsi.l4j.ini.original");
        if (!Files.exists(original)) Files.write(original, previous, java.nio.file.StandardOpenOption.CREATE_NEW);
        // ISO-8859-1 roundtrips arbitrary existing bytes; our block is ASCII only.
        String text = new String(previous, StandardCharsets.ISO_8859_1);
        int begin = text.indexOf(BEGIN);
        if (begin >= 0) {
            int end = text.indexOf(END, begin);
            if (end < 0 || text.indexOf(BEGIN, begin + BEGIN.length()) >= 0)
                throw new Exception("启动参数中的 Mod 配置块损坏，请查看 tametsi.l4j.ini。");
            text = text.substring(0, begin) + text.substring(end + END.length());
        }
        String relative = root.relativize(runtime).toString().replace('\\', '/');
        String next = text + (text.endsWith("\n") || text.isEmpty() ? "" : "\r\n")
                + BEGIN + "\r\n-javaagent:" + relative + "=native\r\n" + END + "\r\n";
        Path temp = Files.createTempFile(root, "mod-launch-", ".tmp");
        Files.write(temp, next.getBytes(StandardCharsets.ISO_8859_1));
        FilesSupport.replace(temp, ini);
        try { settings.save(enabled, features); }
        catch (Exception ex) {
            Path rollback = Files.createTempFile(root, "mod-launch-rollback-", ".tmp");
            Files.write(rollback, previous); FilesSupport.replace(rollback, ini);
            throw ex;
        }
    }
}
