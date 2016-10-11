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
 *
 * Add a public method called setCourse to ProjectorTrainingExerciseBase that accepts a File parameter (the course to load): public List<Target> setCourse(File courseFile).
Call Course newCourse = CourseIO.loadCourse(arenaController, courseFile) to get the course.
Call arenaController.setCourse(newCourse) to set the new course (this will clear all existing targets).
Return the list of targets from newCourse.getTargets(). The exercise would run List<Target> targets = super.setCourse(...) to set the course and learn all the targets at the same time.
 */

package com.shootoff.plugins;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.shootoff.camera.Shot;
import com.shootoff.camera.cameratypes.PS3EyeCamera;
import com.shootoff.camera.cameratypes.PS3EyeCamera.eyecam;
import com.shootoff.courses.Course;
//import com.shootoff.plugins.FrontSight_v1.delayForShooterReset;
//import com.shootoff.plugins.FrontSight_v1.presentTarget;
//import com.shootoff.plugins.FrontSight_v1.turnAwayTarget;
//import com.shootoff.plugins.FrontSight_v1.stopShotDetection;
import com.shootoff.targets.Hit;
import com.shootoff.targets.ImageRegion;
import com.shootoff.targets.RegionType;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.util.NamedThreadFactory;
import com.shootoff.gui.LocatedImage;
import com.shootoff.gui.controller.ShootOFFController;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class FrontSight_v3 extends ProjectorTrainingExerciseBase implements TrainingExercise {
	//private final static String LENGTH_COL_NAME = "Length";
	//private final static int LENGTH_COL_WIDTH = 60;
	//private final static String HIT_COL_NAME = "Hit";
	//private final static int HIT_COL_WIDTH = 60;
	//private final static int START_DELAY = 4; // s
	//private final static int PAUSE_DELAY = 1; // s
	private static final int CORE_POOL_SIZE = 2;
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
			new NamedThreadFactory("FrontSight_v3Exercise"));
	private TrainingExerciseBase thisSuper;
	private List<Target> targets;
	//private Set<Target> roundTargets;
	//private long startTime = 0;
	private boolean repeatExercise = true;
	private boolean testing = false;
	private ScheduledFuture mySF;

	static long startTimeR = 0;
	static long stopTimeR = 0;
	static long startTimeP = 0;
	static long stopTimeP = 0;
	static long startTimeB = 0;
	static long stopTimeB = 0;
	static double rotationTimeForTarget = 1.05;//1 is .25 seconds; 0.75 is 335; 0.5 is .5 seconds
	static long presentationTimeforTargetInMilli = (long) 1400; // 1200 fastest target turn time in test
	static long timeBetweenRoundsInMilli = (long) 3800;//3800 syncs up with line is set wav file whistle
	static long timeDelayBeforeShotDetectionTurnedOffInMilli = (long) 100;
	static long timeDelayBeforeLineIsSetCallInMilli = (long) 3000;
	static long startShotTimer = 0;
	static long stopShotTimer = 0;
	static int yardline = 7;
	static int numberOfReps = 0;
	static int repCounter = 0;
	public Map<String, Point2D> hitMap = new HashMap<String, Point2D>();
	static boolean stopFlag = false;
	static boolean animatedAlready = false;
	static long shotTime = 0;
	boolean courseToggle = true;
	boolean calibration = false;
	private Button showShotsButton;
	static private int roundCounter = 1;
	boolean manualMode = false;

	public FrontSight_v3() {}

	public FrontSight_v3(List<Target> targets) {
		super(targets);

		thisSuper = super.getInstance();
		this.targets = targets;
		//thisSuper.showTextOnFeed(String.format("in constructor  %d", targets.size()) );

	}

	@Override
	public void init() {
		super.pauseShotDetection(true);

		config.setUseHitMod(false);
		//config.setDelayValue((float) 75);
		//config.setHitWindowX((float) 5);
		//config.setHitWindowY((float) 8);
	
		
		eyecam myEyecam = (eyecam) PS3EyeCamera.getEyecamLib();
		if (myEyecam.ps3eye_set_parameter(com.shootoff.camera.cameratypes.PS3EyeCamera.ps3ID, eyecam.ps3eye_parameter.PS3EYE_AUTO_GAIN, 0) == -1 ){
			logger.debug("did not set autogain to off in Frontsight");
		}
		myEyecam = null;

		initUI();
		//startRound();
	}

	// For testing
	public void init(final Course course) {
		testing = true;
		thisSuper = super.getInstance();

		targets = new ArrayList<Target>();
		targets.addAll(course.getTargets());

		//if (checkTargets(targets)) startRound();
		//startRound();
	}

	Boolean schoolDrills = false;
	Boolean headShots_7 = false;
	Boolean headShots_5 = false;
	Boolean ambiDrill = false;
	Boolean weaponPres = false;
	Boolean responses_7 = false;
	Boolean multiples = false;


	protected void initUI() {
		addShotTimerColumn("ShotTime", 200);
		addShotTimerColumn("Subtarget",400);
		addShotTimerColumn("Points", 100);
		addShotTimerColumn("Number", 100);

		this.showShotsButton = addShootOFFButton("FrontSight\nTest Times", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			executorService.shutdownNow();
			//File myFile = new File("C:\\Users\\Tim\\Documents\\GitHub\\ShootOFF\\targets\\frontsightHandgunSkillsTestTimes_Page_1.jpg");

			Image page1 = new Image("file:c:\\Users\\Tim\\Documents\\GitHub\\ShootOFF\\targets\\frontsightHandgunSkillsTestTimes_Page_1.jpg");

			Image page2 = new Image("file:c:\\Users\\Tim\\Documents\\GitHub\\ShootOFF\\targets\\frontsightHandgunSkillsTestTimes_Page_2.jpg");
			Image page3 = new Image("file:c:\\Users\\Tim\\Documents\\GitHub\\ShootOFF\\targets\\frontsightHandgunSkillsTestTimes_Page_3.jpg");

			ImageView iv1 = new ImageView();
			iv1.setImage(page1);
			ImageView iv2 = new ImageView();
			iv2.setImage(page2);
			ImageView iv3 = new ImageView();
			iv3.setImage(page3);

			Group myRoot1 = new Group();
			Scene myScene1 = new Scene(myRoot1);
			Stage myStage1 = new Stage();
			HBox myHbox1 = new HBox();
			myHbox1.getChildren().add(iv1);
			myRoot1.getChildren().add(myHbox1);
			myStage1.setScene(myScene1);
			myStage1.show();

			Group myRoot2 = new Group();
			Scene myScene2 = new Scene(myRoot2);
			Stage myStage2 = new Stage();
			HBox myHbox2 = new HBox();
			myHbox2.getChildren().add(iv2);
			myRoot2.getChildren().add(myHbox2);
			myStage2.setScene(myScene2);
			myStage2.show();

			Group myRoot3 = new Group();
			Scene myScene3 = new Scene(myRoot3);
			Stage myStage3 = new Stage();
			HBox myHbox3 = new HBox();
			myHbox3.getChildren().add(iv3);
			myRoot3.getChildren().add(myHbox3);
			myStage3.setScene(myScene3);
			myStage3.show();



//			this.targets.clear();
//			this.targets = super.setCourse(new File("courses/AllTargetsEdgeOfDesk_v4.course"));
//			manualMode = true;
//			calibration = true;
			//setupRound();
			//startRound();
		});
		this.showShotsButton = addShootOFFButton("Target\nCalibration", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			executorService.shutdownNow();
			this.targets.clear();
			this.targets = super.setCourse(new File("courses/AllTargetsEdgeOfDesk_v4.course"));
			manualMode = true;
			calibration = true;
			//setupRound();
			//startRound();
		});
		this.showShotsButton = addShootOFFButton("Show\nShots", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			//showShots();
			//numberOfReps = 1;
			//repCounter = 1;
			executorService.shutdownNow();
			//stopFlag = true;
			showShots();
		});
		this.showShotsButton = addShootOFFButton("7yd Line\nHostage", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			SevenYdLineHostage();
		});
		this.showShotsButton = addShootOFFButton("School\nDrills", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = false;

			schoolDrills = true;
			ambiDrill = false;
			weaponPres = false;
			responses_7 = false;
			headShots_5 = false;
			headShots_7 = false;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("5yd\nHead Shots", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = false;
			weaponPres = false;
			responses_7 = false;
			headShots_5 = true;
			headShots_7 = false;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("7yd\nHead Shots", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = false;
			weaponPres = false;
			responses_7 = false;
			headShots_5 = false;
			headShots_7 = true;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("Ambi\nDrill", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = true;
			weaponPres = false;
			responses_7 = false;
			headShots_5 = false;
			headShots_7 = false;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("5yd\nMultiples", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = false;
			weaponPres = false;
			responses_7 = false;
			headShots_5 = false;
			headShots_7 = false;
			multiples = true;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("Weapon\nPresentations", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = false;
			weaponPres = true;
			responses_7 = false;
			headShots_5 = false;
			headShots_7 = false;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});

		this.showShotsButton = addShootOFFButton("7yd\nResponses", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = false;
			noPreview = true;

			schoolDrills = false;
			ambiDrill = false;
			weaponPres = false;
			responses_7 = true;
			headShots_5 = false;
			headShots_7 = false;
			multiples = false;
			//reset(targets);
			roundCounter = 1;
			setupRound();
			startRound();

		});
		this.showShotsButton = addShootOFFButton("3yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			ThreeYdLine();

		});
		this.showShotsButton = addShootOFFButton("5yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			FiveYdLine();
		});
		this.showShotsButton = addShootOFFButton("7yd\nLineSD", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			SevenYdLineSD();
		});

		this.showShotsButton = addShootOFFButton("10yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			TenYdLine();

		});
		this.showShotsButton = addShootOFFButton("15yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			FifteenYdLine();

		});
		this.showShotsButton = addShootOFFButton("25yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			TwentyFiveYdLine();

		});
		this.showShotsButton = addShootOFFButton("50yd\nLine", (event) -> {
			Button showShotsButton = (Button) event.getSource();
			manualMode = true;
			FiftyYdLine();

		});


	}

	public void setupRound () {
		super.pauseShotDetection(true);

		repeatExercise = false;
		executorService.shutdownNow();

		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
				new NamedThreadFactory("FrontSight_v3Exercise"));
		getProjArenaController().getCanvasManager().setShowShots(false);
	}

//	String messageForTitle = "noTitleSet";
//	private void setRoundTitle(){
//		super.setCourse(new File("courses/empty1600x1200.course"));
//		showTextOnFeed(messageForTitle, (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
		//super.exerciseLabel.setText(messageForTitle);
//		Text text1 = new Text(messageForTitle);
//		text1.setFont(new Font("TimesRoman", 120.0));
//		text1.setFill(Color.YELLOW);
//
//		textFlow.setLayoutX(super.getArenaWidth()/4);
//		textFlow.getChildren().add(text1);
//		super.arenaController.getCanvasManager().addChild(textFlow);

//	}//end setRoundTitle

	private void setRound(){
		if(schoolDrills){
			SchoolDrills();
		}//end if
		else {
			if(headShots_7){
				SevenYdLineHeadShots();
			}//end if
			else {
				if(ambiDrill){
					AmbiDrill();
				}//end if
				else {
					if(weaponPres){
						WeaponPres();
					}//end if
					else {
						if(responses_7){
							Responses_7();
						}//end if
						else {
							if(headShots_5){
								FiveYdLineHeadShots_script();
							}//end if
							else {
								if(multiples){
									Multiples();
								}//end if
								else{
									doThePause = false;
									goodbye.cancel();
								}//end else
							}//end else
						}//end else
					}//end else
				}//end else
			}//end else
		}//end else


	}
	private void Multiples() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("5 Yard Line \n2 Targets \n1 Shot to each TC in 1.5 Seconds\n1 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiveYdLine2Targets();
				roundCounter++;
				break;
			}
			case(2):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("5 Yard Line \n3 Targets \n1 Shot to each TC in 1.8 Seconds\n1 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiveYdLine3Targets();
				roundCounter++;
				break;
			}
			case(3):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("5 Yard Line \n4 Targets \n1 Shot to each TC in 2.1 Seconds\n1 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiveYdLine4Targets();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}
	}
	private void FiveYdLineHeadShots_script() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("5 Yard Line \nHead Shots \n1 Shot to Head in 1.3 Seconds\n4 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiveYdLineHeadShots();
				roundCounter++;
				break;
			}
			case(2):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				FiveYdLineHeadShots();
				roundCounter++;
				break;
			}
			case(3):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				FiveYdLineHeadShots();
				roundCounter++;
				break;
			}
			case(4):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				FiveYdLineHeadShots();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}
	}
	private void Responses_7() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nSupport Side Response \n1 Shot to TC in 1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(2):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(3):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(4):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(5):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(6):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nFiring Side Response \n1.3 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSDFiringSide();
				roundCounter++;
				break;
			}
			case(7):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSupport Side Response \n1 Shot to TC in 1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDFiringSide();
				roundCounter++;
				break;
			}
			case(8):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDFiringSide();
				roundCounter++;
				break;
			}
			case(9):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDFiringSide();
				roundCounter++;
				break;
			}
			case(10):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDFiringSide();
				roundCounter++;
				break;
			}
			case(11):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nRear Response \n1.4 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSDRear();
				roundCounter++;
				break;
			}
			case(12):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSupport Side Response \n1 Shot to TC in 1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDRear();
				roundCounter++;
				break;
			}
			case(13):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDRear();
				roundCounter++;
				break;
			}
			case(14):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDRear();
				roundCounter++;
				break;
			}
			case(15):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDRear();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}
	}
	private void WeaponPres() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nWeapons Presentation \n1 Shot to TC in 1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(2):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(3):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(4):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			case(5):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nWeapons Presentation \n1.2 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSDPresentation();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}
	}
	private void AmbiDrill() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nAmbidexterous Drill \n6 Seconds\n1 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				AmbiDrillSevenYdLineSD();
				roundCounter++;
				break;
			}

			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}
	}
	private void SchoolDrills() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("3 Yard Line \nTwo Shots \nThoracic Cavity\n1.3 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));

				//messageForTitle = "3 Yard Line \nTwo Shots \nThoracic Cavity\n1.3 Seconds\n1 Rep";
				//setRoundTitle();
				//Thread.sleep(5000);
//				final Task<Boolean> showTitle = setTheRoundTitle();
//				showTitle.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
//						@Override
//						public void handle(WorkerStateEvent event){
//							showTextOnFeed("showTitle succeeded",false);
//
//							ThreeYdLine();
//							roundCounter++;
//						}
//
//				});
				//showTextOnFeed("3 Yard Line \nTwo Shots \nThoracic Cavity\n1.3 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));

				doThePause = true;
				ThreeYdLine();
				roundCounter++;
				//showTitle.run();
//				try {
//					while(!showTitle.get()){
//
//					}
//				} catch (InterruptedException e) {
//
//					e.printStackTrace();
//				} catch (ExecutionException e) {
//
//					e.printStackTrace();
//				}
				break;
			}
			case(2):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("5 Yard Line \nTwo Shots \nThoracic Cavity\n1.5 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiveYdLine();
				roundCounter++;
				break;
			}
			case(3):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nTwo Shots \nThoracic Cavity\n1.5 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(4):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("10 Yard Line \nTwo Shots \nThoracic Cavity\n1.8 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				TenYdLine();
				roundCounter++;
				break;
			}
			case(5):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("15 Yard Line \nTwo Shots \nThoracic Cavity\n2.1 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FifteenYdLine();
				roundCounter++;
				break;
			}
			case(6):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("25 Yard Line \nTwo Shots \nThoracic Cavity\n2.7 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				TwentyFiveYdLine();
				roundCounter++;
				break;
			}
			case(7):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("50 Yard Line \nTwo Shots \nThoracic Cavity\n6 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				FiftyYdLine();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}//end switch


	}//end setround
	private void SevenYdLineHeadShots() {
		switch(roundCounter){
			case(1):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line \nSingle Head Shot \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(2):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("5 Yard Line \nTwo Shots \nThoracic Cavity\n1.5 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(3):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nTwo Shots \nThoracic Cavity\n1.5 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(4):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("10 Yard Line \nTwo Shots \nThoracic Cavity\n1.8 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(5):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("15 Yard Line \nTwo Shots \nThoracic Cavity\n2.1 Seconds\n1 Rep", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineSD();
				roundCounter++;
				break;
			}
			case(6):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line Hostage \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(7):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(8):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(9):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(10):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(11):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line Hostage \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(12):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(13):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(14):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(15):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(16):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line Odd Angle \nSingle Head Shot \nLeft Side \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(17):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(18):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(19):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(20):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nLeft Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(21):{
				super.setCourse(new File("courses/empty1600x1200.course"));
				showTextOnFeed("7 Yard Line Odd Angle \nSingle Head Shot \nRight Side \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = true;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(22):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(23):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(24):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			case(25):{
				//super.setCourse(new File("courses/empty1600x1200.course"));
				//showTextOnFeed("7 Yard Line \nSingle Head Shot \nRight Hostage \n1.5 Seconds\n5 Reps", (int) super.getArenaWidth()/4, 0, Color.BLACK, Color.YELLOW, new Font("TimesRoman", 120.0));
				doThePause = false;
				SevenYdLineHostage();
				roundCounter++;
				break;
			}
			default:{
				doThePause = false;
				showShots();
				goodbye.cancel();
				break;
			}
		}//end switch


	}//end setround
	private void startRound() {

		if(logger.isTraceEnabled())logger.debug("inside startRound");
		if (!repeatExercise) {
			logger.debug("returning without action from startRound");
			return;
		}

		//getProjArenaController().getCanvasManager().setShowShots(true);
		//getProjArenaController().getCanvasManager().setShowShots(false);

		//this.roundTargets = new HashSet<Target>();
		//this.roundTargets.addAll(targets);

		//first scheduled function
		mySF = executorService.schedule(new preview(), (long)4.0, TimeUnit.SECONDS);
		if(logger.isTraceEnabled())logger.debug("scheduled a preview to start in 4 seconds");

	}

	//protected class callSetRound implements Callable<Void> {
	public Task<Boolean> setTheRound(){//Task task = new Task<Boolean>(){
		return new Task<Boolean>() {
			@Override
			public Boolean call() throws InterruptedException {
				setRound();
				return true;
			}

		};
	}

//	public Task<Boolean> setTheRoundTitle(){//Task task = new Task<Boolean>(){
//		return new Task<Boolean>() {
//			@Override
//			public Boolean call() {
//				setRoundTitle();
//				return true;
//			}
//
//		};
//	}

	protected class presentTarget implements Callable<Void> {
		@Override
		public Void call() {


			//showTextOnFeed(String.format("in presentTarget"));//  %d", roundTargets.size()) );
			stopTimeB = System.currentTimeMillis();

			thisSuper.pauseShotDetection(false);
			//thisSuper.showTextOnFeed("shot detection started in presentTarget");

//			if(numberOfReps != 0){
//				if(repCounter > numberOfReps) reset();
//				repCounter++;
//			}

			ImageRegion myImageRegion1a = null;// = myImageRegion;
			for (final Target t : targets) {
				for (final TargetRegion r : t.getRegions()) {

					if (r.getType().equals(RegionType.IMAGE)){
						myImageRegion1a = (ImageRegion)r;
						if(myImageRegion1a.getAnimation().isPresent()){
							if(logger.isTraceEnabled())logger.debug("in presentTarget 1a calling play");
							myImageRegion1a.getAnimation().get().setRate(rotationTimeForTarget);
							myImageRegion1a.getAnimation().get().play();
						}
					}
				}

			}


			myImageRegion1a.getAnimation().get().setOnFinished(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event){
					startTimeP = System.currentTimeMillis();
					startShotTimer = startTimeP;
					if(logger.isTraceEnabled())logger.debug("scheduling turnAwayTarget2 in presentationTime");
					executorService.schedule(new turnAwayTarget2(), presentationTimeforTargetInMilli, TimeUnit.MILLISECONDS);//1.3
				}
			});

			return null;
		}//end void call

	}//end presentTarget


	Boolean doThePause = true;

	Task<Boolean> goodbye;// = setTheRound();

	protected class preview implements Callable<Void> {
		@Override
		public Void call() {

			thisSuper.pauseShotDetection(true);
			if(logger.isTraceEnabled())logger.debug("in preview");
			if(!manualMode){
				Task<Boolean> hello = setTheRound();
				goodbye = hello;
				//Thread myThread = new Thread(hello);
					//mySF = executorService.schedule(new callSetRound(), 1, TimeUnit.MILLISECONDS);
					//showTextOnFeed("targetListSize: "+targets.size());
				//final Task<Boolean> hello = setTheRound();

				//task.run();
					//hello.cancel();
					//myThread.stop();

					hello.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
						@Override
						public void handle(WorkerStateEvent event){

						//showTextOnFeed("succeeded");
						//didItFinish = true;



//						if(numberOfReps != 0){
//							if(repCounter > numberOfReps) reset();
//							repCounter++;
//						}
							if(doThePause){
								try {
									logger.debug("pausing for 5 seconds");
									Thread.sleep(5000);//to show round title message
									showTextOnFeed("");

								} catch (InterruptedException e) {

									e.printStackTrace();
								}
							}
							if(noPreview){
								if(logger.isTraceEnabled())logger.debug("scheduling delayForShooterReset");
								executorService.schedule(new delayForShooterReset(), timeDelayBeforeLineIsSetCallInMilli, TimeUnit.MILLISECONDS);

							}else{
								stopTimeB = System.currentTimeMillis();

								thisSuper.pauseShotDetection(false);

								ImageRegion myImageRegion1a = null;// = myImageRegion;
								for (final Target t : targets) {
									for (final TargetRegion r : t.getRegions()) {

										if (r.getType().equals(RegionType.IMAGE)){
											myImageRegion1a = (ImageRegion)r;
											if(myImageRegion1a.getAnimation().isPresent()){
												if(logger.isTraceEnabled())logger.debug("in preview 1a");
												myImageRegion1a.getAnimation().get().setRate(rotationTimeForTarget);
												myImageRegion1a.getAnimation().get().reverse();
											}//end if
										}//end if
									}//end for

								}//end for


								myImageRegion1a.getAnimation().get().setOnFinished(new EventHandler<ActionEvent>() {
									@Override
									public void handle(ActionEvent event){
										startTimeP = System.currentTimeMillis();
										startShotTimer = startTimeP;
										if(logger.isTraceEnabled())logger.debug("scheduling turnAwayTarget in presentationTime");
										executorService.schedule(new turnAwayTarget(), presentationTimeforTargetInMilli, TimeUnit.MILLISECONDS);//1.3
									}
								});
							}//end else
						//hello.cancel();

						}//end handle
					});//end task

					hello.setOnFailed(new EventHandler<WorkerStateEvent>(){
						@Override
						public void handle(WorkerStateEvent t){
							logger.debug("hello failed");
						}
					});
					hello.setOnRunning(new EventHandler<WorkerStateEvent>(){
						@Override
						public void handle(WorkerStateEvent t){
							logger.debug("hello running");
						}
					});
					hello.setOnScheduled(new EventHandler<WorkerStateEvent>(){
						@Override
						public void handle(WorkerStateEvent t){
							logger.debug("hello scheduled");
						}
					});
					hello.setOnCancelled(new EventHandler<WorkerStateEvent>(){
						@Override
						public void handle(WorkerStateEvent t){
							logger.debug("hello cancelled");
						}
					});
					//Thread myThread = new Thread(hello);
					//myThread.start();
					hello.run();
					logger.debug("hello running");

//				while(true){
//
//					if(didItFinish)break;
//
//				}//end while
				//didItFinish = false;

			}//end if
			else{
			//thisSuper.showTextOnFeed(String.format("in presentTarget"));//  %d", roundTargets.size()) );
			stopTimeB = System.currentTimeMillis();

			thisSuper.pauseShotDetection(false);

			ImageRegion myImageRegion1a = null;// = myImageRegion;
			for (final Target t : targets) {
				for (final TargetRegion r : t.getRegions()) {

					if (r.getType().equals(RegionType.IMAGE)){
						myImageRegion1a = (ImageRegion)r;
						if(myImageRegion1a.getAnimation().isPresent()){
							if(logger.isTraceEnabled())logger.debug(String.format("in else 1a calling play"));
							myImageRegion1a.getAnimation().get().setRate(rotationTimeForTarget);
							myImageRegion1a.getAnimation().get().play();
						}//end if
					}//end if
				}//end for

			}//end for


			myImageRegion1a.getAnimation().get().setOnFinished(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event){
					startTimeP = System.currentTimeMillis();
					startShotTimer = startTimeP;
					if(logger.isTraceEnabled())logger.debug("turnawayTarget scheduled for presentationTime");
					executorService.schedule(new turnAwayTarget(), presentationTimeforTargetInMilli, TimeUnit.MILLISECONDS);//1.3
				}
			});
		}//end else

			if(logger.isTraceEnabled())logger.debug("preview returning null");
			return null;
		}//end void call

	}//end presentTarget
	protected class turnAwayTarget implements Callable<Void> {

		@Override
		public Void call() {


			//showTextOnFeed(String.format("in turnAwayTarget"));//  %d", roundTargets.size()) );

//			if(numberOfReps != 0){
//				repCounter++;
//				if(repCounter > numberOfReps) showShots();
//
//			}else{
//
//				if(stopFlag) showShots();
//			}

			ImageRegion myImageRegion2a = null;// = myImageRegion;

			for (final Target t : targets) {
				for (final TargetRegion r : t.getRegions()) {

					if (r.getType().equals(RegionType.IMAGE)){
						myImageRegion2a = (ImageRegion)r;
						if(myImageRegion2a.getAnimation().isPresent()){
							if(logger.isTraceEnabled())logger.debug("in turnAwayTarget 2a calling reverse");
							myImageRegion2a.getAnimation().get().setRate(rotationTimeForTarget);
							myImageRegion2a.getAnimation().get().reverse();
						}
					}
				}

			}

			stopTimeP = System.currentTimeMillis();
			final long presentedTime = stopTimeP-startTimeP;
			//thisSuper.showTextOnFeed("PresentedTime: " + presentedTime);
			startTimeR = System.currentTimeMillis();

			executorService.schedule(new stopShotDetection(), timeDelayBeforeShotDetectionTurnedOffInMilli, TimeUnit.MILLISECONDS);

			myImageRegion2a.getAnimation().get().setOnFinished(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event){

					stopTimeR = System.currentTimeMillis();
					final long rotationTime = stopTimeR-startTimeR;
					final long roundTime = stopTimeB-startTimeB;

					//showTextOnFeed("FirstShotTime: " + shotTime + " RotationTime: " + rotationTime + " PresentedTime: " + presentedTime + " RoundTime: " + roundTime,false);

					if(logger.isTraceEnabled())logger.debug("in turnAwayTarget 2a scheduling delayforshooterReset");
					executorService.schedule(new delayForShooterReset(), timeDelayBeforeLineIsSetCallInMilli, TimeUnit.MILLISECONDS);

				}

			});

			return null;
		}//void call
	}//end class
	Boolean noPreview = false;
	protected class turnAwayTarget2 implements Callable<Void> {

		@Override
		public Void call() {

			//showTextOnFeed(String.format("in turnAwayTarget_2"));//  %d", roundTargets.size()) );

//			if(numberOfReps != 0){
//				repCounter++;
//				if(repCounter > numberOfReps) showShots();
//
//			}else{
//
//				if(stopFlag) showShots();
//			}

			ImageRegion myImageRegion2a = null;// = myImageRegion;

			for (final Target t : targets) {
				for (final TargetRegion r : t.getRegions()) {

					if (r.getType().equals(RegionType.IMAGE)){
						myImageRegion2a = (ImageRegion)r;
						if(myImageRegion2a.getAnimation().isPresent()){
							if(logger.isTraceEnabled())logger.debug("in turnAwayTarget2 2a calling reverse");
							myImageRegion2a.getAnimation().get().setRate(rotationTimeForTarget);
							myImageRegion2a.getAnimation().get().reverse();
						}
					}
				}

			}

			stopTimeP = System.currentTimeMillis();
			final long presentedTime = stopTimeP-startTimeP;
			//thisSuper.showTextOnFeed("PresentedTime: " + presentedTime);
			startTimeR = System.currentTimeMillis();

			executorService.schedule(new stopShotDetection(), timeDelayBeforeShotDetectionTurnedOffInMilli, TimeUnit.MILLISECONDS);

			myImageRegion2a.getAnimation().get().setOnFinished(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event){

					stopTimeR = System.currentTimeMillis();
					final long rotationTime = stopTimeR-startTimeR;
					final long roundTime = stopTimeB-startTimeB;

					//showTextOnFeed("FirstShotTime: " + shotTime + " RotationTime: " + rotationTime + " PresentedTime: " + presentedTime + " RoundTime: " + roundTime, false);
					//executorService.schedule(new presentTarget(), (long)4.0, TimeUnit.SECONDS);
					if(manualMode){
						if(logger.isTraceEnabled())logger.debug("manualMode is true, scheduling delayForShooterReset");
						executorService.schedule(new delayForShooterReset(), timeDelayBeforeLineIsSetCallInMilli, TimeUnit.MILLISECONDS);
					}else{
//						repeatExercise = false;
//						executorService.shutdownNow();
//
//						repeatExercise = true;
//						executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
//								new NamedThreadFactory("FrontSight_v3Exercise"));

						if(logger.isTraceEnabled())logger.debug("manualMode is false, scheduling preview");
						executorService.schedule(new preview(), (long)4.0, TimeUnit.SECONDS);
						
					}

				}

			});

			return null;
		}//void call
	}//end class
	protected class stopShotDetection implements Callable<Void> {
		@Override
		public Void call() {
			thisSuper.pauseShotDetection(true);
			setColumnText(theShotTime, subTarget, points1,count1);
			//showTextOnFeed("in stopShotDetection");

			return null;

		}
	}
	protected class delayForShooterReset implements Callable<Void> {
		@Override
		public Void call() {
			//showTextOnFeed("in delay for shooter reset");
			TrainingExerciseBase.playSound("sounds/line_is_set.wav");
			startTimeB = System.currentTimeMillis();
			executorService.schedule(new presentTarget(), timeBetweenRoundsInMilli, TimeUnit.MILLISECONDS);
			return null;
		}

	}//end class

	static int count1 = 0;
	static String subTarget = "noTarget";
	static String points1 = "noPoints";
	static double theShotTime = 0;
	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (hit.isPresent()) {
			stopShotTimer = System.currentTimeMillis();
			shotTime = (stopShotTimer - startShotTimer);
			stopShotTimer = 0;

			theShotTime = (double)shotTime/1000.0;
			//setShotTimerColumnText("ShotTime", String.format("%6f", theShotTime));

			if (hit.get().getHitRegion().tagExists("points")) {
				points1 = hit.get().getHitRegion().getTag("points");
				//setShotTimerColumnText("Points ", points1);
				count1++;
				//setShotTimerColumnText("Number", Integer.toString(count1));
			}//end tagexists
			if (hit.get().getHitRegion().tagExists("subtarget")) {
				subTarget = hit.get().getHitRegion().getTag("subtarget");
				//setShotTimerColumnText("Subtarget", subTarget);
				Point2D thePoint = new Point2D(hit.get().getImpactX(), hit.get().getImpactY());
				hitMap.put(hit.get().getHitRegion().getTag("subtarget"), thePoint);
				//getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().add(new javafx.scene.shape.Circle(thePoint.getX(), thePoint.getY(),10,Color.YELLOW));
			}//end tagexists
			
			showTextOnFeed(String.format("ShotTime: %6f, %s, points: %s ", theShotTime,subTarget,points1),50, 0, Color.BLACK,
					Color.YELLOW, new Font("TimesRoman", 45));
			
		}//end isPresent
	}
	
	public void setColumnText (double theShotTime, String subTarget, String points1, int count1){
		setShotTimerColumnText("ShotTime", String.format("%6f", theShotTime));
		//setShotTimerColumnText("Points ", points1);
		//setShotTimerColumnText("Number", Integer.toString(count1));
		setShotTimerColumnText("Subtarget", subTarget);
	}


	public void showShots() {
		//showTextOnFeed("inside showShots");
		executorService.shutdownNow();
		//so we can remove the shots we added later
		//canvasGroupSize = thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().size();
		List<Shot> myShotList = getProjArenaController().getCanvasManager().shots;

		if(!myShotList.isEmpty()){
			for (Shot s :  myShotList){
				Circle myCircle = new Circle(s.getX(), s.getY(),3,Color.YELLOW );
				myCircle.setStroke(Color.BLACK);
				myCircle.setStrokeWidth(2);
				getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().add(myCircle);//(new javafx.scene.shape.Circle(s.getX(), s.getY(),5,Color.YELLOW)).setStroke(Color.BLACK));

				//s.getMarker().setFill(Color.YELLOW);
				//s.getMarker().setVisible(true);
				//getProjArenaController().getCanvasManager().addArenaShot(s, null);// .addShot(Color.YELLOW, s.getX(), s.getY());// .getCanvasGroup().getChildren().
			}//end for

		}//end if
		else{
			logger.debug("myShotList size was empty");
		}//end else
		//super.myShootOffController.toggleArenaShotsClicked(new ActionEvent());
		repeatExercise = false;
		executorService.shutdownNow();
		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,new NamedThreadFactory("FrontSight_v3Exercise"));

		ImageRegion myImageRegion3a = null;
		for (final Target t : targets) {
			for (final TargetRegion r : t.getRegions()) {

				if (r.getType().equals(RegionType.IMAGE)){
					myImageRegion3a = (ImageRegion)r;
					if(myImageRegion3a.getAnimation().isPresent()){
						if(calibration){
							//showTextOnFeed("calibration is true");
							myImageRegion3a.getAnimation().get().setCurrentFrame(0);

						}else{
							//showTextOnFeed("calibration is false");
							myImageRegion3a.getAnimation().get().setCurrentFrame(5);

						}//end else
					}//end if
				}//end if

			}//end for

		}//end for
		calibration = false;//to reset the toggle

	}//end show shots

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Frontsight", "1.0", "ifly53e",
				"This exersize is a Frontsight course simululator.  Frontsight targets are shown at "
						+ "various ranges (3, 5, 7, 10, 15, 25, and 50 yard lines) with three different "
						+ "types of Frontsight targets (School Drill, Hostage, and Odd Angle)."
						+ "The targets are animated to turn just like on the range.  Target presentation"
						+ "times are geared for the Handgun Combat Master course.");
	}


	private void ThreeYdLine(){

				this.targets.clear();
				this.targets = super.setCourse(new File("courses/3ydLineEdgeOfDesk_v3.course"));
				presentationTimeforTargetInMilli = (long) 1300;
				if(manualMode){
					setupRound();
					startRound();
				}

	}

	private void Type3Malfunction(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/3ydLineEdgeOfDesk_v3.course"));
		presentationTimeforTargetInMilli = (long) 4000;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void ThreeYdLineCloseContact(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/3ydLineEdgeOfDesk_v3.course"));
		presentationTimeforTargetInMilli = (long) 1000;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void ThreeYdLinePalmStrike(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/3ydLineEdgeOfDesk_v3.course"));
		presentationTimeforTargetInMilli = (long) 1300;
		if(manualMode){
			setupRound();
			startRound();
		};
	}
	private void FiveYdLine(){

			this.targets.clear();
			this.targets = super.setCourse(new File("courses/5ydLineEdgeOfDesk1_v5.course"));
			presentationTimeforTargetInMilli = (long) 1500;
			if(manualMode){
				setupRound();
				startRound();
			}
	}

	private void FiveYdLine3Targets(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 1800;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void FiveYdLine4Targets(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 2100;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void FiveYdLine2Targets(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 1500;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void FiveYdLineHeadShots(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/5ydLineEdgeOfDesk1_v5.course"));
		presentationTimeforTargetInMilli = (long) 1300;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void SevenYdLineSD(){

			this.targets.clear();
			this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
			presentationTimeforTargetInMilli = (long) 1500;
			if(manualMode){
				setupRound();
				startRound();
			}

	}

	private void SevenYdLineSDPresentation(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 1200;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void SevenYdLineSDFiringSide(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 1300;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void SevenYdLineSDRear(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 1400;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void AmbiDrillSevenYdLineSD(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineSchoolDrill_v4.course"));
		presentationTimeforTargetInMilli = (long) 6000;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void SevenYdLineHostage(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/7ydLineHostOdd_v4.course"));
		//5TargetLineHigh.jpg x="-545.097737" y="-801.234857
		//String resourceFilename = "arena/backgrounds/5TargetLineHigh.png";
		//String resourceFilename = "targets/5TargetLineHigh.png";
		//InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceFilename);
		//LocatedImage img = new LocatedImage(is, resourceFilename);
		//super.setArenaBackground(img, -545, -801);
		//super.setArenaBackground(img);
		presentationTimeforTargetInMilli = (long) 1500;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void TenYdLine(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/10ydLineEdgeOfDesk2_v5.course"));
		presentationTimeforTargetInMilli = (long) 1800;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void FifteenYdLine(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/15ydLineEdgeOfDesk_v4.course"));
		presentationTimeforTargetInMilli = (long) 2100;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void TwentyFiveYdLine(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/25ydLineEdgeOfDesk_v2.course"));
		presentationTimeforTargetInMilli = (long) 2700;
		if(manualMode){
			setupRound();
			startRound();
		}
	}
	private void FiftyYdLine(){

			this.targets.clear();
			this.targets = super.setCourse(new File("courses/50ydLineEdgeOfDesk_v3.course"));
			presentationTimeforTargetInMilli = (long) 6000;
			if(manualMode){
				setupRound();
				startRound();
			}

	}
	private void FiftyYdLineA(){
		this.targets.clear();
		this.targets = super.setCourse(new File("courses/50ydLineEdgeOfDesk_v3.course"));
		presentationTimeforTargetInMilli = (long) 6000;
		if(manualMode){
			setupRound();
			startRound();
		}
	}


	@Override
	public void reset(List<Target> targets) {

		count1 = 0;
		roundCounter = 1;
		//toggleArenaShotsClicked();
		super.pauseShotDetection(true);

		repeatExercise = false;
		executorService.shutdownNow();

		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
				new NamedThreadFactory("FrontSight_v3Exercise"));

		this.targets.clear();
		this.targets = super.setCourse(new File("courses/empty1600x1200.course"));//3ydLineNoTargets_v3.course"));

		getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().clear();

		//if (checkTargets(targets)) startRound();
		//showTextOnFeed("inside local reset");
		getProjArenaController().getCanvasManager().setShowShots(false);
		//startRound();
	}

	@Override
	public void targetUpdate(Target target, TargetChange change) {


	}
	
	@Override
	public void destroy() {
		reset();
		repeatExercise = false;
		executorService.shutdownNow();
		super.destroy();
	}



}//end class


		//int incX=100;
		//line up the targets
		//for (TossUpTarget b : shootTargets){
			//File theFile = b.getTarget().getTargetFile();
			//String theFileName = theFile.getName();//  getAbsolutePath();//  getName();
			//b.getTarget().setPosition(incX,400);
		//-->	for(Map.Entry<String, Point2D> e: hitMap.entrySet()){
				//if(theFileName.compareToIgnoreCase(e.getKey())==0 ){
					//super.showTextOnFeed(String.format("theFileName was found inside update: %s",theFileName));
						//for (Point2D p2d : e.getValue().g){
						//-->	thisSuper.getProjArenaController().getCanvasManager().getCanvasGroup().getChildren().add(new javafx.scene.shape.Circle(e.getValue().getX(), e.getValue().getY(),20,Color.RED));
						//}//end for
					//}//end for
				//}//end if
		//-->	}//end for

			//incX=incX+225;
		//}//end for

		//super.reset();6package com.shootoff.plugins;

