/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. 
 **To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ 
 *or send a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
 *****************************************************************************/
package ab.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.planner.TrajectoryPlanner;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;

public class NaiveAgent implements Runnable {

    private ActionRobot aRobot;
    private Random randomGenerator;
    public int currentLevel = 1;
    public static int time_limit = 12;
    private Map<Integer,Integer> scores = new LinkedHashMap<Integer,Integer>();
    TrajectoryPlanner tp;
    private boolean firstShot;
    private Point prevTarget;
    // a standalone implementation of the Naive Agent
    public NaiveAgent() {
        
        aRobot = new ActionRobot();
        tp = new TrajectoryPlanner();
        prevTarget = null;
        firstShot = true;
        randomGenerator = new Random();
        // --- go to the Poached Eggs episode level selection page ---
        ActionRobot.GoFromMainMenuToLevelSelection();

    }

    
    // run the client
    public void run() {

        aRobot.loadLevel(currentLevel);
        while (true) {
            GameState state = solve();
            if (state == GameState.WON) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int score = StateUtil.getScore(ActionRobot.proxy);
                if(!scores.containsKey(currentLevel))
                    scores.put(currentLevel, score);
                else
                {
                    if(scores.get(currentLevel) < score)
                        scores.put(currentLevel, score);
                }
                int totalScore = 0;
                for(Integer key: scores.keySet()){

                    totalScore += scores.get(key);
                    System.out.println(" Level " + key
                            + " Score: " + scores.get(key) + " ");
                }
                System.out.println("Total Score: " + totalScore);
                aRobot.loadLevel(++currentLevel);
                // make a new trajectory planner whenever a new level is entered
                tp = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;
            } else if (state == GameState.LOST) {
                System.out.println("Restart");
                aRobot.restartLevel();
            } else if (state == GameState.LEVEL_SELECTION) {
                System.out
                .println("Unexpected level selection page, go to the last current level : "
                        + currentLevel);
                aRobot.loadLevel(currentLevel);
            } else if (state == GameState.MAIN_MENU) {
                System.out
                .println("Unexpected main menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                aRobot.loadLevel(currentLevel);
            } else if (state == GameState.EPISODE_MENU) {
                System.out
                .println("Unexpected episode menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                aRobot.loadLevel(currentLevel);
            }

        }

    }

    private double distance(Point p1, Point p2) {
        return Math
                .sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
                        * (p1.y - p2.y)));
    }
    private List<String> blocksDescription(Vision vision){
        List<String> desc = new ArrayList<String>();
        String res="";
        List<ABObject> blocks = vision.findBlocksRealShape();
        ABObject block=null;

        if(!blocks.isEmpty()){
            for(int j=1;j<blocks.size();j++){
                block = blocks.get(j);
                res = "Block Number: "+block.id+",Block Shape: "+block.shape.toString()+",Block Material: "+block.getType().toString();
                System.out.println("Block Number:"+block.id+",Block Shape:"+block.shape.toString()+",Block Material:"+block.getType().toString());
                desc.add(res);
            }
        }
        return desc;
    }

    public boolean blockOrient(ABObject b)
    {
        if(b.getHeight()>b.getWidth())
            return true;
        else
            return false;
    }

    public GameState solve()
    {

        // capture Image
        BufferedImage screenshot = ActionRobot.doScreenShot();

        // process image
        Vision vision = new Vision(screenshot);

        // find the slingshot
        Rectangle sling = vision.findSlingshotMBR();

        // confirm the slingshot
        while (sling == null && aRobot.getState() == GameState.PLAYING) {
            System.out
                    .println("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            screenshot = ActionRobot.doScreenShot();
            vision = new Vision(screenshot);
            sling = vision.findSlingshotMBR();
        }
        // get all the pigs
        List<ABObject> pigs = vision.findPigsMBR();

        //blocksDescription(vision);

        GameState state = aRobot.getState();

        // if there is a sling, then play, otherwise just skip.
        if (sling != null) {

            if (!pigs.isEmpty()) {

                Point releasePoint = null;
                Shot shot = new Shot();
                Point resP = null;
                int dx,dy;
                {
                    // random pick up a pig
                    Point refPoint = tp.getReferencePoint(sling);

                    //blocksDescription(vision);

                    List<ABObject> blocks = vision.findBlocksRealShape();
                    ABObject resBlock=null,targetBlock = null,resStone=null;


                    for(int i=0;i<blocks.size();i++){
                        if(blocks.get(i).shape.toString() == "Circle" && blocks.get(i).getType().toString()=="Stone"){
                            resStone = blocks.get(i);
                            resBlock = blocks.get(i);
                            break;
                        }
                    }

                    if(resBlock==null){
                        ABObject resPig=pigs.get(0),pig = pigs.get(0);
                        double maxDistance = 25;
                        ABObject block=null;
                        List<Integer> heuristic = new ArrayList();
                        int resH;
                        for(int i=0;i<pigs.size();i++){
                            resH = 0;
                            ABObject mpig=null;
                            pig = pigs.get(i);
                            blocks = vision.findBlocksRealShape();
                            for(int j=0;j<blocks.size();j++){
                                block = blocks.get(j);
                                if(distance(block.getCenter(),pig.getCenter())<=maxDistance && block.getCenterX()<=pig.getCenterX()&&(block.getType().toString()=="Wood" || block.getType().toString() == "Ice")){
                                    resH +=1;
                                }
                            }

                            for(int j=0;j<pigs.size();j++){
                                mpig = pigs.get(j);
                                if(distance(mpig.getCenter(),pig.getCenter())<maxDistance && i!=j){
                                    resH += 5;
                                }
                            }
                            heuristic.add(resH);
                        }
                        double hmax=-1;
                        int hindex=0;
                        for(int i=0;i<heuristic.size();i++){
                            if(heuristic.get(i)>hmax){
                                hindex = i;
                                hmax = heuristic.get(i);
                            }
                            else if(heuristic.get(i)==hmax){
                                if(pigs.get(hindex).getCenterX()>pigs.get(i).getCenterX())
                                    hindex=i;
                            }
                        }
                        resBlock = pigs.get(hindex);
                    }


                    blocks = vision.findBlocksRealShape();
                    ABObject block=null;

                    double distPig,tempd;
                    String btype;
                    ABObject tempResBlock = resBlock;

                    double min = 9999999;

                    //Finding the block on which resBlock lies = lBlock
                    ABObject lBlock=null;
                    for(int j=0;j<blocks.size();j++){
                        block=blocks.get(j);
                        if(block.intersects(resBlock.getCenterX(),resBlock.getCenterY(),resBlock.getWidth(),resBlock.getHeight()) && block.getCenterY()>resBlock.getCenterY()){
                            if(lBlock==null)
                                lBlock = block;
                            else{
                                if(block.getCenterX()<(lBlock.getCenterX()+lBlock.getWidth()/2))
                                    lBlock = block;
                            }
                        }
                    }

                    //System.out.println("Distance: "+distance(lBlock.getCenter(),resBlock.getCenter()));



                    ABObject tBlock=null;
                    if(lBlock == null){
                        //Special Assignment of tBlock based on distance
                        for(int j=0;j<blocks.size();j++){
                            block = blocks.get(j);
                            btype = block.getType().toString();
                            if(block.getCenterX()<resBlock.getCenterX()){
                                distPig = distance(block.getCenter(), resBlock.getCenter());
                                if(distPig<=min && block.getCenterY()>resBlock.getCenterY()){
                                    min = distPig;
                                    tBlock = block;
                                }
                                else if(tBlock==null && distPig<=min){
                                    min = distPig;
                                    tBlock = block;
                                }
                            }
                        }
                    }

                    //Find the block that touches lBlock and lies in left and is below lBlock ==> tBlock

                    else{
                        for(int j=0;j<blocks.size();j++){
                            block=blocks.get(j);
                            if(block.intersects(lBlock.getCenterX(),lBlock.getCenterY(),lBlock.getWidth(),lBlock.getHeight()) && blockOrient(block)&& block.getCenterX()<lBlock.getCenterX() &&block.getCenterY()>lBlock.getCenterY()){
                                tBlock = block;
                            }
                            else if (tBlock==null && block.intersects(lBlock.getCenterX(),lBlock.getCenterY(),lBlock.getWidth(),lBlock.getHeight()) && blockOrient(block) && block.getCenterX()<lBlock.getCenterX()){
                                tBlock = block;
                            }
                        }
                    }


                    if(tBlock == null){
                        for(int j=0;j<blocks.size();j++){
                            block = blocks.get(j);
                            btype = block.getType().toString();
                            if(block.getCenterX()<lBlock.getCenterX() && blockOrient(block) && ((lBlock.getCenterX()-block.getCenterX()) < 25)){
                                distPig = distance(new Point((int)block.getCenterX(),(int)(block.getCenterY()-block.getHeight()/2)), lBlock.getCenter());
                                if(distPig<=min && block.getCenterY()>lBlock.getCenterY()){
                                    min = distPig;
                                    tBlock = block;
                                }
                                else if(tBlock==null && distPig<=min){
                                    min = distPig;
                                    tBlock = block;
                                }
                            }
                        }
                    }

                    //If tBlock is null, Pig is surrounded by nothing, Shoot the pig
                    if(tBlock == null){
                        targetBlock = resBlock;
                    }
                    else{
                        targetBlock = tBlock;
                    }
                    if(resStone !=null){
                        targetBlock = resStone;
                    }

                    //System.out.println(min+" Block number: "+tempResBlock.id +" Block Type: "+tempResBlock.getType().toString());
                    //resBlock = tempResBlock;

                    int xres,yres;
                    xres = (int)targetBlock.getCenterX();
                    yres = (int) (targetBlock.getCenterY() - (3 * targetBlock.getHeight())/10);
                    resP = new Point(xres,yres);

                    //System.out.println(resP.toString());

                    Point _tpt = resP;// if the target is very close to before, randomly choose a
                    // point near it
                    //if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
                    //  double _angle = randomGenerator.nextDouble() * Math.PI * 2;
                    //  _tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
                    //  _tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
                    //  System.out.println("Randomly changing to " + _tpt);
                    //}

                    prevTarget = new Point(_tpt.x, _tpt.y);

                    // estimate the trajectory
                    ArrayList<Point> pts = tp.estimateLaunchPoint_1(sling, _tpt);

                    // do a high shot when entering a level to find an accurate velocity
                    if (firstShot && pts.size() > 1)
                    {
                        releasePoint = pts.get(1);
                    }
                    else if (pts.size() == 1)
                        releasePoint = pts.get(0);
                    else if (pts.size() == 2)
                    {
                        // randomly choose between the trajectories, with a 1 in
                        // 6 chance of choosing the high one
                        if (randomGenerator.nextInt(6) == 0)
                            releasePoint = pts.get(1);
                        else
                            releasePoint = pts.get(0);
                    }
                    else
                    if(pts.isEmpty())
                    {
                        System.out.println("No release point found for the target");
                        System.out.println("Try a shot with 45 degree");
                        releasePoint = tp.findReleasePoint(sling, Math.PI/4);
                    }

                    // Get the reference point



                    //Calculate the tapping time according the bird type
                    if (releasePoint != null) {
                        double releaseAngle = tp.getReleaseAngle(sling,
                                releasePoint);
                        System.out.println("Release Point: " + releasePoint);
                        System.out.println("Release Angle: "
                                + Math.toDegrees(releaseAngle));
                        int tapInterval = 0;
                        switch (aRobot.getBirdTypeOnSling())
                        {

                            case RedBird:
                                tapInterval = 0; break;               // start of trajectory
                            case YellowBird:
                                tapInterval = 75 + randomGenerator.nextInt(15);break; // 75-90% of the way
                            case WhiteBird:
                                tapInterval =  80 + randomGenerator.nextInt(10);break; // 80-90% of the way
                            case BlackBird:
                                tapInterval =  80 + randomGenerator.nextInt(10);break; // 80-90% of the way
                            case BlueBird:
                                tapInterval =  75 + randomGenerator.nextInt(10);break; // 75-85% of the way
                            default:
                                tapInterval =  60;
                        }

                        int tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);
                        dx = (int)releasePoint.getX() - refPoint.x;
                        dy = (int)releasePoint.getY() - refPoint.y;
                        shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
                    }
                    else
                    {
                        System.err.println("No Release Point Found");
                        return state;
                    }
                }

                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                {
                    ActionRobot.fullyZoomOut();
                    screenshot = ActionRobot.doScreenShot();
                    vision = new Vision(screenshot);
                    Rectangle _sling = vision.findSlingshotMBR();
                    if(_sling != null)
                    {
                        double scale_diff = Math.pow((sling.width - _sling.width),2) +  Math.pow((sling.height - _sling.height),2);
                        if(scale_diff < 25)
                        {
                            if(dx < 0)
                            {
                                aRobot.cshoot(shot);
                                state = aRobot.getState();
                                if ( state == GameState.PLAYING )
                                {
                                    screenshot = ActionRobot.doScreenShot();
                                    vision = new Vision(screenshot);
                                    List<Point> traj = vision.findTrajPoints();
                                    tp.adjustTrajectory(traj, sling, releasePoint);
                                    firstShot = false;
                                }
                            }
                        }
                        else
                            System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
                    }
                    else
                        System.out.println("no sling detected, can not execute the shot, will re-segement the image");
                }

            }

        }
        return state;
    }

    public static void main(String args[]) {

        NaiveAgent na = new NaiveAgent();
        if (args.length > 0)
            na.currentLevel = Integer.parseInt(args[0]);
        na.run();

    }
}
