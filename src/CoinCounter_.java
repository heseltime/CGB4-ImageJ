import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.ContrastEnhancer;
import ij.plugin.LutLoader;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import ij.process.ImageConverter;
import ij.process.LUT;

import java.awt.*;
import java.util.*;
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

	private List<Point> getSeedPointsFromBinarySegmentation(int[][] transformedImage, int fg_val) {
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

		//System.out.println("found " + seedPoints.size() + " seed points");

		return seedPoints;
	}

	private int[][] regionGrow(int[][] initImg, int initVal, int neighborhood, double tolerance, List<Point> seedPoints) {
		// seedPoints is basically the initial segmentation here
		int width = initImg.length;
		int height = initImg[0].length;

		int[][] segmentedImg = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				segmentedImg[x][y] = UNPROCESSED_VAL;
			}
		}

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

		//System.out.println("added " + fgCount + " neighbors to foreground");

		return segmentedImg;
	}

	private int[][] subtract(int[][] first, int[][] second, int targetVal) {
		// subtract second from first
		int width = first.length;
		int height = first[0].length;

		int[][] subtracted = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (second[x][y] >= targetVal) {
					subtracted[x][y] = BG_VAL;
				} else {
					subtracted[x][y] = first[x][y];
				}
			}
		}

		return subtracted;
	}

	private int[][] regionLabel(int[][] segmentedImg) {
		// this function was designed for black (background) and white (target) segmented images
		int targetVal = 255;
		int backgroundVal = 0;

		int neighborhood = 1; // hyperparameter

		int width = segmentedImg.length;
		int height = segmentedImg[0].length;

		int[][] labeled = new int[width][height];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				labeled[x][y] = UNPROCESSED_VAL;
			}
		}

		// now go over the image: once a target value is encountered
		// start region growing process with label 1, pause going throw the image!
		// when done increment label, continue
		// plausibility checks to add: minimum region size prob, shape? --> set those labels to background 0

		int currentRegionLabel = 1;

		// go over the while image
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (segmentedImg[x][y] >= targetVal && currentRegionLabel < 255 && labeled[x][y] == UNPROCESSED_VAL) {
					// segmented spot found, id still within range, and equivalent point in the output labeled image is not yet worked!

					// START one point region grow from here
					List<Point> seedPoints = new ArrayList<>();
					seedPoints.add(new Point(x, y));

					// region growing for this label id now, i.e. setting the region to this label value

					Deque<Point> processingStack = new ArrayDeque<>();
					int fgCount = 0;

					// for plausibility check later (possible reversion)
					Deque<Point> processedStack = new ArrayDeque<>();

					// another criterion for plausibility
					boolean touchesEdgeOfImg = false;

					// analogously to region growing
					for (Point p : seedPoints) {
						int actVal = segmentedImg[p.x][p.y];
						if (actVal >= targetVal) {
							labeled[p.x][p.y] = currentRegionLabel;
							processingStack.push(p);
							fgCount++;
						} else {
							labeled[p.x][p.y] = BG_VAL;
						}
					}

					while (!processingStack.isEmpty()) {
						Point actPos = processingStack.pop();

						for (int xOffset = -neighborhood; xOffset <= neighborhood; xOffset++) {
							for (int yOffset = -neighborhood; yOffset <= neighborhood; yOffset++) {
								int nbX = actPos.x + xOffset;
								int nbY = actPos.y + yOffset;

								if (nbX >= 0 && nbX < width && nbY >= 0 && nbY < height) {
									int actVal = segmentedImg[nbX][nbY];
									if (labeled[nbX][nbY] == UNPROCESSED_VAL) {
										if (actVal >= targetVal) {
											labeled[nbX][nbY] = currentRegionLabel;
											processingStack.push(new Point(nbX, nbY));

											// add as processed actually processed points
											processedStack.push(new Point(nbX, nbY));

											fgCount++;
										} else {
											labeled[nbX][nbY] = BG_VAL;
										}
									}
								} else if (nbX == 0 || nbX == width - 1 || nbY == 0 || nbY == height - 1) {
									// grown region touches edge
									touchesEdgeOfImg = true;
								}
							}
						}

					}

					// plausibility check based on fgCount, i.e. labeled region size

					//System.out.println(currentRegionLabel + " labeled region has size " + fgCount);
					// many regions that are not even visible in the 10 - 20 range!

					if (fgCount < 10000 || touchesEdgeOfImg) {
						// revert to background (not a valid region)
						while (!processedStack.isEmpty()) {
							Point p = processedStack.pop();
							labeled[p.x][p.y] = BG_VAL;
						}

						// don't forget seedpoint!
						for (Point p : seedPoints) {
							labeled[p.x][p.y] = BG_VAL;
						}

						// don't increase the label if set
						// proper functioning if |artefacts| > 255 actually requires this
						//		because of artefacts, I ended up with unprocessed regions otherwise
						//		in the logic of this method
						// side effect is that there are no gaps, which is good actually
					} else {
						// finally increase the label
						currentRegionLabel += 1;
					}
					// END one point region grow
				} else if (segmentedImg[x][y] == backgroundVal) {
					labeled[x][y] = backgroundVal;
				}

			}
		}

		return labeled;
	}

	private int countRegions(int[][] labeledRegions) {
		int count = 0;
		// I implemented no gap labeling, so it would be as simple as returning highest label encountered
		// but here is a more generic way that could also deal with gaps

		int width = labeledRegions.length;
		int height = labeledRegions[0].length;

		List<Integer> seen = new ArrayList<>();

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (labeledRegions[x][y] != BG_VAL && !seen.contains(labeledRegions[x][y])) {
					count += 1;
					seen.add(labeledRegions[x][y]);
				}
			}
		}

		return count;
	}

	private Map<Integer, Integer> calculateWidths(int[][] labeledImg) {
		Map<Integer, Integer> widthsPerLabel = new HashMap<>();
		Map<Integer, Integer> diametersPerLabel = new HashMap<>();

		// idea: count the regions to the map, per label, then calculate diameter from the count (equiv. area)

		int width = labeledImg.length;
		int height = labeledImg[0].length;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (labeledImg[x][y] != BG_VAL && widthsPerLabel.containsKey(labeledImg[x][y])) {
					Integer currentCount = widthsPerLabel.get(labeledImg[x][y]);
					widthsPerLabel.put(labeledImg[x][y], currentCount + 1);
				} else  {
					widthsPerLabel.put(labeledImg[x][y], 1);
				}
			}
		}

		for (Map.Entry<Integer, Integer> areaSet : widthsPerLabel.entrySet()) {
			int area = areaSet.getValue();
			double radius = Math.sqrt(area / Math.PI);
			Integer diameter = (int) (2 * radius + 0.5); // in pixels
			diametersPerLabel.put(areaSet.getKey(), diameter);
		}

		return diametersPerLabel;
	}


	private void show2DGrayscaleWithGlasbey(int[][] gray2D, int width, int height) {
		byte[] gray1D = ImageJUtility.convertFrom2DIntArr(gray2D, width, height);
		ImageProcessor outImgProc = new ByteProcessor(width, height);
		outImgProc.setPixels(gray1D);

		ImagePlus imp = new ImagePlus("Glasbey LUT Representation", outImgProc);

		IJ.run(imp, "glasbey","");
		imp.show();
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

		/*
		Part 1
		 */

		byte[] pixels = (byte[])imp.getProcessor().getPixels();
		int width = ip.getWidth();
		int height = ip.getHeight();


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

		// here region growing is used to fill out only the reference marker segment containing the seed point (on thresholded image)
		int[][] segmentedImg = regionGrow(transformedImage, 255, 2, (256 * 0.2) / 2.0, seedPoints);

		ImageJUtility.showNewImage(segmentedImg, width, height, "1.1 Binary Segmentation with Region Growing Reference Marker on Thresholded Image");

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
		//ImageJUtility.showNewImage(transformedImageCoins, width, height, "1.2 Binary Segmentation for the Coins: First Pass with Thresholding");

		/*
		// region growing test
		List<Point> seedPointsCoins;
		int[][] segmentedImgCoins = transformedImageCoins;

		seedPointsCoins = getSeedPointsFromBinarySegmentation(segmentedImgCoins, fg_val);
		segmentedImgCoins = regionGrow(inDataArrInt, 80, 2,  (256 * 0.2) / 2.0, seedPointsCoins);
		// closing could be helpful for this binary thresholding step, but seems to come in part 2
		*/

		// subtract reference marker
		int[][] segmentedImgCoinsSubtracted = subtract(transformedImageCoins, segmentedImg, fg_val);

		ImageJUtility.showNewImage(segmentedImgCoinsSubtracted, width, height, "1.2 Binary Segmentation with Region Growing, Reference Subtracted");

		// Idea for 1.3
		// the marker is 30 mm wide: this length was actually measured in 1.1 (longest) and can now be put to good use
		// if the whole length in pixels represents 30 mm, we know what the equivalence between 1 mm and pixels is
		double referenceScalingFactor = longest / 30.0; // 1 mm in pixels is equivalent to this value
		System.out.println("referenceScalingFactor (ANSWER to 1.3): " + referenceScalingFactor);
		// i.e. a length in pixels / referenceScalingFactor is its length in mm

		/*
		Part 2
		 */

		// approach idea
		// research region labeling and glasbey lut
		// find regions, create image matrix with label entries for x-y-values?
		// per label, calculate mean color?
		// compare to LUT taken from manual anylaysis of different coin types
		// weight coloring and scale somehow to come up with best guess in an additive (?) function
		//			(w1 * c + w2 * c / 2) = ? --> ranges in LUT (Part 3)
		// encapsulate with algorithm for coins, closing and opening (apply in part 1?)
		// output count
		// to get sizes, go through label map and count widths, created dictionary structure of label to width

		int[][] labeledImg = regionLabel(segmentedImgCoinsSubtracted);
		int regionCount = countRegions(labeledImg);

		System.out.println(regionCount + " labels applied (ANSWER 1 to Task 2.3)");

		// closing needed for accurate results
		Map<Integer, Integer> widthsPerLabel = calculateWidths(labeledImg);

		System.out.println(widthsPerLabel + " diameters (in pixels) per label (ANSWER 2 to Task 2.3)");

		show2DGrayscaleWithGlasbey(labeledImg, width, height);

	} //run

	void showAbout() {
		IJ.showMessage("About Template_...",
			"this is a PluginFilter template\n");
	} //showAbout

} //class Invert_

