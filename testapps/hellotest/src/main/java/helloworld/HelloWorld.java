package helloworld;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.logging.Logger;

public final class HelloWorld {
    private static final Logger LOG = Logger.getLogger(HelloWorld.class.getName());
    public static void main(String[] args) {
        LOG.info("Hello World!");
        printClass(HelloWorld.class);
        printClass(A.class);
        printClass(B.class);
        printClass(C.class);
        printClass(D.class);
        new HelloWorld();
        new A();
        new B();
        new C();
        new D();
    }

    private static void printClass(Class clazz) {
        LOG.info("Class " + clazz.getName()
                + ": final=" + Modifier.isFinal(clazz.getModifiers())
                + ": sealed=" + clazz.isSealed() + " " + Arrays.toString(clazz.getPermittedSubclasses()));
    }
}

class A { }

class B extends A { }

class C extends A { }

class D extends C { }
