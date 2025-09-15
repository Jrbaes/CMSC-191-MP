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
    // Local toggle state used only if VersionControl toggle is not available
    private boolean toggleWantsRedo = false;
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
            {"Add Vertex", null, "Click to add a new vertex."},
            {"Add Edges", null, "Drag between nodes to connect. (Double-click on the selected node for a self-loop)"},
            {"Grab Tool", null, "Click and drag to move vertices."},
            {"Pan View", null, "Click and drag to pan the view."},
            {"Zoom In", null, "Zoom in the view."},
            {"Zoom Out", null, "Zoom out the view."},
            {"Reset View", null, "Reset pan and zoom back to default."},
            {"Highlight Tool", null, "Click an edge to highlight it; click a node to highlight it and its incident edges."},
            {"Remove Tool", null, "Double-click on a node or edge to remove it."},
            {"Set Node Color", null, "Select a color, then click a node to apply it."},
            {"Set Vertex Label", null, "Click a node to set or edit its label (name)."},
            {"Set Edge Color", null, "Select a color, then click an edge to apply it."},
            {"Enable Directionality", null, "Toggle directed edges on or off."},
            {"Invert Edge Direction", null, "Click an edge to reverse its direction. Double-click arrow to remove direction."},
            {"Enable Weights", null, "Toggle showing and editing edge weights."},
            {"Set Edge Weight", null, "Click an edge to set its weight (Enable Weights first)."},
            {"Shortest Path (Bellman-Ford)", null, "Click source, then target (handles negative weights)."},
            {"Graph Complement", null, "Replace the graph with its complement (respects directionality setting)."},
            {"Auto Arrange Vertices", null, "Arrange vertices in a circle."},
            {"Undo", null, "Undo the last action."},
            {"Redo", null, "Redo the last undone action."},
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
        item.addActionListener(new MenuListener());
        menuOptions1.add(item);
        item = new JMenuItem("Save to File");
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

        // Clear file-based 2-version version control on startup
        VersionControl.clear();
        
        // Clear undo/redo stacks on program initialization
        undoStack.clear();
        redoStack.clear();

    }

    class InputListener implements MouseListener, MouseMotionListener {

        @Override
        public void mouseClicked(MouseEvent e) {

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
                                Edge hitEdge = null;
                                for (Edge edge : edgeList) {
                                    int ht = edgeHitDetail(edge, e.getX(), e.getY());
                                    if (ht > 0) { hitEdge = edge; break; }
                                }
                                if (hitEdge != null) {
                                    // Remove ONLY the clicked directed edge, regardless of arrow or line body
                                    Vertex a = hitEdge.vertex1, b = hitEdge.vertex2;
                                    edgeList.remove(hitEdge);
                                    if (!hasAnyEdgeBetween(a, b)) {
                                        a.connectedVertices.remove(b);
                                        if (a != b) b.connectedVertices.remove(a);
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
                            int ht = edgeHitDetail(ed, e.getX(), e.getY());
                            if (ht == 2) {
                                // Arrow head: highlight only that direction and its endpoints
                                ed.wasClicked = true;
                                ed.vertex1.wasClicked = true;
                                ed.vertex2.wasClicked = true;
                                highlighted = true;
                                break;
                            } else if (ht == 1) {
                                // Line body: highlight only THIS directed edge and endpoints
                                ed.wasClicked = true;
                                ed.vertex1.wasClicked = true;
                                ed.vertex2.wasClicked = true;
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
                    case 14: { // Invert/Remove Edge Direction
                        if (!directionalityEnabled) break; // only relevant in directed mode
                        for (Edge edge : edgeList) {
                            if (edgeHit(edge, e.getX(), e.getY())) {
                                // Always invert the direction of the clicked edge
                                Vertex tmp = edge.vertex1; edge.vertex1 = edge.vertex2; edge.vertex2 = tmp;
                                edge.isDirected = true;
                                refresh();
                                break;
                            }
                        }
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
                                            // In undirected (visual) mode, enforce equal weights on opposite direction
                                            if (!directionalityEnabled) {
                                                Edge rev = findDirectedEdge(edge.vertex2, edge.vertex1);
                                                if (rev != null) rev.weight = w;
                                            }
                                            // If currently viewing Properties, recompute weighted distances immediately
                                            if (selectedWindow == 1) {
                                                gP.generateDistanceMatrixWeighted(vertexList, getEffectiveEdgesForAlgorithms());
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
                    case 12: { // Shortest Path (Bellman-Ford)
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
                                Vector<Edge> effEdges = (weightsEnabled || directionalityEnabled)
                                        ? getEffectiveEdgesForAlgorithms()
                                        : getUnweightedEdgesForAlgorithms();
                                // Use Bellman-Ford to support negative weights
                                int[] prev = gP.bellmanFordPredecessor(vertexList, effEdges, srcIdx);
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
                                                highlightEdgeBetween(a, b);
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
            // Capture state after any potential mutations from the click
            saveState();

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
            if (selectedWindow == 0 && vertexList.size() > 0) {
                switch (selectedTool) {
                    case 2: {
                        Vertex parentV = vertexList.get(clickedVertexIndex);
                        for (Vertex v : vertexList) {
                            if (vertexHit(v, e.getX(), e.getY())) {
                                if (v == parentV) break; // ignore self

                                if (directionalityEnabled) {
                                    // Block duplicate in same direction
                                    if (hasDirectedEdge(parentV, v)) {
                                        JOptionPane.showMessageDialog(frame, "An edge in this direction already exists.", "Duplicate Edge", JOptionPane.WARNING_MESSAGE);
                                        break;
                                    }
                                    // Allow adding even if reverse exists
                                    Edge edge = new Edge(parentV, v);
                                    edge.isDirected = true;
                                    if (!parentV.connectedToVertex(v)) parentV.addVertex(v);
                                    if (!v.connectedToVertex(parentV)) v.addVertex(parentV);
                                    edgeList.add(edge);
                                    break;
                                } else {
                                    // Undirected visual mode: block if any edge exists between the pair
                                    if (hasAnyEdgeBetween(parentV, v)) {
                                        JOptionPane.showMessageDialog(frame, "An edge between these vertices already exists.", "Duplicate Edge", JOptionPane.WARNING_MESSAGE);
                                        break;
                                    }
                                    Edge edge = new Edge(parentV, v);
                                    edge.isDirected = false;
                                    if (!parentV.connectedToVertex(v)) parentV.addVertex(v);
                                    if (!v.connectedToVertex(parentV)) v.addVertex(parentV);
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
            // Capture state after potential mutations on release
            saveState();
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
            int oldTool = selectedTool;
            
            // Tool selection commands
            if (command.equals("Add Vertex")) selectedTool = 1;
            else if (command.equals("Add Edges")) selectedTool = 2;
            else if (command.equals("Grab Tool")) selectedTool = 3;
            else if (command.equals("Pan View")) selectedTool = 11;
            else if (command.equals("Remove Tool")) selectedTool = 4;
            else if (command.equals("Set Node Color")) {
                selectedTool = 5;
                selectedColor = JColorChooser.showDialog(frame, "Choose Node Color", Color.BLACK);
            }
            else if (command.equals("Set Edge Color")) {
                selectedTool = 6;
                selectedColor = JColorChooser.showDialog(frame, "Choose Edge Color", Color.BLACK);
            }
            else if (command.equals("Highlight Tool")) selectedTool = 10;
            else if (command.equals("Set Vertex Label")) selectedTool = 9;
            else if (command.equals("Set Edge Weight")) selectedTool = 8;
            else if (command.equals("Shortest Path (Bellman-Ford)")) selectedTool = 12;
            else if (command.equals("Invert Edge Direction")) selectedTool = 14;
            else if (command.equals("Undo")) { undo(); }
            else if (command.equals("Redo")) { redo(); }
            
            // View commands
            else if (command.equals("Zoom In")) {
                saveState();
                zoom = Math.min(5.0, zoom * 1.2);
                refresh();
            }
            else if (command.equals("Zoom Out")) {
                saveState();
                zoom = Math.max(0.2, zoom / 1.2);
                refresh();
            }
            else if (command.equals("Reset View")) {
                saveState();
                zoom = 1.0; panX = 0; panY = 0; 
                refresh();
            }
            
            // Graph operations
            else if (command.equals("Enable Directionality")) {
                boolean turningOn = !directionalityEnabled;
                if (turningOn) {
                    saveState();
                    directionalityEnabled = true;
                    // Ensure reverse arcs exist and both directions are explicitly present
                    Vector<Edge> toAdd = new Vector<>();
                    for (Edge edge : edgeList) {
                        edge.isDirected = true; // underlying representation remains directed
                        if (edge.vertex1 != edge.vertex2 && !hasDirectedEdge(edge.vertex2, edge.vertex1)) {
                            Edge rev = new Edge(edge.vertex2, edge.vertex1);
                            rev.color = edge.color;
                            rev.isDirected = true;
                            rev.weight = edge.weight;
                            toAdd.add(rev);
                        }
                    }
                    edgeList.addAll(toAdd);
                    refresh();
                } else {
                    // Enforce symmetric weights before disabling
                    if (!checkSymmetricWeightsOrWarn()) {
                        // Keep directionality enabled; highlight handled in the method
                        return;
                    }
                    saveState();
                    directionalityEnabled = false;
                    // Do not merge or drop arcs; just hide arrows in rendering (handled in refresh())
                    refresh();
                }
            }
            else if (command.equals("Enable Weights")) {
                saveState();
                weightsEnabled = !weightsEnabled;
                refresh();
            }
            else if (command.equals("Auto Arrange Vertices")) {
                saveState();
                arrangeVertices();
            }
            else if (command.equals("Remove All")) {
                saveState();
                vertexList.clear();
                edgeList.clear();
                refresh();
            }
            else if (command.equals("Graph Complement")) {
                saveState();
                complementGraph();
            }
            
            // File operations
            else if (command.equals("Save to File")) {
                int returnValue = fileManager.jF.showSaveDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    fileManager.saveFile(vertexList, edgeList, fileManager.jF.getSelectedFile());
                    System.out.println(fileManager.jF.getSelectedFile());
                }
            }
            else if (command.equals("Open File")) {
                int returnValue = fileManager.jF.showOpenDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    // Clear version control and load fresh graph
                    VersionControl.clear();
                    // Clear undo/redo stacks when opening a file
                    undoStack.clear();
                    redoStack.clear();
                    loadFile(fileManager.loadFile(fileManager.jF.getSelectedFile()));
                    System.out.println(fileManager.jF.getSelectedFile());
                    selectedWindow = 0;
                }
            }
            else if (command.equals("Import From Matrix...")) {
                openImportDialog();
            }
            
            // UI settings
            else if (command.equals("Dark Mode")) {
                darkMode = !darkMode;
                if (darkMode) {
                    backgroundColour = new Color(60, 63, 65);
                    graphic.setBackground(backgroundColour);
                    graphic.setColor(Color.WHITE);
                } else {
                    backgroundColour = Color.WHITE;
                    graphic.setBackground(backgroundColour);
                    graphic.setColor(Color.BLACK);
                }
                refresh();
            }
            
            // Clear all vertices and edges
            else if (command.equals("Remove All")) {
                edgeList.removeAllElements();
                vertexList.removeAllElements();
                clickedVertexIndex = 0;
                erase();
            }
            
            // Window view selection
            else if (command.equals("Graph")) {
                selectedWindow = 0;
                erase();
            } 
            else if (command.equals("Properties")) {
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

            // If we just switched away from Highlight Tool, clear all highlight states
            if (oldTool == 10 && selectedTool != 10) {
                for (Vertex v : vertexList) v.wasClicked = false;
                for (Edge ed : edgeList) ed.wasClicked = false;
                refresh();
            }
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
        // Don't save state if nothing has changed
        GraphState newState = new GraphState(vertexList, edgeList, directionalityEnabled, 
                                          weightsEnabled, selectedTool, zoom, panX, panY);
        
        if (!undoStack.isEmpty() && isSameState(undoStack.peek(), newState)) {
            return; // No changes to save
        }
        
        // Clear backup stack when making new changes (standard undo/redo behavior)
        redoStack.clear();
        
        // Push new state to main stack
        undoStack.push(newState);

        // Also capture to file-based 2-version version control
        VersionControl.captureCurrent(vertexList, edgeList, directionalityEnabled, weightsEnabled,
                                      selectedTool, zoom, panX, panY, fileManager);
    }

    private void loadState(GraphState state) {
        if (state == null) return;
        
        // Update graph state
        this.vertexList = state.vertexList;
        this.edgeList = state.edgeList;
        this.directionalityEnabled = state.directionalityEnabled;
        this.weightsEnabled = state.weightsEnabled;
        this.selectedTool = state.selectedTool;
        this.zoom = state.zoom;
        this.panX = state.panX;
        this.panY = state.panY;
        
        // Update UI elements to reflect the new state
        updateToolbarSelection();
    }

    // Public entry point for VersionControl snapshots to apply a saved state
    public void applyGraphAndUI(Vector<Vertex> v, Vector<Edge> e,
                                boolean dir, boolean weights, int tool,
                                double zoom, int panX, int panY) {
        this.vertexList = v;
        this.edgeList = e;
        this.directionalityEnabled = dir;
        this.weightsEnabled = weights;
        this.selectedTool = tool;
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
        updateToolbarSelection();
        refresh();
    }
    
    private void updateToolbarSelection() {
        // Update the selected state of toolbar buttons based on current tool
        // This ensures the UI reflects the current state after undo/redo
        // Implementation depends on how your toolbar is set up
    }
    
    private boolean isSameState(GraphState state1, GraphState state2) {
        if (state1 == null || state2 == null) return false;
        
        // Compare graph state
        if (state1.directionalityEnabled != state2.directionalityEnabled ||
            state1.weightsEnabled != state2.weightsEnabled ||
            state1.selectedTool != state2.selectedTool ||
            state1.zoom != state2.zoom ||
            state1.panX != state2.panX ||
            state1.panY != state2.panY) {
            return false;
        }
        
        // Compare vertices and edges
        return compareVerticesAndEdges(state1, state2);
    }
    
    private boolean compareVerticesAndEdges(GraphState state1, GraphState state2) {
        Vector<Vertex> v1 = state1.vertexList;
        Vector<Vertex> v2 = state2.vertexList;
        Vector<Edge> e1 = state1.edgeList;
        Vector<Edge> e2 = state2.edgeList;
        
        if (v1.size() != v2.size() || e1.size() != e2.size()) {
            return false;
        }
        
        // Compare vertex properties
        for (int i = 0; i < v1.size(); i++) {
            Vertex vv1 = v1.get(i);
            Vertex vv2 = v2.get(i);
            
            if (!vv1.name.equals(vv2.name) || 
                vv1.location.x != vv2.location.x || 
                vv1.location.y != vv2.location.y ||
                (vv1.color != null ? !vv1.color.equals(vv2.color) : vv2.color != null)) {
                return false;
            }
            
            // Check connections count (order doesn't matter)
            if (vv1.connectedVertices.size() != vv2.connectedVertices.size()) {
                return false;
            }
        }
        
        // Compare edge properties including direction
        for (int i = 0; i < e1.size(); i++) {
            Edge ee1 = e1.get(i);
            Edge ee2 = e2.get(i);
            
            if (ee1.vertex1 != ee2.vertex1 || 
                ee1.vertex2 != ee2.vertex2 ||
                ee1.isDirected != ee2.isDirected ||
                ee1.weight != ee2.weight ||
                (ee1.color != null ? !ee1.color.equals(ee2.color) : ee2.color != null)) {
                return false;
            }
        }
        
        return true;
    }

    private void undo() {
        // Prefer file-based 2-version undo
        if (VersionControl.undo(this, fileManager)) {
            return;
        }
        // Fallback to in-memory stack if available
        if (!undoStack.isEmpty()) {
            // Save current state to backup stack
            redoStack.push(new GraphState(vertexList, edgeList, directionalityEnabled, 
                                       weightsEnabled, selectedTool, zoom, panX, panY));
            
            // Pop from main stack and restore previous state
            GraphState previousState = undoStack.pop();
            loadState(previousState);
            refresh();
        }
    }

    private void redo() {
        // Prefer file-based 2-version redo
        if (VersionControl.redo(this, fileManager)) {
            return;
        }
        // Fallback to in-memory stack if available
        if (!redoStack.isEmpty()) {
            // Save current state to main stack
            undoStack.push(new GraphState(vertexList, edgeList, directionalityEnabled, 
                                       weightsEnabled, selectedTool, zoom, panX, panY));
            
            // Pop from backup stack and restore next state
            GraphState nextState = redoStack.pop();
            loadState(nextState);
            refresh();
        }
    }

    private void toggleUndoRedo() {
        // Prefer file-based toggle
        if (VersionControl.toggle(this, fileManager)) {
            // Flip local toggle indicator too to keep behavior consistent if we fallback later
            toggleWantsRedo = !toggleWantsRedo;
            return;
        }
        // Fallback: toggle between undo and redo using in-memory stacks
        if (!toggleWantsRedo) {
            // Try UNDO
            if (!undoStack.isEmpty()) {
                redoStack.push(new GraphState(vertexList, edgeList, directionalityEnabled, 
                                           weightsEnabled, selectedTool, zoom, panX, panY));
                GraphState prev = undoStack.pop();
                loadState(prev);
                refresh();
                toggleWantsRedo = true;
            }
        } else {
            // Try REDO
            if (!redoStack.isEmpty()) {
                undoStack.push(new GraphState(vertexList, edgeList, directionalityEnabled, 
                                           weightsEnabled, selectedTool, zoom, panX, panY));
                GraphState next = redoStack.pop();
                loadState(next);
                refresh();
                toggleWantsRedo = false;
            }
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

        // Draw edges. If directionality is disabled, we hide arrows; if both directions exist, offset arrowheads.
        for (Edge e : edgeList) {
            boolean hasOpposite = directionalityEnabled && e.isDirected && hasDirectedEdge(e.vertex2, e.vertex1);
            if (directionalityEnabled && e.isDirected && hasOpposite) {
                // Offset arrowheads to visually separate opposite directions.
                // Always place the arrow on the clockwise (right-hand) side of its direction.
                int side = +1; // clockwise side relative to direction v1->v2
                e.draw(g2, 10, side);
            } else {
                // Hide arrows when directionality is disabled (visual only)
                boolean orig = e.isDirected;
                if (!directionalityEnabled) e.isDirected = false;
                e.draw(g2);
                e.isDirected = orig;
            }

            // Draw weight label if enabled; offset when both directions exist
            if (weightsEnabled && e.vertex1 != null && e.vertex2 != null) {
                Color prev = g2.getColor();
                g2.setColor(darkMode ? Color.WHITE : Color.BLACK);
                
                if (e.vertex1 == e.vertex2) {
                    // Self-loop: draw weight above the loop
                    int x = e.vertex1.location.x;
                    int y = e.vertex1.location.y - 45; // Above the self-loop circle
                    g2.drawString(Integer.toString(e.weight), x + 6, y - 6);
                } else {
                    // Regular edge
                    int x1 = e.vertex1.location.x, y1 = e.vertex1.location.y;
                    int x2 = e.vertex2.location.x, y2 = e.vertex2.location.y;
                    int mx = (x1 + x2) / 2;
                    int my = (y1 + y2) / 2;
                    if (directionalityEnabled && e.isDirected && hasOpposite) {
                        double dx = x2 - x1, dy = y2 - y1;
                        double ang = Math.atan2(dy, dx);
                        double nx = -Math.sin(ang), ny = Math.cos(ang);
                        int side = +1; // clockwise side relative to direction v1->v2
                        int off = 10;
                        int lx = mx + (int)Math.round(side * off * nx);
                        int ly = my + (int)Math.round(side * off * ny);
                        g2.drawString(Integer.toString(e.weight), lx + 6, ly - 6);
                    } else {
                        g2.drawString(Integer.toString(e.weight), mx + 6, my - 6);
                    }
                }
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
            int[][] dist = gP.floydWarshall(vertexList, getUnweightedEdgesForAlgorithms());
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
    private boolean edgeHit(Edge e, int sx, int sy) { 
        int wx = toWorldX(sx);
        int wy = toWorldY(sy);
        // First check if we're clicking near the arrow head
        if (e.isDirected) {
            double dx = e.vertex2.location.x - e.vertex1.location.x;
            double dy = e.vertex2.location.y - e.vertex1.location.y;
            double edgeLength = Math.sqrt(dx*dx + dy*dy);
            if (edgeLength > 0) {
                // Check if click is within tolerance of the arrow head (last 20% of edge)
                double edgeX = e.vertex1.location.x + dx * 0.8;
                double edgeY = e.vertex1.location.y + dy * 0.8;
                // If both directions exist and arrows are offset, apply the same offset used in drawing
                boolean hasOpp = directionalityEnabled && hasDirectedEdge(e.vertex2, e.vertex1);
                if (hasOpp) {
                    double ang = Math.atan2(dy, dx);
                    double nx = -Math.sin(ang), ny = Math.cos(ang);
                    int side = +1; // clockwise side for v1->v2
                    int off = 10;
                    edgeX += side * off * nx;
                    edgeY += side * off * ny;
                }
                double distToArrow = Math.sqrt(Math.pow(wx - edgeX, 2) + Math.pow(wy - edgeY, 2));
                if (distToArrow < getArrowTolerancePx()) {
                    return true;
                }
            }
        }
        // Otherwise do line-body hit test (account for offset lines when both directions exist)
        if (directionalityEnabled && e.isDirected && hasDirectedEdge(e.vertex2, e.vertex1)) {
            int x1 = e.vertex1.location.x, y1 = e.vertex1.location.y;
            int x2 = e.vertex2.location.x, y2 = e.vertex2.location.y;
            double dx = x2 - x1, dy = y2 - y1;
            double ang = Math.atan2(dy, dx);
            double nx = -Math.sin(ang), ny = Math.cos(ang);
            int side = +1, off = 10;
            double ox1 = x1 + side * off * nx;
            double oy1 = y1 + side * off * ny;
            double ox2 = x2 + side * off * nx;
            double oy2 = y2 + side * off * ny;
            double dist = java.awt.geom.Line2D.ptSegDist(ox1, oy1, ox2, oy2, wx, wy);
            return dist < 5.0;
        } else {
            return e.hasIntersection(wx, wy);
        }
    }

    // 0 = miss, 1 = line body, 2 = arrow head (near v2)
    private int edgeHitDetail(Edge e, int sx, int sy) {
        int wx = toWorldX(sx);
        int wy = toWorldY(sy);
        boolean onLine;
        if (directionalityEnabled && e.isDirected && hasDirectedEdge(e.vertex2, e.vertex1)) {
            int x1 = e.vertex1.location.x, y1 = e.vertex1.location.y;
            int x2 = e.vertex2.location.x, y2 = e.vertex2.location.y;
            double dx = x2 - x1, dy = y2 - y1;
            double ang = Math.atan2(dy, dx);
            double nx = -Math.sin(ang), ny = Math.cos(ang);
            int side = +1, off = 10;
            double ox1 = x1 + side * off * nx;
            double oy1 = y1 + side * off * ny;
            double ox2 = x2 + side * off * nx;
            double oy2 = y2 + side * off * ny;
            double dist = java.awt.geom.Line2D.ptSegDist(ox1, oy1, ox2, oy2, wx, wy);
            onLine = dist < 5.0;
        } else {
            onLine = e.hasIntersection(wx, wy);
        }
        if (e.isDirected) {
            double dx = e.vertex2.location.x - e.vertex1.location.x;
            double dy = e.vertex2.location.y - e.vertex1.location.y;
            double edgeLength = Math.sqrt(dx*dx + dy*dy);
            if (edgeLength > 0) {
                double edgeX = e.vertex1.location.x + dx * 0.8;
                double edgeY = e.vertex1.location.y + dy * 0.8;
                boolean hasOpp = directionalityEnabled && hasDirectedEdge(e.vertex2, e.vertex1);
                if (hasOpp) {
                    double ang = Math.atan2(dy, dx);
                    double nx = -Math.sin(ang), ny = Math.cos(ang);
                    int side = +1; int off = 10;
                    edgeX += side * off * nx;
                    edgeY += side * off * ny;
                }
                double distToArrow = Math.sqrt(Math.pow(wx - edgeX, 2) + Math.pow(wy - edgeY, 2));
                if (distToArrow < getArrowTolerancePx()) return 2;
            }
        }
        return onLine ? 1 : 0;
    }

    // Convert 0.5 cm to pixels using the screen DPI
    private int getArrowTolerancePx() {
        int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
        double inches = 0.5 / 2.54; // 0.5 cm
        return (int)Math.round(dpi * inches);
    }

    private boolean hasDirectedEdge(Vertex a, Vertex b) {
        for (Edge e : edgeList) {
            if (e.vertex1 == a && e.vertex2 == b) return true;
        }
        return false;
    }

    private Edge findDirectedEdge(Vertex a, Vertex b) {
        for (Edge e : edgeList) {
            if (e.vertex1 == a && e.vertex2 == b) return e;
        }
        return null;
    }

    private boolean hasAnyEdgeBetween(Vertex a, Vertex b) {
        for (Edge e : edgeList) {
            if ((e.vertex1 == a && e.vertex2 == b) || (e.vertex1 == b && e.vertex2 == a)) return true;
        }
        return false;
    }

    // Build a temporary edge list for algorithm computation.
    // If directionality is disabled, treat all edges as undirected by forcing isDirected=false.
    private Vector<Edge> getEffectiveEdgesForAlgorithms() {
        Vector<Edge> eff = new Vector<>();
        for (Edge e : edgeList) {
            Edge c = new Edge(e.vertex1, e.vertex2);
            c.color = e.color;
            c.weight = e.weight;
            c.isDirected = directionalityEnabled ? e.isDirected : false;
            eff.add(c);
        }
        return eff;
    }

    // Build an unweighted, undirected view of the current edges for path algorithms: if any arc exists between a and b
    // in either direction, include a single undirected edge (weight=1). This ignores current visibility toggles, but
    // treats all connections as unit cost as requested.
    private Vector<Edge> getUnweightedEdgesForAlgorithms() {
        Vector<Edge> eff = new Vector<>();
        int n = vertexList.size();
        boolean[][] seen = new boolean[n][n];
        for (Edge e : edgeList) {
            int i = vertexList.indexOf(e.vertex1);
            int j = vertexList.indexOf(e.vertex2);
            if (i < 0 || j < 0) continue;
            int a = Math.min(i, j), b = Math.max(i, j);
            if (!seen[a][b]) {
                // Check if any arc exists between these two in either direction
                if (hasAnyEdgeBetween(vertexList.get(a), vertexList.get(b))) {
                    Edge c = new Edge(vertexList.get(a), vertexList.get(b));
                    c.color = e.color;
                    c.weight = 1; // unit cost
                    c.isDirected = false; // treat as undirected for algorithms
                    eff.add(c);
                    seen[a][b] = true;
                }
            }
        }
        return eff;
    }

    // Ensure that for every pair of opposite directed edges, weights are equal before disabling directionality.
    // Highlights offending edges and shows a blocking popup. Returns true if OK to disable.
    private boolean checkSymmetricWeightsOrWarn() {
        // Clear previous highlights
        for (Vertex v : vertexList) v.wasClicked = false;
        for (Edge ed : edgeList) ed.wasClicked = false;

        java.util.List<String> conflicts = new java.util.ArrayList<>();
        for (Edge e : edgeList) {
            if (e.vertex1 == null || e.vertex2 == null) continue;
            Edge rev = findDirectedEdge(e.vertex2, e.vertex1);
            if (rev != null && (e.weight != rev.weight)) {
                // Mark both edges
                e.wasClicked = true;
                rev.wasClicked = true;
                conflicts.add(e.vertex1.name + " -> " + e.vertex2.name + " (" + e.weight + ") vs " +
                               rev.vertex1.name + " -> " + rev.vertex2.name + " (" + rev.weight + ")");
            }
        }
        if (!conflicts.isEmpty()) {
            refresh();
            String msg = "Cannot disable directionality: some opposite edges have different weights.\n" +
                         "They have been highlighted. Please make the weights equal, then try again.\n\n" +
                         String.join("\n", conflicts);
            JOptionPane.showMessageDialog(frame, msg, "Directionality", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    // Highlight the visual edge(s) between two vertices respecting current directionality setting.
    private void highlightEdgeBetween(Vertex a, Vertex b) {
        boolean marked = false;
        for (Edge ed : edgeList) {
            if (ed.vertex1 == a && ed.vertex2 == b) { ed.wasClicked = true; marked = true; }
        }
        if (!directionalityEnabled) {
            for (Edge ed : edgeList) {
                if (ed.vertex1 == b && ed.vertex2 == a) { ed.wasClicked = true; }
            }
        } else if (!marked) {
            // fallback if only reverse arc exists
            for (Edge ed : edgeList) {
                if (ed.vertex1 == b && ed.vertex2 == a) { ed.wasClicked = true; break; }
            }
        }
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

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createTitledBorder("Adjacency Matrix"));
        JTable adjTable = new JTable();
        JScrollPane adjScroll = new JScrollPane(adjTable);
        JLabel dirNote = new JLabel(" ");
        dirNote.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        center.add(adjScroll, BorderLayout.CENTER);
        center.add(dirNote, BorderLayout.SOUTH);

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

        class AdjTableModel extends DefaultTableModel {
            AdjTableModel(int rows, int cols) { super(rows, cols); }
            @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex == 0 ? String.class : Boolean.class; }
            @Override public boolean isCellEditable(int row, int column) { return column > 0; }
        }
        class WTableModel extends DefaultTableModel {
            WTableModel(int rows, int cols) { super(rows, cols); }
            @Override public Class<?> getColumnClass(int columnIndex) { return columnIndex == 0 ? String.class : Integer.class; }
            @Override public boolean isCellEditable(int row, int column) { return column > 0; }
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

            // Build adjacency model with row label column
            AdjTableModel model = new AdjTableModel(0, 0);
            java.util.Vector<String> cols = new java.util.Vector<>();
            cols.add("Vertex");
            for (String nm : names) cols.add(nm);
            model.setColumnIdentifiers(cols);
            model.setRowCount(n);
            for (int r = 0; r < n; r++) {
                model.setValueAt(names.get(r), r, 0);
                for (int c = 1; c <= n; c++) model.setValueAt(Boolean.FALSE, r, c);
            }
            adjTable.setModel(model);
            adjTable.getTableHeader().setReorderingAllowed(false);
            adjScroll.setViewportView(adjTable);

                // Build weight table with row label column
                WTableModel wModel = new WTableModel(0, 0);
                wModel.setColumnIdentifiers(cols);
                wModel.setRowCount(n);
                for (int r = 0; r < n; r++) {
                    wModel.setValueAt(names.get(r), r, 0);
                    for (int c = 1; c <= n; c++) wModel.setValueAt("", r, c); // Start with blank, not 0
                }
            weightTable.setModel(wModel);
            weightTable.getTableHeader().setReorderingAllowed(false);
            weightScroll.setViewportView(weightTable);

                // Listeners: when undirected, mirror adjacency and weights; default weight=1 when adjacency set true, blank when false
                model.addTableModelListener(e2 -> {
                    if (e2.getColumn() <= 0 || e2.getFirstRow() < 0) return;
                    int r = e2.getFirstRow(); int c = e2.getColumn(); // c = 1..n
                    boolean val = Boolean.TRUE.equals(model.getValueAt(r, c));
                    // default weight updates
                    if (val && "".equals(String.valueOf(wModel.getValueAt(r, c)))) wModel.setValueAt(Integer.valueOf(1), r, c);
                    if (!val) wModel.setValueAt("", r, c);
                if (!chkDirected.isSelected()) {
                    // mirror symmetric
                    int mr = c - 1; int mc = r + 1;
                    if (mr >= 0 && mr < n && mc >= 1 && mc <= n) {
                        if (!Boolean.valueOf(val).equals(model.getValueAt(mr, mc))) model.setValueAt(val, mr, mc);
                        // mirror weights when edge exists
                        if (val) {
                            Object wv = wModel.getValueAt(r, c);
                            if (!String.valueOf(wv).equals(String.valueOf(wModel.getValueAt(mr, mc)))) wModel.setValueAt(wv, mr, mc);
                            } else {
                                wModel.setValueAt("", mr, mc);
                            }
                    }
                }
            });
            // Mirror weight edits for undirected if both sides are edges
            weightTable.getModel().addTableModelListener(e3 -> {
                if (e3.getColumn() <= 0 || e3.getFirstRow() < 0) return;
                if (chkDirected.isSelected()) return;
                int r = e3.getFirstRow(); int c = e3.getColumn();
                boolean edge = Boolean.TRUE.equals(model.getValueAt(r, c));
                if (!edge) return;
                int mr = c - 1; int mc = r + 1;
                if (mr >= 0 && mr < n && mc >= 1 && mc <= n) {
                    if (Boolean.TRUE.equals(model.getValueAt(mr, mc))) {
                        Object wv = weightTable.getModel().getValueAt(r, c);
                        if (!String.valueOf(wv).equals(String.valueOf(weightTable.getModel().getValueAt(mr, mc)))) {
                            weightTable.getModel().setValueAt(wv, mr, mc);
                        }
                    }
                }
            });

            // Direction note
            if (chkDirected.isSelected()) {
                dirNote.setText("Columns = origin, Rows = destination.");
            } else {
                dirNote.setText("Undirected: cells (i,j) and (j,i) mirror; weights mirror too.");
            }
        });

        chkDirected.addActionListener(ev -> {
            dirNote.setText(chkDirected.isSelected() ? "Columns = origin, Rows = destination." : "Undirected: cells (i,j) and (j,i) mirror; weights mirror too.");
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

            // If directed, allow per-edge direction selection via checkboxes initialized from adjacency matrix
            java.util.Map<String, JCheckBox> dirBoxes = new java.util.HashMap<>();
            if (directed) {
                JPanel dirPanel = new JPanel();
                dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.Y_AXIS));
                dirPanel.setBorder(BorderFactory.createTitledBorder("Select Directions per Edge"));
                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        boolean aToB = Boolean.TRUE.equals(adjTable.getValueAt(i, j+1));
                        boolean bToA = (i == j) ? false : Boolean.TRUE.equals(adjTable.getValueAt(j, i+1));
                        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
                        if (i == j) {
                            JCheckBox aa = new JCheckBox(names.get(i) + " -> " + names.get(j), aToB);
                            dirBoxes.put(i + "," + j, aa);
                            row.add(aa);
                        } else {
                            JCheckBox ab = new JCheckBox(names.get(i) + " -> " + names.get(j), aToB);
                            JCheckBox ba = new JCheckBox(names.get(j) + " -> " + names.get(i), bToA);
                            dirBoxes.put(i + "," + j, ab);
                            dirBoxes.put(j + "," + i, ba);
                            row.add(ab);
                            row.add(ba);
                        }
                        dirPanel.add(row);
                    }
                }
                int res = JOptionPane.showConfirmDialog(dlg, new JScrollPane(dirPanel), "Edge Directions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (res != JOptionPane.OK_OPTION) return;
            }

            // Build edges based on selections
            if (!directed) {
                for (int i = 0; i < n; i++) {
                    for (int j = i; j < n; j++) {
                        boolean present = Boolean.TRUE.equals(adjTable.getValueAt(i, j+1));
                        boolean presentPair = present || (j != i && Boolean.TRUE.equals(adjTable.getValueAt(j, i+1)));
                        if (i == j) {
                            if (present) {
                                Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                                e.isDirected = false;
                                int w = 1;
                                if (withWeights) {
                                    Object wv = weightTable.getValueAt(i, j+1);
                                    String wvStr = String.valueOf(wv).trim();
                                    if (!wvStr.isEmpty()) {
                                        try { w = Integer.parseInt(wvStr); } catch (Exception ex) { w = 1; }
                                    }
                                }
                                e.weight = w;
                                edgeList.add(e);
                                vertexList.get(i).addVertex(vertexList.get(j));
                            }
                        } else if (presentPair) {
                            Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                            e.isDirected = false;
                                int w = 1;
                                if (withWeights) {
                                    Object w1 = weightTable.getValueAt(i, j+1);
                                    Object w2 = weightTable.getValueAt(j, i+1);
                                    String w1Str = String.valueOf(w1).trim();
                                    String w2Str = String.valueOf(w2).trim();
                                    if (!w1Str.isEmpty()) {
                                        try { w = Integer.parseInt(w1Str); } catch (Exception ex) { w = 1; }
                                    } else if (!w2Str.isEmpty()) {
                                        try { w = Integer.parseInt(w2Str); } catch (Exception ex) { w = 1; }
                                    }
                                }
                            e.weight = w;
                            edgeList.add(e);
                            vertexList.get(i).addVertex(vertexList.get(j));
                            vertexList.get(j).addVertex(vertexList.get(i));
                        }
                    }
                }
            } else { // directed
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        JCheckBox cb = dirBoxes.get(i + "," + j);
                        boolean selected = cb != null && cb.isSelected();
                        if (selected) {
                            Edge e = new Edge(vertexList.get(i), vertexList.get(j));
                            e.isDirected = true;
                                int w = 1;
                                if (withWeights) {
                                    Object wv = weightTable.getValueAt(i, j+1);
                                    String wvStr = String.valueOf(wv).trim();
                                    if (!wvStr.isEmpty()) {
                                        try { w = Integer.parseInt(wvStr); } catch (Exception ex) { w = 1; }
                                    }
                                }
                            e.weight = w;
                            edgeList.add(e);
                            vertexList.get(i).addVertex(vertexList.get(j));
                            if (i != j) vertexList.get(j).addVertex(vertexList.get(i));
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
            case 12:
                return "Shortest Path (Bellman-Ford): Click source vertex, then target vertex (handles negative weights).";
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
