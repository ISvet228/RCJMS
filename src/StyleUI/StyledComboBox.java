package StyleUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StyledComboBox extends AnimatedComponent {
    public static class ItemStyle {
        private Color normalColor, hoverColor, selectedColor, textColor, borderColor;

        private int arc = 12;
        private int horizontalPadding = 9;
        private int verticalPadding = 4;

        private Font font;
        private boolean glass;
        private float glassOpacity = 0.45f, glassRadius = 0.5f;

        public ItemStyle() {
            normalColor = new Color(45, 47, 50);
            hoverColor = new Color(61, 63, 66);
            selectedColor = new Color(72, 99, 151);
            textColor = Color.WHITE;
            borderColor = new Color(82, 84, 87);
            font = new Font("SansSerif", Font.BOLD, 14);
        }

        public Color getNormalColor() { return normalColor; }
        public void setNormalColor(Color color) { if (color != null) normalColor = color; }

        public Color getHoverColor() {return hoverColor; }
        public void setHoverColor(Color color) { if (color != null) hoverColor = color; }

        public Color getSelectedColor() { return selectedColor; }
        public void setSelectedColor(Color color) { if (color != null) selectedColor = color; }

        public Color getTextColor() { return textColor; }
        public void setTextColor(Color color) { if (color != null) textColor = color;}

        public Color getBorderColor() { return borderColor; }
        public void setBorderColor(Color color) { if (color != null) borderColor = color; }

        public int getArc() { return arc; }
        public void setArc(int arc) { this.arc = Math.max(0, arc);}

        public int getHorizontalPadding() { return horizontalPadding; }
        public void setHorizontalPadding(int padding) { horizontalPadding = Math.max(0, padding); }

        public int getVerticalPadding() { return verticalPadding; }
        public void setVerticalPadding(int padding) { verticalPadding = Math.max(0, padding); }

        public Font getFont() { return font; }
        public void setFont(Font font) { if (font != null) this.font = font; }

        public boolean isGlass() { return glass; }
        public void setGlass(boolean glass) { this.glass = glass; }

        public float getGlassOpacity() { return glassOpacity; }
        public void setGlassOpacity(float opacity) { glassOpacity = Math.clamp(opacity, 0f, 1f); }

        public float getGlassRadius() { return glassRadius; }
        public void setGlassRadius(float radius) { glassRadius = Math.max(0f, radius); }
    }

    private Style style;

    private final List<Object> items = new ArrayList<>();
    private final List<ActionListener> listeners = new ArrayList<>();

    private final ItemStyle itemStyle = new ItemStyle(), popupStyle = new ItemStyle();

    private int selectedIndex = -1;
    private boolean popupVisible, editable;
    private boolean enabled = true, lightWeightPopup = true;

    private int maximumRowCount = 8;

    private Color backgroundColor, hoverColor, selectedColor, textColor, borderColor, arrowColor, popupBackgroundColor, popupBorderColor;

    private int arc = 12, popupArc = 14;
    private int horizontalPadding = 9, verticalPadding = 4;
    private int popupPadding = 4, itemSpacing = 2, itemHeight = 34;

    private Font font, popupFont;

    private boolean glass;
    private float glassOpacity = 0.45f;
    private float glassRadius = 0.5f;

    private boolean popupGlass;
    private float popupGlassOpacity = 0.45f, popupGlassRadius = 0.5f;

    private JTextField editor;

    private JPopupMenu popup;
    private JPanel popupPanel;
    private JScrollPane scrollPane;

    private double popupAnimation;
    private Timer popupTimer;

    public StyledComboBox(Style style, Object[] values) {
        super();
        this.style = style;

        if (values != null) Collections.addAll(items, values);

        if (!items.isEmpty()) selectedIndex = 0;

        backgroundColor = style.field;
        hoverColor = style.hover;
        popupBorderColor = selectedColor = borderColor = style.accent;
        textColor = arrowColor = style.text;
        popupBackgroundColor = style.panel;

        font = new Font("SansSerif", Font.BOLD, 14);
        popupFont = new Font("SansSerif", Font.BOLD, 14);

        if (style == Style.GLASS) {
            glass = true;
            popupGlass = true;
        }

        itemStyle.setNormalColor(style.field);
        itemStyle.setHoverColor(style.hover);
        itemStyle.setSelectedColor(style.accent);
        itemStyle.setTextColor(style.text);
        itemStyle.setBorderColor(style.accent);
        itemStyle.setGlass(style == Style.GLASS);

        popupStyle.setNormalColor(style.panel);
        popupStyle.setHoverColor(style.hover);
        popupStyle.setSelectedColor(style.accent);
        popupStyle.setTextColor(style.text);
        popupStyle.setBorderColor(new Color(255, 255, 255, 0));
        popupStyle.setGlass(style == Style.GLASS);

        setFocusable(true);
        setOpaque(false);

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(180, 42));

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!enabled) return;
                requestFocusInWindow();
                if (popupVisible) hidePopup();
                else showPopup();
            }
        });
        addMouseWheelListener(e -> {
            if (!enabled || items.isEmpty()) return;
            int direction = e.getWheelRotation();
            if (direction > 0) setSelectedIndex(Math.min(items.size() - 1, selectedIndex + 1));
            else if (direction < 0) setSelectedIndex(Math.max(0, selectedIndex - 1));
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!enabled) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> togglePopup();
                    case KeyEvent.VK_ESCAPE -> { if (popupVisible) hidePopup(); }
                    case KeyEvent.VK_UP -> {
                        if (popupVisible) changePopupSelection(-1);
                        else setSelectedIndex(Math.max(0, selectedIndex - 1));
                    }

                    case KeyEvent.VK_DOWN -> {
                        if (popupVisible) changePopupSelection(1);
                        else setSelectedIndex(Math.min(items.size() - 1, selectedIndex + 1));
                    }
                    case KeyEvent.VK_HOME -> setSelectedIndex(0);
                    case KeyEvent.VK_END -> setSelectedIndex(items.size() - 1);
                }
            }
        });

        popupTimer = new Timer(16, e -> {
            double target = popupVisible ? 1.0 : 0.0;
            popupAnimation += (target - popupAnimation) * 0.25;

            if (Math.abs(popupAnimation - target) < 0.02) {
                popupAnimation = target;
                popupTimer.stop();
            }
            if (popup != null) popup.repaint();
            if (popupPanel != null) popupPanel.repaint();
            if (scrollPane != null) {
                scrollPane.repaint();
                scrollPane.getViewport().repaint();
            }
            repaint();
        });
    }
    public StyledComboBox(Style style, Object[] values, int referenceWidth, int referenceHeight) {
        this(style, values);
        setReferenceSize(referenceWidth, referenceHeight);
    }
    @Override public void addNotify() {
        super.addNotify();
        LocalizationBridge.addLanguageChangeListener(languageChangeTask);
    }

    @Override public void removeNotify() {
        LocalizationBridge.removeLanguageChangeListener(languageChangeTask);
        super.removeNotify();
    }

    @Override public void repaint() {
        super.repaint();
        if (popup != null && popup.isVisible()) {
            popup.repaint();
            if (popupPanel != null) popupPanel.repaint();
            if (scrollPane != null) {
                scrollPane.repaint();
                if (scrollPane.getViewport() != null) scrollPane.getViewport().repaint();
            }
        }
    }
    @Override protected Style getStyle() { return style; }
    public Style getStyleValue() { return style; }

    public ItemStyle getItemStyle() { return itemStyle; }
    public ItemStyle getPopupStyle() { return popupStyle; }

    public void applyStyle(Style style) {
        if (style == null) return;

        this.style = style;

        backgroundColor = style.field;
        hoverColor = style.hover;
        popupBorderColor = borderColor = selectedColor = style.accent;
        textColor = arrowColor = style.text;
        popupBackgroundColor = style.panel;

        glass = popupGlass = style == Style.GLASS;

        itemStyle.setNormalColor(style.field);
        itemStyle.setHoverColor(style.hover);
        itemStyle.setSelectedColor(style.accent);
        itemStyle.setTextColor(style.text);
        itemStyle.setBorderColor(style.accent);
        itemStyle.setGlass(glass);

        popupStyle.setNormalColor(style.panel);
        popupStyle.setHoverColor(style.hover);
        popupStyle.setSelectedColor(style.accent);
        popupStyle.setTextColor(style.text);
        popupStyle.setBorderColor(new Color(255, 255, 255, 0));
        popupStyle.setGlass(glass);

        repaint();
        if (popupVisible) rebuildPopup();
    }
    public void setStyle(Style style) { applyStyle(style); }

    public int getItemCount() { return items.size(); }
    public Object getItemAt(int index) {
        if (index < 0 || index >= items.size()) throw new IndexOutOfBoundsException("index: " + index);
        return items.get(index);
    }

    public void addItem(Object item) {
        items.add(item);
        if (selectedIndex < 0) selectedIndex = 0;
        repaint();
        if (popupVisible) rebuildPopup();
    }
    public void addItem(int index, Object item) {
        if (index < 0 || index > items.size()) throw new IndexOutOfBoundsException("index: " + index);
        items.add(index, item);

        if (selectedIndex < 0) selectedIndex = 0;
        else if (index <= selectedIndex) selectedIndex++;

        repaint();
        if (popupVisible) rebuildPopup();
    }
    public void addItems(Object... values) {
        if (values == null) return;

        items.addAll(Arrays.asList(values));
        if (selectedIndex < 0 && !items.isEmpty()) selectedIndex = 0;

        repaint();
        if (popupVisible) rebuildPopup();
    }
    public void removeItem(Object item) {
        int index = items.indexOf(item);
        if (index >= 0) removeItemAt(index);
    }
    public void removeItemAt(int index) {
        if (index < 0 || index >= items.size()) throw new IndexOutOfBoundsException("index: " + index);
        items.remove(index);

        if (items.isEmpty()) selectedIndex = -1;
        else if (selectedIndex > index)
            selectedIndex--;
        else if (selectedIndex == index) selectedIndex = Math.min(selectedIndex, items.size() - 1);

        repaint();
        if (popupVisible) rebuildPopup();
    }
    public void removeAllItems() {
        items.clear();
        selectedIndex = -1;
        hidePopup();
        repaint();
    }

    public boolean containsItem(Object item) { return items.contains(item); }
    public int indexOf(Object item) { return items.indexOf(item); }

    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int index) {
        if (items.isEmpty()) {
            selectedIndex = -1;
            repaint();
            return;
        }
        int newIndex = Math.clamp(index, 0, items.size() - 1);
        if (newIndex == selectedIndex) {
            repaint();
            return;
        }
        selectedIndex = newIndex;
        if (editor != null) editor.setText(getSelectedItemString());

        fireActionEvent();
        repaint();
        if (popupVisible) updatePopupSelection();
    }

    public Object getSelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= items.size()) return null;
        return items.get(selectedIndex);
    }
    public void setSelectedItem(Object item) {
        if (item == null) {
            selectedIndex = -1;
            repaint();
            return;
        }

        int index = items.indexOf(item);

        if (index >= 0) setSelectedIndex(index);
        else if (editable) {
            ensureEditor();
            editor.setText(item.toString());
            repaint();
        }
    }

    public String getSelectedItemString() {
        Object value = getSelectedItem();
        if (value == null) return editor != null ? editor.getText() : "";
        return displayString(value);
    }

    private String displayString(Object value) {
        if (value == null) return "";
        return LocalizationBridge.localized(String.valueOf(value));
    }

    private final Runnable languageChangeTask = () -> {
        if (editor != null && selectedIndex >= 0) editor.setText(getSelectedItemString());
        if (popupVisible) rebuildPopup();
        revalidate();
        repaint();
    };

    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) {
        if (this.editable == editable) return;
        this.editable = editable;
        if (editable) ensureEditor();
        else if (editor != null) {
            remove(editor);
            editor = null;
        }
        revalidate();
        repaint();
    }

    private void ensureEditor() {
        if (editor != null) return;
        editor = new JTextField(getSelectedItemString());
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(0, scaled(horizontalPadding), 0, scaled(2)));
        editor.setForeground(textColor);
        editor.setCaretColor(textColor);
        editor.setFont(scaledFont(font));
        editor.setEnabled(enabled);

        editor.addActionListener(e -> {
            String text = editor.getText();
            for (int i = 0; i < items.size(); i++) {
                if (displayString(items.get(i)).equals(text)) {
                    setSelectedIndex(i);
                    return;
                }
            }
            fireActionEvent();
        });

        add(editor);
        updateEditorBounds();
    }
    private void updateEditorBounds() {
        if (editor == null) return;
        int arrowWidth = Math.max(scaled(28), getHeight());
        editor.setBounds(scaled(3), scaled(2), Math.max(1, getWidth() - arrowWidth - scaled(6)), Math.max(1, getHeight() - scaled(4)));
    }

    public int getMaximumRowCount() { return maximumRowCount; }
    public void setMaximumRowCount(int count) {
        maximumRowCount = Math.max(1, count);
        if (popupVisible) rebuildPopup();
    }

    public boolean isPopupVisible() { return popupVisible; }

    public void showPopup() {
        if (!enabled || items.isEmpty() || popupVisible) return;
        createPopup();
        popupVisible = true;
        popupAnimation = 0;
        popupTimer.restart();

        Point location = getPopupLocation();
        popup.setLocation(location);
        popup.setOpaque(false);
        popup.setBackground(new Color(0, 0, 0, 0));
        popup.setBorder(null);
        popup.setBorderPainted(false);
        popup.setLightWeightPopupEnabled(true);
        popup.setVisible(true);

        updatePopupSelection();
        repaint();
    }

    public void hidePopup() {
        if (!popupVisible) return;

        popupVisible = false;
        popupAnimation = 1;
        popupTimer.restart();

        if (popup != null) popup.setVisible(false);
        repaint();
    }

    public void togglePopup() {
        if (popupVisible) hidePopup();
        else showPopup();
    }
    private Point getPopupLocation() {
        try {
            Point point = getLocationOnScreen();
            return new Point(point.x, point.y + getHeight() + scaled(4));
        } catch (IllegalComponentStateException e) { return new Point(0, getHeight()); }
    }
    private void createPopup() {
        popup = new JPopupMenu() {
            @Override public void paint(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
                super.paint(g);
            }
            @Override public void update(Graphics g) { paint(g); }
        };
        popup.setUI(new javax.swing.plaf.basic.BasicPopupMenuUI());
        popup.setOpaque(false);
        popup.setBackground(new Color(0, 0, 0, 0));
        popup.setBorder(null);
        popup.setBorderPainted(false);
        popup.setLightWeightPopupEnabled(true);
        rebuildPopup();
    }

    private void rebuildPopup() {
        if (popup == null) return;

        popup.removeAll();
        popup.setOpaque(false);
        popup.setBackground(new Color(0, 0, 0, 0));
        popup.setBorder(null);
        popup.setBorderPainted(false);

        popupPanel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {super.paintComponent(g);}
        };
        popupPanel.setOpaque(false);
        popupPanel.setBackground(new Color(0, 0, 0, 0));
        popupPanel.setLayout(new BoxLayout(popupPanel, BoxLayout.Y_AXIS));

        for (int index = 0; index < items.size(); index++) {
            popupPanel.add(createPopupItem(index));
            if (index < items.size() - 1) popupPanel.add(Box.createVerticalStrut(scaled(itemSpacing)));
        }

        scrollPane = new JScrollPane(popupPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setOpaque(false);
        scrollPane.setBackground(new Color(0, 0, 0, 0));
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(scaled(12));


        scrollPane.setPreferredSize(new Dimension(Math.max(getWidth(), popup.getWidth()),
                Math.clamp(items.size(), 1, maximumRowCount) * scaled(itemHeight) + scaled(popupPadding * 2)));

        JPanel wrapper = createPopupBackground();
        wrapper.setOpaque(false);
        wrapper.setBackground(new Color(0, 0, 0, 0));
        wrapper.add(scrollPane, BorderLayout.CENTER);

        popup.add(wrapper);
        popup.pack();
        popup.revalidate();
        popup.repaint();
    }
    private JPanel createPopupItem(int index) {
        return new JPanel(new BorderLayout()) {
            private boolean over;
            {
                setOpaque(false);
                setBackground(new Color(0, 0, 0, 0));

                int height = scaled(itemHeight);
                setPreferredSize(new Dimension(Math.max(getWidth(), scaled(100)), height));
                setMinimumSize(new Dimension(scaled(80), height));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { over = true;repaint(); }
                    @Override public void mouseExited(MouseEvent e) { over = false;repaint(); }
                    @Override public void mousePressed(MouseEvent e) {
                        if (!enabled) return;
                        setSelectedIndex(index);
                        hidePopup();
                        requestFocusInWindow();
                    }});
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = graphics(g);

                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) {
                    g2d.dispose();
                    return;
                }
                ItemStyle s = popupStyle;
                Color fill;

                if (s.isGlass()) {
                    int alpha;

                    if (index == selectedIndex) alpha = 62;
                    else if (over) alpha = 48;
                    else alpha = 28;

                    fill = new Color(255, 255, 255, alpha);
                } else if (index == selectedIndex) fill = s.getSelectedColor();
                else if (over) fill = s.getHoverColor();
                else fill = s.getNormalColor();

                float itemArc = Math.clamp(Math.min(w, h) * 0.22f, scaled(2), scaled(s.getArc()));

                g2d.setColor(fill);
                g2d.fillRoundRect(0, 0, w, h, Math.round(itemArc), Math.round(itemArc));

                if (s.isGlass()) {
                    Graphics2D sheen = (Graphics2D) g2d.create();
                    paintGlassSheen(sheen, w, h, itemArc, reflectionPhase);
                    sheen.dispose();
                }

                Color itemBorder = s.getBorderColor();

                if (itemBorder != null && itemBorder.getAlpha() > 0) {
                    g2d.setColor(itemBorder);
                    g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(itemArc), Math.round(itemArc));
                }

                g2d.setFont(scaledFont(s.getFont()));
                g2d.setColor(enabled ? s.getTextColor() : disabledColor());

                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString(displayString(items.get(index)), scaled(s.getHorizontalPadding()),  (h - fm.getHeight()) / 2 + fm.getAscent());
                g2d.dispose();
            }
        };
    }
    private JPanel createPopupBackground() {
        return new JPanel(new BorderLayout()) {
            {
                setOpaque(false);
                setBackground(new Color(0, 0, 0, 0));
                setBorder(BorderFactory.createEmptyBorder(scaled(popupPadding), scaled(popupPadding), scaled(popupPadding), scaled(popupPadding)));
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = graphics(g);
                int w = getWidth(), h = getHeight();

                if (w <= 0 || h <= 0) {
                    g2d.dispose();
                    return;
                }
                float popupRadius = Math.clamp(Math.min(w, h) * 0.16f, scaled(4), scaled(popupArc));
                g2d.clip(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, popupRadius, popupRadius));

                if (popupGlass) {
                    g2d.setColor(new Color(255, 255, 255, safeAlpha(22 + 35 * (float) popupAnimation + 25 * popupGlassOpacity)));
                    g2d.fillRect(0, 0, w, h);
                    paintGlassSheen(g2d, w, h, popupRadius, reflectionPhase);
                } else {
                    g2d.setColor(popupBackgroundColor);
                    g2d.fillRect(0, 0, w, h);
                }
                g2d.dispose();
            }
        };
    }

    private void changePopupSelection(int amount) {
        if (items.isEmpty()) return;

        int index = selectedIndex;
        if (index < 0) index = 0;

        index = Math.clamp(index + amount, 0, items.size() - 1);
        selectedIndex = index;
        updatePopupSelection();
        repaint();
    }
    private void updatePopupSelection() {
        if (popupPanel == null) return;

        Component[] components = popupPanel.getComponents();
        int itemIndex = 0;

        for (Component component : components) {
            if (component instanceof JPanel) {
                component.repaint();
                if (itemIndex == selectedIndex && scrollPane != null) scrollPane.getViewport().scrollRectToVisible(component.getBounds());
                itemIndex++;
            }
        }
    }

    public void addActionListener(ActionListener listener) { if (listener != null) listeners.add(listener); }
    public void removeActionListener(ActionListener listener) { listeners.remove(listener); }
    public ActionListener[] getActionListeners() { return listeners.toArray(new ActionListener[0]); }

    private void fireActionEvent() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getSelectedItemString());
        for (ActionListener listener : new ArrayList<>(listeners)) listener.actionPerformed(event);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) hidePopup();
        if (editor != null) editor.setEnabled(enabled);
        super.setEnabled(enabled);
        repaint();
    }

    public boolean isLightWeightPopupEnabled() { return lightWeightPopup; }
    public void setLightWeightPopupEnabled(boolean enabled) {
        lightWeightPopup = enabled;
        if (popup != null) popup.setLightWeightPopupEnabled(enabled);
    }

    public Color getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(Color color) {
        if (color == null) return;
        backgroundColor = color;
        repaint();
    }

    public Color getHoverColor() { return hoverColor; }
    public void setHoverColor(Color color) {
        if (color == null) return;
        hoverColor = color;
        repaint();
    }

    public Color getSelectedColor() { return selectedColor; }
    public void setSelectedColor(Color color) {
        if (color == null) return;
        selectedColor = color;
        repaint();
        if (popupVisible) rebuildPopup();
    }

    public Color getTextColor() { return textColor; }
    public void setTextColor(Color color) {
        if (color == null) return;
        textColor = color;
        if (editor != null) {
            editor.setForeground(color);
            editor.setCaretColor(color);
        }
        repaint();
    }

    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color color) {
        if (color == null) return;
        borderColor = color;
        repaint();
    }

    public Color getArrowColor() { return arrowColor; }
    public void setArrowColor(Color color) {
        if (color == null) return;
        arrowColor = color;
        repaint();
    }

    public Color getPopupBackgroundColor() { return popupBackgroundColor; }
    public void setPopupBackgroundColor(Color color) {
        if (color == null) return;
        popupBackgroundColor = color;
        if (popupVisible) rebuildPopup();
    }

    public Color getPopupBorderColor() { return popupBorderColor; }
    public void setPopupBorderColor(Color color) {
        if (color == null) return;
        popupBorderColor = color;
        if (popupVisible) rebuildPopup();
    }

    public int getArc() { return arc; }
    public void setArc(int arc) {
        this.arc = Math.max(0, arc);
        repaint();
    }

    public int getPopupArc() { return popupArc; }
    public void setPopupArc(int arc) {
        popupArc = Math.max(0, arc);
        if (popupVisible) rebuildPopup();
    }

    public int getHorizontalPadding() { return horizontalPadding; }
    public void setHorizontalPadding(int padding) {
        horizontalPadding = Math.max(0, padding);
        if (editor != null) editor.setBorder(BorderFactory.createEmptyBorder(0, scaled(horizontalPadding), 0, scaled(2)));
        repaint();
    }

    public int getVerticalPadding() { return verticalPadding; }
    public void setVerticalPadding(int padding) {
        verticalPadding = Math.max(0, padding);
        repaint();
    }

    public int getPopupPadding() { return popupPadding; }
    public void setPopupPadding(int padding) {
        popupPadding = Math.max(0, padding);
        if (popupVisible) rebuildPopup();
    }

    public int getItemSpacing() { return itemSpacing; }
    public void setItemSpacing(int spacing) {
        itemSpacing = Math.max(0, spacing);
        if (popupVisible) rebuildPopup();
    }

    public int getItemHeight() { return itemHeight; }
    public void setItemHeight(int height) {
        itemHeight = Math.max(1, height);
        if (popupVisible) rebuildPopup();
    }

    public Font getFontValue() { return font; }
    public void setFontValue(Font font) {
        if (font == null) return;
        this.font = font;
        if (editor != null) editor.setFont(scaledFont(font));
        repaint();
    }

    public Font getPopupFont() { return popupFont; }
    public void setPopupFont(Font font) {
        if (font == null) return;
        popupFont = font;
        if (popupVisible) rebuildPopup();
    }

    public boolean isGlass() { return glass; }
    public void setGlass(boolean glass) {
        this.glass = glass;
        repaint();
        if (popupVisible) rebuildPopup();
    }

    public float getGlassOpacity() { return glassOpacity; }
    public void setGlassOpacity(float opacity) {
        glassOpacity = Math.clamp(opacity, 0f, 1f);
        repaint();
    }

    public float getGlassRadius() { return glassRadius; }
    public void setGlassRadius(float radius) {
        glassRadius = Math.max(0f, radius);
        repaint();
    }

    public boolean isPopupGlass() { return popupGlass; }
    public void setPopupGlass(boolean glass) {
        popupGlass = glass;
        if (popupVisible) rebuildPopup();
        repaint();
    }

    public float getPopupGlassOpacity() { return popupGlassOpacity; }
    public void setPopupGlassOpacity(float opacity) {
        popupGlassOpacity = Math.clamp(opacity, 0f, 1f);
        if (popupVisible) {
            if (popup != null) popup.repaint();
            if (popupPanel != null) popupPanel.repaint();
        }
        repaint();
    }

    public float getPopupGlassRadius() { return popupGlassRadius; }
    public void setPopupGlassRadius(float radius) {
        popupGlassRadius = Math.max(0f, radius);
        repaint();
    }

    private Font scaledFont(Font source) {
        if (source == null) source = font;
        return source.deriveFont(Math.max(1, source.getSize2D() * parentScale()));
    }
    private Color disabledColor() { return new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 90); }
    @Override public void doLayout() {
        super.doLayout();
        updateEditorBounds();
    }
    @Override public Dimension getMinimumSize() { return new Dimension(scaled(80), scaled(30)); }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2d = graphics(g);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            g2d.dispose();
            return;
        }

        float currentArc = Math.clamp(Math.min(w, h) * 0.22f, scaled(2), scaled(arc));
        Color fill;

        if (!enabled) fill = new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), Math.min(backgroundColor.getAlpha(), 90));
        else if (glass) fill = new Color(255, 255, 255, safeAlpha(22 + 35 * (float) animation + 25 * glassOpacity));
        else fill = mix(backgroundColor, hoverColor, (float) animation);

        g2d.setColor(fill);
        g2d.fillRoundRect(0, 0, w, h, Math.round(currentArc), Math.round(currentArc));

        if (glass) {
            Graphics2D sheen = (Graphics2D)g2d.create();
            paintGlassSheen(sheen, w, h, currentArc, reflectionPhase);
            sheen.dispose();
        }

        g2d.setColor(enabled ? borderColor : disabledColor());
        g2d.drawRoundRect(0, 0, w - 1, h - 1, Math.round(currentArc), Math.round(currentArc));

        if (editor == null) {
            String text = getSelectedItemString();
            g2d.setFont(scaledFont(font));
            g2d.setColor(enabled ? textColor : disabledColor());

            FontMetrics fm = g2d.getFontMetrics();
            int available = Math.max(1, w - Math.max(scaled(28), h) - scaled(horizontalPadding * 2));

            String display = text;
            while (fm.stringWidth(display) > available && display.length() > 1) display = display.substring(0, display.length() - 1);
            if (!display.equals(text) && display.length() > 3) display = display.substring(0, display.length() - 3) + "...";

            g2d.drawString(display, scaled(horizontalPadding), (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        paintArrow(g2d, w, h);
        g2d.dispose();
    }
    private static int safeAlpha(double value) { return Math.clamp((int) Math.round(value), 0, 255); }

    private void paintArrow(Graphics2D g2d, int w, int h) {
        int size = Math.max(scaled(5), Math.round(h * 0.12f));
        int centerX = w - Math.max(scaled(12), h / 2);
        int centerY = h / 2;
        int[] x = {centerX - size, centerX + size, centerX};
        int[] y;
        if (popupVisible) y = new int[]{centerY + size / 2, centerY + size / 2, centerY - size};
        else y = new int[]{centerY - size / 2, centerY - size / 2, centerY + size};
        g2d.setColor(enabled ? arrowColor : disabledColor());
        g2d.fillPolygon(x, y, 3);
    }
}