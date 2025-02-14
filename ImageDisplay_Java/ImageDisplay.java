
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;
import javax.swing.*;

public class ImageDisplay {

    JFrame frame;
    JLabel lbIm1;
    BufferedImage imgOne;

    // Modify the height and width values here to read and display an image with
    // different dimensions.
    int width = 512;
    int height = 512;

    /**
     * Read Image RGB Reads the image of given width and height at the given
     * imgPath into the provided BufferedImage.
     */
    private void readImageRGB(int width, int height, String imgPath, BufferedImage img) {
        try {
            int frameLength = width * height * 3;

            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);

            long len = frameLength;
            byte[] bytes = new byte[(int) len];

            raf.read(bytes);

            int ind = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte a = 0;
                    byte r = bytes[ind];
                    byte g = bytes[ind + height * width];
                    byte b = bytes[ind + height * width * 2];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    // int pix = ((a << 24) + (r << 16) + (g << 8) + b);
                    img.setRGB(x, y, pix);
                    ind++;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int computeOptimalPivot(BufferedImage img) {
        int[] histogram = new int[256];
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int color = img.getRGB(x, y);
                int intensity = (int) (0.299 * ((color >> 16) & 0xFF)
                        + 0.587 * ((color >> 8) & 0xFF)
                        + 0.114 * (color & 0xFF));
                histogram[intensity]++;
            }
        }

        int sum = Arrays.stream(histogram).sum();
        int cumulative = 0, pivot = 0;
        for (int i = 0; i < histogram.length; i++) {
            cumulative += histogram[i];
            if (cumulative >= sum * 0.5) {
                pivot = i;
                break;
            }
        }
        return Math.max(pivot, 1);
    }

    public void showIms(String[] args) {

        // Read in the specified image
        imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[0], imgOne);

        // Read args & process if has more than just the path arg
        if (args.length > 1) {

            // scale image
            float scale = Float.parseFloat(args[1]);
            imgOne = scaleImg(imgOne, scale);

            // quantize image
            int quantizationBits = Integer.parseInt(args[2]);
            int quantizationMode = (args.length > 3) ? Integer.parseInt(args[3]) : computeOptimalPivot(imgOne);
            System.out.println("Optimal Pivot: " + quantizationMode);
            imgOne = quantizeImg(imgOne, quantizationBits, quantizationMode);
        }

        // Use label to display the image
        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        lbIm1 = new JLabel(new ImageIcon(imgOne));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.pack();
        frame.setVisible(true);
    }

    ///////CORE IMPLEMENTATIONS//////
	private BufferedImage scaleImg(BufferedImage img, float scale) {
        int newWidth = (int) (img.getWidth() * scale);
        int newHeight = (int) (img.getHeight() * scale);
        BufferedImage rescaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int px = (int) (x / scale);
                int py = (int) (y / scale);

                // Clip bounds!
                if (px < img.getWidth() && py < img.getHeight()) {
                    rescaledImage.setRGB(x, y, img.getRGB(px, py));
                }
            }
        }
        return rescaledImage;
    }

    private BufferedImage quantizeImg(BufferedImage img, int bitsPerChannel, int mode) {
        BufferedImage quantizedImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);

        // quantize rgb channels here
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int color = img.getRGB(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                // Apply quantization
                r = quantizeChannel(r, bitsPerChannel, mode);
                g = quantizeChannel(g, bitsPerChannel, mode);
                b = quantizeChannel(b, bitsPerChannel, mode);

                // Set the quantized pixel back to the image
                int quantizedColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
                quantizedImage.setRGB(x, y, quantizedColor);
            }
        }

        return quantizedImage;
    }

    private int quantizeChannel(int value, int bitsPerChannel, int mode) {
        int maxVal = (1 << bitsPerChannel) - 1;
        if (mode == -1) {  // Uniform quantization
            int step = 256 / (maxVal + 1);
            return (value / step) * step + step / 2;
        } else {  // Logarithmic quantization
            int pivot = mode;
            int logValue = (int) (Math.log(value + 1) / Math.log(pivot + 1) * maxVal);
            return Math.min(logValue, maxVal);
        }
    }

    /////////////////////////////////
	

	public static void main(String[] args) {
        ImageDisplay ren = new ImageDisplay();
        ren.showIms(args);
    }

}
