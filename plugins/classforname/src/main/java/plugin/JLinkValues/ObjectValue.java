package plugin.JLinkValues;

import sootup.core.signatures.FieldSignature;
import sootup.core.types.ClassType;

import java.util.HashMap;
import java.util.Map;

public class ObjectValue implements JLinkValue {
    private ClassType classType;
    private Map<FieldSignature, JLinkValue> fields;

    public ObjectValue(ClassType classType) {
        this.classType = classType;
    }

    public ClassType getClassType() {
        return this.classType;
    }

    public void addField(FieldSignature fieldSignature, JLinkValue value) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.put(fieldSignature, value);
    }

    public Map<FieldSignature, JLinkValue> getFields() {
        return this.fields;
    }

    public JLinkValue getFieldValue(FieldSignature fieldSignature) {
        return this.fields.get(fieldSignature);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ObjectValue objectValue) {
            if (this.fields != null && objectValue.getFields() != null) {
                return this.fields.equals(objectValue.getFields())
                        && this.classType.equals(objectValue.getClassType());
            } else if (this.fields == null && objectValue.getFields() == null) {
                return this.classType.equals(objectValue.getClassType());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (fields == null ? 0 : fields.hashCode());
        hash = 31 * hash + (classType == null ? 0 : classType.hashCode());
        return hash;
    }

    @Override
    public boolean isConstant() {
        return false;
    }
}

