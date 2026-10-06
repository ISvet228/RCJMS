import StyleUI.*;
import Helpers.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntUnaryOperator;

public class SeedMapDialog extends JDialog {
    //region Variables
    private static final String[] FINISH_MODES = {"mm.opposite_corner", "mm.center", "mm.random_edge"};

    private final IntUnaryOperator sizeNormalizer;
    private final int maxFloors;
    private final SeedMapBuilder.Options initial;

    private final StyledTextField widthField;
    private final StyledTextField heightField;
    private final StyledTextField floorsField;
    private final StyledTextField seedField;
    private final StyledToggle loopedToggle;
    private final StyledComboBox finishBox;

    private SeedMapBuilder.Options result = null;
    //endregion

    //region Constructors
    public SeedMapDialog(Component owner, Style style, SeedMapBuilder.Options initial, IntUnaryOperator sizeNormalizer, int maxFloors) {
        super(SwingUtilities.getWindowAncestor(owner), ModalityType.APPLICATION_MODAL);
        this.initial = initial;
        this.sizeNormalizer = sizeNormalizer;
        this.maxFloors = maxFloors;

        widthField = new StyledTextField(style, String.valueOf(initial.width()));
        heightField = new StyledTextField(style, String.valueOf(initial.height()));
        floorsField = new StyledTextField(style, String.valueOf(initial.floors()));
        seedField = new StyledTextField(style, SeedUtil.normalize(initial.seed()));
        loopedToggle = new StyledToggle(style, "mm.looped_geometry");
        finishBox = new StyledComboBox(style, FINISH_MODES);

        loopedToggle.setSelected(initial.looped());
        finishBox.setSelectedIndex(Math.clamp(initial.finishMode(), 0, FINISH_MODES.length - 1));
        seedField.limitInput(SeedUtil.LENGTH, SeedUtil::isAllowedChar);

        StyledButton randomSeedButton = new StyledButton(style, "me.random_seed");
        StyledButton generateButton = new StyledButton(style, "me.generate");
        StyledButton cancelButton = new StyledButton(style, "cancel");
        randomSeedButton.addActionListener(e -> seedField.setText(SeedUtil.randomSeed()));
        generateButton.addActionListener(e -> confirm());
        cancelButton.addActionListener(e -> dispose());
        for (StyledTextField field : new StyledTextField[]{widthField, heightField, floorsField, seedField}) {
            field.setPreferredSize(new Dimension(150, 34));
            field.addActionListener(e -> confirm());
        }
        seedField.setPreferredSize(new Dimension(150, 34));
        loopedToggle.setPreferredSize(new Dimension(220, 34));
        finishBox.setPreferredSize(new Dimension(220, 36));

        buildLayout(style, randomSeedButton, generateButton, cancelButton);
        NSLocalizedString.bind(this, "me.load_from_seed", this::setTitle);
        setAlwaysOnTop(true);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }
    //endregion

    //region Public API
    public SeedMapBuilder.Options showDialog() { setVisible(true); return result; }
    //endregion

    //region Helpers
    private void buildLayout(Style style, StyledButton randomSeedButton, StyledButton generateButton, StyledButton cancelButton) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(RCJMS.MY_FAV_GRAY);
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        int row = 0;
        addRow(content, row++, new StyledLabel(style, "width"), widthField);
        addRow(content, row++, new StyledLabel(style, "height"), heightField);
        addRow(content, row++, new StyledLabel(style, "mm.floors_count"), floorsField);
        addRow(content, row++, new StyledLabel(style, "mm.mode"), finishBox);
        addRow(content, row++, null, loopedToggle);

        JPanel seedPanel = new JPanel(new BorderLayout(6, 0));
        seedPanel.setOpaque(false);
        seedPanel.add(seedField, BorderLayout.CENTER);
        seedPanel.add(randomSeedButton, BorderLayout.EAST);
        addRow(content, row++, new StyledLabel(style, "mm.custom_seed"), seedPanel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(generateButton);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0; constraints.gridy = row; constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(14, 0, 0, 0);
        content.add(buttons, constraints);

        setContentPane(content);
    }
    private void addRow(JPanel panel, int row, JComponent label, JComponent control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(4, 0, 4, 10);
        constraints.anchor = GridBagConstraints.WEST;
        if (label != null) {
            constraints.gridx = 0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(label, constraints);
        }
        constraints.gridx = 1;
        constraints.insets = new Insets(4, 0, 4, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(control, constraints);
    }
    private void confirm() {
        String seed = SeedUtil.normalize(seedField.getText());
        if (seed.isEmpty()) seed = SeedUtil.randomSeed();
        result = new SeedMapBuilder.Options(
                sizeNormalizer.applyAsInt(parseNumber(widthField, initial.width())),
                sizeNormalizer.applyAsInt(parseNumber(heightField, initial.height())),
                Math.clamp(parseNumber(floorsField, initial.floors()), 1, maxFloors),
                loopedToggle.isSelected(), finishBox.getSelectedIndex(), seed);
        dispose();
    }
    private static int parseNumber(StyledTextField field, int fallback) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }
    //endregion
}
