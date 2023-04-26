package plugin;

public class ClassValue extends JLinkValue {

    private String content;

    public ClassValue(String content) {
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

    @Override
    public String toString() {
        return "Class: " + content;
    }
}
