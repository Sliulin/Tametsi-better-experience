package tametsimod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class Settings {
    private final Path path;
    private final Properties values;
    Settings(Path root) throws Exception {
        path = root.resolve("mods/settings.properties");
        values = Files.exists(path) ? FilesSupport.read(path) : new Properties();
        String enabled = values.getProperty("enabled", "false");
        if (!"true".equals(enabled) && !"false".equals(enabled)) throw new Exception("Mod 开关配置无效。");
        for (Feature feature : Feature.values()) {
            String value = values.getProperty(feature.key, "true");
            if (!"true".equals(value) && !"false".equals(value)) throw new Exception("功能配置无效：" + feature.label);
        }
    }
    boolean enabled() { return Boolean.parseBoolean(values.getProperty("enabled", "false")); }
    boolean feature(Feature feature) { return Boolean.parseBoolean(values.getProperty(feature.key, "true")); }
    void save(boolean enabled, java.util.Map<Feature, Boolean> features) throws Exception {
        Properties next = new Properties(); next.putAll(values);
        next.setProperty("enabled", Boolean.toString(enabled));
        for (Feature feature : features.keySet()) next.setProperty(feature.key, features.get(feature).toString());
        FilesSupport.write(path, next);
        values.clear(); values.putAll(next);
    }
    void setEnabled(boolean enabled) throws Exception {
        String previous = values.getProperty("enabled", "false");
        values.setProperty("enabled", Boolean.toString(enabled));
        try { FilesSupport.write(path, values); }
        catch (Exception ex) { values.setProperty("enabled", previous); throw ex; }
    }
    void ensureFirstBackup(Path root, Backups backups) throws Exception {
        String previous = values.getProperty("firstBackup");
        if (previous != null) {
            try {
                backups.validate(root.resolve("mods/backups").resolve(previous));
                return;
            } catch (Exception ex) {
                // A missing or damaged first backup must be replaced by a new valid snapshot.
            }
        }
        Path backup = backups.create("first-mod-start");
        values.setProperty("firstBackup", backup.getFileName().toString());
        FilesSupport.write(path, values);
    }
}
