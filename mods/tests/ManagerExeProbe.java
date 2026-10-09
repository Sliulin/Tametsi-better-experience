package tametsimodtests;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import org.objectweb.asm.*;

/** Test-only agent: exercise the production EXE and Manager.main without displaying a window. */
public final class ManagerExeProbe {
    private static int replacements;

    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!"tametsimod/Manager".equals(name)) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, 0);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                            public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                                if (op == Opcodes.INVOKEVIRTUAL && owner.equals("javax/swing/JFrame") && name.equals("setVisible") && desc.equals("(Z)V")) {
                                    replacements++;
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimodtests/ManagerExeProbe", "show", "(Ljavax/swing/JFrame;Z)V", false);
                                } else super.visitMethodInsn(op, owner, name, desc, itf);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }

    public static void show(JFrame frame, boolean visible) {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Properties result = new Properties();
        try {
            if (replacements != 1 || !visible) throw new Exception("Unexpected manager window invocation");
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> manager = Class.forName("tametsimod.Manager", false, loader);
            result.setProperty("runtime", Paths.get(manager.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            result.setProperty("java.home", System.getProperty("java.home"));
            result.setProperty("root", root.toString());
            result.setProperty("title", frame.getTitle());
            result.setProperty("checkboxes", Integer.toString(boxes(frame, result)));
            java.lang.reflect.Method command = manager.getDeclaredMethod("gameCommand", Path.class, boolean.class, Path.class);
            command.setAccessible(true);
            result.setProperty("game.command", command.invoke(null, root, false, root.resolve("mods/runs/test")).toString());
            Container content = frame.getContentPane();
            content.doLayout();
            BufferedImage preview = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = preview.createGraphics(); content.printAll(graphics); graphics.dispose();
            ImageIO.write(preview, "png", root.resolve("mods/manager-exe-preview.png").toFile());
            frame.dispose();
            result.setProperty("success", "true");
        } catch (Throwable ex) { result.setProperty("error", ex.toString()); }
        try (java.io.OutputStream out = Files.newOutputStream(root.resolve("mods/manager-exe-probe.properties"))) {
            result.store(out, "Actual production manager EXE test");
        } catch (Exception ex) { ex.printStackTrace(); System.exit(2); }
        System.exit("true".equals(result.getProperty("success")) ? 0 : 1);
    }

    private static int boxes(Component component, Properties result) {
        if (component instanceof JCheckBox) {
            JCheckBox box = (JCheckBox) component;
            result.setProperty("box." + box.getText(), Boolean.toString(box.isSelected()));
            return 1;
        }
        int count = 0;
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) count += boxes(child, result);
        return count;
    }
}
