import ru.gr0946x.net.Server;

void main() {
    try {
        var s = new Server(9460);
        // Server starts in background threads
        // To stop: type 'exit' in console
        synchronized (Object.class) {
            Object.class.wait();
        }
    } catch (Exception e) {
        System.err.println("Критическая ошибка запуска сервера: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
}