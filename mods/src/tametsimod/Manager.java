package tametsimod;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;

public final class Manager {
    private final Path root;
    private final Settings settings;
    private final Backups backups;
    private final JFrame frame = new JFrame("Tametsi Mod 管理器");
    private final JCheckBox enabled = new JCheckBox("启用 Mod");
    private final java.util.Map<Feature, JCheckBox> featureBoxes = new java.util.EnumMap<Feature, JCheckBox>(Feature.class);
    private final JButton apply = new JButton("确定并保存");
    private final JLabel status = new JLabel("选择功能并确定；以后直接打开 tametsi.exe 即可。");
    private final JButton start = new JButton("启动游戏");
    private final JButton backup = new JButton("备份存档");
    private final JButton restore = new JButton("恢复备份…");
    private volatile Process child;
    private boolean busy;
    private static GameGuard managerLock;

    private Manager(Path root) throws Exception {
        this(root, true);
    }

    Manager(Path root, boolean visible) throws Exception {
        this.root = root;
        settings = new Settings(root);
        backups = new Backups(root);
        enabled.setSelected(settings.enabled());
        JPanel main = new JPanel(new BorderLayout(0, 18));
        main.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel text = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        JLabel title = new JLabel("Tametsi Mod 管理器  v4.1");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
        text.add(title);
        text.add(enabled);
        for (Feature feature : Feature.values()) {
            JCheckBox box = new JCheckBox(feature.label, settings.feature(feature));
            featureBoxes.put(feature, box); text.add(box);
        }
        text.add(new JLabel("开关在下次启动生效；当前游戏保持原有模式。"));
        text.add(new JLabel("确定后可直接从原版 exe 启动；共用原版存档，关闭不回退进度。"));
        text.add(new JLabel("自动备份满 10 个后替换最旧快照；已有手动备份另行保留。"));
        main.add(text, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton folder = new JButton("打开备份目录");
        buttons.add(apply); buttons.add(start); buttons.add(backup); buttons.add(restore); buttons.add(folder);
        main.add(buttons, BorderLayout.CENTER);
        main.add(status, BorderLayout.SOUTH);
        frame.setContentPane(main);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (!busy) System.exit(0); // The game process remains independent.
            }
        });
        enabled.addActionListener(event -> { status.setText("设置待保存，点击“确定并保存”后下次启动生效。"); refresh(); });
        for (JCheckBox box : featureBoxes.values()) box.addActionListener(event -> status.setText("设置待保存。"));
        apply.addActionListener(event -> applySelection(false));
        start.addActionListener(event -> applySelection(true));
        backup.addActionListener(event -> work(() -> {
            try (GameGuard guard = GameGuard.acquire(root, "game")) {
                GameGuard.ensureStopped(root);
                Path snapshot = backups.create("manual");
                return "备份完成：" + snapshot.getFileName();
            }
        }));
        restore.addActionListener(event -> chooseRestore());
        folder.addActionListener(event -> {
            try { Desktop.getDesktop().open(root.resolve("mods/backups").toFile()); }
            catch (Exception ex) { error(ex); }
        });
        new Timer(1000, event -> {
            if (child != null && !child.isAlive() && !busy) {
                status.setText("启动请求已完成；游戏状态由原版窗口显示。"); child = null;
            }
            refresh();
        }).start();
        frame.pack(); frame.setResizable(false); frame.setLocationRelativeTo(null); frame.setVisible(visible);
    }

    private String launch() throws Exception {
        try (GameGuard guard = GameGuard.acquire(root, "game")) {
            GameGuard.ensureStopped(root);
        }
        child = new ProcessBuilder(root.resolve("tametsi.exe").toString()).directory(root.toFile()).start();
        return "已打开原版 exe，启动记录见 mods/runs；在游戏窗口点击 Play。";
    }

    static List<String> gameCommand(Path root, boolean mode, Path run) throws Exception {
        return java.util.Collections.singletonList(root.resolve("tametsi.exe").toString());
    }

    private void applySelection(boolean launch) {
        final boolean selected = enabled.isSelected();
        final java.util.Map<Feature, Boolean> features = new java.util.EnumMap<Feature, Boolean>(Feature.class);
        for (Feature feature : Feature.values()) features.put(feature, featureBoxes.get(feature).isSelected());
        work(() -> {
            NativeIntegration.apply(root, settings, selected, features);
            if (launch) return launch();
            return "已确定：下次打开 tametsi.exe 为" + (selected ? " Mod 模式。" : "原版模式。");
        });
    }

    private void chooseRestore() {
        work(() -> {
            List<Path> available = backups.list();
            if (available.isEmpty()) throw new Exception("尚无备份，请先创建备份。");
            final Path[] selected = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                Object choice = JOptionPane.showInputDialog(frame, "选择要恢复的备份（北京时间）：", "恢复备份",
                        JOptionPane.QUESTION_MESSAGE, null,
                        available.stream().map(path -> root.resolve("mods/backups").relativize(path).toString()).toArray(), null);
                if (choice != null) selected[0] = root.resolve("mods/backups").resolve(choice.toString());
            });
            if (selected[0] == null) return "已取消恢复。";
            Properties manifest = backups.validate(selected[0]);
            StringBuilder details = new StringBuilder("将恢复到：" + manifest.getProperty("created") + "\n文件：\n");
            for (String name : Backups.FILES) {
                details.append(" • ").append(name).append("：")
                        .append("true".equals(manifest.getProperty(name + ".present")) ? "恢复备份内容" : "备份时不存在，保留当前文件").append('\n');
            }
            details.append("\n这会回退本地进度和原版设置。恢复前会另存当前文件。\n请先正常退出游戏，再确认恢复。");
            final boolean[] confirmed = new boolean[1];
            SwingUtilities.invokeAndWait(() -> confirmed[0] = JOptionPane.showConfirmDialog(frame, details.toString(),
                    "确认恢复所选备份", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION);
            if (!confirmed[0]) return "已取消恢复。";
            try (GameGuard guard = GameGuard.acquire(root, "game")) {
                GameGuard.ensureStopped(root);
                Backups.RestoreResult result = backups.restore(selected[0]);
                return "恢复完成；恢复前备份：" + result.before.getFileName();
            }
        });
    }

    private interface Operation { String run() throws Exception; }
    private void work(Operation operation) {
        if (busy) return;
        busy = true; status.setText("正在处理…"); refresh();
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return operation.run(); }
            @Override protected void done() {
                try { status.setText(get()); }
                catch (Exception ex) { status.setText("操作未完成。"); error(ex.getCause() == null ? ex : ex.getCause()); }
                busy = false; refresh();
            }
        }.execute();
    }
    private void refresh() {
        boolean stopped = child == null || !child.isAlive();
        start.setEnabled(!busy && stopped); backup.setEnabled(!busy && stopped); restore.setEnabled(!busy && stopped);
        enabled.setEnabled(!busy);
        apply.setEnabled(!busy);
        for (JCheckBox box : featureBoxes.values()) box.setEnabled(!busy && enabled.isSelected());
    }
    private void error(Throwable ex) {
        JOptionPane.showMessageDialog(frame, ex.getMessage(), "Tametsi Mod", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            Path root = Paths.get("").toRealPath();
            if (!Files.isRegularFile(root.resolve("tametsi.exe"))) throw new Exception("未找到 tametsi.exe，请从游戏目录启动。");
            managerLock = GameGuard.acquire(root, "manager");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { managerLock.close(); } catch (Exception ex) { ex.printStackTrace(); }
            }));
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeLater(() -> {
                try { new Manager(root); }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), "Tametsi Mod", JOptionPane.ERROR_MESSAGE); System.exit(1);
                }
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Tametsi Mod", JOptionPane.ERROR_MESSAGE); System.exit(1);
        }
    }
}
