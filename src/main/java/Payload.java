import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Класс, описывающий полезные данные, передаваемые в пакете, конкретный формат данных для каждого типа пакета отличается.
 * @author Mikhail Semenov
 * @version 1.0
 */
public class Payload {

    /** 14-битный адрес устройства-отправителя */
    private final Varuint src;

    /**
     * 14-битный “адрес” устройства-получателя, причем адреса 0x0000 и 0x3FFF (16383) зарезервированы.
     * Адрес 0x3FFF означает “широковещательную” рассылку, то есть данные адресованы всем устройствам одновременно
     * */
    private final Varuint dst;

    /** Порядковый номер пакета, отправленного устройством, от момента его включения. Нумеруется с 1. */
    private final Varuint serial;

    /**
     * Тип устройства, к которому относится пакет. <p>
     * 0x01 = {@link DeviceType#SMARTHUB} <p>
     * 0x02 = {@link DeviceType#ENVSENSOR} <p>
     * 0x03 = {@link DeviceType#SWITCH} <p>
     * 0x04 = {@link DeviceType#LAMP} <p>
     * 0x05 = {@link DeviceType#SOCKET} <p>
     * 0x06 = {@link DeviceType#CLOCK}
     */
    private final byte devType;

    /**
     * Команда протокола <p>
     * 0x01 = {@code WHOISHERE} <p>
     * 0x02 = {@code IAMHERE} <p>
     * 0x03 = {@code GETSTATUS} <p>
     * 0x04 = {@code STATUS} <p>
     * 0x05 = {@code SETSTATUS} <p>
     * 0x05 = {@code TICK}
     */
    private final byte cmd;

    /**
     * Передаваемые данные. Тип устройства {@link #devType} и команда {@link #cmd}
     * в совокупности определяют данные, которые передаются.
     */
    private final byte[][] cmdBody;

    Payload(long src, long dst, long serial, byte devType, byte cmd, byte[][] cmdBody) {
        this.src = new Varuint(src);
        this.dst = new Varuint(dst);
        this.serial = new Varuint(serial);
        this.devType = devType;
        this.cmd = cmd;
        this.cmdBody = cmdBody;
    }

    Payload(byte[] payload) {
        int index = 0;
        if (payload[index] < 0) {
            this.src = new Varuint(new byte[]{payload[index],payload[index+1]});
            index += 2;
        } else {
            this.src = new Varuint(payload[index]);
            index++;
        }
        if (payload[index] < 0) {
            this.dst = new Varuint(new byte[]{payload[index],payload[index+1]});
            index += 2;
        } else {
            this.dst = new Varuint(payload[index]);
            index++;
        }
        List<Byte> serialBytesList = new ArrayList<>();
        while (payload[index] < 0) {
            serialBytesList.add(payload[index]);
            index++;
        }
        serialBytesList.add(payload[index]);
        index++;
        byte[] serialBytes = new byte[serialBytesList.size()];
        for (int i = 0; i < serialBytes.length; i++) {
            serialBytes[i] = serialBytesList.get(i);
        }
        this.serial = new Varuint(serialBytes);
        this.devType = payload[index];
        this.cmd = payload[index + 1];
        this.cmdBody = cmdBodyByDeviceAndType(this.devType, this.cmd, Arrays.copyOfRange(payload, index+2, payload.length));
    }

    /**
     * Парсит байты для инициализации {@link #cmdBody}.
     * @param devType тип устройства
     * @param cmd команда протокола
     * @param bodyBytes массив байт с данными
     * @return {@link #cmdBody}
     */
    private byte[][] cmdBodyByDeviceAndType(byte devType, byte cmd, byte[] bodyBytes) {
        switch (devType) {
            case 1 -> {
                switch (cmd) {
                    case 1, 2 -> {
                        byte [][] result = new byte[1][bodyBytes[0]];
                        result[0] = Arrays.copyOfRange(bodyBytes, 1, bodyBytes.length);
                        return result;
                    }
                    case 3 -> {
                        return new byte[0][0];
                    }
                    case 5 -> {
                        return new byte[][] {new byte[] {bodyBytes[0]}};
                    }
                }
            }
            case 2 -> {
                switch (cmd) {
                    case 1, 2 -> {
                        byte[][] result = new byte[2][];
                        byte sensors = bodyBytes[0];
                        result[0][0] = sensors;
                        for (int i = 1; i < bodyBytes.length; i++) {
                            result[1][0] = bodyBytes[i];                // triggers
                        }
                        return result;
                    }
                    case 4 -> {
                        byte[][] result = new byte[1][bodyBytes.length];
                        result[0] = bodyBytes;                      // values
                        return result;
                    }
                }
            }
            case 3 -> {
                switch (cmd) {
                    case 1, 2 -> {
                        byte[][] result = new byte[2][];
                        result[0] = Arrays.copyOfRange(bodyBytes, 0, bodyBytes[0]+1);
                        result[1] = Arrays.copyOfRange(bodyBytes, bodyBytes[0]+2, bodyBytes.length-1);
                        return result;
                    }
                    case 4 -> {
                        return new byte[][] {bodyBytes};
                    }
                }
            }
            case 4, 5 -> {
                switch (cmd) {
                    case 1, 2 -> { return new byte[][] { Arrays.copyOf(bodyBytes, bodyBytes.length-1) }; }
                    case 4 -> {return new byte[][] {new byte[] {bodyBytes[0]}};}
                }
            }
            case 6 -> {
                switch (cmd) {
                    case 1, 2 -> { return new byte[][] { Arrays.copyOf(bodyBytes, bodyBytes.length-1) }; }
                    case 6 -> {return new byte[][] {bodyBytes};}
                }
            }
        }
        return new byte[][] {bodyBytes};    // 4, 5 cases
    }

    /**
     * Представление полезной нагрузки в виде {@link Bytes}.
     * @return {@link Bytes}
     */
    public Bytes asBytes() {
        Bytes result = new Bytes();
        result.addArray(this.src.getValue());
        result.addArray(this.dst.getValue());
        result.addArray(this.serial.getValue());
        result.add(this.devType);
        result.add(this.cmd);
        for (byte[] arr : this.cmdBody) {
            result.addArray(arr);
        }
        return result;
    }

    public byte getLength() {
        return (byte) this.asBytes().getBytes().size();
    }

    public long getSrcAsLong() {
        return src.asLong();
    }

    public byte getDevType() {
        return devType;
    }

    public byte[][] getCmdBody() {
        return cmdBody;
    }

    public Varuint getSerial() {
        return serial;
    }

    public byte getCmd() {
        return cmd;
    }
}
