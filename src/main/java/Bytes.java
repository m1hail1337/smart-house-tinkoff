import java.util.ArrayList;

/**
 * Массив байтов переменного размера. Конкретизируется в описании конкретных форматов пакетов
 * в зависимости от типа пакета и типа устройства.
 * @author Mikhail Semenov
 * @version 1.0
 */
public class Bytes {

    /**
     * Список (массив переменного размера) байтов.
     */
    private final ArrayList<Byte> bytes;

    public Bytes() {
        this.bytes = new ArrayList<>();
    }

    public ArrayList<Byte> getBytes() {
        return bytes;
    }

    public void add(byte value) {
        this.bytes.add(value);
    }

    public void addArray(byte[] array) {
        for (byte b : array) {
            this.bytes.add(b);
        }
    }
}
