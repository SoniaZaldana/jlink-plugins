module seal {
    requires jdk.jlink;
    requires org.objectweb.asm;
    requires org.objectweb.asm.tree;

    provides jdk.tools.jlink.plugin.Plugin with plugin.seal.SealPlugin;
}
