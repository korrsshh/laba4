import ru.gr0946x.net.Client;
import ru.gr0946x.net.MessageType;
import ru.gr0946x.net.ProtocolConstants;
import ru.gr0946x.ui.ConsoleUi;

void main() {
    try {
        var c = new Client("localhost", 9460);
        var ui = new ConsoleUi(c);
        ui.addUserDataListener(c::sendData);
        c.addRawDataListener(data -> {
            String[] parts = data.split("\\:", 2);
            if (parts.length == 2) {
                try {
                    MessageType type = MessageType.valueOf(parts[0]);
                    ui.showInfo(parts[1], type);
                } catch (IllegalArgumentException e) {
                    ui.showInfo(data, MessageType.INFO);
                }
            }
        });
        c.start();
        ui.start();
    } catch (Exception e) {
        System.out.println("Ошибка: " + e.getMessage());
        e.printStackTrace();
    }
}