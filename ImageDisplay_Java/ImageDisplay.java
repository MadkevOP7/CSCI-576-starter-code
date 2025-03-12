import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

public class ImageDisplay {

    private JFrame frame;
    private JLabel lbIm1;
    private JLabel lbIm2;
    private BufferedImage imgOriginal;
    private BufferedImage imgDecompressed;

    // Default image dimensions for your .rgb/.raw. Many students use 352x288 in class.
    // Adjust if your input images differ.
    private int width = 352;
    private int height = 288;

    // This will be set to 3 if reading a .rgb file (color), or 1 if .raw (grayscale).
    private int channels = 3;

    /**
     * Reads an image file into a BufferedImage. 
     * Supports:
     *   - .rgb => color => channels=3
     *   - .raw => grayscale => channels=1
     * Expects row-major pixel data, with R then G then B for .rgb.
     */
    private void readImage(String imgPath, BufferedImage img) {
        try {
            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);
            String lower = imgPath.toLowerCase();

            if (lower.endsWith(".rgb")) {
                // color
                channels = 3;
                int frameLength = width * height * channels;
                byte[] bytes = new byte[frameLength];
                raf.read(bytes);

                int ind = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        byte r = bytes[ind];
                        byte g = bytes[ind + width * height];
                        byte b = bytes[ind + 2 * width * height];
                        int pix = (0xff << 24)
                                | ((r & 0xff) << 16)
                                | ((g & 0xff) << 8)
                                | (b & 0xff);
                        img.setRGB(x, y, pix);
                        ind++;
                    }
                }
            } else if (lower.endsWith(".raw")) {
                // grayscale
                channels = 1;
                int frameLength = width * height;
                byte[] bytes = new byte[frameLength];
                raf.read(bytes);
                int ind = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int val = bytes[ind] & 0xff;
                        int pix = (0xff << 24)
                                | (val << 16)
                                | (val << 8)
                                | val;  // replicate grayscale
                        img.setRGB(x, y, pix);
                        ind++;
                    }
                }
            } else {
                System.err.println("Unsupported file extension. Must be .rgb or .raw.");
                System.exit(1);
            }
            raf.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Pads the input image to ensure its width/height are divisible by blockWidth/blockHeight.
     * Returns a new BufferedImage that includes the original pixels plus padding at right/bottom.
     *
     * We'll keep track of the original width/height for cropping after decompression.
     */
    private BufferedImage padIfNeeded(BufferedImage img, int blockWidth, int blockHeight) {
        int originalW = img.getWidth();
        int originalH = img.getHeight();

        // If already divisible, no padding required.
        boolean needsPadW = (originalW % blockWidth != 0);
        boolean needsPadH = (originalH % blockHeight != 0);

        if (!needsPadW && !needsPadH) {
            return img;  // no change needed
        }

        // compute new padded dimensions
        int newW = (needsPadW) 
                   ? ((originalW / blockWidth) + 1) * blockWidth 
                   : originalW;
        int newH = (needsPadH) 
                   ? ((originalH / blockHeight) + 1) * blockHeight 
                   : originalH;

        BufferedImage padded = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

        // copy old pixels
        Graphics g = padded.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        // If desired, fill the padding region with some color or average, etc.
        // We'll just leave it as black (0,0,0).

        // Update width/height fields so code uses the padded dimensions for block extraction
        this.width = newW;
        this.height = newH;

        return padded;
    }

    class BlockVector {
        int x, y;       // top-left corner of this block in the (possibly padded) image
        double[] data;  // blockWidth * blockHeight * channels

        BlockVector(int x, int y, double[] data) {
            this.x = x;
            this.y = y;
            this.data = data;
        }
    }

    /**
     * Extracts non-overlapping blocks from the (potentially padded) image.
     * For M=2, we do a 2x1 block; for M>2 that’s a sqrt(M)x sqrt(M) block.
     */
    private ArrayList<BlockVector> extractBlocks(BufferedImage img, int M) {
        ArrayList<BlockVector> blocks = new ArrayList<>();

        int blockWidth, blockHeight;
        if (M == 2) {
            blockWidth = 2;
            blockHeight = 1;
        } else {
            int bSize = (int) Math.round(Math.sqrt(M));
            blockWidth = bSize;
            blockHeight = bSize;
        }

        // possibly padded width & height
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        // We have already ensured divisibility via padIfNeeded(),
        // but let's check anyway:
        if (imgW % blockWidth != 0 || imgH % blockHeight != 0) {
            System.err.println("Image dimensions not divisible by block size. Should never happen after padding!");
            System.exit(1);
        }

        // row by row
        for (int y = 0; y < imgH; y += blockHeight) {
            for (int x = 0; x < imgW; x += blockWidth) {
                double[] vec = new double[blockWidth * blockHeight * channels];
                int idx = 0;
                for (int j = 0; j < blockHeight; j++) {
                    for (int i = 0; i < blockWidth; i++) {
                        int px = x + i;
                        int py = y + j;
                        int rgb = img.getRGB(px, py);

                        if (channels == 3) {
                            int r = (rgb >> 16) & 0xff;
                            int g = (rgb >> 8) & 0xff;
                            int b = rgb & 0xff;
                            vec[idx++] = r;
                            vec[idx++] = g;
                            vec[idx++] = b;
                        } else {
                            // grayscale
                            int val = rgb & 0xff;
                            vec[idx++] = val;
                        }
                    }
                }
                blocks.add(new BlockVector(x, y, vec));
            }
        }
        return blocks;
    }

    /**
     * Squared Euclidean distance
     */
    private double distanceSquared(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * k-means++ style initialization:
     * 1. Pick a random block as first centroid
     * 2. Weighted probability for subsequent centroids, based on distance from existing ones
     */
    private ArrayList<double[]> initCodebook(ArrayList<BlockVector> blocks, int dimension, int N) {
        ArrayList<double[]> codebook = new ArrayList<>();
        Random rand = new Random();
        int numBlocks = blocks.size();

        // first centroid
        int firstIndex = rand.nextInt(numBlocks);
        codebook.add(blocks.get(firstIndex).data.clone());

        while (codebook.size() < N) {
            // for each block, find its dist to nearest existing centroid
            double[] distArray = new double[numBlocks];
            double totalDist = 0;
            for (int i = 0; i < numBlocks; i++) {
                double minD = Double.MAX_VALUE;
                double[] vec = blocks.get(i).data;
                for (double[] cw : codebook) {
                    double d2 = distanceSquared(vec, cw);
                    if (d2 < minD) {
                        minD = d2;
                    }
                }
                distArray[i] = minD;
                totalDist += minD;
            }

            // pick next centroid stochastically
            double r = rand.nextDouble() * totalDist;
            double cumsum = 0;
            int nextIdx = 0;
            for (int i = 0; i < numBlocks; i++) {
                cumsum += distArray[i];
                if (cumsum >= r) {
                    nextIdx = i;
                    break;
                }
            }
            codebook.add(blocks.get(nextIdx).data.clone());
        }

        return codebook;
    }

    /**
     * The main k-means iteration.
     * Returns cluster assignments for each block in 'blocks'
     */
    private int[] quantizeVectors(ArrayList<BlockVector> blocks, int dimension, int N, ArrayList<double[]> codebook) {
        int numBlocks = blocks.size();
        int[] assignments = new int[numBlocks];
        Arrays.fill(assignments, -1);

        int maxIter = 200;
        double threshold = 0.1;
        boolean changed = true;
        int iter = 0;

        while (changed && iter < maxIter) {
            changed = false;

            // 1) Assignment step
            for (int i = 0; i < numBlocks; i++) {
                double[] v = blocks.get(i).data;
                double bestDist = Double.MAX_VALUE;
                int bestIndex = -1;
                for (int k = 0; k < codebook.size(); k++) {
                    double dist = distanceSquared(v, codebook.get(k));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestIndex = k;
                    }
                }
                if (assignments[i] != bestIndex) {
                    assignments[i] = bestIndex;
                    changed = true;
                }
            }

            // 2) Update step: average each cluster
            ArrayList<double[]> newCw = new ArrayList<>();
            for (int k = 0; k < N; k++) {
                double[] sum = new double[dimension];
                int count = 0;
                for (int i = 0; i < numBlocks; i++) {
                    if (assignments[i] == k) {
                        double[] vec = blocks.get(i).data;
                        for (int j = 0; j < dimension; j++) {
                            sum[j] += vec[j];
                        }
                        count++;
                    }
                }
                if (count == 0) {
                    // if cluster is empty, reinit from a random block
                    sum = blocks.get(new Random().nextInt(numBlocks)).data.clone();
                } else {
                    // average
                    for (int j = 0; j < dimension; j++) {
                        sum[j] /= count;
                    }
                }
                newCw.add(sum);
            }

            // measure codebook shift to see if we can stop
            double maxChange = 0;
            for (int k = 0; k < N; k++) {
                double shift = Math.sqrt(distanceSquared(codebook.get(k), newCw.get(k)));
                if (shift > maxChange) {
                    maxChange = shift;
                }
            }
            codebook = newCw;

            if (maxChange <= threshold) {
                changed = false;
            }

            iter++;
        }

        // final codebook is now in 'codebook'; assignments are final
        return assignments;
    }

    /**
     * Reconstruct (decompress) the image from cluster assignments & codebook.
     * We write the codeword's pixel data back into a new, padded-size image,
     * then crop out any padding before returning.
     */
    private BufferedImage reconstruct(
            ArrayList<BlockVector> blocks,
            int[] assignments,
            ArrayList<double[]> codebook,
            int M,
            int origWidth,  // to crop back to
            int origHeight) {

        // the code might have changed 'this.width' & 'this.height' if we padded
        int paddedW = this.width;
        int paddedH = this.height;

        // figure out block shape
        int blockWidth, blockHeight;
        if (M == 2) {
            blockWidth = 2;
            blockHeight = 1;
        } else {
            int s = (int) Math.round(Math.sqrt(M));
            blockWidth = s;
            blockHeight = s;
        }

        // create a new image (padded size)
        BufferedImage out = new BufferedImage(paddedW, paddedH, BufferedImage.TYPE_INT_RGB);

        // fill each block
        for (int b = 0; b < blocks.size(); b++) {
            BlockVector bv = blocks.get(b);
            double[] cw = codebook.get(assignments[b]);
            int idx = 0;

            for (int j = 0; j < blockHeight; j++) {
                for (int i = 0; i < blockWidth; i++) {
                    int px = bv.x + i;
                    int py = bv.y + j;

                    if (channels == 3) {
                        int r = (int) Math.round(cw[idx++]);
                        int g = (int) Math.round(cw[idx++]);
                        int bcol = (int) Math.round(cw[idx++]);

                        // clamp
                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        bcol = Math.max(0, Math.min(255, bcol));

                        int rgb = (0xff << 24) | (r << 16) | (g << 8) | bcol;
                        out.setRGB(px, py, rgb);
                    } else {
                        // grayscale
                        int val = (int) Math.round(cw[idx++]);
                        val = Math.max(0, Math.min(255, val));
                        int rgb = (0xff << 24) | (val << 16) | (val << 8) | val;
                        out.setRGB(px, py, rgb);
                    }
                }
            }
        }

        // now crop off the padding so we return an image at original size
        BufferedImage cropped = out.getSubimage(0, 0, origWidth, origHeight);

        // the subimage shares data with 'out', so let's make a copy if we want a safe standalone
        BufferedImage finalImage = new BufferedImage(origWidth, origHeight, BufferedImage.TYPE_INT_RGB);
        Graphics g = finalImage.getGraphics();
        g.drawImage(cropped, 0, 0, null);
        g.dispose();

        return finalImage;
    }

    /**
     * Main routine: read & compress & show
     *  cmdline: java ImageDisplay <imagePath> <M> <N>
     */
    public void showIms(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java ImageDisplay <imagePath> <M> <N>");
            System.exit(1);
        }
        String imagePath = args[0];
        int M = Integer.parseInt(args[1]);
        int N = Integer.parseInt(args[2]);

        // Read original
        imgOriginal = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImage(imagePath, imgOriginal);

        // keep track of these, since we might pad
        int origWidth = width;
        int origHeight = height;

        // figure out block shape
        int blockWidth, blockHeight;
        if (M == 2) {
            blockWidth = 2;
            blockHeight = 1;
        } else {
            int s = (int) Math.round(Math.sqrt(M));
            blockWidth = s;
            blockHeight = s;
        }

        // pad if needed
        BufferedImage paddedImg = padIfNeeded(imgOriginal, blockWidth, blockHeight);

        // extract block vectors from padded
        ArrayList<BlockVector> blocks = extractBlocks(paddedImg, M);

        // dimension = blockWidth * blockHeight * channels
        int dimension = blocks.get(0).data.length;

        // k-means++ init
        ArrayList<double[]> codebook = initCodebook(blocks, dimension, N);

        // main iteration
        int[] assignments = quantizeVectors(blocks, dimension, N, codebook);

        // reconstruct & crop
        imgDecompressed = reconstruct(blocks, assignments, codebook, M, origWidth, origHeight);

        // Show side-by-side
        frame = new JFrame("Original (left) vs Decompressed (right)");
        frame.setLayout(new FlowLayout());
        lbIm1 = new JLabel(new ImageIcon(imgOriginal));
        lbIm2 = new JLabel(new ImageIcon(imgDecompressed));
        frame.add(lbIm1);
        frame.add(lbIm2);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {
        ImageDisplay display = new ImageDisplay();
        display.showIms(args);
    }
}
