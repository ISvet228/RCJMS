package Project.Game;
import Project.EngineCreator.*;
import Project.ThemeCreator.*;
import Project.CreatorKit.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public final class DevConsole {
    //region Variables
    //region Layout Constants
    private static final int MAX_LOG_LINES = 400, MAX_HISTORY = 100, PADDING = 10, LINE_HEIGHT = 18, WHEEL_ROWS = 3;
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Color BACKGROUND = new Color(8, 10, 14, 218);
    private static final Color BORDER = new Color(120, 170, 255);
    private static final Color INPUT_TEXT = new Color(235, 235, 235);
    private static final Color SELECTION = new Color(70, 110, 180, 200);
    //endregion
    //region Command Registry
    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private final Map<String, String> shortNames = new HashMap<>();
    //endregion
    //region Console Text
    private final Deque<LogLine> log = new ArrayDeque<>();
    private long nextSeq = 0;
    private String input = "";
    private long cursorStart = System.currentTimeMillis();
    private String pending;
    //endregion
    //region History State
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String draft = "";
    //endregion
    //region Panel State
    private boolean open = false;
    private int scrollRows = 0;
    private List<Row> rows = List.of();
    private int firstVisible = 0, visibleCount = 1, panelHeight = 0, charWidth = 8;
    //endregion
    //region Selection State
    private Point selectionStart, selectionEnd;
    private boolean dragging;
    //endregion
    //endregion

    //region Commands
    public synchronized void register(String name, String usage, Command command) {
        commands.put(name.toLowerCase(Locale.ROOT), new Entry(usage, command, new ArrayList<>()));
    }
    public synchronized void alias(String shortName, String name) {
        String target = name.toLowerCase(Locale.ROOT);
        Entry entry = commands.get(target);
        if (entry == null) throw new IllegalArgumentException("no command " + name + " to give a short name to");
        String key = shortName.toLowerCase(Locale.ROOT);
        entry.shortNames().add(key);
        shortNames.put(key, target);
    }
    public synchronized List<String> commandNames() { return new ArrayList<>(commands.keySet()); }
    public synchronized List<String> helpLines() {
        List<String> out = new ArrayList<>();
        for (Entry entry : commands.values()) {
            String line = entry.usage();
            if (!entry.shortNames().isEmpty()) line += "  (short: " + String.join(", ", entry.shortNames()) + ")";
            out.add(line);
        }
        return out;
    }
    public synchronized String usageOf(String name) {
        Entry entry = commands.get(name.toLowerCase(Locale.ROOT));
        return entry == null ? null : entry.usage();
    }
    public void execute(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) return;
        synchronized (this) {
            log(Level.COMMAND, "> " + text);
            if (history.isEmpty() || !history.getLast().equals(text)) history.add(text);
            while (history.size() > MAX_HISTORY) history.removeFirst();
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        String name = tokens.removeFirst().toLowerCase(Locale.ROOT);
        Entry entry;
        synchronized (this) { entry = commands.get(shortNames.getOrDefault(name, name)); }
        if (entry == null) {
            log(Level.ERROR, "unknown command: " + name + " (type --help)");
            return;
        }
        Args args = new Args(tokens, this);
        try {
            entry.command().run(args);
            for (String token : tokens) {
                if (args.isOptionUnread(token)) log(Level.WARN, name + ": unknown option " + token + " (type --help)");
            }
        }
        catch (IllegalArgumentException exception) {
            log(Level.ERROR, exception.getMessage());
            log(Level.INFO, "usage: " + entry.usage());
        }
        catch (RuntimeException exception) { log(Level.ERROR, name + " failed: " + exception); }
    }
    @FunctionalInterface public interface Command {
void run(Args args);
 }
    public static final class Args {
        private final List<String> tokens;
        private final Set<String> used = new HashSet<>();
        private final DevConsole console;
        private Args(List<String> tokens, DevConsole console) {
            this.tokens = tokens;
            this.console = console;
        }
        public List<String> values() {
            List<String> out = new ArrayList<>();
            for (String t : tokens) if (!t.startsWith("-")) out.add(t);
            return out;
        }
        public boolean flag(String name) {
            String wanted = name.toLowerCase(Locale.ROOT);
            used.add(wanted);
            for (String t : tokens) if (t.toLowerCase(Locale.ROOT).equals(wanted)) return true;
            return false;
        }
        public String optionValue(String prefix) {
            String wanted = prefix.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                String lower = t.toLowerCase(Locale.ROOT);
                if (t.startsWith("-") && lower.startsWith(wanted)) {
                    used.add(lower);
                    return t.substring(wanted.length());
                }
            }
            return null;
        }
        boolean isOptionUnread(String token) { return token.startsWith("-") && !used.contains(token.toLowerCase(Locale.ROOT)); }
        public void info(String text) { console.log(Level.INFO, text); }
        public void warn(String text) { console.log(Level.WARN, text); }
    }
    //endregion

    //region Visibility
    public synchronized boolean isOpen() { return open; }
    public synchronized void setOpen(boolean value) {
        open = value;
        cursorStart = System.currentTimeMillis();
        if (!value) clearSelection();
    }
    public synchronized void toggle() { setOpen(!open); }
    //endregion

    //region Logging
    public synchronized void log(Level level, String text) {
        for (String line : text.split("\n", -1)) {
            log.addLast(new LogLine(nextSeq++, level, line));
            while (log.size() > MAX_LOG_LINES) log.removeFirst();
        }
    }
    public synchronized void clearLog() {
        log.clear();
        scrollRows = 0;
        clearSelection();
    }
    public synchronized String transcript() {
        StringBuilder out = new StringBuilder();
        for (LogLine line : log) out.append(line.text()).append('\n');
        return out.toString();
    }
    public enum Level { INFO, COMMAND, WARN, ERROR }
    private record Entry(String usage, Command command, List<String> shortNames) {

    }
    private record LogLine(long seq, Level level, String text) {

    }
    //endregion

    //region Keyboard
    public synchronized boolean handleKey(KeyEvent keyEvent) {
        int key = keyEvent.getKeyCode();
        if (keyEvent.isControlDown() || keyEvent.isMetaDown()) {
            if (key == KeyEvent.VK_C) copySelection();
            else if (key == KeyEvent.VK_V) pasteIntoInput();
            else if (key == KeyEvent.VK_A) selectAll();
            return true;
        }
        if (key == KeyEvent.VK_ESCAPE) { setOpen(false); return true; }
        if (key == KeyEvent.VK_ENTER) { submit(); return true; }
        if (key == KeyEvent.VK_BACK_SPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (key == KeyEvent.VK_UP) { recall(-1); return true; }
        if (key == KeyEvent.VK_DOWN) { recall(1); return true; }
        if (key == KeyEvent.VK_PAGE_UP) { scrollRows += Math.max(1, visibleCount - 1); return true; }
        if (key == KeyEvent.VK_PAGE_DOWN) { scrollRows = Math.max(0, scrollRows - Math.max(1, visibleCount - 1)); return true; }
        return true;
    }
    public synchronized void typed(char c) {
        if (isToggleChar(c) || c == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(c)) return;
        if (input.length() < 200) input += c;
    }
    public static boolean isToggleChar(char c) {
        return c == '`' || c == '~' || c == 'ё' || c == 'Ё';
    }
    public synchronized String takeSubmitted() {
        String line = pending;
        pending = null;
        return line;
    }
    //endregion

    //region Command Line
    private void submit() {
        String line = input;
        input = "";
        draft = "";
        historyIndex = -1;
        scrollRows = 0;
        pending = line;
    }
    private void recall(int direction) {
        if (history.isEmpty()) return;
        if (historyIndex < 0) draft = input;
        int next = historyIndex < 0 ? (direction < 0 ? history.size() - 1 : -1) : historyIndex + direction;
        if (next < 0 || next >= history.size()) { historyIndex = -1; input = draft; return; }
        historyIndex = next;
        input = history.get(next);
    }
    //endregion

    //region Mouse
    public synchronized void wheel(int rotation) { scrollRows = Math.max(0, scrollRows - rotation * WHEEL_ROWS); }
    public synchronized void mousePressed(int x, int y) {
        if (y < 0 || y >= panelHeight - 2 * PADDING - LINE_HEIGHT) return;
        Point point = pointAt(x, y);
        if (point == null) return;
        selectionStart = selectionEnd = point;
        dragging = true;
    }
    public synchronized void mouseDragged(int x, int y) {
        if (!dragging) return;
        Point point = pointAt(x, y);
        if (point != null) selectionEnd = point;
    }
    public synchronized void mouseReleased() {
        dragging = false;
        if (selectionStart != null && selectionStart.equals(selectionEnd)) clearSelection();
    }
    //endregion

    //region Selection
    private void clearSelection() {
        selectionStart = selectionEnd = null;
        dragging = false;
    }
    private void selectAll() {
        if (log.isEmpty()) return;
        selectionStart = new Point(log.peekFirst().seq(), 0);
        assert log.peekLast() != null;
        selectionEnd = new Point(log.peekLast().seq(), log.peekLast().text().length());
    }
    private String selectedText() {
        if (selectionStart == null || selectionEnd == null) return "";
        Point from = selectionStart, point = selectionEnd;
        if (from.seq() > point.seq() || (from.seq() == point.seq() && from.index() > point.index())) { Point t = from; from = point; point = t; }
        StringBuilder out = new StringBuilder();
        for (LogLine line : log) {
            if (line.seq() < from.seq() || line.seq() > point.seq()) continue;
            int a = line.seq() == from.seq() ? from.index() : 0;
            int b = line.seq() == point.seq() ? point.index() : line.text().length();
            a = Math.clamp(a, 0, line.text().length());
            b = Math.clamp(b, line.text().length(), a);
            if (!out.isEmpty()) out.append('\n');
            out.append(line.text(), a, b);
        }
        return out.toString();
    }
    private void copySelection() {
        String text = selectedText();
        if (text.isEmpty()) return;
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (IllegalStateException ignored) { log(Level.WARN, "clipboard is busy, try again"); }
    }
    private void pasteIntoInput() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return;
            String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
            StringBuilder clean = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (c == '\n' || c == '\r') break;
                if (!Character.isISOControl(c) && !isToggleChar(c)) clean.append(c);
            }
            if (input.length() + clean.length() <= 200) input += clean;
        } catch (Exception ignored) { log(Level.WARN, "clipboard is not available"); }
    }
    private Point pointAt(int x, int y) {
        if (rows.isEmpty()) return null;
        int row = firstVisible + Math.floorDiv(y - PADDING, LINE_HEIGHT);
        row = Math.max(firstVisible, Math.min(row, firstVisible + visibleCount - 1));
        if (row < 0 || row >= rows.size()) return null;
        Row r = rows.get(row);
        int column = Math.round((x - PADDING) / (float) charWidth);
        column = Math.clamp(column, 0, r.text().length());
        return new Point(r.seq(), r.start() + column);
    }
    private void drawSelectionFor(Graphics2D g2d, Row row, int top) {
        if (selectionStart == null || selectionEnd == null) return;
        Point from = selectionStart, point = selectionEnd;
        if (from.seq() > point.seq() || (from.seq() == point.seq() && from.index() > point.index())) { Point t = from; from = point; point = t; }
        if (row.seq() < from.seq() || row.seq() > point.seq()) return;
        int a = row.seq() == from.seq() ? from.index() : 0;
        int b = row.seq() == point.seq() ? point.index() : Integer.MAX_VALUE;
        int from0 = Math.max(row.start(), a), to0 = Math.min(row.end(), b);
        if (from0 >= to0) return;
        g2d.setColor(SELECTION);
        g2d.fillRect(PADDING + (from0 - row.start()) * charWidth, top, (to0 - from0) * charWidth, LINE_HEIGHT);
    }
    private record Point(long seq, int index) {

    }
    //endregion

    //region Drawing
    public synchronized void draw(Graphics2D g2d, int width, int height) {
        Font oldFont = g2d.getFont();
        g2d.setFont(FONT);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        charWidth = Math.max(1, fontMetrics.charWidth('0'));
        panelHeight = (int) (height * 0.45);
        int textWidth = Math.max(1, (width - 2 * PADDING) / charWidth);

        g2d.setColor(BACKGROUND);
        g2d.fillRect(0, 0, width, panelHeight);
        g2d.setColor(BORDER);
        g2d.fillRect(0, panelHeight - 2, width, 2);

        rows = layout(textWidth);
        int inputY = panelHeight - PADDING - 4;
        visibleCount = Math.max(1, (inputY - PADDING - LINE_HEIGHT) / LINE_HEIGHT + 1);
        int maxScroll = Math.max(0, rows.size() - visibleCount);
        scrollRows = Math.min(scrollRows, maxScroll);
        int end = rows.size() - scrollRows;
        firstVisible = Math.max(0, end - visibleCount);

        int y = PADDING + LINE_HEIGHT;
        for (int i = firstVisible; i < end; i++) {
            Row row = rows.get(i);
            drawSelectionFor(g2d, row, y - fontMetrics.getAscent() + 2);
            g2d.setColor(colorOf(row.level()));
            g2d.drawString(row.text(), PADDING, y);
            y += LINE_HEIGHT;
        }
        if (scrollRows > 0) {
            g2d.setColor(BORDER);
            g2d.drawString("^ " + scrollRows + " more", width - PADDING - 12 * charWidth, PADDING + LINE_HEIGHT);
        }
        boolean cursorOn = (System.currentTimeMillis() - cursorStart) / 500 % 2 == 0;
        g2d.setColor(INPUT_TEXT);
        g2d.drawString("> " + input + (cursorOn ? "_" : " "), PADDING, inputY);
        g2d.setFont(oldFont);
    }
    private List<Row> layout(int textWidth) {
        List<Row> out = new ArrayList<>();
        for (LogLine line : log) {
            String text = line.text();
            if (text.isEmpty()) { out.add(new Row(line.seq(), 0, 0, line.level(), "")); continue; }
            for (int start = 0; start < text.length(); start += textWidth) {
                int end = Math.min(text.length(), start + textWidth);
                out.add(new Row(line.seq(), start, end, line.level(), text.substring(start, end)));
            }
        }
        return out;
    }
    private static Color colorOf(Level level) {
        return switch (level) {
            case COMMAND -> new Color(150, 220, 255);
            case WARN -> new Color(241, 196, 15);
            case ERROR -> new Color(231, 76, 60);
            case INFO -> new Color(210, 210, 210);
        };
    }
    private record Row(long seq, int start, int end, Level level, String text) {

    }
    //endregion
}