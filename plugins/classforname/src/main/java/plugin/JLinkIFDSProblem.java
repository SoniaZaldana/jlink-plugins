package plugin;

import heros.DefaultSeeds;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;
import sootup.analysis.interprocedural.ifds.DefaultJimpleIFDSTabulationProblem;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JReturnStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;

import java.util.*;

public class JLinkIFDSProblem
        extends DefaultJimpleIFDSTabulationProblem<Map<Local, JLinkValue>, InterproceduralCFG<Stmt, SootMethod>> {

    public static final JLinkValue TOP_VALUE = new JLinkValue();
    private SootMethod entryMethod;
    protected InterproceduralCFG<Stmt, SootMethod> icfg;
    private Map<SootMethod, Map<Local, JLinkValue>> methodToConstants;
    public JLinkIFDSProblem(InterproceduralCFG<Stmt, SootMethod> icfg, SootMethod entryMethod) {
        super(icfg);
        this.icfg = icfg;
        this.entryMethod = entryMethod;
        this.methodToConstants = new HashMap<>();
    }

    @Override
    protected FlowFunctions<Stmt, Map<Local, JLinkValue>, SootMethod> createFlowFunctionsFactory() {
        return new FlowFunctions<Stmt, Map<Local, JLinkValue>, SootMethod>() {

            @Override
            public FlowFunction<Map<Local, JLinkValue>> getNormalFlowFunction(Stmt stmt, Stmt n1) {
                return getNormalFlow(stmt);
            }

            @Override
            public FlowFunction<Map<Local, JLinkValue>> getCallFlowFunction(Stmt stmt, SootMethod sootMethod) {
                return getCallFlow(stmt, sootMethod);
            }

            @Override
            public FlowFunction<Map<Local, JLinkValue>> getReturnFlowFunction(Stmt stmt, SootMethod sootMethod, Stmt n1, Stmt n2) {
                return getReturnFlow(stmt, sootMethod, n1, n2);
            }

            @Override
            public FlowFunction<Map<Local, JLinkValue>> getCallToReturnFlowFunction(Stmt stmt, Stmt n1) {
                return getCallToReturnFlow(stmt, n1);
            }
        };
    }

    FlowFunction<Map<Local, JLinkValue>> getNormalFlow(Stmt curr) {
        final SootMethod m = interproceduralCFG().getMethodOf(curr);

        if (curr instanceof AbstractDefinitionStmt<?,?> definitionStmt) {
            final Value leftOp = definitionStmt.getLeftOp();
            if (leftOp instanceof Local) {
                return new FlowFunction<Map<Local, JLinkValue>>() {
                    @Override
                    public Set<Map<Local, JLinkValue>> computeTargets(Map<Local, JLinkValue> source) {
                        Set<Map<Local, JLinkValue>> res = new LinkedHashSet<>();
                        JLinkVisitor visitor;
                        if (source.isEmpty() ||
                                (methodToConstants.containsKey(m) && methodToConstants.get(m).size() > source.size())) {
                            // We carried forward more information than what source reflects.
                            visitor = new JLinkVisitor(methodToConstants.get(m));
                        } else {
                            visitor = new JLinkVisitor(source);
                        }
                        if (definitionStmt.getRightOp() instanceof JStaticFieldRef) {
                            // TODO gotta do something with the clinit.
                        }
                        curr.accept(visitor);
                        res.add(visitor.getSetOut());
                        methodToConstants.put(m, visitor.getSetOut());
                        return res;
                    }
                };
            }
        }
        return Identity.v();
    }

    FlowFunction<Map<Local, JLinkValue>> getCallFlow(Stmt callStmt, final SootMethod destinationMethod) {
        AbstractInvokeExpr invokeExpr = callStmt.getInvokeExpr();
        final List<Immediate> args = invokeExpr.getArgs();
        return new FlowFunction<Map<Local, JLinkValue>>() {
            @Override
            public Set<Map<Local, JLinkValue>> computeTargets(Map<Local, JLinkValue> source) {
                LinkedHashSet<Map<Local, JLinkValue>> res = new LinkedHashSet<>();
                Map<Local, JLinkValue> constants = new HashMap<>();

                for (int i = 0; i < destinationMethod.getParameterCount(); i++) {
                    if (args.get(i) instanceof Local argLocal) {
                        constants.put(destinationMethod.getBody().getParameterLocal(i), source.get(argLocal));
                    } else if (args.get(i) instanceof ClassConstant classConstant) {
                        constants.put(destinationMethod.getBody().getParameterLocal(i), new ClassValue(classConstant.getValue()));
                    } else if (args.get(i) instanceof StringConstant stringConstant) {
                        constants.put(destinationMethod.getBody().getParameterLocal(i), new StringValue(stringConstant.getValue()));
                    }
                }

                // fill in the other locals we don't know
                for (Local l : destinationMethod.getBody().getLocals()) {
                    if (! constants.containsKey(l)) {
                        constants.put(l, TOP_VALUE);
                    }
                }

                methodToConstants.put(destinationMethod, constants);
                res.add(constants);
                return res;
            }
        };
    }

    FlowFunction<Map<Local, JLinkValue>> getReturnFlow(final Stmt callSite, final SootMethod calleeMethod,
                                                   Stmt exitStmt, Stmt returnSite) {

        SootMethod caller = interproceduralCFG().getMethodOf(returnSite);
        if (callSite instanceof AbstractDefinitionStmt<?, ?> definitionStmt) {
            if (definitionStmt.getLeftOp() instanceof Local) {
                final Local leftOpLocal = (Local) definitionStmt.getLeftOp();
                if (exitStmt instanceof JReturnStmt returnStmt) {
                    return new FlowFunction<Map<Local, JLinkValue>>() {
                        @Override
                        public Set<Map<Local, JLinkValue>> computeTargets(Map<Local, JLinkValue> source) {
                            Set<Map<Local, JLinkValue>> ret = new LinkedHashSet<>();
                            JLinkValue returnValue = TOP_VALUE;
                            if (returnStmt.getOp() instanceof Local op) {
                                // for debugging
                                returnValue = source.get(op);
                            } else if (returnStmt.getOp() instanceof StringConstant constant) {
                                returnValue = new StringValue(constant.getValue());
                            } else if (returnStmt.getOp() instanceof ClassConstant constant) {
                                returnValue = new ClassValue(constant.getValue());
                            }

                            methodToConstants.get(caller).put(leftOpLocal, returnValue);
                            ret.add(methodToConstants.get(caller));
                            return ret;
                        }
                    };
                }
            }
        }

        // a flow function that always return an empty set
        return KillAll.v();
    }

    FlowFunction<Map<Local, JLinkValue>> getCallToReturnFlow(final Stmt callSite, Stmt returnSite) {
        final SootMethod m = interproceduralCFG().getMethodOf(callSite);

        return new FlowFunction<Map<Local, JLinkValue>>() {
            @Override
            public Set<Map<Local, JLinkValue>> computeTargets(Map<Local, JLinkValue> source) {
                Set<Map<Local, JLinkValue>> res = new LinkedHashSet<>();
                JLinkVisitor visitor = new JLinkVisitor(source);
                callSite.accept(visitor);
                res.add(visitor.getSetOut());
                methodToConstants.put(m, visitor.getSetOut());
                return res;
            }
        };
    }

    @Override
    protected Map<Local, JLinkValue> createZeroValue() {
        Map<Local, JLinkValue> entryMap = new HashMap<>();
        for (Local l : entryMethod.getBody().getLocals()) {
            entryMap.put(l, TOP_VALUE);
        }

        return entryMap;
    }

    @Override
    public Map<Stmt, Set<Map<Local, JLinkValue>>> initialSeeds() {
        return DefaultSeeds.make(Collections.singleton(
                entryMethod.getBody().getStmtGraph().getStartingStmt()), zeroValue());
    }
}
