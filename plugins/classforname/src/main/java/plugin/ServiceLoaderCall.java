package plugin;

import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;


public class ServiceLoaderCall extends Call {
    private Stmt stmt;
    public ServiceLoaderCall(Stmt stmt) {
        super(stmt.getInvokeExpr());
        this.stmt = stmt;
    }

    @Override
    public boolean hasConstantParameters() {
        return argMap.keySet().iterator().next() instanceof ClassConstant;
    }

    public ServiceLoaderCall clone() {
        ServiceLoaderCall clone = new ServiceLoaderCall(stmt);
        return clone;
    }

    public Stmt getStmt() {
        return this.stmt;
    }
}
