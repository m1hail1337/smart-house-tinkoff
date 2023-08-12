import java.util.Arrays;

/**
 * Класс, описывающий структуру пакета в канале связи умного дома.
 * @author Mikhail Semenov
 * @version 1.0
 */
public class Packet {

    /** Размер поля payload в октетах (байтах). */
    private final byte length;

    /** Данные, передаваемые в пакете. См. {@link Payload}.*/
    private final Payload payload;

    /**
     * Контрольная сумма поля {@link #payload},
     * вычисленная по алгоритму Cyclic Redundancy Check 8
     * (<a href="http://www.sunshine2k.de/articles/coding/crc/understanding_crc.html">Подробнее</a>).
     */
    private final byte crc8;

    Packet(Payload payload) {
        this.length = payload.getLength();
        this.payload = payload;
        byte[] bytes = new byte[payload.asBytes().getBytes().size()];
        for (int i = 0; i < payload.asBytes().getBytes().size(); i++) {
            bytes[i] = payload.asBytes().getBytes().get(i);
        }
        this.crc8 = computeCRC8(bytes);
    }
    Packet(byte[] packet) {
        this.length = packet[0];
        this.crc8 = packet[packet.length-1];
        this.payload = new Payload(Arrays.copyOfRange(packet, 1, packet.length));
    }

    /**
     * Представление пакета в виде массива байт
     * @return Массив байт.
     */
    public byte[] asBytes() {
        byte[] result = new byte[2 + payload.asBytes().getBytes().size()];
        result[0] = length;
        for (int i = 1; i <= payload.asBytes().getBytes().size(); i++) {
            result[i] = payload.asBytes().getBytes().get(i-1);
        }
        result[result.length-1] = crc8;
        return result;
    }

    public Payload getPayload() {
        return payload;
    }

    /**
     * Алгоритм вычисления контрольной суммы CRC8.
     * @param bytes Информация
     * @return Контрольная сумма в байтах
     */
    private static byte computeCRC8(byte[] bytes) {
        byte generator = 0x1D;
        byte crc = 0;
        for (byte currByte : bytes) {
            crc ^= currByte;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) {
                    crc = (byte)((crc << 1) ^ generator);
                } else {
                    crc <<= 1;
                }
            }
        }
        return crc;
    }
}
