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

package com.shootoff.camera.perspective;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.util.Pair;

/*
 * 
 * distance to object (mm) =
 * 
 *  focal length (mm) * real height of the object (mm) * image height (pixels)
 * ---------------------------------------------------------------------------
 *                  object height (pixels) * sensor height (mm)
 * 
 */




public class PerspectiveManager {
	private static final Logger logger = LoggerFactory.getLogger(PerspectiveManager.class);
	
	// Should be put in a resource file
	public final static double C270_FOCAL_LENGTH = 4.0;
	public final static double C270_SENSOR_WIDTH = 3.58;
	public final static double C270_SENSOR_HEIGHT = 2.02;

	
	// All in millimeters
	private double focalLength = -1;
	private double sensorHeight = -1;
	private double sensorWidth = -1;
	private int cameraDistance = -1;
	private int shooterDistance = -1;
	
	private double pxPerMMhigh = -1;
	private double pxPerMMwide = -1;
	
	// All in pixels
	private int projectionHeight = -1;
	private int projectionWidth = -1;
	private int cameraHeight = -1;
	private int cameraWidth = -1;
	private int patternHeight = -1;
	private int patternWidth = -1;
	
	private int projectorResHeight = -1;
	private int projectorResWidth = -1;
	
	public PerspectiveManager()
	{
		
	}
	
	public void setCameraParams(double focalLength, double sensorWidth, double sensorHeight)
	{
		logger.trace("camera params fl {} sw {} sh {}", focalLength, sensorWidth, sensorHeight);
		
		this.focalLength = focalLength;
		this.sensorHeight = sensorHeight;
		this.sensorWidth = sensorWidth;
	}
	
	/* The real world width and height of the projector arena in the camera feed (in mm) */
	public void setProjectionSize(int width, int height)
	{
		logger.trace("projection w {} h {}", width, height);
		
		this.projectionHeight = height;
		this.projectionWidth = width;
	}
	
	/* The camera feed width and height (in px) */
	public void setCameraFeedSize(int width, int height)
	{
		logger.trace("camera feed w {} h {}", width, height);
		this.cameraHeight = height;
		this.cameraWidth = width;
	}
	
	/* The pattern feed width and height (in px) */
	public void setPatternSize(int width, int height)
	{
		logger.trace("pattern res w {} h {}", width, height);
		this.patternHeight = height;
		this.patternWidth = width;
	}
	
	
	
	/* Distance (in mm) camera to screen */
	public void setCameraDistance(int cameraDistance)
	{
		logger.trace("cameraDistance {}", cameraDistance);
		this.cameraDistance = cameraDistance;
	}
	
	/* Distance (in mm) camera to shooter */
	public void setShooterDistance(int shooterDistance)
	{
		logger.trace("shooterDistance {}", shooterDistance);
		this.shooterDistance = shooterDistance;
	}
	
	/* The resolution of the screen the arena is projected on
	 * Due to DPI scaling this might not correspond to the projector's
	 * resolution, but it is easier to think of that way */
	public void setProjectorResolution(int width, int height)
	{
		logger.trace("projector res w {} h {}", width, height);
		this.projectorResWidth = width;
		this.projectorResHeight = height;
	}
	
	public int getProjectionWidth()
	{
		return projectionWidth;
	}
	public int getProjectionHeight()
	{
		return projectionHeight;
	}
	
	public void calculateUnknown()
	{
		
		double wValues[] = { focalLength, patternWidth, cameraWidth, projectionWidth, sensorWidth, cameraDistance, projectorResWidth };
		
		boolean foundUnknown = false;
		for (int i = 0; i < wValues.length; i++)
		{
			if (wValues[i] == -1)
			{
				logger.trace("Unknown: {}", i);
				if (foundUnknown)
				{
					logger.error("More than one unknown");
					return;
				}
				foundUnknown = true;
			}
		}
		
		if (!foundUnknown)
		{
			logger.error("No unknown found");
			return;
		}
		
		if (projectionWidth == -1)
		{
			projectionWidth = (int) (((double)cameraDistance * (double)patternWidth * sensorWidth) / (focalLength * (double)cameraWidth)); 
			projectionHeight = (int) (((double)cameraDistance * (double)patternHeight * sensorHeight) / (focalLength * (double)cameraHeight));

			logger.trace("({} *  {} * {}) / ({} * {})", cameraDistance, patternWidth, sensorWidth, focalLength, cameraWidth);
			
			
			pxPerMMwide = ((double)projectorResWidth / (double)projectionWidth);
			pxPerMMhigh = ((double)projectorResHeight / (double)projectionHeight);
			
			logger.trace("pW {} pH {} - pxW {} pxH {}", projectionWidth, projectionHeight, pxPerMMwide, pxPerMMhigh);
		}
		else
		{
			logger.error("Unknown not supported");
			return;
		}
	}
	
	// Parameters in mm, return in px
	public Pair<Double, Double> calculateObjectSize(double realWidth, double realHeight, double realDistance, double desiredDistance)
	{
		if (projectionWidth == -1 || projectionHeight == -1 || shooterDistance == -1)
		{
			logger.error("projectionWidth or projectionHeight or shooterDistance unknown");
			return new Pair<Double, Double>(-1.0, -1.0);
		}
	
		// Make it appropriate size for the shooter
		double distRatio = realDistance / shooterDistance;
		
		// Make it appropriate size for the desired distance
		distRatio *= cameraDistance / desiredDistance;
		
		double adjWidthmm = realWidth * distRatio;
		double adjHeightmm = realHeight * distRatio;
		
		double adjWidthpx = adjWidthmm * pxPerMMwide;
		double adjHeightpx = adjHeightmm * pxPerMMhigh;
		
		logger.trace("rD {} dD {} sD {} dR {} - adjmm {} {} adjpx {} {}", realDistance, desiredDistance, shooterDistance, distRatio,
				adjWidthmm, adjHeightmm, adjWidthpx, adjHeightpx);
		
		return new Pair<Double, Double>(adjWidthpx, adjHeightpx);
	}
	

}
