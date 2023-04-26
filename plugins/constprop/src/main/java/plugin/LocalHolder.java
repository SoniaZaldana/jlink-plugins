package plugin;

import sootup.core.jimple.basic.Local;

public class LocalHolder extends ValueHolder {

    private final Local local;

    public LocalHolder(Local local) {
        this.local = local;
    }

    public Local getLocal() {
        return local;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (local == null ? 0 : local.hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LocalHolder localHolder) {
            return this.local.equals(localHolder.getLocal());
        }
        return false;
    }

    @Override
    public String toString() {
        return this.local.toString();
    }
}
