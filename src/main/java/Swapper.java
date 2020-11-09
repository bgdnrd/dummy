import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;

public class Swapper {
    private static final int DEFAULT_PAGE_SIZE_BYTES = 32;
    // out of the 32 bytes per page first 2 bytes are reserved for page length
    // up to 2^16 - 1 (2 bytes)
    private final int usableBytes;

    // that means that max page size in bytes can be up 65535
    // and thanks again Java for not providing unsigned int
    // and force me to make this variable LONG
    // TODO check this
    private static final long MAX_PAGE_SIZE_BYTES = 65535;

    private final int pageSizeBytes;

    private static final int DEFAULT_NO_OF_PAGES = 128;
    private final int noOfPages;

    private final BitSet pageTable;

    private static final String DEFAULT_SWAP_LOCATION = "./dummy.swap";

    private final File fd;

    // Arthur, remember? *wink wink*
    // P.S. yep, inca un file format :))
    private static final String MAGIC_LOL = "Pln Bbcf!";
    private static final int HEADER_SIZE = MAGIC_LOL.length();

    public Swapper() throws IOException {
        this(DEFAULT_PAGE_SIZE_BYTES, DEFAULT_NO_OF_PAGES, DEFAULT_SWAP_LOCATION);
    }

    public Swapper(int pageSizeBytes, int noOfPages, String swapLocation) throws IOException {
        this.pageSizeBytes = pageSizeBytes;
        this.usableBytes = this.pageSizeBytes - 2;
        this.noOfPages = noOfPages;
        this.pageTable = new BitSet(this.noOfPages);

        Path p = Paths.get(swapLocation);
        Files.deleteIfExists(p);
        Files.createFile(p);
        FileWriter fw = new FileWriter(swapLocation);
        fw.write(MAGIC_LOL);
        fw.flush();
        fw.close();

        this.fd = new File(swapLocation);
    }

    // tries to find free numberOfConsecutivePages in the pageTable
    // might fail because of fragmentation :(
    // TODO: maybe implement some defragmentation strategy
    // returns the index of the 1st free page
    private int findFreePages(int numberOfConsecutivePages) throws SwapFragmentationException {
        int candidateIndex = pageTable.nextClearBit(0);
        if (candidateIndex + numberOfConsecutivePages >= noOfPages) {
            throw new SwapFragmentationException("meh, swap full!");
        }
        while (true) {
            BitSet tempBs = pageTable.get(candidateIndex, candidateIndex + numberOfConsecutivePages);
            if (tempBs.isEmpty()) {
                return candidateIndex;
            } else {
                candidateIndex += tempBs.nextClearBit(0);
            }

            if (candidateIndex + numberOfConsecutivePages >= noOfPages) {
                throw new SwapFragmentationException("yep, fragmentation needs to be handled");
            }
        }
    }

    private int computeNoOfPages(int serializedLength) {
        int pages = 0;
        int noOfFullPages = serializedLength / usableBytes;
        pages += noOfFullPages;
        int trailingBytes = serializedLength % usableBytes;
        if (trailingBytes != 0) {
            pages+=1;
        }

        return pages;
    }

    private void updatePageTable(int firstPageIndex, int noOfPages) {
        pageTable.set(firstPageIndex, firstPageIndex+noOfPages);
    }

    private RandomAccessFile RAFInit(String mode) throws FileNotFoundException {
        return new RandomAccessFile(fd, mode);
    }

    private void RAFEnd(RandomAccessFile raf, FileChannel fc, FileLock lk) throws IOException {
        lk.release();
        fc.close();
        raf.close();
    }

    public <T> void swap(CacheObj<T> cacheObj) throws IOException, SwapFragmentationException {
        byte[] serialized = cacheObj.bytes();
        int len = serialized.length;

        int noOfPages = computeNoOfPages(len);
        int firstPageIndex = findFreePages(noOfPages);

        byte[] readBuffer = new byte[usableBytes];
        byte[] writeBuffer = new byte[pageSizeBytes];
        ByteArrayInputStream objectReader = new ByteArrayInputStream(serialized);
        int bytesRead;
        ByteBuffer bb = ByteBuffer.allocate(writeBuffer.length);

        RandomAccessFile raf = RAFInit("rw");
        FileChannel fc = raf.getChannel();

        int startReadFrom = HEADER_SIZE + firstPageIndex*pageSizeBytes;
        FileLock lk = fc.lock(startReadFrom, noOfPages*pageSizeBytes, false);
        fc.position(startReadFrom);

        while ((bytesRead = objectReader.read(readBuffer)) != -1 ) {
            writeBuffer[0] = (byte) bytesRead;
            writeBuffer[1] = (byte) (bytesRead >>> 8);
            System.arraycopy(readBuffer, 0, writeBuffer,2, bytesRead);

            long fp = fc.position();
            bb.put(writeBuffer).flip();
            fc.write(bb, fp);
            fc.position(fp+pageSizeBytes);
            bb.clear();
        }
        updatePageTable(firstPageIndex, noOfPages);
        cacheObj.setSwap(firstPageIndex, noOfPages);

        RAFEnd(raf, fc, lk);
    }

    public <T> void unSwap(CacheObj<T> cacheObj) throws IOException, ClassNotFoundException {
        SwapInfo si = cacheObj.getSwapInfo();
        int firstPageIndex = si.getFirstPageIndex();
        int noOfPages = si.getNoOfPages();

        RandomAccessFile raf = RAFInit("r");
        FileChannel fc = raf.getChannel();

        int startReadFrom = HEADER_SIZE + firstPageIndex*pageSizeBytes;
        int readLength = noOfPages*pageSizeBytes;

        FileLock lk = fc.lock(startReadFrom, readLength, true);
        fc.position(startReadFrom);

        ByteBuffer bb = ByteBuffer.allocate(readLength);
        fc.read(bb);
        byte[] dataBytes = new byte[readLength];
        bb.flip();
        bb.get(dataBytes);

        byte[] serializedObjWithTrailingZeros = new byte[readLength];
        int pagesRead = 0;
        int cursor = 0;
        while (pagesRead < noOfPages) {
            byte firstByteInPage = dataBytes[pagesRead * pageSizeBytes];
            byte secondByteInPage = dataBytes[pagesRead * pageSizeBytes + 1];
            int pageReadSize = firstByteInPage + (secondByteInPage<<8);
            System.arraycopy(dataBytes, cursor+=2, serializedObjWithTrailingZeros, cursor-2*(pagesRead+1), pageReadSize);
            cursor+=pageReadSize;
            pagesRead++;
        }

        int i = serializedObjWithTrailingZeros.length - 1;
        while (i >= 0 && serializedObjWithTrailingZeros[i] == 0) { --i; }
        byte[] serializedObj = Arrays.copyOf(serializedObjWithTrailingZeros, i + 1);

        cacheObj.dropSwap(serializedObj);
        updatePageTable(firstPageIndex, noOfPages);

        RAFEnd(raf, fc, lk);
    }
}

class SwapFragmentationException extends Exception {
    public SwapFragmentationException(String msg) {
        super(msg);
    }
}