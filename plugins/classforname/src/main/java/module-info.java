module classforname {
    requires jdk.jlink;
    requires sootup.analysis;
    requires sootup.java.bytecode;
    requires sootup.core;
    requires sootup.java.core;
    requires heros;
    requires sootup.callgraph;

    provides jdk.tools.jlink.plugin.Plugin with plugin.ClassForNamePlugin;

}