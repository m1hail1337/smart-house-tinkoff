import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Класс описывающий структуру данных {@code varuint} - беззнаковое целое число в формате ULEB128.
 * @author Mikhail Semenov
 * @version 1.0
 */
public class Varuint {
    private final static int BITS_LONG = 64;
    private final static int MASK_DATA = 0x7f;
    private final static int MASK_CONTINUE = 0x80;
    private final byte[] value;

    public Varuint(long value) {
        this.value = encode(value);
    }
    public Varuint(byte[] value) {
        this.value = value;
    }

    private static long decode(byte[] bytes) {
        long value = 0;
        int bitSize = 0;
        int read;
        InputStream is = new ByteArrayInputStream(bytes);

        do {
            try {
                read = is.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            value += ((long) read & MASK_DATA) << bitSize;
            bitSize += 7;
            if (bitSize >= BITS_LONG) {
                throw new ArithmeticException("ULEB128 value exceeds maximum value for long type.");
            }
        } while ((read & MASK_CONTINUE) != 0);
        return value;
    }

    private static byte[] encode(long value) {
        ArrayList<Byte> bytes = new ArrayList<>();
        do {
            byte b = (byte) (value & MASK_DATA);
            value >>= 7;
            if (value != 0) {
                b |= MASK_CONTINUE;
            }
            bytes.add(b);
        } while (value != 0);

        byte[] ret = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            ret[i] = bytes.get(i);
        }
        return ret;
    }

    public long asLong() {
        return decode(value);
    }

    public byte[] getValue() {
        return value;
    }
}

