package plugin;


import jdk.tools.jlink.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public abstract class AbstractPlugin implements Plugin {

    protected static final boolean JLINK_DEBUG = Boolean.getBoolean("jlink.debug");

    static final String DESCRIPTION = "description";
    static final String USAGE = "usage";
    private final String name;

    protected AbstractPlugin(String name) {
        this.name = name;
    }

    protected AbstractPlugin(String name, ResourceBundle bundle) {
        this.name = name;
    }

    private void dumpClassFile(String path, byte[] buf) {
        try {
            String fullPath = String.format("%d-%s%s%s",
                    ProcessHandle.current().pid(),
                    getName(), File.separator,
                    path.replace('/', File.separatorChar));
            System.err.printf("Dumping class file %s\n", fullPath);
            new File(fullPath.substring(0, fullPath.lastIndexOf('/'))).mkdirs();
            Files.write(Paths.get(fullPath), buf);
        } catch (IOException ioExp) {
            System.err.println("writing " + path + " failed");
            ioExp.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return name;
    }
}