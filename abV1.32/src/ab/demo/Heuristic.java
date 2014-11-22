package ab.demo;

import ab.demo.other.ActionRobot;
import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.Vision;

import java.awt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by madhav on 17/11/14.
 */
public class Heuristic {

    private Vision vision;

    private int counterGames;
    TrajectoryPlanner tp;
    private List<Double> heuristic = new ArrayList();
    public Boolean targetIsPig=false;
    private ActionRobot aRobot;

    public Heuristic(Vision vision, TrajectoryPlanner tp, ActionRobot aRobot,int counterGames){
        this.vision=vision;
        this.tp=tp;
        targetIsPig=false;
        this.aRobot=aRobot;
        this.counterGames=counterGames;
    }

    public Point solve(){
        List<ABObject> pigs = vision.findPigsMBR();
        Rectangle sling = vision.findSlingshotMBR();
        Point refPoint = tp.getReferencePoint(sling);
        List<ABObject> blocks = vision.findBlocksRealShape();

        ABObject targetBlock = null;
        Point targetPoint=null;
        int targetX,targetY;

        ABObject resStone = getRoundStone(blocks,pigs);
        if(resStone!=null){
            targetBlock=resStone;
            targetX = (int)targetBlock.getCenterX();
            targetY = (int) (targetBlock.getCenterY() - (3 * targetBlock.getHeight())/10);
            targetPoint = new Point(targetX,targetY);
            return targetPoint;
        }

        ABObject resPig = getTargetPig(pigs, blocks);
        ABObject lBlock = getLowerBlock(resPig,blocks);

        ABObject tBlock=null;

        if(lBlock!=null){
            tBlock = getTargetBlock(lBlock,blocks);
        }
        else{
            tBlock = getTargetBlock1(resPig,blocks);
        }
        if(tBlock==null && lBlock!=null){
            tBlock = getTargetBlockTrivial(lBlock, blocks);
        }

        if(tBlock == null){
            targetBlock = resPig;
            targetIsPig=true;
        }
        else{
            targetBlock = tBlock;
        }



        targetX = (int)targetBlock.getCenterX();
        targetY = (int) (targetBlock.getCenterY() - (3 * targetBlock.getHeight())/10);
        targetPoint = new Point(targetX,targetY);

        return targetPoint;

    }

    public ABObject getRoundStone(List<ABObject> blocks, List<ABObject> pigs){
        ABObject resStone=null;
        int maxArea=0;

        if(blocks.isEmpty())
            return null;

        for(int i=0;i<blocks.size();i++){
            if(blocks.get(i).shape.toString() == "Circle" && blocks.get(i).getType().toString()=="Stone" && blocks.get(i).area>=maxArea){
                if(blocks.get(i).area==maxArea && blocks.get(i).getCenterX()<resStone.getCenterX())
                    resStone = blocks.get(i);
                else if(blocks.get(i).area>maxArea)
                    resStone = blocks.get(i);
                maxArea=blocks.get(i).area;
            }
        }
        if(resStone==null || counterGames>1)
            return null;
        ABObject pig=null;
        for(int i=0;i<pigs.size();i++){
            pig = pigs.get(i);
            if((pig.getCenterX()+5)>resStone.getCenterX() && pig.getCenterY()>=resStone.getCenterY()+5){
                return resStone;
            }
        }
        blocks.remove(resStone);
        return getRoundStone(blocks, pigs);
    }

    public ABObject getTargetPig(List<ABObject> pigs,List<ABObject> blocks){
        ABObject resBlock=null;
        generateHeuristic(pigs,blocks);

        double hmax=-1;
        int hindex=0;
        for(int i=0;i<heuristic.size();i++){

            if(heuristic.get(i)>hmax){
                hindex = i;
                hmax = heuristic.get(i);
            }

            else if(heuristic.get(i)==hmax){
                if(pigs.get(hindex).getCenterX()<pigs.get(i).getCenterX())
                    hindex=i;
            }
        }
        resBlock = pigs.get(hindex);
        return resBlock;
    }

    //Function to find the block on which the pig lies.
    public ABObject getLowerBlock(ABObject resBlock,List<ABObject> blocks){
        ABObject lBlock=null,block=null;

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
        return lBlock;
    }

    public ABObject getTargetBlock(ABObject lBlock,List<ABObject> blocks){
        ABObject tBlock=null,block=null;

        //Find the block that touches lBlock and lies in left and is below lBlock ==> tBlock

        for(int j=0;j<blocks.size();j++){
            block=blocks.get(j);
            if(block.intersects(lBlock.getCenterX(),lBlock.getCenterY(),lBlock.getWidth(),lBlock.getHeight()) && blockOrient(block)&& block.getCenterX()<lBlock.getCenterX() &&block.getCenterY()>lBlock.getCenterY()){
                tBlock = block;
            }
            else if (tBlock==null && block.intersects(lBlock.getCenterX(),lBlock.getCenterY(),lBlock.getWidth(),lBlock.getHeight()) && blockOrient(block) && block.getCenterX()<lBlock.getCenterX()){
                tBlock = block;
            }
        }
        //System.out.println(tBlock.getCenter().toString()+"Orient:"+blockOrient(tBlock));
        return tBlock;
    }

    //Special Assignment of tBlock based on distance, Called when lBlock = null
    public ABObject getTargetBlock1(ABObject resBlock, List<ABObject> blocks){
        ABObject block = null,tBlock=null;
        String btype = null;
        double distFromPig,min=9999999;
        for(int j=0;j<blocks.size();j++){
            block = blocks.get(j);
            btype = block.getType().toString();
            if(block.getCenterX()<resBlock.getCenterX() && blockOrient(block)){
                distFromPig = distance(block.getCenter(), resBlock.getCenter());
                if(distFromPig<=min){
                    min = distFromPig;
                    tBlock = block;
                }
            }
        }
        return tBlock;
    }

    public ABObject getTargetBlockTrivial(ABObject lBlock,List<ABObject> blocks){
        ABObject tBlock=null,block=null;
        String btype=null;
        double distFromPig,min=9999999;
        for(int j=0;j<blocks.size();j++){
            block = blocks.get(j);
            btype = block.getType().toString();
            if(block.getCenterX()<lBlock.getCenterX() && blockOrient(block) && ((lBlock.getCenterX()-block.getCenterX()) < 25)){
                distFromPig = distance(new Point((int)block.getCenterX(),(int)(block.getCenterY()-block.getHeight()/2)), lBlock.getCenter());
                if(distFromPig<=min){
                    min = distFromPig;
                    tBlock = block;
                }
            }
        }
        return tBlock;
    }

    public void generateHeuristic(List<ABObject> pigs,List<ABObject> blocks){
        ABObject pig = pigs.get(0),mpig=null;
        double maxDistance = 25;
        ABObject block=null;
        double hValue;

        for(int i=0;i<pigs.size();i++){
            hValue = 0;
            mpig=null;
            pig = pigs.get(i);

            for(int j=0;j<blocks.size();j++){
                block = blocks.get(j);
                if(distance(block.getCenter(),pig.getCenter())<=maxDistance && block.getCenterX()<=pig.getCenterX()&&(block.getType().toString()=="Wood" || block.getType().toString() == "Ice")){
                    hValue +=1;
                }
            }

            for(int j=0;j<pigs.size();j++){
                mpig = pigs.get(j);
                if(distance(mpig.getCenter(),pig.getCenter())<maxDistance && i!=j){
                    hValue += 5;
                }
            }
            hValue=hValue + (((1000-pig.getCenterY())/1000)*10);

            heuristic.add(hValue);
        }

    }

    public boolean blockOrient(ABObject b)
    {
        if((b.getMaxY()-b.getMinY())>(b.getMaxX()-b.getMinX())){
            if(counterGames<=1)
                return true;
            else{
                if (b.type.toString().equals("Wood") || b.type.toString().equals("Ice"))
                    return true;
                else return false;
            }
        }
        else
            return false;
    }

    private double distance(Point p1, Point p2) {
        return Math
                .sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
                        * (p1.y - p2.y)));
    }


}

