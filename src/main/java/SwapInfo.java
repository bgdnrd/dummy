public class SwapInfo {
    private final int firstPageIndex;
    private final int noOfPages;

    public SwapInfo(int startPageIndex, int noOfPages) {
        this.firstPageIndex = startPageIndex;
        this.noOfPages = noOfPages;
    }

    public int getFirstPageIndex() {
        return firstPageIndex;
    }

    public int getNoOfPages() {
        return noOfPages;
    }
}
