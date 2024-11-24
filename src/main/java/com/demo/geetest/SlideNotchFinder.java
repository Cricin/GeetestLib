package com.demo.geetest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.OptionalInt;

/** 滑动验证码寻找缺口位置，比较简单这里就不用机器学习找了 */
public final class SlideNotchFinder {

  /** 寻找缺口位置，这里返回的x坐标是已经经过校正的坐标 */
  public static int findNotchX(byte[] fullImageData, byte[] notchImageData, boolean needRestore, boolean save) {
    BufferedImage image1 = needRestore ? restore(fullImageData) : read(fullImageData);
    BufferedImage image2 = needRestore ? restore(notchImageData) : read(notchImageData);

    if (save) {
      write(image1, "slide_origin");
      write(image2, "slide_notch");
    }

    // 创建最终标注图
    BufferedImage output = null;
    Graphics outputGraphics = null;
    if (save) {
      output = new BufferedImage(image2.getWidth(), image2.getHeight(), BufferedImage.TYPE_INT_RGB);
      output.setData(image2.getData());
      outputGraphics = output.getGraphics();
      outputGraphics.setColor(Color.RED);
    }

    // 两张图做异或操作去除大部分干扰区域
    Graphics d = image2.getGraphics();
    d.setXORMode(Color.WHITE);
    d.drawImage(image1, 0, 0, null);

    if (save) {
      write(image2, "slide_xor");
    }

    // 灰度
    for (int i = 0; i < image2.getWidth(); i++) {
      for (int j = 0; j < image2.getHeight(); j++) {
        int color = image2.getRGB(i, j);
        final int r = (color >> 16) & 0xff;
        final int g = (color >> 8) & 0xff;
        final int b = color & 0xff;
        int gray = (r+g+b)/3;//均值法灰度效果要好一点
        image2.setRGB(i, j, (gray << 16) + (gray << 8) + gray);
      }
    }

    if (save) {
      write(image2, "slide_gray");
    }

    // 获取每个x坐标对应y轴上的颜色和
    int[] colorSum = new int[image2.getWidth()];
    int[] array = new int[image2.getHeight()];
    for (int i = 0; i < image2.getWidth(); i++) {
      int[] rgb = image2.getRGB(i, 0, 1, image2.getHeight(), array, 0, 1);
      int c = 0;
      for (int v : rgb) {
        c += (v & 0xff);
      }
      colorSum[i] = Math.abs(c);
    }

    // 标注颜色强度
    OptionalInt max = Arrays.stream(colorSum).max();
    if (max.isPresent() && outputGraphics != null) {
      int m = max.getAsInt();
      for (int i = 0; i < image2.getWidth(); i++) {
        int h = colorSum[i] * 160 / m;
        outputGraphics.drawLine(i, 160 - h, i, 160 - h);
      }
    }

    // 缺口大小图片为56像素，这里以56为宽度，统计颜色最深的区块的x坐标
    int min = Integer.MAX_VALUE;
    int pos = 0;
    for (int x = 0; x < colorSum.length - 56; x += 2) {
      int c = 0;
      for(int i = 0; i < 56; i++) {
        c += colorSum[x + i];
      }
      if (c < min) {
        min = c;
        pos = x;
      }
    }

    if (output != null) {
      int centerX = pos + 28;
      outputGraphics.drawLine(centerX, 0, centerX, output.getHeight());
      write(output, "slide_labeled");
    }
    // 这里取的是缺口在缺口图中的起始x坐标，实际上缺口图距离原图边缘还有大概6个像素的距离，这里需要减去
    return pos;
  }

  private static BufferedImage read(byte[] imageData) {
    try {
      return ImageIO.read(new ByteArrayInputStream(imageData));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static void write(BufferedImage image, String filenameWithoutExt) {
    try {
      ImageIO.write(image, "png", new File(filenameWithoutExt + ".png"));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /** 还原乱序的验证图片 */
  private static BufferedImage restore(byte[] rawImageData) {
    BufferedImage image;
    try {
      image = ImageIO.read(new ByteArrayInputStream(rawImageData));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    int[] _Ge = {39, 38, 48, 49, 41, 40, 46, 47, 35, 34, 50, 51, 33, 32, 28, 29, 27, 26, 36, 37, 31, 30, 44, 45, 43,
      42, 12, 13, 23, 22, 14, 15, 21, 20, 8, 9, 25, 24, 6, 7, 3, 2, 0, 1, 11, 10, 4, 5, 19, 18, 16, 17};
    int w_sep = 10, h_sep = 80;

    BufferedImage out = new BufferedImage(260, 160, BufferedImage.TYPE_INT_RGB);
    Graphics d = out.getGraphics();

    for (int i = 0; i < _Ge.length; i++) {
      int x = _Ge[i] % 26 * 12 + 1;
      int y = _Ge[i] > 25 ? h_sep : 0;

      int newX = i % 26 * 10;
      int newY = i > 25 ? h_sep : 0;
      d.drawImage(image, newX, newY, newX + w_sep, newY + h_sep,
        x, y, x + w_sep, y + h_sep, null);
    }
    return out;
  }
}