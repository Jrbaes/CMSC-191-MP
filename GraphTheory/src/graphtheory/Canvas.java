package graphtheory;

/**
 *
 * @author mk
 */
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Vector;
import java.util.Stack;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;

public class Canvas {

    public JFrame frame;
    private JMenuBar menuBar;
    private CanvasPane canvas;
    private Graphics2D graphic;
    private Color backgroundColour;
    private Image canvasImage,  canvasImage2;
    private int selectedTool;
    private int selectedWindow;
    private Dimension screenSize;
    public int width,  height;
    private int clickedVertexIndex;
    private Color selectedColor;
    private FileManager fileManager = new FileManager();
    private boolean directionalityEnabled = false;
    private boolean showInstructionsOverlay = false;
    private boolean weightsEnabled = false;
    private boolean darkMode = false;
    private double zoom = 1.0;
    private int panX = 0, panY = 0;
    private int lastMouseX = 0, lastMouseY = 0;
    private Vertex pathSource = null;
    private static final int TOOLBAR_BUTTON_WIDTH = 200;
    private JPanel propertiesPanel;

    /////////////
    private Vector<Vertex> vertexList;
    private Vector<Edge> edgeList;
    private GraphProperties gP = new GraphProperties();
    private Stack<GraphState> undoStack = new Stack<>();
    private Stack<GraphState> redoStack = new Stack<>();
    /////////////

    public Canvas(String title, int width, int height, Color bgColour) {
        frame = new JFrame();
        frame.setTitle(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        canvas = new CanvasPane();
        InputListener inputListener = new InputListener();
        canvas.addMouseListener(inputListener);
        canvas.addMouseMotionListener(inputListener);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                Dimension size = canvas.getSize();
                Canvas.this.width = size.width;
                Canvas.this.height = size.height;
                canvasImage = canvas.createImage(size.width, size.height);
                canvasImage2 = canvas.createImage(size.width, size.height);
                graphic = (Graphics2D) canvasImage.getGraphics();
                graphic.setColor(backgroundColour);
                graphic.fillRect(0, 0, size.width, size.height);
                graphic.setColor(Color.black);
                refresh();
            }
        });
        // Content pane will be set later with a root BorderLayout containing toolbar (WEST), canvas (CENTER), and properties (EAST)

        this.width = width;
        this.height = height;
        canvas.setPreferredSize(new Dimension(width, height));

        //events
        // Set up a locked (non-floatable) vertical toolbar inside a bordered pane on the left
        JToolBar toolBar = new JToolBar(JToolBar.VERTICAL);
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Instructions toggle button at the top of the toolbar
        JButton instructionsButton = new JButton("Instructions");
        instructionsButton.setToolTipText("Show instructions for the selected tool (toggles overlay)");
        instructionsButton.setPreferredSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        instructionsButton.setMaximumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        instructionsButton.setMinimumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        instructionsButton.setHorizontalAlignment(SwingConstants.LEFT);
        instructionsButton.addActionListener(ev -> { showInstructionsOverlay = !showInstructionsOverlay; refresh(); });
        toolBar.add(instructionsButton);
        
        // View toggle: Graph <-> Properties
        JToggleButton viewToggle = new JToggleButton("Show Properties");
        viewToggle.setToolTipText("Toggle between Graph and Properties view");
        viewToggle.setPreferredSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        viewToggle.setMaximumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        viewToggle.setMinimumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
        viewToggle.setHorizontalAlignment(SwingConstants.LEFT);
        viewToggle.addActionListener(ev -> {
            boolean toProperties = viewToggle.isSelected();
            if (toProperties) {
                selectedWindow = 1;
                if (vertexList.size() > 0) {
                    int[][] matrix = gP.generateAdjacencyMatrix(vertexList, edgeList);
                    Vector<Vertex> tempList = gP.vertexConnectivity(vertexList);
                    for (Vertex v : tempList) {
                        vertexList.get(vertexList.indexOf(v)).wasClicked = true;
                    }
                    reloadVertexConnections(matrix, vertexList);
                    gP.generateDistanceMatrixWeighted(vertexList, edgeList);
                }
                viewToggle.setText("Show Graph");
            } else {
                selectedWindow = 0;
                viewToggle.setText("Show Properties");
            }
            erase();
            refresh();
        });
        toolBar.add(viewToggle);
        toolBar.addSeparator();

        Object[][] toolData = {
            {"Add Vertex", "Ctrl+A", "Click to add a new vertex."},
            {"Add Edges", "Ctrl+E", "Drag between nodes to connect. (Double-click on the selected node for a self-loop)"},
            {"Grab Tool", "Ctrl+G", "Click and drag to move vertices."},
            {"Pan View", null, "Click and drag to pan the view."},
            {"Zoom In", null, "Zoom in the view."},
            {"Zoom Out", null, "Zoom out the view."},
            {"Reset View", null, "Reset pan and zoom back to default."},
            {"Highlight Tool", null, "Click an edge to highlight it; click a node to highlight it and its incident edges."},
            {"Remove Tool", null, "(Double-click on the selected node/edge) to remove it."},
            {"Set Node Color", null, "Select a color, then click a node to apply it."},
            {"Set Vertex Label", null, "Click a node to set or edit its label (name)."},
            {"Set Edge Color", null, "Select a color, then click an edge to apply it."},
            {"Enable Directionality", null, "Toggle directed edges on or off."},
            {"Invert Edge Direction", null, "Click a directed edge to reverse its direction."},
            {"Enable Weights", null, "Toggle showing and editing edge weights."},
            {"Set Edge Weight", null, "Click an edge to set its weight (Enable Weights first)."},
            {"Dijkstra Path", null, "Click source vertex, then target vertex to visualize shortest path (Dijkstra)."},
            {"Bellman-Ford Path", null, "Click source vertex, then target vertex to visualize shortest path (Bellman-Ford)."},
            {"APSP (Floyd-Warshall)", null, "Compute all-pairs shortest paths and show in Properties."},
            {"Graph Complement", null, "Replace the graph with its complement (respects directionality setting)."},
            {"Auto Arrange Vertices", null, "Arrange vertices in a circle."},
            {"Undo", "Ctrl+Z", "Undo the last action."},
            {"Redo", "Ctrl+Y", "Redo the last undone action."},
            {"Remove All", null, "Clear the canvas, removing all vertices and edges."}
        };

        for (Object[] tool : toolData) {
            String name = (String) tool[0];
            String shortcut = (String) tool[1];
            String tooltip = (String) tool[2];

            String buttonText = name + (shortcut != null ? " (" + shortcut + ")" : "");
            JButton button = new JButton(buttonText);
            button.setActionCommand(name); // Use the base name for the action command
            button.setToolTipText(tooltip);
            button.setPreferredSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
            button.setMaximumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
            button.setMinimumSize(new Dimension(TOOLBAR_BUTTON_WIDTH, 32));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.addActionListener(new MenuListener());
            toolBar.add(button);
        }

        // Make the tools area scrollable while staying locked to the left
        JScrollPane toolScroll = new JScrollPane(toolBar, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        toolScroll.setBorder(BorderFactory.createEmptyBorder());
        toolScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel toolPanel = new JPanel(new BorderLayout());
        toolPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        toolPanel.add(toolScroll, BorderLayout.CENTER);
        toolPanel.setPreferredSize(new Dimension(TOOLBAR_BUTTON_WIDTH + 20, height));
        // Do not add directly to frame; we'll assemble a root panel below

        // Set up the properties panel (EAST)
        propertiesPanel = new JPanel();
        propertiesPanel.setLayout(new BoxLayout(propertiesPanel, BoxLayout.Y_AXIS));
        propertiesPanel.setBorder(BorderFactory.createTitledBorder("Graph Properties"));

        // Root layout to separate panes: tools (WEST), work canvas (CENTER), properties (EAST)
        JPanel root = new JPanel(new BorderLayout());
        root.add(toolPanel, BorderLayout.WEST);
        root.add(canvas, BorderLayout.CENTER);
        root.add(propertiesPanel, BorderLayout.EAST);
        frame.setContentPane(root);

        menuBar = new JMenuBar();
        JMenu menuOptions1 = new JMenu("File");
        // Window menu removed per user request

        JMenuItem item = new JMenuItem("Open File");
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        item.addActionListener(new MenuListener());
        menuOptions1.add(item);
        item = new JMenuItem("Save to File");
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        item.addActionListener(new MenuListener());
        menuOptions1.add(item);
        item = new JMenuItem("Import From Matrix...");
        item.addActionListener(new MenuListener());
        menuOptions1.add(item);

        menuOptions1.addSeparator();
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.addActionListener(new MenuListener());
        menuOptions1.add(darkModeItem);

        menuBar.add(menuOptions1);

        frame.setJMenuBar(menuBar);

        backgroundColour = bgColour;

        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setBounds(screenSize.width / 2 - width / 2, screenSize.height / 2 - height / 2, width, height);
        frame.pack();
        setVisible(true);

        vertexList = new Vector<Vertex>();
        edgeList = new Vector<Edge>();

    }

    class InputListener implements MouseListener, MouseMotionListener {

        @Override
        public void mouseClicked(MouseEvent e) {
            saveState();

            if (selectedWindow == 0) {
                if (e.getClickCount() == 2 && selectedTool == 2) { // Double-click for self-loop
                    for (Vertex v : vertexList) {
                        if (vertexHit(v, e.getX(), e.getY())) {
                            Edge edge = new Edge(v, v);
                            edge.isDirected = directionalityEnabled;
                            edgeList.add(edge);
                            v.addVertex(v);
                            break;
                        }
                    }
                } else {
                    switch (selectedTool) {
                        case 1: {
                            Vertex v = new Vertex("" + vertexList.size(), toWorldX(e.getX()), toWorldY(e.getY()));
                            vertexList.add(v);
                            v.draw(graphic);
                            break;
                        }
                    case 4: { // Remove Tool
                        if (e.getClickCount() == 1) {
                            // Single-click: select vertex or edge under cursor
                            boolean selectedSomething = false;
                            for (Vertex v : vertexList) {
                                if (vertexHit(v, e.getX(), e.getY())) {
                                    v.wasClicked = true;
                                    selectedSomething = true;
                                } else {
                                    v.wasClicked = false;
                                }
                            }
                            for (Edge edge : edgeList) {
                                if (edgeHit(edge, e.getX(), e.getY())) {
                                    edge.wasClicked = true;
                                    selectedSomething = true;
                                } else {
                                    edge.wasClicked = false;
                                }
                            }
                            if (selectedSomething) refresh();
                        } else if (e.getClickCount() == 2) {
                            // Double-click: if selected vertex/edge is under cursor, remove it
                            Vertex selectedVertex = null;
                            for (Vertex v : vertexList) {
                                if (v.wasClicked && vertexHit(v, e.getX(), e.getY())) {
                                    selectedVertex = v;
                                    break;
                                }
                            }
                            if (selectedVertex != null) {
                                Vector<Edge> edgesToRemove = new Vector<>();
                                for (Edge edge : edgeList) {
                                    if (edge.vertex1 == selectedVertex || edge.vertex2 == selectedVertex) {
                                        edgesToRemove.add(edge);
                                    }
                                }
                                edgeList.removeAll(edgesToRemove);
                                for (Vertex v : vertexList) {
                                    v.connectedVertices.remove(selectedVertex);
                                }
                                vertexList.remove(selectedVertex);
                            } else {
                                Edge selectedEdge = null;
                                for (Edge edge : edgeList) {
                                    if (edge.wasClicked && edgeHit(edge, e.getX(), e.getY())) {
                                        selectedEdge = edge;
                                        break;
                                    }
                                }
                                if (selectedEdge != null) {
                                    // Remove only the clicked arrow (edge)
                                    edgeList.remove(selectedEdge);
                                    // Update adjacency only if no other edge remains between the pair
                                    if (!hasAnyEdgeBetween(selectedEdge.vertex1, selectedEdge.vertex2)) {
                                        selectedEdge.vertex1.connectedVertices.remove(selectedEdge.vertex2);
                                        if (selectedEdge.vertex1 != selectedEdge.vertex2) {
                                            selectedEdge.vertex2.connectedVertices.remove(selectedEdge.vertex1);
                                        }
                                    }
                                }
                            }
                            // Clear any selection
                            for (Vertex v : vertexList) v.wasClicked = false;
                            for (Edge edge : edgeList) edge.wasClicked = false;
                        }
                        break;
                    }
                    case 11: { // Pan View - no action on click
                        break;
                    }
                    case 10: { // Highlight Tool
                        // Clear previous highlights
                        for (Vertex v : vertexList) v.wasClicked = false;
                        for (Edge ed : edgeList) ed.wasClicked = false;
                        // Prefer edge highlight if an edge is under cursor
                        boolean highlighted = false;
                        for (Edge ed : edgeList) {
                            if (edgeHit(ed, e.getX(), e.getY())) {
                                ed.wasClicked = true;
                                highlighted = true;
                                break;
                            }
                        }
                        if (!highlighted) {
                            for (Vertex v : vertexList) {
                                if (vertexHit(v, e.getX(), e.getY())) {
                                    v.wasClicked = true;
                                    for (Edge ed : edgeList) {
                                        if (ed.vertex1 == v || ed.vertex2 == v) {
                                            ed.wasClicked = true;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        refresh();
                        break;
                    }
                    case 5: { // Set Node Color
                        if (selectedColor != null) {
                            for (Vertex v : vertexList) {
                                if (vertexHit(v, e.getX(), e.getY())) {
                                    v.color = selectedColor;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    case 6: { // Set Edge Color
                        if (selectedColor != null) {
                            for (Edge edge : edgeList) {
                                if (edgeHit(edge, e.getX(), e.getY())) {
                                    edge.color = selectedColor;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    case 7: { // Invert Edge Direction
                        if (directionalityEnabled) {
                            for (Edge edge : edgeList) {
                                if (edgeHit(edge, e.getX(), e.getY())) {
                                    // Swap the vertices to invert the direction
                                    Vertex temp = edge.vertex1;
                                    edge.vertex1 = edge.vertex2;
                                    edge.vertex2 = temp;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    case 8: { // Set Edge Weight
                        if (weightsEnabled) {
                            for (Edge edge : edgeList) {
                                if (edgeHit(edge, e.getX(), e.getY())) {
                                    String input = JOptionPane.showInputDialog(frame, "Set edge weight:", Integer.toString(edge.weight));
                                    if (input != null) {
                                        try {
                                            int w = Integer.parseInt(input.trim());
                                            edge.weight = w;
                                            // If currently viewing Properties, recompute weighted distances immediately
                                            if (selectedWindow == 1) {
                                                gP.generateDistanceMatrixWeighted(vertexList, edgeList);
                                                erase();
                                            }
                                        } catch (NumberFormatException ex) {
                                            // ignore invalid input
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    case 9: { // Set Vertex Label
                        for (Vertex vtx : vertexList) {
                            if (vertexHit(vtx, e.getX(), e.getY())) {
                                String input = JOptionPane.showInputDialog(frame, "Set vertex label:", vtx.name);
                                if (input != null) {
                                    String trimmed = input.trim();
                                    if (!trimmed.isEmpty()) {
                                        vtx.name = trimmed;
                                    }
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case 12: // Dijkstra Path
                    case 13: { // Bellman-Ford Path
                        // Select source first, then target; visualize path
                        Vertex clicked = null;
                        for (Vertex vtx : vertexList) {
                            if (vertexHit(vtx, e.getX(), e.getY())) { clicked = vtx; break; }
                        }
                        if (clicked != null) {
                            if (pathSource == null) {
                                pathSource = clicked;
                                for (Vertex v : vertexList) v.wasClicked = false;
                                for (Edge ed : edgeList) ed.wasClicked = false;
                                clicked.wasClicked = true; // mark source
                            } else {
                                Vertex target = clicked;
                                int srcIdx = vertexList.indexOf(pathSource);
                                int tgtIdx = vertexList.indexOf(target);
                                int[] prev = (selectedTool == 12)
                                        ? gP.dijkstraPredecessor(vertexList, edgeList, srcIdx)
                                        : gP.bellmanFordPredecessor(vertexList, edgeList, srcIdx);
                                if (prev == null) {
                                    JOptionPane.showMessageDialog(frame, "Negative cycle detected (Bellman-Ford).", "Shortest Path", JOptionPane.WARNING_MESSAGE);
                                } else {
                                    if (srcIdx != tgtIdx && prev[tgtIdx] == -1) {
                                        JOptionPane.showMessageDialog(frame, "Target unreachable from source.", "Shortest Path", JOptionPane.INFORMATION_MESSAGE);
                                    } else {
                                        // Clear previous highlights
                                        for (Vertex v : vertexList) v.wasClicked = false;
                                        for (Edge ed : edgeList) ed.wasClicked = false;
                                        // Reconstruct path
                                        java.util.ArrayList<Integer> seq = new java.util.ArrayList<>();
                                        for (int v = tgtIdx; v != -1; v = prev[v]) seq.add(0, v);
                                        // Highlight vertices and edges
                                        for (int i = 0; i < seq.size(); i++) {
                                            vertexList.get(seq.get(i)).wasClicked = true;
                                            if (i + 1 < seq.size()) {
                                                Vertex a = vertexList.get(seq.get(i));
                                                Vertex b = vertexList.get(seq.get(i+1));
                                                Edge picked = null;
                                                for (Edge ed : edgeList) {
                                                    if (ed.vertex1 == a && ed.vertex2 == b) { picked = ed; break; }
                                                }
                                                if (picked == null) {
                                                    for (Edge ed : edgeList) {
                                                        if (!ed.isDirected && ((ed.vertex1 == b && ed.vertex2 == a))) { picked = ed; break; }
                                                    }
                                                }
                                                if (picked != null) picked.wasClicked = true;
                                            }
                                        }
                                        refresh();
                                    }
                                }
                                pathSource = null; // reset selection
                            }
                        }
                        break;
                    }
                }
            }
            //refresh();
            }


        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (selectedWindow == 0 && vertexList.size() > 0) {
                switch (selectedTool) {
                    case 2: {
                        for (Vertex v : vertexList) {
                            if (vertexHit(v, e.getX(), e.getY())) {
                                v.wasClicked = true;
                                clickedVertexIndex = vertexList.indexOf(v);
                            } else {
                                v.wasClicked = false;
                            }
                        }
                        break;
                    }
                    case 3: {

                        for (Edge d : edgeList) {
                            if (edgeHit(d, e.getX(), e.getY())) {
                                d.wasClicked = true;
                            } else {
                                d.wasClicked = false;
                            }
                        }
                        for (Vertex v : vertexList) {
                            if (vertexHit(v, e.getX(), e.getY())) {
                                v.wasClicked = true;
                                clickedVertexIndex = vertexList.indexOf(v);
                            } else {
                                v.wasClicked = false;
                            }
                        }
                        break;
                    }
                    case 11: {
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        break;
                    }
                }
            }

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            saveState();
            if (selectedWindow == 0 && vertexList.size() > 0) {
                switch (selectedTool) {
                    case 2: {
                        Vertex parentV = vertexList.get(clickedVertexIndex);
                        for (Vertex v : vertexList) {
                            if (vertexHit(v, e.getX(), e.getY())) {
                                if (!v.connectedToVertex(parentV) && v != parentV) { // Edge to another vertex
                                    // Direction from first (pressed) to second (released)
                                    Edge edge = new Edge(parentV, v);
                                    edge.isDirected = directionalityEnabled;
                                    v.addVertex(parentV);
                                    parentV.addVertex(v);
                                    edgeList.add(edge);
                                    break;
                                }
                            }
                        }

                        for (Vertex v : vertexList) {
                            v.wasClicked = false;
                        }
                        break;
                    }
                    case 3: {
                        vertexList.get(clickedVertexIndex).wasClicked = false;
                        break;
                    }
                }
            }
            erase();
            refresh();
        }

        @Override
        public void mouseDragged(MouseEvent e) {

            if (selectedWindow == 0 && vertexList.size() > 0) {
                erase();
                switch (selectedTool) {
                    case 2: {
                        graphic.setColor(Color.RED);
                        Graphics2D g2 = graphic;
                        AffineTransform old = g2.getTransform();
                        g2.translate(panX, panY);
                        g2.scale(zoom, zoom);
                        drawLine(vertexList.get(clickedVertexIndex).location.x, vertexList.get(clickedVertexIndex).location.y, toWorldX(e.getX()), toWorldY(e.getY()));
                        g2.setTransform(old);
                        break;

                    }
                    case 3: {
                        if (vertexList.get(clickedVertexIndex).wasClicked) {
                            vertexList.get(clickedVertexIndex).location.x = toWorldX(e.getX());
                            vertexList.get(clickedVertexIndex).location.y = toWorldY(e.getY());
                        }
                        break;
                    }
                    case 11: {
                        int dx = e.getX() - lastMouseX;
                        int dy = e.getY() - lastMouseY;
                        panX += dx;
                        panY += dy;
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        break;
                    }
                }
                refresh();
            }

        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (selectedWindow == 0) {
                for (Edge d : edgeList) {
                    if (edgeHit(d, e.getX(), e.getY())) {
                        d.wasFocused = true;
                    } else {
                        d.wasFocused = false;
                    }
                }
                for (Vertex v : vertexList) {
                    if (vertexHit(v, e.getX(), e.getY())) {
                        v.wasFocused = true;
                    } else {
                        v.wasFocused = false;
                    }
                }
                refresh();
            }

        }
    }

    class MenuListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();
            if (command.equals("Add Vertex")) {
                selectedTool = 1;
            } else if (command.equals("Add Edges")) {
                selectedTool = 2;
            } else if (command.equals("Grab Tool")) {
                selectedTool = 3;
            } else if (command.equals("Pan View")) {
                selectedTool = 11;
            } else if (command.equals("Remove Tool")) {
                selectedTool = 4;
            } else if (command.equals("Set Node Color")) {
                selectedTool = 5;
                selectedColor = JColorChooser.showDialog(frame, "Choose Node Color", Color.BLACK);
            } else if (command.equals("Set Edge Color")) {
                selectedTool = 6;
                selectedColor = JColorChooser.showDialog(frame, "Choose Edge Color", Color.BLACK);
            } else if (command.equals("Highlight Tool")) {
                selectedTool = 10;
            } else if (command.equals("Set Vertex Label")) {
                selectedTool = 9;
            } else if (command.equals("Zoom In")) {
                zoom = Math.min(5.0, zoom * 1.2);
                refresh();
            } else if (command.equals("Zoom Out")) {
                zoom = Math.max(0.2, zoom / 1.2);
                refresh();
            } else if (command.equals("Reset View")) {
                zoom = 1.0; panX = 0; panY = 0; refresh();
            } else if (command.equals("Dijkstra Path")) {
                selectedTool = 12;
            } else if (command.equals("Bellman-Ford Path")) {
                selectedTool = 13;
            } else if (command.equals("Enable Directionality")) {
                directionalityEnabled = !directionalityEnabled;
                if (directionalityEnabled) {
                    // Make all edges directed and ensure reverse edges exist
                    Vector<Edge> toAdd = new Vector<>();
                    for (Edge edge : edgeList) {
                        edge.isDirected = true;
                        if (edge.vertex1 != edge.vertex2 && !hasDirectedEdge(edge.vertex2, edge.vertex1)) {
                            Edge rev = new Edge(edge.vertex2, edge.vertex1);
                            rev.color = edge.color;
                            rev.isDirected = true;
                            rev.weight = edge.weight;
                            toAdd.add(rev);
                        }
                    }
                    edgeList.addAll(toAdd);
                } else {
                    // Hide direction arrows when disabled
                    for (Edge edge : edgeList) edge.isDirected = false;
                }
                // If Properties view is active, recompute matrices
                if (selectedWindow == 1) {
                    int[][] matrix = gP.generateAdjacencyMatrix(vertexList, edgeList);
                    Vector<Vertex> tempList = gP.vertexConnectivity(vertexList);
                    for (Vertex v : tempList) {
                        vertexList.get(vertexList.indexOf(v)).wasClicked = true;
                    }
                    reloadVertexConnections(matrix, vertexList);
                    gP.generateDistanceMatrixWeighted(vertexList, edgeList);
                    erase();
                }
                refresh();
            } else if (command.equals("Invert Edge Direction")) {
                selectedTool = 7;
            } else if (command.equals("Enable Weights")) {
                weightsEnabled = !weightsEnabled;
                // If Properties view is active, recompute matrices
                if (selectedWindow == 1) {
                    int[][] matrix = gP.generateAdjacencyMatrix(vertexList, edgeList);
                    Vector<Vertex> tempList = gP.vertexConnectivity(vertexList);
                    for (Vertex v : tempList) {
                        vertexList.get(vertexList.indexOf(v)).wasClicked = true;
                    }
                    reloadVertexConnections(matrix, vertexList);
                    gP.generateDistanceMatrixWeighted(vertexList, edgeList);
                    erase();
                }
                refresh();
            } else if (command.equals("Set Edge Weight")) {
                selectedTool = 8;
            } else if (command.equals("APSP (Floyd-Warshall)")) {
                gP.floydWarshall(vertexList, edgeList);
                selectedWindow = 1;
                erase();
            } else if (command.equals("Graph Complement")) {
                saveState();
                complementGraph();
                erase();
            } else if (command.equals("Graph Diameter/Radius")) {
                computeAndShowDiameterRadius();
            } else if (command.equals("Dark Mode")) {
                darkMode = !darkMode;
                backgroundColour = darkMode ? new Color(30,30,30) : Color.WHITE;
                erase();
            } else if (command.equals("Undo")) {
                undo();
            } else if (command.equals("Redo")) {
                redo();
            } else if (command.equals("Auto Arrange Vertices")) {
                saveState();
                arrangeVertices();
                erase();
            } else if (command.equals("Remove All")) {
                saveState();
                edgeList.removeAllElements();
                vertexList.removeAllElements();
                clickedVertexIndex = 0;
                erase();
            } else if (command.equals("Open File")) {
                int returnValue = fileManager.jF.showOpenDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    loadFile(fileManager.loadFile(fileManager.jF.getSelectedFile()));
                    System.out.println(fileManager.jF.getSelectedFile());
                    selectedWindow=0;
                }
            } else if (command.equals("Save to File")) {
                int returnValue = fileManager.jF.showSaveDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    fileManager.saveFile(vertexList, edgeList, fileManager.jF.getSelectedFile());
                    System.out.println(fileManager.jF.getSelectedFile());
                }
            } else if (command.equals("Import From Matrix...")) {
                openImportDialog();
            } else if (command.equals("Graph")) {
                selectedWindow = 0;
                erase();
            } else if (command.equals("Properties")) {
                selectedWindow = 1;
                if (vertexList.size() > 0) {
                    //adjacency list
                    int[][] matrix = gP.generateAdjacencyMatrix(vertexList, edgeList);

                    //connectivity
                    Vector<Vertex> tempList = gP.vertexConnectivity(vertexList);
                    for (Vertex v : tempList) {
                        vertexList.get(vertexList.indexOf(v)).wasClicked = true;
                    }
                    reloadVertexConnections(matrix, vertexList);

                    //distance (weighted + direction-aware)
                    gP.generateDistanceMatrixWeighted(vertexList, edgeList);

                    //VD paths
                    gP.displayContainers(vertexList);
                //gP.drawNWideDiameter();
                }
                erase();
            } else if (command.equals("Maximize Window")) {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.validate();
            } else if (command.equals("Instructions")) {
                showInstructions();
            }

            refresh();
        }
    }

    private void arrangeVertices() {
        double deg2rad = Math.PI / 180;
        double radius = height / 5;
        double centerX = width / 2;
        double centerY = height / 2;
        int interval = 360 / vertexList.size();


        for (int i = 0; i < vertexList.size(); i++) {
            double degInRad = i * deg2rad * interval;
            double x = centerX + (Math.cos(degInRad) * radius);
            double y = centerY + (Math.sin(degInRad) * radius);
            int X = (int) x;
            int Y = (int) y;
            vertexList.get(i).location.x = X;
            vertexList.get(i).location.y = Y;
        }

    }

    private void reloadVertexConnections(int[][] aMatrix, Vector<Vertex> vList) {
        for (Vertex v : vList) {
            v.connectedVertices.clear();
        }

        for (int i = 0; i < aMatrix.length; i++) {
            for (int j = 0; j < aMatrix.length; j++) {
                if (aMatrix[i][j] == 1) {
                    vList.get(i).addVertex(vList.get(j));
                }
            }
        }

    }

    @SuppressWarnings("unchecked")
    private void loadFile(Vector<Vector<?>> File) {
        vertexList = (Vector<Vertex>) File.firstElement();
        edgeList = (Vector<Edge>) File.lastElement();
        erase();
    }

    private void saveState() {
        // To prevent saving states unnecessarily, we could add a check here
        // to see if the graph has actually changed since the last save.
        // For now, we'll clear the redo stack and push the new state.
        redoStack.clear();
        if (undoStack.isEmpty() || !isSameState(undoStack.peek(), vertexList, edgeList)) {
            undoStack.push(new GraphState(vertexList, edgeList));
        }
    }

    private boolean isSameState(GraphState state, Vector<Vertex> currentVertices, Vector<Edge> currentEdges) {
        if (state.vertexList.size() != currentVertices.size() || state.edgeList.size() != currentEdges.size()) {
            return false;
        }
        // This is a simplified check. A more thorough check would compare vertex positions, colors, and connections.
        return true;
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(new GraphState(vertexList, edgeList));
            GraphState previousState = undoStack.pop();
            this.vertexList = previousState.vertexList;
            this.edgeList = previousState.edgeList;
            refresh();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(new GraphState(vertexList, edgeList));
            GraphState nextState = redoStack.pop();
            this.vertexList = nextState.vertexList;
            this.edgeList = nextState.edgeList;
            refresh();
        }
    }

    public void refresh() {
        // Clear back buffer to avoid drawing ghosts
        graphic.setColor(backgroundColour);
        graphic.fillRect(0, 0, width, height);

        // Apply pan/zoom for drawing graph content
        Graphics2D g2 = graphic;
        AffineTransform oldTx = g2.getTransform();
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        // Draw edges (hide arrows when directionality is disabled)
        for (Edge e : edgeList) {
            boolean orig = e.isDirected;
            if (!directionalityEnabled) e.isDirected = false;
            e.draw(g2);
            e.isDirected = orig;

            // Draw weight label if enabled
            if (weightsEnabled && e.vertex1 != null && e.vertex2 != null) {
                int mx = (e.vertex1.location.x + e.vertex2.location.x) / 2;
                int my = (e.vertex1.location.y + e.vertex2.location.y) / 2;
                Color prev = g2.getColor();
                g2.setColor(darkMode ? Color.WHITE : Color.BLACK);
                g2.drawString(Integer.toString(e.weight), mx + 6, my - 6);
                g2.setColor(prev);
            }
        }
        for (Vertex v : vertexList) {
            v.draw(g2);
        }
        // Restore transform for UI overlays
        g2.setTransform(oldTx);

        // Update properties panel
        propertiesPanel.removeAll();

        // Counts
        propertiesPanel.add(new JLabel("Vertices: " + vertexList.size()));
        propertiesPanel.add(new JLabel("Edges: " + edgeList.size()));

        // Graph Density
        double density = 0;
        if (vertexList.size() > 1) {
            density = (2.0 * edgeList.size()) / (vertexList.size() * (vertexList.size() - 1));
        }
        propertiesPanel.add(new JLabel(String.format("Density: %.2f", density)));

        // Diameter / Radius (via Floyd–Warshall)
        if (!vertexList.isEmpty()) {
            final int INF = 1_000_000_000;
            int[][] dist = gP.floydWarshall(vertexList, edgeList);
            boolean hasInf = false;
            int diameter = 0;
            int radius = Integer.MAX_VALUE;
            for (int i = 0; i < vertexList.size(); i++) {
                int ecc = 0;
                boolean any = false;
                for (int j = 0; j < vertexList.size(); j++) {
                    if (i == j) continue;
                    if (dist[i][j] >= INF) { hasInf = true; continue; }
                    ecc = Math.max(ecc, dist[i][j]);
                    any = true;
                }
                if (any) {
                    diameter = Math.max(diameter, ecc);
                    radius = Math.min(radius, ecc);
                }
            }
            if (radius == Integer.MAX_VALUE) {
                propertiesPanel.add(new JLabel("Diameter: N/A"));
                propertiesPanel.add(new JLabel("Radius: N/A"));
            } else {
                propertiesPanel.add(new JLabel("Diameter: " + diameter));
                propertiesPanel.add(new JLabel("Radius: " + radius));
            }
            if (hasInf) {
                propertiesPanel.add(new JLabel("Note: Graph not fully connected (unreachable pairs ignored)."));
            }
        }

        // Node Degrees
        Vertex selectedVertex = null;
        for (Vertex v : vertexList) {
            if (v.wasClicked) {
                selectedVertex = v;
                break;
            }
        }

        if (selectedVertex != null) {
            propertiesPanel.add(new JLabel("Node " + selectedVertex.name + " Degree: " + selectedVertex.getDegree()));
        } else {
            propertiesPanel.add(new JLabel("Node Degrees (All):"));
            for (Vertex v : vertexList) {
                propertiesPanel.add(new JLabel("  " + v.name + ": " + v.getDegree()));
            }
        }

        propertiesPanel.revalidate();
        propertiesPanel.repaint();

        canvas.repaint();
    }

    public void setVisible(boolean visible) {
        if (graphic == null) {
            Dimension size = canvas.getSize();
            canvasImage = canvas.createImage(size.width, size.height);
            canvasImage2 = canvas.createImage(size.width, size.height);
            graphic = (Graphics2D) canvasImage.getGraphics();
            graphic.setColor(backgroundColour);
            graphic.fillRect(0, 0, size.width, size.height);
            graphic.setColor(Color.black);
        }
        frame.setVisible(visible);
    }

    public boolean isVisible() {
        return frame.isVisible();
    }

    public void erase() {
        graphic.clearRect(0, 0, width, height);
    }

    public void erase(int x, int y, int x1, int y2) {
        graphic.clearRect(x, y, x1, y2);
    }

    public void drawString(String text, int x, int y, float size) {
        Font orig = graphic.getFont();
        graphic.setFont(graphic.getFont().deriveFont(1, size));
        graphic.drawString(text, x, y);
        graphic.setFont(orig);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        graphic.drawLine(x1, y1, x2, y2);
    }

    private int toWorldX(int sx) { return (int)Math.round((sx - panX) / zoom); }
    private int toWorldY(int sy) { return (int)Math.round((sy - panY) / zoom); }
    private boolean vertexHit(Vertex v, int sx, int sy) { return v.hasIntersection(toWorldX(sx), toWorldY(sy)); }
    private boolean edgeHit(Edge e, int sx, int sy) { return e.hasIntersection(toWorldX(sx), toWorldY(sy)); }

    private boolean hasDirectedEdge(Vertex a, Vertex b) {
        for (Edge e : edgeList) {
            if (e.vertex1 == a && e.vertex2 == b) return true;
        }
        return false;
    }

    private boolean hasAnyEdgeBetween(Vertex a, Vertex b) {
        for (Edge e : edgeList) {
            if ((e.vertex1 == a && e.vertex2 == b) || (e.vertex1 == b && e.vertex2 == a)) return true;
        }
        return false;
    }

    private void complementGraph() {
        int n = vertexList.size();
        if (n <= 1) return;
        // Build existence map
        boolean[][] exist = new boolean[n][n];
        for (Edge e : edgeList) {
            int u = vertexList.indexOf(e.vertex1);
            int v = vertexList.indexOf(e.vertex2);
            if (u < 0 || v < 0) continue;
            if (directionalityEnabled) {
                exist[u][v] = true;
            } else {
                exist[u][v] = exist[v][u] = true;
            }
        }
        Vector<Edge> newEdges = new Vector<>();
        if (directionalityEnabled) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    if (!exist[i][j]) {
                        Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                        e.isDirected = true;
                        e.weight = 1;
                        e.color = Color.BLACK;
                        newEdges.add(e);
                    }
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = i+1; j < n; j++) {
                    if (!exist[i][j]) {
                        Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                        e.isDirected = false;
                        e.weight = 1;
                        e.color = Color.BLACK;
                        newEdges.add(e);
                    }
                }
            }
        }
        edgeList = newEdges;
        // Rebuild connectedVertices symmetrically
        for (Vertex v : vertexList) v.connectedVertices.clear();
        for (Edge e : edgeList) {
            e.vertex1.addVertex(e.vertex2);
            e.vertex2.addVertex(e.vertex1);
        }
        refresh();
    }

    private void computeAndShowDiameterRadius() {
        int n = vertexList.size();
        if (n == 0) {
            JOptionPane.showMessageDialog(frame, "Graph is empty.", "Diameter/Radius", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int[][] dist = gP.floydWarshall(vertexList, edgeList);
        final int INF = 1_000_000_000;
        boolean hasInf = false;
        int diameter = 0;
        int radius = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int ecc = 0;
            boolean any = false;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (dist[i][j] >= INF) { hasInf = true; continue; }
                ecc = Math.max(ecc, dist[i][j]);
                any = true;
            }
            if (any) {
                diameter = Math.max(diameter, ecc);
                radius = Math.min(radius, ecc);
            }
        }
        String note = hasInf ? "\nNote: Graph is not fully connected (under current directionality). Unreachable pairs ignored." : "";
        if (radius == Integer.MAX_VALUE) {
            JOptionPane.showMessageDialog(frame, "No finite paths between vertices." + note, "Diameter/Radius", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Diameter: " + diameter + "\nRadius: " + radius + note, "Diameter/Radius", JOptionPane.INFORMATION_MESSAGE);
        }
        selectedWindow = 1;
        erase();
        refresh();
    }

    // ===== Import From Matrix GUI =====
    private void openImportDialog() {
        JDialog dlg = new JDialog(frame, "Import Graph", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(10,10));

        JPanel top = new JPanel(new BorderLayout(5,5));
        top.setBorder(BorderFactory.createTitledBorder("Vertex Names (one per line)"));
        JTextArea namesArea = new JTextArea(8, 24);
        JScrollPane namesScroll = new JScrollPane(namesArea);
        top.add(namesScroll, BorderLayout.CENTER);
        JButton buildBtn = new JButton("Build Matrix");
        top.add(buildBtn, BorderLayout.SOUTH);

        JPanel center = new JPanel(new GridLayout(1,1));
        center.setBorder(BorderFactory.createTitledBorder("Adjacency Matrix"));
        JTable adjTable = new JTable();
        JScrollPane adjScroll = new JScrollPane(adjTable);
        center.add(adjScroll);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        JCheckBox chkDirected = new JCheckBox("Directed (use asymmetric matrix)");
        JCheckBox chkWeights = new JCheckBox("Enable Weights");
        south.add(chkDirected);
        south.add(chkWeights);

        JPanel weightPanel = new JPanel(new BorderLayout());
        weightPanel.setBorder(BorderFactory.createTitledBorder("Weights (optional)"));
        JTable weightTable = new JTable();
        JScrollPane weightScroll = new JScrollPane(weightTable);
        weightPanel.add(weightScroll, BorderLayout.CENTER);
        weightPanel.setVisible(false);
        south.add(weightPanel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton importBtn = new JButton("Import");
        buttons.add(cancel);
        buttons.add(importBtn);

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(center, BorderLayout.CENTER);
        dlg.add(south, BorderLayout.EAST);
        dlg.add(buttons, BorderLayout.SOUTH);

        final int[] nHolder = new int[]{0};

        class BooleanTableModel extends DefaultTableModel {
            BooleanTableModel(int rows, int cols) { super(rows, cols); }
            @Override public Class<?> getColumnClass(int columnIndex) { return Boolean.class; }
            @Override public boolean isCellEditable(int row, int column) { return true; }
        }

        class IntegerTableModel extends DefaultTableModel {
            IntegerTableModel(int rows, int cols) { super(rows, cols); }
            @Override public Class<?> getColumnClass(int columnIndex) { return Integer.class; }
            @Override public boolean isCellEditable(int row, int column) { return true; }
        }

        buildBtn.addActionListener(ev -> {
            String[] lines = namesArea.getText().split("\n");
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String s : lines) {
                String t = s.trim();
                if (!t.isEmpty()) names.add(t);
            }
            if (names.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Please enter at least one vertex name.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int n = names.size();
            nHolder[0] = n;
            Object[] headers = names.toArray(new Object[0]);
            BooleanTableModel model = new BooleanTableModel(0, 0);
            model.setColumnIdentifiers(headers);
            model.setRowCount(n);
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    model.setValueAt(Boolean.FALSE, r, c);
                }
            }
            adjTable.setModel(model);
            adjTable.getTableHeader().setReorderingAllowed(false);
            adjTable.getTableHeader().repaint();
            adjScroll.setViewportView(adjTable);

            // Build default weight table too
            IntegerTableModel wModel = new IntegerTableModel(0, 0);
            wModel.setColumnIdentifiers(headers);
            wModel.setRowCount(n);
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    wModel.setValueAt(Integer.valueOf(1), r, c);
                }
            }
            weightTable.setModel(wModel);
            weightTable.getTableHeader().setReorderingAllowed(false);
            weightTable.getTableHeader().repaint();
            weightScroll.setViewportView(weightTable);
        });

        chkWeights.addActionListener(ev -> weightPanel.setVisible(chkWeights.isSelected()));
        cancel.addActionListener(ev -> dlg.dispose());

        importBtn.addActionListener(ev -> {
            if (nHolder[0] <= 0 || adjTable.getModel() == null || adjTable.getRowCount() == 0) {
                JOptionPane.showMessageDialog(dlg, "Please click 'Build Matrix' after entering vertex names.", "Build Matrix First", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int n = nHolder[0];
            // Collect names again (trimmed, non-empty) to keep consistent ordering
            String[] lines2 = namesArea.getText().split("\n");
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String s : lines2) {
                String t = s.trim();
                if (!t.isEmpty()) names.add(t);
            }
            if (names.size() != n) {
                JOptionPane.showMessageDialog(dlg, "Vertex list changed. Click 'Build Matrix' again.", "Rebuild Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Build new graph
            saveState();
            edgeList.clear();
            vertexList.clear();
            for (int i = 0; i < n; i++) {
                Vertex v = new Vertex(names.get(i), 100, 100);
                vertexList.add(v);
            }

            boolean directed = chkDirected.isSelected();
            boolean withWeights = chkWeights.isSelected();

            // Build edges from adjacency
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    boolean present = Boolean.TRUE.equals(adjTable.getValueAt(i, j));
                    if (!directed) {
                        if (i < j) {
                            boolean presentPair = present || Boolean.TRUE.equals(adjTable.getValueAt(j, i));
                            if (presentPair) {
                                Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                                e.isDirected = false;
                                if (withWeights) {
                                    Object w1 = weightTable.getValueAt(i, j);
                                    Object w2 = weightTable.getValueAt(j, i);
                                    int w = 1;
                                    try { w = Integer.parseInt(String.valueOf(w1)); } catch (Exception ex) {
                                        try { w = Integer.parseInt(String.valueOf(w2)); } catch (Exception ex2) { w = 1; }
                                    }
                                    e.weight = w;
                                }
                                edgeList.add(e);
                                vertexList.get(i).addVertex(vertexList.get(j));
                                vertexList.get(j).addVertex(vertexList.get(i));
                            }
                        }
                    } else { // directed
                        if (present && i != j) {
                            Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                            e.isDirected = true;
                            if (withWeights) {
                                Object w1 = weightTable.getValueAt(i, j);
                                int w = 1;
                                try { w = Integer.parseInt(String.valueOf(w1)); } catch (Exception ex) { w = 1; }
                                e.weight = w;
                            }
                            edgeList.add(e);
                            vertexList.get(i).addVertex(vertexList.get(j));
                            vertexList.get(j).addVertex(vertexList.get(i));
                        }
                    }
                }
            }

            // Apply toggles to match user choice
            directionalityEnabled = directed;
            weightsEnabled = withWeights;

            arrangeVertices();
            erase();
            refresh();
            dlg.dispose();
        });

        dlg.setSize(900, 600);
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    private String getInstructionForSelectedTool() {
        switch (selectedTool) {
            case 1:
                return "Add Vertex: Click on the canvas to add a vertex.";
            case 2:
                return "Add Edges: Drag between nodes to connect; (double-click on the selected node) for a self-loop.";
            case 3:
                return "Grab Tool: Click and drag a node to move it.";
            case 4:
                return "Remove Tool: Single-click to select a node/edge; (double-click on the selected node/edge) to delete it.";
            case 5:
                return "Set Node Color: Choose a color, then click a node to apply it.";
            case 6:
                return "Set Edge Color: Choose a color, then click an edge to apply it.";
            case 7:
                return "Invert Edge Direction: Click a directed edge to reverse direction (when directionality is enabled).";
            case 8:
                return "Set Edge Weight: Click an edge to set its weight (Enable Weights first).";
            case 9:
                return "Set Vertex Label: Click a node to set or edit its label (name).";
            case 10:
                return "Highlight Tool: Click an edge to highlight it; click a node to highlight it and its incident edges.";
            default:
                return "Select a tool from the left toolbar to see instructions.";
        }
    }

    // Draw a full-width instruction area at the bottom of the work canvas with word wrapping
    private void drawInstructionsOverlay(Graphics g) {
        if (!showInstructionsOverlay) return;

        String text = getInstructionForSelectedTool();
        if (text == null || text.isEmpty()) return;

        int pad = 10;
        int canvasW = canvas.getWidth();
        int canvasH = canvas.getHeight();
        FontMetrics fm = g.getFontMetrics();
        int maxWidth = Math.max(50, canvasW - pad * 2);

        java.util.List<String> lines = wrapText(text, fm, maxWidth);
        int lineHeight = fm.getHeight();
        int blockHeight = lineHeight * lines.size() + pad * 2;
        int yTop = canvasH - blockHeight; // stick to bottom

        // Background bar (opaque white)
        Color prev = g.getColor();
        g.setColor(Color.WHITE);
        g.fillRect(0, yTop, canvasW, blockHeight);
        g.setColor(Color.BLACK);

        // Draw lines
        int y = yTop + pad + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, pad, y);
            y += lineHeight;
        }
        g.setColor(prev);
    }

    // Simple word-wrap using FontMetrics
    private java.util.List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            String candidate = current.length() == 0 ? w : current + " " + w;
            if (fm.stringWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (current.length() > 0) lines.add(current.toString());
                // If a single word is too long, hard-cut it
                if (fm.stringWidth(w) > maxWidth) {
                    String cut = w;
                    String part = "";
                    for (int i = 0; i < cut.length(); i++) {
                        String tryPart = part + cut.charAt(i);
                        if (fm.stringWidth(tryPart) > maxWidth) {
                            if (!part.isEmpty()) lines.add(part);
                            part = String.valueOf(cut.charAt(i));
                        } else {
                            part = tryPart;
                        }
                    }
                    current.setLength(0);
                    current.append(part);
                } else {
                    current.setLength(0);
                    current.append(w);
                }
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private class CanvasPane extends JPanel {

        public void paint(Graphics g) {
            switch (selectedWindow) {
                case 0: {   //graph window
                    // Draw the base canvas image first (work area)
                    g.drawImage(canvasImage, 0, 0, null); // layer 1
                    g.setColor(Color.black);

                    // Full-width, bottom instructions with wrapping inside the work canvas
                    drawInstructionsOverlay(g);
                    break;
                }
                case 1: {   //properties window
                    canvasImage2.getGraphics().clearRect(0, 0, width, height); //clear
                    gP.drawAdjacencyMatrix(canvasImage2.getGraphics(), vertexList, width / 2 + 50, 50);//draw adjacency matrix
                    gP.drawDistanceMatrix(canvasImage2.getGraphics(), vertexList, width / 2 + 50, height / 2 + 50);//draw distance matrix
                    g.drawImage(canvasImage2, 0, 0, null); //layer 1
                    drawString("Graph disconnects when nodes in color red are removed.", 100, height - 30, 20);
                    // Removed outdated diameter console note
                    g.drawImage(canvasImage.getScaledInstance(width / 2, height / 2, Image.SCALE_SMOOTH), 0, 0, null); //layer 1
                    g.draw3DRect(0, 0, width / 2, height / 2, true);
                    g.setColor(Color.black);

                    break;
                }
            }

        }
    }

    private void showInstructions() {
        String instructions = "";
        switch (selectedTool) {
            case 1:
                instructions = "Add Vertex: Click to add a new vertex.";
                break;
            case 2:
                instructions = "Add Edges: Drag between nodes to connect. Double-click a node for a self-loop.";
                break;
            case 3:
                instructions = "Grab Tool: Click and drag to move vertices.";
                break;
            case 4:
                instructions = "Remove Tool: Double-click a node or edge to remove it.";
                break;
            case 5:
                instructions = "Set Node Color: Select a color, then click a node to apply it.";
                break;
            case 6:
                instructions = "Set Edge Color: Select a color, then click an edge to apply it.";
                break;
            case 7:
                instructions = "Enable Directionality: Toggle directed edges on or off.";
                break;
            case 8:
                instructions = "Invert Edge Direction: Click a directed edge to reverse its direction.";
                break;
            case 9:
                instructions = "Auto Arrange Vertices: Arrange vertices in a circle.";
                break;
            case 10:
                instructions = "Undo: Undo the last action.";
                break;
            case 11:
                instructions = "Redo: Redo the last undone action.";
                break;
            case 12:
                instructions = "Remove All: Clear the canvas, removing all vertices and edges.";
                break;
        }
        // Display instructions at the bottom right
        drawString(instructions, width - 300, height - 20, 12);
    }
}
