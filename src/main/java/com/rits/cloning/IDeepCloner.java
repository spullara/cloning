package com.rits.cloning;

import java.util.Map;

/**
 * used by fast cloners to deep clone objects
 *
 * @author kostas.kougios Date 24/06/14
 */
public interface IDeepCloner {
    /**
     * deep clones o
     *
     * @param o      the object to be deep cloned
     * @param <T>    the type of o
     * @return a clone of o
     */
    <T> T deepClone(final T o);
}
