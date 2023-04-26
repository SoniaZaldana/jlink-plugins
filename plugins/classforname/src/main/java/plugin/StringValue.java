package plugin;

public class StringValue extends JLinkValue {

    private String content;

    public StringValue(String content) {
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

    @Override
    public String toString() {
        return "String: " + content;
    }
}
