import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DummyCache<K, T> extends GenericCache<K, T> {
    private static final long DEFAULT_EXPIRE_SECONDS = 15; // 10 min

    private static final int DEFAULT_CAPACITY = 16;
    private final int capacity;

    public AtomicInteger size = new AtomicInteger();

    private static final int DEFAULT_CONCURRENCY_LEVEL = 1;

    private static final float DEFAULT_LOAD_FACTOR = 0.5f;

    private static final Policy DEFAULT_POLICY = Policy.LRU;
    private final Policy policy;

    private static final Swap DEFAULT_SWAP = Swap.ON;
    private final Swap swap;

    private Swapper swapper = null;
    private final Cleaner cleaner;

    // keeps hot keys alive and ordered
    private final List<K> hotKeysList;
    private final ConcurrentHashMap<K, SoftReference<CacheObj<T>>> underLyingMap;

    public DummyCache() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL, DEFAULT_POLICY, DEFAULT_SWAP);
    }

    public DummyCache(int capacity, float loadFactor, int concurrencyLevel, Policy policy, Swap swap) {
        this.capacity = capacity;
        this.policy = policy;

        this.swap = swap;
        if (this.swap == Swap.ON) {
            try {
                this.swapper = new Swapper();
            } catch (IOException e) {
                System.out.println("swap unavailable, check stack");
                e.printStackTrace();
                // "exceptional" handling here, please excuse
                System.exit(-1);
            }
        }

        hotKeysList = Collections.synchronizedList(new LinkedList<>());
        underLyingMap = new ConcurrentHashMap<>(capacity, loadFactor, concurrencyLevel);

        this.cleaner = new Cleaner(underLyingMap, hotKeysList);
    }

    // this one is public just for debugging
    public List<K> getHotKeysList() {
        return hotKeysList;
    }

    private boolean hasReachedCapacity() {
        return size.get() > capacity;
    }

    private void handleCapacityReach(K key) {
        if (swap == Swap.ON) {
            try {
                CacheObj<T> obj = underLyingMap.get(key).get();
                swapper.swap(obj);
            } catch (IOException e) {
                /* just ignore it and remove it from the cache */
            } catch (SwapFragmentationException e) { /* /---/ */ }
        }

        hotKeysList.remove(key);
    }

    private void hotKeySwap(K key) {
        if (policy == Policy.LRU) {
            hotKeysList.remove(key);
            hotKeysList.add(key);
        } else {
            // FIFO
            if (!hotKeysList.contains(key)) {
                hotKeysList.add(key);
            }
        }

        if (hasReachedCapacity()) {
            handleCapacityReach(hotKeysList.remove(0));
        }
    }

    private SoftReference<CacheObj<T>> pack(T value, long expireAfterSeconds) {
        return new SoftReference<>(new CacheObj<>(value, System.currentTimeMillis() * 1000 + expireAfterSeconds));
    }

    public void put(K key, T value) {
        this.put(key, value, DEFAULT_EXPIRE_SECONDS);
    }

    public void put(K key, T value, long expireAfterSeconds) {
        underLyingMap.put(key, pack(value, expireAfterSeconds));
        size.incrementAndGet();
        hotKeySwap(key);
    }

    public T get(K key){
        if (underLyingMap.containsKey(key)) {
            hotKeySwap(key);
            CacheObj<T> cacheObj = underLyingMap.get(key).get();

            if (cacheObj.isMem()) {
                return cacheObj.getValue();
            } else {
                try {
                    swapper.unSwap(cacheObj);
                    return cacheObj.getValue();
                } catch (IOException | ClassNotFoundException e) {
                    return null; // miss
                }
            }
        }
        return null;
    }

    // todo: add remove, clear and other eyecandy methods
}

enum Policy {
    LRU,
    FIFO
}

enum Swap {
    ON,
    OFF
}