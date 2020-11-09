public class dummy {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("hello dummy!");

        DummyCache<String, Object> test = new DummyCache<>();

        for (int i=0; i < 17; i++) {
            test.put("KEY" + i, "VALUE" + i, i + 5);
        }

        System.out.println(test.size);
        System.out.println(test.get("KEY1"));
        System.out.println(test.get("KEY0"));
        System.out.println(test.getHotKeysList());

        int seconds = 0;
        while (seconds < 301) {
            Thread.sleep(1 * 1000);
            System.out.println(test.getHotKeysList());
            seconds++;
        }

//        DummyCache<String, String> test = new DummyCache<>(16, 0.75f, 1, Policy.FIFO, Swap.OFF);
//        for (int i=0; i < 64; i++) {
//            test.put("KEY" + i, "VALUE" + i, 200);
//        }
//
//        System.out.println(test.size);
//        System.out.println(test.get("KEY1"));
//        test.put("KEY48", "newVALUE", 200);
//        test.put("KEY48", "newVALUE", 200);
//        System.out.println(test.getHotKeysList());
//
//        test.put("KEY2", "VALUE2", 200);
//        System.out.println(test.get("KEY2"));
    }
}