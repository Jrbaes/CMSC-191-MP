/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphtheory;

import java.awt.Point;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Scanner;
import java.util.Vector;
import javax.swing.JFileChooser;

/**
 *
 * @author mk
 */
public class FileManager {

    public JFileChooser jF;

    public FileManager() {
        jF = new JFileChooser();


    }

    public void saveFile(Vector<Vertex> vList, File fName) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fName));

            out.write(""+vList.size());
            out.newLine();
            for (Vertex v : vList) {
                out.write(v.name);
                out.newLine();
            }
            for (int i = 0; i < vList.size(); i++) {
                for (int j = 0; j < vList.size(); j++) {
                    if (vList.get(i).connectedToVertex(vList.get(j))) {
                        out.write("1");
                    } else {
                        out.write("0");
                    }
                }
                out.newLine();
            }
            for (int k = 0; k < vList.size(); k++) {
                out.write(vList.get(k).location.x + "," + vList.get(k).location.y);
                out.newLine();
            }
            out.close();

        } catch (IOException e) {
            System.out.println(e);
        }

    }

    // New format that appends a VERTEX_COLORS section and an EDGES section after positions to persist colors, directionality and weights
    public void saveFile(Vector<Vertex> vList, Vector<Edge> eList, File fName) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fName));

            out.write(""+vList.size());
            out.newLine();
            for (Vertex v : vList) {
                out.write(v.name);
                out.newLine();
            }
            // adjacency matrix remains for backward compatibility
            for (int i = 0; i < vList.size(); i++) {
                for (int j = 0; j < vList.size(); j++) {
                    if (vList.get(i).connectedToVertex(vList.get(j))) {
                        out.write("1");
                    } else {
                        out.write("0");
                    }
                }
                out.newLine();
            }
            // positions
            for (int k = 0; k < vList.size(); k++) {
                out.write(vList.get(k).location.x + "," + vList.get(k).location.y);
                out.newLine();
            }

            // VERTEX_COLORS section
            out.write("VERTEX_COLORS");
            out.newLine();
            for (Vertex v : vList) {
                Color c = v.color != null ? v.color : Color.BLACK;
                out.write(c.getRed() + "," + c.getGreen() + "," + c.getBlue());
                out.newLine();
            }

            // EDGES section
            out.write("EDGES");
            out.newLine();
            // Count entries: each directed edge counts as 1; each undirected edge will be saved as TWO directed entries (both directions)
            int edgeEntries = 0;
            for (Edge e : eList) {
                if (e.vertex1 == null || e.vertex2 == null) continue;
                edgeEntries += (e.isDirected ? 1 : 2);
            }
            out.write(Integer.toString(edgeEntries));
            out.newLine();
            for (Edge e : eList) {
                int i1 = vList.indexOf(e.vertex1);
                int i2 = vList.indexOf(e.vertex2);
                if (i1 < 0 || i2 < 0) continue; // skip inconsistent
                Color c = e.color != null ? e.color : Color.BLACK;
                if (e.isDirected) {
                    out.write(i1 + "," + i2 + ",1," + e.weight + "," + c.getRed() + "," + c.getGreen() + "," + c.getBlue());
                    out.newLine();
                } else {
                    // Save as two directed entries with same weight/color so users can edit directions independently later
                    out.write(i1 + "," + i2 + ",1," + e.weight + "," + c.getRed() + "," + c.getGreen() + "," + c.getBlue());
                    out.newLine();
                    out.write(i2 + "," + i1 + ",1," + e.weight + "," + c.getRed() + "," + c.getGreen() + "," + c.getBlue());
                    out.newLine();
                }
            }
            out.close();
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    public Vector<Vector<?>> loadFile(File fName) {
        Vector<Vertex> vertexList = new Vector<Vertex>();
        Vector<Edge> edgeList = new Vector<Edge>();
        Vector<Vector<?>> file = new Vector<>();
        try (Scanner data = new Scanner(new FileReader(fName.toString()))) {
            if (data.hasNext()) {
                int size = Integer.parseInt(data.nextLine());
                for (int i = 0; i < size; i++) {//vertex only
                    Vertex v = new Vertex(data.nextLine(), 0, 0);
                    vertexList.add(v);
                }

                for (int j = 0; j < vertexList.size(); j++) { // adjacency list
                    String adjacencyLine = data.nextLine();
                    System.out.println(adjacencyLine);
                    for (int k = 0; k < vertexList.size(); k++) {
                        if (adjacencyLine.charAt(k) == '1') {
                            vertexList.get(j).addVertex(vertexList.get(k));
                        }
                    }


                    for (int l = j + 1; l < vertexList.size(); l++) { //edges
                        if (adjacencyLine.charAt(l) == '1') {
                            Edge e = new Edge(vertexList.get(j), vertexList.get(l));
                            edgeList.add(e);
                        }
                    }
                }

                if (data.hasNextLine()) {
                    for (Vertex v : vertexList) {
                        String pos = data.nextLine();
                        v.location = new Point(Integer.parseInt(pos.split(",")[0]), Integer.parseInt(pos.split(",")[1]));
                    }
                }
                // Optional EDGES section
                // Parse optional sections (VERTEX_COLORS, EDGES) in any order until EOF
                while (data.hasNextLine()) {
                    String tag = data.nextLine().trim();
                    if ("VERTEX_COLORS".equals(tag)) {
                        // Expect exactly vertexList.size() lines of r,g,b
                        for (int i = 0; i < vertexList.size() && data.hasNextLine(); i++) {
                            String line = data.nextLine();
                            String[] rgb = line.split(",");
                            if (rgb.length >= 3) {
                                try {
                                    int r = Integer.parseInt(rgb[0]);
                                    int g = Integer.parseInt(rgb[1]);
                                    int b = Integer.parseInt(rgb[2]);
                                    vertexList.get(i).color = new Color(r, g, b);
                                } catch (Exception ignore) {}
                            }
                        }
                    } else if ("EDGES".equals(tag)) {
                        Vector<Edge> parsedEdges = new Vector<Edge>();
                        if (data.hasNextLine()) {
                            int eCount = Integer.parseInt(data.nextLine());
                            for (int i = 0; i < eCount && data.hasNextLine(); i++) {
                                String line = data.nextLine();
                                String[] parts = line.split(",");
                                if (parts.length >= 4) {
                                    int i1 = Integer.parseInt(parts[0]);
                                    int i2 = Integer.parseInt(parts[1]);
                                    boolean directed = Integer.parseInt(parts[2]) == 1;
                                    int weight = Integer.parseInt(parts[3]);
                                    Color color = Color.BLACK;
                                    if (parts.length >= 7) {
                                        try {
                                            int r = Integer.parseInt(parts[4]);
                                            int g = Integer.parseInt(parts[5]);
                                            int b = Integer.parseInt(parts[6]);
                                            color = new Color(r, g, b);
                                        } catch (Exception ignore) {}
                                    }
                                    if (i1 >= 0 && i1 < vertexList.size() && i2 >= 0 && i2 < vertexList.size()) {
                                        Edge e = new Edge(vertexList.get(i1), vertexList.get(i2));
                                        e.isDirected = directed;
                                        e.weight = weight;
                                        e.color = color;
                                        parsedEdges.add(e);
                                    }
                                }
                            }
                        }
                        // replace edgeList with parsed edges if we found any, and rebuild vertex connections
                        if (!parsedEdges.isEmpty()) {
                            edgeList = parsedEdges;
                            // Rebuild connectedVertices symmetrically for UI and property calculations
                            for (Vertex v : vertexList) v.connectedVertices.clear();
                            for (Edge e : edgeList) {
                                if (e.vertex1 != null && e.vertex2 != null) {
                                    if (!e.vertex1.connectedVertices.contains(e.vertex2)) e.vertex1.addVertex(e.vertex2);
                                    if (e.vertex1 != e.vertex2 && !e.vertex2.connectedVertices.contains(e.vertex1)) e.vertex2.addVertex(e.vertex1);
                                }
                            }
                        }
                    } else {
                        // Unknown tag or EOF
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        file.add(vertexList);
        file.add(edgeList);
        return file;
    }

    // Serialize the current graph (vertices + edges) to a string compatible with saveFile()
    public String saveToString(Vector<Vertex> vList, Vector<Edge> eList) {
        StringBuilder out = new StringBuilder();
        out.append(vList.size()).append('\n');
        for (Vertex v : vList) {
            out.append(v.name).append('\n');
        }
        // adjacency matrix for backward compatibility
        for (int i = 0; i < vList.size(); i++) {
            for (int j = 0; j < vList.size(); j++) {
                out.append(vList.get(i).connectedToVertex(vList.get(j)) ? '1' : '0');
            }
            out.append('\n');
        }
        // positions
        for (int k = 0; k < vList.size(); k++) {
            out.append(vList.get(k).location.x).append(',').append(vList.get(k).location.y).append('\n');
        }
        // vertex colors
        out.append("VERTEX_COLORS\n");
        for (Vertex v : vList) {
            Color c = v.color != null ? v.color : Color.BLACK;
            out.append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue()).append('\n');
        }
        // edges (directed entries, two entries for undirected)
        out.append("EDGES\n");
        int edgeEntries = 0;
        for (Edge e : eList) {
            if (e.vertex1 == null || e.vertex2 == null) continue;
            edgeEntries += (e.isDirected ? 1 : 2);
        }
        out.append(edgeEntries).append('\n');
        for (Edge e : eList) {
            int i1 = vList.indexOf(e.vertex1);
            int i2 = vList.indexOf(e.vertex2);
            if (i1 < 0 || i2 < 0) continue;
            Color c = e.color != null ? e.color : Color.BLACK;
            if (e.isDirected) {
                out.append(i1).append(',').append(i2).append(',').append('1').append(',').append(e.weight)
                   .append(',').append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue()).append('\n');
            } else {
                out.append(i1).append(',').append(i2).append(',').append('1').append(',').append(e.weight)
                   .append(',').append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue()).append('\n');
                out.append(i2).append(',').append(i1).append(',').append('1').append(',').append(e.weight)
                   .append(',').append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue()).append('\n');
            }
        }
        return out.toString();
    }

    // Deserialize graph from string (compatible with loadFile())
    public Vector<Vector<?>> loadFromString(String content) {
        Vector<Vertex> vertexList = new Vector<Vertex>();
        Vector<Edge> edgeList = new Vector<Edge>();
        Vector<Vector<?>> file = new Vector<>();
        try (Scanner data = new Scanner(content)) {
            if (data.hasNext()) {
                int size = Integer.parseInt(data.nextLine());
                for (int i = 0; i < size; i++) {
                    Vertex v = new Vertex(data.nextLine(), 0, 0);
                    vertexList.add(v);
                }
                for (int j = 0; j < vertexList.size(); j++) {
                    String adjacencyLine = data.nextLine();
                    for (int k = 0; k < vertexList.size(); k++) {
                        if (adjacencyLine.charAt(k) == '1') {
                            vertexList.get(j).addVertex(vertexList.get(k));
                        }
                    }
                    for (int l = j + 1; l < vertexList.size(); l++) {
                        if (adjacencyLine.charAt(l) == '1') {
                            Edge e = new Edge(vertexList.get(j), vertexList.get(l));
                            edgeList.add(e);
                        }
                    }
                }
                if (data.hasNextLine()) {
                    for (Vertex v : vertexList) {
                        String pos = data.nextLine();
                        String[] xy = pos.split(",");
                        if (xy.length >= 2) {
                            v.location = new java.awt.Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
                        }
                    }
                }
                while (data.hasNextLine()) {
                    String tag = data.nextLine().trim();
                    if ("VERTEX_COLORS".equals(tag)) {
                        for (int i = 0; i < vertexList.size() && data.hasNextLine(); i++) {
                            String line = data.nextLine();
                            String[] rgb = line.split(",");
                            if (rgb.length >= 3) {
                                try {
                                    int r = Integer.parseInt(rgb[0]);
                                    int g = Integer.parseInt(rgb[1]);
                                    int b = Integer.parseInt(rgb[2]);
                                    vertexList.get(i).color = new Color(r, g, b);
                                } catch (Exception ignore) {}
                            }
                        }
                    } else if ("EDGES".equals(tag)) {
                        Vector<Edge> parsedEdges = new Vector<Edge>();
                        if (data.hasNextLine()) {
                            int eCount = Integer.parseInt(data.nextLine());
                            for (int i = 0; i < eCount && data.hasNextLine(); i++) {
                                String line = data.nextLine();
                                String[] parts = line.split(",");
                                if (parts.length >= 4) {
                                    int i1 = Integer.parseInt(parts[0]);
                                    int i2 = Integer.parseInt(parts[1]);
                                    boolean directed = Integer.parseInt(parts[2]) == 1;
                                    int weight = Integer.parseInt(parts[3]);
                                    Color color = Color.BLACK;
                                    if (parts.length >= 7) {
                                        try {
                                            int r = Integer.parseInt(parts[4]);
                                            int g = Integer.parseInt(parts[5]);
                                            int b = Integer.parseInt(parts[6]);
                                            color = new Color(r, g, b);
                                        } catch (Exception ignore) {}
                                    }
                                    if (i1 >= 0 && i1 < vertexList.size() && i2 >= 0 && i2 < vertexList.size()) {
                                        Edge e = new Edge(vertexList.get(i1), vertexList.get(i2));
                                        e.isDirected = directed;
                                        e.weight = weight;
                                        e.color = color;
                                        parsedEdges.add(e);
                                    }
                                }
                            }
                        }
                        if (!parsedEdges.isEmpty()) {
                            edgeList = parsedEdges;
                            for (Vertex v : vertexList) v.connectedVertices.clear();
                            for (Edge e : edgeList) {
                                if (e.vertex1 != null && e.vertex2 != null) {
                                    if (!e.vertex1.connectedVertices.contains(e.vertex2)) e.vertex1.addVertex(e.vertex2);
                                    if (e.vertex1 != e.vertex2 && !e.vertex2.connectedVertices.contains(e.vertex1)) e.vertex2.addVertex(e.vertex1);
                                }
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        file.add(vertexList);
        file.add(edgeList);
        return file;
    }
}
