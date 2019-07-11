package com.rits.tests.cloning;

import com.rits.cloning.Cloner;
import com.rits.cloning.IObjectCloner;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class CloneCompilerExample implements IObjectCloner {

    @Test
    public void testClone() {
        A a = new A();
        Cloner cloner = new Cloner();
        A clone = cloner.deepClone(a);
        assertEquals(a, clone);
    }

    @Test
    public void testNewClone() {
        CloneCompilerExample cce = new CloneCompilerExample();
        A a = new A();
        A clone = (A) cce.deepClone(a);
        assertEquals(a, clone);
    }

    class C {
        D d;
    }

    class D {
        C c;
    }

    @Test
    public void testCycle() {
        D d = new D();
        C c = new C();
        c.d = d;
        d.c = c;
        Cloner cloner = new Cloner();
        D d1 = cloner.deepClone(d);
    }

    @Override
    public Object deepClone(Object o) {
        A a = (A) o;
        return null;
    }
}

class A {
    String a = "test";
    int b = 10;
    int[] numbers = new int[] {0,1,2,3,4,5,6,7,8};
    List l = new ArrayList();
    {
        l.add("test");
        l.add("food");
        l.add("foor");
        l.add("bar");
    }
    Map m = new HashMap();
    {
        m.put("foo", new B());
        m.put("sam", new B());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        A a1 = (A) o;
        return b == a1.b &&
                Objects.equals(a, a1.a) &&
                Arrays.equals(numbers, a1.numbers) &&
                Objects.equals(l, a1.l) &&
                Objects.equals(m, a1.m);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(a, b, l, m);
        result = 31 * result + Arrays.hashCode(numbers);
        return result;
    }
}

class B {
    String a = "foo";
    String b = "bar";
    int i = 10;
    int j = 20;
    int k = 30;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        B b1 = (B) o;
        return i == b1.i &&
                j == b1.j &&
                k == b1.k &&
                Objects.equals(a, b1.a) &&
                Objects.equals(b, b1.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, i, j, k);
    }
}