public abstract class AbstractCache<K, T> implements ICache<K, T> {
    protected AbstractCache() {}

    public abstract void put(K key, T value);
    public abstract T get(K key);
}