package de.haberland.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Guard before resolving the API-31 helper, including its singleton field access. */
public final class TranslationApiVisitor extends ClassVisitor {
    public static final String HELPER = "androidx/compose/ui/platform/AndroidComposeViewTranslationCallbackS";
    private int fields;
    private int attachCalls;
    private int detachCalls;

    public TranslationApiVisitor(ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!(name.equals("onAttachedToWindow") || name.equals("onDetachedFromWindow"))
                || !descriptor.equals("()V")) return delegate;
        return new MethodVisitor(Opcodes.ASM9, delegate) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                if (opcode == Opcodes.GETSTATIC && owner.equals(HELPER)
                        && field.equals("INSTANCE") && desc.equals("L" + HELPER + ";")) {
                    // Preserve stack shape without resolving the AndroidX helper prematurely.
                    super.visitInsn(Opcodes.ACONST_NULL);
                    fields++;
                } else {
                    super.visitFieldInsn(opcode, owner, field, desc);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String method,
                                        String desc, boolean isInterface) {
                if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(HELPER)
                        && desc.equals("(Landroid/view/View;)V")
                        && (method.equals("setViewTranslationCallback") || method.equals("clearViewTranslationCallback"))) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PlatformApiVisitor.BRIDGE,
                            method, "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    if (method.equals("setViewTranslationCallback")) attachCalls++;
                    else detachCalls++;
                } else {
                    super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                }
            }
        };
    }

    @Override
    public void visitEnd() {
        if (fields != 2 || attachCalls != 1 || detachCalls != 1) {
            throw new IllegalStateException("Compose translation lifecycle changed: singleton=" + fields
                    + ", attach=" + attachCalls + ", detach=" + detachCalls + "; review compatibility patch");
        }
        System.out.println("MeiCaller translation guard: AndroidComposeView attach + detach verified");
        super.visitEnd();
    }
}
