import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

class LinearRgb {
    int[] red;
    int[] green;
    int[] blue;
    int[] alpha;
    final boolean supportAlpha;

    public LinearRgb(int width, int height, boolean supportAlpha) {
        this.supportAlpha = supportAlpha;
        red = new int[height * width];
        green = new int[height * width];
        blue = new int[height * width];
        alpha = new int[height * width];
    }

    public void fromPixelByPos(int pos, int pixel) {
        alpha[pos] = (int)((pixel & 0xff000000) >> 24);
        red[pos] = (pixel & 0xff0000) >> 16;
        green[pos] = (pixel & 0x00ff00) >> 8;
        blue[pos] = pixel & 0x0000ff;
    }

    public int toPixelByPos(int pos) {
        return (blue[pos])
                | (green[pos] << 8)
                | (red[pos] << 16)
                | (alpha[pos] << 24);
    }

    public void valid() {
        if (supportAlpha) {
            validSingle(alpha);
        }
        validSingle(red);
        validSingle(green);
        validSingle(blue);
    }

    private void validSingle(int[] arr) {
        for (int i = 0; i < arr.length; ++i) {
            if (arr[i] > 255) {
                arr[i] = 255;
            }
            if (arr[i] < 0) {
                arr[i] = 0;
            }
        }
    }
}

public class GaussianBlur {
    public static void gaussianBlurImage(String path, int radius, double sigma, String inputName) {
        String[] inputs = inputName.split("\\.");
        String fileName = inputs[0];
        String suffix = inputs[1];
        byte[] input = readByteFromFile(path);
        byte[] transformBytes = transform(input, sigma, radius, "image/" + suffix);
        String outputFile = String.format("D:\\gaussia_picture_test\\output\\%s_%d_%.2f.%s",
                fileName, radius, sigma, suffix);
        writeBytesToFile(transformBytes, outputFile);
    }

    private static byte[] readByteFromFile(String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static void writeBytesToFile(byte[] data, String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            Files.write(path, data, StandardOpenOption.CREATE);
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    public static byte[] transform(byte[] data, double sigma, int radius, String mimeType) {
        if (data == null || data.length < 1) {
            System.err.println("got empty data to transform");
            return null;
        }

        if (radius < 3) {
            System.err.println("radius must large than:3");
            return null;
        }

        BufferedImage bufferedImage = getBufferedImageFromBinary(data);
        if (bufferedImage == null) {
            return null;
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        LinearRgb old = new LinearRgb(width, height, bufferedImage.isAlphaPremultiplied());
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                int pos = j * width + i;
                old.fromPixelByPos(pos, bufferedImage.getRGB(i, j));
            }
        }

        LinearRgb newRgb = new LinearRgb(width, height, bufferedImage.isAlphaPremultiplied());
        if (bufferedImage.getColorModel().isAlphaPremultiplied()) {
            gaussBlur(old.alpha, newRgb.alpha, width, height, radius, sigma);
        } else {
            System.arraycopy(old.alpha, 0, newRgb.alpha, 0, old.alpha.length);
        }
        gaussBlur(old.red, newRgb.red, width, height, radius, sigma);
        gaussBlur(old.green, newRgb.green, width, height, radius, sigma);
        gaussBlur(old.blue, newRgb.blue, width, height, radius, sigma);
        newRgb.valid();

        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                int pos = j * width + i;
                bufferedImage.setRGB(i, j, newRgb.toPixelByPos(pos));
            }
        }

        return getJpgBinaryFromBufferedImage(bufferedImage, mimeType);
    }

    private static BufferedImage getBufferedImageFromBinary(byte[] data) {
        InputStream input = new ByteArrayInputStream(data);
        try {
            return ImageIO.read(input);
        } catch (IOException ex) {
            System.err.println("can not convert byte to image:" + ex.toString());
            return null;
        }
    }

    private static String getFormatFromMime(String mimeType) {
        if (mimeType == null) {
            System.err.println("should not happen empty mime, return jpeg as default");
            return "jpeg";
        }
        String[] strs = mimeType.split("/");
        if (strs.length != 2) {
            System.err.println("wrong format mime:" + mimeType);
            return "jpeg";
        }
        return strs[1];
    }

    private static byte[] getJpgBinaryFromBufferedImage(BufferedImage bufferedImage, String mimeType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, getFormatFromMime(mimeType), out);
            return out.toByteArray();
        } catch (IOException ex) {
            System.err.println("can not get byte from image, ex:" + ex.toString());
            return null;
        }
    }

    private static void gaussBlur(int[] source, int[] dest, int width, int height, int radius, double sigma) {
        List<Integer> boxes = calculateGaussBox(sigma, radius);
        boxBlur(source, dest, width, height, (boxes.get(0) - 1) / 2);
        boxBlur(dest, source, width, height, (boxes.get(1) - 1) / 2);
        boxBlur(source, dest, width, height, (boxes.get(2) - 1) / 2);
    }

    private static List<Integer> calculateGaussBox(double sigma, int radius) {
        double value = Math.sqrt((12 * sigma * sigma / radius) + 1);
        int aValue = (int) Math.floor(value);
        if (aValue % 2 == 0) {
            aValue--;
        }

        double value1 = (12 * sigma * sigma - radius *aValue * aValue - 4 * radius * aValue - 3 * radius) / (-4 * aValue - 4);
        int bValue = (int) Math.round(value1);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < radius; ++i) {
            if (i < bValue) {
                result.add(aValue);
            } else {
                result.add(aValue + 2);
            }
        }
        return result;
    }

    private static void boxBlur(int[] source, int[] dest, int width, int height, int radius) {
        System.arraycopy(source, 0, dest, 0, source.length);
        boxBlurHorizontal(dest, source, width, height, radius);
        boxBlurTotal(source, dest, width, height, radius);
    }

    private static void boxBlurHorizontal(int[] source, int[] dest, int width, int height, int radius) {
        double weight = 1.0 / (2.0 * radius + 1);
        for (int i = 0; i < height; ++i) {
            int destPos = i * width, sourcePos = destPos, radiusPos = destPos + radius;
            int sourceBeginValue = source[destPos];
            int sourceEndValue = source[destPos + width - 1];
            int total = (radius + 1) * sourceBeginValue;

            for (int j = 0; j < radius; ++j) {
                total += source[destPos + j];
            }
            for (int j = 0; j <= radius; ++j) {
                total += (source[radiusPos++] - sourceBeginValue);
                dest[destPos++] = (int) Math.round(total * weight);
            }
            for (int j = radius + 1; j < width - radius; ++j) {
                total += (source[radiusPos++] - source[sourcePos++]);
                dest[destPos++] = (int) Math.round(total * weight);
            }
            for (int j = width - radius; j < width; ++j) {
                total += (sourceEndValue - source[sourcePos++]);
                dest[destPos++] = (int) Math.round(total * weight);
            }
        }
    }

    private static void boxBlurTotal(int[] source, int[] dest, int width, int height, int radius) {
        double weight = 1.0 / (2.0 * radius + 1);
        for (int i = 0; i < width; ++i) {
            int destPos = i, sourcePos = i, radiusPos = destPos + radius * width;

            int sourceBeginValue = source[destPos];
            int sourceEndValue = source[destPos + width * (height - 1)];
            int total = (radius + 1) * sourceBeginValue;
            for (int j = 0; j < radius; ++j) {
                total += source[destPos + j * width];
            }
            for (int j = 0; j <= radius; ++j) {
                total += (source[radiusPos] - sourceBeginValue);
                dest[destPos] = (int) Math.round(total * weight);
                radiusPos += width;
                destPos += width;
            }
            for (int j = radius + 1; j < height - radius; ++j) {
                total += (source[radiusPos] - source[sourcePos]);
                dest[destPos] = (int) Math.round(total * weight);
                sourcePos += width;
                radiusPos += width;
                destPos += width;
            }
            for (int j = height - radius; j < height; ++j) {
                total += (sourceEndValue - source[sourcePos]);
                dest[destPos] = (int) Math.round(total * weight);
                sourcePos += width;
                destPos += width;
            }
        }
    }
}