package com.rits.cloning;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class FastClonerTreeSet implements IFastCloner {
    private static final Field m;
    private static final Field comparator;

    static {
        try {
            m = TreeSet.class.getDeclaredField("m");
            m.setAccessible(true);
            comparator = TreeMap.class.getDeclaredField("comparator");
            comparator.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Object clone(Object t, IDeepCloner cloner, Map<Object, Object> clones) {
        TreeSet treeSet = (TreeSet) t;
        TreeSet result = null;
        try {
            result = new TreeSet((Comparator) comparator.get(m.get(t)));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        for (Object o : treeSet) {
            result.add(cloner.deepClone(o, clones));
        }
        return result;
    }
}
