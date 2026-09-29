package de.haberland.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;

public abstract class PlatformApiVisitorFactory
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    @Override
    public boolean isInstrumentable(ClassData data) {
        String name = data.getClassName();
        return name.equals("androidx.compose.ui.platform.AndroidComposeView")
                || name.equals("androidx.compose.ui.text.font.FontWeightAdjustmentHelperApi31")
                || name.equals("androidx.core.view.WindowInsetsCompat$TypeImpl34");
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        if (context.getCurrentClassData().getClassName().equals("androidx.compose.ui.platform.AndroidComposeView")) {
            return new TranslationApiVisitor(next);
        }
        return new PlatformApiVisitor(next);
    }
}
