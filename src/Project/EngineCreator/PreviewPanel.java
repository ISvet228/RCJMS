package Project.EngineCreator;
import Project.CreatorKit.*;
import Project.Helpers.EditorKit;
import Project.Helpers.EditorKit.*;

import Project.Game.*;
import Project.Helpers.MazeGenerator;
import Project.StyleUI.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.*;

public final class PreviewPanel extends JPanel {
    //region Variables
    //region Preview Size
    static final int W = 240, H = 150;
    private final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    //endregion
    //region Camera State
    private static final double STEP = 0.25, TURN = 0.2;
    private double angle = 0.4, playerX = 2.5, playerY = 2.5;
    private double curvature = 4.0;
    //endregion
    //region Scene Data
    private final int[][] map = EngineBuilder.testFrame().map;
    private final RenderFrame textures = EngineBuilder.testFrame();
    private RenderEngine engine;
    //endregion
    //region Panel Widgets
    private final JComponent view;
    private final Text status = new Text(null, 11f, false, EditorKit.MUTED, 0);
    private final Note error = new Note(188, 11f, EditorKit.BAD);
    private final StyledComboBox geometry;
    //endregion
    //region Callbacks
    private final Supplier<CkProject> projectSupplier;
    private final Consumer<String> flash;
    //endregion
    //endregion

    //region Constructors
    PreviewPanel(Style style, Supplier<CkProject> projectSupplier, Consumer<String> flash) {
        super(new BorderLayout(12, 0));
        this.projectSupplier = projectSupplier;
        this.flash = flash;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        view = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(EditorKit.CANVAS_BG);
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.drawImage(image, 0, 0, null);
                graphics.setColor(engine == null ? EditorKit.MUTED : EditorKit.CARD_LINE);
                graphics.drawRect(0, 0, W - 1, H - 1);
            }
        };
        view.setPreferredSize(new Dimension(W, H));
        view.setFocusable(true);
        view.setToolTipText(L.t("ec.ui.previewKeys"));
        view.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent mouseEvent) { view.requestFocusInWindow(); } });
        view.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent keyEvent) { onKey(keyEvent.getKeyCode()); } });

        geometry = new StyledComboBox(style, new Object[]{L.t("ec.o.euclid"), L.t("ec.o.hyperbolic")});
        geometry.setPreferredSize(new Dimension(188, 30));
        geometry.addActionListener(_ -> render());
        StyledButton buildButton = new StyledButton(style, "ec.ui.previewBuild");
        buildButton.setPreferredSize(new Dimension(188, 32));
        buildButton.addActionListener(_ -> build());

        Column info = new Column();
        info.put(new Text("ec.ui.previewTitle", 12f, true, EditorKit.TEXT, 0), 0);
        Note help = new Note(188, 11f, EditorKit.MUTED);
        help.set(null, EditorKit.MUTED, L.t("ec.ui.previewHelp"));
        info.put(help, 4);
        info.put(geometry, 8);
        info.put(buildButton, 6);
        info.put(status, 6);
        info.put(error, 4);

        add(view, BorderLayout.WEST);
        add(info, BorderLayout.CENTER);
        status.setKey("ec.ui.previewIdle");
    }
    //endregion

    //region Layout
    void build() {
        CkProject ckProject = projectSupplier.get();
        EngineBuilder.PreviewBuild result = EngineBuilder.buildForPreview(ckProject);
        engine = result.engine();
        curvature = ckProject.curvature;
        if (engine == null) {
            status.setRaw("");
            error.set(null, EditorKit.BAD, result.error());
            flash.accept(result.error());
            repaint();
            return;
        }
        error.set(null, EditorKit.OK, L.t("ec.ui.previewReady"));
        render();
    }
    //endregion

    //region Input
    private void onKey(int key) {
        if (engine == null) return;
        double nx = playerX, ny = playerY;
        switch (key) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> { nx += Math.cos(angle) * STEP; ny += Math.sin(angle) * STEP; }
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> { nx -= Math.cos(angle) * STEP; ny -= Math.sin(angle) * STEP; }
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> { angle -= TURN; render(); return; }
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> { angle += TURN; render(); return; }
            default -> { return; }
        }
        if (isOpen(nx, ny)) { playerX = nx; playerY = ny; }
        render();
    }
    void reset() {
        engine = null;
        status.setKey("ec.ui.previewIdle");
        error.set(null, EditorKit.MUTED, "");
        view.repaint();
    }
    //endregion

    //region Rendering
    private void render() {
        if (engine == null) return;
        RenderFrame renderFrame = new RenderFrame();
        renderFrame.renderWidth = W;
        renderFrame.renderHeight = H;
        renderFrame.renderPixels = new int[W * H];
        renderFrame.map = map;
        renderFrame.quality = 1;
        renderFrame.cameraAngle = angle;
        renderFrame.cameraHeight = 0.5;
        renderFrame.cameraPitch = 0;
        renderFrame.playerX = playerX;
        renderFrame.playerY = playerY;
        renderFrame.curvature = curvature;
        renderFrame.wallTexture = textures.wallTexture;
        renderFrame.floorTexture = textures.floorTexture;
        renderFrame.ceilingTexture = textures.ceilingTexture;
        renderFrame.finishTexture = textures.finishTexture;
        boolean hyp = geometry.getSelectedIndex() == 1;
        renderFrame.geometry = hyp ? MazeGenerator.GeometryMode.WRONG : MazeGenerator.GeometryMode.EUCLIDEAN;
        long t0 = System.nanoTime();
        try { engine.render(renderFrame);}
        catch (Throwable exception) {
            error.set(null, EditorKit.BAD, L.f("ec.b.smokeError", exception.toString()));
            return;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) image.setRGB(x, y, renderFrame.renderPixels[y * W + x] & 0xFFFFFF);
        status.setRaw(L.f("ec.ui.previewStatus", ms));
        view.repaint();
    }
    private boolean isOpen(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        return cy >= 0 && cy < map.length && cx >= 0 && cx < map[cy].length && map[cy][cx] == 0;
    }
    //endregion
}