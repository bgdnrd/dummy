public class GenericCache<K, T> extends AbstractCache<K, T>  {
    protected GenericCache() {}

    @Override
    public void put(K key, T value) {}

    @Override
    public T get(K key) {
        return null;
    }
}

