package tametsimod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Ten atomic ZIP slots. Replaces only the oldest automatic snapshot; no deletion. */
final class AutomaticBackups {
    static final int LIMIT = 10;
    static final class Snapshot {
        final Properties manifest;
        final Map<String, byte[]> files;
        Snapshot(Properties manifest, Map<String, byte[]> files) { this.manifest = manifest; this.files = files; }
    }
    private AutomaticBackups() {}
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder();
        for (byte b : digest) text.append(String.format("%02x", b & 255));
        return text.toString();
    }
    static Path create(Path root) throws Exception {
        Path directory = root.resolve("mods/backups/automatic");
        Files.createDirectories(directory);
        if (!directory.toRealPath().startsWith(root.toRealPath())) throw new Exception("自动备份目录越界。");
        Path selected = null; String oldest = null;
        for (int i = 1; i <= LIMIT; i++) {
            Path slot = directory.resolve(String.format("startup-%02d.zip", i));
            if (!Files.exists(slot, LinkOption.NOFOLLOW_LINKS)) { selected = slot; break; }
            Backups.requireLocalFile(slot, directory);
            String created;
            try { created = read(root, slot).manifest.getProperty("created"); }
            catch (Exception ex) { created = ""; }
            if (oldest == null || created.compareTo(oldest) < 0) { oldest = created; selected = slot; }
        }
        Properties manifest = new Properties();
        manifest.setProperty("format", "2"); manifest.setProperty("complete", "true");
        manifest.setProperty("reason", "every-game-start");
        manifest.setProperty("created", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        manifest.setProperty("game.sha256", FilesSupport.sha256(root.resolve("tametsi.exe")));
        Map<String, byte[]> contents = new HashMap<String, byte[]>();
        for (String name : Backups.FILES) {
            Path source = root.resolve(name);
            boolean present = Files.exists(source, LinkOption.NOFOLLOW_LINKS);
            manifest.setProperty(name + ".present", Boolean.toString(present));
            if (present) {
                Backups.requireLocalFile(source, root);
                String before = FilesSupport.sha256(source);
                byte[] bytes = Files.readAllBytes(source);
                if (!before.equals(hash(bytes)) || !before.equals(FilesSupport.sha256(source)))
                    throw new Exception("自动备份期间文件变化：" + name);
                contents.put(name, bytes); manifest.setProperty(name + ".sha256", before);
            }
        }
        Path temporary = Files.createTempFile(directory, "snapshot-", ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for (String name : Backups.FILES) if (contents.containsKey(name)) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(contents.get(name)); zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("manifest.properties"));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); manifest.store(bytes, "Tametsi automatic snapshot");
            zip.write(bytes.toByteArray()); zip.closeEntry();
        }
        readArchive(temporary); // Complete and verify before replacing an old slot.
        FilesSupport.replace(temporary, selected);
        read(root, selected);
        return selected;
    }
    static Snapshot read(Path root, Path file) throws Exception {
        Path directory = root.resolve("mods/backups/automatic");
        Backups.requireLocalFile(file, directory);
        if (!file.toRealPath().getParent().equals(directory.toRealPath())
                || !file.getFileName().toString().matches("startup-(0[1-9]|10)\\.zip"))
            throw new Exception("自动备份路径无效。");
        return readArchive(file);
    }
    private static Snapshot readArchive(Path file) throws Exception {
        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!("manifest.properties".equals(name) || java.util.Arrays.asList(Backups.FILES).contains(name))
                        || entries.containsKey(name)) throw new Exception("自动备份包含无效或重复条目。");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int count;
                while ((count = zip.read(buffer)) >= 0) {
                    if (bytes.size() + count > 32 * 1024 * 1024) throw new Exception("自动备份文件过大。");
                    bytes.write(buffer, 0, count);
                }
                entries.put(name, bytes.toByteArray());
            }
        }
        byte[] manifestBytes = entries.remove("manifest.properties");
        if (manifestBytes == null) throw new Exception("自动备份缺少清单。");
        Properties manifest = new Properties(); manifest.load(new ByteArrayInputStream(manifestBytes));
        if (!"2".equals(manifest.getProperty("format")) || !"true".equals(manifest.getProperty("complete")))
            throw new Exception("自动备份未完成或格式不支持。");
        ZonedDateTime.parse(manifest.getProperty("created"));
        for (String name : Backups.FILES) {
            String present = manifest.getProperty(name + ".present");
            if ("true".equals(present)) {
                byte[] bytes = entries.get(name);
                if (bytes == null || !hash(bytes).equals(manifest.getProperty(name + ".sha256")))
                    throw new Exception("自动备份校验失败：" + name);
            } else if (!"false".equals(present) || entries.containsKey(name))
                throw new Exception("自动备份清单不一致：" + name);
        }
        return new Snapshot(manifest, entries);
    }
}
