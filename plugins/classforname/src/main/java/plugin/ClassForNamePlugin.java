package plugin;

import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;


import sootup.core.model.SootMethod;
import sootup.core.types.ClassType;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.types.JavaClassType;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Plugin for ServiceLoader analysis. Added it as ClassForName initially because I had already
 * opened up JLink to that plugin.
 *
 * TODO change name.
 */
public class ClassForNamePlugin extends AbstractPlugin {

    public static final String NAME = "class-for-name";
    private final String REPORT_FILE_NAME = "failed_report.txt";
    private final String PROP_FILE = "prop_report.txt";
    private final FileWriter failedPropagationWriter;
    private final FileWriter propWriter;
    private IFDS ifds;

    public ClassForNamePlugin() {
        super(NAME);
        try {
            failedPropagationWriter = new FileWriter(REPORT_FILE_NAME);
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            Date date = new Date();
            failedPropagationWriter.write(String.format("------------ Failed Propagation Information " +
                    "Report generated On %s ------------\n", formatter.format(date)));
            propWriter = new FileWriter(PROP_FILE);
            propWriter.write(String.format("PROP REPORT \n"));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Category getType() {
         return Category.TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getDescription() {
        return "TODO ";
    }

    @Override
    public String getArgumentsDescription() {
        return "TODO ";
    }

    @Override
    public void configure(Map<String, String> config) {
        // TODO - there are no config parameters as of now.
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        ifds = new IFDS(getClassMap(in));

        in.entries()
                .forEach(resource -> {
                    String path = resource.path();

                    if (path.endsWith(".class") && !path.endsWith("/module-info.class")
//                            && path.contains("javax/tools/ToolProvider.class") // testing condition
                    ) {
                        System.out.println(path);
                        out.add(transform(resource));
                    } else {
                        out.add(resource);
                    }
                });

        try {
            failedPropagationWriter.close();
            propWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Constant calls: " + ifds.constantCounter);
        System.out.println("IFDS Calls: " + ifds.ifdsCounter);
        return out.build();
    }

    /**
     * Generates a class map linking each class -> content bytes.
     * This is necessary for the custom Soot Up input location that
     * takes in content bytes as opposed to
     * the absolute path to an application jar or directory.
     * @param pool
     * @return
     */
    private Map<ClassType, byte[]> getClassMap(ResourcePool pool) {
        Map<ClassType, byte[]> map = new HashMap<>();
        JavaIdentifierFactory identifierFactory = JavaIdentifierFactory.getInstance();
        pool.entries().forEach(resource -> {
            if (resource.path().endsWith(".class")
                    && !resource.path().endsWith("/module-info.class")) {
                JavaClassType classSignature = identifierFactory.getClassType(reformatClassName(resource));
                map.put(classSignature, resource.contentBytes());
            }
        });
        System.out.println("finished generating map for input location");
        return map;
    }

    /**
     * Doesn't do any actual transformation. Just fetches constant values for ServiceLoader.load()
     * and reports them on a text file.
     * @param resource
     * @return
     */
    private ResourcePoolEntry transform(ResourcePoolEntry resource) {

        try {
            Map<SootMethod, List<IFDS.EntryMethodWithCall>> map =
                    ifds.doServiceLoaderStaticAnalysis(reformatClassName(resource), failedPropagationWriter);
            if (! map.isEmpty()) {
                propWriter.write(resource.path() + " \n");
                for (SootMethod sm : map.keySet()) {
                    propWriter.write("\t " + sm + "\n");
                    for (IFDS.EntryMethodWithCall mWithCall : map.get(sm)) {
                        propWriter.write("\t\t " + mWithCall.getEntryMethod() + "\n");
                        propWriter.write("\t\t\t " + mWithCall.getCall() + " \n");

                    }
                }
            }
        } catch (IOException e) {
            // do nothing
        }

        return resource;
    }

    /**
     * Reformats class name to meet Soot's class name expectations.
     * @param resource
     * @return
     */
    private String reformatClassName(ResourcePoolEntry resource) {
        String withoutModule = resource.path().replaceFirst("/" + resource.moduleName(), "");
        return withoutModule.substring(1, withoutModule.indexOf(".class", 0))
                .replace("/", ".");
    }
}
