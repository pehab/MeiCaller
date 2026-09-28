package de.haberland.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Only replaces the two platform member accesses named in the production crash traces. */
public final class PlatformApiVisitor extends ClassVisitor {
    public static final String BRIDGE = "de/haberland/meicaller/compat/PlatformApiCompat";
    private int replacements;

    public PlatformApiVisitor(ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9,
                super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                if (opcode == Opcodes.GETFIELD
                        && owner.equals("android/content/res/Configuration")
                        && field.equals("fontWeightAdjustment") && desc.equals("I")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE,
                            "fontWeightAdjustment", "(Ljava/lang/Object;)I", false);
                    replacements++;
                } else {
                    super.visitFieldInsn(opcode, owner, field, desc);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String method,
                                        String desc, boolean isInterface) {
                if (opcode == Opcodes.INVOKESTATIC
                        && owner.equals("android/view/WindowInsets$Type")
                        && method.equals("systemOverlays") && desc.equals("()I")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, method, desc, false);
                    replacements++;
                } else {
                    super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                }
            }
        };
    }

    @Override
    public void visitEnd() {
        if (replacements == 0) {
            throw new IllegalStateException("AndroidX compatibility target changed; review the platform API patch.");
        }
        super.visitEnd();
    }
}
