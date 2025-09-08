/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphtheory;

import java.awt.Point;
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

    // New format that appends an EDGES section after positions to persist directionality and weights
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

            // EDGES section
            out.write("EDGES");
            out.newLine();
            out.write(Integer.toString(eList.size()));
            out.newLine();
            for (Edge e : eList) {
                int i1 = vList.indexOf(e.vertex1);
                int i2 = vList.indexOf(e.vertex2);
                if (i1 < 0 || i2 < 0) continue; // skip inconsistent
                out.write(i1 + "," + i2 + "," + (e.isDirected ? 1 : 0) + "," + e.weight);
                out.newLine();
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
        try {
            FileReader f = new FileReader(fName.toString());
            Scanner data = new Scanner(f);
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
                if (data.hasNextLine()) {
                    String maybeTag = data.nextLine();
                    if ("EDGES".equals(maybeTag)) {
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
                                    if (i1 >= 0 && i1 < vertexList.size() && i2 >= 0 && i2 < vertexList.size()) {
                                        Edge e = new Edge(vertexList.get(i1), vertexList.get(i2));
                                        e.isDirected = directed;
                                        e.weight = weight;
                                        parsedEdges.add(e);
                                    }
                                }
                            }
                        }
                        // replace edgeList with parsed edges if we found any
                        if (!parsedEdges.isEmpty()) {
                            edgeList = parsedEdges;
                        }
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
