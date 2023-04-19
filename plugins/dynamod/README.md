# DynaMod plugin

This is just a no-op plugin that can be used to test if the dynamic loading of plugins is working. It can also be used as a starting point to create new plugins.

You enable it by passing `--dyna-test` to the `jlink` command, eg:

```bash
$ jlink --dyna-test \
        -p target/hellotest-1.0-SNAPSHOT.jar \
        -J--module-path=../../lib --add-modules hellotest \
        --output out --launcher hello=hellotest/helloworld.HelloWorld
```

You should see the text "DynaMod plugin activated" in the output as a sign that the plugin was activated.

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

But it's not really interesting, it just shows we didn't break `jlink`.
