/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Copyright (C) 2015 phrack
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

package com.shootoff.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.shootoff.camera.Shot;
import com.shootoff.camera.cameratypes.PS3EyeCamera;
import com.shootoff.camera.cameratypes.PS3EyeCamera.eyecam;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;

import javafx.animation.KeyFrame;
//import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class HitMe3 extends ProjectorTrainingExerciseBase implements TrainingExercise {
	private static Color myColor = new Color(0.0, 0.0, 0.0, 0.0);
	private static double fontSize = 45.0;
	private boolean troubleshootingReset = false;
	final int MAX_TARGETS = 20;
	final int DEFAULT_TARGET_COUNT = 0;//4 - 1;
	final int DEFAULT_TIME_BETWEEN_TARGET_MOVEMENT = 10;//1 - 1;
	final int DEFAULT_MAX_ROUNDS = 2;
	final String DEFAULT_TARGET_STRING = "ISSF";
	final String DEFAULT_SCALE = "one half";
	private int max_targets_reset = MAX_TARGETS;
	private int default_target_count_reset = DEFAULT_TARGET_COUNT;
	private int default_time_between_target_movement_reset = DEFAULT_TIME_BETWEEN_TARGET_MOVEMENT;
	private int default_max_rounds_reset = DEFAULT_MAX_ROUNDS;
	private String addTargetString_reset = DEFAULT_TARGET_STRING;
	private String theScale_reset = DEFAULT_SCALE;
	private final static String POINTS_COL_NAME = "Score";
	private final static int POINTS_COL_WIDTH = 60;
	private boolean fromReset = false;
	private String addTargetString = DEFAULT_TARGET_STRING;//"nothing here";
	private String theScale = DEFAULT_SCALE;//"nothing here";
	private int shootCount = 1;
	private int roundCount = 5;
	private int timeBetweenTargetMovement = 2;
	private int misses = 0;
	private int hits = 0;
	private double newScale = 0;
	private int decRoundCount = 0;
	private static int edgeProtection = 250;
	public double targetX = 0;
	public double targetY = 0;
	public double consolidatedX = 0;
	public double consolidatedY = 0;

	public ArrayList<Point2D> consolidatedList = new ArrayList<Point2D> ();
	private double shotX = 0;
	private double shotY = 0;

	private long beepTime = 0;
	private long finishTime = 0;

	private static final List<HitMe3Target> shootTargets = new ArrayList<HitMe3Target>();
	private static final List<HitMe3Target> dontShootTargets = new ArrayList<HitMe3Target>();

	private static ProjectorTrainingExerciseBase thisSuper;
	private Timeline targetAnimation;
	private int score = 0;

	private boolean testing = false;

	private static VBox myVBox = new VBox();
	private static Label myLabel = new Label("hello");

	public HitMe3() {}

	public HitMe3(List<Target> targets) {
		super(targets);
		setThisSuper(super.getInstance());
	}

	// For testing
	protected void init(int shootCount, int timeBetweenTargetMovement, int maxVelocity) {

		testing = true;
		this.shootCount = shootCount;
		this.timeBetweenTargetMovement = timeBetweenTargetMovement;
		shootTargets.clear();
		startExercise();

	}

	private static void setThisSuper(ProjectorTrainingExerciseBase thisSuper) {
		HitMe3.thisSuper = thisSuper;
	}

	private void initColumn(){
		if (!fromReset){
		super.addShotTimerColumn(POINTS_COL_NAME, POINTS_COL_WIDTH);
		}
	}

	@Override
	public void init() {
		eyecam myEyecam = (eyecam) PS3EyeCamera.getEyecamLib();
		if (myEyecam.ps3eye_set_parameter(com.shootoff.camera.cameratypes.PS3EyeCamera.ps3ID, eyecam.ps3eye_parameter.PS3EYE_AUTO_GAIN, 0) == -1 ){
			logger.debug("did not set autogain to off in TossUp");
		}
		myEyecam = null;
		initColumn();
		collectSettings();
		startExercise();
	}//end init

	static Timeline soundAnimation;
	private void startExercise()  {
		if(inCollectSettings)return;
		score = 0;
		decRoundCount = roundCount;
		//super.showTextOnFeed(String.format("Score: %f  Misses: %d Hits: %d %nTotal Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ));

		String path = "targets/";
		String fileType = ".target";

		//myLabel = thisSuper.arenaController.getCanvasManager().addProjectorMessage(String.format("Score: %d", score), Color.YELLOW); //getCanvasGroup().getChildren().set(1,myLabel);

		//thisSuper.arenaController.getCanvasManager().hideProjectorMessage(myLabel);

		//myVBox.getChildren().add(myLabel);

		//thisSuper.arenaController.getCanvasManager().getCanvasGroup().getChildren().add(myVBox);
		//thisSuper.arenaController.getCanvasManager().getCanvasGroup().getChildren().add(myLabel);

		if (troubleshootingReset){
			Optional<Target> newTarget1 = super.addTarget(new File("targets/Reset.target"), 0, 0);
			newTarget1.get().setPosition(300, 300);
			dontShootTargets.add(new HitMe3Target(newTarget1.get()));
		}

		targetAnimation = new Timeline(new KeyFrame(Duration.millis(timeBetweenTargetMovement * 1000), e -> updateTargets()));
		targetAnimation.setCycleCount(roundCount);

		//playSound("sounds/voice/shootoff-makeready.wav");
		//Media media = new Media("sounds/voice/shootoff-makeready.wav");
		//final MediaPlayer mediaPlayer1 = new MediaPlayer(media);
		soundAnimation = new Timeline(
		    new KeyFrame(Duration.ZERO, new EventHandler<ActionEvent>() {

		        @Override
		        public void handle(ActionEvent t) {

		            playSound("sounds/voice/shootoff-3.wav");
		        }
		    }),
		    new KeyFrame(Duration.seconds(1.2), new EventHandler<ActionEvent>() {

		        @Override
		        public void handle(ActionEvent t) {

		        	 playSound("sounds/voice/shootoff-2.wav");
		        }
		    }),
		    new KeyFrame(Duration.seconds(2.4), new EventHandler<ActionEvent>() {

		        @Override
		        public void handle(ActionEvent t) {

		        	playSound("sounds/voice/shootoff-1.wav");
		        }
		    }),
		    new KeyFrame(Duration.seconds(3.6), new EventHandler<ActionEvent>() {

		        @Override
		        public void handle(ActionEvent t) {

		        	addTargets(shootTargets, path+addTargetString+fileType, shootCount);
		        	playSound("sounds/beep.wav");
		    		beepTime = System.currentTimeMillis();
		    		targetAnimation.play();
		    		pauseShotDetection(false);
		        }
		    })
		);
		soundAnimation.play();
//		try {
//			Thread.sleep((long) 3500.);
//		} catch (InterruptedException e1) {
//			e1.printStackTrace();
//		}

	}//end start exercise


	boolean inCollectSettings = true;

	private void collectSettings() {
		super.pauseShotDetection(true);

		//final Stage HitMe3TargetsStage = new Stage();
		final GridPane HitMe3TargetsPane = new GridPane();
		final ColumnConstraints cc = new ColumnConstraints(200);
		cc.setHalignment(HPos.CENTER);
		HitMe3TargetsPane.setHgap(10);
		final ObservableList<String> targetList = FXCollections.observableArrayList();
		final ComboBox<String> targetListComboBox = new ComboBox<String>(targetList);
		final ObservableList<String> targetCounts = FXCollections.observableArrayList();
		final ObservableList<String> targetCounts2 = FXCollections.observableArrayList();
		//final ComboBox<String> shootTargetsComboBox = new ComboBox<String>(targetCounts);
		final ComboBox<String> targetTimeComboBox = new ComboBox<String>(targetCounts);//ok to use between 1 and 10
		final ObservableList<String> maxScale = FXCollections.observableArrayList();
		final ComboBox<String> maxScaleComboBox = new ComboBox<String>(maxScale);
		final ComboBox<String> numberOfRoundsComboBox = new ComboBox<String>(targetCounts2);//ok to use between 1 and 10
		//final Scene scene = new Scene(HitMe3TargetsPane);
		//final Button okButton = new Button("OK");


		//if(true){

			//cc.setHalignment(HPos.LEFT);
			//HitMe3TargetsPane.getColumnConstraints().addAll(new ColumnConstraints(), cc);


//			shootTargetsComboBox.getSelectionModel().select(default_target_count_reset);
//			HitMe3TargetsPane.add(new Label("Shoot Targets:"), 0, 0);
//			HitMe3TargetsPane.add(shootTargetsComboBox, 1, 0);
//			//for future use
//			shootTargetsComboBox.setVisible(false);



			HitMe3TargetsPane.add(new Label("Target Face Time:"), 0, 0);
			HitMe3TargetsPane.add(targetTimeComboBox, 1, 0);

			targetList.add("ISSF");
			targetList.add("IPSC");
			targetList.add("SimpleBullseye_score");


			HitMe3TargetsPane.add(new Label("Select Target Type:"), 3, 0);
			HitMe3TargetsPane.add(targetListComboBox, 4, 0);

			for (int i = 1; i <= 10; i++)
				targetCounts.add(Integer.toString(i));

			for (int i = 1; i <= 10; i++)
				targetCounts2.add(Integer.toString(i));


			maxScale.add("one quarter");
			maxScale.add("one half");
			maxScale.add("original");
			maxScale.add("double");



			HitMe3TargetsPane.add(new Label("Target Scale:"), 6, 0);
			HitMe3TargetsPane.add(maxScaleComboBox, 7, 0);



			HitMe3TargetsPane.add(new Label("Target Rounds:"), 9, 0);
			HitMe3TargetsPane.add(numberOfRoundsComboBox, 10, 0);


			//okButton.setDefaultButton(true);
			//HitMe3TargetsPane.add(okButton, 1, 5);

		//}


		//okButton.setOnAction((e) -> {
			maxScaleComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				theScale = newValue;
				theScale_reset = theScale;
				stopExercise();
				startExercise();
		    });
			//theScale = maxScaleComboBox.getSelectionModel().getSelectedItem();

			targetListComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				addTargetString = newValue;
				addTargetString_reset = addTargetString;
				stopExercise();
				startExercise();
		    });
			//addTargetString =  targetListComboBox.getSelectionModel().getSelectedItem();

//			shootTargetsComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
//				shootCount = Integer.parseInt(newValue);
//				stopExercise();
//				startExercise();
//		    });
			//shootCount = Integer.parseInt(shootTargetsComboBox.getSelectionModel().getSelectedItem());

			targetTimeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				timeBetweenTargetMovement = Integer.parseInt(newValue);
				default_time_between_target_movement_reset = timeBetweenTargetMovement -1;
				stopExercise();
				startExercise();
		    });
			//timeBetweenTargetMovement = Integer.parseInt(targetTimeComboBox.getSelectionModel().getSelectedItem());


			numberOfRoundsComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
				roundCount = Integer.parseInt(newValue);
				default_max_rounds_reset = roundCount -1;
				decRoundCount = roundCount;
				stopExercise();
				startExercise();
		    });
			//roundCount = Integer.parseInt(numberOfRoundsComboBox.getSelectionModel().getSelectedItem());

			//HitMe3TargetsStage.close();
		//});//end OKButton


		//HitMe3TargetsStage.initOwner(null);//(super.getShootOFFStage());
		//HitMe3TargetsStage.initModality(Modality.WINDOW_MODAL);
		//HitMe3TargetsStage.setTitle("HitMe3 Target Settings");
		//HitMe3TargetsStage.setScene(scene);
		//HitMe3TargetsStage.showAndWait();

			targetTimeComboBox.getSelectionModel().select( 5);//default_time_between_target_movement_reset);
			targetListComboBox.getSelectionModel().select(addTargetString_reset);
			maxScaleComboBox.getSelectionModel().select(theScale_reset);
			numberOfRoundsComboBox.getSelectionModel().select(default_max_rounds_reset);

		addTargetString_reset = addTargetString;
		default_target_count_reset = shootCount -1;
		default_time_between_target_movement_reset = timeBetweenTargetMovement -1;
		theScale_reset = theScale;
		default_max_rounds_reset = roundCount -1;
		decRoundCount = roundCount;
		score = 0;

		super.addExercisePane(HitMe3TargetsPane);

		inCollectSettings = false;

	}//end collectSettings

	protected List<HitMe3Target> getShootTargets() {
		return shootTargets;
	}

	//kept in so I did not break TestBouncingTargets...
	protected List<HitMe3Target> getDontShootTargets() {
		return dontShootTargets;
	}

	private void updateTargets() {

		myLabel.setText(String.format("Score: %d", ++score));

		decRoundCount--;

		//for testing
		//super.showTextOnFeed(String.format("targetX: %f  targetY: %f  shotX: %f  shotY: %f", targetX,targetY,shotX,shotY));

		//stop the shooting and present user with consolidated target
		if(decRoundCount <= 0) {
			finishTime = System.currentTimeMillis();

			targetAnimation.stop();
			super.pauseShotDetection(true);
			for (HitMe3Target b : shootTargets){
				b.getTarget().setPosition(400, 350);//somewhere in the middle

			}

			//super.showTextOnFeed(String.format("Score: %f  Misses: %d Hits: %d %n Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ));


			getProjArenaController().getCanvasManager().setShowShots(true);

			//make the shots go away for a cleaner picture
			for (Shot shot : thisSuper.getProjArenaController().getCanvasManager().getShots()) {
				shot.getMarker().setVisible(false);
			}



			for (Point2D p2d : consolidatedList){
				Shot cShot = new Shot(Color.YELLOW, p2d.getX()+400, p2d.getY()+350, thisSuper.getCamerasSupervisor().getCameraManager(0).getFrameCount(),3);
				this.getProjArenaController().getCanvasManager().addArenaShot(cShot, null, false);
			}

			//thisSuper.arenaController.getCanvasManager().removeProjectorMessage(myLabel);
			//myLabel.setText(String.format("Score: %d Misses %d", score, misses));
			//myLabel.setText(String.format("Score: %f Misses: %d Hits: %d %n Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ));


			thisSuper.showTextOnFeed(String.format("Score: %f Misses: %d Hits: %d %n Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ), 50, (int) super.getArenaHeight() - 200, myColor,
					Color.YELLOW, new Font("TimesRoman", fontSize));


			//thisSuper.getProjArenaController().getCanvasManager().showProjectorMessage(myLabel);
			//myLabel = thisSuper.arenaController.getCanvasManager().addProjectorMessage(String.format("Score: %d  ",score),Color.YELLOW);//Misses: %d Hits: %d Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ), Color.YELLOW); //getCanvasGroup().getChildren().set(1,myLabel);

			if (troubleshootingReset){
				//to check for a reset hit
				super.pauseShotDetection(false);
			}

			return;
		}//end if

		for (HitMe3Target b : shootTargets){
			b.moveTarget();

			targetX= b.getTarget().getPosition().getX();
			targetY= b.getTarget().getPosition().getY();
		}

		if (troubleshootingReset) {
			for (HitMe3Target b : dontShootTargets){
				//b.moveTarget();
				b.getTarget().setPosition(300, 300);
			}//end for

		}//end if

	}//end updateTargets

	protected static class HitMe3Target {
		private final Target target;

		public HitMe3Target(Target target) {
			this.target = target;
		}

		public Target getTarget() {
			return target;
		}

		//called by update
		public void moveTarget() {

			int maxX = (int) (thisSuper.getArenaWidth() - target.getDimension().getWidth() - edgeProtection);
			int x = new Random().nextInt(maxX + 1) + 1;

			int maxY = (int) (thisSuper.getArenaHeight() - target.getDimension().getHeight() - edgeProtection);
			int y = new Random().nextInt(maxY + 1) + 1;

			target.setPosition(x, y);

		}//end moveTarget

	}//end class HitMe3Targets

	private void addTargets(List<HitMe3Target> targets, String target, int count) {
		for (int i = 0; i < count; i++) {
			Optional<Target> newTarget = super.addTarget(new File(target), 0, 0);

			if (newTarget.isPresent()) {

				//scale the target
				if(theScale == "one half")newScale = 0.5;
				if(theScale == "one quarter")newScale = 0.25;
				if(theScale == "original")newScale = 1.0;
				if(theScale == "double")newScale = 2.0;
				newTarget.get().setDimensions(newTarget.get().getDimension().getWidth() * newScale, newTarget.get().getDimension().getHeight() * newScale);

				// Randomly place the target
				int maxX = (int) (super.getArenaWidth() - newTarget.get().getDimension().getWidth() - edgeProtection);
				int x = new Random().nextInt(maxX + 1) + 1;
				int maxY = (int) (super.getArenaHeight() - newTarget.get().getDimension().getHeight() - edgeProtection);
				int y = new Random().nextInt(maxY + 1) + 1;
				newTarget.get().setPosition(x, y);
				targetX = x;
				targetY = y;

				targets.add(new HitMe3Target(newTarget.get()));
			}//end if
		}//end for



	}//end function

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("HitMe3", "1.0", "ifly53e",
				"This exercise randomly moves a target at a user specified interval around the projector arena."
					+ "Hit the target as quickly as possible to move to the next randomly placed target."
					+ "The target will disappear after the target face time expires."
					+ "The rounds are the number of cycles in which targets will be presented."
					+ "You can scale the target to be bigger or smaller "
					//+ "You can specify how many targets will be presented during each round." not yet...
					+ "Scoring:  Hit targets are scored according to their point scoring region."
					+ "A target that is not hit is minus 10 points.  All misses are minus five points."
					+ "A time bonus is added to your score based on how quickly all targets are hit.");
		//TODO:
		//add a screen count down to start...currently stopping thread for a few seconds
		//figure out how to reset and start over the exercise from hitting a reset target
		//provide multiple targets to hit before the end of a cycle or round (instead of hitting one target, hit a user specified amount)
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {

		if(decRoundCount <=0){
			if (troubleshootingReset) {
				if (hit.isPresent()) {
					if (hit.get().getHitRegion().tagExists("points")) {

						int checkValue = Integer.parseInt(hit.get().getHitRegion().getTag("points"));

						//modified Reset.target to be points with a value of 1000 because regular reset target would not call
						//my reset function even though reset appears to be an override...
						/*<?xml version="1.0" encoding="UTF-8"?>
						<target>
							<ellipse centerX="41.500000" centerY="300.500000" radiusX="25.000000" radiusY="25.000000" fill="blue">
								<tag name="points" value="1000" />
							</ellipse>
						</target>
					*/

						if (checkValue == 1000){
							super.showTextOnFeed(String.format("reset hit"));


							//com.shootoff.gui.controller.ShootOFFController mySOC = new com.shootoff.gui.controller.ShootOFFController() ;
							//mySOC = thisSuper.getShootOFFController();
							//mySOC.resetShotsAndTargets();

							//TrainingExerciseBase myTEB = new TrainingExerciseBase();
							//myTEB.getInstance().reset();

							//ProjectorTrainingExerciseBase myPTE = new ProjectorTrainingExerciseBase();
							//myPTE.getInstance().reset();
							thisSuper.getCamerasSupervisor().reset();

							//super.showTextOnFeed(String.format("2reset hit2"));

							reset();
							return;

						}
					}
				}
			}//end if troubleshootingReset
		}//end if decRoundCount

		shotX = shot.getX();
		shotY = shot.getY();

		if (hit.isPresent()) {
			if (hit.get().getHitRegion().tagExists("points")) {

				super.setShotTimerColumnText(POINTS_COL_NAME, hit.get().getHitRegion().getTag("points"));

				if (troubleshootingReset){
					int checkValue = Integer.parseInt(hit.get().getHitRegion().getTag("points"));

					if (checkValue == 1000){

						return;

					}
				}

				if (shot.getColor().equals(Color.RED)) {
					hits++;
					score += Integer.parseInt(hit.get().getHitRegion().getTag("points"));
					//super.showTextOnFeed(String.format("Score: %f  Misses: %d Hits: %d Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ));

					myLabel.setText(String.format("Score: %d",score));
					consolidatedX = Math.abs(targetX-shotX);
					consolidatedY = Math.abs(targetY-shotY);

					consolidatedList.add( new Point2D (consolidatedX,consolidatedY) );

					//after it is hit, go to the next target
					targetAnimation.setCycleCount(targetAnimation.getCycleCount() - 1);
					targetAnimation.stop();
					targetAnimation.play();
					updateTargets();
					return;
				}//end if
			}//end if tagexists points

		}//end if hitRegion.isPresent
		else{
			//its a miss
			if (shot.getColor().equals(Color.RED)) {
				score = score - 5;
				misses++;
				//super.showTextOnFeed(String.format("Score: %f  Misses: %d Hits: %d Total Time: %f  Time Bonus %f", score+( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0 )- (roundCount-hits)*10,misses,hits,(finishTime-beepTime)/1000.0,( (roundCount*timeBetweenTargetMovement)-(finishTime-beepTime)/1000.0) ));
			}//end if

		}//end else
	}//end function



	public void stopExercise() {

		if(targetAnimation == null)return;
		targetAnimation.stop();

		if(soundAnimation == null)return;
		soundAnimation.stop();

		for (HitMe3Target b : shootTargets)
			super.removeTarget(b.getTarget());
		shootTargets.clear();

		consolidatedList.clear();

		//shots.clear wont work for the projector arena so do it manually

		if (troubleshootingReset){
			List<Shot> myShotList = new ArrayList<Shot>();
			myShotList = thisSuper.getProjArenaController().getCanvasManager().getShots();

			if(myShotList.isEmpty() )
			{
				//empty
			}
			else
			{
				//not empty
			}

			int size = myShotList.size();

			if (myShotList.size()>0)
			{

				for (Shot shot : myShotList ){//thisSuper.arenaController.getCanvasManager().getShots() ) {
					//if (!thisSuper.arenaController.getCanvasManager().getShots().isEmpty() )
					if(thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().contains(shot.getMarker()) ){
						thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().remove(shot.getMarker());
					}//endif

				}//end for
			}
		}//end if

		if(!troubleshootingReset){
			for (Shot shot : thisSuper.getProjArenaController().getCanvasManager().getShots() ) {
				//if (!thisSuper.arenaController.getCanvasManager().getShots().isEmpty() )
				if(thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().contains(shot.getMarker()) ){
					thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().remove(shot.getMarker());
				}//endif

			}//end for
		}//end iff

		this.getProjArenaController().getCanvasManager().getShots().clear();
		//thisSuper.getProjArenaController().getCanvasManager().removeProjectorMessage(myLabel);
		//myLabel = thisSuper.arenaController.getCanvasManager().addProjectorMessage(String.format("Score: %d", score), Color.YELLOW); //getCanvasGroup().getChildren().set(1,myLabel);

		thisSuper.showTextOnFeed("");

		fromReset = true;
		getProjArenaController().getCanvasManager().setShowShots(false);
		hits = 0;
		misses = 0;

		//init();

	}//end reset



	@Override
	public void reset(List<Target> targets) {
		score = 0;
		stopExercise();
		startExercise();
	}

	@Override
	public void targetUpdate(Target target, TargetChange change) {
		// TODO Auto-generated method stub

	}

	@Override
	public void destroy(){
		stopExercise();
		super.destroy();
	}



}//end public class HitMe3

