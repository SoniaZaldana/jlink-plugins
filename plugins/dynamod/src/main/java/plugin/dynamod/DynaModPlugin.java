
package plugin.dynamod;

import java.util.Map;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

public class DynaModPlugin implements Plugin {
    private boolean first = true;

    private static final String NAME = "dyna-test";
    
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public void configure(Map<String, String> config) {
    }

    @Override
    public String getDescription() {
        return "A test plugin to see if dynamic loading works";
    }
    
    @Override
    public String getArgumentsDescription() {
        return "";
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        if (first) {
            System.err.println("DynaMod plugin activated");
            first = false;
        }
        return in;
    }
}
