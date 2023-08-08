package plugin;

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.NullConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JNewExpr;
import sootup.core.jimple.common.ref.IdentityRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.*;
import sootup.core.jimple.visitor.StmtVisitor;
import sootup.core.signatures.FieldSignature;
import sootup.core.signatures.MethodSignature;
import sootup.core.signatures.PackageName;
import sootup.java.core.types.JavaClassType;

import java.util.HashMap;
import java.util.Map;

public class JLinkVisitor implements StmtVisitor {

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
    private Map<ValueHolder, JLinkValue> setIn;
    private Map<ValueHolder, JLinkValue> setOut;

    public JLinkVisitor(Map<ValueHolder, JLinkValue> setIn) {
        this.setIn = setIn;
        this.setOut = new HashMap<>();
    }

    public Map<ValueHolder, JLinkValue> getSetOut() {
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
            handleInvocation(iExpr, new LocalHolder(iExpr.getBase()));
        }
    }

    private boolean handleInvocation(AbstractInvokeExpr iExpr, ValueHolder toReassign) {
        MethodSignature iMethod = iExpr.getMethodSignature();
        ValueHolder lBase = null;
        if (iExpr instanceof AbstractInstanceInvokeExpr exp) {
            lBase = new LocalHolder(exp.getBase());
        }

        return switch (iMethod.getDeclClassType().getFullyQualifiedName()) {
            case JAVA_LANG_STRING_BUILDER -> handleStringBuilderOperations(iMethod, toReassign, lBase, iExpr);
            case JAVA_LANG_STRING -> handleStringOperations(iMethod, toReassign, lBase, iExpr);
            case JAVA_LANG_CLASS -> handleClassOperations(iMethod, toReassign, lBase, iExpr);
            default -> false;
        };
    }

    private boolean handleStringBuilderOperations(MethodSignature iMethod, ValueHolder toReassign,
                                                  ValueHolder lBase, AbstractInvokeExpr iExpr) {
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

    private boolean handleStringOperations(MethodSignature iMethod, ValueHolder toReassign,
                                           ValueHolder lBase, AbstractInvokeExpr iExpr) {
        if (iMethod.getName().equals(CONCAT)) {
            return concat(iExpr, toReassign, lBase);
        }
        return false;
    }

    private boolean handleClassOperations(MethodSignature iMethod, ValueHolder toReassign, ValueHolder lBase, AbstractInvokeExpr iExpr) {
        if (iMethod.getName().equals(FOR_NAME)) {
            return classForName(iExpr.getArg(0), toReassign);
        } else if (iMethod.getName().equals(CANONICAL_NAME)) {
            return canonicalName(lBase, toReassign);
        } else if (iMethod.getName().equals(SIMPLE_NAME)) {
            return simpleName(lBase, toReassign);
        }
        return false;
    }

    private boolean simpleName(ValueHolder lBase, ValueHolder toReassign) {
        if (setIn.get(lBase) instanceof ClassValue classValue) {
            String className = classValue.getContent();
            setOut.put(toReassign, new StringValue(
                    className.substring(className.lastIndexOf("/"), className.length() - 1)));
            return true;
        }

        return false;
    }

    private boolean canonicalName(ValueHolder lBase, ValueHolder toReassign) {
        if (setIn.get(lBase) instanceof ClassValue cv) {
            setOut.put(toReassign, new StringValue(
                    cv.getContent().substring(1, cv.getContent().length() - 1)
                            .replace("/", ".")));
            return true;
        }
        return false;
    }

    private boolean concat(AbstractInvokeExpr iExpr, ValueHolder toReassign, ValueHolder lBase) {
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
        return JLinkIFDSProblem.TOP_VALUE;
    }

    private JLinkValue stringBuilderParamValue(Value arg) {
        if (arg instanceof StringConstant stringConstant) {
            return new StringValue(stringConstant.getValue());
        } else if (arg instanceof Local local) {
            if (setIn.get(new LocalHolder(local)) instanceof StringValue stringValue) {
                return stringValue;
            }
        }
        return JLinkIFDSProblem.TOP_VALUE;
    }

    private boolean classForName(Value arg, ValueHolder toReassign) {
        JLinkValue value = classForNameParamValue(arg);
        setOut.put(toReassign, value);
        return true;
    }

    private JLinkValue classForNameParamValue(Value arg) {
        if (arg instanceof StringConstant stringConstant) {
            return new ClassValue(stringConstant.getValue());
        } else if (arg instanceof Local local) {
            return setIn.get(new LocalHolder(local));
        }
        return JLinkIFDSProblem.TOP_VALUE;
    }


    @Override
    public void caseAssignStmt(JAssignStmt<?, ?> jAssignStmt) {
        defaultCaseStmt(jAssignStmt);
        Value left = jAssignStmt.getLeftOp();
        Value right = jAssignStmt.getRightOp();
        if (left instanceof Local || left instanceof JStaticFieldRef) {
            ValueHolder lHolder;
            if (left instanceof Local) {
                lHolder = new LocalHolder((Local) left);
            } else {
                FieldSignature fieldSignature = ((JStaticFieldRef) left).getFieldSignature();
                lHolder = new StaticFieldHolder(fieldSignature);
            }

            if (right instanceof AbstractInvokeExpr invokeExpr) {
                handleInvocation(invokeExpr, lHolder);
            } else if (right instanceof Local rLocal) {
                // we simply update the value of left with whatever was there in right.
                setOut.put(lHolder, setIn.get(new LocalHolder(rLocal)));
            } else if (right instanceof ClassConstant constant) {
                // this time the new value of lLocal is the class constant
                setOut.put(lHolder, new ClassValue(constant.getValue()));
            } else if (right instanceof StringConstant constant) {
                // this time the new value of lLocal is the string constant
                setOut.put(lHolder, new StringValue(constant.getValue()));
            } else if (right instanceof NullConstant) {
                setOut.put(lHolder, new NullValue());
            } else if (right instanceof JNewExpr newExpr) {
                if (newExpr.getType().toString().equals(JAVA_LANG_STRING_BUILDER)) {
                    setOut.putAll(setIn);
                } else {
                    setOut.put(lHolder, new ObjectValue(newExpr.getType())); // establishes that there is an object value of this type in this slot.
                }

            } else if (right instanceof JInstanceFieldRef instanceFieldRef) {
                LocalHolder base = new LocalHolder(instanceFieldRef.getBase());
                if (setIn.get(base) instanceof ObjectValue objectValue) {
                    JLinkValue fieldValue = objectValue.getFieldValue(instanceFieldRef.getFieldSignature());
                    if (fieldValue != null) {
                        setOut.put(lHolder, fieldValue);
                    }
                }
            } else if (right instanceof JFieldRef fRef) {
                JLinkValue value;
                if (right instanceof ClassConstant constant) {
                    // we have a constant, so we are done
                    value = new ClassValue(constant.getValue());
                } else if (right instanceof StringConstant strConstant) {
                    value = new StringValue(strConstant.getValue());
                } else if (right instanceof NullValue) {
                    value = new NullValue();
                } else if (right instanceof JStaticFieldRef staticFieldRef) {
                    value = setIn.get(new StaticFieldHolder(staticFieldRef.getFieldSignature()));
                } else {
                    value = JLinkIFDSProblem.TOP_VALUE;
                }
                setOut.put(lHolder, value);
            } else {
                // we don't support any other cases, so we assign TOP.
                setOut.put(lHolder, JLinkIFDSProblem.TOP_VALUE);
            }
        } else if (left instanceof JInstanceFieldRef fieldRef) {

            if (setIn.get(new LocalHolder(fieldRef.getBase())) instanceof ObjectValue objectValue) {
                JLinkValue value;
                if (right instanceof Local rLocal) {
                    value = setIn.get(new LocalHolder(rLocal));
                } else if (right instanceof ClassConstant constant) {
                    value = new ClassValue(constant.getValue());
                } else if (right instanceof StringConstant stringConstant) {
                    value = new StringValue(stringConstant.getValue());
                } else if (right instanceof NullValue) {
                    value = new NullValue();
                } else {
                    value = JLinkIFDSProblem.TOP_VALUE;
                }
                objectValue.addField(fieldRef.getFieldSignature(), value);
                setOut.put(new LocalHolder(fieldRef.getBase()), objectValue);
            }
        } else {
            // we do not care.
        }
    }

    @Override
    public void caseIdentityStmt(JIdentityStmt<?> jIdentityStmt) {
        defaultCaseStmt(jIdentityStmt);
        Value left = jIdentityStmt.getLeftOp();
        Value right = jIdentityStmt.getRightOp();

        if (left instanceof Local local) {
            LocalHolder lLocal = new LocalHolder(local);
            if (right instanceof IdentityRef) {
                // the local is not a constant and depends on external input
                if (setIn.get(lLocal) != null) {
                    setOut.put(lLocal, setIn.get(lLocal));
                }
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
