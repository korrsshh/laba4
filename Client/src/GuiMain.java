import ru.gr0946x.ui.GuiUi;

void main() {
    try {
        GuiUi.main(new String[]{});
    } catch (Exception e) {
        System.out.println("Ошибка запуска GUI: " + e.getMessage());
        e.printStackTrace();
    }
}
