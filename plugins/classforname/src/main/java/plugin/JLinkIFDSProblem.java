package plugin;

import heros.DefaultSeeds;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import heros.flowfunc.KillAll;
import sootup.analysis.interprocedural.ifds.DefaultJimpleIFDSTabulationProblem;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.NullConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootField;
import sootup.core.model.SootMethod;
import sootup.core.types.ClassType;
import sootup.core.types.UnknownType;

import java.util.*;

public class JLinkIFDSProblem
        extends DefaultJimpleIFDSTabulationProblem<Map<ValueHolder, JLinkValue>, InterproceduralCFG<Stmt, SootMethod>> {

    public static final JLinkValue TOP_VALUE = new JLinkValue();
    private SootMethod entryMethod;
    private SootClass<?> sootClass;
    private Map<SootMethod, Map<ValueHolder, JLinkValue>> methodToConstants;
    private Map<ValueHolder, JLinkValue> seedValues;

    public JLinkIFDSProblem(InterproceduralCFG<Stmt, SootMethod> icfg,
                            SootMethod entryMethod,
                            Map<ValueHolder, JLinkValue> seedValues,
                            SootClass<?> sootClass) {
        super(icfg);
        this.entryMethod = entryMethod;
        this.methodToConstants = new HashMap<>();
        this.seedValues = seedValues;
        this.sootClass = sootClass;
    }

    @Override
    protected FlowFunctions<Stmt, Map<ValueHolder, JLinkValue>, SootMethod> createFlowFunctionsFactory() {
        return new FlowFunctions<>() {

            @Override
            public FlowFunction<Map<ValueHolder, JLinkValue>> getNormalFlowFunction(Stmt curr, Stmt succ) {
                return getNormalFlow(curr);
            }

            @Override
            public FlowFunction<Map<ValueHolder, JLinkValue>> getCallFlowFunction(Stmt callStmt, SootMethod destinationMethod) {
                return getCallFlow(callStmt, destinationMethod);
            }

            @Override
            public FlowFunction<Map<ValueHolder, JLinkValue>> getReturnFlowFunction(Stmt callSite, SootMethod calleeMethod, Stmt exitStmt, Stmt returnSite) {
                return getReturnFlow(callSite, calleeMethod, exitStmt, returnSite);
            }

            @Override
            public FlowFunction<Map<ValueHolder, JLinkValue>> getCallToReturnFlowFunction(Stmt callSite, Stmt returnSite) {
                return getCallToReturnFlow(callSite, returnSite);
            }
        };
    }

    FlowFunction<Map<ValueHolder, JLinkValue>> getNormalFlow(Stmt curr) {
        final SootMethod m = interproceduralCFG().getMethodOf(curr);
        return source -> {
            Set<Map<ValueHolder, JLinkValue>> res = new LinkedHashSet<>();
            JLinkVisitor visitor = new JLinkVisitor(source);
            curr.accept(visitor);
            res.add(visitor.getSetOut());
            methodToConstants.put(m, visitor.getSetOut());
            return res;
        };

    }

    FlowFunction<Map<ValueHolder, JLinkValue>> getCallFlow(Stmt callStmt, final SootMethod destinationMethod) {
        final AbstractInvokeExpr invokeExpr = callStmt.getInvokeExpr();
        final Local base;
        if (invokeExpr instanceof AbstractInstanceInvokeExpr instanceInvokeExpr) {
            base = instanceInvokeExpr.getBase();
        } else {
            base = null;
        }
        final List<Immediate> args = invokeExpr.getArgs();

        return source -> {
            LinkedHashSet<Map<ValueHolder, JLinkValue>> res = new LinkedHashSet<>();
            Map<ValueHolder, JLinkValue> constants = new HashMap<>();

            /* Propagate base as "this" reference to caller method.*/
            if (base != null) {
                constants.put(new LocalHolder(new Local("this", UnknownType.getInstance())), source.get(new LocalHolder(base)));
            }

            for (int i = 0; i < destinationMethod.getParameterCount(); i++) {
                JLinkValue value = null;
                if (args.get(i) instanceof Local argLocal) {
                    value = source.get(new LocalHolder(argLocal));
                } else if (args.get(i) instanceof ClassConstant classConstant) {
                    value = new ClassValue(classConstant.getValue());
                } else if (args.get(i) instanceof StringConstant stringConstant) {
                    value = new StringValue(stringConstant.getValue());
                } else if (args.get(i) instanceof NullConstant) {
                    value = new NullValue();
                }

                if (value != null) {
                    constants.put(new LocalHolder(destinationMethod.getBody().getParameterLocal(i)), value);
                }
            }

            // fill in the other locals we don't know
            for (Local l : destinationMethod.getBody().getLocals()) {
                if (! constants.containsKey(new LocalHolder(l))) {
                    constants.put(new LocalHolder(l), TOP_VALUE);
                }
            }

            // Fill in static values we may not know
            if (! destinationMethod.getDeclaringClassType().equals(entryMethod.getDeclaringClassType())) {
                ClassType destinationClass = destinationMethod.getDeclaringClassType();
                // TODO should we remove the static values that do not correspond to that class?
            }

            methodToConstants.put(destinationMethod, constants);
            res.add(constants);
            return res;
        };

    }

    FlowFunction<Map<ValueHolder, JLinkValue>> getReturnFlow(final Stmt callSite, final SootMethod calleeMethod,
                                                             Stmt exitStmt, Stmt returnSite) {

        SootMethod caller = interproceduralCFG().getMethodOf(returnSite);
        if (callSite instanceof AbstractDefinitionStmt<?, ?> definitionStmt) {
            if (definitionStmt.getLeftOp() instanceof Local) {
                final Local leftOpLocal = (Local) definitionStmt.getLeftOp();
                if (exitStmt instanceof JReturnStmt returnStmt) {
                    return source -> {
                        Set<Map<ValueHolder, JLinkValue>> ret = new LinkedHashSet<>();
                        JLinkValue returnValue = JLinkIFDSProblem.TOP_VALUE;
                        if (returnStmt.getOp() instanceof Local op) {
                            // for debugging
                            returnValue = source.get(new LocalHolder(op));
                        } else if (returnStmt.getOp() instanceof StringConstant constant) {
                            returnValue = new StringValue(constant.getValue());
                        } else if (returnStmt.getOp() instanceof ClassConstant constant) {
                            returnValue = new ClassValue(constant.getValue());
                        } else if (returnStmt.getOp() instanceof NullConstant) {
                            returnValue = new NullValue();
                        }


                        if (methodToConstants.containsKey(caller)) {
                            methodToConstants.get(caller).put(new LocalHolder(leftOpLocal), returnValue);
                        } else {
                            Map<ValueHolder, JLinkValue> map = new HashMap<>();
                            map.put(new LocalHolder(leftOpLocal), returnValue);
                            methodToConstants.put(caller, map);
                        }
                        ret.add(methodToConstants.get(caller));
                        return ret;
                    };
                }
            }
        }

        // a flow function that always returns an empty set
        return KillAll.v();
    }

    FlowFunction<Map<ValueHolder, JLinkValue>> getCallToReturnFlow(final Stmt callSite, Stmt returnSite) {
        final SootMethod m = interproceduralCFG().getMethodOf(callSite);
        return source -> {
            Set<Map<ValueHolder, JLinkValue>> res = new LinkedHashSet<>();
            JLinkVisitor visitor = new JLinkVisitor(source);
            callSite.accept(visitor);
            res.add(visitor.getSetOut());
            methodToConstants.put(m, visitor.getSetOut());
            return res;
        };
    }

    @Override
    protected Map<ValueHolder, JLinkValue> createZeroValue() {
        Map<ValueHolder, JLinkValue> entryMap = new HashMap<>();
        if (seedValues != null) {
            entryMap.putAll(seedValues);
        }

        for (Local l : entryMethod.getBody().getLocals()) {
            LocalHolder lHolder = new LocalHolder(l);
            if (! entryMap.containsKey(lHolder)) {
                entryMap.put(lHolder, TOP_VALUE);
            }
        }

        for (SootField field : sootClass.getFields()) {
            StaticFieldHolder fieldHolder = new StaticFieldHolder(field.getSignature());
            if (!entryMap.containsKey(fieldHolder)) {
                entryMap.put(fieldHolder, TOP_VALUE);
            }
        }
        return entryMap;
    }

    @Override
    public Map<Stmt, Set<Map<ValueHolder, JLinkValue>>> initialSeeds() {
        return DefaultSeeds.make(Collections.singleton(
                entryMethod.getBody().getStmtGraph().getStartingStmt()), zeroValue());
    }
}
