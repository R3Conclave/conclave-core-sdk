package avian.test;

import avian.test.avian.OcallReadResourceBytes;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;

public class DefineClass {

  private static Class loadClass(String name) throws Exception {
    byte[] data = OcallReadResourceBytes.readBytes("avian/test/" + name + ".class");
    return new MyClassLoader(DefineClass.class.getClassLoader()).defineClass
      ("avian.test." + name, data);
  }

  private static void testStatic() throws Exception {
    loadClass("DefineClass$Hello")
      .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
  }

  private static void testDerived() throws Exception {
    System.out.println
      (String.valueOf
       (((Base) loadClass("DefineClass$Derived").newInstance()).zip()));
  }

  public static void main(String[] args) throws Exception {
    testStatic();
    testDerived();
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class defineClass(String name, byte[] bytes) {
      return defineClass(name, bytes, 0, bytes.length);
    }
  }

  public static class Hello {
    public static void main(String[] args) {
      System.out.println("hello, world!");
    }
  }

  public abstract static class Base {
    public int foo;
    public int[] array;
    
    public void bar() { }

    public abstract int zip();
  }

  public static class Derived extends Base {
    public int zip() {
      return 42;
    }
  }
}
