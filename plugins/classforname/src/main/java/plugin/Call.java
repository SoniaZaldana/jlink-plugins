package plugin;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;

import java.util.LinkedHashMap;

import static plugin.JLinkIFDSProblem.TOP_VALUE;

public abstract class Call {

    protected final AbstractInvokeExpr invokeExpr;
    protected LinkedHashMap<Immediate, JLinkValue> argMap;

    public Call(AbstractInvokeExpr invokeExpr) {
        this.invokeExpr = invokeExpr;
        this.argMap = new LinkedHashMap<>();
        for (Immediate arg : invokeExpr.getArgs()) {
            this.argMap.put(arg, TOP_VALUE);
        }
    }

    public AbstractInvokeExpr getInvokeExpr() {
        return this.invokeExpr;
    }

    public LinkedHashMap<Immediate, JLinkValue> getArgMap() {
        return this.argMap;
    }

    public abstract boolean hasConstantParameters();

    public void addPropValue(Immediate i, JLinkValue value) {
        this.argMap.put(i, value);
    }

    @Override
    public String toString() {
        String toString = invokeExpr.toString() + " (";
        for (Immediate i : argMap.keySet()) {
            toString += argMap.get(i) + ", ";
        }
        toString += " )";
        return toString;
    }
}
