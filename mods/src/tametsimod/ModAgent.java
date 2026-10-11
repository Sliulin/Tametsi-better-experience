package tametsimod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Verify and adapt original rendering, hover calculation and key dispatch in enabled sessions. */
public final class ModAgent {
    private static volatile boolean active, patched, mousePatched, keysPatched;
    private static volatile Throwable failure;
    private ModAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        if ("native".equals(arguments)) { NativeStartup.start(instrumentation); return; }
        activate(instrumentation);
    }

    static void activate(Instrumentation instrumentation) {
        active = true;
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!"draw/Drawer".equals(name) && !"io/MouseHandler".equals(name) && !"game/Game".equals(name)) return null;
                try {
                    if ("io/MouseHandler".equals(name)) {
                        byte[] result = patchMouse(bytes); mousePatched = true; return result;
                    }
                    if ("game/Game".equals(name)) {
                        byte[] result = patchKeys(bytes); keysPatched = true; return result;
                    }
                    byte[] result = patch(bytes);
                    patched = true;
                    return result;
                } catch (Throwable ex) {
                    failure = ex;
                    return null; // Bootstrap checks this before launching the game.
                }
            }
        });
    }

    static byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final int[] replacements = new int[1];
        final int[] iconInsertions = new int[1];
        final int[] toggleReads = new int[1];
        final int[] columnText = new int[1];
        final int[] colorText = new int[1];
        final int[] frames = new int[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
                final boolean column = "drawColumnHints".equals(name)
                        && "(Ljava/awt/Graphics2D;Lpuzzle/Puzzle;)V".equals(descriptor);
                final boolean color = "drawHintMineCount".equals(name)
                        && "(IILjava/awt/Graphics2D;Lpuzzle/Puzzle;)V".equals(descriptor);
                if (column || color) return new MethodVisitor(Opcodes.ASM9, original) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && "java/awt/Graphics2D".equals(owner)
                                && "drawString".equals(method) && "(Ljava/lang/String;FF)V".equals(desc)) {
                            int occurrence = column ? ++columnText[0] : ++colorText[0];
                            if (column || occurrence == 1) {
                                super.visitVarInsn(Opcodes.ALOAD, column ? 1 : 3);
                                super.visitVarInsn(Opcodes.ALOAD, column ? 3 : 9);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        column ? "tametsimod/ColumnIndicators" : "tametsimod/ColorChord",
                                        "drawText", "(Ljava/awt/Graphics2D;Ljava/lang/String;FFLpuzzle/Puzzle;"
                                                + (column ? "Lpuzzle/ColumnHint;" : "Lpuzzle/Hint;") + ")V", false);
                                return;
                            }
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                    }
                };
                if ("draw".equals(name) && "(IILjava/awt/Graphics;Lpuzzle/Puzzle;)V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, original) {
                        @Override public void visitCode() {
                            super.visitCode(); frames[0]++;
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitVarInsn(Opcodes.ILOAD, 0);
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/ColorChord", "begin",
                                    "(Lpuzzle/Puzzle;II)V", false);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            if (opcode == Opcodes.GETSTATIC && "game/State".equals(owner) && "SHOW_NEIGHBORS".equals(field) && "Z".equals(desc)) {
                                toggleReads[0]++;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/NeighborToggle", "visible", "()Z", false);
                            } else super.visitFieldInsn(opcode, owner, field, desc);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                String desc, boolean isInterface) {
                            super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                            if (opcode == Opcodes.INVOKESTATIC && "draw/Drawer".equals(owner)
                                    && "drawRevealedTiles".equals(method)
                                    && "(Ljava/awt/Graphics2D;Lpuzzle/Puzzle;)V".equals(desc)) {
                                iconInsertions[0]++;
                                super.visitVarInsn(Opcodes.ALOAD, 10);
                                super.visitVarInsn(Opcodes.ALOAD, 3);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/FlagIcons", "draw", desc, false);
                            }
                        }
                    };
                }
                if (!"drawHoverNeighbors".equals(name)
                        || !"(IILjava/awt/Graphics2D;Lgraph/Graph;)V".equals(descriptor)) return original;
                return new MethodVisitor(Opcodes.ASM9, original) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && "graph/Node".equals(owner)
                                && "getNeighbors".equals(method)
                                && "()Ljava/util/HashSet;".equals(descriptor)) {
                            replacements[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/NeighborHighlights",
                                    "neighbors", "(Lgraph/Node;)Ljava/util/HashSet;", false);
                        } else super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (replacements[0] != 1 || iconInsertions[0] != 1 || toggleReads[0] != 1
                || columnText[0] != 1 || colorText[0] != 3 || frames[0] != 1)
            throw new IllegalStateException("绘制入口不匹配：" + replacements[0] + "/" + iconInsertions[0]
                    + "/" + toggleReads[0] + "/" + columnText[0] + "/" + colorText[0] + "/" + frames[0]);
        return writer.toByteArray();
    }

    static byte[] patchMouse(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        final int[] count = new int[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor original = super.visitMethod(access, name, desc, signature, exceptions);
                if (!"mouseMoved".equals(name) || !"(Ljava/awt/event/MouseEvent;)V".equals(desc)) return original;
                return new MethodVisitor(Opcodes.ASM9, original) {
                    @Override public void visitFieldInsn(int op, String owner, String field, String desc) {
                        if (op == Opcodes.GETSTATIC && "game/State".equals(owner) && "SHOW_NEIGHBORS".equals(field) && "Z".equals(desc)) {
                            count[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/NeighborToggle", "visible", "()Z", false);
                        } else super.visitFieldInsn(op, owner, field, desc);
                    }
                };
            }
        }, 0);
        if (count[0] != 1) throw new IllegalStateException("鼠标邻居显示入口不匹配：" + count[0]);
        return writer.toByteArray();
    }

    static byte[] patchKeys(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final int[] count = new int[2];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor original = super.visitMethod(access, name, desc, signature, exceptions);
                final int index = "keyPressed".equals(name) ? 0 : "keyReleased".equals(name) ? 1 : -1;
                if (index < 0 || !"(Ljava/awt/event/KeyEvent;)V".equals(desc)) return original;
                count[index]++;
                return new MethodVisitor(Opcodes.ASM9, original) {
                    @Override public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "tametsimod/NeighborToggle", index == 0 ? "pressed" : "released",
                                "(Lgame/Game;Ljava/awt/event/KeyEvent;)Z", false);
                        org.objectweb.asm.Label original = new org.objectweb.asm.Label();
                        super.visitJumpInsn(Opcodes.IFEQ, original); super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(original); super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        }, 0);
        if (count[0] != 1 || count[1] != 1) throw new IllegalStateException("键盘入口不匹配：" + count[0] + "/" + count[1]);
        return writer.toByteArray();
    }

    static void requirePatched() throws Exception {
        if (!active) throw new IllegalStateException("Mod v4.1 未加载输入与绘制组件，请打开新版管理器并保存设置。");
        Class.forName("draw.Drawer", false, ModAgent.class.getClassLoader());
        Class.forName("io.MouseHandler", false, ModAgent.class.getClassLoader());
        Class.forName("game.Game", false, ModAgent.class.getClassLoader());
        if (!patched || !mousePatched || !keysPatched || failure != null)
            throw new IllegalStateException("输入与邻居高亮组件加载失败：draw=" + patched
                    + ", mouse=" + mousePatched + ", keys=" + keysPatched, failure);
    }
}
