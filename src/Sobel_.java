import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Applies a sobel edge detection (high-pass filter) onto a given image. (Lab 6)
 */
public class Sobel_ implements PlugInFilter {

   public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about"))
			{showAbout(); return DONE;}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	} //setup



	public void run(ImageProcessor ip) {
		byte[] pixels = (byte[])ip.getPixels();
		int width = ip.getWidth();
		int height = ip.getHeight();
		int[][] inDataArrInt = ImageJUtility.convertFrom1DByteArr(pixels, width, height);
        double[][] inDataArrDbl =
        		ImageJUtility.convertToDoubleArr2D(inDataArrInt, width, height);

		double[][] sobelV = new double[][]{
						{1.0, 2.0, 1.0},
						{0, 0, 0},
						{-1.0, -2.0, -1.0}
		};

		// without vertical implementation

		double[][] doubles = ConvolutionFilter.convolveDouble(inDataArrDbl, width, height, sobelV, 1);

		double maxVal = Double.MIN_VALUE;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				double actVal = Math.abs(doubles[x][y]);
				if (actVal > maxVal) {
					maxVal = actVal;
				}
			}
		}

		double normFactor = 255.0 / maxVal;

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				doubles[x][y] = Math.abs(doubles[x][y] * normFactor);
			}
		}

		ImageJUtility.showNewImage(doubles, width, height, "Sobel");
	} //run

	void showAbout() {
		IJ.showMessage("About Template_...",
			"this is a PluginFilter template\n");
	} //showAbout

} //class Mean_

