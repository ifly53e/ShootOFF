/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Copyright (C) 2016 phrack
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shootoff.camera.arenamask;

import java.awt.image.BufferedImage;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.shootoff.camera.Camera;

public class Mask {
	private BufferedImage bImage;
	private Mat mask;
	private final long timestamp;

	public Mask(BufferedImage bImage, long timestamp) {
		this.bImage = bImage;
		this.timestamp = timestamp;
		this.mask = new Mat();
	}

	private int avgMaskLum = 0;

	public int getAvgMaskLum() {
		return avgMaskLum;
	}

	public BufferedImage getMaskImage() {
		return bImage;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Mat getLumMask(Size targetSize) {
		// Indicates it is already initialized
		if (mask != null && mask.rows() > 0) return mask;

		mask = new Mat((int) targetSize.height, (int) targetSize.width, CvType.CV_32S);

		Mat src = Camera.bufferedImageToMat(bImage);
		Imgproc.resize(src, src, targetSize);

		bImage = Camera.matToBufferedImage(src);

		Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2HSV);

		long tmpAvgMaskLum = 0;
		for (int y = 0; y < src.rows(); y++) {
			for (int x = 0; x < src.cols(); x++) {
				byte[] px = { 0, 0, 0 };
				src.get(y, x, px);
				int pxS = px[1] & 0xFF;
				int pxV = px[2] & 0xFF;

				int pxLum = ((255 - pxS) * pxV);

				tmpAvgMaskLum += pxLum;

				int[] dstLum = { pxLum };

				mask.put(y, x, dstLum);
			}
		}

		avgMaskLum = (int) (tmpAvgMaskLum / (mask.rows() * mask.cols()));

		return mask;
	}
}
