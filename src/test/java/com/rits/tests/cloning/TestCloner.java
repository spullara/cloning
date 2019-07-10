package com.rits.tests.cloning;

import com.rits.cloning.Cloner;
import com.rits.cloning.FastClonerHashMap;
import com.rits.cloning.Immutable;
import com.rits.tests.cloning.TestCloner.SynthOuter.Inner;
import com.rits.tests.cloning.domain.A;
import com.rits.tests.cloning.domain.B;
import junit.framework.TestCase;
import org.junit.Assert;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author kostantinos.kougios
 *
 *         18 Sep 2008
 */
public class TestCloner extends TestCase {
	private final Cloner cloner = new Cloner();

	@Target(TYPE)
	@Retention(RUNTIME)
	private @interface MyImmutable {

	}

	@MyImmutable
	static private class MyAX {
	}

	public void testCalendarTimezone() {
		TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
		Calendar c = Calendar.getInstance(timeZone);
		Calendar cloned = cloner.deepClone(c);
		assertEquals(timeZone, cloned.getTimeZone());
	}

	public void testCloneEnumInMapIssue20() {
		Map<Integer, TestEnum> m = new HashMap<Integer, TestEnum>();
		m.put(1, TestEnum.A);
		m.put(2, TestEnum.B);
		m.put(3, TestEnum.C);
		Map<Integer, TestEnum> clone = cloner.deepClone(m);

		assertSame(clone.get(1), TestEnum.A);
		assertSame(clone.get(2), TestEnum.B);
		assertSame(clone.get(3), TestEnum.C);
	}

	public void testCustomAnnotation() {
		final Cloner cloner = new Cloner() {
			@Override
			protected Class<?> getImmutableAnnotation() {
				return MyImmutable.class;
			}
		};
		final MyAX o = new MyAX();
		final MyAX c = cloner.deepClone(o);
		assertSame(o, c);
	}

	public void testConsiderImmutable() {
		final Cloner cloner = new Cloner() {
			@Override
			protected boolean considerImmutable(final Class<?> clz) {
				return clz == Object.class;
			}
		};
		final Object o = new Object();
		final Object c = cloner.deepClone(o);
		assertSame(o, c);
	}

	class X {
		private X(int x) {
			x = 5;
		}
	}

	@Immutable(subClass = true)
	static public class ATestImmutable {
	}

	static public class ATestImmutableSubclass extends ATestImmutable {

	}

	@Immutable
	static public class BTestImmutable {
	}

	static public class BTestImmutableSubclass extends BTestImmutable {
	}

	public void testIssue7() {
		final HashMap<Object, Object> source = new HashMap<Object, Object>();
		source.put("string", "string");
		source.put("array", new Integer[]{1, 2, 3});
		final HashMap<Object, Object> sc = cloner.shallowClone(source);
		assertEquals("string", sc.get("string"));
	}

	public void testIgnoreInstanceOf() {
		final Cloner cloner = new Cloner();
		cloner.dontCloneInstanceOf(A.class);

		final A a = new A() {
		};
		assertNotSame(a.getClass(), A.class);
		assertSame(a, cloner.deepClone(a));
	}

	public void testImmutableSubclassNotEnabled() {
		final BTestImmutableSubclass a = new BTestImmutableSubclass();
		final BTestImmutableSubclass ca = cloner.deepClone(a);
		assertNotSame(a, ca);
	}

	public void testImmutableSubclass() {
		final ATestImmutableSubclass a = new ATestImmutableSubclass();
		assertSame(a, cloner.deepClone(a));
		assertSame(a, cloner.deepClone(a));
	}

	public void testImmutable() {
		final ATestImmutable a = new ATestImmutable();
		assertSame(a, cloner.deepClone(a));
		assertSame(a, cloner.deepClone(a));
	}

	/**
	 * tests if immutable clone is the same instance
	 */
	public void testCloneImmutables() {
		final String s = "test1";
		final String clone1 = cloner.deepClone(s);
		assertSame(s, clone1);
	}

	/**
	 * tests if immutable clone is the same instance
	 */
	public void testCloneFloat() {
		final Float float1 = new Float(8);
		final Float cloned = cloner.deepClone(float1);
		assertSame(float1, cloned);
		assertEquals(float1, cloned);
	}

	/**
	 * tests if arrays are cloned correctly
	 */
	public void testCloneArrays() {
		final int[] ia = {1, 2, 3};
		final int[] cloned = cloner.deepClone(ia);
		assertEquals(ia.length, cloned.length);
		for (int i = 0; i < ia.length; i++) {
			assertEquals(ia[i], cloned[i]);
		}
		final double[] da = {1, 2, 3};
		final double[] dcloned = cloner.deepClone(da);
		assertEquals(da.length, dcloned.length);
		for (int i = 0; i < ia.length; i++) {
			assertEquals(da[i], dcloned[i]);
		}
	}

	private class Simple {
		private int x = 1;
		private String s = "simple";
		private Complex complex;

		public Complex getComplex() {
			return complex;
		}

		public void setComplex(final Complex complex) {
			this.complex = complex;
		}

		public int getX() {
			return x;
		}

		public void setX(final int x) {
			this.x = x;
		}

		public String getS() {
			return s;
		}

		public void setS(final String s) {
			this.s = s;
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj instanceof Simple) {
				final Simple s = (Simple) obj;
				return s.getS().equals(getS()) && s.getX() == getX();
			}
			return super.equals(obj);
		}
	}

	/**
	 * tests cloning of a simple class
	 */
	public void testCloneSimple() {
		final Simple simple = new Simple();
		simple.setS("x1");
		simple.setX(20);
		final Simple clone = cloner.deepClone(simple);
		assertEquals(simple.getS(), clone.getS());
		assertSame(simple.getS(), clone.getS());
		assertEquals(simple.getX(), clone.getX());
		simple.setS("x2");
		simple.setX(30);
		assertNotSame(simple.getS(), clone.getS());
		assertFalse(simple.getS().equals(clone.getS()));
		assertFalse(simple.getX() == clone.getX());
	}

	protected class Complex {
		private int x = 1;
		private String s = "complex";
		private final List<Simple> l = new ArrayList<Simple>();

		public Complex() {
			l.add(new Simple());
			final Simple simple = new Simple();
			simple.setS("s2");
			simple.setX(30);
			l.add(simple);
			simple.setComplex(this);
		}

		public int getX() {
			return x;
		}

		public void setX(final int x) {
			this.x = x;
		}

		public String getS() {
			return s;
		}

		public List<Simple> getL() {
			return l;
		}

		public void setS(final String s) {
			this.s = s;
		}

	}

	public void testCloneStack() {
		final List<Integer> lst = new LinkedList<Integer>();
		for (int i = 0; i < 100000; i++) {
			lst.add(i);
		}
		final List<Integer> clone = cloner.deepClone(lst);
		assertEquals(lst.size(), clone.size());
	}

	public void testCloneTreeSet() {
		final TreeSet<DC> set = new TreeSet<DC>();
		final DC dc1 = new DC(5);
		set.add(dc1);
		final DC dc2 = new DC(10);
		set.add(dc2);

		assertTrue(set.contains(dc1));
		assertTrue(set.contains(dc2));

		assertTrue(set.remove(dc1));
		set.add(dc1);

		final TreeSet<DC> set2 = cloner.deepClone(set);

		assertTrue(set2.contains(dc1));
		assertTrue(set2.contains(dc2));
		assertTrue(set2.remove(dc1));
		assertEquals(1, set2.size());
	}

	public void testCloneHashSet() {
		Set<DC> set = new HashSet<DC>();
		final DC dc1 = new DC(5);
		set.add(dc1);
		final DC dc2 = new DC(10);
		set.add(dc2);

		assertTrue(set.contains(dc1));
		assertTrue(set.contains(dc2));

		assertTrue(set.remove(dc1));
		set.add(dc1);

		//		cloner.setDumpClonedClasses(true);

		set = cloner.deepClone(set);

		assertTrue(set.contains(dc1));
		assertTrue(set.contains(dc2));
		assertTrue(set.remove(dc1));
		assertEquals(1, set.size());
	}

	public void testArrayListCloning() {
		final ArrayList<Object> l = new ArrayList<Object>();
		l.add(Calendar.getInstance());
		l.add(2);
		l.add(3);
		l.add("kostas");

		final ArrayList<Object> cloned = cloner.deepClone(l);
		assertEquals(l.size(), cloned.size());
		for (int i = 0; i < l.size(); i++) {
			assertEquals(l.get(i), cloned.get(i));
		}
		assertNotSame(l, cloned);
		assertNotSame(l.get(0), cloned.get(0));
		assertSame(l.get(1), cloned.get(1));

		l.add(5);
		assertEquals(4, cloned.size());
		cloned.add(8);
		assertEquals(5, l.size());
	}

	public void testLinkedListCloning() {
		final LinkedList<Object> l = new LinkedList<Object>();
		l.add(Calendar.getInstance());
		l.add(2);
		l.add(3);
		l.add("kostas");

		final LinkedList<Object> cloned = cloner.deepClone(l);
		assertEquals(l.size(), cloned.size());
		for (int i = 0; i < l.size(); i++) {
			assertEquals(l.get(i), cloned.get(i));
		}
		assertNotSame(l, cloned);
		assertNotSame(l.get(0), cloned.get(0));
		assertSame(l.get(1), cloned.get(1));

		l.add(5);
		assertEquals(4, cloned.size());
		cloned.add(8);
		assertEquals(5, l.size());
	}

	public void testHashSetCloning() {
		final HashSet<Object> l = new HashSet<Object>();
		l.add(Calendar.getInstance());
		l.add(2);
		l.add(3);
		l.add("kostas");

		final HashSet<Object> cloned = cloner.deepClone(l);
		assertNotSame(l, cloned);
		assertEquals(l.size(), cloned.size());
		for (final Object o : l) {
			assertTrue(cloned.contains(o));
		}
	}

	public void testHashMapCloning() {
		final HashMap<String, Object> m = new HashMap<String, Object>();
		m.put("kostas", Calendar.getInstance());
		m.put("tina", 500);
		m.put("george", "Ah!");
		final HashMap<String, Object> cloned = cloner.deepClone(m);
		assertEquals(m.size(), cloned.size());
		for (final Map.Entry<String, Object> e : m.entrySet()) {
			assertEquals(e.getValue(), cloned.get(e.getKey()));
		}
		assertNotSame(m, cloned);
		assertNotSame(m.get("kostas"), cloned.get("kostas"));
		assertSame(m.get("tina"), cloned.get("tina"));
		cloned.put("x", 100);
		assertEquals(3, m.size());
		assertEquals(4, cloned.size());
	}

	public void testTreeMapCloning() {
		final TreeMap<String, Object> m = new TreeMap<String, Object>();
		m.put("kostas", Calendar.getInstance());
		m.put("tina", 500);
		m.put("george", "Ah!");
		final TreeMap<String, Object> cloned = cloner.deepClone(m);
		assertEquals(m.size(), cloned.size());
		for (final Map.Entry<String, Object> e : m.entrySet()) {
			assertEquals(e.getValue(), cloned.get(e.getKey()));
		}
		assertNotSame(m, cloned);
		assertNotSame(m.get("kostas"), cloned.get("kostas"));
		assertSame(m.get("tina"), cloned.get("tina"));
		cloned.put("x", 100);
		assertEquals(3, m.size());
		assertEquals(4, cloned.size());
	}

	public void testCopyPropertiesArrayPrimitive() {
		final int[] src = new int[]{5, 6, 7};
		final int[] dest = new int[3];
		cloner.copyPropertiesOfInheritedClass(src, dest);
		assertEquals(src[0], dest[0]);
		assertEquals(src[1], dest[1]);
		assertEquals(src[2], dest[2]);
	}

	public void testCopyPropertiesArray() {
		final Object[] src = new Object[]{new Integer(5), new Float(8.5f), new Double(3.5d)};
		final Object[] dest = new Object[3];
		cloner.copyPropertiesOfInheritedClass(src, dest);
		assertEquals(src[0], dest[0]);
		assertEquals(src[1], dest[1]);
		assertEquals(src[2], dest[2]);
	}

	public void testCopyPropertiesInheritedClasses() {
		final A a = new A();
		final B b = new B();
		b.setName("x");
		b.setX(-1);
		b.setY(10);
		cloner.copyPropertiesOfInheritedClass(a, b);

		assertEquals("kostas", b.getName());
		assertEquals(5, b.getX());
		assertEquals(10, b.getY());
	}

	static class SynthOuter {
		public Inner getInner() {
			return new Inner();
		}

		class Inner {
			Object x = new Object();

			public SynthOuter getOuter() {
				return SynthOuter.this;
			}
		}
	}

	public void testTreeMapWithComparator() {
		final TreeMap<Object, String> m = new TreeMap<Object, String>(new Comparator<Object>() {
			public int compare(final Object o1, final Object o2) {
				return o1.hashCode() - o2.hashCode();
			}
		});
		m.put(new Object() {
			@Override
			public int hashCode() {
				return 1;
			}
		}, "1");
		m.put(new Object() {
			@Override
			public int hashCode() {
				return 2;
			}
		}, "2");

		final TreeMap<Object, String> clone = cloner.deepClone(m);
		assertEquals(m, clone);
	}

	public void testTreeSetWithComparator() {
		final TreeSet<Object> set = new TreeSet<Object>(new Comparator<Object>() {
			public int compare(final Object o1, final Object o2) {
				return o1.hashCode() - o2.hashCode();
			}
		});

		set.add(new Object() {
			@Override
			public int hashCode() {
				return 1;
			}
		});
		set.add(new Object() {
			@Override
			public int hashCode() {
				return 2;
			}
		});

		final TreeSet<Object> clone = cloner.deepClone(set);
		assertEquals(set, clone);
	}

	public void testEnumIssue9() {
		final TestEnum original = TestEnum.A;
		final TestEnum clone = cloner.deepClone(original);
		assertSame(clone, original);
	}

	public void testUnregisterFastCloner() {
		Cloner cloner = new Cloner();
		cloner.unregisterFastCloner(HashMap.class);
		cloner.registerFastCloner(HashMap.class, new FastClonerHashMap());
	}

	public void testEmptyLinkedHashMap() {
		LinkedHashMap<Integer, Integer> m = new LinkedHashMap<Integer, Integer>();
		LinkedHashMap<Integer, Integer> cloned = cloner.deepClone(m);
		assertEquals(m, cloned);
	}

	public void testLinkedHashMap() {
		LinkedHashMap<Integer, Integer> m = new LinkedHashMap<Integer, Integer>();
		for (int i = 1; i < 10000; i++) {
			m.put(i, i * 2);
		}
		LinkedHashMap<Integer, Integer> cloned = cloner.deepClone(m);
		assertEquals(m, cloned);
	}

	public void testLinkedHashMapIterationOrder() {
		LinkedHashMap<Integer, Integer> m = new LinkedHashMap<Integer, Integer>();
		for (int i = 1000; i >= 1; i--) {
			m.put(i, i * 2);
		}
		LinkedHashMap<Integer, Integer> cloned = cloner.deepClone(m);
		Iterator<Integer> it = cloned.keySet().iterator();
		for (int i = 1000; i >= 1; i--) {
			assertEquals((Integer) i, it.next());
		}
	}

	public void testCloneArrayListSubList() {
		List<String> a = new ArrayList<String>();
		a.add("1");
		a.add("2");
		a.add("3");

		List<String> b = a.subList(0, 1);

		b.add("3");
		b.remove(0);

		b.size(); // fine

		assertEquals(1, cloner.deepClone(b).size()); // throws ConcurrentModificationException
	}

	public void testCloneLinkedListSubList() {
		List<String> a = new LinkedList<String>();
		a.add("1");
		a.add("2");
		a.add("3");

		List<String> b = a.subList(0, 1);

		b.add("3");
		b.remove(0);

		b.size(); // fine

		List<String> clone = cloner.deepClone(b);
		assertEquals(1, clone.size());
	}

//	public void testHashMapIterator() {
//		HashMap<Integer, String> m = new HashMap<Integer, String>();
//		m.put(1, "one");
//		m.put(2, "two");
//
//		Iterator<Map.Entry<Integer, String>> it = m.entrySet().iterator();
//		m.put(3, "three");
//
//		Iterator<Map.Entry<Integer, String>> cIt = cloner.deepClone(it);
//		cIt.next(); // throws ConcurrentModificationException
//	}

	public void testConcurrentLinkedQueue() {
		ConcurrentLinkedQueue<A> list = new ConcurrentLinkedQueue<A>();
		for (int i = 0; i < 3000; ++i) {
			A a = new A();
			a.setX(i);
			list.add(a);
		}
		ConcurrentLinkedQueue<A> cloned = cloner.deepClone(list);// StackOverflowError
		Assert.assertArrayEquals(list.toArray(), cloned.toArray());
		assertNotSame(list, cloned);
		assertNotSame(list.peek(), cloned.peek());
	}
}
