/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphtheory;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;

/**
 *
 * @author mk
 */
public class Edge {

    public Color color;

    public Vertex vertex1;
    public Vertex vertex2;
    public boolean wasFocused;
    public boolean wasClicked;
    public boolean isDirected = false;

    public Edge(Vertex v1, Vertex v2) {
        vertex1 = v1;
        vertex2 = v2;
        this.color = Color.BLACK;
    }

    public void draw(Graphics g) {
        if (wasClicked) {
            g.setColor(Color.red);
        } else if (wasFocused) {
            g.setColor(Color.blue);
        } else {
            g.setColor(this.color);
        }
        if (vertex1 == vertex2) {
            // Draw self-loop as a circle above the vertex
            int loopSize = 20;
            g.drawOval(vertex1.location.x - loopSize / 2, vertex1.location.y - 30, loopSize, loopSize);
        } else {
            if (isDirected && g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D) g;
                java.awt.Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(vertex1.location.x, vertex1.location.y, vertex2.location.x, vertex2.location.y);
                g2.setStroke(old);
            } else {
                g.drawLine(vertex1.location.x, vertex1.location.y, vertex2.location.x, vertex2.location.y);
            }
            if (isDirected) {
                drawArrow(g, vertex1.location.x, vertex1.location.y, vertex2.location.x, vertex2.location.y);
            }
        }

    }

    private void drawArrow(Graphics g, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double angle = Math.atan2(dy, dx);
        int len = (int) Math.sqrt(dx * dx + dy * dy);
        int arrowSize = 18; // enlarged arrow head

        // Shorten the line slightly to make room for the arrow
        x2 = x1 + (int) ((len - arrowSize) * Math.cos(angle));
        y2 = y1 + (int) ((len - arrowSize) * Math.sin(angle));

        // Draw the arrow
        int[] xpoints = {x2, (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6)), (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6))};
        int[] ypoints = {y2, (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6)), (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6))};
        g.fillPolygon(xpoints, ypoints, 3);
    }

    public boolean hasIntersection(int x, int y) {
        if (vertex1 == vertex2) {
            // Self-loop intersection logic
            int loopSize = 20;
            int loopX = vertex1.location.x;
            int loopY = vertex1.location.y - 20; // Center of the loop
            double distanceToCenter = Math.sqrt(Math.pow(x - loopX, 2) + Math.pow(y - loopY, 2));
            // Check if the click is within a certain tolerance of the loop's circumference
            return Math.abs(distanceToCenter - (loopSize / 2)) < 5;
        } else {
            // Use Line2D to calculate the distance from the point to the line segment
            double distance = java.awt.geom.Line2D.ptSegDist(vertex1.location.x, vertex1.location.y, vertex2.location.x, vertex2.location.y, x, y);
            return distance < 5; // Consider it an intersection if the click is within 5 pixels of the line
        }
    }
}
