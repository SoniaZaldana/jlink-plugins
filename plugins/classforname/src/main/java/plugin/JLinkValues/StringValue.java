package plugin.JLinkValues;

public class StringValue implements JLinkValue {

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

    @Override
    public boolean equals(Object o) {
        if (o instanceof StringValue stringValue) {
            return stringValue.getContent().equals(content);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (content == null ? 0 : content.hashCode());
        return hash;
    }

    @Override
    public boolean isConstant() {
        return true;
    }
}
