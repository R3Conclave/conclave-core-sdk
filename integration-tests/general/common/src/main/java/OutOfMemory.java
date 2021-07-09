public class OutOfMemory {
  // assume a 63MB heap size:
  private static final int Padding = (63 - 10) * 1024 * 1024;

  private static class Node {
    Object value;
    Node next;
  }

  private static void bigObjects() {
    Object[] root = null;
    while (true) {
      System.out.println("Allocating...");
      Object[] x = new Object[1024 * 1024];
      x[0] = root;
      root = x;
    }
  }

  private static void littleObjects() {
    byte[] padding = new byte[Padding];
    padding[0] = 1;
    padding[Padding-1] = 2;
    Node root = null;
    while (true) {
      Node x = new Node();
      x.next = root;
      root = x;
    }
  }

  private static void bigAndLittleObjects() {
    byte[] padding = new byte[Padding];
    padding[0] = 1;
    padding[Padding-1] = 2;
    Node root = null;
    while (true) {
      Node x = new Node();
      x.value = new Object[1024 * 1024];
      x.next = root;
      root = x;
    }
  }

  public static void main(String[] args) {
    try {
      bigObjects();
      throw new RuntimeException();
    } catch (OutOfMemoryError e) {
      e.printStackTrace();
    }

    try {
      littleObjects();
      throw new RuntimeException();
    } catch (OutOfMemoryError e) {
      e.printStackTrace();
    }

    try {
      bigAndLittleObjects();
      throw new RuntimeException();
    } catch (OutOfMemoryError e) {
      e.printStackTrace();
    }
  }
}
