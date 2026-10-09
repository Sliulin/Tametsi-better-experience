package tametsimod;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Only these four fixed paths may be restored; Steam account metadata is excluded. */
public final class Backups {
    static final String[] FILES = {"profiles/p0.sav", "profiles/p1.sav", "profiles/p2.sav", "config.txt"};
    private final Path root;
    private final Path directory;

    public Backups(Path root) throws Exception {
        this.root = root.toRealPath();
        this.directory = this.root.resolve("mods/backups");
        Files.createDirectories(directory);
        if (!directory.toRealPath().startsWith(this.root)) throw new Exception("备份目录位于游戏目录之外。");
    }

    public Path create(String reason) throws Exception {
        String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path backup = directory.resolve(timestamp + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectory(backup);
        Properties manifest = new Properties();
        manifest.setProperty("format", "1");
        manifest.setProperty("created", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        manifest.setProperty("reason", reason);
        manifest.setProperty("game.sha256", FilesSupport.sha256(root.resolve("tametsi.exe")));
        for (String name : FILES) {
            Path source = root.resolve(name);
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                requireLocalFile(source, root);
                Path target = backup.resolve(name);
                Files.createDirectories(target.getParent());
                String originalHash = FilesSupport.sha256(source);
                Files.copy(source, target);
                String copiedHash = FilesSupport.sha256(target);
                if (!originalHash.equals(copiedHash) || !originalHash.equals(FilesSupport.sha256(source))) {
                    throw new Exception("备份期间文件发生变化或复制失败：" + name);
                }
                manifest.setProperty(name + ".present", "true");
                manifest.setProperty(name + ".sha256", copiedHash);
            } else {
                manifest.setProperty(name + ".present", "false");
            }
        }
        manifest.setProperty("complete", "true");
        FilesSupport.write(backup.resolve("manifest.properties"), manifest);
        validate(backup);
        return backup;
    }

    static void requireLocalFile(Path file, Path base) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(base.toRealPath())) {
            throw new Exception("拒绝访问非普通文件或目录外文件：" + file);
        }
    }

    public Properties validate(Path backup) throws Exception {
        if (backup.getFileName().toString().endsWith(".zip")) return AutomaticBackups.read(root, backup).manifest;
        Path actual = backup.toRealPath();
        if (!actual.getParent().equals(directory.toRealPath())) throw new Exception("备份路径无效。");
        Path manifestPath = actual.resolve("manifest.properties");
        requireLocalFile(manifestPath, actual);
        Properties manifest = FilesSupport.read(manifestPath);
        if (!"1".equals(manifest.getProperty("format")) || !"true".equals(manifest.getProperty("complete"))) {
            throw new Exception("备份尚未完成或格式不受支持。");
        }
        for (String name : FILES) {
            String present = manifest.getProperty(name + ".present");
            if ("true".equals(present)) {
                Path file = actual.resolve(name);
                requireLocalFile(file, actual);
                if (!FilesSupport.sha256(file).equals(manifest.getProperty(name + ".sha256"))) {
                    throw new Exception("备份校验失败：" + name);
                }
            } else if (!"false".equals(present)) throw new Exception("备份清单缺失：" + name);
        }
        return manifest;
    }

    public List<Path> list() throws Exception {
        List<Path> result = new ArrayList<Path>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(path.resolve("manifest.properties"))) result.add(path);
            }
        }
        Collections.sort(result, Collections.reverseOrder());
        Path automatic = directory.resolve("automatic");
        if (Files.isDirectory(automatic)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(automatic, "startup-*.zip")) {
                for (Path path : stream) {
                    try { AutomaticBackups.read(root, path); result.add(path); } catch (Exception ex) { /* Incomplete snapshots are not offered. */ }
                }
            }
        }
        java.util.Map<Path, String> dates = new java.util.HashMap<Path, String>();
        for (Path path : result) { try { dates.put(path, validate(path).getProperty("created", "")); } catch (Exception ex) { dates.put(path, ""); } }
        Collections.sort(result, (a, b) -> dates.get(b).compareTo(dates.get(a)));
        return result;
    }

    /** Caller must hold the game-operation lock and verify that the game is stopped. */
    public RestoreResult restore(Path backup) throws Exception {
        Properties manifest = validate(backup);
        AutomaticBackups.Snapshot archive = backup.getFileName().toString().endsWith(".zip")
                ? AutomaticBackups.read(root, backup) : null;
        Path before = create("before-restore");
        List<String> restored = new ArrayList<String>();
        List<String> skipped = new ArrayList<String>();
        try {
            for (String name : FILES) {
                if (!"true".equals(manifest.getProperty(name + ".present"))) {
                    skipped.add(name);
                    continue;
                }
                Path target = root.resolve(name);
                Files.createDirectories(target.getParent());
                if (!target.getParent().toRealPath().startsWith(root)) throw new Exception("目标目录越界：" + name);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireLocalFile(target, root);
                Path temp = Files.createTempFile(target.getParent(), "restore-", ".tmp");
                if (archive == null) Files.copy(backup.resolve(name), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                else Files.write(temp, archive.files.get(name));
                if (!FilesSupport.sha256(temp).equals(manifest.getProperty(name + ".sha256"))) {
                    throw new Exception("恢复文件校验失败：" + name);
                }
                FilesSupport.replace(temp, target);
                restored.add(name);
            }
        } catch (Exception ex) {
            throw new Exception("恢复未完成。已恢复：" + restored + "；尚未恢复的文件保持当前状态。\n"
                    + "恢复前备份：" + before + "\n原因：" + ex.getMessage(), ex);
        }
        return new RestoreResult(before, restored, skipped);
    }

    public static final class RestoreResult {
        public final Path before;
        public final List<String> restored, skipped;
        RestoreResult(Path before, List<String> restored, List<String> skipped) {
            this.before = before; this.restored = restored; this.skipped = skipped;
        }
    }
}
