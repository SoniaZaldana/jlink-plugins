module dynamod {
    requires jdk.jlink;

    provides jdk.tools.jlink.plugin.Plugin with plugin.dynamod.DynaModPlugin;
}
