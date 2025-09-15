package graphtheory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * File-based persistent undo/redo using two stack files.
 * - UNDO_STACK: main stack; each edit pushes a snapshot to the end of the file
 * - REDO_STACK: backup stack; undo pops from UNDO_STACK and appends to REDO_STACK; redo pops from REDO_STACK and appends to UNDO_STACK
 *
 * Each snapshot is stored as a block:
 * ===SNAP===\n
 * UI\n
 * directionalityEnabled=0|1\n
 * weightsEnabled=0|1\n
 * selectedTool=<int>\n
 * zoom=<double>\n
 * panX=<int>\n
 * panY=<int>\n
 * GRAPH\n
 * <FileManager.saveToString graph content>\n
 */
public class VersionControl {

    private static final File UNDO_FILE = new File("UndoStack.txt");
    private static final File REDO_FILE = new File("RedoStack.txt");
    private static final String SNAP_DELIM = "===SNAP===";

    public static synchronized void clear() {
        try {
            Files.write(UNDO_FILE.toPath(), new byte[0]);
        } catch (IOException ignore) {}
        try {
            Files.write(REDO_FILE.toPath(), new byte[0]);
        } catch (IOException ignore) {}
    }

    // Push a new CURRENT snapshot to the UNDO stack and clear REDO stack (called on each edit/saveState)
    public static synchronized void captureCurrent(Vector<Vertex> vertexList,
                                                   Vector<Edge> edgeList,
                                                   boolean directionalityEnabled,
                                                   boolean weightsEnabled,
                                                   int selectedTool,
                                                   double zoom,
                                                   int panX,
                                                   int panY,
                                                   FileManager fm) {
        Snapshot snap = new Snapshot(directionalityEnabled, weightsEnabled, selectedTool, zoom, panX, panY,
                                     fm.saveToString(vertexList, edgeList));
        appendSnapshot(UNDO_FILE, snap);
        // Clear redo stack when a new change happens
        try { Files.write(REDO_FILE.toPath(), new byte[0]); } catch (IOException ignore) {}
    }

    // Undo: move the latest snapshot from UNDO to REDO, then apply the new top of UNDO (or empty if none)
    public static synchronized boolean undo(Canvas canvas, FileManager fm) {
        List<Snapshot> undo = readAll(UNDO_FILE);
        if (undo.isEmpty()) return false;
        // Pop latest to redo
        Snapshot latest = undo.remove(undo.size() - 1);
        appendSnapshot(REDO_FILE, latest);
        // Rewrite UNDO without the popped snapshot
        writeAll(UNDO_FILE, undo);
        // Apply new top or empty
        if (!undo.isEmpty()) {
            applySnapshot(canvas, fm, undo.get(undo.size() - 1));
        } else {
            // Apply empty graph/state
            canvas.applyGraphAndUI(new Vector<>(), new Vector<>(), false, false, 0, 1.0, 0, 0);
        }
        return true;
    }

    // Redo: pop from REDO and push to UNDO, then apply it
    public static synchronized boolean redo(Canvas canvas, FileManager fm) {
        List<Snapshot> redo = readAll(REDO_FILE);
        if (redo.isEmpty()) return false;
        Snapshot next = redo.remove(redo.size() - 1);
        writeAll(REDO_FILE, redo);
        appendSnapshot(UNDO_FILE, next);
        applySnapshot(canvas, fm, next);
        return true;
    }

    // Toggle preserved for compatibility: tries redo first if REDO has entries; else undo
    public static synchronized boolean toggle(Canvas canvas, FileManager fm) {
        List<Snapshot> redo = readAll(REDO_FILE);
        if (!redo.isEmpty()) return redo(canvas, fm);
        return undo(canvas, fm);
    }

    private static void applySnapshot(Canvas canvas, FileManager fm, Snapshot snap) {
        if (snap == null) return;
        Vector<Vector<?>> fileData = fm.loadFromString(snap.graphText);
        @SuppressWarnings("unchecked")
        Vector<Vertex> v = (Vector<Vertex>) fileData.firstElement();
        @SuppressWarnings("unchecked")
        Vector<Edge> e = (Vector<Edge>) fileData.lastElement();
        canvas.applyGraphAndUI(v, e, snap.directionalityEnabled, snap.weightsEnabled, snap.selectedTool, snap.zoom, snap.panX, snap.panY);
    }

    private static void appendSnapshot(File file, Snapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append(SNAP_DELIM).append('\n');
        sb.append("UI\n");
        sb.append("directionalityEnabled=").append(snap.directionalityEnabled ? 1 : 0).append('\n');
        sb.append("weightsEnabled=").append(snap.weightsEnabled ? 1 : 0).append('\n');
        sb.append("selectedTool=").append(snap.selectedTool).append('\n');
        sb.append("zoom=").append(snap.zoom).append('\n');
        sb.append("panX=").append(snap.panX).append('\n');
        sb.append("panY=").append(snap.panY).append('\n');
        sb.append("GRAPH\n");
        sb.append(snap.graphText);
        if (!snap.graphText.endsWith("\n")) sb.append('\n');
        try {
            List<String> lines = file.exists() ? Files.readAllLines(file.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
            String existing = String.join("\n", lines);
            String toWrite = existing.isEmpty() ? sb.toString() : (existing + "\n" + sb.toString());
            Files.write(file.toPath(), toWrite.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {}
    }

    private static List<Snapshot> readAll(File file) {
        List<Snapshot> out = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try {
            if (file.exists()) {
                lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignore) {}
        int i = 0;
        while (i < lines.size()) {
            // seek delim
            while (i < lines.size() && !SNAP_DELIM.equals(lines.get(i))) i++;
            if (i >= lines.size()) break;
            i++; // move past delim
            ParseResult pr = parseSnapshot(lines, i);
            if (pr.snapshot != null) out.add(pr.snapshot);
            i = pr.nextIndex;
        }
        return out;
    }

    private static void writeAll(File file, List<Snapshot> snaps) {
        StringBuilder sb = new StringBuilder();
        for (Snapshot s : snaps) {
            sb.append(SNAP_DELIM).append('\n');
            sb.append("UI\n");
            sb.append("directionalityEnabled=").append(s.directionalityEnabled ? 1 : 0).append('\n');
            sb.append("weightsEnabled=").append(s.weightsEnabled ? 1 : 0).append('\n');
            sb.append("selectedTool=").append(s.selectedTool).append('\n');
            sb.append("zoom=").append(s.zoom).append('\n');
            sb.append("panX=").append(s.panX).append('\n');
            sb.append("panY=").append(s.panY).append('\n');
            sb.append("GRAPH\n");
            sb.append(s.graphText);
            if (!s.graphText.endsWith("\n")) sb.append('\n');
        }
        try {
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {}
    }

    private static ParseResult parseSnapshot(List<String> lines, int startIdx) {
        Snapshot snap = null;
        int i = startIdx;
        if (i >= lines.size()) return new ParseResult(null, i);
        // Expect UI
        if (i < lines.size() && lines.get(i).equals("UI")) {
            i++;
            boolean dir = false, w = false;
            int tool = 0; double zoom = 1.0; int panX = 0, panY = 0;
            while (i < lines.size() && !lines.get(i).equals("GRAPH") && !lines.get(i).equals(SNAP_DELIM)) {
                String ln = lines.get(i).trim();
                if (ln.startsWith("directionalityEnabled=")) dir = ln.endsWith("1");
                else if (ln.startsWith("weightsEnabled=")) w = ln.endsWith("1");
                else if (ln.startsWith("selectedTool=")) { try { tool = Integer.parseInt(ln.substring(13)); } catch (Exception ignore) {} }
                else if (ln.startsWith("zoom=")) { try { zoom = Double.parseDouble(ln.substring(5)); } catch (Exception ignore) {} }
                else if (ln.startsWith("panX=")) { try { panX = Integer.parseInt(ln.substring(5)); } catch (Exception ignore) {} }
                else if (ln.startsWith("panY=")) { try { panY = Integer.parseInt(ln.substring(5)); } catch (Exception ignore) {} }
                i++;
            }
            // Expect GRAPH
            if (i < lines.size() && lines.get(i).equals("GRAPH")) {
                i++;
                StringBuilder graph = new StringBuilder();
                while (i < lines.size() && !lines.get(i).equals(SNAP_DELIM)) {
                    graph.append(lines.get(i)).append('\n');
                    i++;
                }
                snap = new Snapshot(dir, w, tool, zoom, panX, panY, graph.toString());
            }
        }
        return new ParseResult(snap, i);
    }

    private static class Snapshot {
        boolean directionalityEnabled;
        boolean weightsEnabled;
        int selectedTool;
        double zoom;
        int panX, panY;
        String graphText;
        Snapshot(boolean dir, boolean w, int tool, double zoom, int panX, int panY, String graphText) {
            this.directionalityEnabled = dir;
            this.weightsEnabled = w;
            this.selectedTool = tool;
            this.zoom = zoom;
            this.panX = panX;
            this.panY = panY;
            this.graphText = graphText;
        }
    }

    private static class ParseResult {
        Snapshot snapshot; int nextIndex;
        ParseResult(Snapshot s, int i) { this.snapshot = s; this.nextIndex = i; }
    }
}
