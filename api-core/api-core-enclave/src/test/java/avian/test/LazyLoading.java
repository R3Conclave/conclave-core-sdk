package avian.test;

import avian.test.avian.OcallReadResourceBytes;
import avian.test.avian.testing.FileUtils;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class LazyLoading {
  public static boolean loadLazy;

  public static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }
  
  public static void main(String[] args) throws Exception {
    Class c = new MyClassLoader(LazyLoading.class.getClassLoader()).loadClass
      ("LazyLoading$Test");

    c.getMethod("test").invoke(null);

    testInMemoryUrlClassLoader();
  }

  public static void testInMemoryUrlClassLoader() throws Exception {
    URL u = FileUtils.createInMemory("helloworld.jar");

    System.out.println("Loaded in memory");

    URLClassLoader cl = new URLClassLoader(new URL[] { u });
    Class hw = cl.loadClass("HelloWorld");
    Object o = hw.newInstance();
    Method m = hw.getDeclaredMethod("test");
    System.out.println(m.invoke(o));
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    protected Class findClass(String name) throws ClassNotFoundException {
        String fullyQualified = "avian.test." + name;
        String classFileName = "avian/test/" + name + ".class";
        return defineClass(fullyQualified, OcallReadResourceBytes.readBytes(classFileName));
    }

    public Class loadClass(String name) throws ClassNotFoundException {
      if ("LazyLoading$Test".equals(name)) {
        return findClass(name);
      } else if ("LazyLoading$Lazy".equals(name)
                 || "LazyLoading$Interface".equals(name))
      {
        if (loadLazy) {
          return findClass(name);
        } else {
          throw new ClassNotFoundException(name);
        }
      } else {
        return super.loadClass(name);
      }
    }

    private Class defineClass(String name, byte[] bytes) {
      return defineClass(name, bytes, 0, bytes.length);
    }
  }

  public static class Test {
    public static void test() {
      doTest();
      loadLazy = true;
      doTest();
    }

    private static void doTest() {
      if (loadLazy) {
        // anewarray
        Lazy[] array = new Lazy[1];
        
        // new and invokespecial
        Object lazy = new Lazy();
        
        // checkcast
        array[0] = (Lazy) lazy;

        // instanceof
        expect(lazy instanceof Lazy);

        // invokeinterface
        expect(array[0].interfaceMethod() == 42);

        // invokestatic
        expect(Lazy.staticMethod() == 43);

        // invokevirtual
        expect(array[0].virtualMethod() == 44);

        // ldc
        expect(Lazy.class == lazy.getClass());

        // multianewarray
        Lazy[][] multiarray = new Lazy[5][6];
        multiarray[2][3] = array[0];
        expect(multiarray[2][3] == array[0]);

        // getfield
        expect(array[0].intField == 45);

        // getstatic
        expect(Lazy.intStaticField == 46);

        // putfield int
        array[0].intField = 47;
        expect(array[0].intField == 47);

        // putfield long
        array[0].longField = 48;
        expect(array[0].longField == 48);

        // putfield object
        Object x = new Object();
        array[0].objectField = x;
        expect(array[0].objectField == x);

        // putstatic int
        array[0].intStaticField = 49;
        expect(array[0].intStaticField == 49);

        // putstatic long
        array[0].longStaticField = 50;
        expect(array[0].longStaticField == 50);

        // putstatic object
        Object y = new Object();
        array[0].objectStaticField = y;
        expect(array[0].objectStaticField == y);
      }
    }
  }

  private interface Interface {
    public int interfaceMethod();
  }

  private static class Lazy implements Interface {
    public static int intStaticField = 46;
    public static long longStaticField;
    public static Object objectStaticField;

    public int intField = 45;
    public long longField;
    public Object objectField;

    public int interfaceMethod() {
      return 42;
    }

    public static int staticMethod() {
      return 43;
    }

    public int virtualMethod() {
      return 44;
    }
  }
}
