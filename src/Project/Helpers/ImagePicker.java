package Project.Helpers;

import Project.StyleUI.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static Project.Helpers.EditorKit.*;

public final class ImagePicker {
    private ImagePicker() { }

    public static File choose(Component parent, Style style) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), L("ip.title"), Dialog.ModalityType.APPLICATION_MODAL);
        PickerPanel panel = new PickerPanel(style, dialog::dispose);
        dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(900, 560));
        dialog.setSize(1100, 720);
        dialog.setLocationRelativeTo(parent);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { panel.shutdown(); }
        });
        dialog.setVisible(true);
        return panel.result;
    }

    //region Settings Keys
    private static final String LAST_DIR = "ip.lastDir", RECENT = "ip.recent", CELL = "ip.cell", SORT = "ip.sort", SHOW_ALL = "ip.showAll";
    //endregion

    private record Entry(File file, boolean dir) { @Override public String toString() { return file.getName().isEmpty() ? file.getPath() : file.getName(); } }
    private record Place(String label, File dir) { }
    private record Details(int width, int height, String format) { }

    static final class PickerPanel extends JPanel {
        //region Palette
        private static final Color PANEL = new Color(24, 24, 28), FIELD = new Color(20, 20, 24);
        private static final Color CHECK_A = new Color(46, 46, 52), CHECK_B = new Color(38, 38, 43);
        //endregion

        //region State
        File result;
        private final Style style;
        private final Runnable close;
        private File current;
        private final Deque<File> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
        private List<Entry> listing = List.of();
        private final DefaultListModel<Entry> model = new DefaultListModel<>();
        private final JList<Entry> grid = new JList<>(model);
        private final StyledTextField pathField, nameField;
        private final SearchField searchField;
        private final StyledToggle showAll;
        private final StyledComboBox sortBox;
        private final StyledSlider cellSlider;
        private final StyledLabel status;
        private final JList<Place> places = new JList<>();
        private final DefaultListModel<Place> placeModel = new DefaultListModel<>();
        private final Preview preview = new Preview();
        private final StyledButton backButton, forwardButton, upButton, openButton, cancelButton;
        private final javax.swing.Timer searchDebounce;
        private final AtomicInteger generation = new AtomicInteger();
        private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "image-picker");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, BufferedImage> thumbs = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) { return size() > 400; }
        });
        private final Map<String, Details> details = new java.util.concurrent.ConcurrentHashMap<>();
        private final Set<String> loading = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final AtomicInteger reloadGeneration = new AtomicInteger();
        //endregion

        //region Construction
        PickerPanel(Style style, Runnable close) {
            super(new BorderLayout(0, 0));
            this.style = style;
            this.close = close;
            setBackground(BG);

            backButton = button("←", 42, 38);
            forwardButton = button("→", 42, 38);
            upButton = button("↑", 42, 38);
            backButton.setToolTipText(L("ip.back") + "   [Alt+←]");
            forwardButton.setToolTipText(L("ip.forward") + "   [Alt+→]");
            upButton.setToolTipText(L("ip.up") + "   [Alt+↑]");
            openButton = button("ip.open", 130, 40);
            cancelButton = button("ip.cancel", 120, 40);
            backButton.addActionListener(_ -> goBack());
            forwardButton.addActionListener(_ -> goForward());
            upButton.addActionListener(_ -> goUp());
            openButton.addActionListener(_ -> acceptSelection());
            cancelButton.addActionListener(_ -> dispose());

            pathField = field(0, 38);
            nameField = field(0, 38);
            searchField = new SearchField(style, L("ip.search"));
            searchField.setPreferredSize(new Dimension(300, 38));
            pathField.setToolTipText(L("ip.path") + "   [Ctrl+L]");

            showAll = toggle("ip.show_all", 140);
            showAll.setSelected(Settings.getBoolean(SHOW_ALL, false));

            sortBox = new StyledComboBox(style, new Object[]{L("ip.sort.name"), L("ip.sort.date"), L("ip.sort.size"), L("ip.sort.type")});
            sortBox.setPreferredSize(new Dimension(170, 38));
            sortBox.setSelectedIndex(Math.clamp(Settings.getInt(SORT, 0), 0, 3));
            sortBox.addActionListener(_ -> { Settings.set(SORT, String.valueOf(sortBox.getSelectedIndex())); applyFilter(); });

            cellSlider = new StyledSlider(style, 80, 220, Settings.getInt(CELL, 128));
            cellSlider.setPreferredSize(new Dimension(170, 38));
            cellSlider.addChangeListener(_ -> {
                int size = cellSlider.getValue();
                grid.setFixedCellWidth(size + 16);
                grid.setFixedCellHeight(size + 44);
                if (!cellSlider.getValueIsAdjusting()) Settings.set(CELL, String.valueOf(size));
                grid.repaint();
            });

            status = label(" ", 12f);
            status.setPreferredSize(240, 30);
            searchDebounce = new javax.swing.Timer(140, _ -> applyFilter());
            searchDebounce.setRepeats(false);

            add(buildToolbar(), BorderLayout.NORTH);
            add(buildBody(), BorderLayout.CENTER);
            add(buildFooter(), BorderLayout.SOUTH);

            wireGrid();
            wirePlaces();
            wireKeys();
            wireDrop(this);

            File start = new File(Settings.getString(LAST_DIR, System.getProperty("user.home")));
            if (!start.isDirectory()) start = new File(System.getProperty("user.home"));
            fillPlaces();
            navigate(start, false);
            SwingUtilities.invokeLater(grid::requestFocusInWindow);
        }

        void dispose() { close.run(); }
        void shutdown() { generation.incrementAndGet(); workers.shutdownNow(); }

        private StyledButton button(String keyOrText, int w, int h) {
            StyledButton button = new StyledButton(style, keyOrText);
            button.setPreferredSize(new Dimension(w, h));
            return button;
        }
        private StyledTextField field(int w, int h) {
            StyledTextField field = new StyledTextField(style, "");
            field.setPreferredSize(new Dimension(w, h));
            return field;
        }
        private StyledToggle toggle(String key, int w) {
            StyledToggle toggle = new StyledToggle(style, key);
            toggle.setPreferredSize(new Dimension(w, 36));
            return toggle;
        }
        private StyledLabel label(String text, float size) {
            StyledLabel label = new StyledLabel(style, text, false, false);
            label.setTextColor(EditorKit.MUTED);
            label.setTextSize(size);
            FontMetrics metrics = new JLabel().getFontMetrics(font(size, false));
            label.setPreferredSize(metrics.stringWidth(text) + 14, 30);
            return label;
        }
        private static final class SearchField extends StyledTextField {
            private final String hint;
            SearchField(Style style, String hint) { super(style, ""); this.hint = hint; }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(MUTED);
                g2.setFont(getFont());
                FontMetrics m = g2.getFontMetrics();
                g2.drawString(hint, getInsets().left, (getHeight() + m.getAscent() - m.getDescent()) / 2);
                g2.dispose();
            }
        }
        //endregion

        //region Layout
        private JComponent buildToolbar() {
            JPanel bar = new JPanel(new BorderLayout(10, 0));
            bar.setBackground(CARD);
            bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_LINE), new EmptyBorder(10, 12, 10, 12)));
            JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            nav.setOpaque(false);
            nav.add(backButton);
            nav.add(forwardButton);
            nav.add(upButton);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(searchField);
            bar.add(nav, BorderLayout.WEST);
            bar.add(pathField, BorderLayout.CENTER);
            bar.add(right, BorderLayout.EAST);
            return bar;
        }

        private JComponent buildBody() {
            places.setModel(placeModel);
            places.setBackground(PANEL);
            places.setCellRenderer(new PlaceRenderer());
            places.setFixedCellHeight(32);
            places.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            StyledScrollPane placeScroll = new StyledScrollPane(places, style);
            placeScroll.setPreferredSize(new Dimension(190, 100));

            grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
            grid.setVisibleRowCount(-1);
            grid.setBackground(CANVAS_BG);
            grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            grid.setCellRenderer(new CellRenderer());
            grid.setFixedCellWidth(cellSlider.getValue() + 16);
            grid.setFixedCellHeight(cellSlider.getValue() + 44);
            StyledScrollPane gridScroll = new StyledScrollPane(grid, style);
            gridScroll.getVerticalScrollBar().setUnitIncrement(32);

            preview.setPreferredSize(new Dimension(320, 100));

            JPanel body = new JPanel(new BorderLayout(0, 0));
            body.setBackground(BG);
            JPanel left = new JPanel(new BorderLayout());
            left.setBackground(BG);
            left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, CARD_LINE));
            left.add(placeScroll);
            body.add(left, BorderLayout.WEST);
            body.add(gridScroll, BorderLayout.CENTER);
            body.add(preview, BorderLayout.EAST);
            return body;
        }
        private JComponent buildFooter() {
            JPanel footer = new JPanel();
            footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
            footer.setBackground(CARD);
            footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_LINE), new EmptyBorder(10, 12, 12, 12)));

            JPanel options = new JPanel(new BorderLayout(12, 0));
            options.setOpaque(false);
            JPanel optionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            optionControls.setOpaque(false);
            optionControls.add(label(L("ip.sort"), 13f));
            optionControls.add(sortBox);
            optionControls.add(label(L("ip.thumb"), 13f));
            optionControls.add(cellSlider);
            optionControls.add(showAll);
            options.add(optionControls, BorderLayout.WEST);
            JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
            statusBox.setOpaque(false);
            statusBox.add(status);
            options.add(statusBox, BorderLayout.CENTER);

            JPanel actions = new JPanel(new BorderLayout(10, 0));
            actions.setOpaque(false);
            actions.setBorder(new EmptyBorder(10, 0, 0, 0));
            JPanel fileLabelBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
            fileLabelBox.setOpaque(false);
            fileLabelBox.add(label(L("ip.filename"), 13f));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            buttons.setOpaque(false);
            buttons.add(openButton);
            buttons.add(cancelButton);
            actions.add(fileLabelBox, BorderLayout.WEST);
            actions.add(nameField, BorderLayout.CENTER);
            actions.add(buttons, BorderLayout.EAST);

            options.setAlignmentX(Component.LEFT_ALIGNMENT);
            actions.setAlignmentX(Component.LEFT_ALIGNMENT);
            footer.add(options);
            footer.add(actions);
            return footer;
        }
        //endregion

        //region Wiring
        private void wireGrid() {
            grid.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) selectionChanged(); });
            grid.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                        int index = grid.locationToIndex(event.getPoint());
                        if (index >= 0 && grid.getCellBounds(index, index).contains(event.getPoint())) acceptSelection();
                    }
                }
            });
            grid.addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    int index = grid.locationToIndex(event.getPoint());
                    boolean over = index >= 0 && grid.getCellBounds(index, index).contains(event.getPoint());
                    grid.setToolTipText(over ? model.get(index).file().getAbsolutePath() : null);
                }
            });
            nameField.addActionListener(_ -> {
                String text = nameField.getText().trim();
                if (text.isEmpty()) return;
                File typed = new File(text);
                if (!typed.isAbsolute()) typed = new File(current, text);
                if (typed.isDirectory()) navigate(typed, true);
                else if (typed.isFile()) { result = typed; remember(); dispose(); }
                else Toolkit.getDefaultToolkit().beep();
            });
            pathField.addActionListener(_ -> {
                File typed = new File(pathField.getText().trim());
                if (typed.isDirectory()) navigate(typed, true);
                else if (typed.isFile()) { navigate(typed.getParentFile(), true); selectFile(typed); }
                else { pathField.setText(current.getAbsolutePath()); Toolkit.getDefaultToolkit().beep(); }
            });
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { searchDebounce.restart(); }
                @Override public void removeUpdate(DocumentEvent event) { searchDebounce.restart(); }
                @Override public void changedUpdate(DocumentEvent event) { searchDebounce.restart(); }
            });
            showAll.addActionListener(_ -> { Settings.set(SHOW_ALL, String.valueOf(showAll.isSelected())); reload(); });
        }

        private void wirePlaces() {
            places.addListSelectionListener(event -> {
                if (event.getValueIsAdjusting()) return;
                Place place = places.getSelectedValue();
                if (place != null && !place.dir().equals(current)) navigate(place.dir(), true);
            });
        }

        private void wireKeys() {
            JComponent root = this;
            bind(root, "ESCAPE", this::dispose);
            bind(root, "alt LEFT", this::goBack);
            bind(root, "alt RIGHT", this::goForward);
            bind(root, "alt UP", this::goUp);
            bind(root, "F5", this::reload);
            bind(root, "control F", () -> { searchField.requestFocusInWindow(); searchField.selectAll(); });
            bind(root, "control L", () -> { pathField.requestFocusInWindow(); pathField.selectAll(); });
            grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open");
            grid.getActionMap().put("open", action(this::acceptSelection));
            grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("BACK_SPACE"), "up");
            grid.getActionMap().put("up", action(this::goUp));
        }
        private static void bind(JComponent root, String stroke, Runnable run) {
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), stroke);
            root.getActionMap().put(stroke, action(run));
        }
        private static Action action(Runnable run) { return new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { run.run(); } }; }

        private void wireDrop(JComponent target) {
            target.setTransferHandler(new TransferHandler() {
                @Override public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
                @Override @SuppressWarnings("unchecked") public boolean importData(TransferSupport support) {
                    try {
                        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (files.isEmpty()) return false;
                        File dropped = files.getFirst();
                        if (dropped.isDirectory()) { navigate(dropped, true); return true; }
                        result = dropped;
                        remember();
                        dispose();
                        return true;
                    } catch (Exception exception) { Log.debug(exception); return false; }
                }
            });
        }
        //endregion

        //region Places
        private void fillPlaces() {
            placeModel.clear();
            String home = System.getProperty("user.home");
            add(placeModel, L("ip.place.home"), new File(home));
            add(placeModel, L("ip.place.desktop"), new File(home, "Desktop"));
            add(placeModel, L("ip.place.documents"), new File(home, "Documents"));
            add(placeModel, L("ip.place.pictures"), new File(home, "Pictures"));
            add(placeModel, L("ip.place.downloads"), new File(home, "Downloads"));
            File[] roots = File.listRoots();
            if (roots != null) for (File root : roots) add(placeModel, root.getPath(), root);
            for (String path : Settings.getString(RECENT, "").split("\\|")) {
                if (path.isBlank()) continue;
                File dir = new File(path);
                if (dir.isDirectory()) add(placeModel, "⟲ " + (dir.getName().isEmpty() ? dir.getPath() : dir.getName()), dir);
            }
        }
        private static void add(DefaultListModel<Place> list, String label, File dir) { if (dir.isDirectory()) list.addElement(new Place(label, dir)); }
        private void remember() {
            if (current == null) return;
            Settings.set(LAST_DIR, current.getAbsolutePath());
            LinkedHashSet<String> recent = new LinkedHashSet<>();
            recent.add(current.getAbsolutePath());
            for (String path : Settings.getString(RECENT, "").split("\\|")) if (!path.isBlank()) recent.add(path);
            StringBuilder joined = new StringBuilder();
            int count = 0;
            for (String path : recent) { if (count++ >= 6) break; if (!joined.isEmpty()) joined.append('|'); joined.append(path); }
            Settings.set(RECENT, joined.toString());
        }
        //endregion

        //region Navigation
        private void navigate(File dir, boolean recordHistory) {
            if (dir == null || !dir.isDirectory()) return;
            if (recordHistory && current != null && !current.equals(dir)) { back.push(current); forward.clear(); }
            current = dir.getAbsoluteFile();
            pathField.setText(current.getPath());
            searchField.setText("");
            reload();
            updateNavButtons();
            syncPlaceSelection();
        }
        private void goBack() { if (back.isEmpty()) return; forward.push(current); File dir = back.pop(); current = null; navigateNoHistory(dir); }
        private void goForward() { if (forward.isEmpty()) return; back.push(current); File dir = forward.pop(); current = null; navigateNoHistory(dir); }
        private void navigateNoHistory(File dir) { navigate(dir, false); }
        private void goUp() {
            File parent = current == null ? null : current.getParentFile();
            if (parent != null) navigate(parent, true);
        }
        private void updateNavButtons() { backButton.setEnabled(!back.isEmpty()); forwardButton.setEnabled(!forward.isEmpty()); upButton.setEnabled(current != null && current.getParentFile() != null); }
        private void syncPlaceSelection() {
            for (int i = 0; i < placeModel.size(); i++) if (placeModel.get(i).dir().equals(current)) { places.setSelectedIndex(i); return; }
            places.clearSelection();
        }

        private void reload() {
            if (current == null) return;
            final int token = reloadGeneration.incrementAndGet();
            final File folder = current;
            final boolean all = showAll.isSelected();
            status.setText(L("ip.loading"));
            new SwingWorker<List<Entry>, Void>() {
                @Override protected List<Entry> doInBackground() {
                    List<Entry> found = new ArrayList<>();
                    File[] children = folder.listFiles();
                    if (children == null) return found;
                    for (File child : children) {
                        if (child.isHidden() || child.getName().startsWith(".")) continue;
                        if (child.isDirectory()) found.add(new Entry(child, true));
                        else if (all || ImageLoader.hasImageExtension(child)) found.add(new Entry(child, false));
                    }
                    return found;
                }
                @Override protected void done() {
                    if (token != reloadGeneration.get()) return;
                    try { listing = get(); }
                    catch (Exception exception) { listing = List.of(); Log.debug(exception); }
                    applyFilter();
                }
            }.execute();
        }
        //endregion

        //region Filtering, Sorting, Searching
        private void applyFilter() {
            String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
            List<Entry> shown = new ArrayList<>();
            String[] words = query.isEmpty() ? new String[0] : query.split("\\s+");
            for (Entry entry : listing) if (matches(entry.file().getName(), words)) shown.add(entry);
            show(shown, query.isEmpty() ? null : L("ip.no_match"));
        }
        private static boolean matches(String name, String[] words) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String word : words) if (!lower.contains(word)) return false;
            return true;
        }
        private void show(List<Entry> entries, String emptyMessage) {
            List<Entry> sorted = new ArrayList<>(entries);
            sorted.sort(comparator());
            File selected = selectedFile();
            model.clear();
            for (Entry entry : sorted) model.addElement(entry);
            int images = 0, folders = 0;
            for (Entry entry : sorted) { if (entry.dir()) folders++; else images++; }
            if (sorted.isEmpty()) status.setText(emptyMessage != null ? emptyMessage : L("ip.empty"));
            else status.setText(String.format(L("ip.status"), images, folders));
            if (selected != null) selectFile(selected);
            else { grid.clearSelection(); selectionChanged(); }
            grid.repaint();
        }
        private Comparator<Entry> comparator() {
            Comparator<String> natural = String.CASE_INSENSITIVE_ORDER;
            Comparator<Entry> byName = Comparator.comparing(entry -> entry.file().getName(), natural);
            Comparator<Entry> chosen = switch (sortBox.getSelectedIndex()) {
                case 1 -> Comparator.comparingLong((Entry entry) -> entry.file().lastModified()).reversed().thenComparing(byName);
                case 2 -> Comparator.comparingLong((Entry entry) -> entry.file().length()).reversed().thenComparing(byName);
                case 3 -> Comparator.comparing((Entry entry) -> extension(entry.file()), natural).thenComparing(byName);
                default -> byName;
            };
            return Comparator.comparing((Entry entry) -> !entry.dir()).thenComparing(chosen);
        }
        private static String extension(File file) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "" : name.substring(dot + 1);
        }
        //endregion

        //region Selection
        private File selectedFile() { Entry entry = grid.getSelectedValue(); return entry == null ? null : entry.file(); }
        private void selectFile(File file) {
            for (int i = 0; i < model.size(); i++) if (model.get(i).file().equals(file)) { grid.setSelectedIndex(i); grid.ensureIndexIsVisible(i); return; }
        }
        private void selectionChanged() {
            Entry entry = grid.getSelectedValue();
            if (entry == null) { nameField.setText(""); preview.clear(); return; }
            nameField.setText(entry.file().getName());
            if (entry.dir()) preview.showFolder(entry.file());
            else preview.show(entry.file());
        }
        private void acceptSelection() {
            Entry entry = grid.getSelectedValue();
            if (entry == null) { Toolkit.getDefaultToolkit().beep(); return; }
            if (entry.dir()) { navigate(entry.file(), true); return; }
            result = entry.file();
            remember();
            dispose();
        }
        //endregion

        //region Thumbnails
        private static final int THUMB_MAX = 224;
        private BufferedImage thumbnail(Entry entry) {
            File file = entry.file();
            String key = file.getPath() + "|" + file.lastModified() + "|" + file.length();
            BufferedImage cached = thumbs.get(key);
            if (cached != null) return cached;
            if (loading.add(key)) {
                try {
                    workers.execute(() -> {
                        try {
                            if (file.length() > 96L * 1024 * 1024) return;
                            BufferedImage source = ImageLoader.read(file);
                            details.put(file.getPath() + "|" + file.lastModified(), new Details(source.getWidth(), source.getHeight(), ImageLoader.formatName(file)));
                            thumbs.put(key, downscale(source, THUMB_MAX));
                        } catch (IOException | RuntimeException | OutOfMemoryError exception) {
                            thumbs.put(key, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));   // unreadable: remember it, do not retry every repaint
                        } finally {
                            loading.remove(key);
                            SwingUtilities.invokeLater(grid::repaint);
                        }
                    });
                } catch (RuntimeException rejected) { loading.remove(key); }
            }
            return null;
        }
        //endregion

        //region Cell Renderer
        private final class CellRenderer extends JComponent implements ListCellRenderer<Entry> {
            private Entry entry;
            private boolean selected;
            private int size;

            @Override public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value, int index, boolean isSelected, boolean focus) {
                entry = value;
                selected = isSelected;
                size = cellSlider.getValue();
                return this;
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    g.setColor(selected ? alpha(HILITE, 70) : CANVAS_BG);
                    g.fillRoundRect(4, 4, w - 8, h - 8, 12, 12);
                    if (selected) { g.setColor(HILITE); g.setStroke(new BasicStroke(1.6f)); g.drawRoundRect(4, 4, w - 9, h - 9, 12, 12); }
                    int box = size;
                    int ix = (w - box) / 2, iy = 10;
                    if (entry.dir()) {
                        Glyph.FOLDER.paint(g, ix + box * 0.18, iy + box * 0.1, box * 0.64, selected ? HILITE : MUTED);
                    } else {
                        paintChecker(g, ix, iy, box, box);
                        BufferedImage thumb = thumbnail(entry);
                        if (thumb == null) {
                            g.setColor(MUTED);
                            g.setFont(font(12f, false));
                            String loadingText = "…";
                            g.drawString(loadingText, ix + (box - g.getFontMetrics().stringWidth(loadingText)) / 2, iy + box / 2);
                        } else if (thumb.getWidth() > 1) {
                            double scale = Math.min((double) box / thumb.getWidth(), (double) box / thumb.getHeight());
                            if (scale >= 1) scale = Math.max(1, Math.floor(scale));   // small pictures: whole-number zoom keeps pixel art crisp
                            int dw = Math.max(1, (int) Math.round(thumb.getWidth() * scale)), dh = Math.max(1, (int) Math.round(thumb.getHeight() * scale));
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g.drawImage(thumb, ix + (box - dw) / 2, iy + (box - dh) / 2, dw, dh, null);
                        } else {
                            g.setColor(BAD);
                            g.setFont(font(20f, true));
                            g.drawString("!", ix + box / 2 - 4, iy + box / 2 + 7);
                        }
                        String ext = extension(entry.file()).toUpperCase(Locale.ROOT);
                        if (!ext.isEmpty() && box >= 96) {
                            g.setFont(font(10f, true));
                            FontMetrics m = g.getFontMetrics();
                            int tw = m.stringWidth(ext) + 8;
                            g.setColor(new Color(0, 0, 0, 160));
                            g.fillRoundRect(ix + box - tw - 4, iy + box - 18, tw, 14, 6, 6);
                            g.setColor(TEXT);
                            g.drawString(ext, ix + box - tw, iy + box - 7);
                        }
                    }
                    g.setFont(font(12f, false));
                    g.setColor(TEXT);
                    FontMetrics fm = g.getFontMetrics();
                    String name = ellipsize(entry.toString(), fm, w - 20);
                    g.drawString(name, (w - fm.stringWidth(name)) / 2, iy + box + 20);
                } finally { g.dispose(); }
            }
        }
        private static void paintChecker(Graphics2D g, int x, int y, int w, int h) {
            Shape clip = g.getClip();
            g.clipRect(x, y, w, h);
            int tile = 8;
            for (int cy = 0; cy * tile < h; cy++)
                for (int cx = 0; cx * tile < w; cx++) {
                    g.setColor(((cx + cy) & 1) == 0 ? CHECK_A : CHECK_B);
                    g.fillRect(x + cx * tile, y + cy * tile, tile, tile);
                }
            g.setClip(clip);
        }

        private static final class PlaceRenderer extends JComponent implements ListCellRenderer<Place> {
            private Place place;
            private boolean selected;
            @Override public Component getListCellRendererComponent(JList<? extends Place> list, Place value, int index, boolean isSelected, boolean focus) {
                place = value;
                selected = isSelected;
                return this;
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(selected ? alpha(HILITE, 60) : PANEL);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (selected) { g.setColor(HILITE); g.fillRect(0, 4, 3, getHeight() - 8); }
                    Glyph.FOLDER.paint(g, 12, (getHeight() - 16) / 2.0, 16, selected ? HILITE : MUTED);
                    g.setFont(font(13f, selected));
                    g.setColor(selected ? TEXT : new Color(210, 210, 220));
                    g.drawString(ellipsize(place.label(), g.getFontMetrics(), getWidth() - 44), 36, getHeight() / 2 + 5);
                } finally { g.dispose(); }
            }
        }
        //endregion

        //region Preview Pane
        private final class Preview extends JComponent {
            private File file;
            private boolean folder;
            private BufferedImage image;
            private String error;
            private Details info;
            private int token;

            Preview() { setBackground(CARD); setOpaque(true); }

            void clear() { file = null; folder = false; image = null; error = null; info = null; token++; repaint(); }
            void showFolder(File dir) { clear(); file = dir; folder = true; repaint(); }
            void show(File selected) {
                clear();
                file = selected;
                final int mine = token;
                try { workers.execute(() -> {
                    BufferedImage loaded = null;
                    String problem = null;
                    try { loaded = ImageLoader.read(selected); }
                    catch (IOException | RuntimeException | OutOfMemoryError exception) { problem = String.valueOf(exception.getMessage()); }
                    final BufferedImage finalImage = loaded;
                    final String finalProblem = problem;
                    SwingUtilities.invokeLater(() -> {
                        if (mine != token) return;
                        image = finalImage;
                        error = finalProblem;
                        if (finalImage != null) info = new Details(finalImage.getWidth(), finalImage.getHeight(), ImageLoader.formatName(selected));
                        repaint();
                    });
                }); }
                catch (RuntimeException rejected) { Log.debug(rejected); }   // the dialog is already closing
            }

            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    g.setColor(CARD);
                    g.fillRect(0, 0, w, h);
                    g.setColor(CARD_LINE);
                    g.drawLine(0, 0, 0, h);
                    int pad = 14, box = w - 2 * pad, boxH = Math.clamp(h - 220, 120, box);
                    g.setColor(CANVAS_BG);
                    g.fillRoundRect(pad, pad, box, boxH, 12, 12);
                    if (file == null) {
                        drawCentered(g, L("ip.no_selection"), pad, pad, box, boxH, MUTED);
                        return;
                    }
                    if (folder) { Glyph.FOLDER.paint(g, pad + box / 2.0 - 36, pad + boxH / 2.0 - 36, 72, MUTED); }
                    else if (image != null) { paintPreviewImage(g, pad, pad, box, boxH); }
                    else if (error != null) { drawCentered(g, L("ip.cannot_open"), pad, pad, box, boxH, BAD); }
                    else { drawCentered(g, L("ip.loading"), pad, pad, box, boxH, MUTED); }
                    int y = pad + boxH + 24;
                    g.setFont(font(14f, true));
                    g.setColor(TEXT);
                    FontMetrics nameMetrics = g.getFontMetrics();
                    for (String line : wrap(file.getName(), nameMetrics, box)) {
                        if (y > h - 10) break;
                        g.drawString(line, pad, y);
                        y += 19;
                    }
                    y += 4;
                    g.setFont(font(12f, false));
                    if (folder) { y = line(g, y, pad, L("ip.info.type"), L("ip.folder")); }
                    else {
                        if (info != null) {
                            y = line(g, y, pad, L("ip.info.format"), info.format());
                            y = line(g, y, pad, L("ip.info.dimensions"), info.width() + " × " + info.height());
                        } else if (error != null) {
                            g.setColor(BAD);
                            for (String line : wrap(error, g.getFontMetrics(), box)) { g.drawString(line, pad, y); y += 16; }
                        }
                        y = line(g, y, pad, L("ip.info.size"), humanSize(file.length()));
                    }
                    line(g, y, pad, L("ip.info.modified"), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(file.lastModified())));
                } finally { g.dispose(); }
            }
            private int line(Graphics2D g, int y, int x, String label, String value) {
                g.setColor(MUTED);
                g.drawString(label, x, y);
                g.setColor(TEXT);
                int labelWidth = 96;
                g.drawString(ellipsize(value, g.getFontMetrics(), getWidth() - 2 * x - labelWidth), x + labelWidth, y);
                return y + 18;
            }
            private void paintPreviewImage(Graphics2D g, int x, int y, int w, int h) {
                Shape clip = g.getClip();
                g.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, 12, 12));
                paintChecker(g, x, y, w, h);
                double scale = Math.min((w - 12.0) / image.getWidth(), (h - 12.0) / image.getHeight());
                if (scale >= 1) scale = Math.max(1, Math.floor(scale));   // small pixel art: whole-number zoom, no blur
                int dw = Math.max(1, (int) Math.round(image.getWidth() * scale)), dh = Math.max(1, (int) Math.round(image.getHeight() * scale));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
                g.setClip(clip);
            }
            private void drawCentered(Graphics2D g, String text, int x, int y, int w, int h, Color color) {
                g.setFont(font(13f, false));
                g.setColor(color);
                FontMetrics m = g.getFontMetrics();
                List<String> lines = wrap(text, m, w - 24);
                int top = y + (h - lines.size() * 18) / 2 + 12;
                for (String line : lines) { g.drawString(line, x + (w - m.stringWidth(line)) / 2, top); top += 18; }
            }
        }
        //endregion

        //region Helpers
        private static String humanSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024L * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        private static BufferedImage downscale(BufferedImage source, int max) {
            double scale = Math.min(1.0, Math.min((double) max / source.getWidth(), (double) max / source.getHeight()));
            if (scale >= 1.0) return source;
            int w = Math.max(1, (int) Math.round(source.getWidth() * scale)), h = Math.max(1, (int) Math.round(source.getHeight() * scale));
            Image scaled = source.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING);
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            return out;
        }
        //endregion
    }
}