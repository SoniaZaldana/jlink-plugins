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
    int ifdsCounter;
    int constantCounter;

    public IFDS(Map<ClassType, byte[]> allClasses) {
        ifdsCounter = 0;
        constantCounter = 0;
        this.viewMap = new HashMap<>();
        this.allClasses = allClasses;
        factory = JavaIdentifierFactory.getInstance();
        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(allClasses))
                .build();

        view = javaProject.createView();
        System.out.println("finished resolving all classes");
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
            try {
                if (sm.hasBody()) {
                    sm.getBody().getStmts().forEach(stmt -> {
                        if (isServiceLoaderCall(stmt)) {
                            calls.add(new ServiceLoaderCall(stmt));
                        }
                    });
                }
            } catch (Exception e) {
                // do nothing
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
                        try {
                            writer.write("ClassName: " + targetClassName + " \n");
                            writer.write("\t\t Solver was null");
                            writer.write("\t\t" + call + "\n");
                        } catch (IOException e) {
                            // do nothing
                        }
                        continue;
                    }

                    /* Note, solver#equivResultsAt ensures we find the results for the given call based on equivalence,
                       since they will likely be different objects since they originate from different views.
                     */

                    List<Map<Local, JLinkValue>> results =
                            (List<Map<Local, JLinkValue>>) solver.equivResultsAt(call.getStmt()).stream().toList();

                    if (addConstantParametersToCall(results, call)) {

                        /* We found sufficient information with the analysis above, so we add the call with
                         * the respective constant parameters and the enclosing method as the "entry method"
                         */
                        entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
                        ifdsCounter++;
                    } else {

                        /* Tackling case (A.2) i.e. we examine just the enclosing class, but we backtrack
                         * in the call graph in that class if necessary.
                         */

                        if (backtrackCallGraphInEnclosingClass(sm, entryMethodsWithCalls, call)) {
                            // do nothing
                            System.out.println("We found " + entryMethodsWithCalls.size() + " calls in " + targetClassName);
                            ifdsCounter += entryMethodsWithCalls.size();
                        } else {
                            try {
                                writer.write("ClassName: " + targetClassName + " \n");
                                writer.write("\t\t" + call + "\n");
                            } catch (IOException e) {
                                // do nothing
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
            // todo - probable avenue to expand call graph incrementally here.

        } catch (Exception e) {
            // todo - something else went wrong here - likely stuff with AccessController.doPrivileged()
        }

        return null;
    }

    private boolean backtrackCallGraphInEnclosingClass(SootMethod sm,
                                                    List<EntryMethodWithCall> entryMethodsWithCalls,
                                                    ServiceLoaderCall call) {

        JavaView view = inputLocationWithSingleClass(sm.getDeclaringClassType()).createView();
        CallGraphAlgorithm cha =
                new ClassHierarchyAnalysisAlgorithm(view);

        /* Initialize call graph with all signatures in class */

        /* Ensure you get the class from the correct view */
        JavaClassType classSignature = factory.getClassType(sm.getDeclaringClassType().getFullyQualifiedName());
        SootClass<?> sootClass = view.getClass(classSignature).get();
        
        List<MethodSignature> classMethodSignatures = new ArrayList<>(sootClass.getMethods()
                .stream().map(SootMethod::getSignature).toList());


        try {
            CallGraph cg = cha.initialize(classMethodSignatures);
            // note - important to pass down method corresponding to examined view.
            return backTrackRecursive(view.getMethod(sm.getSignature()).get(), entryMethodsWithCalls, call, cg, view);
        } catch (Exception e) {
            // todo do nothing for now. Likely an issue initializing call graph because of AccessController.doPrivileged()
        }

        return false;
//        return call.hasConstantParameters();
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
                    continue; // todo - hopefully won't need this null condition check soon.
                }

                List<Map<Local, JLinkValue>> results = (List<Map<Local, JLinkValue>>)
                        callerSolver.equivResultsAt(call.getStmt()).stream().toList();
                ServiceLoaderCall clone = call.clone();


                if (addConstantParametersToCall(results, clone)) {
                    /* Base case 1: Direct caller method has sufficient info */
                    entryMethodsWithCalls.add(new EntryMethodWithCall(callerMethod, clone));
                    foundConstants = true;
                } else {
                    /* Recursive case: We need to backtrack once again */
                    foundConstants = foundConstants || backTrackRecursive(callerMethod, entryMethodsWithCalls, call, cg, view);
                }
            }
        }

        return foundConstants;
    }

    public boolean addConstantParametersToCall(List<Map<Local, JLinkValue>> results, ServiceLoaderCall call) {

        boolean foundPropagatedValues = false;
        for (Immediate i : call.getArgMap().keySet()) {
            for (Map<Local, JLinkValue> set : results) {
                if (set.containsKey(i) && set.get(i) != JLinkIFDSProblem.TOP_VALUE && set.get(i) != null) {
                    call.addPropValue(i, set.get(i));
                    foundPropagatedValues = true;
                }
            }
        }
        return foundPropagatedValues;
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
}
