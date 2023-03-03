# JLink Plugin Modules

This repository contains proof of concepts for dynamically loadable plugins for Java's jlink command.

## Getting started

First of all current versions of the Java JDK do not export JLink's plugin API, so what we need to do is build a special version of the JDK with a very simple change applied.

 1. Download/clone the JDK sources, for example from here: https://github.com/openjdk/jdk or here: https://github.com/openjdk/jdk-sandbox

 2. Either manually edit the `src/jdk.jlink/share/classes/module-info.java` file by adding a `exports jdk.tools.jlink.plugin;` line, or apply a patch by running the following command:

 ```bash
patch -p1 <<EOF 
--- a/src/jdk.jlink/share/classes/module-info.java
+++ b/src/jdk.jlink/share/classes/module-info.java
@@ -55,6 +55,8 @@ module jdk.jlink {
     requires jdk.internal.opt;
     requires jdk.jdeps;

+    exports jdk.tools.jlink.plugin;
+
     uses jdk.tools.jlink.plugin.Plugin;

     provides java.util.spi.ToolProvider with
--
EOF
```

 3. First you need to compile the JDK by following the instructions in [their documentation](https://github.com/openjdk/jdk/blob/master/doc/building.md), although it basically boils down to running:

```
$ bash configure
$ make images
```

 in the root of the project.

If everything built correctly you should now have a JDK that supports loading and running external jlink plugins. For the remainder of this README we'll assume you're using that JDK to build any code or run any commands.

## Building

In the root of the project simply run:

```bash
$ mvn clean package
```

This will build the plugins as well as the test applications.

## Testing

To see if the plugins and the patched JDK were built correctly run the following:

```bash
$ jlink --module-path=lib --suggest-providers jdk.tools.jlink.plugin.Plugin

Suggested providers:
  dynamod provides jdk.tools.jlink.plugin.Plugin used by jdk.jlink
  jdk.jlink provides jdk.tools.jlink.plugin.Plugin used by jdk.jlink
  seal provides jdk.tools.jlink.plugin.Plugin used by jdk.jlink
```

The "dynamod" and "seal" plugins should show up in the output.

Another test is:

```bash
$ jlink -J--module-path=lib --list-plugins

List of available plugins:

Plugin Name: dyna-test
Plugin Class: plugin.dynamod.DynaModPlugin
Plugin Module: dynamod
Category: TRANSFORMER
Functional state: Functional.
Option: --dyna-test
Description: A test plugin to see if dynamic loading works

Plugin Name: seal
Plugin Class: plugin.seal.SealPlugin
Plugin Module: seal
Category: TRANSFORMER
Functional state: Functional.
Option: --seal=[comma separated list of module names]
Description: Analyzes classes and decided if they can be made final and/or marked sealed.

.
.
.
```

Which shows a list of all known plugins, their descriptions and arguments.

NB: As you might have noticed, this time we passed the `--module-path` using the `-J` option which sets it for the VM that `jlink` is using. This is actually necessary for `jlink` to actually access the plugin modules and use them.

## DynaMod plugin

This is just a no-op plugin that can be used to test if the dynamic loading of plugins is working. It can also be used as a starting point to create new plugins.

For more details on how to use it see its [README](plugins/dynamod).

## Seal plugin

The seal plugin is a plugin for the `jlink` command that analyses
classes and tries to determine if the can be marked `final` or `sealed`.

For more details on how to use it see its [README](plugins/seal).
