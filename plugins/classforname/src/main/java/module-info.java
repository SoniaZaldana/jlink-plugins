module classforname {
    requires jdk.jlink;
    requires org.objectweb.asm;
    requires org.objectweb.asm.tree;
    requires sootup.analysis;
    requires sootup.java.bytecode;
//    requires sootup.java.core;


    provides jdk.tools.jlink.plugin.Plugin with plugin.ClassForNamePlugin;

}