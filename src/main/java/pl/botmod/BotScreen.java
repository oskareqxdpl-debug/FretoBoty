package pl.botmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.botmod.bot.BotClient;
import pl.botmod.bot.BotManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BotScreen extends Screen {
    // paleta
    private static final int BG = 0xFF0E0F13, PANEL = 0xFF16181D, HEADER = 0xFF1E2128, BORDER = 0xFF2A2D35;
    private static final int ACCENT = 0xFF5865F2, TEXT = 0xFFE6E6E6, MUTED = 0xFF8A8F98;
    private static final int GREEN = 0xFF3BA55D, RED = 0xFFED4245, YELLOW = 0xFFFAA61A;
    private static final int BTN = 0xFF272B33, BTN_HOVER = 0xFF353A45;

    private static String nickText = "Bot";
    private static String ipText = "";
    private static String cmdText = "";
    private static boolean allMode = false;
    private static int focus = 0;
    private static final Map<BotClient, Integer> SCROLL = new HashMap<>();

    private final Screen parent;
    private TextFieldWidget nickField, ipField, cmdField;
    private final List<Btn> btns = new ArrayList<>();
    private BotClient overlayBot;

    private record Btn(String label, int x, int y, int w, int h, Runnable action, BooleanSupplier active) {}

    private record Cell(int idx, int x, int y, Object stack) {}

    private record Overlay(int px, int py, int pw, int ph, String title, boolean window, List<Cell> cells) {}

    public BotScreen(Screen parent) {
        super(Text.literal("Panel botow"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ budowa

    @Override
    protected void init() {
        btns.clear();
        int w = width, h = height;

        nickField = field(8, 9, 90, "Nick", 16, nickText, s -> nickText = s);
        ipField = field(102, 9, 150, "IP serwera", 120, ipText, s -> ipText = s);
        btn("Dodaj bota", 256, 9, 84, 20, this::addBot, null);
        btn("Mój IP", 344, 9, 52, 20, this::fillMyIp, null);

        int cmdW = w - 16 - 60 - 104 - 8;
        cmdField = field(8, h - 62, cmdW, "Wiadomość lub /komenda  (Enter)", 256, cmdText, s -> cmdText = s);
        btn("Wyślij", 8 + cmdW + 4, h - 62, 60, 20, this::sendCmd, null);
        btn(allMode ? "Wszystkie boty" : "Wybrany bot", 8 + cmdW + 68, h - 62, 100, 20, () -> {
            allMode = !allMode;
            init(this.client, this.width, this.height);
        }, () -> allMode);

        // sterowanie (obok hotbara)
        int x0 = 8 + 9 * 22 + 6;
        String[] labels = {"W", "A", "S", "D", "Skok", "<", ">", "Stop"};
        int[] ws = {22, 22, 22, 22, 32, 16, 16, 30};
        double f = Math.min(1.0, (w - 8 - x0) / 206.0);
        int x = x0;
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            int bw = Math.max(12, (int) (ws[i] * f));
            BooleanSupplier act = switch (i) {
                case 0 -> () -> focused() != null && focused().isForward();
                case 1 -> () -> focused() != null && focused().isLeft();
                case 2 -> () -> focused() != null && focused().isBack();
                case 3 -> () -> focused() != null && focused().isRight();
                default -> null;
            };
            btn(labels[i], x, h - 34, bw, 22, () -> control(k), act);
            x += bw + 2;
        }
    }

    private TextFieldWidget field(int x, int y, int w, String hint, int max, String value, Consumer<String> onChange) {
        TextFieldWidget t = new TextFieldWidget(textRenderer, x + 6, y + 6, w - 12, 14, Text.literal(hint));
        t.setDrawsBackground(false);
        t.setMaxLength(max);
        t.setText(value);
        t.setPlaceholder(Text.literal(hint).styled(s -> s.withColor(0x6B7078)));
        t.setChangedListener(onChange);
        addDrawableChild(t);
        return t;
    }

    private void btn(String label, int x, int y, int w, int h, Runnable action, BooleanSupplier active) {
        btns.add(new Btn(label, x, y, w, h, action, active));
    }

    // ------------------------------------------------------------------ logika

    private BotClient focused() {
        List<BotClient> b = BotManager.bots();
        return focus >= 0 && focus < b.size() ? b.get(focus) : null;
    }

    private void forTargets(Consumer<BotClient> action) {
        List<BotClient> list = new ArrayList<>();
        if (allMode) list.addAll(BotManager.bots());
        else if (focused() != null) list.add(focused());
        if (list.isEmpty()) {
            BotManager.log("Najpierw dodaj bota.");
            return;
        }
        for (BotClient b : list) action.accept(b);
    }

    private void control(int i) {
        switch (i) {
            case 0 -> forTargets(b -> b.setForward(!b.isForward()));
            case 1 -> forTargets(b -> b.setLeft(!b.isLeft()));
            case 2 -> forTargets(b -> b.setBack(!b.isBack()));
            case 3 -> forTargets(b -> b.setRight(!b.isRight()));
            case 4 -> forTargets(BotClient::jump);
            case 5 -> forTargets(b -> b.turn(-30f, 0f));
            case 6 -> forTargets(b -> b.turn(30f, 0f));
            default -> forTargets(BotClient::stopMoving);
        }
    }

    private void addBot() {
        String ip = ipText;
        if (ip.isBlank()) {
            String mine = myServerAddress();
            ip = mine == null ? "" : mine;
        }
        if (BotManager.addBot(nickText, ip)) {
            focus = BotManager.bots().size() - 1;
            saveConfig();
        }
    }

    private void sendCmd() {
        String text = cmdField.getText();
        if (text.isEmpty()) return;
        forTargets(b -> b.say(text));
        cmdField.setText("");
    }

    private String myServerAddress() {
        if (client != null && client.getCurrentServerEntry() != null) return client.getCurrentServerEntry().address;
        return null;
    }

    private void fillMyIp() {
        String a = myServerAddress();
        if (a == null) {
            BotManager.log("Nie jesteś na żadnym serwerze - wpisz IP ręcznie.");
            return;
        }
        ipField.setText(a);
    }

    /** Prawdziwy widok z oczu bota (tryb spectator + /spectate; wymaga OP). */
    private void observe(BotClient b) {
        if (client == null || client.player == null) {
            BotManager.log("Wejdź swoim klientem na ten sam serwer, żeby obserwować bota.");
            return;
        }
        client.player.networkHandler.sendChatCommand("gamemode spectator");
        client.player.networkHandler.sendChatCommand("spectate " + b.getName());
        client.setScreen(null);
    }

    private void closeOverlay() {
        if (overlayBot != null) overlayBot.closeWindow();
        overlayBot = null;
    }

    private boolean typing() {
        return nickField.isFocused() || ipField.isFocused() || cmdField.isFocused();
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("botmod.txt");
    }

    public static void loadConfig() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                List<String> l = Files.readAllLines(p);
                if (l.size() > 0) nickText = l.get(0);
                if (l.size() > 1) ipText = l.get(1);
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveConfig() {
        try {
            Files.write(configPath(), List.of(nickText, ipText));
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ geometria

    private int[] colRect(int i, int n) {
        int gap = 6;
        int cw = (width - 16 - gap * (n - 1)) / n;
        return new int[]{8 + i * (cw + gap), 40, cw, height - 68 - 40};
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // header: [Kam][EQ][x]
    private int[] closeRect(int[] c) { return new int[]{c[0] + c[2] - 16, c[1] + 3, 12, 12}; }
    private int[] eqRect(int[] c) { return new int[]{c[0] + c[2] - 38, c[1] + 3, 20, 12}; }
    private int[] camRect(int[] c) { return new int[]{c[0] + c[2] - 66, c[1] + 3, 26, 12}; }

    private Overlay layoutOverlay(BotClient b) {
        boolean window = b.getOpenWindow() != 0 && b.getOpenItems() != null;
        int pw = 9 * 18 + 24;
        List<Cell> cells = new ArrayList<>();
        int ph;
        String title;
        int px = (width - pw) / 2;
        if (window) {
            Object[] items = b.getOpenItems();
            int n = Math.max(0, items.length - 36);
            int rows = (n + 8) / 9;
            ph = 20 + rows * 18 + 6 + 54 + 4 + 18 + 22;
            int py = Math.max(4, (height - ph) / 2);
            for (int i = 0; i < n; i++)
                cells.add(new Cell(i, px + 12 + (i % 9) * 18, py + 20 + (i / 9) * 18, items[i]));
            int my0 = py + 20 + rows * 18 + 6;
            for (int k = 0; k < 27 && n + k < items.length; k++)
                cells.add(new Cell(n + k, px + 12 + (k % 9) * 18, my0 + (k / 9) * 18, items[n + k]));
            for (int k = 0; k < 9 && n + 27 + k < items.length; k++)
                cells.add(new Cell(n + 27 + k, px + 12 + k * 18, my0 + 58, items[n + 27 + k]));
            title = "Okno serwera - " + b.getName();
            return new Overlay(px, py, pw, ph, title, true, cells);
        }
        Object[] inv = b.getInventory();
        ph = 20 + 24 + 54 + 4 + 18 + 22;
        int py = Math.max(4, (height - ph) / 2);
        for (int i = 5; i <= 8; i++) cells.add(new Cell(i, px + 12 + (i - 5) * 18, py + 20, inv[i]));
        cells.add(new Cell(45, px + 12 + 4 * 18 + 12, py + 20, inv[45]));
        for (int k = 0; k < 27; k++)
            cells.add(new Cell(9 + k, px + 12 + (k % 9) * 18, py + 44 + (k / 9) * 18, inv[9 + k]));
        for (int k = 0; k < 9; k++)
            cells.add(new Cell(36 + k, px + 12 + k * 18, py + 44 + 58, inv[36 + k]));
        title = "Ekwipunek - " + b.getName();
        return new Overlay(px, py, pw, ph, title, false, cells);
    }

    // ------------------------------------------------------------------ wejscie

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (overlayBot != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeOverlay();
            return true;
        }
        if (cmdField.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendCmd();
            return true;
        }
        if (!typing() && handleKey(key, true)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (overlayBot == null) handleKey(key, false);
        return super.keyReleased(key, scan, mods);
    }

    private boolean handleKey(int key, boolean down) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            if (down) forTargets(b -> b.selectHotbar(key - GLFW.GLFW_KEY_1));
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_W -> forTargets(b -> b.setForward(down));
            case GLFW.GLFW_KEY_S -> forTargets(b -> b.setBack(down));
            case GLFW.GLFW_KEY_A -> forTargets(b -> b.setLeft(down));
            case GLFW.GLFW_KEY_D -> forTargets(b -> b.setRight(down));
            case GLFW.GLFW_KEY_SPACE -> {
                if (down) forTargets(BotClient::jump);
            }
            case GLFW.GLFW_KEY_R -> {
                if (down) forTargets(BotClient::useItem);
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (down) forTargets(b -> b.turn(-15f, 0f));
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (down) forTargets(b -> b.turn(15f, 0f));
            }
            case GLFW.GLFW_KEY_UP -> {
                if (down) forTargets(b -> b.turn(0f, -10f));
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (down) forTargets(b -> b.turn(0f, 10f));
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (down && !BotManager.bots().isEmpty()) focus = (focus + 1) % BotManager.bots().size();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // okno / ekwipunek
        if (overlayBot != null) {
            Overlay o = layoutOverlay(overlayBot);
            if (in(mx, my, o.px + o.pw - 18, o.py + 4, 14, 14)) {
                closeOverlay();
                return true;
            }
            for (Cell c : o.cells) {
                if (in(mx, my, c.x, c.y, 18, 18)) {
                    if (o.window) overlayBot.clickWindow(c.idx, button == 1 ? 1 : 0, hasShiftDown() ? 1 : 0);
                    else overlayBot.dropSlot(c.idx, hasShiftDown());
                    return true;
                }
            }
            return true;
        }
        // przyciski
        for (Btn b : btns) {
            if (in(mx, my, b.x, b.y, b.w, b.h)) {
                setFocused(null);
                b.action.run();
                return true;
            }
        }
        // hotbar zaznaczonego bota: lewy = wybierz slot, prawy = wybierz i uzyj (prawy klik)
        BotClient fb = focused();
        for (int i = 0; i < 9; i++) {
            if (in(mx, my, 8 + i * 22, height - 34, 20, 22)) {
                setFocused(null);
                if (fb != null) {
                    final int slot = i;
                    forTargets(b -> {
                        b.selectHotbar(slot);
                        if (button == 1) b.useItem();
                    });
                }
                return true;
            }
        }
        // kolumny botow
        List<BotClient> bots = BotManager.bots();
        for (int i = 0; i < bots.size(); i++) {
            int[] c = colRect(i, bots.size());
            if (in(mx, my, c[0], c[1], c[2], c[3])) {
                setFocused(null);
                focus = i;
                BotClient b = bots.get(i);
                int[] cl = closeRect(c), eq = eqRect(c), cam = camRect(c);
                if (in(mx, my, cl[0], cl[1], cl[2], cl[3])) {
                    BotManager.remove(b);
                    focus = Math.max(0, Math.min(focus, BotManager.bots().size() - 1));
                } else if (in(mx, my, eq[0], eq[1], eq[2], eq[3])) {
                    overlayBot = b;
                } else if (in(mx, my, cam[0], cam[1], cam[2], cam[3])) {
                    observe(b);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        List<BotClient> bots = BotManager.bots();
        for (int i = 0; i < bots.size(); i++) {
            int[] c = colRect(i, bots.size());
            if (in(mx, my, c[0], c[1], c[2], c[3])) {
                int cur = SCROLL.getOrDefault(bots.get(i), 0) + (int) Math.signum(vAmount) * 3;
                SCROLL.put(bots.get(i), Math.max(0, cur));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // ------------------------------------------------------------------ rysowanie

    private void box(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, x, y, color);
    }

    private void centered(DrawContext ctx, String s, int cx, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, cx - textRenderer.getWidth(s) / 2, y, color);
    }

    /** Tlo + caly glowny interfejs (widgety pol tekstowych rysuja sie na wierzchu). */
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
        List<BotClient> bots = BotManager.bots();
        if (focus >= bots.size()) focus = bots.size() - 1;
        if (focus < 0 && !bots.isEmpty()) focus = 0;
        for (int i = 0; i < bots.size(); i++) {
            if (bots.get(i).consumeWindowOpened()) {
                overlayBot = bots.get(i);
                focus = i;
            }
        }

        // ramki pol tekstowych
        for (TextFieldWidget t : new TextFieldWidget[]{nickField, ipField, cmdField}) {
            box(ctx, t.getX() - 6, t.getY() - 6, t.getWidth() + 12, 20, PANEL, t.isFocused() ? ACCENT : BORDER);
        }
        String cnt = "Boty " + bots.size() + "/" + BotManager.MAX_BOTS;
        text(ctx, cnt, width - 8 - textRenderer.getWidth(cnt), 15, MUTED);

        // kolumny
        if (bots.isEmpty()) {
            centered(ctx, "Wpisz nick i IP serwera, potem „Dodaj bota”.", width / 2, height / 2 - 14, MUTED);
            centered(ctx, "Możesz uruchomić do 3 botów naraz - każdy dostanie własną konsolę.", width / 2, height / 2 - 2, MUTED);
        }
        for (int i = 0; i < bots.size(); i++) drawColumn(ctx, bots.get(i), i, bots.size(), mx, my);

        // przyciski
        for (Btn b : btns) {
            boolean hover = in(mx, my, b.x, b.y, b.w, b.h);
            boolean act = b.active != null && b.active.getAsBoolean();
            ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, act ? ACCENT : hover ? BTN_HOVER : BTN);
            centered(ctx, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, TEXT);
        }

        // hotbar zaznaczonego bota
        BotClient fb = focused();
        Object[] inv = fb == null ? new Object[46] : fb.getInventory();
        for (int i = 0; i < 9; i++) {
            int x = 8 + i * 22, y = height - 34;
            boolean sel = fb != null && fb.getHeldSlot() == i;
            box(ctx, x, y, 20, 22, PANEL, sel ? ACCENT : BORDER);
            if (sel) ctx.drawBorder(x + 1, y + 1, 18, 20, ACCENT);
            if (inv[36 + i] instanceof ItemStack st && !st.isEmpty()) {
                ctx.drawItem(st, x + 2, y + 3);
                ctx.drawStackOverlay(textRenderer, st, x + 2, y + 3);
            }
        }

        // podpowiedz / komunikat
        String sys = BotManager.lastSystem(6000);
        if (sys != null) text(ctx, sys, 8, height - 11, YELLOW);
        else text(ctx, "WASD ruch · Spacja skok · 1-9 slot · R lub PPM na slocie = prawy klik · Tab = następny bot", 8, height - 11, MUTED);
    }

    private void drawColumn(DrawContext ctx, BotClient b, int i, int n, int mx, int my) {
        int[] c = colRect(i, n);
        int x = c[0], y = c[1], w = c[2], h = c[3];
        boolean foc = i == focus;
        box(ctx, x, y, w, h, PANEL, foc ? ACCENT : BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 18, HEADER);
        int dot = switch (b.getState()) {
            case ONLINE -> GREEN;
            case CONNECTING, IDLE -> YELLOW;
            case DISCONNECTED -> RED;
        };
        ctx.fill(x + 6, y + 6, x + 12, y + 12, dot);
        text(ctx, textRenderer.trimToWidth(b.getName(), w - 24 - 70), x + 17, y + 5, TEXT);
        mini(ctx, camRect(c), "Kam", mx, my);
        mini(ctx, eqRect(c), "EQ", mx, my);
        mini(ctx, closeRect(c), "x", mx, my);

        // konsola
        int ax = x + 5, ay = y + 22, aw = w - 10, ah = h - 26;
        List<String> lines = b.getLog();
        List<OrderedText> rows = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (String s : lines.subList(Math.max(0, lines.size() - 150), lines.size())) {
            int color = s.contains("[dbg]") ? MUTED : s.contains("] > ") ? 0xFF9DB1FF : TEXT;
            for (OrderedText o : textRenderer.wrapLines(Text.literal(s), aw)) {
                rows.add(o);
                cols.add(color);
            }
        }
        int visible = Math.max(1, ah / 10);
        int maxScroll = Math.max(0, rows.size() - visible);
        int scroll = Math.min(SCROLL.getOrDefault(b, 0), maxScroll);
        SCROLL.put(b, scroll);
        int end = rows.size() - scroll;
        int start = Math.max(0, end - visible);
        ctx.enableScissor(ax, ay, ax + aw, ay + ah);
        for (int k = start; k < end; k++) {
            ctx.drawTextWithShadow(textRenderer, rows.get(k), ax, ay + (k - start) * 10, cols.get(k));
        }
        ctx.disableScissor();
    }

    private void mini(DrawContext ctx, int[] r, String label, int mx, int my) {
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], in(mx, my, r[0], r[1], r[2], r[3]) ? BTN_HOVER : BTN);
        centered(ctx, label, r[0] + r[2] / 2, r[1] + 2, TEXT);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        if (overlayBot == null) return;

        // okno serwera albo ekwipunek na wierzchu
        Overlay o = layoutOverlay(overlayBot);
        ctx.fill(0, 0, width, height, 0xC0000000);
        box(ctx, o.px, o.py, o.pw, o.ph, PANEL, ACCENT);
        text(ctx, o.title, o.px + 12, o.py + 6, TEXT);
        boolean hc = in(mx, my, o.px + o.pw - 18, o.py + 4, 14, 14);
        ctx.fill(o.px + o.pw - 18, o.py + 4, o.px + o.pw - 4, o.py + 18, hc ? RED : BTN);
        centered(ctx, "x", o.px + o.pw - 11, o.py + 7, TEXT);

        ItemStack hovered = null;
        for (Cell c : o.cells) {
            box(ctx, c.x, c.y, 18, 18, 0xFF2E3138, BORDER);
            boolean hover = in(mx, my, c.x, c.y, 18, 18);
            if (c.stack instanceof ItemStack st && !st.isEmpty()) {
                ctx.drawItem(st, c.x + 1, c.y + 1);
                ctx.drawStackOverlay(textRenderer, st, c.x + 1, c.y + 1);
                if (hover) hovered = st;
            }
            if (hover) ctx.fill(c.x + 1, c.y + 1, c.x + 17, c.y + 17, 0x66FFFFFF);
        }
        String hint = o.window
                ? "Klik = wybierz (lewy/prawy), Shift = szybki ruch. Esc = zamknij"
                : "Klik = wyrzuć 1 szt., Shift+klik = cały stack. Esc = zamknij";
        text(ctx, textRenderer.trimToWidth(hint, o.pw - 16), o.px + 8, o.py + o.ph - 14, MUTED);
        if (hovered != null) ctx.drawItemTooltip(textRenderer, hovered, mx, my);
    }
}
