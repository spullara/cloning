package com.rits.cloning;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 * <p>
 * This class is thread safe. One instance can be used by multiple threads on the same time.
 *
 * @author kostantinos.kougios
 * 18 Sep 2008
 */
public class Cloner {
    public static final IgnoreClassCloner IGNORE_CLONER = new IgnoreClassCloner();
    private final IInstantiationStrategy instantiationStrategy;
    private final Set<Class<?>> ignored = new HashSet<Class<?>>();
    private final Set<Class<?>> ignoredInstanceOf = new HashSet<Class<?>>();
    private final Map<Class<?>, IFastCloner> fastCloners = new HashMap<Class<?>, IFastCloner>();
    private final ConcurrentMap<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();

    private final boolean cloningEnabled = true;
    private final boolean nullTransient = false;
    private final boolean cloneSynthetics = true;

    public Cloner() {
        this.instantiationStrategy = ObjenesisInstantiationStrategy.getInstance();
        init();
    }

    public Cloner(final IInstantiationStrategy instantiationStrategy) {
        this.instantiationStrategy = instantiationStrategy;
        init();
    }

    public boolean isNullTransient() {
        return nullTransient;
    }

    private void init() {
        registerKnownJdkImmutableClasses();
        registerKnownConstants();
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
        fastCloners.put(LinkedHashMap.class, new FastClonerLinkedHashMap());
        fastCloners.put(ConcurrentHashMap.class, new FastClonerConcurrentHashMap());
        fastCloners.put(ConcurrentLinkedQueue.class, new FastClonerConcurrentLinkedQueue());
        fastCloners.put(TreeSet.class, new FastClonerTreeSet());

        // register private classes
        FastClonerArrayListSubList subListCloner = new FastClonerArrayListSubList();
        registerInaccessibleClassToBeFastCloned("java.util.ArrayList$SubList", subListCloner);
        registerInaccessibleClassToBeFastCloned("java.util.AbstractList$SubList", subListCloner);
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
            return cloneInternal(o, clones);
        }
    };

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

    protected void registerKnownConstants() {
        // registering known constants of the jdk.
        // registerStaticFields(TreeSet.class, HashSet.class, HashMap.class, TreeMap.class);
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

    /**
     * creates a new instance of c. Override to provide your own implementation
     *
     * @param <T> the type of c
     * @param c   the class
     * @return a new instance of c
     */
    protected <T> T newInstance(final Class<T> c) {
        return instantiationStrategy.newInstance(c);
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
        if (!cloningEnabled) return o;
        if (cyclic.get(o.getClass()) == null) {
            try {
                return cloneInternal(o, null);
            } catch (StackOverflowError soe) {
                cyclic.put(o.getClass(), true);
                return deepClone(o);
            }
        } else {
            return cloneInternal(o, new IdentityHashMap<>());
        }
    }

    private Map<Class, Boolean> cyclic = new ConcurrentHashMap<>();

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
        if (!cloningEnabled) return o;
        return cloneInternal(o, null);
    }

    // caches immutables for quick reference
    private final ConcurrentHashMap<Class<?>, Boolean> immutables = new ConcurrentHashMap<Class<?>, Boolean>();

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

    private Map<Class, IDeepCloner> cloners = new ConcurrentHashMap<>();

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

        final Class<T> clz = (Class<T>) o.getClass();
        IDeepCloner cloner = cloners.get(clz);
        if (cloner == null) {
            if (o instanceof Enum) {
                cloner = IGNORE_CLONER;
            } else if (ignored.contains(clz)) {
                cloner = IGNORE_CLONER;
            } else if (isImmutable(clz)) {
                cloner = IGNORE_CLONER;
            } else if (clz.isArray()) {
                cloner = new CloneArrayCloner();
            } else {
                final IFastCloner fastCloner = fastCloners.get(clz);
                if (fastCloner != null) {
                    cloner = new FastClonerCloner(fastCloner);
                } else {
                    for (final Class<?> iClz : ignoredInstanceOf) {
                        if (iClz.isAssignableFrom(clz)) {
                            cloner = IGNORE_CLONER;
                        }
                    }
                }
            }
            if (cloner == null) cloner = new CloneObjectCloner(clz);
            cloners.put(clz, cloner);
        }
        if (cloner == IGNORE_CLONER) {
            return o;
        }
        return cloner.deepClone(o, clones);
    }

    private class CloneArrayCloner implements IDeepCloner {
        @Override
        public <T> T deepClone(T o, Map<Object, Object> clones) {
            return cloneArray(o, clones);
        }
    }

    private class FastClonerCloner implements IDeepCloner {
        private IFastCloner fastCloner;

        FastClonerCloner(IFastCloner fastCloner) {
            this.fastCloner = fastCloner;
        }

        @Override
        public <T> T deepClone(T o, Map<Object, Object> clones) {
            return (T) fastCloner.clone(o, deepCloner, clones);
        }
    }

    private static class IgnoreClassCloner implements IDeepCloner {
        @Override
        public <T> T deepClone(T o, Map<Object, Object> clones) {
            throw new AssertionError("Don't call this directly");
        }
    }

    private class CloneObjectCloner implements IDeepCloner {
        private final Class<?> clz;
        private boolean cyclic = false;

        CloneObjectCloner(Class<?> clz) {
            this.clz = clz;
        }

        @Override
        public <T> T deepClone(T o, Map<Object, Object> clones) {
            try {
                try {
                    return cloneObject(o, (Class<T>) clz, clones);
                } catch (StackOverflowError soe) {
                    cyclic = true;
                    throw soe;
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    private enum FieldCloneType {
        CLONE,
        REFERENCE
    }

    // clones o, no questions asked!
    private <T> T cloneObject(T o, Class<T> clz, Map<Object, Object> clones) throws IllegalAccessException {
        final T newInstance = newInstance(clz);
        if (clones != null) clones.put(o, newInstance);
        final Field[] fields = allFields(clz);
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            final Object fieldObject = field.get(o);
            final boolean shouldClone = !field.isSynthetic() && !isAnonymousParent(field);
            final Object fieldObjectClone = shouldClone ? cloneInternal(fieldObject, clones) : fieldObject;
            field.set(newInstance, fieldObjectClone);
        }
        return newInstance;
    }

    private <T> IDeepCloner cloneCompiler(Class<T> clz) {
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T cloneArray(T o, Map<Object, Object> clones) {
        final Class<T> clz = (Class<T>) o.getClass();
        final int length = Array.getLength(o);
        final T newInstance = (T) Array.newInstance(clz.getComponentType(), length);
        if (clz.getComponentType().isPrimitive() || isImmutable(clz.getComponentType())) {
            System.arraycopy(o, 0, newInstance, 0, length);
        } else {
            for (int i = 0; i < length; i++) {
                final Object v = Array.get(o, i);
                final Object clone = cloneInternal(v, clones);
                Array.set(newInstance, i, clone);
            }
        }
        return newInstance;
    }

    private boolean isAnonymousParent(final Field field) {
        return "this$0".equals(field.getName());
    }


    /**
     * reflection utils
     */
    private void addAll(final List<Field> l, final Field[] fields) {
        for (final Field field : fields) {
            final int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                l.add(field);
            }
        }
    }

    /**
     * reflection utils, override this to choose which fields to clone
     */
    protected Field[] allFields(final Class<?> c) {
        Field[] l = fieldsCache.get(c);
        if (l == null) {
            List<Field> list = new ArrayList<>();
            final Field[] fields = c.getDeclaredFields();
            addAll(list, fields);
            Class<?> sc = c;
            while ((sc = sc.getSuperclass()) != Object.class && sc != null) {
                addAll(list, sc.getDeclaredFields());
            }
            fieldsCache.putIfAbsent(c, l = list.toArray(new Field[0]));
        }
        return l;
    }

    public boolean isCloningEnabled() {
        return cloningEnabled;
    }

    /**
     * @return a standard cloner instance, will do for most use cases
     */
    public static Cloner standard() {
        return new Cloner();
    }

    /**
     * @return if Cloner lib is in a shared jar folder for a container (i.e. tomcat/shared), then
     * this method is preferable in order to instantiate cloner. Please
     * see https://code.google.com/p/cloning/issues/detail?id=23
     */
    public static Cloner shared() {
        return new Cloner(new ObjenesisInstantiationStrategy());
    }

}
