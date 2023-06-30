package plugin;

import heros.InterproceduralCFG;
import plugin.inputlocation.JLinkInputLocation;
import sootup.analysis.interprocedural.icfg.JimpleBasedInterproceduralCFG;
import sootup.analysis.interprocedural.ifds.JimpleIFDSSolver;
import sootup.callgraph.CallGraph;
import sootup.callgraph.CallGraphAlgorithm;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;
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
    protected Map<ClassType, byte[]> allClasses;
    private List<MethodSignature> allSignatures;

    protected Map<ClassType, CallGraph> cgMap;

    protected Map<ClassType, JavaView> viewMap;

    public IFDS(Map<ClassType, byte[]> allClasses) {
        this.cgMap = new HashMap<>();
        this.viewMap = new HashMap<>();
        this.allClasses = allClasses;
        factory = JavaIdentifierFactory.getInstance();
        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(allClasses))
                .build();

        view = javaProject.createFullView();
        System.out.println("finished resolving all classes");

        /* Create a massive call graph with all methods in the view.
        * This helps us retroactively look up who called ServiceLoader.load() */
        allSignatures = new ArrayList<>();

        for (JavaSootClass clazz : view.getClasses()) {
            allSignatures.addAll(clazz.getMethods().stream().map(SootMethod::getSignature).collect(Collectors.toList()));
        }

//        CallGraphAlgorithm cha =
//                new ClassHierarchyAnalysisAlgorithm(view);
//        List<MethodSignature> test = new ArrayList<>();
//        test.add(allSignatures.get(0));
//        cg = cha.initialize(test);
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
//    public Map<SootMethod, List<EntryMethodWithCall>> doServiceLoaderStaticAnalysis(String targetClassName) {
//        SootClass<?> sc = getClass(targetClassName);
//
//        /* We first find all methods in the class with service loader calls */
//        Map<SootMethod, List<ServiceLoaderCall>> methodsEnclosingSLCalls = getEnclosingMethodsWithServiceLoaderCalls(sc);
//
//        /* Then, we perform analysis on each of these methods and calls to determine constant values */
//        Map<SootMethod, List<EntryMethodWithCall>> analysisMap = new HashMap<>();
//        for (SootMethod sm : methodsEnclosingSLCalls.keySet()) {
//            List<EntryMethodWithCall> entryMethodsWithCalls = new ArrayList<>();
//            for (ServiceLoaderCall call : methodsEnclosingSLCalls.get(sm)) {
//                if (call.hasConstantParameters()) {
//                    /* Call has constant parameters. We simply add the "entry method" as the enclosing method */
//                    entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
//                } else {
//                    /* Call doesn't have readily available constants, so we do further analysis */
//
//                    /**
//                     * We have two analysis cases:
//                     * (i) We have sufficient information in the enclosing method to deduce the values passed
//                     *     on to ServiceLoader.load().
//                     * (ii) The enclosing method does not offer enough information to deduce values passed on
//                     *      to ServiceLoader.load(), so we need to do more involved analysis.
//                     *
//                     * First, we tackle case (i) by setting the enclosing method with the call as the "entry
//                     * method" for interprocedural analysis.
//                     * For example, in functions like the ones below, it suffices to use "foo" as the entry
//                     * method for interprocedural analysis.
//                     *      public void foo() {
//                     *          Class clazz = bar();
//                     *          ServiceLoader.load(clazz);
//                     *      }
//                     *      or
//                     *      public void foo() {
//                     *          ServiceLoader.load(Class.forName("Test.clazz"));
//                     *      }
//                     */
//
//                    JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = getSolver(sm); // enclosing method as entry method
//
//                    List<Map<Local, JLinkValue>> results =
//                            (List<Map<Local, JLinkValue>>) solver.solverResultsAt(call.getStmt()).stream().toList();
//
//                    if (addConstantParametersToCall(results, call)) {
//
//                        /* We found sufficient information with the analysis above, so we add the call with
//                           the respective constant parameters and the enclosing method as the "entry method"
//                         */
//                        entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
//                    } else {
//
//                        /**
//                         * Tackling case (ii) where the enclosing method did not have sufficient information
//                         * to determine any constant information.
//                         * For example, in the functions below we need to backtrack in the call graph
//                         * to find the callers of the enclosing method, and use those as entry methods instead.
//                         *      public void bar() {
//                         *          foo(Test.class);
//                         *      }
//                         *      public void foo(Class clazz) {
//                         *          ServiceLoader.load(clazz);
//                         *      }
//                         * Using the enclosing method "foo" as the entry method is insufficient. We need to
//                         * find all callers to foo (namely bar) and use that as the entry method for the
//                         * analysis framework to successfully find the parameters passed on to the call.
//                         */
//
////                        findCallerEntryRecursive(sm, entryMethodsWithCalls, call);
//                    }
//                }
//            }
//
//            analysisMap.put(sm, entryMethodsWithCalls);
//        }
//
//        return analysisMap;
//    }


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
                    JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = getMinimalSolver(sm);

                    List<Map<Local, JLinkValue>> results =
                            (List<Map<Local, JLinkValue>>) solver.solverResultsAt(call.getStmt()).stream().toList();

                    if (addConstantParametersToCall(results, call)) {

                        /* We found sufficient information with the analysis above, so we add the call with
                           the respective constant parameters and the enclosing method as the "entry method"
                         */
                        entryMethodsWithCalls.add(new EntryMethodWithCall(sm, call));
                    } else {


                        /**
                         * Let's tackle forward flow with various classes
                         */

                        Map<ClassType, byte[]>  classes = new HashMap<>();
                        classes.put(sm.getDeclaringClassType(), allClasses.get(sm.getDeclaringClassType()));
                        ClassType sl = allClasses.keySet().stream()
                                .filter(ct -> ct.getFullyQualifiedName().equals("java.util.ServiceLoader")).findFirst().orElse(null);
                        classes.put(sl, allClasses.get(sl));
//                        findCallerBacktrack(sm, entryMethodsWithCalls, call, classes, 3);
                        experiment(sm, entryMethodsWithCalls, call, classes, 3);

                    }
                }
            }

            analysisMap.put(sm, entryMethodsWithCalls);
        }

        return analysisMap;
    }

    /**
     * Generates an interprocedural solver that examines only the enclosing class for
     * the ServiceLoader.load() call.
     * @param entryMethod the entry method
     * @return
     */
    public JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> getMinimalSolver(SootMethod entryMethod) {

        /* Generate an input location only containing the class enclosing the ServiceLoader.load() call */
        Map<ClassType, byte[]> classes = new HashMap<>();
        allClasses.keySet()
                .stream()
                .filter(type -> type.equals(entryMethod.getDeclaringClassType()))
                .findFirst().ifPresent(classType -> classes.put(classType, allClasses.get(classType)));

        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(classes))
                .build();

        JavaView view = javaProject.createFullView();

        return getSolver(entryMethod, view);
    }

    /**
     * Creates interprocedural solver based on given java view
     * @param entryMethod the entry method for analysis
     * @param view the java view containing classes to analyze
     * @return
     */
    public JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> getSolver(SootMethod entryMethod, JavaView view) {
        JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG(view,
                entryMethod.getSignature(),
                false,
                false);
        JLinkIFDSProblem problem = new JLinkIFDSProblem(icfg, entryMethod);
        JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = new JimpleIFDSSolver<>(problem);
        solver.solve(entryMethod.getDeclaringClassType().getClassName());
        return solver;
    }

    private void experiment(SootMethod sm,
                            List<EntryMethodWithCall> entryMethodsWithCalls,
                            ServiceLoaderCall call,
                            Map<ClassType, byte[]> classMap,
                            int layers) {

        Map<ClassType, byte[]>  classes = new HashMap<>();
        ClassType p = allClasses.keySet().stream()
                .filter(ct -> ct.getFullyQualifiedName().contains("jlink.P")).findFirst().orElse(null);
        classes.put(p, allClasses.get(p));
        ClassType staticTest = allClasses.keySet().stream()
                .filter(ct -> ct.getFullyQualifiedName().contains("jlink.StaticTest")).findFirst().orElse(null);
        classes.put(staticTest, allClasses.get(staticTest));

        ClassType q = allClasses.keySet().stream()
                .filter(ct -> ct.getFullyQualifiedName().contains("jlink.Q")).findFirst().orElse(null);
        classes.put(q, allClasses.get(q));

        classMap.putAll(classes);

        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(classMap))
                .build();

        JavaView view = javaProject.createView();

        CallGraphAlgorithm cha =
                new ClassHierarchyAnalysisAlgorithm(view);

        /* Idea: probably need to initialize with all method signatures for these classes */

        SootClass<?> sootClass = getClass(staticTest.getFullyQualifiedName());
        List<MethodSignature> classMethodSignatures = new ArrayList<>(sootClass.getMethods()
                .stream().map(SootMethod::getSignature).toList());

        SootClass<?> otherClass = getClass(p.getFullyQualifiedName());
        classMethodSignatures.addAll(new ArrayList<>(
                otherClass.getMethods().stream().map(SootMethod::getSignature).toList()));

        SootClass<?> qClass = getClass(q.getFullyQualifiedName());
        classMethodSignatures.addAll(new ArrayList<>(
                qClass.getMethods().stream().map(SootMethod::getSignature).toList()));

//        SootClass<?> slClass = getClass("java.util.ServiceLoader");
//        classMethodSignatures.addAll(new ArrayList<>(
//                slClass.getMethods().stream().map(SootMethod::getSignature).toList()));

        CallGraph cg = cha.initialize(classMethodSignatures);
        Object[] calls = cg.callsTo(sm.getSignature()).toArray();

        if (calls.length > 0) {
            /* Found an entry method */
            for (int i = 0; i < calls.length; i++) {

                Optional<? extends SootMethod> mOpt = view.getMethod((MethodSignature) calls[i]);
                if (mOpt.isPresent()) {
                    /* Try to do analysis with this caller method as entry method */
                    JavaSootMethod callerMethod = (JavaSootMethod) mOpt.get();
                    JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> callerSolver = getSolver(callerMethod, view);
                    List<Map<Local, JLinkValue>> results = (List<Map<Local, JLinkValue>>)
                            callerSolver.solverResultsAt(call.getStmt()).stream().toList();

                    ServiceLoaderCall clone = call.clone();
                    if (addConstantParametersToCall(results, clone)) {
                        /* Base case 1: Direct caller method has sufficient info */
                        entryMethodsWithCalls.add(new EntryMethodWithCall(callerMethod, clone));
                    } else {
                        /* Recursive case: We need to backtrack once again */
                        System.out.println("we didn't make it Sonia");
                        experiment(callerMethod, entryMethodsWithCalls, call, classMap, layers);
                    }
                }
            }
        }

    }

    private void findCallerBacktrack(SootMethod sm,
                                     List<EntryMethodWithCall> entryMethodsWithCalls,
                                     ServiceLoaderCall call,
                                     Map<ClassType, byte[]> classMap,
                                     int layers) {

        /*
            Create contained call graphs (just one class) to try to find one that calls the method with
            the enclosing call. A lot quicker than creating massive call graphs.
         */
        if (layers == 0) {
            return;
        }

        boolean found = false;
        JavaView view;
        Iterator iterator = allClasses.keySet().iterator();
        Map<ClassType, byte[]>  classes = new HashMap<>();
        while (iterator.hasNext() && !found) {
            ClassType classType = (ClassType) iterator.next();
            CallGraph cg = null;
            if (cgMap.containsKey(classType)) {
                /* Check if we have already generated this call graph before */
                cg = cgMap.get(classType);
                view = viewMap.get(classType);
            } else {
                /* Generate a call graph for this class */

                classes = new HashMap<>();
                classes.put(classType, allClasses.get(classType));
                classes.putAll(classMap);

                JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                        .addInputLocation(new JLinkInputLocation(classes))
                        .build();

                view = javaProject.createFullView();
                viewMap.put(classType, view);

                CallGraphAlgorithm cha =
                        new ClassHierarchyAnalysisAlgorithm(view);
                SootClass<?> sootClass = getClass(classType.getFullyQualifiedName());
                List<MethodSignature> classMethodSignatures = new ArrayList<>(sootClass.getMethods()
                        .stream().map(SootMethod::getSignature).toList());
                try {
                    cg = cha.initialize(classMethodSignatures);
                    cgMap.put(classType, cg);
                } catch (Exception e) {
                    // do nothing
                }
            }

            /* Try to identify calls to method enclosing call */
            Object[] calls = new Object[0];
            try {
                calls = cg.callsTo(sm.getSignature()).toArray();
            } catch (Exception e) {
                // do nothing
            }

            if (calls.length > 0) {
                found = true;

                /* Found an entry method */
                for (int i = 0; i < calls.length; i++) {

                    Optional<? extends SootMethod> mOpt = view.getMethod((MethodSignature) calls[i]);
                    if (mOpt.isPresent()) {
                        /* Try to do analysis with this caller method as entry method */
                        JavaSootMethod callerMethod = (JavaSootMethod) mOpt.get();
                        JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> callerSolver = getSolver(callerMethod, view);
                        List<Map<Local, JLinkValue>> results = (List<Map<Local, JLinkValue>>)
                                callerSolver.solverResultsAt(call.getStmt()).stream().toList();

                        ServiceLoaderCall clone = call.clone();
                        if (addConstantParametersToCall(results, clone)) {
                            /* Base case 1: Direct caller method has sufficient info */
                            entryMethodsWithCalls.add(new EntryMethodWithCall(callerMethod, clone));
                        } else {
                            /* Recursive case: We need to backtrack once again */
                            findCallerBacktrack(callerMethod, entryMethodsWithCalls, call, classes, layers - 1);
                        }
                    }
                }
            }
        }
    }

    private void findCallerEntryRecursive(SootMethod sm, List<EntryMethodWithCall> entryMethodsWithCalls, ServiceLoaderCall call) {

        /* Try to create a call graph algorithm with forward calls - 1 layer up */
        Map<ClassType, byte[]> classes = new HashMap<>();
        allClasses.keySet()
                .stream()
                .filter(type -> type.equals(sm.getDeclaringClassType()))
                .findFirst().ifPresent(classType -> classes.put(classType, allClasses.get(classType)));

        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
                .addInputLocation(new JLinkInputLocation(classes))
                .build();

        JavaView view = javaProject.createFullView();

        CallGraphAlgorithm cha =
                new ClassHierarchyAnalysisAlgorithm(view);
        List<MethodSignature> test = new ArrayList<>();
        test.add(sm.getSignature());
        CallGraph cg = cha.initialize(allSignatures);


        /* Base case 0: There's no caller methods */
        Object[] calls = cg.callsTo(sm.getSignature()).toArray();
        if (calls.length == 0) return;

//        for (int i = 0; i < calls.length; i++) {
//            Optional<? extends SootMethod> mOpt = view.getMethod((MethodSignature) calls[i]);
//            if (mOpt.isPresent()) {
//                /* Try to do analysis with this caller method as entry method */
//                JavaSootMethod callerMethod = (JavaSootMethod) mOpt.get();
//                JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> callerSolver = getSolver(callerMethod, view);
//                List<Map<Local, JLinkValue>> results = (List<Map<Local, JLinkValue>>)
//                        callerSolver.solverResultsAt(call.getStmt()).stream().toList();
//
//                ServiceLoaderCall clone = call.clone();
//                if (addConstantParametersToCall(results, clone)) {
//                    /* Base case 1: Direct caller method has sufficient info */
//                    entryMethodsWithCalls.add(new EntryMethodWithCall(callerMethod, clone));
//                } else {
//                    /* Recursive case: We need to backtrack once again */
//                    findCallerEntryRecursive(callerMethod, entryMethodsWithCalls, call);
//                }
//            }
//        }
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

//    /**
//     * Generates an interprocedural analysis solver using a given entry method.
//     * @param entryMethod
//     * @return
//     */
//    public JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> getSolver(SootMethod entryMethod) {
////        JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG(
////                view,
////                entryMethod.getSignature(),
////                false,
////                false);
//
//        JimpleBasedInterproceduralCFG icfg = generateCFG(entryMethod);
//
//        JLinkIFDSProblem problem = new JLinkIFDSProblem(icfg, entryMethod);
//        JimpleIFDSSolver<?, InterproceduralCFG<Stmt, SootMethod>> solver = new JimpleIFDSSolver<>(problem);
//        solver.solve(entryMethod.getDeclaringClassType().getClassName());
//        return solver;
//    }



//    /**
//     * Generate an interprocedural callgraph that only includes the enclosing
//     * class with the ServiceLoader.load() call.
//     * @param entryMethod the entry method for the call
//     * @return
//     */
//    public JimpleBasedInterproceduralCFG generateCFG(SootMethod entryMethod) {
//
//        Map<ClassType, byte[]> classes = new HashMap<>();
//        allClasses.keySet()
//                .stream()
//                .filter(type -> type.equals(entryMethod.getDeclaringClassType()))
//                .findFirst().ifPresent(classType -> classes.put(classType, allClasses.get(classType)));
//
//        JavaProject javaProject = JavaProject.builder(new JavaLanguage(9))
//                .addInputLocation(new JLinkInputLocation(classes))
//                .build();
//
//        JavaView view = javaProject.createFullView();
//
//        return new JimpleBasedInterproceduralCFG(view,
//                entryMethod.getSignature(),
//                false,
//                false);
//
////        while (icfg == null) {
////            try {
////                icfg = new JimpleBasedInterproceduralCFG(view,
////                        entryMethod.getSignature(),
////                        false,
////                        false);
////            } catch (RuntimeException e) {
////
////                String notFound = "";
////                if (e.getMessage().startsWith("Could not find")) {
////                    notFound = e.getMessage().substring(15).substring(0, e.getMessage().substring(15).indexOf(" "));
////                } else {
////                    notFound = e.getMessage().substring(19).substring(0, e.getMessage().substring(19).indexOf(" "));
////                }
////                JavaClassType cType = factory.getClassType(notFound);
////                classes.put(cType, allClasses.get(cType));
////                javaProject = JavaProject.builder(new JavaLanguage(9))
////                        .addInputLocation(new JLinkInputLocation(classes))
////                        .build();
////                view = javaProject.createFullView();
////
////                System.out.println("Adding " + notFound);
////                counter++;
////            }
////        }
////
////        return icfg;
//    }

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
