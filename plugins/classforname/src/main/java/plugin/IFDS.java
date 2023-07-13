package plugin;

import heros.InterproceduralCFG;
import plugin.inputlocation.JLinkInputLocation;
import sootup.analysis.interprocedural.icfg.JimpleBasedInterproceduralCFG;
import sootup.analysis.interprocedural.ifds.JimpleIFDSSolver;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
import sootup.core.frontend.ResolveException;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.stmt.*;


import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;


import sootup.core.types.ClassType;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.JavaProject;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.language.JavaLanguage;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class IFDS {
    protected JavaView view;
    protected JavaIdentifierFactory factory;
    protected Map<ClassType, byte[]> allClasses;
    protected Map<ClassType, JavaView> viewMap;
    private final String SERVICE_LOADER = "java.util.ServiceLoader";
    private final String LOAD = "load";

    /* The counters below are just to render a summary at the end of the analysis */
    int ifdsCounter;
    int constantCounter;
    int unknownCounter;

    public IFDS(Map<ClassType, byte[]> allClasses) {
        ifdsCounter = 0;
        constantCounter = 0;
        unknownCounter = 0;
        this.viewMap = new HashMap<>();
        this.allClasses = allClasses;

        /* Generate a Soot view containing all reachable classes */
        factory = JavaIdentifierFactory.getInstance();
        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(allClasses))
                .build();

        view = javaProject.createView();
    }

    /**
     * Fetch SootView from view containing all reachable classes
     * @param targetClassName class to fetch.
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
     * @param stmt the soot stmt to evaluate
     * @return
     */
    private boolean isServiceLoaderCall(Stmt stmt) {
        if (stmt instanceof JAssignStmt assignStmt) {
            if (assignStmt.containsInvokeExpr() && assignStmt.getInvokeExpr() instanceof JStaticInvokeExpr invokeExpr) {
                MethodSignature signature = invokeExpr.getMethodSignature();
                return signature.getName().equals(LOAD)
                        && signature.getDeclClassType().getFullyQualifiedName().equals(SERVICE_LOADER);
            }
        }
        return false;
    }


    public Map<SootMethod, List<EntryMethodWithCall>> doServiceLoaderStaticAnalysis(String targetClassName, FileWriter writer) {
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
                    constantCounter++;
                } else {
                    /* Call doesn't have readily available constants, so we do further analysis */

                    /**
                     * We break down into two main analysis cases:
                     * (A) We have sufficient information in the enclosing class to deduce the values passed on
                     * to ServiceLoader.load();
                     * (B) We have insufficient information in the enclosing class, so we need to find other
                     * classes referenced.
                     *
                     * Each of those analysis cases breaks down into two subcases:
                     * (1) We have sufficient information in the enclosing method to determine the
                     * ServiceLoader.load() constants i.e. "forward flow analysis"
                     * (2) We have insufficient information in the enclosing method and need to backtrack
                     * the call graph i.e. "backward flow analysis"
                     *
                     * In its current state, I have backtracked the implementation for (B) due to performance issues.
                     */


                    /**
                     * Tackling case (A.1) i.e. we examine just the enclosing class and try to
                     * determine whether "forward flow" analysis is sufficient. i.e.
                     *
                     * public static void foo() {
                     *      Class<?> c = bar();
                     *      ServiceLoader.load(c);
                     * }
                     *
                     * public Class<?> void bar() {
                     *      return Class.forName("Test");
                     * }
                     *
                     */

                    JavaView view = inputLocationWithSingleClass(sm.getDeclaringClassType()).createView();
                    JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = getSolver(view.getMethod(sm.getSignature()).get(), view);
                    if (solver == null) {
                        // Refer to note in #getSolver to see why this could be null.

                        try {
                            // Document in report problematic classes.
                            writer.write("ClassName: " + targetClassName + " \n");
                            writer.write("\t\t Solver was null");
                            writer.write("\t\t" + call + "\n");
                        } catch (IOException e) {
                        }
                        continue;
                    }

                    List<Map<Local, JLinkValue>> results =
                            (List<Map<Local, JLinkValue>>) solver.equivResultsAt(call.getStmt()).stream().toList();

                    /* A note on solver#equivResultsAt:
                       This is not a native Soot method. I added it to their JimpleSolver.
                       This ensures we can fetch values from the solver based on statement (instruction) equivalence.
                       The reason is that creating tightly constrained views (views containing only 1 class)
                       requires regenerating views frequently. As a result, methods and statements will be
                       different objects as they originate from different views.
                     */


                    /* Next we interpret the interprocedural results to see if we found any constant values */
                    List<ServiceLoaderCall> constantCalls = getCallsWithConstantParams(results, call);

                    if (! constantCalls.isEmpty()) {

                        /* We found sufficient information with the analysis above, so we add the call with
                         * the respective constant parameters and the enclosing method as the "entry method"
                         */
                        ifdsCounter += addConstantCalls(entryMethodsWithCalls, constantCalls, sm);
                    } else {

                        /* Tackling case (A.2) i.e. we examine just the enclosing class, but we backtrack
                         * in the call graph in that class if necessary.
                         */

                        if (backtrackCallGraphInEnclosingClass(sm, entryMethodsWithCalls, call)) {
                            ifdsCounter += entryMethodsWithCalls.size();
                        } else {
                            /* We are likely in case (B) where we make calls to outside classes.
                            * Though there are some classes with ServiceLoader.load(someParam) calls
                            * that have no callers anywhere such as:
                            * javax.imageio.spi.ServiceRegistry - 2 SL calls.
                            */

                            unknownCounter++;

                            try {
                                // documenting the classes i can't solve yet.
                                writer.write("ClassName: " + targetClassName + " \n");
                                writer.write("\t\t" + call + "\n");
                            } catch (IOException e) {
                            }
                        }
                    }
                }
            }
            if (! entryMethodsWithCalls.isEmpty()) {
                analysisMap.put(sm, entryMethodsWithCalls);
            }
        }

        return analysisMap;
    }

    /**
     * Generates an input location with a single class
     * @param clazz the class to include in the input location
     * @return
     */
    private JavaProject inputLocationWithSingleClass(ClassType clazz) {
        Map<ClassType, byte[]> classes = new HashMap<>();
        allClasses.keySet()
                .stream()
                .filter(type -> type.equals(clazz))
                .findFirst().ifPresent(classType -> classes.put(classType, allClasses.get(classType)));

        return JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(classes))
                .build();
    }

    /**
     * Creates interprocedural solver based on given java view
     * @param entryMethod the entry method for analysis
     * @param view the java view containing classes to analyze
     * @return
     */
    public JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> getSolver(SootMethod entryMethod, JavaView view) {
        try {
            JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG(view,
                    entryMethod.getSignature(),
                    false,
                    false);
            JLinkIFDSProblem problem = new JLinkIFDSProblem(icfg, entryMethod);
            JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = new JimpleIFDSSolver<>(problem);
            solver.solve(entryMethod.getDeclaringClassType().getClassName());
            return solver;
        } catch (ResolveException e) {
            /* Note, we get an exception here when the call graph tries to evaluate an edge to a function
            in another class. It will complain about not being able to find the class in the view.
            This is true, as the view only contains the class being evaluated for the sake of performance.
            This is likely an avenue for incremental call graphs - catching this exception and increasing
            the size of the view by 1. It might be a slippery slope if the graph grows exponentially.
            For now, we return null if this happens.
             */
            return null;
        }
    }

    /**
     * Generates a complete call graph for a given class. In doing so, we can "backtrack"
     * the call graph and identify any callers to the Soot method enclosing the ServiceLoader call.
     * This enables us to run the interprocedural framework with an alternate entry method
     * and hopefully propagate sufficient constant information.
     * @param sm The soot method containing the ServiceLoader call
     * @param entryMethodsWithCalls the list tracking the entry method that gave us sufficient constant info
     *                              and the ServiceLoader call with constant information.
     * @param call the ServiceLoader call we are interested in learning constant parameters.
     * @return
     */
    private boolean backtrackCallGraphInEnclosingClass(SootMethod sm,
                                                    List<EntryMethodWithCall> entryMethodsWithCalls,
                                                    ServiceLoaderCall call) {

        JavaView view = inputLocationWithSingleClass(sm.getDeclaringClassType()).createView();

        /* Initialize call graph with all signatures in class */
        CallGraphAlgorithm cha =
                new ClassHierarchyAnalysisAlgorithm(view);

        /* Ensure you get the class from the view we created above */
        JavaClassType classSignature = factory.getClassType(sm.getDeclaringClassType().getFullyQualifiedName());
        SootClass<?> sootClass = view.getClass(classSignature).get();
        List<MethodSignature> classMethodSignatures = new ArrayList<>(sootClass.getMethods()
                .stream().map(SootMethod::getSignature).toList());

        CallGraph cg = cha.initialize(classMethodSignatures);
        return backTrackRecursive(view.getMethod(sm.getSignature()).get(), entryMethodsWithCalls, call, cg, view); // note - important to pass down method corresponding to examined view.
    }

    private boolean backTrackRecursive(SootMethod sm,
                                    List<EntryMethodWithCall> entryMethodsWithCalls,
                                    ServiceLoaderCall call,
                                    CallGraph cg,
                                    JavaView view) {

        Object[] calls = cg.callsTo(sm.getSignature()).toArray();

        /* Base case 0: No calls to method with ServiceLoader call */
        if (calls.length == 0) {
            return false;
        }

        /* Found entry method(s) */
        boolean foundConstants = false;
        for (int i = 0; i < calls.length; i++) {
            Optional<? extends SootMethod> mOpt = view.getMethod((MethodSignature) calls[i]);
            if (mOpt.isPresent()) {

                /* Try to do interprocedural analysis with this caller method as entry method */
                JavaSootMethod callerMethod = (JavaSootMethod) mOpt.get();
                JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> callerSolver = getSolver(callerMethod, view);

                if (callerSolver == null) {
                    continue; // Refer to note in #getSolver for context.
                }

                List<Map<Local, JLinkValue>> results = (List<Map<Local, JLinkValue>>)
                        callerSolver.equivResultsAt(call.getStmt()).stream().toList();
                List<ServiceLoaderCall> constantCalls = getCallsWithConstantParams(results, call);
                if (! constantCalls.isEmpty()) {
                    /* Base case 1: Direct caller method has sufficient info */
                    foundConstants = true;
                    addConstantCalls(entryMethodsWithCalls, constantCalls, callerMethod);

                    /* Note, there is a possibility here that a certain function foo() has 2 calls
                    to an enclosing service loader method and only one of the calls has sufficient information.
                    i.e.

                        private void foo(Class<?> c) {
                            bar(c);
                            bar(Test.class);
                        }

                        private void bar(Class<?> c) {
                            ServiceLoader.load(c);
                        }

                        At the moment, I am not dealing with this situation and digging deeper
                         with the call we couldn't find sufficient info for.
                         TODO - can probably refactor this to account for this.
                     */
                } else {
                    /* Recursive case: We need to backtrack once again.
                       Note how caller method is the method we are backtracking from now */
                    foundConstants = foundConstants || backTrackRecursive(callerMethod, entryMethodsWithCalls, call, cg, view);
                }
            }
        }

        return foundConstants;
    }

    /**
     * Takes an interprocedural set of results and examines them to see if any constant values were
     * propagated.
     * Then, it matches them with their respective argument per the service loader call.
     * Finally, we produce a list of <Method, ServiceLoader> calls to account for methods with multiple
     * calls to methods enclosing ServiceLoader calls i.e.
     *
     * public void foo() {
     *     bar(A.class);
     *     bar(B.class);
     * }
     *
     * public void bar(Class c) {
     *     ServiceLoader.load(c);
     * }
     * @param results interprocedural set of results
     * @param call the call we are interested in propagating constant values to
     * @return
     */
    public List<ServiceLoaderCall> getCallsWithConstantParams(List<Map<Local, JLinkValue>> results,
                                                              ServiceLoaderCall call) {
        List<ServiceLoaderCall> calls = new ArrayList<>();
        for (Map<Local, JLinkValue> result : results) {  // multiple results indicate multiple calls to SL enclosing methods
            boolean foundConstant = false;
            ServiceLoaderCall clone = call.clone();
            for (Immediate arg : call.getArgMap().keySet()) { // immediate is soot lingo for method arguments
                if (result.containsKey(arg)) {
                    JLinkValue val = result.get(arg);
                    if (val != null && val != JLinkIFDSProblem.TOP_VALUE) {
                        clone.addPropValue(arg, val);
                        foundConstant = true;
                    }
                }
            }
            if (foundConstant) calls.add(clone);
        }
        return calls;
    }

    /**
     * Adds service loader calls with constant parameters along with its entry method.
     * Also ensures we don't have repeat ServiceLoader class values for the same method.
     * @param entryMethodsWithCalls
     * @param calls
     * @param sm
     * @return
     */
    public int addConstantCalls(List<EntryMethodWithCall> entryMethodsWithCalls,
                                    List<ServiceLoaderCall> calls,
                                    SootMethod sm) {
        int constantCounter = 0;
        for (ServiceLoaderCall call : calls) {
            EntryMethodWithCall emc = new EntryMethodWithCall(sm, call);
            if (! entryMethodsWithCalls.contains(emc)) {
                entryMethodsWithCalls.add(emc);
                constantCounter += 1;
            }
        }
        return constantCounter;
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

        @Override
        public boolean equals(Object o) {
            if (o instanceof EntryMethodWithCall emc) {
                return emc.getEntryMethod().equals(entryMethod) && emc.getCall().equals(call);
            }
            return false;
        }
    }
}
