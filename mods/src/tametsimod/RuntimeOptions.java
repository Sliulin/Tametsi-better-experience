package tametsimod;

import java.util.EnumSet;

/** Snapshot taken at game startup; manager changes affect only the next session. */
final class RuntimeOptions {
    private static volatile EnumSet<Feature> enabled = EnumSet.allOf(Feature.class);
    private RuntimeOptions() {}
    static boolean enabled(Feature feature) { return enabled.contains(feature); }
    static void configure(Settings settings) {
        EnumSet<Feature> selected = EnumSet.noneOf(Feature.class);
        if (settings.enabled()) for (Feature feature : Feature.values())
            if (settings.feature(feature)) selected.add(feature);
        enabled = selected;
    }
    static void setForTests(EnumSet<Feature> selected) { enabled = selected.clone(); }
}
