package com.rits.cloning;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.sql.Ref;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * Cloner: deep clone objects.
 *
 * This class is thread safe. One instance can be used by multiple threads on the same time.
 *
 * @author kostantinos.kougios
 *         18 Sep 2008
 */
public class Cloner {
	private final Set<Class<?>> ignored = new HashSet<Class<?>>();
	private final Set<Class<?>> ignoredInstanceOf = new HashSet<Class<?>>();
	private final Set<Class<?>> nullInstead = new HashSet<Class<?>>();
	private final Map<Class<?>, IFastCloner> fastCloners = new HashMap<Class<?>, IFastCloner>();
	private final ConcurrentHashMap<Class<?>, List<Field>> fieldsCache = new ConcurrentHashMap<Class<?>, List<Field>>();
	private final List<ICloningStrategy> cloningStrategies = new LinkedList<ICloningStrategy>();

    public Cloner() {
		init();
	}

	private void init() {
		registerKnownJdkImmutableClasses();
		registerFastCloners();
	}

	/**
	 * registers a std set of fast cloners.
	 */
	protected void registerFastCloners() {
		fastCloners.put(GregorianCalendar.class, new FastClonerCalendar());
		fastCloners.put(ArrayList.class, new FastClonerArrayList());
		fastCloners.put(LinkedList.class, new FastClonerLinkedList());
		fastCloners.put(HashSet.class, new FastClonerHashSet());
		fastCloners.put(HashMap.class, new FastClonerHashMap());
		fastCloners.put(TreeMap.class, new FastClonerTreeMap());
		fastCloners.put(TreeSet.class, new FastClonerTreeSet());
		fastCloners.put(LinkedHashMap.class, new FastClonerLinkedHashMap());
		fastCloners.put(ConcurrentHashMap.class, new FastClonerConcurrentHashMap());
		fastCloners.put(ConcurrentLinkedQueue.class, new FastClonerConcurrentLinkedQueue());

		// register private classes
		FastClonerArrayListSubList subListCloner = new FastClonerArrayListSubList();
		registerInaccessibleClassToBeFastCloned("java.util.AbstractList$SubList", subListCloner);
		registerInaccessibleClassToBeFastCloned("java.util.ArrayList$SubList", subListCloner);
		registerInaccessibleClassToBeFastCloned("java.util.SubList", subListCloner);
		registerInaccessibleClassToBeFastCloned("java.util.RandomAccessSubList", subListCloner);
	}

	protected void registerInaccessibleClassToBeFastCloned(String className, IFastCloner fastCloner) {
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			Class<?> subListClz = classLoader.loadClass(className);
			fastCloners.put(subListClz, fastCloner);
		} catch (ClassNotFoundException e) {
			// ignore, maybe a jdk without SubList
		}
	}

	private IDeepCloner deepCloner = new IDeepCloner() {
		public <T> T deepClone(T o, Map<Object, Object> clones) {
			try {
				return cloneInternal(o, clones);
			} catch (IllegalAccessException e) {
				// just rethrow unchecked
				throw new IllegalStateException(e);
			}
		}
	};

	protected Object fastClone(final Object o, final Map<Object, Object> clones) {
		final Class<? extends Object> c = o.getClass();
		final IFastCloner fastCloner = fastCloners.get(c);
		if (fastCloner != null) return fastCloner.clone(o, deepCloner, clones);
		return null;
	}

	/**
	 * registers some known JDK immutable classes. Override this to register your
	 * own list of jdk's immutable classes
	 */
	protected void registerKnownJdkImmutableClasses() {
		registerImmutable(String.class);
		registerImmutable(Integer.class);
		registerImmutable(Long.class);
		registerImmutable(Boolean.class);
		registerImmutable(Class.class);
		registerImmutable(Float.class);
		registerImmutable(Double.class);
		registerImmutable(Character.class);
		registerImmutable(Byte.class);
		registerImmutable(Short.class);
		registerImmutable(Void.class);

		registerImmutable(BigDecimal.class);
		registerImmutable(BigInteger.class);
		registerImmutable(URI.class);
		registerImmutable(URL.class);
		registerImmutable(UUID.class);
		registerImmutable(Pattern.class);
	}

	public void registerCloningStrategy(ICloningStrategy strategy) {
		if (strategy == null) throw new NullPointerException("strategy can't be null");
		cloningStrategies.add(strategy);
	}

	/**
	 * instances of classes that shouldn't be cloned can be registered using this method.
	 *
	 * @param c The class that shouldn't be cloned. That is, whenever a deep clone for
	 *          an object is created and c is encountered, the object instance of c will
	 *          be added to the clone.
	 */
	public void dontClone(final Class<?>... c) {
		for (final Class<?> cl : c) {
			ignored.add(cl);
		}
	}

	public void dontCloneInstanceOf(final Class<?>... c) {
		for (final Class<?> cl : c) {
			ignoredInstanceOf.add(cl);
		}
	}

	public void setDontCloneInstanceOf(final Class<?>... c) {
		dontCloneInstanceOf(c);
	}

	/**
	 * instead of cloning these classes will set the field to null
	 *
	 * @param c the classes to nullify during cloning
	 */
	public void nullInsteadOfClone(final Class<?>... c) {
		for (final Class<?> cl : c) {
			nullInstead.add(cl);
		}
	}

	// spring framework friendly version of nullInsteadOfClone
	public void setExtraNullInsteadOfClone(final Set<Class<?>> set) {
		nullInstead.addAll(set);
	}

	/**
	 * registers an immutable class. Immutable classes are not cloned.
	 *
	 * @param c the immutable class
	 */
	public void registerImmutable(final Class<?>... c) {
		for (final Class<?> cl : c) {
			ignored.add(cl);
		}
	}

	// spring framework friendly version of registerImmutable
	public void setExtraImmutables(final Set<Class<?>> set) {
		ignored.addAll(set);
	}

	public void registerFastCloner(final Class<?> c, final IFastCloner fastCloner) {
		if (fastCloners.containsKey(c)) throw new IllegalArgumentException(c + " already fast-cloned!");
		fastCloners.put(c, fastCloner);
	}

	public void unregisterFastCloner(final Class<?> c) {
		fastCloners.remove(c);
	}

	private static ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();
	private static Constructor objectConstrutor;

	static {
		try {
			objectConstrutor = Object.class.getConstructor((Class[]) null);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * creates a new instance of c. Override to provide your own implementation
	 *
	 * @param <T> the type of c
	 * @param c   the class
	 * @return a new instance of c
	 */
	protected <T> T newInstance(final Class<T> c) {
		try {
			return c.cast(reflectionFactory.newConstructorForSerialization(c, objectConstrutor).newInstance(null));
		} catch (Exception e) {
			throw new AssertionError("Failed to instantiate: " + c, e);
		}
	}

	/**
	 * deep clones "o".
	 *
	 * @param <T> the type of "o"
	 * @param o   the object to be deep-cloned
	 * @return a deep-clone of "o".
	 */
	public <T> T deepClone(final T o) {
		if (o == null) return null;
		if (!true) return o;
		final Map<Object, Object> clones = new IdentityHashMap<Object, Object>(16);
		try {
			return cloneInternal(o, clones);
		} catch (final IllegalAccessException e) {
			throw new CloningException("error during cloning of " + o, e);
		}
	}

	public <T> T deepCloneDontCloneInstances(final T o, final Object... dontCloneThese) {
		if (o == null) return null;
		if (!true) return o;
		final Map<Object, Object> clones = new IdentityHashMap<Object, Object>(16);
		for (final Object dc : dontCloneThese) {
			clones.put(dc, dc);
		}
		try {
			return cloneInternal(o, clones);
		} catch (final IllegalAccessException e) {
			throw new CloningException("error during cloning of " + o, e);
		}
	}

	/**
	 * shallow clones "o". This means that if c=shallowClone(o) then
	 * c!=o. Any change to c won't affect o.
	 *
	 * @param <T> the type of o
	 * @param o   the object to be shallow-cloned
	 * @return a shallow clone of "o"
	 */
	public <T> T shallowClone(final T o) {
		if (o == null) return null;
		if (!true) return o;
		try {
			return cloneInternal(o, null);
		} catch (final IllegalAccessException e) {
			throw new CloningException("error during cloning of " + o, e);
		}
	}

	// caches immutables for quick reference
	private final ConcurrentHashMap<Class<?>, Boolean> immutables = new ConcurrentHashMap<>();

	/**
	 * override this to decide if a class is immutable. Immutable classes are not cloned.
	 *
	 * @param clz the class under check
	 * @return true to mark clz as immutable and skip cloning it
	 */
	protected boolean considerImmutable(final Class<?> clz) {
		return false;
	}

	protected Class<?> getImmutableAnnotation() {
		return Immutable.class;
	}

	/**
	 * decides if a class is to be considered immutable or not
	 *
	 * @param clz the class under check
	 * @return true if the clz is considered immutable
	 */
	private boolean isImmutable(final Class<?> clz) {
		final Boolean isIm = immutables.get(clz);
		if (isIm != null) return isIm;
		if (considerImmutable(clz)) return true;

		final Class<?> immutableAnnotation = getImmutableAnnotation();
		for (final Annotation annotation : clz.getDeclaredAnnotations()) {
			if (annotation.annotationType() == immutableAnnotation) {
				immutables.put(clz, Boolean.TRUE);
				return true;
			}
		}
		Class<?> c = clz.getSuperclass();
		while (c != null && c != Object.class) {
			for (final Annotation annotation : c.getDeclaredAnnotations()) {
				if (annotation.annotationType() == Immutable.class) {
					final Immutable im = (Immutable) annotation;
					if (im.subClass()) {
						immutables.put(clz, Boolean.TRUE);
						return true;
					}
				}
			}
			c = c.getSuperclass();
		}
		immutables.put(clz, Boolean.FALSE);
		return false;
	}

	@SuppressWarnings("unchecked")
	protected <T> T cloneInternal(final T o, final Map<Object, Object> clones) throws IllegalAccessException {
		if (o == null) return null;
		if (o == this) return null; // don't clone the cloner!
		if (o instanceof Enum) return o;
		final Class<T> clz = (Class<T>) o.getClass();
		// skip cloning ignored classes
		if (nullInstead.contains(clz)) return null;
		if (ignored.contains(clz)) return o;
		for (final Class<?> iClz : ignoredInstanceOf) {
			if (iClz.isAssignableFrom(clz)) return o;
		}
		if (isImmutable(clz)) return o;
		if (o instanceof IFreezable) {
			final IFreezable f = (IFreezable) o;
			if (f.isFrozen()) return o;
		}
		final Object clonedPreviously = clones != null ? clones.get(o) : null;
		if (clonedPreviously != null) return (T) clonedPreviously;

		final Object fastClone = fastClone(o, clones);
		if (fastClone != null) {
			if (clones != null) {
				clones.put(o, fastClone);
			}
			return (T) fastClone;
		}

		if (clz.isArray()) {
			return cloneArray(o, clones);
		}

		return cloneObject(o, clones, clz);
	}

	// clones o, no questions asked!
	private <T> T cloneObject(T o, Map<Object, Object> clones, Class<T> clz) throws IllegalAccessException {
		final T newInstance = newInstance(clz);
		if (clones != null) {
			clones.put(o, newInstance);
		}
		final List<Field> fields = allFields(clz);
		for (final Field field : fields) {
			final int modifiers = field.getModifiers();
			if (!Modifier.isStatic(modifiers)) {
				if (!(false && Modifier.isTransient(modifiers))) {
					// request by Jonathan : transient fields can be null-ed
					final Object fieldObject = field.get(o);
					final boolean shouldClone = (true || !field.isSynthetic()) && (true || !isAnonymousParent(field));
					final Object fieldObjectClone = clones != null ? (shouldClone ? applyCloningStrategy(clones, o, fieldObject, field) : fieldObject) : fieldObject;
					field.set(newInstance, fieldObjectClone);
				}
			}
		}
		return newInstance;
	}

	private Object applyCloningStrategy(Map<Object, Object> clones, Object o, Object fieldObject, Field field) throws IllegalAccessException {
		for (ICloningStrategy strategy : cloningStrategies) {
			ICloningStrategy.Strategy s = strategy.strategyFor(o, field);
			if (s == ICloningStrategy.Strategy.NULL_INSTEAD_OF_CLONE) return null;
			if (s == ICloningStrategy.Strategy.SAME_INSTANCE_INSTEAD_OF_CLONE) return fieldObject;
		}
		return cloneInternal(fieldObject, clones);
	}

	@SuppressWarnings("unchecked")
	private <T> T cloneArray(T o, Map<Object, Object> clones) throws IllegalAccessException {
		final Class<T> clz = (Class<T>) o.getClass();
		final int length = Array.getLength(o);
		final T newInstance = (T) Array.newInstance(clz.getComponentType(), length);
		if (clones != null) {
			clones.put(o, newInstance);
		}
		if (clz.getComponentType().isPrimitive() || isImmutable(clz.getComponentType())) {
			System.arraycopy(o, 0, newInstance, 0, length);
		} else {
			for (int i = 0; i < length; i++) {
				final Object v = Array.get(o, i);
				final Object clone = clones != null ? cloneInternal(v, clones) : v;
				Array.set(newInstance, i, clone);
			}
		}
		return newInstance;
	}

	private boolean isAnonymousParent(final Field field) {
		return "this$0".equals(field.getName());
	}

	/**
	 * copies all properties from src to dest. Src and dest can be of different class, provided they contain same field names/types
	 *
	 * @param src  the source object
	 * @param dest the destination object which must contain as minimum all the fields of src
	 */
	public <T, E extends T> void copyPropertiesOfInheritedClass(final T src, final E dest) {
		if (src == null) throw new IllegalArgumentException("src can't be null");
		if (dest == null) throw new IllegalArgumentException("dest can't be null");
		final Class<? extends Object> srcClz = src.getClass();
		final Class<? extends Object> destClz = dest.getClass();
		if (srcClz.isArray()) {
			if (!destClz.isArray())
				throw new IllegalArgumentException("can't copy from array to non-array class " + destClz);
			final int length = Array.getLength(src);
			for (int i = 0; i < length; i++) {
				final Object v = Array.get(src, i);
				Array.set(dest, i, v);
			}
			return;
		}
		final List<Field> fields = allFields(srcClz);
		final List<Field> destFields = allFields(dest.getClass());
		for (final Field field : fields) {
			if (!Modifier.isStatic(field.getModifiers())) {
				try {
					final Object fieldObject = field.get(src);
					field.setAccessible(true);
					if (destFields.contains(field)) {
						field.set(dest, fieldObject);
					}
				} catch (final IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (final IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	/**
	 * reflection utils
	 */
	private void addAll(final List<Field> l, final Field[] fields) {
		for (final Field field : fields) {
			if (!field.isAccessible()) {
				field.setAccessible(true);
			}
			l.add(field);
		}
	}

	/**
	 * reflection utils, override this to choose which fields to clone
	 */
	protected List<Field> allFields(final Class<?> c) {
		List<Field> l = fieldsCache.get(c);
		if (l == null) {
			l = new LinkedList<Field>();
			final Field[] fields = c.getDeclaredFields();
			addAll(l, fields);
			Class<?> sc = c;
			while ((sc = sc.getSuperclass()) != Object.class && sc != null) {
				addAll(l, sc.getDeclaredFields());
			}
			fieldsCache.putIfAbsent(c, l);
		}
		return l;
	}

	/**
	 * @return a standard cloner instance, will do for most use cases
	 */
	public static Cloner standard() {
		return new Cloner();
	}

}
