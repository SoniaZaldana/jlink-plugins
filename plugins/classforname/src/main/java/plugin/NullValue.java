package plugin;

import sootup.core.jimple.common.constant.NullConstant;

public class NullValue extends JLinkValue {

    public NullValue() {

    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NullConstant || o instanceof NullValue) {
            return true;
        }
        return false;
    }

}
