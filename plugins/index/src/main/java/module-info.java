import plugin.JandexPlugin;

module index {
    requires jdk.jlink;
    requires org.jboss.jandex;
    requires org.glavo.classfile;

    provides jdk.tools.jlink.plugin.Plugin with JandexPlugin;
}