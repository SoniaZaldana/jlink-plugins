package plugin;

import heros.InterproceduralCFG;
import sootup.analysis.interprocedural.icfg.JimpleBasedInterproceduralCFG;
import sootup.analysis.interprocedural.ifds.JimpleIFDSSolver;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.stmt.*;


import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;


import sootup.core.types.ClassType;
import sootup.java.bytecode.inputlocation.JLinkInputLocation;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.JavaProject;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.language.JavaLanguage;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.util.*;
import java.util.stream.Collectors;

public class IFDS {
    protected JavaView view;
    protected JavaIdentifierFactory factory;
    protected CallGraph cg;

    public IFDS(Map<ClassType, byte[]> allClasses) {
        factory = JavaIdentifierFactory.getInstance();
        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(allClasses))
                .build();

        view = javaProject.createFullView();
        System.out.println("finished resolving all classes");

        /* Create a massive call graph with all methods in the view.
        * This helps us retroactively look up who called ServiceLoader.load() */
        List<MethodSignature> allSignatures = new ArrayList<>();
        for (JavaSootClass clazz : view.getClasses()) {
            allSignatures.addAll(clazz.getMethods().stream().map(SootMethod::getSignature).collect(Collectors.toList()));
        }
        CallGraphAlgorithm cha =
                new RapidTypeAnalysisAlgorithm(view, view.getTypeHierarchy());
        cg = cha.initialize(allSignatures);
    }

    /**
     * Fetch SootClass from SootView
     * @param targetClassName class name
     * @return
     */
    private SootClass<?> getClass(String targetClassName) {
        JavaClassType classSignature = factory.getClassType(targetClassName);
        return view.getClass(classSignature).get();
    }

    /**
     * Returns a map of an enclosing Soot Method and a list of all service loader calls inside of it
     * in a given class.
     * @param sootClass the soot class to analyse
     * @return
     */
    private Map<SootMethod, List<ServiceLoaderCall>> getEnclosingMethodsWithServiceLoaderCalls(SootClass<?> sootClass) {
        Map<SootMethod, List<ServiceLoaderCall>> methodsEnclosingSLCalls = new HashMap<>();
        for (SootMethod sm : sootClass.getMethods()) {

            List<ServiceLoaderCall> calls = new ArrayList<>();
            if (sm.hasBody()) {
                sm.getBody().getStmts().forEach(stmt -> {
                    if (isServiceLoaderCall(stmt)) {
                        calls.add(new ServiceLoaderCall(stmt));
                    }
                });
            }

            if (!calls.isEmpty()) {
                methodsEnclosingSLCalls.put(sm, calls);
            }
        }
        return methodsEnclosingSLCalls;
    }

    /**
     * Determines whether a given statement contains a call to ServiceLoader.load()
     * @param stmt the stmt to evaluate
     * @return
     */
    private boolean isServiceLoaderCall(Stmt stmt) {
        if (stmt instanceof JAssignStmt assignStmt) {
            if (assignStmt.containsInvokeExpr() && assignStmt.getInvokeExpr() instanceof JStaticInvokeExpr invokeExpr) {
                MethodSignature signature = invokeExpr.getMethodSignature();
                return signature.getName().equals("load")
                        && signature.getDeclClassType().getFullyQualifiedName().equals("java.util.ServiceLoader");
            }
        }
        return false;
    }

    /**
     * Generates a map with propagated constant values to service loader calls in the following format:
     * Keys: The enclosing {@link SootMethod} containing the ServiceLoader.load call(s).
     * Values: A list of {@link EntryMethodWithCall} encompassing an entry method along with the
     * {@link ServiceLoaderCall} containing propagated constant values in its argument map.
     *
     * Note, {@link EntryMethodWithCall#entryMethod} can be the same as if that method contained sufficient
     * information to draw conclusions about any constant values propagated to the
     * ServiceLoader.load() calls.
     *
     * For example, the method below yields the following entry in the map:
     *
     *      public static void foo() {
     *          ServiceLoader.load(Test.class);
     *      }
     *
     * Key: {@link SootMethod} foo()
     * Value: List containing single {@link EntryMethodWithCall} object,
     * where {@link EntryMethodWithCall#entryMethod} is the same as the key
     * and {@link EntryMethodWithCall#call} contains an argument map with propagated constant values.
     **
     * @param targetClassName the class to evaluate
     * @return
     */
    public Map<SootMethod, List<EntryMethodWithCall>> doServiceLoaderStaticAnalysis(String targetClassName) {
        SootClass<?> sc = getClass(targetClassName);

        /* We first find all methods in the class with service loader calls */
        Map<SootMethod, List<ServiceLoaderCall>> methodsEnclosingSLCalls = getEnclosingMethodsWithServiceLoaderCalls(sc);

        /* Then, we perform analysis on each of these methods and calls to determine constant values */
        Map<SootMethod, List<EntryMethodWithCall>> analysisMap = new HashMap<>();
        for (SootMethod sm : methodsEnclosingSLCalls.keySet()) {
            List<EntryMethodWithCall> entryMethodsWithCalls = new ArrayList<>();
            for (ServiceLoaderCall call : methodsEnclosingSLCalls.get(sm)) {
                if (call.hasConstantParameters()) {
                    /* Call has constant parameters. We simply add the "entry method" as the enclosing method */
                    entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
                } else {
                    /* Call doesn't have readily available constants, so we do further analysis */

                    /**
                     * We have two analysis cases:
                     * (i) We have sufficient information in the enclosing method to deduce the values passed
                     *     on to ServiceLoader.load().
                     * (ii) The enclosing method does not offer enough information to deduce values passed on
                     *      to ServiceLoader.load(), so we need to do more involved analysis.
                     *
                     * First, we tackle case (i) by setting the enclosing method with the call as the "entry
                     * method" for interprocedural analysis.
                     * For example, in functions like the ones below, it suffices to use "foo" as the entry
                     * method for interprocedural analysis.
                     *      public void foo() {
                     *          Class clazz = bar();
                     *          ServiceLoader.load(clazz);
                     *      }
                     *      or
                     *      public void foo() {
                     *          ServiceLoader.load(Class.forName("Test.clazz"));
                     *      }
                     */

                    JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = getSolver(sm); // enclosing method as entry method
                    List<Map<Local, JLinkValue>> results =
                            (List<Map<Local, JLinkValue>>) solver.ifdsResultsAt(call.getStmt()).stream().toList();

                    if (addConstantParametersToCall(results, call)) {

                        /* We found sufficient information with the analysis above, so we add the call with
                           the respective constant parameters and the enclosing method as the "entry method"
                         */
                        entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
                    } else {

                        /**
                         * Tackling case (ii) where the enclosing method did not have sufficient information
                         * to determine any constant information.
                         * For example, in the functions below we need to backtrack in the call graph
                         * to find the callers of the enclosing method, and use those as entry methods instead.
                         *      public void bar() {
                         *          foo(Test.class);
                         *      }
                         *      public void foo(Class clazz) {
                         *          ServiceLoader.load(clazz);
                         *      }
                         * Using the enclosing method "foo" as the entry method is insufficient. We need to
                         * find all callers to foo (namely bar) and use that as the entry method for the
                         * analysis framework to successfully find the parameters passed on to the call.
                         */

                        Map<SootMethod, PropagatedValues> methodWithPropValues = getEntryMethodsWithPropagatedValues(sm);
                        for (SootMethod entryMethod : methodWithPropValues.keySet()) {
                            JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> entrySolver = getSolver(entryMethod);

                            // TODO I broke something here - not working.
                            results =
                                    (List<Map<Local, JLinkValue>>) entrySolver.ifdsResultsAt(call.getStmt()).stream().toList();

                            /* Final attempt to get some constant values */
                            if (addConstantParametersToCall(results, call)) {
                                entryMethodsWithCalls.add(new EntryMethodWithCall(entryMethod, call));
                            }

                        }
                    }
                }
            }

            analysisMap.put(sm, entryMethodsWithCalls);
        }

        return analysisMap;
    }

    public boolean addConstantParametersToCall(List<Map<Local, JLinkValue>> results, ServiceLoaderCall call) {

        boolean foundPropagatedValues = false;
        for (Immediate i : call.getArgMap().keySet()) {
            for (Map<Local, JLinkValue> set : results) {
                if (set.containsKey(i) && set.get(i) != JLinkIFDSProblem.TOP_VALUE) {
                    call.addPropValue(i, set.get(i));
                    foundPropagatedValues = true;
                }
            }
        }
        return foundPropagatedValues;
    }

    /**
     * Generates an interprocedural analysis solver using a given entry method.
     * @param entryMethod
     * @return
     */
    public JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> getSolver(SootMethod entryMethod) {
        JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG(
                view,
                entryMethod.getSignature(),
                false,
                false);

        JLinkIFDSProblem problem = new JLinkIFDSProblem(icfg, entryMethod);
        JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = new JimpleIFDSSolver<>(problem);
        solver.solve(entryMethod.getDeclaringClassType().getClassName());
        return solver;
    }

    /**
     * Finds the respective "entry methods" for a given target method that grant sufficient values
     * to propagate to ServiceLoader.load() calls.
     * Generates a map of {@link SootMethod} to {@link PropagatedValues}
     *
     *
     * @param targetMethod the method enclosing the ServiceLoader.load() call.
     * @return
     */
    private Map<SootMethod, PropagatedValues> getEntryMethodsWithPropagatedValues(SootMethod targetMethod) {

        Map<SootMethod, PropagatedValues> propValuesMap = new HashMap<>();
        Object[] calls = cg.callsTo(targetMethod.getSignature()).toArray();

        if (calls.length > 0) {

            PropagatedValues propValues = new PropagatedValues();
            for (int i = 0; i < calls.length; i++) {
                Optional<? extends SootMethod> mOpt = view.getMethod((MethodSignature) calls[i]);
                if (mOpt.isPresent()) {
                    /* Get the stmts responsible for calling target method */
                    JavaSootMethod callerMethod = (JavaSootMethod) mOpt.get();
                    List<Stmt> callStmts = callerMethod.getBody().getStmts().stream()
                            .filter(stmt ->
                                    stmt instanceof JInvokeStmt invokeStmt
                                            && invokeStmt.getInvokeExpr().getMethodSignature().equals(targetMethod.getSignature()))
                            .toList();

                    for (Stmt call : callStmts) {
                        if (hasConstantParameter(call)) {
                            /* Found a direct caller with parameters we needed */
                            propValues.addCallSiteWithValues(call, call.getInvokeExpr().getArgs());
                        } else {
                            /* We need to do a bit of interprocedural analysis to find values propagated.
                             *  We first check if we can determine the value by analyzing the existing method */
                            if (! foundConstantInEnclosingMethod(propValues,callerMethod, call)) {
                                /* We were unable to find sufficient constant information within caller method.
                                   Need to backtrack more in the call graph */
                                // big TODO
                            }


                        }
                    }
                    propValuesMap.put(callerMethod, propValues);
                }
            }
        }
        return propValuesMap;
    }

    private boolean foundConstantInEnclosingMethod(PropagatedValues propValues, JavaSootMethod callerMethod, Stmt call) {
        boolean foundValues = false;
        JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = getSolver(callerMethod);
        List<Map<Local, JLinkValue>> results =
                (List<Map<Local, JLinkValue>>) solver.ifdsResultsAt(call).stream().toList();

        Value var = call.getInvokeExpr().getArg(0);
        for (Map<Local, JLinkValue> set : results) {
            if (set.containsKey(var) && set.get(var) != JLinkIFDSProblem.TOP_VALUE) {
                propValues.addPropagatedValue(call, set.get(var));
                foundValues = true;
            }
        }

        return foundValues;
    }

    private boolean hasConstantParameter(Stmt call) {
        return call.getInvokeExpr().getArg(0) instanceof ClassConstant;
    }


    public class EntryMethodWithCall {
        private final SootMethod entryMethod;
        private ServiceLoaderCall call;

        public EntryMethodWithCall(SootMethod entryMethod, ServiceLoaderCall call) {
            this.entryMethod = entryMethod;
            this.call = call;
        }

        public SootMethod getEntryMethod() {
            return this.entryMethod;
        }

        public ServiceLoaderCall getCall() {
            return this.call;
        }
    }


    // TODO Might not need this data structure. Perhaps finding entry method is sufficient.
    class PropagatedValues {
        Map<Stmt, List<JLinkValue>> values;

        public PropagatedValues() {
            this.values = new HashMap<>();
        }

        public void addCallSiteWithValues(Stmt stmt, List<Immediate> propValues) {
            List<JLinkValue> valueList = new ArrayList<>();
            for (Immediate i : propValues) {
                if (i instanceof ClassConstant c) {
                    valueList.add(new ClassValue(c.getValue()));
                }
            }
            this.values.put(stmt, valueList);
        }

        public void addPropagatedValue(Stmt stmt, JLinkValue value) {
            if (! this.values.containsKey(stmt)) {
                this.values.put(stmt, new ArrayList<>());
            }
            this.values.get(stmt).add(value);
        }

        public List<JLinkValue> getPropValues(Stmt stmt) {
            return this.values.get(stmt);
        }
    }

}
