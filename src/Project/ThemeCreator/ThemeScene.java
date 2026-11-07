package Project.ThemeCreator;

import java.awt.geom.*;
import java.util.*;

public final class ThemeScene {
    //region Variables
    public static final int W = 960, H = 540;
    //endregion

    //region Constructors
    private ThemeScene() {}
    //endregion

    //region Coordinates
    public static AffineTransform parentWorld(ThemeDoc doc, ThemeRenderer.Frame frame, ThemeObject themeObject) {
        ThemeObject parent = doc.find(themeObject.parent);
        AffineTransform affineTransform = parent == null ? null : frame.world.get(parent.id);
        return affineTransform == null ? new AffineTransform() : affineTransform;
    }
    public static Point2D toParentSpace(ThemeDoc doc, ThemeRenderer.Frame frame, ThemeObject themeObject, Point2D canvas) {
        return inverse(parentWorld(doc, frame, themeObject)).transform(canvas, null);
    }
    private static AffineTransform inverse(AffineTransform affineTransform) {
        try { return affineTransform.createInverse(); }
        catch (NoninvertibleTransformException exception) { return new AffineTransform(); }
    }
    private static double number(ThemeObject themeObject, String key) {
        Object v = themeObject.props.get(key);
        return v instanceof Number n ? n.doubleValue() : (double) 0;
    }
    //endregion

    //region Picking
    public static ThemeObject pick(ThemeDoc doc, ThemeRenderer.Frame frame, Point2D canvas, double tol) {
        List<ThemeObject> order = frame.order;
        for (int i = order.size() - 1; i >= 0; i--) {
            ThemeObject themeObject = order.get(i);
            AffineTransform affineTransform = frame.world.get(themeObject.id);
            if (themeObject.type.equals("empty") || affineTransform == null) continue;
            Point2D local = inverse(affineTransform).transform(canvas, null);
            if (ThemeRenderer.hit(themeObject.type, frame.props.get(themeObject.id), local, tol)) return themeObject;
        }
        return null;
    }
    public static double segmentDistance(Point2D point2D, Point2D a, Point2D b) {
        return Line2D.ptSegDist(a.getX(), a.getY(), b.getX(), b.getY(), point2D.getX(), point2D.getY());
    }
    //endregion

    //region Handles
    public static Point2D[] handle(AffineTransform parent, Point2D origin, double arrow) {
        Point2D point2D = parent.deltaTransform(new Point2D.Double(1, 0), null);
        Point2D y = parent.deltaTransform(new Point2D.Double(0, 1), null);
        return new Point2D[]{origin, tip(origin, point2D, arrow), tip(origin, y, arrow)};
    }
    public static char handleAt(Point2D[] handle, Point2D canvas, double tol) {
        if (canvas.distance(handle[0]) <= tol) return 'c';
        if (segmentDistance(canvas, handle[0], handle[1]) <= tol) return 'x';
        if (segmentDistance(canvas, handle[0], handle[2]) <= tol) return 'y';
        return 0;
    }
    private static Point2D tip(Point2D origin, Point2D dir, double len) {
        double m = Math.hypot(dir.getX(), dir.getY());
        if (m < 1e-9) return new Point2D.Double(origin.getX(), origin.getY());
        return new Point2D.Double(origin.getX() + dir.getX() / m * len, origin.getY() + dir.getY() / m * len);
    }
    //endregion

    //region Placement
    public static double[] centred(ThemeDoc doc, ThemeRenderer.Frame frame, ThemeObject themeObject) {
        return placedAt(doc, frame, themeObject, new Point2D.Double(W / 2.0, H / 2.0));
    }
    public static double[] placedAt(ThemeDoc doc, ThemeRenderer.Frame frame, ThemeObject themeObject, Point2D canvas) {
        Point2D mid = toParentSpace(doc, frame, themeObject, canvas);
        double w = number(themeObject, "w"), h = number(themeObject, "h");
        return switch (themeObject.type) {
            case "rect", "ellipse", "ring", "arc" -> new double[]{mid.getX() - w / 2, mid.getY() - h / 2};
            case "polygon", "polyline" -> new double[]{mid.getX() - 50, mid.getY() - 50};
            default -> new double[]{mid.getX(), mid.getY()};
        };
    }
    //endregion
}