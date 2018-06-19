package de.tum.bgu.msm.syntheticPopulationGenerator.munich.microlocation;

import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.Job;
import de.tum.bgu.msm.properties.PropertiesSynPop;
import de.tum.bgu.msm.syntheticPopulationGenerator.DataSetSynPop;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GenerateJobMicrolocation {

    private static final Logger logger = Logger.getLogger(GenerateJobMicrolocation.class);
    
    private final SiloDataContainer dataContainer;
    private final DataSetSynPop dataSetSynPop;
    private Map<Integer, Float> jobX = new HashMap<>();
    private Map<Integer, Float> jobY = new HashMap<>();
    Map<Integer, Integer> jobCount = new HashMap<Integer, Integer>();
    Map<Integer, Float> jobArea = new HashMap<>();
    Map<Integer, Integer> jobZone = new HashMap<Integer, Integer>();
    Map<Integer, Map<String,Map<Integer,Float>>> zoneJobTypeJobLocationArea = new HashMap<>();
    Map<Integer, Map<String,Float>> zoneJobTypeDensity = new HashMap<>();
    Map<Integer, Map<String,Integer>> jobsByJobTypeInTAZ = new HashMap<>();
    
    public GenerateJobMicrolocation(SiloDataContainer dataContainer, DataSetSynPop dataSetSynPop){
        this.dataSetSynPop = dataSetSynPop;
        this.dataContainer = dataContainer;
    }

    public void run() {
        logger.info("   Running module: job microlocation");
        logger.info("Start parsing jobs information to hashmap");
        readJobFile();
        densityCalculation();
        logger.info("Start Selecting the job to allocate the job");

        //Select the job to allocate the job
        int errorjob = 0;
        
        for (Job jj: dataContainer.getJobData().getJobs()) {
            int zoneID = jj.getZone();
            String jobType = jj.getType();

            if (zoneJobTypeDensity.get(zoneID).get(jobType)==0.0){
                jj.setCoord(new Coord(0.0,0.0));
                errorjob++;
                continue;
            }

            int selectedJobID = SiloUtil.select(zoneJobTypeJobLocationArea.get(zoneID).get(jobType));


            float remainingArea = zoneJobTypeJobLocationArea.get(zoneID).get(jobType).get(selectedJobID)- zoneJobTypeDensity.get(zoneID).get(jobType);
            if (remainingArea > 0) {
                zoneJobTypeJobLocationArea.get(zoneID).get(jobType).put(selectedJobID, remainingArea);
            } else {
                zoneJobTypeJobLocationArea.get(zoneID).get(jobType).put(selectedJobID, 0.0f);
            }
            jj.setCoord(new Coord(jobX.get(selectedJobID),jobY.get(selectedJobID)));

            //for test only
            if (jobCount.get(selectedJobID) ==null){
                jobCount.put(selectedJobID,0);
            }
            int count = jobCount.get(selectedJobID);
            jobCount.put(selectedJobID,(count + 1));
            //for test only
            
        }
        logger.info("Number of errorjob:" + errorjob);

        //for test only
        String filetest = "C:/Users/Qin/Desktop/testJob.csv";
        PrintWriter pwt = SiloUtil.openFileForSequentialWriting(filetest, false);
        pwt.println("jobid,area,x,y,jobCount");
        for (int id: jobX.keySet()){
            pwt.print(id);
            pwt.print(",");
            pwt.print(jobArea.get(id));
            pwt.print(",");
            pwt.print(jobX.get(id));
            pwt.print(",");
            pwt.print(jobY.get(id));
            pwt.print(",");
            if(jobCount.get(id) ==null){
                pwt.println(0);
            }else{
                pwt.println(jobCount.get(id));
            }
        }
        pwt.close();
        //for test only

        logger.info("   Finished job microlocation.");
    }



    private void readJobFile() {

        for (int zone : dataSetSynPop.getTazs()){
            Map<String,Map<Integer,Float>> jobLocationListForThisJobType = new HashMap<>();
            for (String jobType : PropertiesSynPop.get().main.jobStringType){
                Map<Integer,Float> jobLocationAndArea = new HashMap<>();
                jobLocationListForThisJobType.put(jobType,jobLocationAndArea);
            }
            zoneJobTypeJobLocationArea.put(zone,jobLocationListForThisJobType);
        }
        
        for (int row = 1; row <= PropertiesSynPop.get().main.jobLocationlist.getRowCount(); row++) {

            int id = (int) PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"OBJECTID");
            int zone = (int) PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"zoneID");
            float xCoordinate = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"x");
            float yCoordinate = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"y");
            float agriArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job1");
            float mnftArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job2");
            float utilArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job3");
            float consArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job4");
            float retlArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job5");
            float trnsArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job6");
            float fincArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job7");
            float rlstArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job8");
            float admnArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job9");
            float servArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"job10");
            jobZone.put(id,zone);
            jobX.put(id,xCoordinate);
            jobY.put(id,yCoordinate);


            if (zoneJobTypeJobLocationArea.get(zone) != null){
                zoneJobTypeJobLocationArea.get(zone).get("Agri").put(id,agriArea);
                zoneJobTypeJobLocationArea.get(zone).get("Mnft").put(id,mnftArea);
                zoneJobTypeJobLocationArea.get(zone).get("Util").put(id,utilArea);
                zoneJobTypeJobLocationArea.get(zone).get("Cons").put(id,consArea);
                zoneJobTypeJobLocationArea.get(zone).get("Retl").put(id,retlArea);
                zoneJobTypeJobLocationArea.get(zone).get("Trns").put(id,trnsArea);
                zoneJobTypeJobLocationArea.get(zone).get("Finc").put(id,fincArea);
                zoneJobTypeJobLocationArea.get(zone).get("Rlst").put(id,rlstArea);
                zoneJobTypeJobLocationArea.get(zone).get("Admn").put(id,admnArea);
                zoneJobTypeJobLocationArea.get(zone).get("Serv").put(id,servArea);
            }

        }
    }

    private void densityCalculation() {
        for (int zone : dataSetSynPop.getTazs()){
            Map<String,Integer> jobsByJobType = new HashMap<>();
            Map<String,Float> densityByJobType = new HashMap<>();
            jobsByJobTypeInTAZ.put(zone,jobsByJobType);
            zoneJobTypeDensity.put(zone,densityByJobType);
        }

        for (Job jj: dataContainer.getJobData().getJobs()) {
            int zoneID = jj.getZone();
            String jobType = jj.getType();

            if (jobsByJobTypeInTAZ.get(zoneID).get(jobType) ==null){
                jobsByJobTypeInTAZ.get(zoneID).put(jobType,0);
            }

            int numberOfJobs = jobsByJobTypeInTAZ.get(zoneID).get(jobType);
            jobsByJobTypeInTAZ.get(zoneID).put(jobType,numberOfJobs+1);

        }

        for (int zone : dataSetSynPop.getTazs()){
            if((jobsByJobTypeInTAZ.get(zone) == null)||(zoneJobTypeJobLocationArea.get(zone) == null)){
                continue;
            }
            for (String jobType : PropertiesSynPop.get().main.jobStringType){
                if ((zoneJobTypeJobLocationArea.get(zone).get(jobType) != null)&(jobsByJobTypeInTAZ.get(zone).get(jobType) != null)) {
                    float density = getSum(zoneJobTypeJobLocationArea.get(zone).get(jobType).values())/jobsByJobTypeInTAZ.get(zone).get(jobType);
                    zoneJobTypeDensity.get(zone).put(jobType, density);
                }
            }
        }
    }


    private static float getSum(Collection<? extends Number> values) {
        float sm = 0.f;
        for (Number value : values) {
            sm += value.doubleValue();
        }
        return sm;
    }
}