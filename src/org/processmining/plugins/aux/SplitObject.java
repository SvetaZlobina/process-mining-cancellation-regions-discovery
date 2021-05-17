package org.processmining.plugins.aux;

import org.processmining.framework.util.Pair;

public class SplitObject extends Pair<Object, String> {

    public final static String ENTER = " [En]";
    public final static String EXIT = " [Ex]";
    public final static String NOTCROSS = " [NC]";
    public final static String SL1 = " [SL1]";
    public final static String SL2 = " [SL2]";
    private final Object parent;

    public SplitObject(Object parent, String type) {
        super(parent, type);
        if (parent instanceof SplitObject) {
            this.parent = ((SplitObject) parent).getParent();
        } else {
            this.parent = parent;
        }
    }

    public Object getParent() {
        return parent;
    }

    public String toString() {
        return getParent().toString();
    }

    public String getOpposed() {
        if (getSecond() == ENTER) {
            return EXIT;
        }
        if (getSecond() == EXIT) {
            return ENTER;
        }
        if (getSecond() == NOTCROSS) {
            return NOTCROSS;
        }
        return "";
    }
}
