# Seal plugin

The seal plugin is a plugin for the `jlink` command that analyses
classes and tries to determine if the can be marked `final` or `sealed`.

It does this by scanning all classes that are available and it then
creates a hierarchy of classes and their subclasses. Any classes that
have no subclasses are marked `final`, while any classes that only
have subclasses that are part of the same module are marked `sealed`.

The plugin can be activated by simply passing the "--seal" flag to the
`jlink` command. The flag takes arguments:

    --seal=<module_names>:final=[y|n]:sealed=[y|n]:excludefile=<path>:log=<level>

The flag has a single, required, argument which is a comma-separated
list of module names that will be analysed and processed. This can be
set to `*` to process all modules.

All the other arguments are optional:

 - final - takes `y` or `n` to indicate that marking with `final` should
            or should not be taking place. Default `y`.
 - sealed - takes `y` or `n` to indicate that marking with `sealed` should
            or should not be taking place. Default `y`.
 - excludefile - takes a path to a text file with class names that should
            be excluded from `sealed` processing. If the name is that of an
            outer class all its inner classes will also be excluded.
 - log - sets the log output level for the plugin. Takes either `error`,
            `warning`, `info`, `debug` or `trace`. Default `warning`.

## Example

### Using hellotest

Using the Seal plugin with the hellotest application can be done like this:

```bash
$ jlink --seal='*:final=y:sealed=y:excludefile=../excludedclasses.txt:log=info' \
        -p testapps/hellotest/target/hellotest-1.0-SNAPSHOT.jar \
        -J--module-path=lib --add-modules hellotest \
        --output out --launcher hello=hellotest/helloworld.HelloWorld
```

You can run the test app like this:

```bash
$ ./out/bin/hello
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld main
INFO: Hello World!
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld printClass
INFO: Class helloworld.HelloWorld: final=true: sealed=false null
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld printClass
INFO: Class helloworld.A: final=false: sealed=false null
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld printClass
INFO: Class helloworld.B: final=false: sealed=false null
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld printClass
INFO: Class helloworld.C: final=false: sealed=false null
Mar 03, 2023 3:56:16 PM helloworld.HelloWorld printClass
INFO: Class helloworld.D: final=false: sealed=false null
```

It shows that the application still works even with the changes that the Seal plugin made to it.

### Using jettytest

Using the Seal plugin with the jettytest application can be done like this:

```bash
$ jlink --seal='*:final=y:sealed=y:excludefile=../excludedclasses.txt:log=info' \
        -p target/lib:target/jettytest-1.0-SNAPSHOT.jar  \ 
        -J--module-path=../../lib --add-modules jettytest \
        --output out --launcher jetty=jettytest/test.jetty
```

You can run the test app like this:

```bash
$ ./out/bin/jetty
```
