import java.io.*;

public class CacheObj<T> implements java.io.Serializable {
    private T value;
    private final transient long expiresAt;

    private transient StorageType storageType;
    private transient SwapInfo swapInfo = null;

    public CacheObj(T value, long expiresAt) {
        this.value = value;
        storageType = StorageType.MEMORY;
        this.expiresAt = expiresAt;
    }

    public T getValue() {
        return value;
    }

    public boolean isExpired() { return System.currentTimeMillis() * 1000 > expiresAt; }

    public boolean isMem() { return storageType == StorageType.MEMORY; }
//    public boolean isDisk() { return storageType == StorageType.DISK; }

    public byte[] bytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        oos.close();
        bos.close();
        return bos.toByteArray();
    }

    public void setSwap(int firstPageIndex, int noOfPages) {
        storageType = StorageType.DISK;
        swapInfo = new SwapInfo(firstPageIndex, noOfPages);
        value = null;
    }

    public void dropSwap(byte[] serializedObj) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(serializedObj);
        ObjectInputStream ois = new ObjectInputStream(bis);
        CacheObj<T> tmpCacheObj = (CacheObj<T>) ois.readObject();
        bis.close();
        ois.close();

        storageType = StorageType.MEMORY;
        swapInfo = null;
        value = tmpCacheObj.getValue();
//        System.out.println(tmpCacheObj.getValue());
    }

    public SwapInfo getSwapInfo() {
        return swapInfo;
    }
}

enum StorageType {
    MEMORY,
    DISK,
//    LOADING,
//    SWAPPING
// todo: remove those since no longer needed
}
