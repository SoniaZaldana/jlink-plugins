package plugin.inputlocation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import sootup.core.frontend.AbstractClassSource;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.AsmClassSource;
import sootup.java.bytecode.frontend.AsmMethodSource;
import sootup.java.bytecode.frontend.AsmUtil;
import sootup.core.model.SootClass;
import sootup.core.types.ClassType;
import sootup.core.views.View;
import sootup.java.core.JavaSootClass;
import sootup.java.core.types.JavaClassType;


public class AsmByteClassProvider {

    private final View<?> view;


    public AsmByteClassProvider(View<?> view) {
        this.view = view;
    }

    public AbstractClassSource<JavaSootClass> createClassSource(
            AnalysisInputLocation<? extends SootClass<?>> srcNamespace,
            byte[] contentBytes,
            ClassType classType) {

        SootClassNode classNode = new SootClassNode(srcNamespace);
        ClassReader cr = new ClassReader(contentBytes);
        cr.accept(classNode, ClassReader.SKIP_FRAMES);

        return new AsmClassSource(srcNamespace, null, (JavaClassType) classType, classNode);
    }

    class SootClassNode extends ClassNode {

        private final AnalysisInputLocation<? extends SootClass<?>> analysisInputLocation;

        SootClassNode(AnalysisInputLocation<? extends SootClass<?>> analysisInputLocation) {
            super(AsmUtil.SUPPORTED_ASM_OPCODE);
            this.analysisInputLocation = analysisInputLocation;
        }

        @Override
        
        public MethodVisitor visitMethod(
                int access,
                 String name,
                 String desc,
                 String signature,
                 String[] exceptions) {

            AsmMethodSource mn =
                    new AsmMethodSource(
                            access,
                            name,
                            desc,
                            signature,
                            exceptions,
                            view,
                            view.getBodyInterceptors(this.analysisInputLocation));
            methods.add(mn);
            return mn;
        }
    }
}
