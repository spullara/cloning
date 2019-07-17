package com.rits.cloning;

import sun.reflect.ReflectionFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
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
	private final Set<Class<?>> ignored = new HashSet<>();
	private final Set<Class<?>> ignoredInstanceOf = new HashSet<>();
	private final Map<Class<?>, IFastCloner> fastCloners = new HashMap<>();

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
	 * registers an immutable class. Immutable classes are not cloned.
	 *
	 * @param c the immutable class
	 */
	public void registerImmutable(final Class<?>... c) {
		for (final Class<?> cl : c) {
			ignored.add(cl);
		}
	}

	public void registerFastCloner(final Class<?> c, final IFastCloner fastCloner) {
		if (fastCloners.containsKey(c)) throw new IllegalArgumentException(c + " already fast-cloned!");
		fastCloners.put(c, fastCloner);
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
		Map<Object, Object> clones = new IdentityHashMap<>();
		return cloneInternal(o, clones);
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
		return cloneInternal(o, null);
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

	private ConcurrentMap<Class, IDeepCloner> cloners = new ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")
	protected <T> T cloneInternal(final T o, Map<Object, Object> clones) {
		if (o == null) return null;
		if (o == this) return null;

		// Prevent cycles, expensive but necessary
		if (clones != null) {
			T clone = (T) clones.get(o);
			if (clone != null) {
				return clone;
			}
		}
		if (o instanceof Enum) return o;

		Class<?> aClass = o.getClass();
		IDeepCloner cloner = cloners.get(aClass);
		if (cloner == null) {
			cloner = findDeepCloner(aClass);
			cloners.put(aClass, cloner);
		}
		if (cloner == IGNORE_CLONER) {
			return o;
		}
		return cloner.deepClone(o, clones);
	}

	private IDeepCloner findDeepCloner(Class<?> clz) {
		if (ignored.contains(clz)) {
			return IGNORE_CLONER;
		} else if (isImmutable(clz)) {
			return IGNORE_CLONER;
		} else if (clz.isArray()) {
			return new CloneArrayCloner(clz);
		} else {
			final IFastCloner fastCloner = fastCloners.get(clz);
			if (fastCloner != null) {
				return new FastClonerCloner(fastCloner);
			} else {
				for (final Class<?> iClz : ignoredInstanceOf) {
					if (iClz.isAssignableFrom(clz)) {
						return IGNORE_CLONER;
					}
				}
			}
		}
		return new MethodHandleObjectCloner(clz);
	}

	private class CloneArrayCloner implements IDeepCloner {

		private boolean primitive;
		private boolean immutable;
		private Class<?> componentType;

		public CloneArrayCloner(Class<?> clz) {
			primitive = clz.getComponentType().isPrimitive();
			immutable = isImmutable(clz.getComponentType());
			componentType = clz.getComponentType();
		}

		@Override
		public <T> T deepClone(T o, Map<Object, Object> clones) {
			int length = Array.getLength(o);
			T newInstance = (T) Array.newInstance(componentType, length);
			if (clones != null) {
				clones.put(o, newInstance);
			}
			if (primitive || immutable) {
				System.arraycopy(o, 0, newInstance, 0, length);
			} else {
				if (clones == null) {
					for (int i = 0; i < length; i++) {
						Array.set(newInstance, i, Array.get(o, i));
					}
				} else {
					for (int i = 0; i < length; i++) {
						Array.set(newInstance, i, cloneInternal(Array.get(o, i), clones));
					}
				}
			}
			return newInstance;
		}
	}

	private class FastClonerCloner implements IDeepCloner {
		private IFastCloner fastCloner;

		FastClonerCloner(IFastCloner fastCloner) {
			this.fastCloner = fastCloner;
		}

		@Override
		public <T> T deepClone(T o, Map<Object, Object> clones) {
			T clone = (T) fastCloner.clone(o, Cloner.this::cloneInternal, clones);
			if (clones != null) clones.put(o, clone);
			return clone;
		}
	}

	private static IDeepCloner IGNORE_CLONER = new IgnoreClassCloner();

	private static class IgnoreClassCloner implements IDeepCloner {
		@Override
		public <T> T deepClone(T o, Map<Object, Object> clones) {
			throw new AssertionError("Don't call this directly");
		}
	}

	private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
	private static final ReflectionFactory REFLECTION_FACTORY = ReflectionFactory.getReflectionFactory();
	private static final Constructor OBJECT_CONSTRUCTOR;

	static {
		try {
			OBJECT_CONSTRUCTOR = Object.class.getConstructor((Class[]) null);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	private class MethodHandleObjectCloner implements IDeepCloner {

        private final Constructor constructor;
        private final MethodHandle[] getters;
		private final MethodHandle[] setters;
        private final int numFields;

        MethodHandleObjectCloner(Class<?> clz) {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			try {
				constructor = REFLECTION_FACTORY.newConstructorForSerialization(clz, OBJECT_CONSTRUCTOR);
				List<Field> l = new ArrayList<>();
				Class<?> sc = clz;
				do {
					Field[] fs = sc.getDeclaredFields();
					for (final Field f : fs) {
						if (!f.isAccessible()) {
							f.setAccessible(true);
						}
						int modifiers = f.getModifiers();
						if (!Modifier.isStatic(modifiers)) {
							l.add(f);
						}
					}
				} while ((sc = sc.getSuperclass()) != Object.class && sc != null);
				numFields = l.size();
				getters = new MethodHandle[numFields];
				setters = new MethodHandle[numFields];
				for (int i = 0; i < l.size(); i++) {
					Field field = l.get(i);
					getters[i] = lookup.unreflectGetter(field);
					setters[i] = lookup.unreflectSetter(field);
				}
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			}
        }

        @Override
        public <T> T deepClone(T o, Map<Object, Object> clones) {
            try {
                T newInstance = (T) constructor.newInstance();
                if (clones != null) {
                    clones.put(o, newInstance);
                    for (int i = 0; i < numFields; i++) {
						setters[i].invoke((T) newInstance, cloneInternal(getters[i].invoke((T) o), clones));
                    }
                } else {
                    // Shallow clone
                    for (int i = 0; i < numFields; i++) {
						setters[i].invoke((T) newInstance, getters[i].invoke((T) o));
                    }
                }
                return newInstance;
            } catch (Throwable e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
	 * @return a standard cloner instance, will do for most use cases
	 */
	public static Cloner standard() {
		return new Cloner();
	}

}
