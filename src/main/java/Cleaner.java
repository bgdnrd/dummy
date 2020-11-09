import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

public class Cleaner {
    private static final int DEFAULT_CLEAN_PERIOD_SECONDS = 1;

    public <K, T> Cleaner(ConcurrentHashMap<K, SoftReference<CacheObj<T>>> underLyingMap, List<K> hotKeyList) {
        this(underLyingMap, hotKeyList, DEFAULT_CLEAN_PERIOD_SECONDS);
    }

    public <K, T> Cleaner(ConcurrentHashMap<K, SoftReference<CacheObj<T>>> underLyingMap, List<K> hotKeyList, int cleanPeriodSeconds) {
        Runnable target = () -> {
            Thread tt = Thread.currentThread();
            while (tt.isAlive()) {
                Iterator<Map.Entry<K, SoftReference<CacheObj<T>>>> iterator = underLyingMap.entrySet().iterator();

                CacheObj<T> tmpObj;
                while (iterator.hasNext()) {
                    // maybe use type inference everywhere XD
                    var next = iterator.next();
                    tmpObj = next.getValue().get();

                    // todo: expire disk also
                    // todo: might require additional testing
                    if (tmpObj.isMem() && tmpObj.isExpired()) {
                        hotKeyList.remove(next.getKey());
                        iterator.remove();
                    }

                    try {
                        Thread.sleep(cleanPeriodSeconds * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        tt.interrupt();
                    }
                }
            }
        };

        Thread cleaner = new Thread(target);
        cleaner.setDaemon(true);
        cleaner.start();
    }
 }
