package plugin;

import plugin.JLinkValues.JLinkValue;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;


public class ServiceLoaderCall extends Call {
    private Stmt stmt;
    private boolean hasConstantParams;
    public ServiceLoaderCall(Stmt stmt) {
        super(stmt.getInvokeExpr());
        this.stmt = stmt;
        if (argMap.keySet().iterator().next() instanceof ClassConstant) {
            this.hasConstantParams = true;
        } else {
            this.hasConstantParams = false;
        }
    }

    @Override
    public boolean hasConstantParameters() {
        return hasConstantParams;
    }

    @Override
    public void addPropValue(Immediate i, JLinkValue value) {
        super.addPropValue(i, value);
        hasConstantParams = true;
    }

    public ServiceLoaderCall clone() {
        ServiceLoaderCall clone = new ServiceLoaderCall(stmt);
        return clone;
    }

    public Stmt getStmt() {
        return this.stmt;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ServiceLoaderCall call) {
            return super.equals(o) && call.getStmt().equals(stmt);
        }
        return false;
    }
}
