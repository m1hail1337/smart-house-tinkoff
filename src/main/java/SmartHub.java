import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.*;


/**
 * Класс хаба умного дома
 * @author Mikhail Semenov
 * @version 1.0
 */
public class SmartHub {

    /** Широковещательный адрес: {@value}. */
    public static final long BROADCAST_ADDRESS = 0x3FFF;

    /**
     * Точка входа в программу.
     * @param args Массив аргументов командной строки. <p>
     * {@code args[0]} - ссылка на сервер умного дома <p>
     * {@code args[1]} - адрес хаба.
     */
    public static void main(String[] args) {
        long hubAddress = Integer.parseInt(args[1], 16);
        Map<Long, Device> devices = new HashMap<>(Map.of(hubAddress, new Device(hubAddress,
                DeviceType.SMARTHUB, "HUB01", 1)));
        try {
            URL url = new URL(args[0]);
            sendWHOISHERE(url, new Packet(new Payload(hubAddress,             // WHOISHERE
                            BROADCAST_ADDRESS,
                            1,
                            (byte) 0x01,
                            (byte) 0x01,
                            new byte[][]{new PacketString("HUB01").getBytes()})),
                    devices);
            List<Packet> statuses = new ArrayList<>();
            for (Device device : devices.values()) {
                devices.get(hubAddress).incrementSerial();
                long start = sendGetStatus(device, url, hubAddress, devices.get(hubAddress).getSerial());
                statuses.addAll(responsesByLimit300ms(start, url));
            }
            Map<Long, List<Long>> masterToSlaves = createMasterToSlaveMap(devices);
            Map<Long, Byte> entities = defineStartEntities(statuses);
            monitorRequests(url, devices, hubAddress, entities, masterToSlaves);
        } catch (IOException e) {
            System.exit(99);
        }
    }

    /**
     * Метод, инициализирующий связи мастер-устройств и ведомых
     * @param devices все устройства в сети в виде Адрес-Устройство
     * @return {@link Map}<{@link Long}, {@link List}<{@link Long}>>, которая содержит адреса мастеров как ключи и
     * списки с адресами ведомых как значения.
     */
    private static Map<Long, List<Long>> createMasterToSlaveMap(Map<Long, Device> devices) {
        Map<Long, List<Long>> result = new HashMap<>();
        for (Device device : devices.values()) {
            if (device.getType() == DeviceType.SWITCH || device.getType() == DeviceType.ENVSENSOR) {
                List<Long> slaves = result.getOrDefault(device.getAddress(), new ArrayList<>());
                for (String name : device.getSlaves()) {
                    for (Device dev : devices.values()) {
                        if (dev.getName().equals(name)) {
                            slaves.add(dev.getAddress());
                        }
                    }
                }
                result.put(device.getAddress(), slaves);
            }
        }
        return result;
    }

    /**
     * Добавляет новое мастер-устройство в {@link Map} связей.
     * @param master мастер-устройство
     * @param devices все устройства в системе
     * @param masterToSlaves связи мастер-устройств с ведомыми
     */
    private static void addNewMaster(Device master, Map<Long, Device> devices, Map<Long, List<Long>> masterToSlaves) {
        List<Long> slavesAddresses = new ArrayList<>();
        for (String name : master.getSlaves()) {
            for (Device device : devices.values()) {
                if (device.getName().equals(name)) {
                    slavesAddresses.add(device.getAddress());
                }
            }
        }
        masterToSlaves.put(master.getAddress(), slavesAddresses);
    }

    /**
     * Метод, принимающий пакеты по адресу хаба. Заканчивает выполнение программы с
     * @param url ссылка на сервер умного дома
     * @param devices все устройства в системе
     * @param hubAddress адрес хаба
     * @param entities текущие состояния устройств
     * @param masterToSlaves связи мастер-устройств с ведомыми
     * @throws IOException при проблемах с чтением потока данных в канале
     */
    private static void monitorRequests(URL url, Map<Long, Device> devices, long hubAddress,
                                        Map<Long, Byte> entities, Map<Long, List<Long>> masterToSlaves) throws IOException {
        while (true) {
            HttpURLConnection httpURLConnection = createHttpURLConnection(url);
            httpURLConnection.setDoOutput(false);
            httpURLConnection.connect();
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader isr = new InputStreamReader(httpURLConnection.getInputStream());
                BufferedReader bfr = new BufferedReader(isr);
                String line = bfr.readLine();
                byte[] packets = Base64.getUrlDecoder().decode(line);
                List<Packet> packetList = parsePackets(packets);
                for (Packet packet : packetList) {
                    sendResponse(packet, url, devices, hubAddress, entities, masterToSlaves);
                }
                isr.close();
            } else if (HttpURLConnection.HTTP_NO_CONTENT == httpURLConnection.getResponseCode()) {
                System.exit(0);
            } else {
                System.exit(99);
            }
            httpURLConnection.disconnect();
        }
    }

    /**
     * Отправляет инструкции от хаба в ответ на принятый пакет данных, изменяет состояние системы.
     * @param packet принятый пакет
     * @param url ссылка на сервер умного дома
     * @param devices все устройства в системе
     * @param hubAddress адрес хаба
     * @param entities текущие состояния устройств
     * @param masterToSlaves связи мастер-устройств с ведомыми
     * @throws IOException при проблемах с чтением потока данных в канале
     */
    private static void sendResponse(Packet packet, URL url, Map<Long, Device> devices, long hubAddress,
                                     Map<Long, Byte> entities, Map<Long, List<Long>> masterToSlaves) throws IOException {
        Device hub = devices.get(hubAddress);
        switch (packet.getPayload().getCmd()) {
            case 1 -> {
                Device newDevice =  new Device(packet.getPayload().getSrcAsLong(),
                        DeviceType.values()[packet.getPayload().getDevType()+1],
                        new String(packet.getPayload().getCmdBody()[0]),
                        packet.getPayload().getSerial().asLong());
                devices.put(packet.getPayload().getSrcAsLong(), newDevice);
                for (int i = 0; i < packet.getPayload().getCmdBody()[1].length; ) {
                    byte[] arr = Arrays.copyOfRange(packet.getPayload().getCmdBody()[1], i + 1, i + packet.getPayload().getCmdBody()[1][i] + 1);
                    devices.get(packet.getPayload().getSrcAsLong()).addSlave(new String(arr));
                    i += packet.getPayload().getCmdBody()[1][i] + 1;
                }
                hub.incrementSerial();
                sendIAMHERE(url, new Packet(new Payload(hubAddress,
                        BROADCAST_ADDRESS,
                        hub.getSerial(),
                        (byte) 1,
                        (byte) 2,
                        new byte[][]{})));
                if (packet.getPayload().getDevType() == 2 || packet.getPayload().getDevType() == 3) {
                    addNewMaster(newDevice, devices, masterToSlaves);
                }
                hub.incrementSerial();
                sendGetStatus(newDevice, url, hubAddress, hub.getSerial());
            }
            case 4 -> {
                byte value = packet.getPayload().getCmdBody()[0][0];
                if (entities.getOrDefault(packet.getPayload().getSrcAsLong(), (byte) -1) != value) {
                    if (packet.getPayload().getDevType() == 3) {
                        List<Long> slavesAddresses = masterToSlaves.get(packet.getPayload().getSrcAsLong());
                        for (Long slaveAddress : slavesAddresses) {
                            hub.incrementSerial();
                            sendSetStatus(devices.get(slaveAddress), url, hubAddress, hub.getSerial(), value);
                        }
                    } else if (packet.getPayload().getDevType() == 2) {
                        // TODO()
                    }
                    entities.put(packet.getPayload().getSrcAsLong(), value);
                }
            }
            default -> {}
        }
    }

    /**
     * Превращает массив байт в список пакетов.
     * @param packets массив байтов пакетов
     * @return {@link List}<{@link Packet}> список пакетов
     */
    private static List<Packet> parsePackets(byte[] packets) {
        List<Packet> result = new ArrayList<>();
        for (int i = 0; i < packets.length; ) {
            if (packets[i] == packets.length + 2) {
                result.add(new Packet(packets));
                break;
            } else {
                result.add(new Packet(Arrays.copyOfRange(packets, i, i + packets[i] + 2)));
                i += packets[i] + 2;
            }
        }
        return result;
    }

    /**
     * Инициализирует {@link  Map} с начальными состояниями устройств.
     * @param statuses пакеты с начальными состояниями устройств
     * @return {@code Map<Long, Byte>} Адрес-Состояние
     */
    private static Map<Long, Byte> defineStartEntities(List<Packet> statuses) {
        Map<Long, Byte> result = new HashMap<>();
        for (Packet status : statuses) {
            if (status.getPayload().getCmd() == 4) {
                byte value = status.getPayload().getCmdBody()[0][0];
                result.put(status.getPayload().getSrcAsLong(), value);
            }
        }
        return result;
    }

    /**
     * Отправляет GETSTATUS от хаба на указанное устройство.
     * @param device устройство-получатель
     * @param url ссылка на сервер умного дома
     * @param hubAddress адрес хаба
     * @param hubSerial номер пакета от хаба
     * @return {@code long} - время отправки запроса в формате {@code timestamp}
     * @throws IOException при проблемах с чтением потока данных в канале
     */
    private static long sendGetStatus(Device device, URL url, long hubAddress, long hubSerial) throws IOException {
        long start = 0;
        switch (device.getType()) {
            case SMARTHUB, CLOCK -> {
            }
            default -> {
                HttpURLConnection httpURLConnection = createHttpURLConnection(url);
                httpURLConnection.connect();
                Packet packet = new Packet(new Payload(hubAddress,
                        device.getAddress(),
                        hubSerial,
                        (byte) (device.getType().ordinal() + 1),
                        (byte) 3, new byte[][]{}));
                OutputStream os = httpURLConnection.getOutputStream();
                byte[] encodedPacket = Base64.getUrlEncoder().withoutPadding().encode(packet.asBytes());
                os.write(encodedPacket);
                if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStreamReader isr = new InputStreamReader(httpURLConnection.getInputStream());
                    BufferedReader bfr = new BufferedReader(isr);
                    String line = bfr.readLine();
                    byte[] packets = Base64.getUrlDecoder().decode(line);
                    // Т.к. пакет с тиком всегда первый (по усл.) можем сделать так:
                    Packet clockPacket = new Packet(Arrays.copyOfRange(packets, 0, packets[0] + 2));
                    start = new Varuint(clockPacket.getPayload().getCmdBody()[0]).asLong();
                }
                httpURLConnection.disconnect();
            }
        }
        return start;
    }

    /**
     * Отправляет SETSTATUS от хаба на указанное устройство.
     * @param device устройство-получатель
     * @param url ссылка на сервер умного дома
     * @param hubAddress адрес хаба
     * @param hubSerial номер пакета от хаба
     * @param value устанавливаемое значение
     * @throws IOException при проблемах с чтением потока данных в канале
     */
    private static void sendSetStatus(Device device, URL url, long hubAddress, long hubSerial, byte value) throws IOException {
        HttpURLConnection httpURLConnection = createHttpURLConnection(url);
        httpURLConnection.connect();
        Packet packet = new Packet(new Payload(hubAddress,
                device.getAddress(),
                hubSerial,
                (byte) (device.getType().ordinal() + 1),
                (byte) 5, new byte[][]{new byte[] {value}}));
        OutputStream os = httpURLConnection.getOutputStream();
        byte[] encodedPacket = Base64.getUrlEncoder().withoutPadding().encode(packet.asBytes());
        os.write(encodedPacket);
        os.close();
        if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK)
            httpURLConnection.disconnect();
    }

    /**
     * Собирает все пакеты, отправляемые на хаб в течение 300ms модельного времени.
     * @param start время начала
     * @param url ссылка на сервер умного дома
     * @return {@code List<Packet>} - Список пакетов, полученные в течение 300ms модельного времени
     * @throws IOException при проблемах с чтением потока данных в канале
     */
    private static List<Packet> responsesByLimit300ms(long start, URL url) throws IOException {
        long current = start;
        List<Packet> responses = new ArrayList<>();
        while (current - start < 300) {
            HttpURLConnection httpURLConnection = createHttpURLConnection(url);
            httpURLConnection.connect();
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStreamReader isr = new InputStreamReader(httpURLConnection.getInputStream());
                BufferedReader bfr = new BufferedReader(isr);
                String line;
                while ((line = bfr.readLine()) != null) {
                    byte[] packets = Base64.getUrlDecoder().decode(line);
                    List<Packet> packetList = parsePackets(packets);
                    for (Packet pack : packetList) {
                        if (!(pack.getPayload().getDevType() == DeviceType.CLOCK.ordinal() + 1)) {   // Ticks ignored.
                            responses.add(pack);
                        } else {
                            current = new Varuint(pack.getPayload().getCmdBody()[0]).asLong();
                        }
                    }
                }
                isr.close();
            }
        }
        return responses;
    }

    /**
     * Создает объект HTTP-подключения к серверу умного дома
     * @param url {@link  URL} ссылка на сервер умного дома
     * @return {@link  HttpURLConnection} объект HTTP-подключения к серверу умного дома
     * @throws IOException при проблемах с подключением к серверу или отправкой на него POST-запроса
     */
    private static HttpURLConnection createHttpURLConnection(URL url) throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoInput(true);
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setConnectTimeout(300);
        httpURLConnection.setReadTimeout(300);
        httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpURLConnection.setRequestProperty("Accept", "application/x-www-form-urlencoded");
        return httpURLConnection;
    }

    /**
     * Отправляет сообщение IAMHERE от хаба.
     * @param url ссылка на сервер умного дома
     * @param packet пакет сообщения
     * @throws IOException при проблемах с записью потока данных в канале
     */
    private static void sendIAMHERE(URL url, Packet packet) throws IOException {
        HttpURLConnection httpURLConnection = createHttpURLConnection(url);
        httpURLConnection.setDoInput(false);
        httpURLConnection.connect();
        OutputStream os = httpURLConnection.getOutputStream();
        byte[] encodedPacket = Base64.getUrlEncoder().withoutPadding().encode(packet.asBytes());
        os.write(encodedPacket);
        os.close();
        httpURLConnection.disconnect();
    }

    /**
     * Отправляет сообщение WHOISHERE от хаба.
     * @param url ссылка на сервер умного дома
     * @param packet пакет сообщения
     * @param devices список всех устройств в сети
     * @throws IOException при проблемах с записью или чтением потока данных в канале
     */
    private static void sendWHOISHERE(URL url, Packet
            packet, Map<Long, Device> devices) throws IOException {
        HttpURLConnection httpURLConnection = createHttpURLConnection(url);
        httpURLConnection.connect();
        OutputStream os = httpURLConnection.getOutputStream();
        byte[] encodedPacket = Base64.getUrlEncoder().withoutPadding().encode(packet.asBytes());
        os.write(encodedPacket);
        os.close();
        if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            InputStreamReader isr = new InputStreamReader(httpURLConnection.getInputStream());
            BufferedReader bfr = new BufferedReader(isr);
            String line = bfr.readLine();
            byte[] packets = Base64.getUrlDecoder().decode(line);
            isr.close();
            List<Packet> packetList = parsePackets(packets);
            packetList.addAll(responsesByLimit300ms(new Varuint(packetList.get(0).getPayload().getCmdBody()[0]).asLong(), url));
            for (Packet pack : packetList) {
                if (pack.getPayload().getCmd() == 2) {      // Проверка на то, что устройство отправило IAMHERE
                    Device device = new Device(pack.getPayload().getSrcAsLong(),
                            DeviceType.values()[pack.getPayload().getDevType() - 1],
                            new String(Arrays.copyOfRange(pack.getPayload().getCmdBody()[0],
                                    1, pack.getPayload().getCmdBody()[0].length)),
                            pack.getPayload().getSerial().asLong());
                    devices.put(device.getAddress(), device);
                } else if (pack.getPayload().getCmd() == 1) {   // Если получили WHOISHERE - отвечаем и добавляем в структуру
                    Device device = new Device(pack.getPayload().getSrcAsLong(),
                            DeviceType.values()[pack.getPayload().getDevType() - 1],
                            new String(Arrays.copyOfRange(pack.getPayload().getCmdBody()[0],
                                    1, pack.getPayload().getCmdBody()[0].length)),
                            pack.getPayload().getSerial().asLong());
                    devices.put(device.getAddress(), device);
                    Device hub = devices.get(packet.getPayload().getSrcAsLong());
                    hub.incrementSerial();
                    sendIAMHERE(url, new Packet(new Payload(hub.getAddress(),       // packet - hub's whoishere request
                            BROADCAST_ADDRESS,
                            hub.getSerial(),
                            (byte) (hub.getType().ordinal() + 1),
                            (byte) 2,  // IAMHERE
                            new byte[][]{})));
                }
                if (pack.getPayload().getDevType() == 2 || pack.getPayload().getDevType() == 3) {
                    for (int i = 0; i < pack.getPayload().getCmdBody()[1].length; ) {
                        byte[] arr = Arrays.copyOfRange(pack.getPayload().getCmdBody()[1], i + 1, i + pack.getPayload().getCmdBody()[1][i] + 1);
                        devices.get(pack.getPayload().getSrcAsLong()).addSlave(new String(arr));
                        i += pack.getPayload().getCmdBody()[1][i] + 1;
                    }
                }
            }
            //  printSystemStructure(devices);
        }
        httpURLConnection.disconnect();
    }

    /**
     * Выводит выявленную структуру системы (для отладки).
     * @param devices выявленные запросом WHOISHERE устройства
     */
    private static void printSystemStructure(Map<Long, Device> devices) {
        for (Map.Entry<Long, Device> device : devices.entrySet()) {
            System.out.println(device.getValue());
        }
    }
}

