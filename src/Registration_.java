import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Simple implementation of a registration using nearest neighbor interpolation and sum of squared error distance
 * metric. (Lab 8)
 */
public class Registration_ implements PlugInFilter {

    public int setup(String arg, ImagePlus imp) {
        if (arg.equals("about")) {
            showAbout();
            return DONE;
        }
        return DOES_8G + DOES_STACKS + SUPPORTS_MASKING;
    } //setup

    /**
     * @param xIdx   double x-image coordinate
     * @param yIdx   double y-image coordinate
     * @param width  the width of the input image
     * @param height the height of the image image
     * @param img    input image
     * @return the interpolated value for x and y.
     */
    public int getNNinterpolatedValue(double xIdx, double yIdx, int width, int height, int[][] img) {
        int xIdxInt = (int) (xIdx + 0.5);
        int yIdxInt = (int) (yIdx + 0.5);

        if(xIdxInt >= 0 && xIdxInt < width && yIdxInt >= 0 && yIdxInt < height){
            return img[xIdxInt][yIdxInt];
        }
        return -1;
    }

    /**
     * @param inImg    input image
     * @param width    the width of the input image
     * @param height   the height of the input image
     * @param transX   translation in x-direction
     * @param transY   translation in y-direction
     * @param rotAngle rotation angle in degrees
     * @return transformed image
     */
    public int[][] transformImg(int[][] inImg, int width, int height, double transX, double transY, double rotAngle) {
        int[][] resultImg = new int[width][height];

        double rotAngleRad = -rotAngle * Math.PI / 180.0;
        double cos = Math.cos(rotAngleRad);
        double sin = Math.sin(rotAngleRad);

        double widthHalf = width / 2.0;
        double heightHalf = height / 2.0;

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                double posX = x - widthHalf;
                double posY = y - heightHalf;

                double rotatedX = posX * cos + posY * sin;
                double rotatedY = -posX * sin + posY * cos;

                posX = rotatedX;
                posY = rotatedY;

                posX += widthHalf;
                posY += heightHalf;

                posX -= transX;
                posY -= transY;

                int nNinterpolatedValue = getNNinterpolatedValue(posX, posY, width, height, inImg);
                resultImg[x][y] = nNinterpolatedValue;
            }
        }

        return resultImg;
    }

    /**
     * @param refImg  - static reference image
     * @param testImg - moving image getting transformed
     * @param width   the width of the input image
     * @param height  the height of the input image
     * @return error metric
     */
    public double getImgDiffSSE(int[][] refImg, int[][] testImg, int width, int height) {
        double totalError = 0.0;

        for(int x = 0; x < width; x++) {
            for(int y = 0; y < height; y++) {
                int val1 = refImg[x][y];
                int val2 = testImg[x][y];

                totalError += (val2-val1) * (val2-val1);
            }
        }

        return totalError;
    }

    /**
     * @param refImg  - static reference image
     * @param testImg - moving image getting transformed
     * @param width   the width of the input image
     * @param height  the height of the input image
     * @return difference image
     */
    public double[][] getDiffImg(double[][] refImg, double[][] testImg, int width, int height) {
        double[][] res = new double[width][height];

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                res[x][y] = Math.abs(refImg[x][y] -testImg[x][y]);
            }
        }

        return res;
    }

    /**
     * method for automated registration utilizing 11x11x11 permutations for the transformation parameters
     *
     * @param refImg  The reference image
     * @param testImg the image to test with.
     * @param width   the width of the input image
     * @param height  the height of the input image
     * @return returns the registered image
     */
    public int[][] getRegisteredImage(int[][] refImg, int[][] testImg, int width, int height) {
        double bestTx = 0.0;
        double bestTy = 0.0;
        double bestR = 0.0;
        double currMinError = getImgDiffSSE(refImg, testImg, width, height);

        double searchStep = 2.0;
        int stepsPerSide = 5;

        for(int x = -stepsPerSide; x <= stepsPerSide; x++){
            for(int y = -stepsPerSide; y <= stepsPerSide; y++) {
                for(int r = -stepsPerSide; r <= stepsPerSide; r++) {
                    double tx = x * searchStep;
                    double ty = y * searchStep;
                    double tr = r * searchStep;

                    int[][] ints = transformImg(testImg, width, height, tx, ty, tr);
                    double imgDiffSSE = getImgDiffSSE(refImg, ints, width, height);
                    if(imgDiffSSE < currMinError){
                        bestTx = tx;
                        bestTy = ty;
                        bestR = tr;
                        currMinError = imgDiffSSE;
                        IJ.log("new best Tx=" + bestTx + " Ty=" + bestTy + " R=" + bestR);
                    }
                }
            }
        }

        return transformImg(testImg, width, height, bestTx, bestTy, bestR);
    }


    public void run(ImageProcessor ip) {
        byte[] pixels = (byte[]) ip.getPixels();
        int width = ip.getWidth();
        int height = ip.getHeight();
        int[][] inDataArrInt = ImageJUtility.convertFrom1DByteArr(pixels, width, height);

        //initially transform input image to get a registration task
        double transX = 9.78;
        double transY = -1.99;
        double rot = 2.14;

        int[][] transformedImage = transformImg(inDataArrInt, width, height, transX, transY, rot);
        double initError = getImgDiffSSE(inDataArrInt, transformedImage, width, height);
        IJ.log("init error = " + initError);
        ImageJUtility.showNewImage(transformedImage, width, height, "transformed img");

        int[][] registeredImg = getRegisteredImage(inDataArrInt, transformedImage, width, height);
        ImageJUtility.showNewImage(registeredImg, width, height, "registered img");

    } //run

    void showAbout() {
        IJ.showMessage("About Template_...",
                "this is a PluginFilter template\n");
    } //showAbout

} //class Registration_
