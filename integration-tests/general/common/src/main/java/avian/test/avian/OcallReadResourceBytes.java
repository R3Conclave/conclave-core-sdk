package avian.test.avian;

public abstract class OcallReadResourceBytes {

    public static synchronized byte[] readBytes(String path) {
        if (instance == null) {
            throw new RuntimeException("Uninitalized OcallReadResourceBytes");
        }
        return instance.readBytes_(path);
    }

    public static synchronized void initialize(OcallReadResourceBytes impl) {
        if (instance != null) {
            throw new RuntimeException("Double initialization");
        }
        instance = impl;
    }

    private static OcallReadResourceBytes instance = null;
    protected abstract byte[] readBytes_(String path);
}
