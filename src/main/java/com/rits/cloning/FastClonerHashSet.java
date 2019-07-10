package com.rits.cloning;

import java.util.HashSet;
import java.util.Map;

/**
 * @author kostantinos.kougios
 *
 * 21 May 2009
 */
public class FastClonerHashSet implements IFastCloner
{
	@SuppressWarnings({ "unchecked", "rawtypes" })
    public Object clone(final Object t, final IDeepCloner cloner) {
		final HashSet al = (HashSet) t;
		final HashSet l = new HashSet();
		for (final Object o : al)
		{
            final Object cloneInternal = cloner.deepClone(o);
            l.add(cloneInternal);
		}
		return l;
	}
}
