package plugin;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JNewExpr;
import sootup.core.jimple.common.ref.IdentityRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.*;
import sootup.core.jimple.visitor.StmtVisitor;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.signatures.PackageName;
import sootup.java.core.types.JavaClassType;

import java.util.HashMap;
import java.util.Map;

import static plugin.JLinkIFDSProblem.TOP_VALUE;

public class JLinkVisitor implements StmtVisitor {

    // TODO extrapolate all these constants to another class as they keep expanding.
    private static final String JAVA_LANG_STRING_BUILDER = "java.lang.StringBuilder";
    private static final String JAVA_LANG_STRING = "java.lang.String";
    private static final String JAVA_LANG_CLASS = "java.lang.Class";
    private static final String TO_STRING = "toString";
    private static final String INIT = "<init>";
    private static final String APPEND = "append";
    private static final String CONCAT = "concat";
    private static final String FOR_NAME = "forName";
    private static final String CANONICAL_NAME = "getCanonicalName";
    private static final String SIMPLE_NAME = "getSimpleName";
    private Map<Local, JLinkValue> setIn;
    private Map<Local, JLinkValue> setOut;
    private SootMethod clinit;

    public JLinkVisitor(Map<Local, JLinkValue> setIn) {
        this.setIn = setIn;
        this.setOut = new HashMap<>();
    }

    // TODO - have not added support for static final variables in this iteration of the visitor yet.
    public JLinkVisitor(Map<Local, JLinkValue> setIn, SootMethod clinit) {
        this(setIn);
        this.clinit = clinit;
    }

    public Map<Local, JLinkValue> getSetOut() {
        return setOut;
    }
    
    @Override
    public void caseBreakpointStmt(JBreakpointStmt jBreakpointStmt) {
        defaultCaseStmt(jBreakpointStmt);
    }

    @Override
    public void caseInvokeStmt(JInvokeStmt jInvokeStmt) {
        defaultCaseStmt(jInvokeStmt);

        if (jInvokeStmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr iExpr) {
            // currently supports operations like Class.forName, append, toString, and init for StringBuilder.
            handleInvocation(iExpr, iExpr.getBase());
        }
    }

    private boolean handleInvocation(AbstractInvokeExpr iExpr, Local toReassign) {
        MethodSignature iMethod = iExpr.getMethodSignature();
        Local lBase = null;
        if (iExpr instanceof AbstractInstanceInvokeExpr exp) {
            lBase = exp.getBase();
        }

        switch (iMethod.getDeclClassType().getFullyQualifiedName()) {
            case JAVA_LANG_STRING_BUILDER:
                return handleStringBuilderOperations(iMethod, toReassign, lBase, iExpr);
            case JAVA_LANG_STRING:
                return handleStringOperations(iMethod, toReassign, lBase, iExpr);
            case JAVA_LANG_CLASS:
                return handleClassOperations(iMethod, toReassign, lBase, iExpr);
        }
        return false;
    }

    private boolean handleStringBuilderOperations(MethodSignature iMethod, Local toReassign,
                                                  Local lBase, AbstractInvokeExpr iExpr) {
        if (iMethod.getName().equals(TO_STRING)) {
            setOut.put(toReassign, setIn.get(lBase));
            return true;
        } else if (iMethod.getName().equals(INIT)) {
            if (iMethod.getSubSignature().getParameterTypes().isEmpty()) {
                // then we have <init> ()
                setOut.put(toReassign, new StringValue(""));
                return true;
            } else if (iMethod.getSubSignature().getParameterTypes().get(0) instanceof JavaClassType type) {
                JavaClassType stringType = new JavaClassType("String", new PackageName("java.lang"));
                if (type.equals(stringType)) {
                    // we have <init> (java.lang.String)
                    setOut.put(toReassign, stringBuilderParamValue(iExpr.getArg(0)));
                    return true;
                }
            }
            return false;
        } else if (iMethod.getName().equals(APPEND)) {
            return concat(iExpr, toReassign, lBase);
        }
        return false;
    }

    private boolean handleStringOperations(MethodSignature iMethod, Local toReassign,
                                           Local lBase, AbstractInvokeExpr iExpr) {
        if (iMethod.getName().equals(CONCAT)) {
            return concat(iExpr, toReassign, lBase);
        }
        return false;
    }

    private boolean handleClassOperations(MethodSignature iMethod, Local toReassign, Local lBase, AbstractInvokeExpr iExpr) {
        if (iMethod.getName().equals(FOR_NAME)) {
            return classForName(iExpr.getArg(0), toReassign);
        } else if (iMethod.getName().equals(CANONICAL_NAME)) {
            return canonicalName(lBase, toReassign);
        } else if (iMethod.getName().equals(SIMPLE_NAME)) {
            return simpleName(lBase, toReassign);
        }
        return false;
    }

    private boolean simpleName(Local lBase, Local toReassign) {
        if (setIn.get(lBase) instanceof ClassValue classValue) {
            String className = classValue.getContent();
            setOut.put(toReassign, new StringValue(
                    className.substring(className.lastIndexOf("/"), className.length() - 1)));
            return true;
        }

        return false;
    }

    private boolean canonicalName(Local lBase, Local toReassign) {
        if (setIn.get(lBase) instanceof ClassValue cv) {
            setOut.put(toReassign, new StringValue(
                    cv.getContent().substring(1, cv.getContent().length() - 1)
                            .replace("/", ".")));
            return true;
        }
        return false;
    }

    private boolean concat(AbstractInvokeExpr iExpr, Local toReassign, Local lBase) {
        Value arg = iExpr.getArg(0);
        JLinkValue value = stringBuilderParamValue(arg);
        if (lBase != null) {
            setOut.put(toReassign, concat(setIn.get(lBase), value));
        } else {
            setOut.put(toReassign, concat(setIn.get(toReassign), value));
        }
        return true;
    }

    private JLinkValue concat(JLinkValue value1, JLinkValue value2) {
        if (value1 instanceof StringValue s1 && value2 instanceof StringValue s2) {
            return new StringValue(s1.getContent().concat(s2.getContent()));
        }
        return TOP_VALUE;
    }

    private JLinkValue stringBuilderParamValue(Value arg) {
        if (arg instanceof StringConstant stringConstant) {
            return new StringValue(stringConstant.getValue());
        } else if (arg instanceof Local local) {
            if (setIn.get(local) instanceof StringValue stringValue) {
                return stringValue;
            }
        }
        return TOP_VALUE;
    }

    private boolean classForName(Value arg, Local toReassign) {
        JLinkValue value = classForNameParamValue(arg);
        setOut.put(toReassign, value);
        return true;
    }

    private JLinkValue classForNameParamValue(Value arg) {
        if (arg instanceof StringConstant stringConstant) {
            return new ClassValue(stringConstant.getValue());
        } else if (arg instanceof Local local) {
            return setIn.get(local);
        }
        return TOP_VALUE;
    }

    @Override
    public void caseAssignStmt(JAssignStmt<?, ?> jAssignStmt) {
        defaultCaseStmt(jAssignStmt);
        Value left = jAssignStmt.getLeftOp();
        Value right = jAssignStmt.getRightOp();
        if (left instanceof Local lLocal) {
            if (right instanceof AbstractInvokeExpr invokeExpr) {
                handleInvocation(invokeExpr, lLocal);
            } else if (right instanceof Local rLocal) {
                // we simply update the value of left with whatever was there in right.
                setOut.put(lLocal, setIn.get(rLocal));
            } else if (right instanceof ClassConstant constant) {
                // this time the new value of lLocal is the class constant
                setOut.put(lLocal, new ClassValue(constant.getValue()));
            } else if (right instanceof StringConstant constant) {
                // this time the new value of lLocal is the string constant
                setOut.put(lLocal, new StringValue(constant.getValue()));
            } else if (right instanceof JNewExpr newExpr) {
                if (newExpr.getType().toString().equals(JAVA_LANG_STRING_BUILDER)) {
                    setOut.putAll(setIn);
                }

                // TODO do we need to do anything with Class for "new"?

            } else if (right instanceof JInstanceFieldRef) {
                setOut.put(lLocal, TOP_VALUE);
            } else if (right instanceof JFieldRef fRef) {
                JLinkValue value;
                if (right instanceof ClassConstant constant) {
                    // we have a constant, so we are done
                    value = new ClassValue(constant.getValue());
                } else if (right instanceof StringConstant strConstant) {
                    value = new StringValue(strConstant.getValue());
                } else {
                    value = lookUpFieldRef(fRef);
                }
                setOut.put(lLocal, value);
            } else {
                // we don't support any other cases, so we assign TOP.
                setOut.put(lLocal, TOP_VALUE);
            }
        } else {
            // if left is not a local, we don't care.
        }
    }
    
    private JLinkValue lookUpFieldRef(JFieldRef fieldRef) {
        if (clinit != null) {
            for (Stmt u : clinit.getBody().getStmts()) {
                if (u instanceof JAssignStmt aStmt) {
                    Value left = aStmt.getLeftOp();
                    Value right = aStmt.getRightOp();

                    if (left instanceof JFieldRef aFieldRef) {
                        // if the static field is the one we are looking for...
                        if (aFieldRef.getFieldSignature().equals(fieldRef.getFieldSignature())) {
                            // we then retrieve its constant value if it is a class constant
                            if (right instanceof ClassConstant constant) {
                                return new ClassValue(constant.getValue());
                            }
                        }
                    }
                }
            }
        }
        
        return TOP_VALUE;
    }

    @Override
    public void caseIdentityStmt(JIdentityStmt<?> jIdentityStmt) {
        defaultCaseStmt(jIdentityStmt);
        Value left = jIdentityStmt.getLeftOp();
        Value right = jIdentityStmt.getRightOp();

        if (left instanceof Local lLocal) {
            if (right instanceof IdentityRef) {
                // the local is not a constant and depends on external input
                setOut.put(lLocal, setIn.get(lLocal));
            }
        }
    }

    @Override
    public void caseEnterMonitorStmt(JEnterMonitorStmt jEnterMonitorStmt) {
        defaultCaseStmt(jEnterMonitorStmt);
    }

    @Override
    public void caseExitMonitorStmt(JExitMonitorStmt jExitMonitorStmt) {
        defaultCaseStmt(jExitMonitorStmt);
    }

    @Override
    public void caseGotoStmt(JGotoStmt jGotoStmt) {
        defaultCaseStmt(jGotoStmt);
    }

    @Override
    public void caseIfStmt(JIfStmt jIfStmt) {
        defaultCaseStmt(jIfStmt);
    }

    @Override
    public void caseNopStmt(JNopStmt jNopStmt) {
        defaultCaseStmt(jNopStmt);
    }

    @Override
    public void caseRetStmt(JRetStmt jRetStmt) {
        defaultCaseStmt(jRetStmt);
    }

    @Override
    public void caseReturnStmt(JReturnStmt jReturnStmt) {
        defaultCaseStmt(jReturnStmt);
    }

    @Override
    public void caseReturnVoidStmt(JReturnVoidStmt jReturnVoidStmt) {
        defaultCaseStmt(jReturnVoidStmt);
    }

    @Override
    public void caseSwitchStmt(JSwitchStmt jSwitchStmt) {
        defaultCaseStmt(jSwitchStmt);
    }

    @Override
    public void caseThrowStmt(JThrowStmt jThrowStmt) {
        defaultCaseStmt(jThrowStmt);
    }

    @Override
    public void defaultCaseStmt(Stmt stmt) {
        setOut.putAll(setIn);
    }
}
