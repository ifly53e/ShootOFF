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

package com.shootoff.gui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.camera.CameraManager;
import com.shootoff.camera.CameraView;
import com.shootoff.camera.Shot;
import com.shootoff.camera.processors.MalfunctionsProcessor;
import com.shootoff.camera.processors.ShotProcessor;
import com.shootoff.camera.processors.VirtualMagazineProcessor;
import com.shootoff.camera.recorders.ShotRecorder;
import com.shootoff.config.Configuration;
import com.shootoff.gui.pane.ProjectorArenaPane;
import com.shootoff.plugins.TrainingExercise;
import com.shootoff.plugins.TrainingExerciseBase;
import com.shootoff.targets.Hit;
import com.shootoff.targets.ImageRegion;
import com.shootoff.targets.RegionType;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.targets.io.TargetIO;
import com.shootoff.targets.io.TargetIO.TargetComponents;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class CanvasManager implements CameraView {
	private final Logger logger = LoggerFactory.getLogger(CanvasManager.class);
	private final Group canvasGroup;
	private final Configuration config;
	protected CameraManager cameraManager;

	private final VBox diagnosticsVBox = new VBox();
	private static final int DIAGNOSTIC_POOL_SIZE = 10;
	private static final int DIAGNOSTIC_CHIME_DELAY = 5000; // ms
	private final ScheduledExecutorService diagnosticExecutorService = Executors
			.newScheduledThreadPool(DIAGNOSTIC_POOL_SIZE);
	private final Map<Label, ScheduledFuture<Void>> diagnosticFutures = new HashMap<Label, ScheduledFuture<Void>>();
	private final Image muteImage = new Image(CanvasManager.class.getResourceAsStream("/images/mute.png"));
	private final Image soundImage = new Image(CanvasManager.class.getResourceAsStream("/images/sound.png"));

	private final Resetter resetter;
	private final String cameraName;
	private final ObservableList<ShotEntry> shotEntries;
	private final ImageView background = new ImageView();
	public final List<Shot> shots;
	private final List<Target> targets = new ArrayList<Target>();

	private ProgressIndicator progress;
	private Optional<ContextMenu> contextMenu = Optional.empty();
	private Optional<TargetView> selectedTarget = Optional.empty();
	private boolean showShots = true;
	private boolean hadMalfunction = false;
	private boolean hadReload = false;

	private static final int MAX_FEED_FPS = 15;
	private static final int MINIMUM_FRAME_DELTA = 1000 / MAX_FEED_FPS; // ms
	private long lastFrameTime = 0;

	private Optional<ProjectorArenaPane> arenaPane = Optional.empty();
	private Optional<Bounds> projectionBounds = Optional.empty();

	public CanvasManager(Group canvasGroup, Configuration config, Resetter resetter, String cameraName,
			ObservableList<ShotEntry> shotEntries) {
		this.canvasGroup = canvasGroup;
		this.config = config;
		this.resetter = resetter;
		this.cameraName = cameraName;
		this.shotEntries = shotEntries;
		shots = Collections.synchronizedList(new ArrayList<Shot>());

		this.background.setOnMouseClicked((event) -> {
			toggleTargetSelection(Optional.empty());
		});

		if (Platform.isFxApplicationThread()) {
			progress = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
			progress.setPrefHeight(config.getDisplayHeight());
			progress.setPrefWidth(config.getDisplayWidth());
			canvasGroup.getChildren().add(progress);
			canvasGroup.getChildren().add(diagnosticsVBox);
			diagnosticsVBox.setAlignment(Pos.CENTER);
			diagnosticsVBox.setFillWidth(true);
			diagnosticsVBox.setPrefWidth(config.getDisplayWidth());
		}

		canvasGroup.setOnMouseClicked((event) -> {
			if (contextMenu.isPresent() && contextMenu.get().isShowing())
				contextMenu.get().hide();

			if (config.inDebugMode() && event.getButton() == MouseButton.PRIMARY) {
				// Click to shoot
				final Color shotColor;

				if (event.isShiftDown()) {
					shotColor = Color.RED;
				} else if (event.isControlDown()) {
					shotColor = Color.GREEN;
				} else {
					return;
				}

				// Skip the camera manager for injected shots made from the
				// arena tab otherwise they get scaled before the call to
				// addArenaShot when they go through the arena camera feed's
				// canvas manager
				if (this instanceof MirroredCanvasManager) {
					addShot(
							new Shot(shotColor, event.getX(), event.getY(), cameraManager.getCurrentFrameTimestamp(), config.getMarkerRadius()),
							false);
				} else {
					cameraManager.injectShot(shotColor, event.getX(), event.getY(), false);
				}
				return;
			} else if (contextMenu.isPresent() && event.getButton() == MouseButton.SECONDARY) {
				contextMenu.get().show(canvasGroup, event.getScreenX(), event.getScreenY());
			}
		});
	}

	@Override
	public void close() {
		diagnosticExecutorService.shutdownNow();
	}

	@Override
	public void setCameraManager(CameraManager cameraManager) {
		this.cameraManager = cameraManager;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public boolean addChild(Node c) {
		return getCanvasGroup().getChildren().add(c);
	}

	@Override
	public boolean removeChild(Node c) {
		return getCanvasGroup().getChildren().remove(c);
	}

	public Label addDiagnosticMessage(final String message, final long chimeDelay, final Color backgroundColor) {
		final Label diagnosticLabel = new Label(message);
		diagnosticLabel.setStyle("-fx-background-color: " + colorToWebCode(backgroundColor));

		final ImageView muteView = new ImageView();
		muteView.setFitHeight(20);
		muteView.setFitWidth(muteView.getFitHeight());
		if (config.isChimeMuted(message)) {
			muteView.setImage(muteImage);
		} else {
			muteView.setImage(soundImage);
		}

		diagnosticLabel.setContentDisplay(ContentDisplay.RIGHT);
		diagnosticLabel.setGraphic(muteView);
		diagnosticLabel.setOnMouseClicked((event) -> {
			if (config.isChimeMuted(message)) {
				muteView.setImage(soundImage);
				config.unmuteMessageChime(message);
			} else {
				muteView.setImage(muteImage);
				config.muteMessageChime(message);
			}

			try {
				config.writeConfigurationFile();
			} catch (Exception e) {
				logger.error("Failed persisting message's (" + message + ") chime mute settings.", e);
			}
		});

		Platform.runLater(() -> diagnosticsVBox.getChildren().add(diagnosticLabel));

		if (chimeDelay > 0 && !config.isChimeMuted(message) && !diagnosticExecutorService.isShutdown()) {
			@SuppressWarnings("unchecked")
			ScheduledFuture<Void> chimeFuture = (ScheduledFuture<Void>) diagnosticExecutorService.schedule(
					() -> TrainingExerciseBase.playSound("sounds/chime.wav"), chimeDelay, TimeUnit.MILLISECONDS);
			diagnosticFutures.put(diagnosticLabel, chimeFuture);
		}

		return diagnosticLabel;
	}

	@Override
	public Label addDiagnosticMessage(String message, Color backgroundColor) {
		return addDiagnosticMessage(message, DIAGNOSTIC_CHIME_DELAY, backgroundColor);
	}

	@Override
	public void removeDiagnosticMessage(Label diagnosticLabel) {
		if (diagnosticFutures.containsKey(diagnosticLabel)) {
			diagnosticFutures.get(diagnosticLabel).cancel(false);
			diagnosticFutures.remove(diagnosticLabel);
		}

		Platform.runLater(() -> diagnosticsVBox.getChildren().remove(diagnosticLabel));
	}

	public static String colorToWebCode(Color color) {
		return String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
				(int) (color.getBlue() * 255));
	}

	private void jdk8094135Warning() {
		Platform.runLater(() -> {
			Alert cameraAlert = new Alert(AlertType.ERROR);
			cameraAlert.setTitle("Internal Error");
			cameraAlert.setHeaderText("Internal Error -- Likely Too Many false Shots");
			cameraAlert.setResizable(true);
			cameraAlert.setContentText("An internal error due to JDK bug 8094135 occured in Java that will cause all "
					+ "of your shots to be lost. This error is most likely to occur when you are getting a lot of false "
					+ "shots due to poor lighting conditions and/or a poor camera setup. Please put the camera in front "
					+ "of the shooter and turn off any bright lights in front of the camera that are the same height as "
					+ "the shooter. If problems persist you may need to restart ShootOFF.");
			cameraAlert.show();

			shots.clear();
			shotEntries.clear();
		});
	}

	public String getCameraName() {
		return cameraName;
	}

	public void setContextMenu(ContextMenu menu) {
		this.contextMenu = Optional.of(menu);
	}

	public void setBackgroundFit(double width, double height) {
		background.setFitWidth(width);
		background.setFitHeight(height);
	}

	@Override
	public void updateBackground(BufferedImage frame, Optional<Bounds> projectionBounds) {
		updateCanvasGroup();

		if (frame == null) {
			background.setX(0);
			background.setY(0);
			background.setImage(null);
			return;
		}

		// Prevent the webcam feed from being refreshed faster than some maximum
		// FPS otherwise we waste CPU cycles converting a frames to show the
		// user and these are cycles we could spend detecting shots. A lower
		// FPS (e.g. ~15) looks perfect fine to a person
		if (System.currentTimeMillis() - lastFrameTime < MINIMUM_FRAME_DELTA)
			return;
		else
			lastFrameTime = System.currentTimeMillis();

		Image img;
		if (projectionBounds.isPresent()) {
			Bounds translatedBounds = translateCameraToCanvas(projectionBounds.get());
			background.setX(translatedBounds.getMinX());
			background.setY(translatedBounds.getMinY());

			img = SwingFXUtils.toFXImage(
					resize(frame, (int) translatedBounds.getWidth(), (int) translatedBounds.getHeight()), null);
		} else {
			background.setX(0);
			background.setY(0);

			img = SwingFXUtils.toFXImage(resize(frame, (int) config.getDisplayWidth(), (int) config.getDisplayHeight()),
					null);
		}

		Platform.runLater(() -> background.setImage(img));
	}

	public void updateBackground(Image img) {
		updateCanvasGroup();
		background.setX(0);
		background.setY(0);
		Platform.runLater(() -> background.setImage(img));
	}

	private void updateCanvasGroup() {
		if (!canvasGroup.getChildren().contains(background)) {
			if (canvasGroup.getChildren().isEmpty()) {
				canvasGroup.getChildren().add(background);
			} else {
				// Remove the wait spinner and replace it
				// with the background
				Platform.runLater(() -> canvasGroup.getChildren().set(0, background));
			}
		}
	}

	private BufferedImage resize(BufferedImage source, int width, int height) {
		if (source.getWidth() == width && source.getHeight() == height) return source;

		BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = tmp.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(source, 0, 0, width, height, null);
		g2.dispose();

		return tmp;
	}

	public BufferedImage getBufferedImage() {
		BufferedImage projectedScene = SwingFXUtils.fromFXImage(canvasGroup.getScene().snapshot(null), null);
		return projectedScene;
	}

	public Bounds translateCameraToCanvas(Bounds bounds) {
		if (config.getDisplayWidth() == cameraManager.getFeedWidth()
				&& config.getDisplayHeight() == cameraManager.getFeedHeight())
			return bounds;

		double scaleX = (double) config.getDisplayWidth() / (double) cameraManager.getFeedWidth();
		double scaleY = (double) config.getDisplayHeight() / (double) cameraManager.getFeedHeight();

		double minX = (bounds.getMinX() * scaleX);
		double minY = (bounds.getMinY() * scaleY);
		double width = (bounds.getWidth() * scaleX);
		double height = (bounds.getHeight() * scaleY);

		logger.trace("translateCameraToCanvas {} {} {} {} - {} {} {} {}", bounds.getMinX(), bounds.getMinY(),
				bounds.getWidth(), bounds.getHeight(), minX, minY, width, height);

		return new BoundingBox(minX, minY, width, height);
	}

	public Bounds translateCanvasToCamera(Bounds bounds) {
		if (config.getDisplayWidth() == cameraManager.getFeedWidth()
				&& config.getDisplayHeight() == cameraManager.getFeedHeight())
			return bounds;

		double scaleX = (double) cameraManager.getFeedWidth() / (double) config.getDisplayWidth();
		double scaleY = (double) cameraManager.getFeedHeight() / (double) config.getDisplayHeight();

		double minX = (bounds.getMinX() * scaleX);
		double minY = (bounds.getMinY() * scaleY);
		double width = (bounds.getWidth() * scaleX);
		double height = (bounds.getHeight() * scaleY);

		logger.trace("translateCanvasToCamera {} {} {} {} - {} {} {} {}", bounds.getMinX(), bounds.getMinY(),
				bounds.getWidth(), bounds.getHeight(), minX, minY, width, height);

		return new BoundingBox(minX, minY, width, height);
	}

	public Group getCanvasGroup() {
		return canvasGroup;
	}

	@Override
	public void clearShots() {
		final Runnable clearShotsAction = () -> {
			for (Shot shot : shots) {
				canvasGroup.getChildren().remove(shot.getMarker());
			}

			shots.clear();
			try {
				if (shotEntries != null) shotEntries.clear();
			} catch (NullPointerException npe) {
				logger.error("JDK 8094135 exception", npe);
				jdk8094135Warning();
			}
			if (arenaPane.isPresent()) arenaPane.get().getCanvasManager().clearShots();
		};

		if (Platform.isFxApplicationThread()) {
			clearShotsAction.run();
		} else {
			Platform.runLater(clearShotsAction);
		}
	}

	@Override
	public void reset() {
		// Reset animations
		for (Target target : targets) {
			for (TargetRegion region : target.getRegions()) {
				if (region.getType() == RegionType.IMAGE) ((ImageRegion) region).reset();
			}
		}

		if (arenaPane.isPresent()) {
			arenaPane.get().getCanvasManager().reset();
		}

		clearShots();
	}

	public void setProjectorArena(ProjectorArenaPane arenaPane, Bounds projectionBounds) {
		this.arenaPane = Optional.ofNullable(arenaPane);
		this.projectionBounds = Optional.ofNullable(projectionBounds);
	}

	public void setShowShots(boolean showShots) {
		if (this.showShots != showShots) {
			for (Shot shot : shots)
				shot.getMarker().setVisible(showShots);
		}

		this.showShots = showShots;
	}

	private void notifyShot(Shot shot) {
		if (config.getSessionRecorder().isPresent()) {
			for (CameraManager cm : config.getRecordingManagers())
				cm.notifyShot(shot);
		}
	}

	private Optional<String> createVideoString(Shot shot) {
		if (config.getSessionRecorder().isPresent() && !config.getRecordingManagers().isEmpty()) {
			final StringBuilder sb = new StringBuilder();

			for (CameraManager cm : config.getRecordingManagers()) {
				ShotRecorder r = cm.getRevelantRecorder(shot);

				if (sb.length() > 0) {
					sb.append(",");
				}

				sb.append(r.getCameraName().replaceAll(":", "-"));
				sb.append(":");
				sb.append(r.getRelativeVideoFile().getPath());
			}

			return Optional.of(sb.toString());
		}

		return Optional.empty();
	}

	private Optional<ShotProcessor> processShot(Shot shot) {

		Optional<ShotProcessor> rejectingProcessor = Optional.empty();

		for (ShotProcessor processor : config.getShotProcessors()) {
			if (!processor.processShot(shot)) {
				if (processor instanceof MalfunctionsProcessor) {
					hadMalfunction = true;
				} else if (processor instanceof VirtualMagazineProcessor) {
					hadReload = true;
				}

				rejectingProcessor = Optional.of(processor);
				logger.debug("Processing Shot: Shot Rejected By {}", processor.getClass().getName());
				break;
			}
		}

		return rejectingProcessor;
	}

	private void recordRejectedShot(Shot shot, ShotProcessor rejectingProcessor) {

		if (!config.getSessionRecorder().isPresent()) return;

		notifyShot(shot);

		Optional<String> videoString = createVideoString(shot);

		if (rejectingProcessor instanceof MalfunctionsProcessor) {
			config.getSessionRecorder().get().recordShot(cameraName, shot, true, false, Optional.empty(),
					Optional.empty(), videoString);
		} else if (rejectingProcessor instanceof VirtualMagazineProcessor) {
			config.getSessionRecorder().get().recordShot(cameraName, shot, false, true, Optional.empty(),
					Optional.empty(), videoString);
		}
	}

	// For testing
	public List<Shot> getShots() {
		return shots;
	}

	@Override
	public void addShot(Shot shot, boolean isMirroredShot) {
		if (!isMirroredShot) {
			Optional<ShotProcessor> rejectingProcessor = processShot(shot);
			if (rejectingProcessor.isPresent()) {
				recordRejectedShot(shot, rejectingProcessor.get());
				return;
			} else {
				notifyShot(shot);
			}

			// TODO: Add separate infrared sound or switch config to read "red/infrared"
			if (config.useRedLaserSound() && (
					Color.RED.equals(shot.getColor()) ||
					Color.BLACK.equals(shot.getColor())
					)) {
				TrainingExerciseBase.playSound(config.getRedLaserSound());
			} else if (config.useGreenLaserSound() && Color.GREEN.equals(shot.getColor())) {
				TrainingExerciseBase.playSound(config.getGreenLaserSound());
			}
		}

		// Create a shot entry to show the shot's data
		// in the shot timer table if the shot timer
		// table is in use
		if (shotEntries != null) {
			Optional<Shot> lastShot = Optional.empty();

			if (shotEntries.size() > 0) lastShot = Optional.of(shotEntries.get(shotEntries.size() - 1).getShot());

			final ShotEntry shotEntry;
			if (hadMalfunction || hadReload) {
				shotEntry = new ShotEntry(shot, lastShot, config.getShotTimerRowColor(), hadMalfunction, hadReload);
				hadMalfunction = false;
				hadReload = false;
			} else {
				shotEntry = new ShotEntry(shot, lastShot, config.getShotTimerRowColor(), false, false);
			}

			try {
				shotEntries.add(shotEntry);
			} catch (NullPointerException npe) {
				logger.error("JDK 8094135 exception", npe);
				jdk8094135Warning();
			}
		}

		shots.add(shot);
		drawShot(shot);

		Optional<String> videoString = createVideoString(shot);
		Optional<TrainingExercise> currentExercise = config.getExercise();
		Optional<Hit> hit = checkHit(shot, videoString, isMirroredShot);
		if (hit.isPresent() && hit.get().getHitRegion().tagExists("command")) executeRegionCommands(hit.get());

		boolean processedShot = false;

		if (arenaPane.isPresent() && projectionBounds.isPresent()) {
			Bounds b = projectionBounds.get();

			if (b.contains(shot.getX(), shot.getY())) {
				final double x_scale = arenaPane.get().getWidth() / b.getWidth();
				final double y_scale = arenaPane.get().getHeight() / b.getHeight();

				final Shot arenaShot = new Shot(shot.getColor(), (shot.getX() - b.getMinX()) * x_scale,
						(shot.getY() - b.getMinY()) * y_scale, shot.getTimestamp(), shot.getFrame(),
						config.getMarkerRadius());

				processedShot = arenaPane.get().getCanvasManager().addArenaShot(arenaShot, videoString, isMirroredShot);
			}
		}

		if (!isMirroredShot && currentExercise.isPresent() && !processedShot) {
			currentExercise.get().shotListener(shot, hit);
		}
	}

	public boolean addArenaShot(Shot shot, Optional<String> videoString, boolean isMirroredShot) {
		shots.add(shot);
		drawShot(shot);

		Optional<TrainingExercise> currentExercise = config.getExercise();
		Optional<Hit> hit = checkHit(shot, videoString, isMirroredShot);
		if (hit.isPresent() && hit.get().getHitRegion().tagExists("command")) {
			executeRegionCommands(hit.get());
		}

		if (!isMirroredShot) {
			if (currentExercise.isPresent()) {
				currentExercise.get().shotListener(shot, hit);
				return true;
			}
		}

		return false;
	}

	private void drawShot(Shot shot) {
		Runnable drawShotAction  = () -> {
			canvasGroup.getChildren().add(shot.getMarker());
			shot.getMarker().setVisible(showShots);
		};

		if (Platform.isFxApplicationThread()) {
			drawShotAction.run();
		} else {
			Platform.runLater(drawShotAction);
		}
	}

	protected Optional<Hit> checkHit(Shot shot, Optional<String> videoString, boolean isMirroredShot) {
		// Targets are in order of when they were added, thus we must search in
		// reverse to ensure shots register for the top target when targets
		// overlap
		for (ListIterator<Target> li = targets.listIterator(targets.size()); li.hasPrevious();) {
			Target target = li.previous();

			//Optional<Hit> hit = target.isHit(shot);
			logger.debug("checking target: {}, {}",target, target.getTargetFile().getName());

			Optional<Hit> hit;
			if(config.getUseHitMod()){
				hit = target.isHit_mod(shot);
			}else{
				hit = target.isHit(shot);
			}

			if (hit.isPresent()) {
				TargetRegion region = hit.get().getHitRegion();

				if (config.inDebugMode()) {
					Map<String, String> tags = region.getAllTags();

					StringBuilder tagList = new StringBuilder();
					for (Iterator<Entry<String, String>> it = tags.entrySet().iterator(); it.hasNext();) {
						Entry<String, String> entry = it.next();
						tagList.append(entry.getKey());
						tagList.append(":");
						tagList.append(entry.getValue());
						if (it.hasNext()) tagList.append(", ");
					}

					logger.debug("Processing Shot: Found Hit Region For Shot ({}, {}), Type ({}), Tags ({})",
							shot.getX(), shot.getY(), region.getType(), tagList.toString());
				}

				if (!isMirroredShot && config.getSessionRecorder().isPresent()) {
					config.getSessionRecorder().get().recordShot(cameraName, shot, false, false, Optional.of(target),
							Optional.of(target.getRegions().indexOf(region)), videoString);
				}

				return hit;
			}
		}

		logger.debug("Processing Shot: Did Not Find Hit For Shot ({}, {})", shot.getX(), shot.getY());

		if (!isMirroredShot && config.getSessionRecorder().isPresent()) {
			config.getSessionRecorder().get().recordShot(cameraName, shot, false, false, Optional.empty(),
					Optional.empty(), videoString);
		}

		return Optional.empty();
	}

	private void executeRegionCommands(Hit hit) {
		TargetView.parseCommandTag(hit.getHitRegion(), (commands, commandName, args) -> {
			switch (commandName) {
			case "reset":
				resetter.reset();
				break;

			case "animate":
				hit.getTarget().animate(hit.getHitRegion(), args);
				break;

			case "reverse":
				hit.getTarget().reverseAnimation(hit.getHitRegion());
				break;

			case "play_sound":
				// If there is a second parameter, we should look to see
				// if it's an image region that is down and if so, don't
				// play the sound
				if (args.size() == 2) {
					Optional<TargetRegion> namedRegion = TargetView.getTargetRegionByName(targets, hit.getHitRegion(),
							args.get(1));
					if (namedRegion.isPresent() && namedRegion.get().getType() == RegionType.IMAGE) {
						if (!((ImageRegion) namedRegion.get()).onFirstFrame()) break;
					}
				}

				// If the string starts with an @ we are supposed to
				// load the sound as a resource from the current exercises
				// JAR file. This indicates that the target is from
				// a modular exercise
				String soundPath = args.get(0);
				if (config.getExercise().isPresent() && '@' == soundPath.charAt(0)) {
					InputStream is = config.getExercise().get().getClass().getResourceAsStream(soundPath.substring(1));
					TrainingExerciseBase.playSound(new BufferedInputStream(is));
				} else if ('@' != soundPath.charAt(0)) {
					TrainingExerciseBase.playSound(soundPath);
				} else {
					logger.error("Can't play {} because it is a resource in an exercise but no exercise is loaded.",
							soundPath);
				}

				break;
			}
		});
	}

	protected Optional<TargetComponents> loadTarget(File targetFile, boolean playAnimations) {
		Optional<TargetComponents> targetComponents;

		if ('@' == targetFile.toString().charAt(0)) {
			if (!config.getPlugin().isPresent()) {
				throw new AssertionError("Loaded target from training exercise resources, but a plugin does not "
						+ "exist for the target.");
			}

			ClassLoader loader = config.getPlugin().get().getLoader();

			InputStream resourceTargetStream = loader
					.getResourceAsStream(targetFile.toString().substring(1).replace("\\", "/"));
			if (resourceTargetStream != null) {
				targetComponents = TargetIO.loadTarget(resourceTargetStream, playAnimations, loader);
			} else {
				targetComponents = Optional.empty();
				logger.error("Error adding target from stream created from resource {}",
						targetFile.toString().substring(1).replace("\\", "/"));
			}
		} else {
			targetComponents = TargetIO.loadTarget(targetFile, playAnimations);
		}

		return targetComponents;
	}

	public Optional<Target> addTarget(File targetFile, boolean playAnimations) {
		Optional<TargetComponents> targetComponents = loadTarget(targetFile, playAnimations);

		if (targetComponents.isPresent()) {
			TargetComponents tc = targetComponents.get();
			Optional<Target> target = Optional.of(addTarget(targetFile, tc.getTargetGroup(), tc.getTargetTags(), true));

			if (config.getSessionRecorder().isPresent() && target.isPresent()) {
				config.getSessionRecorder().get().recordTargetAdded(cameraName, target.get());
			}

			return target;
		}

		return Optional.empty();
	}

	@Override
	public Optional<Target> addTarget(File targetFile) {
		return addTarget(targetFile, true);
	}

	public Target addTarget(File targetFile, Group targetGroup, Map<String, String> targetTags, boolean userDeletable) {
		final TargetView newTarget;

		if (this instanceof MirroredCanvasManager) {
			newTarget = new MirroredTarget(targetFile, targetGroup, targetTags, config, this, userDeletable);
		} else {
			newTarget = new TargetView(targetFile, targetGroup, targetTags, config, this, userDeletable);
		}

		return addTarget(newTarget);
	}

	@Override
	public Target addTarget(Target newTarget) {
		final Runnable addTargetAction = () -> canvasGroup.getChildren().add(((TargetView) newTarget).getTargetGroup());

		if (Platform.isFxApplicationThread()) {
			addTargetAction.run();
		} else {
			Platform.runLater(addTargetAction);
		}

		targets.add(newTarget);

		Optional<TrainingExercise> enabledExercise = config.getExercise();
		if (enabledExercise.isPresent())
			enabledExercise.get().targetUpdate(newTarget, TrainingExercise.TargetChange.ADDED);

		return newTarget;
	}

	public void removeTarget(Target target) {
		final Runnable removeTargetAction = () -> canvasGroup.getChildren()
				.remove(((TargetView) target).getTargetGroup());

		if (Platform.isFxApplicationThread()) {
			removeTargetAction.run();
		} else {
			Platform.runLater(removeTargetAction);
		}

		if (config.getSessionRecorder().isPresent()) {
			config.getSessionRecorder().get().recordTargetRemoved(cameraName, target);
		}

		targets.remove(target);

		Optional<TrainingExercise> enabledExercise = config.getExercise();
		if (enabledExercise.isPresent())
			enabledExercise.get().targetUpdate(target, TrainingExercise.TargetChange.REMOVED);
	}

	public void clearTargets() {
		for (Target t : new ArrayList<Target>(targets)) {
			removeTarget(t);
		}
	}

	public List<Target> getTargets() {
		return targets;
	}

	public void toggleTargetSelection(Optional<TargetView> newSelection) {
		if (selectedTarget.isPresent()) selectedTarget.get().toggleSelected();

		if (newSelection.isPresent()) {
			newSelection.get().toggleSelected();
			selectedTarget = newSelection;
		} else {
			selectedTarget = Optional.empty();
			canvasGroup.requestFocus();
		}
	}
}