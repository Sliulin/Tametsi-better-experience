package tametsimod;

public enum Feature {
    REVEAL("revealChord", "数字格一键展开"),
    FLAG("flagChord", "数字格自动插旗"),
    HIGHLIGHTS("neighborHighlights", "Ctrl 高亮跳过已揭开的邻居"),
    COLUMNS("columnChord", "行列提示一键展开（旗数齐全时加圆圈）"),
    COLORS("colorChord", "颜色剩余雷数为零时一键展开"),
    BACKUP("autoBackup", "每次启动自动备份（最近 10 个）"),
    FLAG_ICON("flagIcon", "标记格中央显示 🚩（随格子缩放）"),
    NEIGHBOR_TOGGLE("neighborToggle", "P 键切换常驻邻居高亮");

    public final String key, label;
    Feature(String key, String label) { this.key = key; this.label = label; }
}
