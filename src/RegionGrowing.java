import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Applies a region growing onto a given image. This requires a ROI in form of points. (Lab 7)
 */
public class RegionGrowing implements PlugInFilter {

    public static final int FG_VAL = 255;
    public static final int BG_VAL = 0;
    public static final int UNPROCESSED_VAL = -1;

    private ImagePlus imp = null;

    public int setup(String arg, ImagePlus imp) {
        if (arg.equals("about")) {
            showAbout();
            return DONE;
        }

        this.imp = imp;

        return DOES_8G + DOES_STACKS + SUPPORTS_MASKING + ROI_REQUIRED;
    } //setup

    private List<Point> getSeedPoints() {
        List<Point> seedPositions = new ArrayList<>();
        PointRoi pr = (PointRoi) imp.getRoi();
        int[] xPositions = pr.getXCoordinates();
        int[] yPositions = pr.getYCoordinates();
        Rectangle boundingBox = pr.getBounds();

        //finally fill
        for (int i = 0; i < xPositions.length; i++) {
            seedPositions.add(new Point(xPositions[i] + boundingBox.x, yPositions[i] + boundingBox.y));
        }

        return seedPositions;
    }

    public void run(ImageProcessor ip) {
        byte[] pixels = (byte[]) ip.getPixels();
        int width = ip.getWidth();
        int height = ip.getHeight();
        int[][] inDataArrInt = ImageJUtility.convertFrom1DByteArr(pixels, width, height);

        int lowerThresh = 100;
        int upperThresh = 150;

        int[][] segmentedImg = new int[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                segmentedImg[x][y] = UNPROCESSED_VAL;
            }
        }

        List<Point> seedPoints = getSeedPoints();

        if (!seedPoints.isEmpty()) {
            Point seedPos = seedPoints.get(0);
            int initVal = inDataArrInt[seedPos.x][seedPos.y];
            double tolerance = (256 * 0.1) / 2.0;
            lowerThresh = Math.max(0, (int) (initVal - tolerance + 0.5));
            upperThresh = Math.min(255, (int) (initVal + tolerance + 0.5));
        }


        Deque<Point> processingStack = new ArrayDeque<>();
        int fgCount = 0;

        for (Point p : seedPoints) {
            int actVal = inDataArrInt[p.x][p.y];
            if (segmentedImg[p.x][p.y] == UNPROCESSED_VAL) {
                if (actVal >= lowerThresh && actVal <= upperThresh) {
                    segmentedImg[p.x][p.y] = FG_VAL;
                    processingStack.push(p);
                    fgCount++;
                } else {
                    segmentedImg[p.x][p.y] = BG_VAL;
                }
            }
        }

        while (!processingStack.isEmpty()) {
            Point actPos = processingStack.pop();

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    int nbX = actPos.x + xOffset;
                    int nbY = actPos.y + yOffset;

                    if (nbX >= 0 && nbX < width && nbY >= 0 && nbY < height) {
                        int actVal = inDataArrInt[nbX][nbY];
                        if (segmentedImg[nbX][nbY] == UNPROCESSED_VAL) {
                            if (actVal >= lowerThresh && actVal <= upperThresh) {
                                segmentedImg[nbX][nbY] = FG_VAL;
                                processingStack.push(new Point(nbX, nbY));
                                fgCount++;
                            } else {
                                segmentedImg[nbX][nbY] = BG_VAL;
                            }
                        }
                    }
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (segmentedImg[x][y] == UNPROCESSED_VAL) {
                    segmentedImg[x][y] = 0;
                }
            }
        }

        ImageJUtility.showNewImage(segmentedImg, width, height, "Region Growing");
    } //run

    void showAbout() {
        IJ.showMessage("About Template_...",
                "this is a PluginFilter template\n");
    } //showAbout

} //class RegionGrow_