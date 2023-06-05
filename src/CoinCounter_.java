import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.ContrastEnhancer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import ij.process.ImageConverter;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Plugin that counts up values for given Euro coins from an image.
 */
public class CoinCounter_ implements PlugInFilter {

	public static final int FG_VAL = 255;
	public static final int BG_VAL = 0;
	public static final int UNPROCESSED_VAL = -1;
	public static final int BRIGHTNESS_INCREASE = 80; // in pixels

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about"))
			{showAbout(); return DONE;}
		return DOES_RGB+DOES_STACKS+SUPPORTS_MASKING; // RGB image assumed
	} //setup


	// UTILITIES

	public List<Point> getSeedPointsFromBinarySegmentation(int[][] transformedImage, int fg_val) {
		int width = transformedImage.length;
		int height = transformedImage[0].length;

		List<Point> seedPoints = new ArrayList<>();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (transformedImage[x][y] >= fg_val) {
					seedPoints.add(new Point(x, y));
				}
			}
		}

		System.out.println("found " + seedPoints.size() + " seed points");

		return seedPoints;
	}

	int[][] regionGrow(int[][] initImg, int initVal, int neighborhood, List<Point> seedPoints) {
		int width = initImg.length;
		int height = initImg[0].length;

		int[][] segmentedImg = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				segmentedImg[x][y] = UNPROCESSED_VAL;
			}
		}

		double tolerance = (256 * 0.1) / 2.0;
		int lowerThresh = Math.max(0, (int) (initVal - tolerance + 0.5));
		int upperThresh = Math.min(255, (int) (initVal + tolerance + 0.5));

		Deque<Point> processingStack = new ArrayDeque<>();
		int fgCount = 0;

		for (Point p : seedPoints) {
			int actVal = initImg[p.x][p.y]; // not inDataArrInt[][]
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

			for (int xOffset = -neighborhood; xOffset <= neighborhood; xOffset++) {
				for (int yOffset = -neighborhood; yOffset <= neighborhood; yOffset++) {
					int nbX = actPos.x + xOffset;
					int nbY = actPos.y + yOffset;

					if (nbX >= 0 && nbX < width && nbY >= 0 && nbY < height) {
						int actVal = initImg[nbX][nbY];
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

		System.out.println("added " + fgCount + " neighbors to foreground");

		return segmentedImg;
	}

	public void run(ImageProcessor ip) {
		// Pre-Processing:
		// from tests with Color Inspector: increased contrast (with brightness) leads to sharper edges to help with thresholding
		// start with color again
		ImagePlus imp = new ImagePlus("Initial Image", ip);

		ContrastEnhancer enh = new ContrastEnhancer();
		enh.stretchHistogram(imp, 25);

		ImageConverter ic = new ImageConverter(imp); // convert to grayscale again, to make brightness increase simpler
		ic.convertToGray8();

		imp.updateAndDraw();
		//imp.show();
		//

		// brightness will be increased in the following first thresholding step

		byte[] pixels = (byte[])imp.getProcessor().getPixels();
		int width = ip.getWidth();
		System.out.println(width);
		int height = ip.getHeight();
		System.out.println(height);


		int[][] inDataArrInt = ImageJUtility.convertFrom1DByteArr(pixels, width, height);

		int fg_val = 255;
		int bg_val = 0;

		int tmin = 0;
		int tmax = 32; // to capture reference marker, i.e. darkes part of the image

		int[] binaryThresholdTF = ImageTransformationFilter.getBinaryThresholdTF(fg_val, tmin, tmax, fg_val, bg_val);
		int[][] transformedImage = ImageTransformationFilter.getTransformedImage(inDataArrInt, width, height, binaryThresholdTF);

		// Uncomment to show intermediate processing step
		//ImageJUtility.showNewImage(transformedImage, width, height, "1.1 Binary Segmentation First Pass with Thresholding");

		// Idea for 1.1:
		// At the above show step there are false positives (smaller! coins) and no full circle for the reference.
		// So: idea to use region growing, seed point from largest area (so find that largest area, throw out the rest)
		// I define the largest area as the white space with the longest vertical line, the seed point is then any pt on this line

		// find longest connected vertical line to: longest will later be used as well (1.3)
		int xStartOfLongest, yStartOfLongest, longest, current;
		longest = 0;
		current = 0;
		xStartOfLongest = -1;
		yStartOfLongest = -1;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (transformedImage[x][y] >= 255) {
					if (current > longest) {
						// remember an x and y on the longest run
						xStartOfLongest = x;
						yStartOfLongest = y;
						longest = current;
					}
					current += 1;
				} else {
					current = 0;
				}

				// this loop over the image pixels (transformed image so far) will also be used to increase brightness
				// i.e. the pixel value for the grayscale image by a scaling factor - needed later for coin segmentation
				inDataArrInt[x][y] = Math.min(255, inDataArrInt[x][y] + BRIGHTNESS_INCREASE);
			}
		}

		if (xStartOfLongest == -1) {
			IJ.showMessage("Error",
							"A region growing seed point for the black reference area could not be found. \n");

		}

		System.out.println("(Info:) Seed point for 1.1 reference region growing: " + xStartOfLongest + " " + yStartOfLongest);

		// region growing
		Point seedPoint = new Point(xStartOfLongest, yStartOfLongest);

		List<Point> seedPoints = new ArrayList<>();
		seedPoints.add(seedPoint);

		int[][] segmentedImg = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				segmentedImg[x][y] = UNPROCESSED_VAL;
			}
		}

		int initVal = 255;
		double tolerance = (256 * 0.1) / 2.0;
		int lowerThresh = Math.max(0, (int) (initVal - tolerance + 0.5));
		int upperThresh = Math.min(255, (int) (initVal + tolerance + 0.5));


		Deque<Point> processingStack = new ArrayDeque<>();
		int fgCount = 0;

		for (Point p : seedPoints) {
			int actVal = transformedImage[p.x][p.y]; // not inDataArrInt[][]
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
						int actVal = transformedImage[nbX][nbY];
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

		ImageJUtility.showNewImage(segmentedImg, width, height, "1.1 Binary Segmentation with Region Growing");

		// Idea for 1.2
		// use different thresholds to segment the coins incl the reference marker, region grow globally
		// then post process to reset the marker since we have already located this -- if even necessary, based on new thresholds

		// contrast and brightness increases already done in first steps, on inDataArrInt:
		// Uncomment to show intermediate processing step
		//ImageJUtility.showNewImage(inDataArrInt, width, height, "Brightness and Contrast Increase on inDataArrInt from first steps");

		int tminCoins = 70;
		int tmaxCoins = 180; // could be done dynamically?

		int[] binaryThresholdTFCoins = ImageTransformationFilter.getBinaryThresholdTF(fg_val, tminCoins, tmaxCoins, fg_val, bg_val);
		int[][] transformedImageCoins = ImageTransformationFilter.getTransformedImage(inDataArrInt, width, height, binaryThresholdTFCoins);

		// Uncomment to show intermediate processing step
		ImageJUtility.showNewImage(transformedImageCoins, width, height, "1.2 Binary Segmentation for the Coins: First Pass with Thresholding");

		// apply region growing with some plausibility checks
		// issues: lower edge of image where bg is darkest is segmented, reference marker is still in
		List<Point> seedPointsCoins;
		int[][] segmentedImgCoins = transformedImageCoins;

		// iterative region growing
		for (int i = 0; i < 10; i++) {
			seedPointsCoins = getSeedPointsFromBinarySegmentation(segmentedImgCoins, fg_val);
			segmentedImgCoins = regionGrow(segmentedImgCoins, fg_val, 2, seedPointsCoins);
		}

		ImageJUtility.showNewImage(segmentedImgCoins, width, height, "1.2 Binary Segmentation with Region Growing");

		// subtract reference marker
		// TODO


		// Idea for 1.3
		// the marker is 30 mm wide: this length was actually measured in 1.1 (longest) and can now be put to good use
		// if the whole length in pixels represents 30 mm, we know what the equivalence between 1 mm and pixels is
		double referenceScalingFactor = longest / 30.0; // 1 mm in pixels is equivalent to this value
		System.out.println("referenceScalingFactor (ANSWER to 1.3): " + referenceScalingFactor);
		// i.e. a length in pixels / referenceScalingFactor is its length in mm

	} //run

	void showAbout() {
		IJ.showMessage("About Template_...",
			"this is a PluginFilter template\n");
	} //showAbout

} //class Invert_

