package avian.test;

import java.lang.ref.WeakReference;

public class WeakRef {
  /*
     Object().finalize() is unreliable and is not recommended to be used;
     on the other hand, WeakReference works just fine.
     Note: with native-image ahead-of-time compiler,
     the hard reference becomes eligible for GC right after the last explicit use.
   */
  public static void main(String[] args) {
    Example obj = new Example();
    obj.print("direct use via hard reference");

    WeakReference<Example> ref = new WeakReference<>(obj);
    ref.get().print("use via weak reference");

    System.gc(); // kick off GC

    // the object is still accessible as there is an explicit use below via hard ref
    expect(ref.get() != null, "obj must not be GC'd");

    // note: use via weak ref alone won't suffice (yes, I've tried that)
    obj.print("still accessible");
    ref.get().print("use via weak ref");

    // traditionally, we would need to release the hard ref,
    // but not with native-image
    // obj = null;

    System.gc();
    expect(ref.get() == null, "obj must be GC'd");
  }

  private static void expect(boolean v, String error) {
    if (! v) throw new RuntimeException(error);
  }

  private static class Example {
    public void print(String message){
      System.out.println(message);
    }
  }
}
