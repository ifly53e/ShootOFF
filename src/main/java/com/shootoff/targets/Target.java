package com.shootoff.targets;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.shootoff.camera.Shot;
import com.shootoff.gui.TargetView.TargetSelectionListener;

import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Node;

/**
 * A potentially animated target that the user can shoot, reposition, and
 * resize. This interface should be implemented by a class that implements the
 * GUI code to allow users to see and interact with targets.
 * 
 * @author phrack
 */
public interface Target {
	public static final String TAG_IGNORE_HIT = "ignoreHit";
	public static final String TAG_RESIZABLE = "isResizable";
	public static final String TAG_OPACITY = "opacity";
	public static final String TAG_VISIBLE = "visible";
	public static final String TAG_DEFAULT_PERCEIVED_WIDTH = "defaultPerceivedWidth";
	public static final String TAG_DEFAULT_PERCEIVED_HEIGHT = "defaultPerceivedHeight";
	public static final String TAG_DEFAULT_PERCEIVED_DISTANCE = "defaultDistance";
	public static final String TAG_CURRENT_PERCEIVED_DISTANCE = "currentDistance";
	public static final String TAG_SHOOTER_DISTANCE = "shooterDistance";
	
	File getTargetFile();

	int getTargetIndex();

	void addTargetChild(Node child);

	void removeTargetChild(Node child);

	List<TargetRegion> getRegions();

	/**
	 * Check whether or not this target contains a particular region.
	 * 
	 * @param region
	 *            a region that may exist in this target
	 * @return <tt>true</tt> if the target contains <tt>region</tt>
	 */
	boolean hasRegion(TargetRegion region);

	void setVisible(boolean isVisible);

	void setPosition(double x, double y);

	Point2D getPosition();

	void setDimensions(double newWidth, double newHeight);

	Dimension2D getDimension();

	Bounds getBoundsInParent();

	void animate(TargetRegion region, List<String> args);

	void reverseAnimation(TargetRegion region);

	Optional<Hit> isHit(Shot shot);

	boolean tagExists(String name);

	String getTag(String name);

	Map<String, String> getAllTags();
	
	void setTargetSelectionListener(TargetSelectionListener selectionListener);
}
