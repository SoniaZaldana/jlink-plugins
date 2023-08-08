package plugin;

import sootup.core.signatures.FieldSignature;

public class StaticFieldHolder extends ValueHolder {

    private final FieldSignature fieldSignature;

    public StaticFieldHolder(FieldSignature fieldSignature) {
        this.fieldSignature = fieldSignature;
    }
    public FieldSignature getFieldSignature() {
        return this.fieldSignature;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StaticFieldHolder staticFieldHolder) {
            return this.fieldSignature.equals(staticFieldHolder.getFieldSignature());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (fieldSignature == null ? 0 : fieldSignature.hashCode());
        return hash;
    }

    @Override
    public String toString() {
        return "Static field: " + fieldSignature.toString();
    }
}
