import java.util.ArrayList;
import java.util.List;

/**
 * Класс, описывающий устройство.
 * @author Mikhail Semenov
 * @version 1.0
 */
public class Device {

    /** Адрес устройства */
    private final long address;

    /** Тип устройства */
    private final DeviceType type;

    /** Имя устройства */
    private final String name;

    /** Список имен ведомых устройств. Если устройство не имеет ведомых, список пуст. */
    private final List<String> slaves = new ArrayList<>();

    /** Количество пакетов, отправленных устройством */
    private long serial;

    public Device(long src, DeviceType type, String name, long serial) {
        this.address = src;
        this.type = type;
        this.name = name;
        this.serial = serial;
    }

    /**
     * Инкрементирует количество отправленных пакетов.
     */
    public void incrementSerial() {
        this.serial++;
    }

    /**
     * Добавляет в список ведомых имя устройства.
     * @param name Имя ведомого.
     */
    public void addSlave(String name) {
        this.slaves.add(name);
    }

    public List<String> getSlaves() {
        return slaves;
    }

    public long getAddress() {
        return address;
    }

    public DeviceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getSerial() {
        return serial;
    }

    // Для отладки
    @Override
    public String toString() {
        return "Type: " + type + "; Address: " + address + "; Name: " + name +
                ((slaves.size() > 0) ?"; Slaves: " + slaves : "");
    }
}
