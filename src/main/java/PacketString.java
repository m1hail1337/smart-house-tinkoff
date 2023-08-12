import java.util.Arrays;

/**
 * Класс строки, используемой в пакете. Первый байт строки хранит ее длину,
 * затем идут символы строки, в строке допустимы символы (байты) с кодами 32-126 (включительно).
 * @author Mikhail Semenov
 * @version 1.0
 */
public class PacketString {

    /** Длина строки */
    private final byte length;

    /** Массив символов строки в байтах */
    private final byte[] symbols;

    public PacketString(String string) {
        byte[] bytes = string.getBytes();
        this.length = (byte) bytes.length;
        this.symbols = Arrays.copyOfRange(bytes, 0, bytes.length);
    }

    /**
     * Выводит строку как массив байтов.
     * @return {@code byte[] bytes}, где {@code bytes[0]} - длина строки, остальное - символы.
     */
    public byte[] getBytes() {
        byte[] bytes = new byte[symbols.length + 1];
        bytes[0] = length;
        System.arraycopy(symbols, 0, bytes, 1, symbols.length);
        return bytes;
    }
}
