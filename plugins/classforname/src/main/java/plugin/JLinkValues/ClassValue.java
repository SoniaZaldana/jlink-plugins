package plugin.JLinkValues;

public class ClassValue implements JLinkValue {

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

    @Override
    public boolean equals(Object o) {
        if (o instanceof ClassValue classValue) {
            return classValue.getContent().equals(content);
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
