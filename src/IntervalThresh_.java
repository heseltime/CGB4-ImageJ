import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Plugin that inverts a given greyscale image. (Lab 5)
 */
public class IntervalThresh_ implements PlugInFilter {

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

		int fg_val = 255;
		int bg_val = 0;

		int tmin = 100;
		int tmax = 255;

		GenericDialog gd = new GenericDialog("User input");
		gd.addNumericField("Tmin: ", tmin, 0);
		gd.addNumericField("Tmax: ", tmax, 0);

		gd.showDialog();

		if (gd.wasCanceled()) {
			return;
		}

		tmin = (int) gd.getNextNumber();
		tmax = (int) gd.getNextNumber();

		int[] binaryThresholdTF = ImageTransformationFilter.getBinaryThresholdTF(fg_val, tmin, tmax, fg_val, bg_val);
		int[][] transformedImage = ImageTransformationFilter.getTransformedImage(inDataArrInt, width, height, binaryThresholdTF);
		ImageJUtility.showNewImage(transformedImage, width, height, "Threshold");

	} //run

	void showAbout() {
		IJ.showMessage("About Template_...",
			"this is a PluginFilter template\n");
	} //showAbout

} //class Invert_

