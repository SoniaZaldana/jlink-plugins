package plugin;

import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

import java.util.Map;

/**
 * ignore this plugin
 */
public class JandexPlugin extends AbstractPlugin{

    private static final String NAME = "index";

    public JandexPlugin() {
        super(NAME);
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public void configure(Map<String, String> config) {
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder poolBuilder) {
        return null;
    }
}
