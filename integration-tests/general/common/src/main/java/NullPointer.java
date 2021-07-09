public class NullPointer {
  private int x;
  private Object y;

  private static void throw_(Object o) {
    o.toString();
  }

  private static void throwAndCatch(Object o) {
    try {
      o.toString();
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {

    // invokevirtual
    try {
      System.out.println("**************************");

      ((Object) null).toString();
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }


    try {
      System.out.println("**************************");
      ((Object) null).getClass();
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      System.out.println("**************************");
      throw_(null);
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    System.out.println("**************************");

    throwAndCatch(null);

    // invokeinterface
    try {
      System.out.println("**************************");

      ((Runnable) null).run();
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // arraylength
    try {
      System.out.println("**************************");

      int a = ((byte[]) null).length;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // iaload
    try {
      System.out.println("**************************");

      int a = ((byte[]) null)[42];
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // aaload
    try {
      System.out.println("**************************");

      Object a = ((Object[]) null)[42];
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // getfield (int)
    try {
      System.out.println("**************************");

      int a = ((NullPointer) null).x;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // getfield (Object)
    try {
      System.out.println("**************************");

      Object a = ((NullPointer) null).y;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // iastore
    try {
      System.out.println("**************************");

      ((byte[]) null)[42] = 42;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // aastore
    try {
      System.out.println("**************************");

      ((Object[]) null)[42] = null;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // putfield (int)
    try {
      System.out.println("**************************");

      ((NullPointer) null).x = 42;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // putfield (Object)
    try {

      System.out.println("**************************");

      ((NullPointer) null).y = null;
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // monitorenter
    try {

      System.out.println("**************************");

      synchronized ((Object) null) {
        int a = 42;
      }
      throw new RuntimeException();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
  }
}
