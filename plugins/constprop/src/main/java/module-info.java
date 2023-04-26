import plugin.ServiceLoaderConstantPropagator;

module constprop {
    requires jdk.jlink;
    requires sootup.analysis;
    requires sootup.java.bytecode;
    requires sootup.core;
    requires sootup.java.core;
    requires heros;
    requires sootup.callgraph;
    requires org.objectweb.asm.tree;

    provides jdk.tools.jlink.plugin.Plugin with ServiceLoaderConstantPropagator;

}