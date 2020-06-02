package avian.test;


public class VirtualMethodsInheritance {

    /****************************************************************/
    // 1. More specific interface can override a default method
    interface Goo {
        int foo();
    }

    interface FooDefault extends Goo {
        default int foo() {
            return 42;
        }
    }

    static class FooDefaultImpl implements FooDefault {
    }

    /****************************************************************/
    // 2. Test correct resolution against diamond problem
    interface Top {
        default int foo() { return 0; }
        int bar();
    }

    interface Left extends Top {
        default int bar() { return 1; }
    }

    interface Right extends Top {
        default int foo() { return 2; }
    }

    static class DiamondBottom implements Left, Right {
        // Inherit default bar from Left
        // Inherit default default foo from Right
    }

    /****************************************************************/
    // 3. Validate interface default methods never override methods
    // of concrete classes
    interface Bar {
        int bar();
    }

    interface DefaultBar extends Bar {
        default int bar() { return -1; }
        int foo();
    }

    static class BarImpl implements Bar {
        public int bar() {
            return 42;
        }
    }

    static class DefaultBarImpl extends BarImpl implements DefaultBar {
        public int foo() { return 0; }
        // must inherit BarImpl.bar
    }

    public static void main(String[] args) {
        {
            FooDefault f = new FooDefaultImpl();
            if (f.foo() != 42) {
                throw new RuntimeException("Method resolution failed");
            }
        }

        {
            Top n = new DiamondBottom();
            if (n.foo() != 2) {
                throw new RuntimeException("Method resolution failed");
            }
            if (n.bar() != 1) {
                throw new RuntimeException("Method resolution failed");
            }
        }

        {
            Bar o = new DefaultBarImpl();
            if (o.bar() != 42) {
                throw new RuntimeException("Method resolution failed");
            }
        }
    }
}
